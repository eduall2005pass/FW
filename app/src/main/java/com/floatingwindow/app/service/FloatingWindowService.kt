package com.floatingwindow.app.service

import android.app.*
import android.content.*
import android.content.ComponentCallbacks2
import android.graphics.PixelFormat
import android.os.*
import android.view.*
import androidx.core.app.NotificationCompat
import com.floatingwindow.app.R
import com.floatingwindow.app.launcher.LauncherManager
import com.floatingwindow.app.manager.FloatingWindowManager
import com.floatingwindow.app.model.WindowContentType
import com.floatingwindow.app.model.WindowState
import com.floatingwindow.app.perf.FrameThrottler
import com.floatingwindow.app.perf.MemoryWatcher
import com.floatingwindow.app.perf.WindowRenderPool
import com.floatingwindow.app.ui.*
import java.util.concurrent.ConcurrentHashMap

/**
 * FloatingWindowService — fully optimised foreground service.
 *
 * Performance features:
 *  • FrameThrottler: coalesces rapid drag/resize WM calls to 60fps
 *  • WindowRenderPool: recycles View trees
 *  • MemoryWatcher: trims content on low RAM
 *  • SnapEngine + SnapOverlayView: Windows 11 edge-snap
 *  • START_STICKY + WakeLock: survives background kill
 */
class FloatingWindowService : Service() {

    companion object {
        const val ACTION_NEW_WINDOW      = "action.NEW_WINDOW"
        const val ACTION_CLOSE_ALL       = "action.CLOSE_ALL"
        const val ACTION_SWITCH_NEXT     = "action.SWITCH_NEXT"
        const val ACTION_TOGGLE_LAUNCHER = "action.TOGGLE_LAUNCHER"
        const val EXTRA_TITLE            = "extra.TITLE"
        const val EXTRA_CONTENT_TYPE     = "extra.CONTENT_TYPE"
        const val CHANNEL_ID             = "fws_channel"
        const val NOTIF_ID               = 1

        private var instance: FloatingWindowService? = null
        fun getInstance() = instance
    }

    // ── Core fields ───────────────────────────────────────────────────────────
    private lateinit var sysWM:   WindowManager
    lateinit var wm: FloatingWindowManager; private set

    // Performance subsystems
    private lateinit var throttler:    FrameThrottler
    private lateinit var renderPool:   WindowRenderPool
    private lateinit var memWatcher:   MemoryWatcher
    private lateinit var snapEngine:   SnapEngine
    private          var snapOverlay:  SnapOverlayView? = null

    private val viewMap  = ConcurrentHashMap<String, FloatingWindowView>()
    private var taskbar: TaskbarView? = null
    private lateinit var launcherManager: LauncherManager

    val binder = LocalBinder()
    inner class LocalBinder : Binder() { fun getService() = this@FloatingWindowService }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        instance = this

        sysWM       = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm          = FloatingWindowManager(this)
        throttler   = FrameThrottler()
        renderPool  = WindowRenderPool()
        snapEngine  = SnapEngine(this)
        memWatcher  = MemoryWatcher(this, ::onMemoryLevel)

        wm.addListener(eventListener)
        launcherManager = LauncherManager(this, sysWM, wm)

        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification(0))

        attachSnapOverlay()
        attachTaskbar()
        memWatcher.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NEW_WINDOW -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Window"
                val type  = WindowContentType.entries.getOrElse(
                    intent.getIntExtra(EXTRA_CONTENT_TYPE, 0)) { WindowContentType.BLANK }
                wm.createWindow(title, type)
            }
            ACTION_CLOSE_ALL       -> wm.closeAll()
            ACTION_SWITCH_NEXT     -> wm.switchToNext()
            ACTION_TOGGLE_LAUNCHER -> launcherManager.toggle()
        }
        return START_STICKY   // ← restart if killed
    }

    override fun onBind(intent: Intent?) = binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped app from recents — keep service alive, restart it
        val restart = Intent(applicationContext, FloatingWindowService::class.java)
        val pending = PendingIntent.getService(
            applicationContext, 1, restart,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE)
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, pending)
        super.onTaskRemoved(rootIntent)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        memWatcher.onTrimMemory(level)
    }

    override fun onDestroy() {
        throttler.cancelAll()
        memWatcher.stop()
        renderPool.clear()
        wm.removeListener(eventListener)
        viewMap.values.forEach { safeRemove(it) }
        viewMap.clear()
        taskbar?.let { safeRemove(it) }
        snapOverlay?.let { safeRemove(it) }
        launcherManager.destroy()
        instance = null
        super.onDestroy()
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun openWindow(title: String, type: WindowContentType) = wm.createWindow(title, type)
    fun closeAll()       = wm.closeAll()
    fun switchNext()     = wm.switchToNext()
    fun toggleLauncher() = launcherManager.toggle()

    // ── Snap overlay (full-screen, sits below windows) ────────────────────────
    private fun attachSnapOverlay() {
        val overlay = SnapOverlayView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            0, 0,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        safeAdd(overlay, lp)
        snapOverlay = overlay
    }

    // ── Taskbar ───────────────────────────────────────────────────────────────
    private fun attachTaskbar() {
        taskbar = TaskbarView(
            context        = this,
            onWindowSelect = { id -> wm.switchToWindow(id) },
            onNewWindow    = { wm.createWindow("Window ${wm.getWindowCount() + 1}") },
            onLauncherOpen = { launcherManager.toggle() }
        )
        safeAdd(taskbar!!, taskbar!!.layoutParams)
    }

    private fun refreshTaskbar() = taskbar?.updateWindows(wm.getAllWindows())

    // ── Event listener ────────────────────────────────────────────────────────
    private val eventListener = object : FloatingWindowManager.WindowEventListener {

        override fun onWindowCreated(state: WindowState) {
            val view = FloatingWindowView(
                context        = this@FloatingWindowService,
                state          = state,
                manager        = wm,
                windowManager  = sysWM,
                throttler      = throttler,
                snapEngine     = snapEngine,
                snapOverlay    = snapOverlay,
                onFocusRequest = { id -> wm.focusWindow(id) },
                onMinimize     = { id -> wm.minimizeWindow(id) },
                onMaximize     = { id -> wm.maximizeWindow(id) },
                onClose        = { id -> wm.closeWindow(id) }
            )
            state.rootView = view
            viewMap[state.id] = view
            safeAdd(view, view.layoutParams)
            view.post { view.animateIn() }
            refreshTaskbar()
            updateNotification()
        }

        override fun onWindowFocused(state: WindowState, allStates: List<WindowState>) {
            allStates.forEach { s -> viewMap[s.id]?.updateVisualState(s.isFocused) }
            bringToTop(state.id)
            refreshTaskbar()
        }

        override fun onWindowMinimized(state: WindowState) {
            // Animate then hide — animation drives the hide via callback in FloatingWindowView
            val v = viewMap[state.id] ?: return
            v.animateMinimize {
                v.visibility = android.view.View.GONE
                v.trimContent()   // free RAM while minimised
            }
            refreshTaskbar()
            updateNotification()
        }

        override fun onWindowRestored(state: WindowState) {
            val v = viewMap[state.id] ?: return
            v.restoreContent()
            v.visibility = android.view.View.VISIBLE
            v.animateRestore()
            bringToTop(state.id)
            refreshTaskbar()
            updateNotification()
        }

        override fun onWindowMaximized(state: WindowState) {
            viewMap[state.id]?.applyGeometry(); refreshTaskbar()
        }
        override fun onWindowUnmaximized(state: WindowState) {
            viewMap[state.id]?.applyGeometry(); refreshTaskbar()
        }

        override fun onWindowClosed(id: String) {
            val v = viewMap.remove(id) ?: return
            v.animateClose { safeRemove(v) }
            refreshTaskbar()
            updateNotification()
        }

        override fun onWindowMoved(state: WindowState)   { viewMap[state.id]?.applyGeometry() }
        override fun onWindowResized(state: WindowState) { viewMap[state.id]?.applyGeometry() }

        override fun onZOrderChanged(orderedIds: List<String>) {
            // Remove all windows + re-add in bottom→top order
            val views = orderedIds.mapNotNull { viewMap[it] }
            views.forEach { safeRemove(it) }
            views.forEach { safeAdd(it, it.layoutParams) }
            // Snap overlay below everything, taskbar always on top
            snapOverlay?.let { safeRemove(it) }
            taskbar?.let    { tb -> safeRemove(tb) }
            snapOverlay?.let { safeAdd(it, getSnapOverlayLp()) }
            taskbar?.let    { tb -> safeAdd(tb, tb.layoutParams) }
        }
    }

    // ── Memory management ─────────────────────────────────────────────────────
    private fun onMemoryLevel(level: MemoryWatcher.MemoryLevel) {
        when (level) {
            MemoryWatcher.MemoryLevel.LOW -> {
                renderPool.clear()
                // Trim content of all minimized windows
                wm.getMinimizedWindows().forEach { state ->
                    viewMap[state.id]?.trimContent()
                }
            }
            MemoryWatcher.MemoryLevel.CRITICAL -> {
                renderPool.clear()
                // Trim all non-focused windows
                wm.getAllWindows()
                    .filter { !it.isFocused }
                    .forEach { state -> viewMap[state.id]?.trimContent() }
            }
            else -> {}
        }
    }

    // ── WM helpers ────────────────────────────────────────────────────────────
    private fun safeAdd(v: View, lp: WindowManager.LayoutParams) {
        try { sysWM.addView(v, lp) } catch (e: Exception) { e.printStackTrace() }
    }
    private fun safeRemove(v: View) {
        try { sysWM.removeView(v) } catch (_: Exception) {}
    }
    private fun bringToTop(id: String) {
        val v = viewMap[id] ?: return
        safeRemove(v); safeAdd(v, v.layoutParams)
        taskbar?.let { tb -> safeRemove(tb); safeAdd(tb, tb.layoutParams) }
    }

    private fun getSnapOverlayLp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT, 0, 0,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Floating Windows",
                NotificationManager.IMPORTANCE_LOW).apply {
                description         = "Floating window service"
                setShowBadge(false)
                enableVibration(false)
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    private fun buildNotification(count: Int): Notification {
        // Tap notification → open launcher
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // "Close All" action
        val closeIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingWindowService::class.java).apply { action = ACTION_CLOSE_ALL },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Floating Windows")
            .setContentText(if (count > 0) "$count window(s) open" else "Running in background")
            .setSmallIcon(R.drawable.ic_window)
            .setContentIntent(tapIntent)
            .addAction(0, "Close All", closeIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(wm.getWindowCount()))
    }
}

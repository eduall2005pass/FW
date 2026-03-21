package com.floatingwindow.app.ui

import android.app.AlertDialog
import android.content.*
import android.os.*
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.R
import com.floatingwindow.app.group.AppGroupManager
import com.floatingwindow.app.model.WindowContentType
import com.floatingwindow.app.permission.OverlayPermissionManager
import com.floatingwindow.app.permission.PermissionChecker
import com.floatingwindow.app.permission.WindowModeDetector
import com.floatingwindow.app.service.FloatingWindowService
import com.floatingwindow.app.settings.AppSettings
import com.floatingwindow.app.settings.ThemeEngine
import com.floatingwindow.app.settings.SettingsActivity
import com.floatingwindow.app.virtual.ui.VirtualDesktopActivity

class MainActivity : AppCompatActivity() {

    private var service: FloatingWindowService? = null
    private var bound = false
    private var groupManager: AppGroupManager? = null
    private lateinit var settings: AppSettings

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = (b as? FloatingWindowService.LocalBinder)?.getService()
            bound   = true
            service?.wm?.let { wm -> groupManager = AppGroupManager(this@MainActivity, wm) }
            refreshCount(); renderQuickGroups()
        }
        override fun onServiceDisconnected(n: ComponentName?) { service = null; bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        setContentView(R.layout.activity_main)
        applyTheme()
        wireButtons()
        updateModeIndicator()
    }

    override fun onResume() {
        super.onResume()
        if (WindowModeDetector.isForcedVirtual(this) ||
            settings.defaultWindowMode == "virtual") { goToVirtual(); return }
        val status = PermissionChecker.check(this)
        if (!status.hasOverlay) { startActivity(PermissionChecker.buildPermissionIntent(this)); return }
        if (WindowModeDetector.detect(this) == WindowModeDetector.WindowMode.VIRTUAL) { goToVirtual(); return }
        if (OverlayPermissionManager.isSamsung() && !OverlayPermissionManager.hasPermission(this)) {
            showSamsungWarning(); return
        }
        bindAndStart()
        refreshCount(); renderQuickGroups()
        updateModeIndicator()
    }

    override fun onDestroy() {
        if (bound) { unbindService(conn); bound = false }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
    }

    private fun applyTheme() {
        val theme = ThemeEngine.getTheme(settings)
        window.statusBarColor     = theme.statusBar
        window.navigationBarColor = theme.bgPrimary
        findViewById<View>(android.R.id.content)?.setBackgroundColor(theme.bgPrimary)
    }

    private fun wireButtons() {
        listOf(
            R.id.btnNotepad    to (WindowContentType.NOTEPAD      to "Notepad"),
            R.id.btnCalculator to (WindowContentType.CALCULATOR   to "Calculator"),
            R.id.btnTerminal   to (WindowContentType.TERMINAL     to "Terminal"),
            R.id.btnSettings   to (WindowContentType.SETTINGS     to "Settings"),
            R.id.btnFiles      to (WindowContentType.FILE_BROWSER to "Files"),
            R.id.btnBlank      to (WindowContentType.BLANK        to "Window")
        ).forEach { (id, pair) ->
            val (type, title) = pair
            findViewById<View>(id)?.setOnClickListener { open(title, type); moveTaskToBack(true) }
        }
        findViewById<View>(R.id.btnVirtualDesktop)?.setOnClickListener { goToVirtual() }
        findViewById<View>(R.id.btnAppLauncher)?.setOnClickListener {
            service?.toggleLauncher(); moveTaskToBack(true)
        }
        findViewById<View>(R.id.btnGroups)?.setOnClickListener {
            startActivity(Intent(this, GroupsActivity::class.java))
        }
        // ✨ Settings button
        findViewById<View>(R.id.btnSettingsActivity)?.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.btnSwitchNext)?.setOnClickListener { service?.switchNext(); moveTaskToBack(true) }
        findViewById<View>(R.id.btnCloseAll)?.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Close all windows?")
                .setPositiveButton("Close All") { _, _ -> service?.closeAll(); refreshCount() }
                .setNegativeButton("Cancel", null).show()
        }
        findViewById<View>(R.id.btnSwitchToVirtual)?.setOnClickListener { goToVirtual() }
        findViewById<View>(R.id.btnStopService)?.setOnClickListener {
            stopService(Intent(this, FloatingWindowService::class.java)); finish()
        }
    }

    private fun updateModeIndicator() {
        val tv = findViewById<TextView>(R.id.tvModeIndicator) ?: return
        val mode = WindowModeDetector.detect(this)
        tv.text = if (mode == WindowModeDetector.WindowMode.OVERLAY) "🪟 Overlay Mode" else "🖥️ Virtual Mode"
        tv.setTextColor(if (mode == WindowModeDetector.WindowMode.OVERLAY) 0xFF3FB950.toInt() else 0xFF4CC9F0.toInt())
    }

    private fun renderQuickGroups() {
        val container = findViewById<LinearLayout>(R.id.quickGroupsRow) ?: return
        container.removeAllViews()
        val gm = groupManager ?: return
        val groups = gm.getAllGroups().take(3)
        container.visibility = if (groups.isEmpty()) View.GONE else View.VISIBLE
        groups.forEach { group ->
            val chip = TextView(this).apply {
                text = "${group.emoji} ${group.name}"; textSize = 11f
                setTextColor(android.graphics.Color.WHITE)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat(); setColor(0x33FFFFFF); setStroke(dp(1), 0x66FFFFFF)
                }
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setOnClickListener { gm.launchGroup(group.id); moveTaskToBack(true) }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dp(8), 0) }
            }
            container.addView(chip)
        }
    }

    private fun showSamsungWarning() {
        AlertDialog.Builder(this)
            .setTitle("Samsung Permission Issue")
            .setMessage("Settings → Apps → ⋮ → Special access → Appear on top\n\nToggle OFF → ON for this app.")
            .setPositiveButton("Open Settings") { _, _ -> startActivity(OverlayPermissionManager.getPermissionIntent(this)) }
            .setNeutralButton("Use Virtual Mode") { _, _ -> goToVirtual() }
            .setNegativeButton("Continue Anyway") { _, _ -> bindAndStart() }
            .show()
    }

    private fun goToVirtual() {
        startActivity(Intent(this, VirtualDesktopActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP) }); finish()
    }

    private fun bindAndStart() {
        val i = Intent(this, FloatingWindowService::class.java)
        startForegroundService(i)
        if (!bound) bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    private fun open(title: String, type: WindowContentType) {
        if (bound) service?.openWindow(title, type)
        else startForegroundService(Intent(this, FloatingWindowService::class.java).apply {
            action = FloatingWindowService.ACTION_NEW_WINDOW
            putExtra(FloatingWindowService.EXTRA_TITLE, title)
            putExtra(FloatingWindowService.EXTRA_CONTENT_TYPE, type.ordinal)
        })
    }

    private fun refreshCount() {
        val count = service?.wm?.getWindowCount() ?: 0
        findViewById<TextView>(R.id.tvWindowCount)?.text = "$count window(s)"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

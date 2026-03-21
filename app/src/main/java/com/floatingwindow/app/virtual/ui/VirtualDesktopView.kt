package com.floatingwindow.app.virtual.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.floatingwindow.app.virtual.engine.VirtualWindowEngine
import com.floatingwindow.app.virtual.model.VAppType
import com.floatingwindow.app.virtual.model.VWState
import com.floatingwindow.app.virtual.model.VirtualWindow
import java.util.concurrent.ConcurrentHashMap

/**
 * VirtualDesktopView
 *
 * The "screen" canvas that holds all VirtualWindowFrames.
 * Draws its own wallpaper, manages z-order of child views,
 * and shows snap preview rectangles.
 *
 * This is a standard FrameLayout — no SYSTEM_ALERT_WINDOW needed.
 */
class VirtualDesktopView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private val engine = VirtualWindowEngine()
    private val frameMap = ConcurrentHashMap<String, VirtualWindowFrame>()

    // Snap preview overlay (drawn via Canvas)
    private var snapZone = VirtualWindowEngine.SnapZone.NONE
    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x330067C0; style = Paint.Style.FILL
    }
    private val snapStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA0067C0.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat()
    }
    private val snapCorner = dp(10).toFloat()

    // Wallpaper grid paint
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x10000000; strokeWidth = 1f
    }

    // Taskbar listener
    var onWindowsChanged: (() -> Unit)? = null

    init {
        setWillNotDraw(false)
        setBackgroundColor(0xFF1A1A2E.toInt())
        post { engine.addListener(engineListener) }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        engine.setDesktopSize(w, h)
    }

    // ── Public API ────────────────────────────────────────────────────────────
    fun openApp(type: VAppType, title: String = type.title) = engine.create(type, title)
    fun closeAll()    = engine.closeAll()
    fun switchNext()  = engine.switchToNext()
    fun getEngine()   = engine

    // ── Draw wallpaper + snap overlay ─────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawWallpaper(canvas)
        if (snapZone != VirtualWindowEngine.SnapZone.NONE) drawSnapPreview(canvas)
    }

    private fun drawWallpaper(canvas: Canvas) {
        // Subtle dot-grid
        val step = dp(28).toFloat()
        val dotR = dp(1).toFloat()
        var x = step
        while (x < width) {
            var y = step
            while (y < height - dp(42)) {
                canvas.drawCircle(x, y, dotR, gridPaint)
                y += step
            }
            x += step
        }
    }

    private fun drawSnapPreview(canvas: Canvas) {
        val taskH = dp(42)
        val usableH = height - taskH
        val rect = when (snapZone) {
            VirtualWindowEngine.SnapZone.LEFT  -> RectF(0f, 0f, width / 2f, usableH.toFloat())
            VirtualWindowEngine.SnapZone.RIGHT -> RectF(width / 2f, 0f, width.toFloat(), usableH.toFloat())
            VirtualWindowEngine.SnapZone.TOP   -> RectF(0f, 0f, width.toFloat(), usableH.toFloat())
            else                               -> return
        }
        canvas.drawRoundRect(rect, snapCorner, snapCorner, snapPaint)
        canvas.drawRoundRect(rect, snapCorner, snapCorner, snapStroke)
    }

    // ── Engine listener → View operations ─────────────────────────────────────
    private val engineListener = object : VirtualWindowEngine.Listener {

        override fun onCreated(w: VirtualWindow) {
            val frame = VirtualWindowFrame(context, w, engine) { id ->
                engine.focus(id)
            }
            frame.onSnapZoneChanged = { zone ->
                snapZone = zone; invalidate()
            }
            frameMap[w.id] = frame
            val lp = LayoutParams(w.width, w.height)
            addView(frame, lp)
            frame.x = w.x.toFloat(); frame.y = w.y.toFloat()
            frame.post { frame.animateOpen() }
            onWindowsChanged?.invoke()
        }

        override fun onFocused(w: VirtualWindow, all: List<VirtualWindow>) {
            all.forEach { vw -> frameMap[vw.id]?.applyFocusStyle(vw.isFocused) }
            // Bring focused view to top of View hierarchy
            frameMap[w.id]?.let { bringChildToFront(it) }
            onWindowsChanged?.invoke()
        }

        override fun onMinimized(w: VirtualWindow) {
            val f = frameMap[w.id] ?: return
            f.animateMinimize { f.visibility = View.GONE }
            onWindowsChanged?.invoke()
        }

        override fun onRestored(w: VirtualWindow) {
            val f = frameMap[w.id] ?: return
            f.visibility = View.VISIBLE
            f.syncGeometry()
            f.animateRestore()
            bringChildToFront(f)
            onWindowsChanged?.invoke()
        }

        override fun onMaximized(w: VirtualWindow) {
            frameMap[w.id]?.syncGeometry(); onWindowsChanged?.invoke()
        }
        override fun onUnmaximized(w: VirtualWindow) {
            frameMap[w.id]?.syncGeometry(); onWindowsChanged?.invoke()
        }

        override fun onClosed(id: String) {
            val f = frameMap.remove(id) ?: return
            f.animateClose { removeView(f) }
            onWindowsChanged?.invoke()
        }

        override fun onMoved(w: VirtualWindow) {
            frameMap[w.id]?.syncGeometry()
        }
        override fun onResized(w: VirtualWindow) {
            frameMap[w.id]?.syncGeometry()
        }

        override fun onZOrderChanged(orderedIds: List<String>) {
            orderedIds.forEach { id -> frameMap[id]?.let { bringChildToFront(it) } }
        }

        override fun onAllClosed() {
            onWindowsChanged?.invoke()
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

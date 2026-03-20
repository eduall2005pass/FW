package com.floatingwindow.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.view.*
import android.view.animation.*
import android.widget.*
import com.floatingwindow.app.R
import com.floatingwindow.app.manager.FloatingWindowManager
import com.floatingwindow.app.model.WindowState
import com.floatingwindow.app.model.WindowStatus
import com.floatingwindow.app.perf.FrameThrottler
import kotlin.math.abs

@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class FloatingWindowView(
    context: Context,
    val state: WindowState,
    private val manager: FloatingWindowManager,
    private val windowManager: WindowManager,
    private val throttler: FrameThrottler,
    private val snapEngine: SnapEngine,
    private val snapOverlay: SnapOverlayView?,
    private val onFocusRequest: (String) -> Unit,
    private val onMinimize:     (String) -> Unit,
    private val onMaximize:     (String) -> Unit,
    private val onClose:        (String) -> Unit
) : FrameLayout(context) {

    val layoutParams: WindowManager.LayoutParams = makeLp()

    // Views
    private lateinit var titleBar:    LinearLayout
    private lateinit var titleText:   TextView
    private lateinit var btnMin:      TextView
    private lateinit var btnMax:      TextView
    private lateinit var btnClose:    TextView
    private lateinit var contentWrap: FrameLayout
    private lateinit var resizeHandle: View
    private lateinit var statusText:  TextView

    // Drag state
    private var dragStartRawX = 0f; private var dragStartRawY = 0f
    private var dragStartLpX  = 0;  private var dragStartLpY  = 0
    private var dragging      = false
    private var currentSnap   = SnapEngine.SnapZone.NONE

    // Pre-snap saved geometry (for drag-away restore)
    private var snapSavedX = 0; private var snapSavedY = 0
    private var snapSavedW = 0; private var snapSavedH = 0
    private var wasSnapped = false

    // Resize state
    private var resRawX0 = 0f; private var resRawY0 = 0f
    private var resW0    = 0;  private var resH0    = 0
    private var resizing = false

    init {
        inflate(context, R.layout.floating_window, this)
        bindViews()
        setupTitleBar()
        setupResizeHandle()
        loadContent()
        applyFocusColors(state.isFocused)
    }

    // ── Layout params ─────────────────────────────────────────────────────────
    private fun makeLp() = WindowManager.LayoutParams(
        state.width, state.height, state.x, state.y,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).also { it.gravity = Gravity.TOP or Gravity.START }

    private fun bindViews() {
        titleBar    = findViewById(R.id.titleBar)
        titleText   = findViewById(R.id.titleText)
        btnMin      = findViewById(R.id.btnMinimize)
        btnMax      = findViewById(R.id.btnMaximize)
        btnClose    = findViewById(R.id.btnClose)
        contentWrap = findViewById(R.id.contentContainer)
        resizeHandle= findViewById(R.id.resizeHandle)
        statusText  = findViewById(R.id.statusBar)
    }

    // ── Title bar ─────────────────────────────────────────────────────────────
    private fun setupTitleBar() {
        titleText.text   = state.title
        statusText.text  = state.contentType.label

        titleBar.setOnTouchListener { _, e -> handleDrag(e) }

        // Double-tap title bar = maximize toggle
        var lastTap = 0L
        titleBar.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < 300) onMaximize(state.id)
            lastTap = now
        }

        btnMin.setOnClickListener   { animateMinimize { onMinimize(state.id) } }
        btnMax.setOnClickListener   { onMaximize(state.id) }
        btnClose.setOnClickListener { animateClose { onClose(state.id) } }

        setupButtonHover(btnClose, hoverBg = 0xFFC42B1C.toInt())
        setupButtonHover(btnMin,   hoverBg = 0x33000000)
        setupButtonHover(btnMax,   hoverBg = 0x33000000)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonHover(v: TextView, hoverBg: Int) {
        v.setOnTouchListener { btn, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN ->
                    btn.setBackgroundColor(hoverBg)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL ->
                    btn.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
            false
        }
    }

    // ── Drag with snap ────────────────────────────────────────────────────────
    private fun handleDrag(e: MotionEvent): Boolean {
        if (state.isMaximized) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = e.rawX; dragStartRawY = e.rawY
                dragStartLpX  = layoutParams.x; dragStartLpY = layoutParams.y
                dragging      = false
                onFocusRequest(state.id)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - dragStartRawX
                val dy = e.rawY - dragStartRawY
                if (!dragging && (abs(dx) > 8 || abs(dy) > 8)) dragging = true
                if (!dragging) return true

                val newX = (dragStartLpX + dx).toInt()
                val newY = (dragStartLpY + dy).toInt()

                // Detect snap zone
                val zone = snapEngine.evaluate(newX + layoutParams.width / 2, newY)
                if (zone != currentSnap) {
                    currentSnap = zone
                    if (zone != SnapEngine.SnapZone.NONE) {
                        val geo = snapEngine.geometryFor(zone)
                        snapOverlay?.showZone(zone, RectF(
                            geo.x.toFloat(), geo.y.toFloat(),
                            (geo.x + geo.width).toFloat(), (geo.y + geo.height).toFloat()
                        ))
                    } else {
                        snapOverlay?.hide()
                    }
                }

                // If dragging away from a snapped position, restore saved geometry first
                if (wasSnapped && zone == SnapEngine.SnapZone.NONE) {
                    layoutParams.width  = snapSavedW; layoutParams.height = snapSavedH
                    state.width = snapSavedW; state.height = snapSavedH
                    wasSnapped = false
                }

                layoutParams.x = newX; layoutParams.y = newY
                state.x = newX;  state.y = newY

                throttler.post(state.id) { safeUpdate() }
                return true
            }

            MotionEvent.ACTION_UP -> {
                snapOverlay?.hide()
                if (dragging) {
                    if (currentSnap != SnapEngine.SnapZone.NONE) {
                        commitSnap(currentSnap)
                    } else {
                        manager.moveWindow(state.id, layoutParams.x, layoutParams.y)
                    }
                }
                dragging    = false
                currentSnap = SnapEngine.SnapZone.NONE
                return true
            }
        }
        return false
    }

    private fun commitSnap(zone: SnapEngine.SnapZone) {
        // Save pre-snap geometry for restore
        snapSavedX = state.x; snapSavedY = state.y
        snapSavedW = state.width; snapSavedH = state.height
        wasSnapped = true

        val geo = snapEngine.geometryFor(zone)
        state.x = geo.x;         state.y = geo.y
        state.width = geo.width;  state.height = geo.height

        // Animate into snap position
        val targetX = geo.x.toFloat()
        val targetY = geo.y.toFloat()

        layoutParams.x = geo.x; layoutParams.y = geo.y
        layoutParams.width = geo.width; layoutParams.height = geo.height

        animate()
            .translationX(0f).translationY(0f)
            .setDuration(180).setInterpolator(DecelerateInterpolator())
            .withEndAction { safeUpdate() }
            .start()
        safeUpdate()
        manager.moveWindow(state.id, geo.x, geo.y)
        manager.resizeWindow(state.id, geo.width, geo.height)
    }

    // ── Resize ────────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandle() {
        resizeHandle.setOnTouchListener { _, e ->
            if (state.isMaximized) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    resRawX0 = e.rawX; resRawY0 = e.rawY
                    resW0    = layoutParams.width; resH0 = layoutParams.height
                    resizing = true
                    onFocusRequest(state.id)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!resizing) return@setOnTouchListener false
                    val nW = (resW0 + (e.rawX - resRawX0)).toInt().coerceAtLeast(manager.dpToPx(200))
                    val nH = (resH0 + (e.rawY - resRawY0)).toInt().coerceAtLeast(manager.dpToPx(130))
                    layoutParams.width = nW; layoutParams.height = nH
                    state.width = nW;  state.height = nH
                    throttler.post("resize_${state.id}") { safeUpdate() }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (resizing) {
                        throttler.flush("resize_${state.id}") {
                            manager.resizeWindow(state.id, layoutParams.width, layoutParams.height)
                        }
                    }
                    resizing = false; true
                }
                else -> false
            }
        }
    }

    private fun loadContent() {
        contentWrap.addView(
            WindowContentFactory.create(context, state.contentType, state.title))
    }

    // ── Focus ─────────────────────────────────────────────────────────────────
    fun updateVisualState(focused: Boolean) = applyFocusColors(focused)

    private fun applyFocusColors(focused: Boolean) {
        if (focused) {
            titleBar.setBackgroundColor(0xFF0067C0.toInt())
            listOf(titleText, btnMin, btnMax, btnClose)
                .forEach { it.setTextColor(0xFFFFFFFF.toInt()) }
            // Subtle elevation increase for focused window
            elevation = dp(20).toFloat()
        } else {
            titleBar.setBackgroundColor(0xFFE8E8E8.toInt())
            listOf(titleText, btnMin, btnMax, btnClose)
                .forEach { it.setTextColor(0xFF666666.toInt()) }
            elevation = dp(10).toFloat()
        }
    }

    // ── Geometry sync ─────────────────────────────────────────────────────────
    fun applyGeometry() {
        layoutParams.x      = state.x;     layoutParams.y      = state.y
        layoutParams.width  = state.width; layoutParams.height = state.height
        btnMax.text         = if (state.isMaximized) "❐" else "□"
        resizeHandle.visibility = if (state.isMaximized) GONE else VISIBLE
        safeUpdate()
    }

    // ── Content trim (called by MemoryWatcher on low RAM) ──────────────────
    fun trimContent() {
        if (state.status == WindowStatus.MINIMIZED) {
            contentWrap.removeAllViews()   // free inner views while minimized
        }
    }

    fun restoreContent() {
        if (contentWrap.childCount == 0) {
            contentWrap.addView(
                WindowContentFactory.create(context, state.contentType, state.title))
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────

    /** Open: scale+fade in from center */
    fun animateIn() {
        alpha = 0f; scaleX = 0.82f; scaleY = 0.82f
        pivotX = width / 2f; pivotY = height / 2f
        animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()
    }

    /** Minimize: shrink down to taskbar position */
    fun animateMinimize(onDone: () -> Unit) {
        val sw = context.resources.displayMetrics.widthPixels.toFloat()
        val sh = context.resources.displayMetrics.heightPixels.toFloat()
        animate()
            .translationX(sw / 2 - layoutParams.x - layoutParams.width / 2)
            .translationY(sh - layoutParams.y - layoutParams.height)
            .scaleX(0.15f).scaleY(0.15f)
            .alpha(0f)
            .setDuration(260)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                translationX = 0f; translationY = 0f
                scaleX = 1f;  scaleY = 1f
                onDone()
            }
            .start()
    }

    /** Restore: pop up from taskbar */
    fun animateRestore() {
        val sh = context.resources.displayMetrics.heightPixels.toFloat()
        translationY = sh - layoutParams.y.toFloat()
        scaleX = 0.15f; scaleY = 0.15f; alpha = 0f
        animate()
            .translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(240)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()
    }

    /** Close: shrink + fade */
    fun animateClose(onDone: () -> Unit) {
        animate()
            .scaleX(0.88f).scaleY(0.88f).alpha(0f)
            .setDuration(150)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction(onDone)
            .start()
    }

    /** Snap preview shake (brief bounce when snapping) */
    private fun snapBounce() {
        animate().scaleX(0.97f).scaleY(0.97f).setDuration(80)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(100)
                    .setInterpolator(OvershootInterpolator()).start()
            }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun safeUpdate() {
        try { windowManager.updateViewLayout(this, layoutParams) } catch (_: Exception) {}
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) onFocusRequest(state.id)
        return super.onTouchEvent(e)
    }
}

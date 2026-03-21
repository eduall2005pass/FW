package com.floatingwindow.app.virtual.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.*
import android.view.animation.*
import android.widget.*
import com.floatingwindow.app.virtual.apps.VAppFactory
import com.floatingwindow.app.virtual.engine.VirtualWindowEngine
import com.floatingwindow.app.virtual.model.VWState
import com.floatingwindow.app.virtual.model.VirtualWindow
import kotlin.math.abs

/**
 * VirtualWindowFrame
 * The actual Android View that represents one floating window
 * inside the VirtualDesktopView canvas.
 */
@SuppressLint("ClickableViewAccessibility", "ViewConstructor")
class VirtualWindowFrame(
    context: Context,
    val vw: VirtualWindow,
    private val engine: VirtualWindowEngine,
    private val onBringToFront: (String) -> Unit
) : FrameLayout(context) {

    // Title bar views
    private val titleBar   = LinearLayout(context)
    private val titleIcon  = TextView(context)
    private val titleText  = TextView(context)
    private val btnMin     = TextView(context)
    private val btnMax     = TextView(context)
    private val btnClose   = TextView(context)
    // Content
    private val contentArea = FrameLayout(context)
    // Resize handle
    private val resizeHandle = View(context)
    // Status bar
    private val statusBar  = TextView(context)

    // Drag
    private var dragX0 = 0f; private var dragY0 = 0f
    private var dragFrameX0 = 0f; private var dragFrameY0 = 0f
    private var dragging = false

    // Resize
    private var resX0 = 0f; private var resY0 = 0f
    private var resW0 = 0;  private var resH0 = 0
    private var resizing = false

    // Snap preview callback (set by VirtualDesktopView)
    var onSnapZoneChanged: ((VirtualWindowEngine.SnapZone) -> Unit)? = null
    var onDropped: ((String, Int, Int) -> Unit)? = null

    init {
        buildLayout()
        syncGeometry()
        applyFocusStyle(vw.isFocused)
        elevation = dp(12).toFloat()
    }

    // ── Layout ────────────────────────────────────────────────────────────────
    private fun buildLayout() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), 0xFFCCCCCC.toInt())
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = dp(10).toFloat()
        }

        // Title bar
        titleBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(32))
        }
        titleIcon.apply { text = vw.appType.emoji; textSize = 12f; setPadding(0,0,dp(6),0) }
        titleText.apply {
            text = vw.title; textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        fun mkBtn(label: String) = TextView(context).apply {
            text = label; textSize = 13f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(32))
            isClickable = true; isFocusable = true
        }
        btnMin.apply   { text = "—"; textSize = 13f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(38), dp(32)) }
        btnMax.apply   { text = "□"; textSize = 13f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(38), dp(32)) }
        btnClose.apply { text = "✕"; textSize = 12f; gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(dp(38), dp(32)) }

        setupButtonHover(btnMin,   hoverBg = 0x22000000)
        setupButtonHover(btnMax,   hoverBg = 0x22000000)
        setupButtonHover(btnClose, hoverBg = 0xFFC42B1C.toInt())

        btnMin.setOnClickListener   { animateMinimize { engine.minimize(vw.id) } }
        btnMax.setOnClickListener   { engine.maximize(vw.id) }
        btnClose.setOnClickListener { animateClose { engine.close(vw.id) } }

        titleBar.addView(titleIcon); titleBar.addView(titleText)
        titleBar.addView(btnMin); titleBar.addView(btnMax); titleBar.addView(btnClose)
        titleBar.setOnTouchListener { _, e -> handleDrag(e) }

        // Double-tap title bar = maximize
        var lastTap = 0L
        titleBar.setOnClickListener {
            val now = System.currentTimeMillis()
            if (now - lastTap < 300) engine.maximize(vw.id)
            lastTap = now
        }

        // Content
        contentArea.apply {
            setBackgroundColor(Color.WHITE)
            addView(VAppFactory.build(context, vw.appType),
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

        // Status bar + resize handle
        val bottomBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF0F0F0.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(18))
        }
        statusBar.apply {
            text = vw.appType.title; textSize = 9f; setTextColor(0xFF999999.toInt())
            setPadding(dp(8), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        resizeHandle.apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(2).toFloat(); setColor(0xFF0067C0.toInt())
            }
            layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply {
                setMargins(0, dp(2), dp(2), dp(2))
            }
            setOnTouchListener { _, e -> handleResize(e) }
        }
        bottomBar.addView(statusBar); bottomBar.addView(resizeHandle)

        // Dividers
        val div1 = View(context).apply { setBackgroundColor(0xFFE0E0E0.toInt()) }
        val div2 = View(context).apply { setBackgroundColor(0xFFE8E8E8.toInt()) }

        root.addView(titleBar)
        root.addView(div1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(contentArea, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(div2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
        root.addView(bottomBar)

        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Touch anywhere on frame → focus
        setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) onBringToFront(vw.id)
            false
        }
    }

    // ── Drag ──────────────────────────────────────────────────────────────────
    private fun handleDrag(e: MotionEvent): Boolean {
        if (vw.isMaximized) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                dragX0 = e.rawX; dragY0 = e.rawY
                dragFrameX0 = x; dragFrameY0 = y
                dragging = false
                onBringToFront(vw.id)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.rawX - dragX0; val dy = e.rawY - dragY0
                if (!dragging && (abs(dx) > 6 || abs(dy) > 6)) dragging = true
                if (dragging) {
                    val nx = (dragFrameX0 + dx).toInt()
                    val ny = (dragFrameY0 + dy).toInt()
                    x = nx.toFloat(); y = ny.toFloat()
                    vw.x = nx; vw.y = ny

                    // Snap zone feedback
                    val zone = engine.evaluateSnap(nx + vw.width / 2, ny)
                    onSnapZoneChanged?.invoke(zone)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                onSnapZoneChanged?.invoke(VirtualWindowEngine.SnapZone.NONE)
                if (dragging) {
                    val zone = engine.evaluateSnap(vw.x + vw.width / 2, vw.y)
                    if (zone != VirtualWindowEngine.SnapZone.NONE) {
                        engine.applySnap(vw.id, zone)
                        syncGeometry()
                        snapBounce()
                    } else {
                        engine.move(vw.id, vw.x, vw.y)
                    }
                }
                dragging = false
                return true
            }
        }
        return false
    }

    // ── Resize ────────────────────────────────────────────────────────────────
    private fun handleResize(e: MotionEvent): Boolean {
        if (vw.isMaximized) return false
        when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                resX0 = e.rawX; resY0 = e.rawY
                resW0 = vw.width; resH0 = vw.height
                resizing = true; onBringToFront(vw.id); return true
            }
            MotionEvent.ACTION_MOVE -> {
                val nw = (resW0 + (e.rawX - resX0)).toInt().coerceAtLeast(dp(200))
                val nh = (resH0 + (e.rawY - resY0)).toInt().coerceAtLeast(dp(130))
                vw.width = nw; vw.height = nh
                layoutParams = (layoutParams as ViewGroup.MarginLayoutParams).also { it.width = nw; it.height = nh }
                requestLayout(); return true
            }
            MotionEvent.ACTION_UP -> {
                if (resizing) engine.resize(vw.id, vw.width, vw.height)
                resizing = false; return true
            }
        }
        return false
    }

    // ── Sync position/size from VirtualWindow state ───────────────────────────
    fun syncGeometry() {
        x = vw.x.toFloat(); y = vw.y.toFloat()
        val lp = layoutParams as? ViewGroup.LayoutParams ?: LayoutParams(vw.width, vw.height)
        lp.width = vw.width; lp.height = vw.height
        layoutParams = lp
        btnMax.text = if (vw.isMaximized) "❐" else "□"
        resizeHandle.visibility = if (vw.isMaximized) GONE else VISIBLE
    }

    // ── Focus style ───────────────────────────────────────────────────────────
    fun applyFocusStyle(focused: Boolean) {
        if (focused) {
            titleBar.setBackgroundColor(0xFF0067C0.toInt())
            listOf(titleIcon, titleText, btnMin, btnMax, btnClose)
                .forEach { it.setTextColor(Color.WHITE) }
            elevation = dp(20).toFloat()
        } else {
            titleBar.setBackgroundColor(0xFFEBEBEB.toInt())
            listOf(titleIcon, titleText, btnMin, btnMax, btnClose)
                .forEach { it.setTextColor(0xFF666666.toInt()) }
            elevation = dp(8).toFloat()
        }
    }

    // ── Animations ────────────────────────────────────────────────────────────
    fun animateOpen() {
        alpha = 0f; scaleX = 0.80f; scaleY = 0.80f
        pivotX = width / 2f; pivotY = height / 2f
        animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220).setInterpolator(OvershootInterpolator(1.0f)).start()
    }

    fun animateMinimize(then: () -> Unit) {
        val parent = parent as? View
        val targetX = ((parent?.width ?: 0) / 2f) - (vw.x + vw.width / 2f)
        val targetY = ((parent?.height ?: 0).toFloat()) - vw.y.toFloat()
        animate().translationX(targetX).translationY(targetY)
            .scaleX(0.12f).scaleY(0.12f).alpha(0f)
            .setDuration(280).setInterpolator(AccelerateInterpolator(1.4f))
            .withEndAction { translationX=0f; translationY=0f; scaleX=1f; scaleY=1f; then() }
            .start()
    }

    fun animateRestore() {
        val parent = parent as? View
        val fromY  = ((parent?.height ?: 0).toFloat()) - vw.y.toFloat()
        translationY = fromY; scaleX = 0.12f; scaleY = 0.12f; alpha = 0f
        animate().translationY(0f).scaleX(1f).scaleY(1f).alpha(1f)
            .setDuration(250).setInterpolator(DecelerateInterpolator(1.4f)).start()
    }

    fun animateClose(then: () -> Unit) {
        animate().scaleX(0.85f).scaleY(0.85f).alpha(0f)
            .setDuration(160).setInterpolator(AccelerateInterpolator())
            .withEndAction(then).start()
    }

    private fun snapBounce() {
        animate().scaleX(0.96f).scaleY(0.96f).setDuration(80).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(120).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtonHover(v: TextView, hoverBg: Int) {
        v.setOnTouchListener { btn, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN              -> btn.setBackgroundColor(hoverBg)
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL            -> btn.setBackgroundColor(Color.TRANSPARENT)
            }
            false
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

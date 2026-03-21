package com.floatingwindow.app.virtual.ui

import android.animation.*
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.virtual.model.VAppType

/**
 * VirtualDesktopActivity
 *
 * Full-screen Activity that IS the virtual desktop.
 * No SYSTEM_ALERT_WINDOW permission required — everything lives
 * inside this Activity's window.
 *
 * Layout:
 *  ┌──────────────────────────┐
 *  │   VirtualDesktopView     │  ← drag/resize windows here
 *  │                          │
 *  ├──────────────────────────┤
 *  │   VirtualTaskbar (42dp)  │  ← window chips + ⊞ Start
 *  └──────────────────────────┘
 *
 *  + StartMenu overlay (shows when ⊞ tapped)
 */
class VirtualDesktopActivity : AppCompatActivity() {

    private lateinit var desktop: VirtualDesktopView
    private lateinit var taskbar: VirtualTaskbar
    private lateinit var startMenuOverlay: FrameLayout
    private var startMenuVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Full-screen immersive
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.statusBarColor = Color.TRANSPARENT

        buildLayout()

        // Open a welcome window on first launch
        desktop.post {
            desktop.openApp(VAppType.NOTEPAD, "Welcome")
        }
    }

    override fun onBackPressed() {
        when {
            startMenuVisible -> hideStartMenu()
            else             -> super.onBackPressed()
        }
    }

    // ── Build full layout ─────────────────────────────────────────────────────
    private fun buildLayout() {
        val root = FrameLayout(this)

        // Desktop canvas
        desktop = VirtualDesktopView(this).apply {
            onWindowsChanged = { refreshTaskbar() }
        }

        // Taskbar
        taskbar = VirtualTaskbar(this).apply {
            onStartClick = { toggleStartMenu() }
            onWindowChipClick = { id -> desktop.getEngine().switchTo(id); hideStartMenu() }
        }

        // Assemble root: desktop above, taskbar pinned to bottom
        val desktopLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { bottomMargin = dp(42) }

        val taskbarLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(42)
        ).apply { gravity = Gravity.BOTTOM }

        root.addView(desktop, desktopLp)
        root.addView(taskbar, taskbarLp)

        // Start menu overlay
        startMenuOverlay = buildStartMenu()
        startMenuOverlay.visibility = View.GONE
        root.addView(startMenuOverlay, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    // ── Start menu ────────────────────────────────────────────────────────────
    private fun buildStartMenu(): FrameLayout {
        val overlay = FrameLayout(this)

        // Dim background
        val dim = View(this).apply {
            setBackgroundColor(0x88000000.toInt())
            setOnClickListener { hideStartMenu() }
        }
        overlay.addView(dim, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Menu panel
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(dp(16f),dp(16f),dp(16f),dp(16f),0f,0f,0f,0f)
                setColor(0xFF16213E.toInt())
                setStroke(dp(1), 0x33FFFFFF)
            }
            elevation = dp(24).toFloat()
        }

        // Search bar
        val searchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        val searchField = EditText(this).apply {
            hint = "🔍  Search apps…"; setHintTextColor(0xFF888888.toInt())
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(20f); setColor(0xFF2A3550.toInt()) }
            setPadding(dp(14), dp(10), dp(14), dp(10)); textSize = 13f; isSingleLine = true
        }
        searchBar.addView(searchField, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Section label
        val pinnedLabel = makeSectionLabel("PINNED APPS")

        // App grid
        val apps = VAppType.entries.toList()
        val appGrid = GridLayout(this).apply {
            columnCount = 4; setPadding(dp(12), dp(4), dp(12), dp(12))
        }

        // Filter by search
        fun refreshGrid(query: String = "") {
            appGrid.removeAllViews()
            val filtered = if (query.isBlank()) apps
                else apps.filter { it.title.contains(query, ignoreCase = true) }
            filtered.forEach { type ->
                val cell = buildAppCell(type) {
                    desktop.openApp(type)
                    hideStartMenu()
                }
                appGrid.addView(cell, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f),
                    GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width=0; height=GridLayout.LayoutParams.WRAP_CONTENT; setMargins(dp(4),dp(4),dp(4),dp(4)) })
            }
        }
        refreshGrid()

        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) { refreshGrid(s?.toString() ?: "") }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // All windows section
        val windowsLabel = makeSectionLabel("OPEN WINDOWS")
        val windowsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }

        fun refreshWindows() {
            windowsRow.removeAllViews()
            desktop.getEngine().windows.forEach { vw ->
                val chip = TextView(this).apply {
                    text = "${vw.appType.emoji} ${vw.title}"
                    textSize = 11f; setTextColor(Color.WHITE)
                    background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(0xFF0067C0.toInt()) }
                    setPadding(dp(10), dp(5), dp(10), dp(5))
                    setOnClickListener { desktop.getEngine().switchTo(vw.id); hideStartMenu() }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0,0,dp(6),0) }
                }
                windowsRow.addView(chip)
            }
        }

        panel.addView(searchBar)
        panel.addView(pinnedLabel)
        panel.addView(appGrid)
        panel.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply { setMargins(dp(16),0,dp(16),0) })
        panel.addView(windowsLabel)
        panel.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(windowsRow)
            setPadding(0, 0, 0, dp(4))
        })

        // Bottom controls
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
            setBackgroundColor(0x22000000)
        }
        val closeAllBtn = TextView(this).apply {
            text = "✕  Close All"; textSize = 12f; setTextColor(0xFFFF6B6B.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(0xFF2A1A1A.toInt()) }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { desktop.closeAll(); hideStartMenu() }
        }
        val switchBtn = TextView(this).apply {
            text = "⇄  Switch"; textSize = 12f; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(0xFF1F3A6E.toInt()) }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { desktop.switchNext(); hideStartMenu() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(8), 0, 0, 0) }
        }
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        val backBtn = TextView(this).apply {
            text = "✕ Exit"; textSize = 12f; setTextColor(0xFFAAAAAA.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(6f); setColor(0xFF333333.toInt()) }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { finish() }
        }
        bottomRow.addView(closeAllBtn); bottomRow.addView(switchBtn)
        bottomRow.addView(spacer); bottomRow.addView(backBtn)
        panel.addView(bottomRow)

        val panelLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.BOTTOM }
        overlay.addView(panel, panelLp)

        // Refresh windows list whenever it's shown
        overlay.tag = Runnable { refreshWindows() }

        return overlay
    }

    // ── Start menu show/hide ──────────────────────────────────────────────────
    private fun toggleStartMenu() {
        if (startMenuVisible) hideStartMenu() else showStartMenu()
    }

    private fun showStartMenu() {
        startMenuVisible = true
        startMenuOverlay.visibility = View.VISIBLE
        // Run refresh
        (startMenuOverlay.tag as? Runnable)?.run()

        val panel = startMenuOverlay.getChildAt(1)
        panel.translationY = panel.height.toFloat().coerceAtLeast(dp(400f))
        panel.alpha = 0f
        panel.animate().translationY(0f).alpha(1f)
            .setDuration(260).setInterpolator(DecelerateInterpolator(1.2f)).start()
    }

    private fun hideStartMenu() {
        if (!startMenuVisible) return
        startMenuVisible = false
        val panel = startMenuOverlay.getChildAt(1)
        panel.animate().translationY(panel.height.toFloat().coerceAtLeast(dp(400f))).alpha(0f)
            .setDuration(200).setInterpolator(DecelerateInterpolator())
            .withEndAction { startMenuOverlay.visibility = View.GONE }.start()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun refreshTaskbar() {
        taskbar.update(desktop.getEngine().getOrdered())
    }

    private fun buildAppCell(type: VAppType, onClick: () -> Unit): View {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(6), dp(10), dp(6), dp(8))
            isClickable = true; isFocusable = true
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x330067C0), null, null)
            setOnClickListener { onClick() }
        }
        val icon = TextView(this).apply { text = type.emoji; textSize = 28f; gravity = Gravity.CENTER }
        val lbl  = TextView(this).apply {
            text = type.title; textSize = 10f; setTextColor(0xFFCCCCEE.toInt())
            gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        }
        cell.addView(icon); cell.addView(lbl)
        return cell
    }

    private fun makeSectionLabel(text: String) = TextView(this).apply {
        this.text = text; textSize = 9f; setTextColor(0xFF8888AA.toInt())
        letterSpacing = 0.1f; setPadding(dp(16), dp(10), dp(16), dp(4))
    }

    private fun dp(v: Int)   = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()
}

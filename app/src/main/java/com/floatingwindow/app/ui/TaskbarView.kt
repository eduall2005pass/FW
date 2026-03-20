package com.floatingwindow.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.*
import android.widget.*
import com.floatingwindow.app.model.WindowState
import com.floatingwindow.app.model.WindowStatus

/**
 * TaskbarView
 * Bottom overlay bar with:
 *  🪟 launcher button  |  window chips  |  grid (app launcher) button
 */
@SuppressLint("ViewConstructor")
class TaskbarView(
    context: Context,
    private val onWindowSelect: (String) -> Unit,
    private val onNewWindow: () -> Unit,
    private val onLauncherOpen: () -> Unit       // ← new: opens app launcher
) : LinearLayout(context) {

    val layoutParams: WindowManager.LayoutParams = buildLp()
    private val chipRow: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xF5F0F0F0.toInt())

        // Left: ＋ new blank window
        val newBtn = makeIconBtn("＋", 0xFF0067C0.toInt()) { onNewWindow() }

        // Separator
        val sep1 = makeSep()

        // Middle: scrollable chip area
        chipRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }

        // Separator
        val sep2 = makeSep()

        // Right: 🔲 app launcher
        val launcherBtn = makeIconBtn("⊞", 0xFF333333.toInt()) { onLauncherOpen() }

        addView(newBtn,      LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        addView(sep1,        LayoutParams(dp(1), LayoutParams.MATCH_PARENT).also { it.setMargins(0,dp(6),0,dp(6)) })
        addView(scroll,      LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(sep2,        LayoutParams(dp(1), LayoutParams.MATCH_PARENT).also { it.setMargins(0,dp(6),0,dp(6)) })
        addView(launcherBtn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    // ── Chip rendering ────────────────────────────────────────────────────────
    fun updateWindows(windows: List<WindowState>) {
        chipRow.removeAllViews()
        windows.filter { it.status != WindowStatus.CLOSED }
            .forEach { chipRow.addView(makeChip(it)) }
    }

    private fun makeChip(state: WindowState): View {
        val focused   = state.isFocused
        val minimized = state.isMinimized
        val bg = GradientDrawable().apply {
            cornerRadius = dp(4).toFloat()
            setColor(when {
                focused   -> 0xFF0067C0.toInt()
                minimized -> 0xFFE0E0E0.toInt()
                else      -> 0xFFF5F5F5.toInt()
            })
            if (!focused) setStroke(dp(1), 0xFFCCCCCC.toInt())
        }
        return TextView(context).apply {
            text      = clip("${state.contentType.emoji} ${state.title}")
            textSize  = 10.5f
            maxLines  = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(dp(9), dp(4), dp(9), dp(4))
            setTextColor(if (focused) 0xFFFFFFFF.toInt()
                else if (minimized) 0xFF666666.toInt() else 0xFF1A1A1A.toInt())
            background = bg
            setOnClickListener { onWindowSelect(state.id) }
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(dp(3), dp(6), dp(3), dp(6)) }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun makeIconBtn(label: String, color: Int, onClick: () -> Unit) =
        TextView(context).apply {
            text      = label
            textSize  = 18f
            setTextColor(color)
            gravity   = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), 0)
            setOnClickListener { onClick() }
        }

    private fun makeSep() = View(context).apply { setBackgroundColor(0xFFCCCCCC.toInt()) }

    private fun clip(s: String) = if (s.length > 16) s.take(14) + "…" else s

    private fun buildLp() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, dp(42), 0, 0,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.BOTTOM or Gravity.START }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

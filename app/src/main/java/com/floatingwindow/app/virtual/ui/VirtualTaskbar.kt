package com.floatingwindow.app.virtual.ui

import android.view.View

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.*
import com.floatingwindow.app.virtual.engine.VirtualWindowEngine
import com.floatingwindow.app.virtual.model.VirtualWindow

/**
 * VirtualTaskbar — the bottom bar of the virtual desktop.
 * Shows: Start button | window chips | clock
 */
class VirtualTaskbar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onStartClick: (() -> Unit)? = null
    var onWindowChipClick: ((String) -> Unit)? = null

    private val chipArea = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
    }
    private val clockTv = TextView(context).apply {
        textSize = 10f; setTextColor(0xFFE0E0E0.toInt()); gravity = Gravity.CENTER
        setPadding(dp(8), 0, dp(10), 0)
    }

    init {
        orientation = HORIZONTAL
        gravity     = Gravity.CENTER_VERTICAL
        setBackgroundColor(0xF21A1A2E.toInt())

        // Start button
        val startBtn = TextView(context).apply {
            text = "⊞"; textSize = 20f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(10), 0)
            setOnClickListener { onStartClick?.invoke() }
        }

        val sep = View(context).apply {
            setBackgroundColor(0x33FFFFFF)
            layoutParams = LayoutParams(dp(1), dp(24)).apply { setMargins(0, 0, dp(4), 0) }
        }

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipArea, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
        }

        // Clock — live ticking
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                val c = java.util.Calendar.getInstance()
                clockTv.text = "%02d:%02d".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE))
                handler.postDelayed(this, 30_000)
            }
        }
        clockTv.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) { handler.post(tick) }
            override fun onViewDetachedFromWindow(v: android.view.View) { handler.removeCallbacks(tick) }
        })

        addView(startBtn)
        addView(sep)
        addView(scroll, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        addView(clockTv)
    }

    fun update(windows: List<VirtualWindow>) {
        chipArea.removeAllViews()
        windows.forEach { vw ->
            val chip = TextView(context).apply {
                text = "${vw.appType.emoji} ${clip(vw.title)}"
                textSize = 10f; maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(4).toFloat()
                    setColor(when {
                        vw.isFocused   -> 0xFF0067C0.toInt()
                        vw.isMinimized -> 0xFF333355.toInt()
                        else           -> 0xFF2A2A4A.toInt()
                    })
                    if (vw.isFocused) setStroke(dp(1), 0xFF4499FF.toInt())
                }
                setTextColor(when {
                    vw.isFocused   -> Color.WHITE
                    vw.isMinimized -> 0xFF8888AA.toInt()
                    else           -> 0xFFCCCCEE.toInt()
                })
                setOnClickListener { onWindowChipClick?.invoke(vw.id) }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(3), dp(6), dp(3), dp(6)) }
            }
            chipArea.addView(chip)
        }
    }

    private fun clip(s: String) = if (s.length > 14) s.take(12) + "…" else s
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

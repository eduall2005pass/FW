package com.floatingwindow.app.permission

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.ui.MainActivity
import com.floatingwindow.app.virtual.ui.VirtualDesktopActivity

/**
 * PermissionActivity — updated with "Use Virtual Mode" fallback button.
 * User can skip permission and go straight to virtual desktop.
 */
class PermissionActivity : AppCompatActivity() {

    companion object { const val REQUEST_OVERLAY = 1001 }

    private val handler  = Handler(Looper.getMainLooper())
    private var polling  = false
    private lateinit var statusIcon:    TextView
    private lateinit var statusTitle:   TextView
    private lateinit var successOverlay: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OverlayPermissionManager.hasPermission(this)) { goToMain(); return }
        buildUI()
    }

    override fun onResume() { super.onResume(); checkAndProceed() }
    override fun onPause()  { super.onPause(); polling = false; handler.removeCallbacksAndMessages(null) }
    override fun onDestroy(){ super.onDestroy(); handler.removeCallbacksAndMessages(null) }

    private fun buildUI() {
        val info     = OverlayPermissionManager.getInstructions(this)
        val isSamsung = OverlayPermissionManager.isSamsung()

        val root = FrameLayout(this)

        // Gradient background
        val bg = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF4A6CF7.toInt(), 0xFF7C3AED.toInt())
            )
        }

        val scroll = android.widget.ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(52), dp(20), dp(32))
        }

        // ── Header card ──
        val headerCard = card(0x15FFFFFF, dp(20))
        val headerInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        statusIcon = TextView(this).apply { text = "🔐"; textSize = 52f; gravity = Gravity.CENTER }
        statusTitle = TextView(this).apply {
            text = "Overlay Permission"; textSize = 22f; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD; setPadding(0,dp(12),0,dp(6))
        }
        val desc = TextView(this).apply {
            text = "Required to show floating windows over other apps."; textSize = 13f
            setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER; setLineSpacing(0f, 1.4f)
        }
        val deviceBadge = TextView(this).apply {
            text = "📱 ${info.deviceName}  •  Android ${Build.VERSION.RELEASE}"
            textSize = 11f; setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        }
        headerInner.addView(statusIcon); headerInner.addView(statusTitle)
        headerInner.addView(desc); headerInner.addView(deviceBadge)
        headerCard.addView(headerInner)
        content.addView(headerCard, lp(dp(16)))

        // ── Quick path ──
        val path = TextView(this).apply {
            text = "📍  ${info.quickPath}"; textSize = 11f; setTextColor(Color.WHITE)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat(); setColor(0x25FFFFFF); setStroke(dp(1), 0x55FFFFFF)
            }
        }
        content.addView(path, lp(dp(14)))

        // ── Open Settings button ──
        val settingsBtn = Button(this).apply {
            text = "⚙️   Open Settings"; textSize = 15f; setTextColor(0xFF0067C0.toInt())
            setTypeface(typeface, Typeface.BOLD)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat(); setColor(Color.WHITE)
            }
            elevation = dp(4).toFloat(); setPadding(0, dp(16), 0, dp(16))
            setOnClickListener { openSettings() }
        }
        content.addView(settingsBtn, lp(dp(10)))

        // ── Step-by-step guide ──
        val stepsCard = card(0x15FFFFFF, dp(16))
        val stepsInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        stepsInner.addView(TextView(this).apply {
            text = "📋  Step-by-step guide"; textSize = 13f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD; setPadding(0,0,0,dp(12))
        })
        info.englishSteps.forEachIndexed { i, step ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.TOP; setPadding(0,dp(6),0,dp(6))
            }
            val num = TextView(this).apply {
                text = "${i+1}"; textSize = 11f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(0xFF0067C0.toInt()); setSize(dp(24), dp(24))
                }
                layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            }
            val txt = TextView(this).apply {
                text = step; textSize = 12f; setTextColor(0xEEFFFFFF.toInt())
                lineSpacingMultiplier = 1.3f; setPadding(dp(10), dp(2), 0, 0)
            }
            row.addView(num)
            row.addView(txt, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            stepsInner.addView(row)
            if (i < info.englishSteps.size - 1) {
                stepsInner.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) },
                    LinearLayout.LayoutParams(dp(1), dp(12)).apply { marginStart = dp(11) })
            }
        }
        stepsCard.addView(stepsInner)
        content.addView(stepsCard, lp(dp(14)))

        // Samsung / warning note
        if (info.warningNote != null || isSamsung) {
            val warnCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background  = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat(); setColor(0x25FFAA00); setStroke(dp(1), 0x66FFAA00)
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            if (info.warningNote != null) warnCard.addView(TextView(this).apply {
                text = "⚠️  ${info.warningNote}"; textSize = 12f
                setTextColor(0xFFFFEEAA.toInt()); setLineSpacing(0f, 1.4f)
            })
            if (isSamsung) warnCard.addView(TextView(this).apply {
                text = "\n💡 Samsung tip: Toggle OFF then ON and restart the app if it doesn't work."
                textSize = 12f; setTextColor(0xCCFFFFFF.toInt()); setLineSpacing(0f, 1.4f)
            })
            content.addView(warnCard, lp(dp(14)))
        }

        // ── Divider ──
        content.addView(View(this).apply { setBackgroundColor(0x33FFFFFF) },
            lp(dp(8)).also { it.height = dp(1) })

        // ── Virtual Desktop fallback button ──────────────────────────────────
        val virtualCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background  = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(0x20FFFFFF); setStroke(dp(1), 0x44FFFFFF)
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val virtualTitle = TextView(this).apply {
            text = "🖥️  Skip & Use Virtual Desktop"; textSize = 14f
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
        }
        val virtualDesc = TextView(this).apply {
            text = "Can't grant permission? No problem.\nVirtual Desktop mode works without any permission — fully functional floating windows inside the app."
            textSize = 12f; setTextColor(0xCCFFFFFF.toInt()); lineSpacingMultiplier = 1.5f
            setPadding(0, dp(6), 0, dp(12))
        }
        val virtualBtn = TextView(this).apply {
            text = "Launch Virtual Desktop  →"; textSize = 13f
            setTextColor(0xFF1A1A2E.toInt()); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(10).toFloat(); setColor(0xFF4CC9F0.toInt())
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setOnClickListener { goToVirtual() }
        }
        virtualCard.addView(virtualTitle); virtualCard.addView(virtualDesc)
        virtualCard.addView(virtualBtn)
        content.addView(virtualCard, lp(dp(8)))

        // polling blink
        val pollRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val dot = TextView(this).apply {
            text = "●"; textSize = 10f; setTextColor(0xFFFFAA00.toInt()); setPadding(0,0,dp(6),0)
            val blink = android.view.animation.AlphaAnimation(0.2f,1f).apply {
                duration = 800; repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            startAnimation(blink)
        }
        val pollTxt = TextView(this).apply { text = "Waiting for permission…"; textSize = 11f; setTextColor(0xAAFFFFFF.toInt()) }
        pollRow.addView(dot); pollRow.addView(pollTxt)
        content.addView(pollRow, lp(dp(4)))

        scroll.addView(content)
        bg.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        root.addView(bg)

        successOverlay = buildSuccess()
        successOverlay.visibility = View.GONE
        root.addView(successOverlay, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
    }

    private fun buildSuccess(): FrameLayout {
        val ol = FrameLayout(this).apply { setBackgroundColor(0xCC000000.toInt()) }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(20).toFloat(); setColor(Color.WHITE)
            }
            setPadding(dp(32),dp(32),dp(32),dp(32)); elevation = dp(8).toFloat()
        }
        card.addView(TextView(this).apply { text = "✅"; textSize = 52f; gravity = Gravity.CENTER })
        card.addView(TextView(this).apply {
            text = "Permission Granted!"; textSize = 20f; setTextColor(0xFF1A1A1A.toInt())
            typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setPadding(0,dp(12),0,dp(8))
        })
        card.addView(TextView(this).apply {
            text = "Launching Floating Windows…"; textSize = 13f; setTextColor(0xFF666666.toInt()); gravity = Gravity.CENTER
        })
        ol.addView(card, FrameLayout.LayoutParams(dp(280), FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        return ol
    }

    private fun openSettings() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(OverlayPermissionManager.getPermissionIntent(this), REQUEST_OVERLAY)
            startPolling()
        } catch (e: Exception) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                startPolling()
            } catch (_: Exception) {}
        }
    }

    private fun startPolling() {
        if (polling) return; polling = true; poll()
    }
    private fun poll() {
        handler.postDelayed({
            if (OverlayPermissionManager.hasPermission(this)) showSuccess()
            else if (polling) poll()
        }, 500)
    }
    private fun checkAndProceed() {
        if (OverlayPermissionManager.hasPermission(this)) showSuccess()
    }
    private fun showSuccess() {
        polling = false; handler.removeCallbacksAndMessages(null)
        successOverlay.visibility = View.VISIBLE
        successOverlay.alpha = 0f; successOverlay.animate().alpha(1f).setDuration(300).start()
        handler.postDelayed({ goToMain() }, 1200)
    }
    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }); finish()
    }
    private fun goToVirtual() {
        WindowModeDetector.forceVirtual(this)
        startActivity(Intent(this, VirtualDesktopActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }); finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        @Suppress("DEPRECATION") super.onActivityResult(req, res, data)
        if (req == REQUEST_OVERLAY) startPolling()
    }

    private fun card(bg: Int, r: Int) = FrameLayout(this).apply {
        background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = r.toFloat(); setColor(bg) }
    }
    private fun lp(bottom: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = bottom }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

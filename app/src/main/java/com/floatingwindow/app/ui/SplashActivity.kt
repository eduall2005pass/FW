package com.floatingwindow.app.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.permission.OverlayPermissionManager
import com.floatingwindow.app.permission.PermissionChecker
import com.floatingwindow.app.permission.WindowModeDetector
import com.floatingwindow.app.virtual.ui.VirtualDesktopActivity

/**
 * SplashActivity — Smart entry point.
 *
 * Decision tree:
 *   1. Check overlay permission
 *   2. If no permission → try to get it (PermissionActivity)
 *   3. If has permission → check WindowModeDetector
 *      a. OVERLAY mode → MainActivity (real floating windows)
 *      b. VIRTUAL mode → VirtualDesktopActivity (no permission needed)
 *   4. If forced virtual → go straight to VirtualDesktopActivity
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var modeLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildSplash()
        PermissionChecker.requestNotificationPermission(this)
        Handler(Looper.getMainLooper()).postDelayed({ route() }, 1400)
    }

    private fun route() {
        // Forced virtual (device had overlay crash before)
        if (WindowModeDetector.isForcedVirtual(this)) {
            showStatus("🖥️ Virtual Desktop Mode", 0xFF4CC9F0.toInt())
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, VirtualDesktopActivity::class.java))
                finish()
            }, 600)
            return
        }

        val hasPermission = PermissionChecker.check(this).hasOverlay
        if (!hasPermission) {
            // No permission — detect if this is a permanently blocked device
            if (WindowModeDetector.isKnownBlockedDevice() || WindowModeDetector.isMiuiBlocked()) {
                // Known blocked device — go straight to virtual, skip permission request
                showStatus("🖥️ Virtual Desktop Mode", 0xFF4CC9F0.toInt())
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this, VirtualDesktopActivity::class.java))
                    finish()
                }, 600)
            } else {
                // Unknown device, maybe they can grant it — show permission screen
                showStatus("🔐 Checking permissions…", 0xFFFFAA00.toInt())
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this,
                        com.floatingwindow.app.permission.PermissionActivity::class.java))
                    finish()
                }, 400)
            }
            return
        }

        // Has permission — now check if device will actually honor it
        val mode = WindowModeDetector.detect(this)
        when (mode) {
            WindowModeDetector.WindowMode.OVERLAY -> {
                showStatus("🪟 Overlay Mode Active", 0xFF3FB950.toInt())
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }, 500)
            }
            WindowModeDetector.WindowMode.VIRTUAL -> {
                showStatus("🖥️ Virtual Desktop Mode", 0xFF4CC9F0.toInt())
                Handler(Looper.getMainLooper()).postDelayed({
                    startActivity(Intent(this, VirtualDesktopActivity::class.java))
                    finish()
                }, 600)
            }
        }
    }

    private fun showStatus(msg: String, color: Int) {
        statusText.text = msg
        statusText.setTextColor(color)
        modeLabel.visibility = View.VISIBLE
        modeLabel.text = when {
            WindowModeDetector.isKnownBlockedDevice() ->
                "📱 ${android.os.Build.BRAND}: overlay restricted"
            WindowModeDetector.isMiuiBlocked() ->
                "📱 MIUI: overlay restricted"
            else -> ""
        }
    }

    private fun buildSplash() {
        val root = FrameLayout(this)
        root.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xFF1A1A2E.toInt(), 0xFF16213E.toInt())
        )

        val center = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), 0, dp(32), 0)
        }

        val icon = TextView(this).apply {
            text = "⊞"; textSize = 72f; gravity = Gravity.CENTER
            setTextColor(0xFF4A6CF7.toInt())
        }
        val title = TextView(this).apply {
            text = "Floating Windows"; textSize = 26f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(12), 0, dp(6))
        }
        val subtitle = TextView(this).apply {
            text = "Desktop-grade window management"; textSize = 13f
            setTextColor(0xAAFFFFFF.toInt()); gravity = Gravity.CENTER
        }

        // Detecting mode label
        val detectingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER; setPadding(0, dp(24), 0, dp(4))
        }
        val dots = TextView(this).apply {
            text = "●"; textSize = 10f; setTextColor(0xFF4A6CF7.toInt()); setPadding(0,0,dp(6),0)
            val blink = android.view.animation.AlphaAnimation(0.2f, 1f).apply {
                duration = 600; repeatMode = android.view.animation.Animation.REVERSE
                repeatCount = android.view.animation.Animation.INFINITE
            }
            startAnimation(blink)
        }
        val detecting = TextView(this).apply {
            text = "Detecting best mode…"; textSize = 12f; setTextColor(0xFF8888AA.toInt())
        }
        detectingRow.addView(dots); detectingRow.addView(detecting)

        statusText = TextView(this).apply {
            text = ""; textSize = 13f; gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(4), 0, 0)
        }
        modeLabel = TextView(this).apply {
            text = ""; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(0xFF666688.toInt()); visibility = View.GONE
        }

        center.addView(icon); center.addView(title); center.addView(subtitle)
        center.addView(detectingRow); center.addView(statusText); center.addView(modeLabel)

        if (OverlayPermissionManager.isSamsung()) {
            center.addView(TextView(this).apply {
                text = "Samsung One UI Compatible"; textSize = 10f
                setTextColor(0x88FFFFFF.toInt()); gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }

        root.addView(center, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        setContentView(root)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

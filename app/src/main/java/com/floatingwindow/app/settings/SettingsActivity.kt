package com.floatingwindow.app.settings

import android.content.*
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.permission.WindowModeDetector

class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings
    private lateinit var theme: ThemeEngine.Theme
    private lateinit var rootContent: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = AppSettings(this)
        theme    = ThemeEngine.getTheme(settings)
        buildUI()
    }

    private fun buildUI() {
        val root = FrameLayout(this)
        root.setBackgroundColor(theme.bgPrimary)

        // App bar
        val appBar = buildAppBar()

        val scroll = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER }
        rootContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(32))
        }

        buildAllSections()
        scroll.addView(rootContent)

        val scrollLp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { topMargin = dp(56) }

        root.addView(appBar, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, dp(56), Gravity.TOP))
        root.addView(scroll, scrollLp)

        setContentView(root)
        ThemeEngine.applyToWindow(window, theme)
    }

    private fun buildAppBar(): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(theme.accent)
            setPadding(dp(4), 0, dp(16), 0)
            elevation = dp(4).toFloat()
        }
        val back = TextView(this).apply {
            text = "←"; textSize = 22f; setTextColor(Color.WHITE)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Settings"; textSize = 18f; setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val resetBtn = TextView(this).apply {
            text = "Reset"; textSize = 13f; setTextColor(0xCCFFFFFF.toInt())
            setPadding(dp(8), dp(6), dp(4), dp(6))
            setOnClickListener { confirmReset() }
        }
        bar.addView(back); bar.addView(title); bar.addView(resetBtn)
        return bar
    }

    private fun buildAllSections() {
        // ── 1. APPEARANCE ──────────────────────────────────────────────────────
        addSectionHeader("🎨  Appearance")
        addToggleRow("Dark Mode", "Switch between light and dark theme", settings.darkMode) { v ->
            settings.darkMode = v; recreate()
        }
        addToggleRow("Window Shadow", "Drop shadow under floating windows", settings.windowShadow) { v -> settings.windowShadow = v }
        addToggleRow("Window Blur", "Frosted glass effect", settings.windowBlur) { v -> settings.windowBlur = v }
        addSliderRow("Window Opacity", "Transparency of floating windows",
            (settings.windowOpacity * 100).toInt(), 30, 100, "%") { v -> settings.windowOpacity = v / 100f }
        addSliderRow("Corner Radius", "Rounded corners on windows", settings.cornerRadius, 0, 24, "dp") { v -> settings.cornerRadius = v }
        addClickRow("Accent Color", "Choose the highlight color", colorDot(settings.accentColor)) { showAccentPicker() }

        // ── 2. WINDOW BEHAVIOUR ────────────────────────────────────────────────
        addSectionHeader("🪟  Window Behaviour")
        addToggleRow("Snap to Edges", "Snap when dragged near edges", settings.snapEnabled) { v -> settings.snapEnabled = v }
        addToggleRow("Remember Positions", "Restore positions on relaunch", settings.rememberWindowPositions) { v -> settings.rememberWindowPositions = v }
        addToggleRow("Auto-Focus New Window", "Bring new windows to front", settings.autoFocusNewWindow) { v -> settings.autoFocusNewWindow = v }
        addToggleRow("Show Taskbar", "Bottom window taskbar overlay", settings.showTaskbar) { v -> settings.showTaskbar = v }
        addDropdownRow("Default Mode", settings.defaultWindowMode,
            listOf("auto" to "Auto-detect", "overlay" to "Always Overlay")) { v -> settings.defaultWindowMode = v }
        addSliderRow("Max Open Windows", "Limit simultaneous windows", settings.maxOpenWindows, 2, 16, "") { v -> settings.maxOpenWindows = v }

        // ── 3. PERFORMANCE ─────────────────────────────────────────────────────
        addSectionHeader("⚡  Performance")
        addToggleRow("Animations", "Window open/close/minimize animations", settings.animationsEnabled) { v -> settings.animationsEnabled = v }
        addDropdownRow("Animation Speed", settings.animationSpeed,
            listOf("slow" to "Slow", "normal" to "Normal", "fast" to "Fast")) { v -> settings.animationSpeed = v }
        addToggleRow("Low Memory Mode", "Reduce RAM usage", settings.lowMemoryMode) { v -> settings.lowMemoryMode = v }

        // ── 4. SERVICE ─────────────────────────────────────────────────────────
        addSectionHeader("🔧  Service")
        addToggleRow("Keep Service Alive", "Prevent system from killing service", settings.keepServiceAlive) { v -> settings.keepServiceAlive = v }
        addToggleRow("Show Notification", "Persistent notification while service runs", settings.showNotification) { v -> settings.showNotification = v }

        // ── 5. PERMISSIONS ─────────────────────────────────────────────────────
        addSectionHeader("🛡️  Permissions & Mode")
        val mode = WindowModeDetector.detect(this)
        addInfoRow("Current Mode", if (mode == WindowModeDetector.WindowMode.OVERLAY) "🪟 Overlay Active" else "🖥️ Virtual Mode")
        addInfoRow("Device", "${android.os.Build.BRAND}  •  Android ${android.os.Build.VERSION.RELEASE}")
        addClickRow("Overlay Permission", "Open system permission settings", label("→", theme.accent)) {
            try {
                startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        }

        // ── 6. PRIVACY ─────────────────────────────────────────────────────────
        addSectionHeader("🔒  Privacy")
        addToggleRow("Save Window History", "Remember recently opened windows", settings.saveWindowHistory) { v -> settings.saveWindowHistory = v }
        addToggleRow("Crash Analytics", "Send anonymous crash reports", settings.analyticsEnabled) { v -> settings.analyticsEnabled = v }
        addClickRow("Clear All Data", "Delete all groups, history and settings", label("⚠️", Color.RED)) { confirmClearData() }

        // ── 7. ABOUT ───────────────────────────────────────────────────────────
        addSectionHeader("ℹ️  About")
        buildAboutCard()
    }

    // ── ABOUT CARD with developer photo ──────────────────────────────────────
    private fun buildAboutCard() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg()
            setPadding(dp(20), dp(24), dp(20), dp(20))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(8)) }
        }

        // App header
        val appHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val appIconBg = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(theme.accent)
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
        }
        val appIconTv = TextView(this).apply {
            text = "⊞"; textSize = 28f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        appIconBg.addView(appIconTv)
        val appTextBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        appTextBlock.addView(TextView(this).apply {
            text = "Multitask Windows"
            textSize = 18f; setTextColor(theme.textPrimary); typeface = Typeface.DEFAULT_BOLD
        })
        appTextBlock.addView(TextView(this).apply {
            text = "Version ${AppSettings.APP_VERSION} (Build ${AppSettings.APP_BUILD})"
            textSize = 11f; setTextColor(theme.textSecondary); setPadding(0, dp(2), 0, 0)
        })

        // Badge row
        val badgeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0)
        }
        listOf("Kotlin" to theme.accent, "Android 8+" to 0xFF3B82F6.toInt(), "Samsung ✓" to 0xFF22C55E.toInt())
            .forEach { (txt, color) ->
                badgeRow.addView(TextView(this).apply {
                    text = txt; textSize = 10f; setTextColor(color)
                    background = GradientDrawable().apply {
                        cornerRadius = dp(6).toFloat()
                        setColor(0)
                        setStroke(dp(1), color)
                    }
                    setPadding(dp(7), dp(2), dp(7), dp(2))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, dp(5), 0) }
                })
            }
        appTextBlock.addView(badgeRow)
        appHeader.addView(appIconBg); appHeader.addView(appTextBlock)
        card.addView(appHeader)
        card.addView(divider())

        // ── DEVELOPER SECTION with photo ──────────────────────────────────────
        val devSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, dp(16))
        }

        val devLabel = TextView(this).apply {
            text = "DEVELOPER"; textSize = 10f
            setTextColor(theme.accent)
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(12))
        }
        devSection.addView(devLabel)

        val devRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // Developer photo (circular)
        val photoFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
        }
        val photoBg = object : android.view.View(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeResource(resources,
                        android.R.drawable.sym_contact_card)
                    if (bitmap != null) {
                        val size = minOf(width, height).toFloat()
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                        val shader = android.graphics.BitmapShader(
                            Bitmap.createScaledBitmap(bitmap, width, height, true),
                            android.graphics.Shader.TileMode.CLAMP,
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        paint.shader = shader
                        canvas.drawCircle(width / 2f, height / 2f, size / 2f, paint)

                        // Ring
                        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.STROKE
                            strokeWidth = dp(2).toFloat()
                            color = theme.accent
                        }
                        canvas.drawCircle(width / 2f, height / 2f, size / 2f - dp(1), ringPaint)
                    }
                } catch (e: Exception) {
                    // fallback: colored circle with initial
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent }
                    canvas.drawCircle(width / 2f, height / 2f, width / 2f, paint)
                    val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE; textSize = dp(28).toFloat()
                        textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD
                    }
                    canvas.drawText("S", width / 2f, height / 2f + dp(10), txtPaint)
                }
            }
        }
        photoBg.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        photoFrame.addView(photoBg)

        // Dev info block
        val devInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
        }
        devInfo.addView(TextView(this).apply {
            text = AppSettings.DEVELOPER
            textSize = 18f; setTextColor(theme.textPrimary)
            typeface = Typeface.DEFAULT_BOLD
        })
        devInfo.addView(TextView(this).apply {
            text = "Android Developer"; textSize = 12f
            setTextColor(theme.textSecondary); setPadding(0, dp(2), 0, dp(4))
        })
        // Social links row
        val socialRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        }
        listOf("GitHub" to "🐙", "Email" to "📧").forEach { (lbl, icon) ->
            socialRow.addView(TextView(this).apply {
                text = "$icon $lbl"; textSize = 11f; setTextColor(theme.accent)
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(theme.accent and 0x00FFFFFF or 0x15000000)
                    setStroke(dp(1), theme.accent)
                }
                setPadding(dp(8), dp(3), dp(8), dp(3))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dp(6), 0) }
            })
        }
        devInfo.addView(socialRow)

        devRow.addView(photoFrame)
        devRow.addView(devInfo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        devSection.addView(devRow)
        card.addView(devSection)
        card.addView(divider())

        // Links
        listOf(
            "⭐  Rate the App"  to "https://play.google.com",
            "🐛  Report a Bug"  to "https://github.com/siam/issues",
            "💬  Feedback"      to "mailto:siam@dev.com",
            "📖  Open Source"   to AppSettings.GITHUB_URL
        ).forEachIndexed { i, (lbl, url) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(11), 0, dp(11))
                isClickable = true; isFocusable = true
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x220000FF), null, null)
                setOnClickListener {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
                }
            }
            row.addView(TextView(this).apply {
                text = lbl; textSize = 13f; setTextColor(theme.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply { text = "›"; textSize = 18f; setTextColor(theme.textSecondary) })
            card.addView(row)
            if (i < 3) card.addView(divider())
        }

        card.addView(divider())
        card.addView(TextView(this).apply {
            text = "© 2025 ${AppSettings.DEVELOPER}. All rights reserved."
            textSize = 10f; setTextColor(theme.textHint); gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, 0)
        })

        rootContent.addView(card)
    }

    // ── Section helpers ───────────────────────────────────────────────────────
    private fun addSectionHeader(title: String) {
        rootContent.addView(TextView(this).apply {
            text = title; textSize = 11f; setTextColor(theme.accent)
            letterSpacing = 0.08f; typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(20), dp(16), dp(6))
        })
    }

    private fun addToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = buildRow(title, subtitle)
        val toggle = Switch(this).apply {
            isChecked = checked
            thumbTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            setOnCheckedChangeListener { _, v -> onChange(v) }
        }
        row.addView(toggle)
        addRowToCard(row)
    }

    private fun addSliderRow(title: String, subtitle: String, value: Int, min: Int, max: Int, unit: String, onChange: (Int) -> Unit) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(theme.bgCard)
            setPadding(dp(16), dp(10), dp(16), dp(8))
        }
        val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleTv = TextView(this).apply {
            text = title; textSize = 13f; setTextColor(theme.textPrimary); typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valTv = TextView(this).apply { text = "$value$unit"; textSize = 12f; setTextColor(theme.accent); typeface = Typeface.DEFAULT_BOLD }
        topRow.addView(titleTv); topRow.addView(valTv)
        val subTv = TextView(this).apply {
            text = subtitle; textSize = 11f; setTextColor(theme.textSecondary); setPadding(0, dp(2), 0, dp(6))
        }
        val seekBar = android.widget.SeekBar(this).apply {
            this.max = max - min; progress = value - min
            progressTintList = android.content.res.ColorStateList.valueOf(theme.accent)
            thumbTintList    = android.content.res.ColorStateList.valueOf(theme.accent)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, user: Boolean) {
                    val v = p + min; valTv.text = "$v$unit"; if (user) onChange(v)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
            })
        }
        wrapper.addView(topRow); wrapper.addView(subTv); wrapper.addView(seekBar)
        addCardWrapped(wrapper)
    }

    private fun addDropdownRow(title: String, current: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
        val currentLabel = options.firstOrNull { it.first == current }?.second ?: current
        addClickRow(title, "Current: $currentLabel", label("▾", theme.accent)) {
            val labels = options.map { it.second }.toTypedArray()
            AlertDialog.Builder(this).setTitle(title)
                .setItems(labels) { _, i -> onChange(options[i].first); recreate() }.show()
        }
    }

    private fun addClickRow(title: String, subtitle: String, badge: View? = null, onClick: () -> Unit) {
        val row = buildRow(title, subtitle)
        badge?.let { row.addView(it) }
        row.addView(TextView(this).apply { text = "›"; textSize = 18f; setTextColor(theme.textSecondary); setPadding(dp(6), 0, 0, 0) })
        row.setOnClickListener { onClick() }; row.isClickable = true; row.isFocusable = true
        row.foreground = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000), null, null)
        addRowToCard(row)
    }

    private fun addInfoRow(title: String, value: String) {
        val row = buildRow(title, "")
        row.addView(TextView(this).apply {
            text = value; textSize = 11f; setTextColor(theme.textSecondary); gravity = Gravity.END
        })
        addRowToCard(row)
    }

    // ── Card management ───────────────────────────────────────────────────────
    private var currentCard: LinearLayout? = null

    private fun addRowToCard(row: LinearLayout) {
        if (currentCard == null) startNewCard()
        val card = currentCard!!
        if (card.childCount > 0) card.addView(divider())
        card.addView(row)
    }

    private fun addCardWrapped(view: View) {
        flushCard()
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(); elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(8)) }
        }
        wrapper.addView(view); rootContent.addView(wrapper)
    }

    private fun flushCard() {
        val card = currentCard ?: return
        if (card.childCount > 0) rootContent.addView(card)
        currentCard = null
    }

    private fun startNewCard() {
        flushCard()
        currentCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg(); elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), 0, dp(16), dp(8)) }
        }
    }

    override fun onStop() { super.onStop(); flushCard() }

    // ── Dialogs ───────────────────────────────────────────────────────────────
    private fun showAccentPicker() {
        val grid = android.widget.GridLayout(this).apply { columnCount = 4; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        ThemeEngine.ACCENTS.forEach { (name, color) ->
            val dot = FrameLayout(this).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = dp(52); height = dp(52); setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
            dot.addView(android.view.View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL; setColor(color)
                    if (color == settings.accentColor) setStroke(dp(3), Color.WHITE)
                }
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                ).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
            })
            dot.setOnClickListener {
                settings.accentColor = color
                android.widget.Toast.makeText(this, "Accent: $name", android.widget.Toast.LENGTH_SHORT).show()
                recreate()
            }
            grid.addView(dot)
        }
        AlertDialog.Builder(this).setTitle("Choose Accent Color").setView(grid).show()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this).setTitle("Reset all settings?")
            .setMessage("All preferences will be restored to defaults.")
            .setPositiveButton("Reset") { _, _ -> settings.resetAll(); recreate() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun confirmClearData() {
        AlertDialog.Builder(this).setTitle("⚠️ Clear All Data?")
            .setMessage("This will delete all app groups, window history, and settings. Cannot be undone.")
            .setPositiveButton("Clear Everything") { _, _ ->
                settings.resetAll()
                getSharedPreferences("app_groups", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("launcher_prefs", MODE_PRIVATE).edit().clear().apply()
                getSharedPreferences("wm_mode", MODE_PRIVATE).edit().clear().apply()
                android.widget.Toast.makeText(this, "All data cleared", android.widget.Toast.LENGTH_SHORT).show()
                recreate()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ── View factories ────────────────────────────────────────────────────────
    private fun buildRow(title: String, subtitle: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        textBlock.addView(TextView(this).apply {
            text = title; textSize = 13f; setTextColor(theme.textPrimary); typeface = Typeface.DEFAULT_BOLD
        })
        if (subtitle.isNotEmpty()) textBlock.addView(TextView(this).apply {
            text = subtitle; textSize = 11f; setTextColor(theme.textSecondary)
            setPadding(0, dp(2), 0, 0); setLineSpacing(0f, 1.3f)
        })
        row.addView(textBlock)
        return row
    }

    private fun colorDot(color: Int): View = android.view.View(this).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(color); setStroke(dp(1), 0x33000000)
        }
        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { setMargins(0, 0, dp(8), 0) }
    }

    private fun label(text: String, color: Int) = TextView(this).apply {
        this.text = text; textSize = 13f; setTextColor(color); setPadding(0, 0, dp(6), 0)
    }

    private fun divider() = android.view.View(this).apply {
        setBackgroundColor(if (settings.darkMode) 0xFF2A2A2A.toInt() else 0xFFF0F0F0.toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { marginStart = dp(16) }
    }

    private fun cardBg() = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(theme.bgCard) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}

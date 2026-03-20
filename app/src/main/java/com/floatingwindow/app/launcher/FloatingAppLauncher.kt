package com.floatingwindow.app.launcher

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.*
import com.floatingwindow.app.manager.FloatingWindowManager
import com.floatingwindow.app.model.WindowContentType
import kotlinx.coroutines.*

/**
 * FloatingAppLauncher
 *
 * An overlay panel (not a floating *window*) that sits above everything.
 * Shows:  Search bar → Favorites row → All Apps grid
 * Tapping an app opens it as a floating window via FloatingWindowManager.
 * Long-press toggles favorite.
 */
class FloatingAppLauncher(
    private val context: Context,
    private val windowManager: WindowManager,
    private val floatingWM: FloatingWindowManager,
    private val repo: AppLauncherRepository,
    private val onDismiss: () -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var rootView: View
    private lateinit var searchField: EditText
    private lateinit var favRow: LinearLayout
    private lateinit var appGrid: GridLayout
    private lateinit var loadingText: TextView
    private lateinit var emptyText: TextView

    private var allApps: List<InstalledApp> = emptyList()
    private var query = ""

    val layoutParams: WindowManager.LayoutParams = buildLp()

    // ── Build overlay ─────────────────────────────────────────────────────────
    fun buildView(): View {
        rootView = buildPanel()
        loadApps()
        return rootView
    }

    private fun buildPanel(): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xF8FFFFFF.toInt())
            // Rounded top corners via programmatic outline
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height + dp(20), dp(20).toFloat())
                }
            }
            clipToOutline = true
            elevation = dp(24).toFloat()
        }

        // ── Drag handle ───────────────────────────────────────────────────────
        val handle = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(6))
        }
        val handleBar = View(context).apply {
            setBackgroundColor(0xFFCCCCCC.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(4)).apply {
                topMargin = dp(2); bottomMargin = dp(2)
            }
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(2).toFloat()
                setColor(0xFFCCCCCC.toInt())
            }
        }
        handle.addView(handleBar)

        // ── Header row ────────────────────────────────────────────────────────
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        val titleTv = TextView(context).apply {
            text = "App Launcher"
            textSize = 16f
            setTextColor(0xFF1A1A1A.toInt())
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 18f
            setTextColor(0xFF666666.toInt())
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener { dismiss() }
        }
        header.addView(titleTv)
        header.addView(closeBtn)

        // ── Search bar ────────────────────────────────────────────────────────
        val searchWrap = FrameLayout(context).apply {
            setPadding(dp(14), dp(4), dp(14), dp(10))
        }
        searchField = EditText(context).apply {
            hint = "🔍  Search apps..."
            setHintTextColor(0xFF999999.toInt())
            setTextColor(0xFF1A1A1A.toInt())
            textSize = 13f
            setPadding(dp(14), dp(10), dp(14), dp(10))
            isSingleLine = true
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(0xFFF2F2F2.toInt())
                setStroke(dp(1), 0xFFE0E0E0.toInt())
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    query = s?.toString() ?: ""
                    renderApps()
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        searchWrap.addView(searchField, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))

        // ── Favorites strip ───────────────────────────────────────────────────
        val favHeader = TextView(context).apply {
            text = "  ⭐ FAVORITES"
            textSize = 10f
            setTextColor(0xFF999999.toInt())
            letterSpacing = 0.1f
            setPadding(dp(16), dp(4), dp(16), dp(4))
        }
        val favScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }
        favRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        favScroll.addView(favRow)

        val favDivider = View(context).apply {
            setBackgroundColor(0xFFF0F0F0.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
        }

        // ── All apps section ──────────────────────────────────────────────────
        val allAppsHeader = TextView(context).apply {
            text = "  ALL APPS"
            textSize = 10f
            setTextColor(0xFF999999.toInt())
            letterSpacing = 0.1f
            setPadding(dp(16), dp(8), dp(16), dp(4))
        }

        loadingText = TextView(context).apply {
            text = "Loading apps..."
            textSize = 13f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            visibility = View.VISIBLE
        }
        emptyText = TextView(context).apply {
            text = "No apps found"
            textSize = 13f
            setTextColor(0xFF999999.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            visibility = View.GONE
        }

        appGrid = GridLayout(context).apply {
            columnCount = 4
            setPadding(dp(8), dp(4), dp(8), dp(16))
        }

        val scrollContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        scrollContent.addView(allAppsHeader)
        scrollContent.addView(loadingText)
        scrollContent.addView(emptyText)
        scrollContent.addView(appGrid)

        val scrollView = ScrollView(context).apply {
            addView(scrollContent)
        }

        // ── Assemble ──────────────────────────────────────────────────────────
        root.addView(handle)
        root.addView(header)
        root.addView(searchWrap)
        root.addView(favHeader)
        root.addView(favScroll)
        root.addView(favDivider)
        root.addView(scrollView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        return root
    }

    // ── Load apps async ───────────────────────────────────────────────────────
    private fun loadApps() {
        scope.launch {
            allApps = repo.loadApps()
            loadingText.visibility = View.GONE
            renderApps()
            renderFavorites()
        }
    }

    // ── Render filtered grid ──────────────────────────────────────────────────
    private fun renderApps() {
        appGrid.removeAllViews()
        val filtered = if (query.isBlank()) allApps
        else allApps.filter { it.label.contains(query, ignoreCase = true) }

        emptyText.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE

        filtered.forEach { app -> appGrid.addView(buildAppCell(app, compact = false)) }
    }

    // ── Render favorites strip ────────────────────────────────────────────────
    private fun renderFavorites() {
        favRow.removeAllViews()
        val favs = allApps.filter { it.isFavorite }
        if (favs.isEmpty()) {
            val hint = TextView(context).apply {
                text = "Long-press any app to add favorites"
                textSize = 11f
                setTextColor(0xFFAAAAAA.toInt())
                setPadding(dp(6), 0, 0, 0)
            }
            favRow.addView(hint)
        } else {
            favs.forEach { app -> favRow.addView(buildAppCell(app, compact = true)) }
        }
    }

    // ── Build one app cell ────────────────────────────────────────────────────
    private fun buildAppCell(app: InstalledApp, compact: Boolean): View {
        val cellSize = if (compact) dp(64) else dp(78)
        val iconSize = if (compact) dp(40) else dp(46)

        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = if (compact)
                LinearLayout.LayoutParams(cellSize, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(4), 0, dp(4), 0) }
            else {
                val spec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                GridLayout.LayoutParams(spec, spec).apply {
                    width  = 0; height = GridLayout.LayoutParams.WRAP_CONTENT
                    setMargins(dp(2), dp(4), dp(2), dp(4))
                }
            }
            // Ripple touch feedback
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x220067C0),
                null, null
            )
            isClickable  = true
            isFocusable  = true
            setPadding(dp(4), dp(6), dp(4), dp(6))
        }

        // Icon
        val iconView = ImageView(context).apply {
            setImageDrawable(app.icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        // Fav star badge
        val iconFrame = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        iconFrame.addView(iconView)
        if (app.isFavorite) {
            val star = TextView(context).apply {
                text = "⭐"
                textSize = 9f
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.END
                )
            }
            iconFrame.addView(star)
        }

        // Label
        val label = TextView(context).apply {
            text = app.label
            textSize = if (compact) 9f else 10f
            setTextColor(0xFF1A1A1A.toInt())
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }

        cell.addView(iconFrame)
        cell.addView(label)

        // ── Click: open as floating window ────────────────────────────────────
        cell.setOnClickListener {
            openAsFloatingWindow(app)
        }

        // ── Long press: toggle favorite ───────────────────────────────────────
        cell.setOnLongClickListener {
            val nowFav = repo.toggleFavorite(app.packageName)
            // Rebuild list with updated fav state
            allApps = allApps.map {
                if (it.packageName == app.packageName) it.copy(isFavorite = nowFav) else it
            }
            val msg = if (nowFav) "⭐ Added to favorites" else "Removed from favorites"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            renderApps()
            renderFavorites()
            true
        }

        return cell
    }

    // ── Open an installed app as a floating WebView/Activity window ───────────
    private fun openAsFloatingWindow(app: InstalledApp) {
        // We open it as a BLANK window that wraps the app's launch intent
        // The window title shows the app name and the content is an ActivityView stub.
        // On real devices the app opens in a floating overlay via ActivityEmbedding or
        // we fall back to a "shortcut" window that launches the app normally.
        floatingWM.createWindow(
            title       = app.label,
            contentType = com.floatingwindow.app.model.WindowContentType.BLANK
        )
        // Actually launch the app so it runs (system handles it separately)
        scope.launch(Dispatchers.Main) {
            repo.launchApp(app.packageName)
        }
        dismiss()
    }

    // ── Dismiss with animation ────────────────────────────────────────────────
    fun dismiss() {
        rootView.animate()
            .translationY(rootView.height.toFloat())
            .alpha(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction { onDismiss() }
            .start()
    }

    fun animateIn() {
        rootView.translationY = rootView.height.toFloat().coerceAtLeast(dp(400).toFloat())
        rootView.alpha = 0f
        rootView.animate()
            .translationY(0f).alpha(1f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun destroy() { scope.cancel() }

    // ── WindowManager layout params ───────────────────────────────────────────
    private fun buildLp(): WindowManager.LayoutParams {
        val screenH = context.resources.displayMetrics.heightPixels
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (screenH * 0.78f).toInt(),
            0, 0,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.START }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

package com.floatingwindow.app.virtual.apps

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.floatingwindow.app.virtual.model.VAppType
import java.text.SimpleDateFormat
import java.util.*

/**
 * VAppFactory — creates the content View for each virtual app.
 * All apps are real, functional mini-applications.
 */
object VAppFactory {

    fun build(context: Context, type: VAppType): View = when (type) {
        VAppType.NOTEPAD    -> buildNotepad(context)
        VAppType.CALCULATOR -> buildCalculator(context)
        VAppType.TERMINAL   -> buildTerminal(context)
        VAppType.FILE_MGR   -> buildFileManager(context)
        VAppType.SETTINGS   -> buildSettings(context)
        VAppType.BROWSER    -> buildBrowser(context)
        VAppType.PAINT      -> buildPaint(context)
        VAppType.CLOCK      -> buildClock(context)
        VAppType.BLANK      -> buildBlank(context)
    }

    // ── NOTEPAD ───────────────────────────────────────────────────────────────
    private fun buildNotepad(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Toolbar
        val toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF5F5F5.toInt())
            setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,4), dp(ctx,4))
        }
        val wordCount = TextView(ctx).apply {
            text = "0 words"; textSize = 9f; setTextColor(0xFF888888.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        listOf("B","I","U").forEach { fmt ->
            val btn = TextView(ctx).apply {
                text = fmt; textSize = 11f
                setTextColor(0xFF333333.toInt())
                setPadding(dp(ctx,6), dp(ctx,2), dp(ctx,6), dp(ctx,2))
                background = roundRect(ctx, 0xFFE8E8E8.toInt(), dp(ctx,4))
                typeface = if (fmt == "B") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            toolbar.addView(btn)
        }
        toolbar.addView(wordCount)

        val edit = EditText(ctx).apply {
            hint = "Start typing…"
            setHintTextColor(0xFFAAAAAA.toInt())
            setTextColor(0xFF1A1A1A.toInt())
            background = null
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            isSingleLine = false
            setPadding(dp(ctx,12), dp(ctx,10), dp(ctx,12), dp(ctx,10))
            setLineSpacing(dp(ctx,3).toFloat(), 1f)
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {
                    val words = s?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }?.size ?: 0
                    wordCount.text = "$words word${if (words != 1) "s" else ""}"
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }

        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(View(ctx).apply { setBackgroundColor(0xFFE8E8E8.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))
        root.addView(ScrollView(ctx).apply {
            addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ── CALCULATOR ────────────────────────────────────────────────────────────
    private fun buildCalculator(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1C1C1E.toInt())
        }

        val history = TextView(ctx).apply {
            textSize = 12f; setTextColor(0xFF8E8E93.toInt())
            gravity = Gravity.END; setPadding(dp(ctx,16), dp(ctx,8), dp(ctx,16), 0)
        }
        val display = TextView(ctx).apply {
            text = "0"; textSize = 36f; setTextColor(Color.WHITE)
            gravity = Gravity.END; setPadding(dp(ctx,16), dp(ctx,4), dp(ctx,16), dp(ctx,12))
            typeface = Typeface.DEFAULT_BOLD
        }

        root.addView(history, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(display, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(View(ctx).apply { setBackgroundColor(0xFF3A3A3C.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))

        var acc = 0.0; var op = ""; var cur = "0"; var fresh = false

        val rows = listOf(
            listOf("AC" to 0xFF3A3A3C, "±" to 0xFF3A3A3C, "%" to 0xFF3A3A3C, "÷" to 0xFFFF9F0A),
            listOf("7"  to 0xFF2C2C2E, "8"  to 0xFF2C2C2E, "9"  to 0xFF2C2C2E, "×" to 0xFFFF9F0A),
            listOf("4"  to 0xFF2C2C2E, "5"  to 0xFF2C2C2E, "6"  to 0xFF2C2C2E, "−" to 0xFFFF9F0A),
            listOf("1"  to 0xFF2C2C2E, "2"  to 0xFF2C2C2E, "3"  to 0xFF2C2C2E, "+" to 0xFFFF9F0A),
            listOf("0"  to 0xFF2C2C2E, "."  to 0xFF2C2C2E, "⌫" to 0xFF2C2C2E, "=" to 0xFFFF9F0A)
        )

        val grid = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row ->
            val rowL = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(ctx,4), dp(ctx,2), dp(ctx,4), dp(ctx,2))
            }
            row.forEach { (label, bgColor) ->
                val btn = TextView(ctx).apply {
                    text = label; textSize = 18f
                    setTextColor(Color.WHITE); gravity = Gravity.CENTER
                    background = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(ctx,8).toFloat()
                        setColor(bgColor.toInt())
                    }
                    setPadding(0, dp(ctx,12), 0, dp(ctx,12))
                    setOnClickListener {
                        when (label) {
                            "AC" -> { acc=0.0; op=""; cur="0"; fresh=false; history.text="" }
                            "±"  -> cur = if (cur.startsWith("-")) cur.drop(1) else "-$cur"
                            "%"  -> cur = (cur.toDoubleOrNull()?.div(100) ?: 0.0).toString()
                            "⌫"  -> cur = if (cur.length > 1) cur.dropLast(1) else "0"
                            "."  -> if (!cur.contains(".")) cur += "."
                            "÷","×","−","+" -> { acc=cur.toDoubleOrNull()?:0.0; op=label; fresh=true; history.text="$cur $label" }
                            "=" -> {
                                val b = cur.toDoubleOrNull()?:0.0
                                val r = when(op) { "÷"->if(b!=0.0)acc/b else 0.0; "×"->acc*b; "−"->acc-b; "+"->acc+b; else->b }
                                history.text = "$acc $op $b ="
                                cur = if (r == r.toLong().toDouble()) r.toLong().toString() else "%.8g".format(r)
                                op=""; fresh=false
                            }
                            else -> { if(fresh){cur=label;fresh=false} else cur=if(cur=="0")label else cur+label }
                        }
                        display.text = cur
                    }
                }
                rowL.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(ctx,2),0,dp(ctx,2),0) })
            }
            grid.addView(rowL, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        root.addView(grid, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ── TERMINAL ──────────────────────────────────────────────────────────────
    private fun buildTerminal(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D1117.toInt())
        }
        val outputScroll = ScrollView(ctx)
        val output = TextView(ctx).apply {
            textSize = 10.5f; typeface = Typeface.MONOSPACE
            setTextColor(0xFFCDD9E5.toInt()); setLineSpacing(dp(ctx,2).toFloat(), 1f)
            setPadding(dp(ctx,10), dp(ctx,8), dp(ctx,10), dp(ctx,4))
            text = buildString {
                appendLine("\u001B[32mFloating Terminal v2.0\u001B[0m")
                appendLine("Type 'help' for available commands\n")
            }
        }
        outputScroll.addView(output)

        // Command history
        val cmdHistory = mutableListOf<String>(); var histIdx = -1

        val inputRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF161B22.toInt()); setPadding(dp(ctx,10), dp(ctx,6), dp(ctx,6), dp(ctx,6))
        }
        val prompt = TextView(ctx).apply {
            text = "❯ "; textSize = 11f; typeface = Typeface.MONOSPACE
            setTextColor(0xFF3FB950.toInt())
        }
        val input = EditText(ctx).apply {
            hint = "command…"; setHintTextColor(0xFF484F58.toInt())
            setTextColor(0xFFCDD9E5.toInt()); background = null
            textSize = 11f; typeface = Typeface.MONOSPACE; isSingleLine = true
        }

        val commandMap = mapOf(
            "help"    to "Commands:\n  ls, pwd, echo, date, clear, cal, uname, whoami, history",
            "pwd"     to "/sdcard/FloatingWindows",
            "whoami"  to "user@floating-os",
            "uname"   to "FloatingOS 1.0 Android ${android.os.Build.VERSION.RELEASE}",
            "cal"     to SimpleDateFormat("MMMM yyyy", Locale.US).format(Date()),
            "ls"      to "documents/  downloads/  pictures/\nmusic/       notes.md    readme.txt",
            "date"    to SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy", Locale.US).format(Date())
        )

        fun appendOutput(text: String) {
            output.append("$text\n")
            outputScroll.post { outputScroll.fullScroll(View.FOCUS_DOWN) }
        }

        input.setOnEditorActionListener { _, _, _ ->
            val cmd = input.text.toString().trim()
            if (cmd.isNotEmpty()) { cmdHistory.add(0, cmd); histIdx = -1 }
            appendOutput("❯ $cmd")
            when {
                cmd == "clear"          -> { output.text = "" }
                cmd == "history"        -> appendOutput(cmdHistory.takeLast(10).reversed().joinToString("\n"))
                cmd.startsWith("echo ") -> appendOutput(cmd.removePrefix("echo "))
                commandMap[cmd] != null -> appendOutput(commandMap[cmd]!!)
                cmd.isEmpty()           -> {}
                else                    -> appendOutput("bash: $cmd: command not found")
            }
            input.setText(""); true
        }

        inputRow.addView(prompt)
        inputRow.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(outputScroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(View(ctx).apply { setBackgroundColor(0xFF30363D.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))
        root.addView(inputRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    // ── FILE MANAGER ──────────────────────────────────────────────────────────
    private fun buildFileManager(ctx: Context): View {
        val entries = listOf(
            Triple("📁","Documents","Folder • 12 items"),
            Triple("📁","Downloads","Folder • 8 items"),
            Triple("📁","Pictures","Folder • 34 items"),
            Triple("📁","Music","Folder • 156 items"),
            Triple("📄","README.md","2.1 KB • Text"),
            Triple("📄","notes.txt","4.7 KB • Text"),
            Triple("🖼️","wallpaper.jpg","3.2 MB • Image"),
            Triple("🎵","playlist.mp3","18 MB • Audio"),
            Triple("📦","archive.zip","124 MB • Archive"),
            Triple("⚙️","config.json","8.4 KB • JSON")
        )
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE)
        }
        // Path bar
        val pathBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF8F8F8.toInt()); setPadding(dp(ctx,10), dp(ctx,6), dp(ctx,10), dp(ctx,6))
        }
        val pathText = TextView(ctx).apply {
            text = "🏠  /Home"; textSize = 11f; setTextColor(0xFF0067C0.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        pathBar.addView(pathText)
        root.addView(pathBar)
        root.addView(View(ctx).apply { setBackgroundColor(0xFFEEEEEE.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))

        val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        entries.forEach { (icon, name, meta) ->
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(ctx,12), dp(ctx,8), dp(ctx,12), dp(ctx,8))
                isClickable = true; isFocusable = true
                foreground = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x220067C0), null, null)
            }
            val iconV = TextView(ctx).apply { text = icon; textSize = 20f }
            val textBlock = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(ctx,10), 0, 0, 0)
            }
            textBlock.addView(TextView(ctx).apply { text = name; textSize = 13f; setTextColor(0xFF1A1A1A.toInt()); typeface = Typeface.DEFAULT_BOLD })
            textBlock.addView(TextView(ctx).apply { text = meta; textSize = 10f; setTextColor(0xFF888888.toInt()) })
            row.addView(iconV)
            row.addView(textBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            list.addView(row)
            list.addView(View(ctx).apply { setBackgroundColor(0xFFF5F5F5.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))
        }
        root.addView(ScrollView(ctx).apply { addView(list) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ── SETTINGS ──────────────────────────────────────────────────────────────
    private fun buildSettings(ctx: Context): View {
        val sections = listOf(
            "Display" to listOf("Dark Mode" to false, "Night Light" to true, "Auto-brightness" to true),
            "Sound"   to listOf("Notifications" to true, "Haptic feedback" to true),
            "Privacy" to listOf("Location" to false, "Camera" to true, "Microphone" to false),
            "System"  to listOf("Auto-update" to true, "Developer mode" to false)
        )
        val root = ScrollView(ctx)
        val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFFF5F5F5.toInt()) }
        sections.forEach { (section, items) ->
            val header = TextView(ctx).apply {
                text = section.uppercase(); textSize = 10f; setTextColor(0xFF0067C0.toInt())
                setPadding(dp(ctx,16), dp(ctx,14), dp(ctx,16), dp(ctx,6))
                letterSpacing = 0.1f
            }
            content.addView(header)
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.WHITE)
                background = roundRect(ctx, Color.WHITE, dp(ctx,8))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    .apply { setMargins(dp(ctx,12), 0, dp(ctx,12), dp(ctx,8)) }
                layoutParams = lp
            }
            items.forEachIndexed { i, (label, default) ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(ctx,14), dp(ctx,12), dp(ctx,14), dp(ctx,12))
                }
                val lbl = TextView(ctx).apply {
                    text = label; textSize = 13f; setTextColor(0xFF1A1A1A.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val toggle = Switch(ctx).apply { isChecked = default }
                row.addView(lbl); row.addView(toggle)
                card.addView(row)
                if (i < items.size - 1) card.addView(View(ctx).apply { setBackgroundColor(0xFFF0F0F0.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)).apply { marginStart = dp(ctx,14) })
            }
            content.addView(card)
        }
        root.addView(content); return root
    }

    // ── BROWSER (simple URL-bar style) ────────────────────────────────────────
    private fun buildBrowser(ctx: Context): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val urlBar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF2F2F2.toInt()); setPadding(dp(ctx,8), dp(ctx,6), dp(ctx,8), dp(ctx,6))
        }
        val urlField = EditText(ctx).apply {
            hint = "Search or type URL"; setHintTextColor(0xFFAAAAAA.toInt())
            setTextColor(0xFF1A1A1A.toInt()); background = roundRect(ctx, Color.WHITE, dp(ctx,20))
            setPadding(dp(ctx,14), dp(ctx,6), dp(ctx,14), dp(ctx,6)); textSize = 12f; isSingleLine = true
        }
        val goBtn = TextView(ctx).apply {
            text = "→"; textSize = 18f; setTextColor(0xFF0067C0.toInt()); setPadding(dp(ctx,10), 0, dp(ctx,4), 0)
        }
        urlBar.addView(urlField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        urlBar.addView(goBtn)

        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(0xFFFAFAFA.toInt())
        }
        val icon = TextView(ctx).apply { text = "🌐"; textSize = 52f; gravity = Gravity.CENTER }
        val msg  = TextView(ctx).apply { text = "Virtual Browser"; textSize = 16f; setTextColor(0xFF333333.toInt()); gravity = Gravity.CENTER; setPadding(0, dp(ctx,8), 0, dp(ctx,4)) }
        val sub  = TextView(ctx).apply { text = "Type a URL above"; textSize = 12f; setTextColor(0xFF888888.toInt()); gravity = Gravity.CENTER }
        content.addView(icon); content.addView(msg); content.addView(sub)

        root.addView(urlBar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(View(ctx).apply { setBackgroundColor(0xFFDDDDDD.toInt()) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1)))
        root.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ── PAINT ─────────────────────────────────────────────────────────────────
    private fun buildPaint(ctx: Context): View {
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val toolbar = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFFF0F0F0.toInt()); setPadding(dp(ctx,8), dp(ctx,4), dp(ctx,8), dp(ctx,4))
        }
        var currentColor = Color.BLACK
        val colors = listOf(Color.BLACK, Color.RED, 0xFF0067C0.toInt(), 0xFF00AA44.toInt(), Color.YELLOW, Color.WHITE)
        colors.forEach { c ->
            val dot = View(ctx).apply {
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(c); setStroke(dp(ctx,1), 0xFF888888.toInt())
                }
                layoutParams = LinearLayout.LayoutParams(dp(ctx,22), dp(ctx,22)).apply { setMargins(dp(ctx,2),0,dp(ctx,2),0) }
                setOnClickListener { currentColor = c }
            }
            toolbar.addView(dot)
        }
        val clearBtn = TextView(ctx).apply {
            text = "Clear"; textSize = 11f; setTextColor(0xFFC42B1C.toInt())
            setPadding(dp(ctx,10), dp(ctx,2), dp(ctx,4), dp(ctx,2))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { gravity = Gravity.END }
        }
        toolbar.addView(clearBtn)

        val canvas = object : View(ctx) {
            val paths = mutableListOf<android.graphics.Path>()
            val paints = mutableListOf<android.graphics.Paint>()
            var curPath: android.graphics.Path? = null
            val bgPaint = android.graphics.Paint().apply { color = Color.WHITE }

            init {
                setOnTouchListener { _, e ->
                    when (e.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            curPath = android.graphics.Path().also { paths.add(it); it.moveTo(e.x, e.y) }
                            paints.add(android.graphics.Paint().apply { color = currentColor; strokeWidth = dp(ctx,3).toFloat(); style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; isAntiAlias = true })
                        }
                        android.view.MotionEvent.ACTION_MOVE -> { curPath?.lineTo(e.x, e.y); invalidate() }
                        android.view.MotionEvent.ACTION_UP   -> { curPath = null; invalidate() }
                    }
                    true
                }
                clearBtn.setOnClickListener { paths.clear(); paints.clear(); invalidate() }
            }
            override fun onDraw(c: android.graphics.Canvas) {
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
                paths.forEachIndexed { i, p -> c.drawPath(p, paints.getOrNull(i) ?: return@forEachIndexed) }
            }
        }

        root.addView(toolbar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(canvas, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return root
    }

    // ── CLOCK ─────────────────────────────────────────────────────────────────
    private fun buildClock(ctx: Context): View {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(0xFF1C1C1E.toInt())
        }
        val timeTv = TextView(ctx).apply {
            textSize = 48f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.05f
        }
        val dateTv = TextView(ctx).apply {
            textSize = 14f; setTextColor(0xFF8E8E93.toInt()); gravity = Gravity.CENTER
            setPadding(0, dp(ctx,4), 0, dp(ctx,20))
        }
        val tzTv = TextView(ctx).apply {
            textSize = 11f; setTextColor(0xFF636366.toInt()); gravity = Gravity.CENTER
        }
        // Live tick
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val tick = object : Runnable {
            override fun run() {
                val now = Date()
                timeTv.text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
                dateTv.text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(now)
                tzTv.text   = "UTC${SimpleDateFormat("Z", Locale.getDefault()).format(now)}"
                handler.postDelayed(this, 1000)
            }
        }
        timeTv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { handler.post(tick) }
            override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacks(tick) }
        })
        root.addView(timeTv); root.addView(dateTv); root.addView(tzTv)
        return root
    }

    // ── BLANK ────────────────────────────────────────────────────────────────
    private fun buildBlank(ctx: Context): View =
        FrameLayout(ctx).apply {
            setBackgroundColor(Color.WHITE)
            addView(TextView(ctx).apply {
                text = "🪟\nEmpty Window"; textSize = 14f
                setTextColor(0xFFBBBBBB.toInt()); gravity = Gravity.CENTER
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    private fun roundRect(ctx: Context, color: Int, radius: Int) =
        android.graphics.drawable.GradientDrawable().apply { cornerRadius = radius.toFloat(); setColor(color) }
}

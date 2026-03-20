package com.floatingwindow.app.ui

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.floatingwindow.app.model.WindowContentType

/**
 * Factory that creates the inner content view for each window type.
 * Extend this to add more app types.
 */
object WindowContentFactory {

    fun create(context: Context, type: WindowContentType, title: String): View {
        return when (type) {
            WindowContentType.NOTEPAD -> buildNotepad(context)
            WindowContentType.CALCULATOR -> buildCalculator(context)
            WindowContentType.TERMINAL -> buildTerminal(context)
            WindowContentType.SETTINGS -> buildSettings(context)
            WindowContentType.FILE_BROWSER -> buildFileBrowser(context)
            WindowContentType.BLANK -> buildBlank(context, title)
        }
    }

    // ─── NOTEPAD ───────────────────────────────────────────────────────────────

    private fun buildNotepad(context: Context): View {
        val editText = EditText(context).apply {
            hint = "Start typing here…"
            setHintTextColor(0xFF999999.toInt())
            setTextColor(0xFF1A1A1A.toInt())
            background = null
            setPadding(dp(context, 10), dp(context, 8), dp(context, 10), dp(context, 8))
            inputType = InputType.TYPE_CLASS_TEXT or
                    InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            textSize = 13f
            gravity = Gravity.TOP or Gravity.START
            isSingleLine = false
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }

        val scroll = ScrollView(context).apply {
            addView(editText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        return scroll
    }

    // ─── CALCULATOR ────────────────────────────────────────────────────────────

    private fun buildCalculator(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 8))
        }

        val display = TextView(context).apply {
            text = "0"
            textSize = 28f
            setTextColor(0xFF1A1A1A.toInt())
            gravity = Gravity.END
            setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 4))
            setBackgroundColor(0xFFF8F8F8.toInt())
            typeface = Typeface.MONOSPACE
        }
        root.addView(display, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(context, 6) })

        var accumulator = 0.0
        var operator = ""
        var newInput = true
        var currentInput = "0"

        fun updateDisplay(value: String) {
            currentInput = value
            display.text = value
        }

        val buttons = listOf(
            listOf("C", "±", "%", "÷"),
            listOf("7", "8", "9", "×"),
            listOf("4", "5", "6", "−"),
            listOf("1", "2", "3", "+"),
            listOf("0", ".", "⌫", "=")
        )

        val operatorBg = 0xFF0067C0.toInt()
        val funcBg     = 0xFFD0D0D0.toInt()
        val numBg      = 0xFFEEEEEE.toInt()
        val equalsBg   = 0xFF003E92.toInt()

        buttons.forEach { row ->
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            row.forEach { label ->
                val btn = Button(context).apply {
                    text = label
                    textSize = 14f
                    setTextColor(if (label in listOf("÷","×","−","+","=")) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                    setBackgroundColor(when (label) {
                        "=", "÷","×","−","+" -> if (label == "=") equalsBg else operatorBg
                        "C", "±", "%" -> funcBg
                        else -> numBg
                    })
                    setPadding(0, dp(context, 4), 0, dp(context, 4))
                    setOnClickListener {
                        when (label) {
                            "C" -> { accumulator = 0.0; operator = ""; newInput = true; updateDisplay("0") }
                            "±" -> updateDisplay(if (currentInput.startsWith("-")) currentInput.drop(1) else "-$currentInput")
                            "%" -> updateDisplay((currentInput.toDoubleOrNull()?.div(100) ?: 0.0).toString())
                            "⌫" -> updateDisplay(if (currentInput.length > 1) currentInput.dropLast(1) else "0")
                            "." -> if (!currentInput.contains(".")) updateDisplay("$currentInput.")
                            "÷","×","−","+" -> {
                                accumulator = currentInput.toDoubleOrNull() ?: 0.0
                                operator = label; newInput = true
                            }
                            "=" -> {
                                val b = currentInput.toDoubleOrNull() ?: 0.0
                                val result = when (operator) {
                                    "÷" -> if (b != 0.0) accumulator / b else Double.NaN
                                    "×" -> accumulator * b
                                    "−" -> accumulator - b
                                    "+" -> accumulator + b
                                    else -> b
                                }
                                val resultStr = if (result == result.toLong().toDouble()) result.toLong().toString() else result.toString()
                                updateDisplay(resultStr)
                                operator = ""; newInput = true
                            }
                            else -> {
                                if (newInput) { updateDisplay(label); newInput = false }
                                else updateDisplay(if (currentInput == "0") label else currentInput + label)
                            }
                        }
                    }
                }
                rowLayout.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(context,2), dp(context,2), dp(context,2), dp(context,2))
                })
            }
            root.addView(rowLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        return root
    }

    // ─── TERMINAL ──────────────────────────────────────────────────────────────

    private fun buildTerminal(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0C0C0C.toInt())
            setPadding(dp(context, 6), dp(context, 6), dp(context, 6), dp(context, 6))
        }

        val output = TextView(context).apply {
            text = "Floating Terminal v1.0\nType 'help' for commands\n\n> "
            setTextColor(0xFFCCCCCC.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setLineSpacing(dp(context, 2).toFloat(), 1f)
        }

        val scroll = ScrollView(context).apply {
            addView(output)
        }

        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(context, 4), 0, 0)
        }

        val prompt = TextView(context).apply {
            text = "> "
            setTextColor(0xFF4EC9B0.toInt())
            textSize = 11f
            typeface = Typeface.MONOSPACE
        }

        val inputField = EditText(context).apply {
            hint = "command..."
            setHintTextColor(0xFF555555.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            background = null
            textSize = 11f
            typeface = Typeface.MONOSPACE
            isSingleLine = true
        }

        val helpMap = mapOf(
            "help" to "Commands: help, clear, date, echo [text], ls",
            "date" to java.util.Date().toString(),
            "ls" to "documents/  downloads/  pictures/  music/",
            "clear" to "__CLEAR__"
        )

        inputField.setOnEditorActionListener { _, _, _ ->
            val cmd = inputField.text.toString().trim()
            val response = when {
                cmd.startsWith("echo ") -> cmd.removePrefix("echo ")
                helpMap.containsKey(cmd) -> helpMap[cmd]!!
                cmd.isEmpty() -> ""
                else -> "command not found: $cmd"
            }
            if (response == "__CLEAR__") {
                output.text = "> "
            } else {
                output.append("$cmd\n$response\n\n> ")
            }
            inputField.setText("")
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
            true
        }

        inputRow.addView(prompt)
        inputRow.addView(inputField, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(inputRow)
        return root
    }

    // ─── SETTINGS ──────────────────────────────────────────────────────────────

    private fun buildSettings(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8))
        }

        val settings = listOf(
            "Notifications" to true,
            "Dark Mode" to false,
            "Auto-save" to true,
            "Haptic Feedback" to true,
            "Always on Top" to false
        )

        settings.forEach { (name, default) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(context, 8), 0, dp(context, 8))
            }
            val label = TextView(context).apply {
                text = name
                setTextColor(0xFF1A1A1A.toInt())
                textSize = 13f
            }
            val toggle = Switch(context).apply {
                isChecked = default
            }
            row.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(toggle)
            root.addView(row)

            // Divider
            val div = View(context).apply {
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            root.addView(div, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)))
        }

        return ScrollView(context).apply { addView(root) }
    }

    // ─── FILE BROWSER ──────────────────────────────────────────────────────────

    private fun buildFileBrowser(context: Context): View {
        val items = listOf(
            "📁" to "Documents",
            "📁" to "Downloads",
            "📁" to "Pictures",
            "📁" to "Music",
            "📄" to "readme.txt",
            "📄" to "notes.md",
            "🖼️" to "wallpaper.jpg",
            "🎵" to "playlist.mp3"
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        items.forEach { (icon, name) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 10), dp(context, 7), dp(context, 10), dp(context, 7))
                setOnClickListener { /* open */ }
            }
            val iconView = TextView(context).apply {
                text = icon
                textSize = 18f
            }
            val label = TextView(context).apply {
                text = name
                setTextColor(0xFF1A1A1A.toInt())
                textSize = 13f
                setPadding(dp(context, 8), 0, 0, 0)
            }
            row.addView(iconView)
            row.addView(label)
            root.addView(row)

            val div = View(context).apply { setBackgroundColor(0xFFF0F0F0.toInt()) }
            root.addView(div, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1)))
        }

        return ScrollView(context).apply { addView(root) }
    }

    // ─── BLANK ─────────────────────────────────────────────────────────────────

    private fun buildBlank(context: Context, title: String): View {
        return TextView(context).apply {
            text = "📋 $title"
            setTextColor(0xFF999999.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
        }
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}

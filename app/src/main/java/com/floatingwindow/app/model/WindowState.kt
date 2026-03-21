package com.floatingwindow.app.model

import android.view.View

enum class WindowStatus {
    NORMAL, MINIMIZED, MAXIMIZED, CLOSED
}

enum class WindowContentType(val label: String, val emoji: String) {
    BLANK("Window",       "🪟"),
    NOTEPAD("Notepad",    "📝"),
    CALCULATOR("Calc",    "🧮"),
    FILE_BROWSER("Files", "📁"),
    TERMINAL("Terminal",  "💻"),
    SETTINGS("Settings",  "⚙️")
}

data class WindowState(
    val id: String,
    var title: String,
    val contentType: WindowContentType = WindowContentType.BLANK,
    var x: Int = 80,
    var y: Int = 80,
    var width: Int = 360,
    var height: Int = 280,
    var savedX: Int = 80,
    var savedY: Int = 80,
    var savedWidth: Int = 360,
    var savedHeight: Int = 280,
    var status: WindowStatus = WindowStatus.NORMAL,
    var zIndex: Int = 0,
    var isFocused: Boolean = false,
    var rootView: View? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isVisible get() = status == WindowStatus.NORMAL || status == WindowStatus.MAXIMIZED
    val isMinimized get() = status == WindowStatus.MINIMIZED
    val isMaximized get() = status == WindowStatus.MAXIMIZED
}

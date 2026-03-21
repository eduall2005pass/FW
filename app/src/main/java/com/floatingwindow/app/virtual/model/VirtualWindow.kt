package com.floatingwindow.app.virtual.model

import android.view.View

// ── Window state ──────────────────────────────────────────────────────────────
enum class VWState { NORMAL, MINIMIZED, MAXIMIZED, CLOSED }

// ── App type running inside the window ────────────────────────────────────────
enum class VAppType(val title: String, val emoji: String) {
    NOTEPAD    ("Notepad",    "📝"),
    CALCULATOR ("Calculator", "🧮"),
    TERMINAL   ("Terminal",   "💻"),
    FILE_MGR   ("Files",      "📁"),
    SETTINGS   ("Settings",   "⚙️"),
    BROWSER    ("Browser",    "🌐"),
    PAINT      ("Paint",      "🎨"),
    CLOCK      ("Clock",      "🕐"),
    BLANK      ("Window",     "🪟")
}

data class VirtualWindow(
    val id: String,

    // Display
    var title: String,
    val appType: VAppType,

    // Geometry — in pixels relative to desktop surface
    var x: Int = 40,
    var y: Int = 40,
    var width: Int = 380,
    var height: Int = 280,

    // Pre-maximize saved geometry
    var savedX: Int = 40,
    var savedY: Int = 40,
    var savedWidth: Int = 380,
    var savedHeight: Int = 280,

    // Pre-snap saved geometry
    var snapSavedX: Int = 40,
    var snapSavedY: Int = 40,
    var snapSavedW: Int = 380,
    var snapSavedH: Int = 280,
    var isSnapped: Boolean = false,

    var state: VWState = VWState.NORMAL,
    var zIndex: Int = 0,
    var isFocused: Boolean = false,

    // Actual Android View managed by the engine
    var view: View? = null,

    val createdAt: Long = System.currentTimeMillis()
) {
    val isVisible  get() = state == VWState.NORMAL || state == VWState.MAXIMIZED
    val isMinimized get() = state == VWState.MINIMIZED
    val isMaximized get() = state == VWState.MAXIMIZED
}

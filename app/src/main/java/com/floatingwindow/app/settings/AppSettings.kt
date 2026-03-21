package com.floatingwindow.app.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * AppSettings — single source of truth for all user preferences.
 * Persisted in SharedPreferences, observed by SettingsActivity.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    companion object {
        const val APP_VERSION   = "1.0.0"
        const val APP_BUILD     = "2025.1"
        const val DEVELOPER     = "Siam"
        const val GITHUB_URL    = "https://github.com/siam"
    }

    // ── Theme ─────────────────────────────────────────────────────────────────
    var darkMode: Boolean
        get()  = prefs.getBoolean("dark_mode", false)
        set(v) { prefs.edit().putBoolean("dark_mode", v).apply(); notifyChanged() }

    var accentColor: Int
        get()  = prefs.getInt("accent_color", 0xFF0067C0.toInt())
        set(v) { prefs.edit().putInt("accent_color", v).apply(); notifyChanged() }

    var windowOpacity: Float
        get()  = prefs.getFloat("window_opacity", 1.0f)
        set(v) { prefs.edit().putFloat("window_opacity", v).apply(); notifyChanged() }

    var windowBlur: Boolean
        get()  = prefs.getBoolean("window_blur", true)
        set(v) { prefs.edit().putBoolean("window_blur", v).apply(); notifyChanged() }

    var windowShadow: Boolean
        get()  = prefs.getBoolean("window_shadow", true)
        set(v) { prefs.edit().putBoolean("window_shadow", v).apply(); notifyChanged() }

    var cornerRadius: Int
        get()  = prefs.getInt("corner_radius", 8)   // dp
        set(v) { prefs.edit().putInt("corner_radius", v).apply(); notifyChanged() }

    // ── Window behaviour ──────────────────────────────────────────────────────
    var snapEnabled: Boolean
        get()  = prefs.getBoolean("snap_enabled", true)
        set(v) { prefs.edit().putBoolean("snap_enabled", v).apply(); notifyChanged() }

    var rememberWindowPositions: Boolean
        get()  = prefs.getBoolean("remember_positions", true)
        set(v) { prefs.edit().putBoolean("remember_positions", v).apply(); notifyChanged() }

    var autoFocusNewWindow: Boolean
        get()  = prefs.getBoolean("auto_focus_new", true)
        set(v) { prefs.edit().putBoolean("auto_focus_new", v).apply(); notifyChanged() }

    var showTaskbar: Boolean
        get()  = prefs.getBoolean("show_taskbar", true)
        set(v) { prefs.edit().putBoolean("show_taskbar", v).apply(); notifyChanged() }

    var taskbarPosition: String
        get()  = prefs.getString("taskbar_pos", "bottom") ?: "bottom"
        set(v) { prefs.edit().putString("taskbar_pos", v).apply(); notifyChanged() }

    var defaultWindowMode: String
        get()  = prefs.getString("default_mode", "auto") ?: "auto"
        set(v) { prefs.edit().putString("default_mode", v).apply(); notifyChanged() }

    // ── Performance ───────────────────────────────────────────────────────────
    var animationsEnabled: Boolean
        get()  = prefs.getBoolean("animations", true)
        set(v) { prefs.edit().putBoolean("animations", v).apply(); notifyChanged() }

    var animationSpeed: String
        get()  = prefs.getString("anim_speed", "normal") ?: "normal"
        set(v) { prefs.edit().putString("anim_speed", v).apply(); notifyChanged() }

    var maxOpenWindows: Int
        get()  = prefs.getInt("max_windows", 8)
        set(v) { prefs.edit().putInt("max_windows", v).apply(); notifyChanged() }

    var lowMemoryMode: Boolean
        get()  = prefs.getBoolean("low_memory", false)
        set(v) { prefs.edit().putBoolean("low_memory", v).apply(); notifyChanged() }

    // ── Notifications ─────────────────────────────────────────────────────────
    var showNotification: Boolean
        get()  = prefs.getBoolean("show_notif", true)
        set(v) { prefs.edit().putBoolean("show_notif", v).apply(); notifyChanged() }

    var keepServiceAlive: Boolean
        get()  = prefs.getBoolean("keep_alive", true)
        set(v) { prefs.edit().putBoolean("keep_alive", v).apply(); notifyChanged() }

    // ── Privacy ───────────────────────────────────────────────────────────────
    var saveWindowHistory: Boolean
        get()  = prefs.getBoolean("save_history", true)
        set(v) { prefs.edit().putBoolean("save_history", v).apply(); notifyChanged() }

    var analyticsEnabled: Boolean
        get()  = prefs.getBoolean("analytics", false)
        set(v) { prefs.edit().putBoolean("analytics", v).apply(); notifyChanged() }

    // ── Change listener ───────────────────────────────────────────────────────
    private val listeners = mutableListOf<() -> Unit>()
    fun addChangeListener(l: () -> Unit)    { listeners.add(l) }
    fun removeChangeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged()             { listeners.forEach { it() } }

    // ── Reset ─────────────────────────────────────────────────────────────────
    fun resetAll() { prefs.edit().clear().apply(); notifyChanged() }
}

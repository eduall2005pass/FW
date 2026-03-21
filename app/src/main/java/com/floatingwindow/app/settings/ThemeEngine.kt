package com.floatingwindow.app.settings

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.Window

/**
 * ThemeEngine
 * Applies dark/light mode and accent color to any Activity or View.
 */
object ThemeEngine {

    data class Theme(
        // Backgrounds
        val bgPrimary:    Int,
        val bgSecondary:  Int,
        val bgCard:       Int,
        val bgInput:      Int,
        // Text
        val textPrimary:  Int,
        val textSecondary:Int,
        val textHint:     Int,
        // Accent
        val accent:       Int,
        val accentDark:   Int,
        // Window chrome
        val titleBarActive:   Int,
        val titleBarInactive: Int,
        val titleBarActiveText:   Int,
        val titleBarInactiveText: Int,
        val windowBorder: Int,
        val taskbarBg:    Int,
        val taskbarText:  Int,
        // Desktop
        val desktopBg:    Int,
        val desktopDot:   Int,
        // Status bar
        val statusBar:    Int
    )

    fun light(accent: Int = 0xFF0067C0.toInt()) = Theme(
        bgPrimary         = 0xFFF3F3F3.toInt(),
        bgSecondary       = 0xFFEBEBEB.toInt(),
        bgCard            = 0xFFFFFFFF.toInt(),
        bgInput           = 0xFFF2F2F2.toInt(),
        textPrimary       = 0xFF1A1A1A.toInt(),
        textSecondary     = 0xFF666666.toInt(),
        textHint          = 0xFFAAAAAA.toInt(),
        accent            = accent,
        accentDark        = darken(accent, 0.2f),
        titleBarActive    = accent,
        titleBarInactive  = 0xFFE8E8E8.toInt(),
        titleBarActiveText   = 0xFFFFFFFF.toInt(),
        titleBarInactiveText = 0xFF666666.toInt(),
        windowBorder      = 0xFFCCCCCC.toInt(),
        taskbarBg         = 0xF5F0F0F0.toInt(),
        taskbarText       = 0xFF1A1A1A.toInt(),
        desktopBg         = 0xFF1A1A2E.toInt(),
        desktopDot        = 0x15FFFFFF,
        statusBar         = accent
    )

    fun dark(accent: Int = 0xFF4A9EFF.toInt()) = Theme(
        bgPrimary         = 0xFF1E1E1E.toInt(),
        bgSecondary       = 0xFF2C2C2C.toInt(),
        bgCard            = 0xFF252525.toInt(),
        bgInput           = 0xFF333333.toInt(),
        textPrimary       = 0xFFE8E8E8.toInt(),
        textSecondary     = 0xFFAAAAAA.toInt(),
        textHint          = 0xFF666666.toInt(),
        accent            = accent,
        accentDark        = darken(accent, 0.2f),
        titleBarActive    = accent,
        titleBarInactive  = 0xFF2C2C2C.toInt(),
        titleBarActiveText   = 0xFFFFFFFF.toInt(),
        titleBarInactiveText = 0xFF888888.toInt(),
        windowBorder      = 0xFF3A3A3A.toInt(),
        taskbarBg         = 0xF2161616.toInt(),
        taskbarText       = 0xFFE0E0E0.toInt(),
        desktopBg         = 0xFF111111.toInt(),
        desktopDot        = 0x12FFFFFF,
        statusBar         = 0xFF1E1E1E.toInt()
    )

    fun getTheme(settings: AppSettings): Theme =
        if (settings.darkMode) dark(settings.accentColor)
        else light(settings.accentColor)

    fun applyToWindow(window: Window, theme: Theme) {
        window.statusBarColor     = theme.statusBar
        window.navigationBarColor = theme.bgPrimary
        window.decorView.setBackgroundColor(theme.bgPrimary)
    }

    private fun darken(color: Int, factor: Float): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[2] *= (1f - factor)
        return Color.HSVToColor(hsv)
    }

    // Preset accent colors
    val ACCENTS = listOf(
        "Windows Blue"  to 0xFF0067C0.toInt(),
        "Ocean"         to 0xFF006FD6.toInt(),
        "Violet"        to 0xFF7C3AED.toInt(),
        "Rose"          to 0xFFE11D48.toInt(),
        "Emerald"       to 0xFF059669.toInt(),
        "Amber"         to 0xFFD97706.toInt(),
        "Slate"         to 0xFF475569.toInt(),
        "Crimson"       to 0xFFDC143C.toInt()
    )
}

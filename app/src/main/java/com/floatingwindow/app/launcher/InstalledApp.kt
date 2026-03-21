package com.floatingwindow.app.launcher

import android.graphics.drawable.Drawable

/**
 * Represents one installed app on the device.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable,
    val isFavorite: Boolean = false
)

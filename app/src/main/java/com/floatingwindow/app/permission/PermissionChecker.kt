package com.floatingwindow.app.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * PermissionChecker
 * Checks ALL permissions the app needs and returns a status report.
 */
object PermissionChecker {

    data class PermissionStatus(
        val hasOverlay: Boolean,
        val hasNotification: Boolean,   // Android 13+
        val allGranted: Boolean = hasOverlay && hasNotification
    )

    fun check(context: Context): PermissionStatus {
        val hasOverlay = OverlayPermissionManager.hasPermission(context)
        val hasNotif   = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
        } else true

        return PermissionStatus(hasOverlay, hasNotif)
    }

    /**
     * Request POST_NOTIFICATIONS on Android 13+ (needed for foreground service notification).
     */
    fun requestNotificationPermission(activity: Activity, requestCode: Int = 2001) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                requestCode
            )
        }
    }

    /**
     * Returns the Intent that leads to PermissionActivity.
     * Use this from the splash/main to gate-keep the app.
     */
    fun buildPermissionIntent(context: Context): Intent =
        Intent(context, PermissionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
}

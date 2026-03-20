package com.floatingwindow.app.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * OverlayPermissionManager
 *
 * Handles SYSTEM_ALERT_WINDOW across all Android versions + Samsung One UI quirks.
 *
 * Android version matrix:
 *  < API 23  → permission auto-granted at install time
 *  API 23–25 → Settings.canDrawOverlays() works, but Samsung may block it
 *  API 26–28 → Standard overlay; Samsung has extra "Appear on top" toggle
 *  API 29+   → Google added opacity restrictions; Samsung One UI adds its own
 *  API 31+   → Notification permission also needed for foreground service
 *  Samsung   → Needs dedicated "Appear on top" setting, sometimes in a
 *               different menu path than AOSP
 */
object OverlayPermissionManager {

    // ── Check ─────────────────────────────────────────────────────────────────

    fun hasPermission(context: Context): Boolean {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.M -> true
            isSamsung() -> hasSamsungOverlayPermission(context)
            else        -> Settings.canDrawOverlays(context)
        }
    }

    /**
     * Samsung has a known bug where Settings.canDrawOverlays() returns false
     * even after the user granted it through the One UI menu.
     * We cross-check with AppOpsManager as a fallback.
     */
    private fun hasSamsungOverlayPermission(context: Context): Boolean {
        if (Settings.canDrawOverlays(context)) return true
        // AppOpsManager double-check for Samsung
        return try {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(), context.packageName
                )
            } else {
                ops.checkOpNoThrow(
                    AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    Process.myUid(), context.packageName
                )
            }
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    // ── Device detection ──────────────────────────────────────────────────────

    fun isSamsung(): Boolean =
        Build.MANUFACTURER.lowercase().contains("samsung")

    fun isOneUI(): Boolean =
        isSamsung() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun getDeviceType(): DeviceType = when {
        isSamsung() && Build.VERSION.SDK_INT >= 34 -> DeviceType.SAMSUNG_ONE_UI_6
        isSamsung() && Build.VERSION.SDK_INT >= 31 -> DeviceType.SAMSUNG_ONE_UI_5
        isSamsung() && Build.VERSION.SDK_INT >= 29 -> DeviceType.SAMSUNG_ONE_UI_2_3
        isSamsung()                                -> DeviceType.SAMSUNG_LEGACY
        Build.VERSION.SDK_INT >= 29                -> DeviceType.ANDROID_10_PLUS
        Build.VERSION.SDK_INT >= 26                -> DeviceType.ANDROID_8_9
        else                                       -> DeviceType.ANDROID_LEGACY
    }

    enum class DeviceType {
        ANDROID_LEGACY,
        ANDROID_8_9,
        ANDROID_10_PLUS,
        SAMSUNG_LEGACY,
        SAMSUNG_ONE_UI_2_3,
        SAMSUNG_ONE_UI_5,
        SAMSUNG_ONE_UI_6
    }

    // ── Permission intent ─────────────────────────────────────────────────────

    /**
     * Returns the best Intent to open the overlay permission screen.
     * Samsung One UI uses a different activity path.
     */
    fun getPermissionIntent(context: Context): Intent {
        val packageUri = Uri.parse("package:${context.packageName}")

        // Try Samsung-specific intent first
        if (isSamsung()) {
            val samsungIntent = getSamsungPermissionIntent(context, packageUri)
            if (samsungIntent != null) return samsungIntent
        }

        // Standard Android intent
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            packageUri
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun getSamsungPermissionIntent(context: Context, packageUri: Uri): Intent? {
        // Samsung One UI 3+ path
        val candidates = listOf(
            "com.android.settings/.applications.AppDrawOverlayPermissionActivity",
            "com.samsung.android.settings/.overlay.AppDrawOverlayPermissionActivity"
        )
        val pm = context.packageManager
        for (component in candidates) {
            try {
                val parts = component.split("/")
                val intent = Intent().apply {
                    setClassName(parts[0], parts[0] + parts[1])
                    data = packageUri
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (pm.resolveActivity(intent, 0) != null) return intent
            } catch (_: Exception) {}
        }
        // Fallback to standard
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)
            .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    // ── Step-by-step instructions per device ─────────────────────────────────

    fun getInstructions(context: Context): PermissionInstructions {
        return when (getDeviceType()) {
            DeviceType.SAMSUNG_ONE_UI_6 -> PermissionInstructions(
                deviceName   = "Samsung One UI 6",
                steps = listOf(
                    "Settings 앱 열기",
                    "\"앱\" 또는 \"애플리케이션\" 탭",
                    "우측 상단 메뉴(⋮) → \"특별한 앱 접근\" 선택",
                    "\"다른 앱 위에 표시\" 선택",
                    "\"Floating Windows\" 찾아서 탭",
                    "\"다른 앱 위에 표시\" 스위치 켜기",
                    "앱으로 돌아오기"
                ),
                englishSteps = listOf(
                    "Open Settings app",
                    "Tap \"Apps\" or \"Applications\"",
                    "Tap the 3-dot menu (⋮) → \"Special app access\"",
                    "Tap \"Appear on top\"",
                    "Find \"Floating Windows\" and tap it",
                    "Enable the \"Appear on top\" switch",
                    "Return to the app"
                ),
                warningNote  = "One UI 6: The toggle may show as enabled but still require a restart of the app.",
                quickPath    = "Settings → Apps → ⋮ → Special access → Appear on top"
            )

            DeviceType.SAMSUNG_ONE_UI_5,
            DeviceType.SAMSUNG_ONE_UI_2_3 -> PermissionInstructions(
                deviceName   = "Samsung One UI",
                steps        = listOf(), // unused
                englishSteps = listOf(
                    "Open Settings",
                    "Go to Apps",
                    "Tap the menu icon (⋮) → Special access",
                    "Tap \"Appear on top\"",
                    "Enable toggle for \"Floating Windows\"",
                    "Return to app"
                ),
                warningNote  = "On some Samsung devices the permission toggle resets after reboot. Re-grant if needed.",
                quickPath    = "Settings → Apps → Special access → Appear on top"
            )

            DeviceType.SAMSUNG_LEGACY -> PermissionInstructions(
                deviceName   = "Samsung (older)",
                steps        = listOf(),
                englishSteps = listOf(
                    "Open Settings",
                    "Go to Application Manager",
                    "Find \"Floating Windows\"",
                    "Tap \"Draw over other apps\" or \"Appear on top\"",
                    "Enable the permission"
                ),
                warningNote  = null,
                quickPath    = "Settings → Application Manager → Floating Windows → Draw over other apps"
            )

            DeviceType.ANDROID_10_PLUS -> PermissionInstructions(
                deviceName   = "Android 10+",
                steps        = listOf(),
                englishSteps = listOf(
                    "Tap \"Open Settings\" button below",
                    "Find \"Floating Windows\" in the list",
                    "Enable \"Allow display over other apps\"",
                    "Press Back to return"
                ),
                warningNote  = "Android 10+ restricts overlay opacity. The app handles this automatically.",
                quickPath    = "Settings → Apps → Special app access → Display over other apps"
            )

            else -> PermissionInstructions(
                deviceName   = "Android",
                steps        = listOf(),
                englishSteps = listOf(
                    "Tap \"Open Settings\" button below",
                    "Enable \"Allow display over other apps\"",
                    "Return to the app"
                ),
                warningNote  = null,
                quickPath    = "Settings → Apps → Floating Windows → Draw over other apps"
            )
        }
    }

    data class PermissionInstructions(
        val deviceName:   String,
        val steps:        List<String>,       // localized (Samsung Korean UI)
        val englishSteps: List<String>,
        val warningNote:  String?,
        val quickPath:    String
    )
}

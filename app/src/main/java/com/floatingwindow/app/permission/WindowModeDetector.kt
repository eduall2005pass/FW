package com.floatingwindow.app.permission

import android.content.Context
import android.os.Build

/**
 * WindowModeDetector
 *
 * Decides which mode the app should run in:
 *   OVERLAY  → SYSTEM_ALERT_WINDOW granted, real floating windows
 *   VIRTUAL  → No permission / blocked device → in-app virtual desktop
 *
 * Also detects devices that are known to block overlay even after grant.
 */
object WindowModeDetector {

    enum class WindowMode {
        OVERLAY,   // Real system overlay (phone environment)
        VIRTUAL    // In-app virtual desktop (no permission needed)
    }

    // Devices/brands known to forcefully block or break SYSTEM_ALERT_WINDOW
    private val BLOCKED_BRANDS = setOf(
        "huawei", "honor",        // EMUI 10+ blocks many overlay apps
        "oppo", "realme",         // ColorOS 11+ restricted
        "vivo",                   // FuntouchOS restricted
        "xiaomi", "redmi", "poco" // MIUI 12+ may block (user can still grant)
    )

    // MIUI versions that block overlay regardless of permission
    private val MIUI_BLOCKED_VERSIONS = setOf("12", "13", "14")

    /**
     * Main decision function.
     * Call this on every app start to decide which mode to use.
     */
    fun detect(context: Context): WindowMode {
        // 1. Check if permission is granted
        val hasPermission = OverlayPermissionManager.hasPermission(context)

        // 2. If no permission at all → Virtual
        if (!hasPermission) return WindowMode.VIRTUAL

        // 3. Permission granted — but check if device will actually honor it
        if (isKnownBlockedDevice()) return WindowMode.VIRTUAL

        // 4. MIUI special case
        if (isMiuiBlocked()) return WindowMode.VIRTUAL

        // 5. Android 10+ extra check: can we actually create an overlay window?
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!canActuallyOverlay(context)) return WindowMode.VIRTUAL
        }

        // All good — use real overlay
        return WindowMode.OVERLAY
    }

    /**
     * Force virtual mode regardless of permission.
     * Called when overlay attempt crashes or shows nothing.
     */
    fun forceVirtual(context: Context) {
        context.getSharedPreferences("wm_mode", Context.MODE_PRIVATE)
            .edit().putBoolean("force_virtual", true).apply()
    }

    fun isForcedVirtual(context: Context): Boolean =
        context.getSharedPreferences("wm_mode", Context.MODE_PRIVATE)
            .getBoolean("force_virtual", false)

    fun clearForceVirtual(context: Context) {
        context.getSharedPreferences("wm_mode", Context.MODE_PRIVATE)
            .edit().putBoolean("force_virtual", false).apply()
    }

    // ── Device checks ─────────────────────────────────────────────────────────

    fun isKnownBlockedDevice(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return BLOCKED_BRANDS.any { brand.contains(it) || manufacturer.contains(it) }
    }

    fun isMiuiBlocked(): Boolean {
        return try {
            val miuiVersion = getMiuiVersion()
            if (miuiVersion != null) {
                val major = miuiVersion.split(".").firstOrNull() ?: return false
                MIUI_BLOCKED_VERSIONS.contains(major)
            } else false
        } catch (e: Exception) { false }
    }

    private fun getMiuiVersion(): String? {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java)
            val version = method.invoke(null, "ro.miui.ui.version.name") as? String
            if (version.isNullOrBlank()) null else version.removePrefix("V")
        } catch (e: Exception) { null }
    }

    private fun canActuallyOverlay(context: Context): Boolean {
        // On Android 10+ some ROMs return true from canDrawOverlays()
        // but still block the overlay at WindowManager level.
        // We do a quick test by checking AppOps mode directly.
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE)
                    as android.app.AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    android.os.Process.myUid(), context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW,
                    android.os.Process.myUid(), context.packageName
                )
            }
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { true } // assume ok if check fails
    }

    // ── Human-readable reason ─────────────────────────────────────────────────
    fun getVirtualModeReason(context: Context): String {
        val hasPermission = OverlayPermissionManager.hasPermission(context)
        return when {
            isForcedVirtual(context) ->
                "Overlay mode had an issue on your device. Running in Virtual Desktop mode."
            !hasPermission ->
                "Overlay permission not granted. Running in Virtual Desktop mode."
            isKnownBlockedDevice() ->
                "${Build.BRAND} devices restrict overlay windows. Running in Virtual Desktop mode."
            isMiuiBlocked() ->
                "MIUI ${getMiuiVersion()} restricts overlay windows. Running in Virtual Desktop mode."
            else ->
                "Your device does not support overlay windows. Running in Virtual Desktop mode."
        }
    }
}

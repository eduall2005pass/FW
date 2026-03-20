package com.floatingwindow.app.launcher

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AppLauncherRepository
 * - Queries PackageManager for all launchable apps
 * - Persists favorites in SharedPreferences
 */
class AppLauncherRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("launcher_prefs", Context.MODE_PRIVATE)

    private val FAV_KEY = "favorites"

    // ── Load all installed apps ───────────────────────────────────────────────
    suspend fun loadApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val favSet = getFavoritePackages()

        pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { info: ResolveInfo ->
                try {
                    InstalledApp(
                        packageName = info.activityInfo.packageName,
                        label       = info.loadLabel(pm).toString(),
                        icon        = info.loadIcon(pm),
                        isFavorite  = favSet.contains(info.activityInfo.packageName)
                    )
                } catch (e: Exception) { null }
            }
            .sortedWith(
                compareByDescending<InstalledApp> { it.isFavorite }
                    .thenBy { it.label.lowercase() }
            )
    }

    // ── Favorites ─────────────────────────────────────────────────────────────
    fun getFavoritePackages(): Set<String> =
        prefs.getStringSet(FAV_KEY, emptySet()) ?: emptySet()

    fun toggleFavorite(packageName: String): Boolean {
        val favs = getFavoritePackages().toMutableSet()
        val nowFav = if (favs.contains(packageName)) {
            favs.remove(packageName); false
        } else {
            favs.add(packageName); true
        }
        prefs.edit().putStringSet(FAV_KEY, favs).apply()
        return nowFav
    }

    fun isFavorite(packageName: String) = getFavoritePackages().contains(packageName)

    // ── Launch an app normally (non-floating) ─────────────────────────────────
    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        if (intent != null) context.startActivity(intent)
    }
}

package com.floatingwindow.app.launcher

import android.content.Context
import android.view.View
import android.view.WindowManager
import com.floatingwindow.app.manager.FloatingWindowManager

/**
 * LauncherManager — owns the FloatingAppLauncher overlay lifecycle.
 * Holds a direct view reference so it can remove it from WindowManager.
 */
class LauncherManager(
    private val context: Context,
    private val systemWM: WindowManager,
    private val floatingWM: FloatingWindowManager
) {
    private val repo = AppLauncherRepository(context)
    private var launcher: FloatingAppLauncher? = null
    private var launcherView: View? = null
    private var isVisible = false

    fun toggle() { if (isVisible) hide() else show() }

    fun show() {
        if (isVisible) return
        val l = FloatingAppLauncher(
            context       = context,
            windowManager = systemWM,
            floatingWM    = floatingWM,
            repo          = repo,
            onDismiss     = { removeLauncher() }
        )
        val view = l.buildView()
        try {
            systemWM.addView(view, l.layoutParams)
            view.post { l.animateIn() }
            launcher     = l
            launcherView = view
            isVisible    = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun hide() { launcher?.dismiss() }

    private fun removeLauncher() {
        launcherView?.let { v ->
            try { systemWM.removeView(v) } catch (_: Exception) {}
        }
        launcher?.destroy()
        launcher     = null
        launcherView = null
        isVisible    = false
    }

    fun destroy() {
        launcher?.destroy()
        launcherView?.let { v -> try { systemWM.removeView(v) } catch (_: Exception) {} }
        launcher     = null
        launcherView = null
        isVisible    = false
    }
}

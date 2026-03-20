package com.floatingwindow.app.ui

import android.content.Context
import android.view.WindowManager

/**
 * SnapEngine
 *
 * Windows 11-style snap behaviour:
 *  - Drag window to left edge  → snap to left half
 *  - Drag window to right edge → snap to right half
 *  - Drag window to top edge   → maximize
 *  - Drag away from edge       → restore saved size
 *
 * Call [evaluate] during drag with the current raw position.
 * It returns a [SnapZone] hint so the UI can show a preview overlay.
 * Call [applySnap] on ACTION_UP to commit the snap geometry.
 */
class SnapEngine(private val context: Context) {

    enum class SnapZone { NONE, LEFT, RIGHT, TOP }

    private val wm get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val sw get() = wm.currentWindowMetrics.bounds.width()
    private val sh get() = wm.currentWindowMetrics.bounds.height()

    // How close (px) to edge before snapping kicks in
    private val SNAP_THRESHOLD get() = dp(48)

    fun evaluate(rawX: Int, rawY: Int): SnapZone = when {
        rawY <= SNAP_THRESHOLD                  -> SnapZone.TOP
        rawX <= SNAP_THRESHOLD                  -> SnapZone.LEFT
        rawX >= sw - SNAP_THRESHOLD             -> SnapZone.RIGHT
        else                                    -> SnapZone.NONE
    }

    data class SnapGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

    /** Returns the target geometry for the given snap zone */
    fun geometryFor(zone: SnapZone, statusBarOffset: Int = dp(28)): SnapGeometry {
        val usableH = sh - statusBarOffset - dp(42) // leave space for taskbar
        return when (zone) {
            SnapZone.LEFT  -> SnapGeometry(0, statusBarOffset, sw / 2, usableH)
            SnapZone.RIGHT -> SnapGeometry(sw / 2, statusBarOffset, sw / 2, usableH)
            SnapZone.TOP   -> SnapGeometry(0, statusBarOffset, sw, usableH)
            SnapZone.NONE  -> SnapGeometry(0, 0, 0, 0) // unused
        }
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

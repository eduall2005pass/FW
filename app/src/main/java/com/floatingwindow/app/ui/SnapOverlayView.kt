package com.floatingwindow.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * SnapOverlayView
 *
 * Transparent full-screen view drawn on top of everything during a drag.
 * Shows a semi-transparent blue rectangle previewing where the window
 * will snap to — just like Windows 11's snap highlight.
 */
class SnapOverlayView(context: Context) : View(context) {

    private var zone: SnapEngine.SnapZone = SnapEngine.SnapZone.NONE
    private var previewRect: RectF = RectF()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x330067C0.toInt()
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA0067C0.toInt()
        style = Paint.Style.STROKE
        strokeWidth = dp(2).toFloat()
    }
    private val cornerR = dp(8).toFloat()

    fun showZone(z: SnapEngine.SnapZone, rect: RectF) {
        zone        = z
        previewRect = rect
        visibility  = if (z == SnapEngine.SnapZone.NONE) INVISIBLE else VISIBLE
        invalidate()
    }

    fun hide() {
        zone       = SnapEngine.SnapZone.NONE
        visibility = INVISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (zone == SnapEngine.SnapZone.NONE) return
        canvas.drawRoundRect(previewRect, cornerR, cornerR, fillPaint)
        canvas.drawRoundRect(previewRect, cornerR, cornerR, strokePaint)
    }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
}

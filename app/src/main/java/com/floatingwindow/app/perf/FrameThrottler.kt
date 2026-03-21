package com.floatingwindow.app.perf

import android.os.Handler
import android.os.Looper

/**
 * FrameThrottler
 *
 * Coalesces rapid layout updates (drag, resize) into a single
 * WindowManager.updateViewLayout() call per frame at 60 fps.
 *
 * Without this, each touch-move event triggers a separate IPC call
 * to WindowManager, causing jank with many open windows.
 */
class FrameThrottler {

    private val handler = Handler(Looper.getMainLooper())
    private val pendingUpdates = mutableMapOf<String, Runnable>()
    private val FRAME_MS = 16L   // ~60 fps

    /**
     * Schedule [action] for window [id], replacing any pending update for the same window.
     */
    fun post(id: String, action: () -> Unit) {
        // Cancel previous pending update for this window
        pendingUpdates.remove(id)?.let { handler.removeCallbacks(it) }

        val r = Runnable {
            pendingUpdates.remove(id)
            action()
        }
        pendingUpdates[id] = r
        handler.postDelayed(r, FRAME_MS)
    }

    /** Execute [action] immediately and cancel any pending for [id] */
    fun flush(id: String, action: () -> Unit) {
        pendingUpdates.remove(id)?.let { handler.removeCallbacks(it) }
        action()
    }

    fun cancelAll() {
        pendingUpdates.values.forEach { handler.removeCallbacks(it) }
        pendingUpdates.clear()
    }

    fun cancel(id: String) {
        pendingUpdates.remove(id)?.let { handler.removeCallbacks(it) }
    }
}

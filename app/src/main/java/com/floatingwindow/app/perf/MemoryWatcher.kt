package com.floatingwindow.app.perf

import android.app.ActivityManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * MemoryWatcher
 *
 * Periodically monitors available RAM.
 * If memory is critically low:
 *  - Trims window content views (free bitmaps etc.)
 *  - Clears the render pool
 *  - Notifies the service to optionally minimize background windows
 */
class MemoryWatcher(
    private val context: Context,
    private val onLowMemory: (level: MemoryLevel) -> Unit
) {
    enum class MemoryLevel { NORMAL, LOW, CRITICAL }

    private val handler  = Handler(Looper.getMainLooper())
    private val am       = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var running  = false
    private val INTERVAL = 15_000L   // check every 15 s

    fun start() {
        if (running) return
        running = true
        schedule()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
    }

    private fun schedule() {
        handler.postDelayed({
            if (!running) return@postDelayed
            check()
            schedule()
        }, INTERVAL)
    }

    private fun check() {
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val level = when {
            info.lowMemory                         -> MemoryLevel.CRITICAL
            info.availMem < info.totalMem * 0.10f -> MemoryLevel.LOW
            else                                   -> MemoryLevel.NORMAL
        }
        if (level != MemoryLevel.NORMAL) {
            Log.w("MemoryWatcher", "Memory level: $level  avail=${info.availMem/1024/1024}MB")
            onLowMemory(level)
        }
    }

    /** Call from Service.onTrimMemory for immediate response */
    fun onTrimMemory(trimLevel: Int) {
        val level = when {
            trimLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> MemoryLevel.CRITICAL
            trimLevel >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW      -> MemoryLevel.LOW
            else                                                                           -> MemoryLevel.NORMAL
        }
        if (level != MemoryLevel.NORMAL) onLowMemory(level)
    }
}

package com.floatingwindow.app.perf

import android.content.Context
import android.view.View
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * WindowRenderPool
 *
 * Recycles detached window Views instead of creating new ones from scratch.
 * Dramatically cuts GC pressure when opening/closing windows rapidly.
 *
 * Usage:
 *   val view = pool.acquire(contentType) ?: createNewView(contentType)
 *   // ... use view ...
 *   pool.release(contentType, view)   // when closing
 */
class WindowRenderPool(private val maxPerType: Int = 3) {

    private val pools = mutableMapOf<String, ConcurrentLinkedQueue<View>>()

    fun acquire(key: String): View? =
        pools[key]?.poll()

    fun release(key: String, view: View) {
        val q = pools.getOrPut(key) { ConcurrentLinkedQueue() }
        if (q.size < maxPerType) {
            // Reset view state before pooling
            view.alpha        = 1f
            view.scaleX       = 1f
            view.scaleY       = 1f
            view.translationX = 0f
            view.translationY = 0f
            view.visibility   = View.VISIBLE
            view.clearAnimation()
            q.offer(view)
        }
        // else: let GC handle it (pool full)
    }

    fun clear() = pools.values.forEach { it.clear() }

    fun size(key: String) = pools[key]?.size ?: 0
}

package com.floatingwindow.app.manager

import android.content.Context
import android.view.WindowManager
import com.floatingwindow.app.model.WindowContentType
import com.floatingwindow.app.model.WindowState
import com.floatingwindow.app.model.WindowStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * FloatingWindowManager
 *
 * Pure logic layer — no Android Views here.
 * Tracks all windows, manages z-order, focus, minimize/restore.
 * The Service listens via WindowEventListener and updates Views.
 */
class FloatingWindowManager(private val context: Context) {

    // ── Storage ───────────────────────────────────────────────────────────────
    private val windows    = ConcurrentHashMap<String, WindowState>()
    private val zOrderList = mutableListOf<String>()   // index 0 = bottom, last = top
    private var zCounter   = 0

    // ── Event bus ────────────────────────────────────────────────────────────
    interface WindowEventListener {
        fun onWindowCreated(state: WindowState)
        fun onWindowFocused(state: WindowState, allStates: List<WindowState>)
        fun onWindowMinimized(state: WindowState)
        fun onWindowRestored(state: WindowState)
        fun onWindowMaximized(state: WindowState)
        fun onWindowUnmaximized(state: WindowState)
        fun onWindowClosed(id: String)
        fun onWindowMoved(state: WindowState)
        fun onWindowResized(state: WindowState)
        fun onZOrderChanged(orderedIds: List<String>)
    }

    private val listeners = mutableListOf<WindowEventListener>()
    fun addListener(l: WindowEventListener)    { listeners.add(l) }
    fun removeListener(l: WindowEventListener) { listeners.remove(l) }
    private fun emit(block: WindowEventListener.() -> Unit) = listeners.forEach { it.block() }

    // ── Screen helpers ────────────────────────────────────────────────────────
    private val wm get() = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    fun screenWidth()  = wm.currentWindowMetrics.bounds.width()
    fun screenHeight() = wm.currentWindowMetrics.bounds.height()
    fun dpToPx(dp: Int) = (dp * context.resources.displayMetrics.density).toInt()

    // ─────────────────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────────────────
    fun createWindow(
        title: String,
        contentType: WindowContentType = WindowContentType.BLANK,
        width: Int  = dpToPx(360),
        height: Int = dpToPx(280)
    ): WindowState {
        val id  = UUID.randomUUID().toString().take(8)
        val sw  = screenWidth()
        val sh  = screenHeight()
        val off = windows.size % 7 * dpToPx(28)

        val x = ((sw / 2 - width  / 2) + off).coerceIn(0, sw - width)
        val y = ((sh / 4)               + off).coerceIn(0, sh - height - dpToPx(48))

        val state = WindowState(
            id           = id,
            title        = title,
            contentType  = contentType,
            x = x,  y = y,
            width = width, height = height,
            savedX = x, savedY = y,
            savedWidth = width, savedHeight = height,
            zIndex = ++zCounter
        )

        windows[id] = state
        zOrderList.add(id)
        focusWindow(id)                // auto-focus new window
        emit { onWindowCreated(state) }
        return state
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FOCUS  — brings window to top of z-stack and marks it focused
    // ─────────────────────────────────────────────────────────────────────────
    fun focusWindow(id: String) {
        val target = windows[id] ?: return
        windows.values.forEach { it.isFocused = false }
        target.isFocused = true
        target.zIndex = ++zCounter

        // Move to end (= top) of z-order list
        zOrderList.remove(id)
        zOrderList.add(id)

        emit { onWindowFocused(target, getAllWindows()) }
        emit { onZOrderChanged(zOrderList.toList()) }
    }

    fun getFocusedWindow(): WindowState? = windows.values.firstOrNull { it.isFocused }

    // ─────────────────────────────────────────────────────────────────────────
    // MINIMIZE
    // ─────────────────────────────────────────────────────────────────────────
    fun minimizeWindow(id: String) {
        val state = windows[id] ?: return
        if (state.isMinimized) return
        state.status    = WindowStatus.MINIMIZED
        state.isFocused = false

        // Auto-focus the next visible window on the stack
        autoFocusNext(excluding = id)
        emit { onWindowMinimized(state) }
        emit { onZOrderChanged(zOrderList.toList()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESTORE  — brings minimized window back
    // ─────────────────────────────────────────────────────────────────────────
    fun restoreWindow(id: String) {
        val state = windows[id] ?: return
        if (state.status == WindowStatus.CLOSED) return
        state.status = WindowStatus.NORMAL
        focusWindow(id)
        emit { onWindowRestored(state) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MAXIMIZE / UNMAXIMIZE
    // ─────────────────────────────────────────────────────────────────────────
    fun maximizeWindow(id: String) {
        val state = windows[id] ?: return
        if (state.isMaximized) { unmaximizeWindow(id); return }

        state.savedX      = state.x;      state.savedY      = state.y
        state.savedWidth  = state.width;  state.savedHeight = state.height

        state.x      = 0;                 state.y      = dpToPx(28)
        state.width  = screenWidth();     state.height = screenHeight() - dpToPx(28)
        state.status = WindowStatus.MAXIMIZED

        focusWindow(id)
        emit { onWindowMaximized(state) }
    }

    fun unmaximizeWindow(id: String) {
        val state = windows[id] ?: return
        if (!state.isMaximized) return

        state.x = state.savedX;     state.y = state.savedY
        state.width = state.savedWidth; state.height = state.savedHeight
        state.status = WindowStatus.NORMAL

        focusWindow(id)
        emit { onWindowUnmaximized(state) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CLOSE
    // ─────────────────────────────────────────────────────────────────────────
    fun closeWindow(id: String) {
        val state = windows[id] ?: return
        state.status = WindowStatus.CLOSED
        windows.remove(id)
        zOrderList.remove(id)
        autoFocusNext()
        emit { onWindowClosed(id) }
        emit { onZOrderChanged(zOrderList.toList()) }
    }

    fun closeAll() = windows.keys.toList().forEach { closeWindow(it) }

    // ─────────────────────────────────────────────────────────────────────────
    // MOVE / RESIZE
    // ─────────────────────────────────────────────────────────────────────────
    fun moveWindow(id: String, newX: Int, newY: Int) {
        val state = windows[id] ?: return
        val sw = screenWidth(); val sh = screenHeight()
        state.x = newX.coerceIn(-(state.width / 2), sw - state.width / 2)
        state.y = newY.coerceIn(0, sh - dpToPx(32))
        emit { onWindowMoved(state) }
    }

    fun resizeWindow(id: String, newW: Int, newH: Int) {
        val state = windows[id] ?: return
        state.width  = newW.coerceAtLeast(dpToPx(200))
        state.height = newH.coerceAtLeast(dpToPx(130))
        emit { onWindowResized(state) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SWITCH  — desktop-OS style alt-tab cycling
    // ─────────────────────────────────────────────────────────────────────────
    fun switchToNext() {
        val visible = getVisibleWindows()
        if (visible.size < 2) return
        val curIdx  = visible.indexOfFirst { it.isFocused }.coerceAtLeast(0)
        val nextIdx = (curIdx + 1) % visible.size
        focusWindow(visible[nextIdx].id)
    }

    fun switchToPrev() {
        val visible = getVisibleWindows()
        if (visible.size < 2) return
        val curIdx  = visible.indexOfFirst { it.isFocused }.coerceAtLeast(0)
        val prevIdx = (curIdx - 1 + visible.size) % visible.size
        focusWindow(visible[prevIdx].id)
    }

    /** Smart switch: if minimized → restore, else → focus */
    fun switchToWindow(id: String) {
        val state = windows[id] ?: return
        when (state.status) {
            WindowStatus.MINIMIZED                          -> restoreWindow(id)
            WindowStatus.NORMAL, WindowStatus.MAXIMIZED    -> focusWindow(id)
            WindowStatus.CLOSED                            -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────────────────
    fun getWindow(id: String)      = windows[id]
    fun getAllWindows()             = zOrderList.mapNotNull { windows[it] }
    fun getVisibleWindows()        = getAllWindows().filter { it.isVisible }
    fun getMinimizedWindows()      = getAllWindows().filter { it.isMinimized }
    fun getWindowCount()           = windows.size
    fun getZOrderedIds()           = zOrderList.toList()

    // ─────────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────────
    private fun autoFocusNext(excluding: String? = null) {
        val next = zOrderList.lastOrNull { id ->
            id != excluding && windows[id]?.isVisible == true
        }
        if (next != null) focusWindow(next)
    }
}

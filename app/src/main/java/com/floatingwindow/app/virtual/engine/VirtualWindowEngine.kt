package com.floatingwindow.app.virtual.engine

import com.floatingwindow.app.virtual.model.VAppType
import com.floatingwindow.app.virtual.model.VWState
import com.floatingwindow.app.virtual.model.VirtualWindow
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * VirtualWindowEngine
 *
 * Pure logic — no Android Views.
 * Tracks all virtual windows, z-order, focus, minimize/restore/snap.
 * The VirtualDesktopView listens and updates Views accordingly.
 */
class VirtualWindowEngine {

    private val _windows    = mutableListOf<VirtualWindow>()
    private val zOrder      = mutableListOf<String>()
    private var zCounter    = 0
    private var desktopW    = 1080
    private var desktopH    = 1920

    val windows: List<VirtualWindow> get() = _windows.toList()

    // ── Event callbacks ───────────────────────────────────────────────────────
    interface Listener {
        fun onCreated(w: VirtualWindow)
        fun onFocused(w: VirtualWindow, all: List<VirtualWindow>)
        fun onMinimized(w: VirtualWindow)
        fun onRestored(w: VirtualWindow)
        fun onMaximized(w: VirtualWindow)
        fun onUnmaximized(w: VirtualWindow)
        fun onClosed(id: String)
        fun onMoved(w: VirtualWindow)
        fun onResized(w: VirtualWindow)
        fun onZOrderChanged(orderedIds: List<String>)
        fun onAllClosed()
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    fun addListener(l: Listener)    { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }
    private fun emit(fn: Listener.() -> Unit) = listeners.forEach { it.fn() }

    // ── Desktop size (set by VirtualDesktopView on layout) ────────────────────
    fun setDesktopSize(w: Int, h: Int) { desktopW = w; desktopH = h }

    // ── CREATE ────────────────────────────────────────────────────────────────
    fun create(
        appType: VAppType,
        title:   String  = appType.title,
        width:   Int     = (desktopW * 0.62f).toInt().coerceAtLeast(300),
        height:  Int     = (desktopH * 0.44f).toInt().coerceAtLeast(220)
    ): VirtualWindow {
        val id  = UUID.randomUUID().toString().take(8)
        val off = (_windows.size % 7) * 26
        val x   = (desktopW / 2 - width  / 2 + off).coerceIn(0, desktopW - width)
        val y   = (desktopH / 6            + off).coerceIn(0, desktopH - height - 48)

        val w = VirtualWindow(
            id = id, title = title, appType = appType,
            x = x, y = y, width = width, height = height,
            savedX = x, savedY = y, savedWidth = width, savedHeight = height,
            zIndex = ++zCounter
        )
        _windows.add(w)
        zOrder.add(id)
        focus(id)
        emit { onCreated(w) }
        return w
    }

    // ── FOCUS ─────────────────────────────────────────────────────────────────
    fun focus(id: String) {
        val target = find(id) ?: return
        _windows.forEach { it.isFocused = false }
        target.isFocused = true
        target.zIndex    = ++zCounter
        zOrder.remove(id); zOrder.add(id)
        emit { onFocused(target, _windows.toList()) }
        emit { onZOrderChanged(zOrder.toList()) }
    }

    fun getFocused() = _windows.firstOrNull { it.isFocused }

    // ── MINIMIZE ──────────────────────────────────────────────────────────────
    fun minimize(id: String) {
        val w = find(id) ?: return
        if (w.isMinimized) return
        w.state = VWState.MINIMIZED; w.isFocused = false
        autoFocusNext(excluding = id)
        emit { onMinimized(w) }
        emit { onZOrderChanged(zOrder.toList()) }
    }

    // ── RESTORE ───────────────────────────────────────────────────────────────
    fun restore(id: String) {
        val w = find(id) ?: return
        if (w.state == VWState.CLOSED) return
        w.state = VWState.NORMAL
        focus(id)
        emit { onRestored(w) }
    }

    // ── MAXIMIZE / RESTORE ────────────────────────────────────────────────────
    fun maximize(id: String) {
        val w = find(id) ?: return
        if (w.isMaximized) { unmaximize(id); return }
        w.savedX = w.x; w.savedY = w.y
        w.savedWidth = w.width; w.savedHeight = w.height
        w.x = 0;         w.y = 0
        w.width = desktopW; w.height = desktopH - 42  // leave taskbar
        w.state = VWState.MAXIMIZED
        focus(id)
        emit { onMaximized(w) }
    }

    fun unmaximize(id: String) {
        val w = find(id) ?: return
        if (!w.isMaximized) return
        w.x = w.savedX; w.y = w.savedY
        w.width = w.savedWidth; w.height = w.savedHeight
        w.state = VWState.NORMAL
        focus(id)
        emit { onUnmaximized(w) }
    }

    // ── SNAP ──────────────────────────────────────────────────────────────────
    enum class SnapZone { NONE, LEFT, RIGHT, TOP }

    fun evaluateSnap(windowX: Int, windowY: Int): SnapZone {
        val threshold = 32
        return when {
            windowY <= threshold                   -> SnapZone.TOP
            windowX <= threshold                   -> SnapZone.LEFT
            windowX >= desktopW - threshold        -> SnapZone.RIGHT
            else                                   -> SnapZone.NONE
        }
    }

    fun applySnap(id: String, zone: SnapZone) {
        val w = find(id) ?: return
        if (zone == SnapZone.NONE) return
        w.snapSavedX = w.x; w.snapSavedY = w.y
        w.snapSavedW = w.width; w.snapSavedH = w.height
        w.isSnapped = true
        val taskH = 42
        val usableH = desktopH - taskH
        when (zone) {
            SnapZone.LEFT  -> { w.x=0; w.y=0; w.width=desktopW/2; w.height=usableH }
            SnapZone.RIGHT -> { w.x=desktopW/2; w.y=0; w.width=desktopW/2; w.height=usableH }
            SnapZone.TOP   -> { w.x=0; w.y=0; w.width=desktopW; w.height=usableH }
            else            -> {}
        }
        focus(id)
        emit { onMoved(w) }
        emit { onResized(w) }
    }

    fun unsnap(id: String) {
        val w = find(id) ?: return
        if (!w.isSnapped) return
        w.x = w.snapSavedX; w.y = w.snapSavedY
        w.width = w.snapSavedW; w.height = w.snapSavedH
        w.isSnapped = false
        emit { onMoved(w) }
        emit { onResized(w) }
    }

    // ── MOVE ──────────────────────────────────────────────────────────────────
    fun move(id: String, x: Int, y: Int) {
        val w = find(id) ?: return
        w.x = x.coerceIn(-w.width/2, desktopW - w.width/2)
        w.y = y.coerceIn(0, desktopH - 48)
        emit { onMoved(w) }
    }

    // ── RESIZE ────────────────────────────────────────────────────────────────
    fun resize(id: String, nw: Int, nh: Int) {
        val w = find(id) ?: return
        w.width  = nw.coerceAtLeast(200)
        w.height = nh.coerceAtLeast(130)
        emit { onResized(w) }
    }

    // ── CLOSE ─────────────────────────────────────────────────────────────────
    fun close(id: String) {
        val w = find(id) ?: return
        w.state = VWState.CLOSED
        _windows.removeAll { it.id == id }
        zOrder.remove(id)
        autoFocusNext()
        emit { onClosed(id) }
        emit { onZOrderChanged(zOrder.toList()) }
        if (_windows.isEmpty()) emit { onAllClosed() }
    }

    fun closeAll() {
        _windows.map { it.id }.forEach { close(it) }
    }

    // ── SWITCH ────────────────────────────────────────────────────────────────
    fun switchToNext() {
        val visible = _windows.filter { it.isVisible }
        if (visible.size < 2) return
        val cur  = visible.indexOfFirst { it.isFocused }.coerceAtLeast(0)
        focus(visible[(cur + 1) % visible.size].id)
    }

    fun switchTo(id: String) {
        val w = find(id) ?: return
        when {
            w.isMinimized -> restore(id)
            else          -> focus(id)
        }
    }

    // ── QUERY ─────────────────────────────────────────────────────────────────
    fun find(id: String) = _windows.firstOrNull { it.id == id }
    fun getVisible()     = zOrder.mapNotNull { id -> _windows.firstOrNull { it.id == id && it.isVisible } }
    fun getMinimized()   = _windows.filter { it.isMinimized }
    fun getOrdered()     = zOrder.mapNotNull { id -> _windows.firstOrNull { it.id == id } }
    fun count()          = _windows.size

    // ── Internal ──────────────────────────────────────────────────────────────
    private fun autoFocusNext(excluding: String? = null) {
        val next = zOrder.lastOrNull { id -> id != excluding && _windows.any { it.id == id && it.isVisible } }
        if (next != null) focus(next)
    }
}

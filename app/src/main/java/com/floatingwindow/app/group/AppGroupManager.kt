package com.floatingwindow.app.group

import android.content.Context
import com.floatingwindow.app.manager.FloatingWindowManager
import com.floatingwindow.app.model.WindowState

/**
 * AppGroupManager
 *
 * Bridges AppGroupRepository ↔ FloatingWindowManager.
 * Handles: launch group, save layout from live windows, delete.
 */
class AppGroupManager(
    private val context: Context,
    private val windowManager: FloatingWindowManager
) {
    val repo = AppGroupRepository(context)

    init { repo.seedDefaultsIfEmpty() }

    // ── LAUNCH ────────────────────────────────────────────────────────────────

    /**
     * Open every entry in the group as a floating window,
     * restoring the saved position + size.
     */
    fun launchGroup(groupId: String): List<WindowState> {
        val group = repo.get(groupId) ?: return emptyList()
        val sw = windowManager.screenWidth().toFloat()
        val sh = windowManager.screenHeight().toFloat()

        val opened = group.entries.map { entry ->
            windowManager.createWindow(
                title       = entry.title,
                contentType = entry.contentType,
                width       = (entry.wFraction * sw).toInt(),
                height      = (entry.hFraction * sh).toInt()
            ).also { state ->
                // Override the auto-positioned x/y with saved fractions
                val targetX = (entry.xFraction * sw).toInt()
                val targetY = (entry.yFraction * sh).toInt()
                windowManager.moveWindow(state.id, targetX, targetY)
            }
        }

        repo.touchLastUsed(groupId)
        return opened
    }

    // ── SAVE LAYOUT ───────────────────────────────────────────────────────────

    /**
     * Snapshot the current geometry of a set of open windows
     * back into the group (so next launch restores this layout).
     *
     * Call this when the user presses "Save Layout" in the group detail UI.
     */
    fun saveLayoutFromWindows(groupId: String, windowIds: List<String>) {
        val group = repo.get(groupId) ?: return
        val sw = windowManager.screenWidth().toFloat()
        val sh = windowManager.screenHeight().toFloat()

        val updated = windowIds.mapIndexedNotNull { i, wid ->
            val state = windowManager.getWindow(wid) ?: return@mapIndexedNotNull null
            val originalEntry = group.entries.getOrNull(i)
            GroupEntry(
                contentType = state.contentType,
                title       = state.title,
                xFraction   = (state.x / sw).coerceIn(0f, 1f),
                yFraction   = (state.y / sh).coerceIn(0f, 1f),
                wFraction   = (state.width  / sw).coerceIn(0.1f, 1f),
                hFraction   = (state.height / sh).coerceIn(0.1f, 1f)
            )
        }

        repo.updateLayout(groupId, updated)
    }

    // ── CREATE ────────────────────────────────────────────────────────────────

    fun createGroup(name: String, emoji: String, entries: List<GroupEntry>): AppGroup =
        repo.create(name, emoji, entries)

    // ── DELETE ────────────────────────────────────────────────────────────────

    fun deleteGroup(groupId: String) = repo.delete(groupId)

    // ── QUERY ─────────────────────────────────────────────────────────────────

    fun getAllGroups() = repo.getAll().sortedByDescending { it.lastUsed }
    fun getGroup(id: String) = repo.get(id)
}

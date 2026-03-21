package com.floatingwindow.app.group

import android.content.Context
import android.content.SharedPreferences
import com.floatingwindow.app.model.WindowContentType
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AppGroupRepository
 *
 * Persists groups in SharedPreferences as JSON.
 * No external libraries needed.
 *
 * Schema:
 * [
 *   {
 *     "id": "abc123",
 *     "name": "Work",
 *     "emoji": "💼",
 *     "createdAt": 1234567890,
 *     "lastUsed": 1234567900,
 *     "entries": [
 *       { "type": "NOTEPAD", "title": "Notes", "x": 0.05, "y": 0.08, "w": 0.55, "h": 0.40 }
 *     ]
 *   }
 * ]
 */
class AppGroupRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_groups", Context.MODE_PRIVATE)

    private val KEY = "groups_json"

    // ── CRUD ──────────────────────────────────────────────────────────────────

    fun getAll(): MutableList<AppGroup> {
        val json = prefs.getString(KEY, "[]") ?: "[]"
        return parseAll(json)
    }

    fun get(id: String): AppGroup? = getAll().firstOrNull { it.id == id }

    fun save(group: AppGroup) {
        val all = getAll()
        val idx = all.indexOfFirst { it.id == group.id }
        if (idx >= 0) all[idx] = group else all.add(group)
        persist(all)
    }

    fun delete(id: String) {
        val all = getAll().filter { it.id != id }.toMutableList()
        persist(all)
    }

    fun create(name: String, emoji: String, entries: List<GroupEntry>): AppGroup {
        val group = AppGroup(
            id      = UUID.randomUUID().toString().take(8),
            name    = name,
            emoji   = emoji,
            entries = entries.toMutableList()
        )
        save(group)
        return group
    }

    /** Update the saved geometry of every entry from current open window states */
    fun updateLayout(groupId: String, updatedEntries: List<GroupEntry>) {
        val group = get(groupId) ?: return
        group.entries.clear()
        group.entries.addAll(updatedEntries)
        group.lastUsed = System.currentTimeMillis()
        save(group)
    }

    fun touchLastUsed(groupId: String) {
        val group = get(groupId) ?: return
        group.lastUsed = System.currentTimeMillis()
        save(group)
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private fun persist(groups: List<AppGroup>) {
        val arr = JSONArray()
        groups.forEach { g ->
            val obj = JSONObject().apply {
                put("id",        g.id)
                put("name",      g.name)
                put("emoji",     g.emoji)
                put("createdAt", g.createdAt)
                put("lastUsed",  g.lastUsed)
                val entriesArr = JSONArray()
                g.entries.forEach { e ->
                    entriesArr.put(JSONObject().apply {
                        put("type",  e.contentType.name)
                        put("title", e.title)
                        put("x",     e.xFraction)
                        put("y",     e.yFraction)
                        put("w",     e.wFraction)
                        put("h",     e.hFraction)
                    })
                }
                put("entries", entriesArr)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private fun parseAll(json: String): MutableList<AppGroup> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val entriesArr = obj.getJSONArray("entries")
                val entries = (0 until entriesArr.length()).map { j ->
                    val e = entriesArr.getJSONObject(j)
                    GroupEntry(
                        contentType = WindowContentType.valueOf(e.getString("type")),
                        title       = e.getString("title"),
                        xFraction   = e.getDouble("x").toFloat(),
                        yFraction   = e.getDouble("y").toFloat(),
                        wFraction   = e.getDouble("w").toFloat(),
                        hFraction   = e.getDouble("h").toFloat()
                    )
                }
                AppGroup(
                    id        = obj.getString("id"),
                    name      = obj.getString("name"),
                    emoji     = obj.optString("emoji", "📦"),
                    entries   = entries.toMutableList(),
                    createdAt = obj.getLong("createdAt"),
                    lastUsed  = obj.optLong("lastUsed", 0L)
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    // ── Default groups (first install) ────────────────────────────────────────
    fun seedDefaultsIfEmpty() {
        if (getAll().isNotEmpty()) return
        listOf(
            Triple("Work", "💼", listOf(
                GroupEntry(WindowContentType.NOTEPAD,    "Notes",    0.03f, 0.05f, 0.52f, 0.42f),
                GroupEntry(WindowContentType.CALCULATOR, "Calc",     0.42f, 0.08f, 0.50f, 0.42f),
                GroupEntry(WindowContentType.FILE_BROWSER,"Files",   0.03f, 0.50f, 0.52f, 0.38f)
            )),
            Triple("Dev", "🖥️", listOf(
                GroupEntry(WindowContentType.TERMINAL,   "Terminal", 0.03f, 0.05f, 0.58f, 0.52f),
                GroupEntry(WindowContentType.NOTEPAD,    "Editor",   0.38f, 0.08f, 0.58f, 0.52f)
            )),
            Triple("Quick", "⚡", listOf(
                GroupEntry(WindowContentType.CALCULATOR, "Calc",     0.05f, 0.08f, 0.50f, 0.45f),
                GroupEntry(WindowContentType.NOTEPAD,    "Scratch",  0.42f, 0.12f, 0.50f, 0.45f)
            ))
        ).forEach { (name, emoji, entries) -> create(name, emoji, entries) }
    }
}

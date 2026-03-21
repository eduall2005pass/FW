package com.floatingwindow.app.group

import com.floatingwindow.app.model.WindowContentType

/**
 * One entry inside a group — stores which app + its saved layout geometry.
 */
data class GroupEntry(
    val contentType: WindowContentType,
    val title: String,
    // Saved geometry (fraction of screen: 0.0–1.0) so layout works on any screen size
    val xFraction: Float  = 0.05f,
    val yFraction: Float  = 0.08f,
    val wFraction: Float  = 0.55f,
    val hFraction: Float  = 0.40f
)

/**
 * AppGroup — a named collection of GroupEntry items.
 */
data class AppGroup(
    val id: String,
    var name: String,
    var emoji: String          = "📦",
    val entries: MutableList<GroupEntry> = mutableListOf(),
    val createdAt: Long        = System.currentTimeMillis(),
    var lastUsed: Long         = 0L
) {
    val size get() = entries.size
    val preview get() = entries.take(3).joinToString(" ") { it.contentType.emoji }
}

package com.floatingwindow.app.ui

import android.content.*
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.floatingwindow.app.R
import com.floatingwindow.app.group.*
import com.floatingwindow.app.model.WindowContentType
import com.floatingwindow.app.service.FloatingWindowService

/**
 * GroupsActivity
 * Full-screen activity for creating, editing, launching App Groups.
 */
class GroupsActivity : AppCompatActivity() {

    private lateinit var groupManager: AppGroupManager
    private var service: FloatingWindowService? = null
    private var bound = false

    private lateinit var listContainer: LinearLayout
    private lateinit var emptyView: TextView

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = (b as? FloatingWindowService.LocalBinder)?.getService()
            bound   = true
            initGroupManager()
            renderGroups()
        }
        override fun onServiceDisconnected(n: ComponentName?) { service = null; bound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_groups)
        supportActionBar?.title = "App Groups"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        listContainer = findViewById(R.id.groupListContainer)
        emptyView     = findViewById(R.id.tvEmpty)

        findViewById<View>(R.id.fabNewGroup).setOnClickListener { showCreateDialog() }

        val i = Intent(this, FloatingWindowService::class.java)
        startForegroundService(i)
        bindService(i, conn, Context.BIND_AUTO_CREATE)
    }

    override fun onResume() {
        super.onResume()
        if (bound) renderGroups()
    }

    override fun onDestroy() {
        if (bound) { unbindService(conn); bound = false }
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    // ── Init ──────────────────────────────────────────────────────────────────
    private fun initGroupManager() {
        val svc = service ?: return
        groupManager = AppGroupManager(this, svc.wm)
    }

    // ── Render group list ─────────────────────────────────────────────────────
    private fun renderGroups() {
        if (!::groupManager.isInitialized) return
        listContainer.removeAllViews()
        val groups = groupManager.getAllGroups()
        emptyView.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE

        groups.forEach { group ->
            listContainer.addView(buildGroupCard(group))
        }
    }

    private fun buildGroupCard(group: AppGroup): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.card_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(16), dp(8), dp(16), dp(0)) }
            layoutParams = lp
        }

        // ── Header row ──────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val emojiView = TextView(this).apply {
            text     = group.emoji
            textSize = 28f
        }

        val titleBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        val nameView = TextView(this).apply {
            text      = group.name
            textSize  = 15f
            setTextColor(Color.parseColor("#1A1A1A"))
            typeface  = android.graphics.Typeface.DEFAULT_BOLD
        }
        val metaView = TextView(this).apply {
            text      = "${group.size} app${if (group.size != 1) "s" else ""}  •  ${group.preview}"
            textSize  = 11f
            setTextColor(Color.parseColor("#888888"))
        }
        titleBlock.addView(nameView)
        titleBlock.addView(metaView)

        // Launch button
        val launchBtn = TextView(this).apply {
            text      = "▶  Launch"
            textSize  = 12f
            setTextColor(Color.WHITE)
            background = roundRect(Color.parseColor("#0067C0"), dp(6))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { launchGroup(group) }
        }

        header.addView(emojiView)
        header.addView(titleBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(launchBtn)
        card.addView(header)

        // ── App chips ────────────────────────────────────────────────────────
        if (group.entries.isNotEmpty()) {
            val chipRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, dp(4))
            }
            group.entries.forEach { entry ->
                val chip = TextView(this).apply {
                    text       = "${entry.contentType.emoji} ${entry.title}"
                    textSize   = 10f
                    setTextColor(Color.parseColor("#0067C0"))
                    background = roundRect(Color.parseColor("#EEF3FF"), dp(12), stroke = Color.parseColor("#C0D4F5"))
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { setMargins(0, 0, dp(6), 0) }
                    layoutParams = lp
                }
                chipRow.addView(chip)
            }
            card.addView(chipRow)
        }

        // ── Action row ───────────────────────────────────────────────────────
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#F0F0F0"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                setMargins(0, dp(10), 0, dp(8))
            }
        }
        card.addView(divider)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val editBtn = textAction("✏️ Edit")   { showEditDialog(group) }
        val delBtn  = textAction("🗑️ Delete") { confirmDelete(group) }

        // Last used label
        val lastUsedLabel = TextView(this).apply {
            text      = if (group.lastUsed > 0) "Last: ${timeAgo(group.lastUsed)}" else "Never used"
            textSize  = 10f
            setTextColor(Color.parseColor("#AAAAAA"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        actions.addView(lastUsedLabel)
        actions.addView(editBtn)
        actions.addView(delBtn)
        card.addView(actions)

        return card
    }

    // ── Launch ────────────────────────────────────────────────────────────────
    private fun launchGroup(group: AppGroup) {
        if (!::groupManager.isInitialized) return
        groupManager.launchGroup(group.id)
        Toast.makeText(this, "Launched: ${group.name}", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)   // go back to the floating windows
    }

    // ── Create dialog ─────────────────────────────────────────────────────────
    private fun showCreateDialog() = showGroupDialog(null)
    private fun showEditDialog(group: AppGroup) = showGroupDialog(group)

    private fun showGroupDialog(existing: AppGroup?) {
        val isEdit   = existing != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_group_editor, null)

        val etName   = dialogView.findViewById<EditText>(R.id.etGroupName)
        val etEmoji  = dialogView.findViewById<EditText>(R.id.etGroupEmoji)
        val appGrid  = dialogView.findViewById<LinearLayout>(R.id.appSelectionGrid)

        // Pre-fill for edit
        etName.setText(existing?.name  ?: "")
        etEmoji.setText(existing?.emoji ?: "📦")

        // App selection checkboxes
        val selected = mutableMapOf<WindowContentType, Boolean>()
        val titleMap = mutableMapOf<WindowContentType, String>()

        existing?.entries?.forEach { e ->
            selected[e.contentType] = true
            titleMap[e.contentType] = e.title
        }

        appGrid.removeAllViews()
        WindowContentType.entries.filter { it != WindowContentType.BLANK }.forEach { type ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity     = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val cb = CheckBox(this).apply {
                isChecked = selected[type] == true
                setOnCheckedChangeListener { _, c -> selected[type] = c }
            }
            val label = TextView(this).apply {
                text      = "${type.emoji}  ${type.label}"
                textSize  = 13f
                setTextColor(Color.parseColor("#1A1A1A"))
                setPadding(dp(8), 0, 0, 0)
            }
            row.addView(cb)
            row.addView(label)
            appGrid.addView(row)
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "Edit Group" else "New Group")
            .setView(dialogView)
            .setPositiveButton(if (isEdit) "Save" else "Create") { _, _ ->
                val name  = etName.text.toString().trim().ifEmpty { "Group" }
                val emoji = etEmoji.text.toString().trim().ifEmpty { "📦" }
                val entries = WindowContentType.entries
                    .filter { it != WindowContentType.BLANK && selected[it] == true }
                    .mapIndexed { i, type ->
                        // Tile layout: 2 columns
                        val col = i % 2; val row = i / 2
                        GroupEntry(
                            contentType = type,
                            title       = titleMap[type] ?: type.label,
                            xFraction   = if (col == 0) 0.03f else 0.45f,
                            yFraction   = 0.06f + row * 0.38f,
                            wFraction   = 0.48f,
                            hFraction   = 0.36f
                        )
                    }
                if (entries.isEmpty()) {
                    Toast.makeText(this, "Select at least one app", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (isEdit) {
                    existing!!.name = name; existing.emoji = emoji
                    existing.entries.clear(); existing.entries.addAll(entries)
                    groupManager.repo.save(existing)
                } else {
                    groupManager.createGroup(name, emoji, entries)
                }
                renderGroups()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Delete confirm ────────────────────────────────────────────────────────
    private fun confirmDelete(group: AppGroup) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${group.name}?")
            .setMessage("This group will be permanently deleted.")
            .setPositiveButton("Delete") { _, _ ->
                groupManager.deleteGroup(group.id)
                renderGroups()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun textAction(label: String, onClick: () -> Unit) = TextView(this).apply {
        text      = label
        textSize  = 11f
        setTextColor(Color.parseColor("#0067C0"))
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { onClick() }
    }

    private fun roundRect(
        fillColor: Int, radius: Int,
        stroke: Int? = null, strokeW: Int = 1
    ): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius.toFloat()
            setColor(fillColor)
            if (stroke != null) setStroke(dp(strokeW), stroke)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun timeAgo(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000      -> "just now"
            diff < 3_600_000   -> "${diff / 60_000}m ago"
            diff < 86_400_000  -> "${diff / 3_600_000}h ago"
            else               -> "${diff / 86_400_000}d ago"
        }
    }
}

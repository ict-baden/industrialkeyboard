package de.ictbaden.industrialkeyboard.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.config.Snippet
import de.ictbaden.industrialkeyboard.config.Snippets
import de.ictbaden.industrialkeyboard.config.UserSnippets
import java.io.IOException

/**
 * Snippets (Textbausteine). User mode (✎ in the keyboard's panel, the setup screen): the
 * organisation's snippets read-only, the user's own editable. Admin mode (only from the unlocked
 * settings – this activity is not exported, and no other entry point may pass `admin = true`):
 * edits `snippets.items` of the configuration, read-only while the EMM sets them.
 */
class SnippetsActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private val user by lazy { UserSnippets.get(this) }
    private lateinit var ui: Ui
    private lateinit var dialogs: Dialogs
    private lateinit var content: LinearLayout
    private var admin = false
    private val configListener: (KeyboardConfig) -> Unit = { render() }
    private val userListener: (List<Snippet>) -> Unit = { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        admin = intent.getBooleanExtra(EXTRA_ADMIN, false)
        ui = Ui(this)
        dialogs = Dialogs(this, ui)
        val toolbar = MaterialToolbar(this).apply {
            title = getString(if (admin) R.string.snippets_org else R.string.snippets_mine)
            navigationIcon = ContextCompat.getDrawable(this@SnippetsActivity, R.drawable.ic_back)
            setNavigationContentDescription(R.string.back)
            setNavigationOnClickListener { finish() }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32))
        }
        val scroll = NestedScrollView(this).apply { addView(content) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        toolbar.setBackgroundColor(ui.color(com.google.android.material.R.attr.colorSurfaceContainer))
        toolbar.navigationIcon?.setTint(ui.color(com.google.android.material.R.attr.colorOnSurface))
        store.addListener(configListener)
        user.addListener(userListener)
        if (!admin) {
            user.takeLoadError()?.let {
                dialogs.message(getString(R.string.snippets_mine), dialogs.withCode(getString(R.string.user_snippets_reset), ErrorCodes.USER_SNIPPETS_UNREADABLE))
            }
        }
        render()
    }

    override fun onDestroy() {
        store.removeListener(configListener)
        user.removeListener(userListener)
        super.onDestroy()
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        val c = store.config
        if (admin) renderAdmin(c) else renderUser(c)
    }

    private fun note(text: String): View = ui.card(listOf(ui.row(R.drawable.ic_info, text, null, onClick = null)))

    private fun preview(s: Snippet): String = listOfNotNull(Snippets.preview(s.text), s.shortcut).joinToString("  ·  ")

    private fun renderUser(c: KeyboardConfig) {
        if (!c.snippets.enabled) {
            content.addView(ui.section(getString(R.string.snippets_title)))
            content.addView(note(getString(R.string.snippets_off)))
            return
        }
        content.addView(ui.section(getString(R.string.snippets_org)))
        content.addView(
            ui.card(
                c.snippets.items.map { s -> ui.row(R.drawable.ic_lock, s.label, preview(s), onClick = null) }
                    .ifEmpty { listOf(ui.row(R.drawable.ic_info, getString(R.string.snippets_empty_admin), null, onClick = null)) },
            ),
        )
        content.addView(ui.section(getString(R.string.snippets_mine)))
        if (!c.snippets.allowUser) {
            content.addView(note(getString(R.string.snippets_user_off)))
            return
        }
        val mine = user.items
        content.addView(
            ui.card(
                mine.map { s -> ui.row(R.drawable.ic_snippets, s.label, preview(s)) { editUser(s) } } +
                    ui.row(R.drawable.ic_snippets, getString(R.string.snippet_add), null) { editUser(null) },
            ),
        )
    }

    /** Own snippets may not take a shortcut of the organisation's or of another own snippet. */
    private fun editUser(current: Snippet?) {
        if (current == null && user.items.size >= Snippets.MAX_USER) return full(Snippets.MAX_USER)
        val others = store.config.snippets.items + user.items.filter { it.id != current?.id }
        dialogs.editSnippet(current, taken(others), user = true, onSave = { label, text, shortcut ->
            saveUser { if (current == null) user.add(label, text, shortcut) else user.replace(current.copy(label = label, text = text, shortcut = shortcut)) }
        }, onDelete = current?.let { { saveUser { user.remove(it.id) } } })
    }

    /**
     * Runs a change of the own snippets; false (with a message) when it failed. Only the limit
     * check in [UserSnippets.add] reports the limit; a failed write or a list changed meanwhile
     * report that the snippet was not saved – the editor then stays open with the entered values.
     */
    private fun saveUser(change: () -> Unit): Boolean = try {
        change()
        true
    } catch (e: IllegalStateException) {
        full(Snippets.MAX_USER)
        false
    } catch (e: Exception) {
        if (e !is IOException && e !is IllegalArgumentException) throw e
        Log.w(TAG, "Own snippet not saved (${e.javaClass.simpleName})")
        dialogs.toast(dialogs.withCode(getString(R.string.snippet_save_failed), ErrorCodes.USER_SNIPPETS_SAVE_FAILED))
        false
    }

    private fun renderAdmin(c: KeyboardConfig) {
        val managed = Provisioning.snippetsManaged(this)
        content.addView(ui.section(getString(R.string.snippets_org)))
        if (!c.snippets.enabled) content.addView(note(getString(R.string.snippets_off)))
        if (managed) content.addView(note(getString(R.string.snippets_managed_note)))
        val rows = c.snippets.items.map { s ->
            if (managed) ui.row(R.drawable.ic_lock, s.label, preview(s), onClick = null) else ui.row(R.drawable.ic_snippets, s.label, preview(s)) { editAdmin(s) }
        }
        val add = if (managed) null else ui.row(R.drawable.ic_snippets, getString(R.string.snippet_add), null) { editAdmin(null) }
        val all = rows + listOfNotNull(add)
        content.addView(ui.card(all.ifEmpty { listOf(ui.row(R.drawable.ic_info, getString(R.string.snippets_empty_admin), null, onClick = null)) }))
    }

    private fun editAdmin(current: Snippet?) {
        val items = store.config.snippets.items
        if (current == null && items.size >= Snippets.MAX_ADMIN) return full(Snippets.MAX_ADMIN)
        dialogs.editSnippet(current, taken(items.filter { it.id != current?.id }), user = false, onSave = { label, text, shortcut ->
            store.update { config ->
                val list = config.snippets.items
                val updated = if (current == null) {
                    if (list.size >= Snippets.MAX_ADMIN) return@update config
                    list + Snippet(nextAdminId(list), label, text, shortcut)
                } else {
                    list.map { if (it.id == current.id) it.copy(label = label, text = text, shortcut = shortcut) else it }
                }
                config.copy(snippets = config.snippets.copy(items = updated))
            }
            true
        }, onDelete = current?.let { s ->
            { store.update { config -> config.copy(snippets = config.snippets.copy(items = config.snippets.items.filter { it.id != s.id })) } }
        })
    }

    private fun full(max: Int) = dialogs.toast(dialogs.withCode(resources.getQuantityString(R.plurals.snippet_error_full, max, max), ErrorCodes.SNIPPET_INVALID))

    private fun taken(others: List<Snippet>): Map<String, String> =
        others.mapNotNull { s -> s.shortcut?.let { Snippets.key(it) to s.label } }.toMap()

    companion object {
        private const val EXTRA_ADMIN = "admin"
        private const val TAG = "Snippets"

        /** [admin] only from the unlocked settings. */
        fun intent(context: Context, admin: Boolean): Intent = Intent(context, SnippetsActivity::class.java).putExtra(EXTRA_ADMIN, admin)

        /** `s<n>`, the next n no snippet of [items] uses (also the ids `s1`, `s2` … the JSON format gives). */
        internal fun nextAdminId(items: List<Snippet>): String {
            val used = items.map { it.id }.toSet()
            return generateSequence(1) { it + 1 }.map { "s$it" }.first { it !in used }
        }
    }
}

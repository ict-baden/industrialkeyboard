package de.ictbaden.industrialkeyboard.app

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.suggest.ImportException
import de.ictbaden.industrialkeyboard.suggest.ImportResult
import de.ictbaden.industrialkeyboard.suggest.PersonalDictionary
import de.ictbaden.industrialkeyboard.suggest.PersonalWord
import de.ictbaden.industrialkeyboard.suggest.PersonalWords
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

/**
 * The keyboard's own personal dictionary: words (with an optional shortcut and language) the user
 * adds here or imports from a Gboard export. Never anything typed. Long lists are filtered, at most
 * [MAX_ROWS] rows are drawn.
 */
class PersonalDictionaryActivity : AppCompatActivity() {

    private val dictionary by lazy { PersonalDictionary.get(this) }
    private val store by lazy { ConfigStore.get(this) }
    private lateinit var ui: Ui
    private lateinit var dialogs: Dialogs
    private lateinit var content: LinearLayout
    private lateinit var list: LinearLayout
    private var filter = ""
    private val main = Handler(Looper.getMainLooper())
    private val listener: (List<PersonalWord>) -> Unit = { renderList() }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::importFrom)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = Ui(this)
        dialogs = Dialogs(this, ui)
        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.personal_dictionary_title)
            navigationIcon = ContextCompat.getDrawable(this@PersonalDictionaryActivity, R.drawable.ic_back)
            setNavigationContentDescription(R.string.back)
            setNavigationOnClickListener { finish() }
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // The note card comes first: some space below the toolbar.
            setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(32))
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
        dictionary.addListener(listener)
        build()
    }

    override fun onDestroy() {
        dictionary.removeListener(listener)
        super.onDestroy()
    }

    private fun build() {
        content.addView(ui.card(listOf(ui.row(R.drawable.ic_info, getString(R.string.personal_dictionary_note), null, onClick = null))))
        content.addView(ui.section(getString(R.string.personal_dictionary_title)))
        content.addView(
            ui.card(
                listOf(
                    // The same switch as in the settings: a configuration saved by 2.1 has it off.
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.personal_dictionary_switch), getString(R.string.personal_dictionary_switch_summary), store.config.typing.personalDictionary) { on ->
                        store.update { it.copy(typing = it.typing.copy(personalDictionary = on)) }
                    },
                    ui.row(R.drawable.ic_text, getString(R.string.personal_dictionary_add), null) { edit(null) },
                    ui.row(R.drawable.ic_text, getString(R.string.personal_dictionary_import), getString(R.string.personal_dictionary_import_summary)) {
                        importFile.launch(arrayOf("application/zip", "application/x-zip-compressed", "text/plain", "application/octet-stream"))
                    },
                    ui.row(R.drawable.ic_text, getString(R.string.personal_dictionary_delete_all), null) { deleteAll() },
                ),
            ),
        )
        content.addView(ui.section(getString(R.string.personal_dictionary_section_words)))
        val search = ui.textField(getString(R.string.personal_dictionary_filter))
        search.editText?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                filter = s?.toString().orEmpty()
                renderList()
            }
        })
        content.addView(search)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        renderList()
    }

    private fun renderList() {
        if (!::list.isInitialized) return
        list.removeAllViews()
        val words = dictionary.words
        if (words.isEmpty()) {
            list.addView(ui.card(listOf(ui.row(R.drawable.ic_info, getString(R.string.personal_dictionary_empty), null, onClick = null))))
            return
        }
        val wanted = filter.trim().lowercase(Locale.ROOT)
        val hits = words.filter { w ->
            wanted.isEmpty() || w.word.lowercase(Locale.ROOT).contains(wanted) || w.shortcut?.lowercase(Locale.ROOT)?.contains(wanted) == true
        }.sortedBy { it.word.lowercase(Locale.ROOT) }
        val rows: List<View> = hits.take(MAX_ROWS).map { w -> ui.row(R.drawable.ic_text, w.word, subtitle(w)) { edit(w) } } +
            listOfNotNull(
                (hits.size - MAX_ROWS).takeIf { it > 0 }?.let { more ->
                    ui.row(R.drawable.ic_info, resources.getQuantityString(R.plurals.personal_dictionary_more, more, more), null, onClick = null)
                },
            )
        if (rows.isNotEmpty()) list.addView(ui.card(rows))
    }

    private fun subtitle(w: PersonalWord): String =
        listOfNotNull(w.shortcut, w.locale?.let(::languageName) ?: getString(R.string.personal_dictionary_all_languages)).joinToString("  ·  ")

    private fun languageName(tag: String): String = Locale.forLanguageTag(tag).getDisplayName(resources.configuration.locales[0]).ifEmpty { tag }

    /** Adds ([current] null) or edits a word; invalid input stays in the dialog with an error (IK-0111). */
    private fun edit(current: PersonalWord?) {
        if (current == null && dictionary.words.size >= PersonalWords.MAX_WORDS) {
            return dialogs.toast(dialogs.withCode(resources.getQuantityString(R.plurals.personal_dictionary_full, PersonalWords.MAX_WORDS, PersonalWords.MAX_WORDS), ErrorCodes.PERSONAL_DICTIONARY_SAVE_FAILED))
        }
        val word = ui.textField(getString(R.string.personal_dictionary_word_hint), current?.word.orEmpty(), helper = getString(R.string.personal_dictionary_word_helper))
        val shortcut = ui.textField(getString(R.string.personal_dictionary_shortcut_hint), current?.shortcut.orEmpty(), helper = getString(R.string.personal_dictionary_shortcut_helper))
        val language = ui.textField(getString(R.string.personal_dictionary_language_hint), current?.locale.orEmpty(), helper = getString(R.string.personal_dictionary_language_helper))
        val builder = MaterialAlertDialogBuilder(this)
            .setIcon(ui.icon(R.drawable.ic_text))
            .setTitle(R.string.personal_dictionary_edit_title)
            .setView(ScrollView(this).apply { addView(ui.form(word, shortcut, language)) })
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
        current?.let { w -> builder.setNeutralButton(R.string.personal_dictionary_delete) { _, _ -> save { dictionary.remove(w) } } }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            listOf(word, shortcut, language).forEach { it.error = null }
            val w = word.value.trim()
            val s = shortcut.value.trim().ifEmpty { null }
            val l = language.value.trim()
            val locale = if (l.isEmpty()) null else PersonalWords.normalizeLocale(l)
            val invalid = { message: String -> dialogs.withCode(message, ErrorCodes.PERSONAL_DICTIONARY_SAVE_FAILED) }
            when {
                !PersonalWords.validWord(w) -> word.error = invalid(getString(R.string.personal_dictionary_error_word))
                s != null && !PersonalWords.validShortcut(s) -> shortcut.error = invalid(getString(R.string.personal_dictionary_error_shortcut))
                l.isNotEmpty() && locale == null -> language.error = invalid(getString(R.string.personal_dictionary_error_language))
                else -> {
                    val saved = save { dictionary.put(PersonalWord(w, s, locale), replacing = current) }
                    if (saved) {
                        dialog.dismiss()
                        switchOn()
                    }
                }
            }
        }
    }

    /** Whoever adds words wants them offered: switches them on (a configuration saved by 2.1 has them off). */
    private fun switchOn() {
        if (store.config.typing.personalDictionary) return
        store.update { it.copy(typing = it.typing.copy(personalDictionary = true)) }
        content.removeAllViews()
        build()
        dialogs.toast(getString(R.string.personal_dictionary_switched_on))
    }

    private fun deleteAll() {
        val count = dictionary.words.size
        if (count == 0) return
        dialogs.confirm(getString(R.string.personal_dictionary_delete_all), resources.getQuantityString(R.plurals.personal_dictionary_delete_all_confirm, count, count), getString(R.string.personal_dictionary_delete)) {
            save { dictionary.clear() }
        }
    }

    /** Runs a change; false (with a message) when the list is full or the file could not be written. */
    private fun save(change: () -> Any?): Boolean = try {
        val result = change()
        if (result == false) dialogs.toast(dialogs.withCode(resources.getQuantityString(R.plurals.personal_dictionary_full, PersonalWords.MAX_WORDS, PersonalWords.MAX_WORDS), ErrorCodes.PERSONAL_DICTIONARY_SAVE_FAILED))
        result != false
    } catch (e: IOException) {
        Log.w(TAG, "Personal dictionary not saved (${e.javaClass.simpleName})")
        dialogs.toast(dialogs.withCode(getString(R.string.personal_dictionary_save_failed), ErrorCodes.PERSONAL_DICTIONARY_SAVE_FAILED))
        false
    }

    /** Reads and parses off the main thread (at most 1 MB), then adds and shows the summary. */
    private fun importFrom(uri: Uri) {
        Thread {
            val outcome = runCatching { PersonalWords.parseGboard(readLimited(uri)) }
            main.post { if (!isFinishing && !isDestroyed) finishImport(outcome) }
        }.start()
    }

    private fun finishImport(outcome: Result<ImportResult>) {
        val parsed = outcome.getOrElse { e ->
            val reason = if (e is ImportException) e.message.orEmpty() else e.javaClass.simpleName
            Log.i(TAG, "Import rejected ($reason)")
            dialogs.message(
                getString(R.string.personal_dictionary_import_failed),
                dialogs.withCode(getString(R.string.personal_dictionary_import_invalid, reason), ErrorCodes.PERSONAL_DICTIONARY_IMPORT_INVALID),
            )
            return
        }
        if (parsed.words.isEmpty() && parsed.skipped == 0) {
            // A valid export of an empty "Personal dictionary" (Gboard writes only the header then).
            dialogs.message(getString(R.string.personal_dictionary_import_done), getString(R.string.personal_dictionary_import_empty))
            return
        }
        var added = 0
        var duplicates = 0
        if (!save { dictionary.import(parsed.words).also { added = it.first; duplicates = it.second } }) return
        // Words the full list could not take count as skipped.
        val skipped = parsed.skipped + (parsed.words.size - added - duplicates)
        if (added > 0) switchOn()
        dialogs.message(getString(R.string.personal_dictionary_import_done), getString(R.string.personal_dictionary_import_result, added, duplicates, skipped))
    }

    private fun readLimited(uri: Uri): ByteArray {
        val input = contentResolver.openInputStream(uri) ?: throw IOException("not readable")
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > PersonalWords.MAX_FILE_BYTES) throw ImportException("file larger than 1 MB")
            }
            out.toByteArray()
        }
    }

    @VisibleForTesting
    internal fun importForTest(bytes: ByteArray) = finishImport(runCatching { PersonalWords.parseGboard(bytes) })

    private companion object {
        const val TAG = "PersonalDictionary"
        const val MAX_ROWS = 200
    }
}

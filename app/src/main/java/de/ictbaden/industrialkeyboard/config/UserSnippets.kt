package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The user's own snippets (`files/user-snippets.json`), entered in the app's snippet screen only –
 * never taken from what is typed in other apps, the clipboard or a selection. Kept apart from the
 * configuration: an MDM push, an import or `INSTALL_CONFIG` (which replace the whole
 * [KeyboardConfig]) never remove them, and export, QR code and *Reset to defaults* never contain
 * them. Process-wide like [ConfigStore]; the keyboard and the activities share it.
 */
class UserSnippets @VisibleForTesting internal constructor(private val file: File) {

    /**
     * Set when the file could not be read: it was moved to `user-snippets.json.invalid` and the
     * list starts empty. Declared before [items], which sets it while loading.
     */
    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    var items: List<Snippet> = load()
        private set

    private val listeners = CopyOnWriteArrayList<(List<Snippet>) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (List<Snippet>) -> Unit) = listeners.add(listener)

    fun removeListener(listener: (List<Snippet>) -> Unit) = listeners.remove(listener)

    /** [loadError] once, for the one message the screen shows; null afterwards. */
    @Synchronized
    fun takeLoadError(): String? = loadError.also { loadError = null }

    /**
     * Adds a snippet with the next free id `u<n>`. Throws [IllegalStateException] beyond
     * [Snippets.MAX_USER] and [IllegalArgumentException] for a snippet the editor would refuse.
     */
    @Synchronized
    fun add(label: String, text: String, shortcut: String?): Snippet {
        check(items.size < Snippets.MAX_USER) { "at most ${Snippets.MAX_USER} snippets" }
        val used = items.map { it.id }.toSet()
        val id = generateSequence(1) { it + 1 }.map { "u$it" }.first { it !in used }
        val snippet = valid(Snippet(id, label, text, shortcut))
        save(items + snippet)
        return snippet
    }

    /** Replaces the snippet with the same id; unknown ids are ignored. */
    @Synchronized
    fun replace(snippet: Snippet) {
        val checked = valid(snippet)
        if (items.none { it.id == checked.id }) return
        save(items.map { if (it.id == checked.id) checked else it })
    }

    @Synchronized
    fun remove(id: String) {
        if (items.none { it.id == id }) return
        save(items.filterNot { it.id == id })
    }

    /** Trimmed like the JSON format; refuses what [Snippets.problem] refuses, so the file always reads back. */
    private fun valid(s: Snippet): Snippet {
        val taken = items.filter { it.id != s.id }.mapNotNull { it.shortcut?.let(Snippets::key) }.toSet()
        Snippets.problem(s.label, s.text, s.shortcut, taken, user = true)?.let { throw IllegalArgumentException("invalid snippet: $it") }
        return s.copy(label = s.label.trim(), shortcut = s.shortcut?.trim()?.ifEmpty { null })
    }

    private fun load(): List<Snippet> {
        if (!file.exists()) return emptyList()
        return try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val version = root.optInt("version", VERSION)
            if (version > VERSION) throw ConfigException("version $version is newer than this app supports")
            val array = root.optJSONArray("items") ?: throw ConfigException("items missing")
            ConfigJson.parseSnippetItems(array, FILE_WHERE, Snippets.MAX_USER, user = true)
        } catch (e: Exception) {
            if (e !is IOException && e !is JSONException && e !is ConfigException) throw e
            broken(if (e is JSONException) "invalid JSON" else e.message.orEmpty())
        }
    }

    /** Keeps the broken file as `.invalid` (one copy, the latest) and starts empty. Never logs the texts. */
    private fun broken(reason: String): List<Snippet> {
        val backup = File(file.parentFile, "${file.name}.invalid")
        val kept = runCatching {
            file.copyTo(backup, overwrite = true)
            file.delete()
        }.isSuccess
        Log.e(TAG, "Own snippets could not be read, starting empty${if (kept) "; kept as ${backup.name}" else ""}: $reason")
        loadError = reason
        return emptyList()
    }

    private fun save(list: List<Snippet>) {
        val text = JSONObject().apply {
            put("version", VERSION)
            put("items", ConfigJson.writeSnippetItems(list))
        }.toString(2)
        // Like ConfigStore: written to a new file, synced, then renamed – a power loss leaves the old
        // or the new list, never half of one. (AtomicFile does the same, but cannot replace a file
        // on the file systems the unit tests run on.)
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("could not save ${file.name}")
        }
        items = list
        main.post { listeners.forEach { it(list) } }
    }

    companion object {
        const val FILE_NAME = "user-snippets.json"
        private const val VERSION = 1
        private const val FILE_WHERE = "user-snippets"
        private const val TAG = "UserSnippets"

        @Volatile
        private var instance: UserSnippets? = null

        fun get(context: Context): UserSnippets = instance ?: synchronized(this) {
            instance ?: UserSnippets(File(context.applicationContext.filesDir, FILE_NAME)).also { instance = it }
        }

        /** Forgets the cached instance so the next [get] reads the file again (tests). */
        @VisibleForTesting
        internal fun reset() {
            instance = null
        }
    }
}

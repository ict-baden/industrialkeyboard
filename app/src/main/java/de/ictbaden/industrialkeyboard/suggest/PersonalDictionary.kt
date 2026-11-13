package de.ictbaden.industrialkeyboard.suggest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The keyboard's own personal dictionary (`files/personal-dictionary.json`): words the user added
 * in the settings or imported from a Gboard export – never anything typed. Excluded from backup
 * (data_extraction_rules.xml excludes all files). Process-wide; the keyboard and the settings share it.
 */
class PersonalDictionary @VisibleForTesting internal constructor(private val file: File) {

    @Volatile
    var words: List<PersonalWord> = load()
        private set

    private val listeners = CopyOnWriteArrayList<(List<PersonalWord>) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (List<PersonalWord>) -> Unit) = listeners.add(listener)

    fun removeListener(listener: (List<PersonalWord>) -> Unit) = listeners.remove(listener)

    /** Adds or replaces (same word and language). False when the word is invalid or the list is full. */
    @Synchronized
    fun put(word: PersonalWord, replacing: PersonalWord? = null): Boolean {
        if (!PersonalWords.validWord(word.word.trim()) || (word.shortcut != null && !PersonalWords.validShortcut(word.shortcut))) return false
        val clean = word.copy(word = word.word.trim(), shortcut = word.shortcut?.trim()?.ifEmpty { null })
        val rest = words.filter { it != replacing && PersonalWords.key(it) != PersonalWords.key(clean) }
        if (rest.size >= PersonalWords.MAX_WORDS) return false
        save(rest + clean)
        return true
    }

    @Synchronized
    fun remove(word: PersonalWord) {
        if (word !in words) return
        save(words - word)
    }

    /** Adds an import; returns (added, duplicates). */
    @Synchronized
    fun import(imported: List<PersonalWord>): Pair<Int, Int> {
        val (merged, added, duplicates) = PersonalWords.merge(words, imported)
        if (added > 0 || merged != words) save(merged)
        return added to duplicates
    }

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(list: List<PersonalWord>) {
        val array = JSONArray()
        list.forEach { w ->
            array.put(JSONObject().apply {
                put("word", w.word)
                w.shortcut?.let { put("shortcut", it) }
                w.locale?.let { put("locale", it) }
            })
        }
        // Like UserSnippets: a new file, synced, then renamed – a power loss leaves the old or the new
        // list, never half of one (AtomicFile cannot replace a file on the unit tests' file systems).
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use {
            it.write(JSONObject().put("words", array).toString().toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("could not save ${file.name}")
        }
        words = list
        listeners.forEach { l -> main.post { l(list) } }
    }

    private fun load(): List<PersonalWord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("words") ?: JSONArray()
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val word = o.optString("word").trim()
                if (!PersonalWords.validWord(word)) null else PersonalWord(word, o.optString("shortcut").ifEmpty { null }, o.optString("locale").ifEmpty { null })
            }.take(PersonalWords.MAX_WORDS)
        }.onFailure { Log.w(TAG, "Personal dictionary unreadable (${it.javaClass.simpleName})") }.getOrDefault(emptyList())
    }

    companion object {
        private const val TAG = "PersonalDictionary"
        const val FILE = "personal-dictionary.json"

        @Volatile
        private var instance: PersonalDictionary? = null

        fun get(context: Context): PersonalDictionary = instance ?: synchronized(this) {
            instance ?: PersonalDictionary(File(context.applicationContext.filesDir, FILE)).also { instance = it }
        }
    }
}

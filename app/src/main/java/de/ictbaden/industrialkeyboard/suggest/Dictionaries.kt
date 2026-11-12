package de.ictbaden.industrialkeyboard.suggest

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Loads the built-in word list of the keyboard language (assets/dict/<tag>.dic, gzip) on a background
 * thread and keeps only that one in memory. Read only: nothing typed is stored.
 */
class Dictionaries(context: Context) {

    private val assets = context.applicationContext.assets
    private val available: Set<String> = runCatching { assets.list(DIR)?.map { it.removeSuffix(SUFFIX) }?.toSet() }.getOrNull().orEmpty()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var loadedTag: String? = null

    @Volatile
    var current: WordList? = null
        private set

    /** Next-word suggestions of the same language (curated, see tools/dictionaries/next/); empty if none. */
    @Volatile
    var next: NextWords = NextWords.EMPTY
        private set

    /** Asset tag for a language: exact (en-GB, pt-BR), else the language, else the first match; English as fallback. */
    fun tagFor(locale: Locale): String? {
        val language = locale.language.lowercase(Locale.ROOT)
        val full = if (locale.country.isEmpty()) language else "$language-${locale.country.uppercase(Locale.ROOT)}"
        return when {
            full in available -> full
            language in available -> language
            language == "en" && "en-US" in available -> "en-US"
            else -> available.sorted().firstOrNull { it.startsWith("$language-") } ?: if ("en-US" in available) "en-US" else null
        }
    }

    /** Makes sure the list for [locale] is (being) loaded; [onReady] runs on the main thread. */
    fun use(locale: Locale, onReady: () -> Unit) {
        val tag = tagFor(locale) ?: return
        if (tag == loadedTag) return
        loadedTag = tag
        current = null
        next = NextWords.EMPTY
        executor.execute {
            val started = SystemClock.elapsedRealtime()
            val list = runCatching { assets.open("$DIR/$tag$SUFFIX").use { WordList.read(it, locale) } }
                .onFailure { Log.w(TAG, "Dictionary $tag unreadable", it) }
                .getOrNull()
            val language = tag.substringBefore('-')
            val followers = runCatching { assets.open("$DIR/$language$NEXT_SUFFIX").use { NextWords.read(it, locale) } }.getOrDefault(NextWords.EMPTY)
            Log.i(TAG, "Dictionary $tag: ${list?.size ?: 0} words, ${followers.size} next-word entries in ${SystemClock.elapsedRealtime() - started} ms")
            main.post {
                if (loadedTag == tag) {
                    current = list
                    next = followers
                    onReady()
                }
            }
        }
    }

    fun close() = executor.shutdown()

    private companion object {
        const val TAG = "Suggestions"
        const val DIR = "dict"
        // Not ".txt.gz": the Android build unpacks .gz assets and drops the extension (seen on a Pixel 9 Pro).
        const val SUFFIX = ".dic"
        const val NEXT_SUFFIX = ".nxt"
    }
}

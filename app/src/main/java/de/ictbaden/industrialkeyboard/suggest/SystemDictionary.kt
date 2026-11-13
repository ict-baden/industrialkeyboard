package de.ictbaden.industrialkeyboard.suggest

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.UserDictionary
import android.util.Log
import java.util.concurrent.Executors

/**
 * Android's user dictionary (`UserDictionary.Words`): where Gboard and the system settings keep the
 * personal dictionary (words and shortcuts). Only the current input method may read it. Read off
 * the main thread when the keyboard starts and whenever it changes (ContentObserver), never per key.
 * Nothing is copied to the keyboard's files.
 */
class SystemDictionary(context: Context, private val onChange: () -> Unit) {

    private val resolver = context.applicationContext.contentResolver
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var words: List<PersonalWord> = emptyList()
        private set

    private var observing = false
    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    /** Starts reading and observing; idempotent. */
    fun start() {
        if (!observing) {
            observing = runCatching { resolver.registerContentObserver(UserDictionary.Words.CONTENT_URI, true, observer) }
                .onFailure { Log.i(TAG, "User dictionary not observable (${it.javaClass.simpleName})") }.isSuccess
        }
        reload()
    }

    fun stop() {
        if (observing) runCatching { resolver.unregisterContentObserver(observer) }
        observing = false
        words = emptyList()
    }

    fun close() {
        stop()
        executor.shutdown()
    }

    private fun reload() {
        if (executor.isShutdown) return
        executor.execute {
            val loaded = runCatching { query() }
                // Not the current input method, provider missing (some AOSP builds): just no words.
                .onFailure { Log.i(TAG, "User dictionary not readable (${it.javaClass.simpleName})") }
                .getOrDefault(emptyList())
            main.post {
                if (loaded != words) {
                    words = loaded
                    onChange()
                }
            }
        }
    }

    private fun query(): List<PersonalWord> {
        val columns = arrayOf(UserDictionary.Words.WORD, UserDictionary.Words.SHORTCUT, UserDictionary.Words.LOCALE, UserDictionary.Words.FREQUENCY)
        val cursor = resolver.query(UserDictionary.Words.CONTENT_URI, columns, null, null, null) ?: return emptyList()
        return cursor.use { c ->
            val list = ArrayList<PersonalWord>()
            while (c.moveToNext() && list.size < PersonalWords.MAX_WORDS) {
                val word = c.getString(0)?.trim().orEmpty()
                if (!PersonalWords.validWord(word)) continue
                val shortcut = c.getString(1)?.trim()?.ifEmpty { null }?.takeIf(PersonalWords::validShortcut)
                val locale = c.getString(2)?.trim()?.ifEmpty { null }?.let(PersonalWords::normalizeLocale)
                // Android stores 1–255 (250 for words added in the settings).
                val frequency = if (c.isNull(3)) PersonalWord.DEFAULT_FREQUENCY else c.getInt(3).coerceIn(1, 255)
                list += PersonalWord(word, shortcut, locale, frequency)
            }
            list
        }
    }

    private companion object {
        const val TAG = "SystemDictionary"
    }
}

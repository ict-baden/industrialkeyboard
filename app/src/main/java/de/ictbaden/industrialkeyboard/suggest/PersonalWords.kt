package de.ictbaden.industrialkeyboard.suggest

import de.ictbaden.industrialkeyboard.config.Snippet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * A word the user added on purpose: in the keyboard's personal dictionary (settings, Gboard import)
 * or in Android's user dictionary. Never something learnt from typing.
 *
 * [locale]: language tag ("de", "de-DE") or null for all languages. [frequency] 1–255.
 */
data class PersonalWord(val word: String, val shortcut: String? = null, val locale: String? = null, val frequency: Int = DEFAULT_FREQUENCY) {
    companion object {
        /** Personal words rank high: the user added them on purpose (Gboard does the same). */
        const val DEFAULT_FREQUENCY = 230
    }
}

/** What an import found: [words] to add, and lines that could not be used. */
data class ImportResult(val words: List<PersonalWord>, val skipped: Int)

/** Rejected import file (not a Gboard export, too large, …); [message] is English and names the reason. */
class ImportException(message: String) : Exception(message)

object PersonalWords {
    const val MAX_WORDS = 5000
    const val MAX_FILE_BYTES = 1024 * 1024
    const val MAX_WORD_LENGTH = 64
    const val MAX_SHORTCUT_LENGTH = 16
    private const val MAX_ZIP_ENTRIES = 32
    private const val GBOARD_HEADER = "# Gboard Dictionary"

    /**
     * Reads a Gboard export: the ZIP Gboard writes (with `dictionary.txt`) or that text file itself.
     * Gboard writes "shortcut<TAB>word<TAB>language_tag" (version 2 adds `pos_tag` and names the
     * columns in a `# Gboard Dictionary format:` line, which wins when present). Shortcut and
     * language may be empty, a line with a single value is that word, other `#` lines are comments.
     * Unpacks at most [MAX_FILE_BYTES], so a zip bomb stops early; entry names are only compared,
     * never used as paths.
     */
    fun parseGboard(bytes: ByteArray): ImportResult {
        if (bytes.size > MAX_FILE_BYTES) throw ImportException("file larger than 1 MB")
        val text = if (isZip(bytes)) dictionaryFromZip(bytes) else String(bytes, Charsets.UTF_8)
        return parseText(text)
    }

    /** The text format (also what a plain `dictionary.txt` contains). */
    fun parseText(text: String): ImportResult {
        val lines = text.removePrefix("\uFEFF").split('\n').map { it.removeSuffix("\r") }
        val first = lines.firstOrNull { it.isNotBlank() } ?: throw ImportException("the file is empty")
        val looksGboard = first.startsWith(GBOARD_HEADER) || lines.any { '\t' in it }
        if (!looksGboard) throw ImportException("not a Gboard dictionary export (dictionary.txt)")
        val columns = columns(lines)
        val words = ArrayList<PersonalWord>()
        var skipped = 0
        for (line in lines) {
            if (line.isBlank() || line.trimStart().startsWith("#")) continue
            val parts = line.split('\t').map { it.trim() }
            val filled = parts.filter { it.isNotEmpty() }
            var word = parts.getOrNull(columns.word).orEmpty()
            var shortcut = parts.getOrNull(columns.shortcut)?.ifEmpty { null }
            val locale = parts.getOrNull(columns.locale)?.ifEmpty { null }?.let(::normalizeLocale)
            // "Wort" alone (own lists, or a shortcut column without a word): that is the word.
            if (word.isEmpty() && shortcut != null && filled.size == 1) {
                word = shortcut
                shortcut = null
            }
            if (!validWord(word) || (shortcut != null && !validShortcut(shortcut))) {
                skipped++
                continue
            }
            if (words.size >= MAX_WORDS) {
                skipped++
                continue
            }
            words += PersonalWord(word, shortcut, locale)
        }
        return ImportResult(words, skipped)
    }

    private class Columns(val shortcut: Int, val word: Int, val locale: Int)

    /** Column order from the `format:` line; without one Gboard's: shortcut, word, language. */
    private fun columns(lines: List<String>): Columns {
        val format = lines.firstOrNull { it.startsWith(GBOARD_HEADER) && it.contains("format:") }
            ?.substringAfter("format:")?.split('\t', ',', ' ')?.map { it.trim().lowercase(Locale.ROOT) }?.filter { it.isNotEmpty() }
        val word = format?.indexOf("word") ?: -1
        if (format == null || word < 0) return Columns(0, 1, 2)
        val locale = format.indexOfFirst { it == "language_tag" || it == "locale" || it == "language" }
        return Columns(format.indexOf("shortcut"), word, locale)
    }

    fun validWord(word: String) = word.isNotEmpty() && word.length <= MAX_WORD_LENGTH && word.none { it == '\t' || it == '\n' || it == '\r' }

    fun validShortcut(shortcut: String) = shortcut.length in 1..MAX_SHORTCUT_LENGTH && shortcut.none { it.isWhitespace() }

    /** "de_DE" → "de-DE"; nonsense → null (= all languages). */
    fun normalizeLocale(tag: String): String? {
        val locale = Locale.forLanguageTag(tag.replace('_', '-'))
        return locale.toLanguageTag().takeIf { locale.language.isNotEmpty() && it != "und" }
    }

    /** Same word in the same language (case-insensitive): a duplicate. */
    fun key(w: PersonalWord) = w.word.lowercase(Locale.ROOT) + "\u0000" + (w.locale?.lowercase(Locale.ROOT) ?: "")

    /**
     * Adds [imported] to [existing]: duplicates keep the existing entry (but get its shortcut if they
     * had none); returns the new list and how many were new and how many duplicates.
     */
    fun merge(existing: List<PersonalWord>, imported: List<PersonalWord>): Triple<List<PersonalWord>, Int, Int> {
        val byKey = LinkedHashMap<String, PersonalWord>()
        existing.forEach { byKey[key(it)] = it }
        var added = 0
        var duplicates = 0
        for (w in imported) {
            val k = key(w)
            val old = byKey[k]
            when {
                old == null && byKey.size < MAX_WORDS -> {
                    byKey[k] = w
                    added++
                }
                old != null -> {
                    duplicates++
                    if (old.shortcut == null && w.shortcut != null) byKey[k] = old.copy(shortcut = w.shortcut)
                }
            }
        }
        return Triple(byKey.values.toList(), added, duplicates)
    }

    /** Words for the keyboard language: those of that language and those for all languages. */
    fun forLanguage(words: List<PersonalWord>, locale: Locale): List<PersonalWord> {
        val language = locale.language.lowercase(Locale.ROOT)
        return words.filter { w -> w.locale == null || Locale.forLanguageTag(w.locale).language.lowercase(Locale.ROOT) == language }
    }

    /** Entries for [WordList.withExtra]: (lower-case key, spelling, frequency). Only single words. */
    fun entries(words: List<PersonalWord>, locale: Locale): List<Triple<String, String, Int>> =
        words.filter { w -> w.word.none(Char::isWhitespace) }
            .map { Triple(it.word.lowercase(locale), it.word, it.frequency.coerceIn(1, 255)) }

    /**
     * The personal word whose [shortcut] was typed, as a snippet for the strip's pill (its text is a
     * macro, so braces are written as `{U+007B}`/`{U+007D}`).
     */
    fun shortcutSnippet(typed: String, words: List<PersonalWord>): Snippet? {
        if (typed.length < 2) return null
        val wanted = typed.lowercase(Locale.ROOT)
        val hit = words.firstOrNull { it.shortcut?.lowercase(Locale.ROOT) == wanted } ?: return null
        return Snippet(id = "word", label = hit.word, text = escapeMacro(hit.word), shortcut = hit.shortcut)
    }

    fun escapeMacro(text: String) = buildString {
        for (c in text) when (c) {
            '{' -> append("{U+007B}")
            '}' -> append("{U+007D}")
            else -> append(c)
        }
    }

    private fun isZip(bytes: ByteArray) = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    private fun dictionaryFromZip(bytes: ByteArray): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > MAX_ZIP_ENTRIES) throw ImportException("too many files in the ZIP")
                val name = entry.name.substringAfterLast('/').substringAfterLast('\\')
                if (entry.isDirectory || !name.equals("dictionary.txt", ignoreCase = true)) continue
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_FILE_BYTES) throw ImportException("dictionary.txt larger than 1 MB")
                }
                return out.toString(Charsets.UTF_8.name())
            }
        }
        throw ImportException("no dictionary.txt in the ZIP")
    }
}

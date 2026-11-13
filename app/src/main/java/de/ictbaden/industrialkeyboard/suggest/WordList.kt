package de.ictbaden.industrialkeyboard.suggest

import java.io.InputStream
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * A word list for suggestions (built-in, read only): words with a frequency 0–255, searchable by a
 * lower-case key. Built from the AOSP LatinIME dictionaries by tools/dictionaries/convert.py.
 * Nothing typed is ever added – the keyboard does not learn.
 */
class WordList private constructor(
    /** Lower-case keys, sorted. */
    private val keys: Array<String>,
    /** Spelling as in the dictionary (German nouns capitalised, names …), same order as [keys]. */
    private val words: Array<String>,
    private val freqs: IntArray,
) {
    val size: Int get() = keys.size

    /** The entry with exactly this lower-case [key], or -1. */
    fun indexOf(key: String): Int {
        val i = lowerBound(key)
        return if (i < keys.size && keys[i] == key) i else -1
    }

    fun word(index: Int) = words[index]

    fun frequency(index: Int) = freqs[index]

    /** Up to [limit] entries whose key starts with [prefix], most frequent first. */
    fun completions(prefix: String, limit: Int): List<Int> {
        if (prefix.isEmpty()) return emptyList()
        val best = ArrayList<Int>(limit + 1)
        var i = lowerBound(prefix)
        while (i < keys.size && keys[i].startsWith(prefix)) {
            insertByFrequency(best, i, limit)
            i++
        }
        return best
    }

    private fun insertByFrequency(best: ArrayList<Int>, index: Int, limit: Int) {
        val f = freqs[index]
        var pos = best.size
        while (pos > 0 && freqs[best[pos - 1]] < f) pos--
        if (pos >= limit) return
        best.add(pos, index)
        if (best.size > limit) best.removeAt(best.size - 1)
    }

    /**
     * This list plus the user's own words ([extra]: key, spelling, frequency). A word in both keeps
     * the higher frequency and the user's spelling. One merge of two sorted lists – done when the
     * personal words or the language change, never per key.
     */
    fun withExtra(extra: List<Triple<String, String, Int>>): WordList {
        if (extra.isEmpty()) return this
        val add = of(extra)
        val n = keys.size + add.keys.size
        val k = ArrayList<String>(n)
        val w = ArrayList<String>(n)
        val f = ArrayList<Int>(n)
        var i = 0
        var j = 0
        while (i < keys.size || j < add.keys.size) {
            val takeAdd = i >= keys.size || (j < add.keys.size && add.keys[j] <= keys[i])
            if (takeAdd) {
                val same = i < keys.size && add.keys[j] == keys[i]
                k += add.keys[j]
                w += add.words[j]
                f += if (same) maxOf(add.freqs[j], freqs[i]) else add.freqs[j]
                if (same) i++
                j++
            } else {
                k += keys[i]
                w += words[i]
                f += freqs[i]
                i++
            }
        }
        return WordList(k.toTypedArray(), w.toTypedArray(), f.toIntArray())
    }

    private fun lowerBound(key: String): Int {
        var lo = 0
        var hi = keys.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[mid] < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    companion object {
        /** Reads "word<TAB>frequency" lines (gzip). */
        fun read(input: InputStream, locale: Locale): WordList {
            // gzip as built by tools/dictionaries; plain text too, in case a build step unpacked it.
            val buffered = java.io.BufferedInputStream(input).apply { mark(2) }
            val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val stream = if (gzip) GZIPInputStream(buffered) else buffered
            val entries = stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val tab = line.indexOf('\t')
                    if (tab <= 0) null else Triple(line.substring(0, tab).lowercase(locale), line.substring(0, tab), line.substring(tab + 1).toIntOrNull() ?: 0)
                }.toList()
            }
            return of(entries)
        }

        /** For tests: (key, word, frequency). Duplicate keys keep the most frequent spelling. */
        fun of(entries: List<Triple<String, String, Int>>): WordList {
            val sorted = entries.sortedWith(compareBy<Triple<String, String, Int>> { it.first }.thenByDescending { it.third })
                .distinctBy { it.first }
            return WordList(
                Array(sorted.size) { sorted[it].first },
                Array(sorted.size) { sorted[it].second },
                IntArray(sorted.size) { sorted[it].third },
            )
        }
    }
}

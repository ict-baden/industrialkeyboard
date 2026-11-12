package de.ictbaden.industrialkeyboard.suggest

import java.io.InputStream
import java.util.Locale

/**
 * Likely next words after a word ("vielen" → "Dank", "Grüße"), from a small curated list per
 * language (tools/dictionaries/next/). The AOSP word lists contain no word pairs, and the keyboard
 * learns nothing from typing, so this stays a fixed list.
 */
class NextWords(private val followers: Map<String, List<String>>) {

    val size: Int get() = followers.size

    /** Up to [limit] words after [previous] (any case); the case of a capitalised [previous] at a sentence start does not matter. */
    fun after(previous: String, locale: Locale, limit: Int = 3): List<String> =
        followers[previous.lowercase(locale)].orEmpty().take(limit)

    companion object {
        val EMPTY = NextWords(emptyMap())

        /** Lines "word<TAB>next<TAB>next…", UTF-8. */
        fun read(input: InputStream, locale: Locale): NextWords = NextWords(
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val parts = line.split('\t').filter { it.isNotEmpty() }
                    if (parts.size < 2) null else parts[0].lowercase(locale) to parts.drop(1)
                }.toMap()
            },
        )
    }
}

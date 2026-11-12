package de.ictbaden.industrialkeyboard.suggest

import java.util.Locale
import kotlin.math.abs

/** One suggestion; [correction]: the typed word is spelt differently (typo), not just completed. */
data class Suggestion(val text: String, val correction: Boolean, val score: Int)

/** What the strip shows for the word being typed, and the word space may replace it with. */
data class Suggestions(val words: List<Suggestion>, val typedIsWord: Boolean, val autocorrection: String?) {
    companion object {
        val NONE = Suggestions(emptyList(), typedIsWord = false, autocorrection = null)
    }
}

/**
 * Completes and corrects the word being typed from a built-in [WordList]: prefix completions by
 * frequency, plus one-typo corrections (a neighbouring key, a missing, extra or swapped letter).
 * Pure and fast – called on every key, it must stay well below a few milliseconds.
 */
class Suggester(private val words: WordList, private val locale: Locale, letterRows: List<String>) {

    private val neighbours: Map<Char, Set<Char>> = neighbours(letterRows)
    private val alphabet: Set<Char> = letterRows.joinToString("").toSet()

    fun suggest(typed: String, limit: Int = 3): Suggestions {
        if (typed.isEmpty() || typed.length > MAX_WORD || typed.any { !it.isLetter() && it != '\'' && it != '-' }) return Suggestions.NONE
        val key = typed.lowercase(locale)
        val scores = HashMap<Int, Suggestion>()
        fun offer(index: Int, score: Int, correction: Boolean) {
            val existing = scores[index]
            if (existing == null || existing.score < score) scores[index] = Suggestion(words.word(index), correction, score)
        }

        val exact = words.indexOf(key)
        if (exact >= 0) offer(exact, words.frequency(exact) + TYPED_BONUS, correction = false)
        words.completions(key, COMPLETIONS).forEach { if (it != exact) offer(it, words.frequency(it) - completionPenalty(key, it), correction = false) }

        if (key.length >= 2) {
            for ((candidate, penalty) in edits(key)) {
                val i = words.indexOf(candidate)
                if (i >= 0 && i != exact) offer(i, words.frequency(i) - penalty, correction = true)
                // A typo early in a long word: also complete the corrected prefix, a bit weaker.
                if (key.length >= 3 && penalty == NEIGHBOUR_PENALTY) {
                    words.completions(candidate, 3).forEach { c -> if (c != exact) offer(c, words.frequency(c) - penalty - CORRECTED_COMPLETION_PENALTY, correction = true) }
                }
            }
        }

        val ranked = scores.values.sortedByDescending { it.score }
            .map { it.copy(text = withCase(typed, it.text)) }
            .distinctBy { it.text }
            .take(limit)
        val best = ranked.firstOrNull()
        val auto = best?.takeIf { it.correction && exact < 0 && key.length >= 3 && it.score >= AUTOCORRECT_MIN_SCORE && it.text.length >= key.length - 1 }?.text
        return Suggestions(ranked, typedIsWord = exact >= 0, autocorrection = auto)
    }

    /** Longer completions rank a little lower, so "Hal" offers "Hallo" before "Halbleiterindustrie". */
    private fun completionPenalty(key: String, index: Int) = (words.word(index).length - key.length).coerceAtMost(10) * 2

    /** Strings one typo away, with a penalty: neighbouring key cheaper than other edits. */
    private fun edits(key: String): List<Pair<String, Int>> = buildList {
        for (i in key.indices) {
            neighbours[key[i]]?.forEach { n -> add(key.substring(0, i) + n + key.substring(i + 1) to NEIGHBOUR_PENALTY) }
            add(key.removeRange(i, i + 1) to EDIT_PENALTY)
            if (i < key.length - 1) add(key.substring(0, i) + key[i + 1] + key[i] + key.substring(i + 2) to EDIT_PENALTY)
        }
        // A missing letter: only letters of the layout, to keep it small.
        for (i in 0..key.length) for (c in alphabet) add(key.substring(0, i) + c + key.substring(i) to EDIT_PENALTY)
    }

    /** "HAL" → "HALLO", "Hal" → "Hallo", "hal" → the dictionary spelling (German nouns stay capitalised). */
    private fun withCase(typed: String, word: String): String = when {
        typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(locale)
        typed.first().isUpperCase() -> word.replaceFirstChar { it.titlecase(locale) }
        else -> word
    }

    companion object {
        private const val MAX_WORD = 40
        private const val COMPLETIONS = 8
        private const val TYPED_BONUS = 60
        private const val NEIGHBOUR_PENALTY = 45
        private const val EDIT_PENALTY = 60
        private const val CORRECTED_COMPLETION_PENALTY = 40
        private const val AUTOCORRECT_MIN_SCORE = 90

        /** Keys next to each other on the letter rows (same row and the rows above and below). */
        fun neighbours(rows: List<String>): Map<Char, Set<Char>> {
            // Rows are indented like on the keyboard: half a key, then one and a half keys.
            val offsets = listOf(0f, 0.5f, 1.5f)
            val positions = rows.flatMapIndexed { r, row -> row.mapIndexed { i, c -> Triple(c, r, i + offsets.getOrElse(r) { 0f }) } }
            return positions.associate { (c, r, x) ->
                c to positions.filter { (o, r2, x2) -> o != c && abs(r2 - r) <= 1 && abs(x2 - x) <= if (r2 == r) 1f else 0.75f }.map { it.first }.toSet()
            }
        }
    }
}

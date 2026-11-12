package de.ictbaden.industrialkeyboard.suggest

/** Finds the word a next-word suggestion follows: the text before the cursor ends with "word ". */
object NextWordContext {

    /** The last word before a single trailing space, or null (no space, sentence end, a number …). */
    fun previousWord(before: String): String? {
        if (!before.endsWith(" ") || before.endsWith("  ")) return null
        val text = before.dropLast(1)
        val start = text.indexOfLast { !(it.isLetter() || it == '\'' || it == '-') } + 1
        val word = text.substring(start)
        return word.takeIf { it.isNotEmpty() && it.first().isLetter() }
    }
}

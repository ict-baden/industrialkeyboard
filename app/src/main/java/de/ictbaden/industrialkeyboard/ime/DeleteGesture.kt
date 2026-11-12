package de.ictbaden.industrialkeyboard.ime

/** Word boundaries for the delete gesture (swipe left from Backspace, like Gboard). */
object DeleteGesture {

    /**
     * Index in [before] (the text before the cursor) where the last [words] words start, together
     * with the spaces after them – deleting from there to the cursor removes exactly those words.
     */
    fun start(before: CharSequence, words: Int): Int {
        var i = before.length
        repeat(words) {
            while (i > 0 && before[i - 1].isWhitespace()) i--
            if (i == 0) return 0
            // A run of letters/digits is a word; a run of other characters (".", "!!") counts as one too.
            val wordChar = before[i - 1].isLetterOrDigit()
            while (i > 0 && !before[i - 1].isWhitespace() && before[i - 1].isLetterOrDigit() == wordChar) i--
        }
        return i
    }
}

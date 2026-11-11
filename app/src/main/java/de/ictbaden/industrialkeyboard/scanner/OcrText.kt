package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.OcrCharset
import de.ictbaden.industrialkeyboard.config.OcrMode
import de.ictbaden.industrialkeyboard.config.OcrSettings
import java.util.Locale

/**
 * Which recognised text lines count (`scanner.ocr`): cleanup by character set, length filter and,
 * in pattern mode, the regular expression. Pure, so it can be tested without a camera.
 */
object OcrText {

    /** The line after the cleanup of [OcrSettings.charset]. */
    fun clean(line: String, charset: OcrCharset): String {
        val text = line.trim()
        return when (charset) {
            OcrCharset.ANY -> text
            OcrCharset.DIGITS -> text.filter { it.isDigit() || it == ',' || it == '.' || it == '-' }
            OcrCharset.ALNUM -> text.filter { it.isLetterOrDigit() }
            OcrCharset.UPPER -> text.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
        }
    }

    /** The cleaned line, or `null` when it does not count. */
    fun accept(line: String, settings: OcrSettings, regex: Regex? = settings.pattern?.let(::Regex)): String? {
        val text = clean(line, settings.charset)
        if (text.isEmpty()) return null
        if (settings.minLength > 0 && text.length < settings.minLength) return null
        if (settings.maxLength > 0 && text.length > settings.maxLength) return null
        if (settings.mode == OcrMode.PATTERN && regex != null && !regex.matches(text)) return null
        return text
    }

    /**
     * Pattern mode: the same text has to come in [OcrSettings.confirmReads] frames in a row, because
     * OCR misreads single characters more often than a barcode decoder. Not thread-safe; one per
     * analyzer thread.
     */
    class Confirmer(private val needed: Int) {
        private var candidate: String? = null
        private var count = 0

        /** Feed the best text of a frame (or `null`); returns the text once it is confirmed. */
        fun offer(text: String?): String? {
            if (text == null) {
                candidate = null
                count = 0
                return null
            }
            count = if (text == candidate) count + 1 else 1
            candidate = text
            if (count < needed) return null
            candidate = null
            count = 0
            return text
        }
    }
}

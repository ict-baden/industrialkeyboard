package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.OcrCharset
import de.ictbaden.industrialkeyboard.config.OcrMode
import de.ictbaden.industrialkeyboard.config.OcrSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcrTextTest {

    @Test
    fun `charset cleanup`() {
        assertEquals("Lot: AB-12", OcrText.clean("  Lot: AB-12 ", OcrCharset.ANY))
        assertEquals("12.345,6-", OcrText.clean("Stand: 12.345,6 m³-", OcrCharset.DIGITS))
        assertEquals("SN4711ab", OcrText.clean("S/N 4711-ab", OcrCharset.ALNUM))
        assertEquals("SN4711AB", OcrText.clean("S/N 4711-ab", OcrCharset.UPPER))
    }

    @Test
    fun `pick mode filters only by length`() {
        val s = OcrSettings(minLength = 3, maxLength = 6)
        assertEquals("ABCD", OcrText.accept("ABCD", s))
        assertNull(OcrText.accept("AB", s))
        assertNull(OcrText.accept("ABCDEFG", s))
        assertNull(OcrText.accept("   ", OcrSettings()))
        // The pattern only counts in pattern mode.
        assertEquals("hello", OcrText.accept("hello", OcrSettings(pattern = "\\d+")))
    }

    @Test
    fun `pattern mode needs the whole cleaned line to match`() {
        val serial = OcrSettings(mode = OcrMode.PATTERN, pattern = "[A-Z]{2}\\d{6}", charset = OcrCharset.UPPER)
        assertEquals("AB123456", OcrText.accept("ab 123456", serial))
        assertNull(OcrText.accept("Serial AB123456", serial))
        val mhd = OcrSettings(mode = OcrMode.PATTERN, pattern = "\\d{2}\\.\\d{2}\\.\\d{4}")
        assertEquals("31.12.2027", OcrText.accept("31.12.2027", mhd))
        assertNull(OcrText.accept("MHD 31.12.2027", mhd))
    }

    @Test
    fun `confirmer needs the same text in a row`() {
        val c = OcrText.Confirmer(2)
        assertNull(c.offer("AB1"))
        assertNull(c.offer("AB7"))
        assertEquals("AB7", c.offer("AB7"))
        // After a confirmed read it starts over.
        assertNull(c.offer("AB7"))
        assertNull(c.offer(null))
        assertNull(c.offer("AB7"))
        assertEquals("X", OcrText.Confirmer(1).offer("X"))
    }
}

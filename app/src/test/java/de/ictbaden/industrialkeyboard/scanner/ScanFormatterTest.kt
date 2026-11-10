package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.DataSettings
import de.ictbaden.industrialkeyboard.config.Replacement
import de.ictbaden.industrialkeyboard.config.RuleAction
import de.ictbaden.industrialkeyboard.config.ScanRule
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.config.TextCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanFormatterTest {

    private fun text(raw: String, data: DataSettings, type: Symbology? = null) = ScanFormatter.format(raw, type, data)?.text

    @Test
    fun `defaults trim and keep everything else`() {
        val out = ScanFormatter.format("  4006381333931\r\n", Symbology.EAN13, DataSettings())!!
        assertEquals("4006381333931", out.text)
        assertEquals("", out.prefix)
        assertNull(out.suffix)
    }

    @Test
    fun `trim keeps a trailing GS separator`() {
        assertEquals("0104012345678901\u001D", text(" 0104012345678901\u001D", DataSettings()))
        assertEquals(" a ", text(" a ", DataSettings(trim = false)))
    }

    @Test
    fun `steps run in order`() {
        val data = DataSettings(
            stripLeading = 2,
            stripTrailing = 1,
            removeSpaces = true,
            replace = listOf(Replacement("-", "/"), Replacement("(\\d+)/(\\d+)", "$2-$1", regex = true)),
            case = TextCase.UPPER,
            prefix = "{TAB}",
            suffix = "{ENTER}",
        )
        val out = ScanFormatter.format("xx12 3-45ab9", null, data)!!
        assertEquals("45-123AB", out.text)
        assertEquals("{TAB}", out.prefix)
        assertEquals("{ENTER}", out.suffix)
    }

    @Test
    fun `gs replacement is a macro typed between the parts`() {
        val out = ScanFormatter.format("0104012345678901\u001D10AB12\u001D", null, DataSettings(gsReplacement = "{TAB}"))!!
        // Trimming keeps the GS at the end; the keyboard types TAB in its place.
        assertEquals(listOf("0104012345678901", "10AB12", ""), out.parts)
        assertEquals("{TAB}", out.gs)
        assertNull(ScanFormatter.format("a", null, DataSettings())!!.gs)
    }

    @Test
    fun `strip check digit of a GTIN`() {
        val data = DataSettings(rules = listOf(ScanRule(symbologies = setOf(Symbology.EAN13), stripTrailing = 1)))
        assertEquals("400638133393", text("4006381333931", data, Symbology.EAN13))
        assertEquals("ABC123", text("ABC123", data, Symbology.CODE128))
    }

    @Test
    fun `only EAN-13 with unmatched drop`() {
        val data = DataSettings(rules = listOf(ScanRule(symbologies = setOf(Symbology.EAN13))), unmatched = RuleAction.DROP)
        assertEquals("4006381333931", text("4006381333931", data, Symbology.EAN13))
        assertNull(text("ABC", data, Symbology.CODE128))
        assertNull(text("ABC", data, null))
        assertFalse(ScanFormatter.accepts("ABC", Symbology.QR, data))
        assertTrue(ScanFormatter.accepts("4006381333931", Symbology.EAN13, data))
    }

    @Test
    fun `first matching rule wins, drop rule`() {
        val data = DataSettings(
            prefix = "P",
            rules = listOf(
                ScanRule(startsWith = "TEST", action = RuleAction.DROP),
                ScanRule(pattern = "\\d{5}", prefix = "ZIP:", case = TextCase.LOWER),
                ScanRule(minLength = 3, maxLength = 4, suffix = "{TAB}"),
            ),
        )
        assertNull(ScanFormatter.format("TEST1", null, data))
        val zip = ScanFormatter.format("76131", null, data)!!
        assertEquals("ZIP:", zip.prefix)
        val short = ScanFormatter.format("ABCD", null, data)!!
        assertEquals("P", short.prefix)
        assertEquals("{TAB}", short.suffix)
        // No rule matches, unmatched = send: defaults.
        val other = ScanFormatter.format("ABCDEFG", null, data)!!
        assertEquals("P", other.prefix)
        assertNull(other.suffix)
    }

    @Test
    fun `rules match the trimmed text and unknown types only without symbologies`() {
        val rule = ScanRule(symbologies = setOf(Symbology.QR))
        assertFalse(ScanFormatter.matches(rule, "x", null))
        assertTrue(ScanFormatter.matches(ScanRule(pattern = "x"), "x", null))
        val data = DataSettings(rules = listOf(ScanRule(pattern = "\\d+", stripLeading = 1)))
        assertEquals("23", text(" 123 ", data))
    }

    @Test
    fun `rule overrides replace list`() {
        val data = DataSettings(
            replace = listOf(Replacement("a", "b")),
            rules = listOf(ScanRule(startsWith = "x", replace = emptyList())),
        )
        assertEquals("xa", text("xa", data))
        assertEquals("bb", text("ab", data))
    }
}

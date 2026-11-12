package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.Appearance
import de.ictbaden.industrialkeyboard.config.CustomKey
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeySettings
import de.ictbaden.industrialkeyboard.config.KeyStyle
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.LayoutSettings
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.NumericOrder
import de.ictbaden.industrialkeyboard.config.ProgrammableKey
import de.ictbaden.industrialkeyboard.config.ScannerSettings
import de.ictbaden.industrialkeyboard.config.Tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutsTest {

    private fun labels(row: KeyRow) = row.keys.mapNotNull { it.label }

    @Test
    fun `qwertz like Gboard - umlauts first on long press, digit hints`() {
        val layout = Layouts.alpha(KeyboardConfig(layout = LayoutSettings(alphabet = Alphabet.QWERTZ)))
        assertEquals("qwertzuiop".map { it.toString() }, labels(layout.rows[0]))
        assertEquals("asdfghjkl".map { it.toString() }, labels(layout.rows[1]))
        assertEquals("1", layout.rows[0].keys[0].hint)
        assertEquals(listOf("1"), layout.rows[0].keys[0].popup)
        // u carries the digit 7 first, then ü; a and s carry their symbol (@, #) first, then ä and ß.
        assertEquals(listOf("7", "ü"), layout.rows[0].keys[6].popup.take(2))
        assertEquals(listOf("@", "ä"), layout.rows[1].keys[0].popup.take(2))
        assertEquals(listOf("#", "ß"), layout.rows[1].keys[1].popup.take(2))
        assertEquals(10f, layout.units)
    }

    @Test
    fun `symbol hints on all letters like Gboard`() {
        val layout = Layouts.alpha(KeyboardConfig(layout = LayoutSettings(alphabet = Alphabet.QWERTZ)))
        assertEquals(listOf("@", "#", Layouts.currency(), "_", "&", "-", "+", "(", ")"), layout.rows[1].keys.map { it.hint })
        // Third row: shift, y x c v b n m, backspace.
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"), layout.rows[2].keys.drop(1).dropLast(1).map { it.hint })
        val off = Layouts.alpha(KeyboardConfig(appearance = Appearance(symbolHints = false)))
        assertTrue(off.rows[1].keys.all { it.hint == null })
    }

    @Test
    fun `currency follows the keyboard language`() {
        assertEquals("€", Layouts.currency(java.util.Locale.GERMANY))
        assertEquals("$", Layouts.currency(java.util.Locale.US))
        assertEquals("£", Layouts.currency(java.util.Locale.UK))
    }

    @Test
    fun `symbols page in Gboard order`() {
        val page = Layouts.symbols(KeyboardConfig())
        assertEquals("1234567890".map { it.toString() }, labels(page.rows[0]))
        assertEquals(listOf("@", "#", Layouts.currency(), "_", "&", "-", "+", "(", ")", "/"), labels(page.rows[1]))
        assertEquals("=\\<", page.rows[2].keys.first().label)
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"), labels(page.rows[2]).drop(1))
        assertEquals("?123", Layouts.symbols(KeyboardConfig(), page = 2).rows[2].keys.first().label)
    }

    @Test
    fun `comma offers settings and emoji first`() {
        val comma = Layouts.alpha(KeyboardConfig()).rows.last().keys.first { it.label == "," }
        assertEquals(listOf(Layouts.COMMA_SETTINGS, Layouts.COMMA_EMOJI), comma.popup.take(2))
    }

    @Test
    fun `qwertz with umlaut keys`() {
        val layout = Layouts.alpha(KeyboardConfig(layout = LayoutSettings(alphabet = Alphabet.QWERTZ, umlautKeys = true)))
        assertEquals("qwertzuiopü".map { it.toString() }, labels(layout.rows[0]))
        assertEquals("asdfghjklöä".map { it.toString() }, labels(layout.rows[1]))
        assertTrue("ß" in layout.rows[1].keys[1].popup)
        assertEquals(11f, layout.units)
    }

    @Test
    fun `number key like Gboard when switched on`() {
        fun first(config: KeyboardConfig) = Layouts.alpha(config).rows.last().keys.first()
        assertTrue(first(KeyboardConfig()).label != "?123")
        val on = KeyboardConfig(layout = LayoutSettings(symbolsKey = true))
        assertEquals("?123", first(on).label)
        assertEquals(KeyOutput.ShowTab(Tabs.SYMBOLS), first(on).output)
        // On the symbols page it leads back.
        val back = Layouts.symbols(on).rows.last().keys.first()
        assertEquals("ABC", back.label)
        assertEquals(KeyOutput.ShowTab(Tabs.ALPHA), back.output)
    }

    @Test
    fun `language key next to the space bar only when asked`() {
        val plain = Layouts.alpha(KeyboardConfig()).rows.last().keys
        val withKey = Layouts.alpha(KeyboardConfig(), languageKey = true).rows.last().keys
        assertTrue(plain.none { it.output == KeyOutput.Language })
        assertEquals(1, withKey.count { it.output == KeyOutput.Language })
    }

    @Test
    fun `number row and function row add rows without shrinking letters`() {
        val plain = Layouts.alpha(KeyboardConfig())
        val extended = Layouts.alpha(KeyboardConfig(appearance = Appearance(numberRow = true, functionRow = true)))
        assertEquals(plain.rows.size + 2, extended.rows.size)
        assertEquals(plain.units, extended.units)
        assertEquals((1..12).map { "F$it" }, labels(extended.rows[0]))
        assertEquals(null, extended.rows[2].keys[0].hint)
    }

    @Test
    fun `bottom row follows the field and navigation`() {
        val email = Layouts.alpha(KeyboardConfig(), AlphaVariant.EMAIL).rows.last()
        assertTrue(email.keys.any { it.label == "@" })
        val url = Layouts.alpha(KeyboardConfig(), AlphaVariant.URL).rows.last()
        assertTrue(url.keys.any { ".com" in it.popup })
        val classic = Layouts.alpha(KeyboardConfig(layout = LayoutSettings(navigation = Navigation.KEYS))).rows.last()
        assertEquals(KeyOutput.ShowTab("symbols"), classic.keys.first().output)
        val scan = Layouts.alpha(KeyboardConfig(scanner = ScannerSettings(scanKey = true))).rows.last()
        assertTrue(scan.keys.any { it.icon == KeyIcon.SCAN })
    }

    @Test
    fun `numeric pad order and programmable keys`() {
        val config = KeyboardConfig(keys = KeySettings(ProgrammableKey.DEFAULTS + ("numeric.p1" to "{TAB}")))
        val phone = Layouts.numeric(config)
        assertEquals(listOf("1", "2", "3"), phone.rows[0].keys.drop(1).take(3).map { it.label })
        assertEquals(KeyIcon.TAB, phone.rows[0].keys[0].icon)
        val calculator = Layouts.numeric(config.copy(layout = LayoutSettings(numericOrder = NumericOrder.CALCULATOR)))
        assertEquals(listOf("7", "8", "9"), calculator.rows[0].keys.drop(1).take(3).map { it.label })
        // Dial pads always use telephone order.
        val dial = Layouts.numeric(config.copy(layout = LayoutSettings(numericOrder = NumericOrder.CALCULATOR)), NumericVariant.PHONE)
        assertEquals(listOf("+", "1", "2", "3"), dial.rows[0].keys.take(4).map { it.label })
        assertEquals(4, Layouts.numeric(config, NumericVariant.PIN).rows[0].keys.size)
        assertEquals("/", Layouts.numeric(config, NumericVariant.DATE).rows[0].keys[0].label)
    }

    @Test
    fun `function keys with shift become F13 to F24`() {
        val plain = Layouts.function()
        assertEquals((1..6).map { "F$it" }, labels(plain.rows[0]))
        val shifted = Layouts.function(shifted = true)
        assertEquals((13..18).map { "F$it" }, labels(shifted.rows[0]))
        val f24 = shifted.rows[1].keys.last().output as KeyOutput.Macro
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_F12, KeyEvent.META_SHIFT_ON), f24.steps.single())
        assertEquals(KeyOutput.Shift, plain.rows[2].keys[0].output)
    }

    @Test
    fun `custom layouts turn macros into keys`() {
        val layout = Layouts.custom(
            CustomLayout(
                id = "wms",
                label = "WMS",
                rows = listOf(listOf(CustomKey("OK", "OK{ENTER}", 2f, KeyStyle.ACCENT), CustomKey("", "{TAB}"), CustomKey("A", "A"))),
            ),
        )
        val keys = layout.rows.single().keys
        assertEquals("OK", keys[0].label)
        assertEquals(KeyStyle.ACCENT, keys[0].style)
        assertEquals(2f, keys[0].width)
        assertEquals(KeyIcon.TAB, keys[1].icon)
        assertEquals(KeyOutput.Text("A"), keys[2].output)
        assertEquals("WMS", layout.name)
    }
}

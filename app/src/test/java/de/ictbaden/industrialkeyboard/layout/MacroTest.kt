package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.config.QuickToggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTest {

    @Test
    fun `quick toggle macros`() {
        assertEquals(listOf(MacroStep.Toggle(QuickToggle.GLOVE)), Macro.parse("{TOGGLE:glove}"))
        assertEquals(listOf(MacroStep.Toggle(QuickToggle.SCANNER)), Macro.parse("{toggle: Scanner }"))
        assertEquals("Sound", Macro.label(Macro.parse("{TOGGLE:sound}")))
        // Keys show the tile icon, not the English label.
        assertEquals(KeyIcon.SOUND, Macro.icon(Macro.parse("{TOGGLE:sound}")))
        assertEquals(KeyIcon.SCANNER_TOGGLE, Macro.icon(Macro.parse("{TOGGLE:scanner}")))
        assertNull(Macro.icon(Macro.parse("{TOGGLE:glove}X")))
        assertFalse(Macro.isValid("{TOGGLE:torch}"))
        assertFalse(Macro.isValid("{TOGGLE:}"))
    }

    @Test
    fun `text follows a step`() {
        // The snippet from examples/snippets.json: "Lager Süd" comes after the Enter (HC20 test).
        val regards = Macro.parse("Mit freundlichen Grüßen{ENTER}Lager Süd")
        assertTrue(Macro.textFollows(regards, 1))
        assertFalse(Macro.textFollows(regards, 2))
        // A command in between types nothing, the text after it still counts.
        assertTrue(Macro.textFollows(Macro.parse("{ENTER}{TOGGLE:glove}X"), 0))
        // A scan suffix or an "OK{ENTER}" key: nothing after the Enter, it stays a key.
        assertFalse(Macro.textFollows(Macro.parse("OK{ENTER}"), 1))
        assertFalse(Macro.textFollows(Macro.parse("{ENTER}{SCAN}"), 0))
        assertFalse(Macro.textFollows(emptyList(), 0))
    }

    @Test
    fun `text and keys`() {
        assertEquals(
            listOf(MacroStep.Text("OK"), MacroStep.Key(KeyEvent.KEYCODE_ENTER)),
            Macro.parse("OK{ENTER}"),
        )
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_F1 + 9)), Macro.parse("{f10}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_TAB), MacroStep.Key(KeyEvent.KEYCODE_TAB)), Macro.parse("{TAB}{tab}"))
    }

    @Test
    fun `function keys above F12 use shift`() {
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_F1, KeyEvent.META_SHIFT_ON)), Macro.parse("{F13}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_F12, KeyEvent.META_SHIFT_ON)), Macro.parse("{F24}"))
        assertEquals("F24", Macro.label(Macro.parse("{F24}")))
        assertEquals("F5", Macro.label(Macro.parse("{F5}")))
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{F25}") }
    }

    @Test
    fun `text tokens and unicode merge into the text`() {
        assertEquals(listOf(MacroStep.Text("ä{x} ")), Macro.parse("{U+00E4}{LBRACE}x{RBRACE}{SPACE}"))
        assertEquals(listOf(MacroStep.Text("€")), Macro.parse("{HEX:20AC}"))
    }

    @Test
    fun `commands and tab switching`() {
        assertEquals(
            listOf(MacroStep.Command(KeyCommand.SCAN), MacroStep.ShowTab("numeric"), MacroStep.Command(KeyCommand.HIDE)),
            Macro.parse("{SCAN}{show:numeric}{hide}"),
        )
    }

    @Test
    fun `invalid tokens are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{TABB}") }
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{TAB") }
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{U+D800}") }
        assertFalse(Macro.isValid("{nope}"))
        assertTrue(Macro.isValid("plain text"))
    }

    @Test
    fun `labels and icons`() {
        assertEquals("OK", Macro.label(Macro.parse("OK{ENTER}")))
        assertEquals("Esc", Macro.label(Macro.parse("{ESC}")))
        assertEquals("Scan", Macro.label(Macro.parse("{SCAN}")))
        assertEquals(KeyIcon.TAB, Macro.icon(Macro.parse("{TAB}")))
        assertEquals(KeyIcon.SCAN, Macro.icon(Macro.parse("{SCAN}")))
        assertNull(Macro.icon(Macro.parse("A{TAB}")))
    }

    @Test
    fun `edit opens the text editing panel`() {
        assertEquals(listOf(MacroStep.Command(KeyCommand.TEXT_EDIT)), Macro.parse("{EDIT}"))
        assertEquals(listOf(MacroStep.Command(KeyCommand.TEXT_EDIT)), Macro.parse("{edit}"))
        assertEquals(KeyIcon.TEXT_EDIT, Macro.icon(Macro.parse("{EDIT}")))
        assertEquals("Edit", Macro.label(Macro.parse("{EDIT}")))
        val error = assertThrows(IllegalArgumentException::class.java) { Macro.parse("{EDITT}") }
        assertEquals("unknown token {EDITT}", error.message)
    }
}

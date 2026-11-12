package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.config.Appearance
import de.ictbaden.industrialkeyboard.config.CustomKey
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeySettings
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.ProgrammableKey
import de.ictbaden.industrialkeyboard.config.TabSettings
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Ctrl, Win and Alt for remote desktop: chords, macros and layouts. */
class ModifierKeysTest {

    private val ctrl = ModifierKey.CTRL.meta
    private val alt = ModifierKey.ALT.meta
    private val win = ModifierKey.WIN.meta

    @Test
    fun `chord presses modifiers around the key like a PC keyboard`() {
        assertEquals(
            listOf(
                ChordEvent(true, KeyEvent.KEYCODE_CTRL_LEFT, ctrl),
                ChordEvent(true, KeyEvent.KEYCODE_ALT_LEFT, ctrl or alt),
                ChordEvent(true, KeyEvent.KEYCODE_FORWARD_DEL, ctrl or alt),
                ChordEvent(false, KeyEvent.KEYCODE_FORWARD_DEL, ctrl or alt),
                ChordEvent(false, KeyEvent.KEYCODE_ALT_LEFT, ctrl),
                ChordEvent(false, KeyEvent.KEYCODE_CTRL_LEFT, 0),
            ),
            KeyChords.events(KeyEvent.KEYCODE_FORWARD_DEL, ctrl or alt),
        )
    }

    @Test
    fun `shift stays a meta state only, so F13 is sent as before`() {
        val shift = KeyEvent.META_SHIFT_ON
        assertEquals(
            listOf(ChordEvent(true, KeyEvent.KEYCODE_F1, shift), ChordEvent(false, KeyEvent.KEYCODE_F1, shift)),
            KeyChords.events(KeyEvent.KEYCODE_F1, shift),
        )
        val ctrlShift = KeyChords.events(KeyEvent.KEYCODE_ESCAPE, ctrl or shift)
        assertEquals(ChordEvent(true, KeyEvent.KEYCODE_CTRL_LEFT, ctrl or shift), ctrlShift.first())
        assertEquals(ChordEvent(false, KeyEvent.KEYCODE_CTRL_LEFT, shift), ctrlShift.last())
    }

    @Test
    fun `win alone is one press of the meta key`() {
        assertEquals(
            listOf(ChordEvent(true, KeyEvent.KEYCODE_META_LEFT, win), ChordEvent(false, KeyEvent.KEYCODE_META_LEFT, win)),
            KeyChords.events(KeyEvent.KEYCODE_META_LEFT, win),
        )
    }

    @Test
    fun `characters map to US key codes`() {
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_C), KeyChords.keyFor('c'))
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_Z, KeyEvent.META_SHIFT_ON), KeyChords.keyFor('Z'))
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_7), KeyChords.keyFor('7'))
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_PERIOD), KeyChords.keyFor('.'))
        assertEquals(MacroStep.Key(KeyEvent.KEYCODE_BACKSLASH), KeyChords.keyFor('\\'))
        assertNull(KeyChords.keyFor('ä'))
        assertNull(KeyChords.keyFor('€'))
    }

    @Test
    fun `macro combinations`() {
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_C, ctrl)), Macro.parse("{CTRL+C}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_F4, alt)), Macro.parse("{alt+f4}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_R, win)), Macro.parse("{Win+R}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_MOVE_END, ctrl or alt)), Macro.parse("{CTRL+ALT+END}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_ESCAPE, ctrl or KeyEvent.META_SHIFT_ON)), Macro.parse("{CTRL+SHIFT+ESC}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_F1, alt or KeyEvent.META_SHIFT_ON)), Macro.parse("{ALT+F13}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_SPACE, win)), Macro.parse("{WIN+SPACE}"))
        assertEquals(listOf(MacroStep.Key(KeyEvent.KEYCODE_META_LEFT), MacroStep.Text("notepad"), MacroStep.Key(KeyEvent.KEYCODE_ENTER)), Macro.parse("{WIN}notepad{ENTER}"))
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{CTRL+}") }
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{HYPER+C}") }
        assertThrows(IllegalArgumentException::class.java) { Macro.parse("{CTRL+ÄÖ}") }
    }

    @Test
    fun `combination labels`() {
        assertEquals("Ctrl+Alt+End", Macro.label(Macro.parse("{CTRL+ALT+END}")))
        assertEquals("Alt+F4", Macro.label(Macro.parse("{ALT+F4}")))
        assertEquals("Ctrl+Shift+Esc", Macro.label(Macro.parse("{CTRL+SHIFT+ESC}")))
        assertEquals("Win+R", Macro.label(Macro.parse("{WIN+R}")))
        assertEquals("Win", Macro.label(Macro.parse("{WIN}")))
        assertEquals("F13", Macro.label(Macro.parse("{F13}")))
        // Ctrl+← is not the plain arrow key
        assertNull(Macro.icon(Macro.parse("{CTRL+LEFT}")))
    }

    @Test
    fun `modifier row sits above the letters in their width`() {
        val plain = Layouts.alpha(KeyboardConfig())
        val layout = Layouts.alpha(KeyboardConfig(appearance = Appearance(modifierRow = true)))
        assertEquals(plain.rows.size + 1, layout.rows.size)
        assertEquals(plain.units, layout.units)
        val row = layout.rows[0]
        assertEquals(listOf("Ctrl", "Win", "Alt", "Esc", "Del"), row.keys.mapNotNull { it.label })
        assertEquals(KeyOutput.Modifier(ModifierKey.CTRL), row.keys[0].output)
        assertEquals(KeyIcon.TAB, row.keys[4].icon)
        assertEquals(plain.units, row.units, 0.001f)
    }

    @Test
    fun `function tab gets a modifier row and every page grows with it`() {
        assertEquals(4, Layouts.function().rows.size)
        val fn = Layouts.function(modifiers = true)
        assertEquals(5, fn.rows.size)
        assertEquals(ModifierKey.entries.map { KeyOutput.Modifier(it) }, fn.rows[0].keys.take(3).map { it.output })
        assertEquals(listOf("Ctrl", "Win", "Alt", "Menu", "Print", "Pause"), fn.rows[0].keys.map { it.label })
        assertEquals(KeyOutput.Macro(listOf(MacroStep.Key(KeyEvent.KEYCODE_SYSRQ))), fn.rows[0].keys[4].output)
        assertEquals(fn.rows[1].units, fn.rows[0].units, 0.001f)
        assertEquals(5, KeyboardRenderer.heightRows(KeyboardConfig(appearance = Appearance(functionModifiers = true))))
        assertEquals(4, KeyboardRenderer.heightRows(KeyboardConfig(appearance = Appearance(functionModifiers = true), tabs = TabSettings(function = false))))
        assertEquals(4, KeyboardRenderer.heightRows(KeyboardConfig()))
    }

    @Test
    fun `a key whose macro is just a modifier becomes sticky`() {
        val config = KeyboardConfig(keys = KeySettings(ProgrammableKey.DEFAULTS + ("alpha.p1" to "{CTRL}")))
        // P1 sits right of the ?123 key.
        val bottom = Layouts.alpha(config).rows.last()
        assertEquals(KeyOutput.Modifier(ModifierKey.CTRL), bottom.keys[1].output)
        assertEquals("Ctrl", bottom.keys[1].label)
        val custom = Layouts.custom(
            CustomLayout("rdp", "RDP", listOf(listOf(CustomKey("Strg", "{CTRL}"), CustomKey("Task-Manager", "{CTRL+SHIFT+ESC}")))),
        )
        assertEquals(KeyOutput.Modifier(ModifierKey.CTRL), custom.rows[0].keys[0].output)
        assertEquals("Strg", custom.rows[0].keys[0].label)
        assertEquals(KeyOutput.Macro(listOf(MacroStep.Key(KeyEvent.KEYCODE_ESCAPE, ctrl or KeyEvent.META_SHIFT_ON))), custom.rows[0].keys[1].output)
    }
}

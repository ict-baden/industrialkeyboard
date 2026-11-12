package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.layout.EditAction
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyIcon
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** What TalkBack says for keys (German keyboard texts). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "de")
class KeyDescriberTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val describer get() = KeyDescriber(context)
    private lateinit var alpha: KeyboardLayout

    @Before
    fun setUp() {
        Alphabet.subtypeLocale = Locale.GERMANY
        alpha = Layouts.alpha(KeyboardConfig())
    }

    @After
    fun tearDown() {
        Alphabet.subtypeLocale = null
    }

    private fun KeyboardLayout.key(predicate: (Key) -> Boolean): Key = rows.flatMap { it.keys }.first(predicate)

    private fun letter(c: String) = alpha.key { it.letter && it.label == c }

    @Test
    fun `shift says its state`() {
        val shift = alpha.key { it.output == KeyOutput.Shift }
        assertEquals("Umschalttaste", describer.describe(shift, KeyDescriber.KeyContext(shift = ShiftState.OFF)))
        assertEquals("Umschalttaste an", describer.describe(shift, KeyDescriber.KeyContext(shift = ShiftState.ONCE)))
        assertEquals("Feststelltaste an", describer.describe(shift, KeyDescriber.KeyContext(shift = ShiftState.LOCKED)))
        assertEquals("Feststellen", describer.longPressLabel(shift, KeyDescriber.KeyContext()))
    }

    @Test
    fun `enter says the action of the field`() {
        val enter = alpha.key { it.output == KeyOutput.Enter }
        assertEquals("Weiter", describer.describe(enter, KeyDescriber.KeyContext(enterAction = EditorInfo.IME_ACTION_NEXT)))
        assertEquals("Buchen", describer.describe(enter, KeyDescriber.KeyContext(enterAction = EditorInfo.IME_ACTION_DONE, enterLabel = "Buchen")))
        val plain = KeyDescriber.enterAction(EditorInfo.IME_ACTION_NEXT or EditorInfo.IME_FLAG_NO_ENTER_ACTION)
        assertEquals(EditorInfo.IME_ACTION_UNSPECIFIED, plain)
        assertEquals("Eingabe", describer.describe(enter, KeyDescriber.KeyContext(enterAction = plain)))
        // An {ENTER} macro is a plain Enter, whatever the field's action.
        val macro = Key(icon = KeyIcon.ENTER, output = KeyOutput.Macro(Macro.parse("{ENTER}")))
        assertEquals("Eingabe", describer.describe(macro, KeyDescriber.KeyContext(enterAction = EditorInfo.IME_ACTION_SEND)))
    }

    @Test
    fun `space says the keyboard language and switches keyboards on a long press`() {
        val space = alpha.key { it.output == KeyOutput.Space }
        assertEquals("Leertaste, Deutsch", describer.describe(space, KeyDescriber.KeyContext()))
        assertEquals("Tastatur wechseln", describer.longPressLabel(space, KeyDescriber.KeyContext()))
        assertNull(describer.longPressLabel(letter("q"), KeyDescriber.KeyContext()))
    }

    @Test
    fun `letters say capitals while shift is on`() {
        assertEquals("q", describer.describe(letter("q"), KeyDescriber.KeyContext()))
        assertEquals("Großbuchstabe Q", describer.describe(letter("q"), KeyDescriber.KeyContext(shift = ShiftState.ONCE)))
        // German: ä first on a; the alternatives are typed as capitals while shift is on.
        assertEquals("ä", describer.alternatives(letter("a"), KeyDescriber.KeyContext()).first())
        assertEquals("Ä", describer.alternatives(letter("a"), KeyDescriber.KeyContext(shift = ShiftState.ONCE)).first())
    }

    @Test
    fun `obscured password keys say only Key`() {
        val c = KeyDescriber.KeyContext(obscure = true)
        assertEquals("Taste", describer.describe(letter("q"), c))
        assertEquals("Löschen", describer.describe(alpha.key { it.output == KeyOutput.Backspace }, c))
        assertEquals("Zeichen 1", describer.alternativeLabel("ä", 0, c))
        // Digits on the number pad, too; Enter and Shift keep their names.
        val digit = Layouts.numeric(KeyboardConfig()).key { it.label == "7" }
        assertEquals("Taste", describer.describe(digit, c))
        assertEquals("Umschalttaste", describer.describe(alpha.key { it.output == KeyOutput.Shift }, c))
        assertEquals("Eingabe", describer.describe(alpha.key { it.output == KeyOutput.Enter }, c))
    }

    @Test
    fun `space is obscured in password fields, too`() {
        val c = KeyDescriber.KeyContext(obscure = true)
        // The space bar with the language label, a space key with the icon, a text key typing a space.
        assertEquals("Taste", describer.describe(alpha.key { it.output == KeyOutput.Space }, c))
        assertEquals("Taste", describer.describe(Key(icon = KeyIcon.SPACE, output = KeyOutput.Space), c))
        assertEquals("Taste", describer.describe(Key(label = " ", output = KeyOutput.Text(" ")), c))
        // Long press still switches keyboards.
        assertEquals(describer.longPressLabel(alpha.key { it.output == KeyOutput.Space }, KeyDescriber.KeyContext()), describer.longPressLabel(alpha.key { it.output == KeyOutput.Space }, c))
    }

    @Test
    fun `forward delete has its own spoken name`() {
        val del = Layouts.function().key { it.label == "Del" }
        val edit = KeyboardRenderer.textEditingLayout(context).key { (it.output as? KeyOutput.Edit)?.action == EditAction.DELETE }
        assertEquals("Entf", describer.describe(del, KeyDescriber.KeyContext()))
        assertEquals("Entf", describer.describe(edit, KeyDescriber.KeyContext()))
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun `chinese forward delete differs from backspace`() {
        val backspace = describer.describe(alpha.key { it.output == KeyOutput.Backspace }, KeyDescriber.KeyContext())
        val del = describer.describe(Layouts.function().key { it.label == "Del" }, KeyDescriber.KeyContext())
        assertEquals("删除", backspace)
        assertEquals("向后删除", del)
    }

    @Test
    fun `navigation macros get spoken names, F keys and own labels stay`() {
        val function = Layouts.function()
        assertEquals("Bild auf", describer.describe(function.key { it.label == "PgUp" }, KeyDescriber.KeyContext()))
        assertEquals("Pos1", describer.describe(function.key { it.label == "Home" }, KeyDescriber.KeyContext()))
        assertEquals("Entf", describer.describe(function.key { it.label == "Del" }, KeyDescriber.KeyContext()))
        assertEquals("F5", describer.describe(function.key { it.label == "F5" }, KeyDescriber.KeyContext()))
        assertEquals("Pfeil hoch", describer.describe(function.key { it.icon == KeyIcon.UP }, KeyDescriber.KeyContext()))
        val pgUp = Key(label = "PgUp", output = KeyOutput.Macro(Macro.parse("{PGUP}")))
        assertEquals("Bild auf", describer.describe(pgUp, KeyDescriber.KeyContext()))
        val own = Key(label = "Buchen", output = KeyOutput.Macro(Macro.parse("{PGUP}")))
        assertEquals("Buchen", describer.describe(own, KeyDescriber.KeyContext()))
    }

    @Test
    fun `modifiers say on and locked`() {
        val ctrl = Layouts.modifierKey(ModifierKey.CTRL)
        assertEquals("Ctrl", describer.describe(ctrl, KeyDescriber.KeyContext()))
        assertNull(describer.state(ctrl, KeyDescriber.KeyContext()))
        assertEquals("An", describer.state(ctrl, KeyDescriber.KeyContext(modifiers = mapOf(ModifierKey.CTRL to ShiftState.ONCE))))
        assertEquals("fest", describer.state(ctrl, KeyDescriber.KeyContext(modifiers = mapOf(ModifierKey.CTRL to ShiftState.LOCKED))))
        assertEquals("Feststellen", describer.longPressLabel(ctrl, KeyDescriber.KeyContext()))
    }

    @Test
    fun `page keys say the page they lead to`() {
        val symbols = alpha.key { it.output == KeyOutput.ShowTab(Tabs.SYMBOLS) }
        assertEquals("Symbole", describer.describe(symbols, KeyDescriber.KeyContext()))
        assertEquals("Weitere Symbole", describer.describe(Key(label = "=\\<", output = KeyOutput.SymbolsPage), KeyDescriber.KeyContext(layoutId = Tabs.SYMBOLS)))
        assertEquals("Symbole", describer.describe(Key(label = "?123", output = KeyOutput.SymbolsPage), KeyDescriber.KeyContext(layoutId = "${Tabs.SYMBOLS}-2")))
        assertEquals("Funktionstasten", describer.pageName(Tabs.FUNCTION))
        assertEquals("Weitere Symbole", describer.pageName(Tabs.SYMBOLS, symbolsPage = 2))
        assertEquals("WMS", describer.pageName("wms", "WMS"))
    }

    @Test
    fun `text editing keys say their action and state`() {
        val edit = KeyboardRenderer.textEditingLayout(context)
        fun action(a: EditAction) = edit.key { (it.output as? KeyOutput.Edit)?.action == a }
        val idle = KeyDescriber.KeyContext(editState = TextEditState(selecting = false, hasSelection = false, password = false, editor = true))
        val selecting = idle.copy(editState = TextEditState(selecting = true, hasSelection = true, password = false, editor = true))
        assertEquals("Pfeil links", describer.describe(action(EditAction.LEFT), idle))
        assertEquals("Auswählen", describer.describe(action(EditAction.SELECT), idle))
        assertNull(describer.state(action(EditAction.SELECT), idle))
        assertEquals("An", describer.state(action(EditAction.SELECT), selecting))
        assertFalse(describer.enabled(action(EditAction.COPY), idle))
        assertTrue(describer.enabled(action(EditAction.COPY), selecting))
        assertEquals("Buchstaben", describer.describe(action(EditAction.CLOSE), idle))
        // The {EDIT} key and its icon.
        val editKey = Key(icon = KeyIcon.TEXT_EDIT, output = KeyOutput.Macro(Macro.parse("{EDIT}")))
        assertEquals("Text bearbeiten", describer.describe(editKey, KeyDescriber.KeyContext()))
    }

    @Test
    fun `toolbar buttons say what they open`() {
        val tabs = KeyboardRenderer.toolbarTabs(KeyboardConfig(), scanAvailable = true)
        assertEquals(listOf("Weitere Funktionen", "Zwischenablage", "Einstellungen"), tabs.take(3).map(describer::tab))
        assertEquals("Tastatur ausblenden", describer.tabBarExtra(KeyDescriber.HIDE))
        assertEquals("Diktat starten", describer.tabBarExtra(KeyDescriber.MIC))
    }

    @Test
    fun `comma alternatives for settings and emoji are named`() {
        val comma = alpha.key { it.label == "," }
        val c = KeyDescriber.KeyContext()
        val options = describer.alternatives(comma, c)
        assertEquals("Einstellungen", describer.alternativeLabel(options[0], 0, c))
        assertEquals("Emoji", describer.alternativeLabel(options[1], 1, c))
    }

    @Test
    @Config(qualifiers = "en")
    fun `english names`() {
        val shift = alpha.key { it.output == KeyOutput.Shift }
        assertEquals("Shift on", describer.describe(shift, KeyDescriber.KeyContext(shift = ShiftState.ONCE)))
        assertEquals("Capital Q", describer.describe(letter("q"), KeyDescriber.KeyContext(shift = ShiftState.ONCE)))
        assertEquals("Delete", describer.describe(alpha.key { it.output == KeyOutput.Backspace }, KeyDescriber.KeyContext()))
        assertEquals("Delete forward", describer.describe(Layouts.function().key { it.label == "Del" }, KeyDescriber.KeyContext()))
        val edit = KeyboardRenderer.textEditingLayout(context).key { (it.output as? KeyOutput.Edit)?.action == EditAction.DELETE }
        assertEquals("Delete forward", describer.describe(edit, KeyDescriber.KeyContext()))
    }
}

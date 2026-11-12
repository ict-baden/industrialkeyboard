package de.ictbaden.industrialkeyboard.config

import android.text.InputType
import de.ictbaden.industrialkeyboard.config.Snippets.Problem
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.NumericVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The snippet rules: lists, shortcut matching, the editor's problems and the panel preview. */
class SnippetsTest {

    private val regards = Snippet("regards", "Mit freundlichen Grüßen", "Mit freundlichen Grüßen{ENTER}Lager Süd", "mfg")
    private val bin = Snippet("bin", "Lagerplatz A-01", "A-01-03{TAB}")
    private val mine = Snippet("u1", "Prüfen", "Bitte prüfen", "pr")
    private val clash = Snippet("u2", "Meine Grüße", "Gruß", "MFG")
    private val config = KeyboardConfig(snippets = SnippetSettings(items = listOf(regards, bin)))
    private val letters = Page.Alpha(AlphaVariant.TEXT)
    private val text = InputType.TYPE_CLASS_TEXT

    @Test
    fun `byShortcut is exact and case-insensitive`() {
        assertSame(regards, Snippets.byShortcut("Mfg", config, emptyList()))
        assertSame(regards, Snippets.byShortcut("MFG", config, emptyList()))
        assertNull(Snippets.byShortcut("mf", config, emptyList()))
        assertNull(Snippets.byShortcut("mfgs", config, emptyList()))
        assertSame(mine, Snippets.byShortcut("pr", config, listOf(mine)))
    }

    @Test
    fun `the administrator's snippet wins over the user's`() {
        assertSame(regards, Snippets.byShortcut("mfg", config, listOf(clash)))
        assertEquals(listOf(regards to true, bin to true, clash to false), Snippets.all(config, listOf(clash)))
    }

    @Test
    fun `allowUser false hides the user's snippets, enabled false everything`() {
        val noUser = config.copy(snippets = config.snippets.copy(allowUser = false))
        assertEquals(listOf(regards to true, bin to true), Snippets.all(noUser, listOf(mine)))
        assertNull(Snippets.byShortcut("pr", noUser, listOf(mine)))
        assertFalse(Snippets.userAllowed(noUser))

        val off = config.copy(snippets = config.snippets.copy(enabled = false))
        assertTrue(Snippets.all(off, listOf(mine)).isEmpty())
        assertNull(Snippets.byShortcut("mfg", off, listOf(mine)))
        assertFalse(Snippets.reachable(off))
        assertFalse(Snippets.userAllowed(off))
    }

    @Test
    fun `reachable with items or own snippets`() {
        assertTrue(Snippets.reachable(KeyboardConfig()))
        assertFalse(Snippets.reachable(KeyboardConfig(snippets = SnippetSettings(allowUser = false))))
        assertTrue(Snippets.reachable(KeyboardConfig(snippets = SnippetSettings(allowUser = false, items = listOf(bin)))))
    }

    @Test
    fun `problem covers every case`() {
        val none = emptySet<String>()
        assertNull(Snippets.problem("Name", "Text", "mfg", none))
        assertNull(Snippets.problem("Name", "Text", null, none))
        assertNull(Snippets.problem("Name", "Text", "  ", none))
        assertEquals(Problem.LABEL_EMPTY, Snippets.problem("  ", "Text", null, none))
        assertEquals(Problem.LABEL_TOO_LONG, Snippets.problem("x".repeat(41), "Text", null, none))
        assertNull(Snippets.problem(" ${"x".repeat(40)} ", "Text", null, none))
        assertEquals(Problem.TEXT_EMPTY, Snippets.problem("Name", "", null, none))
        assertNull("spaces are a text", Snippets.problem("Name", " ", null, none))
        assertEquals(Problem.TEXT_TOO_LONG, Snippets.problem("Name", "x".repeat(2001), null, none))
        assertEquals(Problem.MACRO, Snippets.problem("Name", "A{TABB}", null, none))
        assertEquals(Problem.MACRO_NOT_ALLOWED, Snippets.problem("Name", "{SETTINGS}", null, none, user = true))
        assertEquals(Problem.SHORTCUT_FORMAT, Snippets.problem("Name", "Text", "lp1", none))
        assertEquals(Problem.SHORTCUT_FORMAT, Snippets.problem("Name", "Text", "a", none))
        assertEquals(Problem.SHORTCUT_FORMAT, Snippets.problem("Name", "Text", "m fg", none))
        assertEquals(Problem.SHORTCUT_FORMAT, Snippets.problem("Name", "Text", "x".repeat(17), none))
        assertNull(Snippets.problem("Name", "Text", "Grüße", none))
        assertEquals(Problem.SHORTCUT_TAKEN, Snippets.problem("Name", "Text", "MFG", setOf("mfg")))
    }

    @Test
    fun `own snippets type only text, Tab and Enter`() {
        val none = emptySet<String>()
        listOf("Bitte prüfen", "A{TAB}B{ENTER}", "{SPACE}{LBRACE}{U+00E4}", "Zeile 1{enter}Zeile 2").forEach {
            assertNull(it, Snippets.problem("Name", it, null, none, user = true))
        }
        listOf("{SETTINGS}", "{NEXT-KEYBOARD}", "{SHOW:numeric}", "{CTRL+C}", "{SCAN}", "{HIDE}", "{EDIT}", "{F1}", "{SHIFT+TAB}", "{TOGGLE:glove}", "{CTRL}", "{ESC}").forEach {
            assertEquals(it, Problem.MACRO_NOT_ALLOWED, Snippets.problem("Name", "x$it", null, none, user = true))
            // The administrator's may.
            assertNull(it, Snippets.problem("Name", "x$it", null, none, user = false))
        }
    }

    @Test
    fun `preview shows Tab and Enter as symbols`() {
        assertEquals("A ⇥ B ⏎", Snippets.preview("A{TAB}B{ENTER}"))
        assertEquals("A-01-03 ⇥", Snippets.preview("A-01-03{TAB}"))
        assertEquals("Mit freundlichen Grüßen ⏎ Lager Süd", Snippets.preview("Mit freundlichen Grüßen{ENTER}Lager Süd"))
        assertEquals("OK {F13}", Snippets.preview("OK {F13}"))
        assertEquals("a\nb…", Snippets.preview("a\nb\nc"))
        assertTrue(Snippets.preview("x".repeat(500)).length <= 161)
    }

    @Test
    fun `matchFor needs letters, no panel and a text field`() {
        assertSame(regards, Snippets.matchFor("mfg", letters, text, null, config, emptyList()))
        assertSame("without suggestions too", regards, Snippets.matchFor("Mfg", letters, text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS, null, config, emptyList()))
        assertSame(regards, Snippets.matchFor("mfg", letters, text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, text or InputType.TYPE_TEXT_VARIATION_PASSWORD, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, InputType.TYPE_NULL, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, InputType.TYPE_CLASS_NUMBER, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", Page.Numeric(NumericVariant.NUMBER), text, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", Page.Function, text, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, text, Tabs.TOOL_EDIT, config, emptyList()))
        assertNull(Snippets.matchFor("m", letters, text, null, config, emptyList()))
        assertNull(Snippets.matchFor("", letters, text, null, config, emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, text, null, config.copy(snippets = config.snippets.copy(shortcuts = false)), emptyList()))
        assertNull(Snippets.matchFor("mfg", letters, text, null, config.copy(snippets = config.snippets.copy(enabled = false)), emptyList()))
    }
}

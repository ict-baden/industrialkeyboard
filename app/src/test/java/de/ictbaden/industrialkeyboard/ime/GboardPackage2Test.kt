package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import de.ictbaden.industrialkeyboard.config.Appearance
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.LayoutSettings
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.OneHanded
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.TopBar
import de.ictbaden.industrialkeyboard.config.TypingSettings
import de.ictbaden.industrialkeyboard.suggest.NextWordContext
import de.ictbaden.industrialkeyboard.suggest.NextWords
import de.ictbaden.industrialkeyboard.suggest.Suggester
import de.ictbaden.industrialkeyboard.suggest.WordList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/** Toolbar, clipboard, next words, search fields, ranking and one-handed mode (Gboard package 2). */
class GboardPackage2Test {

    private val text = InputType.TYPE_CLASS_TEXT
    private val search = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_FILTER

    @Test
    fun `new settings survive the JSON round trip`() {
        val config = KeyboardConfig(
            appearance = Appearance(oneHanded = OneHanded.LEFT),
            layout = LayoutSettings(topBar = TopBar.TABS),
            typing = TypingSettings(nextWord = false, clipboardHistory = true),
        )
        val parsed = ConfigJson.parse(ConfigJson.write(config))
        assertEquals(OneHanded.LEFT, parsed.appearance.oneHanded)
        assertEquals(TopBar.TABS, parsed.layout.topBar)
        assertFalse(parsed.typing.nextWord)
        assertTrue(parsed.typing.clipboardHistory)
    }

    @Test
    fun `defaults like Gboard, clipboard history off for privacy`() {
        val d = KeyboardConfig()
        assertEquals(TopBar.TOOLBAR, d.layout.topBar)
        assertEquals(OneHanded.OFF, d.appearance.oneHanded)
        assertTrue(d.typing.nextWord)
        assertFalse(d.typing.clipboardHistory)
    }

    @Test
    fun `a configuration stored by 2_1 keeps its tabs, a new one gets the toolbar`() {
        val old = """{"layout":{"alphabet":"qwertz"}}"""
        assertEquals(TopBar.TABS, ConfigStore.withStoredDefaults(old, ConfigJson.parse(old)).layout.topBar)
        val new = ConfigJson.write(KeyboardConfig())
        assertEquals(TopBar.TOOLBAR, ConfigStore.withStoredDefaults(new, ConfigJson.parse(new)).layout.topBar)
        // An imported file or the MDM without topBar: the default (toolbar).
        assertEquals(TopBar.TOOLBAR, ConfigJson.parse(old).layout.topBar)
    }

    @Test
    fun `toolbar buttons, scan only with a scanner, tabs mode unchanged`() {
        val ids = KeyboardRenderer.toolbarTabs(KeyboardConfig(), scanAvailable = false).map { it.id }
        assertEquals(listOf(Tabs.TOOL_MENU, Tabs.TOOL_CLIPBOARD, Tabs.TOOL_SETTINGS, Tabs.FUNCTION), ids)
        assertTrue(Tabs.SCAN in KeyboardRenderer.toolbarTabs(KeyboardConfig(), scanAvailable = true).map { it.id })
        val tabs = KeyboardConfig(layout = LayoutSettings(topBar = TopBar.TABS))
        assertEquals(Tabs.ALPHA, KeyboardRenderer.tabs(tabs, scanAvailable = false)?.first()?.id)
        // The classic navigation has neither.
        assertNull(KeyboardRenderer.tabs(KeyboardConfig(layout = LayoutSettings(navigation = Navigation.KEYS)), scanAvailable = false))
    }

    @Test
    fun `suggestions in search fields, autocorrect not`() {
        assertTrue(FieldPolicy.suggestionsAllowed(search))
        assertFalse(FieldPolicy.autocorrectAllowed(search, 0))
        assertTrue(FieldPolicy.autocorrectAllowed(text, 0))
        assertFalse(FieldPolicy.autocorrectAllowed(text, EditorInfo.IME_ACTION_SEARCH))
        assertFalse(FieldPolicy.suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertFalse(FieldPolicy.suggestionsAllowed(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI))
    }

    @Test
    fun `clipboard history - newest first, pinned on top, max size and age`() {
        val history = ClipboardHistory(max = 3, maxAgeMs = 1000)
        history.add("a", 0)
        history.add("b", 10)
        history.add("c", 20)
        assertEquals(listOf("c", "b", "a"), history.items(30).map { it.text })
        history.togglePin(history.items(30).last().id) // pin "a"
        history.add("d", 40)
        // "b" was the oldest unpinned one and had to go; "a" stays because it is pinned.
        assertEquals(listOf("a", "d", "c"), history.items(50).map { it.text })
        history.add("c", 60) // the same text again moves up
        assertEquals(listOf("a", "c", "d"), history.items(70).map { it.text })
        // After the maximum age only the pinned entry is left.
        assertEquals(listOf("a"), history.items(5000).map { it.text })
        history.add("  ", 5000)
        assertEquals(1, history.items(5000).size)
    }

    @Test
    fun `next words follow the word before a single space`() {
        assertEquals("vielen", NextWordContext.previousWord("Hallo, vielen "))
        assertEquals("Ich", NextWordContext.previousWord("Ich "))
        assertNull(NextWordContext.previousWord("Hallo"))
        assertNull(NextWordContext.previousWord("Hallo  "))
        assertNull(NextWordContext.previousWord("Seite 12 "))
        val next = NextWords.read(File("src/main/assets/dict/de.nxt").inputStream(), Locale.GERMAN)
        assertTrue(next.after("Vielen", Locale.GERMAN).contains("Dank"))
        assertEquals(3, next.after("ich", Locale.GERMAN).size)
        assertTrue(next.after("Xylophon", Locale.GERMAN).isEmpty())
        val en = NextWords.read(File("src/main/assets/dict/en.nxt").inputStream(), Locale.US)
        assertEquals("you", en.after("thank", Locale.US).first())
    }

    @Test
    fun `everyday words rank high - hall offers Hallo in German`() {
        val de = File("src/main/assets/dict/de.dic").inputStream().use { WordList.read(it, Locale.GERMAN) }
        val suggester = Suggester(de, Locale.GERMAN, listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"))
        val words = suggester.suggest("Hall").words.map { it.text }
        assertTrue(words.toString(), "Hallo" in words)
        // The typed word is a word: it stays the best (shown in the middle).
        assertEquals("Hall", words.first())
        val en = File("src/main/assets/dict/en-US.dic").inputStream().use { WordList.read(it, Locale.US) }
        val english = Suggester(en, Locale.US, listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"))
        assertTrue(english.suggest("Thank").words.map { it.text }.toString(), "Thanks" in english.suggest("Thank").words.map { it.text })
    }
}

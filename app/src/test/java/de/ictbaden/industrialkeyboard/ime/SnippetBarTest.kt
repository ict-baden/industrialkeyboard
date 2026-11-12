package de.ictbaden.industrialkeyboard.ime

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.CustomKey
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.LayoutSettings
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.Snippet
import de.ictbaden.industrialkeyboard.config.SnippetSettings
import de.ictbaden.industrialkeyboard.config.TabSettings
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.TopBar
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.PanelSnippet
import de.ictbaden.industrialkeyboard.ui.ShiftState
import de.ictbaden.industrialkeyboard.ui.SnippetsPanelView
import de.ictbaden.industrialkeyboard.ui.SuggestionStripView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The snippets button in the toolbar and the tab bar, the panel in place of the keys and the strip. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "de")
class SnippetBarTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val letters = Page.Alpha(AlphaVariant.TEXT)
    private val on = SnippetSettings(button = true)
    private val tabsMode = LayoutSettings(topBar = TopBar.TABS)

    private fun ids(config: KeyboardConfig, password: Boolean = false) = KeyboardRenderer.tabs(config, scanAvailable = false, passwordField = password)?.map { it.id }

    @Test
    fun `toolbar icon only with enabled and button, never over a password field`() {
        val custom = listOf(CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK")))))
        assertFalse(Tabs.TOOL_SNIPPETS in ids(KeyboardConfig())!!)
        val tools = ids(KeyboardConfig(snippets = on, customLayouts = custom))!!
        // After Fn (and scan), before the custom layouts.
        assertEquals(listOf(Tabs.TOOL_MENU, Tabs.TOOL_CLIPBOARD, Tabs.TOOL_SETTINGS, Tabs.FUNCTION, Tabs.TOOL_SNIPPETS, "wms"), tools)
        assertFalse(Tabs.TOOL_SNIPPETS in ids(KeyboardConfig(snippets = on.copy(enabled = false)))!!)
        assertFalse(Tabs.TOOL_SNIPPETS in ids(KeyboardConfig(snippets = on), password = true)!!)
        assertFalse(Tabs.TOOL_SNIPPETS in KeyboardRenderer.toolbarTabs(KeyboardConfig(snippets = on), scanAvailable = true, passwordField = true).map { it.id })
        // Nothing to show: no admin snippets and no own ones allowed.
        assertFalse(Tabs.TOOL_SNIPPETS in ids(KeyboardConfig(snippets = on.copy(allowUser = false)))!!)
        assertTrue(Tabs.TOOL_SNIPPETS in ids(KeyboardConfig(snippets = on.copy(allowUser = false, items = listOf(Snippet("a", "A", "a")))))!!)
    }

    @Test
    fun `in tab mode it is the last tab and never a start page`() {
        val config = KeyboardConfig(layout = tabsMode, snippets = on, customLayouts = listOf(CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK"))))))
        assertEquals(listOf(Tabs.ALPHA, Tabs.NUMERIC, Tabs.SYMBOLS, Tabs.FUNCTION, "wms", Tabs.TOOL_SNIPPETS), ids(config))
        assertFalse(Tabs.TOOL_SNIPPETS in FieldPolicy.tabs(config, scanAvailable = false))
        assertFalse(Tabs.TOOL_SNIPPETS in ids(config, password = true)!!)
        assertFalse(Tabs.TOOL_SNIPPETS in ids(config.copy(snippets = SnippetSettings()))!!)
        // With every page tab switched off the snippets tab alone is no start page.
        val only = config.copy(tabs = TabSettings(alpha = false, numeric = false, symbols = false, function = false, scan = false), customLayouts = emptyList())
        assertEquals(letters, FieldPolicy.startPage(Field(AlphaVariant.TEXT, null), only, scanAvailable = false))
        assertNull(FieldPolicy.pageForTab(Tabs.TOOL_SNIPPETS, Field(AlphaVariant.TEXT, null), config, scanAvailable = false))
    }

    @Test
    fun `navigation keys gives no bar, also with the button`() {
        assertNull(ids(KeyboardConfig(layout = LayoutSettings(navigation = Navigation.KEYS, topBar = TopBar.TABS), snippets = on)))
        assertNull(ids(KeyboardConfig(layout = LayoutSettings(navigation = Navigation.KEYS), snippets = on)))
    }

    @Test
    fun `the open panel is the selected tab in tab mode`() {
        val tabs = KeyboardConfig(layout = tabsMode, snippets = on)
        assertEquals(Tabs.TOOL_SNIPPETS, KeyboardRenderer.selectedTab(tabs, letters, Tabs.TOOL_SNIPPETS))
        assertEquals(Tabs.ALPHA, KeyboardRenderer.selectedTab(tabs, letters, null))
        // Other panels keep the page lit in tab mode, as before.
        assertEquals(Tabs.ALPHA, KeyboardRenderer.selectedTab(tabs, letters, Tabs.TOOL_EDIT))
        assertEquals(Tabs.TOOL_SNIPPETS, KeyboardRenderer.selectedTab(KeyboardConfig(snippets = on), letters, Tabs.TOOL_SNIPPETS))
        val state = KeyboardRenderer.state(context, tabs, letters, ShiftState.OFF, null, toolPanel = Tabs.TOOL_SNIPPETS)
        assertEquals(Tabs.TOOL_SNIPPETS, state.selectedTab)
    }

    private val cards = listOf(
        PanelSnippet("a:regards", "Mit freundlichen Grüßen", "Mit freundlichen Grüßen ⏎ Lager Süd", "Mit freundlichen Grüßen{ENTER}Lager Süd"),
        PanelSnippet("u:u1", "Prüfen", "Bitte prüfen", "Bitte prüfen"),
    )

    @Test
    fun `the panel replaces the keys in their height and lists the entries only while open`() {
        val view = KeyboardRootView(context)
        val closed = KeyboardRenderer.state(context, KeyboardConfig(), letters, ShiftState.OFF, null, snippets = cards)
        assertTrue("passed only while open", closed.snippets.isEmpty())
        val open = KeyboardRenderer.state(context, KeyboardConfig(), letters, ShiftState.OFF, null, toolPanel = Tabs.TOOL_SNIPPETS, snippets = cards, snippetsEditable = true)
        assertEquals(closed.heightRows, open.heightRows)
        view.render(open)
        assertEquals(View.VISIBLE, view.snippetsPanel.visibility)
        assertEquals(open.rowHeight * open.heightRows + view.keyboard.paddingTop + view.keyboard.paddingBottom, view.snippetsPanel.layoutParams.height)
        assertEquals(2, view.snippetsPanel.cardCount)
        view.render(closed)
        assertEquals(View.GONE, view.snippetsPanel.visibility)
    }

    @Test
    fun `cards are big enough for gloves, tap types, the empty text depends on own snippets`() {
        val panel = SnippetsPanelView(context)
        var picked: PanelSnippet? = null
        panel.onPick = { picked = it }
        panel.show(cards, editable = true)
        val list = allViews(panel).filter { it.isClickable && it.minimumHeight > 0 }
        assertEquals(2, list.size)
        val density = context.resources.displayMetrics.density
        list.forEach { assertTrue(it.minimumHeight >= (SnippetsPanelView.MIN_CARD_DP * density).toInt()) }
        list[1].performClick()
        assertEquals(cards[1], picked)

        panel.show(emptyList(), editable = true)
        assertTrue(texts(panel).contains("Noch keine Textbausteine. Mit ✎ eigene anlegen."))
        panel.show(emptyList(), editable = false)
        assertTrue(texts(panel).contains("Ihre Organisation hat keine Textbausteine eingerichtet."))
    }

    @Test
    fun `the strip puts the snippet in the middle, the words aside`() {
        val strip = SuggestionStripView(context)
        strip.words = listOf("mag", "mfg", "mit")
        assertEquals("mfg", strip.wordAt(0))
        assertEquals("mag", strip.wordAt(1))
        strip.snippet = "Mit freundlichen Grüßen"
        assertEquals("mag", strip.wordAt(0))
        assertEquals("Mit freundlichen Grüßen", strip.wordAt(1))
        assertEquals("mfg", strip.wordAt(2))

        // Only the snippet (a field without suggestions): the strip shows it.
        val view = KeyboardRootView(context)
        view.render(KeyboardRenderer.state(context, KeyboardConfig(), letters, ShiftState.OFF, null, snippetSuggestion = "Prüfen"))
        assertEquals(View.VISIBLE, view.suggestionStrip.visibility)
        assertEquals("Prüfen", view.suggestionStrip.snippet)
        view.render(KeyboardRenderer.state(context, KeyboardConfig(), letters, ShiftState.OFF, null))
        assertEquals(View.GONE, view.suggestionStrip.visibility)
    }

    private fun allViews(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { allViews(view.getChildAt(it)) } else emptyList()

    private fun texts(view: View) = allViews(view).filterIsInstance<TextView>().map { it.text.toString() }
}

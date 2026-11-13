package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import de.ictbaden.industrialkeyboard.config.CustomKey
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.LayoutSettings
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.TabSettings
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.NumericVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldPolicyTest {

    private val text = InputType.TYPE_CLASS_TEXT

    @Test
    fun `input types map to fields`() {
        assertEquals(Field(AlphaVariant.TEXT, null), FieldPolicy.field(text))
        assertEquals(Field(AlphaVariant.EMAIL, null), FieldPolicy.field(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(Field(AlphaVariant.URL, null), FieldPolicy.field(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(NumericVariant.NUMBER, FieldPolicy.field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).numeric)
        assertEquals(NumericVariant.PIN, FieldPolicy.field(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD).numeric)
        assertEquals(NumericVariant.PHONE, FieldPolicy.field(InputType.TYPE_CLASS_PHONE).numeric)
        assertEquals(NumericVariant.DATE, FieldPolicy.field(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE).numeric)
        assertEquals(NumericVariant.TIME, FieldPolicy.field(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME).numeric)
    }

    @Test
    fun `numeric fields always open the pad`() {
        val config = KeyboardConfig(tabs = TabSettings(numeric = false, preferred = Tabs.SCAN))
        val field = FieldPolicy.field(InputType.TYPE_CLASS_NUMBER)
        assertEquals(Page.Numeric(NumericVariant.NUMBER), FieldPolicy.startPage(field, config, scanAvailable = true))
    }

    @Test
    fun `text fields use the preferred tab when it exists`() {
        val field = FieldPolicy.field(text)
        assertEquals(Page.Alpha(AlphaVariant.TEXT), FieldPolicy.startPage(field, KeyboardConfig(), scanAvailable = true))
        val scan = KeyboardConfig(tabs = TabSettings(preferred = Tabs.SCAN))
        assertEquals(Page.Scan, FieldPolicy.startPage(field, scan, scanAvailable = true))
        // Without a scanner the scan tab does not exist.
        assertEquals(Page.Alpha(AlphaVariant.TEXT), FieldPolicy.startPage(field, scan, scanAvailable = false))
        val custom = KeyboardConfig(
            tabs = TabSettings(preferred = "wms"),
            customLayouts = listOf(CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK"))))),
        )
        assertEquals(Page.Custom("wms"), FieldPolicy.startPage(field, custom, scanAvailable = false))
    }

    @Test
    fun `password fields are recognized, visible ones too`() {
        assertEquals(true, FieldPolicy.isPassword(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(true, FieldPolicy.isPassword(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(true, FieldPolicy.isPassword(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertEquals(true, FieldPolicy.isPassword(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(false, FieldPolicy.isPassword(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(false, FieldPolicy.isPassword(InputType.TYPE_NULL))
    }

    @Test
    fun `tab bar lists enabled tabs and custom layouts`() {
        val config = KeyboardConfig(
            tabs = TabSettings(symbols = false),
            customLayouts = listOf(CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK"))))),
        )
        assertEquals(listOf("alpha", "numeric", "function", "scan", "wms"), FieldPolicy.tabs(config, scanAvailable = true))
        assertEquals(listOf("alpha", "numeric", "function", "wms"), FieldPolicy.tabs(config, scanAvailable = false))
        assertEquals(emptyList<String>(), FieldPolicy.tabs(KeyboardConfig(layout = LayoutSettings(navigation = Navigation.KEYS)), true))
        val none = KeyboardConfig(tabs = TabSettings(alpha = false, numeric = false, symbols = false, function = false, scan = false))
        assertEquals(listOf("alpha"), FieldPolicy.tabs(none, scanAvailable = true))
    }

    @Test
    fun `numeric tab keeps the field's pad variant`() {
        val field = FieldPolicy.field(InputType.TYPE_CLASS_PHONE)
        assertEquals(Page.Numeric(NumericVariant.PHONE), FieldPolicy.pageForTab(Tabs.NUMERIC, field, KeyboardConfig(), true))
        assertNull(FieldPolicy.pageForTab("unknown", field, KeyboardConfig(), true))
    }

    @Test
    fun `suggestions and emoji only in normal text`() {
        val sentence = text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        assertEquals(true, FieldPolicy.suggestionsAllowed(sentence))
        assertEquals(true, FieldPolicy.emojiAllowed(sentence))
        val no = listOf(
            text or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            text or InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            InputType.TYPE_NULL,
        )
        no.forEach {
            assertEquals("suggestions for $it", false, FieldPolicy.suggestionsAllowed(it))
            assertEquals("emoji for $it", false, FieldPolicy.emojiAllowed(it))
        }
        // Barcode fields usually ask for no suggestions; emoji are still fine there.
        assertEquals(false, FieldPolicy.suggestionsAllowed(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
    }

    @Test
    fun `snippet shortcuts in text fields, also without suggestions, never in passwords`() {
        assertEquals(true, FieldPolicy.snippetShortcutsAllowed(text))
        assertEquals(true, FieldPolicy.snippetShortcutsAllowed(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
        assertEquals(true, FieldPolicy.snippetShortcutsAllowed(text or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES))
        assertEquals(false, FieldPolicy.snippetShortcutsAllowed(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(false, FieldPolicy.snippetShortcutsAllowed(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(false, FieldPolicy.snippetShortcutsAllowed(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertEquals(false, FieldPolicy.snippetShortcutsAllowed(InputType.TYPE_CLASS_NUMBER))
        assertEquals(false, FieldPolicy.snippetShortcutsAllowed(InputType.TYPE_NULL))
    }

    @Test
    fun `a line break in a macro is text only in multi-line fields without an action`() {
        val multi = text or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        // What a multi-line TextView reports (the Try-it "Note" field on the HC20).
        assertEquals(true, FieldPolicy.newlineAsText(multi, EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertEquals(true, FieldPolicy.newlineAsText(multi or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, EditorInfo.IME_ACTION_NONE))
        // An action (Send in a chat app) keeps the key, which runs it.
        assertEquals(false, FieldPolicy.newlineAsText(multi, EditorInfo.IME_ACTION_SEND))
        assertEquals(false, FieldPolicy.newlineAsText(text, EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertEquals(false, FieldPolicy.newlineAsText(text, EditorInfo.IME_ACTION_NEXT))
        assertEquals(false, FieldPolicy.newlineAsText(InputType.TYPE_NULL, EditorInfo.IME_FLAG_NO_ENTER_ACTION))
        assertEquals(false, FieldPolicy.newlineAsText(InputType.TYPE_CLASS_NUMBER, EditorInfo.IME_FLAG_NO_ENTER_ACTION))
    }

    @Test
    fun `snippets panel everywhere but in passwords`() {
        assertEquals(true, FieldPolicy.snippetsAllowed(text))
        assertEquals(true, FieldPolicy.snippetsAllowed(InputType.TYPE_CLASS_NUMBER))
        assertEquals(false, FieldPolicy.snippetsAllowed(text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertEquals(false, FieldPolicy.snippetsAllowed(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertEquals(false, FieldPolicy.snippetsAllowed(text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
        assertEquals(false, FieldPolicy.snippetsAllowed(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
    }
}

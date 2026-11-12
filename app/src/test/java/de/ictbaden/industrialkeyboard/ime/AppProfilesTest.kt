package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.AppProfile
import de.ictbaden.industrialkeyboard.config.DataSettings
import de.ictbaden.industrialkeyboard.config.EffectiveConfig
import de.ictbaden.industrialkeyboard.config.FieldKind
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.NumericOrder
import de.ictbaden.industrialkeyboard.config.QuickToggle
import de.ictbaden.industrialkeyboard.config.QuickToggleSettings
import de.ictbaden.industrialkeyboard.config.QuickToggles
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.ScannerSettings
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.TopBar
import de.ictbaden.industrialkeyboard.config.TypingSettings
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.NumericVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProfilesTest {

    private val text = InputType.TYPE_CLASS_TEXT
    private val number = InputType.TYPE_CLASS_NUMBER
    private val textPassword = text or InputType.TYPE_TEXT_VARIATION_PASSWORD
    private val visiblePassword = text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    private val numberPassword = number or InputType.TYPE_NUMBER_VARIATION_PASSWORD

    // region Glob

    @Test
    fun `package globs match the whole name, dots included`() {
        assertTrue(Glob.matches("com.sap.*", "com.sap.mobile"))
        assertTrue(Glob.matches("com.sap.*", "com.sap.a.b"))
        assertFalse(Glob.matches("com.sap.*", "com.sapx"))
        assertFalse(Glob.matches("com.sap.*", "xcom.sap.a"))
        assertTrue(Glob.matches("de.ictbaden.industrialkeyboard*", "de.ictbaden.industrialkeyboard"))
        assertTrue(Glob.matches("de.ictbaden.industrialkeyboard*", "de.ictbaden.industrialkeyboard.debug"))
    }

    @Test
    fun `star matches anything, the empty text included`() {
        assertTrue(Glob.matches("*", ""))
        assertTrue(Glob.matches("*", "anything at all"))
        assertTrue(Glob.matches("**", "x"))
        assertFalse(Glob.matches("", "x"))
        assertTrue(Glob.matches("", ""))
    }

    @Test
    fun `question mark is exactly one character`() {
        assertTrue(Glob.matches("com.?ap.x", "com.sap.x"))
        assertFalse(Glob.matches("com.?ap.x", "com.ap.x"))
        assertFalse(Glob.matches("com.?ap.x", "com.ssap.x"))
    }

    @Test
    fun `several stars backtrack`() {
        assertTrue(Glob.matches("*a*b*", "xxaxxbxx"))
        assertTrue(Glob.matches("*a*b*", "ab"))
        assertFalse(Glob.matches("*a*b*", "ba"))
        assertTrue(Glob.matches("*ab", "aab"))
        assertFalse(Glob.matches("a*a*a*a*a*b", "a".repeat(100)))
    }

    @Test
    fun `case only with ignoreCase`() {
        assertTrue(Glob.matches("*MENGE*", "Menge (Stk)", ignoreCase = true))
        assertFalse(Glob.matches("*MENGE*", "Menge (Stk)"))
        assertFalse(Glob.matches("com.sap.*", "Com.sap.x"))
    }

    // endregion
    // region kind

    @Test
    fun `field kinds from the input type`() {
        assertEquals(FieldKind.NONE, AppProfiles.kind(InputType.TYPE_NULL))
        assertEquals(FieldKind.NUMBER, AppProfiles.kind(number))
        assertEquals(FieldKind.NUMBER, AppProfiles.kind(number or InputType.TYPE_NUMBER_FLAG_DECIMAL))
        assertEquals(FieldKind.PHONE, AppProfiles.kind(InputType.TYPE_CLASS_PHONE))
        assertEquals(FieldKind.DATETIME, AppProfiles.kind(InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE))
        assertEquals(FieldKind.EMAIL, AppProfiles.kind(text or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS))
        assertEquals(FieldKind.EMAIL, AppProfiles.kind(text or InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS))
        assertEquals(FieldKind.URL, AppProfiles.kind(text or InputType.TYPE_TEXT_VARIATION_URI))
        assertEquals(FieldKind.TEXT, AppProfiles.kind(text))
        assertEquals(FieldKind.TEXT, AppProfiles.kind(text or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS))
    }

    @Test
    fun `password fields have no kind`() {
        assertNull(AppProfiles.kind(textPassword))
        assertNull(AppProfiles.kind(visiblePassword))
        assertNull(AppProfiles.kind(text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertNull(AppProfiles.kind(numberPassword))
    }

    // endregion
    // region match

    private val wms = AppProfile(name = "WMS", apps = listOf("com.example.wms"), suggestions = false)

    @Test
    fun `the first matching profile wins`() {
        val specific = AppProfile(name = "qty", apps = listOf("com.example.wms"), hints = listOf("*menge*"), page = Tabs.NUMERIC)
        val hit = AppProfiles.match(listOf(specific, wms), "com.example.wms", text, "Menge")
        assertEquals(0, hit?.index)
        assertSame(specific, hit?.profile)
        assertEquals(1, AppProfiles.match(listOf(specific, wms), "com.example.wms", text, "Artikel")?.index)
        // Not merged: the specific one does not get the general one's suggestions.
        assertNull(hit!!.profile.suggestions)
    }

    @Test
    fun `an apps-only rule matches every field of the app`() {
        for (type in listOf(text, number, InputType.TYPE_NULL, textPassword)) {
            assertEquals(0, AppProfiles.match(listOf(wms), "com.example.wms", type, null)?.index)
        }
    }

    @Test
    fun `apps need a package name`() {
        assertNull(AppProfiles.match(listOf(wms), null, text, null))
        assertNull(AppProfiles.match(listOf(wms), "com.example.other", text, null))
    }

    @Test
    fun `hints need a hint`() {
        val p = AppProfile(hints = listOf("*"), page = Tabs.NUMERIC)
        assertNull(AppProfiles.match(listOf(p), "a.b", text, null))
        assertNull(AppProfiles.match(listOf(p), "a.b", text, "   "))
        assertEquals(0, AppProfiles.match(listOf(p), "a.b", text, " Menge ")?.index)
    }

    @Test
    fun `hints are matched case-insensitively and trimmed`() {
        val p = AppProfile(hints = listOf("menge"), page = Tabs.NUMERIC)
        assertEquals(0, AppProfiles.match(listOf(p), null, text, "  MENGE ")?.index)
        assertNull(AppProfiles.match(listOf(p), null, text, "Menge (Stk)"))
    }

    @Test
    fun `a fields rule never matches a password field`() {
        val p = AppProfile(fields = FieldKind.entries.toSet(), page = Tabs.FUNCTION)
        assertNull(AppProfiles.match(listOf(p), "a.b", textPassword, null))
        assertNull(AppProfiles.match(listOf(p), "a.b", visiblePassword, null))
        assertNull(AppProfiles.match(listOf(p), "a.b", numberPassword, null))
        assertEquals(0, AppProfiles.match(listOf(p), "a.b", text, null)?.index)
    }

    @Test
    fun `all conditions must match`() {
        val p = AppProfile(apps = listOf("com.example.*"), fields = setOf(FieldKind.TEXT), hints = listOf("*item*"), page = Tabs.FUNCTION)
        assertEquals(0, AppProfiles.match(listOf(p), "com.example.wms", text, "Item number")?.index)
        assertNull(AppProfiles.match(listOf(p), "org.example.wms", text, "Item number"))
        assertNull(AppProfiles.match(listOf(p), "com.example.wms", number, "Item number"))
        assertNull(AppProfiles.match(listOf(p), "com.example.wms", text, "Quantity"))
    }

    @Test
    fun `nothing matches`() {
        assertNull(AppProfiles.match(emptyList(), "com.example.wms", text, "x"))
        assertNull(AppProfiles.match(listOf(wms, AppProfile(fields = setOf(FieldKind.URL), page = Tabs.SYMBOLS)), "com.other", text, null))
    }

    // endregion
    // region apply

    @Test
    fun `each override lands in its place`() {
        val base = KeyboardConfig(scanner = ScannerSettings(data = DataSettings(suffix = "{ENTER}")))
        val p = AppProfile(
            apps = listOf("x"),
            page = Tabs.FUNCTION,
            alphabet = Alphabet.AZERTY,
            numericOrder = NumericOrder.CALCULATOR,
            topBar = TopBar.TABS,
            emojiKey = false,
            suggestions = false,
            autocorrect = false,
            scanSuffix = "{TAB}",
        )
        val c = AppProfiles.apply(base, p)
        assertEquals(Tabs.FUNCTION, c.tabs.preferred)
        assertEquals(Alphabet.AZERTY, c.layout.alphabet)
        assertEquals(NumericOrder.CALCULATOR, c.layout.numericOrder)
        assertEquals(TopBar.TABS, c.layout.topBar)
        assertFalse(c.appearance.emojiKey)
        assertFalse(c.typing.suggestions)
        assertFalse(c.typing.autocorrect)
        assertEquals("{TAB}", c.scanner.data.suffix)
        // Everything else as stored.
        assertEquals(base.copy(tabs = c.tabs, layout = c.layout, appearance = c.appearance, typing = c.typing, scanner = c.scanner), c)
        assertEquals(base.tabs.copy(preferred = Tabs.FUNCTION), c.tabs)
        assertEquals(base.scanner.copy(data = base.scanner.data.copy(suffix = "{TAB}")), c.scanner)
    }

    @Test
    fun `values a profile does not set stay`() {
        val base = KeyboardConfig(typing = TypingSettings(suggestions = false, autocorrect = true))
        val c = AppProfiles.apply(base, AppProfile(apps = listOf("x"), suggestions = true))
        assertTrue(c.typing.suggestions)
        assertTrue(c.typing.autocorrect)
        assertEquals(base.copy(typing = c.typing), c)
        assertEquals(base.layout, c.layout)
        assertEquals(base.tabs, c.tabs)
        assertEquals(base.scanner, c.scanner)
        assertEquals(base.appearance, c.appearance)
    }

    @Test
    fun `no profile keeps the same instance`() {
        val base = KeyboardConfig()
        assertSame(base, AppProfiles.apply(base, null))
        assertSame(base, EffectiveConfig.of(base, AppProfiles.layer(null)))
    }

    @Test
    fun `an empty scan suffix types nothing`() {
        val c = AppProfiles.apply(KeyboardConfig(), AppProfile(apps = listOf("x"), scanSuffix = ""))
        assertEquals("", c.scanner.data.suffix)
    }

    @Test
    fun `the stored configuration is not changed`() {
        val base = KeyboardConfig(appProfiles = listOf(wms))
        val copy = base.copy()
        AppProfiles.apply(base, AppProfile(apps = listOf("x"), alphabet = Alphabet.NORDIC))
        assertEquals(copy, base)
    }

    // endregion
    // region start page

    private val textField = FieldPolicy.field(text)

    @Test
    fun `profile page opens in text fields`() {
        val c = AppProfiles.apply(KeyboardConfig(), AppProfile(apps = listOf("x"), page = Tabs.FUNCTION))
        assertEquals(Page.Function, FieldPolicy.startPage(textField, c, scanAvailable = true))
    }

    @Test
    fun `number fields still open their pad`() {
        val c = AppProfiles.apply(KeyboardConfig(), AppProfile(apps = listOf("x"), page = Tabs.FUNCTION))
        assertEquals(Page.Numeric(NumericVariant.NUMBER), FieldPolicy.startPage(FieldPolicy.field(number), c, scanAvailable = true))
    }

    @Test
    fun `page scan without a scanner falls back to the letters`() {
        val c = AppProfiles.apply(KeyboardConfig(), AppProfile(apps = listOf("x"), page = Tabs.SCAN))
        assertEquals(Page.Scan, FieldPolicy.startPage(textField, c, scanAvailable = true))
        assertEquals(Page.Alpha(AlphaVariant.TEXT), FieldPolicy.startPage(textField, c, scanAvailable = false))
    }

    @Test
    fun `page scan with the scanner toggled off falls back to the letters`() {
        val stored = KeyboardConfig(
            appProfiles = listOf(AppProfile(apps = listOf("x"), page = Tabs.SCAN)),
            quickToggles = QuickToggleSettings(setOf(QuickToggle.SCANNER)),
        )
        val hit = AppProfiles.match(stored.appProfiles, "x", text, null)
        val effective = EffectiveConfig.of(
            stored,
            AppProfiles.layer(hit?.profile),
            QuickToggles.layer(stored, mapOf(QuickToggle.SCANNER to false), scanAvailable = true),
        )
        assertEquals(Tabs.SCAN, effective.tabs.preferred)
        assertEquals(ScanTriggerType.NONE, effective.scanner.trigger)
        // With trigger none the scanner is gone (ScanTrigger.available is false).
        assertEquals(Page.Alpha(AlphaVariant.TEXT), FieldPolicy.startPage(textField, effective, scanAvailable = false))
    }

    @Test
    fun `profile and quick toggles together`() {
        val stored = KeyboardConfig(appProfiles = listOf(AppProfile(apps = listOf("x"), suggestions = false, topBar = TopBar.TABS)))
        val profile = AppProfiles.layer(AppProfiles.match(stored.appProfiles, "x", text, null)?.profile)
        val effective = EffectiveConfig.of(stored, profile, QuickToggles.layer(stored, mapOf(QuickToggle.GLOVE to true), scanAvailable = false))
        assertFalse(effective.typing.suggestions)
        assertEquals(TopBar.TABS, effective.layout.topBar)
        assertEquals(QuickToggles.GLOVE_KEY_HEIGHT, effective.appearance.keyHeight)
        // The stored configuration keeps its values (it is what export and settings show).
        assertTrue(stored.typing.suggestions)
        assertEquals(TopBar.TOOLBAR, stored.layout.topBar)
    }

    // endregion

    // region Log line

    @Test
    fun `log key changes with the app even when no profile matches`() {
        // No appProfiles yet: each new app still gets a line, so admins can read its package.
        val start = AppProfiles.logKey(null, null)
        val sap = AppProfiles.logKey("com.sap.mobile", AppProfiles.match(emptyList(), "com.sap.mobile", text, null))
        val wms = AppProfiles.logKey("com.example.wms", null)
        assertFalse(start == sap)
        assertFalse(sap == wms)
        assertEquals(sap, AppProfiles.logKey("com.sap.mobile", null))
        assertEquals("No app profile for com.sap.mobile", AppProfiles.logLine("com.sap.mobile", null))
    }

    @Test
    fun `log key changes between apps that hit the same profile`() {
        val profiles = listOf(AppProfile(name = "Numbers", fields = setOf(FieldKind.NUMBER), page = "numeric"))
        val a = AppProfiles.match(profiles, "a.app", number, null)
        val b = AppProfiles.match(profiles, "b.app", number, null)
        assertEquals(a?.index, b?.index)
        assertFalse(AppProfiles.logKey("a.app", a) == AppProfiles.logKey("b.app", b))
        assertEquals("App profile 1 \"Numbers\" for b.app", AppProfiles.logLine("b.app", b))
        // The hint never reaches the line.
        val hinted = AppProfiles.match(listOf(AppProfile(name = "Lot", hints = listOf("Lot*"), page = "numeric")), "a.app", text, "Lot 4711")
        assertFalse(AppProfiles.logLine("a.app", hinted).contains("4711"))
    }

    // endregion
}

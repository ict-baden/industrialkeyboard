package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun `empty object gives defaults`() {
        assertEquals(KeyboardConfig(), ConfigJson.parse("""{"configVersion":3}"""))
        // Without configVersion: written for 2.1, so the defaults of 2.1.
        assertEquals(ConfigVersion.LEGACY, ConfigJson.parse("{}"))
    }

    @Test
    fun `round trip keeps every setting`() {
        val config = KeyboardConfig(
            appearance = Appearance(ThemeMode.CONTRAST, "#FF8F00", keyHeight = 130, width = 80, numberRow = true, functionRow = true, keyHints = false, modifierRow = true, functionModifiers = true, dynamicColor = false, emojiKey = false),
            layout = LayoutSettings(Alphabet.AZERTY, Navigation.KEYS, NumericOrder.CALCULATOR, umlautKeys = true, symbolsKey = true),
            tabs = TabSettings(alpha = true, numeric = false, symbols = true, function = true, scan = false, preferred = "wms"),
            keys = KeySettings(ProgrammableKey.DEFAULTS + ("numeric.p1" to "{F13}") + ("alpha.p1" to "OK{ENTER}")),
            typing = TypingSettings(autoCapitalize = false, doubleSpacePeriod = false, longPressDelay = 650, suggestions = false, autocorrect = true),
            feedback = FeedbackSettings(keyPopup = false, sound = true, soundVolume = 40, vibrate = false, vibrationMs = 30),
            scanner = ScannerSettings(
                trigger = ScanTriggerType.INTENT,
                intentAction = "com.example.SCAN",
                intentPackage = "com.example.scanner",
                scanKey = true,
                input = ScanInputSettings(
                    enabled = true,
                    action = "com.example.DATA",
                    extra = "barcode",
                    suffix = ScanSuffix.TAB,
                    permission = "com.example.permission.SCAN",
                    allowedPackages = listOf("com.symbol.datawedge", "com.example.scanner"),
                ),
            ),
            customLayouts = listOf(
                CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK", "OK{ENTER}", 2f, KeyStyle.ACCENT), CustomKey("", "{TAB}"), CustomKey("", " ", 3f), CustomKey(" ", " "), CustomKey("Sp", " ")))),
            ),
            adminPasswordHash = PasswordHasher.hash("secret"),
            quickToggles = QuickToggleSettings(setOf(QuickToggle.GLOVE, QuickToggle.SOUND)),
            accessibility = AccessibilitySettings(liftToType = true, speakPasswordKeys = PasswordKeySpeech.NEVER, followFontSize = true),
        )
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
    }

    @Test
    fun `quick toggles default to glove contrast and sound and survive an empty list`() {
        assertEquals(setOf(QuickToggle.GLOVE, QuickToggle.CONTRAST, QuickToggle.SOUND), ConfigJson.parse("{}").quickToggles.allowed)
        assertEquals(QuickToggleSettings(), ConfigJson.parse("""{"quickToggles":{}}""").quickToggles)
        assertEquals(QuickToggleSettings(), ConfigJson.parse("""{"quickToggles":{"allowed":null}}""").quickToggles)
        val none = KeyboardConfig(quickToggles = QuickToggleSettings(emptySet()))
        assertEquals(none, ConfigJson.parse(ConfigJson.write(none)))
        // Names case-insensitive, duplicates collapse.
        assertEquals(
            setOf(QuickToggle.SCANNER, QuickToggle.GLOVE),
            ConfigJson.parse("""{"quickToggles":{"allowed":["Scanner","glove","glove"]}}""").quickToggles.allowed,
        )
        // Also in the compact form of the QR code, an empty list included.
        val defaults = ConfigJson.write(KeyboardConfig())
        for (config in listOf(none, KeyboardConfig(quickToggles = QuickToggleSettings(QuickToggle.entries.toSet())), KeyboardConfig())) {
            assertEquals(config, ConfigJson.parse(ConfigCodec.compact(ConfigJson.write(config), defaults)))
        }
    }

    @Test
    fun `key widths are exported as written`() {
        val config = KeyboardConfig(customLayouts = listOf(CustomLayout("a", "A", listOf(listOf(CustomKey("x", width = 1.2f))))))
        val json = ConfigJson.write(config)
        assertTrue(json, json.contains("\"width\": 1.2") && !json.contains("1.20000"))
    }

    @Test
    fun `plain admin password is hashed`() {
        val config = ConfigJson.parse("""{"adminPassword":"1234"}""")
        assertTrue(PasswordHasher.verify("1234", config.adminPasswordHash))
    }

    @Test
    fun `ranges are clamped`() {
        val config = ConfigJson.parse(
            """{"appearance":{"keyHeight":500,"width":10},"typing":{"longPressDelay":5},"feedback":{"soundVolume":300,"vibrationMs":1}}""",
        )
        assertEquals(160, config.appearance.keyHeight)
        assertEquals(50, config.appearance.width)
        assertEquals(200, config.typing.longPressDelay)
        assertEquals(100, config.feedback.soundVolume)
        assertEquals(5, config.feedback.vibrationMs)
    }

    @Test
    fun `string keys in custom layouts are macros`() {
        val config = ConfigJson.parse("""{"customLayouts":[{"id":"te","rows":[["{F13}","Y{ENTER}"]]}]}""")
        val layout = config.customLayouts.single()
        assertEquals("TE", layout.label)
        assertEquals("{F13}", layout.rows[0][0].output)
    }

    @Test
    fun `space keys survive saving and loading`() {
        // Up to 2.0.0 a " " key was saved as {"label":"","output":" "} and then refused on load,
        // which reset the whole configuration including the admin password.
        val config = ConfigJson.parse("""{"customLayouts":[{"id":"te","rows":[[" ", {"label":"","output":" "}, {"label":" "}]]}]}""")
        assertEquals(listOf(" ", " ", " "), config.customLayouts.single().rows[0].map { it.output })
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        val saved = KeyboardConfig(customLayouts = listOf(CustomLayout("a", "A", listOf(listOf(CustomKey("", " "))))))
        assertEquals(saved, ConfigJson.parse(ConfigJson.write(saved)))
    }

    @Test
    fun `scan input is off by default`() {
        assertEquals(false, ConfigJson.parse("{}").scanner.input.enabled)
        assertEquals(false, ConfigJson.parse("""{"scanner":{"input":{}}}""").scanner.input.enabled)
        assertEquals(true, ConfigJson.parse("""{"scanner":{"input":{"enabled":true}}}""").scanner.input.enabled)
    }

    @Test
    fun `json errors do not repeat the input`() {
        val e = runCatching { ConfigJson.parse("""{"adminPassword":"geheim123", "appearance": }""") }.exceptionOrNull()
        assertTrue(e is ConfigException)
        assertTrue(e!!.message, e.message!!.startsWith("Invalid JSON"))
        assertTrue(e.message, !e.message!!.contains("geheim123"))
    }

    @Test
    fun `password hashes with absurd iteration counts are refused`() {
        rejected("""{"adminPasswordHash":"pbkdf2-sha256${'$'}2000000000${'$'}AAAA${'$'}AAAA"}""", "adminPasswordHash")
        rejected("""{"adminPasswordHash":"pbkdf2-sha256${'$'}x${'$'}AAAA${'$'}AAAA"}""", "adminPasswordHash")
        assertTrue(!PasswordHasher.verify("x", "pbkdf2-sha256${'$'}2000000000${'$'}AAAA${'$'}AAAA"))
    }

    @Test
    fun `language follows the device unless configured`() {
        assertEquals(Languages.AUTO, ConfigJson.parse("{}").language)
        val chinese = ConfigJson.parse("""{"language":"zh-hans"}""")
        assertEquals("zh-Hans", chinese.language)
        assertEquals("zh-Hans", ConfigJson.parse(ConfigJson.write(chinese)).language)
        rejected("""{"language":"tlh"}""", "language")
    }

    @Test
    fun `invalid configurations are rejected with a path`() {
        rejected("""{"appearance":{"accentColor":"blue"}}""", "appearance.accentColor")
        rejected("""{"layout":{"alphabet":"qwertzz"}}""", "layout.alphabet")
        rejected("""{"keys":{"alpha.p9":"x"}}""", "keys")
        rejected("""{"keys":{"numeric.p1":"{TABB}"}}""", "keys.numeric.p1")
        rejected("""{"tabs":{"preferred":"nope"}}""", "tabs.preferred")
        rejected("""{"scanner":{"trigger":"intent"}}""", "scanner.intentAction")
        rejected("""{"customLayouts":[{"id":"scan","rows":[["a"]]}]}""", "reserved")
        rejected("""{"customLayouts":[{"id":"a","rows":[["x"]]},{"id":"a","rows":[["y"]]}]}""", "twice")
        rejected("""{"customLayouts":[{"id":"Bad Id","rows":[["x"]]}]}""", "customLayouts[0].id")
        rejected("""{"customLayouts":[{"id":"a","rows":[[{"label":"x","width":20}]]}]}""", "width")
        rejected("""{"version":99}""", "version")
        rejected("""not json""", "Invalid JSON")
        rejected("""{"customLayouts":[{"id":"a","rows":[[""]]}]}""", "customLayouts[0].rows[0][0]")
        rejected("""{"customLayouts":[{"id":"a","rows":[[{"label":"","output":""}]]}]}""", "label or output")
        rejected("""{"scanner":{"input":{"allowedPackages":["not a package"]}}}""", "scanner.input.allowedPackages[0]")
        rejected("""{"quickToggles":{"allowed":["torch"]}}""", "quickToggles.allowed[0]")
        rejected("""{"quickToggles":{"allowed":"glove"}}""", "quickToggles.allowed")
        rejected("""{"quickToggles":{"allowed":["glove","sound","torch"]}}""", "quickToggles.allowed[2]: unknown toggle \"torch\" (glove, contrast, scanner, sound)")
        rejected("""{"quickToggles":{"allowed":"glove"}}""", "quickToggles.allowed: array expected")
        rejected("""{"keys":{"alpha.p1":"{TOGGLE:torch}"}}""", "keys.alpha.p1: unknown quick toggle {TOGGLE:torch}")
        rejected("""{"accessibility":{"speakPasswordKeys":"loud"}}""", "accessibility.speakPasswordKeys: unknown value \"loud\" (headset, always, never)")
    }

    @Test
    fun `accessibility defaults change nothing and booleans are lenient`() {
        val defaults = ConfigJson.parse("{}").accessibility
        // Off: TalkBack's own typing preference (lift or double tap) decides.
        assertFalse(defaults.liftToType)
        assertEquals(PasswordKeySpeech.HEADSET, defaults.speakPasswordKeys)
        assertFalse(defaults.followFontSize)
        assertEquals(defaults, ConfigJson.parse("""{"accessibility":{}}""").accessibility)
        val set = ConfigJson.parse("""{"accessibility":{"liftToType":"true","speakPasswordKeys":"ALWAYS","followFontSize":true}}""").accessibility
        assertTrue(set.liftToType)
        assertEquals(PasswordKeySpeech.ALWAYS, set.speakPasswordKeys)
        assertTrue(set.followFontSize)
    }

    private fun rejected(json: String, expected: String) {
        try {
            ConfigJson.parse(json)
            fail("accepted: $json")
        } catch (e: ConfigException) {
            assertNotNull(e.message)
            assertTrue("\"${e.message}\" should mention $expected", e.message!!.contains(expected))
        }
    }

    @Test
    fun `text editing tile is on by default and survives saving`() {
        assertTrue(ConfigJson.parse("{}").typing.textEditing)
        assertTrue(ConfigJson.parse("""{"typing":{"clipboardHistory":true}}""").typing.textEditing)
        val off = ConfigJson.parse("""{"typing":{"textEditing":false}}""")
        assertFalse(off.typing.textEditing)
        assertEquals(off, ConfigJson.parse(ConfigJson.write(off)))
        assertTrue(ConfigJson.parse(ConfigJson.write(KeyboardConfig())).typing.textEditing)
        // Lenient like the other typing switches: anything else keeps the default.
        assertTrue(ConfigJson.parse("""{"typing":{"textEditing":"maybe"}}""").typing.textEditing)
    }

    @Test
    fun `a misspelt edit macro is rejected with its path`() {
        rejected("""{"keys":{"alpha.p1":"{EDITT}"}}""", "keys.alpha.p1: unknown token {EDITT}")
        assertEquals("{EDIT}", ConfigJson.parse("""{"keys":{"alpha.p1":"{EDIT}"}}""").programmable(ProgrammableKey.ALPHA_P1))
    }
}

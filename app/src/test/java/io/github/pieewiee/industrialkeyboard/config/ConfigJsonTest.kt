package io.github.pieewiee.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConfigJsonTest {

    @Test
    fun `empty object gives defaults`() {
        assertEquals(KeyboardConfig(), ConfigJson.parse("{}"))
    }

    @Test
    fun `round trip keeps every setting`() {
        val config = KeyboardConfig(
            appearance = Appearance(ThemeMode.CONTRAST, "#FF8F00", keyHeight = 130, width = 80, numberRow = true, functionRow = true, keyHints = false),
            layout = LayoutSettings(Alphabet.AZERTY, Navigation.KEYS, NumericOrder.CALCULATOR),
            tabs = TabSettings(alpha = true, numeric = false, symbols = true, function = true, scan = false, preferred = "wms"),
            keys = KeySettings(ProgrammableKey.DEFAULTS + ("numeric.p1" to "{F13}") + ("alpha.p1" to "OK{ENTER}")),
            typing = TypingSettings(autoCapitalize = false, doubleSpacePeriod = false, longPressDelay = 650),
            feedback = FeedbackSettings(keyPopup = false, sound = true, soundVolume = 40, vibrate = false, vibrationMs = 30),
            scanner = ScannerSettings(
                trigger = ScanTriggerType.INTENT,
                intentAction = "com.example.SCAN",
                intentPackage = "com.example.scanner",
                scanKey = true,
                input = ScanInputSettings(enabled = false, action = "com.example.DATA", extra = "barcode", suffix = ScanSuffix.TAB),
            ),
            customLayouts = listOf(
                CustomLayout("wms", "WMS", listOf(listOf(CustomKey("OK", "OK{ENTER}", 2f, KeyStyle.ACCENT), CustomKey("", "{TAB}")))),
            ),
            adminPasswordHash = PasswordHasher.hash("secret"),
        )
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
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
}

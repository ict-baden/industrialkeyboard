package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScanSettingsJsonTest {

    private fun rejected(json: String, fragment: String) {
        try {
            ConfigJson.parse(json)
            fail("accepted: $json")
        } catch (e: ConfigException) {
            assertTrue("\"${e.message}\" should contain \"$fragment\"", e.message.orEmpty().contains(fragment))
        }
    }

    private val full = KeyboardConfig(
        scanner = ScannerSettings(
            camera = CameraSettings(
                symbologies = setOf(Symbology.EAN13, Symbology.QR),
                aim = AimMode.CENTER,
                continuous = true,
                duplicateDelayMs = 2000,
                confirmReads = 2,
                minLength = 4,
                maxLength = 40,
                zoom = 2.5f,
                torch = true,
                inverted = false,
                beep = false,
                vibrate = false,
                timeoutSeconds = 0,
            ),
            data = DataSettings(
                prefix = "{TAB}",
                suffix = "{ENTER}",
                trim = false,
                removeSpaces = true,
                case = TextCase.UPPER,
                stripLeading = 1,
                stripTrailing = 2,
                replace = listOf(Replacement("a", "b"), Replacement("(\\d)", "<$1>", regex = true)),
                gs1 = Gs1Mode.RAW,
                gsReplacement = "{TAB}",
                rules = listOf(
                    ScanRule(name = "GTIN", symbologies = setOf(Symbology.EAN13, Symbology.UPCA), stripTrailing = 1, suffix = ""),
                    ScanRule(startsWith = "TEST", action = RuleAction.DROP),
                    ScanRule(minLength = 5, maxLength = 5, pattern = "\\d+", case = TextCase.LOWER, removeSpaces = false, replace = emptyList(), prefix = "Z"),
                ),
                unmatched = RuleAction.DROP,
            ),
        ),
    )

    @Test
    fun `round trip of all scan fields`() {
        assertEquals(full, ConfigJson.parse(ConfigJson.write(full)))
        assertEquals(KeyboardConfig(), ConfigJson.parse(ConfigJson.write(KeyboardConfig())))
    }

    @Test
    fun `suffix null and empty are different`() {
        val none = ConfigJson.parse("""{"scanner":{"data":{"suffix":""}}}""")
        assertEquals("", none.scanner.data.suffix)
        val legacy = ConfigJson.parse("""{"scanner":{"data":{"suffix":null}}}""")
        assertEquals(null, legacy.scanner.data.suffix)
        assertNotEquals(none, legacy)
    }

    @Test
    fun `invalid values name the field`() {
        rejected("""{"scanner":{"camera":{"symbologies":["ean-13"]}}}""", "scanner.camera.symbologies[0]")
        rejected("""{"scanner":{"camera":{"aim":"middle"}}}""", "scanner.camera.aim")
        rejected("""{"scanner":{"camera":{"zoom":5}}}""", "scanner.camera.zoom")
        rejected("""{"scanner":{"camera":{"confirmReads":4}}}""", "scanner.camera.confirmReads")
        rejected("""{"scanner":{"camera":{"minLength":10,"maxLength":5}}}""", "scanner.camera.maxLength")
        rejected("""{"scanner":{"camera":{"timeoutSeconds":"x"}}}""", "scanner.camera.timeoutSeconds")
        rejected("""{"scanner":{"data":{"prefix":"{TABB}"}}}""", "scanner.data.prefix")
        rejected("""{"scanner":{"data":{"case":"title"}}}""", "scanner.data.case")
        rejected("""{"scanner":{"data":{"replace":[{"find":"(","regex":true}]}}}""", "scanner.data.replace[0].find")
        rejected("""{"scanner":{"data":{"replace":[{"with":"x"}]}}}""", "scanner.data.replace[0].find")
        rejected("""{"scanner":{"data":{"rules":[{"pattern":"["}]}}}""", "scanner.data.rules[0].pattern")
        rejected("""{"scanner":{"data":{"rules":[{"action":"skip"}]}}}""", "scanner.data.rules[0].action")
        rejected("""{"scanner":{"data":{"rules":[{"suffix":"{NOPE}"}]}}}""", "scanner.data.rules[0].suffix")
        rejected("""{"scanner":{"data":{"rules":["x"]}}}""", "scanner.data.rules[0]")
    }
}

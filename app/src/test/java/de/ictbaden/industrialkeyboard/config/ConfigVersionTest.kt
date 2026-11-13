package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowRestrictionsManager
import java.io.File

/** `configVersion`: configurations written for 2.1 keep the 2.1 defaults, new ones get today's. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigVersionTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val storedFile get() = File(context.filesDir, "config.json")

    /** What ConfigStore of 2.1.1 wrote: every option 2.1 knew, nothing newer. */
    private val stored21 = """
        {
          "version": 1,
          "language": "auto",
          "appearance": { "theme": "system", "keyHeight": 100, "width": 100, "numberRow": false, "functionRow": false, "keyHints": true },
          "layout": { "alphabet": "qwertz", "navigation": "tabs", "numericOrder": "phone" },
          "tabs": { "alpha": true, "numeric": true, "symbols": true, "function": true, "scan": true, "preferred": "auto" },
          "keys": { "alpha.p1": "{TAB}", "numeric.p1": "-", "numeric.p2": "/", "numeric.p3": ":", "numeric.p4": "+" },
          "typing": { "autoCapitalize": true, "doubleSpacePeriod": true, "longPressDelay": 400 },
          "feedback": { "keyPopup": true, "sound": false, "vibrate": true },
          "scanner": { "trigger": "auto", "scanKey": false, "input": { "enabled": false, "action": "${ScanInputSettings.DEFAULT_ACTION}", "extra": "data", "suffix": "enter" } }
        }
    """.trimIndent()

    @Before
    fun setUp() {
        ConfigStore.reset()
        storedFile.delete()
        context.getSharedPreferences("provisioning", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `the legacy defaults are those of 2_1_1`() {
        val l = ConfigVersion.LEGACY
        assertEquals(TopBar.TABS, l.layout.topBar)
        assertEquals(Alphabet.QWERTY, l.layout.alphabet)
        assertTrue(l.layout.umlautKeys)
        assertFalse(l.layout.symbolsKey)
        assertEquals("{TAB}", l.programmable(ProgrammableKey.ALPHA_P1))
        assertEquals(400, l.typing.longPressDelay)
        assertFalse(l.typing.suggestions)
        assertFalse(l.typing.autocorrect)
        assertFalse(l.typing.nextWord)
        assertFalse(l.typing.deleteGesture)
        assertEquals(VoiceInput.OFF, l.typing.voice)
        assertFalse(l.appearance.dynamicColor)
        assertFalse(l.appearance.emojiKey)
        assertEquals(18, l.feedback.vibrationMs)
        assertTrue(l.feedback.keyPopup)
        assertFalse(l.scanner.data.trim)
        assertFalse(l.scanner.nfc.enabled)
        assertFalse(l.snippets.allowUser)
        // What 2.1 also had keeps its value.
        assertEquals(KeyboardConfig().tabs, l.tabs)
        assertEquals(KeyboardConfig().scanner.input, l.scanner.input)
        assertEquals(KeyboardConfig().appearance.keyHeight, l.appearance.keyHeight)
        // The app's own defaults stay like Gboard.
        val d = KeyboardConfig()
        assertEquals(TopBar.TOOLBAR, d.layout.topBar)
        assertEquals("", d.programmable(ProgrammableKey.ALPHA_P1))
        assertTrue(d.typing.suggestions)
        assertNull(d.feedback.vibrationMs)
    }

    @Test
    fun `without configVersion missing options get the 2_1 defaults, with 3 today's`() {
        val legacy = ConfigJson.parse("""{"layout":{"alphabet":"qwertz"},"keys":{"numeric.p1":"{F5}"},"feedback":{"sound":true}}""")
        assertEquals(Alphabet.QWERTZ, legacy.layout.alphabet)
        assertEquals(TopBar.TABS, legacy.layout.topBar)
        assertTrue(legacy.layout.umlautKeys)
        // A keys section without alpha.p1 keeps the Tab key of 2.1.
        assertEquals("{TAB}", legacy.programmable(ProgrammableKey.ALPHA_P1))
        assertEquals("{F5}", legacy.programmable(ProgrammableKey.NUMERIC_P1))
        assertEquals(18, legacy.feedback.vibrationMs)
        assertTrue(legacy.feedback.sound)
        assertFalse(legacy.typing.suggestions)

        val current = ConfigJson.parse("""{"configVersion":3,"layout":{"alphabet":"qwertz"},"keys":{"numeric.p1":"{F5}"},"feedback":{"sound":true}}""")
        assertEquals(TopBar.TOOLBAR, current.layout.topBar)
        assertFalse(current.layout.umlautKeys)
        assertEquals("", current.programmable(ProgrammableKey.ALPHA_P1))
        assertNull(current.feedback.vibrationMs)
        assertTrue(current.typing.suggestions)
        assertTrue(current.typing.autocorrect)

        // Set options win in both; null clears a value the legacy defaults would set.
        val explicit = ConfigJson.parse("""{"layout":{"topBar":"toolbar"},"typing":{"suggestions":true},"feedback":{"vibrationMs":null}}""")
        assertEquals(TopBar.TOOLBAR, explicit.layout.topBar)
        assertTrue(explicit.typing.suggestions)
        assertNull(explicit.feedback.vibrationMs)
    }

    @Test
    fun `configVersion 1 and 2 are legacy, higher is current, nonsense is refused`() {
        assertEquals(ConfigVersion.LEGACY, ConfigJson.parse("""{"configVersion":1}"""))
        assertEquals(ConfigVersion.LEGACY, ConfigJson.parse("""{"configVersion":2}"""))
        assertEquals(ConfigVersion.LEGACY, ConfigJson.parse("""{"configVersion":null}"""))
        assertEquals(KeyboardConfig(), ConfigJson.parse("""{"configVersion":3}"""))
        assertEquals(KeyboardConfig(), ConfigJson.parse("""{"configVersion":"3"}"""))
        assertEquals(KeyboardConfig(), ConfigJson.parse("""{"configVersion":4}"""))
        listOf("0", "-1", "2.5", "\"three\"", "true").forEach { value ->
            try {
                ConfigJson.parse("""{"configVersion":$value}""")
                fail("configVersion $value accepted")
            } catch (e: ConfigException) {
                assertTrue(e.message, e.message!!.startsWith("configVersion:"))
            }
        }
    }

    @Test
    fun `write always writes configVersion 3 and a legacy configuration round-trips`() {
        val legacy = ConfigJson.parse(stored21)
        val written = ConfigJson.write(legacy)
        assertEquals(ConfigVersion.CURRENT, JSONObject(written).getInt("configVersion"))
        assertEquals(legacy, ConfigJson.parse(written))
        assertEquals(TopBar.TABS, ConfigJson.parse(written).layout.topBar)
        assertEquals(KeyboardConfig(), ConfigJson.parse(ConfigJson.write(KeyboardConfig())))
    }

    @Test
    fun `the compact export of a legacy configuration means the same`() {
        val legacy = ConfigJson.parse(stored21)
        val compact = ConfigCodec.compact(ConfigJson.write(legacy), ConfigJson.write(KeyboardConfig()))
        assertEquals(ConfigVersion.CURRENT, JSONObject(compact).getInt("configVersion"))
        // The legacy values differ from today's defaults, so they are in the code.
        assertEquals("tabs", JSONObject(compact).getJSONObject("layout").getString("topBar"))
        assertEquals(legacy, ConfigJson.parse(compact))
        assertEquals(legacy, ConfigJson.parse(ConfigCodec.decode(ConfigCodec.encode(compact))))
    }

    @Test
    fun `a file stored by 2_1 loads with its old behaviour, a stored v3 file and no file with today's`() {
        storedFile.writeText(stored21)
        val old = ConfigStore.get(context).config
        assertEquals(ConfigJson.parse(stored21), old)
        assertEquals(TopBar.TABS, old.layout.topBar)
        assertEquals(Alphabet.QWERTZ, old.layout.alphabet)
        assertTrue(old.layout.umlautKeys)
        assertFalse(old.typing.suggestions)
        assertFalse(old.appearance.emojiKey)
        assertFalse(old.scanner.nfc.enabled)
        // The next save writes every value with configVersion 3: nothing changes on the next start.
        ConfigStore.get(context).update { it.copy(feedback = it.feedback.copy(sound = true)) }
        assertTrue(storedFile.readText().contains("\"configVersion\": 3"))
        ConfigStore.reset()
        assertEquals(old.copy(feedback = old.feedback.copy(sound = true)), ConfigStore.get(context).config)

        ConfigStore.reset()
        storedFile.writeText(ConfigJson.write(KeyboardConfig(language = "de")))
        assertEquals(KeyboardConfig(language = "de"), ConfigStore.get(context).config)

        ConfigStore.reset()
        storedFile.delete()
        assertEquals(KeyboardConfig(), ConfigStore.get(context).config)
        assertNotEquals(ConfigVersion.LEGACY, ConfigStore.get(context).config)
    }

    @Test
    fun `managed simple keys work on top of a configuration stored by 2_1`() {
        storedFile.writeText(stored21)
        restrict("sound" to true, "suggestions" to true, "alphabet" to "azerty")
        Provisioning.applyAll(context)
        val c = ConfigStore.get(context).config
        assertTrue(c.feedback.sound)
        assertTrue(c.typing.suggestions)
        assertEquals(Alphabet.AZERTY, c.layout.alphabet)
        // Everything the keys do not name keeps the 2.1 behaviour.
        assertEquals(TopBar.TABS, c.layout.topBar)
        assertFalse(c.typing.autocorrect)
        assertEquals("{TAB}", c.programmable(ProgrammableKey.ALPHA_P1))
        assertEquals(18, c.feedback.vibrationMs)
        // And stays so after a restart.
        ConfigStore.reset()
        assertEquals(c, ConfigStore.get(context).config)
    }

    @Test
    fun `an old config_json pushed again after the update changes nothing`() {
        storedFile.writeText(stored21)
        val before = ConfigStore.get(context).config
        restrict("config_json" to stored21)
        Provisioning.applyAll(context)
        assertEquals(before, ConfigStore.get(context).config)
        // With configVersion 3 the administrator asks for today's defaults.
        restrict("config_json" to """{"configVersion":3,"layout":{"alphabet":"qwertz"}}""")
        Provisioning.applyAll(context)
        val now = ConfigStore.get(context).config
        assertEquals(TopBar.TOOLBAR, now.layout.topBar)
        assertTrue(now.typing.suggestions)
        assertEquals("", now.programmable(ProgrammableKey.ALPHA_P1))
    }

    @Test
    fun `examples marked configVersion 3 are read with today's defaults`() {
        val dir = listOf(File("../examples"), File("examples")).first { it.isDirectory }
        val snippets = ConfigJson.parse(File(dir, "snippets.json").readText())
        assertEquals(TopBar.TOOLBAR, snippets.layout.topBar)
        val profiles = ConfigJson.parse(File(dir, "app-profiles.json").readText())
        assertTrue(profiles.typing.suggestions)
        // A 2.1-style template keeps the 2.1 defaults.
        assertEquals(TopBar.TABS, ConfigJson.parse(File(dir, "warehouse-gloves.json").readText()).layout.topBar)
    }

    private fun restrict(vararg values: Pair<String, Any>) {
        val bundle = Bundle()
        values.forEach { (key, value) ->
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        Shadow.extract<ShadowRestrictionsManager>(context.getSystemService(RestrictionsManager::class.java)).setApplicationRestrictions(bundle)
    }
}

package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.VoiceInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** Delete gesture, dictation rules and the new settings. */
class GboardFeelTest {

    @Test
    fun `delete gesture marks whole words with their spaces`() {
        val before = "Lager 12 ist voll  "
        assertEquals(before.length, DeleteGesture.start(before, 0))
        assertEquals("Lager 12 ist ".length, DeleteGesture.start(before, 1))
        assertEquals("Lager 12 ".length, DeleteGesture.start(before, 2))
        assertEquals(0, DeleteGesture.start(before, 9))
        // Punctuation counts as its own word, like Gboard.
        assertEquals("Hallo".length, DeleteGesture.start("Hallo!", 1))
        assertEquals(0, DeleteGesture.start("", 3))
    }

    @Test
    fun `dictation engine from the setting and what the device offers`() {
        // auto: on the device first, else the speech service.
        assertEquals(Dictation.Engine.ON_DEVICE, Dictation.engine(VoiceInput.AUTO, onDeviceAvailable = true, serviceAvailable = true))
        assertEquals(Dictation.Engine.SERVICE, Dictation.engine(VoiceInput.AUTO, onDeviceAvailable = false, serviceAvailable = true))
        assertEquals(Dictation.Engine.NONE, Dictation.engine(VoiceInput.AUTO, onDeviceAvailable = false, serviceAvailable = false))
        // offline never falls back to a service that may work online.
        assertEquals(Dictation.Engine.ON_DEVICE, Dictation.engine(VoiceInput.OFFLINE, onDeviceAvailable = true, serviceAvailable = true))
        assertEquals(Dictation.Engine.NONE, Dictation.engine(VoiceInput.OFFLINE, onDeviceAvailable = false, serviceAvailable = true))
        assertEquals(Dictation.Engine.NONE, Dictation.engine(VoiceInput.OFF, onDeviceAvailable = true, serviceAvailable = true))
    }

    @Test
    fun `dictated text gets a space and a capital letter where needed`() {
        val de = Locale.GERMANY
        assertEquals("Hallo Welt", Dictation.prepare("hallo Welt", "", autoCapitalize = true, locale = de))
        assertEquals(" noch etwas", Dictation.prepare("noch etwas", "Lager 12", autoCapitalize = true, locale = de))
        assertEquals("noch etwas", Dictation.prepare("noch etwas", "Lager 12 ", autoCapitalize = true, locale = de))
        assertEquals(" Neuer Satz", Dictation.prepare("neuer Satz", "Fertig.", autoCapitalize = true, locale = de))
        assertEquals("Neuer Satz", Dictation.prepare("neuer Satz", "Fertig. ", autoCapitalize = true, locale = de))
        assertEquals("klein", Dictation.prepare("klein", "(", autoCapitalize = true, locale = de))
        assertEquals("neuer Satz", Dictation.prepare("neuer Satz", "", autoCapitalize = false, locale = de))
        assertEquals("", Dictation.prepare("   ", "x", autoCapitalize = true, locale = de))
    }

    @Test
    fun `old value system means auto`() {
        assertEquals(VoiceInput.AUTO, ConfigJson.parse("""{"typing":{"voice":"system"}}""").typing.voice)
        assertEquals(VoiceInput.OFFLINE, ConfigJson.parse("""{"typing":{"voice":"offline"}}""").typing.voice)
        assertEquals(VoiceInput.AUTO, VoiceInput.parse("System"))
        assertNull(VoiceInput.parse("loud"))
    }

    @Test
    fun `new settings survive the json round trip, preview on by default like Gboard`() {
        val config = KeyboardConfig().let {
            it.copy(
                appearance = it.appearance.copy(symbolHints = false, keyBorders = false),
                typing = it.typing.copy(voice = VoiceInput.OFFLINE, deleteGesture = false),
                feedback = it.feedback.copy(keyPopup = false),
            )
        }
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        assertTrue(KeyboardConfig().feedback.keyPopup)
        assertEquals(VoiceInput.OFF, ConfigJson.parse("""{"typing":{"voice":"off"}}""").typing.voice)
    }
}

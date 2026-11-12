package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.VoiceInput
import de.ictbaden.industrialkeyboard.ime.VoiceInputs.Candidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Delete gesture, voice input selection and the new settings. */
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

    private val gboard = listOf(
        Candidate("com.google.android.inputmethod.latin/.LatinIME", "com.google.android.inputmethod.latin", "keyboard", 0),
        Candidate("own/.Ime", "own", "voice", 0),
        Candidate("com.google.android.tts/.VoiceInputMethodService", "com.google.android.tts", "voice", 0),
    )

    @Test
    fun `microphone uses the voice input of another app`() {
        assertEquals("com.google.android.tts", VoiceInputs.pick(VoiceInput.AUTO, "own", gboard)?.packageName)
        assertEquals("com.google.android.tts", VoiceInputs.pick(VoiceInput.SYSTEM, "own", gboard)?.packageName)
        assertNull(VoiceInputs.pick(VoiceInput.OFF, "own", gboard))
        // Planned (issue #24): until then no microphone.
        assertNull(VoiceInputs.pick(VoiceInput.OFFLINE, "own", gboard))
        assertNull(VoiceInputs.pick(VoiceInput.AUTO, "own", gboard.take(2)))
    }

    @Test
    fun `new settings survive the json round trip, preview off by default`() {
        val config = KeyboardConfig().let {
            it.copy(
                appearance = it.appearance.copy(symbolHints = false, keyBorders = false),
                typing = it.typing.copy(voice = VoiceInput.SYSTEM, deleteGesture = false),
                feedback = it.feedback.copy(keyPopup = true),
            )
        }
        assertEquals(config, ConfigJson.parse(ConfigJson.write(config)))
        assertFalse(KeyboardConfig().feedback.keyPopup)
        assertEquals(VoiceInput.OFF, ConfigJson.parse("""{"typing":{"voice":"off"}}""").typing.voice)
    }
}

package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.ConfigException
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigVersion
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.VoiceInput
import de.ictbaden.industrialkeyboard.ime.Dictation.Engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The smart switch of issue #24 and the options that steer it. */
class DictationEngineTest {

    private fun engine(mode: VoiceInput, onDevice: Boolean = false, service: Boolean = false, free: Boolean = false) =
        Dictation.engine(mode, onDevice, service, free)

    @Test
    fun `auto keeps the device's recognition on GMS devices`() {
        assertEquals(Engine.ON_DEVICE, engine(VoiceInput.AUTO, onDevice = true, service = true, free = true))
        assertEquals(Engine.SERVICE, engine(VoiceInput.AUTO, service = true, free = true))
    }

    @Test
    fun `auto takes the free model on devices without any recognizer (AOSP)`() {
        assertEquals(Engine.FREE, engine(VoiceInput.AUTO, free = true))
        assertEquals(Engine.NONE, engine(VoiceInput.AUTO, free = false))
    }

    @Test
    fun `free never uses a recognizer of the device or Google`() {
        assertEquals(Engine.FREE, engine(VoiceInput.FREE, onDevice = true, service = true, free = true))
        assertEquals(Engine.NONE, engine(VoiceInput.FREE, onDevice = true, service = true, free = false))
    }

    @Test
    fun `offline stays on the device - Android's recognition first, then the free model, never the service`() {
        assertEquals(Engine.ON_DEVICE, engine(VoiceInput.OFFLINE, onDevice = true, service = true, free = true))
        assertEquals(Engine.FREE, engine(VoiceInput.OFFLINE, service = true, free = true))
        assertEquals(Engine.NONE, engine(VoiceInput.OFFLINE, service = true, free = false))
    }

    @Test
    fun `off is off`() {
        assertEquals(Engine.NONE, engine(VoiceInput.OFF, onDevice = true, service = true, free = true))
    }

    @Test
    fun `free model is usable with its pack or when it may be downloaded`() {
        assertTrue(Dictation.freeUsable(packInstalled = true, downloadAllowed = false))
        assertTrue(Dictation.freeUsable(packInstalled = false, downloadAllowed = true))
        assertFalse("nothing in the keyboard could make it work: no microphone", Dictation.freeUsable(packInstalled = false, downloadAllowed = false))
    }

    @Test
    fun `defaults - auto, download allowed, our server`() {
        val t = KeyboardConfig().typing
        assertEquals(VoiceInput.AUTO, t.voice)
        assertTrue(t.voiceModelDownload)
        assertNull(t.voiceModelUrl)
        val parsed = ConfigJson.parse("""{"configVersion":3}""").typing
        assertEquals(VoiceInput.AUTO, parsed.voice)
        assertTrue(parsed.voiceModelDownload)
    }

    @Test
    fun `configurations of 2·1 keep dictation off`() {
        assertEquals(VoiceInput.OFF, ConfigVersion.LEGACY.typing.voice)
        assertEquals(VoiceInput.OFF, ConfigJson.parse("""{"typing":{}}""").typing.voice)
        assertEquals(VoiceInput.FREE, ConfigJson.parse("""{"typing":{"voice":"free"}}""").typing.voice)
    }

    @Test
    fun `free and the language pack options are read and written`() {
        val c = ConfigJson.parse("""{"configVersion":3,"typing":{"voice":"FREE","voiceModelDownload":false,"voiceModelUrl":"https://intranet.example/models"}}""")
        assertEquals(VoiceInput.FREE, c.typing.voice)
        assertFalse(c.typing.voiceModelDownload)
        assertEquals("https://intranet.example/models/", c.typing.voiceModelUrl)
        assertEquals(c, ConfigJson.parse(ConfigJson.write(c)))
        assertNull(ConfigJson.parse("""{"configVersion":3,"typing":{"voiceModelUrl":""}}""").typing.voiceModelUrl)
    }

    @Test
    fun `language pack server must be https`() {
        assertThrows(ConfigException::class.java) { ConfigJson.parse("""{"typing":{"voiceModelUrl":"http://intranet.example/models/"}}""") }
        assertThrows(ConfigException::class.java) { ConfigJson.parse("""{"typing":{"voiceModelUrl":"file:///sdcard/x"}}""") }
        assertThrows(ConfigException::class.java) { ConfigJson.parse("""{"typing":{"voice":"vosk"}}""") }
    }
}

package de.ictbaden.industrialkeyboard.app

import de.ictbaden.industrialkeyboard.config.ConfigCodec
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.CustomKey
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ConfigBarcodeTest {

    @Test
    fun `default configuration is tiny`() {
        val payload = ConfigBarcode.payload(KeyboardConfig())
        val json = org.json.JSONObject(payload)
        assertEquals(setOf("version", "configVersion"), json.keys().asSequence().toSet())
        assertEquals(3, json.getInt("configVersion"))
        val code = ConfigBarcode.qr(payload)
        assertNotNull(code)
        assertTrue(code!!.errorCorrectionLevel.ordinal >= QrCode.Ecc.MEDIUM.ordinal)
        assertEquals(KeyboardConfig(), ConfigJson.parse(payload))
    }

    @Test
    fun `payload is the shorter form`() {
        val small = ConfigBarcode.payload(KeyboardConfig(language = "fr"))
        val json = org.json.JSONObject(small)
        assertEquals(setOf("version", "configVersion", "language"), json.keys().asSequence().toSet())
        assertEquals("fr", json.getString("language"))
        val config = KeyboardConfig(
            customLayouts = (1..4).map { n -> CustomLayout("l$n", "Layout $n", listOf((1..10).map { CustomKey("Key $it of layout $n", "{F$it}") })) },
        )
        val payload = ConfigBarcode.payload(config)
        assertTrue(ConfigCodec.isCompressed(payload))
        assertEquals(config, ConfigJson.parse(payload))
    }

    @Test
    fun `big configuration is compressed and still fits`() {
        val config = KeyboardConfig(
            customLayouts = (1..12).map { n -> CustomLayout("l$n", "Layout $n", listOf((1..12).map { CustomKey("Key $it of layout $n", "{F$it}") })) },
        )
        val payload = ConfigBarcode.payload(config)
        assertTrue(payload.startsWith(ConfigCodec.PREFIX))
        assertNotNull(ConfigBarcode.qr(payload))
        assertEquals(config, ConfigJson.parse(payload))
    }

    @Test
    fun `too large for any QR code`() {
        val random = Random(1)
        // Random text does not compress: far beyond the ~2900 bytes of version 40.
        val text = (1..5000).map { 'a' + random.nextInt(26) }.joinToString("")
        assertNull(ConfigBarcode.qr(text))
    }
}

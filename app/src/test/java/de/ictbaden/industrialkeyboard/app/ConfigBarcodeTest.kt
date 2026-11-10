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
    fun `default configuration fits a QR code with at least medium error correction`() {
        val payload = ConfigBarcode.payload(KeyboardConfig())
        val code = ConfigBarcode.qr(payload)
        assertNotNull(code)
        assertTrue(code!!.errorCorrectionLevel.ordinal >= QrCode.Ecc.MEDIUM.ordinal)
        assertEquals(KeyboardConfig(), ConfigJson.parse(payload))
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

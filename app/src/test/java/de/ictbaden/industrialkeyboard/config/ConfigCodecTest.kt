package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

class ConfigCodecTest {

    @Test
    fun `short configurations stay plain minified JSON`() {
        val encoded = ConfigCodec.encode("""{ "layout": { "alphabet": "qwertz" } }""")
        assertEquals("""{"layout":{"alphabet":"qwertz"}}""", encoded)
        assertEquals(encoded, ConfigCodec.decode(encoded))
    }

    @Test
    fun `long configurations are compressed and come back`() {
        val config = KeyboardConfig(
            customLayouts = (1..8).map { n -> CustomLayout("l$n", "Layout $n", listOf((1..10).map { CustomKey("Key $it of layout $n", "{F$it}") })) },
        )
        val json = ConfigJson.write(config)
        val encoded = ConfigCodec.encode(json)
        assertTrue(encoded.startsWith(ConfigCodec.PREFIX))
        assertTrue("compressed: ${encoded.length} < ${json.length}", encoded.length < json.length / 2)
        assertFalse(encoded.contains('+') || encoded.contains('/') || encoded.contains('='))
        assertEquals(config, ConfigJson.parse(encoded))
        assertEquals(config, ConfigJson.parse("  $encoded\n"))
    }

    @Test
    fun `damaged or oversized payloads are refused`() {
        listOf("ICTZ1:!!!", "ICTZ1:AAAA", "ICTZ1:").forEach {
            try {
                ConfigCodec.decode(it)
                fail("accepted $it")
            } catch (_: ConfigException) {
            }
        }
        // 2 MB of spaces compress to a few KB.
        val deflater = Deflater(9, true)
        deflater.setInput(ByteArray(2_000_000) { ' '.code.toByte() })
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        val bomb = ConfigCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
        try {
            ConfigCodec.decode(bomb)
            fail("accepted 2 MB")
        } catch (e: ConfigException) {
            assertTrue(e.message!!.contains("1 MB"))
        }
    }
}

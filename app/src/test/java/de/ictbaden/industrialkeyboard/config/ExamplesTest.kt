package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every template in `examples/` must be accepted by the app and survive a round trip. */
class ExamplesTest {

    @Test
    fun `examples are valid`() {
        val dir = listOf(File("../examples"), File("examples")).first { it.isDirectory }
        val files = dir.listFiles { file -> file.extension == "json" }.orEmpty().sortedBy { it.name }
        assertTrue("no examples found in ${dir.absolutePath}", files.isNotEmpty())
        files.forEach { file ->
            val config = try {
                ConfigJson.parse(file.readText())
            } catch (e: ConfigException) {
                throw AssertionError("${file.name}: ${e.message}", e)
            }
            assertEquals(file.name, config, ConfigJson.parse(ConfigJson.write(config)))
        }
    }
}

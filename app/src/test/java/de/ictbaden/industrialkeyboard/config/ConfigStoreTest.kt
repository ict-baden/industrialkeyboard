package de.ictbaden.industrialkeyboard.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ConfigStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val file get() = File(folder.root, "config.json")

    @Test
    fun `a saved space key loads again`() {
        val errors = mutableListOf<String>()
        val config = KeyboardConfig(
            customLayouts = listOf(CustomLayout("te", "TE", listOf(listOf(CustomKey("", " "), CustomKey("OK", "OK{ENTER}"))))),
            adminPasswordHash = PasswordHasher.hash("secret"),
        )
        ConfigStore(file).update { config }
        assertEquals(config, ConfigStore(file) { errors += it }.config)
        assertTrue(errors.toString(), errors.isEmpty())
    }

    @Test
    fun `a broken file keeps the admin password and a copy`() {
        val hash = PasswordHasher.hash("secret")
        val broken = """{"version":1,"adminPasswordHash":"$hash","appearance":{"accentColor":"blue"}}"""
        file.writeText(broken)
        val errors = mutableListOf<String>()
        val store = ConfigStore(file) { errors += it }
        assertEquals(KeyboardConfig(adminPasswordHash = hash), store.config)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
        val copies = folder.root.listFiles { f -> f.name.startsWith("config.json.invalid-") }.orEmpty()
        assertEquals(1, copies.size)
        assertEquals(broken, copies[0].readText())
        assertTrue(errors.single(), errors.single().contains("admin password kept"))
    }

    @Test
    fun `a saved file that breaks keeps the admin password, slashes included`() {
        // org.json writes "/" as "\/"; Base64 hashes usually contain one.
        val hash = generateSequence { PasswordHasher.hash("secret") }.first { it.contains('/') }
        ConfigStore(file).update { KeyboardConfig(adminPasswordHash = hash, appearance = Appearance(accentColor = "#FF8F00")) }
        file.writeText(file.readText().replace("#FF8F00", "blue"))
        val store = ConfigStore(file)
        assertEquals(hash, store.config.adminPasswordHash)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
        // Recovered once: the next start reads the saved defaults without another copy.
        assertEquals(hash, ConfigStore(file).config.adminPasswordHash)
        assertEquals(1, folder.root.listFiles { f -> f.name.startsWith("config.json.invalid-") }.orEmpty().size)
    }

    @Test
    fun `a truncated file keeps the admin password`() {
        val hash = PasswordHasher.hash("secret")
        file.writeText("""{"version":1,"adminPasswordHash":"$hash","appea""")
        assertEquals(hash, ConfigStore(file).config.adminPasswordHash)
    }

    @Test
    fun `a broken file without password stays without password`() {
        file.writeText("""{"adminPasswordHash":"pbkdf2-sha256${'$'}60000${'$'}not base64!${'$'}x","appearance":{"accentColor":"blue"}}""")
        assertNull(ConfigStore(file).config.adminPasswordHash)
    }
}

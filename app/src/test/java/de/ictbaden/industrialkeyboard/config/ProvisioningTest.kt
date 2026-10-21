package de.ictbaden.industrialkeyboard.config

import android.app.Application
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
// Plain Application: KeyboardApp would run the migration and a background provisioning check at start.
@Config(sdk = [35], application = Application::class)
class ProvisioningTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ConfigStore.get(context)
    private val appFile get() = File(context.getExternalFilesDir(null), Provisioning.CONFIG_FILE_NAME)

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
    }

    private fun write(json: String, time: Long) {
        appFile.writeText(json)
        appFile.setLastModified(time)
    }

    @Test
    fun `app folder file without password is applied while none is set`() {
        write("""{"language":"fr"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
    }

    @Test
    fun `app folder file needs the current password once one is set`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        write("""{"language":"fr","adminPassword":"mine-now"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("auto", store.config.language)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
        assertTrue(Provisioning.lastError(context).orEmpty().contains("password"))

        write("""{"password":"secret","language":"fr"}""", 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
    }

    @Test
    fun `app folder file is trusted on a managed device`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        val owner = ComponentName("com.example.mdm", "com.example.mdm.Admin")
        val dpm = shadowOf(context.getSystemService(DevicePolicyManager::class.java))
        dpm.setActiveAdmin(owner)
        dpm.setDeviceOwner(owner)
        write("""{"language":"de"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)
    }

    @Test
    fun `an unchanged file is read once`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        write("""{"password":"wrong","language":"fr"}""", 1_000_000)
        // Every focus change checks the sources; a wrong password must not count each time.
        repeat(PasswordGuard.MAX_FAILURES + 1) { Provisioning.applyAll(context) }
        write("""{"password":"secret","language":"fr"}""", 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
    }

    @Test
    fun `rejected json does not leak the password`() {
        write("""{"adminPassword":"geheim123", "language": }""", 1_000_000)
        Provisioning.applyAll(context)
        val error = Provisioning.lastError(context).orEmpty()
        assertTrue(error, error.contains("Invalid JSON") && !error.contains("geheim123"))
    }

    @Test
    fun `migration switches scan input off and applies the sources again`() {
        store.update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = true))) }
        write("""{"scanner":{"input":{"enabled":true}},"language":"fr"}""", 1_000_000)
        Provisioning.applyAll(context)
        store.update { it.copy(language = "de") }
        Migrations.run(context)
        assertEquals(false, store.config.scanner.input.enabled)
        Provisioning.applyAll(context)
        // The file asked for it explicitly, so it is on again.
        assertEquals(true, store.config.scanner.input.enabled)
        assertEquals("fr", store.config.language)
        // Only once.
        store.update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = true))) }
        Migrations.run(context)
        assertEquals(true, store.config.scanner.input.enabled)
    }
}

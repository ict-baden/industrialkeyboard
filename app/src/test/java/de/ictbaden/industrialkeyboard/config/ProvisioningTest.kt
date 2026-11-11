package de.ictbaden.industrialkeyboard.config

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowRestrictionsManager
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProvisioningTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ConfigStore.get(context)
    private val appFile get() = File(context.getExternalFilesDir(null), Provisioning.CONFIG_FILE_NAME)

    @Before
    fun setUp() {
        ConfigStore.reset()
        PasswordGuard.reset()
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
        assertTrue("a refused file with a password is deleted too", !appFile.exists())

        write("""{"password":"secret","language":"fr"}""", 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
    }

    private fun manage() {
        val owner = ComponentName("com.example.mdm", "com.example.mdm.Admin")
        val dpm = shadowOf(context.getSystemService(DevicePolicyManager::class.java))
        dpm.setActiveAdmin(owner)
        dpm.setDeviceOwner(owner)
    }

    @Test
    fun `app folder file on a managed device needs the current password once one is set`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        manage()
        write("""{"language":"de","adminPassword":"mine-now"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("auto", store.config.language)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))

        write("""{"password":"secret","language":"de"}""", 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)
    }

    @Test
    @Config(sdk = [29])
    fun `app folder on a managed device before Android 11 is like the broadcast`() {
        manage()
        write("""{"language":"de"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)

        store.update { it.copy(adminPasswordHash = PasswordHasher.hash("secret")) }
        write("""{"language":"fr"}""", 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)
        assertTrue(Provisioning.lastError(context).orEmpty().contains("password"))
    }

    private fun restrict(vararg values: Pair<String, String>) {
        val bundle = Bundle().apply { values.forEach { (k, v) -> putString(k, v) } }
        Shadow.extract<ShadowRestrictionsManager>(context.getSystemService(RestrictionsManager::class.java)).setApplicationRestrictions(bundle)
    }

    @Test
    fun `managed scan options`() {
        restrict("scan_prefix" to "{TAB}", "scan_suffix" to "{ENTER}{ENTER}", "camera_aim" to "center", "camera_symbologies" to "ean13, QR")
        Provisioning.applyAll(context)
        val scanner = store.config.scanner
        assertEquals("{TAB}", scanner.data.prefix)
        assertEquals("{ENTER}{ENTER}", scanner.data.suffix)
        assertEquals(AimMode.CENTER, scanner.camera.aim)
        assertEquals(setOf(Symbology.EAN13, Symbology.QR), scanner.camera.symbologies)
    }

    @Test
    fun `managed ocr and nfc options`() {
        restrict("ocr_mode" to "pattern", "ocr_pattern" to "[A-Z]{2}\\d{6}", "nfc_read" to "uid")
        Provisioning.applyAll(context)
        val scanner = store.config.scanner
        assertEquals(OcrMode.PATTERN, scanner.ocr.mode)
        assertEquals("[A-Z]{2}\\d{6}", scanner.ocr.pattern)
        assertEquals(NfcRead.UID, scanner.nfc.read)
    }

    @Test
    fun `ocr pattern mode without a pattern is reported`() {
        restrict("ocr_mode" to "pattern")
        Provisioning.applyAll(context)
        assertTrue(Provisioning.lastError(context).orEmpty().contains("ocr_pattern"))
        assertEquals(OcrMode.PICK, store.config.scanner.ocr.mode)
    }

    @Test
    fun `invalid managed scan options are reported`() {
        restrict("camera_symbologies" to "ean13,ean-14")
        Provisioning.applyAll(context)
        assertTrue(Provisioning.lastError(context).orEmpty().contains("ean-14"))
        assertTrue(store.config.scanner.camera.symbologies.isEmpty())
    }

    @Test
    fun `compressed configuration in the app folder`() {
        val json = ConfigJson.write(KeyboardConfig(language = "es", customLayouts = (1..8).map { n -> CustomLayout("l$n", "L$n", listOf((1..10).map { CustomKey("K$it of $n", "{F$it}") })) }))
        val encoded = ConfigCodec.encode(json)
        assertTrue(encoded.startsWith(ConfigCodec.PREFIX))
        write(encoded, 2_000_000)
        Provisioning.applyAll(context)
        assertEquals("es", store.config.language)
        assertEquals(8, store.config.customLayouts.size)
    }

    @Test
    fun `the managed admin password ends an unreadable configuration`() {
        assertTrue(File(context.filesDir, "config.json").mkdir())
        assertTrue(store.unreadable)
        restrict("config_json" to """{"language":"de"}""", "admin_password" to "secret")
        Provisioning.applyAll(context)
        assertFalse(store.unreadable)
        assertEquals("de", store.config.language)
        assertTrue(PasswordHasher.verify("secret", store.config.adminPasswordHash))
        ConfigStore.reset()
        assertEquals("de", store.config.language)
    }

    @Test
    fun `sources are applied again while the configuration is unreadable`() {
        val stored = File(context.filesDir, "config.json")
        assertTrue(stored.mkdir())
        restrict("config_json" to """{"language":"de"}""")
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)
        assertTrue("only in memory", stored.isDirectory)

        // Readable again (e.g. after a restart): the managed configuration is not marked as done.
        assertTrue(stored.delete())
        ConfigStore.reset()
        Provisioning.applyAll(context)
        assertEquals("de", store.config.language)
        assertTrue(stored.isFile)
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
        assertTrue(!appFile.exists())
    }

    @Test
    fun `a file with a password is deleted after use`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        write("""{"password":"secret","language":"fr"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
        assertTrue(!appFile.exists())
    }

    @Test
    @Config(sdk = [29])
    fun `app folder is refused before Android 11 on unmanaged devices`() {
        write("""{"language":"fr"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("auto", store.config.language)
        assertTrue(Provisioning.lastError(context).orEmpty().contains("Android 11"))
    }

    @Test
    fun `a file arriving during the lock is applied after it`() {
        val hash = PasswordHasher.hash("secret")
        store.import(KeyboardConfig(adminPasswordHash = hash))
        val now = System.currentTimeMillis()
        repeat(PasswordGuard.MAX_FAILURES) { PasswordGuard.remote(context).check("guess$it", hash) }
        write("""{"password":"secret","language":"fr"}""", 1_000_000)
        Provisioning.applyAll(context)
        assertEquals("auto", store.config.language)
        assertTrue("kept during the lock", appFile.exists())
        // Unlock by moving the lock end into the past.
        context.getSharedPreferences("password_guard_remote", Context.MODE_PRIVATE).edit().putLong("locked_until", now - 1).commit()
        Provisioning.applyAll(context)
        assertEquals("fr", store.config.language)
    }

    @Test
    fun `migration switches scan input off unless a source asks for it`() {
        store.update { it.copy(language = "de", scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = true))) }
        Migrations.run(context)
        assertEquals(false, store.config.scanner.input.enabled)
        // Nothing else changes.
        assertEquals("de", store.config.language)
        // Only once.
        store.update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = true))) }
        Migrations.run(context)
        assertEquals(true, store.config.scanner.input.enabled)
    }

    @Test
    fun `migration keeps scan input where a file switches it on`() {
        store.update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = true))) }
        write("""{"scanner":{"input":{"enabled":true}}}""", 1_000_000)
        Migrations.run(context)
        assertEquals(true, store.config.scanner.input.enabled)
    }
}

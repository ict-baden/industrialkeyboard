package de.ictbaden.industrialkeyboard.api

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.PasswordHasher
import de.ictbaden.industrialkeyboard.config.Provisioning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ApiReceiverTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val store get() = ConfigStore.get(context)

    @Before
    fun setUp() {
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
    }

    @Test
    fun `refuses a foreign configuration on an unmanaged device without password`() {
        send()
        assertEquals("auto", store.config.language)
        assertTrue(Provisioning.lastError(context).orEmpty().contains("not managed"))
    }

    @Test
    fun `accepts the broadcast on a device with a device owner`() {
        val owner = ComponentName("com.example.mdm", "com.example.mdm.Admin")
        val dpm = shadowOf(context.getSystemService(DevicePolicyManager::class.java))
        dpm.setActiveAdmin(owner)
        dpm.setDeviceOwner(owner)
        send()
        assertEquals("fr", store.config.language)
    }

    @Test
    fun `accepts the broadcast with the admin password only`() {
        store.import(KeyboardConfig(adminPasswordHash = PasswordHasher.hash("secret")))
        send(password = "wrong")
        assertEquals("auto", store.config.language)
        send(password = "secret")
        assertEquals("fr", store.config.language)
    }

    private fun send(password: String? = null) {
        val intent = Intent(KeyboardApi.ACTION_INSTALL_CONFIG).putExtra(KeyboardApi.EXTRA_CONFIG, """{"language":"fr"}""")
        password?.let { intent.putExtra(KeyboardApi.EXTRA_PASSWORD, it) }
        ApiReceiver().onReceive(context, intent)
    }
}

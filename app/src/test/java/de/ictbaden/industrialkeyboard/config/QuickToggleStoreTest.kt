package de.ictbaden.industrialkeyboard.config

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.app.SettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.os.Looper
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class QuickToggleStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = context.getSharedPreferences(QuickToggleStore.PREFS, Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        QuickToggleStore.reset()
        ConfigStore.reset()
        prefs.edit(commit = true) { clear() }
        File(context.filesDir, "config.json").delete()
    }

    @Test
    fun `persists across a new instance on the same prefs`() {
        QuickToggleStore(prefs).set(QuickToggle.GLOVE, true)
        QuickToggleStore(prefs).set(QuickToggle.SOUND, false)
        assertEquals(mapOf(QuickToggle.GLOVE to true, QuickToggle.SOUND to false), QuickToggleStore(prefs).overrides)
    }

    @Test
    fun `set null removes the key`() {
        val store = QuickToggleStore(prefs)
        store.set(QuickToggle.CONTRAST, true)
        assertTrue(prefs.contains("contrast"))
        store.set(QuickToggle.CONTRAST, null)
        assertFalse(prefs.contains("contrast"))
        assertTrue(store.overrides.isEmpty())
    }

    @Test
    fun `clear empties it`() {
        val store = QuickToggleStore(prefs)
        store.replace(mapOf(QuickToggle.GLOVE to true, QuickToggle.SCANNER to false))
        store.clear()
        assertTrue(store.overrides.isEmpty())
        assertTrue(prefs.all.isEmpty())
        assertTrue(QuickToggleStore(prefs).overrides.isEmpty())
    }

    @Test
    fun `unknown keys in the prefs are ignored`() {
        prefs.edit(commit = true) {
            putBoolean("torch", true)
            putString("glove", "yes")
            putBoolean("sound", true)
        }
        assertEquals(mapOf(QuickToggle.SOUND to true), QuickToggleStore(prefs).overrides)
    }

    @Test
    fun `listeners hear changes on the main looper, not unchanged writes`() {
        val store = QuickToggleStore(prefs)
        val heard = mutableListOf<Map<QuickToggle, Boolean>>()
        store.addListener { heard += it }
        store.set(QuickToggle.GLOVE, true)
        store.set(QuickToggle.GLOVE, true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(mapOf(QuickToggle.GLOVE to true)), heard)
    }

    @Test
    fun `reset to defaults also clears the quick toggles`() {
        val toggles = QuickToggleStore.get(context)
        val config = ConfigStore.get(context)
        config.update { it.copy(language = "fr", adminPasswordHash = PasswordHasher.hash("secret")) }
        toggles.set(QuickToggle.GLOVE, true)

        SettingsActivity.resetToDefaults(config, toggles)

        assertEquals(Languages.AUTO, config.config.language)
        assertTrue("admin password kept", PasswordHasher.verify("secret", config.config.adminPasswordHash))
        assertTrue(toggles.overrides.isEmpty())
        assertTrue(prefs.all.isEmpty())
    }
}

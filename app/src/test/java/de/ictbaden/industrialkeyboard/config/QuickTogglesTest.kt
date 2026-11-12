package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Quick toggles: device-local overrides on top of the configuration. */
class QuickTogglesTest {

    private val all = QuickToggle.entries.toSet()

    private fun config(
        theme: ThemeMode = ThemeMode.SYSTEM,
        accent: String? = null,
        keyHeight: Int = 100,
        longPress: Int = 300,
        vibrationMs: Int? = null,
        sound: Boolean = false,
        allowed: Set<QuickToggle> = all,
    ) = KeyboardConfig(
        appearance = Appearance(theme = theme, accentColor = accent, keyHeight = keyHeight),
        typing = TypingSettings(longPressDelay = longPress),
        feedback = FeedbackSettings(keyPopup = true, sound = sound, soundVolume = 70, vibrate = false, vibrationMs = vibrationMs),
        scanner = ScannerSettings(trigger = ScanTriggerType.DATAWEDGE, scanKey = true, input = ScanInputSettings(enabled = true, action = "com.example.SCAN")),
        quickToggles = QuickToggleSettings(allowed),
    )

    @Test
    fun `glove mode raises but never lowers`() {
        val low = QuickToggles.apply(config(keyHeight = 100, longPress = 300, vibrationMs = null), mapOf(QuickToggle.GLOVE to true), true)
        assertEquals(130, low.appearance.keyHeight)
        assertEquals(600, low.typing.longPressDelay)
        assertEquals(40, low.feedback.vibrationMs)
        assertFalse(low.feedback.keyPopup)
        assertTrue(low.feedback.vibrate)

        val high = QuickToggles.apply(config(keyHeight = 150, longPress = 800, vibrationMs = 60), mapOf(QuickToggle.GLOVE to true), true)
        assertEquals(150, high.appearance.keyHeight)
        assertEquals(800, high.typing.longPressDelay)
        assertEquals(60, high.feedback.vibrationMs)
    }

    @Test
    fun `contrast forces the contrast theme without accent`() {
        val on = QuickToggles.apply(config(theme = ThemeMode.LIGHT, accent = "#FF8F00"), mapOf(QuickToggle.CONTRAST to true), true)
        assertEquals(ThemeMode.CONTRAST, on.appearance.theme)
        assertNull(on.appearance.accentColor)

        val off = QuickToggles.apply(config(theme = ThemeMode.CONTRAST), mapOf(QuickToggle.CONTRAST to false), true)
        assertEquals(ThemeMode.SYSTEM, off.appearance.theme)
    }

    @Test
    fun `a configured contrast theme keeps its accent colour`() {
        // Documented: the administrator's own contrast + accent stays; the tile shows on, a tap switches contrast off.
        val base = config(theme = ThemeMode.CONTRAST, accent = "#FF8F00")
        assertTrue(QuickToggles.effective(QuickToggle.CONTRAST, base, emptyMap(), true))
        assertSame(base, QuickToggles.apply(base, mapOf(QuickToggle.CONTRAST to true), true))
        val tapped = QuickToggles.toggled(QuickToggle.CONTRAST, base, emptyMap(), true)
        assertEquals(mapOf(QuickToggle.CONTRAST to false), tapped)
        assertEquals(ThemeMode.SYSTEM, QuickToggles.apply(base, tapped, true).appearance.theme)
    }

    @Test
    fun `scanner off hides the soft trigger only`() {
        val base = config()
        val off = QuickToggles.apply(base, mapOf(QuickToggle.SCANNER to false), scanAvailable = true)
        assertEquals(ScanTriggerType.NONE, off.scanner.trigger)
        assertFalse(off.scanner.scanKey)
        assertEquals(base.scanner.input, off.scanner.input)
        assertEquals(base.scanner.nfc, off.scanner.nfc)

        // Nothing to switch on without a scanner.
        assertSame(base, QuickToggles.apply(base, mapOf(QuickToggle.SCANNER to true), scanAvailable = false))
    }

    @Test
    fun `sound override both ways`() {
        assertFalse(QuickToggles.apply(config(sound = true), mapOf(QuickToggle.SOUND to false), true).feedback.sound)
        val on = QuickToggles.apply(config(sound = false), mapOf(QuickToggle.SOUND to true), true)
        assertTrue(on.feedback.sound)
        assertEquals(70, on.feedback.soundVolume)
    }

    @Test
    fun `toggling twice leaves no override`() {
        val base = config()
        for (t in QuickToggle.entries) {
            val once = QuickToggles.toggled(t, base, emptyMap(), true)
            assertEquals(1, once.size)
            assertTrue("$t", QuickToggles.toggled(t, base, once, true).isEmpty())
        }
        assertEquals(mapOf(QuickToggle.CONTRAST to false), QuickToggles.toggled(QuickToggle.CONTRAST, config(theme = ThemeMode.CONTRAST), emptyMap(), true))
        // Effective state flips with every tap.
        val glove = QuickToggles.toggled(QuickToggle.GLOVE, base, emptyMap(), true)
        assertTrue(QuickToggles.effective(QuickToggle.GLOVE, base, glove, true))
    }

    @Test
    fun `an override that equals a changed base has no effect and disappears on the next tap`() {
        // The user switched sound on, then the administrator switched it on too.
        val overrides = mapOf(QuickToggle.SOUND to true)
        val base = config(sound = true)
        assertSame(base, QuickToggles.apply(base, overrides, true))
        assertEquals(mapOf(QuickToggle.SOUND to false), QuickToggles.toggled(QuickToggle.SOUND, base, overrides, true))
    }

    @Test
    fun `disallowed overrides are ignored but not lost`() {
        val base = config(sound = false, allowed = setOf(QuickToggle.GLOVE))
        val overrides = mapOf(QuickToggle.SOUND to true)
        assertFalse(QuickToggles.apply(base, overrides, true).feedback.sound)
        assertFalse(QuickToggles.effective(QuickToggle.SOUND, base, overrides, true))
        assertFalse(QuickToggle.SOUND in QuickToggles.visible(base, true))
        // A tap on a locked toggle changes nothing, and the stored override stays.
        assertEquals(overrides, QuickToggles.toggled(QuickToggle.SOUND, base, overrides, true))
        // Allowed again: it applies again.
        assertTrue(QuickToggles.apply(base.copy(quickToggles = QuickToggleSettings(all)), overrides, true).feedback.sound)
    }

    @Test
    fun `no overrides returns the base itself`() {
        val base = config()
        assertSame(base, QuickToggles.apply(base, emptyMap(), true))
        assertSame(base, QuickToggles.apply(base, mapOf(QuickToggle.GLOVE to false), true))
        val locked = config(allowed = emptySet())
        assertSame(locked, QuickToggles.apply(locked, mapOf(QuickToggle.GLOVE to true), true))
    }

    @Test
    fun `visible hides the scanner without a scanner`() {
        assertEquals(QuickToggle.entries, QuickToggles.visible(config(), scanAvailable = true))
        assertEquals(listOf(QuickToggle.GLOVE, QuickToggle.CONTRAST, QuickToggle.SOUND), QuickToggles.visible(config(), scanAvailable = false))
        assertTrue(QuickToggles.visible(config(allowed = emptySet()), true).isEmpty())
    }

    @Test
    fun `the scanner is opt-in by default`() {
        assertEquals(setOf(QuickToggle.GLOVE, QuickToggle.CONTRAST, QuickToggle.SOUND), KeyboardConfig().quickToggles.allowed)
        val base = config().copy(quickToggles = QuickToggleSettings())
        assertFalse(QuickToggle.SCANNER in QuickToggles.visible(base, true))
        assertEquals(ScanTriggerType.DATAWEDGE, QuickToggles.apply(base, mapOf(QuickToggle.SCANNER to false), true).scanner.trigger)
    }

    @Test
    fun `the toggles layer reads what is allowed from the stored configuration`() {
        val stored = config(sound = false, allowed = setOf(QuickToggle.GLOVE))
        // A layer below (an app profile) cannot unlock a toggle.
        val profile = EffectiveConfig.Layer { it.copy(quickToggles = QuickToggleSettings(all)) }
        val overrides = mapOf(QuickToggle.SOUND to true, QuickToggle.GLOVE to true)
        val effective = EffectiveConfig.of(stored, profile, QuickToggles.layer(stored, overrides, true))
        assertFalse(effective.feedback.sound)
        assertEquals(130, effective.appearance.keyHeight)
    }

    @Test
    fun `toggles start from what the profile layer made`() {
        val stored = config(keyHeight = 100)
        val profile = EffectiveConfig.Layer { it.copy(appearance = it.appearance.copy(keyHeight = 140, theme = ThemeMode.CONTRAST)) }
        val overrides = mapOf(QuickToggle.GLOVE to true, QuickToggle.CONTRAST to false)
        val effective = EffectiveConfig.of(stored, profile, QuickToggles.layer(stored, overrides, true))
        assertEquals(140, effective.appearance.keyHeight)
        assertEquals(ThemeMode.SYSTEM, effective.appearance.theme)
        assertSame(stored, EffectiveConfig.of(stored, EffectiveConfig.NONE, QuickToggles.layer(stored, emptyMap(), true)))
    }
}

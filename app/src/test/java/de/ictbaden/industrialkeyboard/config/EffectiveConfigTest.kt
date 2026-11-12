package de.ictbaden.industrialkeyboard.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The shared pipeline: stored configuration → app profile → quick toggles. */
class EffectiveConfigTest {

    private val stored = KeyboardConfig(
        appearance = Appearance(theme = ThemeMode.LIGHT, keyHeight = 100),
        typing = TypingSettings(suggestions = true),
        language = "de",
    )

    @Test
    fun `without layers the stored configuration itself is used`() {
        assertSame(stored, EffectiveConfig.of(stored))
        assertSame(stored, EffectiveConfig.of(stored, EffectiveConfig.NONE, EffectiveConfig.NONE))
    }

    @Test
    fun `layers that change nothing keep the same instance`() {
        val unchanged = EffectiveConfig.Layer { it }
        assertSame(stored, EffectiveConfig.of(stored, profile = unchanged, toggles = unchanged))
    }

    @Test
    fun `the profile applies first and the toggles see its result`() {
        val seen = mutableListOf<KeyboardConfig>()
        val profile = EffectiveConfig.Layer { it.copy(typing = it.typing.copy(suggestions = false)) }
        val toggles = EffectiveConfig.Layer { seen += it; it }
        val effective = EffectiveConfig.of(stored, profile, toggles)
        assertEquals(false, seen.single().typing.suggestions)
        assertEquals(false, effective.typing.suggestions)
    }

    @Test
    fun `toggles win over the profile on the same field`() {
        val profile = EffectiveConfig.Layer { it.copy(appearance = it.appearance.copy(keyHeight = 90)) }
        val toggles = EffectiveConfig.Layer { it.copy(appearance = it.appearance.copy(keyHeight = maxOf(it.appearance.keyHeight, 130))) }
        assertEquals(130, EffectiveConfig.of(stored, profile, toggles).appearance.keyHeight)
    }

    @Test
    fun `the stored configuration is never changed`() {
        val copy = stored.copy()
        EffectiveConfig.of(stored, EffectiveConfig.Layer { it.copy(language = "fr") }, EffectiveConfig.Layer { it.copy(language = "es") })
        assertEquals(copy, stored)
        assertEquals("de", stored.language)
    }
}

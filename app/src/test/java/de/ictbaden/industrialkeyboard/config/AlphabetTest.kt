package de.ictbaden.industrialkeyboard.config

import de.ictbaden.industrialkeyboard.layout.Layouts
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AlphabetTest {

    private fun forTag(tag: String) = Alphabet.forLocale(Locale.forLanguageTag(tag))

    @Test
    fun `system language picks the layout`() {
        assertEquals(Alphabet.QWERTZ, forTag("de-DE"))
        assertEquals(Alphabet.QWERTZ, forTag("de-AT"))
        assertEquals(Alphabet.QWERTZ, forTag("cs-CZ"))
        assertEquals(Alphabet.AZERTY, forTag("fr-FR"))
        assertEquals(Alphabet.AZERTY, forTag("fr-BE"))
        assertEquals(Alphabet.AZERTY, forTag("nl-BE"))
        assertEquals(Alphabet.QWERTY, forTag("nl-NL"))
        assertEquals(Alphabet.NORDIC, forTag("sv-SE"))
        assertEquals(Alphabet.NORDIC, forTag("nb-NO"))
        assertEquals(Alphabet.SPANISH, forTag("es-ES"))
        assertEquals(Alphabet.SPANISH, forTag("es-MX"))
        assertEquals(Alphabet.QWERTY, forTag("en-US"))
        assertEquals(Alphabet.QWERTY, forTag("zh-CN"))
        assertEquals(Alphabet.QWERTY, forTag("pl-PL"))
    }

    @Test
    fun `switzerland types qwertz, french canada qwerty`() {
        assertEquals(Alphabet.QWERTZ, forTag("fr-CH"))
        assertEquals(Alphabet.QWERTZ, forTag("it-CH"))
        assertEquals(Alphabet.QWERTY, forTag("fr-CA"))
        assertEquals(Alphabet.QWERTY, forTag("it-IT"))
    }

    @Test
    fun `only auto follows the locale`() {
        val german = Locale.GERMANY
        assertEquals(Alphabet.QWERTZ, Alphabet.AUTO.resolve(german))
        assertEquals(Alphabet.AZERTY, Alphabet.AZERTY.resolve(german))
        assertEquals(Alphabet.QWERTY, Alphabet.QWERTY.resolve(german))
    }

    @Test
    fun `auto is the default and survives the json round trip`() {
        assertEquals(Alphabet.AUTO, KeyboardConfig().layout.alphabet)
        val parsed = ConfigJson.parse(ConfigJson.write(KeyboardConfig()))
        assertEquals(Alphabet.AUTO, parsed.layout.alphabet)
        assertEquals(Alphabet.AUTO, ConfigJson.parse("""{"layout":{"alphabet":"auto"}}""").layout.alphabet)
    }

    @Test
    fun `auto shows the letters of the resolved layout`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val layout = Layouts.alpha(KeyboardConfig())
            assertEquals("QWERTZ", layout.name)
        } finally {
            Locale.setDefault(previous)
        }
    }
}

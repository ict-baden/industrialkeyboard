package de.ictbaden.industrialkeyboard.suggest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class SuggesterTest {

    private val qwertz = listOf("qwertzuiop", "asdfghjkl", "yxcvbnm")
    private val qwerty = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")

    private fun list(vararg words: Pair<String, Int>) = WordList.of(words.map { (w, f) -> Triple(w.lowercase(Locale.ROOT), w, f) })

    private val small = list(
        "Hallo" to 200, "Halle" to 150, "Halbleiterindustrie" to 120, "hallen" to 90, "Haus" to 210,
        "und" to 230, "Lager" to 180, "Palette" to 170, "Paletten" to 160,
    )

    private fun texts(s: Suggestions) = s.words.map { it.text }

    @Test
    fun `prefix completion by frequency, short words first`() {
        val s = Suggester(small, Locale.GERMAN, qwertz).suggest("hal")
        assertEquals("Hallo", s.words.first().text)
        assertTrue("Halbleiterindustrie" !in texts(s).take(2))
    }

    @Test
    fun `case pattern of the typed word`() {
        val sug = Suggester(small, Locale.GERMAN, qwertz)
        assertEquals("Hallo", sug.suggest("Hal").words.first().text)
        assertEquals("HALLO", sug.suggest("HAL").words.first().text)
        // Lower case keeps the dictionary spelling: German nouns stay capitalised.
        assertEquals("Lager", sug.suggest("lag").words.first().text)
    }

    @Test
    fun `typo on a neighbouring key is corrected`() {
        // "Lsger": s is next to a.
        val s = Suggester(small, Locale.GERMAN, qwertz).suggest("lsger")
        assertEquals("Lager", s.words.first().text)
        assertTrue(s.words.first().correction)
        assertEquals("Lager", s.autocorrection)
    }

    @Test
    fun `missing, extra and swapped letters`() {
        val sug = Suggester(small, Locale.GERMAN, qwertz)
        assertTrue("Palette" in texts(sug.suggest("palete")))
        assertTrue("Palette" in texts(sug.suggest("paeltte")))
        assertTrue("Haus" in texts(sug.suggest("hauss")))
    }

    @Test
    fun `a valid typed word is not autocorrected`() {
        val s = Suggester(small, Locale.GERMAN, qwertz).suggest("haus")
        assertTrue(s.typedIsWord)
        assertNull(s.autocorrection)
        assertEquals("Haus", s.words.first().text)
    }

    @Test
    fun `nothing for digits or symbols`() {
        val sug = Suggester(small, Locale.GERMAN, qwertz)
        assertEquals(Suggestions.NONE, sug.suggest("A1234"))
        assertEquals(Suggestions.NONE, sug.suggest(""))
    }

    @Test
    fun `neighbours follow the layout`() {
        val z = Suggester.neighbours(qwertz)
        val y = Suggester.neighbours(qwerty)
        assertTrue('t' in z.getValue('z') && 'u' in z.getValue('z'))
        assertTrue('t' in y.getValue('y') && 'u' in y.getValue('y'))
        assertTrue('s' in z.getValue('y'))
    }

    @Test
    fun `real German and English dictionaries, fast enough`() {
        val dir = File("src/main/assets/dict")
        val de = dir.resolve("de.txt.gz").inputStream().use { WordList.read(it, Locale.GERMAN) }
        val en = dir.resolve("en-US.txt.gz").inputStream().use { WordList.read(it, Locale.US) }
        assertTrue("sizes ${de.size} ${en.size}", de.size > 20000 && en.size > 20000)
        val german = Suggester(de, Locale.GERMAN, qwertz)
        val english = Suggester(en, Locale.US, qwerty)
        assertEquals("Hallo", german.suggest("Hallo").words.first().text)
        assertTrue(texts(german.suggest("Hal")).toString(), texts(german.suggest("Hal")).all { it.startsWith("Hal") })
        assertTrue(texts(german.suggest("Lagr")).toString(), "Lager" in texts(german.suggest("Lagr")))
        assertEquals("hello", english.suggest("hello").words.first().text)
        assertTrue(texts(english.suggest("hel")).toString(), "help" in texts(english.suggest("hel")))
        assertTrue(texts(english.suggest("warehiuse")).toString(), "warehouse" in texts(english.suggest("warehiuse")))
        // Warm up, then 200 lookups: well below 5 ms each even on a slow test machine.
        repeat(20) { german.suggest("Palett") }
        val started = System.nanoTime()
        repeat(200) { german.suggest(if (it % 2 == 0) "Lagerplatz" else "eingng") }
        val perLookupMs = (System.nanoTime() - started) / 200 / 1_000_000.0
        assertTrue("lookup took $perLookupMs ms", perLookupMs < 5.0)
    }
}

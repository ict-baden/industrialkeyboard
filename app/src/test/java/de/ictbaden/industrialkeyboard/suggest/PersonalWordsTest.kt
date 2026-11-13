package de.ictbaden.industrialkeyboard.suggest

import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigVersion
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.TypingSettings
import de.ictbaden.industrialkeyboard.layout.Macro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PersonalWordsTest {

    private val header = "# Gboard Dictionary version:1\n"

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `gboard lines with and without shortcut and locale`() {
        val r = PersonalWords.parseText(header + "Lagerplatz\t\tde-DE\nmfg-Wort\tmfg\t\nICT-Baden\t\t\nKommissionierung\tkom\tde_DE\n")
        assertEquals(0, r.skipped)
        assertEquals(
            listOf(
                PersonalWord("Lagerplatz", null, "de-DE"),
                PersonalWord("mfg-Wort", "mfg", null),
                PersonalWord("ICT-Baden", null, null),
                PersonalWord("Kommissionierung", "kom", "de-DE"),
            ),
            r.words,
        )
    }

    @Test
    fun `BOM, CRLF, comments and bad lines`() {
        val text = "﻿" + header.replace("\n", "\r\n") + "# a comment\r\n\r\nWort\t\tde\r\n\tleer\t\n" + "x".repeat(65) + "\t\t\nKurz\tmit leerzeichen\t\n"
        val r = PersonalWords.parseText(text)
        assertEquals(listOf(PersonalWord("Wort", null, "de")), r.words)
        assertEquals(3, r.skipped)
    }

    @Test
    fun `not a gboard export is rejected`() {
        try {
            PersonalWords.parseGboard("just some words\nwithout tabs".toByteArray())
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("Gboard"))
        }
        try {
            PersonalWords.parseGboard(ByteArray(0))
            fail()
        } catch (_: ImportException) {
        }
        // A plain list with tab-separated lines is fine without the header.
        assertEquals(1, PersonalWords.parseGboard("Wort\t\t\n".toByteArray()).words.size)
    }

    @Test
    fun `zip with dictionary_txt, also in a folder`() {
        val bytes = zip("readme.txt" to "x".toByteArray(), "export/dictionary.txt" to (header + "Palette\tpal\tde\n").toByteArray())
        assertEquals(listOf(PersonalWord("Palette", "pal", "de")), PersonalWords.parseGboard(bytes).words)
    }

    @Test
    fun `zip without dictionary, zip bomb, large file and too many entries`() {
        try {
            PersonalWords.parseGboard(zip("other.txt" to "a\tb\t\n".toByteArray()))
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("dictionary.txt"))
        }
        // Compresses to a few KB, unpacks beyond 1 MB: stops early.
        val bomb = zip("dictionary.txt" to ByteArray(PersonalWords.MAX_FILE_BYTES + 10) { 'a'.code.toByte() })
        assertTrue(bomb.size < PersonalWords.MAX_FILE_BYTES)
        try {
            PersonalWords.parseGboard(bomb)
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("1 MB"))
        }
        try {
            PersonalWords.parseGboard(ByteArray(PersonalWords.MAX_FILE_BYTES + 1))
            fail()
        } catch (_: ImportException) {
        }
        try {
            PersonalWords.parseGboard(zip(*Array(40) { "f$it.txt" to ByteArray(1) }))
            fail()
        } catch (e: ImportException) {
            assertTrue(e.message!!.contains("too many"))
        }
        // A path like ../dictionary.txt is only compared by its name, never written anywhere.
        assertEquals(1, PersonalWords.parseGboard(zip("../../dictionary.txt" to "Wort\t\t\n".toByteArray())).words.size)
    }

    @Test
    fun `at most 5000 words, the rest skipped`() {
        val text = header + (1..5003).joinToString("\n") { "wort$it\t\t" }
        val r = PersonalWords.parseText(text)
        assertEquals(PersonalWords.MAX_WORDS, r.words.size)
        assertEquals(3, r.skipped)
    }

    @Test
    fun `merge skips duplicates and keeps the order`() {
        val existing = listOf(PersonalWord("Lager", null, "de"), PersonalWord("Hallo"))
        val imported = listOf(PersonalWord("lager", "lg", "de"), PersonalWord("Lager", null, "en"), PersonalWord("HALLO"), PersonalWord("Neu"))
        val (merged, added, duplicates) = PersonalWords.merge(existing, imported)
        assertEquals(2, added)
        assertEquals(2, duplicates)
        // A duplicate brings its shortcut to an entry without one.
        assertEquals(listOf(PersonalWord("Lager", "lg", "de"), PersonalWord("Hallo"), PersonalWord("Lager", null, "en"), PersonalWord("Neu")), merged)
    }

    @Test
    fun `words for the language and ranking in the word list`() {
        val words = listOf(PersonalWord("Lagerplatz", locale = "de-DE"), PersonalWord("warehouse", locale = "en"), PersonalWord("ICT-Baden"), PersonalWord("zwei Wörter"))
        val de = PersonalWords.forLanguage(words, Locale.GERMANY)
        assertEquals(listOf("Lagerplatz", "ICT-Baden", "zwei Wörter"), de.map { it.word })
        val entries = PersonalWords.entries(de, Locale.GERMANY)
        // Phrases are no suggestion words.
        assertEquals(listOf("lagerplatz", "ict-baden"), entries.map { it.first })
        val base = WordList.of(listOf(Triple("lager", "Lager", 200), Triple("lagerplatz", "Lagerplatz", 90), Triple("lagern", "lagern", 150)))
        val merged = base.withExtra(entries)
        assertEquals(4, merged.size)
        // The personal word ranks first and keeps the higher frequency.
        val best = merged.completions("lagerp", 3).map { merged.word(it) }
        assertEquals(listOf("Lagerplatz"), best)
        assertEquals(PersonalWord.DEFAULT_FREQUENCY, merged.frequency(merged.indexOf("lagerplatz")))
        assertEquals(listOf("Lagerplatz", "Lager", "lagern"), merged.completions("lager", 3).map { merged.word(it) })
        assertTrue(merged.indexOf("ict-baden") >= 0)
        // Nothing to add: the same list.
        assertTrue(base.withExtra(emptyList()) === base)
    }

    @Test
    fun `a shortcut gives a snippet whose macro types the word`() {
        val words = listOf(PersonalWord("Mit {freundlichen} Grüßen", "mfg"))
        val s = PersonalWords.shortcutSnippet("MFG", words)!!
        assertEquals("Mit {freundlichen} Grüßen", s.label)
        assertEquals("Mit {U+007B}freundlichen{U+007D} Grüßen", s.text)
        // The macro parses and types the braces literally.
        Macro.parse(s.text)
        assertNull(PersonalWords.shortcutSnippet("m", words))
        assertNull(PersonalWords.shortcutSnippet("xyz", words))
    }

    @Test
    fun `locales are normalized`() {
        assertEquals("de-DE", PersonalWords.normalizeLocale("de_DE"))
        assertEquals("en", PersonalWords.normalizeLocale("en"))
        assertNull(PersonalWords.normalizeLocale("!!"))
    }

    @Test
    fun `json round trip and legacy configurations`() {
        val off = KeyboardConfig(typing = TypingSettings(autofillSuggestions = false, systemDictionary = false, personalDictionary = false))
        assertEquals(off, ConfigJson.parse(ConfigJson.write(off)))
        val on = ConfigJson.parse("""{"configVersion":3}""").typing
        assertTrue(on.autofillSuggestions && on.systemDictionary && on.personalDictionary)
        // Written for 2.1: the new features stay off unless switched on.
        val legacy = ConfigJson.parse("""{"typing":{"personalDictionary":true}}""").typing
        assertFalse(legacy.autofillSuggestions)
        assertFalse(legacy.systemDictionary)
        assertTrue(legacy.personalDictionary)
        assertFalse(ConfigVersion.LEGACY.typing.personalDictionary)
    }
}

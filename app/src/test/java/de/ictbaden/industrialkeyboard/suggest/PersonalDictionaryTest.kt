package de.ictbaden.industrialkeyboard.suggest

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PersonalDictionaryTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val file get() = File(context.filesDir, "personal-dictionary-test.json")

    @Before
    fun setUp() {
        file.delete()
    }

    @Test
    fun `add, edit, import and delete survive a reload`() {
        val d = PersonalDictionary(file)
        assertTrue(d.put(PersonalWord("Lagerplatz", "lp", "de")))
        assertFalse(d.put(PersonalWord("")))
        assertFalse(d.put(PersonalWord("Wort", "mit leer")))
        // Same word and language: replaced, not doubled.
        assertTrue(d.put(PersonalWord("lagerplatz", "lpl", "de")))
        assertEquals(listOf(PersonalWord("lagerplatz", "lpl", "de")), d.words)
        val (added, duplicates) = d.import(listOf(PersonalWord("LAGERPLATZ", locale = "de"), PersonalWord("Palette")))
        assertEquals(1, added)
        assertEquals(1, duplicates)
        val reloaded = PersonalDictionary(file)
        assertEquals(d.words.map { it.copy(frequency = PersonalWord.DEFAULT_FREQUENCY) }, reloaded.words)
        reloaded.remove(PersonalWord("Palette"))
        assertEquals(listOf("lagerplatz"), PersonalDictionary(file).words.map { it.word })
        reloaded.clear()
        assertTrue(PersonalDictionary(file).words.isEmpty())
    }

    @Test
    fun `a damaged file starts empty`() {
        file.writeText("{not json")
        assertTrue(PersonalDictionary(file).words.isEmpty())
    }
}

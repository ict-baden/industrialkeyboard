package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** The user's own snippets: kept apart from the configuration, in their own file. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UserSnippetsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var file: File

    @Before
    fun setUp() {
        file = File(temp.root, UserSnippets.FILE_NAME)
        UserSnippets.reset()
        ConfigStore.reset()
        File(context.filesDir, "config.json").delete()
        File(context.filesDir, UserSnippets.FILE_NAME).delete()
    }

    private fun open() = UserSnippets(file)

    @Test
    fun `add, replace and remove persist across a new instance`() {
        val first = open()
        val a = first.add("Prüfen", "Bitte prüfen{ENTER}", "pr")
        val b = first.add("  Lager  ", " A-01 ", null)
        assertEquals(listOf(a, b), open().items)
        assertEquals("Lager", b.label)
        assertEquals(" A-01 ", b.text)

        open().replace(a.copy(label = "Prüfen!", shortcut = "pf"))
        assertEquals(listOf(a.copy(label = "Prüfen!", shortcut = "pf"), b), open().items)

        open().remove(a.id)
        assertEquals(listOf(b), open().items)
    }

    @Test
    fun `ids are u1, u2 and then the next free one`() {
        val s = open()
        assertEquals("u1", s.add("A", "a", null).id)
        assertEquals("u2", s.add("B", "b", null).id)
        assertEquals("u3", s.add("C", "c", null).id)
        s.remove("u2")
        assertEquals("u2", s.add("D", "d", null).id)
        assertEquals("u4", s.add("E", "e", null).id)
    }

    @Test
    fun `adding beyond 50 throws`() {
        val s = open()
        repeat(Snippets.MAX_USER) { s.add("S$it", "t", null) }
        try {
            s.add("one more", "t", null)
            fail("accepted a 51st snippet")
        } catch (_: IllegalStateException) {
        }
        assertEquals(Snippets.MAX_USER, open().items.size)
    }

    @Test
    fun `what the editor refuses is refused here too`() {
        val s = open()
        s.add("A", "a", "mfg")
        listOf(
            { s.add("", "a", null) },
            { s.add("B", "", null) },
            { s.add("B", "b", "x1") },
            { s.add("B", "b", "MFG") },
            { s.add("B", "{SETTINGS}", null) },
        ).forEach { attempt ->
            try {
                attempt()
                fail("accepted")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertEquals(1, open().items.size)
    }

    @Test
    fun `a broken file gives an empty list, an error and an invalid copy`() {
        file.writeText("""{"version":1,"items":[{"id":"u1","label":"A","text":""}""")
        val s = open()
        assertTrue(s.items.isEmpty())
        assertEquals("invalid JSON", s.loadError)
        val invalid = File(temp.root, "${UserSnippets.FILE_NAME}.invalid")
        assertTrue(invalid.exists())
        assertTrue(invalid.readText().contains("\"u1\""))
        assertTrue(!file.exists())
        assertEquals("invalid JSON", s.takeLoadError())
        assertNull(s.takeLoadError())
        // Starts over cleanly.
        s.add("A", "a", null)
        assertEquals(1, open().items.size)
        assertNull(open().loadError)
    }

    @Test
    fun `an invalid entry counts as broken`() {
        file.writeText("""{"version":1,"items":[{"id":"u1","label":"A","text":"{SETTINGS}"}]}""")
        val s = open()
        assertTrue(s.items.isEmpty())
        assertEquals("user-snippets[0].text: own snippets may only contain text, {TAB} and {ENTER}", s.loadError)
    }

    @Test
    fun `the file holds only the snippets`() {
        open().add("Prüfen", "Bitte prüfen", "pr")
        val json = org.json.JSONObject(file.readText())
        assertEquals(setOf("version", "items"), json.keys().asSequence().toSet())
        assertEquals(1, json.getInt("version"))
    }

    @Test
    fun `listeners hear changes on the main thread`() {
        val s = open()
        var heard: List<Snippet>? = null
        s.addListener { heard = it }
        s.add("A", "a", null)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(s.items, heard)
    }

    @Test
    fun `a configuration import does not touch them`() {
        val own = UserSnippets.get(context)
        own.add("Prüfen", "Bitte prüfen", "pr")
        val store = ConfigStore.get(context)
        store.import(ConfigJson.parse("""{"snippets":{"allowUser":false,"items":[{"label":"Org","text":"o"}]}}"""))
        store.import(KeyboardConfig())
        // Nor does "Reset to defaults".
        de.ictbaden.industrialkeyboard.app.SettingsActivity.resetToDefaults(store, QuickToggleStore.get(context))
        UserSnippets.reset()
        assertEquals(listOf("Prüfen"), UserSnippets.get(context).items.map { it.label })
        assertTrue(File(context.filesDir, UserSnippets.FILE_NAME).exists())
        // Not part of the configuration or its export.
        assertTrue(!ConfigJson.write(store.config).contains("Prüfen"))
    }
}

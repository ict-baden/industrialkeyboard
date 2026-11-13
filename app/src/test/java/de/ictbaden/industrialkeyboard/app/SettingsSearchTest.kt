package de.ictbaden.industrialkeyboard.app

import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.divider.MaterialDivider
import de.ictbaden.industrialkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsSearchTest {

    private val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_IndustrialKeyboard)
    private val ui = Ui(context)

    /** Two sections like the settings screen: rows in cards with dividers between them. */
    private fun screen(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(ui.section("Rückmeldung"))
        addView(ui.card(listOf(
            ui.switchRow(R.drawable.ic_keyboard, "Haptik", "Kurzer Tick beim Tippen", true) {},
            ui.row(R.drawable.ic_keyboard, "Ton", "Klick beim Tippen") {},
        )))
        addView(ui.section("Layout"))
        addView(ui.card(listOf(
            ui.row(R.drawable.ic_keyboard, "Umlaut-Tasten", "ü, ö und ä als eigene Tasten") {},
            ui.row(R.drawable.ic_keyboard, "Zahlen-Taste", "?123 unten links") {},
            ui.row(R.drawable.ic_key, "ALPHA.P1", "keine · Buchstaben, unten links", onClick = null),
        )))
    }

    private fun visibleTitles(root: ViewGroup): List<String> = buildList {
        fun walk(v: View) {
            if (v.visibility != View.VISIBLE) return
            if (v is TextView) add(v.text.toString())
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(root)
    }

    @Test
    fun `normalize ignores case and accents`() {
        assertEquals("umlaut-tasten fur strasse", SettingsSearch.normalize("Umlaut-Tasten für Straße"))
        assertTrue(SettingsSearch.matches("Größe der Tasten", "grosse"))
        assertTrue(SettingsSearch.matches("Kontrast", "KONTRAST"))
    }

    @Test
    fun `synonyms find rows in other words and languages`() {
        assertTrue(SettingsSearch.matches("Haptik · Kurzer Tick", "vibration"))
        assertTrue(SettingsSearch.matches("Systemfarben (Material You)", "farbe"))
        assertTrue(SettingsSearch.matches("ALPHA.P1 · Buchstaben", "tab"))
        assertTrue(SettingsSearch.matches("Handschuh-Modus", "glove"))
        assertFalse(SettingsSearch.matches("Haptik", "xyz"))
        // Every word must match.
        assertFalse(SettingsSearch.matches("Haptik", "vibration xyz"))
    }

    @Test
    fun `a synonym match keeps its section header and hides the rest`() {
        val root = screen()
        val count = SettingsSearch.filter(root, "vibration")
        assertEquals(1, count)
        val titles = visibleTitles(root)
        assertTrue("Rückmeldung" in titles)
        assertTrue("Haptik" in titles)
        assertFalse("Ton" in titles)
        assertFalse("Layout" in titles)
        // No divider is left hanging next to a single visible row.
        val column = (root.getChildAt(1) as ViewGroup).getChildAt(0) as ViewGroup
        assertTrue((0 until column.childCount).map(column::getChildAt).filterIsInstance<MaterialDivider>().all { it.visibility == View.GONE })
    }

    @Test
    fun `umlaut and tab find their rows, nothing gives zero`() {
        val root = screen()
        assertEquals(1, SettingsSearch.filter(root, "Umlaut"))
        assertTrue("Umlaut-Tasten" in visibleTitles(root))
        assertEquals(1, SettingsSearch.filter(root, "tab"))
        assertTrue("ALPHA.P1" in visibleTitles(root))
        assertEquals(0, SettingsSearch.filter(root, "xyz"))
    }

    @Test
    fun `clearing the query shows every row again`() {
        val root = screen()
        val before = visibleTitles(root)
        SettingsSearch.filter(root, "vibration")
        assertEquals(5, SettingsSearch.filter(root, ""))
        assertEquals(before, visibleTitles(root))
    }

    @Test
    fun `the settings screen filters and shows no matches`() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.search("xyzqq")
                val empty = activity.findViewById<View>(android.R.id.content).findText(activity.getString(R.string.settings_search_empty))
                assertTrue(empty != null && empty.visibility == View.VISIBLE)
                activity.search("vibration")
                val haptic = activity.findViewById<View>(android.R.id.content).findText(activity.getString(R.string.vibration))
                assertTrue(haptic != null && haptic.isShownTo(activity.findViewById(android.R.id.content)))
                activity.search("")
                assertTrue(empty!!.visibility == View.GONE)
            }
        }
    }

    private fun View.findText(text: String): TextView? {
        if (this is TextView && this.text.toString() == text) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findText(text)?.let { return it }
        return null
    }

    private fun View.isShownTo(root: View): Boolean {
        var v: View? = this
        while (v != null && v !== root) {
            if (v.visibility != View.VISIBLE) return false
            v = v.parent as? View
        }
        return true
    }
}

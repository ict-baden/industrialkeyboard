package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeProvider
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.Layouts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

/** Screen-reader nodes of the keys, lift-to-type and the other canvas views. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "de")
class KeyboardAccessibilityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val typed = mutableListOf<Pair<Key, String?>>()
    private val longPresses = mutableListOf<Key>()
    private val deletes = mutableListOf<Pair<Int, Boolean>>()

    private val listener = object : KeyboardView.Listener {
        override fun onKeyDown(key: Key) = Unit
        override fun onKey(key: Key, alternative: String?) {
            typed += key to alternative
        }
        override fun onCursorMove(steps: Int) = Unit
        override fun onKeyLongPress(key: Key): Boolean {
            longPresses += key
            return true
        }
        override fun onDeleteSwipe(words: Int, done: Boolean) {
            deletes += words to done
        }
    }

    @Before
    fun setUp() {
        Alphabet.subtypeLocale = Locale.GERMANY
    }

    @After
    fun tearDown() {
        Alphabet.subtypeLocale = null
    }

    private fun keyboard(): KeyboardView = KeyboardView(context).apply {
        listener = this@KeyboardAccessibilityTest.listener
        layout = Layouts.alpha(KeyboardConfig())
        (this as View).layout(0, 0, 1080, 800)
    }

    /** The framework provider, as TalkBack sees it (the compat wrapper of a framework provider does not delegate). */
    private fun provider(view: View): AccessibilityNodeProvider = view.accessibilityNodeProvider!!

    private fun node(view: View, id: Int): AccessibilityNodeInfoCompat = AccessibilityNodeInfoCompat.wrap(provider(view).createAccessibilityNodeInfo(id)!!)

    /** Virtual id of the first key labelled [label]: the nodes are numbered in row order. */
    private fun idOf(view: KeyboardView, label: String): Int = (0 until view.a11yKeyCount()).first { view.a11yKey(it)?.label == label }

    private fun hover(view: View, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, x, y, 0)
        // A hover event from the touch screen, as touch exploration sends it.
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        view.dispatchGenericMotionEvent(event)
        event.recycle()
    }

    @Suppress("DEPRECATION")
    private fun bounds(node: AccessibilityNodeInfoCompat) = Rect().also { node.getBoundsInParent(it) }

    @Test
    fun `every key is a keyboard key node with a name and bounds`() {
        val view = keyboard()
        val expected = view.layout!!.rows.sumOf { row -> row.keys.count { !it.isSpacer } }
        assertEquals(expected, view.a11yKeyCount())
        for (id in 0 until expected) {
            val node = node(view, id)
            assertEquals(KeyboardAccessibility.KEY_CLASS, node.className)
            assertEquals("android.inputmethodservice.Keyboard\$Key", node.className)
            assertTrue("key $id has no name", !node.contentDescription.isNullOrEmpty())
            assertFalse("key $id has no bounds", bounds(node).isEmpty)
        }
        assertEquals("Leertaste, Deutsch", node(view, (0 until expected).first { view.a11yKey(it)?.output == KeyOutput.Space }).contentDescription)
    }

    @Test
    fun `a double tap types the key once`() {
        val view = keyboard()
        val q = idOf(view, "q")
        assertTrue(provider(view).performAction(q, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals(listOf("q"), typed.map { it.first.label })
    }

    @Test
    fun `lifting the finger types the explored key once, a click right after is not typed again`() {
        val view = keyboard().apply {
            touchExploration = true
            liftToType = true
        }
        val center = view.keyCenter("q")!!
        hover(view, MotionEvent.ACTION_HOVER_ENTER, center.x, center.y)
        hover(view, MotionEvent.ACTION_HOVER_EXIT, center.x, center.y)
        assertEquals(listOf("q" to null), typed.map { it.first.label to it.second })
        assertTrue(provider(view).performAction(idOf(view, "q"), AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals(1, typed.size)
    }

    @Test
    fun `a lift right after a click on the same key is not typed again`() {
        val view = keyboard().apply { touchExploration = true }
        val center = view.keyCenter("q")!!
        provider(view).performAction(idOf(view, "q"), AccessibilityNodeInfoCompat.ACTION_CLICK, null)
        hover(view, MotionEvent.ACTION_HOVER_ENTER, center.x, center.y)
        hover(view, MotionEvent.ACTION_HOVER_EXIT, center.x, center.y)
        assertEquals(1, typed.size)
    }

    @Test
    fun `leaving the keyboard types nothing`() {
        val view = keyboard().apply { touchExploration = true }
        val center = view.keyCenter("q")!!
        hover(view, MotionEvent.ACTION_HOVER_ENTER, center.x, center.y)
        hover(view, MotionEvent.ACTION_HOVER_MOVE, center.x, 10f)
        hover(view, MotionEvent.ACTION_HOVER_EXIT, center.x, -40f)
        assertTrue(typed.isEmpty())
    }

    @Test
    fun `without lift to type, or without TalkBack, lifting types nothing`() {
        val off = keyboard().apply {
            touchExploration = true
            liftToType = false
        }
        val center = off.keyCenter("q")!!
        hover(off, MotionEvent.ACTION_HOVER_ENTER, center.x, center.y)
        hover(off, MotionEvent.ACTION_HOVER_EXIT, center.x, center.y)
        val noTalkBack = keyboard()
        hover(noTalkBack, MotionEvent.ACTION_HOVER_ENTER, center.x, center.y)
        hover(noTalkBack, MotionEvent.ACTION_HOVER_EXIT, center.x, center.y)
        assertTrue(typed.isEmpty())
    }

    @Test
    fun `alternatives are custom actions`() {
        val view = keyboard()
        val a = idOf(view, "a")
        val actions = node(view, a).actionList.map { it.id }
        assertTrue(R.id.a11y_alt_0 in actions)
        assertEquals("ä", node(view, a).actionList.first { it.id == R.id.a11y_alt_0 }.label)
        assertTrue(provider(view).performAction(a, R.id.a11y_alt_0, null))
        assertEquals(listOf("a" to "ä"), typed.map { it.first.label to it.second })
        // q has no long press of its own; the alternatives are its only extra actions.
        assertFalse(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK in node(view, idOf(view, "q")).actionList.map { it.id })
    }

    @Test
    fun `long press on backspace deletes a word, on shift it locks`() {
        val view = keyboard()
        val backspace = (0 until view.a11yKeyCount()).first { view.a11yKey(it)?.output == KeyOutput.Backspace }
        assertTrue(provider(view).performAction(backspace, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null))
        assertEquals(listOf(1 to false, 1 to true), deletes)
        val shift = (0 until view.a11yKeyCount()).first { view.a11yKey(it)?.output == KeyOutput.Shift }
        assertTrue(provider(view).performAction(shift, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null))
        assertEquals(KeyOutput.Shift, longPresses.single().output)
    }

    @Test
    fun `password keys are obscured on the nodes`() {
        val view = keyboard().apply { obscureKeys = true }
        assertEquals("Taste", node(view, idOf(view, "q")).contentDescription)
        assertEquals("Zeichen 1", node(view, idOf(view, "a")).actionList.first { it.id == R.id.a11y_alt_0 }.label)
    }

    @Test
    fun `toolbar tabs are named and the selected one is selected`() {
        val bar = TabBarView(context)
        bar.setTabs(KeyboardRenderer.toolbarTabs(KeyboardConfig(), scanAvailable = false), Tabs.TOOL_CLIPBOARD, animate = false)
        bar.layout(0, 0, 1080, 138)
        assertEquals("Weitere Funktionen", node(bar, 0).contentDescription)
        assertEquals("Zwischenablage", node(bar, 1).contentDescription)
        assertEquals("Einstellungen", node(bar, 2).contentDescription)
        assertFalse(node(bar, 0).isSelected)
        assertTrue(node(bar, 1).isSelected)
        assertEquals("Tastatur ausblenden", node(bar, KeyDescriber.HIDE).contentDescription)
        var opened: String? = null
        bar.onTab = { opened = it }
        assertTrue(provider(bar).performAction(0, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals(Tabs.TOOL_MENU, opened)
    }

    @Test
    fun `the snippet in the suggestion strip is a node of its own`() {
        val strip = SuggestionStripView(context)
        strip.words = listOf("Mit", "Mittag")
        strip.snippet = "Mit freundlichen Grüßen"
        strip.layout(0, 0, 1080, 138)
        assertEquals("Mit", node(strip, 0).contentDescription)
        assertEquals("Textbaustein: Mit freundlichen Grüßen", node(strip, 1).contentDescription)
        var picked = false
        strip.onPickSnippet = { picked = true }
        assertTrue(provider(strip).performAction(1, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertTrue(picked)
        var word: String? = null
        strip.onPick = { word = it }
        assertTrue(provider(strip).performAction(2, AccessibilityNodeInfoCompat.ACTION_CLICK, null))
        assertEquals("Mittag", word)
    }

    @Test
    fun `the dictation microphone says what a tap does`() {
        val voice = VoicePanelView(context)
        voice.layout(0, 0, 1080, 800)
        voice.listening = true
        assertEquals("Diktat anhalten", node(voice, 0).contentDescription)
        voice.listening = false
        assertEquals("Diktat fortsetzen", node(voice, 0).contentDescription)
        assertEquals("Löschen", node(voice, 2).contentDescription)
    }

    @Test
    fun `the scan pad describes itself and scans on a double tap`() {
        val pad = ScanPadView(context)
        var presses = 0
        var released: Long? = null
        pad.listener = object : ScanPadView.Listener {
            override fun onScanPress() {
                presses++
            }
            override fun onScanRelease(heldMs: Long) {
                released = heldMs
            }
        }
        assertTrue(pad.contentDescription.contains("Kein Scanner"))
        pad.scannerName = "DataWedge"
        assertTrue(pad.contentDescription.contains("Zum Scannen tippen"))
        assertTrue(pad.contentDescription.contains("DataWedge"))
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, pad.accessibilityLiveRegion)
        assertTrue(pad.performClick())
        assertEquals(1, presses)
        assertNotNull(released)
        assertEquals(0L, released)
    }
}

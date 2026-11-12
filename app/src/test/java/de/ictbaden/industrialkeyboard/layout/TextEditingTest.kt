package de.ictbaden.industrialkeyboard.layout

import android.text.InputType
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rules of the text editing panel: keys per field type, the focus at the edges, enabled keys. */
class TextEditingTest {

    private val text = InputType.TYPE_CLASS_TEXT
    private val multiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    private val imeMultiLine = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE
    private val terminal = InputType.TYPE_NULL
    private val number = InputType.TYPE_CLASS_NUMBER

    @Test
    fun `up and down are home and end in single-line fields`() {
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, TextEditing.keyCode(EditAction.UP, text))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TextEditing.keyCode(EditAction.UP, multiLine))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TextEditing.keyCode(EditAction.UP, imeMultiLine))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TextEditing.keyCode(EditAction.UP, terminal))
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, TextEditing.keyCode(EditAction.UP, number))
        assertEquals(KeyEvent.KEYCODE_MOVE_END, TextEditing.keyCode(EditAction.DOWN, text))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, TextEditing.keyCode(EditAction.DOWN, multiLine))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, TextEditing.keyCode(EditAction.DOWN, terminal))
        assertEquals(KeyEvent.KEYCODE_MOVE_END, TextEditing.keyCode(EditAction.DOWN, number))
        assertEquals(KeyEvent.KEYCODE_MOVE_END, TextEditing.keyCode(EditAction.DOWN, InputType.TYPE_CLASS_PHONE))
    }

    @Test
    fun `other movement keys`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, TextEditing.keyCode(EditAction.LEFT, text))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, TextEditing.keyCode(EditAction.RIGHT, terminal))
        assertEquals(KeyEvent.KEYCODE_MOVE_HOME, TextEditing.keyCode(EditAction.HOME, multiLine))
        assertEquals(KeyEvent.KEYCODE_MOVE_END, TextEditing.keyCode(EditAction.END, multiLine))
        assertEquals(KeyEvent.KEYCODE_FORWARD_DEL, TextEditing.keyCode(EditAction.DELETE, text))
        assertNull(TextEditing.keyCode(EditAction.COPY, text))
        assertNull(TextEditing.keyCode(EditAction.SELECT, text))
    }

    @Test
    fun `single line and editor`() {
        assertTrue(TextEditing.singleLine(text))
        assertFalse(TextEditing.singleLine(multiLine))
        assertFalse(TextEditing.singleLine(terminal))
        assertTrue(TextEditing.singleLine(InputType.TYPE_CLASS_DATETIME))
        assertTrue(TextEditing.editor(text))
        assertTrue(TextEditing.editor(number))
        assertFalse(TextEditing.editor(terminal))
    }

    @Test
    fun `left at the start and right at the end keep the focus in the field`() {
        assertTrue(TextEditing.blockedAtEdge(EditAction.LEFT, text, hasSelection = false, before = "", after = "x"))
        assertFalse(TextEditing.blockedAtEdge(EditAction.LEFT, text, hasSelection = true, before = "", after = "x"))
        assertFalse("WebView busy: unknown", TextEditing.blockedAtEdge(EditAction.LEFT, text, hasSelection = false, before = null, after = null))
        assertFalse("terminal: the keys belong to it", TextEditing.blockedAtEdge(EditAction.LEFT, terminal, hasSelection = false, before = "", after = ""))
        assertFalse(TextEditing.blockedAtEdge(EditAction.LEFT, text, hasSelection = false, before = "a", after = ""))
        assertTrue(TextEditing.blockedAtEdge(EditAction.RIGHT, text, hasSelection = false, before = "a", after = ""))
        assertFalse(TextEditing.blockedAtEdge(EditAction.RIGHT, text, hasSelection = false, before = "", after = "b"))
        assertFalse(TextEditing.blockedAtEdge(EditAction.HOME, text, hasSelection = false, before = "", after = ""))
        assertFalse(TextEditing.blockedAtEdge(EditAction.END, text, hasSelection = false, before = "", after = ""))
    }

    @Test
    fun `cut and copy need a selection, never in passwords or terminals`() {
        val plain = TextEditState(selecting = false, hasSelection = true, password = false, editor = true)
        assertTrue(plain.enabled(EditAction.CUT))
        assertTrue(plain.enabled(EditAction.COPY))
        val noSelection = plain.copy(hasSelection = false)
        assertFalse(noSelection.enabled(EditAction.CUT))
        assertFalse(noSelection.enabled(EditAction.COPY))
        val password = plain.copy(password = true)
        assertFalse(password.enabled(EditAction.CUT))
        assertFalse(password.enabled(EditAction.COPY))
        assertTrue(password.enabled(EditAction.SELECT_ALL))
        val terminal = plain.copy(editor = false)
        assertFalse(terminal.enabled(EditAction.CUT))
        assertFalse(terminal.enabled(EditAction.COPY))
        assertFalse(terminal.enabled(EditAction.SELECT_ALL))
        assertFalse(terminal.enabled(EditAction.SELECT))
        for (state in listOf(plain, noSelection, password, terminal)) {
            assertTrue(state.enabled(EditAction.PASTE))
            assertTrue(state.enabled(EditAction.LEFT))
            assertTrue(state.enabled(EditAction.DELETE))
            assertTrue(state.enabled(EditAction.CLOSE))
        }
    }

    @Test
    fun `select mode moves the end that is not the anchor`() {
        // Cursor at 5, select mode on: → selects one character, ← goes back.
        assertEquals(5 to 6, TextEditing.extend(anchor = 5, selStart = 5, selEnd = 5, delta = 1))
        assertEquals(5 to 7, TextEditing.extend(anchor = 5, selStart = 5, selEnd = 6, delta = 1))
        assertEquals(5 to 5, TextEditing.extend(anchor = 5, selStart = 5, selEnd = 6, delta = -1))
        // Left of the anchor: the field may report the selection reversed or ordered.
        assertEquals(5 to 3, TextEditing.extend(anchor = 5, selStart = 5, selEnd = 4, delta = -1))
        assertEquals(5 to 3, TextEditing.extend(anchor = 5, selStart = 4, selEnd = 5, delta = -1))
        // Nothing before the start of the text.
        assertNull(TextEditing.extend(anchor = 0, selStart = 0, selEnd = 0, delta = -1))
        // The app moved the selection: its start becomes the anchor.
        assertEquals(10 to 13, TextEditing.extend(anchor = 5, selStart = 10, selEnd = 12, delta = 1))
        // Unknown positions.
        assertNull(TextEditing.extend(anchor = -1, selStart = -1, selEnd = -1, delta = -1))
        assertNull(TextEditing.extend(anchor = -1, selStart = -1, selEnd = -1, delta = 1))
    }

    @Test
    fun `paste types the clip itself only in terminals`() {
        // Compose pastes via KEYCODE_PASTE and still answers false: no second copy in editors.
        assertFalse(TextEditing.pasteFallback(text))
        assertFalse(TextEditing.pasteFallback(multiLine))
        assertFalse(TextEditing.pasteFallback(number))
        assertFalse(TextEditing.pasteFallback(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertTrue(TextEditing.pasteFallback(terminal))
    }
}

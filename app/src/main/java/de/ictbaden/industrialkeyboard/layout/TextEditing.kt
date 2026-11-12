package de.ictbaden.industrialkeyboard.layout

import android.text.InputType
import android.view.KeyEvent

/** What a key of the text editing panel does. */
enum class EditAction { LEFT, RIGHT, UP, DOWN, HOME, END, SELECT, SELECT_ALL, CUT, COPY, PASTE, DELETE, CLOSE }

/**
 * What the text editing panel shows: Select lit while [selecting], Cut and Copy dimmed without a
 * selection. Positions and field flags only – never text.
 */
data class TextEditState(val selecting: Boolean, val hasSelection: Boolean, val password: Boolean, val editor: Boolean) {
    fun enabled(action: EditAction): Boolean = when (action) {
        EditAction.CUT, EditAction.COPY -> editor && hasSelection && !password
        EditAction.SELECT, EditAction.SELECT_ALL -> editor
        else -> true
    }
}

/** Rules of the text editing panel, free of Android services so they can be unit-tested. */
object TextEditing {

    /** A real text editor; [InputType.TYPE_NULL] = terminal or remote desktop (key events only). */
    fun editor(inputType: Int): Boolean = (inputType and InputType.TYPE_MASK_CLASS) != InputType.TYPE_NULL

    /**
     * Paste types the clip itself when the field has no paste action – only in
     * [InputType.TYPE_NULL] fields. A text editor may answer `false` and paste anyway (Compose
     * sends KEYCODE_PASTE and returns false), so the clip would land twice.
     */
    fun pasteFallback(inputType: Int): Boolean = !editor(inputType)

    /**
     * Single-line field: text without the multi-line flags, or a number, phone or date field. Never
     * [InputType.TYPE_NULL], where ↑/↓ belong to the terminal.
     */
    fun singleLine(inputType: Int): Boolean = when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT -> (inputType and (InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE)) == 0
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE, InputType.TYPE_CLASS_DATETIME -> true
        else -> false
    }

    /** The key for a movement; in single-line fields ↑/↓ are Home/End, so the focus does not jump to the next field. */
    fun keyCode(action: EditAction, inputType: Int): Int? = when (action) {
        EditAction.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
        EditAction.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        EditAction.UP -> if (singleLine(inputType)) KeyEvent.KEYCODE_MOVE_HOME else KeyEvent.KEYCODE_DPAD_UP
        EditAction.DOWN -> if (singleLine(inputType)) KeyEvent.KEYCODE_MOVE_END else KeyEvent.KEYCODE_DPAD_DOWN
        EditAction.HOME -> KeyEvent.KEYCODE_MOVE_HOME
        EditAction.END -> KeyEvent.KEYCODE_MOVE_END
        EditAction.DELETE -> KeyEvent.KEYCODE_FORWARD_DEL
        else -> null
    }

    /**
     * ← at the start or → at the end without a selection: TextView does not handle the key and
     * Android moves the focus to a neighbouring view (the panel would close). Only when the field
     * *says* there is nothing there (""), not when it answers `null` (WebView busy), and never in
     * [InputType.TYPE_NULL] fields, where the keys belong to the terminal.
     */
    fun blockedAtEdge(action: EditAction, inputType: Int, hasSelection: Boolean, before: CharSequence?, after: CharSequence?): Boolean {
        if (hasSelection || !editor(inputType)) return false
        return when (action) {
            EditAction.LEFT -> before != null && before.isEmpty()
            EditAction.RIGHT -> after != null && after.isEmpty()
            else -> false
        }
    }

    /**
     * Select mode, ← and →: the new selection (anchor, focus) one character further, set directly
     * instead of a Shift chord – that works the same in TextView, WebView and Compose. The end that
     * is not [anchor] moves; when neither end is the anchor any more (the app moved the selection),
     * the selection start becomes the anchor. `null` = nothing to do (start of the text).
     */
    fun extend(anchor: Int, selStart: Int, selEnd: Int, delta: Int): Pair<Int, Int>? {
        val (from, focus) = when (anchor) {
            selStart -> selStart to selEnd
            selEnd -> selEnd to selStart
            else -> selStart to selEnd
        }
        val target = focus + delta
        if (target < 0 || from < 0) return null
        return from to target
    }
}

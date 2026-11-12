package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent

/** The PC modifier keys. Win is Android's Meta key, which remote desktop apps send as the Windows key. */
enum class ModifierKey(val label: String, val keyCode: Int, val meta: Int) {
    CTRL("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
    WIN("Win", KeyEvent.KEYCODE_META_LEFT, KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
    ALT("Alt", KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
    ;

    companion object {
        fun byKeyCode(code: Int): ModifierKey? = entries.firstOrNull { it.keyCode == code }

        /** The modifiers whose bits are set in [meta], in PC order (Ctrl, Win, Alt). */
        fun of(meta: Int): List<ModifierKey> = entries.filter { meta and it.meta != 0 }
    }
}

/** One key event of a chord. */
data class ChordEvent(val down: Boolean, val keyCode: Int, val meta: Int)

/**
 * Key combinations as a physical keyboard sends them: Ctrl, Win and Alt go down first, then the key
 * with the full meta state, then the modifiers come up in reverse. Remote desktop clients watch the
 * modifier keys themselves, a meta state alone is not enough for them.
 */
object KeyChords {

    private const val PUNCTUATION = "-=[]\\;',./`"
    private val PUNCTUATION_CODES = intArrayOf(
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_LEFT_BRACKET, KeyEvent.KEYCODE_RIGHT_BRACKET,
        KeyEvent.KEYCODE_BACKSLASH, KeyEvent.KEYCODE_SEMICOLON, KeyEvent.KEYCODE_APOSTROPHE, KeyEvent.KEYCODE_COMMA,
        KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_GRAVE,
    )

    /**
     * The key that types [c] on a US keyboard (A–Z with Shift), or `null` when there is none (ä, €
     * …). Used to send Ctrl+C and the like as a key press instead of text.
     */
    fun keyFor(c: Char): MacroStep.Key? = when (c) {
        in 'a'..'z' -> MacroStep.Key(KeyEvent.KEYCODE_A + (c - 'a'))
        in 'A'..'Z' -> MacroStep.Key(KeyEvent.KEYCODE_A + (c - 'A'), KeyEvent.META_SHIFT_ON)
        in '0'..'9' -> MacroStep.Key(KeyEvent.KEYCODE_0 + (c - '0'))
        ' ' -> MacroStep.Key(KeyEvent.KEYCODE_SPACE)
        else -> PUNCTUATION.indexOf(c).takeIf { it >= 0 }?.let { MacroStep.Key(PUNCTUATION_CODES[it]) }
    }

    /** Meta state of the left Shift key held down. */
    const val SHIFT_LEFT = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON

    fun meta(modifiers: Collection<ModifierKey>): Int = modifiers.fold(0) { acc, m -> acc or m.meta }

    /**
     * Meta state the keyboard adds to every key of a macro: the armed or locked Ctrl, Win and Alt
     * and a Shift tapped by hand ([manualShift]). None for a [snippet]: its `{TAB}` and `{ENTER}`
     * stay plain keys (no Shift+Tab or Ctrl+Enter), and chords only come from its own `{CTRL+…}`.
     */
    fun heldMeta(modifiers: Collection<ModifierKey>, manualShift: Boolean, snippet: Boolean): Int =
        if (snippet) 0 else meta(modifiers) or (if (manualShift) KeyEvent.META_SHIFT_ON else 0)

    /**
     * Events for [keyCode] pressed with [meta]; Ctrl, Win and Alt in [meta] are pressed around it.
     * [pressShift]: Shift is pressed as a key too (inside Ctrl, Win and Alt), not only sent as meta
     * state – TextView extends a selection only when the Shift key itself went down. Otherwise Shift
     * stays a meta state, as F13–F24 and remote desktop apps expect.
     */
    fun events(keyCode: Int, meta: Int, pressShift: Boolean = false): List<ChordEvent> {
        val held = ModifierKey.of(meta).filter { it.keyCode != keyCode }
        val shiftKey = pressShift && keyCode != KeyEvent.KEYCODE_SHIFT_LEFT
        val events = mutableListOf<ChordEvent>()
        val pressed = held.fold(0) { acc, m -> acc or m.meta } or if (shiftKey) KeyEvent.META_SHIFT_MASK else 0
        var state = meta and pressed.inv()
        for (m in held) {
            state = state or m.meta
            events += ChordEvent(true, m.keyCode, state)
        }
        if (shiftKey) {
            state = state or SHIFT_LEFT
            events += ChordEvent(true, KeyEvent.KEYCODE_SHIFT_LEFT, state)
        }
        events += ChordEvent(true, keyCode, meta)
        events += ChordEvent(false, keyCode, meta)
        if (shiftKey) {
            state = state and KeyEvent.META_SHIFT_MASK.inv()
            events += ChordEvent(false, KeyEvent.KEYCODE_SHIFT_LEFT, state)
        }
        for (m in held.asReversed()) {
            state = state and m.meta.inv()
            events += ChordEvent(false, m.keyCode, state)
        }
        return events
    }
}

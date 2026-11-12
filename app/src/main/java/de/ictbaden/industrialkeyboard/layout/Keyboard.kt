package de.ictbaden.industrialkeyboard.layout

import de.ictbaden.industrialkeyboard.config.KeyStyle

enum class KeyIcon { SHIFT, BACKSPACE, ENTER, TAB, SPACE, SCAN, HIDE, LEFT, RIGHT, UP, DOWN, SETTINGS, GLOBE }

/** What a key does when it is released. */
sealed interface KeyOutput {
    /** Commits text; letter keys follow the shift state. */
    data class Text(val text: String) : KeyOutput

    data class Macro(val steps: List<MacroStep>) : KeyOutput

    data class ShowTab(val tab: String) : KeyOutput

    data object Shift : KeyOutput
    data object Backspace : KeyOutput
    data object Enter : KeyOutput
    data object Space : KeyOutput

    /** Ctrl, Alt or Win: applies to the next key; tap again to press it alone, hold to lock. */
    data class Modifier(val modifier: ModifierKey) : KeyOutput

    /** Next keyboard language (tap) or the list of keyboards (long press), like 🌐 in Gboard. */
    data object Language : KeyOutput

    /** Toggles between the two symbol pages. */
    data object SymbolsPage : KeyOutput

    /** Empty space (indentation). */
    data object None : KeyOutput
}

data class Key(
    val label: String? = null,
    val icon: KeyIcon? = null,
    val output: KeyOutput,
    /** Relative width; a standard letter key is 1. */
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
    /** Small corner label: the first long-press alternative. */
    val hint: String? = null,
    /** Long-press alternatives (text). */
    val popup: List<String> = emptyList(),
    /** Grows to fill spare row width (shift, backspace, space bar). */
    val flex: Boolean = false,
    /** Repeats while held (backspace, arrows). */
    val repeat: Boolean = false,
    /** Letter key: label and output follow shift. */
    val letter: Boolean = false,
) {
    val isSpacer: Boolean get() = output == KeyOutput.None

    companion object {
        fun spacer(width: Float) = Key(output = KeyOutput.None, width = width)
    }
}

data class KeyRow(val keys: List<Key>) {
    val units: Float get() = keys.sumOf { it.width.toDouble() }.toFloat()
}

/** A page of keys; [name] is shown on the space bar. */
data class KeyboardLayout(val id: String, val rows: List<KeyRow>, val name: String = "") {
    /** Width of the widest row in key units; narrower rows grow their flex keys or are centered. */
    val units: Float get() = rows.maxOfOrNull { it.units } ?: 1f
}

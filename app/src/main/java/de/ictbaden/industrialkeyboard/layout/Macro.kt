package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.config.QuickToggle

/** One step of a key macro. */
sealed interface MacroStep {
    data class Text(val text: String) : MacroStep
    /** A key press; [meta] holds modifiers such as `KeyEvent.META_SHIFT_ON`. */
    data class Key(val keyCode: Int, val meta: Int = 0) : MacroStep
    data class Command(val command: KeyCommand) : MacroStep
    data class ShowTab(val tab: String) : MacroStep
    /** `{TOGGLE:glove}`: switches a quick toggle, as far as `quickToggles.allowed` lets users. */
    data class Toggle(val toggle: QuickToggle) : MacroStep
}

enum class KeyCommand { SCAN, HIDE, SETTINGS, NEXT_KEYBOARD, TEXT_EDIT }

/**
 * Key macros: plain text plus tokens in braces, case-insensitive, e.g. `OK{ENTER}`, `{F1}`,
 * `{U+00E4}`, `{SCAN}`, `{EDIT}`, `{SHOW:numeric}`, `{TOGGLE:glove}`. Unknown tokens are rejected so typos surface on import.
 *
 * Key combinations join modifiers and one key with `+`: `{CTRL+C}`, `{ALT+F4}`, `{WIN+R}`,
 * `{CTRL+ALT+END}`. `{CTRL}`, `{ALT}` and `{WIN}` alone press that key; a key whose whole macro is
 * one of them becomes a sticky modifier key.
 */
object Macro {

    private val KEYS: Map<String, Int> = buildMap {
        put("TAB", KeyEvent.KEYCODE_TAB)
        put("ENTER", KeyEvent.KEYCODE_ENTER)
        put("ESC", KeyEvent.KEYCODE_ESCAPE)
        put("BS", KeyEvent.KEYCODE_DEL)
        put("BACKSPACE", KeyEvent.KEYCODE_DEL)
        put("DEL", KeyEvent.KEYCODE_FORWARD_DEL)
        put("DELETE", KeyEvent.KEYCODE_FORWARD_DEL)
        put("INS", KeyEvent.KEYCODE_INSERT)
        put("INSERT", KeyEvent.KEYCODE_INSERT)
        put("HOME", KeyEvent.KEYCODE_MOVE_HOME)
        put("END", KeyEvent.KEYCODE_MOVE_END)
        put("PGUP", KeyEvent.KEYCODE_PAGE_UP)
        put("PAGE-UP", KeyEvent.KEYCODE_PAGE_UP)
        put("PGDN", KeyEvent.KEYCODE_PAGE_DOWN)
        put("PAGE-DOWN", KeyEvent.KEYCODE_PAGE_DOWN)
        put("UP", KeyEvent.KEYCODE_DPAD_UP)
        put("DOWN", KeyEvent.KEYCODE_DPAD_DOWN)
        put("LEFT", KeyEvent.KEYCODE_DPAD_LEFT)
        put("RIGHT", KeyEvent.KEYCODE_DPAD_RIGHT)
        for (n in 1..12) put("F$n", KeyEvent.KEYCODE_F1 + n - 1)
        put("CTRL", KeyEvent.KEYCODE_CTRL_LEFT)
        put("CONTROL", KeyEvent.KEYCODE_CTRL_LEFT)
        put("ALT", KeyEvent.KEYCODE_ALT_LEFT)
        put("WIN", KeyEvent.KEYCODE_META_LEFT)
        put("META", KeyEvent.KEYCODE_META_LEFT)
        put("PRINT", KeyEvent.KEYCODE_SYSRQ)
        put("PAUSE", KeyEvent.KEYCODE_BREAK)
        put("MENU", KeyEvent.KEYCODE_MENU)
    }

    /** Modifiers allowed before `+` in a key combination. */
    private val COMBO_MODIFIERS: Map<String, Int> = mapOf(
        "CTRL" to ModifierKey.CTRL.meta,
        "CONTROL" to ModifierKey.CTRL.meta,
        "ALT" to ModifierKey.ALT.meta,
        "WIN" to ModifierKey.WIN.meta,
        "META" to ModifierKey.WIN.meta,
        "SHIFT" to KeyEvent.META_SHIFT_ON,
    )

    /** F13–F24 are Shift + F1–F12 (Android has no higher key codes; 5250 emulators map them this way). */
    private val SHIFTED_F_KEYS: Map<String, Int> = (13..24).associate { "F$it" to KeyEvent.KEYCODE_F1 + it - 13 }

    private val TEXT: Map<String, String> = mapOf(
        "SPACE" to " ",
        "LBRACE" to "{",
        "RBRACE" to "}",
        "OPEN-BRACE" to "{",
        "CLOSE-BRACE" to "}",
    )

    private val COMMANDS: Map<String, KeyCommand> = mapOf(
        "SCAN" to KeyCommand.SCAN,
        "HIDE" to KeyCommand.HIDE,
        "SETTINGS" to KeyCommand.SETTINGS,
        "NEXT-KEYBOARD" to KeyCommand.NEXT_KEYBOARD,
        "EDIT" to KeyCommand.TEXT_EDIT,
    )

    /** Display names of keys and commands, used for key labels. */
    private val NAMES: Map<Int, String> = (1..12).associate { (KeyEvent.KEYCODE_F1 + it - 1) to "F$it" } + mapOf(
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_ENTER to "Enter",
        KeyEvent.KEYCODE_ESCAPE to "Esc",
        KeyEvent.KEYCODE_DEL to "⌫",
        KeyEvent.KEYCODE_FORWARD_DEL to "Del",
        KeyEvent.KEYCODE_INSERT to "Ins",
        KeyEvent.KEYCODE_MOVE_HOME to "Home",
        KeyEvent.KEYCODE_MOVE_END to "End",
        KeyEvent.KEYCODE_PAGE_UP to "PgUp",
        KeyEvent.KEYCODE_PAGE_DOWN to "PgDn",
        KeyEvent.KEYCODE_DPAD_UP to "↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "→",
        KeyEvent.KEYCODE_SPACE to "Space",
        KeyEvent.KEYCODE_SYSRQ to "Print",
        KeyEvent.KEYCODE_BREAK to "Pause",
        KeyEvent.KEYCODE_MENU to "Menu",
    ) + ModifierKey.entries.associate { it.keyCode to it.label } +
        ('A'..'Z').associate { (KeyEvent.KEYCODE_A + (it - 'A')) to it.toString() } +
        ('0'..'9').associate { (KeyEvent.KEYCODE_0 + (it - '0')) to it.toString() }

    /** Parses [macro]; throws [IllegalArgumentException] naming the first invalid token. */
    fun parse(macro: String): List<MacroStep> {
        val steps = mutableListOf<MacroStep>()
        val text = StringBuilder()
        fun flush() {
            if (text.isNotEmpty()) {
                steps += MacroStep.Text(text.toString())
                text.clear()
            }
        }
        var i = 0
        while (i < macro.length) {
            val c = macro[i]
            if (c != '{') {
                text.append(c)
                i++
                continue
            }
            val end = macro.indexOf('}', i + 1)
            require(end > i) { "missing } after \"${macro.substring(i)}\"" }
            val token = macro.substring(i + 1, end).trim().uppercase()
            val original = macro.substring(i, end + 1)
            when {
                token in TEXT -> text.append(TEXT.getValue(token))
                token.startsWith("U+") || token.startsWith("HEX:") -> text.appendCodePoint(codePoint(token, original))
                else -> {
                    flush()
                    steps += when {
                        token in KEYS -> MacroStep.Key(KEYS.getValue(token))
                        token in SHIFTED_F_KEYS -> MacroStep.Key(SHIFTED_F_KEYS.getValue(token), KeyEvent.META_SHIFT_ON)
                        token in COMMANDS -> MacroStep.Command(COMMANDS.getValue(token))
                        '+' in token -> combo(token, original)
                        token.startsWith("SHOW:") && token.length > 5 ->
                            MacroStep.ShowTab(macro.substring(i + 1, end).trim().substringAfter(':').trim())
                        token.startsWith("TOGGLE:") -> MacroStep.Toggle(
                            QuickToggle.byKey(token.substringAfter(':'))
                                ?: throw IllegalArgumentException("unknown quick toggle $original (${QuickToggle.entries.joinToString { it.key }})"),
                        )
                        else -> throw IllegalArgumentException("unknown token $original")
                    }
                }
            }
            i = end + 1
        }
        flush()
        return steps
    }

    fun isValid(macro: String): Boolean = runCatching { parse(macro) }.isSuccess

    /**
     * True when text is typed after step [index]. A key reaches the app later than text from the
     * keyboard: "Line 1{ENTER}Line 2" arrived as "Line 1Line 2⏎" on the HC20 (Android 15) in a
     * multi-line field, so such an Enter is typed as a line break instead (see KeyboardService.runMacro).
     */
    fun textFollows(steps: List<MacroStep>, index: Int): Boolean =
        steps.subList((index + 1).coerceAtMost(steps.size), steps.size).any { it is MacroStep.Text }

    /** Short label for a key that runs [steps]: its text, otherwise the name of the first key or command. */
    fun label(steps: List<MacroStep>): String {
        val text = steps.filterIsInstance<MacroStep.Text>().joinToString("") { it.text }.trim()
        if (text.isNotEmpty()) return text
        return when (val first = steps.firstOrNull()) {
            is MacroStep.Key -> keyName(first)
            is MacroStep.Command -> if (first.command == KeyCommand.TEXT_EDIT) "Edit" else first.command.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')
            is MacroStep.ShowTab -> first.tab.uppercase()
            is MacroStep.Toggle -> first.toggle.key.replaceFirstChar { it.uppercase() }
            is MacroStep.Text -> first.text
            null -> ""
        }
    }

    /** Icon for single-step macros that have one ({TAB}, {ENTER}, {SCAN} …). */
    fun icon(steps: List<MacroStep>): KeyIcon? {
        val step = steps.singleOrNull() ?: return null
        return when (step) {
            is MacroStep.Key -> if (step.meta != 0) null else when (step.keyCode) {
                KeyEvent.KEYCODE_TAB -> KeyIcon.TAB
                KeyEvent.KEYCODE_ENTER -> KeyIcon.ENTER
                KeyEvent.KEYCODE_DEL -> KeyIcon.BACKSPACE
                KeyEvent.KEYCODE_DPAD_LEFT -> KeyIcon.LEFT
                KeyEvent.KEYCODE_DPAD_RIGHT -> KeyIcon.RIGHT
                KeyEvent.KEYCODE_DPAD_UP -> KeyIcon.UP
                KeyEvent.KEYCODE_DPAD_DOWN -> KeyIcon.DOWN
                else -> null
            }
            is MacroStep.Command -> when (step.command) {
                KeyCommand.SCAN -> KeyIcon.SCAN
                KeyCommand.HIDE -> KeyIcon.HIDE
                KeyCommand.SETTINGS -> KeyIcon.SETTINGS
                KeyCommand.NEXT_KEYBOARD -> null
                KeyCommand.TEXT_EDIT -> KeyIcon.TEXT_EDIT
            }
            else -> null
        }
    }

    /** "Ctrl+Alt+End", "F13", "Tab" … */
    fun keyName(step: MacroStep.Key): String {
        functionKeyName(step)?.let { name ->
            val others = step.meta and KeyEvent.META_SHIFT_ON.inv()
            return (ModifierKey.of(others).map { it.label } + name).joinToString("+")
        }
        val modifiers = ModifierKey.of(step.meta).filter { it.keyCode != step.keyCode }.map { it.label } +
            listOfNotNull("Shift".takeIf { step.meta and KeyEvent.META_SHIFT_ON != 0 })
        return (modifiers + (NAMES[step.keyCode] ?: "Key ${step.keyCode}")).joinToString("+")
    }

    /** `CTRL+ALT+DEL`: modifiers, then one key (a name such as `F4`, or a letter, digit or `SPACE`). */
    private fun combo(token: String, original: String): MacroStep.Key {
        val parts = token.split('+').map { it.trim() }
        require(parts.size >= 2 && parts.none { it.isEmpty() }) { "invalid key combination $original" }
        var meta = 0
        for (part in parts.dropLast(1)) {
            meta = meta or (COMBO_MODIFIERS[part] ?: throw IllegalArgumentException("unknown modifier $part in $original"))
        }
        val last = parts.last()
        val key = when {
            last in KEYS -> MacroStep.Key(KEYS.getValue(last))
            last in SHIFTED_F_KEYS -> MacroStep.Key(SHIFTED_F_KEYS.getValue(last), KeyEvent.META_SHIFT_ON)
            last == "SPACE" -> MacroStep.Key(KeyEvent.KEYCODE_SPACE)
            last.length == 1 && (last[0] in 'A'..'Z' || last[0] in '0'..'9') -> KeyChords.keyFor(last[0].lowercaseChar())!!
            else -> throw IllegalArgumentException("unknown key $last in $original")
        }
        return MacroStep.Key(key.keyCode, key.meta or meta)
    }

    /** "F1"–"F24" for function keys (with Shift: F13–F24). */
    fun functionKeyName(step: MacroStep.Key): String? {
        val n = step.keyCode - KeyEvent.KEYCODE_F1 + 1
        if (n !in 1..12) return null
        return if (step.meta and KeyEvent.META_SHIFT_ON != 0) "F${n + 12}" else "F$n"
    }

    private fun codePoint(token: String, original: String): Int {
        val hex = token.substringAfter(if (token.startsWith("U+")) "U+" else "HEX:")
        val value = hex.toIntOrNull(16)
        require(value != null && value in 1..0x10FFFF && value !in 0xD800..0xDFFF) { "invalid code point $original" }
        return value
    }
}

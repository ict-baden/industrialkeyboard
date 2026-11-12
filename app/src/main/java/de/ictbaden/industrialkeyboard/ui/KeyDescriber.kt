package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.QuickToggle
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.layout.EditAction
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyCommand
import de.ictbaden.industrialkeyboard.layout.KeyIcon
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.layout.MacroStep
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState
import java.util.Locale

/**
 * What a screen reader says for the keys, tabs and bar buttons of the keyboard, in the keyboard's
 * language. Pure apart from the string lookup; nothing it is given (a suggestion, a key) is kept.
 */
class KeyDescriber(private val context: Context) {

    /** What the description of a key depends on besides the key itself. */
    data class KeyContext(
        val shift: ShiftState = ShiftState.OFF,
        val modifiers: Map<ModifierKey, ShiftState> = emptyMap(),
        /** `IME_ACTION_*` of the field's Enter key; [EditorInfo.IME_ACTION_UNSPECIFIED] = plain Enter (see [enterAction]). */
        val enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        /** The app's own name for the Enter action (`EditorInfo.actionLabel`), if any. */
        val enterLabel: String? = null,
        /** Password field without a headset: character keys are "Key" ([de.ictbaden.industrialkeyboard.ime.PasswordSpeech]). */
        val obscure: Boolean = false,
        /** A long press on Backspace deletes a word (`typing.deleteGesture`). */
        val deleteGesture: Boolean = true,
        /** The text editing panel: Select lit, Cut/Copy unavailable without a selection. */
        val editState: TextEditState? = null,
        /** Id of the shown layout (the symbol pages say which way their page key goes). */
        val layoutId: String? = null,
    )

    private fun s(@StringRes id: Int, vararg args: Any): String = AppLanguage.localized(context).getString(id, *args)

    /** The spoken name of [key]. */
    fun describe(key: Key, c: KeyContext): String {
        val output = key.output
        if (c.obscure && typesText(key)) return s(R.string.a11y_key_obscured)
        if (key.letter && key.icon == null) {
            val label = key.label.orEmpty()
            if (c.shift != ShiftState.OFF && label.length == 1) return s(R.string.a11y_capital, label.uppercase(locale()))
            return label
        }
        key.icon?.let { icon -> iconName(icon, key, c)?.let { return it } }
        return when (output) {
            KeyOutput.Space -> key.label?.takeIf { it.isNotBlank() }?.let { s(R.string.a11y_space_language, it) } ?: s(R.string.a11y_space)
            KeyOutput.Backspace -> s(R.string.a11y_backspace)
            KeyOutput.Shift -> shiftName(c.shift)
            KeyOutput.Enter -> enterName(c)
            KeyOutput.Language -> s(R.string.a11y_next_language)
            KeyOutput.Emoji -> s(R.string.a11y_emoji)
            KeyOutput.SymbolsPage -> s(if (c.layoutId == "${Tabs.SYMBOLS}-2") R.string.a11y_symbols else R.string.a11y_more_symbols)
            is KeyOutput.ShowTab -> tabName(output.tab, key.label)
            is KeyOutput.Modifier -> key.label ?: output.modifier.label
            is KeyOutput.Macro -> macroName(key, output.steps)
            is KeyOutput.Edit -> if (output.action == EditAction.CLOSE) s(R.string.a11y_letters) else key.label.orEmpty()
            is KeyOutput.Text -> key.label?.takeIf { it.isNotBlank() } ?: output.text.takeIf { it.isNotBlank() } ?: s(R.string.a11y_space)
            KeyOutput.None -> ""
        }
    }

    /** "on" / "locked" for Ctrl, Alt, Win, "on" for Select while selecting; else null. */
    fun state(key: Key, c: KeyContext): String? = when (val output = key.output) {
        is KeyOutput.Modifier -> when (c.modifiers[output.modifier]) {
            ShiftState.ONCE -> s(R.string.state_on)
            ShiftState.LOCKED -> s(R.string.a11y_state_locked)
            else -> null
        }
        is KeyOutput.Edit -> if (output.action == EditAction.SELECT && c.editState?.selecting == true) s(R.string.state_on) else null
        else -> null
    }

    /** False for Cut and Copy without a selection (they are dimmed and do nothing). */
    fun enabled(key: Key, c: KeyContext): Boolean = (key.output as? KeyOutput.Edit)?.let { c.editState?.enabled(it.action) } ?: true

    /** What a long press does, or null when it does nothing a screen reader should offer. */
    fun longPressLabel(key: Key, c: KeyContext): String? = when (key.output) {
        KeyOutput.Space, KeyOutput.Language -> s(R.string.a11y_switch_keyboard)
        KeyOutput.Shift, is KeyOutput.Modifier -> s(R.string.a11y_lock)
        KeyOutput.Backspace -> if (c.deleteGesture) s(R.string.a11y_delete_word) else null
        else -> null
    }

    /** The long-press alternatives as typed (capitals while Shift is on), at most [MAX_ALTERNATIVES]. */
    fun alternatives(key: Key, c: KeyContext): List<String> =
        key.popup.take(MAX_ALTERNATIVES).map { if (key.letter && c.shift != ShiftState.OFF) it.uppercase(locale()) else it }

    /** The name of alternative [index] ([option] as typed); "Character 1" … in an obscured password field. */
    fun alternativeLabel(option: String, index: Int, c: KeyContext): String = when {
        option == Layouts.COMMA_SETTINGS -> s(R.string.feature_settings)
        option == Layouts.COMMA_EMOJI -> s(R.string.a11y_emoji)
        c.obscure -> s(R.string.a11y_alternative, index + 1)
        else -> option
    }

    /** A tab or toolbar button. */
    fun tab(tab: TabBarView.Tab): String = when (tab.id) {
        Tabs.TOOL_MENU -> s(R.string.a11y_more_features)
        Tabs.TOOL_CLIPBOARD -> s(R.string.clipboard_title)
        Tabs.TOOL_SETTINGS -> s(R.string.feature_settings)
        Tabs.TOOL_SNIPPETS -> s(R.string.snippets_title)
        Tabs.TOOL_EDIT -> s(R.string.feature_text_editing)
        else -> tabName(tab.id, tab.label)
    }

    /** The microphone and hide buttons at the right of the tab bar. */
    fun tabBarExtra(id: Int): String = when (id) {
        MIC -> s(R.string.a11y_voice_start)
        else -> s(R.string.a11y_hide)
    }

    /** A page by its tab id ([Tabs]); [symbolsPage] 2 = the second symbol page; custom pages by [label]. */
    fun pageName(tab: String, label: String? = null, symbolsPage: Int = 1): String =
        if (tab == Tabs.SYMBOLS && symbolsPage == 2) s(R.string.a11y_more_symbols) else tabName(tab, label)

    private fun tabName(tab: String, label: String?): String = when (tab) {
        Tabs.ALPHA -> s(R.string.a11y_letters)
        Tabs.NUMERIC -> s(R.string.feature_numpad)
        Tabs.SYMBOLS -> s(R.string.a11y_symbols)
        Tabs.FUNCTION -> s(R.string.feature_function)
        Tabs.SCAN -> s(R.string.feature_scan)
        else -> label ?: tab
    }

    private fun shiftName(shift: ShiftState) = s(
        when (shift) {
            ShiftState.OFF -> R.string.a11y_shift
            ShiftState.ONCE -> R.string.a11y_shift_on
            ShiftState.LOCKED -> R.string.a11y_caps_lock
        },
    )

    private fun enterName(c: KeyContext): String {
        c.enterLabel?.takeIf { it.isNotBlank() }?.let { return it }
        return s(
            when (c.enterAction) {
                EditorInfo.IME_ACTION_DONE -> R.string.a11y_enter_done
                EditorInfo.IME_ACTION_GO -> R.string.a11y_enter_go
                EditorInfo.IME_ACTION_NEXT -> R.string.a11y_enter_next
                EditorInfo.IME_ACTION_PREVIOUS -> R.string.a11y_enter_previous
                EditorInfo.IME_ACTION_SEARCH -> R.string.a11y_enter_search
                EditorInfo.IME_ACTION_SEND -> R.string.a11y_enter_send
                else -> R.string.a11y_enter
            },
        )
    }

    private fun iconName(icon: KeyIcon, key: Key, c: KeyContext): String? = when (icon) {
        KeyIcon.SHIFT -> shiftName(c.shift)
        KeyIcon.BACKSPACE -> s(R.string.a11y_backspace)
        // Only the real Enter key carries the field's action; an {ENTER} macro is plain Enter.
        KeyIcon.ENTER -> if (key.output == KeyOutput.Enter) enterName(c) else s(R.string.a11y_enter)
        KeyIcon.SPACE -> s(R.string.a11y_space)
        KeyIcon.TAB -> s(R.string.a11y_tab_key)
        KeyIcon.SCAN -> s(R.string.feature_scan)
        KeyIcon.GLOBE -> s(R.string.a11y_next_language)
        KeyIcon.EMOJI -> s(R.string.a11y_emoji)
        KeyIcon.HIDE -> s(R.string.a11y_hide)
        KeyIcon.LEFT -> s(R.string.a11y_arrow_left)
        KeyIcon.RIGHT -> s(R.string.a11y_arrow_right)
        KeyIcon.UP -> s(R.string.a11y_arrow_up)
        KeyIcon.DOWN -> s(R.string.a11y_arrow_down)
        KeyIcon.SETTINGS -> s(R.string.feature_settings)
        KeyIcon.TEXT_EDIT -> s(R.string.feature_text_editing)
    }

    /**
     * A single key or command with its generated label ("PgUp", "Scan"): its spoken name ("Page up").
     * A label the administrator chose is spoken as written; F-keys stay "F5".
     */
    private fun macroName(key: Key, steps: List<MacroStep>): String {
        val generated = key.label == null || key.label == Macro.label(steps)
        val step = steps.singleOrNull()
        if (generated && step != null) {
            when (step) {
                is MacroStep.Key -> if (step.meta == 0) keyName(step.keyCode)?.let { return s(it) }
                is MacroStep.Command -> return commandName(step.command)
                is MacroStep.ShowTab -> return tabName(step.tab, null)
                is MacroStep.Toggle -> return s(toggleName(step.toggle))
                is MacroStep.Text -> Unit
            }
        }
        return key.label ?: Macro.label(steps)
    }

    @StringRes
    private fun keyName(code: Int): Int? = when (code) {
        KeyEvent.KEYCODE_PAGE_UP -> R.string.a11y_key_page_up
        KeyEvent.KEYCODE_PAGE_DOWN -> R.string.a11y_key_page_down
        KeyEvent.KEYCODE_MOVE_HOME -> R.string.text_edit_home
        KeyEvent.KEYCODE_MOVE_END -> R.string.text_edit_end
        KeyEvent.KEYCODE_INSERT -> R.string.a11y_key_insert
        KeyEvent.KEYCODE_FORWARD_DEL -> R.string.text_edit_delete
        KeyEvent.KEYCODE_ESCAPE -> R.string.a11y_key_escape
        KeyEvent.KEYCODE_SYSRQ -> R.string.a11y_key_print
        KeyEvent.KEYCODE_BREAK -> R.string.a11y_key_pause
        KeyEvent.KEYCODE_MENU -> R.string.a11y_key_menu
        KeyEvent.KEYCODE_TAB -> R.string.a11y_tab_key
        KeyEvent.KEYCODE_ENTER -> R.string.a11y_enter
        KeyEvent.KEYCODE_DEL -> R.string.a11y_backspace
        KeyEvent.KEYCODE_SPACE -> R.string.a11y_space
        KeyEvent.KEYCODE_DPAD_LEFT -> R.string.a11y_arrow_left
        KeyEvent.KEYCODE_DPAD_RIGHT -> R.string.a11y_arrow_right
        KeyEvent.KEYCODE_DPAD_UP -> R.string.a11y_arrow_up
        KeyEvent.KEYCODE_DPAD_DOWN -> R.string.a11y_arrow_down
        else -> null
    }

    private fun commandName(command: KeyCommand): String = s(
        when (command) {
            KeyCommand.SCAN -> R.string.feature_scan
            KeyCommand.HIDE -> R.string.a11y_hide
            KeyCommand.SETTINGS -> R.string.feature_settings
            KeyCommand.NEXT_KEYBOARD -> R.string.a11y_switch_keyboard
            KeyCommand.TEXT_EDIT -> R.string.feature_text_editing
        },
    )

    @StringRes
    private fun toggleName(t: QuickToggle): Int = when (t) {
        QuickToggle.GLOVE -> R.string.feature_glove
        QuickToggle.CONTRAST -> R.string.feature_contrast
        QuickToggle.SCANNER -> R.string.feature_scanner
        QuickToggle.SOUND -> R.string.feature_sound
    }

    /** Keys that type characters (letters, digits, symbols, text macros): obscured in password fields. */
    private fun typesText(key: Key): Boolean = when (val output = key.output) {
        is KeyOutput.Text -> true
        is KeyOutput.Macro -> output.steps.isNotEmpty() && output.steps.all { it is MacroStep.Text }
        else -> false
    }

    /** The same capitals as the keys show and type. */
    private fun locale(): Locale = Locale.getDefault()

    companion object {
        /** Custom actions per key (`R.id.a11y_alt_0` … `a11y_alt_9`). */
        const val MAX_ALTERNATIVES = 10

        /** Tab bar button ids for [tabBarExtra]. */
        const val MIC = 1000
        const val HIDE = 1001

        /**
         * The Enter action of a field for [KeyContext.enterAction]: [EditorInfo.IME_ACTION_UNSPECIFIED]
         * when the app asks for a plain Enter (`IME_FLAG_NO_ENTER_ACTION`).
         */
        fun enterAction(imeOptions: Int): Int =
            if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) EditorInfo.IME_ACTION_UNSPECIFIED else imeOptions and EditorInfo.IME_MASK_ACTION
    }
}

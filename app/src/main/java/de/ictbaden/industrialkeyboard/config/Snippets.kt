package de.ictbaden.industrialkeyboard.config

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.ime.FieldPolicy
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.layout.MacroStep
import java.util.Locale

/**
 * Rules for snippets (Textbausteine), shared by the JSON format, the user's own snippets, the
 * editor and the keyboard. Pure: no Android services, nothing stored.
 */
object Snippets {
    const val MAX_ADMIN = 100
    const val MAX_USER = 50
    const val MAX_LABEL = 40
    const val MAX_TEXT = 2000
    const val MIN_SHORTCUT = 2
    const val MAX_SHORTCUT = 16

    /** Letters only: the keyboard tracks only letters as the word being typed. */
    val SHORTCUT = Regex("\\p{L}{$MIN_SHORTCUT,$MAX_SHORTCUT}")

    /** Preview lines in the panel. */
    private const val PREVIEW_LINES = 2
    private const val PREVIEW_CHARS = 160
    private val PREVIEW_TOKEN = Regex("\\{\\s*(TAB|ENTER)\\s*\\}", RegexOption.IGNORE_CASE)

    enum class Problem { LABEL_EMPTY, LABEL_TOO_LONG, TEXT_EMPTY, TEXT_TOO_LONG, MACRO, MACRO_NOT_ALLOWED, SHORTCUT_FORMAT, SHORTCUT_TAKEN }

    /**
     * First problem of one snippet, or null. [label] and [shortcut] as the user entered them
     * (trimmed here), [text] exactly. [taken] = lower-case shortcuts of the other snippets.
     * [user]: an own snippet, which may only hold text, `{TAB}` and `{ENTER}` (no commands, no
     * key combinations, no `{SHOW:…}`); the administrator's may hold every macro.
     */
    fun problem(label: String, text: String, shortcut: String?, taken: Set<String>, user: Boolean = false): Problem? {
        val name = label.trim()
        val short = shortcut?.trim()?.ifEmpty { null }
        return when {
            name.isEmpty() -> Problem.LABEL_EMPTY
            name.length > MAX_LABEL -> Problem.LABEL_TOO_LONG
            text.isEmpty() -> Problem.TEXT_EMPTY
            text.length > MAX_TEXT -> Problem.TEXT_TOO_LONG
            !Macro.isValid(text) -> Problem.MACRO
            user && !userMacro(Macro.parse(text)) -> Problem.MACRO_NOT_ALLOWED
            short != null && !short.matches(SHORTCUT) -> Problem.SHORTCUT_FORMAT
            short != null && key(short) in taken -> Problem.SHORTCUT_TAKEN
            else -> null
        }
    }

    /** What an own snippet may do: type text, Tab and Enter – nothing that opens, switches or chords. */
    fun userMacro(steps: List<MacroStep>): Boolean = steps.all { step ->
        step is MacroStep.Text ||
            (step is MacroStep.Key && step.meta == 0 && (step.keyCode == KeyEvent.KEYCODE_TAB || step.keyCode == KeyEvent.KEYCODE_ENTER))
    }

    /** The form shortcuts are compared in. */
    fun key(shortcut: String): String = shortcut.trim().lowercase(Locale.ROOT)

    /** The user's snippets count: enabled and allowed by the administrator. */
    fun userAllowed(config: KeyboardConfig): Boolean = config.snippets.enabled && config.snippets.allowUser

    /** Admin first, then the user's own (only with `allowUser`); empty when disabled. Second = admin. */
    fun all(config: KeyboardConfig, user: List<Snippet>): List<Pair<Snippet, Boolean>> {
        if (!config.snippets.enabled) return emptyList()
        return config.snippets.items.map { it to true } + (if (config.snippets.allowUser) user.map { it to false } else emptyList())
    }

    /** Something to show: the panel has entries or the user may add some. */
    fun reachable(config: KeyboardConfig): Boolean =
        config.snippets.enabled && (config.snippets.items.isNotEmpty() || config.snippets.allowUser)

    /** Exact, case-insensitive; the administrator's snippet wins over the user's on a clash. */
    fun byShortcut(word: String, config: KeyboardConfig, user: List<Snippet>): Snippet? {
        if (word.length < MIN_SHORTCUT) return null
        val wanted = key(word)
        return all(config, user).firstOrNull { (s, _) -> s.shortcut?.let(::key) == wanted }?.first
    }

    /**
     * The snippet the typed [word] offers in the suggestion strip, or null: only with
     * `snippets.enabled` and `snippets.shortcuts`, on the letters, while no panel replaces the keys,
     * and in text fields that are no password fields ([FieldPolicy.snippetShortcutsAllowed] – also
     * those without suggestions, since a shortcut only offers and never replaces by itself).
     */
    fun matchFor(word: CharSequence, page: Page, inputType: Int, toolPanel: String?, config: KeyboardConfig, user: List<Snippet>): Snippet? {
        if (!config.snippets.enabled || !config.snippets.shortcuts) return null
        if (page !is Page.Alpha || toolPanel != null || word.length < MIN_SHORTCUT) return null
        if (!FieldPolicy.snippetShortcutsAllowed(inputType)) return null
        return byShortcut(word.toString(), config, user)
    }

    /** For the panel: `{TAB}` → ⇥, `{ENTER}` → ⏎, other tokens as written, at most two lines. */
    fun preview(text: String): String {
        val out = StringBuilder()
        var spaceNext = false
        var last = 0
        fun append(chunk: String) {
            if (chunk.isEmpty()) return
            if (spaceNext && !chunk.first().isWhitespace()) out.append(' ')
            spaceNext = false
            out.append(chunk)
        }
        for (match in PREVIEW_TOKEN.findAll(text)) {
            append(text.substring(last, match.range.first))
            if (out.isNotEmpty() && !out.last().isWhitespace()) out.append(' ')
            out.append(if (match.groupValues[1].equals("TAB", ignoreCase = true)) '⇥' else '⏎')
            spaceNext = true
            last = match.range.last + 1
        }
        append(text.substring(last))
        val lines = out.toString().lines()
        var shown = lines.take(PREVIEW_LINES).joinToString("\n").trimEnd()
        if (shown.length > PREVIEW_CHARS) shown = shown.take(PREVIEW_CHARS).trimEnd() + "…"
        else if (lines.size > PREVIEW_LINES) shown += "…"
        return shown
    }
}

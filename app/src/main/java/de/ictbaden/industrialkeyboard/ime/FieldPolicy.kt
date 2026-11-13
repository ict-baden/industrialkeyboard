package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import android.view.inputmethod.EditorInfo
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.NumericVariant

/** A page of the keyboard; [tab] is the tab it belongs to. */
sealed interface Page {
    val tab: String

    data class Alpha(val variant: AlphaVariant) : Page {
        override val tab get() = Tabs.ALPHA
    }

    data class Symbols(val page: Int, val variant: AlphaVariant) : Page {
        override val tab get() = Tabs.SYMBOLS
    }

    data class Numeric(val variant: NumericVariant) : Page {
        override val tab get() = Tabs.NUMERIC
    }

    data object Function : Page {
        override val tab get() = Tabs.FUNCTION
    }

    data object Scan : Page {
        override val tab get() = Tabs.SCAN
    }

    data class Custom(val id: String) : Page {
        override val tab get() = id
    }
}

/** What kind of input the focused field expects. */
data class Field(val alpha: AlphaVariant, val numeric: NumericVariant?)

/** Chooses tabs and pages from the field's input type and the configuration. */
object FieldPolicy {

    fun field(inputType: Int): Field {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val alpha = when {
            inputClass != InputType.TYPE_CLASS_TEXT -> AlphaVariant.TEXT
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> AlphaVariant.EMAIL
            variation == InputType.TYPE_TEXT_VARIATION_URI -> AlphaVariant.URL
            else -> AlphaVariant.TEXT
        }
        val numeric = when (inputClass) {
            InputType.TYPE_CLASS_NUMBER ->
                if (variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) NumericVariant.PIN else NumericVariant.NUMBER
            InputType.TYPE_CLASS_PHONE -> NumericVariant.PHONE
            InputType.TYPE_CLASS_DATETIME -> when (variation) {
                InputType.TYPE_DATETIME_VARIATION_DATE -> NumericVariant.DATE
                InputType.TYPE_DATETIME_VARIATION_TIME -> NumericVariant.TIME
                else -> NumericVariant.DATETIME
            }
            else -> null
        }
        return Field(alpha, numeric)
    }

    /**
     * Password fields: no key preview, no scanned text from other apps. Visible password fields
     * count too (like in AOSP's keyboard): apps switch to them for "show password", and the
     * keyboard cannot tell those from fields that only use the type to turn off suggestions.
     */
    /**
     * Normal text where word suggestions help: not passwords, e-mail, web addresses or filters, not
     * numbers or dates, and not when the app asks for no suggestions (barcode fields usually do).
     */
    fun suggestionsAllowed(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) return false
        // Search and filter fields get suggestions too, like Gboard (but no autocorrect, see below).
        return (inputType and InputType.TYPE_MASK_VARIATION) !in NO_WORD_VARIATIONS - InputType.TYPE_TEXT_VARIATION_FILTER
    }

    /** Autocorrect only where suggestions are shown, and never in search fields: a query is often a name or code. */
    fun autocorrectAllowed(inputType: Int, imeOptions: Int): Boolean =
        suggestionsAllowed(inputType) &&
            (inputType and InputType.TYPE_MASK_VARIATION) != InputType.TYPE_TEXT_VARIATION_FILTER &&
            (imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_SEARCH

    /** Emoji only make sense in normal text: not in passwords, e-mail, web addresses, numbers or dates. */
    fun emojiAllowed(inputType: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return (inputType and InputType.TYPE_MASK_VARIATION) !in NO_WORD_VARIATIONS
    }

    private val NO_WORD_VARIATIONS = setOf(
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_URI,
        InputType.TYPE_TEXT_VARIATION_FILTER,
    )

    fun isPassword(inputType: Int): Boolean {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        return when (inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    /**
     * `{ENTER}` with more text after it in a macro (a snippet "Line 1{ENTER}Line 2") types a line
     * break as text: in a multi-line text field without an editor action, where Enter only inserts a
     * line break. As a key it reached the app after the text that followed it (HC20, Android 15);
     * AOSP's keyboard also commits "\n" there. Single-line fields keep their action (Next, Done …),
     * terminals (no text class) the key, and an Enter at the end of a macro stays a key everywhere.
     */
    fun newlineAsText(inputType: Int, imeOptions: Int): Boolean {
        if (inputType and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        if (inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0) return false
        return imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0 ||
            imeOptions and EditorInfo.IME_MASK_ACTION == EditorInfo.IME_ACTION_NONE
    }

    /** Snippets panel, ⊞ tile and bar button: everywhere but in password fields. */
    fun snippetsAllowed(inputType: Int): Boolean = !isPassword(inputType)

    /**
     * Snippet shortcuts in the suggestion strip: text fields that are no password fields. On purpose
     * also fields with `textNoSuggestions` (barcode, article number): a shortcut only offers its
     * snippet and never replaces by itself.
     */
    fun snippetShortcutsAllowed(inputType: Int): Boolean =
        inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT && !isPassword(inputType)

    /** Tab bar entries in display order; empty with classic navigation. */
    fun tabs(config: KeyboardConfig, scanAvailable: Boolean): List<String> {
        if (config.layout.navigation == Navigation.KEYS) return emptyList()
        val tabs = buildList {
            if (config.tabs.alpha) add(Tabs.ALPHA)
            if (config.tabs.numeric) add(Tabs.NUMERIC)
            if (config.tabs.symbols) add(Tabs.SYMBOLS)
            if (config.tabs.function) add(Tabs.FUNCTION)
            if (config.tabs.scan && scanAvailable) add(Tabs.SCAN)
            config.customLayouts.forEach { add(it.id) }
        }
        return tabs.ifEmpty { listOf(Tabs.ALPHA) }
    }

    /** Page shown when a field gains focus: numeric fields get a pad, text the preferred tab. */
    fun startPage(field: Field, config: KeyboardConfig, scanAvailable: Boolean): Page {
        field.numeric?.let { return Page.Numeric(it) }
        val preferred = config.tabs.preferred
        if (preferred != Tabs.AUTO) pageForTab(preferred, field, config, scanAvailable)?.let { return it }
        val first = tabs(config, scanAvailable).firstOrNull() ?: Tabs.ALPHA
        val tab = if (first == Tabs.ALPHA || config.tabs.alpha || config.layout.navigation == Navigation.KEYS) Tabs.ALPHA else first
        return pageForTab(tab, field, config, scanAvailable) ?: Page.Alpha(field.alpha)
    }

    fun pageForTab(tab: String, field: Field, config: KeyboardConfig, scanAvailable: Boolean): Page? = when (tab) {
        Tabs.ALPHA -> Page.Alpha(field.alpha)
        Tabs.NUMERIC -> Page.Numeric(field.numeric ?: NumericVariant.NUMBER)
        Tabs.SYMBOLS -> Page.Symbols(1, field.alpha)
        Tabs.FUNCTION -> Page.Function
        Tabs.SCAN -> if (scanAvailable) Page.Scan else null
        else -> config.customLayout(tab)?.let { Page.Custom(it.id) }
    }
}

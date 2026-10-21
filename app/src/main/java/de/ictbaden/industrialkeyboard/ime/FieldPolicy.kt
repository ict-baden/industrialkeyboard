package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
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

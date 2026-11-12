package de.ictbaden.industrialkeyboard.layout

import android.view.KeyEvent
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.CustomLayout
import de.ictbaden.industrialkeyboard.config.KeyStyle
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.NumericOrder
import de.ictbaden.industrialkeyboard.config.ProgrammableKey
import de.ictbaden.industrialkeyboard.config.Tabs

/** Letter layout variants for special text fields. */
enum class AlphaVariant { TEXT, EMAIL, URL }

/** Numeric pad variants, chosen by the field's input type. */
enum class NumericVariant { NUMBER, PHONE, PIN, DATE, TIME, DATETIME }

/** Builds the key layouts from the configuration. */
object Layouts {

    private const val DIGITS = "1234567890"

    /** QWERTZ with ü, ö and ä as own keys (`layout.umlautKeys`). */
    private val QWERTZ_UMLAUTS = listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm")

    private val GERMAN_FIRST = mapOf('a' to 'ä', 'o' to 'ö', 'u' to 'ü', 's' to 'ß')

    private val ROWS: Map<Alphabet, List<String>> = mapOf(
        Alphabet.QWERTY to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        Alphabet.QWERTZ to listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
        Alphabet.AZERTY to listOf("azertyuiop", "qsdfghjklm", "wxcvbn'"),
        Alphabet.NORDIC to listOf("qwertyuiopå", "asdfghjklöä", "zxcvbnm"),
        Alphabet.SPANISH to listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm"),
    )

    /** Long-press alternatives of Latin letters. */
    private val LETTER_ALTERNATIVES: Map<Char, String> = mapOf(
        'a' to "àáâäæãåā",
        'c' to "çćč",
        'd' to "ďð",
        'e' to "èéêëēėę",
        'g' to "ğ",
        'i' to "ìíîïīįı",
        'l' to "łľ",
        'n' to "ñńň",
        'o' to "òóôöõœøō",
        'r' to "ř",
        's' to "ßśšş",
        't' to "ťþ",
        'u' to "ùúûüūůű",
        'y' to "ýÿ",
        'z' to "žźż",
    )

    private val SYMBOL_ALTERNATIVES: Map<String, List<String>> = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼"),
        "2" to listOf("²", "⅔"),
        "3" to listOf("³", "¾"),
        "0" to listOf("°", "∅"),
        "$" to listOf("€", "£", "¥", "¢"),
        "€" to listOf("$", "£", "¥", "¢"),
        "£" to listOf("€", "$", "¥", "¢"),
        "-" to listOf("–", "—", "_"),
        "+" to listOf("±"),
        "(" to listOf("<", "[", "{"),
        ")" to listOf(">", "]", "}"),
        "%" to listOf("‰"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "'" to listOf("‘", "’", "‚", "`", "´"),
        "!" to listOf("¡"),
        "?" to listOf("¿"),
        "*" to listOf("×", "•"),
        "/" to listOf("\\", "÷"),
        "=" to listOf("≠", "≈"),
        "#" to listOf("№"),
    )

    /** First symbol page in Gboard's order; [CURRENCY] is replaced by the currency of the keyboard language. */
    private val SYMBOLS = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", CURRENCY, "_", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )

    /** Second page, close to Gboard's "=\<" page. */
    private val SYMBOLS_2 = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "Δ"),
        listOf("£", "¥", "$", "¢", "^", "°", "=", "{", "}", "\\"),
        listOf("%", "©", "®", "™", "✓", "[", "]"),
    )

    private const val CURRENCY = "¤"

    /** Long-press choices on the comma that are commands, not text. */
    const val COMMA_SETTINGS = "⚙"
    const val COMMA_EMOJI = "😊"

    /** Symbols a long press on the letters types, by position, like Gboard ("Long press for symbols"). */
    private val ROW2_SYMBOLS = listOf("@", "#", CURRENCY, "_", "&", "-", "+", "(", ")")
    private val ROW3_SYMBOLS = listOf("*", "\"", "'", ":", ";", "!", "?")

    /** € for most languages; $ where Gboard shows it (US English and other dollar countries), £ for the UK. */
    fun currency(locale: java.util.Locale = Alphabet.keyboardLocale()): String = when (locale.country.uppercase(java.util.Locale.ROOT)) {
        "US", "CA", "MX", "AU", "NZ", "SG", "HK" -> "$"
        "GB" -> "£"
        "" -> if (locale.language == "en") "$" else "€"
        else -> "€"
    }

    private fun withCurrency(symbol: String) = if (symbol == CURRENCY) currency() else symbol

    /**
     * Letters. [languageKey]: show 🌐 next to the space bar (several keyboard languages and no
     * switch button of Android below the keyboard).
     */
    fun alpha(config: KeyboardConfig, variant: AlphaVariant = AlphaVariant.TEXT, languageKey: Boolean = false, emojiKey: Boolean = false): KeyboardLayout {
        val alphabet = config.layout.alphabet.resolve()
        val letters = if (alphabet == Alphabet.QWERTZ && config.layout.umlautKeys) QWERTZ_UMLAUTS else ROWS.getValue(alphabet)
        // German like Gboard: ä, ö, ü and ß first on long press of a, o, u and s.
        val firstAlternatives = if (alphabet == Alphabet.QWERTZ && !config.layout.umlautKeys) GERMAN_FIRST else emptyMap()
        val hints = config.appearance.keyHints && !config.appearance.numberRow
        val rows = buildList {
            if (config.appearance.functionRow) {
                // Twelve keys in the width of the letter row, so the letters keep their size.
                val width = letters[0].length / 12f
                add(KeyRow((1..12).map { fKey(it, shifted = false, width = width, style = KeyStyle.FUNCTION) }))
            }
            if (config.appearance.modifierRow) add(modifierRow(letters[0].length.toFloat()))
            if (config.appearance.numberRow) add(KeyRow(DIGITS.map { textKey(it.toString()) }))
            add(KeyRow(letters[0].mapIndexed { i, c -> letterKey(c, digit = if (hints && i < DIGITS.length) DIGITS[i] else null, first = firstAlternatives[c]) }))
            val symbols = config.appearance.symbolHints
            fun symbolAt(row: List<String>, i: Int) = if (symbols) row.getOrNull(i)?.let(::withCurrency) else null
            add(KeyRow(letters[1].mapIndexed { i, c -> letterKey(c, first = firstAlternatives[c], symbol = symbolAt(ROW2_SYMBOLS, i)) }))
            add(KeyRow(listOf(shiftKey()) + letters[2].mapIndexed { i, c -> letterKey(c, first = firstAlternatives[c], symbol = symbolAt(ROW3_SYMBOLS, i)) } + backspaceKey()))
            add(bottomRow(config, variant, Tabs.ALPHA, spaceLabel(config, alphabet), languageKey, emojiKey))
        }
        return KeyboardLayout(Tabs.ALPHA, rows, alphabet.label)
    }

    /** Symbols in two pages ([page] 1 or 2). */
    fun symbols(config: KeyboardConfig, page: Int = 1, variant: AlphaVariant = AlphaVariant.TEXT): KeyboardLayout {
        val source = (if (page == 2) SYMBOLS_2 else SYMBOLS).map { row -> row.map(::withCurrency) }
        // Labels like Gboard: "=\<" leads to the second page, "?123" back to the first.
        val pageKey = Key(label = if (page == 2) "?123" else "=\\<", output = KeyOutput.SymbolsPage, width = 1.5f, style = KeyStyle.FUNCTION, flex = true)
        val rows = listOf(
            KeyRow(source[0].map(::textKey)),
            KeyRow(source[1].map(::textKey)),
            KeyRow(listOf(pageKey) + source[2].map(::textKey) + backspaceKey()),
            bottomRow(config, variant, Tabs.SYMBOLS, ""),
        )
        return KeyboardLayout("${Tabs.SYMBOLS}-$page", rows)
    }

    fun numeric(config: KeyboardConfig, variant: NumericVariant = NumericVariant.NUMBER): KeyboardLayout {
        val calculator = config.layout.numericOrder == NumericOrder.CALCULATOR && variant != NumericVariant.PHONE
        val digits = if (calculator) listOf("789", "456", "123") else listOf("123", "456", "789")
        val classic = config.layout.navigation == Navigation.KEYS
        val side: List<Key>? = when (variant) {
            NumericVariant.PIN -> null
            NumericVariant.PHONE -> listOf("+", "*", "#", "-").map { fixedKey(it) }
            NumericVariant.DATE -> listOf("/", "-", ".", ",").map { fixedKey(it) }
            NumericVariant.TIME -> listOf(":", ".", "-", ",").map { fixedKey(it) }
            NumericVariant.DATETIME -> listOf("/", ":", "-", ".").map { fixedKey(it) }
            NumericVariant.NUMBER -> listOf(
                ProgrammableKey.NUMERIC_P1,
                ProgrammableKey.NUMERIC_P2,
                ProgrammableKey.NUMERIC_P3,
                ProgrammableKey.NUMERIC_P4,
            ).map { programmable(config.programmable(it), 1f) }
        }?.let { keys -> if (classic) keys.dropLast(1) + abcKey() else keys }
        val right = listOf(
            backspaceKey(width = 1.25f, flex = false),
            tabKey(width = 1.25f),
            Key(icon = KeyIcon.SPACE, output = KeyOutput.Space, width = 1.25f, style = KeyStyle.FUNCTION),
            enterKey(width = 1.25f),
        )
        val rows = (0..3).map { r ->
            val middle = if (r < 3) digits[r].map { digitKey(it.toString()) } else listOf(textKey(","), digitKey("0"), textKey("."))
            KeyRow(listOfNotNull(side?.get(r)) + middle + right[r])
        }
        return KeyboardLayout("${Tabs.NUMERIC}-${variant.name.lowercase()}", rows)
    }

    /**
     * F1–F12 (with Shift F13–F24), Esc, navigation and arrow keys – for terminal emulation and
     * hotkeys of web apps.
     */
    fun function(shifted: Boolean = false, modifiers: Boolean = false): KeyboardLayout {
        fun nav(label: String?, code: Int, icon: KeyIcon? = null, repeat: Boolean = false) =
            Key(label = label, icon = icon, output = KeyOutput.Macro(listOf(MacroStep.Key(code))), style = KeyStyle.FUNCTION, repeat = repeat)
        val fWidth = 7f / 6f
        val rows = listOfNotNull(
            if (modifiers) {
                KeyRow(
                    ModifierKey.entries.map { modifierKey(it, fWidth) } +
                        listOf("{MENU}", "{PRINT}", "{PAUSE}").map { macroKey(it, null, fWidth, KeyStyle.FUNCTION) },
                )
            } else {
                null
            },
            KeyRow((1..6).map { fKey(it, shifted, fWidth) }),
            KeyRow((7..12).map { fKey(it, shifted, fWidth) }),
            KeyRow(
                listOf(
                    Key(icon = KeyIcon.SHIFT, output = KeyOutput.Shift, style = KeyStyle.FUNCTION),
                    nav("Esc", KeyEvent.KEYCODE_ESCAPE),
                    nav("Home", KeyEvent.KEYCODE_MOVE_HOME),
                    nav(null, KeyEvent.KEYCODE_DPAD_UP, KeyIcon.UP, repeat = true),
                    nav("End", KeyEvent.KEYCODE_MOVE_END),
                    nav("PgUp", KeyEvent.KEYCODE_PAGE_UP),
                    nav("Del", KeyEvent.KEYCODE_FORWARD_DEL, repeat = true),
                ),
            ),
            KeyRow(
                listOf(
                    tabKey(width = 1f),
                    nav("Ins", KeyEvent.KEYCODE_INSERT),
                    nav(null, KeyEvent.KEYCODE_DPAD_LEFT, KeyIcon.LEFT, repeat = true),
                    nav(null, KeyEvent.KEYCODE_DPAD_DOWN, KeyIcon.DOWN, repeat = true),
                    nav(null, KeyEvent.KEYCODE_DPAD_RIGHT, KeyIcon.RIGHT, repeat = true),
                    nav("PgDn", KeyEvent.KEYCODE_PAGE_DOWN),
                    enterKey(width = 1f),
                ),
            ),
        )
        return KeyboardLayout(if (shifted) "${Tabs.FUNCTION}-shifted" else Tabs.FUNCTION, rows)
    }

    /** An administrator's layout from the configuration. */
    fun custom(layout: CustomLayout): KeyboardLayout = KeyboardLayout(
        layout.id,
        layout.rows.map { row ->
            KeyRow(row.map { key -> macroKey(key.output, key.label.ifEmpty { null }, key.width, key.style) })
        },
        layout.label,
    )

    /** Keys below the scan pad. */
    fun scanRow(): KeyboardLayout = KeyboardLayout(
        "${Tabs.SCAN}-row",
        listOf(KeyRow(listOf(tabKey(width = 1.5f), Key(icon = KeyIcon.SPACE, output = KeyOutput.Space, width = 4f, flex = true), backspaceKey(flex = false), enterKey(width = 2f)))),
    )

    // region keys

    /** Space bar text: the keyboard language with `auto` (like Gboard), else the fixed layout. */
    private fun spaceLabel(config: KeyboardConfig, alphabet: Alphabet): String =
        if (config.layout.alphabet == Alphabet.AUTO) Alphabet.languageName(Alphabet.keyboardLocale()) else alphabet.label

    /** The letter rows of [alphabet] as shown (for the typo model of the suggestions). */
    fun letterRows(config: KeyboardConfig, alphabet: Alphabet): List<String> =
        if (alphabet == Alphabet.QWERTZ && config.layout.umlautKeys) QWERTZ_UMLAUTS else ROWS.getValue(alphabet)

    private fun bottomRow(
        config: KeyboardConfig,
        variant: AlphaVariant,
        page: String,
        spaceLabel: String,
        languageKey: Boolean = false,
        emojiKey: Boolean = false,
    ): KeyRow {
        val classic = config.layout.navigation == Navigation.KEYS
        // "?123" like Gboard: always in the classic navigation, with tabs when switched on.
        val switchKey = classic || config.layout.symbolsKey
        val keys = buildList {
            if (switchKey) {
                val toSymbols = page == Tabs.ALPHA
                add(Key(label = if (toSymbols) "?123" else "ABC", output = KeyOutput.ShowTab(if (toSymbols) Tabs.SYMBOLS else Tabs.ALPHA), width = 1.5f, style = KeyStyle.FUNCTION))
            }
            add(programmable(config.programmable(ProgrammableKey.ALPHA_P1), width = if (switchKey) 1f else 1.5f))
            add(
                when (variant) {
                    AlphaVariant.EMAIL -> textKey("@", listOf(",", "-", "_", "+"))
                    AlphaVariant.URL -> textKey("/", listOf(",", ":", "-", "_", "?", "="))
                    // Like Gboard: a long press on the comma offers the settings and emoji first.
                    AlphaVariant.TEXT -> textKey(",", listOf(COMMA_SETTINGS, COMMA_EMOJI, ";", ":", "_", "-"))
                },
            )
            // Like Gboard: 😊 between the comma and the space bar.
            if (emojiKey) add(Key(icon = KeyIcon.EMOJI, output = KeyOutput.Emoji, style = KeyStyle.FUNCTION))
            if (languageKey) add(Key(icon = KeyIcon.GLOBE, output = KeyOutput.Language, style = KeyStyle.FUNCTION))
            add(Key(label = spaceLabel, output = KeyOutput.Space, width = 3f - (if (languageKey) 1f else 0f) - (if (emojiKey) 1f else 0f), flex = true))
            add(
                if (variant == AlphaVariant.URL) {
                    textKey(".", listOf(".com", ".de", ".org", ".net", "www."))
                } else {
                    textKey(".", listOf(",", "?", "!", "'", "\"", ":", ";", "-"))
                },
            )
            if (config.scanner.scanKey) add(Key(icon = KeyIcon.SCAN, output = KeyOutput.Macro(listOf(MacroStep.Command(KeyCommand.SCAN))), width = 1.25f, style = KeyStyle.FUNCTION))
            // 1.5 like Gboard: the space bar gets the rest.
            add(enterKey(width = 1.5f))
        }
        return KeyRow(keys)
    }

    /** Ctrl, Win, Alt, Esc, Tab and Del in the width of the top letter row. */
    private fun modifierRow(units: Float): KeyRow {
        val width = units / 6f
        fun key(macro: String) = macroKey(macro, null, width, KeyStyle.FUNCTION)
        return KeyRow(ModifierKey.entries.map { modifierKey(it, width) } + listOf(key("{ESC}"), key("{TAB}"), key("{DEL}").copy(repeat = true)))
    }

    fun modifierKey(modifier: ModifierKey, width: Float = 1f, style: KeyStyle = KeyStyle.FUNCTION) =
        Key(label = modifier.label, output = KeyOutput.Modifier(modifier), width = width, style = style)

    private fun letterKey(c: Char, digit: Char? = null, first: Char? = null, symbol: String? = null): Key {
        if (!c.isLetter()) return textKey(c.toString())
        val alternatives = (listOfNotNull(first) + LETTER_ALTERNATIVES[c].orEmpty().toList().filter { it != first }).map { it.toString() }
        return Key(
            label = c.toString(),
            output = KeyOutput.Text(c.toString()),
            hint = digit?.toString() ?: symbol,
            // The hint comes first: a long press types it, like Gboard.
            popup = listOfNotNull(digit?.toString(), symbol) + alternatives,
            letter = true,
        )
    }

    private fun textKey(text: String): Key = textKey(text, SYMBOL_ALTERNATIVES[text].orEmpty())

    private fun textKey(text: String, popup: List<String>) = Key(label = text, output = KeyOutput.Text(text), popup = popup)

    private fun digitKey(text: String) = Key(label = text, output = KeyOutput.Text(text), popup = SYMBOL_ALTERNATIVES[text].orEmpty())

    private fun fixedKey(text: String) = Key(label = text, output = KeyOutput.Text(text), style = KeyStyle.FUNCTION)

    /** F[n] (1–12), or F[n+12] as Shift + F[n] when [shifted]. */
    private fun fKey(n: Int, shifted: Boolean, width: Float, style: KeyStyle = KeyStyle.NORMAL) = Key(
        label = "F${if (shifted) n + 12 else n}",
        output = KeyOutput.Macro(listOf(MacroStep.Key(KeyEvent.KEYCODE_F1 + n - 1, if (shifted) KeyEvent.META_SHIFT_ON else 0))),
        width = width,
        style = style,
    )

    private fun shiftKey() = Key(icon = KeyIcon.SHIFT, output = KeyOutput.Shift, width = 1.5f, style = KeyStyle.FUNCTION, flex = true)

    private fun backspaceKey(width: Float = 1.5f, flex: Boolean = true) =
        Key(icon = KeyIcon.BACKSPACE, output = KeyOutput.Backspace, width = width, style = KeyStyle.FUNCTION, flex = flex, repeat = true)

    private fun enterKey(width: Float) = Key(icon = KeyIcon.ENTER, output = KeyOutput.Enter, width = width, style = KeyStyle.ACCENT)

    private fun tabKey(width: Float) =
        Key(icon = KeyIcon.TAB, output = KeyOutput.Macro(listOf(MacroStep.Key(KeyEvent.KEYCODE_TAB))), width = width, style = KeyStyle.FUNCTION)

    private fun abcKey() = Key(label = "ABC", output = KeyOutput.ShowTab(Tabs.ALPHA), style = KeyStyle.FUNCTION)

    /** A programmable key; invalid macros (which the importer rejects) are typed as text. */
    private fun programmable(macro: String, width: Float) = macroKey(macro, null, width, KeyStyle.FUNCTION)

    private fun macroKey(macro: String, label: String?, width: Float, style: KeyStyle): Key {
        val steps = runCatching { Macro.parse(macro) }.getOrElse { listOf(MacroStep.Text(macro)) }
        // {CTRL}, {ALT} or {WIN} alone: a sticky modifier key.
        (steps.singleOrNull() as? MacroStep.Key)?.takeIf { it.meta == 0 }?.let { ModifierKey.byKeyCode(it.keyCode) }?.let { modifier ->
            return modifierKey(modifier, width, style).copy(label = label ?: modifier.label)
        }
        val icon = if (label == null) Macro.icon(steps) else null
        val output = (steps.singleOrNull() as? MacroStep.Text)?.let { KeyOutput.Text(it.text) } ?: KeyOutput.Macro(steps)
        return Key(label = label ?: Macro.label(steps).takeIf { icon == null }, icon = icon, output = output, width = width, style = style)
    }

    // endregion
}

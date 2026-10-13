package io.github.pieewiee.industrialkeyboard.layout

import android.view.KeyEvent
import io.github.pieewiee.industrialkeyboard.config.Alphabet
import io.github.pieewiee.industrialkeyboard.config.CustomLayout
import io.github.pieewiee.industrialkeyboard.config.KeyStyle
import io.github.pieewiee.industrialkeyboard.config.KeyboardConfig
import io.github.pieewiee.industrialkeyboard.config.Navigation
import io.github.pieewiee.industrialkeyboard.config.NumericOrder
import io.github.pieewiee.industrialkeyboard.config.ProgrammableKey
import io.github.pieewiee.industrialkeyboard.config.Tabs

/** Letter layout variants for special text fields. */
enum class AlphaVariant { TEXT, EMAIL, URL }

/** Numeric pad variants, chosen by the field's input type. */
enum class NumericVariant { NUMBER, PHONE, PIN, DATE, TIME, DATETIME }

/** Builds the key layouts from the configuration. */
object Layouts {

    private const val DIGITS = "1234567890"

    private val ROWS: Map<Alphabet, List<String>> = mapOf(
        Alphabet.QWERTY to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        Alphabet.QWERTZ to listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnm"),
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

    private val SYMBOLS = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/"),
        listOf("*", "\"", "'", ":", ";", "!", "?"),
    )

    private val SYMBOLS_2 = listOf(
        listOf("~", "`", "|", "\\", "^", "_", "=", "{", "}", "°"),
        listOf("€", "£", "¥", "¢", "§", "<", ">", "[", "]", "©"),
        listOf("•", "×", "÷", "¶", "®", "™", "✓"),
    )

    fun alpha(config: KeyboardConfig, variant: AlphaVariant = AlphaVariant.TEXT): KeyboardLayout {
        val alphabet = config.layout.alphabet
        val letters = ROWS.getValue(alphabet)
        val hints = config.appearance.keyHints && !config.appearance.numberRow
        val rows = buildList {
            if (config.appearance.functionRow) {
                // Twelve keys in the width of the letter row, so the letters keep their size.
                val width = letters[0].length / 12f
                add(KeyRow((1..12).map { fKey(it, shifted = false, width = width, style = KeyStyle.FUNCTION) }))
            }
            if (config.appearance.numberRow) add(KeyRow(DIGITS.map { textKey(it.toString()) }))
            add(KeyRow(letters[0].mapIndexed { i, c -> letterKey(c, digit = if (hints && i < DIGITS.length) DIGITS[i] else null) }))
            add(KeyRow(letters[1].map { letterKey(it) }))
            add(KeyRow(listOf(shiftKey()) + letters[2].map { letterKey(it) } + backspaceKey()))
            add(bottomRow(config, variant, Tabs.ALPHA, alphabet.label))
        }
        return KeyboardLayout(Tabs.ALPHA, rows, alphabet.label)
    }

    /** Symbols in two pages ([page] 1 or 2). */
    fun symbols(config: KeyboardConfig, page: Int = 1, variant: AlphaVariant = AlphaVariant.TEXT): KeyboardLayout {
        val source = if (page == 2) SYMBOLS_2 else SYMBOLS
        val pageKey = Key(label = if (page == 2) "2/2" else "1/2", output = KeyOutput.SymbolsPage, width = 1.5f, style = KeyStyle.FUNCTION, flex = true)
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
    fun function(shifted: Boolean = false): KeyboardLayout {
        fun nav(label: String?, code: Int, icon: KeyIcon? = null, repeat: Boolean = false) =
            Key(label = label, icon = icon, output = KeyOutput.Macro(listOf(MacroStep.Key(code))), style = KeyStyle.FUNCTION, repeat = repeat)
        val fWidth = 7f / 6f
        val rows = listOf(
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

    private fun bottomRow(config: KeyboardConfig, variant: AlphaVariant, page: String, spaceLabel: String): KeyRow {
        val classic = config.layout.navigation == Navigation.KEYS
        val keys = buildList {
            if (classic) {
                val toSymbols = page == Tabs.ALPHA
                add(Key(label = if (toSymbols) "?123" else "ABC", output = KeyOutput.ShowTab(if (toSymbols) Tabs.SYMBOLS else Tabs.ALPHA), width = 1.5f, style = KeyStyle.FUNCTION))
            }
            add(programmable(config.programmable(ProgrammableKey.ALPHA_P1), width = if (classic) 1f else 1.5f))
            add(
                when (variant) {
                    AlphaVariant.EMAIL -> textKey("@", listOf(",", "-", "_", "+"))
                    AlphaVariant.URL -> textKey("/", listOf(",", ":", "-", "_", "?", "="))
                    AlphaVariant.TEXT -> textKey(",", listOf(";", ":", "_", "-"))
                },
            )
            add(Key(label = spaceLabel, output = KeyOutput.Space, width = 3f, flex = true))
            add(
                if (variant == AlphaVariant.URL) {
                    textKey(".", listOf(".com", ".de", ".org", ".net", "www."))
                } else {
                    textKey(".", listOf(",", "?", "!", "'", "\"", ":", ";", "-"))
                },
            )
            if (config.scanner.scanKey) add(Key(icon = KeyIcon.SCAN, output = KeyOutput.Macro(listOf(MacroStep.Command(KeyCommand.SCAN))), width = 1.25f, style = KeyStyle.FUNCTION))
            add(enterKey(width = 2f))
        }
        return KeyRow(keys)
    }

    private fun letterKey(c: Char, digit: Char? = null): Key {
        if (!c.isLetter()) return textKey(c.toString())
        val alternatives = LETTER_ALTERNATIVES[c].orEmpty().map { it.toString() }
        return Key(
            label = c.toString(),
            output = KeyOutput.Text(c.toString()),
            hint = digit?.toString(),
            popup = listOfNotNull(digit?.toString()) + alternatives,
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
        val icon = if (label == null) Macro.icon(steps) else null
        val output = (steps.singleOrNull() as? MacroStep.Text)?.let { KeyOutput.Text(it.text) } ?: KeyOutput.Macro(steps)
        return Key(label = label ?: Macro.label(steps).takeIf { icon == null }, icon = icon, output = output, width = width, style = style)
    }

    // endregion
}

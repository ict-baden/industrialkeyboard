package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.DataSettings
import de.ictbaden.industrialkeyboard.config.Replacement
import de.ictbaden.industrialkeyboard.config.RuleAction
import de.ictbaden.industrialkeyboard.config.ScanRule
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.config.TextCase
import java.util.Locale

/**
 * Turns a scanned text into what the keyboard types (`scanner.data`), like the basic and advanced
 * data formatting of scanner apps: rules by type, length and content, trimming, cutting,
 * replacing, case, prefix and suffix.
 */
object ScanFormatter {

    /** What to type: [prefix] and [suffix] are macros; [suffix] `null` = the old `input.suffix`. */
    data class Output(val text: String, val prefix: String, val suffix: String?)

    private const val GS = '\u001D'

    /** `null` = drop the code (a rule says so, or no rule matches and `unmatched` is `drop`). */
    fun format(raw: String, symbology: Symbology?, data: DataSettings): Output? {
        var text = raw
        data.gsReplacement?.let { text = text.replace(GS.toString(), it) }
        if (data.trim) text = trim(text)
        val rule = data.rules.firstOrNull { matches(it, text, symbology) }
        if (rule == null && data.rules.isNotEmpty() && data.unmatched == RuleAction.DROP) return null
        if (rule?.action == RuleAction.DROP) return null
        val stripLeading = rule?.stripLeading ?: data.stripLeading
        val stripTrailing = rule?.stripTrailing ?: data.stripTrailing
        text = text.drop(stripLeading).dropLast(stripTrailing)
        if (rule?.removeSpaces ?: data.removeSpaces) text = text.replace(" ", "")
        for (r in rule?.replace ?: data.replace) text = replace(text, r)
        text = when (rule?.case ?: data.case) {
            TextCase.UPPER -> text.uppercase(Locale.ROOT)
            TextCase.LOWER -> text.lowercase(Locale.ROOT)
            TextCase.NONE -> text
        }
        return Output(text, rule?.prefix ?: data.prefix, rule?.suffix ?: data.suffix)
    }

    /** True when [text] would be typed (the camera keeps looking otherwise). */
    fun accepts(raw: String, symbology: Symbology?, data: DataSettings) = format(raw, symbology, data) != null

    fun matches(rule: ScanRule, text: String, symbology: Symbology?): Boolean {
        if (rule.symbologies.isNotEmpty() && symbology !in rule.symbologies) return false
        if (rule.minLength > 0 && text.length < rule.minLength) return false
        if (rule.maxLength > 0 && text.length > rule.maxLength) return false
        if (rule.startsWith.isNotEmpty() && !text.startsWith(rule.startsWith)) return false
        if (rule.pattern.isNotEmpty() && !Regex(rule.pattern).matches(text)) return false
        return true
    }

    private fun replace(text: String, r: Replacement): String =
        if (r.regex) Regex(r.find).replace(text, r.with) else text.replace(r.find, r.with)

    /** Spaces and control characters at both ends, but not GS: a GS1 field may end with it. */
    private fun trim(text: String): String = text.trim { it != GS && (it.isWhitespace() || it.isISOControl()) }
}

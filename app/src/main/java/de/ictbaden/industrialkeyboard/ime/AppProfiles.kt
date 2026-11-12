package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import de.ictbaden.industrialkeyboard.config.AppProfile
import de.ictbaden.industrialkeyboard.config.EffectiveConfig
import de.ictbaden.industrialkeyboard.config.FieldKind
import de.ictbaden.industrialkeyboard.config.KeyboardConfig

/**
 * App profiles (`appProfiles`): picks the profile for the focused field and applies it to the
 * stored configuration. Pure; runs when a field starts or the configuration changes, never per key.
 *
 * Reads only the app's own metadata of the field – package name, input type and hint – and keeps
 * none of it; never the text in the field.
 */
object AppProfiles {

    /** The matching profile and its position in `appProfiles` (0-based). */
    data class Hit(val index: Int, val profile: AppProfile)

    /** Kind of the field for `fields`; `null` for password fields, which no `fields` rule matches. */
    fun kind(inputType: Int): FieldKind? {
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        if (inputClass == InputType.TYPE_NULL) return FieldKind.NONE
        if (FieldPolicy.isPassword(inputType)) return null
        return when (inputClass) {
            InputType.TYPE_CLASS_NUMBER -> FieldKind.NUMBER
            InputType.TYPE_CLASS_PHONE -> FieldKind.PHONE
            InputType.TYPE_CLASS_DATETIME -> FieldKind.DATETIME
            InputType.TYPE_CLASS_TEXT -> when (inputType and InputType.TYPE_MASK_VARIATION) {
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> FieldKind.EMAIL
                InputType.TYPE_TEXT_VARIATION_URI -> FieldKind.URL
                else -> FieldKind.TEXT
            }
            else -> null
        }
    }

    /**
     * The first profile whose conditions all match (AND; inside one list any entry, OR), or `null`.
     * A profile with `apps` needs a package name, one with `hints` a non-blank hint.
     */
    fun match(profiles: List<AppProfile>, packageName: String?, inputType: Int, hint: CharSequence?): Hit? {
        if (profiles.isEmpty()) return null
        val kind by lazy(LazyThreadSafetyMode.NONE) { kind(inputType) }
        val hintText by lazy(LazyThreadSafetyMode.NONE) { hint?.toString()?.trim().orEmpty() }
        profiles.forEachIndexed { i, p ->
            val apps = p.apps.isEmpty() || (packageName != null && p.apps.any { Glob.matches(it, packageName) })
            val fields = apps && (p.fields.isEmpty() || kind in p.fields)
            val hints = fields && (p.hints.isEmpty() || (hintText.isNotEmpty() && p.hints.any { Glob.matches(it, hintText, ignoreCase = true) }))
            if (hints) return Hit(i, p)
        }
        return null
    }

    /**
     * What the log line depends on: package and profile. The keyboard logs when it changes, so the
     * first field of each app shows its package – also while no profile matches (or none exists).
     */
    fun logKey(packageName: String?, hit: Hit?): Pair<String?, Int> = packageName to (hit?.index ?: -1)

    /** The log line for [logKey]: package name and profile name, never the hint (the app's text). */
    fun logLine(packageName: String?, hit: Hit?): String {
        val pkg = packageName ?: "?"
        return if (hit != null) "App profile ${hit.index + 1} \"${hit.profile.name}\" for $pkg" else "No app profile for $pkg"
    }

    /** The configuration with the profile's overrides; [config] itself (same instance) for `null`. */
    fun apply(config: KeyboardConfig, profile: AppProfile?): KeyboardConfig {
        profile ?: return config
        return config.copy(
            tabs = profile.page?.let { config.tabs.copy(preferred = it) } ?: config.tabs,
            layout = config.layout.copy(
                alphabet = profile.alphabet ?: config.layout.alphabet,
                numericOrder = profile.numericOrder ?: config.layout.numericOrder,
                topBar = profile.topBar ?: config.layout.topBar,
            ),
            appearance = profile.emojiKey?.let { config.appearance.copy(emojiKey = it) } ?: config.appearance,
            typing = config.typing.copy(
                suggestions = profile.suggestions ?: config.typing.suggestions,
                autocorrect = profile.autocorrect ?: config.typing.autocorrect,
            ),
            scanner = profile.scanSuffix?.let { config.scanner.copy(data = config.scanner.data.copy(suffix = it)) } ?: config.scanner,
        )
    }

    /** The profile layer of [EffectiveConfig]. */
    fun layer(profile: AppProfile?): EffectiveConfig.Layer =
        if (profile == null) EffectiveConfig.NONE else EffectiveConfig.Layer { apply(it, profile) }
}

/**
 * Wildcards for package names and hints: `*` any characters (dots too), `?` exactly one, always
 * against the whole text. No regular expressions, so a pattern from a configuration cannot make
 * matching slow (ReDoS): two pointers with backtracking to the last star, at most
 * pattern × text steps (100 × the package name or hint).
 */
internal object Glob {
    fun matches(pattern: String, text: String, ignoreCase: Boolean = false): Boolean {
        var p = 0
        var t = 0
        var star = -1
        var mark = 0
        while (t < text.length) {
            when {
                p < pattern.length && pattern[p] == '*' -> {
                    star = p++
                    mark = t
                }
                p < pattern.length && (pattern[p] == '?' || pattern[p].equals(text[t], ignoreCase)) -> {
                    p++
                    t++
                }
                star >= 0 -> {
                    p = star + 1
                    t = ++mark
                }
                else -> return false
            }
        }
        while (p < pattern.length && pattern[p] == '*') p++
        return p == pattern.length
    }
}

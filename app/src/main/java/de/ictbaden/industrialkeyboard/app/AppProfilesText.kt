package de.ictbaden.industrialkeyboard.app

import android.content.Context
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.AppProfile

/**
 * Read-only description of the app profiles for the settings. The JSON names and values stay
 * untranslated (like validation messages), so the text matches the configuration file.
 */
object AppProfilesText {

    fun describe(context: Context, profiles: List<AppProfile>): String = buildString {
        profiles.forEachIndexed { i, p ->
            append(i + 1).append(". ").append(p.name.ifEmpty { context.getString(R.string.app_profile_unnamed, i + 1) }).append('\n')
            append(conditions(p).joinToString(SEPARATOR)).append('\n')
            append(overrides(p).joinToString(SEPARATOR)).append("\n\n")
        }
        append(context.getString(R.string.app_profiles_first_wins))
    }

    internal fun conditions(p: AppProfile): List<String> = listOfNotNull(
        p.apps.takeIf { it.isNotEmpty() }?.let { "apps: ${it.joinToString(", ")}" },
        p.fields.takeIf { it.isNotEmpty() }?.let { f -> "fields: ${f.sortedBy { it.ordinal }.joinToString(", ") { it.key }}" },
        p.hints.takeIf { it.isNotEmpty() }?.let { "hints: ${it.joinToString(", ")}" },
    )

    internal fun overrides(p: AppProfile): List<String> = listOfNotNull(
        p.page?.let { "page: $it" },
        p.alphabet?.let { "alphabet: ${it.key}" },
        p.numericOrder?.let { "numericOrder: ${it.key}" },
        p.topBar?.let { "topBar: ${it.key}" },
        p.emojiKey?.let { "emojiKey: $it" },
        p.suggestions?.let { "suggestions: $it" },
        p.autocorrect?.let { "autocorrect: $it" },
        p.scanSuffix?.let { "scanSuffix: ${it.ifEmpty { "\"\"" }}" },
    )

    private const val SEPARATOR = " · "
}

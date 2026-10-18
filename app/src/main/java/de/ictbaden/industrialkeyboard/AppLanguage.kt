package de.ictbaden.industrialkeyboard

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import de.ictbaden.industrialkeyboard.config.Languages

/**
 * Applies the configured `language` (richtlinien/13-sprachen.md): `auto` follows the device,
 * otherwise the configured language. The configuration is the source of truth, so it is applied on
 * every start and on every change.
 */
object AppLanguage {

    /** Changes the app language; open settings screens are recreated only when it actually changes. */
    fun apply(language: String) {
        val wanted = if (language == Languages.AUTO) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(language)
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
            AppCompatDelegate.setApplicationLocales(wanted)
        }
    }

    /**
     * Context for texts drawn by the input method. From Android 13 the system applies the app
     * language to every component; before, AppCompat only switches activities, so the keyboard
     * service asks for a localized context.
     */
    fun localized(context: Context): Context {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || locales.isEmpty) return context
        // The scan pad asks on every frame while scanning: reuse the last one.
        cached?.let { (base, tags, localized) -> if (base === context && tags == locales.toLanguageTags()) return localized }
        val config = Configuration(context.resources.configuration).apply { setLocale(locales[0]) }
        return context.createConfigurationContext(config).also { cached = Triple(context, locales.toLanguageTags(), it) }
    }

    @Volatile
    private var cached: Triple<Context, String, Context>? = null

    /** Language names in their own language, so everyone finds theirs. */
    val NAMES = mapOf("en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "zh-Hans" to "简体中文")
}

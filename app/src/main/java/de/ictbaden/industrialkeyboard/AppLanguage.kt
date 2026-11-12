package de.ictbaden.industrialkeyboard

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
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
    fun apply(context: Context, language: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Directly: AppCompat forwards to the LocaleManager only once an activity exists, but the
            // keyboard service and the MDM often change the language without one.
            val manager = context.getSystemService(LocaleManager::class.java) ?: return
            val wanted = if (language == Languages.AUTO) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(language)
            if (manager.applicationLocales.toLanguageTags() != wanted.toLanguageTags()) manager.applicationLocales = wanted
            return
        }
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
        // First: from Android 13 AppCompat asks the LocaleManager (a call to the system) – the render
        // path uses this on every keystroke.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) return context
        // The scan pad asks on every frame while scanning: reuse the last one.
        cached?.let { (base, tags, localized) -> if (base === context && tags == locales.toLanguageTags()) return localized }
        val config = Configuration(context.resources.configuration).apply { setLocale(locales[0]) }
        return context.createConfigurationContext(config).also { cached = Triple(context, locales.toLanguageTags(), it) }
    }

    @Volatile
    private var cached: Triple<Context, String, Context>? = null

    /** Translated by machine, marked as such until native speakers have proofread them. */
    val MACHINE_TRANSLATED = setOf("es", "fr", "zh-Hans")

    /** Language names in their own language, so everyone finds theirs. */
    val NAMES = mapOf("en" to "English", "de" to "Deutsch", "es" to "Español", "fr" to "Français", "zh-Hans" to "简体中文")
}

package io.github.pieewiee.industrialkeyboard.config

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.edit
import java.io.File

/**
 * Applies configuration pushed by an EMM: Android managed configurations
 * (`res/xml/app_restrictions.xml`) and config files in the app's external files directory or
 * Zebra's persistent `/enterprise/usr`. Each source is applied once per change.
 */
object Provisioning {
    const val CONFIG_FILE_NAME = "industrialkeyboard.json"
    private const val TAG = "Provisioning"
    private const val PREFS = "provisioning"
    private const val KEY_LAST_ERROR = "last_error"

    fun applyAll(context: Context) {
        applyManagedConfig(context)
        configFiles(context).forEach { applyConfigFile(context, it) }
    }

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    fun configFiles(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let { File(it, CONFIG_FILE_NAME) },
        File("/enterprise/usr", CONFIG_FILE_NAME),
    )

    private fun applyManagedConfig(context: Context) {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions ?: return
        val fingerprint = fingerprint(restrictions)
        if (fingerprint == prefs(context).getString("managed", null)) return
        runSource(context, "Managed configuration") {
            val store = ConfigStore.get(context)
            val configJson = restrictions.getString("config_json")?.trim()
            if (!configJson.isNullOrEmpty()) {
                store.import(ConfigJson.parse(configJson))
            } else if (!restrictions.isEmpty) {
                store.update { applySimpleKeys(it, restrictions) }
            }
            restrictions.getString("admin_password")?.takeIf { it.isNotEmpty() }?.let { password ->
                if (!PasswordHasher.verify(password, store.config.adminPasswordHash)) {
                    store.update { it.copy(adminPasswordHash = PasswordHasher.hash(password)) }
                }
            }
        }
        prefs(context).edit { putString("managed", fingerprint) }
    }

    /** The single options of the managed configuration schema. */
    private fun applySimpleKeys(config: KeyboardConfig, values: Bundle): KeyboardConfig {
        fun <T> choice(key: String, entries: Array<T>, current: T): T where T : Enum<T>, T : Keyed =
            values.getString(key)?.let { v -> entries.firstOrNull { it.key == v } } ?: current
        fun bool(key: String, current: Boolean) = if (values.containsKey(key)) values.getBoolean(key, current) else current
        return config.copy(
            appearance = config.appearance.copy(theme = choice("theme", ThemeMode.entries.toTypedArray(), config.appearance.theme)),
            layout = config.layout.copy(
                alphabet = choice("alphabet", Alphabet.entries.toTypedArray(), config.layout.alphabet),
                numericOrder = choice("numeric_order", NumericOrder.entries.toTypedArray(), config.layout.numericOrder),
            ),
            tabs = config.tabs.copy(
                scan = bool("scan_tab", config.tabs.scan),
                function = bool("function_tab", config.tabs.function),
            ),
            feedback = config.feedback.copy(
                sound = bool("sound", config.feedback.sound),
                vibrate = bool("vibrate", config.feedback.vibrate),
            ),
        )
    }

    private fun applyConfigFile(context: Context, file: File) {
        if (!file.canRead()) return
        val fingerprint = "${file.lastModified()}:${file.length()}"
        val key = "file:${file.path}"
        if (fingerprint == prefs(context).getString(key, null)) return
        runSource(context, file.path) {
            ConfigStore.get(context).import(ConfigJson.parse(file.readText()))
        }
        prefs(context).edit { putString(key, fingerprint) }
    }

    /** Remembers the outcome of a configuration source for the settings screen (`null` = applied). */
    fun report(context: Context, source: String, error: String?) {
        prefs(context).edit { if (error == null) remove(KEY_LAST_ERROR) else putString(KEY_LAST_ERROR, "$source: $error") }
    }

    private fun runSource(context: Context, source: String, block: () -> Unit) {
        try {
            block()
            report(context, source, null)
            Log.i(TAG, "Applied configuration from $source")
        } catch (e: Exception) {
            Log.e(TAG, "Configuration from $source rejected", e)
            report(context, source, e.message)
        }
    }

    private fun fingerprint(bundle: Bundle): String =
        bundle.keySet().sorted().joinToString("|") { key ->
            @Suppress("DEPRECATION")
            "$key=${bundle.get(key)}"
        }.hashCode().toString()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

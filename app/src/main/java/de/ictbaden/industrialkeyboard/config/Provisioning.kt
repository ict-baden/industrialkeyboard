package de.ictbaden.industrialkeyboard.config

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.RestrictionsManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Applies configuration pushed by an EMM: Android managed configurations
 * (`res/xml/app_restrictions.xml`) and config files in the app's external files directory or
 * Zebra's persistent `/enterprise/usr`. Each source is applied once per change.
 */
object Provisioning {
    const val CONFIG_FILE_NAME = "industrialkeyboard.json"

    /** Top-level key of a configuration file in the app folder with the current admin password. */
    const val FILE_PASSWORD_KEY = "password"
    private const val ENTERPRISE_DIR = "/enterprise/usr"
    private const val TAG = "Provisioning"
    private const val PREFS = "provisioning"
    private const val KEY_LAST_ERROR = "last_error"

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "provisioning").apply { isDaemon = true } }
    private val queued = AtomicBoolean(false)

    /**
     * Checks all sources on a background thread: the keyboard calls this on every focus change and
     * must not wait for file system and system services. Calls while a check is queued are merged.
     */
    fun applyAllAsync(context: Context) {
        val app = context.applicationContext
        if (!queued.compareAndSet(false, true)) return
        worker.execute {
            queued.set(false)
            applyAll(app)
        }
    }

    @VisibleForTesting
    @Synchronized
    fun applyAll(context: Context) {
        applyManagedConfig(context)
        configFiles(context).forEach { applyConfigFile(context, it) }
    }

    /** Makes every source apply again on the next check (after a migration of the stored configuration). */
    fun forgetApplied(context: Context) {
        prefs(context).edit { prefs(context).all.keys.filter { it == "managed" || it.startsWith("file:") }.forEach(::remove) }
    }

    /**
     * True when an EMM manages this app: it has a managed configuration, or the device or work
     * profile has an owner (Android Enterprise). Only then may other apps configure it without the
     * admin password, see `ApiReceiver`.
     */
    fun isManaged(context: Context): Boolean {
        val restrictions = context.getSystemService(RestrictionsManager::class.java)?.applicationRestrictions
        if (restrictions != null && !restrictions.isEmpty) return true
        val dpm = context.getSystemService(DevicePolicyManager::class.java) ?: return false
        return dpm.activeAdmins.orEmpty().any { dpm.isDeviceOwnerApp(it.packageName) || dpm.isProfileOwnerApp(it.packageName) }
    }

    fun lastError(context: Context): String? = prefs(context).getString(KEY_LAST_ERROR, null)

    fun configFiles(context: Context): List<File> = listOfNotNull(
        context.getExternalFilesDir(null)?.let { File(it, CONFIG_FILE_NAME) },
        File(ENTERPRISE_DIR, CONFIG_FILE_NAME),
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
            language = values.getString("language")?.let { v -> (listOf(Languages.AUTO) + Languages.SUPPORTED).firstOrNull { it == v } } ?: config.language,
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
            val text = file.readText()
            val config = ConfigJson.parse(text)
            checkFileAllowed(context, file, text)
            ConfigStore.get(context).import(config)
        }
        // Also after a refusal: a wrong password must not be counted again on every focus change.
        prefs(context).edit { putString(key, fingerprint) }
    }

    /**
     * `/enterprise/usr` is written by Zebra's MX (StageNow, EMM) only, and on a managed device the
     * EMM controls the app folder. Otherwise the app folder is reachable over USB and, before
     * Android 11, by any app with storage permission: then the file must carry the current admin
     * password, and without one it is accepted on Android 11+ only.
     */
    private fun checkFileAllowed(context: Context, file: File, text: String) {
        if (file.parent == ENTERPRISE_DIR || isManaged(context)) return
        val hash = ConfigStore.get(context).config.adminPasswordHash
        if (hash == null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                throw ConfigException("Refused: before Android 11 other apps can write this folder. Set an admin password and add it as \"$FILE_PASSWORD_KEY\", or use /enterprise/usr or the managed configuration.")
            }
            return
        }
        val password = runCatching { JSONObject(text).optString(FILE_PASSWORD_KEY, "") }.getOrDefault("")
        when (PasswordGuard.remote(context).check(password, hash)) {
            PasswordGuard.Result.OK -> Unit
            PasswordGuard.Result.WRONG -> throw ConfigException("Refused: \"$FILE_PASSWORD_KEY\" with the current admin password is missing or wrong")
            PasswordGuard.Result.LOCKED -> throw ConfigException("Refused: locked after too many wrong passwords, change the file again in a few minutes")
        }
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

package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Process-wide holder of the persisted [KeyboardConfig] (`files/config.json`) with change listeners. */
class ConfigStore @VisibleForTesting internal constructor(
    private val file: File,
    private val onLoadError: (String) -> Unit = {},
) {

    @Volatile
    var config: KeyboardConfig = load()
        private set

    private val listeners = CopyOnWriteArrayList<(KeyboardConfig) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (KeyboardConfig) -> Unit) = listeners.add(listener)

    fun removeListener(listener: (KeyboardConfig) -> Unit) = listeners.remove(listener)

    @Synchronized
    fun update(transform: (KeyboardConfig) -> KeyboardConfig): KeyboardConfig {
        val updated = transform(config)
        if (updated == config) return updated
        save(updated)
        config = updated
        main.post { listeners.forEach { it(updated) } }
        return updated
    }

    /** Replaces the configuration with [imported], keeping the admin password when the import sets none. */
    fun import(imported: KeyboardConfig): KeyboardConfig = update { current ->
        imported.copy(adminPasswordHash = imported.adminPasswordHash ?: current.adminPasswordHash)
    }

    /**
     * Reads the stored configuration. If it cannot be read, the file is kept as
     * `config.json.invalid-<time>` and the defaults are used – but with the admin password, so a
     * broken file never unlocks the settings.
     */
    private fun load(): KeyboardConfig {
        if (!file.exists()) return KeyboardConfig()
        val text = try {
            file.readText()
        } catch (e: IOException) {
            return recover(null, e)
        }
        return try {
            ConfigJson.parse(text)
        } catch (e: ConfigException) {
            recover(text, e)
        }
    }

    private fun recover(text: String?, error: Exception): KeyboardConfig {
        val backup = File(file.parentFile, "${file.name}.invalid-${System.currentTimeMillis()}")
        val saved = runCatching { file.copyTo(backup, overwrite = true) }.isSuccess
        Log.e(TAG, "Stored configuration is invalid, using defaults; kept as ${backup.name.takeIf { saved }}: ${error.message}")
        onLoadError("Stored configuration could not be read (${error.message}); reset to defaults, admin password kept${if (saved) ", copy in ${backup.name}" else ""}")
        val recovered = KeyboardConfig(adminPasswordHash = text?.let(::findHash))
        // Saved right away, so the next start does not recover (and copy) again.
        runCatching { save(recovered) }.onFailure { Log.e(TAG, "Could not save the recovered configuration", it) }
        return recovered
    }

    /** The admin password hash of a broken file; org.json writes `/` as `\/`. */
    private fun findHash(text: String): String? {
        val raw = HASH.find(text)?.groupValues?.get(1) ?: return null
        val hash = runCatching { JSONTokener("\"$raw\"").nextValue() as String }.getOrNull() ?: return null
        return hash.takeIf(PasswordHasher::isHash)
    }

    private fun save(config: KeyboardConfig) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(ConfigJson.write(config))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save the configuration" }
        }
    }

    companion object {
        private const val TAG = "ConfigStore"

        /** Found even in a truncated or otherwise broken file. */
        private val HASH = Regex("\"adminPasswordHash\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)+)\"")

        @Volatile
        private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore = instance ?: synchronized(this) {
            val app = context.applicationContext
            instance ?: ConfigStore(File(app.filesDir, "config.json")) { Provisioning.report(app, "Stored configuration", it) }.also { instance = it }
        }

        /** Forgets the cached store so the next [get] reads the file again (tests). */
        @VisibleForTesting
        internal fun reset() {
            instance = null
        }
    }
}

package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Process-wide holder of the persisted [KeyboardConfig] (`files/config.json`) with change listeners. */
class ConfigStore @VisibleForTesting internal constructor(
    private val file: File,
    private val onLoadError: (String) -> Unit = {},
) {

    /**
     * The stored file exists but cannot be read: [config] holds the defaults with an unknown admin
     * password, and changes stay in memory until the file can be read or a new password is set.
     * Declared before [config], which sets it while loading.
     */
    @Volatile
    var unreadable = false
        private set

    @Volatile
    var config: KeyboardConfig = load()
        private set

    private val listeners = CopyOnWriteArrayList<(KeyboardConfig) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (KeyboardConfig) -> Unit) = listeners.add(listener)

    fun removeListener(listener: (KeyboardConfig) -> Unit) = listeners.remove(listener)

    @Synchronized
    fun update(transform: (KeyboardConfig) -> KeyboardConfig): KeyboardConfig {
        if (unreadable) readAgain()
        val updated = transform(config)
        if (updated == config) return updated
        // Saving over an unreadable file would replace its configuration and password for good with
        // the defaults and a password nobody knows. Only a new admin password is known again.
        if (!unreadable || (updated.adminPasswordHash != null && updated.adminPasswordHash != config.adminPasswordHash)) {
            save(updated)
            unreadable = false
        }
        config = updated
        main.post { listeners.forEach { it(updated) } }
        return updated
    }

    /** Replaces the configuration with [imported], keeping the admin password when the import sets none. */
    fun import(imported: KeyboardConfig): KeyboardConfig = update { current ->
        imported.copy(adminPasswordHash = imported.adminPasswordHash ?: current.adminPasswordHash)
    }

    /**
     * Reads the stored configuration. If it is broken, the file is kept as
     * `config.json.invalid-<time>` and the defaults are used – but with the admin password, so a
     * broken file never unlocks the settings.
     */
    private fun load(): KeyboardConfig {
        if (!file.exists()) return KeyboardConfig()
        val text = try {
            read()
        } catch (e: IOException) {
            return unreadable(e)
        }
        return parse(text)
    }

    /** Empty (or zeros) only after a broken write: its admin password is as unknown as after an I/O error. */
    private fun read(): String = file.readText().also { text ->
        if (text.all { it == '\u0000' || it.isWhitespace() }) throw IOException("the file is empty")
    }

    private fun parse(text: String): KeyboardConfig = try {
        ConfigJson.parse(text)
    } catch (e: ConfigException) {
        recover(text, e)
    }

    /**
     * The file exists but cannot be read (I/O error, empty), so its admin password is unknown: the
     * defaults are used with a password nobody knows, which keeps the settings and the remote
     * channels locked instead of open. The file stays as it is (see [update]) and is read again on
     * the next change and the next start. An admin password from the EMM (`admin_password`, or
     * `adminPassword` in `/enterprise/usr` – both applied again meanwhile) replaces the unknown one.
     */
    private fun unreadable(error: IOException): KeyboardConfig {
        Log.e(TAG, "Stored configuration cannot be read, using defaults with the settings locked: ${error.message}")
        onLoadError("Stored configuration could not be read (${error.message}); defaults until it can be read again, settings locked")
        unreadable = true
        return KeyboardConfig(adminPasswordHash = PasswordHasher.unknown())
    }

    /** Before a change while [unreadable]: maybe the read error is gone. */
    private fun readAgain() {
        val text = try {
            read()
        } catch (_: IOException) {
            return
        }
        val loaded = parse(text)
        unreadable = false
        config = loaded
        main.post { listeners.forEach { it(loaded) } }
    }

    private fun recover(text: String, error: Exception): KeyboardConfig {
        val backup = File(file.parentFile, "${file.name}.invalid-${System.currentTimeMillis()}")
        val saved = runCatching { file.copyTo(backup, overwrite = true) }.isSuccess
        Log.e(TAG, "Stored configuration is invalid, using defaults; kept as ${backup.name.takeIf { saved }}: ${error.message}")
        onLoadError("Stored configuration could not be read (${error.message}); reset to defaults, admin password kept${if (saved) ", copy in ${backup.name}" else ""}")
        val recovered = KeyboardConfig(adminPasswordHash = findHash(text))
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
        // On the storage before the rename, so a power loss leaves the old or the new file, not an empty one.
        FileOutputStream(tmp).use {
            it.write(ConfigJson.write(config).toByteArray())
            it.fd.sync()
        }
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

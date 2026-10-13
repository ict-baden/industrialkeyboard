package io.github.pieewiee.industrialkeyboard.config

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.VisibleForTesting
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Process-wide holder of the persisted [KeyboardConfig] (`files/config.json`) with change listeners. */
class ConfigStore private constructor(private val file: File) {

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
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(ConfigJson.write(updated))
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "Could not save the configuration" }
        }
        config = updated
        main.post { listeners.forEach { it(updated) } }
        return updated
    }

    /** Replaces the configuration with [imported], keeping the admin password when the import sets none. */
    fun import(imported: KeyboardConfig): KeyboardConfig = update { current ->
        imported.copy(adminPasswordHash = imported.adminPasswordHash ?: current.adminPasswordHash)
    }

    private fun load(): KeyboardConfig {
        if (!file.exists()) return KeyboardConfig()
        return try {
            ConfigJson.parse(file.readText())
        } catch (e: ConfigException) {
            Log.e(TAG, "Stored configuration is invalid, using defaults", e)
            KeyboardConfig()
        }
    }

    companion object {
        private const val TAG = "ConfigStore"

        @Volatile
        private var instance: ConfigStore? = null

        fun get(context: Context): ConfigStore = instance ?: synchronized(this) {
            instance ?: ConfigStore(File(context.applicationContext.filesDir, "config.json")).also { instance = it }
        }

        /** Forgets the cached store so the next [get] reads the file again (tests). */
        @VisibleForTesting
        internal fun reset() {
            instance = null
        }
    }
}

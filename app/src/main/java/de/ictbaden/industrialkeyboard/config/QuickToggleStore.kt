package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The quick toggles switched on this device (`shared_prefs/quick_toggles.xml`): on/off per toggle,
 * nothing else – never typed text. Kept apart from the configuration, so an MDM push, an import or
 * `INSTALL_CONFIG` never removes them and export and QR code never contain them. Read from memory,
 * so reading costs nothing.
 */
class QuickToggleStore @VisibleForTesting internal constructor(private val prefs: SharedPreferences) {

    @Volatile
    var overrides: Map<QuickToggle, Boolean> = read()
        private set

    private val listeners = CopyOnWriteArrayList<(Map<QuickToggle, Boolean>) -> Unit>()
    private val main = Handler(Looper.getMainLooper())

    fun addListener(listener: (Map<QuickToggle, Boolean>) -> Unit) = listeners.add(listener)

    fun removeListener(listener: (Map<QuickToggle, Boolean>) -> Unit) = listeners.remove(listener)

    /** [value] for [t]; `null` = follow the configuration again. */
    fun set(t: QuickToggle, value: Boolean?) = replace(if (value == null) overrides - t else overrides + (t to value))

    @Synchronized
    fun replace(map: Map<QuickToggle, Boolean>) {
        if (map == overrides) return
        prefs.edit {
            QuickToggle.entries.forEach { t -> map[t]?.let { putBoolean(t.key, it) } ?: remove(t.key) }
        }
        changed(map.toMap())
    }

    @Synchronized
    fun clear() {
        prefs.edit { clear() }
        if (overrides.isNotEmpty()) changed(emptyMap())
    }

    private fun changed(map: Map<QuickToggle, Boolean>) {
        overrides = map
        main.post { listeners.forEach { it(map) } }
    }

    /** Unknown keys (a newer version, a typo) and values other than true/false are ignored. */
    private fun read(): Map<QuickToggle, Boolean> = prefs.all.mapNotNull { (key, value) ->
        val t = QuickToggle.entries.firstOrNull { it.key == key } ?: return@mapNotNull null
        (value as? Boolean)?.let { t to it }
    }.toMap()

    companion object {
        const val PREFS = "quick_toggles"

        @Volatile
        private var instance: QuickToggleStore? = null

        fun get(context: Context): QuickToggleStore = instance ?: synchronized(this) {
            instance ?: QuickToggleStore(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)).also { instance = it }
        }

        /** Forgets the cached store so the next [get] reads the preferences again (tests). */
        @VisibleForTesting
        internal fun reset() {
            instance = null
        }
    }
}

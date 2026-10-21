package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.util.Log
import androidx.core.content.edit

/** One-time changes of the stored configuration after an update. */
object Migrations {
    private const val PREFS = "migrations"
    private const val SCAN_INPUT_OFF = "2.1.0-scan-input-off"

    fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SCAN_INPUT_OFF, false)) return
        // 2.1.0: typing scan broadcasts is off by default. Up to 2.0 every saved configuration had
        // "enabled": true without anyone choosing it. Keep it only where the EMM's managed
        // configuration or a configuration file switches it on explicitly; nothing else changes.
        try {
            val store = ConfigStore.get(context)
            // Only the defaults are known now; the stored configuration is migrated once it can be read.
            if (store.unreadable) return
            if (!Provisioning.scanInputRequested(context)) {
                store.update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = false))) }
            }
            prefs.edit { putBoolean(SCAN_INPUT_OFF, true) }
        } catch (e: Exception) {
            // Never stop the app from starting; tried again on the next start.
            Log.e("Migrations", "Migration $SCAN_INPUT_OFF failed", e)
        }
    }
}

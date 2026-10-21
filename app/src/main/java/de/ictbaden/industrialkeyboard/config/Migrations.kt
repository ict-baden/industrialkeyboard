package de.ictbaden.industrialkeyboard.config

import android.content.Context
import androidx.core.content.edit

/** One-time changes of the stored configuration after an update. */
object Migrations {
    private const val PREFS = "migrations"
    private const val SCAN_INPUT_OFF = "2.1.0-scan-input-off"

    fun run(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SCAN_INPUT_OFF, false)) return
        // 2.1.0: typing scan broadcasts is off by default. Up to 2.0 every saved configuration had
        // "enabled": true without anyone choosing it, so switch it off and let the EMM's managed
        // configuration and configuration files apply again: where the admin enabled it, it comes back.
        ConfigStore.get(context).update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = false))) }
        Provisioning.forgetApplied(context)
        prefs.edit { putBoolean(SCAN_INPUT_OFF, true) }
    }
}

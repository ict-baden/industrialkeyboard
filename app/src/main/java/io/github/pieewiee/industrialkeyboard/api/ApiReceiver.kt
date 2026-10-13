package io.github.pieewiee.industrialkeyboard.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.pieewiee.industrialkeyboard.config.ConfigException
import io.github.pieewiee.industrialkeyboard.config.ConfigJson
import io.github.pieewiee.industrialkeyboard.config.ConfigStore
import io.github.pieewiee.industrialkeyboard.config.PasswordHasher
import io.github.pieewiee.industrialkeyboard.config.Provisioning

/**
 * Explicit broadcasts from the MDM or other apps:
 * `INSTALL_CONFIG` (StageNow Intent CSP, adb) and `SHOW_TAB` (switch the keyboard tab).
 */
class ApiReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            KeyboardApi.ACTION_SHOW_TAB -> intent.getStringExtra(KeyboardApi.EXTRA_TAB)?.trim()?.takeIf { it.isNotEmpty() }?.let(KeyboardBus::requestTab)
            KeyboardApi.ACTION_INSTALL_CONFIG -> installConfig(context, intent)
        }
    }

    private fun installConfig(context: Context, intent: Intent) {
        val store = ConfigStore.get(context)
        val hash = store.config.adminPasswordHash
        if (hash != null && !PasswordHasher.verify(intent.getStringExtra(KeyboardApi.EXTRA_PASSWORD).orEmpty(), hash)) {
            finish(context, KeyboardApi.RESULT_PASSWORD, "Admin password missing or wrong")
            return
        }
        val json = intent.getStringExtra(KeyboardApi.EXTRA_CONFIG)
        if (json.isNullOrBlank()) {
            finish(context, KeyboardApi.RESULT_INVALID, "Extra \"config\" is missing")
            return
        }
        try {
            store.import(ConfigJson.parse(json))
            finish(context, KeyboardApi.RESULT_OK, "Configuration installed")
        } catch (e: ConfigException) {
            finish(context, KeyboardApi.RESULT_INVALID, e.message.orEmpty())
        }
    }

    private fun finish(context: Context, code: Int, message: String) {
        Log.i(TAG, "INSTALL_CONFIG: $message")
        // StageNow and MDM scripts do not show the result code: keep errors visible in the settings.
        Provisioning.report(context, "Broadcast", message.takeIf { code != KeyboardApi.RESULT_OK })
        if (isOrderedBroadcast) setResult(code, message, null)
    }

    private companion object {
        const val TAG = "ApiReceiver"
    }
}

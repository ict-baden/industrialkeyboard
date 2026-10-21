package de.ictbaden.industrialkeyboard.api

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.ictbaden.industrialkeyboard.config.ConfigException
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.PasswordGuard
import de.ictbaden.industrialkeyboard.config.Provisioning

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
        if (hash != null) {
            // Wrong passwords lock this channel for a while, so an app cannot try them all.
            when (PasswordGuard.remote(context).check(intent.getStringExtra(KeyboardApi.EXTRA_PASSWORD).orEmpty(), hash)) {
                PasswordGuard.Result.OK -> Unit
                PasswordGuard.Result.WRONG -> return finish(context, KeyboardApi.RESULT_PASSWORD, "Admin password missing or wrong")
                PasswordGuard.Result.LOCKED -> return finish(context, KeyboardApi.RESULT_PASSWORD, "Locked after too many wrong passwords, try again in a few minutes")
            }
        }
        // Any app can send this broadcast and Android does not tell us which one did: without a
        // password only an EMM-managed device may be configured this way, otherwise any app could
        // reconfigure the keyboard of a store install. On a managed device without a password every
        // app can, too – documented in docs/deployment.md: set an admin password.
        if (hash == null && !Provisioning.isManaged(context)) {
            finish(context, KeyboardApi.RESULT_PASSWORD, "Refused: no admin password set and the device is not managed by an EMM. Set an admin password or use a configuration file or managed configuration.")
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

package de.ictbaden.industrialkeyboard.app

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.api.KeyboardBus
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.scanner.NfcFormat

/**
 * Receives NFC tags for the keyboard (`scanner.nfc`). An input method cannot read tags itself (reader
 * mode needs an activity in front), so Android hands the tag to this transparent activity, which
 * turns it into text, gives it to the keyboard and closes at once. The keyboard types it into the
 * field it is shown in; without a field the tag is ignored.
 */
class NfcTagActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
        finish()
        // No animation: the field below should not flicker.
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun handle(intent: Intent?) {
        val settings = ConfigStore.get(this).config.scanner.nfc
        if (!settings.enabled) return
        val tag = intent?.parcelable<Tag>(NfcAdapter.EXTRA_TAG)
        val messages = intent?.parcelableArray(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()
            ?: tag?.let { readNdef(it) }.orEmpty()
        val records = messages.flatMap { it.records.toList() }.map { NfcFormat.Record(it.tnf, it.type, it.payload) }
        val text = NfcFormat.text(tag?.id, records, settings)
        if (text.isNullOrEmpty()) {
            Log.i(TAG, "NFC tag without ${settings.read.key} data")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!KeyboardBus.keyboardRecentlyShown(now)) {
            toast(R.string.nfc_no_field)
            return
        }
        // Typed right away if the keyboard still has the field, otherwise when the field gets it back.
        if (KeyboardBus.onNfc?.invoke(text) != true) KeyboardBus.pendingNfc = text to now
    }

    /** NDEF message of a tag that was dispatched by technology (no message in the intent). */
    private fun readNdef(tag: Tag): List<NdefMessage> {
        val ndef = Ndef.get(tag) ?: return emptyList()
        return runCatching {
            ndef.connect()
            try {
                listOfNotNull(ndef.ndefMessage)
            } finally {
                ndef.close()
            }
        }.onFailure { Log.w(TAG, "Reading NDEF failed", it) }.getOrDefault(emptyList())
    }

    private fun toast(text: Int) {
        Toast.makeText(applicationContext, AppLanguage.localized(this).getString(text), Toast.LENGTH_SHORT).show()
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(name, T::class.java) else @Suppress("DEPRECATION") getParcelableExtra(name)

    private fun Intent.parcelableArray(name: String): Array<out Parcelable>? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayExtra(name, Parcelable::class.java) else @Suppress("DEPRECATION") getParcelableArrayExtra(name)

    private companion object {
        const val TAG = "NfcTagActivity"
    }
}

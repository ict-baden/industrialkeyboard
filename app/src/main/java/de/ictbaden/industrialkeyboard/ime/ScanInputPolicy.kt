package de.ictbaden.industrialkeyboard.ime

import android.os.Build
import android.text.InputType
import de.ictbaden.industrialkeyboard.config.ScanInputSettings

/**
 * Decides whether a barcode broadcast may be typed. Android does not tell a receiver who sent a
 * broadcast (except on Android 14+ for senders that share their identity), so the keyboard types
 * only into a visible, ordinary field – never into a password field or a raw key field.
 */
object ScanInputPolicy {
    /** Longer than any QR code (about 7,000 digits). */
    const val MAX_LENGTH = 8_192

    /** Why the scan is not typed, or `null` to type it. */
    fun refusal(
        settings: ScanInputSettings,
        sender: String?,
        keyboardShown: Boolean,
        inputType: Int,
        data: String?,
        sdk: Int = Build.VERSION.SDK_INT,
    ): String? = when {
        !settings.enabled -> "scan input is off"
        settings.allowedPackages.isNotEmpty() && sdk < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> "allowedPackages needs Android 14"
        settings.allowedPackages.isNotEmpty() && sender !in settings.allowedPackages -> "sender ${sender ?: "(not identified)"} is not in allowedPackages"
        !keyboardShown -> "keyboard not shown"
        inputType == InputType.TYPE_NULL -> "field takes no text"
        FieldPolicy.isPassword(inputType) -> "password field"
        data.isNullOrEmpty() -> "no barcode in the broadcast"
        data.length > MAX_LENGTH -> "barcode longer than $MAX_LENGTH characters"
        else -> null
    }
}

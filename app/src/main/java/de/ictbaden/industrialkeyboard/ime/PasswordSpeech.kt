package de.ictbaden.industrialkeyboard.ime

import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import de.ictbaden.industrialkeyboard.config.PasswordKeySpeech

/**
 * Whether a screen reader may speak the keys of a password field (`accessibility.speakPasswordKeys`).
 * Like Android and Gboard: by default only while headphones, a headset or a hearing aid are
 * connected, so that bystanders do not hear the password.
 */
object PasswordSpeech {

    /** True: character keys are spoken as "Key" ([passwordField] only; other fields are never obscured). */
    fun obscure(policy: PasswordKeySpeech, passwordField: Boolean, headset: Boolean): Boolean = passwordField && when (policy) {
        PasswordKeySpeech.ALWAYS -> false
        PasswordKeySpeech.NEVER -> true
        PasswordKeySpeech.HEADSET -> !headset
    }

    /** A private audio output is connected. An unknown device type counts as none (keys stay obscured). */
    fun headsetConnected(audio: AudioManager?): Boolean {
        audio ?: return false
        return runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in privateOutputs() } }.getOrDefault(false)
    }

    /** Output device types that only the user hears. */
    fun privateOutputs(): Set<Int> = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(AudioDeviceInfo.TYPE_HEARING_AID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }
}

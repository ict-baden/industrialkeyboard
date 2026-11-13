package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import de.ictbaden.industrialkeyboard.config.FeedbackSettings
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyOutput

/** Click sound and vibration on key presses. */
class Feedback(context: Context) {
    var settings = FeedbackSettings()

    private val audio = context.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    /** The keyboard view: without a configured length the system's keyboard haptic is used on it. */
    var hapticView: View? = null

    fun keyDown(key: Key?) {
        if (settings.vibrate) {
            // Like Gboard: the device's tuned "keyboard tap" (a short tick on Pixel), unless an
            // administrator set a length (e.g. stronger for gloves).
            val ticked = settings.vibrationMs == null &&
                hapticView?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING) == true
            if (!ticked) vibrate()
        }
        if (settings.sound) click(key?.output)
    }

    /** A barcode was read with the camera: a longer buzz, like a scanner beep you can feel. */
    fun scanned() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(SCAN_VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun vibrate() {
        val vibrator = vibrator?.takeIf { it.hasVibrator() } ?: return
        if (settings.vibrationMs == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No view for the system tick: the device's tuned "click", what KEYBOARD_TAP plays.
            if (runCatching { vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) }.isSuccess) return
        }
        val ms = (settings.vibrationMs ?: DEFAULT_VIBRATION_MS).coerceIn(5, 100).toLong()
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun click(output: KeyOutput?) {
        val effect = when (output) {
            KeyOutput.Backspace -> AudioManager.FX_KEYPRESS_DELETE
            KeyOutput.Enter -> AudioManager.FX_KEYPRESS_RETURN
            KeyOutput.Space -> AudioManager.FX_KEYPRESS_SPACEBAR
            else -> AudioManager.FX_KEYPRESS_STANDARD
        }
        val volume = settings.soundVolume?.let { it.coerceIn(0, 100) / 100f } ?: -1f
        audio?.playSoundEffect(effect, volume)
    }

    private companion object {
        const val DEFAULT_VIBRATION_MS = 18
        const val SCAN_VIBRATION_MS = 60L
    }
}

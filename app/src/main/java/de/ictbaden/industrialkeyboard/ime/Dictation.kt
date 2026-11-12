package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.VoiceInput
import java.util.Locale

/** Pure rules for dictation inside the keyboard (issue #23); the recording is in [DictationController]. */
object Dictation {

    /** Which speech recognizer the microphone uses. */
    enum class Engine {
        /** Android's on-device recognition (API 31+): nothing leaves the device. */
        ON_DEVICE,

        /** The device's speech recognition service; it may work online (e.g. Google). */
        SERVICE,

        /** No dictation: switched off or nothing available – the microphone is hidden. */
        NONE,
    }

    fun engine(mode: VoiceInput, onDeviceAvailable: Boolean, serviceAvailable: Boolean): Engine = when (mode) {
        VoiceInput.OFF -> Engine.NONE
        VoiceInput.OFFLINE -> if (onDeviceAvailable) Engine.ON_DEVICE else Engine.NONE
        VoiceInput.AUTO -> when {
            onDeviceAvailable -> Engine.ON_DEVICE
            serviceAvailable -> Engine.SERVICE
            else -> Engine.NONE
        }
    }

    /**
     * Recognised [spoken] text as it is entered after [before] (the text left of the cursor): a
     * space in front unless at the start or after a space or an opening bracket, and a capital
     * first letter at the start of a sentence when [autoCapitalize] is on.
     */
    fun prepare(spoken: String, before: CharSequence?, autoCapitalize: Boolean, locale: Locale): String {
        val text = spoken.trim()
        if (text.isEmpty()) return ""
        val previous = before?.toString().orEmpty()
        val needsSpace = previous.isNotEmpty() && !previous.last().isWhitespace() && previous.last() !in OPENING
        val sentenceStart = previous.trimEnd().let { it.isEmpty() || it.last() in SENTENCE_END }
        val shaped = if (autoCapitalize && sentenceStart) text.replaceFirstChar { it.titlecase(locale) } else text
        return if (needsSpace) " $shaped" else shaped
    }

    private const val OPENING = "([{\"'„“«¿¡/"
    private const val SENTENCE_END = ".!?\n"

    /** Without a sound for this long the dictation stops on its own (Gboard behaves alike). */
    const val SILENCE_TIMEOUT_MS = 30_000L

    /** Pause that ends one utterance; listening then starts again while the panel is open. */
    const val PAUSE_MS = 1_500L
}

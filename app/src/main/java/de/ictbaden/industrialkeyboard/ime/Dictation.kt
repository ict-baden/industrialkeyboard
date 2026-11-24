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

        /** The free offline model inside the app (Vosk, issue #24): nothing leaves the device. */
        FREE,

        /** No dictation: switched off or nothing available – the microphone is hidden. */
        NONE,
    }

    /**
     * The smart switch (issue #24): which engine the microphone uses for [mode].
     *
     * - `auto`: the device's own recognition as before (on the device first, then its speech
     *   service, e.g. Google); only a device without any (AOSP) takes the free model.
     * - `offline`: Android's on-device recognition; without it the free model, which runs on the
     *   device as well, so nothing leaves it either way.
     * - `free`: always the free model, never a recognizer of the device or Google.
     * - `off`: none.
     *
     * [freeAvailable]: the free model can be used – its language pack is installed, or it may be
     * downloaded (see [freeUsable]).
     */
    fun engine(mode: VoiceInput, onDeviceAvailable: Boolean, serviceAvailable: Boolean, freeAvailable: Boolean): Engine = when (mode) {
        VoiceInput.OFF -> Engine.NONE
        VoiceInput.FREE -> if (freeAvailable) Engine.FREE else Engine.NONE
        VoiceInput.OFFLINE -> when {
            onDeviceAvailable -> Engine.ON_DEVICE
            freeAvailable -> Engine.FREE
            else -> Engine.NONE
        }
        VoiceInput.AUTO -> when {
            onDeviceAvailable -> Engine.ON_DEVICE
            serviceAvailable -> Engine.SERVICE
            freeAvailable -> Engine.FREE
            else -> Engine.NONE
        }
    }

    /**
     * The free model counts as available with its language pack installed, or when the user may
     * download it (`typing.voiceModelDownload`); otherwise the microphone is hidden, since nothing in
     * the keyboard could make it work (import is in the settings).
     */
    fun freeUsable(packInstalled: Boolean, downloadAllowed: Boolean): Boolean = packInstalled || downloadAllowed

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

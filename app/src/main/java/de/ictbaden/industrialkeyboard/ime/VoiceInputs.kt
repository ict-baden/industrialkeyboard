package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.VoiceInput

/**
 * Dictation stage 1 (issue #23): which installed voice input method the microphone switches to.
 * The keyboard itself never records audio; the voice input method (e.g. Google Voice Typing) does
 * and usually returns to the previous keyboard on its own.
 */
object VoiceInputs {

    /** One enabled input method subtype of another app. */
    data class Candidate(val imeId: String, val packageName: String, val subtypeMode: String?, val subtypeIndex: Int)

    /**
     * The first voice subtype of another app, or `null` when dictation is off or none exists.
     * `offline` (speech recognition inside the keyboard, issue #24) is not available yet.
     */
    fun pick(mode: VoiceInput, ownPackage: String, candidates: List<Candidate>): Candidate? = when (mode) {
        VoiceInput.OFF, VoiceInput.OFFLINE -> null
        VoiceInput.AUTO, VoiceInput.SYSTEM ->
            candidates.firstOrNull { it.packageName != ownPackage && it.subtypeMode.equals(VOICE, ignoreCase = true) }
    }

    const val VOICE = "voice"
}

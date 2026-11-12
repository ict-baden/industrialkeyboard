package de.ictbaden.industrialkeyboard.ime

import de.ictbaden.industrialkeyboard.config.PasswordKeySpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Which keys a screen reader may speak in password fields (`accessibility.speakPasswordKeys`). */
class PasswordSpeechTest {

    @Test
    fun `password keys are obscured unless the policy and a headset allow them`() {
        // policy, headset → obscured in a password field
        val expected = mapOf(
            (PasswordKeySpeech.HEADSET to false) to true,
            (PasswordKeySpeech.HEADSET to true) to false,
            (PasswordKeySpeech.ALWAYS to false) to false,
            (PasswordKeySpeech.ALWAYS to true) to false,
            (PasswordKeySpeech.NEVER to false) to true,
            (PasswordKeySpeech.NEVER to true) to true,
        )
        for ((input, obscured) in expected) {
            val (policy, headset) = input
            assertEquals("$policy, headset $headset", obscured, PasswordSpeech.obscure(policy, passwordField = true, headset = headset))
        }
    }

    @Test
    fun `a field that is no password is never obscured`() {
        for (policy in PasswordKeySpeech.entries) {
            for (headset in listOf(false, true)) assertFalse(PasswordSpeech.obscure(policy, passwordField = false, headset = headset))
        }
    }

    @Test
    fun `without an audio manager there is no headset`() {
        assertFalse(PasswordSpeech.headsetConnected(null))
    }
}

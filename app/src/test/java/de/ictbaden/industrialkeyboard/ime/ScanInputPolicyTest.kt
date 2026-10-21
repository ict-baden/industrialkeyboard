package de.ictbaden.industrialkeyboard.ime

import android.text.InputType
import de.ictbaden.industrialkeyboard.config.ScanInputSettings
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScanInputPolicyTest {

    private val on = ScanInputSettings(enabled = true)
    private val text = InputType.TYPE_CLASS_TEXT

    private fun refusal(
        settings: ScanInputSettings = on,
        sender: String? = null,
        shown: Boolean = true,
        inputType: Int = text,
        data: String? = "4006381333931",
        sdk: Int = 35,
    ) = ScanInputPolicy.refusal(settings, sender, shown, inputType, data, sdk)

    @Test
    fun `types into a visible text field`() {
        assertNull(refusal())
        assertNull(refusal(inputType = InputType.TYPE_CLASS_NUMBER))
        // Often used for barcode fields to suppress suggestions; the text is visible anyway.
        assertNull(refusal(inputType = text or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD))
    }

    @Test
    fun `refuses when off, hidden or empty`() {
        assertNotNull(refusal(settings = ScanInputSettings()))
        assertNotNull(refusal(shown = false))
        assertNotNull(refusal(data = null))
        assertNotNull(refusal(data = ""))
        assertNotNull(refusal(data = "1".repeat(ScanInputPolicy.MAX_LENGTH + 1)))
    }

    @Test
    fun `never into password or raw fields`() {
        assertNotNull(refusal(inputType = text or InputType.TYPE_TEXT_VARIATION_PASSWORD))
        assertNotNull(refusal(inputType = text or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
        assertNotNull(refusal(inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD))
        assertNotNull(refusal(inputType = InputType.TYPE_NULL))
    }

    @Test
    fun `allowed packages fail closed`() {
        val listed = on.copy(allowedPackages = listOf("com.symbol.datawedge"))
        assertNull(refusal(settings = listed, sender = "com.symbol.datawedge", sdk = 34))
        assertNotNull(refusal(settings = listed, sender = "com.example.evil", sdk = 34))
        assertNotNull(refusal(settings = listed, sender = null, sdk = 34))
        assertNotNull(refusal(settings = listed, sender = "com.symbol.datawedge", sdk = 33))
    }
}

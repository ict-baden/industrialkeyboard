package de.ictbaden.industrialkeyboard.app

import android.text.InputType
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class UiTextFieldTest {

    private val ui = Ui(ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_IndustrialKeyboard))

    @Test
    fun `text fields are single-line by default`() {
        val edit = ui.textField("Name").editText!!
        assertEquals(InputType.TYPE_CLASS_TEXT, edit.inputType)
        assertEquals(1, edit.maxLines)
    }

    @Test
    fun `multi-line text fields show 2 to 4 lines and take line breaks`() {
        val edit = ui.textField("Text", "a\nb", multiLine = true).editText!!
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE, edit.inputType)
        assertEquals(2, edit.minLines)
        assertEquals(4, edit.maxLines)
        assertEquals("a\nb", edit.text.toString())
    }

    @Test
    fun `password fields stay single-line`() {
        val edit = ui.textField("PIN", password = true, multiLine = true).editText!!
        assertEquals(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, edit.inputType)
        assertTrue(edit.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE == 0)
        assertEquals(1, edit.maxLines)
    }
}

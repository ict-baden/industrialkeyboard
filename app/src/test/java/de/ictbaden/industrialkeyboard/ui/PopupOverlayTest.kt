package de.ictbaden.industrialkeyboard.ui

import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PopupOverlayTest {

    /** A custom key as wide as the keyboard used to crash the input method (coerceIn on an empty range). */
    @Test
    fun `popups wider than the overlay do not crash`() {
        val overlay = PopupOverlay(ApplicationProvider.getApplicationContext())
        overlay.layout(0, 0, 300, 400)
        val key = RectF(0f, 200f, 300f, 260f)
        overlay.showPreview(overlay, key, "A")
        overlay.showAlternatives(overlay, key, List(8) { "x" })
    }
}

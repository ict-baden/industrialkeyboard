package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The panels over the keys are only ever measured with the real width. The emoji picker keeps the
 * cell size of its first measure: an unspecified width (baseline alignment of the horizontal row
 * around the keys) made one emoji wider than the screen on the HC20 – an empty grid, and a tap
 * crashed the keyboard with OutOfMemoryError.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en")
class PanelMeasureTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Records every width it is measured with. */
    private class Probe(context: Context) : View(context) {
        val widthModes = mutableListOf<Int>()
        val widths = mutableListOf<Int>()
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            widthModes += MeasureSpec.getMode(widthMeasureSpec)
            widths += MeasureSpec.getSize(widthMeasureSpec)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    @Test
    fun `the emoji panel is never measured with an unspecified width`() {
        val root = KeyboardRootView(context)
        val probe = Probe(context)
        root.emojiPanel.addView(probe, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 10))
        root.render(KeyboardRenderer.state(context, KeyboardConfig(), Page.Alpha(AlphaVariant.TEXT), ShiftState.OFF, null))
        // Visible without opening the picker itself (it loads the emoji list).
        root.emojiPanel.visibility = View.VISIBLE

        val width = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)

        assertTrue("measured at all", probe.widthModes.isNotEmpty())
        assertTrue("only exact widths: ${probe.widthModes}", probe.widthModes.all { it == View.MeasureSpec.EXACTLY })
        assertTrue("never wider than the keyboard: ${probe.widths}", probe.widths.all { it <= width })
        assertEquals(width, probe.width)
    }
}

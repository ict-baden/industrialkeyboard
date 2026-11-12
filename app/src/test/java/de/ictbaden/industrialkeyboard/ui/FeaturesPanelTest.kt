package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.QuickToggle
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The ⊞ panel: on/off tiles and scrolling when the tiles are taller than the keys. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en")
class FeaturesPanelTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun feature(n: Int, checked: Boolean? = null) =
        FeaturesPanelView.Feature("feature:$n", R.drawable.kb_settings, R.string.feature_settings, checked = checked)

    @Test
    fun `switch tiles are selected and say on or off`() {
        val panel = FeaturesPanelView(context)
        panel.show(listOf(feature(0), feature(1, checked = true), feature(2, checked = false)))
        val action = panel.getChildAt(0)
        val on = panel.getChildAt(1)
        val off = panel.getChildAt(2)
        assertFalse(action.isSelected)
        assertNull(ViewCompat.getStateDescription(action))
        assertTrue(on.isSelected)
        assertEquals("On", ViewCompat.getStateDescription(on))
        assertFalse(off.isSelected)
        assertEquals("Off", ViewCompat.getStateDescription(off))
    }

    @Test
    fun `the panel scrolls so that the last of 13 tiles can be reached`() {
        val root = KeyboardRootView(context)
        val features = (1..13).map { feature(it, checked = if (it > 9) it % 2 == 0 else null) }
        root.featuresPanel.show(features)
        root.render(KeyboardRenderer.state(context, KeyboardConfig(), Page.Alpha(AlphaVariant.TEXT), ShiftState.OFF, null, toolPanel = Tabs.TOOL_MENU))
        layout(root)

        val scroll = root.featuresScroll
        assertEquals(View.VISIBLE, scroll.visibility)
        assertEquals(View.VISIBLE, root.featuresPanel.visibility)
        assertTrue("4 rows of tiles are taller than the keys", root.featuresPanel.height > scroll.height)
        assertTrue(scroll.canScrollVertically(1))

        scroll.scrollTo(0, root.featuresPanel.height)
        val last = root.featuresPanel.getChildAt(features.lastIndex)
        assertTrue("last tile fully visible", last.bottom - scroll.scrollY <= scroll.height)
        assertTrue(last.top - scroll.scrollY >= 0)
    }

    @Test
    fun `a switched tile keeps its view and says the new state`() {
        val panel = FeaturesPanelView(context)
        panel.show(listOf(feature(0), feature(1, checked = false)))
        val tile = panel.getChildAt(1)
        assertEquals("Off", ViewCompat.getStateDescription(tile))

        panel.show(listOf(feature(0), feature(1, checked = true)))
        assertSame("no rebuild for a toggle", tile, panel.getChildAt(1))
        assertEquals("On", ViewCompat.getStateDescription(tile))
        assertTrue(tile.isSelected)

        panel.palette = Palette.light(Palette.CONTRAST_ACCENT)
        assertSame("no rebuild for a new palette", tile, panel.getChildAt(1))

        panel.show(listOf(feature(0), feature(1, checked = true), feature(2)))
        assertEquals(3, panel.childCount)
    }

    @Test
    fun `switch tiles are switches for screen readers`() {
        val panel = FeaturesPanelView(context)
        panel.show(listOf(feature(0), feature(1, checked = true)))
        val action = AccessibilityNodeInfoCompat.wrap(panel.getChildAt(0).createAccessibilityNodeInfo())
        assertFalse(action.isCheckable)

        val tile = panel.getChildAt(1)
        var info = AccessibilityNodeInfoCompat.wrap(tile.createAccessibilityNodeInfo())
        assertTrue(info.isCheckable)
        assertTrue(info.isChecked)
        assertFalse("not also \"selected\"", info.isSelected)

        panel.show(listOf(feature(0), feature(1, checked = false)))
        info = AccessibilityNodeInfoCompat.wrap(tile.createAccessibilityNodeInfo())
        assertFalse(info.isChecked)
    }

    @Test
    fun `the panel opens at the top again`() {
        val root = KeyboardRootView(context)
        root.featuresPanel.show((1..13).map { feature(it) })
        val open = KeyboardRenderer.state(context, KeyboardConfig(), Page.Alpha(AlphaVariant.TEXT), ShiftState.OFF, null, toolPanel = Tabs.TOOL_MENU)
        val closed = KeyboardRenderer.state(context, KeyboardConfig(), Page.Alpha(AlphaVariant.TEXT), ShiftState.OFF, null)
        root.render(open)
        layout(root)
        root.featuresScroll.scrollTo(0, root.featuresPanel.height)
        val scrolled = root.featuresScroll.scrollY
        assertTrue(scrolled > 0)

        root.render(open)
        assertEquals("stays in place while open", scrolled, root.featuresScroll.scrollY)

        root.render(closed)
        root.render(open)
        layout(root)
        assertEquals(0, root.featuresScroll.scrollY)
    }

    @Test
    fun `quick toggle tiles have their own ids`() {
        for (t in QuickToggle.entries) assertEquals(t, FeaturesPanelView.toggleOf(FeaturesPanelView.toggleId(t)))
        assertEquals("toggle:glove", FeaturesPanelView.toggleId(QuickToggle.GLOVE))
        assertNull(FeaturesPanelView.toggleOf(FeaturesPanelView.SCAN))
        assertNull(FeaturesPanelView.toggleOf("toggle:torch"))
    }

    @Test
    fun `the scroll container is hidden while no panel is open`() {
        val root = KeyboardRootView(context)
        root.render(KeyboardRenderer.state(context, KeyboardConfig(), Page.Alpha(AlphaVariant.TEXT), ShiftState.OFF, null))
        layout(root)
        assertEquals(View.GONE, root.featuresScroll.visibility)
    }

    private fun layout(root: View) {
        val width = 1080
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        root.layout(0, 0, width, root.measuredHeight)
    }
}

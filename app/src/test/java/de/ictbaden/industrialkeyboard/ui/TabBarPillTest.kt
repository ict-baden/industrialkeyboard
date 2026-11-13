package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The selection pill of the tab bar. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en")
class TabBarPillTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val tabs = listOf(
        TabBarView.Tab(Tabs.ALPHA, "ABC"),
        TabBarView.Tab(Tabs.NUMERIC, "123"),
        TabBarView.Tab(Tabs.SYMBOLS, "#+="),
    )

    @Test
    fun `a pill still sliding is gone after a switch to the toolbar`() {
        val bar = TabBarView(context)
        bar.setTabs(tabs, Tabs.SYMBOLS, animate = false)
        bar.layout(0, 0, 1080, 138)
        // #+= → ABC slides; before it arrives an MDM push switches to the toolbar (no ABC marked).
        bar.setTabs(tabs, Tabs.ALPHA, animate = true)
        bar.setTabs(KeyboardRenderer.toolbarTabs(KeyboardConfig(), scanAvailable = false), null, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertTrue("no pill: ${bar.pillBounds}", bar.pillBounds.isEmpty)
    }

    @Test
    fun `the pill slides to the selected tab`() {
        val bar = TabBarView(context)
        bar.setTabs(tabs, Tabs.SYMBOLS, animate = false)
        bar.layout(0, 0, 1080, 138)
        val symbols = bar.pillBounds
        bar.setTabs(tabs, Tabs.ALPHA, animate = true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertFalse(bar.pillBounds.isEmpty)
        assertTrue("left of #+=", bar.pillBounds.right <= symbols.left)
    }
}

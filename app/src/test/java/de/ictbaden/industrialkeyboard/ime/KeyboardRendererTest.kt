package de.ictbaden.industrialkeyboard.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Key rows with large system fonts (`accessibility.followFontSize`). */
class KeyboardRendererTest {

    @Test
    fun `the font factor follows the system font only when asked and stays between 1 and 1_3`() {
        assertEquals(1f, KeyboardRenderer.fontFactor(false, 2f), 0f)
        assertEquals(1.15f, KeyboardRenderer.fontFactor(true, 1.15f), 0f)
        assertEquals(1.3f, KeyboardRenderer.fontFactor(true, 2f), 0f)
        assertEquals(1f, KeyboardRenderer.fontFactor(true, 0.85f), 0f)
    }

    @Test
    fun `without a factor the row height is exactly the configured one`() {
        assertEquals(162, KeyboardRenderer.rowHeightPx(basePx = 162f, keyHeight = 100, factor = 1f, heightRows = 4, screenHeightPx = 2400, barPx = 138f))
        // Even when the configured keys alone are taller than the cap.
        assertEquals(324, KeyboardRenderer.rowHeightPx(basePx = 162f, keyHeight = 200, factor = 1f, heightRows = 4, screenHeightPx = 1000, barPx = 138f))
    }

    @Test
    fun `rows grow with the factor but never below the configured height and never above 60 percent`() {
        // Plenty of room: 162 × 1.3.
        assertEquals(210, KeyboardRenderer.rowHeightPx(162f, 100, 1.3f, 4, 2400, 180f))
        for (screen in listOf(800, 1200, 1600, 2400, 3200)) {
            for (factor in listOf(1.1f, 1.2f, 1.3f)) {
                val bar = 46f * 3f * factor
                val row = KeyboardRenderer.rowHeightPx(162f, 100, factor, 4, screen, bar)
                assertTrue("screen $screen, factor $factor: $row below the configured height", row >= 162)
                val growth = row > 162
                if (growth) assertTrue("screen $screen, factor $factor: $row over the cap", row * 4 + bar <= screen * 0.6f + 1)
            }
        }
    }

    @Test
    fun `glove mode and the font factor together are capped at 60 percent of the screen`() {
        // Glove mode: keyHeight 130 (effective configuration), then × 1.3 for the font.
        val base = 65f * 2.625f
        val bar = 46f * 2.625f * 1.3f
        val screen = 2000
        val row = KeyboardRenderer.rowHeightPx(base, 130, 1.3f, 4, screen, bar)
        val unscaled = (base * 1.3f).toInt()
        assertTrue(row >= unscaled)
        assertTrue("$row × 4 + bar over 60 % of $screen", row * 4 + bar <= screen * KeyboardRenderer.MAX_SCREEN_SHARE + 1)
        // Without the cap it would be 1.69 × the base; here the cap wins.
        assertTrue(row < (base * 1.3f * 1.3f).toInt())
    }
}

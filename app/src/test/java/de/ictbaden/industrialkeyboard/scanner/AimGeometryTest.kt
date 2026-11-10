package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.AimMode
import de.ictbaden.industrialkeyboard.scanner.AimGeometry.Found
import de.ictbaden.industrialkeyboard.scanner.AimGeometry.Point
import de.ictbaden.industrialkeyboard.scanner.AimGeometry.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AimGeometryTest {

    /** Upright 720×1280 image shown in a 1080×600 keyboard area: scale 1.5, cut 660 px at top and bottom. */
    private val imageW = 720f
    private val imageH = 1280f

    /** Frame as the keyboard draws it: 80 % width, min(62 % height, width). */
    private val screen = Screen(1080f, 600f, 108f, 114f, 972f, 486f)

    private fun box(name: String, cx: Float, cy: Float, half: Float = 40f) = Found(
        name,
        listOf(Point(cx - half, cy - half), Point(cx + half, cy - half), Point(cx + half, cy + half), Point(cx - half, cy + half)),
    )

    @Test
    fun `image to view mapping centre-crops`() {
        // Image centre lands on the view centre.
        assertEquals(Point(540f, 300f), AimGeometry.toView(Point(360f, 640f), imageW, imageH, screen))
        // Scale 1.5: 100 px right of the image centre are 150 px on screen.
        assertEquals(Point(690f, 300f), AimGeometry.toView(Point(460f, 640f), imageW, imageH, screen))
        assertEquals(-660f, AimGeometry.toView(Point(0f, 0f), imageW, imageH, screen).y)
    }

    @Test
    fun `frame mode takes only codes inside the frame`() {
        // y 640 is the middle; ±100 image px = ±150 view px, still inside (114–486).
        val inside = box("inside", 360f, 700f)
        val outside = box("outside", 360f, 300f) // view y = 300*1.5 - 660 = -210
        assertEquals("inside", AimGeometry.pick(listOf(outside, inside), imageW, imageH, screen, AimMode.FRAME))
        assertNull(AimGeometry.pick(listOf(outside), imageW, imageH, screen, AimMode.FRAME))
    }

    @Test
    fun `center mode takes the code under the crosshair`() {
        val left = box("left", 200f, 640f, 50f) // view x 300 – far from 540
        val middle = box("middle", 360f, 640f, 50f)
        assertEquals("middle", AimGeometry.pick(listOf(left, middle), imageW, imageH, screen, AimMode.CENTER))
        assertNull(AimGeometry.pick(listOf(left), imageW, imageH, screen, AimMode.CENTER))
        // Small code next to the crosshair, within 15 % of the frame width (130 px on screen).
        val near = box("near", 400f, 640f, 5f) // view x 600: 60 px away
        assertEquals("near", AimGeometry.pick(listOf(near), imageW, imageH, screen, AimMode.CENTER))
    }

    @Test
    fun `anywhere takes the code nearest the middle`() {
        val far = box("far", 50f, 100f)
        val near = box("near", 300f, 600f)
        assertEquals("near", AimGeometry.pick(listOf(far, near), imageW, imageH, screen, AimMode.ANYWHERE))
        assertEquals("far", AimGeometry.pick(listOf(far), imageW, imageH, null, AimMode.ANYWHERE))
        assertNull(AimGeometry.pick(listOf(far), imageW, imageH, null, AimMode.FRAME))
        assertNull(AimGeometry.pick(emptyList<Found<String>>(), imageW, imageH, screen, AimMode.ANYWHERE))
    }

    @Test
    fun `polygon test`() {
        val diamond = listOf(Point(0f, -10f), Point(10f, 0f), Point(0f, 10f), Point(-10f, 0f))
        assertTrue(AimGeometry.contains(diamond, Point(0f, 0f)))
        assertTrue(AimGeometry.contains(diamond, Point(4f, 4f)))
        assertFalse(AimGeometry.contains(diamond, Point(8f, 8f)))
    }
}

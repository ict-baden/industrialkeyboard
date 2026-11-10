package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.scanner.HardwareScanner.Listed
import de.ictbaden.industrialkeyboard.scanner.HardwareScanner.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanEngineTest {

    /** What DataWedge 15 answered on a Zebra HC20 with SE4720 imager, nothing paired. */
    private val hc20 = listOf(
        Listed("INTERNAL_CAMERA", "camera scanner", true),
        Listed("INTERNAL_IMAGER", "2D Barcode Imager", true),
        Listed("BLUETOOTH_SSI", "Bluetooth Scanner", false),
        Listed("BLUETOOTH_RS6000", "RS6000 Bluetooth Scanner", false),
        Listed("BLUETOOTH_RS5100", "RS5100 Bluetooth Scanner", false),
        Listed("BLUETOOTH_ZEBRA", "Zebra Bluetooth Scanner", false),
        Listed("USB_ZEBRA", "Zebra USB Scanner(SSI)", false),
        Listed("USB_ZEBRACRADLE", "Zebra USB Cradle", false),
        Listed("USB_TGCS_MP7000", "Zebra POS Scanner/Scale", false),
        Listed("USB_SNAPI_ZEBRA", "Zebra USB Scanner(SNAPI)", false),
    )

    @Test
    fun `built-in imager counts`() {
        assertTrue(HardwareScanner.hasScanEngine(hc20))
    }

    @Test
    fun `camera and unpaired external scanners do not count`() {
        assertFalse(HardwareScanner.hasScanEngine(hc20.filterNot { it.identifier == "INTERNAL_IMAGER" }))
        assertFalse(HardwareScanner.hasScanEngine(emptyList()))
    }

    @Test
    fun `connected external scanner counts`() {
        val withRing = hc20.filterNot { it.identifier == "INTERNAL_IMAGER" }
            .map { if (it.identifier == "BLUETOOTH_RS6000") it.copy(connected = true) else it }
        assertTrue(HardwareScanner.hasScanEngine(withRing))
    }

    @Test
    fun `auto uses the scan engine, otherwise the camera`() {
        assertEquals(ScanTriggerType.DATAWEDGE, ScanTrigger.auto(dataWedge = true, honeywell = false, engine = State.PRESENT, camera = true))
        // While DataWedge has not answered yet, keep the old behaviour.
        assertEquals(ScanTriggerType.DATAWEDGE, ScanTrigger.auto(dataWedge = true, honeywell = false, engine = State.UNKNOWN, camera = true))
        assertEquals(ScanTriggerType.CAMERA, ScanTrigger.auto(dataWedge = true, honeywell = false, engine = State.ABSENT, camera = true))
        assertEquals(ScanTriggerType.HONEYWELL, ScanTrigger.auto(dataWedge = false, honeywell = true, engine = State.PRESENT, camera = true))
        assertEquals(ScanTriggerType.CAMERA, ScanTrigger.auto(dataWedge = false, honeywell = false, engine = State.ABSENT, camera = true))
        assertEquals(ScanTriggerType.NONE, ScanTrigger.auto(dataWedge = false, honeywell = false, engine = State.ABSENT, camera = false))
    }
}

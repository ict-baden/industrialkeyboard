package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.Symbology
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import zxingcpp.BarcodeReader.Format

class SymbologiesTest {

    @Test
    fun `zxing names`() {
        assertEquals(Symbology.QR, Symbologies.fromZxing("QR_CODE"))
        assertEquals(Symbology.QR, Symbologies.fromZxing("QR_CODE_MODEL_2"))
        assertEquals(Symbology.MICROQR, Symbologies.fromZxing("MICRO_QR_CODE"))
        assertEquals(Symbology.EAN13, Symbologies.fromZxing("EAN_13"))
        assertEquals(Symbology.EAN13, Symbologies.fromZxing("ISBN"))
        assertEquals(Symbology.CODE39, Symbologies.fromZxing("CODE_39_EXT"))
        assertEquals(Symbology.DATABAR_EXPANDED, Symbologies.fromZxing("DATA_BAR_EXP_STK"))
        assertEquals(Symbology.DATABAR, Symbologies.fromZxing("DATA_BAR_OMNI"))
        assertEquals(Symbology.DATABAR_LIMITED, Symbologies.fromZxing("DATA_BAR_LTD"))
        assertEquals(Symbology.PDF417, Symbologies.fromZxing("COMPACT_PDF_417"))
        assertEquals(Symbology.MICROPDF417, Symbologies.fromZxing("MICRO_PDF_417"))
        assertEquals(Symbology.ITF, Symbologies.fromZxing("ITF_14"))
        assertNull(Symbologies.fromZxing("DX_FILM_EDGE"))
    }

    @Test
    fun `every type maps to zxing formats and back`() {
        assertTrue(Symbologies.toZxing(emptySet()).isEmpty())
        // OCR and NFC are no barcodes: no zxing formats.
        assertTrue(Symbologies.toZxing(setOf(Symbology.OCR, Symbology.NFC)).isEmpty())
        Symbology.entries.filter { it.barcode }.forEach { type ->
            val formats = Symbologies.toZxing(setOf(type))
            assertTrue("$type has formats", formats.isNotEmpty())
            formats.forEach { assertEquals("$it", type, Symbologies.fromZxing(it.name)) }
        }
        assertEquals(setOf(Format.QR_CODE, Format.EAN_13), Symbologies.toZxing(setOf(Symbology.QR, Symbology.EAN13)))
    }

    @Test
    fun `datawedge label types`() {
        assertEquals(Symbology.EAN13, Symbologies.fromDataWedge("LABEL-TYPE-EAN13"))
        assertEquals(Symbology.UPCE, Symbologies.fromDataWedge("LABEL-TYPE-UPCE0"))
        assertEquals(Symbology.CODE128, Symbologies.fromDataWedge("LABEL-TYPE-EAN128"))
        assertEquals(Symbology.QR, Symbologies.fromDataWedge("QRCODE"))
        assertEquals(Symbology.DATABAR_EXPANDED, Symbologies.fromDataWedge("LABEL-TYPE-GS1-DATABAR-EXP"))
        assertEquals(Symbology.ITF, Symbologies.fromDataWedge("LABEL-TYPE-I2OF5"))
        assertNull(Symbologies.fromDataWedge("LABEL-TYPE-POSTNET"))
        assertNull(Symbologies.fromDataWedge(null))
    }

    @Test
    fun `aim identifiers`() {
        assertEquals(Symbology.EAN13, Symbologies.fromAim("]E0"))
        assertEquals(Symbology.EAN8, Symbologies.fromAim("]E4"))
        assertEquals(Symbology.CODE128, Symbologies.fromAim("]C1"))
        assertEquals(Symbology.DATAMATRIX, Symbologies.fromAim("]d2"))
        assertEquals(Symbology.QR, Symbologies.fromAim("]Q3"))
        assertNull(Symbologies.fromAim("]X0"))
        assertNull(Symbologies.fromAim(""))
    }

    @Test
    fun `config names are unique and found case-insensitively`() {
        assertEquals(Symbology.entries.size, Symbology.entries.map { it.key }.toSet().size)
        assertEquals(Symbology.DATABAR_EXPANDED, Symbology.byKey(" DataBarExpanded "))
        assertNull(Symbology.byKey("ean-13"))
    }
}

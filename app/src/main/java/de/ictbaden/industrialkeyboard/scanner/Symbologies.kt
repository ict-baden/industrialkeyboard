package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.Symbology
import zxingcpp.BarcodeReader.Format

/** Barcode type names of zxing-cpp, DataWedge and AIM identifiers mapped to [Symbology]. */
object Symbologies {

    /** zxing-cpp format name (e.g. `QR_CODE`, `EAN_13`) → type; `null` = not one we name. */
    fun fromZxing(name: String): Symbology? {
        val n = name.uppercase()
        return when {
            n.startsWith("AZTEC") -> Symbology.AZTEC
            n == "CODABAR" -> Symbology.CODABAR
            n.startsWith("CODE_39") || n == "PZN" || n == "CODE_32" -> Symbology.CODE39
            n == "CODE_93" -> Symbology.CODE93
            n == "CODE_128" -> Symbology.CODE128
            n.startsWith("DATA_BAR_EXP") -> Symbology.DATABAR_EXPANDED
            n == "DATA_BAR_LTD" -> Symbology.DATABAR_LIMITED
            n.startsWith("DATA_BAR") -> Symbology.DATABAR
            n == "DATA_MATRIX" -> Symbology.DATAMATRIX
            n == "EAN_8" -> Symbology.EAN8
            n == "EAN_13" || n == "ISBN" -> Symbology.EAN13
            n.startsWith("ITF") -> Symbology.ITF
            n == "MAXI_CODE" -> Symbology.MAXICODE
            n == "MICRO_PDF_417" -> Symbology.MICROPDF417
            n == "PDF_417" || n == "COMPACT_PDF_417" -> Symbology.PDF417
            n == "MICRO_QR_CODE" -> Symbology.MICROQR
            n == "RMQR_CODE" -> Symbology.RMQR
            n.startsWith("QR_CODE") -> Symbology.QR
            n == "UPC_A" -> Symbology.UPCA
            n == "UPC_E" -> Symbology.UPCE
            else -> null
        }
    }

    /** zxing-cpp formats to look for; empty = all. */
    fun toZxing(types: Set<Symbology>): Set<Format> = types.flatMap { type ->
        when (type) {
            Symbology.AZTEC -> listOf(Format.AZTEC)
            Symbology.CODABAR -> listOf(Format.CODABAR)
            Symbology.CODE39 -> listOf(Format.CODE_39)
            Symbology.CODE93 -> listOf(Format.CODE_93)
            Symbology.CODE128 -> listOf(Format.CODE_128)
            Symbology.DATABAR -> listOf(Format.DATA_BAR_OMNI, Format.DATA_BAR_STK, Format.DATA_BAR_STK_OMNI)
            Symbology.DATABAR_EXPANDED -> listOf(Format.DATA_BAR_EXP, Format.DATA_BAR_EXP_STK)
            Symbology.DATABAR_LIMITED -> listOf(Format.DATA_BAR_LTD)
            Symbology.DATAMATRIX -> listOf(Format.DATA_MATRIX)
            Symbology.EAN8 -> listOf(Format.EAN_8)
            Symbology.EAN13 -> listOf(Format.EAN_13)
            Symbology.ITF -> listOf(Format.ITF)
            Symbology.MAXICODE -> listOf(Format.MAXI_CODE)
            Symbology.PDF417 -> listOf(Format.PDF_417)
            Symbology.MICROPDF417 -> listOf(Format.MICRO_PDF_417)
            Symbology.QR -> listOf(Format.QR_CODE)
            Symbology.MICROQR -> listOf(Format.MICRO_QR_CODE)
            Symbology.RMQR -> listOf(Format.RMQR_CODE)
            Symbology.UPCA -> listOf(Format.UPC_A)
            Symbology.UPCE -> listOf(Format.UPC_E)
        }
    }.toSet()

    /** DataWedge `com.symbol.datawedge.label_type`, with or without `LABEL-TYPE-`. */
    fun fromDataWedge(labelType: String?): Symbology? {
        val t = labelType?.trim()?.uppercase()?.removePrefix("LABEL-TYPE-") ?: return null
        return when (t) {
            "EAN13", "BOOKLAND" -> Symbology.EAN13
            "EAN8" -> Symbology.EAN8
            "UPCA" -> Symbology.UPCA
            "UPCE0", "UPCE1" -> Symbology.UPCE
            "CODE128", "EAN128", "GS1-128", "ISBT128" -> Symbology.CODE128
            "CODE39", "TRIOPTIC39" -> Symbology.CODE39
            "CODE93" -> Symbology.CODE93
            "CODABAR" -> Symbology.CODABAR
            "I2OF5" -> Symbology.ITF
            "QRCODE" -> Symbology.QR
            "MICROQR" -> Symbology.MICROQR
            "DATAMATRIX", "GS1-DATAMATRIX" -> Symbology.DATAMATRIX
            "PDF417" -> Symbology.PDF417
            "MICROPDF" -> Symbology.MICROPDF417
            "AZTEC" -> Symbology.AZTEC
            "MAXICODE" -> Symbology.MAXICODE
            "GS1-DATABAR", "RSS14" -> Symbology.DATABAR
            "GS1-DATABAR-EXP", "RSSEXP" -> Symbology.DATABAR_EXPANDED
            "GS1-DATABAR-LIM", "RSSLIM" -> Symbology.DATABAR_LIMITED
            else -> null
        }
    }

    /** AIM symbology identifier (`]E0`, `]C1`, `]Q3` …), sent by Honeywell as `aimId`. */
    fun fromAim(aimId: String?): Symbology? {
        val id = aimId?.trim()?.removePrefix("]") ?: return null
        if (id.isEmpty()) return null
        return when (id[0]) {
            'A' -> Symbology.CODE39
            'C' -> Symbology.CODE128
            'd' -> Symbology.DATAMATRIX
            'E' -> if (id.getOrNull(1) == '4') Symbology.EAN8 else Symbology.EAN13
            'e' -> Symbology.DATABAR
            'F' -> Symbology.CODABAR
            'G' -> Symbology.CODE93
            'I' -> Symbology.ITF
            'L' -> Symbology.PDF417
            'Q' -> Symbology.QR
            'U' -> Symbology.MAXICODE
            'z' -> Symbology.AZTEC
            else -> null
        }
    }
}

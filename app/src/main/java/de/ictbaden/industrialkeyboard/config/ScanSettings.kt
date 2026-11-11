package de.ictbaden.industrialkeyboard.config

/** Barcode types by their stable name in the configuration (`scanner.camera.symbologies`, rules). */
enum class Symbology(override val key: String, val label: String, val barcode: Boolean = true) : Keyed {
    AZTEC("aztec", "Aztec"),
    CODABAR("codabar", "Codabar"),
    CODE39("code39", "Code 39"),
    CODE93("code93", "Code 93"),
    CODE128("code128", "Code 128 / GS1-128"),
    DATABAR("databar", "GS1 DataBar"),
    DATABAR_EXPANDED("databarexpanded", "GS1 DataBar Expanded"),
    DATABAR_LIMITED("databarlimited", "GS1 DataBar Limited"),
    DATAMATRIX("datamatrix", "Data Matrix"),
    EAN8("ean8", "EAN-8"),
    EAN13("ean13", "EAN-13"),
    ITF("itf", "Interleaved 2 of 5"),
    MAXICODE("maxicode", "MaxiCode"),
    PDF417("pdf417", "PDF417"),
    MICROPDF417("micropdf417", "MicroPDF417"),
    QR("qr", "QR Code"),
    MICROQR("microqr", "Micro QR"),
    RMQR("rmqr", "rMQR"),
    UPCA("upca", "UPC-A"),
    UPCE("upce", "UPC-E"),

    /** Text read by OCR – for `scanner.data` rules only, not a camera barcode type. */
    OCR("ocr", "Text (OCR)", barcode = false),

    /** NFC tag – for `scanner.data` rules only. */
    NFC("nfc", "NFC", barcode = false),
    ;

    companion object {
        fun byKey(key: String): Symbology? = entries.firstOrNull { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

/** Which codes in the camera picture count. */
enum class AimMode(override val key: String) : Keyed {
    /** Only codes whose centre lies inside the aiming frame. */
    FRAME("frame"),

    /** Only the code under the crosshair (pick list, for labels with many codes). */
    CENTER("center"),

    /** The whole picture. */
    ANYWHERE("anywhere"),
}

enum class TextCase(override val key: String) : Keyed {
    NONE("none"),
    UPPER("upper"),
    LOWER("lower"),
}

/** How GS1 codes read by the camera are written. */
enum class Gs1Mode(override val key: String) : Keyed {
    /** Human readable: `(01)04012345678901(10)ABC`. */
    HRI("hri"),

    /** Raw with the group separator U+001D between variable-length fields. */
    RAW("raw"),
}

enum class RuleAction(override val key: String) : Keyed {
    SEND("send"),
    DROP("drop"),
}

/** Settings of the camera scanner (`scanner.camera`). */
data class CameraSettings(
    /** Codes the camera reads; empty = all. */
    val symbologies: Set<Symbology> = emptySet(),
    val aim: AimMode = AimMode.FRAME,
    /** Camera stays open and types every new code. */
    val continuous: Boolean = false,
    /** Continuous mode: the same code counts again only after this time. 0–10000. */
    val duplicateDelayMs: Int = 1500,
    /** The same code has to be read in this many frames in a row. 1–3. */
    val confirmReads: Int = 1,
    /** Accepted text length, 0 = no limit. */
    val minLength: Int = 0,
    val maxLength: Int = 0,
    /** Zoom when the camera opens, 1.0–4.0. */
    val zoom: Float = 1f,
    /** Torch on when the camera opens. */
    val torch: Boolean = false,
    /** Also light-on-dark codes. */
    val inverted: Boolean = true,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
    /** Closes after this time without a code; 0 = never. 0–600. */
    val timeoutSeconds: Int = 60,
)

/** A text replacement; with [regex] a Java regular expression with `$1` groups. */
data class Replacement(val find: String, val with: String, val regex: Boolean = false)

/**
 * A formatting rule for some codes. The first matching rule wins; its fields that are not `null`
 * replace the defaults of [DataSettings] for that code.
 */
data class ScanRule(
    val name: String = "",
    /** Empty = every type, also unknown ones. */
    val symbologies: Set<Symbology> = emptySet(),
    val minLength: Int = 0,
    val maxLength: Int = 0,
    val startsWith: String = "",
    /** Java regex that has to match the whole (trimmed) text; empty = any. */
    val pattern: String = "",
    val action: RuleAction = RuleAction.SEND,
    val prefix: String? = null,
    val suffix: String? = null,
    val case: TextCase? = null,
    val stripLeading: Int? = null,
    val stripTrailing: Int? = null,
    val removeSpaces: Boolean? = null,
    val replace: List<Replacement>? = null,
)

/** How a barcode text is changed before the keyboard types it (`scanner.data`). */
data class DataSettings(
    /** Macro typed before the code (`{TAB}`, `{F5}` …). */
    val prefix: String = "",
    /** Macro typed after the code; `null` = `scanner.input.suffix` (Enter, Tab or nothing). */
    val suffix: String? = null,
    /** Spaces and control characters (except GS) at both ends are removed. */
    val trim: Boolean = true,
    val removeSpaces: Boolean = false,
    val case: TextCase = TextCase.NONE,
    val stripLeading: Int = 0,
    val stripTrailing: Int = 0,
    val replace: List<Replacement> = emptyList(),
    val gs1: Gs1Mode = Gs1Mode.HRI,
    /** Macro typed in place of the GS separator (U+001D), e.g. `{TAB}` or `|`; `null` = keep it. */
    val gsReplacement: String? = null,
    val rules: List<ScanRule> = emptyList(),
    /** With rules and none matching: type the code anyway (`SEND`) or ignore it (`DROP`). */
    val unmatched: RuleAction = RuleAction.SEND,
)

/** How a recognised text line is taken (`scanner.ocr.mode`). */
enum class OcrMode(override val key: String) : Keyed {
    /** Lines in the frame are shown as boxes; a tap takes one. */
    PICK("pick"),

    /** The first line in the frame matching `scanner.ocr.pattern` is taken automatically. */
    PATTERN("pattern"),
}

/** Cleanup of a recognised line before length and pattern checks. */
enum class OcrCharset(override val key: String) : Keyed {
    ANY("any"),

    /** Digits plus `,` `.` `-`. */
    DIGITS("digits"),

    /** Letters and digits. */
    ALNUM("alnum"),

    /** Letters and digits, upper case. */
    UPPER("upper"),
}

/** Text recognition with the camera (`scanner.ocr`). */
data class OcrSettings(
    val enabled: Boolean = true,
    val mode: OcrMode = OcrMode.PICK,
    /** Java regex the whole line has to match; required for [OcrMode.PATTERN]. */
    val pattern: String? = null,
    /** The camera opens in text mode. */
    val startInOcr: Boolean = false,
    /** Pattern mode: the same text in this many frames in a row. 1–5. */
    val confirmReads: Int = 2,
    val minLength: Int = 0,
    val maxLength: Int = 0,
    val charset: OcrCharset = OcrCharset.ANY,
)

/** What an NFC tag gives (`scanner.nfc.read`). */
enum class NfcRead(override val key: String) : Keyed {
    UID("uid"),
    NDEF("ndef"),

    /** The NDEF text or URI, the UID when the tag has none. */
    NDEF_OR_UID("ndefOrUid"),
}

enum class UidFormat(override val key: String) : Keyed {
    HEX("hex"),
    DECIMAL("decimal"),
}

enum class NdefRecords(override val key: String) : Keyed {
    FIRST("first"),
    ALL_LINES("allLines"),
}

/** Reading NFC tags (`scanner.nfc`). */
data class NfcSettings(
    val enabled: Boolean = true,
    val read: NfcRead = NfcRead.NDEF_OR_UID,
    val uidFormat: UidFormat = UidFormat.HEX,
    val uidReversed: Boolean = false,
    val uidSeparator: String = "",
    val ndefRecord: NdefRecords = NdefRecords.FIRST,
    val beep: Boolean = true,
    val vibrate: Boolean = true,
)

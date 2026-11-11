package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.NdefRecords
import de.ictbaden.industrialkeyboard.config.NfcRead
import de.ictbaden.industrialkeyboard.config.NfcSettings
import de.ictbaden.industrialkeyboard.config.UidFormat
import java.math.BigInteger
import java.util.Locale

/**
 * What an NFC tag gives as text (`scanner.nfc`): the UID as hex or decimal, or the text and URI
 * records of its NDEF message. Pure, without Android NFC classes, so it can be tested.
 */
object NfcFormat {

    /** An NDEF record as it is stored: type name format, type and payload. */
    class Record(val tnf: Short, val type: ByteArray, val payload: ByteArray)

    const val TNF_WELL_KNOWN: Short = 0x01
    const val TNF_MIME_MEDIA: Short = 0x02
    const val TNF_ABSOLUTE_URI: Short = 0x03
    private val RTD_TEXT = byteArrayOf(0x54) // "T"
    private val RTD_URI = byteArrayOf(0x55) // "U"

    /** The text to type, or `null` when the tag has nothing of the wanted kind. */
    fun text(uid: ByteArray?, records: List<Record>, settings: NfcSettings): String? {
        val ndef = { ndefText(records, settings.ndefRecord) }
        val id = { uid?.takeIf { it.isNotEmpty() }?.let { uid(it, settings) } }
        return when (settings.read) {
            NfcRead.UID -> id()
            NfcRead.NDEF -> ndef()
            NfcRead.NDEF_OR_UID -> ndef() ?: id()
        }
    }

    /** UID as upper-case hex bytes (with separator) or as unsigned decimal number. */
    fun uid(bytes: ByteArray, settings: NfcSettings): String {
        val ordered = if (settings.uidReversed) bytes.reversedArray() else bytes
        return when (settings.uidFormat) {
            UidFormat.HEX -> ordered.joinToString(settings.uidSeparator) { String.format(Locale.ROOT, "%02X", it.toInt() and 0xFF) }
            UidFormat.DECIMAL -> BigInteger(1, ordered).toString()
        }
    }

    /** Text and URI records (and MIME `text/…`) joined by line breaks, or only the first. */
    fun ndefText(records: List<Record>, mode: NdefRecords): String? {
        val texts = records.mapNotNull(::recordText).filter { it.isNotEmpty() }
        if (texts.isEmpty()) return null
        return if (mode == NdefRecords.FIRST) texts.first() else texts.joinToString("\n")
    }

    fun recordText(record: Record): String? = when {
        record.tnf == TNF_WELL_KNOWN && record.type.contentEquals(RTD_TEXT) -> textRecord(record.payload)
        record.tnf == TNF_WELL_KNOWN && record.type.contentEquals(RTD_URI) -> uriRecord(record.payload)
        record.tnf == TNF_ABSOLUTE_URI -> String(record.type, Charsets.UTF_8)
        record.tnf == TNF_MIME_MEDIA && String(record.type, Charsets.US_ASCII).lowercase(Locale.ROOT).startsWith("text/") ->
            String(record.payload, Charsets.UTF_8)
        else -> null
    }

    /** NFC Forum text record: status byte (bit 7 = UTF-16, bits 0–5 = length of the language code). */
    fun textRecord(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val status = payload[0].toInt() and 0xFF
        val languageLength = status and 0x3F
        if (1 + languageLength > payload.size) return null
        val charset = if (status and 0x80 != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }

    /** NFC Forum URI record: first byte is the abbreviation of the prefix. */
    fun uriRecord(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val prefix = URI_PREFIXES.getOrElse(payload[0].toInt() and 0xFF) { "" }
        return prefix + String(payload, 1, payload.size - 1, Charsets.UTF_8)
    }

    private val URI_PREFIXES = listOf(
        "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
        "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://", "nfs://", "ftp://",
        "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:", "pop:", "sip:", "sips:", "tftp:",
        "btspp://", "btl2cap://", "btgoep://", "tcpobex://", "irdaobex://", "file://", "urn:epc:id:",
        "urn:epc:tag:", "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
    )
}

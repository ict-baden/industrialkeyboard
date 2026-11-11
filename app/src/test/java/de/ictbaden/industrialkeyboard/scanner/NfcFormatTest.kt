package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.NdefRecords
import de.ictbaden.industrialkeyboard.config.NfcRead
import de.ictbaden.industrialkeyboard.config.NfcSettings
import de.ictbaden.industrialkeyboard.config.UidFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NfcFormatTest {

    private val uid = byteArrayOf(0x04, 0xA2.toByte(), 0x3B, 0xFF.toByte())

    private fun text(value: String, language: String = "de", utf16: Boolean = false): NfcFormat.Record {
        val bytes = value.toByteArray(if (utf16) Charsets.UTF_16 else Charsets.UTF_8)
        val status = (if (utf16) 0x80 else 0) or language.length
        val payload = byteArrayOf(status.toByte()) + language.toByteArray(Charsets.US_ASCII) + bytes
        return NfcFormat.Record(NfcFormat.TNF_WELL_KNOWN, byteArrayOf(0x54), payload)
    }

    private fun uri(prefix: Int, rest: String) =
        NfcFormat.Record(NfcFormat.TNF_WELL_KNOWN, byteArrayOf(0x55), byteArrayOf(prefix.toByte()) + rest.toByteArray())

    @Test
    fun `uid as hex, reversed, with separator, as decimal`() {
        assertEquals("04A23BFF", NfcFormat.uid(uid, NfcSettings()))
        assertEquals("FF:3B:A2:04", NfcFormat.uid(uid, NfcSettings(uidReversed = true, uidSeparator = ":")))
        assertEquals("77741055", NfcFormat.uid(uid, NfcSettings(uidFormat = UidFormat.DECIMAL)))
        assertEquals("4282098180", NfcFormat.uid(uid, NfcSettings(uidFormat = UidFormat.DECIMAL, uidReversed = true)))
    }

    @Test
    fun `text and uri records`() {
        assertEquals("Lager 4", NfcFormat.recordText(text("Lager 4")))
        assertEquals("Größe", NfcFormat.recordText(text("Größe", utf16 = true)))
        assertEquals("https://example.com/a", NfcFormat.recordText(uri(4, "example.com/a")))
        assertEquals("tel:+49123", NfcFormat.recordText(uri(5, "+49123")))
        val mime = NfcFormat.Record(NfcFormat.TNF_MIME_MEDIA, "text/plain".toByteArray(), "Platz 7".toByteArray())
        assertEquals("Platz 7", NfcFormat.recordText(mime))
        val other = NfcFormat.Record(NfcFormat.TNF_MIME_MEDIA, "application/octet-stream".toByteArray(), byteArrayOf(1, 2))
        assertNull(NfcFormat.recordText(other))
        assertNull(NfcFormat.textRecord(byteArrayOf(0x05, 'd'.code.toByte())))
    }

    @Test
    fun `what is read`() {
        val records = listOf(text("A-12"), uri(4, "x.de"))
        assertEquals("A-12", NfcFormat.text(uid, records, NfcSettings()))
        assertEquals("A-12\nhttps://x.de", NfcFormat.text(uid, records, NfcSettings(ndefRecord = NdefRecords.ALL_LINES)))
        assertEquals("04A23BFF", NfcFormat.text(uid, records, NfcSettings(read = NfcRead.UID)))
        // Without NDEF: the UID in ndefOrUid, nothing in ndef.
        assertEquals("04A23BFF", NfcFormat.text(uid, emptyList(), NfcSettings()))
        assertNull(NfcFormat.text(uid, emptyList(), NfcSettings(read = NfcRead.NDEF)))
        assertNull(NfcFormat.text(null, emptyList(), NfcSettings()))
    }
}

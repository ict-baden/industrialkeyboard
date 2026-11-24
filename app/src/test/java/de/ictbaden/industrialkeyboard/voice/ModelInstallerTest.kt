package de.ictbaden.industrialkeyboard.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Checksum, safe unpacking and the download of language packs (issue #24), with small synthetic files. */
class ModelInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, ByteArray?>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name))
                data?.let(zip::write)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** A tiny "Vosk model": the files [ModelInstaller.isModel] looks for. */
    private fun modelZip(folder: String = "vosk-model-small-de-0.15", marker: String = "new") = zip(
        "$folder/" to null,
        "$folder/am/final.mdl" to marker.toByteArray(),
        "$folder/conf/mfcc.conf" to "--sample-frequency=16000".toByteArray(),
        "$folder/graph/HCLr.fst" to ByteArray(2048) { it.toByte() },
    )

    private fun file(bytes: ByteArray) = temp.newFile().apply { writeBytes(bytes) }

    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun modelFor(bytes: ByteArray) = VoiceModel("de", "test.zip", "vosk-model-small-de-0.15", sha(bytes), bytes.size.toLong())

    private fun expect(reason: ModelException.Reason, block: () -> Unit) {
        try {
            block()
            fail("expected $reason")
        } catch (e: ModelException) {
            assertEquals(e.message, reason, e.reason)
        }
    }

    @Test
    fun `installs a verified model atomically into the language folder`() {
        val bytes = modelZip()
        val zipFile = file(bytes)
        ModelInstaller.verify(zipFile, modelFor(bytes))
        val models = temp.newFolder("models")
        val dir = ModelInstaller.install(zipFile, models, "de", "vosk-model-small-de-0.15")
        assertEquals(File(models, "de"), dir)
        assertTrue(ModelInstaller.isModel(dir))
        assertEquals("new", File(dir, "am/final.mdl").readText())
        assertEquals("only the language folder, no temporary ones", listOf("de"), models.list()!!.toList())
    }

    @Test
    fun `wrong checksum or size is rejected before unpacking`() {
        val bytes = modelZip()
        val zipFile = file(bytes)
        expect(ModelException.Reason.CHECKSUM) { ModelInstaller.verify(zipFile, modelFor(bytes).copy(sha256 = "0".repeat(64))) }
        expect(ModelException.Reason.CHECKSUM) { ModelInstaller.verify(zipFile, modelFor(bytes).copy(bytes = bytes.size + 1L)) }
    }

    @Test
    fun `zip slip paths are refused and nothing is left behind`() {
        val models = temp.newFolder("models")
        for (evil in listOf("../evil.txt", "a/../../evil.txt", "/etc/evil", "C:/evil", "a\\..\\..\\evil")) {
            expect(ModelException.Reason.INVALID) { ModelInstaller.install(file(zip("ok/am/final.mdl" to ByteArray(1), evil to ByteArray(1))), models, "de", "x") }
        }
        assertFalse(File(temp.root, "evil.txt").exists())
        assertEquals(emptyList<String>(), models.list()!!.toList())
    }

    @Test
    fun `unpacked size is limited while writing`() {
        val models = temp.newFolder("models")
        expect(ModelException.Reason.TOO_LARGE) { ModelInstaller.install(file(modelZip()), models, "de", "x", maxBytes = 1_000) }
        assertEquals(emptyList<String>(), models.list()!!.toList())
    }

    @Test
    fun `a zip without a model or no zip at all is invalid`() {
        val models = temp.newFolder("models")
        expect(ModelException.Reason.INVALID) { ModelInstaller.install(file(zip("readme.txt" to ByteArray(3))), models, "de", "x") }
        expect(ModelException.Reason.INVALID) { ModelInstaller.install(file("not a zip at all".toByteArray()), models, "de", "x") }
    }

    @Test
    fun `cancel stops unpacking and keeps the old model`() {
        val models = temp.newFolder("models")
        ModelInstaller.install(file(modelZip(marker = "old")), models, "de", "x")
        var calls = 0
        expect(ModelException.Reason.CANCELLED) { ModelInstaller.install(file(modelZip(marker = "new")), models, "de", "x", cancelled = { ++calls > 2 }) }
        assertEquals("old", File(models, "de/am/final.mdl").readText())
        assertEquals(listOf("de"), models.list()!!.toList())
    }

    @Test
    fun `a new model replaces the old one`() {
        val models = temp.newFolder("models")
        ModelInstaller.install(file(modelZip(marker = "old")), models, "de", "x")
        ModelInstaller.install(file(modelZip(marker = "new")), models, "de", "x")
        assertEquals("new", File(models, "de/am/final.mdl").readText())
        assertEquals(listOf("de"), models.list()!!.toList())
    }

    @Test
    fun `model at the top of the zip works too`() {
        val models = temp.newFolder("models")
        val dir = ModelInstaller.install(file(zip("am/final.mdl" to ByteArray(1), "conf/model.conf" to ByteArray(1))), models, "en", "x")
        assertTrue(ModelInstaller.isModel(dir))
    }

    @Test
    fun `leftovers of an interrupted install are removed`() {
        val models = temp.newFolder("models")
        File(models, ".tmp-de-1/am").mkdirs()
        File(models, ".old-de-2").mkdirs()
        File(models, "de").mkdirs()
        ModelInstaller.cleanUp(models)
        assertEquals(listOf("de"), models.list()!!.toList())
    }

    @Test
    fun `download is verified, installed and the temporary file removed`() {
        val bytes = modelZip()
        val work = temp.newFolder("work")
        val models = temp.newFolder("models")
        var last = 0L
        val dir = ModelDownloader { ByteArrayInputStream(bytes) to bytes.size.toLong() }
            .download("https://example.test/x.zip", modelFor(bytes), work, models, { false }) { last = it }
        assertTrue(ModelInstaller.isModel(dir))
        assertEquals(bytes.size.toLong(), last)
        assertEquals(emptyList<String>(), work.list()!!.toList())
    }

    @Test
    fun `download with a wrong checksum installs nothing`() {
        val bytes = modelZip()
        val other = modelZip(marker = "tampered")
        val work = temp.newFolder("work")
        val models = temp.newFolder("models")
        expect(ModelException.Reason.CHECKSUM) {
            ModelDownloader { ByteArrayInputStream(other) to -1L }.download("https://example.test/x.zip", modelFor(bytes), work, models, { false }) {}
        }
        expect(ModelException.Reason.CHECKSUM) {
            ModelDownloader { ByteArrayInputStream(bytes) to bytes.size + 5L }.download("https://example.test/x.zip", modelFor(bytes), work, models, { false }) {}
        }
        // Longer than announced: stops at the expected size instead of filling the storage.
        expect(ModelException.Reason.CHECKSUM) {
            ModelDownloader { ByteArrayInputStream(bytes + ByteArray(10_000)) to -1L }.download("https://example.test/x.zip", modelFor(bytes), work, models, { false }) {}
        }
        assertEquals(emptyList<String>(), models.list()!!.toList())
        assertEquals(emptyList<String>(), work.list()!!.toList())
    }

    @Test
    fun `download can be cancelled and network errors are reported as such`() {
        val bytes = modelZip()
        val work = temp.newFolder("work")
        val models = temp.newFolder("models")
        expect(ModelException.Reason.CANCELLED) {
            ModelDownloader { ByteArrayInputStream(bytes) to -1L }.download("https://example.test/x.zip", modelFor(bytes), work, models, { true }) {}
        }
        expect(ModelException.Reason.NETWORK) {
            ModelDownloader { throw IOException("HTTP 404") }.download("https://example.test/x.zip", modelFor(bytes), work, models, { false }) {}
        }
        assertEquals(emptyList<String>(), work.list()!!.toList())
    }

    @Test
    fun `only https is opened`() {
        try {
            ModelDownloader.openHttps("http://example.test/x.zip")
            fail("http opened")
        } catch (e: IOException) {
            assertTrue(e.message.orEmpty().contains("https"))
        }
    }

    @Test
    fun `catalog - fixed addresses, checksums and the language of the keyboard`() {
        assertEquals(
            "https://github.com/ict-baden/industrialkeyboard/releases/download/voice-models-1/vosk-model-small-de-0.15.zip",
            VoiceModelCatalog.url(VoiceModelCatalog.GERMAN, null),
        )
        assertEquals("https://intranet.example/m/vosk-model-small-en-us-0.15.zip", VoiceModelCatalog.url(VoiceModelCatalog.ENGLISH, "https://intranet.example/m"))
        assertEquals(46, VoiceModelCatalog.GERMAN.megabytes)
        assertEquals(41, VoiceModelCatalog.ENGLISH.megabytes)
        VoiceModelCatalog.ALL.forEach { assertTrue(Regex("[0-9a-f]{64}").matches(it.sha256)) }
        assertEquals("de", VoiceModelCatalog.languageFor(Locale.GERMANY))
        assertEquals("de", VoiceModelCatalog.languageFor(Locale.forLanguageTag("de-CH")))
        assertEquals("en", VoiceModelCatalog.languageFor(Locale.FRANCE))
        assertEquals("en", VoiceModelCatalog.languageFor(Locale.US))
        assertEquals(VoiceModelCatalog.GERMAN, VoiceModelCatalog.bySha256(VoiceModelCatalog.GERMAN.sha256.uppercase()))
    }

    @Test
    fun `base address must be https without query`() {
        assertNull(VoiceModelCatalog.normalizeBaseUrl("http://x.example/"))
        assertNull(VoiceModelCatalog.normalizeBaseUrl("https:///nohost"))
        assertNull(VoiceModelCatalog.normalizeBaseUrl("https://x.example/?a=b"))
        assertNull(VoiceModelCatalog.normalizeBaseUrl("  "))
        assertEquals("https://x.example/a/", VoiceModelCatalog.normalizeBaseUrl("https://x.example/a"))
    }

    @Test
    fun `language of a model folder`() {
        assertEquals("de", VoiceModelCatalog.languageOfFolder("vosk-model-small-de-0.15"))
        assertEquals("en", VoiceModelCatalog.languageOfFolder("vosk-model-small-en-us-0.15"))
        assertEquals("de", VoiceModelCatalog.languageOfFolder("vosk-model-de-0.21"))
        assertEquals("en", VoiceModelCatalog.languageOfFolder("EN"))
        assertNull(VoiceModelCatalog.languageOfFolder("models"))
        assertEquals("vosk-model-small-de-0.15", ModelInstaller.topFolder(file(modelZip())))
    }
}

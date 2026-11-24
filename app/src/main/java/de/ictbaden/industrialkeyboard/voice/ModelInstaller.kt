package de.ictbaden.industrialkeyboard.voice

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/** Why a language pack could not be downloaded, imported or installed; shown with an error code. */
class ModelException(val reason: Reason, message: String, cause: Throwable? = null) : IOException(message, cause) {
    enum class Reason {
        /** No connection, server error, timeout. */
        NETWORK,

        /** Size or SHA-256 differ from the fixed values: nothing is unpacked. */
        CHECKSUM,

        /** No ZIP, a path leaving the target folder, too many files, or no Vosk model inside. */
        INVALID,

        /** More than [ModelInstaller.MAX_UNPACKED_BYTES] unpacked. */
        TOO_LARGE,

        /** Storage full or a folder could not be written or renamed. */
        STORAGE,

        /** The user cancelled. */
        CANCELLED,
    }
}

/**
 * Checks and unpacks a language pack. Plain Java I/O (unit-tested without Android): the ZIP is
 * unpacked only after the checksum matched, into a temporary folder next to the target, with every
 * path checked (no Zip Slip), a size and file limit, and then renamed into place in one step, so a
 * half-written model is never used.
 */
object ModelInstaller {

    /** Unpacked size limit; the small Vosk models need about 70 MB. */
    const val MAX_UNPACKED_BYTES = 600L * 1024 * 1024

    /** File limit per ZIP (a small Vosk model has about 30). */
    const val MAX_ENTRIES = 5_000

    private const val BUFFER = 64 * 1024

    /** Name of the small file in an installed model folder that says where it came from. */
    const val SOURCE_FILE = ".industrialkeyboard-model"

    /**
     * Copies [input] to [output] and returns the SHA-256 (hex) of what was copied. Stops with
     * [ModelException.Reason.CHECKSUM] beyond [maxBytes], with `CANCELLED` as soon as [cancelled] says so.
     */
    fun copyHashing(input: InputStream, output: OutputStream, maxBytes: Long, cancelled: () -> Boolean, progress: (Long) -> Unit = {}): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        var total = 0L
        while (true) {
            if (cancelled()) throw ModelException(ModelException.Reason.CANCELLED, "cancelled")
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw ModelException(ModelException.Reason.CHECKSUM, "more than $maxBytes bytes")
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            progress(total)
        }
        return hex(digest.digest())
    }

    fun sha256(file: File, cancelled: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER)
        file.inputStream().use { input ->
            while (true) {
                if (cancelled()) throw ModelException(ModelException.Reason.CANCELLED, "cancelled")
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return hex(digest.digest())
    }

    /** Throws [ModelException.Reason.CHECKSUM] unless [file] has exactly the size and SHA-256 of [model]. */
    fun verify(file: File, model: VoiceModel, cancelled: () -> Boolean = { false }) {
        if (file.length() != model.bytes) throw ModelException(ModelException.Reason.CHECKSUM, "size ${file.length()} instead of ${model.bytes}")
        val actual = sha256(file, cancelled)
        if (!actual.equals(model.sha256, ignoreCase = true)) throw ModelException(ModelException.Reason.CHECKSUM, "SHA-256 $actual instead of ${model.sha256}")
    }

    /** The top folder of [zip] (`vosk-model-small-de-0.15`), to tell the language of an imported file; `null` if there is none. */
    fun topFolder(zip: File): String? = try {
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            generateSequence { input.nextEntry }.take(MAX_ENTRIES).map { it.name.replace('\\', '/').trimStart('/') }
                .mapNotNull { name -> name.substringBefore('/', "").takeIf { it.isNotEmpty() } }
                .firstOrNull()
        }
    } catch (e: IOException) {
        null
    }

    /**
     * Unpacks [zip] (already verified) as the model for [language] into `[modelsDir]/[language]` and
     * returns that folder. An installed model of the language is replaced only once the new one is
     * complete; on any error the old one stays and nothing is left behind.
     */
    fun install(zip: File, modelsDir: File, language: String, source: String, cancelled: () -> Boolean = { false }, maxBytes: Long = MAX_UNPACKED_BYTES): File {
        require(Regex("[a-z]{2}").matches(language)) { "language $language" }
        if (!modelsDir.isDirectory && !modelsDir.mkdirs()) throw ModelException(ModelException.Reason.STORAGE, "cannot create $modelsDir")
        val temp = File(modelsDir, ".tmp-$language-${System.nanoTime()}")
        try {
            if (!temp.mkdirs()) throw ModelException(ModelException.Reason.STORAGE, "cannot create $temp")
            unzip(zip, temp, cancelled, maxBytes)
            val root = modelRoot(temp) ?: throw ModelException(ModelException.Reason.INVALID, "no Vosk model in the ZIP")
            File(root, SOURCE_FILE).writeText(source)
            return replace(root, File(modelsDir, language))
        } catch (e: ModelException) {
            throw e
        } catch (e: ZipException) {
            throw ModelException(ModelException.Reason.INVALID, "not a ZIP: ${e.message}", e)
        } catch (e: IOException) {
            throw ModelException(ModelException.Reason.STORAGE, e.message ?: "write failed", e)
        } finally {
            temp.deleteRecursively()
        }
    }

    /** Unpacks every entry of [zip] below [target]; rejects paths outside it, too many entries and too many bytes. */
    internal fun unzip(zip: File, target: File, cancelled: () -> Boolean, maxBytes: Long) {
        val base = target.canonicalFile
        var total = 0L
        var entries = 0
        ZipInputStream(zip.inputStream().buffered()).use { input ->
            val buffer = ByteArray(BUFFER)
            var entry: ZipEntry? = input.nextEntry
            if (entry == null) throw ModelException(ModelException.Reason.INVALID, "empty or no ZIP")
            while (entry != null) {
                if (cancelled()) throw ModelException(ModelException.Reason.CANCELLED, "cancelled")
                if (++entries > MAX_ENTRIES) throw ModelException(ModelException.Reason.INVALID, "more than $MAX_ENTRIES entries")
                val out = safeTarget(base, entry.name)
                if (entry.isDirectory) {
                    if (!out.isDirectory && !out.mkdirs()) throw ModelException(ModelException.Reason.STORAGE, "cannot create ${entry.name}")
                } else {
                    out.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw ModelException(ModelException.Reason.STORAGE, "cannot create ${it.name}") }
                    FileOutputStream(out).use { output ->
                        while (true) {
                            if (cancelled()) throw ModelException(ModelException.Reason.CANCELLED, "cancelled")
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            // Counted while writing, not from the header: a ZIP may state any size.
                            if (total > maxBytes) throw ModelException(ModelException.Reason.TOO_LARGE, "more than $maxBytes bytes unpacked")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                entry = input.nextEntry
            }
        }
    }

    /** The file for [name] inside [base]; a path that would leave it (`../`, absolute, drive letter) is rejected (Zip Slip). */
    internal fun safeTarget(base: File, name: String): File {
        val clean = name.replace('\\', '/')
        if (clean.isEmpty() || clean.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(clean) || clean.split('/').any { it == ".." }) {
            throw ModelException(ModelException.Reason.INVALID, "unsafe path in ZIP: $name")
        }
        val out = File(base, clean).canonicalFile
        if (!out.path.startsWith(base.path + File.separator)) throw ModelException(ModelException.Reason.INVALID, "unsafe path in ZIP: $name")
        return out
    }

    /** The folder that holds the Vosk model: [dir] itself or its only subfolder. */
    fun modelRoot(dir: File): File? {
        if (isModel(dir)) return dir
        val children = dir.listFiles()?.filter { it.isDirectory } ?: return null
        return children.singleOrNull()?.takeIf(::isModel)
    }

    /** A Vosk model folder: acoustic model and configuration are there. */
    fun isModel(dir: File): Boolean = File(dir, "am/final.mdl").isFile && File(dir, "conf").isDirectory

    /** Moves [fresh] to [target] in one rename; an older [target] is moved aside first and removed afterwards. */
    private fun replace(fresh: File, target: File): File {
        val old = File(target.parentFile, ".old-${target.name}-${System.nanoTime()}")
        val hadOld = target.exists()
        if (hadOld && !target.renameTo(old)) throw ModelException(ModelException.Reason.STORAGE, "cannot move the old model aside")
        if (!fresh.renameTo(target)) {
            if (hadOld) old.renameTo(target)
            throw ModelException(ModelException.Reason.STORAGE, "cannot move the model into place")
        }
        old.deleteRecursively()
        return target
    }

    /** Leftovers of an interrupted install (app killed while unpacking). */
    fun cleanUp(modelsDir: File) {
        modelsDir.listFiles()?.filter { it.name.startsWith(".tmp-") || it.name.startsWith(".old-") }?.forEach { it.deleteRecursively() }
    }

    fun sizeOf(dir: File): Long = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}

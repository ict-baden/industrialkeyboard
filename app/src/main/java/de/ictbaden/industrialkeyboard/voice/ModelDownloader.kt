package de.ictbaden.industrialkeyboard.voice

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * The only network code of the app: downloads one language pack (issue #24) over https, on an
 * explicit request. Sends nothing but the request for the file (no identifiers, no cookies); the
 * download is checked against size and SHA-256 of [VoiceModel] before [ModelInstaller] unpacks it.
 */
class ModelDownloader(
    /** Opens [url] and returns the body and its length (-1 = unknown); replaced in tests. */
    private val open: (url: String) -> Pair<InputStream, Long> = ::openHttps,
) {

    /**
     * Downloads [model] from [url] into [workDir], verifies and installs it below [modelsDir].
     * [progress] gets the bytes so far; [cancelled] is asked between blocks.
     */
    fun download(url: String, model: VoiceModel, workDir: File, modelsDir: File, cancelled: () -> Boolean, progress: (Long) -> Unit): File {
        if (!workDir.isDirectory && !workDir.mkdirs()) throw ModelException(ModelException.Reason.STORAGE, "cannot create $workDir")
        val part = File(workDir, "voice-${model.language}.zip.part")
        try {
            val (body, length) = try {
                open(url)
            } catch (e: IOException) {
                throw ModelException(ModelException.Reason.NETWORK, e.message ?: "connection failed", e)
            }
            if (length >= 0 && length != model.bytes) {
                body.close()
                throw ModelException(ModelException.Reason.CHECKSUM, "server sends $length bytes instead of ${model.bytes}")
            }
            val sha = try {
                body.use { input -> part.outputStream().use { ModelInstaller.copyHashing(input, it, model.bytes, cancelled, progress) } }
            } catch (e: ModelException) {
                throw e
            } catch (e: IOException) {
                // Reading broke off (connection lost) or the file could not be written.
                val full = generateSequence<Throwable>(e) { it.cause }.any { it.message.orEmpty().let { m -> "ENOSPC" in m || "No space" in m } }
                val reason = if (full) ModelException.Reason.STORAGE else ModelException.Reason.NETWORK
                throw ModelException(reason, e.message ?: "download failed", e)
            }
            if (part.length() != model.bytes || !sha.equals(model.sha256, ignoreCase = true)) {
                throw ModelException(ModelException.Reason.CHECKSUM, "got ${part.length()} bytes, SHA-256 $sha")
            }
            return ModelInstaller.install(part, modelsDir, model.language, model.folder, cancelled)
        } finally {
            part.delete()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000

        /** https only (redirects too: HttpURLConnection does not follow a switch to http). */
        fun openHttps(url: String): Pair<InputStream, Long> {
            if (!url.startsWith("https://", ignoreCase = true)) throw IOException("not https: $url")
            val connection = URL(url).openConnection() as? HttpsURLConnection ?: throw IOException("not https: $url")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.setRequestProperty("Accept-Encoding", "identity")
            // Instead of Android's default (Dalvik/…, Android version, device model and build).
            connection.setRequestProperty("User-Agent", "IndustrialKeyboard")
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                throw IOException("HTTP $code")
            }
            return connection.inputStream to connection.contentLengthLong
        }
    }
}

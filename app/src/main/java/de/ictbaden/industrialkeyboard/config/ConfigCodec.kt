package de.ictbaden.industrialkeyboard.config

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Configuration text for barcodes: plain JSON when it is short, otherwise `ICTZ1:` plus
 * base64url (without padding) of the raw-DEFLATE compressed UTF-8 JSON. Shared format of the
 * ICT-Baden Android apps; every place that accepts a JSON configuration also accepts `ICTZ1:`.
 */
object ConfigCodec {
    const val PREFIX = "ICTZ1:"

    /** Up to this length the barcode carries plain JSON, readable by older versions and any reader. */
    const val PLAIN_LIMIT = 1800

    /** Inflated size limit against "zip bombs". */
    const val MAX_INFLATED = 1_000_000

    /** Minified JSON, compressed when longer than [PLAIN_LIMIT]. */
    fun encode(json: String): String {
        val minified = minify(json)
        if (minified.length <= PLAIN_LIMIT) return minified
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(minified.toByteArray(Charsets.UTF_8))
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** The JSON of [text]: unchanged when it is no `ICTZ1:` payload. */
    fun decode(text: String): String {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return text
        val bytes = try {
            Base64.getUrlDecoder().decode(trimmed.substring(PREFIX.length).trim())
        } catch (_: IllegalArgumentException) {
            throw ConfigException("$PREFIX: invalid base64")
        }
        val inflater = Inflater(true)
        inflater.setInput(bytes)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) throw ConfigException("$PREFIX: data is incomplete")
                out.write(buffer, 0, n)
                if (out.size() > MAX_INFLATED) throw ConfigException("$PREFIX: configuration larger than 1 MB")
            }
        } catch (_: DataFormatException) {
            throw ConfigException("$PREFIX: data is damaged")
        } finally {
            inflater.end()
        }
        return out.toString(Charsets.UTF_8.name())
    }

    /** JSON without the indentation of [ConfigJson.write]. */
    private fun minify(json: String): String = runCatching { org.json.JSONObject(json).toString() }.getOrDefault(json.trim())
}

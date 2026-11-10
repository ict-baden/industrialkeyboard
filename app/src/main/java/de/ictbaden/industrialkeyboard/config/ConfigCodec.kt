package de.ictbaden.industrialkeyboard.config

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Configuration text for barcodes: minified JSON or `ICTZ1:` plus base64url (without padding) of
 * the raw-DEFLATE compressed UTF-8 JSON, whichever is shorter. Shared format of the ICT-Baden
 * Android apps; every place that accepts a JSON configuration also accepts `ICTZ1:`.
 */
object ConfigCodec {
    const val PREFIX = "ICTZ1:"

    /** Inflated size limit against "zip bombs". */
    const val MAX_INFLATED = 1_000_000

    /** Minified JSON or its compressed form, whichever is shorter. */
    fun encode(json: String): String {
        val minified = minify(json)
        val compressed = compress(minified)
        return if (compressed.length < minified.length) compressed else minified
    }

    fun isCompressed(text: String) = text.trimStart().startsWith(PREFIX, ignoreCase = true)

    /**
     * [json] without every value that equals the one in [defaults] (objects compared key by key,
     * arrays and plain values as a whole). The parser fills in the defaults again, so the result
     * means the same configuration; `version` stays.
     */
    fun compact(json: String, defaults: String): String {
        val config = JSONObject(json)
        prune(config, JSONObject(defaults), top = true)
        return config.toString()
    }

    private fun prune(config: JSONObject, defaults: JSONObject, top: Boolean) {
        for (key in config.keys().asSequence().toList()) {
            if (top && key == "version") continue
            if (!defaults.has(key)) continue
            val value = config.opt(key)
            val default = defaults.opt(key)
            if (value is JSONObject && default is JSONObject) {
                prune(value, default, top = false)
                if (value.length() == 0) config.remove(key)
            } else if (same(value, default)) {
                config.remove(key)
            }
        }
    }

    private fun same(a: Any?, b: Any?): Boolean = when {
        a is JSONArray && b is JSONArray -> a.toString() == b.toString()
        a is JSONObject && b is JSONObject -> a.toString() == b.toString()
        a is Number && b is Number -> a.toDouble() == b.toDouble()
        else -> a == b
    }

    private fun compress(minified: String): String {
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
    private fun minify(json: String): String = runCatching { JSONObject(json).toString() }.getOrDefault(json.trim())
}

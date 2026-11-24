package de.ictbaden.industrialkeyboard.voice

import java.net.URI
import java.util.Locale

/** One language pack of the free offline dictation (issue #24): a Vosk model as a ZIP. */
data class VoiceModel(
    /** Language code the keyboard picks it for (`de`, `en`). */
    val language: String,
    /** File name on the server; appended to the base URL. */
    val fileName: String,
    /** The folder inside the ZIP. */
    val folder: String,
    /** SHA-256 of the ZIP, lower case hex; fixed here, also for an own server (`typing.voiceModelUrl`). */
    val sha256: String,
    /** Exact size of the ZIP in bytes. */
    val bytes: Long,
) {
    /** Size for the button, rounded to whole MB (46 MB). */
    val megabytes: Int get() = ((bytes + 500_000) / 1_000_000).toInt()
}

/**
 * The language packs the keyboard knows. Never inside the APK: downloaded on request only, from a
 * fixed address, checked against the SHA-256 written here before anything is unpacked.
 */
object VoiceModelCatalog {

    /** GitHub release of this repository that holds the models (Apache-2.0, from alphacephei.com/vosk/models). */
    const val DEFAULT_BASE_URL = "https://github.com/ict-baden/industrialkeyboard/releases/download/voice-models-1/"

    val GERMAN = VoiceModel(
        language = "de",
        fileName = "vosk-model-small-de-0.15.zip",
        folder = "vosk-model-small-de-0.15",
        sha256 = "b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43",
        bytes = 46_499_967,
    )

    val ENGLISH = VoiceModel(
        language = "en",
        fileName = "vosk-model-small-en-us-0.15.zip",
        folder = "vosk-model-small-en-us-0.15",
        sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        bytes = 41_205_931,
    )

    val ALL = listOf(GERMAN, ENGLISH)

    /** German for German keyboards (de, de-AT, de-CH …), English for every other language. */
    fun languageFor(locale: Locale): String = if (locale.language.lowercase(Locale.ROOT) == "de") GERMAN.language else ENGLISH.language

    fun forLanguage(language: String): VoiceModel? = ALL.firstOrNull { it.language == language }

    fun bySha256(sha256: String): VoiceModel? = ALL.firstOrNull { it.sha256.equals(sha256, ignoreCase = true) }

    /**
     * `typing.voiceModelUrl` as a base URL ending in `/`, or `null` if it is no https:// address. Only
     * https: the checksum protects the content, https keeps the request itself private.
     */
    fun normalizeBaseUrl(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        val uri = runCatching { URI(text) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrEmpty() || uri.rawQuery != null || uri.rawFragment != null) return null
        return if (text.endsWith("/")) text else "$text/"
    }

    /** Where [model] is downloaded from: the own server of [baseUrl] (`typing.voiceModelUrl`) or ours. */
    fun url(model: VoiceModel, baseUrl: String?): String = (normalizeBaseUrl(baseUrl) ?: DEFAULT_BASE_URL) + model.fileName

    /**
     * Language of a model folder an MDM put into the app folder, or of the top folder of an imported
     * ZIP: `de` / `en` or a Vosk name such as `vosk-model-small-de-0.15`, `vosk-model-en-us-0.22`.
     */
    fun languageOfFolder(name: String): String? {
        val lower = name.lowercase(Locale.ROOT).trim()
        if (lower.isEmpty()) return null
        if (Regex("[a-z]{2}").matches(lower)) return lower
        return Regex("^vosk-model-(?:small-)?([a-z]{2})(?:-|$)").find(lower)?.groupValues?.get(1)
    }
}

package de.ictbaden.industrialkeyboard.app

/**
 * Error codes shown next to translated error messages, so support can identify a message in any
 * language (richtlinien/13-sprachen.md). Listed in docs/error-codes.md – never reuse a number.
 */
object ErrorCodes {
    const val PROVISIONING = "IK-0100"
    const val IMPORT_INVALID = "IK-0101"
    const val FILE_UNREADABLE = "IK-0102"
    const val EXPORT_FAILED = "IK-0103"
    const val INVALID_COLOR = "IK-0104"
    const val INVALID_MACRO = "IK-0105"
    const val CONFIG_UNREADABLE = "IK-0106"

    /** Invalid snippet in the editor: name or text missing or too long, shortcut wrong or taken, limit reached. */
    const val SNIPPET_INVALID = "IK-0107"

    /** The file of the user's own snippets could not be read; reset, the broken copy kept as `.invalid`. */
    const val USER_SNIPPETS_UNREADABLE = "IK-0108"

    /** An own snippet could not be written to `files/user-snippets.json` (storage full, rename failed). */
    const val USER_SNIPPETS_SAVE_FAILED = "IK-0109"

    /** Import into the personal dictionary rejected: no Gboard export or word list, larger than 1 MB, no dictionary.txt in the ZIP. */
    const val PERSONAL_DICTIONARY_IMPORT_INVALID = "IK-0110"

    /** The personal dictionary could not be written to `files/personal-dictionary.json`; also an invalid word in its editor. */
    const val PERSONAL_DICTIONARY_SAVE_FAILED = "IK-0111"
    const val CAMERA_PERMISSION = "IK-0200"
    const val CAMERA_FAILED = "IK-0201"
    const val MIC_PERMISSION = "IK-0210"
    const val DICTATION_UNAVAILABLE = "IK-0211"
    const val DICTATION_NETWORK = "IK-0212"
    const val DICTATION_NOT_UNDERSTOOD = "IK-0213"

    /** Free dictation (issue #24): no language pack and `typing.voiceModelDownload` off – import or MDM folder. */
    const val VOICE_MODEL_MISSING = "IK-0214"

    /** The language pack could not be downloaded (no connection, server error, timeout). */
    const val VOICE_MODEL_DOWNLOAD = "IK-0215"

    /** Downloaded language pack has the wrong size or SHA-256: not unpacked, deleted. */
    const val VOICE_MODEL_CHECKSUM = "IK-0216"

    /** File is no Vosk language pack (no ZIP, unsafe paths, too large, language unknown). */
    const val VOICE_MODEL_INVALID = "IK-0217"

    /** Language pack could not be stored (storage full). */
    const val VOICE_MODEL_STORAGE = "IK-0218"

    /** Installed language pack cannot be loaded (damaged): delete it and load it again. */
    const val VOICE_MODEL_BROKEN = "IK-0219"
}

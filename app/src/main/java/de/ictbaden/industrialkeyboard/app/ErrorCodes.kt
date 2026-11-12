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
    const val CAMERA_PERMISSION = "IK-0200"
    const val CAMERA_FAILED = "IK-0201"
    const val MIC_PERMISSION = "IK-0210"
    const val DICTATION_UNAVAILABLE = "IK-0211"
    const val DICTATION_NETWORK = "IK-0212"
    const val DICTATION_NOT_UNDERSTOOD = "IK-0213"
}

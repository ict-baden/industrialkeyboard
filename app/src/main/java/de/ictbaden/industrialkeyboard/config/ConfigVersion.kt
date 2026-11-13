package de.ictbaden.industrialkeyboard.config

/**
 * `configVersion` (top level): which defaults fill in the options a configuration leaves out. Not
 * the format version (`version`, [ConfigJson.VERSION]) – every config version has the same format.
 *
 * - Missing, `1` or `2`: written for 2.1 or older (`1` = 1.x, `2` = 2.0 and 2.1). Options left out
 *   get the defaults of 2.1.1 ([LEGACY]), and options 2.1 did not have yet are off or behave as in
 *   2.1. An EMM that pushes its old `config_json` or file again after the update changes nothing.
 *   Also configuration files stored by 2.1 on the device.
 * - `3` ([CURRENT]): the defaults of this version (Gboard-like, [KeyboardConfig]).
 * - Higher (written by a newer app): the newest defaults this version knows.
 *
 * [ConfigJson.write] always writes [CURRENT] with every option, so a configuration stored or
 * exported once keeps its values, whatever the defaults it was read with.
 */
object ConfigVersion {
    const val KEY = "configVersion"
    const val CURRENT = 3

    /** The v2.1.1 default, `{TAB}` left of the comma. */
    const val LEGACY_ALPHA_P1 = "{TAB}"

    /** Fixed vibration of 2.1 (today: the system's keyboard tick when `vibrationMs` is not set). */
    const val LEGACY_VIBRATION_MS = 18

    /**
     * The defaults for configurations below [CURRENT]: what 2.1.1 used where a value is left out
     * (docs/configuration.md, "configVersion"). Everything not named here was the same in 2.1.1,
     * or is only reachable through something that is off here (⊞ needs the toolbar, the camera's
     * OCR needs the camera).
     */
    val LEGACY: KeyboardConfig = KeyboardConfig().let { d ->
        d.copy(
            appearance = d.appearance.copy(
                dynamicColor = false,
                emojiKey = false,
            ),
            layout = d.layout.copy(
                alphabet = Alphabet.QWERTY,
                // 2.1's QWERTZ had ü, ö and ä as own keys (11 per row).
                umlautKeys = true,
                symbolsKey = false,
                topBar = TopBar.TABS,
            ),
            keys = KeySettings(ProgrammableKey.DEFAULTS + (ProgrammableKey.ALPHA_P1.id to LEGACY_ALPHA_P1)),
            typing = d.typing.copy(
                longPressDelay = 400,
                suggestions = false,
                autocorrect = false,
                voice = VoiceInput.OFF,
                deleteGesture = false,
                nextWord = false,
            ),
            feedback = d.feedback.copy(vibrationMs = LEGACY_VIBRATION_MS),
            scanner = d.scanner.copy(
                // 2.1 typed scan broadcasts exactly as received.
                data = d.scanner.data.copy(trim = false),
                nfc = d.scanner.nfc.copy(enabled = false),
            ),
            snippets = d.snippets.copy(allowUser = false),
        )
    }

    /** The defaults for a configuration with [version] (`null` = no `configVersion`). */
    fun defaults(version: Int?): KeyboardConfig = if (version != null && version >= CURRENT) KeyboardConfig() else LEGACY
}

[← README](../README.md)

# Error codes

Every error message shows a code next to the translated text, so a message can be identified in
any of the five languages. Source: [`ErrorCodes.kt`](../app/src/main/java/de/ictbaden/industrialkeyboard/app/ErrorCodes.kt).

| Code | Message (English) | Typical cause |
|---|---|---|
| `IK-0100` | Last provisioning error | A configuration from the MDM, a file or a broadcast was rejected; the detail names the source and the JSON field |
| `IK-0101` | Import failed | Invalid configuration in the import file or the scanned QR code (also a damaged `ICTZ1:` text) |
| `IK-0102` | Could not read the file | Import file not readable |
| `IK-0103` | Export failed | Target not writable |
| `IK-0104` | Invalid color | Accent color is not `#RRGGBB` |
| `IK-0105` | Invalid (macro) | Unknown token in a programmable key, e.g. `{TABB}` |
| `IK-0200` | Without camera permission the keyboard cannot scan with the camera | The camera permission was denied. Allow it in the Android app settings (Permissions → Camera) or with the EMM |
| `IK-0201` | The camera could not be started | Another app uses the camera, or the device has none. Close the other app and tap scan again |
| `IK-0210` | Without microphone permission the keyboard cannot take dictation | Allow the microphone for Industrial Keyboard (Android settings → Apps → Permissions) or tap 🎤 again and allow it |
| `IK-0211` | Speech recognition is not available for this language | The device's speech recognition does not support the keyboard language, or none is installed |
| `IK-0212` | Speech recognition needs a connection that is not available | The speech service works online and the device is offline; use `typing.voice: "offline"` on devices with on-device recognition |
| `IK-0213` | Not understood | Nothing could be recognised; tap 🎤 and speak again |
| `IK-0106` | The stored configuration cannot be read | Storage error or empty `config.json`: the admin password is unknown, the settings stay locked. Restart; otherwise set the admin password with the EMM (`admin_password`, applied again as long as the file is unreadable) or clear the app's storage |
| `IK-0107` | Invalid snippet (e.g. "Enter a name", "“mfg” is already used by “…”", "At most 50 snippets") | In the [snippet](configuration.md#snippets--snippets) editor: name or text missing or too long (40 / 2000 characters), shortcut not 2–16 letters or already used by another snippet, an own snippet with more than text, `{TAB}` and `{ENTER}`, or the list is full (100 of the organisation, 50 own). Macro errors in the text show `IK-0105` |
| `IK-0108` | Your snippets could not be read and were reset | The file of the user's own snippets (`files/user-snippets.json`) was damaged. It starts empty; the broken copy is kept as `user-snippets.json.invalid` |
| `IK-0109` | Your snippet could not be saved | Writing `files/user-snippets.json` failed, usually because the device storage is full (or the list changed in another window meanwhile). The editor stays open with what was entered; free some storage and tap *Save* again. The list on the device is unchanged |
| `IK-0110` | Not a Gboard dictionary export or word list (…) | Import into the [personal dictionary](configuration.md#typing): the file is larger than 1 MB, a ZIP without `dictionary.txt` (or with more than 32 entries), empty, or text without the Gboard header and without tab-separated lines. The reason in brackets is in English. Nothing was imported |
| `IK-0111` | The personal dictionary could not be saved / invalid word | Writing `files/personal-dictionary.json` failed (storage full), the list is full (5000 words), or in the word editor: word empty or longer than 64 characters, shortcut longer than 16 characters or with spaces, unknown language |

Validation details of a configuration (e.g. `keys.numeric.p1: unknown token {TABB}`) stay in
English because they quote JSON field names and tokens. The result text of the
`INSTALL_CONFIG` broadcast is English too: it is read by scripts, not by people.

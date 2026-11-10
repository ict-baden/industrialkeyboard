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
| `IK-0106` | The stored configuration cannot be read | Storage error or empty `config.json`: the admin password is unknown, the settings stay locked. Restart; otherwise set the admin password with the EMM (`admin_password`, applied again as long as the file is unreadable) or clear the app's storage |

Validation details of a configuration (e.g. `keys.numeric.p1: unknown token {TABB}`) stay in
English because they quote JSON field names and tokens. The result text of the
`INSTALL_CONFIG` broadcast is English too: it is read by scripts, not by people.

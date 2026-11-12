[← README](../README.md)

# Data and privacy

- [Summary](#summary)
- [What is stored on the device](#what-is-stored-on-the-device)
- [What the keyboard reads](#what-the-keyboard-reads)
- [Connections and broadcasts](#connections-and-broadcasts)
- [Deleting everything](#deleting-everything)

## Summary

Industrial Keyboard **sends no data to ICT-Baden or anyone else**. The app has no internet
permission, so Android does not let it open network connections at all. There is no telemetry,
no crash reporting, no update check and no account.

What you type is passed to the focused app and **not stored**: there is no dictionary, no word
learning, no history and no clipboard access.

**Word suggestions** come from built-in word lists (AOSP dictionaries) of the keyboard language. Only
the word being typed is held in memory to look it up; it is dropped when the word ends. Nothing is
learnt, no personal dictionary is built, nothing is written to storage. **Emoji:** the emoji panel keeps
recently used emoji in memory only; the AndroidX picker stores the preferred skin tone per emoji on the
device (no text).

**Camera:** on devices without a scan engine the keyboard can read barcodes with the camera
([`scanner.trigger`](configuration.md#scanner)). The camera runs only while its picture is shown in
the keyboard (after a tap on scan, at most one minute) and stops after the first code. The pictures
are decoded in memory on the device and are neither stored nor sent; only the barcode text is typed
into the field. Android asks for the camera permission the first time. The same camera reads a
configuration QR code when you choose **Import from barcode** in the settings.

**Text recognition (OCR):** the camera picture can also be read as text
([`scanner.ocr`](configuration.md#scannerocr--text-recognition-ocr)). This uses Google ML Kit with the
recognition model inside the app; it runs on the device. ML Kit would like to send anonymous usage
statistics to Google, but the keyboard has no internet permission – it is removed from the app on
purpose – so neither pictures, recognised text nor statistics can leave the device.

**NFC:** a tag held to the device is read once (UID or the text stored on it) and typed into the
field; nothing is kept.

The operator of the devices (your company) is responsible for the data typed with the keyboard;
ICT-Baden processes none of it.

**Dictation:** the microphone records only while the dictation panel is open, and the audio goes only to the speech recognition of the device (Android `SpeechRecognizer`); Industrial Keyboard neither stores nor sends it and has no internet permission. With `typing.voice: "auto"` the keyboard uses on-device recognition where the device has it (Android 12+, e.g. Pixel phones) – then nothing leaves the device. Without it, the device's speech service is used (e.g. Google), which may process the audio online under its own privacy terms. `typing.voice: "offline"` allows on-device recognition only; `"off"` removes the microphone (MDM key `voice`). The microphone permission is asked the first time 🎤 is tapped.

## What is stored on the device

Only the configuration and what belongs to it, in the app's private storage (other apps cannot
read it):

| File | Content |
|---|---|
| `files/config.json` | The [configuration](configuration.md). The admin password only as a salted PBKDF2 hash. |
| `files/config.json.invalid-<time>` | Only if the stored configuration was invalid once: a copy of the broken file for support, password hash included. Delete it with *Clear storage* (below). |
| `shared_prefs/provisioning.xml` | Which configuration sources were applied already (time stamp and size of the file, a hash of the managed configuration) and the last provisioning error. |
| `shared_prefs/password_guard_settings.xml`, `password_guard_remote.xml` | Number of wrong admin passwords in a row and the end of the 5-minute lock – one file for the settings screen, one for broadcasts and files. No passwords. |
| `shared_prefs/migrations.xml` | Which one-time changes after an update were done. |

All of it is excluded from Android backup and device transfer, because the configuration is
provisioned per device and contains the password hash.

The keyboard writes short messages to the system log (logcat) when a configuration is applied
or rejected – never typed text or barcodes.

## What the keyboard reads

- The **managed configuration** of your EMM (Android Enterprise app restrictions).
- `industrialkeyboard.json` in `Android/data/<package>/files/` and in Zebra's `/enterprise/usr/`, if present ([Deployment](deployment.md)).
- The **input type** of the focused field (number, e-mail, password …) to choose the matching keys.
- The **cursor position** (and, for ← and → on the text editing panel, the one character next to the cursor, to keep the focus in the field) – in memory only, never stored or logged.
- The **clipboard**, only while the keyboard is shown and only to offer it in the 📋 panel. With `typing.clipboardHistory` (off by default) copied texts are kept **in memory only** – at most 20, unpinned ones for 1 hour, nothing from password fields or clips marked sensitive – and are gone when the keyboard process ends. Nothing is written to storage.

## Connections and broadcasts

There are no network connections. On the device, the keyboard exchanges broadcasts with other
apps – see [Scanner & app integration](integration.md):

| Direction | What | Why |
|---|---|---|
| sends | Soft scan trigger to DataWedge (Zebra), the Honeywell scanner service or the app in `scanner.intentAction` | Scan pad, scan key, `{SCAN}` |
| receives | `…action.SCAN_INPUT` with a barcode | Only if `scanner.input.enabled` is switched on (off by default): types the barcode into the focused field, never into password fields. Without `scanner.input.permission` or `allowedPackages` any app on the device can send it ([details](integration.md#option-b--the-keyboard-types-it)). |
| receives | `…action.SHOW_TAB` | Switches the keyboard page |
| receives | NFC tags (`NDEF_DISCOVERED`, `TECH_DISCOVERED`, `TAG_DISCOVERED`) from Android | Only with `scanner.nfc.enabled`; typed into the field the keyboard is open in, otherwise ignored |
| receives | `…action.INSTALL_CONFIG` | Installs a configuration; only with the admin password, or on an EMM-managed device while no password is set (then any app can – set a password). After 5 wrong passwords it is locked for 5 minutes |

The links in *Settings → Info* (source code, support, website) open in the browser only when you
tap them.

## Deleting everything

- *Settings → Configuration → Reset to defaults* removes the configuration (the admin password stays).
- Android *Settings → Apps → Industrial Keyboard → Storage → Clear storage*, or uninstalling the app, removes all of it.

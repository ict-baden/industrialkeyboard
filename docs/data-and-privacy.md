[← README](../README.md)

# Data and privacy

- [Summary](#summary)
- [What is stored on the device](#what-is-stored-on-the-device)
- [What the keyboard reads](#what-the-keyboard-reads)
- [Connections and broadcasts](#connections-and-broadcasts)
- [Deleting everything](#deleting-everything)

## Summary

Industrial Keyboard **sends no data to ICT-Baden or anyone else**. There is no telemetry, no
crash reporting, no update check and no account. The app has the internet permission for **one
purpose only**: to download a language pack of the [free offline dictation](configuration.md#free-offline-dictation)
when the user taps *Download* (or never, with `typing.voiceModelDownload: false`). That is the
only network code in the app; it fetches one fixed file over https and sends nothing but the
request for it ([below](#connections-and-broadcasts)). Audio, typed and recognised text, the
clipboard, barcodes and the configuration never go to the network.

What you type is passed to the focused app, and **nothing typed in other apps is stored**: there is
no word learning and no typing history. The clipboard is read only while the keyboard
is shown, for the 📋 panel, and with `typing.clipboardHistory` copied texts are kept in memory only
([below](#what-the-keyboard-reads)). The only texts the app keeps
are [snippets](configuration.md#snippets--snippets) and words of the **personal dictionary** users
enter or import themselves in the app's own screens (below).

**Word suggestions** come from built-in word lists (AOSP dictionaries) of the keyboard language. Only
the word being typed is held in memory to look it up; it is dropped when the word ends. Nothing is
learnt from typing, nothing typed is written to storage.

**Personal words** ([`typing.personalDictionary`, `systemDictionary`](configuration.md#typing)): the
suggestions also offer words the user put into the keyboard's personal dictionary on purpose – added
in *Settings → Typing → Personal dictionary* or imported from a file the user picks there (a Gboard
export or a word list; read once, at most 1 MB, nothing of the file but the words is kept) – and the
words of Android's user dictionary (`UserDictionary`, e.g. Gboard's personal dictionary). The latter
is only read (when a field starts and when it changes), never written or copied.

**Autofill** ([`typing.autofillSuggestions`](configuration.md#typing), Android 11+): the keyboard
asks Android for the chips of the autofill service chosen in Android (Google, a password manager)
and shows them above the keys. The system draws them; their content (e-mail addresses, passwords …)
never reaches the keyboard. **Emoji:** the emoji panel keeps
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
statistics to Google (through Google's `datatransport` library). The keyboard removes the upload
backend of that library from the app, so the statistics have no way out and are deleted on the
device; pictures and recognised text are never sent in any case.

**NFC:** a tag held to the device is read once (UID or the text stored on it) and typed into the
field; nothing is kept.

**Configurations written for 2.1** (without `"configVersion": 3`, see
[`configVersion`](configuration.md#configversion--defaults-of-21-or-todays)) keep the features
2.1 did not have off unless they switch them on: no word suggestions, no dictation, no NFC and no
own snippets.

**Screen readers:** with TalkBack on, the keyboard gives the screen reader the names of the keys,
the suggestions and the words of autocorrection announcements, which TalkBack speaks. Nothing of it
is stored or logged by the keyboard. In password fields the character keys are spoken only as
"Key" unless headphones, a headset or a hearing aid are connected
([`accessibility.speakPasswordKeys`](configuration.md#accessibility--screen-readers-and-large-fonts));
to know that, the keyboard asks Android which audio outputs are connected (device types only).

The operator of the devices (your company) is responsible for the data typed with the keyboard;
ICT-Baden processes none of it.

**Dictation:** the microphone records only while the dictation panel is open. Where the audio goes depends on [`typing.voice`](configuration.md#typing) and the device (the smart switch, [details](configuration.md#free-offline-dictation)):

- **Android's on-device recognition** (Android 12+, e.g. Pixel phones): processed on the device, nothing leaves it.
- **The device's speech service** (e.g. Google), only with `"auto"` on devices that have one but no on-device recognition: it may process the audio online under its own privacy terms. Industrial Keyboard hands the audio to Android and neither stores nor sends it itself.
- **The free offline model** (Vosk, inside the app), with `"free"`, and with `"auto"` or `"offline"` on devices without a suitable recognizer: the keyboard records 16 kHz audio itself and recognises it on the device, block by block in memory. Audio is never written to storage and never sent; only the recognised words are typed into the field.

`"free"` never uses a recognizer of the device or Google; `"offline"` never uses the online speech service; `"off"` removes the microphone (MDM key `voice`). The microphone permission is asked the first time 🎤 is tapped.

**Language packs of the free model** are downloaded only when the user (or the administrator in the settings) taps *Download*, from `https://github.com/ict-baden/industrialkeyboard/releases/…` or the server in `typing.voiceModelUrl`. The request carries no identifiers: no account, no device ID, no cookies, a neutral user agent (`IndustrialKeyboard`); the server sees the device's IP address and the file name, as for any download (GitHub's privacy statement applies to its servers). The file is checked against the size and SHA-256 fixed in the app before it is used. With `typing.voiceModelDownload: false` the app never connects to the network.

## What is stored on the device

Only the configuration and what belongs to it, plus the user's own snippets, in the app's private
storage (other apps cannot read it):

| File | Content |
|---|---|
| `files/config.json` | The [configuration](configuration.md). The admin password only as a salted PBKDF2 hash. |
| `files/config.json.invalid-<time>` | Only if the stored configuration was invalid once: a copy of the broken file for support, password hash included. Delete it with *Clear storage* (below). |
| `shared_prefs/provisioning.xml` | Which configuration sources were applied already (time stamp and size of the file, a hash of the managed configuration) and the last provisioning error. |
| `shared_prefs/password_guard_settings.xml`, `password_guard_remote.xml` | Number of wrong admin passwords in a row and the end of the 5-minute lock – one file for the settings screen, one for broadcasts and files. No passwords. |
| `shared_prefs/migrations.xml` | Which one-time changes after an update were done. |
| `files/user-snippets.json` | [Snippets](configuration.md#snippets--snippets) the user entered in the app's snippet screen (name, text, shortcut) – never text typed in other apps, the clipboard or a selection. Not part of the configuration, export, QR code or *Reset to defaults*; *Clear storage* removes it. |
| `files/personal-dictionary.json` | The [personal dictionary](configuration.md#typing): words, optional shortcuts and languages the user added or imported – never text typed in other apps. Not part of the configuration, export, QR code or *Reset to defaults*; *Delete all words* in its screen or *Clear storage* removes it. |
| `files/user-snippets.json.invalid` | Only if that file was damaged once: the broken copy (`IK-0108`), the list started empty. |
| `files/voice-models/de/`, `files/voice-models/en/` | Language packs of the free offline dictation (Vosk models, downloaded or imported on request; each about 50 MB). No user data. Deleted in *Settings → Typing → Language pack …* or with *Clear storage*. Packs an MDM puts into `Android/data/<package>/files/voice-models/` are used from there and stay the MDM's. |
| `cache/voice-models/` | Only while a language pack is downloaded or imported: the ZIP being checked; deleted afterwards (also after an error or cancel). |
| `shared_prefs/quick_toggles.xml` | Quick toggles switched on this device (glove mode, contrast, scanner, sound) – on/off only, no text. Not part of the configuration, export or QR code; *Reset quick toggles* or *Clear storage* removes it. |

All of it is excluded from Android backup and device transfer, because the configuration is
provisioned per device and contains the password hash; the user's snippets stay on the device too.
None of it is ever sent: the only network use is the download of a language pack (below).

The keyboard writes short messages to the system log (logcat) when a configuration is applied
or rejected – never typed text, barcodes or snippet texts.

## What the keyboard reads

- The **managed configuration** of your EMM (Android Enterprise app restrictions).
- `industrialkeyboard.json` in `Android/data/<package>/files/` and in Zebra's `/enterprise/usr/`, if present ([Deployment](deployment.md)).
- The **input type** of the focused field (number, e-mail, password …) to choose the matching keys.
- The **package name, input type and hint** (placeholder or label) of the focused field, only to pick an [app profile](configuration.md#appprofiles--app-profiles) when the field starts – not the text in it. Nothing of it is stored; logcat gets the package name and the profile name, never the hint.
- The **cursor position** (and, for ← and → on the text editing panel, the one character next to the cursor, to keep the focus in the field) – in memory only, never stored or logged.
- **Android's user dictionary** (words, shortcuts, language, frequency), with `typing.systemDictionary`, when a field starts and when it changes – in memory only.
- A **file the user picks** for the personal dictionary import or a language pack import (system file picker; the app gets access to that one file only).
- The **MDM folder for language packs** `Android/data/<package>/files/voice-models/`, if an MDM filled it.
- The **clipboard**, only while the keyboard is shown and only to offer it in the 📋 panel. With `typing.clipboardHistory` (off by default) copied texts are kept **in memory only** – at most 20, unpinned ones for 1 hour, nothing from password fields or clips marked sensitive – and are gone when the keyboard process ends. An entry removed with × stays removed even while it is still on the clipboard. Nothing is written to storage.
- The **text of the field**, only for *Select all* on the text editing panel when the app does not support the select-all command: the keyboard then reads the field's text once to find its end and select it. It is held in memory only for that moment – never stored or logged.

## Connections and broadcasts

**Network:** one kind of connection only – the download of a language pack of the free dictation
(https `GET` of one fixed file, on request, see [Summary](#summary)). Nothing is uploaded. No other
part of the app opens connections; ML Kit's usage statistics are discarded on the device (above).
Android tells the app whether the current network is metered (mobile data), only to warn before a
download.

On the device, the keyboard exchanges broadcasts with other
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

- *Settings → Configuration → Reset to defaults* removes the configuration (the admin password stays); the user's own snippets stay – delete them in *My snippets*.
- Android *Settings → Apps → Industrial Keyboard → Storage → Clear storage*, or uninstalling the app, removes all of it, the user's own snippets included.

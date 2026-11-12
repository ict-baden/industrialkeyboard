[← README](../README.md)

# Limitations

What Industrial Keyboard has been tested on, what has not been tested, and what it deliberately
does not do.

## Tested devices

| Device | Android | Scanner | Result |
|---|---|---|---|
| Zebra HC20 | 15 | DataWedge | tested, all bugs found there are fixed (see [Changelog](../CHANGELOG.md)) |

Other Zebra devices with DataWedge should behave the same but have not been tried. The keyboard
runs on any Android 8.0 (API 26) or later; without a supported scanner the scan tab and the scan
key are hidden.

## Not tested

- **Honeywell:** the soft trigger (`trigger: "honeywell"`, broadcast to
  `com.intermec.datacollectionservice`) is built from the vendor documentation only and has not
  been tried on a Honeywell device.
- **Other scanner apps** with `trigger: "intent"` – depends on the app.
- **EMMs:** the managed configuration follows the Android Enterprise standard; SOTI MobiControl,
  Intune and Workspace ONE are documented in [Deployment](deployment.md) but not every console
  version has been tried.
- **Remote desktop apps:** Ctrl, Win and Alt are sent as ordinary key events of the input
  method (modifier key down, key, modifier key up). How a remote desktop, VNC or Citrix app
  passes them on is up to that app; they have not yet been tried with one on a device.
- **OCR and NFC** are new and have not yet been tried with real labels and tags on a device. OCR
  reads Latin script only, needs good light and sharp print and is slower than barcodes (several
  pictures per second, not dozens). NFC in the keyboard goes through a small invisible screen:
  if another app on the device also handles tags, Android may ask which one to use.
- **Translations:** Spanish, French and Chinese (simplified) are machine translations and have
  not yet been proofread by native speakers. They are marked as such in the language picker.

Please report results on other devices in the
[issues](https://github.com/ict-baden/industrialkeyboard/issues) – also when it simply works.

## Security limits

- **Scan broadcasts (`scanner.input`):** Android does not tell the keyboard which app sent a
  broadcast. That is why typing scan broadcasts is off by default; when switched on, restrict the
  senders with `permission` or `allowedPackages` where your scanner app allows it
  ([details](integration.md#option-b--the-keyboard-types-it)). Barcodes are never typed into
  password fields, visible ones (`textVisiblePassword`) included – use keystroke output for barcode
  fields of that type.
- **`INSTALL_CONFIG` on a managed device without admin password:** every app on the device can
  reconfigure the keyboard – before Android 11 also with a file in the app folder. Set an admin
  password ([Deployment](deployment.md#broadcast-intent-stagenow-adb)).
- **Wrong passwords lock the remote channels:** any app can send 5 wrong passwords every 5
  minutes and so keep `INSTALL_CONFIG` and the app folder locked. The managed configuration,
  `/enterprise/usr` and the settings screen still work ([Deployment](deployment.md#broadcast-intent-stagenow-adb)).
- **Stored configuration unreadable** (storage error): the settings stay locked, because the admin
  password is unknown (`IK-0106`). Restart the device; otherwise set the admin password with the EMM
  (`admin_password`) or clear the app's storage.
- The admin password protects the settings screen and remote configuration. It is not a device
  lock: whoever can uninstall the app or clear its data gets the defaults.

## Text editing panel

- Select all, Cut, Copy and Paste use the field's own text actions (Compose text fields handle
  them as well). Custom editors without these actions ignore Cut, Copy and Paste; Select all
  then selects the text the field reports.
- Terminal emulators and remote desktop apps (fields without a text editor) get only keys and
  Paste, which types the current clipboard text there; Select, Select all, Cut and Copy are
  dimmed. There is no Ctrl+C fallback on purpose: in a terminal Ctrl+C interrupts the running
  program.
- In single-line fields ↑/↓ act as Home/End (Gboard moves the focus there). Fields that report a
  wrong input type (some WebViews) may behave differently.
- ↑/↓ on the first or last line of a multi-line field, and ←/→ when the app does not answer
  what is next to the cursor, can still move the focus to another view; the panel then closes.
- In select mode ←/→ set the selection directly; ↑/↓, Home and End send Shift with the key,
  which an editor may ignore.
- The panel keys are drawn on a canvas and are not yet readable by TalkBack (issue #9).

## Not included, on purpose

- Word suggestions and autocorrection use fixed built-in word lists: they do not learn names or words
  you type often, and there is no swipe typing. Dictation needs a speech recognizer on the device (on-device on Android 12+ phones like Pixel; AOSP devices without Google usually have none – issue #24). Nothing learns or stores what is typed.
- The dictionaries are general (Wikipedia-weighted): technical terms and company words are often missing. A small list lifts everyday words (greetings, weekdays) for German and English.
- Next-word suggestions come from a small fixed list (German and English); other languages have none yet. Gboard learns from typing, this keyboard does not.
- The clipboard history lives only in memory: it is empty after the keyboard restarts, and Android lets the keyboard see only what is copied while it is the selected keyboard.
- No internet permission: the keyboard cannot send anything off the device.
- Camera scan reads one code per tap and has no batch or multi-code mode; a scan engine is faster
  and works in the dark. The camera picture fills only the keyboard area.
- Key labels are not translated (they are what the keys type) – except on the text editing panel.

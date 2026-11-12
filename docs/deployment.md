# Deployment

Three things have to reach a device: the **app**, the decision that it is **the keyboard**,
and – optionally – a **configuration** ([reference](configuration.md),
[templates](../examples)). The configuration is always the same JSON document; only the
transport differs.

| Method | Suited for | Configuration |
|---|---|---|
| [Managed configuration](#managed-configuration-all-emms) | SOTI, Intune, Workspace ONE, … (Android Enterprise) | key `config_json` |
| [Configuration file](#configuration-file) | SOTI File Sync, Zebra StageNow/MX, adb | `industrialkeyboard.json` |
| [Broadcast intent](#broadcast-intent-stagenow-adb) | Zebra StageNow (Intent CSP), scripts, adb | extra `config` |

|  |  |
|---|---|
| Package | **`de.ictbaden.industrialkeyboard`** |
| Input method (class) | **`de.ictbaden.industrialkeyboard.ime.KeyboardService`** |
| Input method id (adb) | `de.ictbaden.industrialkeyboard/.ime.KeyboardService` |

## Install the app

Add the APK from the [releases](../../../releases/latest) to your EMM's app catalog and assign it,
or install it over USB:

```bash
adb install -r IndustrialKeyboard-2.1.1.apk
```

The app needs no permissions apart from vibration, NFC and – asked on first use – the camera (camera
scan on devices without a scan engine) and the microphone (dictation, `typing.voice`). In particular
**no internet access**. To grant them ahead of time, use your EMM's permission policy (or
`adb shell pm grant de.ictbaden.industrialkeyboard android.permission.CAMERA` / `android.permission.RECORD_AUDIO`).
To forbid dictation, set `typing.voice: "off"`.

## Make it the keyboard

Android only uses a keyboard after it has been **enabled** and **selected**.

**On the device** – open *Industrial Keyboard*: step 1 **Enable** opens the system's keyboard
list (switch on *Industrial Keyboard*, confirm the standard Android notice), step 2 **Select**
opens the keyboard picker. The status at the top turns green: *Active keyboard*.

**Over USB:**

```bash
adb shell ime enable de.ictbaden.industrialkeyboard/.ime.KeyboardService
adb shell ime set de.ictbaden.industrialkeyboard/.ime.KeyboardService
adb shell settings get secure default_input_method   # check
```

**Zebra devices (StageNow, or any EMM that delivers MX XML)** – UI Manager:

| Setting | Value |
|---|---|
| Set Default Input Method? | Change Current Input Method |
| Select a Default Input Method Option | User defined Input Method |
| Package name of input Method | `de.ictbaden.industrialkeyboard` |
| Class name of input Method | `de.ictbaden.industrialkeyboard.ime.KeyboardService` |

```xml
<wap-provisioningdoc>
  <characteristic type="UiMgr">
    <parm name="InputMethodAction" value="1" />
    <characteristic type="InputMethodDetails">
      <parm name="InputMethodOption" value="4" />
      <parm name="InputMethodPackageName" value="de.ictbaden.industrialkeyboard" />
      <parm name="InputMethodClassName" value="de.ictbaden.industrialkeyboard.ime.KeyboardService" />
    </characteristic>
  </characteristic>
</wap-provisioningdoc>
```

**Android Enterprise policies** – if your EMM restricts the *permitted input methods*, add
`de.ictbaden.industrialkeyboard` to the list. Some EMMs can also set the default
keyboard on fully managed devices; otherwise select it once on the device as described above.

## Managed configuration (all EMMs)

The app ships a schema that every EMM with Android Enterprise support shows:

| Key | Type | Effect |
|---|---|---|
| `config_json` | String | **Complete configuration** (contents of a JSON file from `examples/`). Takes precedence over the keys below |
| `language` | Choice | Automatic (device language), English, Deutsch, Español, Français, 简体中文 |
| `theme` | Choice | System, Light, Dark, High contrast |
| `alphabet` | Choice | Automatic (system language), QWERTY, QWERTZ, AZERTY, Nordic, Spanish |
| `numeric_order` | Choice | Phone (1-2-3 at the top) or Calculator (7-8-9 at the top) |
| `scan_tab` | Bool | Show the scan tab (default on) |
| `function_tab` | Bool | Show the function key tab (default on) |
| `sound` | Bool | Click sound (default off) |
| `vibrate` | Bool | Vibration (default on) |
| `suggestions` | Bool | Word suggestions (default on) |
| `next_word` | Bool | Next-word suggestions after a space (default on) |
| `clipboard_history` | Bool | Clipboard history in memory for the 📋 panel (default off) |
| `text_editing` | Bool | Tile *Text editing* in the ⊞ menu: arrows, select, cut, copy and paste (default on) |
| `quick_toggles` | String | [Quick toggles](configuration.md#quicktoggles--quick-toggles-on-the-device) users may change: comma-separated `glove`, `contrast`, `scanner`, `sound`; `none` = none; empty = unchanged (default of the configuration: glove, contrast, sound). Unknown names are reported as provisioning error |
| `top_bar` | Choice | Above the keys: toolbar like Gboard or tabs |
| `one_handed` | Choice | One-handed mode: off, left, right |
| `autocorrect` | Bool | Autocorrection on space (default off) |
| `emoji_key` | Bool | Emoji key next to the space bar (default on) |
| `scan_prefix` | String | Macro typed before every barcode ([`scanner.data.prefix`](configuration.md#scannerdata--scan-data-formatting)); empty = unchanged |
| `scan_suffix` | String | Macro typed after every barcode instead of Enter/Tab (`scanner.data.suffix`); empty = unchanged |
| `camera_aim` | Choice | Camera aiming: frame, crosshair (pick list), anywhere |
| `camera_continuous` | Bool | Camera stays open and types every new code (default off) |
| `camera_symbologies` | String | Barcode types for the camera, comma-separated, e.g. `ean13,code128,qr`; empty = unchanged |
| `ocr_mode` | Choice | Text recognition: tap a line or take it automatically by pattern ([`scanner.ocr`](configuration.md#scannerocr--text-recognition-ocr)) |
| `ocr_pattern` | String | Regular expression for the pattern mode; empty = unchanged. Pattern mode without a pattern is reported as provisioning error |
| `nfc_enabled` | Bool | Type NFC tags (default on) |
| `nfc_read` | Choice | NFC: text on the tag, else UID / UID / text on the tag ([`scanner.nfc`](configuration.md#scannernfc--nfc-tags)) |
| `admin_password` | String | Protects the keyboard settings and configuration broadcasts |

The keys are applied as soon as the EMM changes them and whenever the keyboard opens. The
single keys only change their own option; `config_json` replaces the whole configuration.
[App profiles](configuration.md#appprofiles--app-profiles) (`appProfiles`) need `config_json` (or a
file, the broadcast or an import); the single keys cannot carry them.
Changes made on the device stay until the EMM sends a changed configuration. Errors (such as
invalid JSON) appear in the keyboard settings under **Configuration → Last provisioning
error**.

## SOTI MobiControl

Menu names differ between MobiControl versions and device modes (Android Enterprise or
Android+). The flow:

1. **Upload the app** – add the APK to the app catalog as an enterprise app (or as a
   package) and assign it to the devices.
2. **Make it the keyboard** – on Zebra devices deploy the MX XML from
   [above](#make-it-the-keyboard); on other devices select the keyboard once in the setup
   app. If you use a list of allowed keyboards, add the package.
3. **Configuration – option A, managed configuration** (Android Enterprise): open the app
   configuration of the enterprise app and paste the contents of the JSON file into
   `config_json` (or use the single keys).
4. **Configuration – option B, file** (e.g. Android+ with the Zebra agent): put
   `industrialkeyboard.json` into `/enterprise/usr/` with File Sync or a package (Zebra;
   survives an enterprise reset).
5. **Kiosk / lockdown** – the keyboard works in every allowed app without being part of the
   lockdown. Add *Industrial Keyboard* to the allowed apps only if users should open its setup
   screen or settings – or protect the settings with an admin password.
6. **Check** – tap into a text field: the keyboard appears with your layout. Number fields
   open the numeric pad.

## Microsoft Intune

1. On Android Enterprise devices the app has to come through **managed Google Play**. Use
   the listing of ICT-Baden once the app is published there. **Do not upload the APK as your own
   private app:** package names are unique in all of Google Play, so your upload would take
   `de.ictbaden.industrialkeyboard` for your developer account, and the official app could not
   use it any more. Until it is published, ask ICT-Baden: the Play Console can also publish an
   app privately to the managed Google Play of your organisation.
2. Create an **app configuration policy** (managed devices), use the configuration designer
   and set `config_json` or the single keys.
3. Allow the keyboard in the device restrictions if you limit input methods, and select it on
   the devices.

## VMware Workspace ONE / other EMMs

Add the app as an internal app or through managed Google Play and set the keys from the table
above in the **app configuration**. Every EMM that supports Android managed configurations
shows the schema automatically.

## Zebra StageNow / MX

| Task | CSP | Settings |
|---|---|---|
| Install the app | AppMgr | Install, APK path |
| Make it the keyboard | UiMgr | see [above](#make-it-the-keyboard) |
| Configuration as a file | FileMgr | target `/enterprise/usr/industrialkeyboard.json` |
| Configuration via intent | Intent | Mode *Immediate*, Action *Broadcast*, Android Action Name `de.ictbaden.industrialkeyboard.action.INSTALL_CONFIG`, Package Name `de.ictbaden.industrialkeyboard`, Class Name `de.ictbaden.industrialkeyboard.api.ApiReceiver`, Extra 0: name `config`, type String, value = the JSON. Extra 1: name `password`, type String – required once an admin password is set; without one, only managed devices accept the broadcast ([details](#broadcast-intent-stagenow-adb)) |
| Barcodes as keystrokes | DataWedge (or the DataWedge app) | a profile for your apps with keystroke output – see [Scanner & app integration](integration.md) |

## Configuration as a QR code

To set up a few devices by hand: configure one keyboard, open **Settings → Configuration → Export
as barcode** and scan the QR code on the next device with **Import from barcode** (camera). The
import asks before it replaces the configuration. Like the JSON export, the code contains the
admin password as a hash.

The code holds only the settings that differ from the defaults (the import fills in the rest), as
plain JSON or compressed as `ICTZ1:` + base64url of raw DEFLATE – whichever is shorter; the line
under the code says which. The JSON file export stays complete. Every place that takes a JSON configuration – file import,
the app-folder file, `/enterprise/usr`, `config_json`, the `INSTALL_CONFIG` broadcast – also takes
the `ICTZ1:` text. Very large configurations (many custom layouts) may not fit a QR code; then the
dialog says so – use the file.

## Configuration file

The keyboard reads `industrialkeyboard.json` from

1. `Android/data/de.ictbaden.industrialkeyboard/files/` (app folder; exists after the
   app or the keyboard has run once)
2. `/enterprise/usr/` (Zebra, persistent)

A file is applied when the keyboard opens, once per change of its timestamp or size.

Who may write the file decides whether it is trusted:

- `/enterprise/usr/` – always (written by Zebra's MX: StageNow, EMM).
- the app folder – it can be reached over USB, and before Android 11 any app with storage
  permission can read and write it, on managed devices too. So, like the
  [broadcast](#broadcast-intent-stagenow-adb), once an admin password is set the file must
  contain it as top-level `"password"` (the current password, not a new one):
  `{ "password": "change-me", "appearance": { … } }`. Before Android 11 the app folder is
  refused on unmanaged devices; on managed ones other apps could read that password until the
  keyboard deletes the file – use `/enterprise/usr` or the managed configuration there.

A file in the app folder that contains `"password"` or `"adminPassword"` is **deleted once the
keyboard has read it** – applied or refused – so the password does not stay there in plain text.
A refused file is shown under **Configuration → Last provisioning error**; put a corrected file
there (or change or touch a file without password) to try again.
Five wrong passwords lock files in the app folder and broadcasts for 5 minutes; a file arriving
during the lock is tried again afterwards. Any app can cause that lock, see
[below](#broadcast-intent-stagenow-adb).

```bash
adb push examples/warehouse-gloves.json \
  /sdcard/Android/data/de.ictbaden.industrialkeyboard/files/industrialkeyboard.json
```

## Broadcast intent (StageNow, adb)

An explicit broadcast with the JSON in the string extra `config` and the admin password in
`password`.

Any app on the device can send this broadcast, and Android does not tell the keyboard which one
did. So it is only accepted

- with the admin password, once a password is set, or
- while no admin password is set: only on a device managed by an EMM (the app has a managed
  configuration, or the device or work profile has an owner, i.e. Android Enterprise).

On an unmanaged device without a password the broadcast is refused. Set an admin password first
(in the settings, in a configuration file or with the managed configuration key `admin_password`)
or use the [configuration file](#configuration-file).

> **Managed devices without an admin password:** Android does not tell the keyboard which app
> sent the broadcast, so on such a device *every* app can reconfigure the keyboard (and set an
> admin password). Set an admin password with your EMM (`admin_password`) unless you trust every
> app on the device.

After 5 wrong passwords, broadcasts and configuration files in the app folder are refused for
5 minutes (result code `2`). The settings screen counts its own attempts, so an app cannot lock
the administrator out of it.

> **Denial of service:** any app on the device can send 5 broadcasts with wrong passwords, and
> repeat that every 5 minutes, to keep the broadcast and the app folder locked. It learns nothing
> and changes nothing, but your own broadcasts and files are refused meanwhile. This is the price
> of the lock against password guessing. The managed configuration, `/enterprise/usr` and the
> settings screen are not affected – use them if that happens; refusals show up under
> **Configuration → Last provisioning error**.

```bash
adb push examples/warehouse-gloves.json /data/local/tmp/keyboard.json
adb shell 'am broadcast -a de.ictbaden.industrialkeyboard.action.INSTALL_CONFIG -n de.ictbaden.industrialkeyboard/.api.ApiReceiver --es config "$(cat /data/local/tmp/keyboard.json)" --es password change-me'
adb shell rm /data/local/tmp/keyboard.json
```

Result code: `-1` = installed, `2` = refused (admin password missing or wrong, or unmanaged
device without a password), `3` = invalid
configuration (the message names the problem). Failures are also shown in the keyboard
settings under **Configuration → Last provisioning error**.

## Updates and signing

Android only installs an update if the new APK is signed with the same key as the installed
one. Releases from 2.0.0 on are signed with ICT-Baden's release key, so they update each other in
place, keeping the configuration. The App Bundle for Google Play is signed with the same key
([details](play-store.md#app-signing)). Check a downloaded APK with `SHA256SUMS.txt` from the
same release (`sha256sum -c --ignore-missing SHA256SUMS.txt`).

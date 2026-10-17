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
adb install -r IndustrialKeyboard-1.0.0.apk
```

The app needs no permissions apart from vibration – in particular **no internet access**.

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
| `theme` | Choice | System, Light, Dark, High contrast |
| `alphabet` | Choice | QWERTY, QWERTZ, AZERTY, Nordic, Spanish |
| `numeric_order` | Choice | Phone (1-2-3 at the top) or Calculator (7-8-9 at the top) |
| `scan_tab` | Bool | Show the scan tab (default on) |
| `function_tab` | Bool | Show the function key tab (default on) |
| `sound` | Bool | Click sound (default off) |
| `vibrate` | Bool | Vibration (default on) |
| `admin_password` | String | Protects the keyboard settings and configuration broadcasts |

The keys are applied as soon as the EMM changes them and whenever the keyboard opens. The
single keys only change their own option; `config_json` replaces the whole configuration.
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

1. On Android Enterprise devices the app has to come through **managed Google Play**
   (private app). Google Play only accepts release-signed APKs – the current debug-signed APK
   is not suitable for that yet.
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
| Configuration via intent | Intent | Mode *Immediate*, Action *Broadcast*, Android Action Name `de.ictbaden.industrialkeyboard.action.INSTALL_CONFIG`, Package Name `de.ictbaden.industrialkeyboard`, Class Name `de.ictbaden.industrialkeyboard.api.ApiReceiver`, Extra 0: name `config`, type String, value = the JSON. With an admin password also Extra 1: name `password`, type String |
| Barcodes as keystrokes | DataWedge (or the DataWedge app) | a profile for your apps with keystroke output – see [Scanner & app integration](integration.md) |

## Configuration file

The keyboard reads `industrialkeyboard.json` from

1. `Android/data/de.ictbaden.industrialkeyboard/files/` (app folder; exists after the
   app or the keyboard has run once)
2. `/enterprise/usr/` (Zebra, persistent)

A file is applied when the keyboard opens, once per change of its timestamp or size.

```bash
adb push examples/warehouse-gloves.json \
  /sdcard/Android/data/de.ictbaden.industrialkeyboard/files/industrialkeyboard.json
```

## Broadcast intent (StageNow, adb)

An explicit broadcast with the JSON in the string extra `config` (and the admin password in
`password`, if one is set):

```bash
adb push examples/warehouse-gloves.json /data/local/tmp/keyboard.json
adb shell 'am broadcast -a de.ictbaden.industrialkeyboard.action.INSTALL_CONFIG -n de.ictbaden.industrialkeyboard/.api.ApiReceiver --es config "$(cat /data/local/tmp/keyboard.json)"'
adb shell rm /data/local/tmp/keyboard.json
```

Result code: `-1` = installed, `2` = admin password missing or wrong, `3` = invalid
configuration (the message names the problem). Failures are also shown in the keyboard
settings under **Configuration → Last provisioning error**.

## Updates and signing

Android only installs an update if the new APK is signed with the same key as the installed
one. The current releases are **debug-signed**. When switching to a release key later, the app
has to be uninstalled and reinstalled once – then enable and select the keyboard and
distribute the configuration again.

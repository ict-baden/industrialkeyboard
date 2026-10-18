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

The operator of the devices (your company) is responsible for the data typed with the keyboard;
ICT-Baden processes none of it.

## What is stored on the device

Only the configuration, in the app's private storage (other apps cannot read it):

| File | Content |
|---|---|
| `files/config.json` | The [configuration](configuration.md). The admin password only as a salted PBKDF2 hash. |
| `shared_prefs/provisioning.xml` | Which configuration sources were applied already (time stamp and size of the file, a hash of the managed configuration) and the last provisioning error. |

Both are excluded from Android backup and device transfer, because the configuration is
provisioned per device and contains the password hash.

The keyboard writes short messages to the system log (logcat) when a configuration is applied
or rejected – never typed text or barcodes.

## What the keyboard reads

- The **managed configuration** of your EMM (Android Enterprise app restrictions).
- `industrialkeyboard.json` in `Android/data/<package>/files/` and in Zebra's `/enterprise/usr/`, if present ([Deployment](deployment.md)).
- The **input type** of the focused field (number, e-mail, password …) to choose the matching keys.

## Connections and broadcasts

There are no network connections. On the device, the keyboard exchanges broadcasts with other
apps – see [Scanner & app integration](integration.md):

| Direction | What | Why |
|---|---|---|
| sends | Soft scan trigger to DataWedge (Zebra), the Honeywell scanner service or the app in `scanner.intentAction` | Scan pad, scan key, `{SCAN}` |
| receives | `…action.SCAN_INPUT` with a barcode | Types the barcode into the focused field. Any app on the device can send it – switch it off with `scanner.input.enabled: false` if you do not need it. |
| receives | `…action.SHOW_TAB` | Switches the keyboard page |
| receives | `…action.INSTALL_CONFIG` | Installs a configuration; only with the admin password, or on an EMM-managed device while no password is set |

The links in *Settings → Info* (source code, support, website) open in the browser only when you
tap them.

## Deleting everything

- *Settings → Configuration → Reset to defaults* removes the configuration (the admin password stays).
- Android *Settings → Apps → Industrial Keyboard → Storage → Clear storage*, or uninstalling the app, removes all of it.

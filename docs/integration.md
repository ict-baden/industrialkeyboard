# Scanner & app integration

How the keyboard works together with barcode scanners and with your own apps.

## Starting the scanner

The scan tab, the scan key and the macro `{SCAN}` pull the scanner's **soft trigger**:

| Control | Behavior |
|---|---|
| Scan pad – tap | Starts one scan; the scanner stops after a barcode or its timeout |
| Scan pad – hold | Scans while you hold it (like the hardware trigger), stops when you let go |
| Scan key, `{SCAN}` | Start / stop (toggle) |

Which scanner is used is set with [`scanner.trigger`](configuration.md#scanner):

| Trigger | What the keyboard sends |
|---|---|
| `datawedge` (Zebra) | Broadcast `com.symbol.datawedge.api.ACTION` to `com.symbol.datawedge` with `com.symbol.datawedge.api.SOFT_SCAN_TRIGGER` = `START_SCANNING`, `STOP_SCANNING` or `TOGGLE_SCANNING` |
| `honeywell` | Broadcast `com.honeywell.aidc.action.ACTION_CONTROL_SCANNER` to `com.intermec.datacollectionservice` with the boolean `com.honeywell.aidc.extra.EXTRA_SCAN` (not yet tested on a Honeywell device) |
| `intent` | Broadcast `scanner.intentAction` (to `scanner.intentPackage`, if set) for start and toggle; there is no stop |
| `auto` (default) | `datawedge` if DataWedge is installed, otherwise `honeywell` if the Honeywell service is installed, otherwise none |

Without a scanner the scan tab and the scan key are hidden.

With DataWedge the soft trigger works for the DataWedge profile of the app in the foreground –
the app you are typing into. Make sure that profile has the barcode input enabled.

## Getting the barcode into the field

### Option A – keystroke output (simplest)

Let the scanner type the barcode itself: DataWedge *Keystroke output* or the keyboard wedge of
your scanner app. Nothing to configure in the keyboard – it only pulls the trigger.

### Option B – the keyboard types it

The scanner sends the barcode as a broadcast, the keyboard commits it to the focused field in
one step and adds **Enter** (default), **Tab** or nothing
([`scanner.input`](configuration.md#scanner)). **Off by default** – switch it on with
`"scanner": { "input": { "enabled": true } }` (or in the settings under *Scanner*).

- Action: `de.ictbaden.industrialkeyboard.action.SCAN_INPUT` (or your own
  `scanner.input.action`)
- Data: string extra `data` (or `scanner.input.extra`); `com.symbol.datawedge.data_string`,
  `barcode_string`, `decode_string` and `com.honeywell.aidc.extra.EXTRA_DATA` are understood
  as well.
- Industrial Keyboard must be the active keyboard, visible, and a text field must have focus.
- Never typed into password fields or fields without a text editor (`TYPE_NULL`, some
  terminal emulators) – use keystroke output there. Visible password fields
  (`textVisiblePassword`) count as password fields: apps switch to them for "show password". If
  your app uses that type only to turn off suggestions, use `textNoSuggestions` instead, or
  keystroke output. Barcodes longer than 8,192 characters are ignored.

**DataWedge setup** – in the profile of your app:

| Setting | Value |
|---|---|
| Keystroke output | off |
| Intent output | on |
| Intent action | `de.ictbaden.industrialkeyboard.action.SCAN_INPUT` |
| Intent category | empty |
| Intent delivery | Broadcast intent |

**Test without a scanner:**

```bash
adb shell am broadcast -a de.ictbaden.industrialkeyboard.action.SCAN_INPUT --es data 4006381333931
```

The number appears in the focused field, followed by Enter.

> **Security note:** Android does not tell the keyboard which app sent a broadcast. Without
> restrictions every app on the device can send it and thereby type into the focused field (not
> into password fields). That is why it is off by default. If you switch it on, restrict the
> senders where your scanner app allows it:
>
> - `scanner.input.permission` – a permission the sending app must hold; Android delivers the
>   broadcast only from apps that have it. Use a `signature` (or `signatureOrSystem`) permission
>   of your own scanner or integration app: a permission with protection level `normal` can be
>   declared and granted by any app and protects nothing.
> - `scanner.input.allowedPackages` – package names of the apps that may send. Android 14+ only,
>   and only for senders that identify themselves (`BroadcastOptions.setShareIdentityEnabled`).
>   It fails closed: with a list set, unidentified senders and older Android versions are
>   refused. If scans stop arriving after you set it, your scanner app does not identify itself.
>
> Broadcasts refused by the keyboard's own checks are logged with the reason
> (`adb logcat -s KeyboardService`). Broadcasts from apps without the required `permission` are
> dropped by Android and never reach the keyboard, without a log line.

## Intent API

### Show a tab – `SHOW_TAB`

Switch the keyboard to a page, e.g. the numeric pad for a quantity field or your own layout for
a confirmation step:

| | |
|---|---|
| Action | `de.ictbaden.industrialkeyboard.action.SHOW_TAB` |
| Extra `tab` | `alpha`, `numeric`, `symbols`, `function`, `scan` or the id of a custom layout |

When the keyboard is hidden, the request applies the next time it opens.

```kotlin
sendBroadcast(
    Intent("de.ictbaden.industrialkeyboard.action.SHOW_TAB")
        .setClassName("de.ictbaden.industrialkeyboard", "de.ictbaden.industrialkeyboard.api.ApiReceiver")
        .putExtra("tab", "numeric"),
)
```

```bash
adb shell am broadcast -a de.ictbaden.industrialkeyboard.action.SHOW_TAB \
  -n de.ictbaden.industrialkeyboard/.api.ApiReceiver --es tab numeric
```

Apps targeting Android 11 or later should declare the keyboard in their manifest so that it is
visible to them:

```xml
<queries>
    <package android:name="de.ictbaden.industrialkeyboard" />
</queries>
```

Often no intent is needed at all: give the field the right input type (`number`,
`numberDecimal`, `phone`, `date`, `textEmailAddress`, `textUri` …) and the keyboard picks the
matching page by itself. In web apps the same works with `<input type="number">`,
`inputmode="numeric"`, `type="tel"`, `type="email"` and so on.

### Install a configuration – `INSTALL_CONFIG`

See [Deployment → Broadcast intent](deployment.md#broadcast-intent-stagenow-adb).

## What your app receives

| Key | Delivered as |
|---|---|
| Letters, digits, symbols, macro text | Committed text (no key events) |
| Enter, `{ENTER}` | The field's editor action (next, search, done …); in multi-line fields a new line |
| Backspace | `KEYCODE_DEL`, or deletes the selection |
| Tab, Esc, Ins, Del, Home, End, PgUp, PgDn, arrows | Key events (`KEYCODE_TAB`, `KEYCODE_ESCAPE`, `KEYCODE_INSERT`, `KEYCODE_FORWARD_DEL`, `KEYCODE_MOVE_HOME`, `KEYCODE_MOVE_END`, `KEYCODE_PAGE_UP`, `KEYCODE_PAGE_DOWN`, `KEYCODE_DPAD_*`) |
| F1 … F12 | `KEYCODE_F1` … `KEYCODE_F12` |
| F13 … F24 | `KEYCODE_F1` … `KEYCODE_F12` with Shift (`META_SHIFT_ON`) |

Key events arrive through the normal key handling of the focused window
(`dispatchKeyEvent`, `onKeyDown`). In a WebView they become `keydown` / `keyup` events – F13
is `key: "F1"` with `shiftKey: true`.

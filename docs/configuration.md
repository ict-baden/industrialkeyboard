# Configuration

The whole keyboard is described by one JSON document. The same document works everywhere:
as managed configuration (`config_json`), as a file, as a broadcast, or imported in the app
(**Settings → Configuration → Import from file**). How to get it onto the devices:
[Deployment](deployment.md). Ready-made templates: [`examples/`](../examples).

- Every option is optional – missing options keep their defaults, `{}` is a valid configuration.
- A new configuration **replaces** the current one (including changes made in the settings).
  Only the admin password is kept when the new configuration does not set one.
- Mistakes are rejected as a whole with a precise message, e.g.
  `keys.numeric.p1: unknown token {TABB}` – the keyboard keeps working with its previous
  configuration. The last error is shown under **Settings → Configuration**.
- **Settings → Configuration → Export to file** writes the current configuration in exactly
  this format – a good starting point for your own.

## Complete example with all defaults

```json
{
  "version": 1,
  "language": "auto",
  "appearance": {
    "theme": "system",
    "keyHeight": 100,
    "width": 100,
    "numberRow": false,
    "functionRow": false,
    "keyHints": true,
    "modifierRow": false,
    "functionModifiers": false
  },
  "layout": {
    "alphabet": "auto",
    "navigation": "tabs",
    "numericOrder": "phone"
  },
  "tabs": {
    "alpha": true,
    "numeric": true,
    "symbols": true,
    "function": true,
    "scan": true,
    "preferred": "auto"
  },
  "keys": {
    "alpha.p1": "{TAB}",
    "numeric.p1": "-",
    "numeric.p2": "/",
    "numeric.p3": ":",
    "numeric.p4": "+"
  },
  "typing": {
    "autoCapitalize": true,
    "doubleSpacePeriod": true,
    "longPressDelay": 400
  },
  "feedback": {
    "keyPopup": true,
    "sound": false,
    "vibrate": true
  },
  "scanner": {
    "trigger": "auto",
    "scanKey": false,
    "input": {
      "enabled": false,
      "action": "de.ictbaden.industrialkeyboard.action.SCAN_INPUT",
      "extra": "data",
      "suffix": "enter"
    }
  }
}
```

`version` is `1`. A configuration with a higher version is rejected, so an old app never
misreads a newer file.

## `language`

Language of the setup and settings screens and of the scan pad:

| Value | Language |
|---|---|
| `auto` (default) | Follows the device; English if the device language is not one of the five |
| `en` | English |
| `de` | German |
| `es` | Spanish |
| `fr` | French |
| `zh-Hans` | Chinese (simplified) |

Also selectable on the device under **Settings → Look → Language**, and as the single option
`language` of the managed configuration. The letter layout is a separate option
([`layout.alphabet`](#layout)). Error messages carry a code (`IK-0101` …) that is the same in
every language: [Error codes](error-codes.md).

## `appearance`

| Option | Values | Default | Effect |
|---|---|---|---|
| `theme` | `system`, `light`, `dark`, `contrast` | `system` | `system` follows the device's dark mode. `contrast`: black keys with white outlines and yellow highlights, for bright sunlight and low vision |
| `accentColor` | `#RRGGBB` or `#AARRGGBB` | blue `#2563EB`, yellow `#FFD600` in `contrast` | Selected tab, Enter key, Shift, scan pad and highlights |
| `keyHeight` | 70–160 (percent) | `100` | Row height; 100 % = 54 dp. Use 120–140 for gloves |
| `width` | 50–100 (percent) | `100` | Width of the keys, centered – for tablets and landscape |
| `numberRow` | `true` / `false` | `false` | Row with 1–0 above the letters |
| `functionRow` | `true` / `false` | `false` | Row with F1–F12 above the letters (and above the number row) |
| `keyHints` | `true` / `false` | `true` | Small digits on the top letter row – long press types them (only without number row) |
| `modifierRow` | `true` / `false` | `false` | Row with **Ctrl, Win, Alt**, Esc, Tab and Del above the letters (above the number and function rows) – for remote desktop and PC shortcuts, see [Ctrl, Win and Alt](#ctrl-win-and-alt) |
| `functionModifiers` | `true` / `false` | `false` | Extra row on the Fn tab with Ctrl, Win, Alt, Menu (context menu key), Print (Print Screen) and Pause (Alt+F4, Ctrl+Home …). All pages then get the height of five rows |

<p align="center"><img src="images/themes.png" alt="Light, dark and high contrast" width="100%"></p>

## `layout`

| Option | Values | Default | Effect |
|---|---|---|---|
| `alphabet` | `auto`, `qwerty`, `qwertz`, `azerty`, `nordic`, `spanish` | `auto` | Letter layout. `auto` follows the first system language of the device (German, Czech, Swiss … → `qwertz`, French and Belgian → `azerty`, Swedish, Danish, Norwegian, Finnish → `nordic`, Spanish and Catalan → `spanish`, everything else `qwerty`) and changes with it. `qwertz` adds ü ö ä, `nordic` å ö ä, `spanish` ñ. Accents and special letters (ß, é, ç …) on long press |
| `navigation` | `tabs`, `keys` | `tabs` | `tabs`: tab bar above the keys (ABC, 123, #+=, Fn, scan, custom layouts, hide button). `keys`: no tab bar, `?123` / `ABC` keys like a phone keyboard; on the numeric pad the last side key becomes `ABC`. Other pages are then reachable with [`{SHOW:…}`](#macros) on a programmable key |
| `numericOrder` | `phone`, `calculator` | `phone` | `phone`: 1-2-3 at the top. `calculator`: 7-8-9 at the top. Dial pads (phone number fields) always use phone order |

## `tabs`

| Option | Values | Default | Effect |
|---|---|---|---|
| `alpha`, `numeric`, `symbols`, `function`, `scan` | `true` / `false` | `true` | Show the tab. The scan tab only appears when a scanner is available ([`scanner.trigger`](#scanner)). If every tab is off, ABC stays |
| `preferred` | `auto`, `alpha`, `numeric`, `symbols`, `function`, `scan` or the id of a [custom layout](#custom-layouts) | `auto` | Page that opens in text fields. `auto` = letters |

The page always follows the field first: number, phone, date and time fields open the
numeric pad, whatever is configured here.

| Field (Android input type) | Opens |
|---|---|
| Text | `preferred` page; e-mail fields get `@` instead of `,`, URL fields `/` and `.com` on long press of `.` |
| Number, decimal, signed | Numeric pad with the programmable side keys |
| Number password (PIN) | Numeric pad without side keys |
| Phone | Dial pad with `+ * # -` |
| Date / time / date and time | Numeric pad with `/ - . ,` / `: . - ,` / `/ : - .` |

## `keys` – programmable keys

| Key | Position | Default |
|---|---|---|
| `alpha.p1` | Letters, bottom row, left of the comma | `{TAB}` |
| `numeric.p1` … `numeric.p4` | Numeric pad, left column, top to bottom | `-` `/` `:` `+` |

The value is a [macro](#macros): text (`-`), keys (`{TAB}`), both (`OK{ENTER}`) or a command
(`{SCAN}`). The key shows the text, the key name (`F13`) or an icon (Tab, Scan, Enter …).
Phone, date and time fields show fixed keys instead of the numeric ones.

## `typing`

| Option | Values | Default | Effect |
|---|---|---|---|
| `autoCapitalize` | `true` / `false` | `true` | Capital letters where the field asks for them: sentence start, every word, or all capitals (item numbers) |
| `doubleSpacePeriod` | `true` / `false` | `true` | Two spaces after a word type `. ` |
| `longPressDelay` | 200–1500 (ms) | `400` | Time until a long press shows the alternatives; 500–700 avoids accidental accents with gloves |

Always on: rollover typing with several fingers, key repeat on Backspace, Del and the arrow
keys, slide on the space bar to move the cursor, long press on the space bar to switch
keyboards, double tap on Shift for caps lock, long press on the hide button for the settings.

## `feedback`

| Option | Values | Default | Effect |
|---|---|---|---|
| `keyPopup` | `true` / `false` | `true` | Preview bubble above the pressed key; never in password fields (visible ones included) |
| `sound` | `true` / `false` | `false` | Click sound (independent of the system's touch sounds) |
| `soundVolume` | 0–100 | not set = default volume | Click volume |
| `vibrate` | `true` / `false` | `true` | Short vibration on every key |
| `vibrationMs` | 5–100 (ms) | not set = 18 ms | Vibration length; 30–40 can be felt through gloves |

## `scanner`

| Option | Values | Default | Effect |
|---|---|---|---|
| `trigger` | `auto`, `datawedge`, `honeywell`, `intent`, `camera`, `none` | `auto` | How the scan pad, the scan key and `{SCAN}` start the scanner. `auto`: the scan engine of the device if it has one (Zebra: DataWedge lists a built-in imager or laser or a connected Bluetooth/USB scanner; Honeywell: scanner service installed), otherwise the camera. `camera`: always the camera – its picture replaces the keys, the first code read is typed with `input.suffix`, the camera closes after the code, with ✕ or after one minute. Without a scanner and camera the scan tab and the scan key are hidden |
| `intentAction` | text | – | Broadcast action that starts a scan, for `trigger: "intent"` (required then) |
| `intentPackage` | text | – | Package that receives it (optional, recommended) |
| `scanKey` | `true` / `false` | `false` | Scan key in the bottom row of the letters, next to Enter |
| `input.enabled` | `true` / `false` | `false` | Type barcodes that a scanner app sends as broadcast. Not into password fields or `TYPE_NULL` fields. See the [security note](integration.md#option-b--the-keyboard-types-it) |
| `input.action` | text | `de.ictbaden.industrialkeyboard.action.SCAN_INPUT` | Broadcast action the keyboard listens to |
| `input.extra` | text | `data` | String extra with the barcode. Also checked: `com.symbol.datawedge.data_string`, `barcode_string`, `decode_string`, `com.honeywell.aidc.extra.EXTRA_DATA` |
| `input.suffix` | `none`, `enter`, `tab` | `enter` | Key after the barcode |
| `input.permission` | text | – | Permission the sending app must hold; Android delivers the broadcast only from apps that have it |
| `input.allowedPackages` | list of package names | – | Only these apps may send. Android 14+ and only senders that share their identity; with a list set, all others are refused |

How it works with DataWedge and other scanner apps: [Scanner & app integration](integration.md).

## Custom layouts

Your own keyboards, shown as extra tabs after the built-in ones:

```json
{
  "customLayouts": [
    {
      "id": "picking",
      "label": "Pick",
      "rows": [
        [ { "label": "OK", "output": "OK{ENTER}", "width": 2, "style": "accent" }, { "label": "Skip", "output": "SKIP{ENTER}", "width": 2 } ],
        [ { "label": "Short", "output": "SHORT{ENTER}", "width": 2 }, { "label": "Damaged", "output": "DMG{ENTER}", "width": 2 } ],
        [ { "label": "Scan", "output": "{SCAN}", "width": 2, "style": "function" }, { "label": "Numbers", "output": "{SHOW:numeric}", "width": 2, "style": "function" } ]
      ]
    }
  ]
}
```

<p align="center"><img src="images/screen-custom.png" alt="The Pick layout" width="260"></p>

| Option | Values | Effect |
|---|---|---|
| `id` | `a`–`z`, `0`–`9`, `-`, `_`, up to 24 characters | Unique name, used by `tabs.preferred`, `{SHOW:id}` and the `SHOW_TAB` intent. `auto`, `alpha`, `numeric`, `symbols`, `function` and `scan` are reserved |
| `label` | text | Tab label; default: the id in capitals |
| `rows` | array of rows, each an array of keys | The keys, top to bottom |

A key is either a **string** – a [macro](#macros); the key shows its text, key name or icon
(`"{F13}"`, `"Y{ENTER}"`, `"{TAB}"`) – or an **object**:

| Option | Values | Default | Effect |
|---|---|---|---|
| `label` | text | – | Text on the key |
| `output` | [macro](#macros) | the label | What the key types or does |
| `width` | 0.5–10 | `1` | Width relative to the other keys; shorter rows are centered |
| `style` | `normal`, `function`, `accent` | `normal` | Key color: normal, gray like Shift and Tab, or the accent color like Enter |

The rows share the height of the letter page, so a layout with three rows gets taller keys –
ideal for a handful of big buttons.

## Macros

Text with tokens in curly braces. Tokens are case-insensitive; unknown tokens are rejected
when the configuration is loaded.

| Kind | Tokens |
|---|---|
| Keys | `{TAB}` `{ENTER}` `{ESC}` `{BS}` (`{BACKSPACE}`) `{DEL}` (`{DELETE}`) `{INS}` (`{INSERT}`) `{HOME}` `{END}` `{PGUP}` (`{PAGE-UP}`) `{PGDN}` (`{PAGE-DOWN}`) `{UP}` `{DOWN}` `{LEFT}` `{RIGHT}` |
| Function keys | `{F1}` … `{F12}`, `{F13}` … `{F24}` (sent as Shift + F1 … F12) |
| Modifiers | `{CTRL}` (`{CONTROL}`), `{ALT}`, `{WIN}` (`{META}`) press the key alone (`{WIN}` opens the Start menu). A key whose whole macro is one of them becomes a sticky modifier key, like those of `modifierRow` |
| Key combinations | Modifiers and one key joined with `+`: `{CTRL+C}`, `{ALT+F4}`, `{WIN+R}`, `{CTRL+SHIFT+ESC}`, `{CTRL+ALT+END}`. Modifiers: `CTRL`, `ALT`, `WIN`, `SHIFT`; key: any key name above, `A`–`Z`, `0`–`9` or `SPACE` |
| More keys | `{PRINT}` (Print Screen), `{PAUSE}`, `{MENU}` (context menu key) |
| Characters | `{SPACE}`, `{LBRACE}` / `{OPEN-BRACE}` = `{`, `{RBRACE}` / `{CLOSE-BRACE}` = `}`, `{U+20AC}` or `{HEX:20AC}` = any Unicode character (here €) |
| Commands | `{SCAN}` start/stop the scanner, `{HIDE}` hide the keyboard, `{SETTINGS}` open the settings, `{NEXT-KEYBOARD}` switch to the next keyboard, `{SHOW:tab}` show a tab (`alpha`, `numeric`, `symbols`, `function`, `scan` or a custom layout id) |

`{ENTER}` behaves like the Enter key: it runs the field's action (next field, search, send …)
or starts a new line in multi-line fields.

Examples: `OK{ENTER}` · `{F13}` · `{TAB}{TAB}` · `{U+00E4}` · `{SHOW:picking}` · `{ESC}{F3}` · `{CTRL+C}` · `{WIN}notepad{ENTER}`

## Ctrl, Win and Alt

For remote desktop (Microsoft Remote Desktop / Windows App, RD clients, VNC, Citrix) and PC
shortcuts in web apps. There are three ways to get the keys – switch on what you need:

| Option | Where | How |
|---|---|---|
| Row above the letters | ABC tab | `appearance.modifierRow: true`, or *Settings → Appearance → Ctrl, Win and Alt row* |
| Row on the function keys | Fn tab | `appearance.functionModifiers: true`, or *Settings → Appearance → Ctrl, Win and Alt on Fn* |
| Own keys | programmable keys, custom layouts | `{CTRL}`, `{ALT}`, `{WIN}` as sticky keys, or fixed combinations such as `{CTRL+ALT+END}` |

The modifier keys are sticky, because you cannot hold two keys at once with gloves:

- **Tap** – the key lights up and applies to the next key: tap *Ctrl*, then *C* = Ctrl+C.
  Several modifiers combine (Ctrl, Alt, then Del). The modifiers stay on when you switch tabs, so
  *Alt* on the letters followed by *F4* on the Fn tab is Alt+F4.
- **Tap again** – presses the key alone (*Win* opens the Start menu, *Alt* the menu bar).
- **Shift** – a Shift you tap yourself also applies to Tab, the arrows, Esc, Del, Home/End and
  the other keys: Shift+Tab goes back a field, Shift+→ selects. Neither the automatic Shift at
  the start of a sentence nor Caps Lock does this, so Tab stays Tab while typing in capitals.
- **Hold** – locks the key (filled with the accent color) until you tap it again.

While a modifier is on, letters, digits and the US punctuation keys go out as key presses instead
of text, so the app sees Ctrl+C and not the letter c. Shift counts only when you tap it yourself,
not at the start of a sentence. Ctrl, Win and Alt are pressed and released around the key like on
a PC keyboard.

> [!TIP]
> Windows keeps Ctrl+Alt+Del for itself; in a remote session send **Ctrl+Alt+End**
> (`{CTRL+ALT+END}`). [`examples/remote-desktop.json`](../examples/remote-desktop.json) has a
> ready-made *RDP* tab with it, Alt+Tab, Win+R, Ctrl+C/V/Z and Alt+F4.

## Admin password

```json
{ "adminPassword": "change-me" }
```

- Protects the keyboard settings (asked when they are opened), configuration broadcasts
  (`INSTALL_CONFIG` needs the extra `password`; without a password broadcasts are only accepted on EMM-managed devices)
  and configuration files in the app folder (top-level `"password"`), see [Deployment](deployment.md#configuration-file).
- Stored only as a salted PBKDF2 hash; an export contains `adminPasswordHash` instead of the
  password, and importing that export keeps the same password.
- A configuration without `adminPassword` keeps the current password. Change or remove it in
  **Settings → Configuration → Admin password**; the managed configuration key
  `admin_password` sets it centrally.

# Configuration

The whole keyboard is described by one JSON document. The same document works everywhere:
as managed configuration (`config_json`), as a file, as a broadcast, or imported in the app
(**Settings → Configuration → Import from file**). How to get it onto the devices:
[Deployment](deployment.md). Ready-made templates: [`examples/`](../examples).

- Every option is optional – missing options keep their defaults, `{}` is a valid configuration.
  **Which** defaults depends on [`configVersion`](#configversion--defaults-of-21-or-todays):
  without it, a configuration counts as written for 2.1 and keeps the 2.1 defaults. Write
  `"configVersion": 3` into new configurations to get today's defaults.
- A new configuration **replaces** the current one (including changes made in the settings).
  Only the admin password is kept when the new configuration does not set one.
- Mistakes are rejected as a whole with a precise message, e.g.
  `keys.numeric.p1: unknown token {TABB}` – the keyboard keeps working with its previous
  configuration. The last error is shown under **Settings → Configuration**.
- **Settings → Configuration → Export to file** writes the current configuration in exactly
  this format – a good starting point for your own.

## Order of precedence

The keyboard computes the configuration it works with when a field starts and when the
configuration changes – never per key. From lowest to highest:

1. **Stored configuration.** `config_json` from the MDM, a file, the `INSTALL_CONFIG` broadcast
   and **Import** replace it as a whole; the single options of the managed configuration and the
   admin settings change single values in it. Export and the QR code contain exactly this.
2. **[App profile](#appprofiles--app-profiles)**: the first entry of `appProfiles` that matches
   the focused field (app, field type, hint) overrides a few values. Never stored, never exported.
3. **[Quick toggles](#quicktoggles--quick-toggles-on-the-device)** switched on the device (glove
   mode, contrast, scanner, sound), only those in `quickToggles.allowed` of the stored
   configuration. Stored apart from the configuration; where the app profile sets the same
   field, the toggle wins.
4. **Field rules**, which always win: number, phone and date fields open their pad; password
   fields get no suggestions, no autocorrection and no key preview.

Admin gates – what users may change (`quickToggles.allowed`, `typing.textEditing`, `snippets.enabled`,
`snippets.allowUser`) – are read from
the stored configuration only; an app profile cannot change them.

The page that opens: a `SHOW_TAB` request or [`{SHOW:…}`](#macros) > the field type (number,
phone, date) > the app profile's `page` > [`tabs.preferred`](#tabs).

## Complete example with all defaults

```json
{
  "version": 1,
  "configVersion": 3,
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
    "alpha.p1": "",
    "numeric.p1": "-",
    "numeric.p2": "/",
    "numeric.p3": ":",
    "numeric.p4": "+"
  },
  "typing": {
    "autoCapitalize": true,
    "doubleSpacePeriod": true,
    "longPressDelay": 300
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
    },
    "camera": {
      "symbologies": [],
      "aim": "frame",
      "continuous": false,
      "duplicateDelayMs": 1500,
      "confirmReads": 1,
      "minLength": 0,
      "maxLength": 0,
      "zoom": 1.0,
      "torch": false,
      "inverted": true,
      "beep": true,
      "vibrate": true,
      "timeoutSeconds": 60
    },
    "data": {
      "prefix": "",
      "suffix": null,
      "trim": true,
      "removeSpaces": false,
      "case": "none",
      "stripLeading": 0,
      "stripTrailing": 0,
      "replace": [],
      "gs1": "hri",
      "gsReplacement": null,
      "rules": [],
      "unmatched": "send"
    },
    "ocr": {
      "enabled": true,
      "mode": "pick",
      "pattern": null,
      "startInOcr": false,
      "confirmReads": 2,
      "minLength": 0,
      "maxLength": 0,
      "charset": "any"
    },
    "nfc": {
      "enabled": true,
      "read": "ndefOrUid",
      "uidFormat": "hex",
      "uidReversed": false,
      "uidSeparator": "",
      "ndefRecord": "first",
      "beep": true,
      "vibrate": true
    }
  },
  "snippets": {
    "enabled": true,
    "shortcuts": true,
    "button": false,
    "allowUser": true,
    "items": []
  },
  "quickToggles": { "allowed": ["glove", "contrast", "sound"] },
  "accessibility": {
    "liftToType": false,
    "speakPasswordKeys": "headset",
    "followFontSize": false
  },
  "appProfiles": []
}
```

`appProfiles` is left out of an export while it is empty.

`version` is `1`. A configuration with a higher version is rejected, so an old app never
misreads a newer file.

## `configVersion` – defaults of 2.1 or today's

Many defaults changed after 2.1.1 to work like Gboard (toolbar, suggestions, autocorrection, no
Tab key at the bottom left …), and new features are on by default. A configuration written for
2.1 relies on the old defaults for everything it leaves out – so an EMM that pushes its old
`config_json` or file again after the update would silently change a whole fleet. To prevent
that, `configVersion` says which defaults fill in the options a configuration leaves out:

| `configVersion` | Meaning | Missing options get |
|---|---|---|
| missing, `1`, `2` | Written for 2.1 or older (`1` = 1.x, `2` = 2.0 and 2.1) | the **defaults of 2.1** (table below): the keyboard behaves as it did in 2.1 |
| `3` | Written for this version | **today's defaults**, as listed in the sections of this page |
| higher | Written by a newer app | today's defaults (the newest this version knows) |

`configVersion` is not the format version: `version` stays `1`. It must be a whole number from 1;
anything else is rejected (`configVersion: a whole number from 1 expected …`).

It applies to every way a configuration arrives: `config_json`, the configuration file in the
app folder or `/enterprise/usr`, the `INSTALL_CONFIG` broadcast, **Import** in the app, and the
configuration a device stored with 2.1. The keyboard always **writes** `configVersion: 3` with every
value – the stored configuration, **Export to file** and the QR code – so a configuration saved or
exported once keeps its values, whatever defaults it was read with. A device that never had a
configuration (nothing stored, nothing from the EMM) and *Reset to defaults* get today's defaults.

Options with a different default without `configVersion` 3:

| Option | Without `configVersion` (as in 2.1) | `configVersion: 3` |
|---|---|---|
| `layout.topBar` | `tabs` | `toolbar` |
| `layout.alphabet` | `qwerty` | `auto` |
| `layout.umlautKeys` | `true` – QWERTZ with ü, ö, ä as own keys, as in 2.1 | `false` |
| `layout.symbolsKey` | `false` | `true` |
| `keys.alpha.p1` | `{TAB}` | empty (no key) |
| `typing.longPressDelay` | `400` | `300` |
| `typing.suggestions` | `false` | `true` |
| `typing.autocorrect` | `false` | `true` |
| `typing.nextWord` | `false` | `true` |
| `typing.deleteGesture` | `false` | `true` |
| `typing.voice` | `off` | `auto` |
| `typing.autofillSuggestions` | `false` | `true` |
| `typing.systemDictionary` | `false` | `true` |
| `typing.personalDictionary` | `false` | `true` |
| `appearance.dynamicColor` | `false` | `true` |
| `appearance.emojiKey` | `false` | `true` |
| `feedback.vibrationMs` | `18` – the fixed vibration of 2.1 | not set – the system's keyboard tick |
| `scanner.data.trim` | `false` – scan broadcasts typed exactly as received, as in 2.1 | `true` |
| `scanner.nfc.enabled` | `false` | `true` |
| `snippets.allowUser` | `false` – no own snippets | `true` |

Every other option has the same default in both. That includes options 2.1 did not have but that
change nothing visible by themselves: `typing.textEditing`, `snippets.enabled` and
`quickToggles.allowed` only add tiles to the toolbar's ⊞ (with `topBar: tabs` there is no ⊞, and
without `snippets.items` there are no snippets), `scanner.camera` and `scanner.ocr` only apply
when the camera scans, `appearance.modifierRow`, `functionModifiers`, `symbolHints`,
`clipboardHistory`, `oneHanded`, `appProfiles` and `accessibility` are off or empty, and
`feedback.keyPopup` was already on in 2.1.

**Not covered** by `configVersion`, because they are no default of an option:

- `scanner.trigger: "auto"` uses the camera on devices **without** a scan engine (2.1: no
  scanner there, so no scan tab). Zebra and Honeywell devices with a scan engine are not affected.
  For the 2.1 behaviour on such devices set `"trigger": "none"` for them.
- The look like Gboard: key height follows the screen height, Enter 1.5 keys wide, bigger
  letters, rounded keys and the new symbol pages.
- Single options of the managed configuration change the stored configuration with what the EMM
  sends. Some EMMs send **every** key with its schema default (for example `suggestions` on); such
  a key then counts like an explicit value. Only `top_bar` has the default `unchanged`. Leave the
  single options unset, or use `config_json`.

**Migration for administrators:**

1. Nothing to do to keep the 2.1 behaviour: old configurations, files and broadcasts keep working
   as before.
2. To get the new defaults, add `"configVersion": 3` to the configuration. Check the table above
   first: everything your configuration leaves out switches to the right-hand column – for
   example the toolbar, suggestions, autocorrection, dictation and no Tab key left of the comma.
   Set the options you want to keep explicitly, e.g. `"keys": { "alpha.p1": "{TAB}" }` or
   `"typing": { "autocorrect": false }`.
3. Or configure one device in the settings and export it: the export always has
   `configVersion: 3` and every value.

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
| `dynamicColor` | `true` / `false` | `true` | Android 12+: system colours from the wallpaper (Material You) like Gboard, as long as `accentColor` is not set; not for `contrast` |
| `emojiKey` | `true` / `false` | `true` | 😊 between comma and space bar in normal text fields (not in passwords, e-mail and web addresses, numbers); opens the emoji panel with categories and skin tones, **ABC** returns. Recently used emoji are kept in memory only; the picker remembers the chosen skin tone per emoji on the device |
| `oneHanded` | `off`, `left`, `right` | `off` | One-handed mode like Gboard: the keys about 80 % wide at that side; the free side shows ⇄ (other side) and ⤢ (full width). Also from the toolbar ⊞ |
| `keyBorders` | `true` / `false` | `true` | Letter keys drawn as tiles. `false`: only the letters on the keyboard background, like Gboard with "Key borders" off. Enter and `?123` are pills like in Gboard either way |
| `keyHeight` | 70–160 (percent) | `100` | Row height; 100 % = 54 dp. Use 120–140 for gloves, or let users switch on [glove mode](#quicktoggles--quick-toggles-on-the-device) (at least 130 %) |
| `width` | 50–100 (percent) | `100` | Width of the keys, centered – for tablets and landscape |
| `numberRow` | `true` / `false` | `false` | Row with 1–0 above the letters |
| `functionRow` | `true` / `false` | `false` | Row with F1–F12 above the letters (and above the number row) |
| `keyHints` | `true` / `false` | `true` | Small digits on the top letter row – long press types them (only without number row) |
| `symbolHints` | `true` / `false` | `false` | Small symbols on the second and third letter row like Gboard ("Long press for symbols"): a @, s #, d € (or $ / £ by keyboard language), f _, g &, h -, j +, k (, l ), y *, x ", c ', v :, b ;, n !, m ?. A long press types the symbol first |
| `modifierRow` | `true` / `false` | `false` | Row with **Ctrl, Win, Alt**, Esc, Tab and Del above the letters (above the number and function rows) – for remote desktop and PC shortcuts, see [Ctrl, Win and Alt](#ctrl-win-and-alt) |
| `functionModifiers` | `true` / `false` | `false` | Extra row on the Fn tab with Ctrl, Win, Alt, Menu (context menu key), Print (Print Screen) and Pause (Alt+F4, Ctrl+Home …). All pages then get the height of five rows |

<p align="center"><img src="images/themes.png" alt="Light, dark and high contrast" width="100%"></p>

## `layout`

| Option | Values | Default | Effect |
|---|---|---|---|
| `alphabet` | `auto`, `qwerty`, `qwertz`, `azerty`, `nordic`, `spanish` | `auto` | Letter layout. `auto` follows the selected keyboard language (Android keyboard languages, switched with 🌐; before one is known: the first system language) (German, Czech, Swiss … → `qwertz`, French and Belgian → `azerty`, Swedish, Danish, Norwegian, Finnish → `nordic`, Spanish and Catalan → `spanish`, everything else `qwerty`) and changes with it. `qwertz` has ä ö ü ß first on long press (own keys with `umlautKeys`), `nordic` adds å ö ä, `spanish` ñ. Accents and special letters (ß, é, ç …) on long press |
| `topBar` | `toolbar`, `tabs` | `toolbar` | Above the keys while no word is typed. `toolbar` like Gboard: ⊞ (tiles for function keys, scan, number pad, clipboard, [snippets](#snippets--snippets), text editing ([`typing.textEditing`](#typing)), one-handed mode, languages, settings), 📋 clipboard, ⚙ settings, `Fn`, scan (with a scanner), custom layouts, 🎤 and hide; a second tap on `Fn`/scan goes back to the letters. `tabs`: ABC · 123 · #+= · Fn · Scan as up to 2.1. Configurations without [`configVersion`](#configversion--defaults-of-21-or-todays) `3` – also the one a device stored with 2.1 – get `tabs` when they leave it out; new installations and `configVersion: 3` get `toolbar`. Only with `navigation: "tabs"` |
| `symbolsKey` | `true` / `false` | `true` | `?123` key at the left of the bottom row like Gboard (`ABC` on the symbols page), also with `navigation: "tabs"`; with `"keys"` it is always there |
| `umlautKeys` | `true` / `false` | `false` | QWERTZ with ü, ö and ä as own keys (11 keys per row). Off: like Gboard, ä, ö, ü and ß come first on long press of a, o, u and s |
| `navigation` | `tabs`, `keys` | `tabs` | `tabs`: tab bar above the keys (ABC, 123, #+=, Fn, scan, custom layouts, hide button). `keys`: no tab bar, `?123` / `ABC` keys like a phone keyboard; on the numeric pad the last side key becomes `ABC`. Other pages are then reachable with [`{SHOW:…}`](#macros) on a programmable key |
| `numericOrder` | `phone`, `calculator` | `phone` | `phone`: 1-2-3 at the top. `calculator`: 7-8-9 at the top. Dial pads (phone number fields) always use phone order |

## `tabs`

| Option | Values | Default | Effect |
|---|---|---|---|
| `alpha`, `numeric`, `symbols`, `function`, `scan` | `true` / `false` | `true` | Show the tab. The scan tab only appears when a scanner is available ([`scanner.trigger`](#scanner)). If every tab is off, ABC stays |
| `preferred` | `auto`, `alpha`, `numeric`, `symbols`, `function`, `scan` or the id of a [custom layout](#custom-layouts) | `auto` | Page that opens in text fields. `auto` = letters. Also per app: [app profiles](#appprofiles--app-profiles) |

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
| `alpha.p1` | Letters, bottom row, left of the comma | empty = no key (like Gboard); `{TAB}` up to 2.1 |
| `numeric.p1` … `numeric.p4` | Numeric pad, left column, top to bottom | `-` `/` `:` `+` |

The value is a [macro](#macros): text (`-`), keys (`{TAB}`), both (`OK{ENTER}`) or a command
(`{SCAN}`). The key shows the text, the key name (`F13`) or an icon (Tab, Scan, Enter …).
Phone, date and time fields show fixed keys instead of the numeric ones.

## `typing`

| Option | Values | Default | Effect |
|---|---|---|---|
| `autoCapitalize` | `true` / `false` | `true` | Capital letters where the field asks for them: sentence start, every word, or all capitals (item numbers) |
| `doubleSpacePeriod` | `true` / `false` | `true` | Two spaces after a word type `. ` |
| `longPressDelay` | 200–1500 (ms) | `300` (like Gboard; `400` up to 2.1) | Time until a long press shows the alternatives; 500–700 avoids accidental accents with gloves |
| `suggestions` | `true` / `false` | `true` | Three word suggestions above the keys while a word is typed, like Gboard: from a built-in dictionary of the keyboard language, best one in the middle; tap one to replace the word and add a space. In normal text fields and search fields (there without autocorrect) – never in passwords, e-mail and web addresses, numbers, dates or fields that set `textNoSuggestions` (barcode fields usually do). Nothing is learnt or stored |
| `autocorrect` | `true` / `false` | `true` (like Gboard) | Space replaces an obvious typo (one wrong, missing, extra or swapped letter) by the best suggestion; Backspace right after it brings the typed word back. Only in normal text fields with suggestions – never in fields that switch suggestions off (barcode and article number fields usually do); for pure data entry switch it off |
| `voice` | `auto`, `offline`, `off` | `auto` | Dictation inside the keyboard, like Gboard: the 🎤 in the tab bar (and at the right of the suggestions) replaces the keys with a listening panel; partial text appears in the field while speaking, the keyboard keeps listening until 🎤 is tapped again, ⌨ is pressed or 30 s pass without speech. Uses Android's speech recognition: `auto` = on-device recognition if the device has it (Android 12+, e.g. Pixel), otherwise the device's speech service, which may work online; `offline` = on-device recognition only (later also our own engine, issue #24); `off` = no microphone. Needs the microphone permission (asked on first use). Not in password fields. The old value `system` is read as `auto` |
| `nextWord` | `true` / `false` | `true` | After a space, three likely next words (`vielen ` → Dank, Grüße; `thank ` → you). From a small fixed list per language (German and English so far, `tools/dictionaries/next/`); the AOSP word lists have no word pairs and nothing is learnt from typing |
| `clipboardHistory` | `true` / `false` | `false` | Clipboard history for the toolbar's 📋 panel: text copied while the keyboard is shown and what is on the clipboard when the panel opens, newest first, at most 20 entries, unpinned ones for 1 hour, **only in memory** (gone when the keyboard restarts). Never from password fields or clips marked sensitive. Off: the panel offers only what is copied right now |
| `deleteGesture` | `true` / `false` | `true` | Swipe left from Backspace marks whole words (one more per finger width), lifting the finger deletes them – like Gboard |
| `textEditing` | `true` / `false` | `true` | Tile *Text editing* in the ⊞ menu: a panel in place of the keys with ← ↑ → ↓, Home, End, Select (the arrows then extend the selection, like holding Shift), Select all, Cut, Copy, Paste, Del and ⌫; ABC goes back to the page shown before. In single-line fields ↑/↓ act as Home/End, so the focus stays in the field. Cut and Copy only with a selection and never in password fields. [`{EDIT}`](#macros) opens it from any key, also with `topBar: tabs` (no ⊞ there) and when this option is off. Nothing is stored |
| `personalDictionary` | `true` / `false` | `true` | Words of the keyboard's own **personal dictionary** in the suggestions, ranked high (like Gboard). Users keep it in *Settings → Typing → Personal dictionary*: add, edit and delete words (word or phrase up to 64 characters, optional shortcut up to 16 characters, language or all languages) or **import** a Gboard export (the ZIP from Gboard's *Dictionary → Personal dictionary → Export*, with `dictionary.txt`) or a plain `dictionary.txt` (lines `shortcut<TAB>word<TAB>locale` as Gboard writes them, empty shortcut and locale allowed; a `# Gboard Dictionary format:` line with other column names wins; a line with a single value is a word). An export of an empty Gboard dictionary contains only the header: the import then says so. Gboard exports only its personal dictionary, not suggestions from the Google account such as the own e-mail address. At most 5000 words and 1 MB per file; duplicates (same word and language) are skipped, the import shows how many words were added, already there and skipped. A shortcut (e.g. `mfg`) offers its word in the middle of the strip like a [snippet](#snippets--snippets) shortcut. Only what the user adds or imports, never typed text; stored in `files/personal-dictionary.json`, not in the configuration, export or backup. Off: the words stay stored but are not offered |
| `systemDictionary` | `true` / `false` | `true` | Words and shortcuts of **Android's user dictionary** (`UserDictionary`, where Gboard and *Settings → Languages → Personal dictionary* keep words) in the suggestions; shortcuts as above. Words of the keyboard language and those for all languages. Read off the main thread when a field starts and again only when the dictionary changes, never per key; nothing is copied. Android lets only the current keyboard read it; where the provider is missing, there are simply no words |
| `autofillSuggestions` | `true` / `false` | `true` | Android 11+: chips of the **autofill service** (Google, a password manager) for e-mail addresses, addresses, passwords and one-time codes in place of the tab bar or toolbar, scrolling sideways, styled in the keyboard's colours; they step aside while a word is typed. The autofill service draws them – the keyboard never sees their content. Which service and what it offers is set in Android (*Settings → Passwords & accounts → Autofill service*) |

Always on: rollover typing with several fingers, key repeat on Backspace, Del and the arrow
keys, slide on the space bar to move the cursor, long press on the space bar to switch
keyboards, double tap on Shift for caps lock, long press on the hide button for the settings.

## `feedback`

| Option | Values | Default | Effect |
|---|---|---|---|
| `keyPopup` | `true` / `false` | `true` | Preview balloon above the pressed key, like Gboard (about 1.6 × the key height, shown on touch-down, gone on release); never in password fields (visible ones included) |
| `sound` | `true` / `false` | `false` | Click sound (independent of the system's touch sounds) |
| `soundVolume` | 0–100 | not set = default volume | Click volume |
| `vibrate` | `true` / `false` | `true` | Short vibration on every key |
| `vibrationMs` | 5–100 (ms) | not set = the system's keyboard haptic | Vibration length; 30–40 can be felt through gloves. Not set: the device's tuned keyboard tick, like Gboard |

## `snippets` – snippets

Ready-made texts (German *Textbausteine*): the administrator's, and the users' own. Tapping one
types its text, which is a [macro](#macros) – so `{TAB}` moves to the next field and `{ENTER}`
runs the field's action (next field, search, send …) or starts a new line in multi-line fields.

```json
{
  "snippets": {
    "button": true,
    "items": [
      { "id": "damaged", "label": "Ware beschädigt", "text": "Ware bei Anlieferung beschädigt, Foto folgt.", "shortcut": "wb" },
      { "id": "regards", "label": "Mit freundlichen Grüßen", "text": "Mit freundlichen Grüßen{ENTER}Lager Süd", "shortcut": "mfg" },
      { "id": "bin", "label": "Lagerplatz A-01", "text": "A-01-03{TAB}" }
    ]
  }
}
```

The whole file: [`examples/snippets.json`](../examples/snippets.json).

| Option | Values | Default | Effect |
|---|---|---|---|
| `enabled` | `true` / `false` | `true` | Off: no ⊞ tile, no bar button, no shortcuts; the panel never opens |
| `shortcuts` | `true` / `false` | `true` | A typed shortcut offers its snippet in the suggestion strip |
| `button` | `true` / `false` | `false` | Own button in the bar: an icon in the toolbar (after `Fn` and scan), or the **last tab** with `topBar: tabs`. With `navigation: keys` there is no bar and so no button. Never over password fields. With `topBar: tabs` there is no ⊞, so the panel (and with it snippets without a shortcut and the ✎ entry) is only reachable with `button: true` – set it for fleets that keep the tabs |
| `allowUser` | `true` / `false` | `true` | Users may keep their own snippets. Off: the keyboard ignores them (the file on the device stays) and the ✎ button is hidden. Own snippets survive MDM pushes, imports and *Reset to defaults*; on devices shared between shifts set `false` from the start – snippets already entered are removed only in *My snippets* or with Android's *Clear storage* |
| `items` | array, at most **100** | `[]` | The administrator's snippets, shown first |

Each snippet:

| Option | Values | Default | Effect |
|---|---|---|---|
| `id` | `a`–`z`, `0`–`9`, `-`, `_`, up to 24 characters, unique | `s1`, `s2` … by position | Name of the snippet |
| `label` | text, 1–40 characters (trimmed) | required | Shown in the panel and in the suggestion strip |
| `text` | [macro](#macros), 1–2000 characters, **not** trimmed | required | What is typed. Every macro works, also `{SCAN}`, `{SHOW:…}` and key combinations |
| `shortcut` | 2–16 letters (trimmed) | none | Typed as a word, it offers the snippet |

Where snippets show up:

- **⊞ → Snippets** (toolbar) or the **bar button** (`button: true`, in both bar kinds): a panel in
  place of the keys, in their height. Tapping a card types the text and closes the panel; **ABC**
  goes back; ✎ opens the app's snippet screen (only with `allowUser`). The cards are at least
  56 dp high, for gloves. ⊞ exists only with `topBar: toolbar`: with `topBar: tabs` (kept by
  configurations stored by 2.1 and older) the panel opens only from the bar button, so set
  `button: true` there.
- **Shortcut:** while the word being typed equals a shortcut (whole word, **not** case-sensitive:
  `Mfg` after auto-capitals matches `mfg`), the snippet takes the middle of the suggestion strip,
  on an accent-coloured pill. Tapping it deletes the typed shortcut and types the text (no space
  after it). A shortcut is never replaced by itself, and a word with a shortcut on offer is not
  autocorrected (`mfg` + space stays `mfg`). Shortcuts work in every text field that is no password
  field – also in fields without suggestions (barcode and article number fields), so use
  **non-words** such as `mfg` or `wb` that never come up as a code. Only letters count, because the
  keyboard tracks only letters as a word. When an own snippet has the same shortcut as one of the
  organisation, the organisation's wins.
- Never in password fields (no tile, no button, no shortcuts).

**Own snippets.** Users add them in the app only: ✎ in the panel or **My snippets** on the start
screen – never from typed text, the clipboard or a selection. At most 50, and only text, `{TAB}`
and `{ENTER}` (no commands, key combinations or `{SHOW:…}`), so an own snippet cannot open the
settings or switch keyboards. They are stored on the device in `files/user-snippets.json`, apart
from the configuration: an MDM push, an import or `INSTALL_CONFIG` does not remove them, and they
are not part of the export, the QR code or **Reset to defaults**. A shortcut that the organisation
or another own snippet already uses is refused in the editor (`IK-0107`).

**The organisation's snippets** come from the configuration: `snippets.items` (JSON, file,
broadcast, import), the managed configuration key `snippets_json`, or **Settings → Snippets →
Organisation snippets**, which edits them on the device. While the EMM sets them (`config_json` or
`snippets_json`), that screen is read-only, since the next push would undo the change. Snippets are
an admin gate: an [app profile](#appprofiles--app-profiles) cannot change them.

Mistakes are rejected with the position, e.g. `snippets.items[2].label is missing`,
`snippets.items[2].shortcut: "lp1" must be 2–16 letters` or
`snippets.items: shortcut "mfg" is used twice`. Many long snippets make the configuration large
(100 × 2000 characters are about 200 KB): such a list no longer fits into a QR code, and some
EMMs limit the length of a managed configuration string – use the configuration file or
`/enterprise/usr` for big lists.

## `quickToggles` – quick toggles on the device

Users can switch four things on their device without changing the configuration: as tiles at
the end of the toolbar's ⊞ menu (lit while on), with the macro [`{TOGGLE:name}`](#macros) on any
key (it shows the tile's icon unless the key has a label), and under **Settings → Quick toggles**. The settings are behind the
[admin password](#admin-password) when one is set, so on protected devices users have only ⊞ and
`{TOGGLE:…}` keys.

| Option | Values | Default | Effect |
|---|---|---|---|
| `allowed` | list of `glove`, `contrast`, `scanner`, `sound` | `["glove", "contrast", "sound"]` | Toggles users may switch. Not in the list: hidden in ⊞, locked in the settings, `{TOGGLE:…}` does nothing, and an override made on the device is ignored. `[]` = none. `scanner` is opt-in, because switching the soft scanner off is a support call in most warehouses |

| Toggle | Without an override (follows the configuration) | On | Off |
|---|---|---|---|
| `glove` | off | Keys at least 130 % (`keyHeight`), long press at least 600 ms, no key preview, vibration on with at least 40 ms – it only raises values, never lowers them | the configuration |
| `contrast` | on if `appearance.theme` is `contrast` | `theme: contrast` without `accentColor`, so the yellow highlights are guaranteed | `theme: system` if the configuration says `contrast`, otherwise the configuration |
| `scanner` | on if a scanner is available (scan engine or camera); not shown without one | the configuration | No scan button, scan tab, scan key, Scan tile or camera in the keyboard (`trigger: none`, `scanKey: false`). **Still active:** the device's own scan trigger with DataWedge keystroke output, barcode broadcasts (`scanner.input`) and NFC (`scanner.nfc`) – the keyboard has no control over the trigger, and the other two are switched by the administrator |
| `sound` | `feedback.sound` | click sound on (`soundVolume` as configured) | click sound off |

When the configuration itself selects `theme: contrast` together with an `accentColor`, the toggle
is on without an override and the keyboard keeps the accent colour; a tap switches contrast off.
The yellow highlights are only forced when a user switches contrast on over a configuration
without the contrast theme.

How they layer:

- The toggles are stored on the device only (`shared_prefs/quick_toggles.xml`, on/off per
  toggle), never in the configuration. An MDM push, an import or `INSTALL_CONFIG` does not remove
  them, and they never change a configured value: export and QR code contain only the
  configuration.
- A tap stores the opposite of what is shown; a toggle switched back to what the configuration
  says is removed again, so it then follows the configuration once more.
- A toggle that is no longer allowed is ignored but **kept**: when the administrator allows it
  again, the user's choice applies again.
- Reset: **Settings → Quick toggles → Reset quick toggles**, **Reset to defaults**, or *Clear
  storage* of the app.
- The settings rows for theme, sound, key height … keep showing the configuration; the preview at
  the top shows what the keyboard shows.
- ⊞ exists only with the toolbar (`layout.topBar: toolbar` and `navigation: tabs`). Configurations
  stored by 2.1 or older keep the tabs and therefore have no ⊞. There, put `{TOGGLE:glove}` … on a
  [programmable key](#keys--programmable-keys) or a [custom layout](#custom-layouts), for example
  `"keys": { "alpha.p1": "{TOGGLE:glove}" }`. **Settings → Quick toggles** is a route for users
  only when no admin password is set.

## `accessibility` – screen readers and large fonts

Every key, tab, suggestion and dictation button is named for screen readers (TalkBack), with its
state (Shift on, Ctrl locked …). The defaults change nothing for users without a screen reader.
More in [Accessibility](accessibility.md); for low vision and bright sunlight there is also the
[`contrast` theme](#appearance).

| Option | Values | Default | Effect |
|---|---|---|---|
| `liftToType` | `true` / `false` | `false` | With TalkBack on: the keyboard itself types the explored key when the finger is lifted, whatever TalkBack's *Typing preference* says. `false`: TalkBack decides (*Lift to type* or *Double-tap to type*), like with Gboard. Only for TalkBack versions that do not type keyboard keys on lifting by themselves |
| `speakPasswordKeys` | `headset`, `always`, `never` | `headset` | How TalkBack speaks the character keys of password fields. `headset`: only while wired, USB or Bluetooth headphones or a hearing aid are connected, otherwise just "Key" (like Android and Gboard); `always`; `never` (counters and wards with bystanders). Backspace, Enter and Shift keep their names |
| `followFontSize` | `true` / `false` | `false` | Key rows, the tab bar and the suggestion strip grow with the system font size, up to 130 %, but the keyboard takes at most 60 % of the screen height (glove mode included) and never gets smaller than `keyHeight`. Off by default, so that stored and fleet configurations keep their height. The text inside the keys follows the font size anyway, as far as it fits |

Invalid values are rejected with the path, e.g.
`accessibility.speakPasswordKeys: unknown value "loud" (headset, always, never)`.

### Using the keyboard with TalkBack

- **Explore** by moving a finger over the keys; TalkBack says each key ("q", "Shift", "Delete",
  "Space, English", "Next").
- **Type** as TalkBack's *Typing preference* says: by lifting the finger on the key, or by a double
  tap anywhere (`liftToType` makes the keyboard type on lifting by itself).
- **Long press**: double-tap and hold. Holding a letter opens its alternatives (lift on the one you
  want); holding Backspace repeats it.
- **Alternatives** (ä, é …) are also in TalkBack's actions menu, as are *Delete word* on Backspace,
  *Lock* on Shift, Ctrl, Alt and Win, and *Switch keyboard* on the space bar.
- **Pages**: switching to symbols, numbers or function keys is announced; ⊞, clipboard, snippets,
  emoji, dictation and the camera are announced by their title when they open. An
  autocorrection is announced ("Corrected to …").
- **Scan tab**: a double tap scans once (or opens the camera); double-tap and hold scans for as
  long as the finger stays down. "Scanning …" is announced.

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

### `scanner.camera` – scanning with the camera

Only for the camera (`trigger: "camera"`, or `auto` on a device without scan engine). Aiming, barcode
types and decoders of a scan engine are set in DataWedge or the Honeywell settings, not here.

| Option | Values | Default | Effect |
|---|---|---|---|
| `symbologies` | list of [barcode types](#barcode-types) | `[]` | Codes the camera reads; empty = all |
| `aim` | `frame`, `center`, `anywhere` | `frame` | `frame`: only codes whose centre is inside the aiming frame. `center`: only the code under the crosshair in the middle – the pick list for labels with many codes. `anywhere`: the whole picture, the code nearest the middle wins |
| `continuous` | `true` / `false` | `false` | The camera stays open and types every new code (stock taking); the line under the frame counts them. Close with ✕ |
| `duplicateDelayMs` | 0–10000 | `1500` | Continuous mode: the same code counts again only after this time without seeing it |
| `confirmReads` | 1–3 | `1` | The same code has to be read in this many frames in a row – against misreads of damaged 1D codes |
| `minLength`, `maxLength` | 0–9999 | `0` | Accepted text length, `0` = no limit. Other codes are ignored and the camera keeps looking |
| `zoom` | 1.0–4.0 | `1.0` | Zoom when the camera opens. The button next to the torch switches 1× → 2× → 3× |
| `torch` | `true` / `false` | `false` | Torch on when the camera opens |
| `inverted` | `true` / `false` | `true` | Also light codes on a dark background |
| `beep`, `vibrate` | `true` / `false` | `true` | Beep and short vibration on every code |
| `timeoutSeconds` | 0–600 | `60` | The camera closes after this time without a code, `0` = never. In continuous mode the time starts again after each code |

### `scanner.data` – scan data formatting

Changes the barcode text before the keyboard types it, like the data formatting of scanner apps.
It applies to camera scans and to barcodes from `scanner.input` broadcasts. Barcodes that DataWedge
types itself (keystroke output) never pass through the keyboard – format those in DataWedge.

| Option | Values | Default | Effect |
|---|---|---|---|
| `prefix` | [macro](#macros) | `""` | Typed before the code, e.g. `{TAB}` or `LOC:` |
| `suffix` | [macro](#macros) or `null` | `null` | Typed after the code instead of `input.suffix`, e.g. `{TAB}{TAB}` or `{F5}`; `""` = nothing. `null` keeps `input.suffix` (Enter, Tab or nothing) |
| `trim` | `true` / `false` | `true` | Remove spaces and control characters at both ends (the GS separator stays) |
| `removeSpaces` | `true` / `false` | `false` | Remove all spaces |
| `case` | `none`, `upper`, `lower` | `none` | Upper or lower case |
| `stripLeading`, `stripTrailing` | 0–999 | `0` | Cut this many characters at the start / end, e.g. a check digit |
| `replace` | list of `{"find", "with", "regex"}` | `[]` | Replacements in this order. With `"regex": true`, `find` is a Java regular expression and `with` may use `$1` … |
| `gs1` | `hri`, `raw` | `hri` | Camera: GS1 codes (GS1-128, GS1 DataMatrix, GS1 QR) human readable as `(01)04012345678901(10)AB12`, or raw with the GS separator (U+001D) between variable-length fields |
| `gsReplacement` | [macro](#macros) or `null` | `null` | Typed in place of the GS separator in every code, e.g. `{TAB}`, `"|"` or `""` (remove); `null` = type the GS character itself |
| `rules` | list of rules | `[]` | Different formatting per barcode type or content, see below |
| `unmatched` | `send`, `drop` | `send` | With rules: what happens to a code that no rule matches – type it with the settings above, or ignore it |

The steps run in this order: trimming, cutting, removing spaces, replacements,
case, then prefix and suffix around the text; the GS replacement is typed while the text is typed.

**Rules.** The first rule that matches a code wins; every field it sets replaces the setting above
for that code. A rule matches when all its conditions hold:

| Field | Meaning |
|---|---|
| `name` | Free text, for you |
| `symbologies` | [Barcode types](#barcode-types); empty = all, also when the type is unknown (a broadcast without type) |
| `minLength`, `maxLength` | Length of the (trimmed) text, `0` = no limit |
| `startsWith` | Text the code starts with |
| `pattern` | Java regular expression that has to match the whole (trimmed) text |
| `action` | `send` (default) or `drop` – a dropped camera code is ignored and the camera keeps looking |
| `prefix`, `suffix`, `case`, `stripLeading`, `stripTrailing`, `removeSpaces`, `replace` | Same as above, for the codes of this rule |

The type of a broadcast is taken from DataWedge's `com.symbol.datawedge.label_type` or an AIM
identifier in `aimId` (Honeywell); the camera always knows it.

**Example: GTIN without the check digit, Code 128 as is.**

```json
"data": {
  "rules": [ { "name": "GTIN", "symbologies": ["ean13", "upca"], "stripTrailing": 1 } ]
}
```

**Example: accept only EAN-13**, everything else is ignored (the camera keeps looking):

```json
"camera": { "symbologies": ["ean13"] },
"data": { "rules": [ { "symbologies": ["ean13"] } ], "unmatched": "drop" }
```

**Example: GS1 fields separated by Tab.** Read GS1 codes raw and press Tab in place of every GS
separator, so for example the GTIN (`01…`) and the batch (`10…`) land in two form fields:

```json
"data": { "gs1": "raw", "gsReplacement": "{TAB}", "suffix": "{ENTER}" }
```

### `scanner.ocr` – text recognition (OCR)

Where the keyboard scans with the camera, its picture has a switch **Barcode | Text**. In text mode
the keyboard reads printed text instead of barcodes – serial numbers, batch numbers, best-before
dates, meter readings. Recognition is Google ML Kit (Latin script) and runs on the device; the
keyboard has no internet permission, so nothing can leave it.

| Field | Values | Default | Meaning |
|---|---|---|---|
| `enabled` | `true` / `false` | `true` | Offer the switch Barcode \| Text on the camera picture |
| `mode` | `pick`, `pattern` | `pick` | `pick`: the lines recognised inside the frame get boxes, tap one to type it. `pattern`: the first line inside the frame that fully matches `pattern` is typed automatically |
| `pattern` | Java regular expression or `null` | `null` | Required for `pattern`. The whole (cleaned) line has to match |
| `startInOcr` | `true` / `false` | `false` | The camera opens in text mode |
| `confirmReads` | 1–5 | `2` | `pattern` mode: the same text in this many pictures in a row before it counts (OCR mixes up single characters more often than a barcode decoder) |
| `minLength`, `maxLength` | 0–999 | `0` | Accepted line length, 0 = no limit |
| `charset` | `any`, `digits`, `alnum`, `upper` | `any` | Cleanup of each line before length and pattern: `digits` keeps 0–9 and `, . -`; `alnum` letters and digits; `upper` letters and digits in upper case |

The text then goes through [`scanner.data`](#scannerdata--scan-data-formatting) like a barcode, with
the type `ocr` – so rules can treat it differently (`"symbologies": ["ocr"]`). Continuous mode,
duplicate delay, beep and vibration of `scanner.camera` apply as well.

**Example: serial numbers like `AB123456` automatically.**

```json
"ocr": { "mode": "pattern", "pattern": "[A-Z]{2}\\d{6}", "charset": "upper" }
```

**Example: best-before date `31.12.2027`.**

```json
"ocr": { "mode": "pattern", "pattern": "\\d{2}\\.\\d{2}\\.\\d{4}", "startInOcr": true }
```

OCR depends on light and print quality more than barcodes do; see [Limitations](limitations.md).

### `scanner.nfc` – NFC tags

Hold an NFC tag (badge, container label, storage location) to the device while the keyboard is
open: its UID or the text stored on it is typed into the field.

| Field | Values | Default | Meaning |
|---|---|---|---|
| `enabled` | `true` / `false` | `true` | Type NFC tags (when the device has NFC and it is switched on) |
| `read` | `uid`, `ndef`, `ndefOrUid` | `ndefOrUid` | `uid`: the serial number of the chip. `ndef`: the text or URI stored on the tag (NDEF text, URI and `text/*` records). `ndefOrUid`: the stored text, the UID if there is none |
| `uidFormat` | `hex`, `decimal` | `hex` | UID as upper-case hex bytes or as an unsigned decimal number |
| `uidReversed` | `true` / `false` | `false` | Reverse the byte order – many badge readers show the UID reversed |
| `uidSeparator` | up to 4 characters | `""` | Between hex bytes, e.g. `":"` → `04:A2:3B:FF` |
| `ndefRecord` | `first`, `allLines` | `first` | The first text/URI record, or all of them one per line |
| `beep`, `vibrate` | `true` / `false` | `true` | Feedback when a tag was typed |

The value goes through [`scanner.data`](#scannerdata--scan-data-formatting) with the type `nfc`.

**Example: badge number as reversed decimal UID** (as printed by many access systems):

```json
"nfc": { "read": "uid", "uidFormat": "decimal", "uidReversed": true }
```

How it works: an input method cannot read tags itself (Android's reader mode needs an app screen in
front). Android hands the tag to a small invisible screen of the keyboard, which closes at once and
passes the value to the keyboard. When no field has the keyboard, the tag is ignored with a short
message. If several apps on the device handle NFC tags, Android may ask which one to use; choose
Industrial Keyboard with *Always* (or set it as default through the EMM). Apps that read tags
themselves while they are in front (reader mode) get the tag first.

### Barcode types

`aztec`, `codabar`, `code39`, `code93`, `code128` (also GS1-128), `databar`, `databarexpanded`,
`databarlimited`, `datamatrix`, `ean8`, `ean13`, `itf`, `maxicode`, `pdf417`, `micropdf417`, `qr`,
`microqr`, `rmqr`, `upca`, `upce`.

For [rules](#scannerdata--scan-data-formatting) there are also `ocr` (text from the camera) and
`nfc` (NFC tags); they are not camera barcode types and are ignored in `scanner.camera.symbologies`.

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

The [snippets](#snippets--snippets) button in the tab bar is no layout and takes no tab id: it
cannot be used in `tabs.preferred`, `{SHOW:…}` or `SHOW_TAB`.

## `appProfiles` – app profiles

Rules that change a few options for one app, a kind of field or a field with a certain hint –
for example the number pad with 7-8-9 for quantity fields of the warehouse app, or the function
keys and no suggestions in the terminal emulation. Only in the JSON configuration (there is no
editor and no single managed configuration option); **Settings → Configuration → App profiles**
lists them.

```json
{
  "customLayouts": [ { "id": "host", "label": "Host", "rows": [ ["{F13}", "{F14}", "{F15}", "{F16}"] ] } ],
  "appProfiles": [
    { "name": "WMS quantity", "apps": ["com.example.wms"], "hints": ["*menge*", "*qty*", "*quantity*"], "page": "numeric", "numericOrder": "calculator" },
    { "name": "WMS", "apps": ["com.example.wms"], "suggestions": false, "autocorrect": false, "emojiKey": false, "alphabet": "qwertz", "scanSuffix": "{TAB}" },
    { "name": "SAP ITSmobile", "apps": ["com.symbol.enterprisebrowser", "com.android.chrome"], "fields": ["text", "none"], "page": "alpha", "topBar": "tabs", "suggestions": false, "autocorrect": false, "emojiKey": false, "scanSuffix": "{ENTER}" },
    { "name": "Terminal emulation", "apps": ["com.wavelink.*", "com.staylinked.*"], "page": "function", "topBar": "tabs", "alphabet": "qwerty", "suggestions": false, "autocorrect": false, "emojiKey": false, "scanSuffix": "{ENTER}" }
  ]
}
```

The whole file: [`examples/app-profiles.json`](../examples/app-profiles.json). Put the specific
rules (with `hints`) before the general rule for the same app. ITSmobile gets `topBar: tabs`, so
`Fn` (F1–F12 = the ITSmobile buttons) is one tap away. The terminal package names are examples –
check them on your devices.

**Conditions** – at least one per profile:

| Option | Values | Default | Matches |
|---|---|---|---|
| `apps` | up to 20 package names or patterns (letters, digits, `_`, `.`, `*`, `?`) | `[]` = any app | The package of the app with the focused field, as a whole and case-sensitive. `*` = any characters, dots included; `?` = one character. `com.sap.*` matches `com.sap.mobile`, not `com.sapx`; `de.ictbaden.industrialkeyboard*` matches the release and the `.debug` build |
| `fields` | up to 20 of `text`, `number`, `phone`, `datetime`, `email`, `url`, `none` | `[]` = any field | The field type (Android input type). `none` = fields that want raw key events (`TYPE_NULL`, terminal emulators). Password fields (text and PIN) have **no** type: a rule with `fields` never matches them |
| `hints` | up to 20 patterns, 1–100 characters each | `[]` = no condition | The hint of the field (placeholder or floating label), as a whole, **not** case-sensitive, with `*` and `?` as above. `*menge*` matches "Menge (Stk)". A field without a hint does not match |

**Overrides** – at least one per profile; what a profile leaves out stays as configured:

| Option | Values | Replaces |
|---|---|---|
| `name` | text, up to 40 characters | – (label for the settings and logcat) |
| `page` | `auto`, `alpha`, `numeric`, `symbols`, `function`, `scan` or a [custom layout](#custom-layouts) id | [`tabs.preferred`](#tabs) |
| `alphabet` | as [`layout.alphabet`](#layout) | `layout.alphabet` |
| `numericOrder` | `phone`, `calculator` | `layout.numericOrder` |
| `topBar` | `toolbar`, `tabs` | `layout.topBar` |
| `emojiKey` | `true` / `false` | `appearance.emojiKey` |
| `suggestions` | `true` / `false` | `typing.suggestions` |
| `autocorrect` | `true` / `false` | `typing.autocorrect` |
| `scanSuffix` | [macro](#macros), `""` = nothing | [`scanner.data.suffix`](#scannerdata--scan-data-formatting) (and so also the old `scanner.input.suffix`); a matching `scanner.data.rules[].suffix` still wins |

How profiles are matched:

- When a field gets the focus (and when the configuration changes), the keyboard checks the
  profiles from top to bottom. **The first profile that matches applies**; profiles are not
  merged.
- All conditions of a profile must match (AND); inside one list any entry may match (OR).
- The page: a `SHOW_TAB` request > the field type (number, phone and date fields always open
  their pad) > the profile's `page` > `tabs.preferred`. `page: scan` without a scanner (or with
  the scanner quick toggle off) opens the letters.
- `scanSuffix` applies to the scans the keyboard types itself: camera, NFC, the scan broadcast
  (`scanner.input`, also DataWedge intent output). DataWedge or Honeywell **keystroke** output
  keeps the suffix set in DataWedge or the Honeywell settings.
- `topBar` changes per app whether the toolbar (with ⊞) or the tabs are shown, and so whether
  the ⊞ tiles (text editing, quick toggles) are reachable there. Switching between apps with a
  different `topBar` or `alphabet` can change the keyboard height.
- Booleans in a profile are strict: `true`, `false` or the texts `"true"` and `"false"`. Elsewhere
  in the format a wrong value counts as the default; in a profile a missing value means "keep the
  configured value", so a typo must not silently switch something off.
- Unknown options inside a profile are ignored, so newer files work with older versions. A typo
  such as `"Page"` is caught only when the profile then sets nothing at all
  (`appProfiles[0]: sets nothing (…)`).
- To find the package of an app: `adb shell dumpsys window | grep mCurrentFocus`, or
  `adb logcat -s KeyboardService` – the keyboard logs `App profile 2 "WMS" for com.example.wms`
  or `No app profile for com.example.wms` on the first field of each app and whenever the
  profile changes – also while `appProfiles` is still empty. It never logs the hint.
- Browsers report their own package for every web page; see [Limitations](limitations.md).

Mistakes are rejected like everywhere else, with the position, e.g.
`appProfiles[0].page: unknown tab "fnkeys" (auto, alpha, numeric, symbols, function, scan or a custom layout id)`
or `appProfiles[2].apps[0]: "com sap" is not a package name or pattern (letters, digits, _, ., * and ?)`.
At most 50 profiles.

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
| Commands | `{SCAN}` start/stop the scanner, `{HIDE}` hide the keyboard, `{SETTINGS}` open the settings, `{NEXT-KEYBOARD}` switch to the next keyboard, `{EDIT}` open/close the text editing panel ([`typing.textEditing`](#typing)), `{TOGGLE:glove}` / `{TOGGLE:contrast}` / `{TOGGLE:scanner}` / `{TOGGLE:sound}` switch a [quick toggle](#quicktoggles--quick-toggles-on-the-device) (only if allowed), `{SHOW:tab}` show a tab (`alpha`, `numeric`, `symbols`, `function`, `scan` or a custom layout id) |

`{ENTER}` behaves like the Enter key: it runs the field's action (next field, search, send …)
or starts a new line in multi-line fields. When more text follows it in a multi-line field without
an action (`Mit freundlichen Grüßen{ENTER}Lager Süd`), the line break is typed as text, as the
AOSP keyboard does: sent as a key, it would reach the app after the text that follows. An `{ENTER}`
at the end of a macro, and every `{ENTER}` in other fields, stays the Enter key. Text after other
keys (`{TAB}`, arrows) can still arrive before the key – see [limitations](limitations.md#macros).

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

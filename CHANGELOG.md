# Changelog

## Unreleased

Fixes, all found and verified on a Zebra HC20 (Android 15):

- The keyboard crashed on every key press when a custom layout had a key wider than about
  three quarters of the keyboard (key preview / alternatives larger than the screen).
- The admin password could be skipped by rotating the device while the password dialog was open.
- Double tap on Shift at the start of a field or sentence (automatic capital) now turns on caps
  lock, as documented, instead of a single capital.
- The scan page was 4 dp lower than the other pages, so the tab bar jumped when switching to it.
- Letters in one row now share one text size on narrow keys (W and M were smaller than I).
- Export writes key widths as configured (`1.2` instead of `1.2000000476837158`).

## 1.0.0

First release.

- **Keyboard** for rugged devices: letters (QWERTY, QWERTZ, AZERTY, Nordic, Spanish) with
  accents on long press, symbols, numeric pad, scan tab and function keys in a tab bar – or
  classic `?123` / `ABC` keys without tab bar.
- **Field-aware:** number, PIN, phone, date and time fields open the matching numeric pad;
  e-mail and URL fields get `@`, `/` and `.com`; the Enter key shows the field's action;
  auto-capitalization follows the field.
- **Function keys:** F1–F12, Shift for F13–F24 (sent as Shift + F1…F12), Esc, Ins, Del,
  Home, End, PgUp, PgDn and arrows; optional F1–F12 row above the letters.
- **Made for gloves:** key height 70–160 %, forgiving touch targets, preview bubble, rollover
  typing, key repeat, space bar cursor control, adjustable long-press delay, sound and
  vibration.
- **Scanner:** soft trigger for Zebra DataWedge, Honeywell or any scanner app via intent;
  scan pad (tap or hold), optional scan key, `{SCAN}` macro; barcodes received as broadcast
  are typed with Enter or Tab.
- **Programmable keys and custom layouts** with macros (`{TAB}`, `{F13}`, `OK{ENTER}`,
  `{SHOW:numeric}` …).
- **Themes:** light, dark, high contrast or system, custom accent color, adjustable width.
- **Provisioning:** managed configuration (`config_json` and single keys), configuration
  files (app folder and Zebra `/enterprise/usr`), `INSTALL_CONFIG` broadcast, import and
  export in the settings, admin password (PBKDF2).
- **Intent API:** `SHOW_TAB` switches the keyboard page from other apps.
- **App:** setup in two steps with a test form, settings with live preview.
- No internet permission; the configuration is excluded from backups.
- Tested on a Zebra HC20 with Android 15.

# Änderungen

Alle wichtigen Änderungen an Industrial Keyboard. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionen nach [Semantic Versioning](https://semver.org/lang/de/).

## [Unveröffentlicht]

### Geändert
- **Neuer Paketname `de.ictbaden.industrialkeyboard`** (vorher `io.github.pieewiee.industrialkeyboard`), nach den Richtlinien von ICT-Baden. Android und das MDM sehen eine neue App: alte App deinstallieren, neue verteilen, Konfiguration neu ausrollen. Die Eingabemethode heißt jetzt `de.ictbaden.industrialkeyboard/.ime.KeyboardService`.
- Die Intent-Actions heißen entsprechend `de.ictbaden.industrialkeyboard.action.SCAN_INPUT`, `…SHOW_TAB` und `…INSTALL_CONFIG`. DataWedge-Profile mit Intent-Ausgabe und StageNow-Profile anpassen.
- Debug-Builds heißen `de.ictbaden.industrialkeyboard.debug` und lassen sich neben dem Release installieren.
- Lizenz: MIT, © ICT-Baden.

### Neu
- Info-Bereich in den Einstellungen: Version mit Commit, Lizenz, Drittanbieter-Lizenzen, Datenschutz, Quellcode, Hilfe & Support, Webseite, Impressum.
- Doku-Seite [Data and privacy](docs/data-and-privacy.md).
- CI: Build, Tests und Lint bei jedem Push; mit dem Release-Schlüssel aus den Secrets signierte APK im Release `latest` (main) bzw. `vX.Y.Z` (Tag), mit `SHA256SUMS.txt`.
- `LICENSE`, `THIRD-PARTY-NOTICES.txt`, `SECURITY.md`, `SUPPORT.md`, `CONTRIBUTING.md`, Issue-Formulare, Dependabot.

### Behoben
Alle auf einem Zebra HC20 (Android 15) gefunden und nachgeprüft:
- Die Tastatur stürzte bei jedem Tastendruck ab, wenn ein eigenes Layout eine Taste breiter als etwa drei Viertel der Tastatur hatte.
- Das Admin-Passwort der Einstellungen ließ sich umgehen, indem man das Gerät bei offenem Passwortdialog drehte.
- Doppeltipp auf Shift am Anfang eines Felds oder Satzes (automatischer Großbuchstabe) schaltet jetzt wie beschrieben die Feststelltaste ein.
- Die Scan-Seite war 4 dp niedriger als die anderen Seiten, die Tab-Leiste sprang beim Wechsel.
- Buchstaben einer Reihe haben auf schmalen Tasten jetzt dieselbe Größe (W und M waren kleiner als I).
- Der Export schreibt Tastenbreiten wie eingegeben (`1.2` statt `1.2000000476837158`).

## [1.0.0]

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

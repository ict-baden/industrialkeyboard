# Änderungen

Alle wichtigen Änderungen an Industrial Keyboard. Format nach [Keep a Changelog](https://keepachangelog.com/de/1.1.0/), Versionen nach [Semantic Versioning](https://semver.org/lang/de/).

## [Unveröffentlicht]

### Geändert
- **Standardwerte wie bei Gboard** (gespeicherte Konfigurationen behalten ihre Werte): Autokorrektur an, langes Drücken nach 300 statt 400 ms, keine Tab-Taste unten links (`keys.alpha.p1` leer; wer sie braucht, setzt `{TAB}`), Zahlen-Taste `?123` an, Symbol-Hinweise und Tastenvorschau aus.

### Neu
- **Werkzeugleiste wie Gboard** (`layout.topBar: "toolbar"`, neuer Standard; von 2.1 gespeicherte Konfigurationen behalten die Tabs): ⊞ mit Kacheln für Funktionstasten, Scannen, Ziffernblock, Zwischenablage, Einhandmodus, Sprachen und Einstellungen, dazu 📋, ⚙, `Fn`, Scan, 🎤 und ⌄.
- **Zwischenablage** (📋): einfügen per Tippen; mit `typing.clipboardHistory` (Standard aus) ein Verlauf nur im Arbeitsspeicher, höchstens 20 Einträge, 1 Stunde, anheften per langem Druck, nie aus Passwortfeldern.
- **Vorschläge fürs nächste Wort** nach einem Leerzeichen (`typing.nextWord`), aus einer kleinen festen Liste für Deutsch und Englisch – die AOSP-Wortlisten haben keine Wortpaare, gelernt wird nichts.
- **Einhandmodus** (`appearance.oneHanded`: `left`/`right`), Tasten etwa 80 % breit, mit ⇄ und ⤢ auf der freien Seite.
- Wortvorschläge auch in **Suchfeldern** (ohne Autokorrektur); Alltagswörter wie „Hallo“, Wochentage und Grüße weiter oben; ein getipptes Wort aus dem Wörterbuch bleibt in der Mitte.
- **Text bearbeiten** wie bei Gboard (⊞ → Text bearbeiten, Makro `{EDIT}`, `typing.textEditing`, MDM `text_editing`): Pfeiltasten, Auswählen, Alles auswählen, Pos1/Ende, Ausschneiden, Kopieren, Einfügen, Entf und ⌫ anstelle der Tasten. In einzeiligen Feldern wirken ↑/↓ wie Pos1/Ende, damit der Fokus im Feld bleibt; Ausschneiden und Kopieren nur mit Auswahl und nie in Passwortfeldern. Es wird nichts gespeichert. Das Feld „Notiz“ unter „Ausprobieren“ ist jetzt mehrzeilig.
- **Schnellschalter** in ⊞, in den Einstellungen und per Makro `{TOGGLE:glove}` …: Handschuh-Modus (Tasten mindestens 130 %, langes Drücken 600 ms, keine Tastenvorschau, Vibration mindestens 40 ms), hoher Kontrast, Scanner an/aus, Ton an/aus. Gelten nur auf dem Gerät (`shared_prefs/quick_toggles.xml`), überstehen MDM-Pushes und Importe und landen nie in Export oder QR-Code. Sperrbar mit `quickToggles.allowed` / MDM `quick_toggles`; Standard Handschuh, Kontrast und Ton, der Scanner nur, wenn der Administrator ihn erlaubt. Ein gesperrter Schalter wird ignoriert, bleibt aber gespeichert und gilt wieder, sobald er erlaubt ist. Scanner aus blendet nur Scan-Knopf, Scan-Taste und Kamera aus; Gerätetrigger, Barcode-Broadcasts und NFC laufen weiter. Ohne ⊞ (Tab-Leiste, von 2.1 gespeicherte Konfigurationen) per `{TOGGLE:…}` auf einer Taste, z. B. `"keys": { "alpha.p1": "{TOGGLE:glove}" }`; die Einstellungen sind für Benutzer nur ohne Admin-Passwort ein Weg. Legt die Konfiguration selbst Kontrast mit `accentColor` fest, bleibt die Akzentfarbe.
- **App-Profile** (`appProfiles`): Regeln nach App-Paket (`com.sap.*`), Feldtyp und Hinweistext setzen Startseite, Alphabet, Ziffernfolge, Leiste, Emoji-Taste, Vorschläge, Autokorrektur und Scan-Suffix; die erste passende Regel gilt, Profile werden nicht gemischt. Nur per JSON (`config_json`, Datei, Broadcast, Import), die Einstellungen zeigen sie unter „Konfiguration → App-Profile“ an. Gelesen werden nur Paketname, Feldtyp und Hinweis des Feldes, gespeichert wird nichts; ins Logcat kommen Paket- und Profilname. Gespeicherte Konfigurationen ohne `appProfiles` verhalten sich wie bisher. Vorlage `examples/app-profiles.json`.
- **Textbausteine** (`snippets`): vom Admin verteilt (JSON `snippets.items`, MDM `snippets_json` als JSON-Array, in den Einstellungen unter „Textbausteine → Bausteine der Organisation“) und eigene, im ⊞-Menü, optional als Knopf in der Leiste (`snippets.button`: Symbol in der Werkzeugleiste oder letzter Tab), Kürzel wie „mfg“ als hervorgehobener Vorschlag in der Mitte der Vorschlagsleiste. Tippen auf den Vorschlag ersetzt das Kürzel durch den Text; ein Kürzel wird nie von selbst ersetzt und nicht autokorrigiert. Der Text ist ein Makro (`{TAB}`, `{ENTER}` …). Eigene Bausteine (höchstens 50, nur Text, `{TAB}` und `{ENTER}`) entstehen nur im Baustein-Bildschirm der App (✎ im Panel, „Meine Textbausteine“ auf dem Startbildschirm), nie aus Getipptem, der Zwischenablage oder einer Auswahl; sie liegen getrennt von der Konfiguration in `files/user-snippets.json`, überstehen MDM-Pushes, Importe und „Auf Standard zurücksetzen“ und landen nie in Export oder QR-Code. Ein Baustein wird so getippt, wie er geschrieben ist: ein von Hand getipptes Umschalt und eingerastete Strg/Alt/Win wirken nicht auf sein `{TAB}` und `{ENTER}`. Mit `topBar: tabs` gibt es kein ⊞; dort öffnet nur der Knopf (`snippets.button: true`) das Panel. Nie in Passwortfeldern. Vorlage `examples/snippets.json`. Fehlercodes IK-0107, IK-0108 und IK-0109 (eigener Baustein nicht gespeichert; der Editor bleibt mit den Eingaben offen).
- MDM: `top_bar`, `next_word`, `clipboard_history`, `one_handed`, `quick_toggles`, `snippets_enabled`, `snippets_shortcuts`, `snippets_button`, `snippets_allow_user`, `snippets_json`.
- **Wie Gboard** (gemessen auf einem Pixel 9 Pro):
  - Tastengröße folgt der Bildschirmhöhe (etwa 6,8 % je Reihe, 54–70 dp), Lücken 5,5 dp zwischen Tasten und 12 dp zwischen Reihen; der Regler „Tastenhöhe“ wirkt weiter.
  - Abstand unten: die Tasten bleiben über Androids eigenen Knöpfen ⌄ und 🌐 bei Gestensteuerung (bisher ragten sie hinein).
  - Tastatursprachen (Subtypes) für 40 Sprachen mit lateinischer Schrift: Android schaltet die zur Systemsprache passenden frei, 🌐 wechselt beim Tippen, die Leertaste zeigt die Sprache („Deutsch“), mit `layout.alphabet: "auto"` folgt das Layout der Sprache. Eigene 🌐-Taste neben der Leertaste, wenn mehrere Sprachen aktiv sind und Android keinen Umschaltknopf zeigt. Einstellung „Tastatursprachen“ öffnet die Android-Liste.
  - QWERTZ mit 10/9/7 Tasten, ä, ö, ü, ß zuerst bei langem Druck auf a, o, u, s. Die bisherige Variante mit eigenen Umlaut-Tasten: `layout.umlautKeys: true`.
  - Systemfarben (Material You) ab Android 12, solange keine eigene Akzentfarbe gesetzt ist (`appearance.dynamicColor`, Standard an).
  - Haptik: ohne `feedback.vibrationMs` der Tastatur-Tick des Systems statt eines festen 18-ms-Brummens.
  - Enter 1,5 statt 2 Tasten breit.
  - Zahlen-Taste `?123` unten links wie bei Gboard, auch mit Tab-Leiste (`layout.symbolsKey`, Einstellung „Zahlen-Taste“, MDM `symbols_key`; Standard an wie bei Gboard, gespeicherte Konfigurationen behalten ihren Wert).
  - Buchstaben größer und kräftiger (27 sp, Strichstärke 500).
- **Diktat in der Tastatur wie bei Gboard** ([#23](https://github.com/ict-baden/industrialkeyboard/issues/23)): 🎤 in der Tab-Leiste und rechts neben den Vorschlägen öffnet an Stelle der Tasten eine Diktat-Ansicht (pulsierendes Mikrofon, ⌨ zurück, ⌫). Der erkannte Text erscheint schon beim Sprechen im Feld, mit Leerzeichen und Großschreibung am Satzanfang; die Tastatur hört weiter zu, bis man 🎤 antippt, ⌨ drückt oder 30 s nichts sagt. Androids Spracherkennung: `typing.voice` `auto` (auf dem Gerät, wenn vorhanden, sonst der Sprachdienst, ggf. online), `offline` (nur auf dem Gerät), `off`; `system` gilt als `auto`. Mikrofon-Berechtigung beim ersten Antippen, Fehlercodes `IK-0210`–`IK-0213`. Kein Umschalten mehr auf eine andere Sprach-Tastatur.
- **Weiter wie Gboard:**
  - Symbole bei langem Drücken auf allen Buchstaben (a @, s #, d €/$ …), als Hinweis in der Ecke (`appearance.symbolHints`, Standard an).
  - Enter und `?123` als Pillen, Buchstaben mit 6 dp Rundung wie bei Gboard; „Tastenränder“ (`appearance.keyBorders`) aus zeigt nur die Buchstaben.
  - Tastenvorschau beim Drücken wie bei Gboard: Ballon direkt über der Taste (etwa 1,6 × so hoch), erscheint sofort und verschwindet beim Loslassen; standardmäßig an (`feedback.keyPopup`; gespeicherte Konfigurationen behalten ihren Wert).
  - Langes Drücken auf das Komma bietet zuerst ⚙ Einstellungen und 😊 Emoji.
  - Zeichenseite in Gboards Reihenfolge (1–0 · @ # € _ & - + ( ) / · =\< * " ' : ; ! ?), zweite Seite ähnlich Gboards; Währung nach Tastatursprache.
  - Wischen zum Löschen: von der Rücktaste nach links wischen markiert ganze Wörter, Loslassen löscht sie (`typing.deleteGesture`, Standard an).
- **Wortvorschläge** wie bei Gboard ([#22](https://github.com/ict-baden/industrialkeyboard/issues/22)): drei Vorschläge an Stelle der Tab-Leiste, solange ein Wort getippt wird, aus eingebauten Wortlisten (AOSP-Wörterbücher, 21 Sprachen, je bis 80 000 Wörter), Vervollständigung und ein Tippfehler (Nachbartaste, fehlender, überzähliger oder vertauschter Buchstabe). Nur in normalen Textfeldern, nie in Passwort-, E-Mail-, Adress-, Zahlen- und Datumsfeldern oder bei `textNoSuggestions`. Nichts wird gelernt oder gespeichert. `typing.suggestions` (Standard an), `typing.autocorrect` (Standard aus; Rücktaste macht die Korrektur rückgängig), auch per MDM (`suggestions`, `autocorrect`).
- **Emoji-Taste und Emoji-Auswahl** wie bei Gboard: 😊 zwischen Komma und Leertaste in normalen Textfeldern, Auswahl mit Kategorien und Hauttönen an Stelle der Tasten (AndroidX Emoji Picker 1.7.0, ohne eigene Emoji-Schrift), zuletzt benutzte Emoji nur im Speicher. `appearance.emojiKey` (Standard an), MDM `emoji_key`.
- Feld „Notiz“ unter „Ausprobieren“ in der App, zum Testen von Vorschlägen und Emoji.
- Die APK wird größer (Release etwa 30 → 39 MB) durch die Wörterbücher (7,5 MB) und die Emoji-Auswahl.
- **Strg, Win und Alt für Remotedesktop:** wahlweise als Reihe über den Buchstaben (mit Esc, Tab und Entf, `appearance.modifierRow`) und als zusätzliche Reihe bei den Funktionstasten (`appearance.functionModifiers`), beides auch in den Einstellungen unter „Darstellung“. Die Tasten rasten ein: tippen gilt für die nächste Taste, nochmal tippen drückt sie allein (Win öffnet das Startmenü), halten stellt fest. Sie bleiben beim Wechsel des Tabs an, Alt und dann F4 auf dem Fn-Tab ergibt Alt+F4. Solange eine an ist, gehen Buchstaben, Ziffern und Satzzeichen als Tastendruck raus statt als Text, und Strg/Win/Alt werden wie auf einer PC-Tastatur um die Taste herum gedrückt und losgelassen.
- **Shift auch für Tab, Pfeile und Co.:** ein von Hand getipptes Shift gilt jetzt auch für Tab, Pfeile, Esc, Entf, Pos1/Ende und die anderen Tasten – Shift+Tab springt zurück, Shift+Pfeil markiert, mit Strg zusammen Strg+Shift+Esc. Das automatische Shift am Satzanfang und die Feststelltaste tun das nicht.
- **Menü, Druck und Pause** auf dem Fn-Tab, in der Reihe mit Strg, Win und Alt (`appearance.functionModifiers`).
- **Makros:** `{CTRL}`, `{ALT}`, `{WIN}` (als eigene Taste einrastend), Tastenkombinationen wie `{CTRL+C}`, `{ALT+F4}`, `{WIN+R}`, `{CTRL+SHIFT+ESC}`, `{CTRL+ALT+END}`, dazu `{PRINT}`, `{PAUSE}` und `{MENU}`.
- **Buchstabenlayout nach der Systemsprache** (`layout.alphabet: "auto"`, neuer Standard, [#18](https://github.com/ict-baden/industrialkeyboard/issues/18)): Deutsch, Tschechisch, Schweiz … → QWERTZ, Französisch und Belgien → AZERTY, Skandinavisch → Nordisch, Spanisch und Katalanisch → Español, sonst QWERTY. Wechselt mit der Systemsprache, nicht mit der App-Sprache. In den Einstellungen steht dazu, welches Layout gerade gilt („Automatisch (QWERTZ)“). Gespeicherte Konfigurationen behalten ihr Layout; nur Konfigurationsdateien ohne `alphabet` bekommen jetzt das Layout der Systemsprache statt QWERTY.
- **Scannen mit der Kamera, wenn das Gerät kein Scan-Modul hat** ([#17](https://github.com/ict-baden/industrialkeyboard/issues/17)): `scanner.trigger: "auto"` fragt bei Zebra DataWedge, ob ein eingebauter Imager/Laser oder ein verbundener Bluetooth-/USB-Scanner da ist; ohne Scan-Modul (und auf Geräten ohne Zebra/Honeywell) nimmt die Tastatur die Kamera. Neuer Wert `"camera"` erzwingt sie. Das Kamerabild erscheint an Stelle der Tasten mit Sucherrahmen, Taschenlampe und ✕; der erste erkannte Code wird mit `input.suffix` (Enter, Tab oder nichts) ins Feld getippt, dann schließt die Kamera, spätestens nach einer Minute. Erkennung mit zxing-cpp 3.1.1 (wie Lupe), Bild über CameraX 1.5.2. Die Kamera-Berechtigung fragt Android beim ersten Tippen auf Scannen ab (`IK-0200`, `IK-0201`). **Neu ist damit:** Auf Telefonen und Tablets ohne Scanner erscheint jetzt der Scan-Tab. Die APK wird durch CameraX und die nativen zxing-Bibliotheken größer (etwa 6 → 14 MB, im App-Bundle von Google Play je Gerät weniger).
- **Kamera-Scan einstellbar** (`scanner.camera`, Einstellungen „Scannen mit der Kamera“): Barcode-Typen, Zielen mit Rahmen, Fadenkreuz (Auswahl aus mehreren Codes) oder überall, Dauerscan mit Zähler und Sperre für denselben Code, mehrfaches Lesen gegen Fehllesungen, Mindest-/Höchstlänge, Zoom beim Öffnen und Zoom-Knopf (1×/2×/3×), Taschenlampe beim Öffnen, inverse Codes, Ton, Vibration, Zeitlimit (auch „nie“).
- **Scan-Daten formatieren** (`scanner.data`, Einstellungen „Scan-Daten“) wie die Datenformatierung von Scanner-Apps: Präfix und Suffix als Makro (`{TAB}`, `{F5}` …), Ränder bereinigen, Leerzeichen entfernen, Groß-/Kleinschreibung, Zeichen vorn/hinten abschneiden, Ersetzungen (auch Regex), GS1-Codes lesbar `(01)…` oder roh mit Makro für das GS-Trennzeichen (z. B. `{TAB}`), Regeln nach Barcode-Typ, Länge, Anfang und Muster (senden oder verwerfen). Gilt für Kamera-Scans und für Scan-Broadcasts (Typ aus DataWedge `label_type` oder AIM-ID); was DataWedge selbst tippt, läuft nicht durch die Tastatur. Ersetzungen und Regeln nur per JSON.
- **Konfiguration als QR-Code** exportieren und am nächsten Gerät mit der Kamera importieren (Einstellungen → Konfiguration). Der Code enthält nur die Werte, die vom Standard abweichen, als JSON oder komprimiert als `ICTZ1:` – was kürzer ist (der Datei-Export bleibt vollständig); `ICTZ1:` nehmen jetzt alle Wege an, die JSON annehmen (Datei, App-Ordner, `/enterprise/usr`, `config_json`, `INSTALL_CONFIG`). QR-Code mit qrcodegen 1.8.0 (MIT).
- **Managed Configuration:** `scan_prefix`, `scan_suffix`, `camera_aim`, `camera_continuous`, `camera_symbologies`.
- „Nach dem Scan“ (Enter/Tab/nichts) gilt auch für Kamera-Scans, solange kein eigenes Suffix gesetzt ist.
- Vorlage [`examples/remote-desktop.json`](examples/remote-desktop.json) mit eigenem Tab „RDP“.
- **Texterkennung (OCR) mit der Kamera** ([#19](https://github.com/ict-baden/industrialkeyboard/issues/19), `scanner.ocr`): Im Kamerabild schaltet ein Umschalter zwischen Barcode und Text. Erkannte Zeilen im Rahmen bekommen Kästchen zum Antippen (`mode: "pick"`), oder die erste Zeile, die zu einem Muster passt, wird automatisch übernommen (`mode: "pattern"`, z. B. Seriennummer oder MHD). Zeichenfilter (nur Ziffern, Buchstaben/Ziffern, groß), Länge, Bestätigung über mehrere Bilder. Der Text läuft durch `scanner.data` mit dem Typ `ocr`. Erkennung mit Google ML Kit (Lateinisch, Modell in der App, läuft auf dem Gerät). Die Tastatur hat weiterhin keine Internet-Berechtigung: Die von ML Kit mitgebrachte wird im Manifest entfernt.
- **NFC** ([#20](https://github.com/ict-baden/industrialkeyboard/issues/20), `scanner.nfc`): Tag ans Gerät halten, während die Tastatur offen ist – UID (hex oder dezimal, wahlweise umgedreht, mit Trennzeichen) oder der gespeicherte Text (NDEF-Text, URI) wird ins Feld getippt, Typ `nfc` in `scanner.data`. Weil eine Eingabemethode keinen Reader-Modus hat, nimmt eine kleine unsichtbare Activity den Tag an und gibt ihn an die Tastatur weiter. Einstellungen zeigen, ob das Gerät NFC hat und ob es an ist.
- MDM-Schlüssel `ocr_mode`, `ocr_pattern`, `nfc_enabled`, `nfc_read`.

### Geändert
- **Release-APK nur noch für ARM** (`arm64-v8a`, `armeabi-v7a`): Robuste Android-Geräte ab Android 8 sind ARM. Ohne x86 ist die APK etwa 30 statt 57 MB groß (die OCR-Bibliothek von ML Kit hat rund 11 MB pro Architektur). Debug-Builds behalten x86 für den Emulator.

## [2.1.1] – 2026-10-21

### Sicherheit
- **Konfigurationsdatei im App-Ordner auf verwalteten Geräten:** Sie braucht jetzt wie der `INSTALL_CONFIG`-Broadcast das aktuelle Admin-Passwort (`"password"` in der Datei), sobald eins gesetzt ist. Bisher wurde sie auf EMM-verwalteten Geräten ohne Prüfung angenommen – auch unter Android 8–10, wo jede App mit Speicherberechtigung in den Ordner schreiben kann; so ließen sich Konfiguration und Passwort ohne Passwort ersetzen. `/enterprise/usr` und die Managed Configuration bleiben ohne Passwort. **Handlungsbedarf**, wer auf verwalteten Geräten mit Admin-Passwort Dateien in den App-Ordner legt: `"password"` ergänzen oder auf `/enterprise/usr` bzw. die Managed Configuration umstellen.
- **Sichtbare Passwortfelder (`textVisiblePassword`) gelten jetzt als Passwortfelder:** keine Scans per Broadcast, keine vergrößerte Tastenvorschau. Apps schalten bei „Passwort anzeigen“ auf diesen Typ um. Wer ihn nur gegen Vorschläge nutzt (Barcode-Felder), nimmt `textNoSuggestions` oder die Tastatur-Ausgabe (Keystroke) von DataWedge.
- Eine Konfigurationsdatei im App-Ordner mit Passwort wird auch gelöscht, wenn sie abgelehnt wird (falsches Passwort, ungültiges JSON) – bisher blieb das Passwort dann im Klartext liegen. Nur während der Sperre nach Fehlversuchen bleibt sie für den nächsten Versuch.
- Lässt sich die gespeicherte Konfiguration gar nicht lesen (Lesefehler, leere Datei), bleiben die Einstellungen gesperrt (unbekanntes Passwort, Hinweis `IK-0106`), statt offen zu stehen. Die Datei wird nicht mit Standardwerten überschrieben, sondern bei der nächsten Änderung und beim nächsten Start wieder gelesen; das Admin-Passwort vom EMM (`admin_password` oder `adminPassword` in `/enterprise/usr`) hebt die Sperre auf; diese Quellen werden solange bei jeder Prüfung erneut angewendet. Die Konfiguration wird jetzt vor dem Umbenennen auf den Speicher geschrieben (fsync), damit ein Stromausfall keine leere Datei hinterlässt.

### Doku
- [Deployment](docs/deployment.md): Regeln für die Datei im App-Ordner; ausdrücklicher Hinweis, dass jede App mit 5 falschen Passwörtern Broadcast und App-Ordner immer wieder 5 Minuten sperren kann (DoS) und was dann hilft; Abschnitt zur Signatur auf den Stand gebracht (Releases seit 2.0.0 mit dem Release-Schlüssel signiert).
- [Data and privacy](docs/data-and-privacy.md): alle gespeicherten Dateien, auch die Sicherungskopie einer kaputten Konfiguration und die Zähler der Passwortsperre.
- [Deployment](docs/deployment.md), Intune: die APK nicht als eigene private App in managed Google Play hochladen – das belegt den Paketnamen in ganz Google Play.
- `SUPPORT.md`: Fragen per Issue mit neuer Vorlage „Frage“ (Discussions sind nicht eingeschaltet); die Issue-Vorlagen nutzen die vorhandenen Labels.
- Release-Texte auf Englisch, ohne automatische Liste der Pull Requests.

## [2.1.0] – 2026-10-21

### Neu
- **Oberfläche in fünf Sprachen:** Englisch, Deutsch, Spanisch, Französisch und Chinesisch (vereinfacht), nach Richtlinie 13 von ICT-Baden. Einrichtung, Einstellungen, Dialoge, Scan-Pad und das Schema der Managed Configuration sind übersetzt; die Tastenbeschriftungen bleiben, wie sie sind.
- Sprache wählbar unter **Einstellungen → Aussehen → Sprache**, per Konfiguration (`"language": "de"`) oder als Einzeloption `language` der Managed Configuration. Standard `auto` folgt dem Gerät.
- Fehlermeldungen zeigen einen Code (`IK-0100` …), damit der Support sie in jeder Sprache zuordnen kann: [Error codes](docs/error-codes.md).
- Vorbereitet für Google Play: Die CI baut zusätzlich ein signiertes App Bundle (`.aab`), Store-Texte in fünf Sprachen und Store-Grafiken liegen in `fastlane/`, die Antworten für die Play Console in [docs/play-store.md](docs/play-store.md).

Die Übersetzungen sind maschinell vorübersetzt und müssen vor dem ersten öffentlichen Release von Muttersprachlern gegengelesen werden.

### Sicherheit
- **Konfigurations-Broadcasts (`INSTALL_CONFIG`) werden nur noch mit Admin-Passwort angenommen oder – solange keins gesetzt ist – auf Geräten, die ein EMM verwaltet** (Managed Configuration, Device oder Profile Owner). Bisher konnte auf einem nicht verwalteten Gerät jede App die Tastatur umkonfigurieren. **Handlungsbedarf**, wer nicht verwaltete Geräte per Broadcast konfiguriert (StageNow ohne EMM, adb): vorher ein Admin-Passwort setzen und als `password` mitsenden oder die Konfigurationsdatei nutzen (`/enterprise/usr/industrialkeyboard.json`). Abgelehnte Broadcasts stehen in den Einstellungen unter Konfiguration.

- **Scans per Broadcast (`SCAN_INPUT`) sind ab Werk aus.** Android sagt einem Empfänger nicht, welche App einen Broadcast geschickt hat; bisher konnte jede App in das fokussierte Feld tippen, auch in Passwortfelder. Jetzt gilt: nur wenn `scanner.input.enabled` eingeschaltet ist, nie in Passwortfelder oder Felder ohne Texteditor (`TYPE_NULL`), nur bei sichtbarer Tastatur, höchstens 8 192 Zeichen. Neu `scanner.input.permission` (Berechtigung, die der Absender haben muss) und `scanner.input.allowedPackages` (Android 14+, nur Absender, die sich ausweisen). **Handlungsbedarf**, wer DataWedge mit Intent-Ausgabe an die Tastatur nutzt: `"scanner": { "input": { "enabled": true } }` in die Konfiguration. Beim Update wird der Scan-Eingang einmalig ausgeschaltet – außer die Managed Configuration (`config_json`) oder eine Konfigurationsdatei auf dem Gerät schaltet ihn ausdrücklich ein; sonst bleibt die Konfiguration, wie sie ist. Wer ihn per Broadcast-Konfiguration eingeschaltet hatte, sendet sie neu.
- **Konfigurationsdatei im App-Ordner** (`Android/data/…/files/industrialkeyboard.json`) wird auf nicht verwalteten Geräten nur noch ab Android 11 angenommen und, sobald ein Admin-Passwort gesetzt ist, nur mit dem aktuellen Passwort (`"password"` in der Datei). Enthält die Datei ein Passwort, wird sie nach dem Anwenden gelöscht. `/enterprise/usr` und verwaltete Geräte wie bisher.
- **Sperre nach Fehlversuchen:** Nach 5 falschen Admin-Passwörtern werden Broadcasts und Dateien 5 Minuten lang abgelehnt, ebenso der Passwortdialog der Einstellungen (eigener Zähler, damit eine App den Administrator nicht aussperren kann).
- Fehlermeldungen zu ungültigem JSON enthalten nicht mehr den ganzen Text – bisher stand so ein mitgesendetes Admin-Passwort im Klartext in logcat und unter „Letzter Fehler“.
- Keine vergrößerte Tastenvorschau mehr über Passwortfeldern.
- Passwort-Hashes mit mehr als 1 000 000 Iterationen werden abgelehnt (hätten das Gerät beim Prüfen minutenlang blockiert).

### Behoben
- Ein leeres Passwort (Einstellungsdialog oder Broadcast ohne `password`) bringt die App unter Android nicht mehr zum Absturz.
- **Eine Leertaste im eigenen Layout (`" "`) setzte beim nächsten Start die ganze Konfiguration samt Admin-Passwort auf Standard zurück.** Die Taste wird jetzt richtig gespeichert und geladen. Lässt sich die gespeicherte Konfiguration trotzdem einmal nicht lesen, bleibt das Admin-Passwort erhalten, die Datei wird als `config.json.invalid-…` aufbewahrt und der Fehler steht unter Konfiguration.
- Die Sprache aus Konfiguration oder MDM wird unter Android 13+ auch dann angewandt, wenn noch kein Einstellungsfenster offen war.
- Konfigurationsquellen werden im Hintergrund geprüft, nicht mehr bei jedem Feldwechsel auf dem UI-Thread.
- Französische Store-Texte: Steuerzeichen statt Doppelpunkt entfernt.

### Doku
- Neue Seite [Limitations](docs/limitations.md): getestet an Zebra HC20 (Android 15) mit DataWedge, Honeywell nur nach Herstellerdoku, Übersetzungen maschinell.
- Spanisch, Französisch und Chinesisch sind in der Sprachauswahl und im README als maschinell übersetzt gekennzeichnet.
- Datenschutz-Link in den Einstellungen und im README: https://ict-baden.de/datenschutz/industrialkeyboard/, Impressum https://ict-baden.de/impressum/, Webseite https://ict-baden.de/produkte/industrial-keyboard/.
- Repository heißt jetzt `ict-baden/industrialkeyboard` (alte Adresse leitet weiter).

## [2.0.0] – 2026-10-17

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

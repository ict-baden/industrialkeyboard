# CLAUDE.md – Industrial Keyboard

Industrial Keyboard: Bildschirmtastatur (Android-Eingabemethode) für robuste Geräte von Zebra und Honeywell – große Tasten für Handschuhe, Scan-Trigger, F1–F24 für Terminal-Emulation, Konfiguration per MDM.

Projekt von ICT-Baden. Es gelten die Richtlinien in [`ict-baden/richtlinien`](https://github.com/ict-baden/richtlinien). Stand und Zuständigkeit stehen in `ict-baden/ict-baden-projekte` (`projekte.json`, `in-arbeit.md`).

## Bauen und testen

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable de.ictbaden.industrialkeyboard.debug/de.ictbaden.industrialkeyboard.ime.KeyboardService
adb shell ime set de.ictbaden.industrialkeyboard.debug/de.ictbaden.industrialkeyboard.ime.KeyboardService
```

JDK 17–21, Android SDK mit Compile-SDK 37. Unter Windows `sdk.dir` in `local.properties` mit `C\:/…` schreiben, sonst schlägt Lint fehl.

## Aufbau

- `app/src/main/java/de/ictbaden/industrialkeyboard/`
  - `ime/` – Eingabemethode (`KeyboardService`), Seitenwahl nach Feldtyp (`FieldPolicy`)
  - `layout/` – Tastenlayouts und Makros (`{TAB}`, `{F13}` …)
  - `ui/` – auf Canvas gezeichnete Tasten, Tab-Leiste, Scan-Pad, Popups
  - `config/` – JSON-Konfiguration, Speicher, Provisionierung (Managed Config, Datei, Broadcast)
  - `api/` – Intent-API (`INSTALL_CONFIG`, `SHOW_TAB`)
  - `scanner/` – Soft-Trigger für DataWedge, Honeywell, Intent
  - `app/` – Einrichtung und Einstellungen mit Info-Bereich
- `app/src/test/` – Unit-Tests (JUnit, Robolectric mit `@Config(sdk = [35])`, weil SDK 36 Java 21 bräuchte)
- `docs/` – Doku auf Englisch, Bilder in `docs/images/` (aus dem echten Code gerendert, siehe `tools/screenshots/`)
- `examples/` – Konfigurationsvorlagen, werden von `ExamplesTest` geprüft

## Besonderheiten

- Paketname `de.ictbaden.industrialkeyboard`, Debug `….debug` (Richtlinie 12). Intent-Actions hängen am Paketnamen und sind öffentliche API – nicht nebenbei ändern.
- Release-APKs signiert nur die CI mit dem Schlüssel aus den Secrets `ANDROID_KEYSTORE` (Base64), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. Ohne Schlüssel baut und testet die CI nur.
- `LICENSE` und `THIRD-PARTY-NOTICES.txt` werden beim Bauen als Assets in die APK kopiert (Info-Bereich). Neue Abhängigkeit → `THIRD-PARTY-NOTICES.txt` ergänzen.
- Honeywell-Trigger ist nur nach Herstellerdoku gebaut, nicht an einem Gerät getestet. Getestet an Zebra HC20 (Android 15) mit DataWedge.
- Oberfläche mehrsprachig nach Richtlinie 13: `res/values/strings.xml` (Englisch) plus `values-de`, `-es`, `-fr`, `-zh-rCN`. Kein Text fest im Code; neue Texte in allen fünf Dateien anlegen (Lint meldet fehlende). Französisch mit geschütztem Leerzeichen (U+00A0) vor `: ? ! ;`. Sprache per `language` in der Konfiguration, `AppLanguage` wendet sie an. Fehlermeldungen mit Code `IK-xxxx` (`app/ErrorCodes.kt`, `docs/error-codes.md`).
- Google Play: Store-Texte und -Grafiken in `fastlane/metadata/android/`, Anleitung und Console-Antworten in `docs/play-store.md`. Die CI legt bei Tags zusätzlich das signierte `.aab` ins Release.

## Regeln

- Commit-Nachrichten auf Deutsch.
- Nach Arbeit an diesem Projekt: Logbuch in `ict-baden-projekte` ergänzen.
- Vor jedem Release: Abschnitt „Unveröffentlicht“ im `CHANGELOG.md` pflegen.

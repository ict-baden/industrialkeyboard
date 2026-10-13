# Regenerating the documentation images

The images in `docs/images/` are rendered from the real keyboard and app code, without a device
or an emulator:

1. **Screens** – Robolectric renders the setup screen with the keyboard open on its test form,
   the settings and keyboard-only variants
   (`app/src/test/.../screenshots/DocScreenshots.kt`) into `app/build/screenshots`:
   ```bash
   ./gradlew recordRoborazziDebug -Pscreenshots
   ```
2. **Composition** – status bar, device frames, hero banner, theme and function key
   comparisons:
   ```bash
   python tools/screenshots/compose.py .
   ```

Requirements: JDK 17–21, Python 3 with Pillow, Roboto (available on Windows, otherwise set
`FONTS_DIR`).

The app icon and `docs/images/logo.png` come from `tools/icon/make_icon.py`.

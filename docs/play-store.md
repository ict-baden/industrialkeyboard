[← README](../README.md)

# Google Play

How Industrial Keyboard gets into Google Play, and what to answer in the Play Console.

- [What is in the repo](#what-is-in-the-repo)
- [Before the first upload](#before-the-first-upload)
- [App signing](#app-signing)
- [Play Console answers](#play-console-answers)
- [Publishing a version](#publishing-a-version)

## What is in the repo

| Path | Content |
|---|---|
| `fastlane/metadata/android/<locale>/` | Title, short and full description in `en-US`, `de-DE`, `es-ES`, `fr-FR`, `zh-CN` (limits: 30, 80, 4000 characters) |
| `fastlane/metadata/android/en-US/images/` | Hi-res icon 512 × 512, feature graphic 1024 × 500, six phone screenshots 1080 × 1920. Play shows them for every language. |
| Release `vX.Y.Z` | `IndustrialKeyboard-X.Y.Z.aab` – the App Bundle for Play, signed by the CI |

The folder layout is the one of [fastlane supply](https://docs.fastlane.tools/actions/supply/),
so the texts can later be uploaded automatically. Until then, copy them into the Play Console.

The graphics are rendered from the real UI like the documentation images: run the steps in
[tools/screenshots](../tools/screenshots/README.md); `compose.py` also writes the Play graphics.
Captions are in `PLAY_SCREENS` at the end of `compose.py`.

## Before the first upload

Done once, by a person with access to the company accounts:

1. **Developer account** as an organisation (ICT-Baden GmbH, needs a D-U-N-S number). An
   organisation account does not need the 14-day closed test with 12 testers that new personal
   accounts need.
2. **Package name** is final once the app exists in Play: `de.ictbaden.industrialkeyboard`.
   It assumes that the domain `ict-baden.de` belongs to us (richtlinien/12, open point in
   `richtlinien/OFFEN.md`).
3. **Privacy policy URL** must be public. [data-and-privacy.md](data-and-privacy.md) is the
   content; it needs a public address – either the repo is public, or the page goes on the
   company website.
4. **Contact details** for the store listing: e-mail (e.g. `support@ict-baden.de`), website.
5. **Translations** of the app and of the store texts read by native speakers (richtlinien/13).

## App signing

Play signs the apps it delivers with its *app signing key*; we upload with an *upload key*.
The CI signs the App Bundle with our release key (secret `ANDROID_KEYSTORE`).

Decide this **before the first upload**, it cannot be changed later:

| Choice | Effect |
|---|---|
| **Our release key becomes the app signing key** (Console: *Use a different key* → export with Google's PEPK tool) | Play and MDM/GitHub APKs carry the same signature: devices can switch between both sources without reinstalling. Recommended. |
| Google generates the app signing key, our key is the upload key (richtlinien/05) | Play-installed apps and our APKs have different signatures: a device with the MDM version cannot update from Play and vice versa. |

If Play asks for a separate upload key with the first choice, the CI needs a second keystore
secret for the App Bundle – a small change in `.github/workflows/build.yml`.

## Play Console answers

**App details:** category *Tools*, tags e.g. *Keyboard*, *Enterprise*. Free, no in-app
purchases.

**App access:** all functions are available without login. For the review: open the app, tap
*Enable* and *Select*, then try the keyboard in the test form on the same screen. The settings
have no password until one is configured.

**Ads:** the app contains no ads.

**Content rating (IARC questionnaire):** category *Utility, productivity, communication or
other*. Violence, sexuality, language, controlled substances, gambling: no. User interaction /
sharing of user content: no. Internet access: no (the app has no internet permission).

**Target audience:** 18 and over; not designed for children.

**News app:** no. **Government app:** no. **Financial features:** none. **Health:** none.

**Data safety:**

- *Does the app collect or share user data?* **No.** The app has no internet permission, so it
  cannot send anything off the device. It keeps no dictionary and stores nothing that is typed;
  only its own configuration is stored on the device ([data-and-privacy.md](data-and-privacy.md)).
- Data deletion: no account, nothing to delete on our side; *Clear storage* removes everything
  on the device.

**Keyboard:** Android shows its own warning that an input method can read what is typed; the
store text says that nothing is stored or sent. There is no extra declaration form for
keyboards.

**Permissions:**

| Permission | Why |
|---|---|
| `VIBRATE` | Key and scan feedback |

The input method service is bound with `BIND_INPUT_METHOD` (granted by the system when the user
enables the keyboard). No sensitive permissions that need a declaration form.

## Publishing a version

1. Update `CHANGELOG.md`, push the tag `vX.Y.Z`.
2. The CI builds and signs APK and App Bundle and attaches both to the GitHub release.
3. Play Console → *Testing → Internal testing* → create a release → upload the `.aab`
   → release notes from the changelog → roll out to the internal testers.
4. Check the pre-launch report, then promote the release to *Production*.

The `versionCode` is the CI run number, so every build is higher than the one before. Play
refuses an App Bundle whose `versionCode` was uploaded already – use a new tag, not a re-run.

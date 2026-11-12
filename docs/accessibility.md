[← README](../README.md)

# Accessibility

Industrial Keyboard can be used with a screen reader (TalkBack) and with large system fonts. This
page says what it does, how it maps to EN 301 549 (the standard behind BITV 2.0 in Germany), and
what is missing.

> **Basics, not audited.** The points below are built and covered by automated tests. There has
> been no formal EN 301 549 / BITV test, and the TalkBack behaviour has not yet been checked by ear
> on a device ([Limitations](limitations.md)). Please report what does not work in the
> [issues](https://github.com/ict-baden/industrialkeyboard/issues).

- [Using the keyboard with TalkBack](#using-the-keyboard-with-talkback)
- [Settings](#settings)
- [EN 301 549 mapping](#en-301-549-mapping)
- [Known gaps](#known-gaps)

## Using the keyboard with TalkBack

The keys are drawn on a canvas, so the keyboard gives TalkBack one node per key, tab, suggestion
and dictation button, with a translated name, its role (keyboard key, like the keys of Android's
own keyboards) and its state.

- **Explore** by moving a finger over the keys. TalkBack says "q", "Capital Q", "Shift on",
  "Delete", "Space, English", the field's Enter action ("Next", "Search", or the app's own name)
  and so on. Single symbols are spoken by TalkBack itself ("question mark").
- **Type** the way TalkBack's own *Typing preference* says (*Lift to type* or *Double-tap to
  type*), like with Gboard. Only if a TalkBack version does not type keyboard keys on lifting,
  [`accessibility.liftToType`](configuration.md#accessibility--screen-readers-and-large-fonts)
  (off by default) lets the keyboard itself type the key when the finger is lifted; it then
  ignores TalkBack's preference. A lift and a double tap on the same key within half a second
  type it only once.
- **Long press:** double-tap and hold. Holding a letter opens its alternatives (lift on the one you
  want); holding Backspace repeats it.
- **Actions menu** (TalkBack's "Actions"): the alternatives of a key (ä, á, à …), *Delete word* on
  Backspace (with `typing.deleteGesture`), *Lock* on Shift, Ctrl, Alt and Win, *Switch keyboard*
  on the space bar and 🌐, *Settings* on the hide button of the toolbar, *Pin*/*Unpin* on
  clipboard entries.
- **Announcements:** switching to letters, symbols, numbers, function keys or a custom page;
  opening *Text editing*; an autocorrection ("Corrected to …", a silent correction would be a
  trap). The panels – ⊞ *More features*, clipboard, snippets, emoji, dictation and the camera –
  are announced by their title when they open. Typed characters are not announced by the keyboard:
  the text field already does that.
- **Toolbar and tabs:** each button is named (*More features*, *Clipboard*, *Settings*, *Snippets*,
  *Start dictation*, *Hide keyboard*); the open page or panel is "selected".
- **Suggestions:** each word is a node; a snippet offered for a typed shortcut is "Snippet: …" with its name.
  Suggestions are not read out by themselves (that would be too chatty); explore the strip.
- **⊞ tiles** are named; the quick toggles are switches ("Gloves, on, switch").
- **Text editing panel:** Cut and Copy are "disabled" without a selection, Select says "on" while
  selecting.
- **Scan tab:** the scan pad says its title and hint ("Tap to scan. Hold for continuous scan ·
  DataWedge"). A double tap scans once or opens the camera; double-tap and hold scans for as long as
  the finger stays down. "Scanning …" is announced (live region).
- **Dictation:** the microphone says "Pause dictation" or "Continue dictation". TalkBack's own
  speech can be picked up by the microphone, so the keyboard announces nothing while it listens;
  the panel title is spoken once when it opens. Headphones help.

### Password fields

With a screen reader, the character keys of a password field are spoken only as "Key" (and their
alternatives as "Character 1, 2 …") unless headphones, a headset or a hearing aid are connected –
like Android and Gboard, so that bystanders do not hear the password. Space is a character of the
password as well and is spoken as "Key", too (otherwise the pauses would give away the number and
the lengths of the words of a passphrase). Backspace, Shift, Enter and the other function keys keep
their names.
[`accessibility.speakPasswordKeys`](configuration.md#accessibility--screen-readers-and-large-fonts):
`headset` (default), `always`, `never` (for counters and wards with bystanders). An unusual audio
device the keyboard does not recognise counts as none: the keys stay obscured.

### Large fonts

The text in the tab bar, the suggestion strip, the ⊞ tiles, the panel headers and the dictation
status follows the system font size, but only as far as it fits its fixed height (at most 130 %),
so labels are not clipped. The letters in the keys always use the largest size that fits the key.

With [`accessibility.followFontSize`](configuration.md#accessibility--screen-readers-and-large-fonts)
the key rows, the tab bar and the suggestion strip also grow with the system font size, up to
130 %. The keyboard then takes at most 60 % of the screen height (glove mode included) and is never
smaller than the configured `keyHeight`. It is off by default, so that the keyboard of a fleet does
not change height on devices with large fonts.

## Settings

**Settings → Accessibility:**

- *Android accessibility* opens the system's accessibility settings and says whether a screen
  reader is active.
- *Lift to type*, *Keys in password fields*, *Larger keys with large font* – the options above.

MDM keys: `lift_to_type`, `speak_password_keys`, `follow_font_size` ([Deployment](deployment.md)).

## EN 301 549 mapping

| Clause | What the keyboard does |
|---|---|
| 5.2 Activation of accessibility features | Works with TalkBack and Switch Access through Android's accessibility API; the nodes exist all the time, not only while TalkBack runs |
| 11.1.4.3 Contrast (minimum) | The [`contrast` theme](configuration.md#appearance) (black keys, white outlines, yellow highlights), also as a quick toggle |
| 11.1.4.4 Resize text | Text follows the system font size within the keys and bars (capped so it never clips); key rows with `followFontSize` |
| 11.5.2.5 Object information | Every key, tab, suggestion and button has a name, the role "keyboard key" (`android.inputmethodservice.Keyboard$Key`) or button, and a state (Shift, Caps lock, Ctrl/Alt/Win on or locked, selected tab, toggles on/off, disabled Cut/Copy) |
| 11.5.2.12 Execution of available actions | Click, long click and custom actions (alternatives, delete word, lock, pin) |
| 11.5.2.15 Change notification | Page and autocorrection announcements, pane titles of the panels, live region on the scan pad, names re-read after a key changes its state |
| 11.7 User preferences | Follows the system font size, dark mode and TalkBack's touch exploration; typing follows TalkBack's typing preference (lift or double tap); the keyboard's own lift-to-type is off by default |

## Known gaps

- The emoji picker is AndroidX's: its emoji are read by the library, not by the keyboard.
- The camera picture, its aiming frame and the OCR line picker are not described.
- The long-press popup itself is not accessible; its alternatives are in the actions menu and
  reachable with double-tap-and-hold.
- No braille, no D-pad or hardware-key navigation inside the keyboard, no glide typing.
- `announceForAccessibility` is deprecated from Android 16 without a replacement for input
  methods; the announcements may stop on a future Android version (the pane titles stay).
- Tested only with the automated tests so far; not with Samsung Voice Assistant or other screen
  readers.

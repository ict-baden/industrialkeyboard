package de.ictbaden.industrialkeyboard.config

import android.content.res.Resources
import java.util.Locale

/** Enum values that have a stable key in the JSON format. */
interface Keyed {
    val key: String
}

enum class ThemeMode(override val key: String) : Keyed {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    CONTRAST("contrast"),
}

/** Arrangement of the letter keys. */
enum class Alphabet(override val key: String, val label: String) : Keyed {
    /** Follows the system language of the device, see [forLocale]. */
    AUTO("auto", "Auto"),
    QWERTY("qwerty", "QWERTY"),
    QWERTZ("qwertz", "QWERTZ"),
    AZERTY("azerty", "AZERTY"),
    NORDIC("nordic", "Nordic"),
    SPANISH("spanish", "Español"),
    ;

    /** The layout actually shown: [AUTO] becomes the layout of the system language. */
    fun resolve(locale: Locale = systemLocale()): Alphabet = if (this == AUTO) forLocale(locale) else this

    companion object {
        private val QWERTZ_LANGUAGES = setOf("de", "lb", "rm", "cs", "sk", "hu", "sl", "hr", "bs", "sq")
        private val NORDIC_LANGUAGES = setOf("sv", "fi", "da", "nb", "nn", "no", "is", "fo")
        private val SPANISH_LANGUAGES = setOf("es", "ca", "gl", "eu")

        /** Letter layout people expect for a language and region, like the system keyboards do. */
        fun forLocale(locale: Locale): Alphabet {
            val language = locale.language.lowercase(Locale.ROOT)
            val country = locale.country.uppercase(Locale.ROOT)
            return when {
                // Switzerland types QWERTZ in every language, French Canada QWERTY.
                country == "CH" && language in setOf("de", "fr", "it", "rm") -> QWERTZ
                language in QWERTZ_LANGUAGES -> QWERTZ
                language == "fr" && country != "CA" -> AZERTY
                language == "nl" && country == "BE" -> AZERTY
                language in NORDIC_LANGUAGES -> NORDIC
                language in SPANISH_LANGUAGES -> SPANISH
                else -> QWERTY
            }
        }

        /**
         * First language of the device settings – not the app language, which an administrator may
         * set differently for the texts of the keyboard.
         */
        fun systemLocale(): Locale = runCatching { Resources.getSystem().configuration.locales[0] }.getOrNull() ?: Locale.getDefault()
    }
}

enum class NumericOrder(override val key: String) : Keyed {
    /** 1-2-3 in the top row, like a telephone. */
    PHONE("phone"),

    /** 7-8-9 in the top row, like a calculator or a PC numpad. */
    CALCULATOR("calculator"),
}

enum class Navigation(override val key: String) : Keyed {
    /** Tab bar above the keys (ABC, 123, symbols, function keys, scan, custom). */
    TABS("tabs"),

    /** Classic: ?123 / ABC keys inside the layout, no tab bar. */
    KEYS("keys"),
}

enum class ScanTriggerType(override val key: String) : Keyed {
    AUTO("auto"),
    DATAWEDGE("datawedge"),
    HONEYWELL("honeywell"),
    INTENT("intent"),

    /** The camera of the device, shown in the keyboard; `auto` uses it when there is no scan engine. */
    CAMERA("camera"),
    NONE("none"),
}

enum class ScanSuffix(override val key: String) : Keyed {
    NONE("none"),
    ENTER("enter"),
    TAB("tab"),
}

enum class KeyStyle(override val key: String) : Keyed {
    NORMAL("normal"),
    FUNCTION("function"),
    ACCENT("accent"),
}

data class Appearance(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** `#RRGGBB` for Enter, the active tab and the scan pad; `null` = theme default. */
    val accentColor: String? = null,
    /** Key height in percent (70–160); 130 or more works well with gloves. */
    val keyHeight: Int = 100,
    /** Keyboard width in percent (50–100), centered; for tablets and external displays. */
    val width: Int = 100,
    /** Extra row with digits above the letters. */
    val numberRow: Boolean = false,
    /** Extra row with F1–F12 above the letters (terminal emulation, hotkeys). */
    val functionRow: Boolean = false,
    /** Small hints for long-press characters (digits on the top row). */
    val keyHints: Boolean = true,
    /** Row with Ctrl, Win, Alt, Esc, Tab and Del above the letters (remote desktop, PC shortcuts). */
    val modifierRow: Boolean = false,
    /** Ctrl, Win and Alt as an extra row on the function key tab (Alt+F4, Ctrl+Home …). */
    val functionModifiers: Boolean = false,
)

data class LayoutSettings(
    val alphabet: Alphabet = Alphabet.AUTO,
    val navigation: Navigation = Navigation.TABS,
    val numericOrder: NumericOrder = NumericOrder.PHONE,
)

data class TabSettings(
    val alpha: Boolean = true,
    val numeric: Boolean = true,
    val symbols: Boolean = true,
    val function: Boolean = true,
    val scan: Boolean = true,
    /** `auto` (by the field's input type) or a tab key (`alpha`, `numeric`, … or a custom layout id) for text fields. */
    val preferred: String = Tabs.AUTO,
)

/** Programmable keys: key id (see [ProgrammableKey]) → macro. */
data class KeySettings(val programmable: Map<String, String> = ProgrammableKey.DEFAULTS)

/** Descriptions for the settings are string resources (`key_alpha_p1` …). */
enum class ProgrammableKey(val id: String, val default: String) {
    ALPHA_P1("alpha.p1", "{TAB}"),
    NUMERIC_P1("numeric.p1", "-"),
    NUMERIC_P2("numeric.p2", "/"),
    NUMERIC_P3("numeric.p3", ":"),
    NUMERIC_P4("numeric.p4", "+"),
    ;

    companion object {
        val DEFAULTS: Map<String, String> = entries.associate { it.id to it.default }
        fun byId(id: String) = entries.firstOrNull { it.id == id }
    }
}

data class TypingSettings(
    val autoCapitalize: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    /** Milliseconds until a held key shows its alternatives (200–1500). */
    val longPressDelay: Int = 400,
)

data class FeedbackSettings(
    val keyPopup: Boolean = true,
    val sound: Boolean = false,
    /** 0–100, `null` = system default volume. */
    val soundVolume: Int? = null,
    val vibrate: Boolean = true,
    /** 5–100 ms, `null` = default. */
    val vibrationMs: Int? = null,
)

data class ScanInputSettings(
    /**
     * Type barcodes that arrive as a broadcast into the focused field. Off by default: Android does
     * not tell a receiver who sent a broadcast, so without [permission] any app could type.
     */
    val enabled: Boolean = false,
    val action: String = DEFAULT_ACTION,
    /** Extra with the barcode text; common scanner extras are also checked. */
    val extra: String = "data",
    val suffix: ScanSuffix = ScanSuffix.ENTER,
    /** Permission the sending app must hold (enforced by Android); `null` = any app. */
    val permission: String? = null,
    /**
     * Only these apps may send (Android 14+, and only senders that share their identity with
     * `BroadcastOptions.setShareIdentityEnabled`); empty = no check. Fails closed: with a list set,
     * unidentified senders and older Android versions are refused.
     */
    val allowedPackages: List<String> = emptyList(),
) {
    companion object {
        const val DEFAULT_ACTION = "de.ictbaden.industrialkeyboard.action.SCAN_INPUT"
    }
}

data class ScannerSettings(
    val trigger: ScanTriggerType = ScanTriggerType.AUTO,
    /** Broadcast for `trigger: intent`. */
    val intentAction: String? = null,
    val intentPackage: String? = null,
    /** Scan key next to the space bar of the letter layout. */
    val scanKey: Boolean = false,
    val input: ScanInputSettings = ScanInputSettings(),
    val camera: CameraSettings = CameraSettings(),
    val data: DataSettings = DataSettings(),
    val ocr: OcrSettings = OcrSettings(),
    val nfc: NfcSettings = NfcSettings(),
)

data class CustomKey(
    val label: String,
    /** Macro, e.g. `OK{ENTER}`; defaults to the label. */
    val output: String = label,
    val width: Float = 1f,
    val style: KeyStyle = KeyStyle.NORMAL,
)

/** A layout of the administrator's own keys, shown as an extra tab. */
data class CustomLayout(
    val id: String,
    val label: String,
    val rows: List<List<CustomKey>>,
)

data class KeyboardConfig(
    val appearance: Appearance = Appearance(),
    val layout: LayoutSettings = LayoutSettings(),
    val tabs: TabSettings = TabSettings(),
    val keys: KeySettings = KeySettings(),
    val typing: TypingSettings = TypingSettings(),
    val feedback: FeedbackSettings = FeedbackSettings(),
    val scanner: ScannerSettings = ScannerSettings(),
    val customLayouts: List<CustomLayout> = emptyList(),
    val adminPasswordHash: String? = null,
    /** `auto` (device language) or one of [Languages.SUPPORTED] (richtlinien/13-sprachen.md). */
    val language: String = Languages.AUTO,
) {
    fun programmable(key: ProgrammableKey): String = keys.programmable[key.id] ?: key.default

    fun customLayout(id: String): CustomLayout? = customLayouts.firstOrNull { it.id == id }
}

object Languages {
    const val AUTO = "auto"
    val SUPPORTED = listOf("en", "de", "es", "fr", "zh-Hans")
}

/** Tab keys; custom layouts use their own id. */
object Tabs {
    const val AUTO = "auto"
    const val ALPHA = "alpha"
    const val NUMERIC = "numeric"
    const val SYMBOLS = "symbols"
    const val FUNCTION = "function"
    const val SCAN = "scan"
    val BUILT_IN = listOf(ALPHA, NUMERIC, SYMBOLS, FUNCTION, SCAN)
}

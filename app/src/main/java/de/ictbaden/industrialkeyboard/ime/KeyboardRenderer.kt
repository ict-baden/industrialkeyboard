package de.ictbaden.industrialkeyboard.ime

import android.content.Context
import androidx.annotation.DrawableRes
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.ui.Palette
import de.ictbaden.industrialkeyboard.ui.RenderState
import de.ictbaden.industrialkeyboard.ui.ShiftState
import de.ictbaden.industrialkeyboard.ui.TabBarView

/** Builds what the keyboard frame shows; shared by the input method and the settings preview. */
object KeyboardRenderer {
    const val BASE_ROW_DP = 54f

    /**
     * Row height (key plus gap) at 100 %: like Gboard about 6.8 % of the screen height – 65 dp on a
     * Pixel 9 Pro – but at least the former 54 dp and at most 70 dp. In landscape the short side
     * counts less, so the keys do not cover the whole screen.
     */
    fun baseRowDp(context: Context): Float {
        val config = context.resources.configuration
        val portrait = config.screenHeightDp >= config.screenWidthDp
        val height = maxOf(config.screenHeightDp, config.screenWidthDp).toFloat()
        val ideal = height * 0.068f
        return if (portrait) ideal.coerceIn(BASE_ROW_DP, 70f) else (ideal * 0.75f).coerceIn(48f, BASE_ROW_DP)
    }

    fun layoutFor(page: Page, config: KeyboardConfig, shift: ShiftState = ShiftState.OFF, languageKey: Boolean = false, emojiKey: Boolean = false): KeyboardLayout? = when (page) {
        is Page.Alpha -> Layouts.alpha(config, page.variant, languageKey, emojiKey)
        is Page.Symbols -> Layouts.symbols(config, page.page, page.variant)
        is Page.Numeric -> Layouts.numeric(config, page.variant)
        Page.Function -> Layouts.function(shifted = shift != ShiftState.OFF, modifiers = config.appearance.functionModifiers)
        Page.Scan -> null
        is Page.Custom -> config.customLayout(page.id)?.let(Layouts::custom) ?: Layouts.alpha(config)
    }

    /** Every page has the height of the letters, or of the function keys when they have more rows. */
    fun heightRows(config: KeyboardConfig): Int {
        val alpha = Layouts.alpha(config).rows.size
        val function = if (config.tabs.function && config.appearance.functionModifiers) Layouts.function(modifiers = true).rows.size else 0
        return maxOf(alpha, function)
    }

    fun tabs(config: KeyboardConfig, scanAvailable: Boolean): List<TabBarView.Tab>? =
        FieldPolicy.tabs(config, scanAvailable).takeIf { it.isNotEmpty() }?.map { id ->
            when (id) {
                Tabs.ALPHA -> TabBarView.Tab(id, "ABC")
                Tabs.NUMERIC -> TabBarView.Tab(id, "123")
                Tabs.SYMBOLS -> TabBarView.Tab(id, "#+=")
                Tabs.FUNCTION -> TabBarView.Tab(id, "Fn")
                Tabs.SCAN -> TabBarView.Tab(id, null, R.drawable.kb_scan)
                else -> TabBarView.Tab(id, config.customLayout(id)?.label ?: id)
            }
        }

    fun state(
        context: Context,
        config: KeyboardConfig,
        page: Page,
        shift: ShiftState,
        scannerName: String?,
        @DrawableRes enterIcon: Int = R.drawable.kb_enter,
        scanning: Boolean = false,
        /** No enlarged key preview over a password field: it would show the password to bystanders. */
        passwordField: Boolean = false,
        /** Armed (ONCE) or locked Ctrl, Win and Alt. */
        modifiers: Map<ModifierKey, ShiftState> = emptyMap(),
        camera: Boolean = false,
        /** 🌐 next to the space bar. */
        languageKey: Boolean = false,
        /** 😊 next to the space bar (the field takes emoji). */
        emojiKey: Boolean = false,
        /** Word suggestions, best first; empty = the tab bar. */
        suggestions: List<String> = emptyList(),
        /** The emoji panel replaces the keys. */
        emojiOpen: Boolean = false,
        /** Microphone in the tab bar (a voice input method exists). */
        mic: Boolean = false,
    ): RenderState {
        val appearance = config.appearance
        val rowHeight = (baseRowDp(context) * context.resources.displayMetrics.density * appearance.keyHeight / 100f).toInt()
        // Without a scanner the scan key would do nothing.
        val keys = if (scannerName == null && config.scanner.scanKey) config.copy(scanner = config.scanner.copy(scanKey = false)) else config
        return RenderState(
            palette = Palette.resolve(context, appearance),
            tabs = tabs(config, scannerName != null),
            selectedTab = page.tab,
            layout = layoutFor(page, keys, shift, languageKey, emojiKey),
            shift = shift,
            modifiers = modifiers,
            enterIcon = enterIcon,
            rowHeight = rowHeight,
            heightRows = heightRows(config),
            keyPopups = config.feedback.keyPopup && !passwordField,
            keyBorders = config.appearance.keyBorders,
            deleteGesture = config.typing.deleteGesture,
            longPressDelay = config.typing.longPressDelay.toLong(),
            widthPercent = appearance.width,
            scannerName = scannerName,
            scanning = scanning,
            camera = camera,
            suggestions = suggestions,
            emojiOpen = emojiOpen,
            mic = mic,
        )
    }
}

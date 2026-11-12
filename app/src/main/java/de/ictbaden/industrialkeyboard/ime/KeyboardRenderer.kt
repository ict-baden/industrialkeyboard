package de.ictbaden.industrialkeyboard.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.annotation.DrawableRes
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.Snippets
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.TopBar
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState
import de.ictbaden.industrialkeyboard.ui.ClipItem
import de.ictbaden.industrialkeyboard.ui.Palette
import de.ictbaden.industrialkeyboard.ui.PanelSnippet
import de.ictbaden.industrialkeyboard.ui.RenderState
import de.ictbaden.industrialkeyboard.ui.ShiftState
import de.ictbaden.industrialkeyboard.ui.TabBarView

/** Builds what the keyboard frame shows; shared by the input method and the settings preview. */
object KeyboardRenderer {
    const val BASE_ROW_DP = 54f

    /** `accessibility.followFontSize`: rows grow with the font size up to this factor … */
    const val MAX_FONT_FACTOR = 1.3f

    /** … and the keys with the bar take at most this share of the screen height. */
    const val MAX_SCREEN_SHARE = 0.6f

    /** Height of the tab bar and the suggestion strip at a factor of 1. */
    const val BAR_DP = 46f

    /** How much the rows grow with the system [fontScale]: 1 unless [follow], never below 1, at most [MAX_FONT_FACTOR]. */
    fun fontFactor(follow: Boolean, fontScale: Float): Float = if (!follow) 1f else fontScale.coerceIn(1f, MAX_FONT_FACTOR)

    /**
     * Row height in pixels: [basePx] at [keyHeight] percent (glove mode included) times [factor],
     * but the [heightRows] rows plus the bar ([barPx]) at most [MAX_SCREEN_SHARE] of [screenHeightPx]
     * – and never less than without the factor, so a configured height is never shrunk.
     */
    internal fun rowHeightPx(basePx: Float, keyHeight: Int, factor: Float, heightRows: Int, screenHeightPx: Int, barPx: Float): Int {
        val unscaled = basePx * keyHeight / 100f
        if (factor <= 1f) return unscaled.toInt()
        val cap = (screenHeightPx * MAX_SCREEN_SHARE - barPx) / heightRows.coerceAtLeast(1)
        return maxOf(unscaled, minOf(unscaled * factor, cap)).toInt()
    }

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

    /** The text editing panel with its texts in the keyboard's language. */
    fun textEditingLayout(context: Context): KeyboardLayout {
        val res = AppLanguage.localized(context)
        return Layouts.textEditing(
            Layouts.TextEditLabels(
                home = res.getString(R.string.text_edit_home),
                end = res.getString(R.string.text_edit_end),
                select = res.getString(R.string.text_edit_select),
                selectAll = res.getString(R.string.text_edit_select_all),
                cut = res.getString(R.string.text_edit_cut),
                copy = res.getString(R.string.text_edit_copy),
                paste = res.getString(R.string.text_edit_paste),
                delete = res.getString(R.string.text_edit_delete),
            ),
        )
    }

    /** Every page has the height of the letters, or of the function keys when they have more rows. */
    fun heightRows(config: KeyboardConfig): Int {
        val alpha = Layouts.alpha(config).rows.size
        val function = if (config.tabs.function && config.appearance.functionModifiers) Layouts.function(modifiers = true).rows.size else 0
        return maxOf(alpha, function)
    }

    /** Gboard's toolbar instead of the tabs (`layout.topBar: toolbar`, only with tab navigation). */
    fun toolbar(config: KeyboardConfig) = config.layout.topBar == TopBar.TOOLBAR && config.layout.navigation == Navigation.TABS

    /** ⊞ features, clipboard, settings, then Fn, scan, snippets and custom layouts as far as switched on. */
    fun toolbarTabs(config: KeyboardConfig, scanAvailable: Boolean, passwordField: Boolean = false): List<TabBarView.Tab> = buildList {
        add(TabBarView.Tab(Tabs.TOOL_MENU, null, R.drawable.kb_apps))
        add(TabBarView.Tab(Tabs.TOOL_CLIPBOARD, null, R.drawable.kb_clipboard))
        add(TabBarView.Tab(Tabs.TOOL_SETTINGS, null, R.drawable.kb_settings))
        if (config.tabs.function) add(TabBarView.Tab(Tabs.FUNCTION, "Fn"))
        if (config.tabs.scan && scanAvailable) add(TabBarView.Tab(Tabs.SCAN, null, R.drawable.kb_scan))
        if (snippetsButton(config, passwordField)) add(SNIPPETS_TAB)
        config.customLayouts.forEach { add(TabBarView.Tab(it.id, it.label)) }
    }

    /**
     * The tab bar: the toolbar, or the tabs plus the snippets button as the last tab. Not part of
     * [FieldPolicy.tabs], so it is never a start page, `tabs.preferred` or `{SHOW:…}` target; `null`
     * (no bar) with `navigation: keys`, also with the snippets button.
     */
    fun tabs(config: KeyboardConfig, scanAvailable: Boolean, passwordField: Boolean = false): List<TabBarView.Tab>? =
        if (toolbar(config)) {
            toolbarTabs(config, scanAvailable, passwordField)
        } else {
            FieldPolicy.tabs(config, scanAvailable).takeIf { it.isNotEmpty() }?.map { id ->
                when (id) {
                    Tabs.ALPHA -> TabBarView.Tab(id, "ABC")
                    Tabs.NUMERIC -> TabBarView.Tab(id, "123")
                    Tabs.SYMBOLS -> TabBarView.Tab(id, "#+=")
                    Tabs.FUNCTION -> TabBarView.Tab(id, "Fn")
                    Tabs.SCAN -> TabBarView.Tab(id, null, R.drawable.kb_scan)
                    else -> TabBarView.Tab(id, config.customLayout(id)?.label ?: id)
                }
            }?.let { tabs -> if (snippetsButton(config, passwordField)) tabs + SNIPPETS_TAB else tabs }
        }

    private val SNIPPETS_TAB = TabBarView.Tab(Tabs.TOOL_SNIPPETS, null, R.drawable.kb_snippets)

    /** `snippets.button`, never over a password field, and only with something to show. */
    private fun snippetsButton(config: KeyboardConfig, passwordField: Boolean) =
        config.snippets.button && !passwordField && Snippets.reachable(config)

    /** The tab lit in the bar: an open panel or the page (the toolbar marks no ABC; the tab bar marks the snippets tab while it is open). */
    fun selectedTab(config: KeyboardConfig, page: Page, toolPanel: String?): String? =
        if (toolbar(config)) toolPanel ?: page.tab.takeIf { it != Tabs.ALPHA } else if (toolPanel == Tabs.TOOL_SNIPPETS) toolPanel else page.tab

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
        /** Open toolbar panel: [Tabs.TOOL_MENU], [Tabs.TOOL_CLIPBOARD] or [Tabs.TOOL_EDIT], else null. */
        toolPanel: String? = null,
        /** Clipboard items for the clipboard panel, newest first. */
        clips: List<ClipItem> = emptyList(),
        /** Clipboard history is switched on (otherwise the panel explains how). */
        clipboardHistory: Boolean = false,
        /** Select lit, Cut/Copy dimmed on the text editing panel ([Tabs.TOOL_EDIT]). */
        textEdit: TextEditState? = null,
        /** Entries of the snippets panel ([Tabs.TOOL_SNIPPETS]). */
        snippets: List<PanelSnippet> = emptyList(),
        /** ✎ in the snippets panel (users may keep their own). */
        snippetsEditable: Boolean = false,
        /** Label of the snippet whose shortcut was typed (middle of the suggestion strip). */
        snippetSuggestion: String? = null,
        /** TalkBack's touch exploration is on. */
        touchExploration: Boolean = false,
        /** Password field without a headset: keys are spoken as "Key" ([PasswordSpeech]). */
        obscureKeys: Boolean = false,
        /** `IME_ACTION_*` for the spoken name of Enter ([de.ictbaden.industrialkeyboard.ui.KeyDescriber.enterAction]). */
        enterAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        /** The app's name for the Enter action. */
        enterLabel: String? = null,
    ): RenderState {
        val appearance = config.appearance
        val metrics = context.resources.displayMetrics
        val factor = fontFactor(config.accessibility.followFontSize, context.resources.configuration.fontScale)
        val heightRows = heightRows(config)
        val rowHeight = rowHeightPx(
            basePx = baseRowDp(context) * metrics.density,
            keyHeight = appearance.keyHeight,
            factor = factor,
            heightRows = heightRows,
            screenHeightPx = metrics.heightPixels,
            barPx = BAR_DP * metrics.density * factor,
        )
        // Without a scanner the scan key would do nothing.
        val keys = if (scannerName == null && config.scanner.scanKey) config.copy(scanner = config.scanner.copy(scanKey = false)) else config
        return RenderState(
            palette = Palette.resolve(context, appearance),
            tabs = tabs(config, scannerName != null, passwordField),
            // The toolbar marks an open panel or a page it leads to; ABC needs no mark there.
            selectedTab = selectedTab(config, page, toolPanel),
            // The text editing panel takes the place of the keys, in the same height (heightRows stays).
            layout = if (toolPanel == Tabs.TOOL_EDIT) textEditingLayout(context) else layoutFor(page, keys, shift, languageKey, emojiKey),
            shift = shift,
            modifiers = modifiers,
            enterIcon = enterIcon,
            rowHeight = rowHeight,
            heightRows = heightRows,
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
            toolbar = toolbar(config),
            toolPanel = toolPanel,
            clips = clips,
            clipboardHistory = clipboardHistory,
            oneHanded = config.appearance.oneHanded,
            textEdit = textEdit.takeIf { toolPanel == Tabs.TOOL_EDIT },
            snippets = snippets.takeIf { toolPanel == Tabs.TOOL_SNIPPETS }.orEmpty(),
            snippetsEditable = snippetsEditable,
            snippetSuggestion = snippetSuggestion,
            touchExploration = touchExploration,
            liftToType = config.accessibility.liftToType,
            obscureKeys = obscureKeys,
            enterAction = enterAction,
            enterLabel = enterLabel,
            barScale = factor,
        )
    }
}

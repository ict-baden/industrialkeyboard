package de.ictbaden.industrialkeyboard.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.BuildConfig
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.AimMode
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.CameraSettings
import de.ictbaden.industrialkeyboard.config.ConfigException
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.DataSettings
import de.ictbaden.industrialkeyboard.config.Gs1Mode
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Languages
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.NdefRecords
import de.ictbaden.industrialkeyboard.config.NfcRead
import de.ictbaden.industrialkeyboard.config.NfcSettings
import de.ictbaden.industrialkeyboard.config.NumericOrder
import de.ictbaden.industrialkeyboard.config.OcrCharset
import de.ictbaden.industrialkeyboard.config.OcrMode
import de.ictbaden.industrialkeyboard.config.OcrSettings
import de.ictbaden.industrialkeyboard.config.ProgrammableKey
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.config.ScanSuffix
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.TextCase
import de.ictbaden.industrialkeyboard.config.ThemeMode
import de.ictbaden.industrialkeyboard.config.VoiceInput
import de.ictbaden.industrialkeyboard.config.UidFormat
import de.ictbaden.industrialkeyboard.ime.Field
import de.ictbaden.industrialkeyboard.ime.FieldPolicy
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.scanner.ScanTrigger
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.KeyboardView
import de.ictbaden.industrialkeyboard.ui.Palette
import de.ictbaden.industrialkeyboard.ui.ShiftState
import java.util.Locale

/** All keyboard options with a live preview at the top. Protected by the admin password if one is set. */
class SettingsActivity : AppCompatActivity() {

    private val store by lazy { ConfigStore.get(this) }
    private lateinit var ui: Ui
    private lateinit var dialogs: Dialogs
    private lateinit var content: LinearLayout
    private lateinit var preview: KeyboardRootView
    private lateinit var scanner: ScanTrigger
    private val field = Field(AlphaVariant.TEXT, null)
    private var previewPage: Page = Page.Alpha(AlphaVariant.TEXT)
    private var previewShift = ShiftState.OFF
    private var unlocked = false
    private val listener: (KeyboardConfig) -> Unit = { render() }

    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::readText)?.let(::confirmImport)
    }

    private val importBarcode = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringExtra(ConfigScanActivity.EXTRA_TEXT)?.takeIf { result.resultCode == RESULT_OK }?.let(::confirmImport)
    }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ConfigJson.write(store.config)) }
        }.onSuccess { dialogs.toast(getString(R.string.config_exported)) }
            .onFailure { dialogs.toast(dialogs.withCode(getString(R.string.export_failed, it.message.orEmpty()), ErrorCodes.EXPORT_FAILED)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = Ui(this)
        dialogs = Dialogs(this, ui)
        scanner = ScanTrigger(this)
        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.settings_title)
            navigationIcon = ContextCompat.getDrawable(this@SettingsActivity, R.drawable.ic_back)
            setNavigationContentDescription(R.string.back)
            setNavigationOnClickListener { finish() }
        }
        preview = KeyboardRootView(this, headroomDp = 0f).apply {
            tabBar.showHide = false
            tabBar.onTab = { tab -> FieldPolicy.pageForTab(tab, field, store.config, scanner.available)?.let { previewPage = it; renderPreview() } }
            keyboard.listener = previewKeys
            scanRow.listener = previewKeys
        }
        val previewCard = MaterialCardView(this).apply {
            radius = ui.dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            addView(preview)
        }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32))
        }
        val scroll = NestedScrollView(this).apply { addView(content) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(previewCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4))
            })
            addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            toolbar.updatePadding(top = bars.top, left = bars.left, right = bars.right)
            scroll.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
            // Consume: the preview must not pad itself for the navigation bar.
            WindowInsetsCompat.CONSUMED
        }
        toolbar.setBackgroundColor(ui.color(com.google.android.material.R.attr.colorSurfaceContainer))
        toolbar.navigationIcon?.setTint(ui.color(com.google.android.material.R.attr.colorOnSurface))
        store.addListener(listener)
        // Only a successful unlock survives a rotation; a recreated activity must not skip the password.
        unlocked = savedInstanceState?.getBoolean(KEY_UNLOCKED) == true
        val hash = store.config.adminPasswordHash
        if (store.unreadable) {
            // No password can match: say why instead of counting wrong entries.
            content.visibility = View.INVISIBLE
            dialogs.message(getString(R.string.settings_protected), dialogs.withCode(getString(R.string.config_unreadable), ErrorCodes.CONFIG_UNREADABLE)) { finish() }
        } else if (hash != null && !unlocked) {
            content.visibility = View.INVISIBLE
            dialogs.requirePassword(hash) { ok ->
                if (ok) {
                    unlocked = true
                    content.visibility = View.VISIBLE
                } else {
                    finish()
                }
            }
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, unlocked)
    }

    override fun onDestroy() {
        store.removeListener(listener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Provisioning.applyAllAsync(this)
        render()
    }

    /** Preview keys: switch pages and shift like the keyboard, but type nothing. */
    private val previewKeys = object : KeyboardView.Listener {
        override fun onKeyDown(key: Key) = Unit
        override fun onCursorMove(steps: Int) = Unit
        override fun onKeyLongPress(key: Key) = false
        override fun onKey(key: Key, alternative: String?) {
            when (val output = key.output) {
                is KeyOutput.ShowTab -> FieldPolicy.pageForTab(output.tab, field, store.config, scanner.available)?.let { previewPage = it }
                KeyOutput.SymbolsPage -> previewPage = Page.Symbols(if ((previewPage as? Page.Symbols)?.page == 2) 1 else 2, AlphaVariant.TEXT)
                KeyOutput.Shift -> previewShift = if (previewShift == ShiftState.OFF) ShiftState.ONCE else ShiftState.OFF
                else -> if (previewShift == ShiftState.ONCE && key.letter) previewShift = ShiftState.OFF
            }
            renderPreview()
        }
    }

    @VisibleForTesting
    internal fun showPreviewTab(tab: String) {
        FieldPolicy.pageForTab(tab, field, store.config, scanner.available)?.let { previewPage = it }
        renderPreview()
    }

    private fun renderPreview() {
        val config = store.config
        scanner.settings = config.scanner
        if (FieldPolicy.pageForTab(previewPage.tab, field, config, scanner.available) == null) previewPage = Page.Alpha(AlphaVariant.TEXT)
        preview.render(KeyboardRenderer.state(this, config, previewPage, previewShift, scanner.name).copy(keyPopups = false))
    }

    private fun update(transform: (KeyboardConfig) -> KeyboardConfig) {
        store.update(transform)
    }

    private fun render() {
        if (!::content.isInitialized) return
        renderPreview()
        val c = store.config
        content.removeAllViews()

        content.addView(ui.section(getString(R.string.section_look)))
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_globe, getString(R.string.language), languageName(c.language)) { pickLanguage() },
                    ui.choiceRow(
                        R.drawable.ic_palette,
                        getString(R.string.theme),
                        listOf(
                            ThemeMode.SYSTEM to getString(R.string.theme_system),
                            ThemeMode.LIGHT to getString(R.string.theme_light),
                            ThemeMode.DARK to getString(R.string.theme_dark),
                            ThemeMode.CONTRAST to getString(R.string.theme_contrast),
                        ),
                        c.appearance.theme,
                    ) { theme -> update { it.copy(appearance = it.appearance.copy(theme = theme)) } },
                    ui.row(R.drawable.ic_palette, getString(R.string.accent_color), c.appearance.accentColor ?: getString(R.string.theme_default), trailing = swatch(c)) {
                        dialogs.pickColor(getString(R.string.accent_color), c.appearance.accentColor) { color ->
                            update { it.copy(appearance = it.appearance.copy(accentColor = color)) }
                        }
                    },
                    ui.switchRow(
                        R.drawable.ic_palette,
                        getString(R.string.dynamic_color),
                        getString(R.string.dynamic_color_summary),
                        c.appearance.dynamicColor,
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && c.appearance.accentColor == null && c.appearance.theme != ThemeMode.CONTRAST,
                    ) { on -> update { it.copy(appearance = it.appearance.copy(dynamicColor = on)) } },
                    ui.sliderRow(R.drawable.ic_keyboard, getString(R.string.key_height), 70, 160, 5, c.appearance.keyHeight, format = ::percent) { value ->
                        update { it.copy(appearance = it.appearance.copy(keyHeight = value)) }
                    },
                    ui.sliderRow(R.drawable.ic_keyboard, getString(R.string.keyboard_width), 50, 100, 5, c.appearance.width, format = ::percent) { value ->
                        update { it.copy(appearance = it.appearance.copy(width = value)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.number_row), getString(R.string.number_row_summary), c.appearance.numberRow) { on ->
                        update { it.copy(appearance = it.appearance.copy(numberRow = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.function_row), getString(R.string.function_row_summary), c.appearance.functionRow) { on ->
                        update { it.copy(appearance = it.appearance.copy(functionRow = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.modifier_row), getString(R.string.modifier_row_summary), c.appearance.modifierRow) { on ->
                        update { it.copy(appearance = it.appearance.copy(modifierRow = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.function_modifiers), getString(R.string.function_modifiers_summary), c.appearance.functionModifiers) { on ->
                        update { it.copy(appearance = it.appearance.copy(functionModifiers = on)) }
                    },
                    ui.switchRow(R.drawable.ic_info, getString(R.string.key_hints), getString(R.string.key_hints_summary), c.appearance.keyHints) { on ->
                        update { it.copy(appearance = it.appearance.copy(keyHints = on)) }
                    },
                    ui.switchRow(R.drawable.ic_info, getString(R.string.symbol_hints), getString(R.string.symbol_hints_summary), c.appearance.symbolHints) { on ->
                        update { it.copy(appearance = it.appearance.copy(symbolHints = on)) }
                    },
                    ui.switchRow(R.drawable.ic_palette, getString(R.string.key_borders), getString(R.string.key_borders_summary), c.appearance.keyBorders) { on ->
                        update { it.copy(appearance = it.appearance.copy(keyBorders = on)) }
                    },
                ),
            ),
        )

        content.addView(ui.section(getString(R.string.section_layout)))
        content.addView(
            ui.card(
                listOf(
                    ui.choiceRow(R.drawable.ic_keyboard, getString(R.string.letters), Alphabet.entries.map { it to alphabetLabel(it) }, c.layout.alphabet) { alphabet ->
                        update { it.copy(layout = it.layout.copy(alphabet = alphabet)) }
                    },
                    ui.row(R.drawable.ic_globe, getString(R.string.keyboard_languages), getString(R.string.keyboard_languages_summary)) { openLanguageSettings() },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.emoji_key), getString(R.string.emoji_key_summary), c.appearance.emojiKey) { on ->
                        update { it.copy(appearance = it.appearance.copy(emojiKey = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.umlaut_keys), getString(R.string.umlaut_keys_summary), c.layout.umlautKeys) { on ->
                        update { it.copy(layout = it.layout.copy(umlautKeys = on)) }
                    },
                    ui.switchRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.symbols_key),
                        getString(R.string.symbols_key_summary),
                        c.layout.symbolsKey || c.layout.navigation == Navigation.KEYS,
                        enabled = c.layout.navigation == Navigation.TABS,
                    ) { on -> update { it.copy(layout = it.layout.copy(symbolsKey = on)) } },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.navigation),
                        listOf(Navigation.TABS to getString(R.string.navigation_tabs), Navigation.KEYS to getString(R.string.navigation_keys)),
                        c.layout.navigation,
                    ) { navigation -> update { it.copy(layout = it.layout.copy(navigation = navigation)) } },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.numeric_pad),
                        listOf(NumericOrder.PHONE to getString(R.string.numeric_phone), NumericOrder.CALCULATOR to getString(R.string.numeric_calculator)),
                        c.layout.numericOrder,
                    ) { order -> update { it.copy(layout = it.layout.copy(numericOrder = order)) } },
                    ui.multiChoiceRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.tabs),
                        getString(R.string.tabs_summary),
                        listOf(
                            Triple(Tabs.ALPHA, "ABC", c.tabs.alpha),
                            Triple(Tabs.NUMERIC, "123", c.tabs.numeric),
                            Triple(Tabs.SYMBOLS, "#+=", c.tabs.symbols),
                            Triple(Tabs.FUNCTION, "Fn", c.tabs.function),
                            Triple(Tabs.SCAN, getString(R.string.tab_scan), c.tabs.scan),
                        ),
                        enabled = c.layout.navigation == Navigation.TABS,
                    ) { tab, on ->
                        update {
                            it.copy(
                                tabs = when (tab) {
                                    Tabs.ALPHA -> it.tabs.copy(alpha = on)
                                    Tabs.NUMERIC -> it.tabs.copy(numeric = on)
                                    Tabs.SYMBOLS -> it.tabs.copy(symbols = on)
                                    Tabs.FUNCTION -> it.tabs.copy(function = on)
                                    else -> it.tabs.copy(scan = on)
                                },
                            )
                        }
                    },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.start_tab),
                        listOf(
                            Tabs.AUTO to getString(R.string.automatic),
                            Tabs.ALPHA to "ABC",
                            Tabs.NUMERIC to "123",
                            Tabs.SYMBOLS to "#+=",
                            Tabs.FUNCTION to "Fn",
                            Tabs.SCAN to getString(R.string.tab_scan),
                        ) +
                            c.customLayouts.map { it.id to it.label },
                        c.tabs.preferred,
                    ) { tab -> update { it.copy(tabs = it.tabs.copy(preferred = tab)) } },
                ),
            ),
        )

        content.addView(ui.section(getString(R.string.section_programmable_keys)))
        content.addView(
            ui.card(
                ProgrammableKey.entries.map { key ->
                    val value = c.programmable(key)
                    val description = getString(keyDescription(key))
                    ui.row(R.drawable.ic_key, key.id.uppercase(), "$value  ·  $description") {
                        dialogs.editMacro(description, value, key.default) { macro ->
                            update { it.copy(keys = it.keys.copy(programmable = it.keys.programmable + (key.id to (macro ?: key.default)))) }
                        }
                    }
                },
            ),
        )

        content.addView(ui.section(getString(R.string.section_typing)))
        content.addView(
            ui.card(
                listOf(
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.suggestions), getString(R.string.suggestions_summary), c.typing.suggestions) { on ->
                        update { it.copy(typing = it.typing.copy(suggestions = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.autocorrect), getString(R.string.autocorrect_summary), c.typing.autocorrect, enabled = c.typing.suggestions) { on ->
                        update { it.copy(typing = it.typing.copy(autocorrect = on)) }
                    },
                    ui.choiceRow(
                        R.drawable.ic_mic,
                        getString(R.string.voice_input),
                        listOf(
                            VoiceInput.AUTO to getString(R.string.automatic),
                            VoiceInput.SYSTEM to getString(R.string.voice_system),
                            VoiceInput.OFF to getString(R.string.scan_trigger_off),
                        ),
                        if (c.typing.voice == VoiceInput.OFFLINE) VoiceInput.OFF else c.typing.voice,
                    ) { voice -> update { it.copy(typing = it.typing.copy(voice = voice)) } },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.delete_gesture), getString(R.string.delete_gesture_summary), c.typing.deleteGesture) { on ->
                        update { it.copy(typing = it.typing.copy(deleteGesture = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.auto_capitalization), getString(R.string.auto_capitalization_summary), c.typing.autoCapitalize) { on ->
                        update { it.copy(typing = it.typing.copy(autoCapitalize = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.double_space_period), getString(R.string.double_space_period_summary), c.typing.doubleSpacePeriod) { on ->
                        update { it.copy(typing = it.typing.copy(doubleSpacePeriod = on)) }
                    },
                    ui.sliderRow(R.drawable.ic_timer, getString(R.string.long_press_delay), 200, 1500, 50, c.typing.longPressDelay, format = ::millis) { value ->
                        update { it.copy(typing = it.typing.copy(longPressDelay = value)) }
                    },
                ),
            ),
        )

        content.addView(ui.section(getString(R.string.section_feedback)))
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.key_popup), getString(R.string.key_popup_summary), c.feedback.keyPopup) { on ->
                        update { it.copy(feedback = it.feedback.copy(keyPopup = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.vibration), null, c.feedback.vibrate) { on ->
                        update { it.copy(feedback = it.feedback.copy(vibrate = on)) }
                    },
                    if (c.feedback.vibrate) {
                        ui.sliderRow(R.drawable.ic_timer, getString(R.string.vibration_length), 5, 100, 5, c.feedback.vibrationMs ?: 20, format = ::millis) { value ->
                            update { it.copy(feedback = it.feedback.copy(vibrationMs = value)) }
                        }
                    } else {
                        null
                    },
                    ui.switchRow(R.drawable.ic_keyboard, getString(R.string.sound), null, c.feedback.sound) { on ->
                        update { it.copy(feedback = it.feedback.copy(sound = on)) }
                    },
                    if (c.feedback.sound) {
                        ui.sliderRow(R.drawable.ic_keyboard, getString(R.string.volume), 0, 100, 5, c.feedback.soundVolume ?: 50, format = ::percent) { value ->
                            update { it.copy(feedback = it.feedback.copy(soundVolume = value)) }
                        }
                    } else {
                        null
                    },
                ),
            ),
        )

        content.addView(ui.section(getString(R.string.section_scanner)))
        scanner.settings = c.scanner
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        getString(R.string.scan_trigger),
                        listOf(
                            ScanTriggerType.AUTO to getString(R.string.automatic),
                            ScanTriggerType.DATAWEDGE to "DataWedge",
                            ScanTriggerType.HONEYWELL to "Honeywell",
                            ScanTriggerType.INTENT to "Intent",
                            ScanTriggerType.CAMERA to getString(R.string.scanner_camera),
                            ScanTriggerType.NONE to getString(R.string.scan_trigger_off),
                        ),
                        c.scanner.trigger,
                    ) { trigger ->
                        if (trigger == ScanTriggerType.INTENT && c.scanner.intentAction == null) {
                            editIntentAction(c)
                        } else {
                            update { it.copy(scanner = it.scanner.copy(trigger = trigger)) }
                            if (trigger == ScanTriggerType.CAMERA && !CameraPermissionActivity.granted(this)) CameraPermissionActivity.start(this)
                        }
                    },
                    if (c.scanner.trigger == ScanTriggerType.INTENT) {
                        ui.row(R.drawable.ic_scan, getString(R.string.trigger_broadcast), c.scanner.intentAction) { editIntentAction(c) }
                    } else {
                        null
                    },
                    ui.row(R.drawable.ic_scan, getString(R.string.scanner_in_use), scanner.name ?: getString(R.string.scanner_none_found), onClick = null),
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.scan_key), getString(R.string.scan_key_summary), c.scanner.scanKey) { on ->
                        update { it.copy(scanner = it.scanner.copy(scanKey = on)) }
                    },
                    ui.switchRow(
                        R.drawable.ic_scan,
                        getString(R.string.scan_from_broadcasts),
                        // Android does not tell who sent a broadcast: say so while nothing restricts the senders.
                        c.scanner.input.let { input ->
                            if (input.enabled && input.permission == null && input.allowedPackages.isEmpty()) getString(R.string.scan_from_broadcasts_open, input.action) else input.action
                        },
                        c.scanner.input.enabled,
                    ) { on -> update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = on))) } },
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        getString(R.string.after_scan),
                        listOf(
                            ScanSuffix.NONE to getString(R.string.after_scan_nothing),
                            ScanSuffix.ENTER to getString(R.string.after_scan_enter),
                            ScanSuffix.TAB to getString(R.string.after_scan_tab),
                        ),
                        c.scanner.input.suffix,
                        enabled = c.scanner.data.suffix == null,
                    ) { suffix -> update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(suffix = suffix))) } },
                ),
            ),
        )

        renderCameraSection(c)
        renderOcrSection(c)
        renderNfcSection(c)
        renderDataSection(c)

        content.addView(ui.section(getString(R.string.section_configuration)))
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.row(R.drawable.ic_upload, getString(R.string.import_file), getString(R.string.import_file_summary)) {
                        importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    ui.row(R.drawable.ic_download, getString(R.string.export_file), getString(R.string.export_file_summary)) {
                        exportFile.launch(Provisioning.CONFIG_FILE_NAME)
                    },
                    ui.row(R.drawable.ic_qr, getString(R.string.export_barcode), getString(R.string.export_barcode_summary)) {
                        ConfigBarcode.show(this, ui, store.config)
                    },
                    ui.row(R.drawable.ic_qr, getString(R.string.import_barcode), getString(R.string.import_barcode_summary)) {
                        importBarcode.launch(Intent(this, ConfigScanActivity::class.java))
                    },
                    ui.row(R.drawable.ic_restart, getString(R.string.reset_defaults), getString(R.string.reset_defaults_summary)) {
                        dialogs.confirm(getString(R.string.reset_confirm_title), getString(R.string.reset_confirm_message), getString(R.string.button_reset)) {
                            update { KeyboardConfig(adminPasswordHash = it.adminPasswordHash) }
                        }
                    },
                    ui.row(R.drawable.ic_key, getString(R.string.admin_password), getString(if (c.adminPasswordHash == null) R.string.password_not_set else R.string.password_set)) {
                        dialogs.changePassword { hash -> update { it.copy(adminPasswordHash = hash) } }
                    },
                    Provisioning.lastError(this)?.let { ui.row(R.drawable.ic_info, getString(R.string.last_provisioning_error), dialogs.withCode(it, ErrorCodes.PROVISIONING), onClick = null) },
                    ui.row(
                        R.drawable.ic_download,
                        getString(R.string.config_file_for_emm),
                        "Android/data/$packageName/files/${Provisioning.CONFIG_FILE_NAME}",
                        onClick = null,
                    ),
                ),
            ),
        )

        content.addView(ui.section(getString(R.string.section_info)))
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/') == packageName
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_info, getString(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.COMMIT}", onClick = null),
                    ui.row(
                        R.drawable.ic_keyboard,
                        getString(R.string.status),
                        getString(
                            when {
                                active -> R.string.status_active
                                enabled -> R.string.status_enabled
                                else -> R.string.status_not_enabled
                            },
                        ),
                    ) { imm?.showInputMethodPicker() },
                    // About block required by ict-baden/richtlinien 07.
                    ui.row(R.drawable.ic_lock, getString(R.string.about_privacy), getString(R.string.about_privacy_summary)) {
                        openLink(PRIVACY)
                    },
                    ui.row(R.drawable.ic_info, getString(R.string.about_licence), getString(R.string.about_licence_summary)) {
                        dialogs.message(getString(R.string.about_licence), asset("LICENSE"))
                    },
                    ui.row(R.drawable.ic_info, getString(R.string.about_third_party), getString(R.string.about_third_party_summary)) {
                        dialogs.message(getString(R.string.about_third_party), asset("THIRD-PARTY-NOTICES.txt"))
                    },
                    ui.row(R.drawable.ic_globe, getString(R.string.about_source), SOURCE) { openLink(SOURCE) },
                    ui.row(R.drawable.ic_info, getString(R.string.about_support), getString(R.string.about_support_summary)) {
                        openLink("$SOURCE/blob/main/SUPPORT.md")
                    },
                    ui.row(R.drawable.ic_globe, getString(R.string.about_website), WEBSITE) { openLink(WEBSITE) },
                    ui.row(R.drawable.ic_globe, getString(R.string.about_impressum), getString(R.string.about_impressum_summary)) { openLink(IMPRESSUM) },
                ),
            ),
        )
    }

    private fun percent(value: Int) = getString(R.string.value_percent, value)

    private fun millis(value: Int) = getString(R.string.value_ms, value)

    private fun keyDescription(key: ProgrammableKey) = when (key) {
        ProgrammableKey.ALPHA_P1 -> R.string.key_alpha_p1
        ProgrammableKey.NUMERIC_P1 -> R.string.key_numeric_p1
        ProgrammableKey.NUMERIC_P2 -> R.string.key_numeric_p2
        ProgrammableKey.NUMERIC_P3 -> R.string.key_numeric_p3
        ProgrammableKey.NUMERIC_P4 -> R.string.key_numeric_p4
    }

    private fun languageName(language: String): String {
        val name = AppLanguage.NAMES[language] ?: return getString(R.string.language_system)
        // Not yet proofread by native speakers (richtlinien/13-sprachen.md).
        return if (language in AppLanguage.MACHINE_TRANSLATED) getString(R.string.language_machine_translated, name) else name
    }

    /** Stored in `language` like every other setting; KeyboardApp applies it on every change. */
    private fun pickLanguage() {
        val options = listOf(Languages.AUTO) + Languages.SUPPORTED
        val current = options.indexOf(store.config.language).coerceAtLeast(0)
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setIcon(ui.icon(R.drawable.ic_globe))
            .setTitle(R.string.language)
            .setSingleChoiceItems(options.map(::languageName).toTypedArray(), current) { dialog, which ->
                dialog.dismiss()
                update { it.copy(language = options[which]) }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun asset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    /** Fixed https links only; locked-down devices often have no browser, then the address is shown. */
    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            dialogs.message(getString(R.string.no_browser), url)
        }
    }

    private fun swatch(config: KeyboardConfig) = ImageView(this).apply {
        setImageResource(R.drawable.bg_avatar)
        imageTintList = ColorStateList.valueOf(Palette.resolve(this@SettingsActivity, config.appearance).accent)
        layoutParams = LinearLayout.LayoutParams(ui.dp(28), ui.dp(28))
    }

    /** Android's list of keyboard languages for this keyboard (subtypes in res/xml/method.xml). */
    private fun openLanguageSettings() {
        val id = getSystemService(InputMethodManager::class.java)?.inputMethodList?.firstOrNull { it.packageName == packageName }?.id
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).apply { id?.let { putExtra(Settings.EXTRA_INPUT_METHOD_ID, it) } }
        runCatching { startActivity(intent) }.onFailure { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
    }

    /** „Automatic (QWERTZ)“: shows which layout the system language gives. */
    private fun alphabetLabel(alphabet: Alphabet): String =
        if (alphabet == Alphabet.AUTO) getString(R.string.automatic_with, Alphabet.AUTO.resolve().label) else alphabet.label

    private fun editIntentAction(config: KeyboardConfig) {
        val field = ui.textField(getString(R.string.broadcast_action), config.scanner.intentAction.orEmpty(), helper = getString(R.string.broadcast_action_helper))
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setIcon(ui.icon(R.drawable.ic_scan))
            .setTitle(R.string.trigger_broadcast)
            .setView(ui.form(field))
            .setPositiveButton(R.string.button_save) { _, _ ->
                val action = field.value.trim()
                if (action.isNotEmpty()) update { it.copy(scanner = it.scanner.copy(trigger = ScanTriggerType.INTENT, intentAction = action)) }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    private fun renderCameraSection(c: KeyboardConfig) {
        val cam = c.scanner.camera
        fun set(transform: (CameraSettings) -> CameraSettings) = update { it.copy(scanner = it.scanner.copy(camera = transform(it.scanner.camera))) }
        val timeouts = (listOf(30, 60, 120, 300, 0) + cam.timeoutSeconds).distinct()
        content.addView(ui.section(getString(R.string.section_camera)))
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_scan, getString(R.string.camera_symbologies), symbologyList(cam.symbologies)) {
                        dialogs.pickSymbologies(cam.symbologies) { types -> set { it.copy(symbologies = types) } }
                    },
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        getString(R.string.camera_aim),
                        listOf(
                            AimMode.FRAME to getString(R.string.camera_aim_frame),
                            AimMode.CENTER to getString(R.string.camera_aim_center),
                            AimMode.ANYWHERE to getString(R.string.camera_aim_anywhere),
                        ),
                        cam.aim,
                    ) { aim -> set { it.copy(aim = aim) } },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.camera_continuous), getString(R.string.camera_continuous_summary), cam.continuous) { on ->
                        set { it.copy(continuous = on) }
                    },
                    ui.row(R.drawable.ic_timer, getString(R.string.camera_duplicate_delay), getString(R.string.value_ms, cam.duplicateDelayMs), onClick = if (cam.continuous) ({
                        dialogs.editNumber(getString(R.string.camera_duplicate_delay), cam.duplicateDelayMs, 0, 10_000) { v -> set { it.copy(duplicateDelayMs = v) } }
                    }) else null),
                    ui.choiceRow(R.drawable.ic_check, getString(R.string.camera_confirm_reads), listOf(1, 2, 3).map { it to it.toString() }, cam.confirmReads) { n ->
                        set { it.copy(confirmReads = n) }
                    },
                    ui.row(R.drawable.ic_scan, getString(R.string.camera_length), lengthText(cam.minLength, cam.maxLength)) {
                        dialogs.editNumber(getString(R.string.camera_min_length), cam.minLength, 0, 9999, getString(R.string.zero_no_limit)) { min ->
                            dialogs.editNumber(getString(R.string.camera_max_length), cam.maxLength, 0, 9999, getString(R.string.zero_no_limit)) { max ->
                                set { it.copy(minLength = min, maxLength = if (max in 1 until min) min else max) }
                            }
                        }
                    },
                    ui.choiceRow(R.drawable.ic_scan, getString(R.string.camera_zoom_start), listOf(1f, 2f, 3f, 4f).map { it to String.format(Locale.ROOT, "%.0f×", it) }, listOf(1f, 2f, 3f, 4f).minByOrNull { kotlin.math.abs(it - cam.zoom) }!!) { z ->
                        set { it.copy(zoom = z) }
                    },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.camera_torch_start), null, cam.torch) { on -> set { it.copy(torch = on) } },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.camera_inverted), getString(R.string.camera_inverted_summary), cam.inverted) { on -> set { it.copy(inverted = on) } },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.camera_beep), null, cam.beep) { on -> set { it.copy(beep = on) } },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.camera_vibrate), null, cam.vibrate) { on -> set { it.copy(vibrate = on) } },
                    ui.choiceRow(R.drawable.ic_timer, getString(R.string.camera_timeout), timeouts.map { it to timeoutText(it) }, cam.timeoutSeconds) { t ->
                        set { it.copy(timeoutSeconds = t) }
                    },
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        getString(R.string.data_gs1),
                        listOf(Gs1Mode.HRI to getString(R.string.data_gs1_hri), Gs1Mode.RAW to getString(R.string.data_gs1_raw)),
                        c.scanner.data.gs1,
                    ) { mode -> update { it.copy(scanner = it.scanner.copy(data = it.scanner.data.copy(gs1 = mode))) } },
                ),
            ),
        )
    }

    private fun renderOcrSection(c: KeyboardConfig) {
        val ocr = c.scanner.ocr
        fun set(transform: (OcrSettings) -> OcrSettings) = update { it.copy(scanner = it.scanner.copy(ocr = transform(it.scanner.ocr))) }
        content.addView(ui.section(getString(R.string.section_ocr)))
        content.addView(
            ui.card(
                listOf(
                    ui.switchRow(R.drawable.ic_text, getString(R.string.ocr_enabled), getString(R.string.ocr_enabled_summary), ocr.enabled) { on -> set { it.copy(enabled = on) } },
                    ui.choiceRow(
                        R.drawable.ic_text,
                        getString(R.string.ocr_mode),
                        listOf(OcrMode.PICK to getString(R.string.ocr_mode_pick), OcrMode.PATTERN to getString(R.string.ocr_mode_pattern)),
                        ocr.mode,
                    ) { mode ->
                        if (mode == OcrMode.PATTERN && ocr.pattern == null) {
                            editOcrPattern(ocr) { pattern -> set { it.copy(mode = OcrMode.PATTERN, pattern = pattern) } }
                        } else {
                            set { it.copy(mode = mode) }
                        }
                    },
                    ui.row(R.drawable.ic_text, getString(R.string.ocr_pattern), ocr.pattern ?: getString(R.string.value_none)) {
                        editOcrPattern(ocr) { pattern -> set { it.copy(pattern = pattern, mode = if (pattern == null) OcrMode.PICK else it.mode) } }
                    },
                    ui.choiceRow(
                        R.drawable.ic_text,
                        getString(R.string.ocr_charset),
                        listOf(
                            OcrCharset.ANY to getString(R.string.ocr_charset_any),
                            OcrCharset.DIGITS to getString(R.string.ocr_charset_digits),
                            OcrCharset.ALNUM to getString(R.string.ocr_charset_alnum),
                            OcrCharset.UPPER to getString(R.string.ocr_charset_upper),
                        ),
                        ocr.charset,
                    ) { charset -> set { it.copy(charset = charset) } },
                    ui.row(R.drawable.ic_text, getString(R.string.camera_length), lengthText(ocr.minLength, ocr.maxLength)) {
                        dialogs.editNumber(getString(R.string.camera_min_length), ocr.minLength, 0, 999, getString(R.string.zero_no_limit)) { min ->
                            dialogs.editNumber(getString(R.string.camera_max_length), ocr.maxLength, 0, 999, getString(R.string.zero_no_limit)) { max ->
                                set { it.copy(minLength = min, maxLength = if (max in 1 until min) min else max) }
                            }
                        }
                    },
                    ui.choiceRow(R.drawable.ic_check, getString(R.string.ocr_confirm_reads), (1..5).map { it to it.toString() }, ocr.confirmReads) { n ->
                        set { it.copy(confirmReads = n) }
                    },
                    ui.switchRow(R.drawable.ic_text, getString(R.string.ocr_start_in_text), null, ocr.startInOcr) { on -> set { it.copy(startInOcr = on) } },
                ),
            ),
        )
    }

    /** Regex for OCR pattern mode; an empty field removes it. */
    private fun editOcrPattern(ocr: OcrSettings, onSave: (String?) -> Unit) {
        dialogs.editText(
            getString(R.string.ocr_pattern),
            ocr.pattern,
            getString(R.string.ocr_pattern_helper),
            macro = false,
            validate = { text -> runCatching { Regex(text) }.exceptionOrNull()?.let { getString(R.string.ocr_pattern_invalid) } },
        ) { value -> onSave(value?.ifEmpty { null }) }
    }

    private fun renderNfcSection(c: KeyboardConfig) {
        val nfc = c.scanner.nfc
        fun set(transform: (NfcSettings) -> NfcSettings) = update { it.copy(scanner = it.scanner.copy(nfc = transform(it.scanner.nfc))) }
        val adapter = NfcAdapter.getDefaultAdapter(this)
        val state = when {
            adapter == null -> getString(R.string.nfc_state_missing)
            adapter.isEnabled -> getString(R.string.nfc_state_on)
            else -> getString(R.string.nfc_state_off)
        }
        content.addView(ui.section(getString(R.string.section_nfc)))
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_nfc, getString(R.string.nfc_state), state, onClick = if (adapter != null && !adapter.isEnabled) ({
                        runCatching { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
                    }) else null),
                    ui.switchRow(R.drawable.ic_nfc, getString(R.string.nfc_enabled), getString(R.string.nfc_enabled_summary), nfc.enabled) { on -> set { it.copy(enabled = on) } },
                    ui.choiceRow(
                        R.drawable.ic_nfc,
                        getString(R.string.nfc_read),
                        listOf(
                            NfcRead.NDEF_OR_UID to getString(R.string.nfc_read_ndef_or_uid),
                            NfcRead.UID to getString(R.string.nfc_read_uid),
                            NfcRead.NDEF to getString(R.string.nfc_read_ndef),
                        ),
                        nfc.read,
                    ) { read -> set { it.copy(read = read) } },
                    ui.choiceRow(
                        R.drawable.ic_nfc,
                        getString(R.string.nfc_uid_format),
                        listOf(UidFormat.HEX to getString(R.string.nfc_uid_hex), UidFormat.DECIMAL to getString(R.string.nfc_uid_decimal)),
                        nfc.uidFormat,
                    ) { format -> set { it.copy(uidFormat = format) } },
                    ui.switchRow(R.drawable.ic_nfc, getString(R.string.nfc_uid_reversed), getString(R.string.nfc_uid_reversed_summary), nfc.uidReversed) { on -> set { it.copy(uidReversed = on) } },
                    ui.row(R.drawable.ic_nfc, getString(R.string.nfc_uid_separator), nfc.uidSeparator.ifEmpty { getString(R.string.value_none) }) {
                        dialogs.editText(
                            getString(R.string.nfc_uid_separator),
                            nfc.uidSeparator,
                            null,
                            macro = false,
                            validate = { text -> if (text.length > 4) getString(R.string.nfc_uid_separator_too_long) else null },
                        ) { v -> set { it.copy(uidSeparator = v.orEmpty()) } }
                    },
                    ui.choiceRow(
                        R.drawable.ic_nfc,
                        getString(R.string.nfc_ndef_records),
                        listOf(NdefRecords.FIRST to getString(R.string.nfc_ndef_first), NdefRecords.ALL_LINES to getString(R.string.nfc_ndef_all)),
                        nfc.ndefRecord,
                    ) { mode -> set { it.copy(ndefRecord = mode) } },
                    ui.switchRow(R.drawable.ic_nfc, getString(R.string.camera_beep), null, nfc.beep) { on -> set { it.copy(beep = on) } },
                    ui.switchRow(R.drawable.ic_nfc, getString(R.string.camera_vibrate), null, nfc.vibrate) { on -> set { it.copy(vibrate = on) } },
                ),
            ),
        )
    }

    private fun renderDataSection(c: KeyboardConfig) {
        val data = c.scanner.data
        fun set(transform: (DataSettings) -> DataSettings) = update { it.copy(scanner = it.scanner.copy(data = transform(it.scanner.data))) }
        val asBefore = getString(R.string.data_suffix_default)
        content.addView(ui.section(getString(R.string.section_scan_data)))
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_key, getString(R.string.data_prefix), data.prefix.ifEmpty { getString(R.string.value_none) }) {
                        dialogs.editText(getString(R.string.data_prefix), data.prefix, getString(R.string.macro_helper), macro = true) { v -> set { it.copy(prefix = v.orEmpty()) } }
                    },
                    ui.row(R.drawable.ic_key, getString(R.string.data_suffix), data.suffix?.ifEmpty { getString(R.string.value_none) } ?: asBefore) {
                        dialogs.editText(getString(R.string.data_suffix), data.suffix, getString(R.string.macro_helper), macro = true, defaultLabel = getString(R.string.data_suffix_reset)) { v ->
                            set { it.copy(suffix = v) }
                        }
                    },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.data_trim), getString(R.string.data_trim_summary), data.trim) { on -> set { it.copy(trim = on) } },
                    ui.switchRow(R.drawable.ic_scan, getString(R.string.data_remove_spaces), null, data.removeSpaces) { on -> set { it.copy(removeSpaces = on) } },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        getString(R.string.data_case),
                        listOf(
                            TextCase.NONE to getString(R.string.data_case_none),
                            TextCase.UPPER to getString(R.string.data_case_upper),
                            TextCase.LOWER to getString(R.string.data_case_lower),
                        ),
                        data.case,
                    ) { case -> set { it.copy(case = case) } },
                    ui.row(R.drawable.ic_scan, getString(R.string.data_strip), getString(R.string.data_strip_value, data.stripLeading, data.stripTrailing)) {
                        dialogs.editNumber(getString(R.string.data_strip_leading), data.stripLeading, 0, 999) { lead ->
                            dialogs.editNumber(getString(R.string.data_strip_trailing), data.stripTrailing, 0, 999) { trail ->
                                set { it.copy(stripLeading = lead, stripTrailing = trail) }
                            }
                        }
                    },
                    ui.row(R.drawable.ic_scan, getString(R.string.data_gs_replacement), data.gsReplacement?.let { if (it.isEmpty()) getString(R.string.value_removed) else "\"$it\"" } ?: getString(R.string.value_keep)) {
                        dialogs.editText(getString(R.string.data_gs_replacement), data.gsReplacement, getString(R.string.data_gs_replacement_helper), macro = true, defaultLabel = getString(R.string.value_keep)) { v ->
                            set { it.copy(gsReplacement = v) }
                        }
                    },
                    ui.row(R.drawable.ic_info, getString(R.string.data_replace), resources.getQuantityString(R.plurals.json_only_count, data.replace.size, data.replace.size), onClick = null),
                    ui.row(R.drawable.ic_info, getString(R.string.data_rules), resources.getQuantityString(R.plurals.json_only_count, data.rules.size, data.rules.size), onClick = null),
                ),
            ),
        )
    }

    private fun symbologyList(types: Set<Symbology>): String =
        if (types.isEmpty()) getString(R.string.symbologies_all) else types.sortedBy { it.ordinal }.joinToString { it.label }

    private fun lengthText(min: Int, max: Int): String = when {
        min == 0 && max == 0 -> getString(R.string.length_any)
        max == 0 -> getString(R.string.length_min, min)
        min == 0 -> getString(R.string.length_max, max)
        else -> getString(R.string.length_range, min, max)
    }

    private fun timeoutText(seconds: Int): String = when {
        seconds == 0 -> getString(R.string.timeout_never)
        seconds % 60 == 0 -> getString(R.string.value_min, seconds / 60)
        else -> getString(R.string.value_s, seconds)
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }.onFailure { dialogs.toast(dialogs.withCode(getString(R.string.file_unreadable, it.message.orEmpty()), ErrorCodes.FILE_UNREADABLE)) }.getOrNull()

    private fun confirmImport(text: String) {
        val imported = try {
            ConfigJson.parse(text)
        } catch (e: ConfigException) {
            dialogs.message(getString(R.string.import_failed), dialogs.withCode(e.message.orEmpty(), ErrorCodes.IMPORT_INVALID))
            return
        }
        dialogs.confirm(getString(R.string.import_confirm_title), getString(R.string.import_confirm_message), getString(R.string.button_import)) {
            store.import(imported)
            dialogs.toast(getString(R.string.config_imported))
        }
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val SOURCE = "https://github.com/ict-baden/industrialkeyboard"
        const val WEBSITE = "https://ict-baden.de/produkte/industrial-keyboard/"
        const val IMPRESSUM = "https://ict-baden.de/impressum/"
        const val PRIVACY = "https://ict-baden.de/datenschutz/industrialkeyboard/"
    }
}

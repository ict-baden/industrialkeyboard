package de.ictbaden.industrialkeyboard.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import de.ictbaden.industrialkeyboard.BuildConfig
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.ConfigException
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Navigation
import de.ictbaden.industrialkeyboard.config.NumericOrder
import de.ictbaden.industrialkeyboard.config.ProgrammableKey
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.config.ScanSuffix
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.ThemeMode
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

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(ConfigJson.write(store.config)) }
        }.onSuccess { dialogs.toast("Configuration exported") }.onFailure { dialogs.toast("Export failed: ${it.message}") }
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
            setNavigationContentDescription("Back")
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
        if (hash != null && !unlocked) {
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
        Provisioning.applyAll(this)
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

        content.addView(ui.section("Look"))
        content.addView(
            ui.card(
                listOf(
                    ui.choiceRow(
                        R.drawable.ic_palette,
                        "Theme",
                        listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.CONTRAST to "High contrast"),
                        c.appearance.theme,
                    ) { theme -> update { it.copy(appearance = it.appearance.copy(theme = theme)) } },
                    ui.row(R.drawable.ic_palette, "Accent color", c.appearance.accentColor ?: "Theme default", trailing = swatch(c)) {
                        dialogs.pickColor("Accent color", c.appearance.accentColor) { color ->
                            update { it.copy(appearance = it.appearance.copy(accentColor = color)) }
                        }
                    },
                    ui.sliderRow(R.drawable.ic_keyboard, "Key height", 70, 160, 5, c.appearance.keyHeight, format = { "$it %" }) { value ->
                        update { it.copy(appearance = it.appearance.copy(keyHeight = value)) }
                    },
                    ui.sliderRow(R.drawable.ic_keyboard, "Keyboard width", 50, 100, 5, c.appearance.width, format = { "$it %" }) { value ->
                        update { it.copy(appearance = it.appearance.copy(width = value)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, "Number row", "Digits above the letters", c.appearance.numberRow) { on ->
                        update { it.copy(appearance = it.appearance.copy(numberRow = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, "Function key row", "F1–F12 above the letters", c.appearance.functionRow) { on ->
                        update { it.copy(appearance = it.appearance.copy(functionRow = on)) }
                    },
                    ui.switchRow(R.drawable.ic_info, "Key hints", "Small digits for long press on the top row", c.appearance.keyHints) { on ->
                        update { it.copy(appearance = it.appearance.copy(keyHints = on)) }
                    },
                ),
            ),
        )

        content.addView(ui.section("Layout"))
        content.addView(
            ui.card(
                listOf(
                    ui.choiceRow(R.drawable.ic_keyboard, "Letters", Alphabet.entries.map { it to it.label }, c.layout.alphabet) { alphabet ->
                        update { it.copy(layout = it.layout.copy(alphabet = alphabet)) }
                    },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        "Navigation",
                        listOf(Navigation.TABS to "Tab bar", Navigation.KEYS to "Classic ?123 key"),
                        c.layout.navigation,
                    ) { navigation -> update { it.copy(layout = it.layout.copy(navigation = navigation)) } },
                    ui.choiceRow(
                        R.drawable.ic_keyboard,
                        "Numeric pad",
                        listOf(NumericOrder.PHONE to "1-2-3 on top", NumericOrder.CALCULATOR to "7-8-9 on top"),
                        c.layout.numericOrder,
                    ) { order -> update { it.copy(layout = it.layout.copy(numericOrder = order)) } },
                    ui.multiChoiceRow(
                        R.drawable.ic_keyboard,
                        "Tabs",
                        "Shown in the tab bar",
                        listOf(
                            Triple(Tabs.ALPHA, "ABC", c.tabs.alpha),
                            Triple(Tabs.NUMERIC, "123", c.tabs.numeric),
                            Triple(Tabs.SYMBOLS, "#+=", c.tabs.symbols),
                            Triple(Tabs.FUNCTION, "Fn", c.tabs.function),
                            Triple(Tabs.SCAN, "Scan", c.tabs.scan),
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
                        "Start tab for text fields",
                        listOf(Tabs.AUTO to "Automatic", Tabs.ALPHA to "ABC", Tabs.NUMERIC to "123", Tabs.SYMBOLS to "#+=", Tabs.FUNCTION to "Fn", Tabs.SCAN to "Scan") +
                            c.customLayouts.map { it.id to it.label },
                        c.tabs.preferred,
                    ) { tab -> update { it.copy(tabs = it.tabs.copy(preferred = tab)) } },
                ),
            ),
        )

        content.addView(ui.section("Programmable keys"))
        content.addView(
            ui.card(
                ProgrammableKey.entries.map { key ->
                    val value = c.programmable(key)
                    ui.row(R.drawable.ic_key, key.id.uppercase(), "$value  ·  ${key.description}") {
                        dialogs.editMacro(key.description, value, key.default) { macro ->
                            update { it.copy(keys = it.keys.copy(programmable = it.keys.programmable + (key.id to (macro ?: key.default)))) }
                        }
                    }
                },
            ),
        )

        content.addView(ui.section("Typing"))
        content.addView(
            ui.card(
                listOf(
                    ui.switchRow(R.drawable.ic_keyboard, "Auto-capitalization", "Capital letter at the start of a sentence (if the field asks for it)", c.typing.autoCapitalize) { on ->
                        update { it.copy(typing = it.typing.copy(autoCapitalize = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, "Double-space period", "Two spaces insert a period", c.typing.doubleSpacePeriod) { on ->
                        update { it.copy(typing = it.typing.copy(doubleSpacePeriod = on)) }
                    },
                    ui.sliderRow(R.drawable.ic_timer, "Long-press delay", 200, 1500, 50, c.typing.longPressDelay, format = { "$it ms" }) { value ->
                        update { it.copy(typing = it.typing.copy(longPressDelay = value)) }
                    },
                ),
            ),
        )

        content.addView(ui.section("Feedback"))
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.switchRow(R.drawable.ic_keyboard, "Key popup", "Enlarged character above the pressed key", c.feedback.keyPopup) { on ->
                        update { it.copy(feedback = it.feedback.copy(keyPopup = on)) }
                    },
                    ui.switchRow(R.drawable.ic_keyboard, "Vibration", null, c.feedback.vibrate) { on ->
                        update { it.copy(feedback = it.feedback.copy(vibrate = on)) }
                    },
                    if (c.feedback.vibrate) {
                        ui.sliderRow(R.drawable.ic_timer, "Vibration length", 5, 100, 5, c.feedback.vibrationMs ?: 20, format = { "$it ms" }) { value ->
                            update { it.copy(feedback = it.feedback.copy(vibrationMs = value)) }
                        }
                    } else {
                        null
                    },
                    ui.switchRow(R.drawable.ic_keyboard, "Sound", null, c.feedback.sound) { on ->
                        update { it.copy(feedback = it.feedback.copy(sound = on)) }
                    },
                    if (c.feedback.sound) {
                        ui.sliderRow(R.drawable.ic_keyboard, "Volume", 0, 100, 5, c.feedback.soundVolume ?: 50, format = { "$it %" }) { value ->
                            update { it.copy(feedback = it.feedback.copy(soundVolume = value)) }
                        }
                    } else {
                        null
                    },
                ),
            ),
        )

        content.addView(ui.section("Scanner"))
        scanner.settings = c.scanner
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        "Scan trigger",
                        listOf(
                            ScanTriggerType.AUTO to "Automatic",
                            ScanTriggerType.DATAWEDGE to "DataWedge",
                            ScanTriggerType.HONEYWELL to "Honeywell",
                            ScanTriggerType.INTENT to "Intent",
                            ScanTriggerType.NONE to "Off",
                        ),
                        c.scanner.trigger,
                    ) { trigger ->
                        if (trigger == ScanTriggerType.INTENT && c.scanner.intentAction == null) {
                            editIntentAction(c)
                        } else {
                            update { it.copy(scanner = it.scanner.copy(trigger = trigger)) }
                        }
                    },
                    if (c.scanner.trigger == ScanTriggerType.INTENT) {
                        ui.row(R.drawable.ic_scan, "Trigger broadcast", c.scanner.intentAction) { editIntentAction(c) }
                    } else {
                        null
                    },
                    ui.row(R.drawable.ic_scan, "Scanner in use", scanner.name ?: "None found", onClick = null),
                    ui.switchRow(R.drawable.ic_scan, "Scan key", "Next to the space bar on the letter keys", c.scanner.scanKey) { on ->
                        update { it.copy(scanner = it.scanner.copy(scanKey = on)) }
                    },
                    ui.switchRow(
                        R.drawable.ic_scan,
                        "Type scans from broadcasts",
                        c.scanner.input.action,
                        c.scanner.input.enabled,
                    ) { on -> update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(enabled = on))) } },
                    ui.choiceRow(
                        R.drawable.ic_scan,
                        "After a scan",
                        listOf(ScanSuffix.NONE to "Nothing", ScanSuffix.ENTER to "Enter", ScanSuffix.TAB to "Tab"),
                        c.scanner.input.suffix,
                        enabled = c.scanner.input.enabled,
                    ) { suffix -> update { it.copy(scanner = it.scanner.copy(input = it.scanner.input.copy(suffix = suffix))) } },
                ),
            ),
        )

        content.addView(ui.section("Configuration"))
        content.addView(
            ui.card(
                listOfNotNull(
                    ui.row(R.drawable.ic_upload, "Import from file", "Load a JSON configuration") {
                        importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    },
                    ui.row(R.drawable.ic_download, "Export to file", "Save the current configuration") {
                        exportFile.launch(Provisioning.CONFIG_FILE_NAME)
                    },
                    ui.row(R.drawable.ic_restart, "Reset to defaults", "Keeps the admin password") {
                        dialogs.confirm("Reset all settings?", "Layouts, look, keys and scanner settings go back to their defaults.", "Reset") {
                            update { KeyboardConfig(adminPasswordHash = it.adminPasswordHash) }
                        }
                    },
                    ui.row(R.drawable.ic_key, "Admin password", if (c.adminPasswordHash == null) "Not set" else "Set – protects these settings") {
                        dialogs.changePassword { hash -> update { it.copy(adminPasswordHash = hash) } }
                    },
                    Provisioning.lastError(this)?.let { ui.row(R.drawable.ic_info, "Last provisioning error", it, onClick = null) },
                    ui.row(
                        R.drawable.ic_download,
                        "Configuration file for EMM",
                        "Android/data/$packageName/files/${Provisioning.CONFIG_FILE_NAME}",
                        onClick = null,
                    ),
                ),
            ),
        )

        content.addView(ui.section("Info"))
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/') == packageName
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_info, "Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · ${BuildConfig.COMMIT}", onClick = null),
                    ui.row(
                        R.drawable.ic_keyboard,
                        "Status",
                        when {
                            active -> "Active keyboard"
                            enabled -> "Enabled, not selected"
                            else -> "Not enabled"
                        },
                    ) { imm?.showInputMethodPicker() },
                    // About block required by ict-baden/richtlinien 07.
                    ui.row(R.drawable.ic_lock, "Data and privacy", "This software sends no data to ICT-Baden. It has no internet permission.") {
                        openLink("$SOURCE/blob/main/docs/data-and-privacy.md")
                    },
                    ui.row(R.drawable.ic_info, "Licence", "MIT · © 2026 ICT-Baden") { dialogs.message("Licence", asset("LICENSE")) },
                    ui.row(R.drawable.ic_info, "Third-party licences", "AndroidX, Material Components, Kotlin") {
                        dialogs.message("Third-party licences", asset("THIRD-PARTY-NOTICES.txt"))
                    },
                    ui.row(R.drawable.ic_globe, "Source code", SOURCE) { openLink(SOURCE) },
                    ui.row(R.drawable.ic_info, "Help & support", "Free via GitHub, guaranteed with a support contract") {
                        openLink("$SOURCE/blob/main/SUPPORT.md")
                    },
                    ui.row(R.drawable.ic_globe, "Website", WEBSITE) { openLink(WEBSITE) },
                    ui.row(R.drawable.ic_globe, "Impressum", "Legal notice of ICT-Baden") { openLink(IMPRESSUM) },
                ),
            ),
        )
    }

    private fun asset(name: String): String = assets.open(name).bufferedReader().use { it.readText() }

    /** Fixed https links only; locked-down devices often have no browser, then the address is shown. */
    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            dialogs.message("No browser", url)
        }
    }

    private fun swatch(config: KeyboardConfig) = ImageView(this).apply {
        setImageResource(R.drawable.bg_avatar)
        imageTintList = ColorStateList.valueOf(Palette.resolve(this@SettingsActivity, config.appearance).accent)
        layoutParams = LinearLayout.LayoutParams(ui.dp(28), ui.dp(28))
    }

    private fun editIntentAction(config: KeyboardConfig) {
        val field = ui.textField("Broadcast action", config.scanner.intentAction.orEmpty(), helper = "Sent to start a scan, e.g. com.example.scanner.START")
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setIcon(ui.icon(R.drawable.ic_scan))
            .setTitle("Trigger broadcast")
            .setView(ui.form(field))
            .setPositiveButton("Save") { _, _ ->
                val action = field.value.trim()
                if (action.isNotEmpty()) update { it.copy(scanner = it.scanner.copy(trigger = ScanTriggerType.INTENT, intentAction = action)) }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun readText(uri: Uri): String? = runCatching {
        contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }.onFailure { dialogs.toast("Could not read the file: ${it.message}") }.getOrNull()

    private fun confirmImport(text: String) {
        val imported = try {
            ConfigJson.parse(text)
        } catch (e: ConfigException) {
            dialogs.message("Import failed", e.message.orEmpty())
            return
        }
        dialogs.confirm("Import configuration?", "The current settings will be replaced.", "Import") {
            store.import(imported)
            dialogs.toast("Configuration imported")
        }
    }

    private companion object {
        const val KEY_UNLOCKED = "unlocked"
        const val SOURCE = "https://github.com/ict-baden/industrialkeyboard"
        const val WEBSITE = "https://ict-baden.de"
        const val IMPRESSUM = "https://ict-baden.de/impressum"
    }
}

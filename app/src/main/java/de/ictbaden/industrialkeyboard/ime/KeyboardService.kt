package de.ictbaden.industrialkeyboard.ime

import android.content.BroadcastReceiver
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.api.KeyboardApi
import de.ictbaden.industrialkeyboard.api.KeyboardBus
import de.ictbaden.industrialkeyboard.app.CameraPermissionActivity
import de.ictbaden.industrialkeyboard.app.ErrorCodes
import de.ictbaden.industrialkeyboard.app.MicrophonePermissionActivity
import de.ictbaden.industrialkeyboard.app.SettingsActivity
import de.ictbaden.industrialkeyboard.app.SnippetsActivity
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.EffectiveConfig
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.OneHanded
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.config.QuickToggle
import de.ictbaden.industrialkeyboard.config.QuickToggleStore
import de.ictbaden.industrialkeyboard.config.QuickToggles
import de.ictbaden.industrialkeyboard.config.ScanInputSettings
import de.ictbaden.industrialkeyboard.config.ScanSuffix
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.Snippet
import de.ictbaden.industrialkeyboard.config.Snippets
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.UserSnippets
import de.ictbaden.industrialkeyboard.config.VoiceInput
import de.ictbaden.industrialkeyboard.layout.EditAction
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyChords
import de.ictbaden.industrialkeyboard.layout.KeyCommand
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.layout.MacroStep
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState
import de.ictbaden.industrialkeyboard.layout.TextEditing
import de.ictbaden.industrialkeyboard.scanner.CameraScanner
import de.ictbaden.industrialkeyboard.scanner.HardwareScanner
import de.ictbaden.industrialkeyboard.scanner.ScanFormatter
import de.ictbaden.industrialkeyboard.scanner.ScanTrigger
import de.ictbaden.industrialkeyboard.scanner.Symbologies
import de.ictbaden.industrialkeyboard.suggest.Dictionaries
import de.ictbaden.industrialkeyboard.suggest.NextWordContext
import de.ictbaden.industrialkeyboard.suggest.Suggester
import de.ictbaden.industrialkeyboard.suggest.Suggestion
import de.ictbaden.industrialkeyboard.suggest.Suggestions
import de.ictbaden.industrialkeyboard.suggest.WordList
import de.ictbaden.industrialkeyboard.ui.ClipItem
import de.ictbaden.industrialkeyboard.ui.FeaturesPanelView
import de.ictbaden.industrialkeyboard.ui.Feedback
import de.ictbaden.industrialkeyboard.ui.KeyDescriber
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.KeyboardView
import de.ictbaden.industrialkeyboard.ui.PanelSnippet
import de.ictbaden.industrialkeyboard.ui.ScanPadView
import de.ictbaden.industrialkeyboard.ui.ShiftState
import java.util.Locale

/** The input method: tabs, layouts, typing logic, scanning and the intent API. */
class KeyboardService : InputMethodService(), KeyboardView.Listener, ScanPadView.Listener {

    private lateinit var store: ConfigStore
    private lateinit var feedback: Feedback
    private lateinit var scanner: ScanTrigger

    /** Quick toggles switched on this device; overrides on top of the configuration, never in it. */
    private lateinit var toggles: QuickToggleStore

    /** The scanner of the configuration below the quick toggles: whether the scanner toggle can be on. */
    private lateinit var scannerBelowToggles: ScanTrigger

    /** As stored (MDM, file, import, settings): the base that every recomputation starts from. */
    private var storedConfig = KeyboardConfig()

    /**
     * Effective: [storedConfig] with the app profile and quick toggles ([EffectiveConfig]), read
     * everywhere in the keyboard. Never write it to the store, never pass it to [applyConfig].
     */
    private var config = KeyboardConfig()

    /** [storedConfig] with the layers below the quick toggles: what the toggles start from. */
    private var belowToggles = KeyboardConfig()

    /** A scanner exists without the quick toggles (the scanner toggle is shown and can be on). */
    private var scanAvailableBase = false

    /**
     * Package of the last field and index of its app profile (-1 = none), see [AppProfiles.logKey];
     * only for the log line, written when either changes (once per app, never per key).
     */
    private var profileLogKey: Pair<String?, Int> = AppProfiles.logKey(null, null)
    private var root: KeyboardRootView? = null

    private var field = Field(de.ictbaden.industrialkeyboard.layout.AlphaVariant.TEXT, null)
    private var page: Page = Page.Alpha(field.alpha)
    private var shift = ShiftState.OFF
    /** Ctrl, Win and Alt that are armed (ONCE, for the next key) or locked; absent = off. */
    private val modifiers = LinkedHashMap<ModifierKey, ShiftState>()
    private var autoShifted = false
    private var lastShiftTap = 0L
    private var autoReleasedByTap = false
    private var lastSpace = 0L
    private var scanning = false
    /** Action and permission the scan receiver is registered with; `null` = not registered. */
    private var scanRegistration: Pair<String, String?>? = null
    private val location = IntArray(2)
    private val handler = Handler(Looper.getMainLooper())
    private val stopScanAnimation = Runnable { setScanning(false) }
    private var camera: CameraScanner? = null

    // Word suggestions: only the word being typed is kept, in memory, until it ends.
    private lateinit var dictionaries: Dictionaries
    private var suggester: Suggester? = null
    private var suggesterFor: Pair<WordList, List<String>>? = null
    private val word = StringBuilder()
    private var suggestions = Suggestions.NONE
    /** Edits of our own that onUpdateSelection still has to report; any other move ends the word. */
    private var ownEdits = 0
    /** Last autocorrection (typed, replacement + space): Backspace right after it undoes it. */
    private var autocorrected: Pair<String, String>? = null
    private var timedLookup = false
    private var emojiOpen = false
    private var cameraCount = 0
    private var tone: ToneGenerator? = null
    /** The camera does not run unattended: it closes after a while without a code. */
    private val closeCameraLater = Runnable { closeCamera() }
    /** The scan engine answered: recompute from the stored configuration (not from the effective one). */
    private val engineListener: () -> Unit = { applyConfig(storedConfig) }

    private val configListener: (KeyboardConfig) -> Unit = { applyConfig(it) }

    /** A quick toggle changed (⊞, settings, macro): recompute from the stored configuration. */
    private val togglesListener: (Map<QuickToggle, Boolean>) -> Unit = {
        useEffective()
        refreshFeatures()
        render()
    }

    /** The user's own snippets (entered in the app's snippet screen); read from memory, never per key from disk. */
    private val userSnippets by lazy { UserSnippets.get(this) }

    /** Own snippets changed in the app: the open panel and a shortcut on offer follow at once. */
    private val userSnippetsListener: (List<Snippet>) -> Unit = {
        if (updateSnippetMatch() || toolPanel == Tabs.TOOL_SNIPPETS) render()
    }

    /** The snippet whose shortcut is the word being typed; in memory only, gone with the word. */
    private var snippetMatch: Snippet? = null

    // Screen readers: both states are kept up to date by listeners, never asked for per key.
    private var accessibility: AccessibilityManager? = null
    private var touchExploration = false
    private val touchExplorationListener = AccessibilityManager.TouchExplorationStateChangeListener { on ->
        touchExploration = on
        render()
    }

    /** Headphones, a headset or a hearing aid are connected (password keys may be spoken). */
    private var headset = false
    private var audio: AudioManager? = null
    private val audioDevices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = updateHeadset()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = updateHeadset()
    }

    /** Spoken names of pages for announcements. */
    private val describer by lazy { KeyDescriber(this) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                // Through the bus: while the keyboard is hidden the request waits for the next field.
                KeyboardApi.ACTION_SHOW_TAB -> intent.getStringExtra(KeyboardApi.EXTRA_TAB)?.trim()?.takeIf { it.isNotEmpty() }?.let(KeyboardBus::requestTab)
                Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED -> Provisioning.applyAllAsync(context)
            }
        }
    }

    /** Barcodes typed into the field: registered only while enabled, with the configured sender permission. */
    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) sentFromPackage else null
            if (intent.action == config.scanner.input.action) onScanInput(intent, sender)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = ConfigStore.get(this)
        feedback = Feedback(this)
        scanner = ScanTrigger(this)
        scannerBelowToggles = ScanTrigger(this)
        toggles = QuickToggleStore.get(this)
        dictionaries = Dictionaries(this)
        HardwareScanner.addListener(engineListener)
        HardwareScanner.detect(this)
        // Exported: other apps request tabs; the restrictions broadcast is sent by the system only.
        val filter = IntentFilter().apply {
            addAction(KeyboardApi.ACTION_SHOW_TAB)
            addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        applyConfig(store.config)
        store.addListener(configListener)
        toggles.addListener(togglesListener)
        userSnippets.addListener(userSnippetsListener)
        KeyboardBus.onShowTab = { tab -> root?.isShown == true && showTab(tab) }
        KeyboardBus.onNfc = ::typeNfc
        accessibility = getSystemService(AccessibilityManager::class.java)?.also { manager ->
            touchExploration = manager.isTouchExplorationEnabled
            manager.addTouchExplorationStateChangeListener(touchExplorationListener)
        }
        // The callback reports the connected devices once right away, then each change.
        audio = getSystemService(AudioManager::class.java)?.also { it.registerAudioDeviceCallback(audioDevices, handler) }
    }

    private fun updateHeadset() {
        val now = PasswordSpeech.headsetConnected(audio)
        if (now == headset) return
        headset = now
        if (FieldPolicy.isPassword(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)) render()
    }

    override fun onDestroy() {
        watchClipboard(false)
        clipboard.clear()
        store.removeListener(configListener)
        toggles.removeListener(togglesListener)
        userSnippets.removeListener(userSnippetsListener)
        KeyboardBus.onShowTab = null
        KeyboardBus.onNfc = null
        KeyboardBus.keyboardShown = false
        accessibility?.removeTouchExplorationStateChangeListener(touchExplorationListener)
        audio?.unregisterAudioDeviceCallback(audioDevices)
        HardwareScanner.removeListener(engineListener)
        camera?.destroy()
        camera = null
        if (voiceOpen) dictation.stop()
        dictionaries.close()
        tone?.release()
        tone = null
        runCatching { unregisterReceiver(receiver) }
        unregisterScanReceiver()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** A new stored configuration (store, scan engine detection): recompute the effective one and draw. */
    private fun applyConfig(stored: KeyboardConfig) {
        storedConfig = stored
        useEffective(configChanged = true)
        // Snippets switched off (or nothing left to show) while the panel is open: back to the keys.
        if (toolPanel == Tabs.TOOL_SNIPPETS && !Snippets.reachable(config)) toolPanel = null
        updateSnippetMatch()
        refreshFeatures()
        render()
    }

    /**
     * Recomputes [config] from [storedConfig] and applies what depends on it (feedback, scanner, scan
     * receiver, a page that no longer exists). Runs when a field starts and when the configuration
     * changes, never per key. Returns true when the effective configuration changed.
     *
     * [info] is the focused field: its package name, input type and hint pick the app profile
     * (read here, never kept or logged beyond the package name).
     */
    private fun useEffective(configChanged: Boolean = false, info: EditorInfo? = currentInputEditorInfo): Boolean {
        val hit = AppProfiles.match(storedConfig.appProfiles, info?.packageName, info?.inputType ?: InputType.TYPE_NULL, info?.hintText)
        val logKey = AppProfiles.logKey(info?.packageName, hit)
        if (logKey != profileLogKey) {
            // Never the hint: it is the app's text, and may name what is typed.
            Log.i(TAG, AppProfiles.logLine(info?.packageName, hit))
            profileLogKey = logKey
        }
        val profile = AppProfiles.layer(hit?.profile)
        val below = EffectiveConfig.of(storedConfig, profile)
        // On a configuration change the trigger type is resolved again (package queries); on a new
        // field only when the trigger itself differs – a profile's scan suffix keeps it.
        if (configChanged || scannerBelowToggles.settings != below.scanner) scannerBelowToggles.settings = below.scanner
        if (configChanged) scannerBelowToggles.refresh()
        belowToggles = below
        scanAvailableBase = scannerBelowToggles.available
        val effective = EffectiveConfig.of(storedConfig, profile, QuickToggles.layer(storedConfig, toggles.overrides, scanAvailableBase))
        val changed = effective != config
        config = effective
        feedback.settings = effective.feedback
        if (configChanged || scanner.settings != effective.scanner) {
            // Formatting (a profile's scan suffix) is read from [config] when the code arrives: no reason to stop.
            if (scanning && scanner.settings.copy(data = effective.scanner.data) != effective.scanner) {
                // Stopped with the old trigger, before it is gone (scanner toggle off).
                scanner.stop()
                handler.removeCallbacks(stopScanAnimation)
                scanning = false
            }
            scanner.settings = effective.scanner
            if (configChanged) scanner.refresh()
        }
        if (camera?.isOpen == true && scanner.type != ScanTriggerType.CAMERA) closeCamera()
        val input = effective.scanner.input.takeIf { it.enabled }
        if (scanRegistration != input?.let { it.action to it.permission }) registerScanReceiver(input)
        if (FieldPolicy.pageForTab(page.tab, field, effective, scanner.available) == null) page = FieldPolicy.startPage(field, effective, scanner.available)
        return changed
    }

    private fun registerScanReceiver(input: ScanInputSettings?) {
        unregisterScanReceiver()
        input ?: return
        // Exported, since scanner apps send it – but Android delivers only from apps holding the permission, if set.
        ContextCompat.registerReceiver(this, scanReceiver, IntentFilter(input.action), input.permission, null, ContextCompat.RECEIVER_EXPORTED)
        scanRegistration = input.action to input.permission
    }

    private fun unregisterScanReceiver() {
        if (scanRegistration == null) return
        runCatching { unregisterReceiver(scanReceiver) }
        scanRegistration = null
    }

    // region lifecycle

    override fun onCreateInputView(): View {
        val view = KeyboardRootView(this)
        view.keyboard.listener = this
        view.scanRow.listener = this
        view.scanPad.listener = this
        view.tabBar.onTab = { onBarTab(it) }
        view.featuresPanel.onFeature = {
            feedback.keyDown(null)
            onFeature(it)
        }
        view.clipboardPanel.onBack = { setToolPanel(null) }
        view.snippetsPanel.onPick = ::insertSnippet
        view.snippetsPanel.onBack = { setToolPanel(null) }
        view.snippetsPanel.onEdit = {
            setToolPanel(null)
            startActivity(SnippetsActivity.intent(this, admin = false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        view.clipboardPanel.onPaste = { item -> pasteClip(item.text) }
        view.clipboardPanel.onTogglePin = { item ->
            clipboard.togglePin(item.id)
            render()
        }
        view.clipboardPanel.onDelete = { item ->
            clipboard.remove(item.id)
            render()
        }
        listOf(view.oneHandedLeft, view.oneHandedRight).forEach { column ->
            column.onSwap = { setOneHanded(if (config.appearance.oneHanded == OneHanded.LEFT) OneHanded.RIGHT else OneHanded.LEFT) }
            column.onFull = { setOneHanded(OneHanded.OFF) }
        }
        view.tabBar.onHide = { requestHideSelf(0) }
        view.tabBar.onMic = { startVoice() }
        view.suggestionStrip.onMic = { startVoice() }
        view.tabBar.onHideLongPress = { openSettings() }
        view.tabBar.onPress = { feedback.keyDown(null) }
        root = view
        view.onSystemImeButtonsChanged = { render() }
        view.suggestionStrip.onPress = { feedback.keyDown(null) }
        view.suggestionStrip.onPick = ::pickSuggestion
        view.suggestionStrip.onPickSnippet = ::pickSnippet
        view.emojiPanel.onEmoji = { emoji ->
            feedback.keyDown(null)
            endWord()
            ownEdits++
            currentInputConnection?.commitText(emoji, 1)
        }
        view.emojiPanel.onBack = { setEmojiOpen(false) }
        view.emojiPanel.onBackspace = {
            feedback.keyDown(null)
            backspace()
        }
        feedback.hapticView = view.keyboard
        render()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        useSubtype(newSubtype)
        render()
    }

    /** The keyboard language decides the letters with `layout.alphabet: auto`. */
    private fun useSubtype(subtype: InputMethodSubtype?) {
        val tag = subtype?.languageTag?.takeIf { it.isNotEmpty() }
        Alphabet.subtypeLocale = tag?.let(Locale::forLanguageTag)
        // Asked here and not on every key: it is a call into the system.
        multipleLanguages = severalLanguages()
        if (config.typing.suggestions) dictionaries.use(Alphabet.keyboardLocale()) { refreshSuggestions() }
    }

    private var multipleLanguages = false

    /** More than one of our keyboard languages is switched on in the Android settings. */
    private fun severalLanguages(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        val me = imm.enabledInputMethodList.firstOrNull { it.packageName == packageName } ?: return false
        return imm.getEnabledInputMethodSubtypeList(me, true).size > 1
    }

    // region dictation (issue #23): Android's speech recognition inside the keyboard, like Gboard

    private val dictation by lazy { DictationController(this, dictationListener) }

    /** Engine for the current field; asked when a field starts, not per key. */
    private var voiceEngine = Dictation.Engine.NONE
    private var voiceOpen = false

    /** Text left of the cursor when the current utterance began (for the space and capital letter). */
    private var voiceBefore: CharSequence? = null

    private fun findVoiceInput(info: EditorInfo?) {
        voiceEngine = Dictation.Engine.NONE
        if (config.typing.voice == VoiceInput.OFF || FieldPolicy.isPassword(info?.inputType ?: InputType.TYPE_NULL)) return
        voiceEngine = dictation.engineFor(config.typing.voice)
    }

    private fun startVoice() {
        if (voiceEngine == Dictation.Engine.NONE) return
        if (!MicrophonePermissionActivity.granted(this)) {
            MicrophonePermissionActivity.start(this)
            return
        }
        val panel = root?.voicePanel ?: return
        endWord()
        closeCamera()
        emojiOpen = false
        voiceOpen = true
        voiceBefore = null
        panel.onMic = { if (dictation.isActive) dictation.pause() else dictation.start(voiceEngine, Alphabet.keyboardLocale()) }
        panel.onKeyboard = { closeVoice() }
        panel.onBackspace = { voiceBackspace() }
        panel.status = voiceText(R.string.dictation_speak)
        panel.listening = true
        panel.visibility = View.VISIBLE
        feedback.keyDown(null)
        dictation.fallbackToService = config.typing.voice == VoiceInput.AUTO
        dictation.start(voiceEngine, Alphabet.keyboardLocale())
        render()
    }

    private fun closeVoice() {
        if (!voiceOpen) return
        voiceOpen = false
        dictation.stop()
        currentInputConnection?.finishComposingText()
        voiceBefore = null
        root?.voicePanel?.visibility = View.GONE
        render()
    }

    private fun voiceBackspace() {
        val ic = currentInputConnection ?: return
        ic.finishComposingText()
        voiceBefore = null
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
    }

    private fun voiceText(id: Int, vararg args: Any) = AppLanguage.localized(this).getString(id, *args)

    private fun preparedVoice(text: String, before: CharSequence?) =
        Dictation.prepare(text, before, config.typing.autoCapitalize && sentenceField(), Alphabet.keyboardLocale())

    /** Plain text (not e-mail, URL, user name …): dictated sentences start with a capital letter. */
    private fun sentenceField(): Boolean {
        val type = currentInputEditorInfo?.inputType ?: return false
        if (type and InputType.TYPE_MASK_CLASS != InputType.TYPE_CLASS_TEXT) return false
        return when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI, InputType.TYPE_TEXT_VARIATION_FILTER -> false
            else -> true
        }
    }

    private val dictationListener = object : DictationController.Listener {
        override fun onDictationState(state: DictationController.State) {
            val panel = root?.voicePanel ?: return
            panel.listening = state == DictationController.State.LISTENING || state == DictationController.State.PROCESSING
            panel.status = when (state) {
                DictationController.State.LISTENING -> voiceText(R.string.dictation_speak)
                DictationController.State.PROCESSING -> voiceText(R.string.dictation_processing)
                DictationController.State.PAUSED -> voiceText(R.string.dictation_paused)
                DictationController.State.ERROR_PERMISSION -> "${voiceText(R.string.mic_permission_denied)} (${ErrorCodes.MIC_PERMISSION})"
                DictationController.State.ERROR_NETWORK -> "${voiceText(R.string.dictation_error_network)} (${ErrorCodes.DICTATION_NETWORK})"
                DictationController.State.ERROR_UNAVAILABLE -> "${voiceText(R.string.dictation_error_unavailable)} (${ErrorCodes.DICTATION_UNAVAILABLE})"
                DictationController.State.ERROR_NO_MATCH -> "${voiceText(R.string.dictation_error_no_match)} (${ErrorCodes.DICTATION_NOT_UNDERSTOOD})"
                DictationController.State.ERROR_LANGUAGE_PACK -> "${voiceText(R.string.dictation_error_language_pack)} (${ErrorCodes.DICTATION_UNAVAILABLE})"
            }
            if (state != DictationController.State.LISTENING && state != DictationController.State.PROCESSING) {
                currentInputConnection?.finishComposingText()
                voiceBefore = null
            }
        }

        override fun onPartial(text: String) {
            if (!voiceOpen) return
            val ic = currentInputConnection ?: return
            val before = voiceBefore ?: ic.getTextBeforeCursor(BEFORE_CHARS, 0).also { voiceBefore = it ?: "" }
            ic.setComposingText(preparedVoice(text, before), 1)
        }

        override fun onFinal(text: String) {
            if (!voiceOpen) return
            val ic = currentInputConnection ?: return
            val before = voiceBefore ?: ic.getTextBeforeCursor(BEFORE_CHARS, 0)
            ownEdits++
            ic.commitText(preparedVoice(text, before), 1)
            voiceBefore = null
        }

        override fun onLevel(level: Float) {
            root?.voicePanel?.level = level
        }
    }

    // endregion

    private fun nextLanguage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Only within this keyboard: cycles through its languages.
            if (!switchToNextInputMethod(true)) getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        } else {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Fresh EditorInfo on every start and restart. Not in onStartInputView: on a plain re-show
        // it gets the old EditorInfo, and onUpdateSelection has reported newer positions meanwhile.
        selStart = attribute.initialSelStart
        selEnd = attribute.initialSelEnd
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        toolPanel = null
        textSelecting = false
        // First, so that the dictionary, dictation and the start page see the effective configuration
        // (with the app profile of this field).
        val effectiveChanged = useEffective(info = info)
        watchClipboard(true)
        useSubtype(getSystemService(InputMethodManager::class.java)?.currentInputMethodSubtype)
        findVoiceInput(info)
        endWord()
        emojiOpen = false
        Provisioning.applyAllAsync(this)
        val newField = FieldPolicy.field(info.inputType)
        if (!restarting || newField != field || effectiveChanged) {
            field = newField
            page = FieldPolicy.startPage(field, config, scanner.available)
            shift = ShiftState.OFF
            modifiers.clear()
        }
        KeyboardBus.takePendingTab()?.let { tab -> FieldPolicy.pageForTab(tab, field, config, scanner.available)?.let { page = it } }
        updateAutoShift()
        render()
        KeyboardBus.keyboardShown = true
        // An NFC tag read while the tag activity had the focus: the field has the keyboard back.
        KeyboardBus.takePendingNfc(SystemClock.elapsedRealtime())?.let { text -> handler.post { typeNfc(text) } }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        KeyboardBus.keyboardShown = false
        KeyboardBus.hiddenAt = SystemClock.elapsedRealtime()
        if (scanning) {
            scanner.stop()
            setScanning(false)
        }
        closeCamera()
        closeVoice()
        endWord()
        emojiOpen = false
        toolPanel = null
        textSelecting = false
        watchClipboard(false)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Positions only, for the text editing panel (Cut/Copy need a selection) – never the text.
        val hadSelection = selStart != selEnd
        selStart = newSelStart
        selEnd = newSelEnd
        // Same layout, so only the keys are redrawn and a held ← keeps repeating.
        if (toolPanel == Tabs.TOOL_EDIT && hadSelection != (newSelStart != newSelEnd)) render()
        // Text arrived (typed or scanned): the scan is over.
        if (scanning && newSelStart != oldSelStart) setScanning(false)
        // Our own typing is expected; a tap elsewhere, a selection or the app changing the text ends the word.
        if (ownEdits > 0 && newSelStart == newSelEnd) {
            ownEdits--
        } else if (word.isNotEmpty() || autocorrected != null) {
            endWord()
            render()
        } else if (dropNextWords()) {
            render()
        }
        updateAutoShift()
    }

    /** Only the keyboard panel is touchable; the transparent headroom above it belongs to the app. */
    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        val root = root ?: return
        if (!root.isShown || root.height == 0) return
        root.getLocationInWindow(location)
        val top = location[1] + root.panelTop
        outInsets.contentTopInsets = top
        outInsets.visibleTopInsets = top
        outInsets.touchableInsets = Insets.TOUCHABLE_INSETS_REGION
        outInsets.touchableRegion.set(0, top, root.width, location[1] + root.height)
    }

    // endregion

    // region keys

    override fun onKeyDown(key: Key) = feedback.keyDown(key)

    override fun onKey(key: Key, alternative: String?) {
        when (alternative) {
            Layouts.COMMA_SETTINGS -> return openSettings()
            // Emoji from the comma also when the emoji key is switched off, but only where emoji fit.
            Layouts.COMMA_EMOJI -> return if (currentInputEditorInfo?.let { FieldPolicy.emojiAllowed(it.inputType) } == true) setEmojiOpen(true) else Unit
        }
        if (alternative != null) {
            commit(alternative)
            releaseModifiers()
            return
        }
        val held = modifiers.isNotEmpty()
        when (val output = key.output) {
            is KeyOutput.Text -> if (held) typeWithModifiers(key, output.text) else commit(if (key.letter && shift != ShiftState.OFF) output.text.uppercase(Locale.getDefault()) else output.text)
            is KeyOutput.Macro -> runMacro(output.steps)
            is KeyOutput.ShowTab -> if (showTab(output.tab)) announcePage()
            is KeyOutput.Modifier -> tapModifier(output.modifier)
            KeyOutput.Shift -> toggleShift()
            KeyOutput.Backspace -> {
                if (held) chordWithModifiers(KeyEvent.KEYCODE_DEL) else backspace()
                // ⌫ on the text editing panel ends the select mode, like Cut and Del.
                if (toolPanel == Tabs.TOOL_EDIT && textSelecting) {
                    textSelecting = false
                    render()
                }
            }
            KeyOutput.Enter -> if (held) chordWithModifiers(KeyEvent.KEYCODE_ENTER) else enter()
            KeyOutput.Space -> if (held) chordWithModifiers(KeyEvent.KEYCODE_SPACE) else space()
            KeyOutput.Language -> nextLanguage()
            KeyOutput.Emoji -> setEmojiOpen(true)
            KeyOutput.SymbolsPage -> {
                val current = page as? Page.Symbols
                page = Page.Symbols(if (current?.page == 2) 1 else 2, field.alpha)
                render()
                announcePage()
            }
            is KeyOutput.Edit -> textEdit(output.action)
            KeyOutput.None -> Unit
        }
    }

    override fun onKeyLongPress(key: Key): Boolean = when (val output = key.output) {
        is KeyOutput.Modifier -> {
            modifiers[output.modifier] = ShiftState.LOCKED
            render()
            true
        }
        KeyOutput.Space, KeyOutput.Language -> {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            true
        }
        KeyOutput.Shift -> {
            shift = ShiftState.LOCKED
            autoShifted = false
            render()
            true
        }
        else -> false
    }

    /** Cursor position and text before it when a delete gesture started; `null` = none running. */
    private var deleteAnchor: Pair<Int, String>? = null

    override fun onDeleteSwipe(words: Int, done: Boolean) {
        val ic = currentInputConnection ?: return
        val (cursor, before) = deleteAnchor ?: run {
            val extracted = ic.getExtractedText(ExtractedTextRequest(), 0) ?: return
            val position = extracted.startOffset + extracted.selectionStart
            (position to (ic.getTextBeforeCursor(DELETE_LOOKBACK, 0)?.toString().orEmpty())).also { deleteAnchor = it }
        }
        val start = cursor - (before.length - DeleteGesture.start(before, words))
        if (done) {
            deleteAnchor = null
            if (words > 0 && start < cursor) {
                // The marked words go at once, like Gboard on lifting the finger.
                ic.setSelection(start, cursor)
                ic.commitText("", 1)
                endWord()
            } else {
                ic.setSelection(cursor, cursor)
            }
            render()
        } else {
            ic.setSelection(start, cursor)
        }
    }

    override fun onCursorMove(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1) ?: return
        ownEdits++
        trackWord(text)
        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            autoShifted = false
        }
        updateAutoShift()
    }

    private fun space() {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        if (config.typing.doubleSpacePeriod && now - lastSpace < DOUBLE_SPACE_MS) {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                lastSpace = 0
                commit(". ")
                return
            }
        }
        lastSpace = now
        if (!autocorrect(ic)) commit(" ")
        showNextWords()
    }

    /**
     * After a space: likely next words for the word before it ("vielen " → Dank, Grüße), like Gboard
     * (`typing.nextWord`). Tapping one inserts it with a space; typing a letter replaces them.
     */
    private fun showNextWords() {
        if (!config.typing.nextWord || !suggestionsWanted() || word.isNotEmpty()) return
        val before = currentInputConnection?.getTextBeforeCursor(NEXT_WORD_CONTEXT, 0)?.toString() ?: return
        val previous = NextWordContext.previousWord(before) ?: return
        val locale = Alphabet.keyboardLocale()
        val sentenceStart = shift == ShiftState.ONCE && autoShifted
        val words = dictionaries.next.after(previous, locale)
            .map { if (sentenceStart) it.replaceFirstChar { c -> c.titlecase(locale) } else it }
        if (words.isEmpty()) return
        suggestions = Suggestions(words.map { Suggestion(it, correction = false, score = 0) }, typedIsWord = false, autocorrection = null)
        render()
    }

    /**
     * Next words belong to the text right before the cursor: Enter, a cursor move, a scan or the app
     * changing the text makes them stale (and they hide the toolbar). True when some were dropped;
     * the caller renders.
     */
    private fun dropNextWords(): Boolean {
        if (word.isNotEmpty() || suggestions.words.isEmpty()) return false
        suggestions = Suggestions.NONE
        return true
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (undoAutocorrect(ic)) return
        if (!ic.getSelectedText(0).isNullOrEmpty()) {
            ic.commitText("", 1)
            endWord()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            ownEdits++
            if (word.isNotEmpty()) word.setLength(word.length - 1)
            refreshSuggestions()
        }
        updateAutoShift()
    }

    // region suggestions

    /** Letters (and ' or - inside a word) extend the word; anything else ends it. */
    private fun trackWord(text: String) {
        autocorrected = null
        val c = text.singleOrNull()
        if (c != null && (c.isLetter() || ((c == '\'' || c == '-') && word.isNotEmpty()))) word.append(c) else word.setLength(0)
        refreshSuggestions()
    }

    private fun endWord() {
        word.setLength(0)
        autocorrected = null
        suggestions = Suggestions.NONE
        snippetMatch = null
    }

    private fun suggestionsWanted(): Boolean {
        val info = currentInputEditorInfo ?: return false
        // Not while a panel replaces the keys: ⌫ on the text editing panel must not bring the strip back.
        return config.typing.suggestions && page is Page.Alpha && toolPanel == null && FieldPolicy.suggestionsAllowed(info.inputType)
    }

    /** New suggestions for the word; fast (tens of dictionary lookups), measured once in the log. */
    private fun refreshSuggestions() {
        val before = suggestions
        suggestions = if (word.isEmpty() || !suggestionsWanted()) {
            Suggestions.NONE
        } else {
            suggesterNow()?.let { s ->
                val started = System.nanoTime()
                s.suggest(word.toString()).also {
                    if (!timedLookup) {
                        timedLookup = true
                        Log.i(TAG, "Suggestion lookup took ${(System.nanoTime() - started) / 1000} µs")
                    }
                }
            } ?: Suggestions.NONE
        }
        // Also where no suggestions are wanted (barcode fields): a shortcut only offers its snippet.
        val matchChanged = updateSnippetMatch()
        if (before.words != suggestions.words || matchChanged) render()
    }

    /** Recomputes [snippetMatch] for the word being typed (in memory, no system calls); true when it changed. */
    private fun updateSnippetMatch(): Boolean {
        val info = currentInputEditorInfo
        val match = if (word.isEmpty() || info == null) null else Snippets.matchFor(word, page, info.inputType, toolPanel, config, userSnippets.items)
        if (match == snippetMatch) return false
        snippetMatch = match
        return true
    }

    /** [snippetMatch] while it can be offered: still on the letters, no panel over the keys. */
    private fun offeredSnippet(): Snippet? = snippetMatch?.takeIf { page is Page.Alpha && toolPanel == null }

    /**
     * The snippet in the middle of the strip: the typed shortcut goes, the snippet's text is typed
     * as a macro (no space after it, `{ENTER}` runs the field action).
     */
    private fun pickSnippet() {
        val snippet = offeredSnippet() ?: return
        val ic = currentInputConnection ?: return
        val steps = runCatching { Macro.parse(snippet.text) }.getOrNull() ?: return
        val typed = word.toString()
        if (typed.isNotEmpty() && ic.getTextBeforeCursor(typed.length, 0)?.toString() == typed) ic.deleteSurroundingText(typed.length, 0)
        endWord()
        if (shift == ShiftState.ONCE && !autoShifted) shift = ShiftState.OFF
        runCatching { runMacro(steps, snippet = true) }.onFailure { Log.w(TAG, "Snippet could not be typed (${it.javaClass.simpleName})") }
        updateAutoShift()
        render()
    }

    /** A card of the snippets panel: the panel closes first, so a `{SHOW:…}` in an admin snippet works. */
    private fun insertSnippet(item: PanelSnippet) {
        feedback.keyDown(null)
        endWord()
        setToolPanel(null)
        // Never shown there; defensive, since the field may have changed under the panel.
        if (!FieldPolicy.snippetsAllowed(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)) return
        // A one-shot Shift tapped by hand is dropped, as when a shortcut is picked; runMacro ignores it
        // and the armed Ctrl/Win/Alt for snippets, and uses the one-shot ones up.
        if (shift == ShiftState.ONCE && !autoShifted) shift = ShiftState.OFF
        // A stored snippet is valid by construction; stay defensive anyway.
        runCatching { runMacro(Macro.parse(item.text), snippet = true) }.onFailure { Log.w(TAG, "Snippet could not be typed (${it.javaClass.simpleName})") }
        render()
    }

    /** What the snippets panel lists: the administrator's, then the user's own (with `allowUser`). */
    private fun panelSnippets(): List<PanelSnippet> {
        if (toolPanel != Tabs.TOOL_SNIPPETS) return emptyList()
        return Snippets.all(config, userSnippets.items).map { (s, admin) ->
            PanelSnippet(if (admin) "a:${s.id}" else "u:${s.id}", s.label, Snippets.preview(s.text), s.text)
        }
    }

    private fun suggesterNow(): Suggester? {
        val list = dictionaries.current ?: return null
        val rows = Layouts.letterRows(config, config.layout.alphabet.resolve())
        if (suggesterFor != list to rows) {
            suggester = Suggester(list, Alphabet.keyboardLocale(), rows)
            suggesterFor = list to rows
        }
        return suggester
    }

    /** Replaces the word being typed by [choice] and adds a space, like tapping a suggestion in Gboard. */
    private fun pickSuggestion(choice: String) {
        val ic = currentInputConnection ?: return
        val typed = word.toString()
        val before = ic.getTextBeforeCursor(typed.length, 0)?.toString()
        ic.beginBatchEdit()
        if (typed.isNotEmpty() && before == typed) ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$choice ", 1)
        ic.endBatchEdit()
        ownEdits++
        endWord()
        if (shift == ShiftState.ONCE && !autoShifted) shift = ShiftState.OFF
        updateAutoShift()
        showNextWords()
        render()
    }

    /** Space after an obvious typo: the best suggestion instead (`typing.autocorrect`). */
    private fun autocorrect(ic: InputConnection): Boolean {
        val info = currentInputEditorInfo ?: return false
        // "mfg" + space must stay "mfg", not become "mag": a shortcut on offer is no typo.
        if (snippetMatch != null) return false
        val replacement = suggestions.autocorrection
            ?.takeIf { config.typing.autocorrect && suggestionsWanted() && FieldPolicy.autocorrectAllowed(info.inputType, info.imeOptions) } ?: return false
        val typed = word.toString()
        if (ic.getTextBeforeCursor(typed.length, 0)?.toString() != typed) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(typed.length, 0)
        ic.commitText("$replacement ", 1)
        ic.endBatchEdit()
        ownEdits++
        endWord()
        autocorrected = typed to "$replacement "
        updateAutoShift()
        render()
        // A silent correction is a trap for screen-reader users; only spoken, never logged.
        root?.announce(AppLanguage.localized(this).getString(R.string.a11y_autocorrected, replacement))
        return true
    }

    private fun undoAutocorrect(ic: InputConnection): Boolean {
        val (typed, replaced) = autocorrected ?: return false
        autocorrected = null
        if (ic.getTextBeforeCursor(replaced.length, 0)?.toString() != replaced) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(replaced.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        ownEdits++
        word.setLength(0)
        word.append(typed)
        refreshSuggestions()
        updateAutoShift()
        return true
    }

    private fun setEmojiOpen(open: Boolean) {
        if (emojiOpen == open) return
        emojiOpen = open
        if (open) endWord()
        render()
    }

    // endregion

    /** Runs the field's editor action (Done, Next, Go …) or sends Enter. */
    private fun enter() {
        if (dropNextWords()) render()
        sendKeyChar('\n')
        updateAutoShift()
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        val doubleTap = now - lastShiftTap < DOUBLE_TAP_MS
        // At a sentence start the first tap releases the automatic shift; the second still locks.
        val releasesAuto = shift == ShiftState.ONCE && autoShifted
        shift = when (shift) {
            ShiftState.OFF -> if (doubleTap && autoReleasedByTap) ShiftState.LOCKED else ShiftState.ONCE
            ShiftState.ONCE -> if (doubleTap && !autoShifted) ShiftState.LOCKED else ShiftState.OFF
            ShiftState.LOCKED -> ShiftState.OFF
        }
        autoReleasedByTap = releasesAuto
        autoShifted = false
        lastShiftTap = now
        render()
    }

    private fun updateAutoShift() {
        if (shift == ShiftState.LOCKED) return
        val info = currentInputEditorInfo
        val wanted = config.typing.autoCapitalize && page is Page.Alpha && info != null && info.inputType != InputType.TYPE_NULL &&
            (currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0) != 0
        when {
            wanted && shift == ShiftState.OFF -> {
                shift = ShiftState.ONCE
                autoShifted = true
            }
            !wanted && shift == ShiftState.ONCE && autoShifted -> {
                shift = ShiftState.OFF
                autoShifted = false
            }
            else -> Unit
        }
        // Always: after a capital typed by hand the service already went back to lower case – without
        // this the keys stayed upper case while lower case was typed.
        root?.keyboard?.shift = shift
    }

    /** [snippet]: a snippet's text, typed as written – the held modifiers and a manual Shift do not apply. */
    private fun runMacro(steps: List<MacroStep>, snippet: Boolean = false) {
        // A Shift tapped by hand also applies to Tab, arrows, Esc … (Shift+Tab, Shift+→ selects). Not the
        // automatic one at a sentence start, and not Caps Lock, which would turn every Tab into Shift+Tab.
        val held = KeyChords.heldMeta(modifiers.keys, manualShift = shift == ShiftState.ONCE && !autoShifted, snippet = snippet)
        if (steps.any { it is MacroStep.Key || it is MacroStep.Text } && dropNextWords()) render()
        val info = currentInputEditorInfo
        val newlineAsText = info != null && FieldPolicy.newlineAsText(info.inputType, info.imeOptions)
        for ((index, step) in steps.withIndex()) {
            when (step) {
                is MacroStep.Text -> currentInputConnection?.commitText(step.text, 1)
                is MacroStep.Key -> when {
                    // "Line 1{ENTER}Line 2" in a multi-line field: the Enter key would arrive after "Line 2".
                    step.keyCode == KeyEvent.KEYCODE_ENTER && step.meta == 0 && held == 0 && newlineAsText && Macro.textFollows(steps, index) ->
                        currentInputConnection?.commitText("\n", 1)
                    step.keyCode == KeyEvent.KEYCODE_ENTER && step.meta == 0 && held == 0 -> sendKeyChar('\n')
                    step.meta != 0 || held != 0 -> sendKey(step.keyCode, step.meta or held)
                    else -> sendDownUpKeyEvents(step.keyCode)
                }
                is MacroStep.Command -> when (step.command) {
                    KeyCommand.SCAN -> if (scanner.type == ScanTriggerType.CAMERA) toggleCamera() else scanner.toggle()
                    KeyCommand.HIDE -> requestHideSelf(0)
                    KeyCommand.SETTINGS -> openSettings()
                    KeyCommand.NEXT_KEYBOARD -> nextKeyboard()
                    KeyCommand.TEXT_EDIT -> setToolPanel(if (toolPanel == Tabs.TOOL_EDIT) null else Tabs.TOOL_EDIT)
                }
                is MacroStep.ShowTab -> showTab(step.tab)
                is MacroStep.Toggle -> toggleQuick(step.toggle)
            }
        }
        // A one-shot shift (e.g. F13–F24 on the function keys) applies to one key only.
        if (shift == ShiftState.ONCE && !autoShifted) {
            shift = ShiftState.OFF
            render()
        }
        // Ctrl, Win and Alt are used up by keys and text, not by {SHOW:…} or {SCAN}.
        if (steps.any { it is MacroStep.Key || it is MacroStep.Text }) releaseModifiers()
        updateAutoShift()
    }

    /**
     * Key press with modifiers (Shift + F1 = F13, Ctrl + C …). Ctrl, Win and Alt are pressed and
     * released around the key like on a PC keyboard, which remote desktop apps need.
     */
    private fun sendKey(code: Int, meta: Int, pressShift: Boolean = false) {
        val ic = currentInputConnection ?: return
        val time = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        for (e in KeyChords.events(code, meta, pressShift)) {
            val action = if (e.down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
            ic.sendKeyEvent(KeyEvent(time, time, action, e.keyCode, 0, e.meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags))
        }
    }

    // region Ctrl, Win, Alt

    /** First tap arms the modifier for the next key; tapping it again presses it alone (Win: Start menu). */
    private fun tapModifier(modifier: ModifierKey) {
        when (modifiers[modifier]) {
            null -> modifiers[modifier] = ShiftState.ONCE
            ShiftState.ONCE -> {
                sendKey(modifier.keyCode, KeyChords.meta(modifiers.keys))
                releaseModifiers()
            }
            else -> modifiers.remove(modifier)
        }
        render()
    }

    /** Letters, digits and US punctuation go out as key presses, so Ctrl+C reaches the app as Ctrl+C. */
    private fun typeWithModifiers(key: Key, text: String) {
        // Shift counts only when set by hand: at a sentence start Ctrl+C must not become Ctrl+Shift+C.
        val upper = key.letter && shift != ShiftState.OFF && !autoShifted
        val held = KeyChords.meta(modifiers.keys)
        for (c in if (upper) text.uppercase(Locale.ROOT) else text.lowercase(Locale.ROOT)) {
            val step = KeyChords.keyFor(c)
            if (step != null) sendKey(step.keyCode, step.meta or held) else currentInputConnection?.commitText(c.toString(), 1)
        }
        if (shift == ShiftState.ONCE) {
            shift = ShiftState.OFF
            autoShifted = false
        }
        releaseModifiers()
        updateAutoShift()
    }

    private fun chordWithModifiers(code: Int) {
        sendKey(code, KeyChords.meta(modifiers.keys))
        releaseModifiers()
        updateAutoShift()
    }

    /** Armed modifiers apply to one key; locked ones stay. */
    private fun releaseModifiers() {
        if (modifiers.values.none { it == ShiftState.ONCE }) return
        modifiers.values.removeAll { it == ShiftState.ONCE }
        render()
    }

    // endregion

    /** Switches to [tab]; false when it is not available. */
    private fun showTab(tab: String): Boolean {
        val target = FieldPolicy.pageForTab(tab, field, config, scanner.available) ?: return false
        page = target
        closeCamera()
        closeVoice()
        emojiOpen = false
        toolPanel = null
        dropNextWords()
        textSelecting = false
        // Shift belongs to the page it was set on (letters vs. F13–F24 on the function keys).
        shift = ShiftState.OFF
        autoShifted = false
        updateAutoShift()
        render()
        return true
    }

    private fun nextKeyboard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToNextInputMethod(false)
        } else {
            val token = window?.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            getSystemService(InputMethodManager::class.java)?.switchToNextInputMethod(token, false)
        }
    }

    // region toolbar (layout.topBar: toolbar)

    /** Open panel ([Tabs.TOOL_MENU], [Tabs.TOOL_CLIPBOARD], [Tabs.TOOL_EDIT] or [Tabs.TOOL_SNIPPETS]) in place of the keys, or null. */
    private var toolPanel: String? = null

    /** Copied texts, only in memory and only with `typing.clipboardHistory`. */
    private val clipboard = ClipboardHistory()
    private var clipboardWatched = false
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { rememberClip() }

    /** A tap in the toolbar or tab bar: panels and settings, or a page (a second tap on it goes back to ABC). */
    private fun onBarTab(id: String) {
        when (id) {
            Tabs.TOOL_MENU, Tabs.TOOL_CLIPBOARD, Tabs.TOOL_SNIPPETS -> setToolPanel(if (toolPanel == id) null else id)
            Tabs.TOOL_SETTINGS -> openSettings()
            else -> {
                val switched = if (KeyboardRenderer.toolbar(config) && page.tab == id && toolPanel == null) showTab(Tabs.ALPHA) else showTab(id)
                if (switched) announcePage()
            }
        }
    }

    private fun setToolPanel(panel: String?) {
        closeCamera()
        closeVoice()
        emojiOpen = false
        if (panel != null) endWord()
        if (panel != Tabs.TOOL_EDIT) textSelecting = false
        val editOpened = panel == Tabs.TOOL_EDIT && toolPanel != Tabs.TOOL_EDIT
        val clipboardOpened = panel == Tabs.TOOL_CLIPBOARD && toolPanel != Tabs.TOOL_CLIPBOARD
        toolPanel = panel
        if (panel == Tabs.TOOL_MENU) root?.featuresPanel?.show(features())
        // What is on the clipboard now, maybe copied while the keyboard was hidden – unless removed with ×.
        if (clipboardOpened && config.typing.clipboardHistory) currentClip()?.let { clipboard.offer(it, System.currentTimeMillis()) }
        render()
        // The text editing keys replace the letters in place – no pane, so it gets an announcement.
        if (editOpened) root?.announce(AppLanguage.localized(this).getString(R.string.feature_text_editing))
    }

    /** Says the page after a switch by the user ("Symbols", "Function keys" …), with TalkBack only. */
    private fun announcePage() {
        if (!touchExploration) return
        val name = when (val p = page) {
            is Page.Symbols -> describer.pageName(Tabs.SYMBOLS, symbolsPage = p.page)
            is Page.Custom -> describer.pageName(p.id, config.customLayout(p.id)?.label)
            else -> describer.pageName(p.tab)
        }
        root?.announce(name)
    }

    /**
     * The tiles of the ⊞ panel; scan only with a scanner. Fixed order: Fn, Scan, 123, Clipboard,
     * Snippets, Text editing, One-handed, Languages, Settings, then the quick toggles
     * ([FeaturesPanelView.Feature.checked]). The panel scrolls when they do not fit.
     */
    private fun features(): List<FeaturesPanelView.Feature> = buildList {
        val overrides = toggles.overrides
        val allowed = storedConfig.quickToggles.allowed
        add(FeaturesPanelView.Feature(FeaturesPanelView.FUNCTION_KEYS, null, R.string.feature_function, "Fn"))
        if (scanner.available) add(FeaturesPanelView.Feature(FeaturesPanelView.SCAN, R.drawable.kb_scan, R.string.feature_scan))
        add(FeaturesPanelView.Feature(FeaturesPanelView.NUMBER_PAD, R.drawable.kb_numpad, R.string.feature_numpad))
        add(FeaturesPanelView.Feature(FeaturesPanelView.CLIPBOARD, R.drawable.kb_clipboard, R.string.feature_clipboard))
        if (Snippets.reachable(config) && FieldPolicy.snippetsAllowed(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)) {
            add(FeaturesPanelView.Feature(FeaturesPanelView.SNIPPETS, R.drawable.kb_snippets, R.string.feature_snippets))
        }
        if (config.typing.textEditing) add(FeaturesPanelView.Feature(FeaturesPanelView.TEXT_EDITING, R.drawable.kb_text_edit, R.string.feature_text_editing))
        add(FeaturesPanelView.Feature(FeaturesPanelView.ONE_HANDED, R.drawable.kb_onehand, R.string.feature_one_handed))
        add(FeaturesPanelView.Feature(FeaturesPanelView.LANGUAGES, R.drawable.kb_globe, R.string.feature_languages))
        add(FeaturesPanelView.Feature(FeaturesPanelView.SETTINGS, R.drawable.kb_settings, R.string.feature_settings))
        for (t in QuickToggles.visible(belowToggles, scanAvailableBase, allowed)) {
            val checked = QuickToggles.effective(t, belowToggles, overrides, scanAvailableBase, allowed)
            add(FeaturesPanelView.Feature(FeaturesPanelView.toggleId(t), toggleIcon(t), toggleLabel(t), checked = checked))
        }
    }

    /** Fixed per toggle, so that switching changes the tile in place (screen-reader focus stays). */
    private fun toggleIcon(t: QuickToggle): Int = when (t) {
        QuickToggle.GLOVE -> R.drawable.kb_glove
        QuickToggle.CONTRAST -> R.drawable.kb_contrast
        QuickToggle.SCANNER -> R.drawable.kb_scan_toggle
        QuickToggle.SOUND -> R.drawable.kb_sound
    }

    private fun toggleLabel(t: QuickToggle): Int = when (t) {
        QuickToggle.GLOVE -> R.string.feature_glove
        QuickToggle.CONTRAST -> R.string.feature_contrast
        QuickToggle.SCANNER -> R.string.feature_scanner
        QuickToggle.SOUND -> R.string.feature_sound
    }

    /** The open ⊞ panel shows the current tiles (a toggle's state, the scan tile, text editing). */
    private fun refreshFeatures() {
        if (toolPanel == Tabs.TOOL_MENU) root?.featuresPanel?.show(features())
    }

    /**
     * One tap on a quick toggle (tile or `{TOGGLE:…}`): stored on the device only; the store's
     * listener recomputes and draws. A toggle users may not switch does nothing. The ⊞ panel stays
     * open, so the tile shows the new state and glove mode or contrast are visible right away.
     */
    private fun toggleQuick(t: QuickToggle) {
        val allowed = storedConfig.quickToggles.allowed
        if (!QuickToggles.usable(t, allowed, scanAvailableBase)) return
        toggles.replace(QuickToggles.toggled(t, belowToggles, toggles.overrides, scanAvailableBase, allowed))
    }

    private fun onFeature(id: String) {
        FeaturesPanelView.toggleOf(id)?.let { t ->
            toggleQuick(t)
            return
        }
        when (id) {
            FeaturesPanelView.FUNCTION_KEYS -> showTab(Tabs.FUNCTION)
            FeaturesPanelView.SCAN -> showTab(Tabs.SCAN)
            FeaturesPanelView.NUMBER_PAD -> showTab(Tabs.NUMERIC)
            FeaturesPanelView.CLIPBOARD -> setToolPanel(Tabs.TOOL_CLIPBOARD)
            FeaturesPanelView.SNIPPETS -> setToolPanel(Tabs.TOOL_SNIPPETS)
            FeaturesPanelView.TEXT_EDITING -> setToolPanel(Tabs.TOOL_EDIT)
            FeaturesPanelView.ONE_HANDED -> {
                setToolPanel(null)
                setOneHanded(if (config.appearance.oneHanded == OneHanded.OFF) OneHanded.RIGHT else OneHanded.OFF)
            }
            FeaturesPanelView.LANGUAGES -> {
                setToolPanel(null)
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
            FeaturesPanelView.SETTINGS -> openSettings()
        }
    }

    /** One-handed mode is a setting (it stays after a restart), like in Gboard. */
    private fun setOneHanded(mode: OneHanded) {
        store.update { it.copy(appearance = it.appearance.copy(oneHanded = mode)) }
    }

    /** The default keyboard may read the clipboard; only while it is shown. */
    private fun watchClipboard(on: Boolean) {
        val manager = getSystemService(ClipboardManager::class.java) ?: return
        if (on && !clipboardWatched) {
            manager.addPrimaryClipChangedListener(clipListener)
            clipboardWatched = true
        } else if (!on && clipboardWatched) {
            manager.removePrimaryClipChangedListener(clipListener)
            clipboardWatched = false
        }
    }

    /** Text copied while the keyboard is shown goes into the history – never from password fields or sensitive clips. */
    private fun rememberClip() {
        if (!config.typing.clipboardHistory) return
        val text = currentClip() ?: return
        clipboard.add(text, System.currentTimeMillis())
        if (toolPanel == Tabs.TOOL_CLIPBOARD) render()
    }

    /** The current clipboard text, unless it is marked sensitive or the field is a password field. */
    private fun currentClip(): String? {
        if (FieldPolicy.isPassword(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)) return null
        val clip = runCatching { getSystemService(ClipboardManager::class.java)?.primaryClip }.getOrNull() ?: return null
        val sensitive = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
        if (sensitive || clip.itemCount == 0) return null
        return clip.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
    }

    /** What the clipboard panel lists: the history, or only the current item when it is off. */
    private fun clips(): List<ClipItem> {
        if (toolPanel != Tabs.TOOL_CLIPBOARD) return emptyList()
        // The current clip comes in from the listener and once when the panel opens (setToolPanel),
        // not here: × on it must stay removed when the panel is drawn again.
        if (config.typing.clipboardHistory) return clipboard.items(System.currentTimeMillis())
        return listOfNotNull(currentClip()?.let { ClipItem(0, it, pinned = false, time = 0) })
    }

    // region text editing panel (⊞ → Text editing, {EDIT})

    /** Select mode: arrows extend the selection. In memory only, like the positions below. */
    private var textSelecting = false

    /** Selection as last reported by the field (positions, never text); -1 = unknown. */
    private var selStart = -1
    private var selEnd = -1

    /** The end of the selection that stays put while ← and → extend it in select mode. */
    private var selAnchor = -1

    private fun editState(): TextEditState {
        val type = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL
        return TextEditState(textSelecting, selStart != selEnd, FieldPolicy.isPassword(type), TextEditing.editor(type))
    }

    private fun textEdit(action: EditAction) {
        val ic = currentInputConnection ?: return
        val type = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL
        val before = editState()
        if (!before.enabled(action)) return
        endWord()
        when (action) {
            EditAction.LEFT, EditAction.RIGHT, EditAction.UP, EditAction.DOWN, EditAction.HOME, EditAction.END, EditAction.DELETE ->
                moveCursor(ic, action, type, before.hasSelection)
            EditAction.SELECT -> {
                textSelecting = !textSelecting
                // Off keeps the selection; the next arrow collapses it, like TextView and Gboard.
                if (textSelecting) selAnchor = selStart
            }
            EditAction.SELECT_ALL -> if (!ic.performContextMenuAction(android.R.id.selectAll)) {
                ic.getExtractedText(ExtractedTextRequest(), 0)?.let { extracted ->
                    ic.setSelection(0, extracted.startOffset + (extracted.text?.length ?: 0))
                }
            }
            // No Ctrl+X/C fallback: in a terminal Ctrl+C is an interrupt. Copy keeps the selection.
            EditAction.CUT -> {
                ic.performContextMenuAction(android.R.id.cut)
                textSelecting = false
            }
            EditAction.COPY -> {
                ic.performContextMenuAction(android.R.id.copy)
                textSelecting = false
            }
            EditAction.PASTE -> {
                // Terminals and remote desktops have no paste action: type the clip there (never a
                // sensitive one). Editors may paste and still answer false (Compose sends KEYCODE_PASTE).
                if (!ic.performContextMenuAction(android.R.id.paste) && TextEditing.pasteFallback(type)) {
                    currentClip()?.let { clip ->
                        ownEdits++
                        ic.commitText(clip, 1)
                    }
                }
                textSelecting = false
            }
            EditAction.CLOSE -> {
                setToolPanel(null)
                updateAutoShift()
                return
            }
        }
        if (editState() != before) render()
    }

    /** Arrows, Home, End and Del; in select mode they extend the selection. */
    private fun moveCursor(ic: InputConnection, action: EditAction, type: Int, hasSelection: Boolean) {
        if (textSelecting && (action == EditAction.LEFT || action == EditAction.RIGHT)) {
            extendSelection(ic, right = action == EditAction.RIGHT)
            return
        }
        // Asked only for ← and → without a selection: one character, to keep the focus in the field.
        val before = if (action == EditAction.LEFT && !hasSelection) ic.getTextBeforeCursor(1, 0) else null
        val after = if (action == EditAction.RIGHT && !hasSelection) ic.getTextAfterCursor(1, 0) else null
        if (TextEditing.blockedAtEdge(action, type, hasSelection, before, after)) return
        val code = TextEditing.keyCode(action, type) ?: return
        if (textSelecting && action != EditAction.DELETE) {
            // A real Shift press around the key: TextView extends the selection only then.
            sendKey(code, KeyChords.SHIFT_LEFT, pressShift = true)
        } else {
            sendDownUpKeyEvents(code)
        }
        if (action == EditAction.DELETE) textSelecting = false
    }

    /**
     * Select mode, ← and →: sets the selection one character further from the known positions,
     * which works the same in TextView, WebView and Compose (a Shift chord depends on the editor).
     */
    private fun extendSelection(ic: InputConnection, right: Boolean) {
        val (anchor, focus) = TextEditing.extend(selAnchor, selStart, selEnd, if (right) 1 else -1) ?: return
        // Growing at the end of the text: the field ignores positions past it, so stop there.
        if (right && focus > anchor && ic.getTextAfterCursor(1, 0)?.isEmpty() == true) return
        selAnchor = anchor
        if (ic.setSelection(anchor, focus)) {
            // Right away, so a held key does not repeat from a position the field has not reported yet.
            selStart = anchor
            selEnd = focus
        }
    }

    // endregion

    private fun pasteClip(text: String) {
        feedback.keyDown(null)
        endWord()
        ownEdits++
        currentInputConnection?.commitText(text, 1)
        setToolPanel(null)
    }

    // endregion

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // endregion

    // region scanning

    override fun onScanPress() {
        if (scanner.type == ScanTriggerType.CAMERA) {
            toggleCamera()
            return
        }
        scanner.start()
        setScanning(true)
    }

    override fun onScanRelease(heldMs: Long) {
        if (scanner.type == ScanTriggerType.CAMERA) return
        // A tap scans until a barcode is read; holding scans until released.
        if (heldMs >= HOLD_TO_SCAN_MS) {
            scanner.stop()
            setScanning(false)
        }
    }

    private fun setScanning(active: Boolean) {
        handler.removeCallbacks(stopScanAnimation)
        if (active) handler.postDelayed(stopScanAnimation, SCAN_ANIMATION_MS)
        if (scanning != active) {
            scanning = active
            render()
        }
    }

    /** A barcode broadcast (scanner app or DataWedge intent output): type it into the field. */
    private fun onScanInput(intent: Intent, sender: String?) {
        val settings = config.scanner.input
        val data = (listOf(settings.extra) + SCAN_EXTRAS).firstNotNullOfOrNull { intent.getStringExtra(it) }
        val inputType = currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL
        val refusal = ScanInputPolicy.refusal(settings, sender, isInputViewShown, inputType, data)
        if (refusal != null) {
            Log.w(TAG, "Scan broadcast ignored: $refusal")
            return
        }
        val symbology = Symbologies.fromDataWedge(intent.getStringExtra(EXTRA_DATAWEDGE_LABEL_TYPE))
            ?: Symbologies.fromAim(intent.getStringExtra(EXTRA_AIM_ID))
        val output = ScanFormatter.format(data ?: return, symbology, config.scanner.data)
        if (output == null) {
            Log.i(TAG, "Scan broadcast dropped by scanner.data (${symbology?.key ?: "unknown type"}, ${data.length} characters)")
            return
        }
        typeScan(output)
        setScanning(false)
    }

    /** Types a formatted barcode: prefix macro, text, suffix macro (or the old Enter/Tab/nothing). */
    private fun typeScan(output: ScanFormatter.Output) {
        val ic = currentInputConnection ?: return
        if (dropNextWords()) render()
        if (output.prefix.isNotEmpty()) runCatching { Macro.parse(output.prefix) }.getOrNull()?.let(::runMacro)
        val gs = output.gs?.let { runCatching { Macro.parse(it) }.getOrNull() }
        output.parts.forEachIndexed { i, part ->
            if (i > 0) if (gs != null) runMacro(gs) else ic.commitText(ScanFormatter.GS.toString(), 1)
            if (part.isNotEmpty()) ic.commitText(part, 1)
        }
        val suffix = output.suffix
        if (suffix != null) {
            if (suffix.isNotEmpty()) runCatching { Macro.parse(suffix) }.getOrNull()?.let(::runMacro)
            return
        }
        when (config.scanner.input.suffix) {
            ScanSuffix.ENTER -> sendKeyChar('\n')
            ScanSuffix.TAB -> sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            ScanSuffix.NONE -> Unit
        }
    }

    private fun toggleCamera() {
        if (camera?.isOpen == true) closeCamera() else openCamera()
    }

    private fun openCamera() {
        val view = root?.camera ?: return
        if (!CameraPermissionActivity.granted(this)) {
            CameraPermissionActivity.start(this)
            return
        }
        val cam = camera ?: CameraScanner(this, ::onCameraScan, ::onCameraError).also { camera = it }
        val settings = config.scanner.camera
        val data = config.scanner.data
        val ocr = config.scanner.ocr
        cam.settings = settings
        cam.ocr = ocr
        cam.textMode = ocr.enabled && ocr.startInOcr
        cam.onLines = { lines -> view.showLines(lines.map { it.text to it.box }) }
        cam.gs1 = data.gs1
        cam.accept = { text, type -> ScanFormatter.accepts(text, type, data) }
        cam.onStarted = { hasTorch, torchOn -> view.showTorch(hasTorch, torchOn) }
        cameraCount = 0
        view.reset(settings.aim, settings.zoom, ocr = ocr.mode.takeIf { ocr.enabled }, startInText = ocr.enabled && ocr.startInOcr)
        view.onTextMode = { text -> cam.textMode = text }
        view.onPickLine = { line -> cam.pickLine(line) }
        view.onClose = { closeCamera() }
        view.onTorch = { on -> cam.setTorch(on) }
        view.onZoom = { cam.cycleZoom() }
        view.onScreen = { cam.screen = it }
        if (view.width > 0) cam.screen = view.screen()
        view.visibility = View.VISIBLE
        root?.updateKeysCovered()
        cam.open(view.preview)
        feedback.keyDown(null)
        restartCameraTimeout()
    }

    private fun restartCameraTimeout() {
        handler.removeCallbacks(closeCameraLater)
        val seconds = config.scanner.camera.timeoutSeconds
        if (seconds > 0) handler.postDelayed(closeCameraLater, seconds * 1000L)
    }

    private fun closeCamera() {
        handler.removeCallbacks(closeCameraLater)
        camera?.close()
        root?.camera?.visibility = View.GONE
        root?.updateKeysCovered()
    }

    private fun onCameraScan(hit: CameraScanner.Hit) {
        val settings = config.scanner.camera
        val output = ScanFormatter.format(hit.text, hit.symbology, config.scanner.data) ?: return
        Log.i(TAG, "Camera scan (${hit.format}, ${hit.text.length} characters)")
        if (settings.continuous) {
            cameraCount++
            restartCameraTimeout()
            root?.camera?.setStatus(AppLanguage.localized(this).getString(R.string.camera_continuous_status, cameraCount, output.text.take(40)))
        } else {
            closeCamera()
        }
        if (settings.vibrate) feedback.scanned()
        if (settings.beep) beep()
        typeScan(output)
    }

    /** A tag from [NfcTagActivity][de.ictbaden.industrialkeyboard.app.NfcTagActivity]; `false` = no field now. */
    private fun typeNfc(text: String): Boolean {
        if (!isInputViewShown || currentInputConnection == null) return false
        val settings = config.scanner.nfc
        val output = ScanFormatter.format(text, Symbology.NFC, config.scanner.data)
        if (output == null) {
            Log.i(TAG, "NFC tag dropped by scanner.data (${text.length} characters)")
            return true
        }
        Log.i(TAG, "NFC tag (${text.length} characters)")
        if (settings.vibrate) feedback.scanned()
        if (settings.beep) beep()
        typeScan(output)
        return true
    }

    private fun beep() {
        val tone = this.tone ?: runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME) }.getOrNull()?.also { this.tone = it }
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
    }

    private fun onCameraError(error: Throwable) {
        closeCamera()
        val text = AppLanguage.localized(this).getString(R.string.camera_failed)
        Toast.makeText(this, "$text (${ErrorCodes.CAMERA_FAILED})", Toast.LENGTH_LONG).show()
    }

    // endregion

    /** 🌐 next to the space bar: several languages, letters follow them, and Android shows no switch button. */
    private fun languageKey(): Boolean =
        config.layout.alphabet == Alphabet.AUTO && root?.systemImeButtons != true && multipleLanguages

    /** 😊 next to the space bar in fields that take emoji. */
    private fun emojiKey(): Boolean =
        config.appearance.emojiKey && currentInputEditorInfo?.let { FieldPolicy.emojiAllowed(it.inputType) } == true

    private fun render() {
        val root = root ?: return
        val secret = FieldPolicy.isPassword(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
        val enterAction = enterAction()
        root.render(
            KeyboardRenderer.state(
                this, config, page, shift, scanner.name, enterIcon(enterAction), scanning, passwordField = secret, modifiers = modifiers.toMap(),
                camera = scanner.type == ScanTriggerType.CAMERA, languageKey = languageKey(), emojiKey = emojiKey(), suggestions = suggestions.words.map { it.text },
                emojiOpen = emojiOpen, mic = voiceEngine != Dictation.Engine.NONE && !voiceOpen, toolPanel = toolPanel, clips = clips(),
                clipboardHistory = config.typing.clipboardHistory, textEdit = if (toolPanel == Tabs.TOOL_EDIT) editState() else null, snippets = panelSnippets(),
                snippetsEditable = Snippets.userAllowed(config), snippetSuggestion = offeredSnippet()?.label,
                touchExploration = touchExploration,
                // Only in password fields; the headset state comes from the device callback, not from here.
                obscureKeys = secret && PasswordSpeech.obscure(config.accessibility.speakPasswordKeys, passwordField = true, headset = headset),
                enterAction = enterAction,
                enterLabel = currentInputEditorInfo?.actionLabel?.toString()?.takeIf { enterAction != EditorInfo.IME_ACTION_UNSPECIFIED },
            ),
        )
    }

    /** The field's Enter action; [EditorInfo.IME_ACTION_UNSPECIFIED] for a plain Enter (also with `IME_FLAG_NO_ENTER_ACTION`). */
    private fun enterAction(): Int = currentInputEditorInfo?.let { KeyDescriber.enterAction(it.imeOptions) } ?: EditorInfo.IME_ACTION_UNSPECIFIED

    private fun enterIcon(action: Int): Int = when (action) {
        EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.kb_next
        EditorInfo.IME_ACTION_SEARCH -> R.drawable.kb_search
        EditorInfo.IME_ACTION_SEND -> R.drawable.kb_send
        EditorInfo.IME_ACTION_DONE -> R.drawable.kb_done
        EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.kb_left
        else -> R.drawable.kb_enter
    }

    private companion object {
        const val TAG = "KeyboardService"

        /** Text before the cursor asked for when dictation starts an utterance. */
        const val BEFORE_CHARS = 64
        const val DOUBLE_SPACE_MS = 450L
        const val DOUBLE_TAP_MS = 350L
        const val HOLD_TO_SCAN_MS = 600L
        const val NEXT_WORD_CONTEXT = 48
        const val SCAN_ANIMATION_MS = 5_000L
        const val DELETE_LOOKBACK = 1000
        const val BEEP_MS = 120
        const val BEEP_VOLUME = 80
        const val EXTRA_DATAWEDGE_LABEL_TYPE = "com.symbol.datawedge.label_type"
        const val EXTRA_AIM_ID = "aimId"
        val SCAN_EXTRAS = listOf(
            "com.symbol.datawedge.data_string",
            "data",
            "barcode_string",
            "decode_string",
            "com.honeywell.aidc.extra.EXTRA_DATA",
        )
    }
}

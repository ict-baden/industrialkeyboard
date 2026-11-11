package de.ictbaden.industrialkeyboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.api.KeyboardApi
import de.ictbaden.industrialkeyboard.api.KeyboardBus
import de.ictbaden.industrialkeyboard.app.SettingsActivity
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.config.ScanInputSettings
import de.ictbaden.industrialkeyboard.config.ScanSuffix
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyCommand
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.KeyChords
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.layout.MacroStep
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.app.CameraPermissionActivity
import de.ictbaden.industrialkeyboard.app.ErrorCodes
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.scanner.CameraScanner
import de.ictbaden.industrialkeyboard.scanner.HardwareScanner
import de.ictbaden.industrialkeyboard.scanner.ScanFormatter
import de.ictbaden.industrialkeyboard.scanner.ScanTrigger
import de.ictbaden.industrialkeyboard.scanner.Symbologies
import de.ictbaden.industrialkeyboard.ui.Feedback
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.KeyboardView
import de.ictbaden.industrialkeyboard.ui.ScanPadView
import de.ictbaden.industrialkeyboard.ui.ShiftState
import java.util.Locale

/** The input method: tabs, layouts, typing logic, scanning and the intent API. */
class KeyboardService : InputMethodService(), KeyboardView.Listener, ScanPadView.Listener {

    private lateinit var store: ConfigStore
    private lateinit var feedback: Feedback
    private lateinit var scanner: ScanTrigger
    private var config = KeyboardConfig()
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
    private var cameraCount = 0
    private var tone: ToneGenerator? = null
    /** The camera does not run unattended: it closes after a while without a code. */
    private val closeCameraLater = Runnable { closeCamera() }
    private val engineListener: () -> Unit = { applyConfig(config) }

    private val configListener: (KeyboardConfig) -> Unit = { applyConfig(it) }

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
        KeyboardBus.onShowTab = { tab -> root?.isShown == true && showTab(tab) }
        KeyboardBus.onNfc = ::typeNfc
    }

    override fun onDestroy() {
        store.removeListener(configListener)
        KeyboardBus.onShowTab = null
        KeyboardBus.onNfc = null
        KeyboardBus.keyboardShown = false
        HardwareScanner.removeListener(engineListener)
        camera?.destroy()
        camera = null
        tone?.release()
        tone = null
        runCatching { unregisterReceiver(receiver) }
        unregisterScanReceiver()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun applyConfig(updated: KeyboardConfig) {
        config = updated
        feedback.settings = updated.feedback
        scanner.settings = updated.scanner
        val input = updated.scanner.input.takeIf { it.enabled }
        if (scanRegistration != input?.let { it.action to it.permission }) registerScanReceiver(input)
        if (FieldPolicy.pageForTab(page.tab, field, updated, scanner.available) == null) page = FieldPolicy.startPage(field, updated, scanner.available)
        render()
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
        view.tabBar.onTab = { showTab(it) }
        view.tabBar.onHide = { requestHideSelf(0) }
        view.tabBar.onHideLongPress = { openSettings() }
        view.tabBar.onPress = { feedback.keyDown(null) }
        root = view
        render()
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Provisioning.applyAllAsync(this)
        val newField = FieldPolicy.field(info.inputType)
        if (!restarting || newField != field) {
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
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Text arrived (typed or scanned): the scan is over.
        if (scanning && newSelStart != oldSelStart) setScanning(false)
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
        if (alternative != null) {
            commit(alternative)
            releaseModifiers()
            return
        }
        val held = modifiers.isNotEmpty()
        when (val output = key.output) {
            is KeyOutput.Text -> if (held) typeWithModifiers(key, output.text) else commit(if (key.letter && shift != ShiftState.OFF) output.text.uppercase(Locale.getDefault()) else output.text)
            is KeyOutput.Macro -> runMacro(output.steps)
            is KeyOutput.ShowTab -> showTab(output.tab)
            is KeyOutput.Modifier -> tapModifier(output.modifier)
            KeyOutput.Shift -> toggleShift()
            KeyOutput.Backspace -> if (held) chordWithModifiers(KeyEvent.KEYCODE_DEL) else backspace()
            KeyOutput.Enter -> if (held) chordWithModifiers(KeyEvent.KEYCODE_ENTER) else enter()
            KeyOutput.Space -> if (held) chordWithModifiers(KeyEvent.KEYCODE_SPACE) else space()
            KeyOutput.SymbolsPage -> {
                val current = page as? Page.Symbols
                page = Page.Symbols(if (current?.page == 2) 1 else 2, field.alpha)
                render()
            }
            KeyOutput.None -> Unit
        }
    }

    override fun onKeyLongPress(key: Key): Boolean = when (val output = key.output) {
        is KeyOutput.Modifier -> {
            modifiers[output.modifier] = ShiftState.LOCKED
            render()
            true
        }
        KeyOutput.Space -> {
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

    override fun onCursorMove(steps: Int) {
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1) ?: return
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
        commit(" ")
    }

    private fun backspace() {
        val ic = currentInputConnection ?: return
        if (!ic.getSelectedText(0).isNullOrEmpty()) ic.commitText("", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
    }

    /** Runs the field's editor action (Done, Next, Go …) or sends Enter. */
    private fun enter() {
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
            else -> return
        }
        root?.keyboard?.shift = shift
    }

    private fun runMacro(steps: List<MacroStep>) {
        // A Shift tapped by hand also applies to Tab, arrows, Esc … (Shift+Tab, Shift+→ selects). Not the
        // automatic one at a sentence start, and not Caps Lock, which would turn every Tab into Shift+Tab.
        val manualShift = if (shift == ShiftState.ONCE && !autoShifted) KeyEvent.META_SHIFT_ON else 0
        val held = KeyChords.meta(modifiers.keys) or manualShift
        for (step in steps) {
            when (step) {
                is MacroStep.Text -> currentInputConnection?.commitText(step.text, 1)
                is MacroStep.Key -> when {
                    step.keyCode == KeyEvent.KEYCODE_ENTER && step.meta == 0 && held == 0 -> sendKeyChar('\n')
                    step.meta != 0 || held != 0 -> sendKey(step.keyCode, step.meta or held)
                    else -> sendDownUpKeyEvents(step.keyCode)
                }
                is MacroStep.Command -> when (step.command) {
                    KeyCommand.SCAN -> if (scanner.type == ScanTriggerType.CAMERA) toggleCamera() else scanner.toggle()
                    KeyCommand.HIDE -> requestHideSelf(0)
                    KeyCommand.SETTINGS -> openSettings()
                    KeyCommand.NEXT_KEYBOARD -> nextKeyboard()
                }
                is MacroStep.ShowTab -> showTab(step.tab)
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
    private fun sendKey(code: Int, meta: Int) {
        val ic = currentInputConnection ?: return
        val time = SystemClock.uptimeMillis()
        val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
        for (e in KeyChords.events(code, meta)) {
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

    private fun render() {
        val root = root ?: return
        val secret = FieldPolicy.isPassword(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
        root.render(KeyboardRenderer.state(this, config, page, shift, scanner.name, enterIcon(), scanning, passwordField = secret, modifiers = modifiers.toMap(), camera = scanner.type == ScanTriggerType.CAMERA))
    }

    private fun enterIcon(): Int {
        val info = currentInputEditorInfo ?: return R.drawable.kb_enter
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return R.drawable.kb_enter
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> R.drawable.kb_next
            EditorInfo.IME_ACTION_SEARCH -> R.drawable.kb_search
            EditorInfo.IME_ACTION_SEND -> R.drawable.kb_send
            EditorInfo.IME_ACTION_DONE -> R.drawable.kb_done
            EditorInfo.IME_ACTION_PREVIOUS -> R.drawable.kb_left
            else -> R.drawable.kb_enter
        }
    }

    private companion object {
        const val TAG = "KeyboardService"
        const val DOUBLE_SPACE_MS = 450L
        const val DOUBLE_TAP_MS = 350L
        const val HOLD_TO_SCAN_MS = 600L
        const val SCAN_ANIMATION_MS = 5_000L
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

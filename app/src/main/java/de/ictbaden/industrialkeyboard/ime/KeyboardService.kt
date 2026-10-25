package de.ictbaden.industrialkeyboard.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
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
import androidx.core.content.ContextCompat
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
import de.ictbaden.industrialkeyboard.layout.MacroStep
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.scanner.ScanTrigger
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
        // Exported: other apps request tabs; the restrictions broadcast is sent by the system only.
        val filter = IntentFilter().apply {
            addAction(KeyboardApi.ACTION_SHOW_TAB)
            addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        }
        ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        applyConfig(store.config)
        store.addListener(configListener)
        KeyboardBus.onShowTab = { tab -> root?.isShown == true && showTab(tab) }
    }

    override fun onDestroy() {
        store.removeListener(configListener)
        KeyboardBus.onShowTab = null
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
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (scanning) {
            scanner.stop()
            setScanning(false)
        }
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
                    KeyCommand.SCAN -> scanner.toggle()
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
        scanner.start()
        setScanning(true)
    }

    override fun onScanRelease(heldMs: Long) {
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
        val ic = currentInputConnection ?: return
        ic.commitText(data, 1)
        when (settings.suffix) {
            ScanSuffix.ENTER -> sendKeyChar('\n')
            ScanSuffix.TAB -> sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            ScanSuffix.NONE -> Unit
        }
        setScanning(false)
    }

    // endregion

    private fun render() {
        val root = root ?: return
        val secret = FieldPolicy.isPassword(currentInputEditorInfo?.inputType ?: InputType.TYPE_NULL)
        root.render(KeyboardRenderer.state(this, config, page, shift, scanner.name, enterIcon(), scanning, passwordField = secret, modifiers = modifiers.toMap()))
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
        val SCAN_EXTRAS = listOf(
            "com.symbol.datawedge.data_string",
            "data",
            "barcode_string",
            "decode_string",
            "com.honeywell.aidc.extra.EXTRA_DATA",
        )
    }
}

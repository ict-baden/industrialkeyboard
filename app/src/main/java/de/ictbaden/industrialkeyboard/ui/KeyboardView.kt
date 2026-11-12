package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withSave
import androidx.core.view.ViewCompat
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.KeyStyle
import de.ictbaden.industrialkeyboard.layout.EditAction
import de.ictbaden.industrialkeyboard.layout.Key
import de.ictbaden.industrialkeyboard.layout.KeyIcon
import de.ictbaden.industrialkeyboard.layout.KeyOutput
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

enum class ShiftState { OFF, ONCE, LOCKED }

/** Key previews and long-press alternatives, drawn above the keys by the keyboard frame. */
interface PopupHost {
    fun showPreview(source: View, key: RectF, label: String)
    fun hidePreview()
    fun showAlternatives(source: View, key: RectF, options: List<String>)

    /** Highlights the alternative under the finger at ([x], [y]) in [source] coordinates. */
    fun moveAlternatives(source: View, x: Float, y: Float)
    fun selectedAlternative(): String?
    fun hideAlternatives()
}

/** Canvas-drawn key grid with press animation, previews, long-press alternatives and repeat. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onKeyDown(key: Key)
        fun onKey(key: Key, alternative: String?)
        fun onCursorMove(steps: Int)

        /** Long press without alternatives (space, shift …); true when handled. */
        fun onKeyLongPress(key: Key): Boolean

        /**
         * Swipe left from Backspace (Gboard's delete gesture): [words] before the cursor are marked;
         * [done] = finger lifted, delete them (0 = nothing).
         */
        fun onDeleteSwipe(words: Int, done: Boolean) = Unit
    }

    var listener: Listener? = null
    var popupHost: PopupHost? = null

    /** Screen-reader nodes for the keys (always there; also for Switch Access and UI automation). */
    private val a11y = KeyboardAccessibility(this)

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            invalidate()
        }
    var shift: ShiftState = ShiftState.OFF
        set(value) {
            if (field != value) {
                field = value
                invalidate()
                a11y.invalidateRoot()
            }
        }

    /** Ctrl, Win and Alt that apply to the next key (ONCE) or stay on (LOCKED). */
    var modifiers: Map<ModifierKey, ShiftState> = emptyMap()
        set(value) {
            if (field != value) {
                field = value
                invalidate()
                a11y.invalidateRoot()
            }
        }

    /** Text editing panel: Select lit while selecting, Cut/Copy dimmed and inactive without a selection. */
    var editState: TextEditState? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
                a11y.invalidateRoot()
            }
        }

    @DrawableRes
    var enterIcon: Int = R.drawable.kb_enter
        set(value) {
            val changed = field != value
            field = value
            invalidate()
            if (changed) a11y.invalidateRoot()
        }

    /** TalkBack's touch exploration is on: lifting the finger types the explored key ([liftToType]). */
    var touchExploration = false
        set(value) {
            if (field == value) return
            field = value
            hoverIndex = -1
        }

    /** `accessibility.liftToType`: false = keys are typed only by a double tap. */
    var liftToType = true

    /** Password field without a headset: character keys are spoken as "Key". */
    var obscureKeys = false
        set(value) {
            if (field == value) return
            field = value
            a11y.invalidateRoot()
        }

    /** `IME_ACTION_*` of the field (the spoken name of Enter), see [KeyDescriber.enterAction]. */
    var enterAction = EditorInfo.IME_ACTION_UNSPECIFIED
        set(value) {
            if (field == value) return
            field = value
            a11y.invalidateRoot()
        }

    /** The app's name for the Enter action (`EditorInfo.actionLabel`). */
    var enterLabel: String? = null
        set(value) {
            if (field == value) return
            field = value
            a11y.invalidateRoot()
        }
    var keyPopups = true

    /** Swipe left from Backspace deletes words (`typing.deleteGesture`). */
    var deleteGesture = true
        set(value) {
            if (field == value) return
            field = value
            a11y.invalidateRoot()
        }

    /** Letter keys as tiles; false: like Gboard with "Key borders" off. */
    var keyBorders = true
        set(value) {
            field = value
            invalidate()
        }
    var longPressDelay = 400L

    /** The view is sized for this many rows so that every page has the same height. */
    var heightRows = 4
        set(value) {
            field = value
            requestLayout()
        }
    var rowHeight = dp(54f).toInt()
        set(value) {
            field = value
            requestLayout()
        }
    var layout: KeyboardLayout? = null
        set(value) {
            field = value
            cancelAll()
            computeBoxes()
            invalidate()
        }

    private class KeyBox(val key: Key, val cell: RectF, val rect: RectF) {
        /** Shared text size of the words in this key's row (0 = fit the label on its own). */
        var labelSize = 0f
    }

    private enum class Mode { NORMAL, ALTERNATIVES, CURSOR, REPEAT, DELETE }

    private inner class Pointer(val id: Int, var box: KeyBox?, val downX: Float) {
        var mode = Mode.NORMAL
        var consumed = false
        var cursorAnchor = 0f
        var deleteWords = 0
        val longPress = Runnable { onLongPress(this) }
        val repeat: Runnable = object : Runnable {
            override fun run() {
                val key = box?.key ?: return
                mode = Mode.REPEAT
                listener?.onKey(key, null)
                postDelayed(this, REPEAT_INTERVAL)
            }
        }
    }

    private val boxes = mutableListOf<KeyBox>()
    private val pointers = LinkedHashMap<Int, Pointer>()
    private val released = HashMap<KeyBox, Long>()
    private val icons = HashMap<Int, Drawable>()
    private val tmp = RectF()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    /** Letters a little heavier than Roboto Regular, closer to Gboard's Google Sans. */
    private val regular = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.DEFAULT, 500, false) else Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)

    /** Key under the exploring finger (touch exploration), -1 = none. */
    private var hoverIndex = -1

    /** Last key typed by lifting the finger and by a double tap (index, key, time), against double input. */
    private var lastLift: Triple<Int, Key, Long>? = null
    private var lastClick: Triple<Int, Key, Long>? = null

    init {
        ViewCompat.setAccessibilityDelegate(this, a11y)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, paddingTop + paddingBottom + heightRows * rowHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeBoxes()

    private fun computeBoxes() {
        boxes.clear()
        hoverIndex = -1
        a11y.invalidateRoot()
        val layout = layout ?: return
        if (width == 0 || height == 0 || layout.rows.isEmpty()) return
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        val unit = w / layout.units
        val rowH = h / layout.rows.size
        // Gaps like Gboard (measured on a Pixel 9 Pro: 5.3 dp between keys, 12 dp between rows).
        val gapX = dp(5.5f)
        val gapY = min(dp(12f), rowH * 0.19f)
        layout.rows.forEachIndexed { r, row ->
            val spare = layout.units - row.units
            val flex = row.keys.count { it.flex }
            var x = paddingLeft + if (flex == 0) spare * unit / 2 else 0f
            val y = paddingTop + r * rowH
            val rowBoxes = mutableListOf<KeyBox>()
            for (key in row.keys) {
                val keyWidth = (key.width + if (key.flex && flex > 0) spare / flex else 0f) * unit
                val cell = RectF(x, y, x + keyWidth, y + rowH)
                if (!key.isSpacer) {
                    rowBoxes += KeyBox(key, cell, RectF(cell.left + gapX / 2, cell.top + gapY / 2, cell.right - gapX / 2, cell.bottom - gapY / 2))
                }
                x += keyWidth
            }
            // Words in one row share a size (F1 … F12, Esc / Home / PgUp), and so do single characters
            // (narrow keys would otherwise shrink W and M but not I), so the row looks even.
            // Not the text editing keys: "Alles auswählen" would shrink "Pos1" and "Ende".
            rowBoxes.filter { it.key.icon == null && !it.key.label.isNullOrEmpty() && it.key.output != KeyOutput.Space && it.key.output !is KeyOutput.Edit }
                .groupBy { it.key.label!!.length == 1 }.values.forEach { group ->
                    val size = group.minOf { fitLabel(it.key, measureLabel(it.key), it.rect) }
                    group.forEach { it.labelSize = size }
                }
            boxes += rowBoxes
        }
    }

    // region drawing

    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        var animating = false
        for (box in boxes) {
            val pressed = pointers.values.any { it.box === box && it.mode != Mode.CURSOR }
            val fade = released[box]?.let { 1f - (now - it) / FADE_MS.toFloat() }?.takeIf { it > 0f }
            if (fade == null) released.remove(box) else animating = true
            drawKey(canvas, box, pressed, if (pressed) 1f else fade ?: 0f)
        }
        if (animating) postInvalidateOnAnimation()
    }

    private fun drawKey(canvas: Canvas, box: KeyBox, pressed: Boolean, overlay: Float) {
        val key = box.key
        val p = palette
        // An armed modifier shows its label in the accent color, a locked one is filled with it.
        val modifier = (key.output as? KeyOutput.Modifier)?.let { modifiers[it.modifier] } ?: ShiftState.OFF
        // Select on the text editing panel is lit like a locked modifier while selecting.
        val edit = (key.output as? KeyOutput.Edit)?.action
        val lit = edit == EditAction.SELECT && editState?.selecting == true
        val disabled = edit != null && !editEnabled(edit)
        val background = when {
            modifier == ShiftState.LOCKED || lit -> p.accent
            else -> when (key.style) {
                KeyStyle.NORMAL -> p.key
                KeyStyle.FUNCTION -> p.function
                KeyStyle.ACCENT -> p.accent
            }
        }
        val foreground = when {
            modifier == ShiftState.LOCKED || lit -> p.accentText
            disabled -> p.keyHint
            modifier == ShiftState.ONCE && key.style != KeyStyle.ACCENT -> p.accent
            else -> when (key.style) {
                KeyStyle.NORMAL -> p.keyText
                KeyStyle.FUNCTION -> p.functionText
                KeyStyle.ACCENT -> p.accentText
            }
        }
        val r = box.rect
        // Like Gboard: letters rounded by about 6 dp, Enter and ?123/ABC as pills.
        val pill = key.style == KeyStyle.ACCENT || isSwitchKey(key)
        val radius = if (pill) min(r.height(), r.width()) / 2f else min(dp(6f), r.height() * 0.2f)
        // "Key borders" off: letter keys are drawn without a tile, only the label (and the press).
        val tile = keyBorders || key.style != KeyStyle.NORMAL || lit
        canvas.withSave {
            if (pressed) canvas.scale(PRESS_SCALE, PRESS_SCALE, r.centerX(), r.centerY())
            if (p.shadow != 0 && !pressed && tile) {
                fill.color = p.shadow
                tmp.set(r)
                tmp.offset(0f, dp(1.4f))
                canvas.drawRoundRect(tmp, radius, radius, fill)
            }
            if (tile) {
                fill.color = background
                canvas.drawRoundRect(r, radius, radius, fill)
            }
            if (overlay > 0f) {
                fill.color = ColorUtils.setAlphaComponent(p.pressed, (android.graphics.Color.alpha(p.pressed) * overlay).toInt())
                canvas.drawRoundRect(r, radius, radius, fill)
            }
            if (p.border != 0 && tile) {
                stroke.color = if (key.style == KeyStyle.ACCENT) p.accent else p.border
                tmp.set(r)
                tmp.inset(stroke.strokeWidth / 2, stroke.strokeWidth / 2)
                canvas.drawRoundRect(tmp, radius, radius, stroke)
            }
            val icon = key.icon
            when {
                icon != null -> drawIcon(canvas, icon, r, iconColor(key, foreground))
                key.label != null -> drawLabel(canvas, box, foreground)
            }
            key.hint?.let { drawHint(canvas, it, r, if (key.style == KeyStyle.ACCENT) p.accentText else p.keyHint) }
        }
    }

    /** A text editing key that can act now (without a state, e.g. in a preview: all). */
    private fun editEnabled(action: EditAction) = editState?.enabled(action) ?: true

    private fun disabled(key: Key) = (key.output as? KeyOutput.Edit)?.let { !editEnabled(it.action) } == true

    /** ?123 / ABC at the bottom left: a pill like Gboard (the =\< page key stays a tile, as there). */
    private fun isSwitchKey(key: Key) = key.output is KeyOutput.ShowTab && key.label != null && key.style == KeyStyle.FUNCTION

    private fun iconColor(key: Key, foreground: Int): Int =
        if (key.icon == KeyIcon.SHIFT && shift != ShiftState.OFF && key.style != KeyStyle.ACCENT) palette.accent else foreground

    private fun drawIcon(canvas: Canvas, icon: KeyIcon, r: RectF, color: Int) {
        val res = when (icon) {
            KeyIcon.SHIFT -> when (shift) {
                ShiftState.OFF -> R.drawable.kb_shift
                ShiftState.ONCE -> R.drawable.kb_shift_on
                ShiftState.LOCKED -> R.drawable.kb_caps
            }
            KeyIcon.BACKSPACE -> R.drawable.kb_backspace
            KeyIcon.ENTER -> enterIcon
            KeyIcon.TAB -> R.drawable.kb_tab
            KeyIcon.SPACE -> R.drawable.kb_space
            KeyIcon.SCAN -> R.drawable.kb_scan
            KeyIcon.GLOBE -> R.drawable.kb_globe
            KeyIcon.EMOJI -> R.drawable.kb_emoji
            KeyIcon.HIDE -> R.drawable.kb_hide
            KeyIcon.LEFT -> R.drawable.kb_left
            KeyIcon.RIGHT -> R.drawable.kb_right
            KeyIcon.UP -> R.drawable.kb_up
            KeyIcon.DOWN -> R.drawable.kb_down
            KeyIcon.SETTINGS -> R.drawable.ic_settings
            KeyIcon.TEXT_EDIT -> R.drawable.kb_text_edit
        }
        val drawable = icon(res)
        val size = min(dp(26f), r.height() * 0.5f).toInt()
        val left = (r.centerX() - size / 2f).toInt()
        val top = (r.centerY() - size / 2f).toInt()
        drawable.setBounds(left, top, left + size, top + size)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    private fun drawLabel(canvas: Canvas, box: KeyBox, color: Int) {
        val key = box.key
        val r = box.rect
        val label = displayLabel(key)
        text.textSize = if (box.labelSize > 0f) box.labelSize else fitLabel(key, label, r)
        text.typeface = typeface(key, label)
        text.color = if (key.output == KeyOutput.Space) palette.keyHint else color
        val baseline = r.centerY() - (text.descent() + text.ascent()) / 2
        canvas.drawText(label, r.centerX(), baseline, text)
    }

    /** Largest text size of the label's class that fits the key [r]. */
    private fun fitLabel(key: Key, label: String, r: RectF): Float {
        val base = when {
            key.output == KeyOutput.Space -> 13f
            label.length == 1 && layout?.id?.startsWith("numeric") == true -> 26f
            // Like Gboard (measured on a Pixel 9 Pro: capitals 19 dp high): 27 sp instead of 22 sp.
            label.length == 1 -> 27f
            label.length <= 3 -> 16f
            else -> 13f
        }
        text.textSize = min(sp(base), r.height() * if (label.length == 1) 0.56f else 0.36f)
        text.typeface = typeface(key, label)
        val maxWidth = r.width() - dp(8f)
        while (text.measureText(label) > maxWidth && text.textSize > sp(9f)) text.textSize -= 1f
        return text.textSize
    }

    private fun typeface(key: Key, label: String) = if (label.length > 1 || key.style == KeyStyle.FUNCTION) medium else regular

    private fun drawHint(canvas: Canvas, hint: String, r: RectF, color: Int) {
        text.textSize = min(sp(10f), r.height() * 0.22f)
        text.typeface = medium
        text.color = color
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText(hint, r.right - dp(5f), r.top + dp(3f) - text.ascent(), text)
        text.textAlign = Paint.Align.CENTER
    }

    /** Letters are measured in capitals, so the size also fits while Shift is on. */
    private fun measureLabel(key: Key): String = if (key.letter) key.label!!.uppercase(Locale.getDefault()) else key.label!!

    private fun displayLabel(key: Key): String {
        val label = key.label.orEmpty()
        return if (key.letter && shift != ShiftState.OFF) label.uppercase(Locale.getDefault()) else label
    }

    private fun icon(@DrawableRes res: Int): Drawable =
        icons.getOrPut(res) { ContextCompat.getDrawable(context, res)!!.mutate() }

    // endregion

    // region touch

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Fast typing: a second finger commits the key the first finger is still holding.
                pointers.values.filter { it.mode == Mode.NORMAL && !it.consumed }.forEach { other ->
                    other.box?.let { listener?.onKey(it.key, null) }
                    other.consumed = true
                    stopTimers(other)
                }
                val index = event.actionIndex
                press(event.getPointerId(index), event.getX(index), event.getY(index))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) move(event.getPointerId(i), event.getX(i), event.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val index = event.actionIndex
                release(event.getPointerId(index))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun press(id: Int, x: Float, y: Float) {
        val box = boxAt(x, y) ?: return
        // Dimmed Cut/Copy: no press, no click, no sound.
        if (disabled(box.key)) return
        val pointer = Pointer(id, box, x)
        pointers[id] = pointer
        listener?.onKeyDown(box.key)
        showPreview(box)
        if (box.key.repeat) postDelayed(pointer.repeat, REPEAT_DELAY) else postDelayed(pointer.longPress, longPressDelay)
        invalidate()
    }

    private fun move(id: Int, x: Float, y: Float) {
        val pointer = pointers[id] ?: return
        when (pointer.mode) {
            Mode.ALTERNATIVES -> popupHost?.moveAlternatives(this, x, y)
            Mode.CURSOR -> {
                val steps = ((x - pointer.cursorAnchor) / dp(CURSOR_STEP_DP)).toInt()
                if (steps != 0) {
                    listener?.onCursorMove(steps)
                    pointer.cursorAnchor += steps * dp(CURSOR_STEP_DP)
                }
            }
            Mode.REPEAT -> Unit
            Mode.DELETE -> {
                // One more word per step to the left, fewer when moving back.
                val words = ((pointer.downX - x) / dp(DELETE_STEP_DP)).toInt().coerceAtLeast(0)
                if (words != pointer.deleteWords) {
                    pointer.deleteWords = words
                    listener?.onDeleteSwipe(words, done = false)
                }
            }
            Mode.NORMAL -> {
                if (pointer.consumed) return
                val box = pointer.box
                if (deleteGesture && box?.key?.output == KeyOutput.Backspace && pointer.downX - x > dp(CURSOR_SLOP_DP)) {
                    pointer.mode = Mode.DELETE
                    stopTimers(pointer)
                    popupHost?.hidePreview()
                    pointer.deleteWords = 0
                    move(id, x, y)
                    return
                }
                if (box?.key?.output == KeyOutput.Space && abs(x - pointer.downX) > dp(CURSOR_SLOP_DP)) {
                    pointer.mode = Mode.CURSOR
                    pointer.cursorAnchor = x
                    stopTimers(pointer)
                    popupHost?.hidePreview()
                    invalidate()
                    return
                }
                val target = boxAt(x, y)
                if (target !== box && target != null && box?.cell?.contains(x, y) != true && !disabled(target.key)) {
                    // Sliding to another key moves the press (and restarts the long-press timer).
                    stopTimers(pointer)
                    box?.let { released[it] = SystemClock.uptimeMillis() }
                    pointer.box = target
                    showPreview(target)
                    if (target.key.repeat) postDelayed(pointer.repeat, REPEAT_DELAY) else postDelayed(pointer.longPress, longPressDelay)
                    invalidate()
                }
            }
        }
    }

    private fun release(id: Int) {
        val pointer = pointers.remove(id) ?: return
        stopTimers(pointer)
        val box = pointer.box
        when (pointer.mode) {
            Mode.ALTERNATIVES -> {
                val choice = popupHost?.selectedAlternative()
                popupHost?.hideAlternatives()
                if (choice != null && box != null) listener?.onKey(box.key, choice)
            }
            Mode.NORMAL -> if (!pointer.consumed && box != null) listener?.onKey(box.key, null)
            Mode.DELETE -> listener?.onDeleteSwipe(pointer.deleteWords, done = true)
            Mode.CURSOR, Mode.REPEAT -> Unit
        }
        box?.let { released[it] = SystemClock.uptimeMillis() }
        if (pointers.isEmpty()) popupHost?.hidePreview()
        invalidate()
    }

    private fun onLongPress(pointer: Pointer) {
        val box = pointer.box ?: return
        if (pointer.consumed || pointer.mode != Mode.NORMAL) return
        if (listener?.onKeyLongPress(box.key) == true) {
            pointer.consumed = true
            popupHost?.hidePreview()
            return
        }
        val options = box.key.popup.map { if (box.key.letter && shift != ShiftState.OFF) it.uppercase(Locale.getDefault()) else it }
        if (options.isEmpty()) return
        pointer.mode = Mode.ALTERNATIVES
        popupHost?.hidePreview()
        popupHost?.showAlternatives(this, box.rect, options)
    }

    private fun showPreview(box: KeyBox) {
        val key = box.key
        val previewable = keyPopups && key.style == KeyStyle.NORMAL && key.output is KeyOutput.Text &&
            key.label != null && displayLabel(key).length <= 2 && layout?.id?.startsWith("numeric") != true
        if (previewable) popupHost?.showPreview(this, box.rect, displayLabel(key)) else popupHost?.hidePreview()
    }

    private fun stopTimers(pointer: Pointer) {
        removeCallbacks(pointer.longPress)
        removeCallbacks(pointer.repeat)
    }

    private fun cancelAll() {
        // A cancelled delete gesture deletes nothing.
        if (pointers.values.any { it.mode == Mode.DELETE }) listener?.onDeleteSwipe(0, done = true)
        pointers.values.forEach(::stopTimers)
        pointers.clear()
        popupHost?.hidePreview()
        popupHost?.hideAlternatives()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    // region accessibility

    /**
     * Lift-to-type with TalkBack: exploring moves over the keys, lifting the finger (HOVER_EXIT inside
     * the view on the explored key) types it; leaving the keyboard types nothing. Without touch
     * exploration only the helper sees the hover events. Raw touches (TalkBack's double-tap-and-hold
     * passthrough) still go to [onTouchEvent]: hold for alternatives, hold Backspace to repeat.
     */
    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (touchExploration && liftToType) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> hoverIndex = a11yIndexAt(event.x, event.y)
                MotionEvent.ACTION_HOVER_EXIT -> {
                    val index = hoverIndex
                    hoverIndex = -1
                    if (index >= 0 && a11yIndexAt(event.x, event.y) == index && performA11yKey(index, click = false)) a11y.keyPerformed(index)
                }
            }
        }
        return a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    internal fun a11yKeyCount(): Int = boxes.size

    internal fun a11yKey(index: Int): Key? = boxes.getOrNull(index)?.key

    internal fun a11yRect(index: Int, out: Rect) {
        boxes.getOrNull(index)?.rect?.roundOut(out) ?: out.setEmpty()
    }

    /** The key at ([x], [y]) like a touch would find it (forgiving edges); -1 outside the view. */
    internal fun a11yIndexAt(x: Float, y: Float): Int {
        if (x < 0 || y < 0 || x >= width || y >= height) return -1
        val box = boxAt(x, y) ?: return -1
        return boxes.indexOf(box)
    }

    internal fun a11yContext() = KeyDescriber.KeyContext(
        shift = shift,
        modifiers = modifiers,
        enterAction = enterAction,
        enterLabel = enterLabel,
        obscure = obscureKeys,
        deleteGesture = deleteGesture,
        editState = editState,
        layoutId = layout?.id,
    )

    /**
     * Types key [index] like a tap (haptics and sound, then the key). [click] = TalkBack's double tap,
     * otherwise lifting the finger. The same key from the other path within [LIFT_DEDUPE_MS] is
     * ignored (some TalkBack versions click IME keys on lifting themselves): one character per lift.
     */
    internal fun performA11yKey(index: Int, click: Boolean): Boolean {
        val key = boxes.getOrNull(index)?.key ?: return false
        if (disabled(key)) return false
        val now = SystemClock.uptimeMillis()
        val other = if (click) lastLift else lastClick
        if (other != null && other.first == index && other.second == key && now - other.third < LIFT_DEDUPE_MS) return true
        if (click) lastClick = Triple(index, key, now) else lastLift = Triple(index, key, now)
        listener?.onKeyDown(key)
        listener?.onKey(key, null)
        return true
    }

    /** Long press: Backspace deletes a word (delete gesture), others like holding them (lock, keyboards). */
    internal fun performA11yLongPress(index: Int): Boolean {
        val key = boxes.getOrNull(index)?.key ?: return false
        if (disabled(key)) return false
        if (key.output == KeyOutput.Backspace) {
            if (!deleteGesture) return false
            listener?.onKeyDown(key)
            listener?.onDeleteSwipe(1, done = false)
            listener?.onDeleteSwipe(1, done = true)
            return true
        }
        listener?.onKeyDown(key)
        return listener?.onKeyLongPress(key) == true
    }

    /** Long-press alternative [option] of key [index] (TalkBack's actions menu). */
    internal fun performA11yAlternative(index: Int, option: String): Boolean {
        val key = boxes.getOrNull(index)?.key ?: return false
        listener?.onKeyDown(key)
        listener?.onKey(key, option)
        return true
    }

    // endregion

    /** Center of the first key labeled [label] (documentation screenshots). */
    @VisibleForTesting
    internal fun keyCenter(label: String): PointF? =
        boxes.firstOrNull { it.key.label == label }?.let { PointF(it.rect.centerX(), it.rect.centerY()) }

    private fun boxAt(x: Float, y: Float): KeyBox? {
        boxes.firstOrNull { it.cell.contains(x, y) }?.let { return it }
        // Forgiving edges: the nearest key in the touched row, else the nearest key overall.
        val row = boxes.filter { y >= it.cell.top && y < it.cell.bottom }
        val candidates = row.ifEmpty { boxes }
        return candidates.minByOrNull { abs(it.cell.centerX() - x) + abs(it.cell.centerY() - y) }
    }

    // endregion

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val FADE_MS = 160L
        const val PRESS_SCALE = 0.95f
        const val REPEAT_DELAY = 420L
        const val REPEAT_INTERVAL = 55L
        const val CURSOR_SLOP_DP = 18f
        const val CURSOR_STEP_DP = 14f
        const val DELETE_STEP_DP = 24f

        /** A lift and a double tap on the same key within this time type it once. */
        const val LIFT_DEDUPE_MS = 500L
    }
}

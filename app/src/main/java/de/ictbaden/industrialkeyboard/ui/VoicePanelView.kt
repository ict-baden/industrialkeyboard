package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import kotlin.math.min

/**
 * Dictation in place of the keys, like Gboard: a large microphone that pulses with the voice,
 * a status line, ⌨ (back to the keys) and ⌫. Tapping the microphone pauses or continues. For the
 * free model (issue #24) a button under the status offers the language pack download, with a ring
 * around the microphone for its progress.
 */
@SuppressLint("ViewConstructor")
class VoicePanelView(context: Context) : View(context) {

    var palette: Palette = Palette.dark(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            invalidate()
        }

    /** Line under the microphone ("Jetzt sprechen" …). */
    var status: String = ""
        set(value) {
            field = value
            invalidate()
        }

    /** Listening (pulsing, filled) or paused (outlined). */
    var listening = false
        set(value) {
            val changed = field != value
            field = value
            if (!value) level = 0f
            invalidate()
            if (changed) a11y.invalidateVirtualView(MIC)
        }

    /** Loudness 0–1, smoothed. */
    var level = 0f
        set(value) {
            field = field * 0.6f + value * 0.4f
            invalidate()
        }

    /** Button under the status ("Download language pack (46 MB)", "Cancel"); `null` = none. */
    var action: String? = null
        set(value) {
            val changed = field != value
            field = value
            invalidate()
            if (changed) a11y.invalidateRoot()
        }

    /** Download progress 0–1 as a ring around the microphone; below 0 = none. */
    var progress = -1f
        set(value) {
            field = value
            invalidate()
        }

    var onAction: (() -> Unit)? = null
    var onMic: (() -> Unit)? = null
    var onKeyboard: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val micCircle = RectF()
    private val keyboardButton = RectF()
    private val backspaceButton = RectF()
    private val actionButton = RectF()
    private val ring = RectF()
    private val paragraph = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val actionText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private var statusCache: StaticLayout? = null
    private var statusKey: List<Any>? = null
    private var pressed: RectF? = null

    /** Screen-reader nodes: microphone (pause/continue), ⌨ and ⌫. No status announcements: TalkBack would be dictated. */
    private val a11y = VoiceAccessibility()

    init {
        ViewCompat.setAccessibilityDelegate(this, a11y)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        layoutButtons()
        a11y.invalidateRoot()
    }

    /** Where the microphone and the two buttons are (drawing and screen-reader bounds). */
    private fun layoutButtons() {
        val w = width.toFloat()
        val h = height.toFloat()
        // With a button the microphone moves up and gets smaller, so status and button fit.
        val withAction = action != null
        val radius = minOf(w, h) * if (withAction) 0.12f else 0.17f
        val cx = w / 2
        val cy = h * if (withAction) 0.22f else 0.42f
        micCircle.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val size = dp(52f)
        val bottom = h - dp(10f)
        keyboardButton.set(dp(14f), bottom - size, dp(14f) + size * 1.4f, bottom)
        backspaceButton.set(w - dp(14f) - size * 1.4f, bottom - size, w - dp(14f), bottom)
        if (withAction) {
            // Between ⌨ and ⌫ at the bottom: large enough for gloves.
            val left = keyboardButton.right + dp(10f)
            val right = backspaceButton.left - dp(10f)
            actionButton.set(left, bottom - size, right, bottom)
        } else {
            actionButton.setEmpty()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        layoutButtons()
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = micCircle.width() / 2
        val cx = micCircle.centerX()
        val cy = micCircle.centerY()

        // Halo that grows with the voice.
        if (listening) {
            fill.color = ColorUtils.setAlphaComponent(palette.accent, 70)
            canvas.drawCircle(cx, cy, radius * (1.15f + 0.55f * level), fill)
        }
        if (listening) {
            fill.color = palette.accent
            canvas.drawCircle(cx, cy, radius, fill)
        } else {
            fill.color = palette.key
            canvas.drawCircle(cx, cy, radius, fill)
            stroke.color = palette.accent
            stroke.strokeWidth = dp(2f)
            canvas.drawCircle(cx, cy, radius - dp(1f), stroke)
        }
        if (pressed === micCircle) {
            fill.color = palette.pressed
            canvas.drawCircle(cx, cy, radius, fill)
        }
        drawIcon(canvas, R.drawable.kb_mic, cx, cy, radius * 1.0f, if (listening) palette.accentText else palette.accent)
        if (progress >= 0f) {
            stroke.color = ColorUtils.setAlphaComponent(palette.accent, 60)
            stroke.strokeWidth = dp(4f)
            ring.set(micCircle)
            ring.inset(-dp(8f), -dp(8f))
            canvas.drawOval(ring, stroke)
            stroke.color = palette.accent
            canvas.drawArc(ring, -90f, 360f * progress.coerceIn(0f, 1f), false, stroke)
        }

        text.color = palette.keyText
        // Capped by the height: large system fonts must not push the line into the buttons.
        text.textSize = min(sp(16f), h * 0.1f)
        val top = cy + radius + dp(if (progress >= 0f) 22f else 14f)
        if (action == null) {
            val shown = TextUtils.ellipsize(status, text, w - dp(32f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, cx, cy + radius + dp(34f), text)
        } else {
            // Longer messages (missing language pack, errors): up to three lines above the button.
            val width = (w - dp(32f)).toInt().coerceAtLeast(1)
            val maxHeight = (actionButton.top - dp(8f) - top).coerceAtLeast(0f)
            val lines = ((maxHeight / (text.fontSpacing)).toInt()).coerceIn(1, 3)
            canvas.withTranslation(dp(16f), top) { statusLayout(width, lines).draw(this) }
            fill.color = if (pressed === actionButton) ColorUtils.compositeColors(palette.pressed, palette.accent) else palette.accent
            canvas.drawRoundRect(actionButton, actionButton.height() / 2, actionButton.height() / 2, fill)
            val label = actionText.apply {
                color = palette.accentText
                textSize = min(sp(15f), actionButton.height() * 0.36f)
            }
            val labelText = TextUtils.ellipsize(action, label, actionButton.width() - dp(16f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(labelText, actionButton.centerX(), actionButton.centerY() - (label.descent() + label.ascent()) / 2, label)
        }

        // ⌨ bottom left, ⌫ bottom right – where the thumbs are.
        for ((rect, icon) in listOf(keyboardButton to R.drawable.ic_keyboard, backspaceButton to R.drawable.kb_backspace)) {
            fill.color = if (pressed === rect) ColorUtils.compositeColors(palette.pressed, palette.function) else palette.function
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
            drawIcon(canvas, icon, rect.centerX(), rect.centerY(), dp(26f), palette.functionText)
        }
    }

    /** The status in up to [lines] lines, centred; rebuilt only when text, size or colour change. */
    private fun statusLayout(width: Int, lines: Int): StaticLayout {
        val key = listOf(status, width, lines, text.textSize, text.color)
        statusCache?.takeIf { statusKey == key }?.let { return it }
        paragraph.set(text)
        paragraph.textAlign = Paint.Align.LEFT
        return StaticLayout.Builder.obtain(status, 0, status.length, paragraph, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(lines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
            .also {
                statusCache = it
                statusKey = key
            }
    }

    private fun drawIcon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        val half = (size / 2).toInt()
        drawable.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hit = listOf(micCircle, keyboardButton, backspaceButton, actionButton).firstOrNull { !it.isEmpty && it.contains(event.x, event.y) }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = hit
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val target = pressed.takeIf { it === hit }
                pressed = null
                invalidate()
                when (target) {
                    micCircle -> onMic?.invoke()
                    keyboardButton -> onKeyboard?.invoke()
                    backspaceButton -> onBackspace?.invoke()
                    actionButton -> onAction?.invoke()
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = null
                invalidate()
            }
        }
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private fun buttonRect(id: Int): RectF? = when (id) {
        MIC -> micCircle
        KEYBOARD -> keyboardButton
        BACKSPACE -> backspaceButton
        ACTION -> actionButton
        else -> null
    }

    private inner class VoiceAccessibility : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int =
            visible().firstOrNull { buttonRect(it)?.contains(x, y) == true } ?: INVALID_ID

        private fun visible() = if (action != null) listOf(MIC, ACTION, KEYBOARD, BACKSPACE) else listOf(MIC, KEYBOARD, BACKSPACE)

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds += visible()
        }

        @Suppress("DEPRECATION") // setBoundsInParent: ExploreByTouchHelper computes the screen bounds from it.
        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val res = AppLanguage.localized(context)
            node.className = Button::class.java.name
            node.contentDescription = if (virtualViewId == ACTION) {
                // The status explains the button (e.g. which language pack is missing).
                "${action.orEmpty()}. $status"
            } else {
                res.getString(
                    when (virtualViewId) {
                        MIC -> if (listening) R.string.a11y_voice_pause else R.string.a11y_voice_resume
                        KEYBOARD -> R.string.emoji_back
                        else -> R.string.a11y_backspace
                    },
                )
            }
            val bounds = Rect()
            buttonRect(virtualViewId)?.roundOut(bounds)
            node.setBoundsInParent(bounds)
            node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            val run = when (virtualViewId) {
                MIC -> onMic
                KEYBOARD -> onKeyboard
                BACKSPACE -> onBackspace
                ACTION -> onAction
                else -> null
            } ?: return false
            sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
            run()
            return true
        }
    }

    private companion object {
        const val MIC = 0
        const val KEYBOARD = 1
        const val BACKSPACE = 2
        const val ACTION = 3
    }
}

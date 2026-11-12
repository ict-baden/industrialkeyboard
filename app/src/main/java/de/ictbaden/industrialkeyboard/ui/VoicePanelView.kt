package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import de.ictbaden.industrialkeyboard.R

/**
 * Dictation in place of the keys, like Gboard: a large microphone that pulses with the voice,
 * a status line, ⌨ (back to the keys) and ⌫. Tapping the microphone pauses or continues.
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
            field = value
            if (!value) level = 0f
            invalidate()
        }

    /** Loudness 0–1, smoothed. */
    var level = 0f
        set(value) {
            field = field * 0.6f + value * 0.4f
            invalidate()
        }

    var onMic: (() -> Unit)? = null
    var onKeyboard: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val micCircle = RectF()
    private val keyboardButton = RectF()
    private val backspaceButton = RectF()
    private var pressed: RectF? = null

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.background)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = minOf(w, h) * 0.17f
        val cx = w / 2
        val cy = h * 0.42f
        micCircle.set(cx - radius, cy - radius, cx + radius, cy + radius)

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

        text.color = palette.keyText
        text.textSize = sp(16f)
        val shown = TextUtils.ellipsize(status, text, w - dp(32f), TextUtils.TruncateAt.END).toString()
        canvas.drawText(shown, cx, cy + radius + dp(34f), text)

        // ⌨ bottom left, ⌫ bottom right – where the thumbs are.
        val size = dp(52f)
        val bottom = h - dp(10f)
        keyboardButton.set(dp(14f), bottom - size, dp(14f) + size * 1.4f, bottom)
        backspaceButton.set(w - dp(14f) - size * 1.4f, bottom - size, w - dp(14f), bottom)
        for ((rect, icon) in listOf(keyboardButton to R.drawable.ic_keyboard, backspaceButton to R.drawable.kb_backspace)) {
            fill.color = if (pressed === rect) ColorUtils.compositeColors(palette.pressed, palette.function) else palette.function
            canvas.drawRoundRect(rect, rect.height() / 2, rect.height() / 2, fill)
            drawIcon(canvas, icon, rect.centerX(), rect.centerY(), dp(26f), palette.functionText)
        }
    }

    private fun drawIcon(canvas: Canvas, res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val drawable = ContextCompat.getDrawable(context, res)?.mutate() ?: return
        val half = (size / 2).toInt()
        drawable.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val hit = listOf(micCircle, keyboardButton, backspaceButton).firstOrNull { it.contains(event.x, event.y) }
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
}

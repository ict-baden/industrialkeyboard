package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Three word suggestions in place of the tab bar, like Gboard: the best one in the middle, bold.
 * Same height as the tab bar, so the keys do not move when it appears.
 */
@SuppressLint("ViewConstructor")
class SuggestionStripView(context: Context) : View(context) {

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            invalidate()
        }

    var onPick: ((String) -> Unit)? = null
    var onPress: (() -> Unit)? = null

    /** Suggestions, best first; shown as [second, best, third]. */
    var words: List<String> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val divider = Paint()
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressedSlot = -1

    /** Slot (0 left, 1 middle, 2 right) → index in [words]. */
    private fun wordAt(slot: Int): String? = when (slot) {
        0 -> words.getOrNull(1)
        1 -> words.getOrNull(0)
        else -> words.getOrNull(2)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(46f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.tabBar)
        val slot = width / 3f
        text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics)
        divider.color = palette.keyHint
        divider.alpha = 70
        for (i in 0..2) {
            val word = wordAt(i) ?: continue
            if (i == pressedSlot) {
                pressedPaint.color = palette.pressed
                canvas.drawRoundRect(i * slot + dp(4f), dp(5f), (i + 1) * slot - dp(4f), height - dp(5f), dp(10f), dp(10f), pressedPaint)
            }
            text.color = palette.keyText
            text.typeface = if (i == 1) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val shown = TextUtils.ellipsize(word, text, slot - dp(12f), TextUtils.TruncateAt.END).toString()
            canvas.drawText(shown, i * slot + slot / 2, height / 2f - (text.ascent() + text.descent()) / 2, text)
        }
        for (i in 1..2) {
            if (wordAt(i - 1) != null && wordAt(i) != null) {
                canvas.drawRect(i * slot - dp(0.5f), height * 0.3f, i * slot + dp(0.5f), height * 0.7f, divider)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val slot = (event.x / (width / 3f)).toInt().coerceIn(0, 2)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (wordAt(slot) == null) return false
                pressedSlot = slot
                onPress?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val word = if (slot == pressedSlot) wordAt(slot) else null
                pressedSlot = -1
                invalidate()
                word?.let { onPick?.invoke(it) }
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedSlot = -1
                invalidate()
            }
        }
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}

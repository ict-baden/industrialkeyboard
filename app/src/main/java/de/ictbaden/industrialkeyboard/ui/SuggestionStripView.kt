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
import androidx.core.content.ContextCompat
import de.ictbaden.industrialkeyboard.R

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
    var onMic: (() -> Unit)? = null

    /** Microphone at the right end, like Gboard's suggestion row. */
    var showMic = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val micArea get() = if (showMic) dp(52f) else 0f

    /** Suggestions, best first; shown as [second, best, third]. */
    var words: List<String> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * Label of the snippet whose shortcut was typed: it takes the middle slot, on an accent pill;
     * the best two words move to the sides. Tapping it calls [onPickSnippet].
     */
    var snippet: String? = null
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    var onPickSnippet: (() -> Unit)? = null

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val divider = Paint()
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressedSlot = -1

    /** Slot (0 left, 1 middle, 2 right) → index in [words], or the snippet in the middle. */
    internal fun wordAt(slot: Int): String? = if (snippet != null) {
        when (slot) {
            0 -> words.getOrNull(0)
            1 -> snippet
            else -> words.getOrNull(1)
        }
    } else {
        when (slot) {
            0 -> words.getOrNull(1)
            1 -> words.getOrNull(0)
            else -> words.getOrNull(2)
        }
    }

    private fun isSnippetSlot(slot: Int) = slot == 1 && snippet != null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(46f).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.tabBar)
        val slot = (width - micArea) / 3f
        text.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics)
        divider.color = palette.keyHint
        divider.alpha = 70
        for (i in 0..2) {
            val word = wordAt(i) ?: continue
            if (isSnippetSlot(i)) {
                pillPaint.color = palette.accent
                pillPaint.alpha = SNIPPET_PILL_ALPHA
                canvas.drawRoundRect(i * slot + dp(4f), dp(5f), (i + 1) * slot - dp(4f), height - dp(5f), dp(10f), dp(10f), pillPaint)
            }
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
            // No divider next to the snippet pill: it has its own outline.
            if (wordAt(i - 1) != null && wordAt(i) != null && snippet == null) {
                canvas.drawRect(i * slot - dp(0.5f), height * 0.3f, i * slot + dp(0.5f), height * 0.7f, divider)
            }
        }
        if (showMic) {
            val cx = width - micArea / 2
            val cy = height / 2f
            pressedPaint.color = if (pressedSlot == MIC_SLOT) palette.pressed else palette.key
            canvas.drawCircle(cx, cy, dp(18f), pressedPaint)
            ContextCompat.getDrawable(context, R.drawable.kb_mic)?.mutate()?.let { icon ->
                val half = dp(11f).toInt()
                icon.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
                icon.setTint(palette.tabText)
                icon.draw(canvas)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val slot = if (showMic && event.x >= width - micArea) MIC_SLOT else (event.x / ((width - micArea) / 3f)).toInt().coerceIn(0, 2)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (slot != MIC_SLOT && wordAt(slot) == null) return false
                pressedSlot = slot
                onPress?.invoke()
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val mic = slot == MIC_SLOT && pressedSlot == MIC_SLOT
                val picked = slot == pressedSlot && slot != MIC_SLOT
                val pickedSnippet = picked && isSnippetSlot(slot)
                val word = if (picked && !pickedSnippet) wordAt(slot) else null
                pressedSlot = -1
                invalidate()
                if (mic) onMic?.invoke()
                if (pickedSnippet) onPickSnippet?.invoke()
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

    private companion object {
        const val MIC_SLOT = 9

        /** The snippet pill: the accent colour, light enough for the key text on top. */
        const val SNIPPET_PILL_ALPHA = 50
    }
}

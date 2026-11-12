package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.text.TextUtils
import android.text.TextPaint
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import kotlin.math.min

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
            a11y.invalidateRoot()
        }

    /** Height factor for large fonts (`accessibility.followFontSize`), 1 = 46 dp. */
    var heightScale = 1f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    private val micArea get() = if (showMic) dp(52f) else 0f

    /** Suggestions, best first; shown as [second, best, third]. */
    var words: List<String> = emptyList()
        set(value) {
            if (field == value) return
            field = value
            invalidate()
            a11y.invalidateRoot()
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
            a11y.invalidateRoot()
        }

    var onPickSnippet: (() -> Unit)? = null

    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val divider = Paint()
    private val pressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pressedSlot = -1

    /** Screen-reader nodes: the slots with a word (the word itself, never kept) and the microphone. */
    private val a11y = StripAccessibility()

    init {
        ViewCompat.setAccessibilityDelegate(this, a11y)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
    }

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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (dp(46f) * heightScale).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.tabBar)
        val slot = (width - micArea) / 3f
        // Capped by the height: large system fonts must not clip the words.
        text.textSize = min(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 17f, resources.displayMetrics), height * 0.48f)
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = a11y.invalidateRoot()

    override fun dispatchHoverEvent(event: MotionEvent): Boolean = a11y.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

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

    /** Ids 0–2 are the slots (left, middle, right) that hold a word, [MIC_SLOT] the microphone. */
    private inner class StripAccessibility : ExploreByTouchHelper(this) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            if (showMic && x >= width - micArea) return MIC_SLOT
            val slot = (x / ((width - micArea) / 3f)).toInt().coerceIn(0, 2)
            return if (wordAt(slot) != null) slot else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in 0..2) if (wordAt(i) != null) virtualViewIds += i
            if (showMic) virtualViewIds += MIC_SLOT
        }

        @Suppress("DEPRECATION") // setBoundsInParent: ExploreByTouchHelper computes the screen bounds from it.
        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val res = AppLanguage.localized(context)
            val slot = (width - micArea) / 3f
            node.className = Button::class.java.name
            if (virtualViewId == MIC_SLOT) {
                node.contentDescription = res.getString(R.string.a11y_voice_start)
                node.setBoundsInParent(Rect((width - micArea).toInt(), 0, width, height))
            } else {
                val word = wordAt(virtualViewId).orEmpty()
                node.contentDescription = if (isSnippetSlot(virtualViewId)) res.getString(R.string.a11y_snippet, word) else word
                node.setBoundsInParent(Rect((virtualViewId * slot).toInt(), 0, ((virtualViewId + 1) * slot).toInt(), height))
            }
            node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        }

        override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            when {
                virtualViewId == MIC_SLOT -> if (showMic) onMic else null
                isSnippetSlot(virtualViewId) -> onPickSnippet
                else -> wordAt(virtualViewId)?.let { word -> { onPick?.invoke(word) } }
            }?.let { pick ->
                onPress?.invoke()
                // Before the pick: picking a word changes the strip (the node goes away).
                sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
                pick()
                return true
            }
            return false
        }
    }

    private companion object {
        const val MIC_SLOT = 9

        /** The snippet pill: the accent colour, light enough for the key text on top. */
        const val SNIPPET_PILL_ALPHA = 50
    }
}

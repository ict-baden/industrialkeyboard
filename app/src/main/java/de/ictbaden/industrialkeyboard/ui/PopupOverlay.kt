package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Transparent layer above the keys that draws key previews and long-press alternatives.
 * It never handles touches; the pressed [KeyboardView] keeps the gesture.
 */
@SuppressLint("ViewConstructor")
class PopupOverlay(context: Context) : View(context), PopupHost {

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)

    private var previewLabel: String? = null
    private val previewRect = RectF()
    private var previewKeyHeight = 0f

    private var options: List<String> = emptyList()
    private val cells = mutableListOf<RectF>()
    private val panel = RectF()
    private var selected = -1

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val origin = IntArray(2)
    private val target = IntArray(2)

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun showPreview(source: View, key: RectF, label: String) {
        val r = toLocal(source, key)
        // Like Gboard: a balloon about 20 % wider and 1.6 × as high as the key, standing on the key
        // (slight overlap) – the top row uses the transparent headroom, so nothing is clipped.
        val width = min(max(r.width() * 1.2f, dp(44f)), this.width - dp(4f))
        val height = r.height() * 1.6f
        val bottom = r.top + r.height() * 0.12f
        val top = max(bottom - height, dp(2f))
        val left = clampLeft(r.centerX() - width / 2, width)
        previewRect.set(left, top, left + width, top + height)
        previewKeyHeight = r.height()
        previewLabel = label
        invalidate()
    }

    override fun hidePreview() {
        if (previewLabel != null) {
            previewLabel = null
            invalidate()
        }
    }

    /** Top of the keyboard panel: only the keys are dimmed, not the app above. */
    var dimTop = 0f

    override fun showAlternatives(source: View, key: RectF, options: List<String>) {
        val r = toLocal(source, key)
        this.options = options
        // Like Gboard: at most five per row (eight give 4 × 2); the first option sits in the bottom
        // row right above the key and is preselected, so a long press plus release types it.
        val rows = ceil(options.size / MAX_PER_ROW.toFloat()).toInt().coerceAtLeast(1)
        val perRow = ceil(options.size / rows.toFloat()).toInt()
        val cellW = max(r.width() * 0.92f, dp(38f))
        val cellH = max(r.height() * 0.92f, dp(42f))
        val pad = dp(6f)
        val width = perRow * cellW + pad * 2
        val height = rows * cellH + pad * 2
        // Starts at the key and grows to the right; near the right edge it grows to the left.
        val growLeft = r.centerX() > this.width / 2f && r.centerX() + width - cellW / 2 - pad > this.width
        val preferred = if (growLeft) r.centerX() + cellW / 2 + pad - width else r.centerX() - cellW / 2 - pad
        val left = clampLeft(preferred, width)
        val top = max(r.top - height - dp(4f), dp(2f))
        panel.set(left, top, left + width, top + height)
        cells.clear()
        options.indices.forEach { i ->
            val row = i / perRow
            val inRow = i % perRow
            val col = if (growLeft) perRow - 1 - inRow else inRow
            val x = left + pad + col * cellW
            // Row 0 at the bottom, nearest to the finger.
            val y = top + pad + (rows - 1 - row) * cellH
            cells += RectF(x, y, x + cellW, y + cellH)
        }
        selected = 0
        invalidate()
    }

    override fun moveAlternatives(source: View, x: Float, y: Float) {
        if (cells.isEmpty()) return
        source.getLocationInWindow(origin)
        getLocationInWindow(target)
        val lx = x + origin[0] - target[0]
        val ly = (y + origin[1] - target[1]).coerceIn(panel.top, panel.bottom - 1f)
        val index = cells.indices.minByOrNull { abs(cells[it].centerX() - lx) + abs(cells[it].centerY() - ly) * 2 } ?: return
        if (index != selected) {
            selected = index
            invalidate()
        }
    }

    override fun selectedAlternative(): String? = options.getOrNull(selected)

    override fun hideAlternatives() {
        if (options.isNotEmpty()) {
            options = emptyList()
            cells.clear()
            selected = -1
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val label = previewLabel
        if (label != null) drawPreview(canvas, label)
        if (options.isNotEmpty()) drawAlternatives(canvas)
    }

    private fun drawPreview(canvas: Canvas, label: String) {
        // Shown at once on touch-down and gone on release, no animation – like Gboard.
        val radius = min(dp(14f), previewRect.width() / 3f)
        fill.color = palette.popup
        fill.setShadowLayer(dp(6f), 0f, dp(2f), 0x44000000)
        canvas.drawRoundRect(previewRect, radius, radius, fill)
        fill.clearShadowLayer()
        text.color = palette.popupText
        // About 1.4 × the key label (27 sp on letters).
        text.textSize = min(sp(38f), previewKeyHeight * 0.75f)
        val cy = previewRect.top + previewRect.height() * 0.40f
        canvas.drawText(label, previewRect.centerX(), cy - (text.descent() + text.ascent()) / 2, text)
    }

    private fun drawAlternatives(canvas: Canvas) {
        // Dims the keyboard behind the choices, like Gboard.
        fill.color = SCRIM
        canvas.drawRect(0f, dimTop, width.toFloat(), height.toFloat(), fill)
        val radius = dp(24f)
        fill.color = palette.popup
        fill.setShadowLayer(dp(10f), 0f, dp(4f), 0x66000000)
        canvas.drawRoundRect(panel, radius, radius, fill)
        fill.clearShadowLayer()
        cells.forEachIndexed { i, cell ->
            val active = i == selected
            if (active) {
                fill.color = palette.accent
                val d = min(cell.width(), cell.height()) / 2f
                canvas.drawCircle(cell.centerX(), cell.centerY(), d, fill)
            }
            text.color = if (active) palette.accentText else palette.popupText
            val option = options[i]
            text.textSize = min(sp(if (option.length > 2) 15f else 22f), cell.height() * 0.48f)
            canvas.drawText(option, cell.centerX(), cell.centerY() - (text.descent() + text.ascent()) / 2, text)
        }
    }

    /** Keeps a popup of [popupWidth] inside the overlay; left-aligned when it is wider (no coerceIn crash). */
    private fun clampLeft(left: Float, popupWidth: Float): Float =
        left.coerceAtMost(this.width - popupWidth - dp(2f)).coerceAtLeast(dp(2f))

    private fun toLocal(source: View, r: RectF): RectF {
        source.getLocationInWindow(origin)
        getLocationInWindow(target)
        return RectF(r).apply { offset((origin[0] - target[0]).toFloat(), (origin[1] - target[1]).toFloat()) }
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val MAX_PER_ROW = 5
        const val SCRIM = 0x66000000
    }
}

package de.ictbaden.industrialkeyboard.ui

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.PathInterpolator
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import de.ictbaden.industrialkeyboard.R
import kotlin.math.min

/** Row of tabs (ABC, 123, symbols …) with an animated selection pill and a hide button. */
@SuppressLint("ViewConstructor")
class TabBarView(context: Context) : View(context) {

    data class Tab(val id: String, val label: String?, @param:DrawableRes val icon: Int? = null)

    var onTab: ((String) -> Unit)? = null
    var onHide: (() -> Unit)? = null
    var onHideLongPress: (() -> Unit)? = null
    var onPress: (() -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            invalidate()
        }
    var showHide = true

    /** Toolbar: icons spread evenly over the width like Gboard, instead of tabs packed at the left. */
    var spread = false
        set(value) {
            if (field == value) return
            field = value
            computeCells()
            invalidate()
        }

    /** Microphone for dictation, left of the hide button – at the right end like in Gboard. */
    var showMic = false
        set(value) {
            if (field == value) return
            field = value
            computeCells()
            selectedCell()?.let { pill.set(it) }
            invalidate()
        }
    var onMic: (() -> Unit)? = null
    private val micCell = RectF()

    private var tabs: List<Tab> = emptyList()
    private var selected: String? = null
    private val cells = mutableListOf<RectF>()
    private val hideCell = RectF()
    private val pill = RectF()
    private var pillFrom = RectF()
    private var pillTo = RectF()
    private var animator: ValueAnimator? = null
    private var pressedIndex = -2
    private val longPress = Runnable {
        if (pressedIndex == HIDE) {
            pressedIndex = NONE
            onHideLongPress?.invoke()
            invalidate()
        }
    }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val icons = HashMap<Int, Drawable>()

    fun setTabs(tabs: List<Tab>, selected: String?, animate: Boolean) {
        val changed = tabs != this.tabs
        this.tabs = tabs
        val previous = this.selected
        this.selected = selected
        if (changed) computeCells()
        val target = selectedCell()
        if (target == null) {
            pill.setEmpty()
        } else if (!animate || previous == null || pill.isEmpty || changed) {
            animator?.cancel()
            pill.set(target)
        } else if (previous != selected) {
            animatePill(target)
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), dp(46f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        computeCells()
        selectedCell()?.let { pill.set(it) }
    }

    private fun computeCells() {
        cells.clear()
        if (width == 0) return
        val hideWidth = if (showHide) dp(52f) else 0f
        hideCell.set(width - hideWidth, 0f, width.toFloat(), height.toFloat())
        val micWidth = if (showMic) dp(52f) else 0f
        micCell.set(hideCell.left - micWidth, 0f, hideCell.left, height.toFloat())
        val area = width - hideWidth - micWidth - dp(8f)
        val cellWidth = if (tabs.isEmpty()) 0f else if (spread) area / tabs.size else min(area / tabs.size, dp(92f))
        var x = dp(6f)
        tabs.forEach { _ ->
            cells += RectF(x, dp(6f), x + cellWidth, height - dp(6f))
            x += cellWidth
        }
    }

    private fun selectedCell(): RectF? = tabs.indexOfFirst { it.id == selected }.takeIf { it >= 0 }?.let { cells.getOrNull(it) }?.let { inset(it) }

    private fun inset(cell: RectF) = RectF(cell.left + dp(3f), cell.top, cell.right - dp(3f), cell.bottom)

    private fun animatePill(target: RectF) {
        animator?.cancel()
        pillFrom = RectF(pill)
        pillTo = target
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220
            interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
            addUpdateListener {
                val t = it.animatedValue as Float
                pill.set(
                    pillFrom.left + (pillTo.left - pillFrom.left) * t,
                    pillFrom.top,
                    pillFrom.right + (pillTo.right - pillFrom.right) * t,
                    pillFrom.bottom,
                )
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(palette.tabBar)
        if (!pill.isEmpty) {
            fill.color = palette.accent
            canvas.drawRoundRect(pill, pill.height() / 2, pill.height() / 2, fill)
        }
        tabs.forEachIndexed { i, tab ->
            val cell = cells.getOrNull(i) ?: return@forEachIndexed
            val active = tab.id == selected && pill.contains(cell.centerX(), cell.centerY())
            val color = if (active) palette.accentText else palette.tabText
            if (pressedIndex == i && !active) {
                fill.color = palette.pressed
                canvas.drawRoundRect(inset(cell), cell.height() / 2, cell.height() / 2, fill)
            }
            val icon = tab.icon
            if (icon != null && tab.label == null) {
                drawIcon(canvas, icon, cell.centerX(), cell.centerY(), dp(22f), color)
            } else {
                text.color = color
                text.textSize = sp(14f)
                val label = tab.label.orEmpty()
                while (text.measureText(label) > cell.width() - dp(10f) && text.textSize > sp(9f)) text.textSize -= 1f
                canvas.drawText(label, cell.centerX(), cell.centerY() - (text.descent() + text.ascent()) / 2, text)
            }
        }
        if (showMic) {
            // Like Gboard: the microphone sits in a round key-coloured bubble.
            fill.color = palette.key
            canvas.drawCircle(micCell.centerX(), micCell.centerY(), dp(20f), fill)
            if (pressedIndex == MIC) {
                fill.color = palette.pressed
                canvas.drawCircle(micCell.centerX(), micCell.centerY(), dp(20f), fill)
            }
            drawIcon(canvas, R.drawable.kb_mic, micCell.centerX(), micCell.centerY(), dp(24f), palette.tabText)
        }
        if (showHide) {
            if (pressedIndex == HIDE) {
                fill.color = palette.pressed
                canvas.drawCircle(hideCell.centerX(), hideCell.centerY(), dp(18f), fill)
            }
            drawIcon(canvas, R.drawable.kb_hide, hideCell.centerX(), hideCell.centerY(), dp(26f), palette.tabText)
        }
    }

    private fun drawIcon(canvas: Canvas, @DrawableRes res: Int, cx: Float, cy: Float, size: Float, color: Int) {
        val drawable = icons.getOrPut(res) { ContextCompat.getDrawable(context, res)!!.mutate() }
        val half = (size / 2).toInt()
        drawable.setBounds(cx.toInt() - half, cy.toInt() - half, cx.toInt() + half, cy.toInt() + half)
        drawable.setTint(color)
        drawable.draw(canvas)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedIndex = hit(event.x)
                if (pressedIndex != NONE) onPress?.invoke()
                if (pressedIndex == HIDE) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val index = hit(event.x)
                if (index == pressedIndex) {
                    when {
                        index == HIDE -> onHide?.invoke()
                        index == MIC -> onMic?.invoke()
                        index >= 0 -> onTab?.invoke(tabs[index].id)
                    }
                }
                pressedIndex = NONE
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                pressedIndex = NONE
                invalidate()
            }
        }
        return true
    }

    private fun hit(x: Float): Int {
        if (showHide && x >= hideCell.left) return HIDE
        if (showMic && x >= micCell.left && x < micCell.right) return MIC
        return cells.indexOfFirst { x >= it.left && x < it.right }.takeIf { it >= 0 } ?: NONE
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)

    private companion object {
        const val NONE = -2
        const val HIDE = -1
        const val MIC = -3
    }
}

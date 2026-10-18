package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/** Large scan button: tap to scan once, hold to scan until released. */
@SuppressLint("ViewConstructor")
class ScanPadView(context: Context) : View(context) {

    interface Listener {
        fun onScanPress()
        fun onScanRelease(heldMs: Long)
    }

    var listener: Listener? = null
    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    /** Name of the scanner integration, or null when no scanner was found. */
    var scannerName: String? = null
        set(value) {
            field = value
            invalidate()
        }
    var scanning = false
        set(value) {
            if (field != value) {
                field = value
                scanStarted = SystemClock.uptimeMillis()
                invalidate()
            }
        }

    private var pressedAt = 0L
    private var pressed = false
    private var scanStarted = 0L
    private val pad = RectF()
    private var shader: Shader? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { maskFilter = BlurMaskFilter(dp(10f), BlurMaskFilter.Blur.NORMAL) }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val subtitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val icon = ContextCompat.getDrawable(context, R.drawable.kb_scan)!!.mutate()

    init {
        // Blur mask filters need software rendering.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = text(R.string.scan_pad_description)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        pad.set(dp(8f), dp(8f), w - dp(8f), h - dp(4f))
        shader = null
    }

    override fun onDraw(canvas: Canvas) {
        val available = scannerName != null
        val accent = palette.accent
        if (shader == null) {
            shader = LinearGradient(
                pad.left, pad.top, pad.right, pad.bottom,
                ColorUtils.blendARGB(accent, 0xFFFFFFFF.toInt(), 0.12f),
                ColorUtils.blendARGB(accent, 0xFF000000.toInt(), 0.28f),
                Shader.TileMode.CLAMP,
            )
        }
        val radius = dp(18f)
        canvas.save()
        if (pressed) canvas.scale(0.985f, 0.985f, pad.centerX(), pad.centerY())
        fill.shader = if (available) shader else null
        fill.color = if (available) accent else palette.function
        canvas.drawRoundRect(pad, radius, radius, fill)
        fill.shader = null
        if (pressed) {
            fill.color = 0x22000000
            canvas.drawRoundRect(pad, radius, radius, fill)
        }
        val foreground = if (available) palette.accentText else palette.functionText
        if (scanning) drawLaser(canvas)

        val iconSize = min(dp(56f), pad.height() * 0.34f).toInt()
        val iconTop = (pad.centerY() - iconSize * 0.95f).toInt()
        val left = (pad.centerX() - iconSize / 2f).toInt()
        icon.setBounds(left, iconTop, left + iconSize, iconTop + iconSize)
        icon.setTint(foreground)
        icon.alpha = if (available) 255 else 120
        icon.draw(canvas)

        title.color = foreground
        title.textSize = min(sp(19f), pad.height() * 0.11f)
        val label = when {
            !available -> text(R.string.scan_pad_no_scanner)
            scanning -> text(R.string.scan_pad_scanning)
            else -> text(R.string.scan_pad_tap)
        }
        val titleY = iconTop + iconSize + dp(10f) - title.ascent()
        canvas.drawText(label, pad.centerX(), titleY, title)
        subtitle.color = ColorUtils.setAlphaComponent(foreground, 190)
        subtitle.textSize = min(sp(12.5f), pad.height() * 0.075f)
        val hint = if (available) text(R.string.scan_pad_hint, scannerName) else text(R.string.scan_pad_configure)
        canvas.drawText(hint, pad.centerX(), titleY + dp(6f) - subtitle.ascent() + subtitle.descent(), subtitle)
        canvas.restore()
        if (scanning) postInvalidateOnAnimation()
    }

    private fun text(id: Int, vararg args: Any?): String = AppLanguage.localized(context).getString(id, *args)

    private fun drawLaser(canvas: Canvas) {
        val t = (SystemClock.uptimeMillis() - scanStarted) / 1000.0
        val y = pad.centerY() + (pad.height() * 0.36f) * sin(t * PI * 1.4).toFloat()
        val inset = dp(18f)
        glow.color = 0xCCFF3D00.toInt()
        canvas.drawRoundRect(pad.left + inset, y - dp(6f), pad.right - inset, y + dp(6f), dp(6f), dp(6f), glow)
        fill.color = 0xFFFFE0B2.toInt()
        canvas.drawRoundRect(pad.left + inset, y - dp(1.5f), pad.right - inset, y + dp(1.5f), dp(2f), dp(2f), fill)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (scannerName == null) return true
                pressed = true
                pressedAt = SystemClock.uptimeMillis()
                listener?.onScanPress()
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!pressed) return true
                pressed = false
                listener?.onScanRelease(SystemClock.uptimeMillis() - pressedAt)
                invalidate()
            }
        }
        return true
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density

    private fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}

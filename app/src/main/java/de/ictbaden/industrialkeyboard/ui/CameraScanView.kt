package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.AimMode
import de.ictbaden.industrialkeyboard.config.OcrMode
import de.ictbaden.industrialkeyboard.scanner.AimGeometry
import java.util.Locale

/**
 * Camera picture with an aiming frame or crosshair, plus close, torch and zoom buttons, and a switch
 * between barcode and text (OCR) with tappable boxes around the recognised lines.
 */
@SuppressLint("ViewConstructor")
class CameraScanView(context: Context) : FrameLayout(context) {

    /** TextureView mode: a SurfaceView in the input method window may stay black on some devices. */
    val preview = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    var onClose: (() -> Unit)? = null
    var onTorch: ((Boolean) -> Unit)? = null

    /** Returns the new zoom ratio. */
    var onZoom: (() -> Float)? = null

    /** The user switched between barcode (`false`) and text (`true`). */
    var onTextMode: ((Boolean) -> Unit)? = null

    /** Pick mode: the user tapped a recognised line. */
    var onPickLine: ((String) -> Unit)? = null

    /** Called after every layout with the preview and frame on the screen. */
    var onScreen: ((AimGeometry.Screen) -> Unit)? = null

    var palette: Palette = Palette.dark(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            finder.invalidate()
            updateTorch()
        }

    var aim: AimMode = AimMode.FRAME
        set(value) {
            field = value
            finder.invalidate()
        }

    private var torchOn = false
    private var textMode = false
    private var ocrMode = OcrMode.PICK
    private var lines: List<Pair<String, RectF>> = emptyList()
    private val modeBarcode = modeButton()
    private val modeText = modeButton()
    private val modeSwitch = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_pill)?.mutate()
        addView(modeBarcode)
        addView(modeText)
        isVisible = false
    }
    private val finder = Finder(context)
    private val hint = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
        gravity = Gravity.CENTER
        maxLines = 2
    }
    private val close = button(R.drawable.kb_close) { onClose?.invoke() }
    private val torch = button(R.drawable.kb_torch) {
        torchOn = !torchOn
        updateTorch()
        onTorch?.invoke(torchOn)
    }.apply { isVisible = false }
    private val zoom = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        background = ContextCompat.getDrawable(context, R.drawable.bg_round)?.mutate()
        setOnClickListener { onZoom?.invoke()?.let(::showZoom) }
    }

    init {
        setBackgroundColor(Color.BLACK)
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(finder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { setMargins(dp(8), 0, dp(8), dp(6)) })
        val size = dp(48)
        addView(close, LayoutParams(size, size, Gravity.TOP or Gravity.END).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        addView(torch, LayoutParams(size, size, Gravity.TOP or Gravity.START).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        // Next to the torch, so the hint line below stays free.
        addView(zoom, LayoutParams(size, size, Gravity.TOP or Gravity.START).apply { setMargins(dp(56), dp(4), dp(4), dp(4)) })
        addView(modeSwitch, LayoutParams(LayoutParams.WRAP_CONTENT, size, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        modeBarcode.setOnClickListener { switchMode(false) }
        modeText.setOnClickListener { switchMode(true) }
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> if (width > 0 && height > 0) onScreen?.invoke(screen()) }
    }

    /**
     * Resets buttons and texts for a new scan. [ocr] `null` hides the barcode/text switch;
     * [startInText] opens in text mode.
     */
    fun reset(aim: AimMode, zoomRatio: Float, ocr: OcrMode? = null, startInText: Boolean = false) {
        this.aim = aim
        ocrMode = ocr ?: OcrMode.PICK
        textMode = ocr != null && startInText
        lines = emptyList()
        modeSwitch.isVisible = ocr != null
        torchOn = false
        torch.isVisible = false
        updateTorch()
        showZoom(zoomRatio)
        val localized = AppLanguage.localized(context)
        modeBarcode.text = localized.getString(R.string.camera_mode_barcode)
        modeText.text = localized.getString(R.string.camera_mode_text)
        updateMode()
        close.contentDescription = localized.getString(R.string.camera_close)
        torch.contentDescription = localized.getString(R.string.camera_torch)
        zoom.contentDescription = localized.getString(R.string.camera_zoom)
    }

    fun showTorch(available: Boolean, on: Boolean) {
        torch.isVisible = available
        torchOn = on && available
        updateTorch()
    }

    /** Pick mode: the lines to show as tappable boxes (view coordinates). */
    fun showLines(found: List<Pair<String, RectF>>) {
        lines = found
        finder.invalidate()
    }

    private fun switchMode(text: Boolean) {
        if (textMode == text) return
        textMode = text
        lines = emptyList()
        updateMode()
        onTextMode?.invoke(text)
    }

    private fun updateMode() {
        val localized = AppLanguage.localized(context)
        hint.text = localized.getString(
            when {
                textMode && ocrMode == OcrMode.PATTERN -> R.string.camera_hint_text_pattern
                textMode -> R.string.camera_hint_text_pick
                aim == AimMode.CENTER -> R.string.camera_hint_center
                else -> R.string.camera_hint
            },
        )
        listOf(modeBarcode to !textMode, modeText to textMode).forEach { (button, on) ->
            button.background = if (on) ContextCompat.getDrawable(context, R.drawable.bg_pill)?.mutate()?.apply { setTint(palette.accent) } else null
            button.setTextColor(if (on) palette.accentText else Color.WHITE)
        }
        finder.invalidate()
    }

    private fun modeButton() = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), 0, dp(14), 0)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)
    }

    fun showZoom(ratio: Float) {
        zoom.text = String.format(Locale.ROOT, "%.0f×", ratio)
    }

    /** Line under the frame, e.g. the count and the last code in continuous mode. */
    fun setStatus(text: String) {
        hint.text = text
    }

    /** The aiming frame in view coordinates: 80 % of the width, at most 62 % of the height. */
    fun frame(w: Float = width.toFloat(), h: Float = height.toFloat()): RectF {
        val fw = w * 0.8f
        val fh = (h * 0.62f).coerceAtMost(fw)
        return RectF((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
    }

    fun screen(): AimGeometry.Screen = frame().let { AimGeometry.Screen(width.toFloat(), height.toFloat(), it.left, it.top, it.right, it.bottom) }

    private fun updateTorch() {
        torch.background?.setTint(if (torchOn) palette.accent else 0x66000000)
        torch.imageTintList = ColorStateList.valueOf(if (torchOn) palette.accentText else Color.WHITE)
    }

    private fun button(icon: Int, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        background = ContextCompat.getDrawable(context, R.drawable.bg_round)?.mutate()
        background.setTint(0x66000000)
        imageTintList = ColorStateList.valueOf(Color.WHITE)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Darkens everything outside the frame and draws its corners, a scan line or a crosshair. */
    private inner class Finder(context: Context) : View(context) {
        private val shade = Paint().apply { color = 0x88000000.toInt() }
        private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val line = Paint().apply { color = 0xCCFF3B30.toInt() }
        private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0xEEFF3B30.toInt()
        }
        private val path = Path()
        private val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val boxFill = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            setOnTouchListener { _, event -> touch(event) }
        }

        /** Pick mode: a tap on a box takes its line (a little tolerance for gloves). */
        private fun touch(event: MotionEvent): Boolean {
            if (!textMode || ocrMode != OcrMode.PICK || lines.isEmpty()) return false
            if (event.action == MotionEvent.ACTION_DOWN) return true
            if (event.action != MotionEvent.ACTION_UP) return false
            val slack = 12f * resources.displayMetrics.density
            val hit = lines.firstOrNull { (_, r) -> RectF(r.left - slack, r.top - slack, r.right + slack, r.bottom + slack).contains(event.x, event.y) }
            if (hit != null) {
                performClick()
                onPickLine?.invoke(hit.first)
            }
            return hit != null
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            val frame = frame(w, h)
            val stroke = 4f * resources.displayMetrics.density
            if (aim != AimMode.ANYWHERE) {
                canvas.drawRect(0f, 0f, w, frame.top, shade)
                canvas.drawRect(0f, frame.bottom, w, h, shade)
                canvas.drawRect(0f, frame.top, frame.left, frame.bottom, shade)
                canvas.drawRect(frame.right, frame.top, w, frame.bottom, shade)
            }
            val arm = minOf(frame.width(), frame.height()) * 0.18f
            corner.strokeWidth = stroke
            corner.color = palette.accent
            corner.alpha = if (aim == AimMode.ANYWHERE) 0x88 else 0xFF
            path.reset()
            path.moveTo(frame.left, frame.top + arm); path.lineTo(frame.left, frame.top); path.lineTo(frame.left + arm, frame.top)
            path.moveTo(frame.right - arm, frame.top); path.lineTo(frame.right, frame.top); path.lineTo(frame.right, frame.top + arm)
            path.moveTo(frame.right, frame.bottom - arm); path.lineTo(frame.right, frame.bottom); path.lineTo(frame.right - arm, frame.bottom)
            path.moveTo(frame.left + arm, frame.bottom); path.lineTo(frame.left, frame.bottom); path.lineTo(frame.left, frame.bottom - arm)
            canvas.drawPath(path, corner)
            val cx = frame.centerX()
            val cy = frame.centerY()
            if (textMode) {
                // Text: boxes around the lines that can be taken, no scan line.
                box.strokeWidth = stroke * 0.6f
                box.color = palette.accent
                boxFill.color = palette.accent
                boxFill.alpha = 0x40
                val radius = stroke
                for ((_, r) in lines) {
                    canvas.drawRoundRect(r, radius, radius, boxFill)
                    canvas.drawRoundRect(r, radius, radius, box)
                }
                return
            }
            if (aim == AimMode.CENTER) {
                // Pick list: only the code under the crosshair counts.
                val size = minOf(frame.width(), frame.height()) * 0.12f
                cross.strokeWidth = stroke * 0.75f
                canvas.drawLine(cx - size, cy, cx - size * 0.3f, cy, cross)
                canvas.drawLine(cx + size * 0.3f, cy, cx + size, cy, cross)
                canvas.drawLine(cx, cy - size, cx, cy - size * 0.3f, cross)
                canvas.drawLine(cx, cy + size * 0.3f, cx, cy + size, cross)
                canvas.drawCircle(cx, cy, stroke * 0.6f, cross)
            } else {
                canvas.drawRect(frame.left + stroke * 2, cy - stroke / 4, frame.right - stroke * 2, cy + stroke / 4, line)
            }
        }
    }
}

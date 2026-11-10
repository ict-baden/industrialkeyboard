package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/** Camera picture with a viewfinder in place of the keys, plus close and torch buttons. */
@SuppressLint("ViewConstructor")
class CameraScanView(context: Context) : FrameLayout(context) {

    /** TextureView mode: a SurfaceView in the input method window may stay black on some devices. */
    val preview = PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    var onClose: (() -> Unit)? = null
    var onTorch: ((Boolean) -> Unit)? = null

    var palette: Palette = Palette.dark(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            finder.invalidate()
        }

    private var torchOn = false
    private val finder = Finder(context)
    private val hint = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setShadowLayer(4f, 0f, 1f, Color.BLACK)
        gravity = Gravity.CENTER
    }
    private val close = button(R.drawable.kb_close) { onClose?.invoke() }
    private val torch = button(R.drawable.kb_torch) {
        torchOn = !torchOn
        updateTorch()
        onTorch?.invoke(torchOn)
    }.apply { isVisible = false }

    init {
        setBackgroundColor(Color.BLACK)
        addView(preview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(finder, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(hint, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { bottomMargin = dp(6) })
        val size = dp(48)
        addView(close, LayoutParams(size, size, Gravity.TOP or Gravity.END).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        addView(torch, LayoutParams(size, size, Gravity.TOP or Gravity.START).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
    }

    /** Resets the buttons and texts for a new scan. */
    fun reset() {
        torchOn = false
        torch.isVisible = false
        updateTorch()
        val localized = AppLanguage.localized(context)
        hint.text = localized.getString(R.string.camera_hint)
        close.contentDescription = localized.getString(R.string.camera_close)
        torch.contentDescription = localized.getString(R.string.camera_torch)
    }

    fun showTorch(available: Boolean) {
        torch.isVisible = available
    }

    private fun updateTorch() {
        torch.background.setTint(if (torchOn) palette.accent else 0x66000000)
        torch.imageTintList = android.content.res.ColorStateList.valueOf(if (torchOn) palette.accentText else Color.WHITE)
    }

    private fun button(icon: Int, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(icon)
        background = ContextCompat.getDrawable(context, R.drawable.bg_round)?.mutate()
        background.setTint(0x66000000)
        imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Darkens everything outside the frame and draws its corners and a scan line. */
    private inner class Finder(context: Context) : View(context) {
        private val shade = Paint().apply { color = 0x88000000.toInt() }
        private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val line = Paint().apply { color = 0xCCFF3B30.toInt() }
        private val frame = RectF()
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            // Wide frame: most industrial codes are 1D; QR codes fit in the middle as well.
            val fw = w * 0.8f
            val fh = (h * 0.62f).coerceAtMost(fw)
            frame.set((w - fw) / 2, (h - fh) / 2, (w + fw) / 2, (h + fh) / 2)
            canvas.drawRect(0f, 0f, w, frame.top, shade)
            canvas.drawRect(0f, frame.bottom, w, h, shade)
            canvas.drawRect(0f, frame.top, frame.left, frame.bottom, shade)
            canvas.drawRect(frame.right, frame.top, w, frame.bottom, shade)
            val stroke = 4f * resources.displayMetrics.density
            val arm = minOf(fw, fh) * 0.18f
            corner.strokeWidth = stroke
            corner.color = palette.accent
            path.reset()
            path.moveTo(frame.left, frame.top + arm); path.lineTo(frame.left, frame.top); path.lineTo(frame.left + arm, frame.top)
            path.moveTo(frame.right - arm, frame.top); path.lineTo(frame.right, frame.top); path.lineTo(frame.right, frame.top + arm)
            path.moveTo(frame.right, frame.bottom - arm); path.lineTo(frame.right, frame.bottom); path.lineTo(frame.right - arm, frame.bottom)
            path.moveTo(frame.left + arm, frame.bottom); path.lineTo(frame.left, frame.bottom); path.lineTo(frame.left, frame.bottom - arm)
            canvas.drawPath(path, corner)
            val y = frame.centerY()
            canvas.drawRect(frame.left + stroke * 2, y - stroke / 4, frame.right - stroke * 2, y + stroke / 4, line)
        }
    }
}

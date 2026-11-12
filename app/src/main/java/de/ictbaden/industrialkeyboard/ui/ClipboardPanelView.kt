package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/**
 * Clipboard in place of the keys, like Gboard: tap an entry to paste it, long press to pin it,
 * ✕ to remove it. Without `typing.clipboardHistory` it shows only what is copied right now.
 */
@SuppressLint("ViewConstructor")
class ClipboardPanelView(context: Context) : LinearLayout(context) {

    var onPaste: ((ClipItem) -> Unit)? = null
    var onTogglePin: ((ClipItem) -> Unit)? = null
    var onDelete: ((ClipItem) -> Unit)? = null
    var onBack: (() -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            rebuild()
        }

    private var items: List<ClipItem> = emptyList()
    private var history = false

    private val title = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        gravity = Gravity.CENTER_VERTICAL
    }
    private val abc = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setOnClickListener { onBack?.invoke() }
    }
    private val hint = TextView(context).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f) }
    private val list = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(12), dp(4), dp(4), dp(4))
            addView(title, LayoutParams(0, dp(40), 1f))
            addView(abc, LayoutParams(dp(72), dp(40)))
        }
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(hint, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(12), 0, dp(12), dp(4)) })
        addView(ScrollView(context).apply { addView(list) }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        rebuild()
    }

    fun show(items: List<ClipItem>, history: Boolean) {
        if (items == this.items && history == this.history) return
        this.items = items
        this.history = history
        rebuild()
    }

    private fun rebuild() {
        // Opaque: the panel lies over the keys.
        setBackgroundColor(palette.background)
        val res = AppLanguage.localized(context)
        title.text = res.getString(R.string.clipboard_title)
        title.setTextColor(palette.keyText)
        abc.setText(R.string.label_abc)
        abc.setTextColor(palette.functionText)
        abc.background = rounded(palette.function)
        hint.setTextColor(palette.keyHint)
        hint.text = res.getString(if (!history) R.string.clipboard_history_off_hint else R.string.clipboard_history_on_hint)
        hint.isVisible = true
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = res.getString(R.string.clipboard_empty)
                setTextColor(palette.keyHint)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEach { item -> list.addView(card(item), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(3), dp(8), dp(3)) }) }
    }

    private fun card(item: ClipItem) = FrameLayout(context).apply {
        background = rounded(palette.key)
        isClickable = true
        setOnClickListener { onPaste?.invoke(item) }
        if (history) setOnLongClickListener { onTogglePin?.invoke(item); true }
        addView(TextView(context).apply {
            text = item.text
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(palette.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setPadding(dp(12), dp(10), dp(if (history) 64 else 12), dp(10))
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        if (!history) return@apply
        if (item.pinned) addView(icon(R.drawable.kb_pin, palette.accent), FrameLayout.LayoutParams(dp(20), dp(20), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(10), dp(36), 0) })
        addView(icon(R.drawable.kb_close, palette.keyHint).apply {
            contentDescription = AppLanguage.localized(context).getString(R.string.clipboard_delete)
            setOnClickListener { onDelete?.invoke(item) }
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }, FrameLayout.LayoutParams(dp(32), dp(32), Gravity.TOP or Gravity.END).apply { setMargins(0, dp(4), dp(4), 0) })
    }

    private fun icon(res: Int, color: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/**
 * Snippets in place of the keys (⊞ → Snippets, the bar button): tap a card to type its text. ✎
 * opens the app's snippet screen (only while users may keep their own), ABC goes back.
 */
@SuppressLint("ViewConstructor")
class SnippetsPanelView(context: Context) : LinearLayout(context) {

    var onPick: ((PanelSnippet) -> Unit)? = null
    var onEdit: (() -> Unit)? = null
    var onBack: (() -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            if (value == field) return
            field = value
            rebuild()
        }

    private var items: List<PanelSnippet> = emptyList()
    private var editable = false

    private val title = TextView(context).apply {
        FontCap.apply(this, 15f)
        gravity = Gravity.CENTER_VERTICAL
    }
    private val edit = ImageView(context).apply {
        setImageResource(R.drawable.kb_edit)
        scaleType = ImageView.ScaleType.CENTER
        setOnClickListener { onEdit?.invoke() }
    }
    private val abc = TextView(context).apply {
        gravity = Gravity.CENTER
        FontCap.apply(this, 15f)
        setOnClickListener { onBack?.invoke() }
    }
    private val list = LinearLayout(context).apply { orientation = VERTICAL }
    private val scroll = ScrollView(context).apply { addView(list) }

    init {
        orientation = VERTICAL
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(12), dp(4), dp(4), dp(4))
            addView(title, LayoutParams(0, dp(40), 1f))
            addView(edit, LayoutParams(dp(48), dp(40)).apply { marginEnd = dp(4) })
            addView(abc, LayoutParams(dp(72), dp(40)))
        }
        addView(header, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        rebuild()
    }

    fun show(items: List<PanelSnippet>, editable: Boolean) {
        if (items == this.items && editable == this.editable) return
        val fresh = items.map { it.key } != this.items.map { it.key }
        this.items = items
        this.editable = editable
        rebuild()
        if (fresh) scroll.scrollTo(0, 0)
    }

    /** Cards of the panel (tests). */
    internal val cardCount: Int get() = list.childCount.takeIf { items.isNotEmpty() } ?: 0

    private fun rebuild() {
        // Opaque: the panel lies over the keys.
        setBackgroundColor(palette.background)
        val res = AppLanguage.localized(context)
        title.text = res.getString(R.string.snippets_title)
        title.setTextColor(palette.keyText)
        edit.isVisible = editable
        edit.contentDescription = res.getString(R.string.snippets_edit)
        edit.imageTintList = ColorStateList.valueOf(palette.functionText)
        edit.background = rounded(palette.function)
        abc.setText(R.string.label_abc)
        abc.contentDescription = res.getString(R.string.emoji_back)
        abc.setTextColor(palette.functionText)
        abc.background = rounded(palette.function)
        list.removeAllViews()
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = res.getString(if (editable) R.string.snippets_empty_user else R.string.snippets_empty_admin)
                setTextColor(palette.keyHint)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(24), dp(12), dp(24))
            }, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            return
        }
        items.forEach { item ->
            list.addView(card(item), LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(3), dp(8), dp(3)) })
        }
    }

    /** At least 56 dp high, so it can be hit with gloves. */
    private fun card(item: PanelSnippet) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(MIN_CARD_DP)
        background = rounded(palette.key)
        isClickable = true
        setPadding(dp(12), dp(8), dp(12), dp(8))
        setOnClickListener { onPick?.invoke(item) }
        addView(TextView(context).apply {
            text = item.label
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.keyText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        })
        if (item.preview.isNotEmpty() && item.preview != item.label) {
            addView(TextView(context).apply {
                text = item.preview
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setTextColor(palette.keyHint)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            })
        }
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    internal companion object {
        const val MIN_CARD_DP = 56
    }
}

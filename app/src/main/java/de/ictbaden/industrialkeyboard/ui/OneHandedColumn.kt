package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/** The free side in one-handed mode: ⇄ moves the keys to the other side, ⤢ back to full width. */
@SuppressLint("ViewConstructor")
class OneHandedColumn(context: Context) : LinearLayout(context) {

    var onSwap: (() -> Unit)? = null
    var onFull: (() -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            tint()
        }

    private val swap = button(R.drawable.kb_swap, R.string.one_handed_swap) { onSwap?.invoke() }
    private val full = button(R.drawable.kb_fullwidth, R.string.one_handed_full) { onFull?.invoke() }

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(full, LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(16) })
        addView(swap, LayoutParams(dp(48), dp(48)))
        tint()
    }

    private fun button(@DrawableRes icon: Int, @StringRes label: Int, onClick: () -> Unit) = ImageView(context).apply {
        setImageResource(icon)
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = AppLanguage.localized(context).getString(label)
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun tint() {
        listOf(swap, full).forEach {
            it.imageTintList = ColorStateList.valueOf(palette.functionText)
            it.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette.function)
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

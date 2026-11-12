package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/** The ⊞ panel of the toolbar, like Gboard's: tiles for the keyboard's other features. */
@SuppressLint("ViewConstructor")
class FeaturesPanelView(context: Context) : GridLayout(context) {

    /** One tile; [id] goes back to the keyboard service. */
    data class Feature(val id: String, @param:DrawableRes val icon: Int?, @param:StringRes val label: Int, val text: String? = null)

    var onFeature: ((String) -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            rebuild()
        }

    private var features: List<Feature> = emptyList()

    init {
        columnCount = COLUMNS
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }

    fun show(features: List<Feature>) {
        if (features == this.features) return
        this.features = features
        rebuild()
    }

    private fun rebuild() {
        // Opaque: the panel lies over the keys.
        setBackgroundColor(palette.background)
        removeAllViews()
        val res = AppLanguage.localized(context)
        features.forEach { feature ->
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(palette.key)
                    cornerRadius = dp(14).toFloat()
                }
                isClickable = true
                setOnClickListener { onFeature?.invoke(feature.id) }
                contentDescription = res.getString(feature.label)
                if (feature.icon != null) {
                    addView(ImageView(context).apply {
                        setImageResource(feature.icon)
                        imageTintList = ColorStateList.valueOf(palette.keyText)
                    }, LinearLayout.LayoutParams(dp(28), dp(28)))
                } else {
                    addView(TextView(context).apply {
                        text = feature.text
                        setTextColor(palette.keyText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(48), dp(28)))
                }
                addView(TextView(context).apply {
                    text = res.getString(feature.label)
                    setTextColor(palette.keyHint)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                    maxLines = 2
                }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            }
            addView(tile, LayoutParams(spec(UNDEFINED, 1f), spec(UNDEFINED, 1f)).apply {
                width = 0
                height = dp(84)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val COLUMNS = 4
        const val FUNCTION_KEYS = "feature:function"
        const val SCAN = "feature:scan"
        const val NUMBER_PAD = "feature:numpad"
        const val CLIPBOARD = "feature:clipboard"
        const val ONE_HANDED = "feature:onehanded"
        const val LANGUAGES = "feature:languages"
        const val SETTINGS = "feature:settings"
    }
}

package io.github.pieewiee.industrialkeyboard.app

import android.content.Context
import android.content.res.ColorStateList
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.pieewiee.industrialkeyboard.R

/** Small builder for the card-based screens of the app. */
class Ui(private val context: Context) {

    val accent: Int get() = MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0)

    fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun section(title: String): View = TextView(context).apply {
        text = title
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
        setTextColor(accent)
        setPadding(dp(8), dp(24), dp(8), dp(8))
    }

    fun card(views: List<View>, padded: Boolean = false): MaterialCardView {
        val card = MaterialCardView(context).apply {
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(color(com.google.android.material.R.attr.colorSurfaceContainerLow))
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            if (padded) setPadding(dp(16), dp(12), dp(16), dp(16))
        }
        views.forEachIndexed { index, view ->
            if (index > 0 && !padded) {
                column.addView(MaterialDivider(context).apply {
                    dividerInsetStart = dp(72)
                    dividerColor = color(com.google.android.material.R.attr.colorOutlineVariant)
                })
            }
            column.addView(view)
        }
        card.addView(column)
        return card
    }

    fun row(
        @DrawableRes icon: Int,
        title: String,
        subtitle: String?,
        trailing: View? = null,
        leading: View? = null,
        onClick: (() -> Unit)?,
    ): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(64)
        setPadding(dp(16), dp(10), dp(16), dp(10))
        if (onClick != null) {
            setBackgroundResource(ripple())
            setOnClickListener { onClick() }
        }
        addView(leading ?: iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(texts(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        trailing?.let { addView(it) }
    }

    fun switchRow(@DrawableRes icon: Int, title: String, subtitle: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit): View {
        val toggle = MaterialSwitch(context).apply {
            isChecked = checked
            isEnabled = enabled
            setOnCheckedChangeListener { _, value -> onChange(value) }
        }
        return row(icon, title, subtitle, trailing = toggle, onClick = if (enabled) ({ toggle.toggle() }) else null)
    }

    /** Title plus a group of single-choice chips. */
    fun <T> choiceRow(
        @DrawableRes icon: Int,
        title: String,
        options: List<Pair<T, String>>,
        selected: T,
        enabled: Boolean = true,
        onSelect: (T) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(10))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(texts(title, null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        })
        addView(ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            options.forEach { (value, label) ->
                addView(Chip(context).apply {
                    text = label
                    isCheckable = true
                    isChecked = value == selected
                    isEnabled = enabled
                    setOnClickListener { if (value != selected) onSelect(value) }
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(56)
            topMargin = dp(4)
        })
    }

    /** Title plus a group of multi-select chips. */
    fun <T> multiChoiceRow(
        @DrawableRes icon: Int,
        title: String,
        subtitle: String?,
        options: List<Triple<T, String, Boolean>>,
        enabled: Boolean = true,
        onChange: (T, Boolean) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(10))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(texts(title, subtitle), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
        })
        addView(ChipGroup(context).apply {
            options.forEach { (value, label, checked) ->
                addView(Chip(context).apply {
                    text = label
                    isCheckable = true
                    isChecked = checked
                    isEnabled = enabled
                    setOnCheckedChangeListener { _, isChecked -> onChange(value, isChecked) }
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(56)
            topMargin = dp(4)
        })
    }

    /** Title, value label and a slider; [onChange] fires when the finger is lifted. */
    fun sliderRow(
        @DrawableRes icon: Int,
        title: String,
        from: Int,
        to: Int,
        step: Int,
        value: Int,
        enabled: Boolean = true,
        format: (Int) -> String,
        onChange: (Int) -> Unit,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(4))
        val label = TextView(context).apply {
            text = format(value)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setTextColor(accent)
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconCircle(icon), LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(texts(title, null), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
            addView(label)
        })
        addView(Slider(context).apply {
            valueFrom = from.toFloat()
            valueTo = to.toFloat()
            stepSize = step.toFloat()
            // Material sliders only accept values on the step grid.
            this.value = ((value.coerceIn(from, to) - from + step / 2) / step * step + from).coerceAtMost(to).toFloat()
            isEnabled = enabled
            @Suppress("DEPRECATION")
            isTickVisible = false
            setLabelFormatter { format(it.toInt()) }
            addOnChangeListener { _, v, _ -> label.text = format(v.toInt()) }
            addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) = Unit
                override fun onStopTrackingTouch(slider: Slider) = onChange(slider.value.toInt())
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(44) })
    }

    fun texts(title: String, subtitle: String?) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(context).apply {
            text = title
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
        })
        subtitle?.let {
            addView(TextView(context).apply {
                text = it
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            })
        }
    }

    fun iconCircle(@DrawableRes icon: Int) = android.widget.ImageView(context).apply {
        setImageResource(icon)
        scaleType = android.widget.ImageView.ScaleType.CENTER
        imageTintList = ColorStateList.valueOf(accent)
        setBackgroundResource(R.drawable.bg_tile_icon)
        backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(accent, 0x1F))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun textField(hint: String, value: String = "", password: Boolean = false, inputType: Int = InputType.TYPE_CLASS_TEXT, helper: String? = null) =
        TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = hint
            helper?.let { helperText = it }
            if (password) endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            addView(TextInputEditText(this.context).apply {
                setText(value)
                isSingleLine = true
                this.inputType = if (password) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else inputType
                // Password fields switch to monospace, which the floating hint would inherit.
                if (password) typeface = android.graphics.Typeface.DEFAULT
            })
        }

    /** Vertical form with dialog padding. */
    fun form(vararg views: View): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(8), dp(24), 0)
        views.forEach { view ->
            addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        }
    }

    fun icon(@DrawableRes res: Int) = ContextCompat.getDrawable(context, res)!!.mutate().apply { setTint(accent) }

    fun color(attr: Int) = MaterialColors.getColor(context, attr, 0)

    private fun ripple(): Int = TypedValue().also {
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
    }.resourceId
}

val TextInputLayout.value: String get() = editText?.text?.toString().orEmpty()

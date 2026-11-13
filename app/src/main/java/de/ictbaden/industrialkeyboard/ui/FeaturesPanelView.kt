package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Layout
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.QuickToggle

/**
 * The ⊞ panel of the toolbar, like Gboard's: tiles for the keyboard's other features. It grows with
 * its tiles; [KeyboardRootView.featuresScroll] scrolls it when they are taller than the keys.
 *
 * The tiles are only built anew when tiles come or go; a switched tile or a new palette changes the
 * existing views, so a screen reader keeps its focus on the tapped tile and says the new state.
 */
@SuppressLint("ViewConstructor")
class FeaturesPanelView(context: Context) : GridLayout(context) {

    /**
     * One tile; [id] goes back to the keyboard service. [checked]: `null` = an action tile, otherwise
     * an on/off switch (quick toggles) – lit in the accent colour while on, with "On"/"Off" for
     * screen readers.
     */
    data class Feature(
        val id: String,
        @param:DrawableRes val icon: Int?,
        @param:StringRes val label: Int,
        val text: String? = null,
        val checked: Boolean? = null,
    )

    var onFeature: ((String) -> Unit)? = null

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            // Set on every render of the keyboard: only a new palette touches the tiles.
            if (value == field) return
            field = value
            update()
        }

    private var features: List<Feature> = emptyList()

    /** The views of one tile, to change them in place. */
    private class Tile(val view: LinearLayout, val icon: ImageView?, val glyph: TextView?, val label: TextView)

    private val tiles = mutableListOf<Tile>()

    init {
        columnCount = COLUMNS
        setPadding(dp(8), dp(8), dp(8), dp(8))
        update()
    }

    fun show(features: List<Feature>) {
        val sameTiles = features.size == this.features.size &&
            features.zip(this.features).all { (new, old) -> new.copy(checked = null) == old.copy(checked = null) && (new.checked == null) == (old.checked == null) }
        this.features = features
        if (!sameTiles) rebuild()
        update()
    }

    private fun rebuild() {
        removeAllViews()
        tiles.clear()
        features.forEachIndexed { index, feature ->
            val view = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                setOnClickListener { onFeature?.invoke(feature.id) }
            }
            if (feature.checked != null) {
                // A switch for screen readers ("Gloves, On, switch"), not a selected item.
                ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = Switch::class.java.name
                        info.isCheckable = true
                        info.isChecked = features.getOrNull(index)?.checked == true
                        info.isSelected = false
                    }
                })
            }
            val icon = feature.icon?.let { res ->
                ImageView(context).apply { setImageResource(res) }.also { view.addView(it, LinearLayout.LayoutParams(dp(28), dp(28))) }
            }
            val glyph = if (feature.icon == null) {
                TextView(context).apply {
                    text = feature.text
                    FontCap.apply(this, 18f)
                    gravity = Gravity.CENTER
                }.also { view.addView(it, LinearLayout.LayoutParams(dp(48), dp(28))) }
            } else {
                null
            }
            val label = TextView(context).apply {
                // The tile has a fixed height: the label follows large fonts only up to FontCap.
                FontCap.apply(this, 12f)
                gravity = Gravity.CENTER
                maxLines = 2
                // German labels are long ("Funktionstasten", "Zwischenablage"): hyphenate them in the
                // keyboard's language instead of breaking anywhere ("Funktionstast|en" on the HC20).
                // TextView already breaks with the high-quality strategy, which hyphenation needs.
                hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NORMAL
            }
            view.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
            addView(view, LayoutParams(spec(UNDEFINED, 1f), spec(UNDEFINED, 1f)).apply {
                width = 0
                height = dp(84)
                setMargins(dp(4), dp(4), dp(4), dp(4))
            })
            // The tile speaks for itself (its content description); its parts would be read twice.
            listOfNotNull(icon, glyph, label).forEach { it.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
            tiles += Tile(view, icon, glyph, label)
        }
    }

    /** Colours, texts and on/off state of the existing tiles. */
    private fun update() {
        // Opaque: the panel lies over the keys.
        setBackgroundColor(palette.background)
        val res = AppLanguage.localized(context)
        features.zip(tiles).forEach { (feature, tile) ->
            val on = feature.checked == true
            val foreground = if (on) palette.accentText else palette.keyText
            tile.view.background = GradientDrawable().apply {
                setColor(if (on) palette.accent else palette.key)
                cornerRadius = dp(14).toFloat()
                // High contrast: black tiles on black only show their outline, like the keys.
                if (!on) palette.outline(this, resources.displayMetrics.density)
            }
            val label = res.getString(feature.label)
            // Hyphenation follows the text's language, which is the keyboard's, not always the device's.
            tile.label.textLocales = res.resources.configuration.locales
            tile.view.contentDescription = label
            tile.icon?.imageTintList = ColorStateList.valueOf(foreground)
            tile.glyph?.setTextColor(foreground)
            tile.label.text = label
            tile.label.setTextColor(if (on) palette.accentText else palette.keyHint)
            feature.checked?.let { checked ->
                tile.view.isSelected = checked
                // Unchanged text sends nothing; a new one is spoken on the focused tile.
                ViewCompat.setStateDescription(tile.view, res.getString(if (checked) R.string.state_on else R.string.state_off))
            }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val COLUMNS = 4
        const val FUNCTION_KEYS = "feature:function"
        const val SCAN = "feature:scan"
        const val NUMBER_PAD = "feature:numpad"
        const val CLIPBOARD = "feature:clipboard"
        const val SNIPPETS = "feature:snippets"
        const val TEXT_EDITING = "feature:textedit"
        const val ONE_HANDED = "feature:onehanded"
        const val LANGUAGES = "feature:languages"
        const val SETTINGS = "feature:settings"

        /** Quick toggle tiles: `toggle:glove` … */
        const val TOGGLE_PREFIX = "toggle:"

        fun toggleId(t: QuickToggle) = TOGGLE_PREFIX + t.key

        fun toggleOf(id: String): QuickToggle? = QuickToggle.entries.firstOrNull { toggleId(it) == id }
    }
}

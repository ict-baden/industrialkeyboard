package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import kotlin.math.min

/**
 * Text sizes in the keyboard's fixed-height views: they follow the system font size, but only up to
 * [MAX_SCALE], so that a label never clips in a tile or button of fixed height.
 */
object FontCap {
    const val MAX_SCALE = 1.3f

    /** [sp] in pixels with the font scale capped at [maxScale]. */
    fun px(context: Context, sp: Float, maxScale: Float = MAX_SCALE): Float {
        val config = context.resources.configuration
        return sp * context.resources.displayMetrics.density * min(config.fontScale, maxScale)
    }

    /** Sets [sp] on [view], capped like [px]. */
    fun apply(view: TextView, sp: Float, maxScale: Float = MAX_SCALE) = view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(view.context, sp, maxScale))
}

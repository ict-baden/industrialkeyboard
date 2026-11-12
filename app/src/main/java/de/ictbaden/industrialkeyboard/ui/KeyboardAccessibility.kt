package de.ictbaden.industrialkeyboard.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.customview.widget.ExploreByTouchHelper
import de.ictbaden.industrialkeyboard.R

/**
 * One screen-reader node per key of a [KeyboardView] (virtual views): name, state, and the actions
 * tap, long press and the long-press alternatives (ä, é …) as custom actions. The ids are the
 * indices of the keys in row order, which is also the reading order.
 */
internal class KeyboardAccessibility(private val view: KeyboardView) : ExploreByTouchHelper(view) {

    private val describer = KeyDescriber(view.context)
    private val bounds = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int = view.a11yIndexAt(x, y).takeIf { it >= 0 } ?: INVALID_ID

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (i in 0 until view.a11yKeyCount()) virtualViewIds += i
    }

    @Suppress("DEPRECATION") // setBoundsInParent: ExploreByTouchHelper computes the screen bounds from it.
    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        val key = view.a11yKey(virtualViewId)
        if (key == null) {
            // The keys changed since the node was asked for: an empty node, no actions.
            node.contentDescription = ""
            node.setBoundsInParent(EMPTY)
            return
        }
        val c = view.a11yContext()
        node.className = KEY_CLASS
        node.contentDescription = describer.describe(key, c)
        describer.state(key, c)?.let { node.stateDescription = it }
        view.a11yRect(virtualViewId, bounds)
        node.setBoundsInParent(bounds)
        val enabled = describer.enabled(key, c)
        node.isEnabled = enabled
        if (!enabled) return
        node.addAction(AccessibilityActionCompat.ACTION_CLICK)
        describer.longPressLabel(key, c)?.let { node.addAction(AccessibilityActionCompat(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, it)) }
        describer.alternatives(key, c).forEachIndexed { i, option ->
            node.addAction(AccessibilityActionCompat(ALTERNATIVE_IDS[i], describer.alternativeLabel(option, i, c)))
        }
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        val done = when (action) {
            AccessibilityNodeInfoCompat.ACTION_CLICK -> view.performA11yKey(virtualViewId, click = true)
            AccessibilityNodeInfoCompat.ACTION_LONG_CLICK -> view.performA11yLongPress(virtualViewId)
            else -> {
                val index = ALTERNATIVE_IDS.indexOf(action)
                val key = view.a11yKey(virtualViewId)
                val option = key?.let { describer.alternatives(it, view.a11yContext()).getOrNull(index) }
                option != null && view.performA11yAlternative(virtualViewId, option)
            }
        }
        if (done) keyPerformed(virtualViewId)
        return done
    }

    /** After a key acted: TalkBack hears the click and reads the changed name ("Shift on"). */
    fun keyPerformed(virtualViewId: Int) {
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
        invalidateVirtualView(virtualViewId)
    }

    companion object {
        /** Like the keys of AOSP's LatinIME and Gboard, so that TalkBack treats the nodes as keyboard keys. */
        const val KEY_CLASS = "android.inputmethodservice.Keyboard\$Key"

        private val EMPTY = Rect()

        val ALTERNATIVE_IDS = intArrayOf(
            R.id.a11y_alt_0, R.id.a11y_alt_1, R.id.a11y_alt_2, R.id.a11y_alt_3, R.id.a11y_alt_4,
            R.id.a11y_alt_5, R.id.a11y_alt_6, R.id.a11y_alt_7, R.id.a11y_alt_8, R.id.a11y_alt_9,
        )
    }
}

package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.ModifierKey

/** Everything the keyboard frame shows; built by the keyboard service (or a preview). */
data class RenderState(
    val palette: Palette,
    /** Tab bar entries; `null` hides the bar (classic navigation). */
    val tabs: List<TabBarView.Tab>?,
    val selectedTab: String?,
    /** Key layout, or `null` for the scan pad. */
    val layout: KeyboardLayout?,
    val shift: ShiftState,
    val modifiers: Map<ModifierKey, ShiftState> = emptyMap(),
    @param:DrawableRes val enterIcon: Int = R.drawable.kb_enter,
    val rowHeight: Int,
    val heightRows: Int,
    val keyPopups: Boolean,
    val longPressDelay: Long,
    /** 50–100 %. */
    val widthPercent: Int,
    val scannerName: String?,
    val scanning: Boolean,
    /** The scanner is the camera (tap opens the picture, no hold-to-scan). */
    val camera: Boolean = false,
)

/**
 * The keyboard frame: transparent headroom for previews, tab bar, keys or scan pad, and the
 * popup overlay. Only the panel (below the headroom) is touchable inside the IME window.
 */
@SuppressLint("ViewConstructor")
class KeyboardRootView(context: Context, headroomDp: Float = 76f) : FrameLayout(context) {

    val panel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val tabBar = TabBarView(context)
    val keyboard = KeyboardView(context)
    val scanPad = ScanPadView(context)
    val scanRow = KeyboardView(context).apply { heightRows = 1 }
    val overlay = PopupOverlay(context)

    /** Camera picture over the keys while scanning with the camera; gone otherwise. */
    val camera = CameraScanView(context).apply { visibility = GONE }

    private val content = FrameLayout(context)
    private val scanPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val headroom = (headroomDp * resources.displayMetrics.density).toInt()
    private var state: RenderState? = null
    private var navigationInset = 0

    init {
        clipChildren = false
        panel.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scanPage.addView(scanPad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        scanPage.addView(scanRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(scanPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // Same size as the keys: the frame keeps its height, the tab bar stays in place.
        content.addView(camera, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { topMargin = headroom })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        keyboard.popupHost = overlay
        scanRow.popupHost = overlay
        scanRow.layout = Layouts.scanRow()
        keyboard.setPadding(dp(3), dp(4), dp(3), dp(4))
        scanRow.setPadding(dp(3), 0, dp(3), dp(4))
        // Android 15+ draws the IME behind the navigation bar: keep the keys above it.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            // With gesture navigation the bar itself is only the handle (24 dp), but below the keyboard
            // Android also draws its own buttons (hide ⌄, switch keyboard 🌐) – they count as a
            // tappable element (48 dp). Keep the keys above both, like Gboard.
            val bar = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val tappable = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
            val buttons = tappable > bar
            // Gboard keeps another ~13 dp between its keys and those buttons (measured on a Pixel 9 Pro).
            navigationInset = maxOf(bar, tappable) + if (buttons) dp(13) else 0
            if (buttons != systemImeButtons) {
                systemImeButtons = buttons
                onSystemImeButtonsChanged?.invoke()
            }
            panel.updatePadding(bottom = navigationInset)
            insets
        }
    }

    /** Android draws its own hide and switch-keyboard buttons below the keyboard (gesture navigation). */
    var systemImeButtons = false
        private set
    var onSystemImeButtonsChanged: (() -> Unit)? = null

    /** Top of the touchable keyboard panel inside this view. */
    val panelTop: Int get() = panel.top

    /** Exactly headroom plus panel: the overlay must not stretch the frame to the available height. */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        panel.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
        )
        val height = headroom + panel.measuredHeight
        overlay.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        setMeasuredDimension(width, height)
    }

    fun render(state: RenderState) {
        val previous = this.state
        this.state = state
        val p = state.palette
        panel.setBackgroundColor(p.background)
        overlay.palette = p
        tabBar.palette = p
        tabBar.isVisible = state.tabs != null
        state.tabs?.let { tabBar.setTabs(it, state.selectedTab, animate = previous != null) }

        val sidePadding = if (state.widthPercent < 100) (width * (100 - state.widthPercent) / 200) else 0
        content.updatePadding(left = sidePadding, right = sidePadding)
        listOf(keyboard, scanRow).forEach {
            it.palette = p
            it.shift = state.shift
            it.modifiers = state.modifiers
            it.enterIcon = state.enterIcon
            it.rowHeight = state.rowHeight
            it.keyPopups = state.keyPopups
            it.longPressDelay = state.longPressDelay
        }
        keyboard.heightRows = state.heightRows
        val scan = state.layout == null
        keyboard.isVisible = !scan
        scanPage.isVisible = scan
        if (!scan && keyboard.layout != state.layout) keyboard.layout = state.layout
        scanPad.palette = p
        camera.palette = p
        scanPad.scannerName = state.scannerName
        scanPad.camera = state.camera
        scanPad.scanning = state.scanning
        // Same total height as the key pages (incl. their padding), so the tab bar does not jump.
        (scanPad.layoutParams as LinearLayout.LayoutParams).height = state.rowHeight * (state.heightRows - 1) +
            keyboard.paddingTop + keyboard.paddingBottom - scanRow.paddingTop - scanRow.paddingBottom
        scanPad.requestLayout()
        // The camera picture takes exactly the place of the keys, not the whole screen.
        (camera.layoutParams as LayoutParams).height = state.rowHeight * state.heightRows + keyboard.paddingTop + keyboard.paddingBottom
        camera.requestLayout()
        if (previous?.widthPercent != state.widthPercent) post { requestLayout() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw != w) state?.let { s -> post { render(s) } }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

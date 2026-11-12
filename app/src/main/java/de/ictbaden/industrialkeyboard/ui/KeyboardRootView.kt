package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.annotation.DrawableRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.Tabs
import de.ictbaden.industrialkeyboard.config.OneHanded
import de.ictbaden.industrialkeyboard.layout.KeyboardLayout
import de.ictbaden.industrialkeyboard.layout.Layouts
import de.ictbaden.industrialkeyboard.layout.ModifierKey
import de.ictbaden.industrialkeyboard.layout.TextEditState

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
    /** Letter keys as tiles (`appearance.keyBorders`). */
    val keyBorders: Boolean = true,
    /** Swipe left from Backspace deletes words. */
    val deleteGesture: Boolean = true,
    /** Microphone button for dictation in the tab bar. */
    val mic: Boolean = false,
    val longPressDelay: Long,
    /** 50–100 %. */
    val widthPercent: Int,
    val scannerName: String?,
    val scanning: Boolean,
    /** The scanner is the camera (tap opens the picture, no hold-to-scan). */
    val camera: Boolean = false,
    /** Word suggestions, best first: shown instead of the tab bar while not empty. */
    val suggestions: List<String> = emptyList(),
    /** The emoji panel replaces the keys. */
    val emojiOpen: Boolean = false,
    /** Gboard's toolbar instead of the tabs (icons evenly spread). */
    val toolbar: Boolean = false,
    /** Open toolbar panel (features ⊞, clipboard or text editing) in place of the keys. */
    val toolPanel: String? = null,
    val clips: List<ClipItem> = emptyList(),
    val clipboardHistory: Boolean = false,
    /** One-handed mode: keys narrower at one side. */
    val oneHanded: OneHanded = OneHanded.OFF,
    /** Text editing panel open ([Tabs.TOOL_EDIT], drawn as [layout]): Select lit, Cut/Copy dimmed. */
    val textEdit: TextEditState? = null,
    /** Entries of the snippets panel ([Tabs.TOOL_SNIPPETS]); empty while it is closed. */
    val snippets: List<PanelSnippet> = emptyList(),
    /** ✎ in the snippets panel: users may keep their own snippets. */
    val snippetsEditable: Boolean = false,
    /** Label of the snippet whose shortcut was typed: the middle of the suggestion strip. */
    val snippetSuggestion: String? = null,
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

    /** Word suggestions in place of the tab bar while typing a word. */
    val suggestionStrip = SuggestionStripView(context).apply { visibility = GONE }

    /** Emoji in place of the keys. */
    val emojiPanel = EmojiPanelView(context).apply { visibility = GONE }

    /** Dictation in place of the keys (issue #23); gone otherwise. */
    val voicePanel = VoicePanelView(context).apply { visibility = GONE }

    /** Toolbar ⊞: tiles for the keyboard's features, in place of the keys (inside [featuresScroll]). */
    val featuresPanel = FeaturesPanelView(context)

    /**
     * Scrolls the ⊞ tiles when there are more rows than fit into the height of the keys; shown and
     * hidden instead of [featuresPanel] (screen-reader pane titles belong here, too).
     */
    val featuresScroll = ScrollView(context).apply {
        isFillViewport = true
        visibility = GONE
        addView(featuresPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Toolbar 📋: clipboard entries in place of the keys. */
    val clipboardPanel = ClipboardPanelView(context).apply { visibility = GONE }

    /** Snippets (⊞ → Snippets, the bar button) in place of the keys. */
    val snippetsPanel = SnippetsPanelView(context).apply { visibility = GONE }

    /** One-handed mode: the free side with ⇄ and ⤢ (left column when the keys sit right, and the other way). */
    val oneHandedLeft = OneHandedColumn(context).apply { visibility = GONE }
    val oneHandedRight = OneHandedColumn(context).apply { visibility = GONE }

    /** Camera picture over the keys while scanning with the camera; gone otherwise. */
    val camera = CameraScanView(context).apply { visibility = GONE }

    private val content = FrameLayout(context)
    private val contentRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private val scanPage = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val headroom = (headroomDp * resources.displayMetrics.density).toInt()
    private var state: RenderState? = null
    private var navigationInset = 0

    init {
        clipChildren = false
        panel.addView(tabBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        panel.addView(suggestionStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        contentRow.addView(oneHandedLeft, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        contentRow.addView(content, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        contentRow.addView(oneHandedRight, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        panel.addView(contentRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(keyboard, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        scanPage.addView(scanPad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        scanPage.addView(scanRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        content.addView(scanPage, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        // Same size as the keys: the frame keeps its height, the tab bar stays in place.
        content.addView(camera, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(emojiPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(voicePanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(featuresScroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(clipboardPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        content.addView(snippetsPanel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply { topMargin = headroom })
        addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        keyboard.popupHost = overlay
        overlay.dimTop = headroom.toFloat()
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
        // Like Gboard: while a word is typed, suggestions take the place of the tab bar.
        val suggesting = (state.suggestions.isNotEmpty() || state.snippetSuggestion != null) && !state.emojiOpen
        tabBar.showMic = state.mic
        tabBar.spread = state.toolbar
        tabBar.isVisible = state.tabs != null && !suggesting
        suggestionStrip.isVisible = suggesting
        suggestionStrip.palette = p
        suggestionStrip.words = state.suggestions
        suggestionStrip.snippet = state.snippetSuggestion
        suggestionStrip.showMic = state.mic
        emojiPanel.palette = p
        if (state.emojiOpen) emojiPanel.open()
        emojiPanel.isVisible = state.emojiOpen
        state.tabs?.let { tabBar.setTabs(it, state.selectedTab, animate = previous != null) }

        // One-handed (Gboard): keys about 80 % wide, the buttons ⇄/⤢ on the free side.
        val side = if (state.oneHanded == OneHanded.OFF) 0 else (width * ONE_HANDED_SIDE).toInt()
        oneHandedLeft.isVisible = state.oneHanded == OneHanded.RIGHT
        oneHandedRight.isVisible = state.oneHanded == OneHanded.LEFT
        listOf(oneHandedLeft, oneHandedRight).forEach {
            it.palette = p
            (it.layoutParams as LinearLayout.LayoutParams).width = side
        }
        val sidePadding = if (state.widthPercent < 100 && side == 0) (width * (100 - state.widthPercent) / 200) else 0
        content.updatePadding(left = sidePadding, right = sidePadding)
        val panelOpen = state.toolPanel
        featuresPanel.palette = p
        featuresScroll.setBackgroundColor(p.background)
        clipboardPanel.palette = p
        featuresScroll.isVisible = panelOpen == Tabs.TOOL_MENU
        // Like Gboard: ⊞ opens at the top; while it stays open (a toggle tap) it keeps its place.
        if (panelOpen == Tabs.TOOL_MENU && previous?.toolPanel != Tabs.TOOL_MENU) featuresScroll.scrollTo(0, 0)
        clipboardPanel.isVisible = panelOpen == Tabs.TOOL_CLIPBOARD
        if (panelOpen == Tabs.TOOL_CLIPBOARD) clipboardPanel.show(state.clips, state.clipboardHistory)
        snippetsPanel.palette = p
        snippetsPanel.isVisible = panelOpen == Tabs.TOOL_SNIPPETS
        if (panelOpen == Tabs.TOOL_SNIPPETS) snippetsPanel.show(state.snippets, state.snippetsEditable)
        listOf(keyboard, scanRow).forEach {
            it.palette = p
            it.shift = state.shift
            it.modifiers = state.modifiers
            it.enterIcon = state.enterIcon
            it.rowHeight = state.rowHeight
            it.keyPopups = state.keyPopups
            it.keyBorders = state.keyBorders
            it.deleteGesture = state.deleteGesture
            it.longPressDelay = state.longPressDelay
        }
        keyboard.heightRows = state.heightRows
        // Separate from the layout: a new layout would stop the key repeat of a held ←.
        keyboard.editState = state.textEdit
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
        (emojiPanel.layoutParams as LayoutParams).height = (camera.layoutParams as LayoutParams).height
        (voicePanel.layoutParams as LayoutParams).height = (camera.layoutParams as LayoutParams).height
        (featuresScroll.layoutParams as LayoutParams).height = (camera.layoutParams as LayoutParams).height
        (clipboardPanel.layoutParams as LayoutParams).height = (camera.layoutParams as LayoutParams).height
        (snippetsPanel.layoutParams as LayoutParams).height = (camera.layoutParams as LayoutParams).height
        featuresScroll.requestLayout()
        clipboardPanel.requestLayout()
        snippetsPanel.requestLayout()
        contentRow.requestLayout()
        voicePanel.palette = p
        voicePanel.requestLayout()
        emojiPanel.requestLayout()
        camera.requestLayout()
        if (previous?.widthPercent != state.widthPercent) post { requestLayout() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (oldw != w) state?.let { s -> post { render(s) } }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private companion object {
        /** Share of the width the free side takes in one-handed mode (Gboard: about a fifth). */
        const val ONE_HANDED_SIDE = 0.2f
    }
}

package io.github.pieewiee.industrialkeyboard.ime

import android.content.Context
import androidx.annotation.DrawableRes
import io.github.pieewiee.industrialkeyboard.R
import io.github.pieewiee.industrialkeyboard.config.KeyboardConfig
import io.github.pieewiee.industrialkeyboard.config.Tabs
import io.github.pieewiee.industrialkeyboard.layout.KeyboardLayout
import io.github.pieewiee.industrialkeyboard.layout.Layouts
import io.github.pieewiee.industrialkeyboard.ui.Palette
import io.github.pieewiee.industrialkeyboard.ui.RenderState
import io.github.pieewiee.industrialkeyboard.ui.ShiftState
import io.github.pieewiee.industrialkeyboard.ui.TabBarView

/** Builds what the keyboard frame shows; shared by the input method and the settings preview. */
object KeyboardRenderer {
    const val BASE_ROW_DP = 54f

    fun layoutFor(page: Page, config: KeyboardConfig, shift: ShiftState = ShiftState.OFF): KeyboardLayout? = when (page) {
        is Page.Alpha -> Layouts.alpha(config, page.variant)
        is Page.Symbols -> Layouts.symbols(config, page.page, page.variant)
        is Page.Numeric -> Layouts.numeric(config, page.variant)
        Page.Function -> Layouts.function(shifted = shift != ShiftState.OFF)
        Page.Scan -> null
        is Page.Custom -> config.customLayout(page.id)?.let(Layouts::custom) ?: Layouts.alpha(config)
    }

    fun tabs(config: KeyboardConfig, scanAvailable: Boolean): List<TabBarView.Tab>? =
        FieldPolicy.tabs(config, scanAvailable).takeIf { it.isNotEmpty() }?.map { id ->
            when (id) {
                Tabs.ALPHA -> TabBarView.Tab(id, "ABC")
                Tabs.NUMERIC -> TabBarView.Tab(id, "123")
                Tabs.SYMBOLS -> TabBarView.Tab(id, "#+=")
                Tabs.FUNCTION -> TabBarView.Tab(id, "Fn")
                Tabs.SCAN -> TabBarView.Tab(id, null, R.drawable.kb_scan)
                else -> TabBarView.Tab(id, config.customLayout(id)?.label ?: id)
            }
        }

    fun state(
        context: Context,
        config: KeyboardConfig,
        page: Page,
        shift: ShiftState,
        scannerName: String?,
        @DrawableRes enterIcon: Int = R.drawable.kb_enter,
        scanning: Boolean = false,
    ): RenderState {
        val appearance = config.appearance
        val rowHeight = (BASE_ROW_DP * context.resources.displayMetrics.density * appearance.keyHeight / 100f).toInt()
        // Without a scanner the scan key would do nothing.
        val keys = if (scannerName == null && config.scanner.scanKey) config.copy(scanner = config.scanner.copy(scanKey = false)) else config
        return RenderState(
            palette = Palette.resolve(context, appearance),
            tabs = tabs(config, scannerName != null),
            selectedTab = page.tab,
            layout = layoutFor(page, keys, shift),
            shift = shift,
            enterIcon = enterIcon,
            rowHeight = rowHeight,
            heightRows = Layouts.alpha(config).rows.size,
            keyPopups = config.feedback.keyPopup,
            longPressDelay = config.typing.longPressDelay.toLong(),
            widthPercent = appearance.width,
            scannerName = scannerName,
            scanning = scanning,
        )
    }
}

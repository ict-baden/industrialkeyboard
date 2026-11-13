package de.ictbaden.industrialkeyboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestionsRequest
import android.view.inputmethod.InlineSuggestionsResponse
import android.widget.inline.InlinePresentationSpec
import androidx.annotation.RequiresApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.common.ImageViewStyle
import androidx.autofill.inline.common.TextViewStyle
import androidx.autofill.inline.common.ViewStyle
import androidx.autofill.inline.v1.InlineSuggestionUi
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.ui.Palette

/**
 * Inline autofill suggestions (Android 11+): the autofill service (Google, a password manager) offers
 * e-mail addresses, addresses, passwords or one-time codes for the focused field; the system draws
 * them as chips in our colours, the keyboard only places them. Their content never reaches the
 * keyboard – the chips are views of the autofill service.
 */
@RequiresApi(Build.VERSION_CODES.R)
object InlineAutofill {

    private const val TAG = "InlineAutofill"
    private const val MAX_SUGGESTIONS = 8

    /** The request: up to [MAX_SUGGESTIONS] chips, styled like the keys of [palette]. */
    fun request(context: Context, palette: Palette, uiExtras: Bundle): InlineSuggestionsRequest {
        val d = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val builder = InlinePresentationSpec.Builder(Size(dp(48), dp(36)), Size(dp(320), dp(40)))
        if (UiVersions.INLINE_UI_VERSION_1 in UiVersions.getVersions(uiExtras)) builder.setStyle(styles(context, palette, ::dp))
        return InlineSuggestionsRequest.Builder(listOf(builder.build()))
            .setMaxSuggestionCount(MAX_SUGGESTIONS)
            .build()
    }

    // The builders' setters live in a restricted generic base class; calling them through the public
    // builders is the documented use (lint cannot tell the two apart).
    @SuppressLint("RestrictedApi")
    private fun styles(context: Context, palette: Palette, dp: (Int) -> Int): Bundle {
        val chip = ViewStyle.Builder()
            .setBackground(Icon.createWithResource(context, R.drawable.bg_inline_chip).setTint(palette.key))
            .setPadding(dp(12), 0, dp(12), 0)
            .setLayoutMargin(0, 0, 0, 0)
            .build()
        val icon = ImageViewStyle.Builder()
            .setLayoutMargin(0, 0, dp(6), 0)
            .setMaxWidth(dp(24))
            .setMaxHeight(dp(24))
            .build()
        val style = InlineSuggestionUi.newStyleBuilder()
            .setSingleIconChipStyle(chip)
            .setChipStyle(chip)
            .setStartIconStyle(icon)
            .setEndIconStyle(icon)
            .setSingleIconChipIconStyle(ImageViewStyle.Builder().setMaxWidth(dp(24)).setMaxHeight(dp(24)).setTintList(ColorStateList.valueOf(palette.keyText)).build())
            .setTitleStyle(TextViewStyle.Builder().setTextColor(palette.keyText).setTextSize(15f).setLayoutMargin(0, 0, dp(4), 0).build())
            .setSubtitleStyle(TextViewStyle.Builder().setTextColor(palette.keyHint).setTextSize(13f).build())
            .build()
        return UiVersions.newStylesBuilder().addStyle(style).build()
    }

    /**
     * Inflates the chips of [response] (the system draws them asynchronously) and hands them to
     * [show] in the service's order, on the main thread; an empty list removes the chips.
     */
    fun inflate(context: Context, response: InlineSuggestionsResponse, show: (List<View>) -> Unit) {
        val suggestions = response.inlineSuggestions
        if (suggestions.isEmpty()) return show(emptyList())
        val views = arrayOfNulls<View>(suggestions.size)
        var pending = suggestions.size
        val size = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        suggestions.forEachIndexed { i, suggestion ->
            try {
                suggestion.inflate(context, size, context.mainExecutor) { view ->
                    views[i] = view
                    if (--pending == 0) show(views.filterNotNull())
                }
            } catch (e: Exception) {
                // A suggestion the system cannot draw (size outside the spec): left out.
                Log.i(TAG, "Inline suggestion not shown (${e.javaClass.simpleName})")
                if (--pending == 0) show(views.filterNotNull())
            }
        }
    }
}

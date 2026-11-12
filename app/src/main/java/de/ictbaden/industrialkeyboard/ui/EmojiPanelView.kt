package de.ictbaden.industrialkeyboard.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R

/**
 * Emoji in place of the keys, like Gboard: AndroidX emoji picker (categories, skin tones) and a
 * row with ABC and backspace. Recently used emoji are kept only in memory, so nothing typed is
 * stored on the device (the picker would otherwise write them to its preferences).
 */
@SuppressLint("ViewConstructor")
class EmojiPanelView(context: Context) : LinearLayout(context) {

    var onEmoji: ((String) -> Unit)? = null
    var onBack: (() -> Unit)? = null
    var onBackspace: (() -> Unit)? = null

    private val themed = ContextThemeWrapper(context, R.style.Theme_IndustrialKeyboard)
    private var picker: EmojiPickerView? = null
    private val pickerHost = FrameLayout(context)
    private val abc = TextView(context).apply {
        setText(R.string.label_abc)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setOnClickListener { onBack?.invoke() }
    }
    private val backspace = ImageButton(context).apply {
        setImageResource(R.drawable.kb_backspace)
        setOnClickListener { onBackspace?.invoke() }
    }

    var palette: Palette = Palette.light(Palette.DEFAULT_ACCENT)
        set(value) {
            field = value
            applyPalette()
        }

    init {
        orientation = VERTICAL
        addView(pickerHost, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            addView(abc, LayoutParams(dp(72), dp(44)))
            addView(FrameLayout(context), LayoutParams(0, 0, 1f))
            addView(backspace, LayoutParams(dp(72), dp(44)))
        }
        addView(bar, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        applyPalette()
    }

    /** Creates the picker the first time the panel opens: it loads the emoji list. */
    fun open() {
        if (picker != null) return
        val view = EmojiPickerView(themed).apply {
            setRecentEmojiProvider(SessionRecents)
            setOnEmojiPickedListener { item -> onEmoji?.invoke(item.emoji) }
        }
        picker = view
        pickerHost.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val localized = AppLanguage.localized(context)
        backspace.contentDescription = localized.getString(R.string.emoji_backspace)
        abc.contentDescription = localized.getString(R.string.emoji_back)
    }

    private fun applyPalette() {
        setBackgroundColor(palette.background)
        listOf(abc, backspace).forEach {
            it.background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(palette.function)
            }
        }
        abc.setTextColor(palette.functionText)
        backspace.imageTintList = ColorStateList.valueOf(palette.functionText)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    /** Recent emoji for this process only – never written to storage. */
    private object SessionRecents : RecentEmojiProvider {
        private val recent = ArrayDeque<String>()

        @Synchronized
        override fun recordSelection(emoji: String) {
            recent.remove(emoji)
            recent.addFirst(emoji)
            while (recent.size > MAX) recent.removeLast()
        }

        override suspend fun getRecentEmojiList(): List<String> = synchronized(this) { recent.toList() }

        private const val MAX = 24
    }
}

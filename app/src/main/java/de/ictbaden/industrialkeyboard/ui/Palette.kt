package de.ictbaden.industrialkeyboard.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import de.ictbaden.industrialkeyboard.config.Appearance
import de.ictbaden.industrialkeyboard.config.ThemeMode

/** Colors of one keyboard theme. */
data class Palette(
    val background: Int,
    val key: Int,
    val keyText: Int,
    val keyHint: Int,
    val function: Int,
    val functionText: Int,
    val accent: Int,
    val accentText: Int,
    /** Overlay drawn on a pressed key. */
    val pressed: Int,
    val shadow: Int,
    /** Outline around keys; 0 = none. */
    val border: Int,
    val tabBar: Int,
    val tabText: Int,
    val popup: Int,
    val popupText: Int,
    val dark: Boolean,
) {
    companion object {
        const val DEFAULT_ACCENT = 0xFF2563EB.toInt()
        const val CONTRAST_ACCENT = 0xFFFFD600.toInt()

        fun resolve(context: Context, appearance: Appearance): Palette {
            val accent = parse(appearance.accentColor)
            // Like Gboard: the system colours from the wallpaper, unless a brand colour is set.
            if (accent == null && appearance.dynamicColor && appearance.theme != ThemeMode.CONTRAST && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dark = when (appearance.theme) {
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                    else -> isNight(context)
                }
                return dynamic(context, dark)
            }
            return when (appearance.theme) {
                ThemeMode.LIGHT -> light(accent ?: DEFAULT_ACCENT)
                ThemeMode.DARK -> dark(accent ?: DEFAULT_ACCENT)
                ThemeMode.CONTRAST -> contrast(accent ?: CONTRAST_ACCENT)
                ThemeMode.SYSTEM -> if (isNight(context)) dark(accent ?: DEFAULT_ACCENT) else light(accent ?: DEFAULT_ACCENT)
            }
        }

        /** Material You tones as Gboard uses them (measured on a Pixel 9 Pro, Android 17). */
        @RequiresApi(Build.VERSION_CODES.S)
        fun dynamic(context: Context, dark: Boolean): Palette {
            fun c(id: Int) = context.getColor(id)
            return if (dark) {
                Palette(
                    background = c(android.R.color.system_neutral1_900),
                    key = c(android.R.color.system_neutral1_800),
                    keyText = c(android.R.color.system_neutral1_50),
                    keyHint = c(android.R.color.system_neutral2_300),
                    function = c(android.R.color.system_accent2_800),
                    functionText = c(android.R.color.system_accent2_100),
                    accent = c(android.R.color.system_accent1_200),
                    accentText = c(android.R.color.system_accent1_800),
                    pressed = 0x2EFFFFFF,
                    shadow = 0x66000000,
                    border = 0,
                    tabBar = c(android.R.color.system_neutral1_900),
                    tabText = c(android.R.color.system_neutral2_200),
                    popup = c(android.R.color.system_neutral1_700),
                    popupText = c(android.R.color.system_neutral1_50),
                    dark = true,
                )
            } else {
                Palette(
                    background = c(android.R.color.system_neutral2_50),
                    key = c(android.R.color.system_neutral1_10),
                    keyText = c(android.R.color.system_neutral1_900),
                    keyHint = c(android.R.color.system_neutral2_500),
                    function = c(android.R.color.system_accent2_100),
                    functionText = c(android.R.color.system_accent2_900),
                    accent = c(android.R.color.system_accent1_600),
                    accentText = c(android.R.color.system_accent1_0),
                    pressed = 0x24000000,
                    shadow = 0x2E1C2026,
                    border = 0,
                    tabBar = c(android.R.color.system_neutral2_50),
                    tabText = c(android.R.color.system_neutral2_700),
                    popup = c(android.R.color.system_neutral1_10),
                    popupText = c(android.R.color.system_neutral1_900),
                    dark = false,
                )
            }
        }

        fun light(accent: Int) = Palette(
            background = 0xFFE6E9EF.toInt(),
            key = Color.WHITE,
            keyText = 0xFF1C2026.toInt(),
            keyHint = 0xFF6B7380.toInt(),
            function = 0xFFCDD3DD.toInt(),
            functionText = 0xFF1C2026.toInt(),
            accent = accent,
            accentText = onColor(accent),
            pressed = 0x24000000,
            shadow = 0x2E1C2026,
            border = 0,
            tabBar = 0xFFDCE0E7.toInt(),
            tabText = 0xFF4B5563.toInt(),
            popup = Color.WHITE,
            popupText = 0xFF111827.toInt(),
            dark = false,
        )

        fun dark(accent: Int) = Palette(
            background = 0xFF16181C.toInt(),
            key = 0xFF2C3037.toInt(),
            keyText = 0xFFEDF0F5.toInt(),
            keyHint = 0xFF9AA3AF.toInt(),
            function = 0xFF1F2228.toInt(),
            functionText = 0xFFEDF0F5.toInt(),
            accent = accent,
            accentText = onColor(accent),
            pressed = 0x2EFFFFFF,
            shadow = 0x66000000,
            border = 0,
            tabBar = 0xFF101215.toInt(),
            tabText = 0xFF9AA3AF.toInt(),
            popup = 0xFF3A3F48.toInt(),
            popupText = Color.WHITE,
            dark = true,
        )

        /** Black, white and yellow for bright halls and weak eyesight. */
        fun contrast(accent: Int) = Palette(
            background = Color.BLACK,
            key = Color.BLACK,
            keyText = Color.WHITE,
            keyHint = CONTRAST_ACCENT,
            function = 0xFF1A1A1A.toInt(),
            functionText = CONTRAST_ACCENT,
            accent = accent,
            accentText = onColor(accent),
            pressed = 0x55FFD600,
            shadow = 0,
            border = 0xFFE0E0E0.toInt(),
            tabBar = Color.BLACK,
            tabText = Color.WHITE,
            popup = CONTRAST_ACCENT,
            popupText = Color.BLACK,
            dark = true,
        )

        fun parse(value: String?): Int? = value?.let { runCatching { it.toColorInt() }.getOrNull() }

        fun onColor(color: Int): Int = if (ColorUtils.calculateLuminance(color) > 0.45) 0xFF111111.toInt() else Color.WHITE

        fun isNight(context: Context): Boolean =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }
}

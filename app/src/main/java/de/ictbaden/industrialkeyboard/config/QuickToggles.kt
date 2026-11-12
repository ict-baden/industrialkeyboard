package de.ictbaden.industrialkeyboard.config

/**
 * Quick toggles (⊞, settings, `{TOGGLE:…}`): device-local on/off overrides on top of the
 * configuration below them (the stored one, later with the app profile). Missing entry = follow the
 * administrator. Only toggles in the stored `quickToggles.allowed` count; a disallowed override is
 * ignored but kept, so it comes back when the toggle is allowed again.
 *
 * Pure: computed when a field starts or something changes, never per key.
 */
object QuickToggles {
    const val GLOVE_KEY_HEIGHT = 130
    const val GLOVE_LONG_PRESS_MS = 600
    const val GLOVE_VIBRATION_MS = 40

    /** What [t] is without an override: glove off, contrast/sound as configured, scanner = a scanner exists. */
    fun baseValue(t: QuickToggle, base: KeyboardConfig, scanAvailable: Boolean): Boolean = when (t) {
        QuickToggle.GLOVE -> false
        QuickToggle.CONTRAST -> base.appearance.theme == ThemeMode.CONTRAST
        QuickToggle.SCANNER -> scanAvailable
        QuickToggle.SOUND -> base.feedback.sound
    }

    /** Whether users may switch [t] now: allowed, and the scanner only when there is one. */
    fun usable(t: QuickToggle, allowed: Set<QuickToggle>, scanAvailable: Boolean): Boolean =
        t in allowed && (t != QuickToggle.SCANNER || scanAvailable)

    /** On or off as the keyboard uses it. */
    fun effective(
        t: QuickToggle,
        base: KeyboardConfig,
        overrides: Map<QuickToggle, Boolean>,
        scanAvailable: Boolean,
        allowed: Set<QuickToggle> = base.quickToggles.allowed,
    ): Boolean {
        val baseValue = baseValue(t, base, scanAvailable)
        return if (usable(t, allowed, scanAvailable)) overrides[t] ?: baseValue else baseValue
    }

    /**
     * The overrides after one tap on [t]; an override equal to the base is removed, so two taps
     * always end at "follow the administrator". A toggle users may not switch changes nothing.
     */
    fun toggled(
        t: QuickToggle,
        base: KeyboardConfig,
        overrides: Map<QuickToggle, Boolean>,
        scanAvailable: Boolean,
        allowed: Set<QuickToggle> = base.quickToggles.allowed,
    ): Map<QuickToggle, Boolean> {
        if (!usable(t, allowed, scanAvailable)) return overrides
        val new = !effective(t, base, overrides, scanAvailable, allowed)
        return if (new == baseValue(t, base, scanAvailable)) overrides - t else overrides + (t to new)
    }

    /**
     * The configuration the keyboard uses. Only usable overrides that differ from the base count;
     * when none does, [base] itself is returned (same instance), so rendering sees no change.
     */
    fun apply(
        base: KeyboardConfig,
        overrides: Map<QuickToggle, Boolean>,
        scanAvailable: Boolean,
        allowed: Set<QuickToggle> = base.quickToggles.allowed,
    ): KeyboardConfig {
        if (overrides.isEmpty()) return base
        val active = QuickToggle.entries.filter { t ->
            val value = overrides[t]
            value != null && usable(t, allowed, scanAvailable) && value != baseValue(t, base, scanAvailable)
        }
        if (active.isEmpty()) return base
        var c = base
        for (t in active) {
            val on = overrides.getValue(t)
            c = when (t) {
                // Only on differs from the base (off): raise, never lower.
                QuickToggle.GLOVE -> c.copy(
                    appearance = c.appearance.copy(keyHeight = maxOf(c.appearance.keyHeight, GLOVE_KEY_HEIGHT)),
                    typing = c.typing.copy(longPressDelay = maxOf(c.typing.longPressDelay, GLOVE_LONG_PRESS_MS)),
                    feedback = c.feedback.copy(
                        keyPopup = false,
                        vibrate = true,
                        vibrationMs = maxOf(c.feedback.vibrationMs ?: 0, GLOVE_VIBRATION_MS),
                    ),
                )
                // The yellow highlight of the contrast theme is guaranteed: no company accent colour.
                QuickToggle.CONTRAST -> c.copy(
                    appearance = if (on) c.appearance.copy(theme = ThemeMode.CONTRAST, accentColor = null) else c.appearance.copy(theme = ThemeMode.SYSTEM),
                )
                // Only off differs (on needs a scanner): no soft trigger, scan key or camera. The
                // barcode broadcast, NFC and the device's own trigger stay as they are.
                QuickToggle.SCANNER -> c.copy(scanner = c.scanner.copy(trigger = ScanTriggerType.NONE, scanKey = false))
                QuickToggle.SOUND -> c.copy(feedback = c.feedback.copy(sound = on))
            }
        }
        return c
    }

    /** Toggles shown to users: allowed, and the scanner only with a scanner. */
    fun visible(base: KeyboardConfig, scanAvailable: Boolean, allowed: Set<QuickToggle> = base.quickToggles.allowed): List<QuickToggle> =
        QuickToggle.entries.filter { usable(it, allowed, scanAvailable) }

    /**
     * The toggle layer of [EffectiveConfig]; what users may change comes from the [stored]
     * configuration, never from a layer below (an app profile cannot unlock a toggle).
     */
    fun layer(stored: KeyboardConfig, overrides: Map<QuickToggle, Boolean>, scanAvailable: Boolean): EffectiveConfig.Layer =
        EffectiveConfig.Layer { apply(it, overrides, scanAvailable, stored.quickToggles.allowed) }
}

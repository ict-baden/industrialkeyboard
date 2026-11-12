package de.ictbaden.industrialkeyboard.config

/**
 * The configuration the keyboard works with: the stored configuration, then the app profile, then
 * the quick toggles (docs/configuration.md, "Order of precedence"). Field rules (password fields,
 * number, phone and date pads) come after this, in FieldPolicy and the renderer, and always win.
 *
 * Computed when a field starts or the configuration changes – never per key. The result is never
 * written to the store: export, QR code and settings always show the stored configuration.
 */
object EffectiveConfig {

    /**
     * One layer on top of the configuration below it. A layer that changes nothing returns its input
     * itself (same instance), so an unchanged effective configuration costs no render diffing.
     */
    fun interface Layer {
        fun applyTo(config: KeyboardConfig): KeyboardConfig
    }

    /** No change. */
    val NONE = Layer { it }

    /**
     * [stored] with the [profile] layer, then the [toggles] layer on top: where both set the same
     * field, the toggle wins. Admin gates (what users may change) are read from [stored] by the
     * layers themselves, never from a profile.
     */
    fun of(stored: KeyboardConfig, profile: Layer = NONE, toggles: Layer = NONE): KeyboardConfig =
        toggles.applyTo(profile.applyTo(stored))
}

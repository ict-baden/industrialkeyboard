package de.ictbaden.industrialkeyboard.api

import android.os.Handler
import android.os.Looper

/** Public intent API of the keyboard. */
object KeyboardApi {
    private const val PREFIX = "de.ictbaden.industrialkeyboard.action."

    /** Extra `config` (JSON); with an admin password also `password`. Result code -1 = installed. */
    const val ACTION_INSTALL_CONFIG = PREFIX + "INSTALL_CONFIG"

    /** Extra `tab`: alpha, numeric, symbols, function, scan or a custom layout id. */
    const val ACTION_SHOW_TAB = PREFIX + "SHOW_TAB"

    const val EXTRA_CONFIG = "config"
    const val EXTRA_PASSWORD = "password"
    const val EXTRA_TAB = "tab"

    const val RESULT_OK = -1
    const val RESULT_PASSWORD = 2
    const val RESULT_INVALID = 3
}

/** Hands tab requests to the running keyboard; a request while it is hidden applies to the next field. */
object KeyboardBus {
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    var onShowTab: ((String) -> Boolean)? = null

    @Volatile
    var pendingTab: String? = null

    fun requestTab(tab: String) {
        main.post {
            if (onShowTab?.invoke(tab) != true) pendingTab = tab
        }
    }

    fun takePendingTab(): String? = pendingTab.also { pendingTab = null }

    /** The keyboard is shown in a field right now (set by the keyboard service). */
    @Volatile
    var keyboardShown = false

    /** When the keyboard was last hidden (elapsedRealtime). */
    @Volatile
    var hiddenAt = 0L

    /** Delivers an NFC tag text; `true` when the keyboard took it (main thread). */
    @Volatile
    var onNfc: ((String) -> Boolean)? = null

    /** A tag text that waits for the field to get the keyboard back, with the time it was read. */
    @Volatile
    var pendingNfc: Pair<String, Long>? = null

    /**
     * Was the keyboard in a field a moment ago? The tag activity briefly takes the focus, which can
     * hide the keyboard just before the tag arrives.
     */
    fun keyboardRecentlyShown(now: Long): Boolean = keyboardShown || now - hiddenAt < RECENT_MS

    fun takePendingNfc(now: Long): String? {
        val pending = pendingNfc ?: return null
        pendingNfc = null
        return pending.first.takeIf { now - pending.second < PENDING_MS }
    }

    private const val RECENT_MS = 1_500L
    private const val PENDING_MS = 3_000L
}

package io.github.pieewiee.industrialkeyboard.api

import android.os.Handler
import android.os.Looper

/** Public intent API of the keyboard. */
object KeyboardApi {
    private const val PREFIX = "io.github.pieewiee.industrialkeyboard.action."

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
}

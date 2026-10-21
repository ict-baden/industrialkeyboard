package de.ictbaden.industrialkeyboard.config

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit

/**
 * Limits guessing of the admin password: after [MAX_FAILURES] wrong entries in a row every entry is
 * refused for [LOCK_MS]. The settings screen and the remote channels (`INSTALL_CONFIG`, configuration
 * files) count separately: an app guessing by broadcast must not lock the administrator out of the
 * settings. A missing password is no guess and does not count.
 */
class PasswordGuard @VisibleForTesting internal constructor(
    private val prefs: SharedPreferences,
    private val now: () -> Long,
) {
    enum class Result { OK, WRONG, LOCKED }

    /** Milliseconds until entries are accepted again, 0 when not locked. */
    @Synchronized
    fun lockedForMs(): Long {
        val until = prefs.getLong(KEY_UNTIL, 0)
        val left = until - now()
        if (left > LOCK_MS) {
            // The clock was set back (battery swap, no network time): lock from now, not until the clock catches up.
            prefs.edit { putLong(KEY_UNTIL, now() + LOCK_MS) }
            return LOCK_MS
        }
        return left.coerceAtLeast(0)
    }

    /** Checks [password] against [hash]; while locked the password is not even checked. */
    @Synchronized
    fun check(password: String, hash: String?): Result {
        if (lockedForMs() > 0) return Result.LOCKED
        if (password.isEmpty()) return Result.WRONG
        if (PasswordHasher.verify(password, hash)) {
            prefs.edit { remove(KEY_FAILURES).remove(KEY_UNTIL) }
            return Result.OK
        }
        val failures = prefs.getInt(KEY_FAILURES, 0) + 1
        prefs.edit {
            if (failures >= MAX_FAILURES) {
                remove(KEY_FAILURES)
                putLong(KEY_UNTIL, now() + LOCK_MS)
            } else {
                putInt(KEY_FAILURES, failures)
            }
        }
        return Result.WRONG
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_MS = 5 * 60_000L
        private const val KEY_FAILURES = "failures"
        private const val KEY_UNTIL = "locked_until"

        private val guards = HashMap<String, PasswordGuard>()

        /** Forgets the shared instances (tests: every test has a new application). */
        @VisibleForTesting
        @Synchronized
        internal fun reset() = guards.clear()

        /** Wrong entries in the settings screen. */
        fun settings(context: Context) = shared(context, "settings")

        /** Wrong passwords sent by other apps or in configuration files. */
        fun remote(context: Context) = shared(context, "remote")

        /** One instance per channel, so the broadcast and the provisioning thread count together. */
        @Synchronized
        private fun shared(context: Context, channel: String) = guards.getOrPut(channel) {
            PasswordGuard(context.applicationContext.getSharedPreferences("password_guard_$channel", Context.MODE_PRIVATE), System::currentTimeMillis)
        }
    }
}

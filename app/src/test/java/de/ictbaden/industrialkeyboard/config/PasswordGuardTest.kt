package de.ictbaden.industrialkeyboard.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PasswordGuardTest {

    private var now = 1_000_000L
    private val hash = PasswordHasher.hash("secret")
    private val guard = PasswordGuard(
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("guard-test", Context.MODE_PRIVATE).apply { edit().clear().commit() },
    ) { now }

    @Test
    fun `locks after five wrong passwords`() {
        repeat(PasswordGuard.MAX_FAILURES) { assertEquals(PasswordGuard.Result.WRONG, guard.check("wrong", hash)) }
        // Even the right password is refused while locked.
        assertEquals(PasswordGuard.Result.LOCKED, guard.check("secret", hash))
        now += PasswordGuard.LOCK_MS
        assertEquals(PasswordGuard.Result.OK, guard.check("secret", hash))
    }

    @Test
    fun `a correct password resets the count`() {
        repeat(PasswordGuard.MAX_FAILURES - 1) { guard.check("wrong", hash) }
        assertEquals(PasswordGuard.Result.OK, guard.check("secret", hash))
        repeat(PasswordGuard.MAX_FAILURES - 1) { assertEquals(PasswordGuard.Result.WRONG, guard.check("wrong", hash)) }
        assertEquals(PasswordGuard.Result.OK, guard.check("secret", hash))
    }

    @Test
    fun `a missing password is no attempt`() {
        repeat(20) { assertEquals(PasswordGuard.Result.WRONG, guard.check("", hash)) }
        assertEquals(PasswordGuard.Result.OK, guard.check("secret", hash))
    }

    @Test
    fun `a clock set back does not lock longer`() {
        repeat(PasswordGuard.MAX_FAILURES) { guard.check("wrong", hash) }
        now -= 365L * 24 * 3600 * 1000
        assertEquals(PasswordGuard.LOCK_MS, guard.lockedForMs())
    }
}

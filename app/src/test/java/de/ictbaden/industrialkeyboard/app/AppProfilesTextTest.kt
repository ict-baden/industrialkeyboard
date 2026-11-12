package de.ictbaden.industrialkeyboard.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.AppProfile
import de.ictbaden.industrialkeyboard.config.FieldKind
import de.ictbaden.industrialkeyboard.config.Tabs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AppProfilesTextTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `describes names, conditions and overrides with their JSON names`() {
        val text = AppProfilesText.describe(
            context,
            listOf(
                AppProfile(name = "WMS", apps = listOf("com.sap.*"), fields = setOf(FieldKind.NONE, FieldKind.TEXT), hints = listOf("*menge*"), page = Tabs.NUMERIC, suggestions = false),
                AppProfile(apps = listOf("com.b"), scanSuffix = ""),
            ),
        )
        val lines = text.lines()
        assertEquals("1. WMS", lines[0])
        assertEquals("apps: com.sap.* · fields: text, none · hints: *menge*", lines[1])
        assertEquals("page: numeric · suggestions: false", lines[2])
        assertEquals("2. " + context.getString(R.string.app_profile_unnamed, 2), lines[4])
        assertEquals("scanSuffix: \"\"", lines[6])
        assertTrue(text.endsWith(context.getString(R.string.app_profiles_first_wins)))
    }
}

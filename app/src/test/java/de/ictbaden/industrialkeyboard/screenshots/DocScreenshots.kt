package de.ictbaden.industrialkeyboard.screenshots

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.google.android.material.textfield.TextInputLayout
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.app.SettingsActivity
import de.ictbaden.industrialkeyboard.app.SetupActivity
import de.ictbaden.industrialkeyboard.config.Alphabet
import de.ictbaden.industrialkeyboard.config.Appearance
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import de.ictbaden.industrialkeyboard.config.LayoutSettings
import de.ictbaden.industrialkeyboard.config.ThemeMode
import de.ictbaden.industrialkeyboard.ime.KeyboardRenderer
import de.ictbaden.industrialkeyboard.ime.Page
import de.ictbaden.industrialkeyboard.layout.AlphaVariant
import de.ictbaden.industrialkeyboard.layout.NumericVariant
import de.ictbaden.industrialkeyboard.ui.KeyboardRootView
import de.ictbaden.industrialkeyboard.ui.ShiftState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * Renders the screens used in the documentation (`docs/images`). Not part of the normal test
 * run; record with `./gradlew recordRoborazziDebug -Pscreenshots`.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h760dp-xxhdpi")
class DocScreenshots {

    private val qwertz = KeyboardConfig(layout = LayoutSettings(alphabet = Alphabet.QWERTZ))

    /** The setup screen shows the keyboard as enabled and selected. */
    @Before
    fun activateKeyboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = "${context.packageName}/.ime.KeyboardService"
        Settings.Secure.putString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD, id)
        shadowOf(context.getSystemService(InputMethodManager::class.java)).setEnabledInputMethodInfoList(
            listOf(InputMethodInfo(context.packageName, "${context.packageName}.ime.KeyboardService", "Industrial Keyboard", null)),
        )
    }

    @Test
    fun setup() = ActivityScenario.launch(SetupActivity::class.java).use { capture("setup") }

    @Test
    fun settings() = ActivityScenario.launch(SettingsActivity::class.java).use { capture("settings") }

    @Test
    fun alpha() = inApp("alpha", theme(ThemeMode.LIGHT), Page.Alpha(AlphaVariant.TEXT), field = 0, press = "g")

    @Test
    fun alternatives() = inApp("alternatives", theme(ThemeMode.LIGHT), Page.Alpha(AlphaVariant.TEXT), field = 0, longPress = "a")

    @Test
    fun numeric() = inApp("numeric", theme(ThemeMode.DARK), Page.Numeric(NumericVariant.NUMBER), field = 1)

    // 1.07 s after the start the laser is at the top of the pad, clear of the text.
    @Test
    fun scan() = inApp("scan", theme(ThemeMode.LIGHT), Page.Scan, field = 0, scanning = true, captureDelay = 271)

    @Test
    fun function() = inApp("function", theme(ThemeMode.CONTRAST), Page.Function, field = 0)

    @Test
    fun functionKeys() = keyboardOnly("fn-keys", theme(ThemeMode.DARK), Page.Function)

    @Test
    fun functionShifted() = keyboardOnly("fn-shifted", theme(ThemeMode.DARK), Page.Function, ShiftState.ONCE)

    @Test
    fun functionRow() = keyboardOnly(
        "function-row",
        qwertz.copy(appearance = Appearance(theme = ThemeMode.DARK, functionRow = true)),
        Page.Alpha(AlphaVariant.TEXT),
    )

    @Test
    fun custom() {
        val config = ConfigJson.parse(File(examples(), "custom-layout.json").readText())
        inApp("custom", config, Page.Custom("picking"), field = 0)
    }

    @Test
    fun themeLight() = keyboardOnly("theme-light", theme(ThemeMode.LIGHT), Page.Alpha(AlphaVariant.TEXT))

    @Test
    fun themeDark() = keyboardOnly("theme-dark", theme(ThemeMode.DARK), Page.Alpha(AlphaVariant.TEXT))

    @Test
    fun themeContrast() = keyboardOnly("theme-contrast", theme(ThemeMode.CONTRAST), Page.Alpha(AlphaVariant.TEXT))

    private fun theme(theme: ThemeMode) = qwertz.copy(appearance = Appearance(theme = theme))

    /** The setup screen's sample form with the keyboard open for one of its fields. */
    private fun inApp(
        name: String,
        config: KeyboardConfig,
        page: Page,
        field: Int,
        shift: ShiftState = ShiftState.OFF,
        scanning: Boolean = false,
        press: String? = null,
        longPress: String? = null,
        captureDelay: Long? = null,
    ) {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            var root: KeyboardRootView? = null
            scenario.onActivity { activity ->
                val content = activity.findViewById<FrameLayout>(android.R.id.content)
                val scroll = content.getChildAt(0) as NestedScrollView
                val target = activity.fields[field] as TextInputLayout
                root = KeyboardRootView(activity).also { keyboard ->
                    content.addView(keyboard, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
                    keyboard.render(KeyboardRenderer.state(activity, config, page, shift, "Zebra DataWedge", R.drawable.kb_next, scanning))
                }
                target.editText!!.requestFocus()
                // The sample form starts at the top, below the "Try it" heading.
                val heading = findText(scroll, "Try it") ?: error("no heading")
                scroll.post { scroll.scrollTo(0, top(heading, scroll) - (12 * activity.resources.displayMetrics.density).toInt()) }
            }
            idle(800)
            (press ?: longPress)?.let { label -> scenario.onActivity { touch(root!!, label) } }
            // A preview must be captured before the long-press delay (400 ms) shows the alternatives.
            capture(name, captureDelay ?: if (press != null) 150 else 700)
        }
    }

    /** Only the keyboard, for the theme comparison. */
    private fun keyboardOnly(name: String, config: KeyboardConfig, page: Page, shift: ShiftState = ShiftState.OFF) {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> showKeyboard(activity, config, page, shift) }
            capture(name)
        }
    }

    private fun showKeyboard(activity: Activity, config: KeyboardConfig, page: Page, shift: ShiftState) {
        val root = KeyboardRootView(activity, headroomDp = 0f)
        activity.setContentView(
            FrameLayout(activity).apply {
                addView(root, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            },
        )
        root.render(KeyboardRenderer.state(activity, config, page, shift, "Zebra DataWedge"))
    }

    /** Puts a finger on the key labeled [label] (and keeps it there). */
    private fun touch(root: KeyboardRootView, label: String) {
        val center = root.keyboard.keyCenter(label) ?: error("no key $label")
        val time = SystemClock.uptimeMillis()
        root.keyboard.dispatchTouchEvent(MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, center.x, center.y, 0))
    }

    private fun findText(view: View, text: String): View? = when {
        view is TextView && view.text.toString() == text -> view
        view is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { findText(view.getChildAt(it), text) }
        else -> null
    }

    private fun top(view: View, ancestor: View): Int {
        var y = 0
        var current: View = view
        while (current !== ancestor) {
            y += current.top
            current = current.parent as View
        }
        return y
    }

    private fun examples(): File = listOf(File("../examples"), File("examples")).first { it.isDirectory }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private fun capture(name: String, idleMs: Long = 500) {
        idle(idleMs)
        captureScreenRoboImage("build/screenshots/$name.png", RoborazziOptions())
    }
}

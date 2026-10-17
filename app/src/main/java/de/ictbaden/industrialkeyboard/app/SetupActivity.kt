package de.ictbaden.industrialkeyboard.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import com.google.android.material.button.MaterialButton
import de.ictbaden.industrialkeyboard.BuildConfig
import de.ictbaden.industrialkeyboard.R

/** Start screen: enable and select the keyboard, then try it in a sample form. */
class SetupActivity : AppCompatActivity() {

    private lateinit var ui: Ui
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var steps: LinearLayout
    private lateinit var scroll: NestedScrollView

    /** Sample form fields; tests use them to show the keyboard in context. */
    @VisibleForTesting
    internal val fields = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ui = Ui(this)
        val header = header()
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(32))
        }
        steps = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(ui.section("Setup"))
        content.addView(steps)
        content.addView(ui.section("Try it"))
        content.addView(ui.card(sampleForm(), padded = true))
        content.addView(ui.section("More"))
        content.addView(
            ui.card(
                listOf(
                    ui.row(R.drawable.ic_settings, "Keyboard settings", "Layouts, scanner, look and feedback") {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    },
                    ui.row(R.drawable.ic_globe, "Switch keyboard", "Choose between installed keyboards") { showPicker() },
                ),
            ),
        )
        content.addView(TextView(this).apply {
            text = "Industrial Keyboard ${BuildConfig.VERSION_NAME}"
            gravity = Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
            setTextColor(ui.color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            setPadding(0, ui.dp(24), 0, 0)
        })
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(content)
        }
        scroll = NestedScrollView(this).apply { addView(column) }
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            header.updatePadding(top = bars.top + ui.dp(28))
            // The keyboard shrinks the scroll area, which scrolls the focused field into view.
            scroll.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = ime.bottom }
            content.updatePadding(bottom = (if (ime.bottom > 0) 0 else bars.bottom) + ui.dp(32))
            insets
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The keyboard picker is a dialog: refresh when it closes.
        if (hasFocus) refresh()
    }

    private fun header(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(28))
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF1A237E.toInt(), 0xFF00838F.toInt())).apply {
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, ui.dp(32).toFloat(), ui.dp(32).toFloat(), ui.dp(32).toFloat(), ui.dp(32).toFloat())
        }
        addView(ImageView(context).apply {
            setImageResource(R.drawable.logo)
            contentDescription = null
        }, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)))
        addView(TextView(context).apply {
            text = getString(R.string.app_name)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_HeadlineMedium)
            setTextColor(Color.WHITE)
            setPadding(0, ui.dp(16), 0, 0)
        })
        addView(TextView(context).apply {
            text = "Fast data entry with scanner, gloves and forms"
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
            setTextColor(0xD9FFFFFF.toInt())
        })
        status = TextView(context).apply {
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge)
            setPadding(ui.dp(14), ui.dp(6), ui.dp(14), ui.dp(6))
            compoundDrawablePadding = ui.dp(6)
        }
        addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(16) })
    }

    private fun refresh() {
        val enabled = isEnabled()
        val active = enabled && isActive()
        status.text = when {
            active -> "●  Active keyboard"
            enabled -> "●  Enabled – select it to use it"
            else -> "●  Not set up yet"
        }
        status.setTextColor(if (active) 0xFF1B5E20.toInt() else 0xFF5D4037.toInt())
        status.background = GradientDrawable().apply {
            cornerRadius = ui.dp(100).toFloat()
            setColor(if (active) 0xFFC8F7C5.toInt() else 0xFFFFE0B2.toInt())
        }
        steps.removeAllViews()
        steps.addView(
            ui.card(
                listOf(
                    step(1, "Enable the keyboard", "Turn on Industrial Keyboard in the system settings", enabled, "Enable") {
                        startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                    },
                    step(2, "Select it", "Make it the keyboard that opens in every app", active, "Select") { showPicker() },
                ),
            ),
        )
    }

    private fun step(number: Int, title: String, subtitle: String, done: Boolean, action: String, onClick: () -> Unit): View {
        val trailing: View? = if (done) {
            null
        } else {
            MaterialButton(this).apply {
                text = action
                setOnClickListener { onClick() }
            }
        }
        val badge = TextView(this).apply {
            text = if (done) "✓" else number.toString()
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(if (done) Color.WHITE else ui.accent)
            setBackgroundResource(R.drawable.bg_tile_icon)
            backgroundTintList = ColorStateList.valueOf(if (done) 0xFF2E7D32.toInt() else (ui.accent and 0x00FFFFFF) or 0x1F000000)
        }
        return ui.row(R.drawable.ic_keyboard, title, if (done) "Done" else subtitle, trailing, leading = badge, onClick = if (done) null else onClick)
    }

    private fun sampleForm(): List<View> {
        fun field(hint: String, type: Int, helper: String) = ui.textField(hint, inputType = type, helper = helper).also { fields += it }
        return listOf(
            TextView(this).apply {
                text = "Every field opens the matching keys."
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
                setTextColor(ui.color(com.google.android.material.R.attr.colorOnSurfaceVariant))
            },
            field("Item number", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS, "Letters, scan or the Fn keys"),
            field("Quantity", InputType.TYPE_CLASS_NUMBER, "Numeric pad"),
            field("Weight (kg)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "Numeric pad with , and ."),
            field("Phone", InputType.TYPE_CLASS_PHONE, "Dial pad with + * #"),
            field("Best before", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE, "Date pad with / - ."),
            field("PIN", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD, "PIN pad"),
            field("E-mail", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, "@ next to the space bar"),
        ).onEach { view ->
            view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(10) }
        }
    }

    private fun isEnabled(): Boolean =
        getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.any { it.packageName == packageName } == true

    private fun isActive(): Boolean =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/') == packageName

    private fun showPicker() {
        getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
}

package io.github.pieewiee.industrialkeyboard.app

import android.app.Activity
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.pieewiee.industrialkeyboard.R
import io.github.pieewiee.industrialkeyboard.config.PasswordHasher
import io.github.pieewiee.industrialkeyboard.layout.Macro
import io.github.pieewiee.industrialkeyboard.ui.Palette

/** Dialogs of the settings screens. */
class Dialogs(private val activity: Activity, private val ui: Ui) {

    /** Asks for the admin password; [onResult] receives true after a correct entry. */
    fun requirePassword(hash: String, onResult: (Boolean) -> Unit) {
        val field = ui.textField("Admin password", password = true)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_lock))
            .setTitle("Settings are protected")
            .setView(ui.form(field))
            .setPositiveButton("Unlock", null)
            .setNegativeButton("Cancel") { _, _ -> onResult(false) }
            .setOnCancelListener { onResult(false) }
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (PasswordHasher.verify(field.value, hash)) {
                dialog.dismiss()
                onResult(true)
            } else {
                field.error = "Wrong password"
            }
        }
    }

    fun changePassword(onSave: (String?) -> Unit) {
        val first = ui.textField("New password", password = true, helper = "Leave empty to remove the password")
        val second = ui.textField("Repeat", password = true)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_key))
            .setTitle("Admin password")
            .setView(ui.form(first, second))
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when {
                first.value != second.value -> second.error = "Passwords do not match"
                else -> {
                    dialog.dismiss()
                    onSave(first.value.ifEmpty { null }?.let(PasswordHasher::hash))
                    toast(if (first.value.isEmpty()) "Admin password removed" else "Admin password saved")
                }
            }
        }
    }

    /** Edits a programmable key macro; [onSave] receives the macro or `null` for the default. */
    fun editMacro(title: String, current: String, default: String, onSave: (String?) -> Unit) {
        val field = ui.textField(
            "Text or tokens",
            current,
            helper = "Examples: -   {TAB}   OK{ENTER}   {F1}   {SCAN}   {U+00E4}",
        )
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_key))
            .setTitle(title)
            .setView(ui.form(field))
            .setPositiveButton("Save", null)
            .setNeutralButton("Default ($default)") { _, _ -> onSave(null) }
            .setNegativeButton("Cancel", null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val macro = field.value
            val error = when {
                macro.isEmpty() -> "Enter a character or a token"
                else -> runCatching { Macro.parse(macro) }.exceptionOrNull()?.message
            }
            if (error != null) {
                field.error = error
            } else {
                dialog.dismiss()
                onSave(macro)
            }
        }
    }

    /** Palette plus hex input; [onPicked] receives `null` for the theme default. */
    fun pickColor(title: String, current: String?, onPicked: (String?) -> Unit) {
        val hex = ui.textField("Color (#RRGGBB)", current.orEmpty(), helper = "Empty = theme default")
        val marks = mutableMapOf<String, ImageView>()
        fun select(color: String?) {
            marks.forEach { (key, mark) -> mark.visibility = if (key.equals(color, ignoreCase = true)) View.VISIBLE else View.INVISIBLE }
        }
        val grid = GridLayout(activity).apply {
            columnCount = 6
            for (color in PALETTE) {
                val value = Palette.parse(color)!!
                val mark = ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(Palette.onColor(value))
                    scaleType = ImageView.ScaleType.CENTER
                }
                marks[color] = mark
                val cell = FrameLayout(context).apply {
                    contentDescription = color
                    setOnClickListener {
                        hex.editText?.setText(color)
                        select(color)
                    }
                    addView(View(context).apply {
                        setBackgroundResource(R.drawable.bg_avatar)
                        backgroundTintList = ColorStateList.valueOf(value)
                    }, FrameLayout.LayoutParams(ui.dp(38), ui.dp(38), Gravity.CENTER))
                    addView(mark, FrameLayout.LayoutParams(ui.dp(38), ui.dp(38), Gravity.CENTER))
                }
                addView(cell, GridLayout.LayoutParams().apply {
                    width = 0
                    height = ui.dp(48)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                })
            }
        }
        select(current)
        val reset = MaterialButton(activity, null, androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            text = "Use theme default"
            setOnClickListener {
                hex.editText?.setText("")
                select(null)
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_palette))
            .setTitle(title)
            .setView(ui.form(grid, hex, reset))
            .setPositiveButton("Apply") { _, _ ->
                val value = hex.value.trim().uppercase()
                when {
                    value.isEmpty() -> onPicked(null)
                    value.matches(Regex("#[0-9A-F]{6}")) -> onPicked(value)
                    else -> toast("Invalid color: $value")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun message(title: String, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_info))
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private companion object {
        val PALETTE = listOf(
            "#2563EB", "#1565C0", "#00838F", "#00796B", "#2E7D32", "#558B2F",
            "#F57C00", "#E64A19", "#C62828", "#AD1457", "#6A1B9A", "#FFD600",
        )
    }
}

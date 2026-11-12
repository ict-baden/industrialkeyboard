package de.ictbaden.industrialkeyboard.app

import android.app.Activity
import android.content.res.ColorStateList
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.PasswordGuard
import de.ictbaden.industrialkeyboard.config.PasswordHasher
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.layout.Macro
import de.ictbaden.industrialkeyboard.ui.Palette

/** Dialogs of the settings screens. */
class Dialogs(private val activity: Activity, private val ui: Ui) {

    /** Asks for the admin password; [onResult] receives true after a correct entry. */
    fun requirePassword(hash: String, onResult: (Boolean) -> Unit) {
        val field = ui.textField(s(R.string.admin_password), password = true)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_lock))
            .setTitle(R.string.settings_protected)
            .setView(ui.form(field))
            .setPositiveButton(R.string.button_unlock, null)
            .setNegativeButton(R.string.button_cancel) { _, _ -> onResult(false) }
            .setOnCancelListener { onResult(false) }
            .show()
        val guard = PasswordGuard.settings(activity)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when (guard.check(field.value, hash)) {
                PasswordGuard.Result.OK -> {
                    dialog.dismiss()
                    onResult(true)
                }
                // The attempt that starts the lock already says so.
                PasswordGuard.Result.WRONG, PasswordGuard.Result.LOCKED -> {
                    val lockedMs = guard.lockedForMs()
                    field.error = if (lockedMs > 0) activity.getString(R.string.password_locked, ((lockedMs + 59_999) / 60_000).toInt()) else s(R.string.wrong_password)
                }
            }
        }
    }

    fun changePassword(onSave: (String?) -> Unit) {
        val first = ui.textField(s(R.string.new_password), password = true, helper = s(R.string.new_password_summary))
        val second = ui.textField(s(R.string.repeat_password), password = true)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_key))
            .setTitle(R.string.admin_password)
            .setView(ui.form(first, second))
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            when {
                first.value != second.value -> second.error = s(R.string.passwords_do_not_match)
                else -> {
                    dialog.dismiss()
                    onSave(first.value.ifEmpty { null }?.let(PasswordHasher::hash))
                    toast(s(if (first.value.isEmpty()) R.string.password_removed else R.string.password_saved))
                }
            }
        }
    }

    /** Edits a programmable key macro; [onSave] receives the macro or `null` for the default. */
    fun editMacro(title: String, current: String, default: String, allowEmpty: Boolean = false, onSave: (String?) -> Unit) {
        val field = ui.textField(s(R.string.macro_hint), current, helper = s(R.string.macro_helper))
        val dialog = MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_key))
            .setTitle(title)
            .setView(ui.form(field))
            .setPositiveButton(R.string.button_save, null)
            .setNeutralButton(activity.getString(R.string.button_default, default)) { _, _ -> onSave(null) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val macro = field.value
            val error = when {
                macro.isEmpty() && allowEmpty -> null
                macro.isEmpty() -> s(R.string.macro_empty)
                // The parser names the token in English; the code identifies the message in any language.
                else -> runCatching { Macro.parse(macro) }.exceptionOrNull()?.let {
                    withCode(activity.getString(R.string.macro_invalid, it.message), ErrorCodes.INVALID_MACRO)
                }
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
        val hex = ui.textField(s(R.string.color_hint), current.orEmpty(), helper = s(R.string.color_helper))
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
            text = s(R.string.use_theme_default)
            setOnClickListener {
                hex.editText?.setText("")
                select(null)
            }
        }
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_palette))
            .setTitle(title)
            .setView(ui.form(grid, hex, reset))
            .setPositiveButton(R.string.button_apply) { _, _ ->
                val value = hex.value.trim().uppercase()
                when {
                    value.isEmpty() -> onPicked(null)
                    value.matches(Regex("#[0-9A-F]{6}")) -> onPicked(value)
                    else -> toast(withCode(activity.getString(R.string.invalid_color, value), ErrorCodes.INVALID_COLOR))
                }
            }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    /** Edits a whole number in [min]..[max]. */
    fun editNumber(title: String, current: Int, min: Int, max: Int, helper: String? = null, onSave: (Int) -> Unit) {
        val field = ui.textField(activity.getString(R.string.number_range, min, max), current.toString(), inputType = InputType.TYPE_CLASS_NUMBER, helper = helper)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(ui.form(field))
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = field.value.trim().toIntOrNull()
            if (value == null || value < min || value > max) {
                field.error = activity.getString(R.string.number_range, min, max)
            } else {
                dialog.dismiss()
                onSave(value)
            }
        }
    }

    /**
     * Edits a text; with [macro] it is checked like a key macro. [defaultLabel] adds a button that
     * saves `null` (the default behaviour).
     */
    fun editText(title: String, current: String?, helper: String?, macro: Boolean, defaultLabel: String? = null, validate: ((String) -> String?)? = null, onSave: (String?) -> Unit) {
        val field = ui.textField(s(if (macro) R.string.macro_hint else R.string.text_hint), current.orEmpty(), helper = helper)
        val builder = MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(ui.form(field))
            .setPositiveButton(R.string.button_save, null)
            .setNegativeButton(R.string.button_cancel, null)
        defaultLabel?.let { builder.setNeutralButton(it) { _, _ -> onSave(null) } }
        val dialog = builder.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val text = field.editText?.text?.toString().orEmpty()
            val error = if (macro && text.isNotEmpty()) {
                runCatching { Macro.parse(text) }.exceptionOrNull()?.let {
                    withCode(activity.getString(R.string.macro_invalid, it.message), ErrorCodes.INVALID_MACRO)
                }
            } else if (text.isNotEmpty()) {
                validate?.invoke(text)
            } else {
                null
            }
            if (error != null) {
                field.error = error
            } else {
                dialog.dismiss()
                onSave(text)
            }
        }
    }

    /** Multi-select of barcode types; an empty selection means all. */
    fun pickSymbologies(current: Set<Symbology>, onSave: (Set<Symbology>) -> Unit) {
        // OCR and NFC are no camera barcode types.
        val all = Symbology.entries.filter { it.barcode }
        val checked = BooleanArray(all.size) { all[it] in current }
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_scan))
            .setTitle(R.string.camera_symbologies_pick)
            .setMultiChoiceItems(all.map { it.label }.toTypedArray(), checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(R.string.button_save) { _, _ -> onSave(all.filterIndexed { i, _ -> checked[i] }.toSet()) }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    fun confirm(title: String, message: String, action: String, onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(action) { _, _ -> onConfirm() }
            .setNegativeButton(R.string.button_cancel, null)
            .show()
    }

    fun message(title: String, message: String, onClose: () -> Unit = {}) {
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_info))
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.button_ok, null)
            .setOnDismissListener { onClose() }
            .show()
    }

    fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    /** Translated message plus a language-independent code for support (richtlinien/13-sprachen.md). */
    fun withCode(message: String, code: String): String = activity.getString(R.string.message_with_code, message, code)

    private fun s(id: Int): String = activity.getString(id)

    private companion object {
        val PALETTE = listOf(
            "#2563EB", "#1565C0", "#00838F", "#00796B", "#2E7D32", "#558B2F",
            "#F57C00", "#E64A19", "#C62828", "#AD1457", "#6A1B9A", "#FFD600",
        )
    }
}

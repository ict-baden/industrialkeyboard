package de.ictbaden.industrialkeyboard.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.ConfigCodec
import de.ictbaden.industrialkeyboard.config.ConfigJson
import de.ictbaden.industrialkeyboard.config.KeyboardConfig
import io.nayuki.qrcodegen.DataTooLongException
import io.nayuki.qrcodegen.QrCode

/** The configuration as a QR code, to set up the next device by scanning it. */
object ConfigBarcode {

    /** QR code of [payload]: error correction M where it fits (raised when there is room), else L; `null` = too large. */
    fun qr(payload: String): QrCode? = listOf(QrCode.Ecc.MEDIUM, QrCode.Ecc.LOW).firstNotNullOfOrNull { ecc ->
        try {
            QrCode.encodeText(payload, ecc)
        } catch (_: DataTooLongException) {
            null
        }
    }

    fun payload(config: KeyboardConfig): String = ConfigCodec.encode(ConfigJson.write(config))

    fun show(activity: Activity, ui: Ui, config: KeyboardConfig) {
        val payload = payload(config)
        val code = qr(payload)
        if (code == null) {
            MaterialAlertDialogBuilder(activity)
                .setIcon(ui.icon(R.drawable.ic_qr))
                .setTitle(R.string.export_barcode)
                .setMessage(activity.getString(R.string.export_barcode_too_large, payload.length))
                .setPositiveButton(R.string.button_ok, null)
                .show()
            return
        }
        val image = ImageView(activity).apply {
            setImageBitmap(bitmap(code))
            adjustViewBounds = true
            // Sharp modules: no filtering when the bitmap is scaled up.
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = activity.getString(R.string.export_barcode)
        }
        val info = TextView(activity).apply {
            text = buildString {
                append(activity.getString(R.string.export_barcode_info, payload.length, code.version))
                if (config.adminPasswordHash != null) append("\n").append(activity.getString(R.string.export_barcode_password))
            }
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(24), ui.dp(8), ui.dp(24), 0)
            addView(image, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(info, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(12) })
        }
        MaterialAlertDialogBuilder(activity)
            .setIcon(ui.icon(R.drawable.ic_qr))
            .setTitle(R.string.export_barcode)
            .setView(content)
            .setPositiveButton(R.string.button_close, null)
            .show()
    }

    /** Black modules on white with the quiet zone of four modules, [SCALE] pixels per module. */
    private fun bitmap(code: QrCode): Bitmap {
        val border = 4
        val size = (code.size + border * 2) * SCALE
        val pixels = IntArray(size * size) { Color.WHITE }
        for (y in 0 until code.size) {
            for (x in 0 until code.size) {
                if (!code.getModule(x, y)) continue
                for (dy in 0 until SCALE) {
                    val row = ((y + border) * SCALE + dy) * size + (x + border) * SCALE
                    pixels.fill(Color.BLACK, row, row + SCALE)
                }
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    private const val SCALE = 8
}

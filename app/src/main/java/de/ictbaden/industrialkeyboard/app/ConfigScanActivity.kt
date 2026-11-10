package de.ictbaden.industrialkeyboard.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.AimMode
import de.ictbaden.industrialkeyboard.config.CameraSettings
import de.ictbaden.industrialkeyboard.config.Symbology
import de.ictbaden.industrialkeyboard.scanner.CameraScanner
import de.ictbaden.industrialkeyboard.ui.CameraScanView

/**
 * Reads a configuration QR code with the camera (settings → import from barcode). Single shot,
 * QR codes only, anywhere in the picture; returns the text as [EXTRA_TEXT]. Not exported.
 */
class ConfigScanActivity : ComponentActivity() {

    private lateinit var view: CameraScanView
    private lateinit var scanner: CameraScanner

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            start()
        } else {
            Toast.makeText(this, getString(R.string.message_with_code, getString(R.string.camera_permission_denied), ErrorCodes.CAMERA_PERMISSION), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view = CameraScanView(this)
        val root = FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            insets
        }
        scanner = CameraScanner(
            this,
            onResult = { hit ->
                setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, hit.text))
                finish()
            },
            onError = {
                Toast.makeText(this, getString(R.string.message_with_code, getString(R.string.camera_failed), ErrorCodes.CAMERA_FAILED), Toast.LENGTH_LONG).show()
                finish()
            },
        )
        scanner.settings = CameraSettings(symbologies = setOf(Symbology.QR), aim = AimMode.ANYWHERE, beep = false, timeoutSeconds = 0)
        view.reset(AimMode.ANYWHERE, 1f)
        view.setStatus(getString(R.string.import_barcode_hint))
        view.onClose = { finish() }
        view.onTorch = { on -> scanner.setTorch(on) }
        view.onZoom = { scanner.cycleZoom() }
        view.onScreen = { scanner.screen = it }
        scanner.onStarted = { hasTorch, torchOn -> view.showTorch(hasTorch, torchOn) }
    }

    override fun onResume() {
        super.onResume()
        if (CameraPermissionActivity.granted(this)) start() else permission.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        scanner.close()
        super.onPause()
    }

    override fun onDestroy() {
        scanner.destroy()
        super.onDestroy()
    }

    private fun start() {
        if (!isFinishing) scanner.open(view.preview)
    }

    companion object {
        const val EXTRA_TEXT = "text"
    }
}

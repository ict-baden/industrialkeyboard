package de.ictbaden.industrialkeyboard.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import de.ictbaden.industrialkeyboard.R

/**
 * Asks for the camera permission on behalf of the keyboard: an input method cannot show the
 * permission dialog itself. Transparent; after the answer the user taps scan again.
 */
class CameraPermissionActivity : ComponentActivity() {

    private val request = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(
            this,
            if (granted) getString(R.string.camera_permission_granted) else "${getString(R.string.camera_permission_denied)} (${ErrorCodes.CAMERA_PERMISSION})",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (granted(this)) {
            finish()
            return
        }
        if (savedInstanceState == null) request.launch(Manifest.permission.CAMERA)
    }

    companion object {
        fun granted(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context) {
            context.startActivity(Intent(context, CameraPermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

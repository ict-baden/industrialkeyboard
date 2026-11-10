package de.ictbaden.industrialkeyboard.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Whether the device has a scan engine (imager, laser, Bluetooth scanner). Zebra devices are asked
 * through DataWedge (`ENUMERATE_SCANNERS`), because DataWedge is installed on every Zebra device –
 * also on those without an imager, where it only offers the camera. Honeywell devices with their
 * scanner service count as having one. The answer is kept for the whole process.
 */
object HardwareScanner {

    enum class State { UNKNOWN, PRESENT, ABSENT }

    @Volatile
    var state: State = State.UNKNOWN
        private set

    /** Called on the main thread once the state is known. */
    private val listeners = mutableListOf<() -> Unit>()
    private var asked = false

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Starts the detection once per process; cheap to call again. */
    fun detect(context: Context) {
        if (asked) return
        asked = true
        val app = context.applicationContext
        when {
            installed(app, DATAWEDGE_PACKAGE) -> askDataWedge(app)
            installed(app, HONEYWELL_PACKAGE) -> publish(State.PRESENT, "Honeywell scanner service")
            else -> publish(State.ABSENT, "no scanner service")
        }
    }

    /** One entry of DataWedge's scanner list. */
    data class Listed(val identifier: String?, val name: String?, val connected: Boolean)

    /**
     * DataWedge lists every scanner it supports, also Bluetooth and USB scanners that were never
     * paired. A scan engine is a built-in one that is not the camera (`INTERNAL_IMAGER`,
     * `INTERNAL_LASER` …) or an external scanner that is connected right now.
     */
    fun hasScanEngine(scanners: List<Listed>): Boolean = scanners.any { s ->
        !isCamera(s.identifier) && !isCamera(s.name) &&
            (s.identifier?.startsWith("INTERNAL_", ignoreCase = true) == true || s.connected)
    }

    private fun isCamera(text: String?) = text?.contains("CAMERA", ignoreCase = true) == true

    private fun askDataWedge(context: Context) {
        val handler = Handler(Looper.getMainLooper())
        lateinit var receiver: BroadcastReceiver
        val timeout = Runnable {
            runCatching { context.unregisterReceiver(receiver) }
            // DataWedge did not answer: keep the old behaviour and trust it.
            publish(State.PRESENT, "DataWedge did not answer")
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                @Suppress("DEPRECATION")
                val list = intent.extras?.get(RESULT_ENUMERATE) as? ArrayList<*> ?: return
                handler.removeCallbacks(timeout)
                runCatching { context.unregisterReceiver(this) }
                val scanners = list.filterIsInstance<Bundle>().map {
                    Listed(it.getString("SCANNER_IDENTIFIER"), it.getString("SCANNER_NAME"), it.getBoolean("SCANNER_CONNECTION_STATE"))
                }
                publish(if (hasScanEngine(scanners)) State.PRESENT else State.ABSENT, "DataWedge: $scanners")
            }
        }
        val filter = IntentFilter(RESULT_ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        // DataWedge is a separate app, so the receiver has to be exported.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        handler.postDelayed(timeout, TIMEOUT_MS)
        val ask = Intent(API_ACTION).setPackage(DATAWEDGE_PACKAGE)
            .putExtra(ENUMERATE, "")
            .putExtra("SEND_RESULT", "true")
        runCatching { context.sendBroadcast(ask) }.onFailure {
            handler.removeCallbacks(timeout)
            runCatching { context.unregisterReceiver(receiver) }
            publish(State.PRESENT, "DataWedge not reachable: $it")
        }
    }

    private fun publish(result: State, reason: String) {
        Log.i(TAG, "Scan engine ${result.name.lowercase()} ($reason, ${Build.MANUFACTURER} ${Build.MODEL})")
        Handler(Looper.getMainLooper()).post {
            state = result
            listeners.toList().forEach { it() }
        }
    }

    fun installed(context: Context, pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    fun hasCamera(context: Context) = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    private const val TAG = "ScanEngine"
    private const val TIMEOUT_MS = 2_000L
    const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"
    const val HONEYWELL_PACKAGE = "com.intermec.datacollectionservice"
    private const val API_ACTION = "com.symbol.datawedge.api.ACTION"
    private const val ENUMERATE = "com.symbol.datawedge.api.ENUMERATE_SCANNERS"
    private const val RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
    private const val RESULT_ENUMERATE = "com.symbol.datawedge.api.RESULT_ENUMERATE_SCANNERS"
}

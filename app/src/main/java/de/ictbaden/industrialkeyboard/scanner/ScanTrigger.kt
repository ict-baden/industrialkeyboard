package de.ictbaden.industrialkeyboard.scanner

import android.content.Context
import android.content.Intent
import android.util.Log
import de.ictbaden.industrialkeyboard.AppLanguage
import de.ictbaden.industrialkeyboard.R
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.ScannerSettings

/**
 * Starts and stops the barcode scanner by software trigger. The barcode itself is typed by the
 * scanner service (keystroke output) or arrives as a broadcast that the keyboard types.
 */
class ScanTrigger(private val context: Context) {

    var settings = ScannerSettings()
        set(value) {
            // Only these decide the type; other changes (an app profile's scan suffix when the
            // focus moves) keep it, so a new field causes no package queries.
            if (value.trigger != field.trigger || value.intentAction != field.intentAction) resolved = null
            field = value
        }

    /** Resolves the type again on next use (a new configuration: apps may have been installed meanwhile). */
    fun refresh() {
        resolved = null
    }

    private var resolved: ScanTriggerType? = null
    private var resolvedFor: HardwareScanner.State? = null

    val type: ScanTriggerType
        get() {
            // AUTO depends on the scan engine detection, which answers a moment after the start.
            val engine = HardwareScanner.state
            if (resolvedFor != engine) resolved = null
            return resolved ?: resolve().also {
                resolved = it
                resolvedFor = engine
            }
        }

    val available: Boolean get() = type != ScanTriggerType.NONE

    /** Name for the scan pad; `null` when no scanner is available. */
    val name: String?
        get() = when (type) {
            ScanTriggerType.DATAWEDGE -> "Zebra DataWedge"
            ScanTriggerType.HONEYWELL -> "Honeywell"
            ScanTriggerType.INTENT -> AppLanguage.localized(context).getString(R.string.scanner_app)
            ScanTriggerType.CAMERA -> AppLanguage.localized(context).getString(R.string.scanner_camera)
            else -> null
        }

    fun start() = send(START)

    fun stop() = send(STOP)

    fun toggle() = send(TOGGLE)

    private fun send(command: Int) {
        val intent = when (type) {
            ScanTriggerType.DATAWEDGE -> Intent(DATAWEDGE_ACTION).setPackage(DATAWEDGE_PACKAGE).putExtra(
                DATAWEDGE_SOFT_TRIGGER,
                when (command) {
                    START -> "START_SCANNING"
                    STOP -> "STOP_SCANNING"
                    else -> "TOGGLE_SCANNING"
                },
            )
            ScanTriggerType.HONEYWELL -> Intent(HONEYWELL_ACTION).setPackage(HONEYWELL_PACKAGE).putExtra(HONEYWELL_SCAN, command != STOP)
            ScanTriggerType.INTENT -> if (command == STOP) null else Intent(settings.intentAction).apply { settings.intentPackage?.let(::setPackage) }
            else -> null
        } ?: return
        runCatching { context.sendBroadcast(intent) }.onFailure { Log.w(TAG, "Scan trigger failed", it) }
    }

    private fun resolve(): ScanTriggerType = when (settings.trigger) {
        ScanTriggerType.AUTO -> auto(
            dataWedge = HardwareScanner.installed(context, DATAWEDGE_PACKAGE),
            honeywell = HardwareScanner.installed(context, HONEYWELL_PACKAGE),
            engine = HardwareScanner.state,
            camera = HardwareScanner.hasCamera(context),
        )
        ScanTriggerType.INTENT -> if (settings.intentAction.isNullOrBlank()) ScanTriggerType.NONE else ScanTriggerType.INTENT
        ScanTriggerType.CAMERA -> if (HardwareScanner.hasCamera(context)) ScanTriggerType.CAMERA else ScanTriggerType.NONE
        else -> settings.trigger
    }

    companion object {
        /**
         * `auto`: the scan engine of the device if it has one, otherwise the camera. While the
         * detection runs, a device with DataWedge or the Honeywell service counts as having one.
         */
        fun auto(dataWedge: Boolean, honeywell: Boolean, engine: HardwareScanner.State, camera: Boolean): ScanTriggerType = when {
            engine != HardwareScanner.State.ABSENT && dataWedge -> ScanTriggerType.DATAWEDGE
            engine != HardwareScanner.State.ABSENT && honeywell -> ScanTriggerType.HONEYWELL
            camera -> ScanTriggerType.CAMERA
            else -> ScanTriggerType.NONE
        }

        private const val TAG = "ScanTrigger"
        private const val START = 0
        private const val STOP = 1
        private const val TOGGLE = 2
        private const val DATAWEDGE_PACKAGE = HardwareScanner.DATAWEDGE_PACKAGE
        private const val DATAWEDGE_ACTION = "com.symbol.datawedge.api.ACTION"
        private const val DATAWEDGE_SOFT_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
        private const val HONEYWELL_PACKAGE = HardwareScanner.HONEYWELL_PACKAGE
        private const val HONEYWELL_ACTION = "com.honeywell.aidc.action.ACTION_CONTROL_SCANNER"
        private const val HONEYWELL_SCAN = "com.honeywell.aidc.extra.EXTRA_SCAN"
    }
}

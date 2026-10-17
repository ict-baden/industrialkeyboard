package de.ictbaden.industrialkeyboard.scanner

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import de.ictbaden.industrialkeyboard.config.ScanTriggerType
import de.ictbaden.industrialkeyboard.config.ScannerSettings

/**
 * Starts and stops the barcode scanner by software trigger. The barcode itself is typed by the
 * scanner service (keystroke output) or arrives as a broadcast that the keyboard types.
 */
class ScanTrigger(private val context: Context) {

    var settings = ScannerSettings()
        set(value) {
            field = value
            resolved = null
        }

    private var resolved: ScanTriggerType? = null

    val type: ScanTriggerType
        get() = resolved ?: resolve().also { resolved = it }

    val available: Boolean get() = type != ScanTriggerType.NONE

    /** Name for the scan pad; `null` when no scanner is available. */
    val name: String?
        get() = when (type) {
            ScanTriggerType.DATAWEDGE -> "Zebra DataWedge"
            ScanTriggerType.HONEYWELL -> "Honeywell"
            ScanTriggerType.INTENT -> "Scanner app"
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
        ScanTriggerType.AUTO -> when {
            installed(DATAWEDGE_PACKAGE) -> ScanTriggerType.DATAWEDGE
            installed(HONEYWELL_PACKAGE) -> ScanTriggerType.HONEYWELL
            else -> ScanTriggerType.NONE
        }
        ScanTriggerType.INTENT -> if (settings.intentAction.isNullOrBlank()) ScanTriggerType.NONE else ScanTriggerType.INTENT
        else -> settings.trigger
    }

    private fun installed(pkg: String) = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    private companion object {
        const val TAG = "ScanTrigger"
        const val START = 0
        const val STOP = 1
        const val TOGGLE = 2
        const val DATAWEDGE_PACKAGE = "com.symbol.datawedge"
        const val DATAWEDGE_ACTION = "com.symbol.datawedge.api.ACTION"
        const val DATAWEDGE_SOFT_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
        const val HONEYWELL_PACKAGE = "com.intermec.datacollectionservice"
        const val HONEYWELL_ACTION = "com.honeywell.aidc.action.ACTION_CONTROL_SCANNER"
        const val HONEYWELL_SCAN = "com.honeywell.aidc.extra.EXTRA_SCAN"
    }
}

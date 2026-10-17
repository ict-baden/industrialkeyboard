package de.ictbaden.industrialkeyboard

import android.app.Application
import de.ictbaden.industrialkeyboard.config.Provisioning

class KeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Provisioning.applyAll(this)
    }
}

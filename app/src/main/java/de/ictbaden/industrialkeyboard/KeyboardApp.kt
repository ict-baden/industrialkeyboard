package de.ictbaden.industrialkeyboard

import android.app.Application
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.Migrations
import de.ictbaden.industrialkeyboard.config.Provisioning
import de.ictbaden.industrialkeyboard.scanner.HardwareScanner

class KeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Migrations.run(this)
        Provisioning.applyAllAsync(this)
        // Scan engine or camera: asked once per process, the keyboard and the settings use the answer.
        HardwareScanner.detect(this)
        val store = ConfigStore.get(this)
        // Before the first screen, and again whenever the settings, an import or the MDM change it.
        AppLanguage.apply(this, store.config.language)
        store.addListener { AppLanguage.apply(this, it.language) }
    }
}

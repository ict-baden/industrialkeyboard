package de.ictbaden.industrialkeyboard

import android.app.Application
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.Migrations
import de.ictbaden.industrialkeyboard.config.Provisioning

class KeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Migrations.run(this)
        Provisioning.applyAllAsync(this)
        val store = ConfigStore.get(this)
        // Before the first screen, and again whenever the settings, an import or the MDM change it.
        AppLanguage.apply(this, store.config.language)
        store.addListener { AppLanguage.apply(this, it.language) }
    }
}

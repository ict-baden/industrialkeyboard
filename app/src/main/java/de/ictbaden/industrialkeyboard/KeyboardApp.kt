package de.ictbaden.industrialkeyboard

import android.app.Application
import de.ictbaden.industrialkeyboard.config.ConfigStore
import de.ictbaden.industrialkeyboard.config.Provisioning

class KeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Provisioning.applyAll(this)
        val store = ConfigStore.get(this)
        // Before the first screen, and again whenever the settings, an import or the MDM change it.
        AppLanguage.apply(store.config.language)
        store.addListener { AppLanguage.apply(it.language) }
    }
}

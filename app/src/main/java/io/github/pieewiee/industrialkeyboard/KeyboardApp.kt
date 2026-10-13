package io.github.pieewiee.industrialkeyboard

import android.app.Application
import io.github.pieewiee.industrialkeyboard.config.Provisioning

class KeyboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Provisioning.applyAll(this)
    }
}

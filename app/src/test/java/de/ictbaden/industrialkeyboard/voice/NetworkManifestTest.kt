package de.ictbaden.industrialkeyboard.voice

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The internet permission exists only for the language packs (issue #24): ML Kit's usage statistics
 * must still have no way out – the datatransport upload backend ("cct") stays removed from the merged manifest.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class NetworkManifestTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `no datatransport upload backend is registered`() {
        val discovery = ComponentName(context, "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery")
        val meta = context.packageManager.getServiceInfo(discovery, PackageManager.GET_META_DATA).metaData
        val keys = meta?.keySet().orEmpty()
        assertFalse("backends: $keys", keys.any { it.startsWith("backend:") })
    }

    @Test
    fun `internet permission is requested for the download`() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toList()
        assertTrue(permissions.toString(), Manifest.permission.INTERNET in permissions)
        assertTrue(permissions.toString(), Manifest.permission.ACCESS_NETWORK_STATE in permissions)
    }
}

package org.pysh.janus

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class JanusApplication :
    Application(),
    XposedServiceHelper.OnServiceListener {
    var xposedService: XposedService? = null
        private set

    /** Observable state for Compose UI. */
    val isServiceBound = mutableStateOf(false)

    override fun onCreate() {
        super.onCreate()
        instance = this
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        xposedService = service
        isServiceBound.value = true
        Log.i(TAG, "XposedService bound, API version: ${service.apiVersion}")
        Log.i(TAG, "Framework: ${service.frameworkName} ${service.frameworkVersion}")
        try {
            val scope = service.scope
            Log.i(TAG, "Current scope: $scope")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to get scope: ${e.message}")
        }
        // Sync all config to RemotePreferences on service bind
        try {
            val wm =
                org.pysh.janus.data
                    .WhitelistManager(this)
            wm.syncAllToRemotePrefs()
            Log.i(TAG, "Config synced to RemotePreferences")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync config: ${e.message}")
        }
    }

    override fun onServiceDied(service: XposedService) {
        xposedService = null
        isServiceBound.value = false
        Log.w(TAG, "XposedService died")
    }

    companion object {
        private const val TAG = "Janus"

        @Volatile
        var instance: JanusApplication? = null
            private set
    }
}

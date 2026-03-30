package org.pysh.janus.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.pysh.janus.BuildConfig
import org.pysh.janus.data.WhitelistManager

/**
 * Debug-only BroadcastReceiver that allows ADB to set config values
 * for automated testing without UI interaction.
 *
 * Usage:
 *   adb shell am broadcast -a org.pysh.janus.action.SET_CONFIG \
 *       --es key tracking_disabled --ez value true
 *
 *   adb shell am broadcast -a org.pysh.janus.action.SET_CONFIG \
 *       --es key whitelist --es value "com.netease.cloudmusic,com.tencent.qqmusic"
 *
 * Only active in debug builds — the receiver is declared in
 * app/src/debug/AndroidManifest.xml which is not included in release.
 */
class TestConfigReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return

        val key = intent.getStringExtra("key") ?: return
        val manager = WhitelistManager(context)

        Log.d(TAG, "SET_CONFIG: key=$key")

        when (key) {
            "tracking_disabled" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setTrackingDisabled(value)
                Log.d(TAG, "tracking_disabled=$value")
            }
            "wallpaper_lock" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setWallpaperLocked(value)
                Log.d(TAG, "wallpaper_lock=$value")
            }
            "wallpaper_keep_alive" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setWallpaperKeepAlive(value)
                Log.d(TAG, "wallpaper_keep_alive=$value")
            }
            "hide_time_tip" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setTimeTipHidden(value)
                Log.d(TAG, "hide_time_tip=$value")
            }
            "whitelist" -> {
                val csv = intent.getStringExtra("value") ?: ""
                val packages = csv.split(",").filter { it.isNotBlank() }.toSet()
                manager.saveWhitelist(packages)
                Log.d(TAG, "whitelist=${packages.joinToString(",")}")
            }
            "keep_alive" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setKeepAliveEnabled(value)
                Log.d(TAG, "keep_alive=$value")
            }
            "wallpaper_loop" -> {
                val value = intent.getBooleanExtra("value", false)
                manager.setWallpaperLoop(value)
                Log.d(TAG, "wallpaper_loop=$value")
            }
            else -> Log.w(TAG, "Unknown config key: $key")
        }
    }

    companion object {
        private const val TAG = "Janus-TestConfig"
    }
}

package org.pysh.janus.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import org.pysh.janus.JanusApplication
import java.io.File

class WhitelistManager(
    private val context: Context,
) {
    private val TAG = "Janus-Config"

    companion object {
        const val PREFS_NAME = "janus_config"
        const val KEY_WHITELIST = "whitelist"
        const val KEY_DISABLE_TRACKING = "tracking_disabled"

        // Legacy SP key names — kept for one-time migration in init{}
        private const val LEGACY_KEY_WHITELIST = "music_whitelist"
        private const val LEGACY_KEY_DISABLE_TRACKING = "disable_tracking"
        const val KEY_ACTIVATED = "activated"
        const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
        const val KEY_KEEP_ALIVE_INTERVAL = "keep_alive_interval"
        const val KEY_CAST_ROTATION = "cast_rotation" // 0=none, 1=left, 3=right
        const val KEY_CAST_KEEP_ALIVE = "cast_keep_alive"
        const val KEY_WALLPAPER_KEEP_ALIVE = "wallpaper_keep_alive"
        const val KEY_WALLPAPER_LOCK = "wallpaper_lock"
        const val KEY_HIDE_TIME_TIP = "hide_time_tip"
        const val KEY_FOCUS_NOTICE_ALLOW = "focus_notice_dynamic_allow"
        const val KEY_THEME_AUTO_RESTART = "theme_auto_restart"
        const val KEY_WALLPAPER_LOOP = "wallpaper_loop"
        const val KEY_AUTO_ROTATE = "auto_rotate"
        const val KEY_AUTO_ROTATE_INTERVAL = "auto_rotate_interval"
    }

    private val prefs: SharedPreferences =
        try {
            @Suppress("DEPRECATION")
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            // MODE_WORLD_READABLE throws on targetSdk >= 24 without Xposed context.
            // Fall back to MODE_PRIVATE; XSharedPreferences on the hook side reads
            // the file directly regardless of this mode flag.
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
                makePrefsWorldReadable()
            }
        }

    init {
        // One-time SP key migration: rename legacy key names to match
        // RemotePreferences key names. After this, the SP file and RemotePrefs
        // use the same keys and no mapping table is needed in syncAllToRemotePrefs.
        migrateLegacySpKey(LEGACY_KEY_WHITELIST, KEY_WHITELIST)
        migrateLegacySpKey(LEGACY_KEY_DISABLE_TRACKING, KEY_DISABLE_TRACKING)
    }

    private fun migrateLegacySpKey(oldKey: String, newKey: String) {
        if (prefs.contains(oldKey) && !prefs.contains(newKey)) {
            val value = prefs.all[oldKey]
            val editor = prefs.edit()
            when (value) {
                is String -> editor.putString(newKey, value)
                is Boolean -> editor.putBoolean(newKey, value)
                is Int -> editor.putInt(newKey, value)
                is Long -> editor.putLong(newKey, value)
                is Float -> editor.putFloat(newKey, value)
            }
            editor.remove(oldKey).commit()
            makePrefsWorldReadable()
        }
    }

    fun getWhitelist(): Set<String> {
        val raw = prefs.getString(KEY_WHITELIST, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun isActivated(): Boolean = prefs.getBoolean(KEY_ACTIVATED, false)

    fun setActivated() {
        prefs.edit().putBoolean(KEY_ACTIVATED, true).commit()
    }

    fun isTrackingDisabled(): Boolean = prefs.getBoolean(KEY_DISABLE_TRACKING, false)

    fun setTrackingDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_TRACKING, disabled).commit()
        makePrefsWorldReadable()
        syncRemoteBool(KEY_DISABLE_TRACKING, disabled)
    }

    fun isKeepAliveEnabled(): Boolean = prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, false)

    fun setKeepAliveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).commit()
        makePrefsWorldReadable()
    }

    fun getKeepAliveInterval(): Int = prefs.getInt(KEY_KEEP_ALIVE_INTERVAL, 10)

    fun setKeepAliveInterval(seconds: Int) {
        prefs.edit().putInt(KEY_KEEP_ALIVE_INTERVAL, seconds).commit()
        makePrefsWorldReadable()
    }

    fun getCastRotation(): Int = prefs.getInt(KEY_CAST_ROTATION, 0)

    fun setCastRotation(rotation: Int) {
        prefs.edit().putInt(KEY_CAST_ROTATION, rotation).commit()
        makePrefsWorldReadable()
    }

    fun isCastKeepAlive(): Boolean = prefs.getBoolean(KEY_CAST_KEEP_ALIVE, false)

    fun setCastKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAST_KEEP_ALIVE, enabled).commit()
        makePrefsWorldReadable()
    }

    fun isWallpaperKeepAlive(): Boolean = prefs.getBoolean(KEY_WALLPAPER_KEEP_ALIVE, false)

    fun setWallpaperKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_KEEP_ALIVE, enabled).commit()
        makePrefsWorldReadable()
        syncRemoteBool(KEY_WALLPAPER_KEEP_ALIVE, enabled)
    }

    fun isWallpaperLocked(): Boolean = prefs.getBoolean(KEY_WALLPAPER_LOCK, false)

    fun setWallpaperLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOCK, locked).commit()
        makePrefsWorldReadable()
        syncRemoteBool(KEY_WALLPAPER_LOCK, locked)
    }

    fun isTimeTipHidden(): Boolean = prefs.getBoolean(KEY_HIDE_TIME_TIP, false)

    fun setTimeTipHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_TIME_TIP, hidden).commit()
        makePrefsWorldReadable()
        syncRemoteBool(KEY_HIDE_TIME_TIP, hidden)
    }

    /**
     * Whether third-party focus notifications are allowed to dynamically
     * bypass the subscreencenter whitelist. The hook side reads this via
     * ConfigSource("focus_notice_dynamic_allow"), which is file-flag backed
     * under CONFIG_DIR so the toggle takes effect without restarting the host.
     */
    fun isFocusNoticeAllowEnabled(): Boolean = prefs.getBoolean(KEY_FOCUS_NOTICE_ALLOW, false)

    fun setFocusNoticeAllowEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FOCUS_NOTICE_ALLOW, enabled).commit()
        makePrefsWorldReadable()
        syncRemoteBool(KEY_FOCUS_NOTICE_ALLOW, enabled)
    }

    /** Whether to force-stop subscreencenter after applying/deactivating a theme. Defaults true. */
    fun isThemeAutoRestart(): Boolean = prefs.getBoolean(KEY_THEME_AUTO_RESTART, true)

    fun setThemeAutoRestart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_THEME_AUTO_RESTART, enabled).commit()
        makePrefsWorldReadable()
    }

    fun isWallpaperLoop(): Boolean = prefs.getBoolean(KEY_WALLPAPER_LOOP, false)

    fun setWallpaperLoop(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOOP, enabled).commit()
    }

    fun isAutoRotateEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_ROTATE, false)

    fun setAutoRotateEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ROTATE, enabled).commit()
    }

    /**
     * Wallpaper auto-rotate interval, in **seconds**.
     *
     * UI labels and the `wp_auto_rotate_interval_summary` string both render
     * this value as seconds; `WallpaperRotationReceiver.scheduleNext` also
     * treats it as seconds (`intervalSeconds * 1000L` for `AlarmManager`).
     * Default 30 seconds.
     */
    fun getAutoRotateInterval(): Int = prefs.getInt(KEY_AUTO_ROTATE_INTERVAL, 30)

    fun setAutoRotateInterval(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_ROTATE_INTERVAL, seconds).commit()
    }

    fun saveWhitelist(packages: Set<String>) {
        val oldWhitelist = getWhitelist()
        prefs
            .edit()
            .putString(KEY_WHITELIST, packages.joinToString(","))
            .commit()
        makePrefsWorldReadable()
        syncAllToRemotePrefs()
        syncWhitelistScope(oldWhitelist, packages)
    }

    /**
     * Sync whitelist scope changes to LSPosed — requestScope for added packages,
     * removeScope for removed packages. This makes LSPosed prompt the user to approve
     * the scope expansion, after which the hook will be loaded in that app.
     */
    private fun syncWhitelistScope(
        oldWhitelist: Set<String>,
        newWhitelist: Set<String>,
    ) {
        val added = newWhitelist - oldWhitelist
        val removed = oldWhitelist - newWhitelist
        if (added.isEmpty() && removed.isEmpty()) return

        val service = JanusApplication.instance?.xposedService ?: return
        try {
            if (added.isNotEmpty()) {
                service.requestScope(
                    added.toList(),
                    object : XposedService.OnScopeEventListener {
                        override fun onScopeRequestApproved(approved: List<String>) {
                            Log.i(TAG, "Scope request approved: $approved")
                        }

                        override fun onScopeRequestFailed(message: String) {
                            Log.w(TAG, "Scope request failed: $message")
                        }
                    },
                )
            }
            if (removed.isNotEmpty()) {
                service.removeScope(removed.toList())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync scope: ${e.message}")
        }
    }

    /** Sync all SP settings to RemotePreferences so the hook side can read them. */
    fun syncAllFlags() {
        // Card config is synced by CardManager.syncConfig()
        syncAllToRemotePrefs()
    }

    /**
     * Sync all config to RemotePreferences for the hook side.
     * Key names match the JSON rule configFlag values.
     */
    fun syncAllToRemotePrefs() {
        val remotePrefs =
            try {
                JanusApplication.instance?.xposedService?.getRemotePreferences("janus_config")
            } catch (e: Throwable) {
                Log.w(TAG, "RemotePreferences not available: ${e.message}")
                null
            } ?: return

        try {
            remotePrefs
                .edit()
                .putString(KEY_WHITELIST, getWhitelist().joinToString(","))
                .putBoolean(KEY_DISABLE_TRACKING, isTrackingDisabled())
                .putBoolean(KEY_WALLPAPER_KEEP_ALIVE, isWallpaperKeepAlive())
                .putBoolean(KEY_WALLPAPER_LOCK, isWallpaperLocked())
                .putBoolean(KEY_HIDE_TIME_TIP, isTimeTipHidden())
                .putBoolean(KEY_FOCUS_NOTICE_ALLOW, isFocusNoticeAllowEnabled())
                .commit()
            Log.d(TAG, "Config synced to RemotePreferences")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync RemotePreferences: ${e.message}")
        }
    }

    /** Sync a single boolean config to RemotePreferences. */
    private fun syncRemoteBool(
        key: String,
        value: Boolean,
    ) {
        try {
            JanusApplication.instance
                ?.xposedService
                ?.getRemotePreferences("janus_config")
                ?.edit()
                ?.putBoolean(key, value)
                ?.commit()
        } catch (_: Throwable) {
            // RemotePrefs not available yet
        }
    }

    /**
     * Manually chmod the prefs file so XSharedPreferences can read it.
     * This is the standard workaround for Xposed modules on modern Android.
     */
    private fun makePrefsWorldReadable() {
        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
        val prefsFile = File(prefsDir, "$PREFS_NAME.xml")
        if (prefsFile.exists()) {
            prefsFile.setReadable(true, false)
        }
        prefsDir.setExecutable(true, false)
    }
}

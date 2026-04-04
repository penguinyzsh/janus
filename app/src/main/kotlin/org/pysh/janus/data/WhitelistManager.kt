package org.pysh.janus.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import org.pysh.janus.JanusApplication
import org.pysh.janus.util.JanusPaths
import java.io.File

class WhitelistManager(private val context: Context) {

    private val TAG = "Janus-Config"

    companion object {
        const val PREFS_NAME = "janus_config"
        const val KEY_WHITELIST = "music_whitelist"
        const val KEY_DISABLE_TRACKING = "disable_tracking"
        const val KEY_ACTIVATED = "activated"
        const val KEY_KEEP_ALIVE_ENABLED = "keep_alive_enabled"
        const val KEY_KEEP_ALIVE_INTERVAL = "keep_alive_interval"
        const val KEY_CAST_ROTATION = "cast_rotation" // 0=none, 1=left, 3=right
        const val KEY_CAST_KEEP_ALIVE = "cast_keep_alive"
        const val KEY_WALLPAPER_KEEP_ALIVE = "wallpaper_keep_alive"
        const val KEY_WALLPAPER_LOCK = "wallpaper_lock"
        const val KEY_HIDE_TIME_TIP = "hide_time_tip"
        const val KEY_WALLPAPER_LOOP = "wallpaper_loop"
        const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        val WHITELIST_FLAG_PATH = JanusPaths.WHITELIST
        val TRACKING_FLAG_PATH = JanusPaths.TRACKING_DISABLED
        val WALLPAPER_KEEP_ALIVE_FLAG_PATH = JanusPaths.WALLPAPER_KEEP_ALIVE
        val WALLPAPER_LOCK_FLAG_PATH = JanusPaths.WALLPAPER_LOCK
        val HIDE_TIME_TIP_FLAG_PATH = JanusPaths.HIDE_TIME_TIP
    }

    private val prefs: SharedPreferences = try {
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

    fun getWhitelist(): Set<String> {
        val raw = prefs.getString(KEY_WHITELIST, "") ?: ""
        return raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun isActivated(): Boolean {
        return prefs.getBoolean(KEY_ACTIVATED, false)
    }

    fun setActivated() {
        prefs.edit().putBoolean(KEY_ACTIVATED, true).commit()
    }

    fun isTrackingDisabled(): Boolean {
        return prefs.getBoolean(KEY_DISABLE_TRACKING, false)
    }

    fun setTrackingDisabled(disabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISABLE_TRACKING, disabled).commit()
        makePrefsWorldReadable()
        syncBooleanFlag(TRACKING_FLAG_PATH, disabled)
        syncRemoteBool("tracking_disabled", disabled)
    }

    fun isKeepAliveEnabled(): Boolean {
        return prefs.getBoolean(KEY_KEEP_ALIVE_ENABLED, false)
    }

    fun setKeepAliveEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE_ENABLED, enabled).commit()
        makePrefsWorldReadable()
    }

    fun getKeepAliveInterval(): Int {
        return prefs.getInt(KEY_KEEP_ALIVE_INTERVAL, 10)
    }

    fun setKeepAliveInterval(seconds: Int) {
        prefs.edit().putInt(KEY_KEEP_ALIVE_INTERVAL, seconds).commit()
        makePrefsWorldReadable()
    }

    fun getCastRotation(): Int {
        return prefs.getInt(KEY_CAST_ROTATION, 0)
    }

    fun setCastRotation(rotation: Int) {
        prefs.edit().putInt(KEY_CAST_ROTATION, rotation).commit()
        makePrefsWorldReadable()
    }

    fun isCastKeepAlive(): Boolean {
        return prefs.getBoolean(KEY_CAST_KEEP_ALIVE, false)
    }

    fun setCastKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CAST_KEEP_ALIVE, enabled).commit()
        makePrefsWorldReadable()
    }

    fun isWallpaperKeepAlive(): Boolean {
        return prefs.getBoolean(KEY_WALLPAPER_KEEP_ALIVE, false)
    }

    fun setWallpaperKeepAlive(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_KEEP_ALIVE, enabled).commit()
        makePrefsWorldReadable()
        syncBooleanFlag(WALLPAPER_KEEP_ALIVE_FLAG_PATH, enabled)
        syncRemoteBool("wallpaper_keep_alive", enabled)
    }

    fun isWallpaperLocked(): Boolean {
        return prefs.getBoolean(KEY_WALLPAPER_LOCK, false)
    }

    fun setWallpaperLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOCK, locked).commit()
        makePrefsWorldReadable()
        syncBooleanFlag(WALLPAPER_LOCK_FLAG_PATH, locked)
        syncRemoteBool("wallpaper_lock", locked)
    }

    fun isTimeTipHidden(): Boolean {
        return prefs.getBoolean(KEY_HIDE_TIME_TIP, false)
    }

    fun setTimeTipHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_TIME_TIP, hidden).commit()
        makePrefsWorldReadable()
        syncBooleanFlag(HIDE_TIME_TIP_FLAG_PATH, hidden)
        syncRemoteBool("hide_time_tip", hidden)
    }

    fun isWallpaperLoop(): Boolean {
        return prefs.getBoolean(KEY_WALLPAPER_LOOP, false)
    }

    fun setWallpaperLoop(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOOP, enabled).commit()
    }

    fun getLastSeenVersion(): Int {
        return prefs.getInt(KEY_LAST_SEEN_VERSION, 0)
    }

    fun setLastSeenVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION, versionCode).commit()
    }

    fun saveWhitelist(packages: Set<String>) {
        val oldWhitelist = getWhitelist()
        prefs.edit()
            .putString(KEY_WHITELIST, packages.joinToString(","))
            .commit()
        makePrefsWorldReadable()
        JanusPaths.ensureAllDirs()
        syncWhitelistFlag(packages)
        syncAllToRemotePrefs()
        syncWhitelistScope(oldWhitelist, packages)
    }

    private fun syncWhitelistFlag(packages: Set<String>) {
        if (packages.isEmpty()) {
            org.pysh.janus.util.RootUtils.exec("rm -f $WHITELIST_FLAG_PATH")
        } else {
            // Use printf to write content directly via shell, avoids app-private file access issues
            val content = packages.joinToString(",")
            val cmd = "printf '%s' '$content' > $WHITELIST_FLAG_PATH && chmod 644 $WHITELIST_FLAG_PATH && chcon u:object_r:theme_data_file:s0 $WHITELIST_FLAG_PATH"
            org.pysh.janus.util.RootUtils.exec(cmd)
        }
    }

    /**
     * Sync whitelist scope changes to LSPosed — requestScope for added packages,
     * removeScope for removed packages. This makes LSPosed prompt the user to approve
     * the scope expansion, after which the hook will be loaded in that app.
     */
    private fun syncWhitelistScope(oldWhitelist: Set<String>, newWhitelist: Set<String>) {
        val added = newWhitelist - oldWhitelist
        val removed = oldWhitelist - newWhitelist
        if (added.isEmpty() && removed.isEmpty()) return

        val service = JanusApplication.instance?.xposedService ?: return
        try {
            if (added.isNotEmpty()) {
                service.requestScope(added.toList(), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) {
                        Log.i(TAG, "Scope request approved: $approved")
                    }
                    override fun onScopeRequestFailed(message: String) {
                        Log.w(TAG, "Scope request failed: $message")
                    }
                })
            }
            if (removed.isNotEmpty()) {
                service.removeScope(removed.toList())
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync scope: ${e.message}")
        }
    }

    /** Sync all SP settings to file flags and RemotePreferences so the hook side can read them. */
    fun syncAllFlags() {
        JanusPaths.ensureAllDirs()
        syncWhitelistFlag(getWhitelist())
        syncBooleanFlag(TRACKING_FLAG_PATH, isTrackingDisabled())
        syncBooleanFlag(WALLPAPER_KEEP_ALIVE_FLAG_PATH, isWallpaperKeepAlive())
        syncBooleanFlag(WALLPAPER_LOCK_FLAG_PATH, isWallpaperLocked())
        syncBooleanFlag(HIDE_TIME_TIP_FLAG_PATH, isTimeTipHidden())
        // Card config is synced by CardManager.syncConfig()
        syncAllToRemotePrefs()
    }

    /**
     * Sync all config to RemotePreferences for the hook side.
     * Key names match the JSON rule configFlag values.
     */
    fun syncAllToRemotePrefs() {
        val remotePrefs = try {
            JanusApplication.instance?.xposedService?.getRemotePreferences("janus_config")
        } catch (e: Throwable) {
            Log.w(TAG, "RemotePreferences not available: ${e.message}")
            null
        } ?: return

        try {
            remotePrefs.edit()
                .putString("whitelist", getWhitelist().joinToString(","))
                .putBoolean("tracking_disabled", isTrackingDisabled())
                .putBoolean("wallpaper_keep_alive", isWallpaperKeepAlive())
                .putBoolean("wallpaper_lock", isWallpaperLocked())
                .putBoolean("hide_time_tip", isTimeTipHidden())
                .commit()
            Log.d(TAG, "Config synced to RemotePreferences")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to sync RemotePreferences: ${e.message}")
        }
    }

    /** Sync a single boolean config to RemotePreferences. */
    private fun syncRemoteBool(key: String, value: Boolean) {
        try {
            JanusApplication.instance?.xposedService
                ?.getRemotePreferences("janus_config")
                ?.edit()?.putBoolean(key, value)?.commit()
        } catch (_: Throwable) { /* RemotePrefs not available yet */ }
    }

    private fun syncBooleanFlag(path: String, enabled: Boolean) {
        if (enabled) {
            org.pysh.janus.util.RootUtils.exec("touch $path && chmod 644 $path && chcon u:object_r:theme_data_file:s0 $path")
        } else {
            org.pysh.janus.util.RootUtils.exec("rm -f $path")
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

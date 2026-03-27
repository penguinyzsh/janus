package org.pysh.janus.data

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class WhitelistManager(private val context: Context) {

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
        const val KEY_WALLPAPER_LOOP = "wallpaper_loop"
        const val KEY_WEATHER_CARD_ENABLED = "weather_card_enabled"
        const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        const val WEATHER_FLAG_PATH =
            "/data/system/theme_magic/users/0/subscreencenter/config/janus_weather"
        const val NOTIFICATION_WIDGET_JSON =
            "/data/system/theme_magic/users/0/subscreencenter/notification/notification_widget.json"
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
    }

    fun isWallpaperLocked(): Boolean {
        return prefs.getBoolean(KEY_WALLPAPER_LOCK, false)
    }

    fun setWallpaperLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOCK, locked).commit()
        makePrefsWorldReadable()
    }

    fun isWallpaperLoop(): Boolean {
        return prefs.getBoolean(KEY_WALLPAPER_LOOP, false)
    }

    fun setWallpaperLoop(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WALLPAPER_LOOP, enabled).commit()
    }

    fun isWeatherCardEnabled(): Boolean {
        return prefs.getBoolean(KEY_WEATHER_CARD_ENABLED, false)
    }

    fun setWeatherCardEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WEATHER_CARD_ENABLED, enabled).commit()
        makePrefsWorldReadable()
        // File flag readable by subscreencenter (bypasses SELinux XSharedPreferences issue)
        val flag = WEATHER_FLAG_PATH
        if (enabled) {
            org.pysh.janus.util.RootUtils.exec("touch $flag && chmod 644 $flag")
        } else {
            org.pysh.janus.util.RootUtils.exec("rm -f $flag")
            removePersistedWeatherWidget()
        }
    }

    /**
     * Remove Janus weather entry from notification_widget.json so no stale
     * black card remains after disabling the feature.
     */
    private fun removePersistedWeatherWidget() {
        try {
            val raw = org.pysh.janus.util.RootUtils.execWithOutput("cat $NOTIFICATION_WIDGET_JSON") ?: return
            val arr = org.json.JSONArray(raw)
            val filtered = org.json.JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val pkg = obj.optJSONObject("extra")?.optString("package_name")
                if (pkg != "org.pysh.janus") filtered.put(obj)
            }
            if (filtered.length() < arr.length()) {
                // Write to app-private tmp then root-move to target (avoids shell escaping)
                val tmp = File(context.cacheDir, "nw_tmp.json")
                tmp.writeText(filtered.toString())
                org.pysh.janus.util.RootUtils.exec("cp ${tmp.absolutePath} $NOTIFICATION_WIDGET_JSON && chmod 666 $NOTIFICATION_WIDGET_JSON")
                tmp.delete()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    fun getLastSeenVersion(): Int {
        return prefs.getInt(KEY_LAST_SEEN_VERSION, 0)
    }

    fun setLastSeenVersion(versionCode: Int) {
        prefs.edit().putInt(KEY_LAST_SEEN_VERSION, versionCode).commit()
    }

    fun saveWhitelist(packages: Set<String>) {
        prefs.edit()
            .putString(KEY_WHITELIST, packages.joinToString(","))
            .commit()
        makePrefsWorldReadable()
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

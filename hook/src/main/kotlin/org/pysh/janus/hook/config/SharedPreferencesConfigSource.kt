package org.pysh.janus.hook.config

import android.content.SharedPreferences
import org.pysh.janus.hookapi.ConfigSource

/**
 * [ConfigSource] backed by an Android [SharedPreferences] instance — used at
 * runtime with the store returned by `XposedModule.getRemotePreferences`,
 * which bridges config values from the module UI process to the hooked host.
 */
class SharedPreferencesConfigSource(
    private val prefs: SharedPreferences,
) : ConfigSource {
    override fun getString(key: String, defaultValue: String?): String? =
        try {
            prefs.getString(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try {
            prefs.getBoolean(key, defaultValue)
        } catch (_: Throwable) {
            defaultValue
        }
}

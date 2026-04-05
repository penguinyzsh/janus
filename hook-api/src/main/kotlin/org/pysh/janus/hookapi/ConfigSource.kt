package org.pysh.janus.hookapi

/**
 * Minimal read-only key/value configuration contract consumed by hook engines.
 *
 * Decouples engine code from Android's `SharedPreferences` so that:
 *  - engines can be exercised from pure-JVM unit tests with an in-memory impl;
 *  - the hook side can swap the backing store (RemotePreferences, file-flag
 *    override, or empty fallback) without touching engine logic.
 *
 * Only the operations currently used by engines are exposed — extend
 * deliberately rather than mirroring the full SharedPreferences surface.
 */
interface ConfigSource {
    fun getString(key: String, defaultValue: String?): String?
    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    /** Sentinel instance used when no backing store is available. */
    object Empty : ConfigSource {
        override fun getString(key: String, defaultValue: String?): String? = defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean = defaultValue
    }
}

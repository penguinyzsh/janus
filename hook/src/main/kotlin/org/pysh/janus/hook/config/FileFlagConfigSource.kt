package org.pysh.janus.hook.config

import android.util.Log
import org.pysh.janus.hookapi.ConfigSource
import java.io.File

/**
 * [ConfigSource] that layers a directory of "flag" files on top of a delegate.
 *
 * For boolean reads, a file `$flagsDir/$key` takes precedence over the delegate:
 *  - file absent → delegate.getBoolean(key, default)
 *  - file present with content "0" / "false" → false
 *  - file present otherwise (empty or any other content) → true
 *
 * Rationale: lets the module UI (running in its own process) toggle hook
 * behaviour via root-owned files even when the RemotePreferences bridge is
 * not yet bound in the host process. String reads are always delegated —
 * file flags only carry boolean semantics.
 */
class FileFlagConfigSource(
    private val flagsDir: File,
    private val delegate: ConfigSource,
) : ConfigSource {
    override fun getString(key: String, defaultValue: String?): String? =
        delegate.getString(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            val flagFile = File(flagsDir, key)
            if (flagFile.exists()) {
                val content = flagFile.readText().trim()
                content != "0" && content != "false"
            } else {
                delegate.getBoolean(key, defaultValue)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read flag '$key': ${t.message}")
            delegate.getBoolean(key, defaultValue)
        }
    }

    private companion object {
        const val TAG = "Janus-FileFlagConfig"
    }
}

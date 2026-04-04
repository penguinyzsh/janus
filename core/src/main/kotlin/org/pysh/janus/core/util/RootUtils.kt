package org.pysh.janus.core.util

import kotlin.concurrent.thread

object RootUtils {

    fun hasRoot(): Boolean = exec("id")

    fun restartBackScreen(): Boolean =
        exec("am force-stop com.xiaomi.subscreencenter")

    fun exec(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            // Must consume both stdout and stderr simultaneously to prevent
            // pipe buffer saturation deadlock (Linux default buffer = 64KB).
            val stdoutThread = thread(isDaemon = true) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderrHolder = arrayOfNulls<String>(1)
            val stderrThread = thread(isDaemon = true) {
                stderrHolder[0] = process.errorStream.bufferedReader().use { it.readText() }
            }
            val exitCode = process.waitFor()
            stdoutThread.join(10_000)
            stderrThread.join(10_000)
            if (exitCode != 0) {
                android.util.Log.e("Janus-Root", "exec failed ($exitCode): $command\n${stderrHolder[0]}")
            }
            exitCode == 0
        } catch (e: Exception) {
            android.util.Log.e("Janus-Root", "exec exception: $command", e)
            false
        }
    }

    fun ensureDir(path: String): Boolean {
        if (!exec("mkdir -p '$path' && chmod 777 '$path'")) return false
        // Inherit parent's full SELinux context (preserves MCS categories like c512,c768).
        // Hardcoding s0 without MCS would strip categories and break access on strict devices.
        val parent = path.substringBeforeLast('/')
        exec("chcon --reference='$parent' '$path' 2>/dev/null || true")
        return true
    }

    fun execWithOutput(command: String): String? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            // Must consume both stdout and stderr simultaneously to prevent
            // pipe buffer saturation deadlock.
            val stdoutHolder = arrayOfNulls<String>(1)
            val stdoutThread = thread(isDaemon = true) {
                stdoutHolder[0] = process.inputStream.bufferedReader().use { it.readText().trim() }
            }
            val stderrThread = thread(isDaemon = true) {
                process.errorStream.bufferedReader().use { it.readText() }
            }
            val exitCode = process.waitFor()
            stdoutThread.join(10_000)
            stderrThread.join(10_000)
            if (exitCode == 0) stdoutHolder[0] else null
        } catch (e: Exception) {
            android.util.Log.w("Janus-Root", "execWithOutput exception: $command", e)
            null
        }
    }
}

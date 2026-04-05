package org.pysh.janus.util

object RootUtils {

    fun hasRoot(): Boolean = exec("id")

    fun restartBackScreen(): Boolean =
        exec("am broadcast -a org.pysh.janus.action.SMOOTH_REFRESH")

    fun exec(command: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                android.util.Log.e("Janus-Root", "exec failed ($exitCode): $command\n$stderr")
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
            // 先读完 stdout 再 waitFor，避免缓冲区满导致死锁
            val output = process.inputStream.bufferedReader().use { it.readText().trim() }
            process.errorStream.bufferedReader().use { it.readText() }
            if (process.waitFor() == 0) output else null
        } catch (_: Exception) {
            null
        }
    }
}

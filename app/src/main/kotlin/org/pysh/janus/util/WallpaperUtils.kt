package org.pysh.janus.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.pysh.janus.core.util.JanusPaths
import org.pysh.janus.core.util.RootUtils
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Utility for building, deploying, and managing rear-screen wallpaper MRC files.
 *
 * ## Architecture (v260408+)
 *
 * Janus wallpapers live exclusively in `janus/wallpapers/wp_<uuid>.mrc`. The
 * active wallpaper is designated by a single-line pointer file
 * `janus/config/active_wallpaper` (containing the UUID). Hook-side
 * `pathFromActiveTheme` reads this pointer and returns the resolved MRC path
 * to subscreencenter.
 *
 * **No system files are modified.** Previous versions overwrote the system's
 * AI wallpaper snapshot (`rearScreenWhite/rearscreen_*.mrc`) and kept `.bak`
 * backup files. That mechanism has been entirely removed — the system's
 * original wallpaper stays pristine. If Janus is deactivated (hook removed),
 * subscreencenter falls back to its own wallpaper without any cleanup needed.
 */
object WallpaperUtils {
    private const val TEMPLATE_DIR = "wallpaper_template"
    private const val AI_MP4_ENTRY = "assets/ai/ai.mp4"
    private const val MANIFEST_ENTRY = "manifest.xml"
    private const val OWNER = "system_theme:ext_data_rw"

    // ── Pointer management ──────────────────────────────────────

    /**
     * Set the active wallpaper pointer. Hook reads this to resolve which
     * `wp_<uuid>.mrc` to redirect subscreencenter to.
     */
    fun setActiveWallpaper(uuid: String) {
        JanusPaths.ensureAllDirs()
        RootUtils.exec(
            "printf '%s' '$uuid' > '${JanusPaths.ACTIVE_WALLPAPER}' && " +
                "chmod 644 '${JanusPaths.ACTIVE_WALLPAPER}' && " +
                "chcon u:object_r:theme_data_file:s0 '${JanusPaths.ACTIVE_WALLPAPER}'",
        )
    }

    /** Clear the active wallpaper pointer. Hook falls through to next layer. */
    fun clearActiveWallpaper() {
        RootUtils.exec("rm -f '${JanusPaths.ACTIVE_WALLPAPER}'")
    }

    /** Read the active wallpaper UUID from the pointer file, or null. */
    fun readActiveWallpaper(): String? {
        val raw = RootUtils.execWithOutput("cat '${JanusPaths.ACTIVE_WALLPAPER}' 2>/dev/null")
        return raw?.trim()?.takeIf { it.isNotEmpty() }
    }

    // ── Apply wallpaper ─────────────────────────────────────────

    /**
     * Build an MRC from the bundled template + user video, deploy to
     * `janus/wallpapers/wp_<wpId>.mrc`, and set the active pointer.
     * Triggers a subscreencenter restart to make the change visible.
     *
     * Returns true on success.
     */
    fun applyWallpaper(
        context: Context,
        videoUri: Uri,
        enableLoop: Boolean,
        wpId: String,
    ): Boolean {
        val cacheDir = ensureCacheDir(context)
        try {
            val userVideo = File(cacheDir, "user_video.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                userVideo.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            val mrcFile = buildMrcFromTemplate(context, userVideo, enableLoop) ?: return false

            JanusPaths.ensureAllDirs()
            val destPath = "${JanusPaths.WALLPAPERS_DIR}/wp_$wpId.mrc"
            val ok = RootUtils.exec(
                "cp '${mrcFile.absolutePath}' '$destPath' && " +
                    "chmod 644 '$destPath' && " +
                    "chcon u:object_r:theme_data_file:s0 '$destPath'",
            )
            if (!ok) return false

            setActiveWallpaper(wpId)
            RootUtils.restartBackScreen()
            return true
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    // ── Loop toggle ─────────────────────────────────────────────

    /**
     * Toggle the loop flag on the currently active wallpaper's MRC.
     * Reads the MRC from `janus/wallpapers/`, rebuilds with the new flag,
     * writes it back, and restarts subscreencenter.
     */
    fun setLoop(
        context: Context,
        enabled: Boolean,
    ): Boolean {
        val uuid = readActiveWallpaper() ?: return false
        val mrcPath = "${JanusPaths.WALLPAPERS_DIR}/wp_$uuid.mrc"
        val cacheDir = ensureCacheDir(context)

        try {
            val workZip = copyToCache(context, mrcPath, "work.zip") ?: return false
            val resultZip = File(cacheDir, "result.zip")

            rebuildZipLoopOnly(workZip, resultZip, enabled)

            val ok = RootUtils.exec(
                "cp '${resultZip.absolutePath}' '$mrcPath' && " +
                    "chmod 644 '$mrcPath' && " +
                    "chcon u:object_r:theme_data_file:s0 '$mrcPath'",
            )
            if (ok) RootUtils.restartBackScreen()
            return ok
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    fun isLoopEnabled(context: Context): Boolean? {
        val uuid = readActiveWallpaper() ?: return null
        val mrcPath = "${JanusPaths.WALLPAPERS_DIR}/wp_$uuid.mrc"
        val workZip = copyToCache(context, mrcPath, "check.zip") ?: return null
        return try {
            readManifestLoop(workZip)
        } finally {
            workZip.delete()
        }
    }

    // ── MRC building ────────────────────────────────────────────

    private fun buildMrcFromTemplate(
        context: Context,
        videoFile: File,
        enableLoop: Boolean,
    ): File? {
        val cacheDir = ensureCacheDir(context)
        val resultMrc = File(cacheDir, "new_wallpaper.mrc")
        val assets = context.assets
        try {
            ZipOutputStream(FileOutputStream(resultMrc)).use { zos ->
                fun addAsset(assetPath: String, zipPath: String) {
                    val children = assets.list(assetPath) ?: return
                    if (children.isEmpty()) {
                        val content =
                            try { assets.open(assetPath).use { it.readBytes() } }
                            catch (_: Exception) { return }
                        if (zipPath == MANIFEST_ENTRY) {
                            val modified = modifyLoop(String(content), enableLoop)
                            zos.putNextEntry(ZipEntry(zipPath))
                            zos.write(modified.toByteArray())
                        } else {
                            zos.putNextEntry(ZipEntry(zipPath))
                            zos.write(content)
                        }
                        zos.closeEntry()
                    } else {
                        if (zipPath.isNotEmpty()) {
                            zos.putNextEntry(ZipEntry("$zipPath/"))
                            zos.closeEntry()
                        }
                        for (child in children) {
                            addAsset(
                                "$assetPath/$child",
                                if (zipPath.isEmpty()) child else "$zipPath/$child",
                            )
                        }
                    }
                }
                addAsset(TEMPLATE_DIR, "")
                zos.putNextEntry(storedEntry(AI_MP4_ENTRY, videoFile))
                videoFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
            return resultMrc
        } catch (_: Exception) {
            resultMrc.delete()
            return null
        }
    }

    // ── Internal helpers ────────────────────────────────────────

    private fun ensureCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "wallpaper_work")
        dir.mkdirs()
        return dir
    }

    private fun copyToCache(context: Context, remotePath: String, name: String): File? {
        val cacheFile = File(ensureCacheDir(context), name)
        if (!RootUtils.exec("cp '$remotePath' '${cacheFile.absolutePath}'")) return null
        if (!RootUtils.exec("chmod 644 '${cacheFile.absolutePath}'")) return null
        return if (cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
    }

    private fun readManifestLoop(zipFile: File): Boolean {
        val zip = ZipFile(zipFile)
        val entry = zip.getEntry(MANIFEST_ENTRY) ?: run { zip.close(); return false }
        val content = zip.getInputStream(entry).bufferedReader().readText()
        zip.close()
        val regex = Regex("""loop="(\d+)"""")
        return regex.findAll(content).lastOrNull()?.groupValues?.get(1) == "1"
    }

    private fun modifyLoop(content: String, enabled: Boolean): String {
        val target = if (enabled) """loop="0"""" else """loop="1""""
        val replacement = if (enabled) """loop="1"""" else """loop="0""""
        val lastIndex = content.lastIndexOf(target)
        if (lastIndex < 0) return content
        return content.substring(0, lastIndex) + replacement + content.substring(lastIndex + target.length)
    }

    private fun rebuildZipLoopOnly(source: File, dest: File, enableLoop: Boolean) {
        val zip = ZipFile(source)
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for (entry in zip.entries()) {
                if (entry.name == MANIFEST_ENTRY) {
                    val content = zip.getInputStream(entry).bufferedReader().readText()
                    val modified = modifyLoop(content, enableLoop)
                    zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                    zos.write(modified.toByteArray())
                    zos.closeEntry()
                } else {
                    zos.putNextEntry(ZipEntry(entry.name))
                    if (!entry.isDirectory) {
                        zip.getInputStream(entry).use { it.copyTo(zos) }
                    }
                    zos.closeEntry()
                }
            }
        }
        zip.close()
    }

    /** 创建 STORED（不压缩）的 ZipEntry，需要预计算 size 和 CRC32 */
    private fun storedEntry(name: String, file: File): ZipEntry {
        val crc = CRC32()
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                crc.update(buf, 0, len)
            }
        }
        return ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = file.length()
            compressedSize = file.length()
            setCrc(crc.value)
        }
    }
}

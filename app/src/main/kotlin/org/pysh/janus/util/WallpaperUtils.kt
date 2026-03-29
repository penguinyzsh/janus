package org.pysh.janus.util

import android.content.Context
import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

object WallpaperUtils {

    private val RUNTIME_JSON = JanusPaths.RUNTIME_JSON
    private val RUNTIME_DIR = JanusPaths.RUNTIME_DIR
    private val REAR_SCREEN_WHITE = JanusPaths.REAR_SCREEN_WHITE
    private const val TEMPLATE_DIR = "wallpaper_template"
    // Janus 专用路径，不覆盖系统文件
    private val JANUS_MRC = JanusPaths.CUSTOM_MRC
    private val JANUS_WALLPAPER_DIR = JanusPaths.WALLPAPER_DIR
    private const val AI_MP4_ENTRY = "assets/ai/ai.mp4"
    private const val MANIFEST_ENTRY = "manifest.xml"
    private const val LEGACY_BACKUP_SUFFIX = ".janus_bak"
    private const val OWNER = "system_theme:ext_data_rw"

    data class WallpaperPaths(
        val localPath: String,
        val snapshotPath: String,
    )

    fun detectWallpaper(): WallpaperPaths? {
        val json = RootUtils.execWithOutput("cat '$RUNTIME_JSON'") ?: return null
        return try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("resSubType", "") != "ai") continue
                val local = entry.getString("resLocalPath")
                val snapshot = entry.getString("resSnapshotPath")
                if (!RootUtils.exec("test -f '$snapshot'")) continue
                return WallpaperPaths(local, snapshot)
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun isLoopEnabled(context: Context): Boolean? {
        val paths = detectWallpaper() ?: return null
        val workZip = copyToCache(context, paths.snapshotPath, "check.zip") ?: return null
        return try {
            readManifestLoop(workZip)
        } finally {
            workZip.delete()
        }
    }

    fun replaceVideo(context: Context, videoUri: Uri, enableLoop: Boolean): Boolean {
        val paths = detectWallpaper() ?: return false
        val cacheDir = ensureCacheDir(context)

        try {
            val userVideo = File(cacheDir, "user_video.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                userVideo.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            backupIfNeeded(paths.snapshotPath)

            val workZip = copyToCache(context, paths.snapshotPath, "work.zip") ?: return false
            val resultZip = File(cacheDir, "result.zip")

            rebuildZip(workZip, resultZip, userVideo, enableLoop)

            if (!writeBack(resultZip, paths)) return false
            saveToJanusPath(resultZip.absolutePath)
            return true
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    fun setLoop(context: Context, enabled: Boolean): Boolean {
        val paths = detectWallpaper() ?: return false
        val cacheDir = ensureCacheDir(context)

        try {
            val workZip = copyToCache(context, paths.snapshotPath, "work.zip") ?: return false
            val resultZip = File(cacheDir, "result.zip")

            rebuildZipLoopOnly(workZip, resultZip, enabled)

            return writeBack(resultZip, paths)
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    fun restoreBackup(): Boolean {
        val paths = detectWallpaper() ?: return false
        val backup = findBackup(paths.snapshotPath) ?: return false
        val ok = RootUtils.exec("cp '$backup' '${paths.snapshotPath}'") &&
            RootUtils.exec("cp '$backup' '${paths.localPath}'")
        if (ok) {
            fixOwnership(paths.snapshotPath)
            fixOwnership(paths.localPath)
            // 删除 Janus 自定义壁纸，让 Hook 停止重定向
            disableCustomWallpaper()
            RootUtils.restartBackScreen()
        }
        return ok
    }

    /** 删除 Janus 自定义壁纸文件，让 Hook 停止重定向，恢复原始壁纸 */
    fun disableCustomWallpaper() {
        RootUtils.exec("rm -f '$JANUS_MRC'")
    }

    fun hasBackup(): Boolean {
        val paths = detectWallpaper() ?: return false
        return findBackup(paths.snapshotPath) != null
    }

    /** Find backup file, checking new janus/ dir first, then legacy location. */
    private fun findBackup(snapshotPath: String): String? {
        val snapshotName = snapshotPath.substringAfterLast('/')
        val newBackup = "$JANUS_WALLPAPER_DIR/$snapshotName.bak"
        if (RootUtils.exec("test -f '$newBackup'")) return newBackup
        val legacyBackup = "$snapshotPath$LEGACY_BACKUP_SUFFIX"
        if (RootUtils.exec("test -f '$legacyBackup'")) return legacyBackup
        return null
    }

    /**
     * 从模板创建全新的 AI 动态壁纸并注入系统。
     * 当设备没有 AI 壁纸时使用。
     */
    fun createAiWallpaper(context: Context, videoUri: Uri, enableLoop: Boolean): Boolean {
        val cacheDir = ensureCacheDir(context)
        try {
            val userVideo = File(cacheDir, "user_video.mp4")
            context.contentResolver.openInputStream(videoUri)?.use { input ->
                userVideo.outputStream().use { output -> input.copyTo(output) }
            } ?: return false

            val mrcFile = buildMrcFromTemplate(context, userVideo, enableLoop) ?: return false

            val json = RootUtils.execWithOutput("cat '$RUNTIME_JSON'") ?: return false
            val arr = try { JSONArray(json) } catch (_: Exception) { return false }

            // 找 signature 条目作为克隆源
            var signatureEntry: JSONObject? = null
            for (i in 0 until arr.length()) {
                val entry = arr.getJSONObject(i)
                if (entry.optString("resSubType") != "ai") {
                    signatureEntry = entry
                    break
                }
            }

            val applyId = System.currentTimeMillis().toString()
            // Always use janus- prefix so cleanup can identify Janus-created entries
            val resId = "janus-${java.util.UUID.randomUUID()}"
            val snapshotPath = "$REAR_SCREEN_WHITE/rearscreen_${resId}_${applyId}.mrc"

            if (!RootUtils.exec("cp '${mrcFile.absolutePath}' '$snapshotPath'")) return false
            fixOwnership(snapshotPath)

            // 创建辅助目录
            val entryDir = "$RUNTIME_DIR/${resId}_$applyId"
            RootUtils.exec("mkdir -p '$entryDir/editConfig'")
            RootUtils.exec("mkdir -p '$entryDir/rearScreenScreenshot'")
            fixOwnership(entryDir)
            val metaSnapshotPath = "$entryDir/$resId.mrm"
            val sigMetaSnapshot = signatureEntry?.optString("metaSnapshotPath", "")
            if (!sigMetaSnapshot.isNullOrEmpty()) {
                RootUtils.exec("cp '$sigMetaSnapshot' '$metaSnapshotPath'")
                fixOwnership(metaSnapshotPath)
            }

            // 构建 AI 条目
            val aiEntry = buildAiEntry(signatureEntry, resId, applyId, snapshotPath, entryDir, metaSnapshotPath)
            var maxPosition = 0
            for (i in 0 until arr.length()) {
                val pos = arr.getJSONObject(i).optInt("position", 0)
                if (pos > maxPosition) maxPosition = pos
            }
            aiEntry.put("position", maxPosition + 1)
            arr.put(aiEntry)
            if (!writeRuntimeJson(arr)) return false

            // 保存到 Janus 专用路径（Hook 重定向目标）
            saveToJanusPath(mrcFile.absolutePath)
            RootUtils.restartBackScreen()
            return true
        } finally {
            cacheDir.deleteRecursively()
        }
    }

    private fun buildMrcFromTemplate(context: Context, videoFile: File, enableLoop: Boolean): File? {
        val cacheDir = ensureCacheDir(context)
        val resultMrc = File(cacheDir, "new_wallpaper.mrc")
        val assets = context.assets
        try {
            ZipOutputStream(FileOutputStream(resultMrc)).use { zos ->
                fun addAsset(assetPath: String, zipPath: String) {
                    val children = assets.list(assetPath) ?: return
                    if (children.isEmpty()) {
                        // 空目录（如 etc/）跳过，不尝试 open
                        val content = try {
                            assets.open(assetPath).use { it.readBytes() }
                        } catch (_: Exception) { return }
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
                            addAsset("$assetPath/$child",
                                if (zipPath.isEmpty()) child else "$zipPath/$child")
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

    private fun buildAiEntry(
        signatureEntry: JSONObject?, resId: String, applyId: String,
        snapshotPath: String, entryDir: String, metaSnapshotPath: String,
    ): JSONObject {
        val now = System.currentTimeMillis()
        return if (signatureEntry != null) {
            JSONObject(signatureEntry.toString()).apply {
                put("resSubType", "ai")
                put("applyId", applyId)
                put("applyTime", now)
                put("updateTime", now)
                put("resSnapshotPath", snapshotPath)
                put("resLocalPath", snapshotPath)
                put("mamlEditConfigPath", "$entryDir/editConfig")
                put("metaSnapshotPath", metaSnapshotPath)
                put("snapshotPreviewPath", "$entryDir/rearScreenScreenshot")
                put("editable", true)
                put("isDownload", true)
            }
        } else {
            JSONObject().apply {
                put("resType", "signature")
                put("resSubType", "ai")
                put("resId", resId)
                put("applyId", applyId)
                put("applyTime", now)
                put("updateTime", now)
                put("resSnapshotPath", snapshotPath)
                put("resLocalPath", snapshotPath)
                put("resName", """{"zh_CN":"Janus 壁纸","en_US":"Janus Wallpaper","fallback":"Janus Wallpaper"}""")
                put("resDescription", """{"zh_CN":"","en_US":"","fallback":""}""")
                put("resDesigner", """{"zh_CN":"Janus","en_US":"Janus","fallback":"Janus"}""")
                put("resTypeName", """{"zh_CN":"壁纸与签名","en_US":"Wallpapers and signatures","fallback":"壁纸与签名"}""")
                put("resPreviewPath", "")
                put("metaPath", "")
                put("metaSnapshotPath", metaSnapshotPath)
                put("rightPath", "")
                put("mamlEditConfigPath", "$entryDir/editConfig")
                put("snapshotPreviewPath", "$entryDir/rearScreenScreenshot")
                put("editable", true)
                put("isDownload", true)
                put("isNFC", false)
                put("isThirdParties", false)
                put("supportAon", false)
                put("priority", Int.MAX_VALUE)
                put("onlineInfo", JSONObject().apply {
                    put("isOnlineResource", false)
                    put("onlineId", "")
                })
            }
        }
    }

    private fun writeRuntimeJson(arr: JSONArray): Boolean {
        val tmpFile = "/data/local/tmp/janus_runtime.json"
        val b64 = Base64.encodeToString(arr.toString().toByteArray(), Base64.NO_WRAP)
        if (!RootUtils.exec("echo '$b64' | base64 -d > '$tmpFile'")) return false
        if (!RootUtils.exec("cp '$tmpFile' '$RUNTIME_JSON'")) return false
        fixOwnership(RUNTIME_JSON)
        RootUtils.exec("chmod 777 '$RUNTIME_JSON'")
        RootUtils.exec("rm -f '$tmpFile'")
        return true
    }

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

    private fun backupIfNeeded(snapshotPath: String) {
        // Skip if backup already exists (new or legacy location)
        if (findBackup(snapshotPath) != null) return
        JanusPaths.ensureWallpaperDir()
        val snapshotName = snapshotPath.substringAfterLast('/')
        val backup = "$JANUS_WALLPAPER_DIR/$snapshotName.bak"
        RootUtils.exec("cp '$snapshotPath' '$backup'")
        fixOwnership(backup)
    }

    private fun writeBack(resultZip: File, paths: WallpaperPaths): Boolean {
        val src = resultZip.absolutePath
        if (!RootUtils.exec("cp '$src' '${paths.snapshotPath}'")) return false
        fixOwnership(paths.snapshotPath)
        RootUtils.exec("cp '$src' '${paths.localPath}'")
        fixOwnership(paths.localPath)
        // 仅在 janus/custom.mrc 已存在时同步更新（如循环设置变更）
        // 不主动创建——创建由 replaceVideo/createAiWallpaper 显式调用 saveToJanusPath
        if (RootUtils.exec("test -f '$JANUS_MRC'")) {
            RootUtils.exec("cp '$src' '$JANUS_MRC'")
            fixOwnership(JANUS_MRC)
        }
        RootUtils.restartBackScreen()
        return true
    }

    /**
     * 将 .mrc 保存到 Janus 专用路径，并创建 editConfig（bgmode=2）。
     * Hook 会将 subscreencenter 重定向到此路径。不修改任何系统壁纸文件。
     */
    private fun saveToJanusPath(src: String) {
        JanusPaths.ensureWallpaperDir()
        RootUtils.exec("cp '$src' '$JANUS_MRC'")
        fixOwnership(JANUS_MRC)
    }

    private fun fixOwnership(path: String) {
        RootUtils.exec("chown $OWNER '$path'")
        RootUtils.exec("chmod 775 '$path'")
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

    private fun readManifestLoop(zipFile: File): Boolean {
        val zip = ZipFile(zipFile)
        val entry = zip.getEntry(MANIFEST_ENTRY) ?: run { zip.close(); return false }
        val content = zip.getInputStream(entry).bufferedReader().readText()
        zip.close()
        val regex = Regex("""loop="(\d+)"""")
        val lastMatch = regex.findAll(content).lastOrNull()
        return lastMatch?.groupValues?.get(1) == "1"
    }

    private fun modifyLoop(content: String, enabled: Boolean): String {
        val target = if (enabled) """loop="0"""" else """loop="1""""
        val replacement = if (enabled) """loop="1"""" else """loop="0""""
        val lastIndex = content.lastIndexOf(target)
        if (lastIndex < 0) return content
        return content.substring(0, lastIndex) +
            replacement +
            content.substring(lastIndex + target.length)
    }

    private fun rebuildZip(
        source: File,
        dest: File,
        newVideo: File,
        enableLoop: Boolean,
    ) {
        val zip = ZipFile(source)
        ZipOutputStream(FileOutputStream(dest)).use { zos ->
            for (entry in zip.entries()) {
                when (entry.name) {
                    AI_MP4_ENTRY -> {
                        zos.putNextEntry(storedEntry(AI_MP4_ENTRY, newVideo))
                        newVideo.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                    MANIFEST_ENTRY -> {
                        val content = zip.getInputStream(entry).bufferedReader().readText()
                        val modified = modifyLoop(content, enableLoop)
                        zos.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                        zos.write(modified.toByteArray())
                        zos.closeEntry()
                    }
                    else -> {
                        zos.putNextEntry(ZipEntry(entry.name))
                        if (!entry.isDirectory) {
                            zip.getInputStream(entry).use { it.copyTo(zos) }
                        }
                        zos.closeEntry()
                    }
                }
            }
        }
        zip.close()
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
}

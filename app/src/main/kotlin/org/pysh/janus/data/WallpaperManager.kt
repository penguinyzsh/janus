package org.pysh.janus.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages multiple wallpaper entries stored locally.
 *
 * Wallpaper metadata is persisted as a JSON array in `wallpapers.json` inside the
 * app's private files directory.  The video files and thumbnail bitmaps live next to
 * the metadata in per-wallpaper subdirectories:
 *
 *     filesDir/wallpapers/
 *         wallpapers.json
 *         <id>/
 *             video.mp4
 *             thumb.jpg
 */
class WallpaperManager(
    private val context: Context,
) {
    data class WallpaperEntry(
        val id: String,
        val name: String,
        val videoPath: String,
        val thumbnailPath: String,
        val order: Int,
        val isApplied: Boolean,
        val addedAt: Long,
    )

    private val wallpapersDir: File
        get() = File(context.filesDir, "wallpapers").also { it.mkdirs() }

    private val metaFile: File
        get() = File(wallpapersDir, "wallpapers.json")

    /**
     * Resolve a relative path from wallpapers.json to an absolute path.
     * Handles both new relative paths (`<uuid>/video.mp4`) and legacy
     * absolute paths (`/data/user/0/.../video.mp4`) for backward compat.
     */
    fun resolvePath(relativePath: String): String =
        if (relativePath.startsWith("/")) {
            relativePath // legacy absolute — pass through
        } else {
            File(wallpapersDir, relativePath).absolutePath
        }

    // ── Read ─────────────────────────────────────────────────────

    fun getWallpapers(): List<WallpaperEntry> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(metaFile.readText())
            (0 until arr.length())
                .map { parseEntry(arr.getJSONObject(it)) }
                .map {
                    // Resolve relative paths to absolute at read time so callers
                    // don't need to know about the relative-vs-absolute distinction.
                    it.copy(
                        videoPath = resolvePath(it.videoPath),
                        thumbnailPath = if (it.thumbnailPath.isNotEmpty()) resolvePath(it.thumbnailPath) else "",
                    )
                }.sortedBy { it.order }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getWallpaperCount(): Int = getWallpapers().size

    // ── Add ──────────────────────────────────────────────────────

    fun addWallpaper(uri: Uri): WallpaperEntry? {
        val id = UUID.randomUUID().toString()
        val entryDir = File(wallpapersDir, id).also { it.mkdirs() }
        val videoFile = File(entryDir, "video.mp4")
        val thumbFile = File(entryDir, "thumb.jpg")

        // Copy video from content URI to local storage
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                videoFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
        } catch (_: Exception) {
            entryDir.deleteRecursively()
            return null
        }

        // Generate thumbnail
        generateThumbnail(videoFile.absolutePath)?.let { bitmap ->
            FileOutputStream(thumbFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            bitmap.recycle()
        }

        val existing = getWallpapers()
        val nextOrder = (existing.maxOfOrNull { it.order } ?: -1) + 1

        var originalName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx != -1) {
                    originalName = cursor.getString(idx)?.substringBeforeLast(".")
                }
            }
        }

        val defaultName =
            originalName.takeIf { !it.isNullOrBlank() } ?: context.getString(
                org.pysh.janus.R.string.wp_name_default,
                existing.size + 1,
            )

        val entry =
            WallpaperEntry(
                id = id,
                name = defaultName,
                videoPath = "$id/video.mp4",
                thumbnailPath = if (thumbFile.exists()) "$id/thumb.jpg" else "",
                order = nextOrder,
                isApplied = false,
                addedAt = System.currentTimeMillis(),
            )

        saveEntries(existing + entry)
        return entry
    }

    // ── Remove ───────────────────────────────────────────────────

    fun removeWallpaper(id: String) {
        val entries = getWallpapers().toMutableList()
        entries.removeAll { it.id == id }
        // Clean up files
        File(wallpapersDir, id).deleteRecursively()
        saveEntries(entries)
    }

    // ── Apply ────────────────────────────────────────────────────

    /**
     * Mark the given wallpaper as applied (and all others as not applied).
     * The caller is responsible for actually deploying the video via [WallpaperUtils].
     */
    fun markApplied(id: String) {
        val entries =
            getWallpapers().map {
                it.copy(isApplied = it.id == id)
            }
        saveEntries(entries)
    }

    /**
     * Mark all wallpapers as not applied in a single write. Used by the
     * "restore default" flow so we don't fall into the N-writes-per-reset
     * trap that the old `forEach { markApplied("") }` loop had.
     */
    fun clearApplied() {
        val entries = getWallpapers().map { it.copy(isApplied = false) }
        saveEntries(entries)
    }

    /** Returns the absolute video file path for the given wallpaper entry. */
    fun getVideoPath(id: String): String? =
        getWallpapers().find { it.id == id }?.videoPath?.let { resolvePath(it) }

    // ── Rename ───────────────────────────────────────────────────

    fun renameWallpaper(
        id: String,
        newName: String,
    ) {
        val entries =
            getWallpapers().map {
                if (it.id == id) it.copy(name = newName) else it
            }
        saveEntries(entries)
    }

    // ── Reorder ──────────────────────────────────────────────────

    fun reorderWallpapers(orderedIds: List<String>) {
        val entries = getWallpapers().associateBy { it.id }
        val reordered =
            orderedIds.mapIndexedNotNull { index, id ->
                entries[id]?.copy(order = index)
            }
        // Keep any entries not in orderedIds at the end
        val remaining =
            entries.values
                .filter { it.id !in orderedIds }
                .mapIndexed { index, entry -> entry.copy(order = orderedIds.size + index) }
        saveEntries(reordered + remaining)
    }

    // ── Replace video ────────────────────────────────────────────

    fun replaceVideo(
        id: String,
        uri: Uri,
    ): Boolean {
        val entryDir = File(wallpapersDir, id)
        if (!entryDir.exists()) return false

        val videoFile = File(entryDir, "video.mp4")
        val thumbFile = File(entryDir, "thumb.jpg")

        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                videoFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return false
        } catch (_: Exception) {
            return false
        }

        // Re-generate thumbnail
        thumbFile.delete()
        generateThumbnail(videoFile.absolutePath)?.let { bitmap ->
            FileOutputStream(thumbFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }
            bitmap.recycle()
        }

        return true
    }

    // ── Thumbnail generation ─────────────────────────────────────

    private fun generateThumbnail(videoPath: String): Bitmap? =
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(videoPath)
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame
        } catch (_: Exception) {
            null
        }

    // ── Persistence ──────────────────────────────────────────────

    private fun saveEntries(entries: List<WallpaperEntry>) {
        val arr = JSONArray()
        for (entry in entries) {
            arr.put(entryToJson(entry))
        }
        metaFile.writeText(arr.toString(2))
    }

    private fun entryToJson(entry: WallpaperEntry): JSONObject =
        JSONObject().apply {
            put("id", entry.id)
            put("name", entry.name)
            put("videoPath", entry.videoPath)
            put("thumbnailPath", entry.thumbnailPath)
            put("order", entry.order)
            put("isApplied", entry.isApplied)
            put("addedAt", entry.addedAt)
        }

    private fun parseEntry(json: JSONObject): WallpaperEntry =
        WallpaperEntry(
            id = json.getString("id"),
            name = json.optString("name", ""),
            videoPath = json.optString("videoPath", ""),
            thumbnailPath = json.optString("thumbnailPath", ""),
            order = json.optInt("order", 0),
            isApplied = json.optBoolean("isApplied", false),
            addedAt = json.optLong("addedAt", 0L),
        )
}

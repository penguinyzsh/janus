package org.pysh.janus.data

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.pysh.janus.core.util.JanusPaths
import org.pysh.janus.core.util.RootUtils
import org.pysh.janus.hookapi.theme.InvalidThemeArchiveException
import org.pysh.janus.hookapi.theme.ThemeArchive
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Manages imported rear-screen theme packages (`.mrc` / `.mtz`).
 *
 * Storage is split across two locations:
 *
 *  - **App-private** (`context.filesDir/themes/`) — metadata index + a
 *    per-theme copy of the normalized MRC + extracted thumbnail. This is
 *    what the UI reads and what survives APK-level backups.
 *
 *  - **System root** (`JanusPaths.THEMES_DIR` + `JanusPaths.ACTIVE_THEME`) —
 *    root-deployed theme files and the single-line pointer file that the
 *    `path_from_active_theme` hook action consults at runtime. Written via
 *    `su` so subscreencenter can read under `theme_data_file` context.
 *
 * The class is intentionally synchronous and blocking. Callers are expected
 * to wrap individual operations in a coroutine or background thread when
 * invoking from Compose.
 */
class ThemeManager(
    private val context: Context,
) {
    data class ThemeEntry(
        val id: String,
        val displayName: String,
        val author: String?,
        val description: String?,
        val mamlVersion: String?,
        val fileSize: Long,
        val sourceFileName: String,
        val isExtractedFromMtz: Boolean,
        val hasThumbnail: Boolean,
        val addedAt: Long,
        val order: Int,
    )

    sealed class ImportResult {
        data class Success(
            val entry: ThemeEntry,
        ) : ImportResult()

        data class Failure(
            val reason: String,
        ) : ImportResult()
    }

    // ── Paths ─────────────────────────────────────────────────────

    private val themesDir: File
        get() = File(context.filesDir, "themes").also { it.mkdirs() }

    private val metaFile: File
        get() = File(themesDir, "themes.json")

    private fun entryDir(id: String): File = File(themesDir, id)

    private fun entryMrc(id: String): File = File(entryDir(id), "theme.mrc")

    fun thumbnailFile(id: String): File = File(entryDir(id), "thumb.png")

    // ── Read ──────────────────────────────────────────────────────

    fun getThemes(): List<ThemeEntry> {
        if (!metaFile.exists()) return emptyList()
        return try {
            val arr = JSONArray(metaFile.readText())
            (0 until arr.length())
                .map { parseEntry(arr.getJSONObject(it)) }
                .sortedBy { it.order }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse themes.json: ${e.message}")
            emptyList()
        }
    }

    fun getTheme(id: String): ThemeEntry? = getThemes().firstOrNull { it.id == id }

    /**
     * Returns the currently active theme id, or null if no theme is active.
     *
     * Reads the root-owned pointer file `$ACTIVE_THEME` on the first call,
     * then serves subsequent calls from an in-memory cache. [applyTheme] and
     * [deactivate] invalidate the cache immediately, so the value is always
     * in sync with what this process wrote. Cross-process writes (e.g. a
     * shell poking the file directly) are not observed, but there is no such
     * writer in the current codebase.
     *
     * Rationale for caching: the UI hits this on every `ON_RESUME` of
     * `ThemesPage` and on dialog open, which used to fork a `su` subprocess
     * per call. Root is a hard invariant on Main screen, so the historical
     * "local shadow fallback" for no-root users has been removed.
     */
    fun getActiveThemeId(): String? {
        cachedActiveId?.let { return it.value }
        val raw = RootUtils.execWithOutput("cat '${JanusPaths.ACTIVE_THEME}' 2>/dev/null")
        val id = raw?.trim()?.takeIf { it.isNotEmpty() }
        cachedActiveId = ActiveIdCell(id)
        return id
    }

    /** Wrapper so a cached null (no active theme) is distinguishable from "not loaded yet". */
    private data class ActiveIdCell(val value: String?)

    @Volatile
    private var cachedActiveId: ActiveIdCell? = null

    // ── Import ────────────────────────────────────────────────────

    /**
     * Import a theme from a SAF [uri]. Copies the archive into cache, parses
     * it via [ThemeArchive], normalizes MTZ packages by re-packing their
     * `rearScreen/` sublayer into a plain MRC, extracts the thumbnail, and
     * appends the result to themes.json.
     */
    fun importTheme(
        uri: Uri,
        fallbackFileName: String,
    ): ImportResult {
        val id = UUID.randomUUID().toString()
        val dir = entryDir(id).also { it.mkdirs() }
        val tmpInput = File(context.cacheDir, "theme-import-$id.tmp")

        try {
            // 1. Copy the SAF URI contents into a regular file we can ZipFile.
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmpInput.outputStream().use { output -> input.copyTo(output) }
            } ?: return fail(dir, "Cannot open import source")

            val resolvedName = fallbackFileName.substringBeforeLast('.').ifBlank { "Theme" }

            // 2. Parse & validate.
            val manifest =
                try {
                    ThemeArchive.parse(tmpInput, resolvedName)
                } catch (e: InvalidThemeArchiveException) {
                    Log.w(TAG, "Theme rejected: ${e.message}", e)
                    return fail(dir, e.message ?: "Invalid theme archive")
                }

            // 3. Normalize — if MTZ, re-pack rearScreen/ as root-level MRC.
            val finalMrc = entryMrc(id)
            if (manifest.isExtractedFromMtz) {
                repackMtzSubPackage(tmpInput, manifest.entryRoot, finalMrc)
            } else {
                tmpInput.copyTo(finalMrc, overwrite = true)
            }

            // 4. Extract thumbnail if present.
            val hasThumb =
                manifest.thumbnailEntryPath?.let { path ->
                    val bytes = ThemeArchive.readEntryBytes(tmpInput, path)
                    if (bytes != null && bytes.isNotEmpty()) {
                        thumbnailFile(id).writeBytes(bytes)
                        true
                    } else {
                        false
                    }
                } ?: false

            // 5. Build and persist the entry.
            val existing = getThemes()
            val entry =
                ThemeEntry(
                    id = id,
                    displayName = manifest.displayName,
                    author = manifest.author,
                    description = manifest.description,
                    mamlVersion = manifest.mamlVersion,
                    fileSize = finalMrc.length(),
                    sourceFileName = fallbackFileName,
                    isExtractedFromMtz = manifest.isExtractedFromMtz,
                    hasThumbnail = hasThumb,
                    addedAt = System.currentTimeMillis(),
                    order = (existing.maxOfOrNull { it.order } ?: -1) + 1,
                )
            saveAll(existing + entry)

            return ImportResult.Success(entry)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            return fail(dir, "Unexpected error: ${e.message}")
        } finally {
            tmpInput.delete()
        }
    }

    private fun fail(
        dir: File,
        reason: String,
    ): ImportResult.Failure {
        dir.deleteRecursively()
        return ImportResult.Failure(reason)
    }

    // ── Rename ────────────────────────────────────────────────────

    fun renameTheme(id: String, newName: String) {
        val entries = getThemes()
        saveAll(entries.map { if (it.id == id) it.copy(displayName = newName) else it })
    }

    // ── Delete ────────────────────────────────────────────────────

    fun deleteTheme(id: String): Boolean {
        val entries = getThemes()
        if (entries.none { it.id == id }) return false

        // If this was the active theme, deactivate first.
        if (getActiveThemeId() == id) deactivate()

        // Remove root-deployed copy.
        RootUtils.exec("rm -rf '${JanusPaths.THEMES_DIR}/$id'")

        // Remove app-private copy.
        entryDir(id).deleteRecursively()

        saveAll(entries.filter { it.id != id })
        return true
    }

    // ── Reorder ───────────────────────────────────────────────────

    fun reorderThemes(orderedIds: List<String>) {
        val byId = getThemes().associateBy { it.id }
        val reordered =
            orderedIds.mapIndexedNotNull { i, id ->
                byId[id]?.copy(order = i)
            }
        // Any theme not in the ordered list keeps its relative tail position.
        val extras =
            byId.values
                .filterNot { it.id in orderedIds }
                .mapIndexed { i, entry -> entry.copy(order = orderedIds.size + i) }
        saveAll(reordered + extras)
    }

    // ── Apply / Deactivate ────────────────────────────────────────

    /**
     * Deploy the theme's MRC file to the system root directory and write the
     * active_theme pointer. Returns true on success. Caller decides whether
     * to subsequently call [RootUtils.restartBackScreen].
     */
    fun applyTheme(id: String): Boolean {
        val entry = getTheme(id) ?: return false
        val localMrc = entryMrc(id)
        if (!localMrc.exists()) return false

        JanusPaths.ensureAllDirs()

        val targetDir = "${JanusPaths.THEMES_DIR}/$id"
        val targetFile = "$targetDir/${JanusPaths.THEME_FILE_NAME}"

        if (!RootUtils.ensureDir(targetDir)) return false

        val deployOk =
            RootUtils.exec(
                "cp '${localMrc.absolutePath}' '$targetFile' && " +
                    "chmod 644 '$targetFile' && " +
                    "chcon u:object_r:theme_data_file:s0 '$targetFile'",
            )
        if (!deployOk) return false

        val pointerOk =
            RootUtils.exec(
                "printf '%s' '${entry.id}' > '${JanusPaths.ACTIVE_THEME}' && " +
                    "chmod 644 '${JanusPaths.ACTIVE_THEME}' && " +
                    "chcon u:object_r:theme_data_file:s0 '${JanusPaths.ACTIVE_THEME}'",
            )
        if (!pointerOk) return false

        cachedActiveId = ActiveIdCell(entry.id)
        return true
    }

    /** Clear the active_theme pointer so the hook falls through to the next layer. */
    fun deactivate(): Boolean {
        val ok = RootUtils.exec("rm -f '${JanusPaths.ACTIVE_THEME}'")
        if (ok) cachedActiveId = ActiveIdCell(null)
        return ok
    }

    /**
     * Destructive cleanup: remove all root-deployed themes and the active
     * pointer. App-private themes (and the metadata index) are left alone so
     * users can re-apply them later; call `clearAllLocal()` if you want to
     * wipe those too.
     */
    fun clearAllRootData(): Boolean {
        deactivate()
        return RootUtils.exec("rm -rf '${JanusPaths.THEMES_DIR}'")
    }

    // ── Private helpers ───────────────────────────────────────────

    private fun saveAll(entries: List<ThemeEntry>) {
        val arr = JSONArray()
        entries.sortedBy { it.order }.forEach { entry ->
            arr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("displayName", entry.displayName)
                    put("author", entry.author ?: JSONObject.NULL)
                    put("description", entry.description ?: JSONObject.NULL)
                    put("mamlVersion", entry.mamlVersion ?: JSONObject.NULL)
                    put("fileSize", entry.fileSize)
                    put("sourceFileName", entry.sourceFileName)
                    put("isExtractedFromMtz", entry.isExtractedFromMtz)
                    put("hasThumbnail", entry.hasThumbnail)
                    put("addedAt", entry.addedAt)
                    put("order", entry.order)
                },
            )
        }
        metaFile.writeText(arr.toString(2))
    }

    private fun parseEntry(json: JSONObject): ThemeEntry =
        ThemeEntry(
            id = json.getString("id"),
            displayName = json.getString("displayName"),
            author = json.optString("author", "").ifBlank { null },
            description = json.optString("description", "").ifBlank { null },
            mamlVersion = json.optString("mamlVersion", "").ifBlank { null },
            fileSize = json.optLong("fileSize", 0),
            sourceFileName = json.optString("sourceFileName", ""),
            isExtractedFromMtz = json.optBoolean("isExtractedFromMtz", false),
            hasThumbnail = json.optBoolean("hasThumbnail", false),
            addedAt = json.optLong("addedAt", 0),
            order = json.optInt("order", 0),
        )

    private fun repackMtzSubPackage(
        src: File,
        prefix: String,
        dst: File,
    ) {
        ZipFile(src).use { zipIn ->
            ZipOutputStream(dst.outputStream()).use { zipOut ->
                val entries = zipIn.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    if (!e.name.startsWith(prefix) || e.isDirectory) continue
                    val newName = e.name.substring(prefix.length)
                    if (newName.isEmpty()) continue
                    val newEntry =
                        ZipEntry(newName).apply {
                            time = e.time
                            if (e.method == ZipEntry.STORED) {
                                method = ZipEntry.STORED
                                size = e.size
                                crc = e.crc
                                compressedSize = e.compressedSize
                            }
                        }
                    zipOut.putNextEntry(newEntry)
                    zipIn.getInputStream(e).use { it.copyTo(zipOut) }
                    zipOut.closeEntry()
                }
            }
        }
    }

    private companion object {
        const val TAG = "Janus-ThemeManager"
    }
}

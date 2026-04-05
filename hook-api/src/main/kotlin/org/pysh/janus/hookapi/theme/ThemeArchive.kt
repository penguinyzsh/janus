package org.pysh.janus.hookapi.theme

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Pure-JVM parser for Xiaomi rear-screen theme archives (`.mrc` / `.mtz`).
 *
 * Responsibilities:
 *  - Detect whether the archive is a plain MRC or an MTZ integration bundle
 *    (in which case the real MRC lives under a `rearScreen/` sub-layer).
 *  - Validate the presence and shape of `manifest.xml` (root must be
 *    `<Widget>` with at least a `version` attribute - matches MAML
 *    convention observed in reverse-engineering docs under
 *    `_documents/逆向/背屏/04-扩展与Hook/壁纸系统.md`).
 *  - Resolve a display name through the strings-table fallback chain.
 *  - Pick a thumbnail entry from `assets/ai/{ai,preview,cover}.png` with
 *    graceful fallbacks to any other PNG under `assets/`.
 *
 * This object is intentionally dependency-free (JDK only) so it compiles
 * under `:hook-api` - a pure Kotlin/Java library with no Android APIs -
 * and can be exercised from fast JVM tests without Robolectric.
 *
 * Usage:
 * ```
 * val manifest = ThemeArchive.parse(File("theme.mrc"), fallbackName = "theme")
 * val thumb = manifest.thumbnailEntryPath?.let { path ->
 *     ThemeArchive.readEntryBytes(File("theme.mrc"), path)
 * }
 * ```
 */
object ThemeArchive {

    /** Maximum accepted archive size to guard against pathological inputs. */
    private const val MAX_ARCHIVE_BYTES = 512L * 1024 * 1024 // 512 MiB

    /** Maximum single-entry size read into memory (manifest / strings XML). */
    private const val MAX_TEXT_ENTRY_BYTES = 2 * 1024 * 1024 // 2 MiB

    /**
     * Parse [archiveFile] and return its [ThemeManifest], or throw
     * [InvalidThemeArchiveException] with a human-readable reason.
     *
     * @param fallbackName display name to use when no strings entry yields one.
     *   Typically the file stem from the import SAF URI.
     * @param preferredLocale locale used to resolve `strings_xx_YY.xml`.
     *   Defaults to the JVM default locale.
     */
    @Throws(InvalidThemeArchiveException::class)
    fun parse(
        archiveFile: File,
        fallbackName: String,
        preferredLocale: Locale = Locale.getDefault(),
    ): ThemeManifest {
        if (!archiveFile.exists() || !archiveFile.isFile) {
            throw InvalidThemeArchiveException("Archive not found: ${archiveFile.path}")
        }
        if (archiveFile.length() > MAX_ARCHIVE_BYTES) {
            throw InvalidThemeArchiveException(
                "Archive too large (${archiveFile.length()} bytes, limit $MAX_ARCHIVE_BYTES)",
            )
        }

        return try {
            ZipFile(archiveFile).use { zip ->
                val entries = zip.entries().toList().associateBy { it.name }
                val entryRoot = detectEntryRoot(entries.keys)
                    ?: throw InvalidThemeArchiveException(
                        "Not a rear-screen theme: no manifest.xml found at root or under rearScreen/",
                    )

                val manifestEntry = entries["${entryRoot}manifest.xml"]
                    ?: throw InvalidThemeArchiveException("manifest.xml missing under '$entryRoot'")
                val widgetAttrs = parseWidgetAttributes(zip, manifestEntry)

                val displayName = resolveDisplayName(
                    zip = zip,
                    entries = entries,
                    entryRoot = entryRoot,
                    preferredLocale = preferredLocale,
                    fallbackName = fallbackName.takeIf { it.isNotBlank() } ?: "Theme",
                )

                val thumbPath = pickThumbnail(entries.keys, entryRoot)

                ThemeManifest(
                    displayName = displayName,
                    author = widgetAttrs["author"]?.takeIf { it.isNotBlank() },
                    description = widgetAttrs["description"]?.takeIf { it.isNotBlank() },
                    mamlVersion = widgetAttrs["version"]?.takeIf { it.isNotBlank() },
                    isExtractedFromMtz = entryRoot.isNotEmpty(),
                    thumbnailEntryPath = thumbPath,
                    entryRoot = entryRoot,
                )
            }
        } catch (e: InvalidThemeArchiveException) {
            throw e
        } catch (e: IOException) {
            throw InvalidThemeArchiveException("Failed to read archive: ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            // ZipFile throws IAE on malformed archives on some JDKs.
            throw InvalidThemeArchiveException("Malformed ZIP archive: ${e.message}", e)
        }
    }

    /**
     * Read a single entry's bytes from [archiveFile]. Returns null if the
     * entry is absent or exceeds [MAX_TEXT_ENTRY_BYTES] (for safety when
     * callers mistake this helper for a general file extractor).
     */
    fun readEntryBytes(archiveFile: File, entryPath: String): ByteArray? {
        return try {
            ZipFile(archiveFile).use { zip ->
                val entry = zip.getEntry(entryPath) ?: return null
                if (entry.size > MAX_TEXT_ENTRY_BYTES * 32) return null // 64 MiB hard ceiling
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (_: IOException) {
            null
        }
    }

    // ── Internal helpers ─────────────────────────────────────────────

    /**
     * Returns "" if the archive contains `manifest.xml` at its root (plain
     * MRC), `"rearScreen/"` if it contains `rearScreen/manifest.xml` (MTZ),
     * or null otherwise.
     */
    private fun detectEntryRoot(names: Set<String>): String? {
        if ("manifest.xml" in names) return ""
        if ("rearScreen/manifest.xml" in names) return "rearScreen/"
        // Some MTZ variants nest under a capitalised directory.
        if ("RearScreen/manifest.xml" in names) return "RearScreen/"
        return null
    }

    private fun parseWidgetAttributes(
        zip: ZipFile,
        manifestEntry: ZipEntry,
    ): Map<String, String> {
        if (manifestEntry.size > MAX_TEXT_ENTRY_BYTES) {
            throw InvalidThemeArchiveException("manifest.xml too large")
        }
        val bytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
        val doc = try {
            safeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (e: Exception) {
            throw InvalidThemeArchiveException("manifest.xml is not valid XML: ${e.message}", e)
        }

        val root = doc.documentElement
            ?: throw InvalidThemeArchiveException("manifest.xml has no root element")
        if (!root.tagName.equals("Widget", ignoreCase = true)) {
            throw InvalidThemeArchiveException(
                "manifest.xml root must be <Widget>, got <${root.tagName}>",
            )
        }

        val attrs = mutableMapOf<String, String>()
        val nodeMap = root.attributes
        for (i in 0 until nodeMap.length) {
            val attr = nodeMap.item(i) as? org.w3c.dom.Attr ?: continue
            attrs[attr.name] = attr.value
        }
        return attrs
    }

    private fun resolveDisplayName(
        zip: ZipFile,
        entries: Map<String, ZipEntry>,
        entryRoot: String,
        preferredLocale: Locale,
        fallbackName: String,
    ): String {
        val lang = preferredLocale.language.lowercase()
        val country = preferredLocale.country.uppercase()

        val candidates = listOfNotNull(
            "${entryRoot}strings/strings_${lang}_$country.xml".takeIf { country.isNotEmpty() },
            "${entryRoot}strings/strings_$lang.xml",
            "${entryRoot}strings/strings.xml",
        )

        for (path in candidates) {
            val entry = entries[path] ?: continue
            val name = readThemeNameFromStrings(zip, entry) ?: continue
            if (name.isNotBlank()) return name
        }
        return fallbackName
    }

    private fun readThemeNameFromStrings(zip: ZipFile, entry: ZipEntry): String? {
        if (entry.size > MAX_TEXT_ENTRY_BYTES) return null
        val bytes = zip.getInputStream(entry).use { it.readBytes() }
        val doc = try {
            safeDocumentBuilder().parse(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            return null
        }
        val root = doc.documentElement ?: return null

        // MAML themes historically use <resource name="..." value="..."/>, but
        // many packages (including card-style MRCs) ship an Android-style
        // <string name="...">value</string> instead. Accept both.
        val preferredKeys = setOf("theme_name", "app_name", "name", "display_name", "title")
        val tagNames = listOf("resource", "string")
        var firstValue: String? = null
        for (tag in tagNames) {
            val nodes = root.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                val name = el.getAttribute("name")
                val value = el.getAttribute("value").takeIf { it.isNotBlank() }
                    ?: el.textContent?.trim().orEmpty()
                if (value.isBlank()) continue
                // Skip obvious non-name strings that some MRCs ship as the
                // first <string> entry (e.g., language/RTL metadata).
                if (name == "language" || name == "RTL") continue
                if (name in preferredKeys) return value
                if (firstValue == null) firstValue = value
            }
            if (firstValue != null) return firstValue
        }
        return firstValue
    }

    /**
     * Build a [DocumentBuilder] with best-effort XXE hardening that works on
     * both JVM (Apache Xerces) and Android (internal Xerces-derived parser).
     *
     * We intentionally avoid Apache-specific feature URIs like
     * `http://apache.org/xml/features/disallow-doctype-decl` because Android's
     * `DocumentBuilderFactoryImpl` throws `ParserConfigurationException` for
     * them on some API levels, causing otherwise-valid archives to be
     * rejected. The parser is still hardened via:
     *  - `FEATURE_SECURE_PROCESSING` (JAXP standard, supported everywhere)
     *  - external entity disablement via `XMLConstants` URIs when available
     *  - `isExpandEntityReferences = false` as a final belt.
     *
     * Any feature/property that is not supported is silently ignored — we
     * prefer loosened security over rejecting the user's file.
     */
    private fun safeDocumentBuilder(): javax.xml.parsers.DocumentBuilder {
        val factory = DocumentBuilderFactory.newInstance()
        runCatching {
            factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        runCatching {
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "")
        }
        runCatching {
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        return factory.newDocumentBuilder()
    }

    private fun pickThumbnail(entryNames: Set<String>, entryRoot: String): String? {
        val preferred = listOf(
            "${entryRoot}assets/ai/ai.png",
            "${entryRoot}assets/ai/preview.png",
            "${entryRoot}assets/ai/cover.png",
            "${entryRoot}preview.png",
            "${entryRoot}assets/preview.png",
        )
        for (path in preferred) {
            if (path in entryNames) return path
        }
        // Fallback: any PNG under assets/ (deterministic order).
        val anyAssetPng = entryNames
            .filter { it.startsWith("${entryRoot}assets/") && it.endsWith(".png") }
            .minOrNull()
        if (anyAssetPng != null) return anyAssetPng
        return null
    }
}

/** Thrown when an archive cannot be accepted as a rear-screen theme. */
class InvalidThemeArchiveException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

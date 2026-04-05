package org.pysh.janus.hookapi.theme

/**
 * Parsed metadata describing a rear-screen theme package (`.mrc` / `.mtz`).
 *
 * The source archive is a ZIP container - either:
 *  - A plain MRC: entries rooted at the archive root
 *    (`manifest.xml`, `strings/<locale>.xml`, `assets/...`).
 *  - An MTZ integration package: rear-screen content lives under a
 *    `rearScreen/` subdirectory inside the archive; the parser transparently
 *    peels that layer so callers can treat both formats uniformly.
 *
 * The [ThemeManifest] carries only *metadata* - it does NOT retain file
 * handles or byte buffers. Large payloads (ZIP body, assets) stay on disk
 * and callers re-open the archive via [ThemeArchive] when they need them.
 */
data class ThemeManifest(
    /**
     * Best-effort display name chosen from, in order:
     *  1. `strings/strings_${lang}_${country}.xml` matching `preferredLocale`
     *  2. `strings/strings_${lang}.xml`
     *  3. `strings/strings.xml`
     *  4. Fallback: the file name stem supplied by the caller.
     *
     * Always non-blank.
     */
    val displayName: String,
    /** `author` attribute of `<Widget>` root, or null. */
    val author: String?,
    /** `description` attribute or empty. */
    val description: String?,
    /** MAML `version` attribute of `<Widget>`, or null. */
    val mamlVersion: String?,
    /**
     * True if the archive came in as an MTZ integration package and was
     * peeled to its `rearScreen/` sub-layer. Useful for UI hints.
     */
    val isExtractedFromMtz: Boolean,
    /**
     * Absolute ZIP entry path of the thumbnail picked by [ThemeArchive], or
     * null if no image asset was found. Callers may re-open the archive and
     * read this entry to materialise a preview bitmap.
     */
    val thumbnailEntryPath: String?,
    /**
     * Entry path prefix inside the archive that hosts `manifest.xml` -
     * empty string for plain MRC, `"rearScreen/"` for MTZ. All other entry
     * paths referenced by this manifest are relative to this prefix.
     */
    val entryRoot: String,
)

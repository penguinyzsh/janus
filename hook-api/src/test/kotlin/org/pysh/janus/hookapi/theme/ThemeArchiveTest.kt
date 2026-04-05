package org.pysh.janus.hookapi.theme

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises [ThemeArchive] against in-memory fixture ZIPs built at test time
 * so no checked-in binaries are required. Runs under pure JVM via
 * `./gradlew :hook-api:test`.
 */
class ThemeArchiveTest {

    private val tempFiles = mutableListOf<File>()

    @After
    fun cleanup() {
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    // ── Fixture builders ──────────────────────────────────────────────

    private fun writeZip(name: String, entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("theme-fixture-$name-", ".mrc")
        tempFiles += file
        ZipOutputStream(file.outputStream()).use { zos ->
            entries.forEach { (path, bytes) ->
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return file
    }

    private fun minimalManifest(
        version: String = "2",
        author: String? = "Janus Test",
        description: String? = "unit test fixture",
    ): ByteArray {
        val attrs = buildString {
            append(" version=\"$version\"")
            append(" screenWidth=\"1080\"")
            append(" frameRate=\"0\"")
            if (author != null) append(" author=\"$author\"")
            if (description != null) append(" description=\"$description\"")
        }
        return """<?xml version="1.0" encoding="utf-8"?>
            |<Widget$attrs>
            |  <Var name="bgmode" type="number" value="2" />
            |</Widget>
        """.trimMargin().toByteArray()
    }

    private fun stringsXml(themeName: String): ByteArray =
        """<?xml version="1.0" encoding="utf-8"?>
           |<resources>
           |  <resource name="theme_name" value="$themeName" />
           |  <resource name="author" value="Janus" />
           |</resources>
        """.trimMargin().toByteArray()

    private fun pngBytes(): ByteArray {
        // Minimal 1x1 PNG (8-byte sig + IHDR + IDAT + IEND), hand-crafted.
        val baos = ByteArrayOutputStream()
        // PNG signature.
        baos.write(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10))
        // IHDR chunk (length 13).
        baos.write(byteArrayOf(0, 0, 0, 13))
        baos.write("IHDR".toByteArray())
        baos.write(byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0))
        baos.write(byteArrayOf(-112, 119, 83, -34)) // CRC (not verified by parser)
        // IDAT (empty — test only parses metadata, not pixels)
        baos.write(byteArrayOf(0, 0, 0, 0))
        baos.write("IDAT".toByteArray())
        baos.write(byteArrayOf(35, 40, 96, 19))
        // IEND
        baos.write(byteArrayOf(0, 0, 0, 0))
        baos.write("IEND".toByteArray())
        baos.write(byteArrayOf(-82, 66, 96, -126))
        return baos.toByteArray()
    }

    // ── Test cases ────────────────────────────────────────────────────

    @Test
    fun `plain MRC with manifest + strings + thumbnail parses successfully`() {
        val archive = writeZip(
            "plain",
            mapOf(
                "manifest.xml" to minimalManifest(author = "Acme", description = "A cool theme"),
                "strings/strings.xml" to stringsXml("Fallback Name"),
                "strings/strings_zh_CN.xml" to stringsXml("中文主题"),
                "assets/ai/ai.png" to pngBytes(),
                "assets/fontconf/misans-light.png" to pngBytes(),
            ),
        )

        val manifest = ThemeArchive.parse(
            archiveFile = archive,
            fallbackName = "Fallback",
            preferredLocale = Locale.SIMPLIFIED_CHINESE,
        )

        assertEquals("中文主题", manifest.displayName)
        assertEquals("Acme", manifest.author)
        assertEquals("A cool theme", manifest.description)
        assertEquals("2", manifest.mamlVersion)
        assertFalse(manifest.isExtractedFromMtz)
        assertEquals("", manifest.entryRoot)
        assertEquals("assets/ai/ai.png", manifest.thumbnailEntryPath)
    }

    @Test
    fun `MTZ integration package is transparently peeled to rearScreen sublayer`() {
        val archive = writeZip(
            "mtz",
            mapOf(
                "description.xml" to "<theme/>".toByteArray(),
                "lockscreen/manifest.xml" to "<OtherThing/>".toByteArray(),
                "rearScreen/manifest.xml" to minimalManifest(),
                "rearScreen/strings/strings.xml" to stringsXml("MTZ Embedded"),
                "rearScreen/assets/ai/preview.png" to pngBytes(),
            ),
        )

        val manifest = ThemeArchive.parse(
            archiveFile = archive,
            fallbackName = "fallback-ignored",
            preferredLocale = Locale.US,
        )

        assertEquals("MTZ Embedded", manifest.displayName)
        assertTrue(manifest.isExtractedFromMtz)
        assertEquals("rearScreen/", manifest.entryRoot)
        assertEquals("rearScreen/assets/ai/preview.png", manifest.thumbnailEntryPath)
    }

    @Test
    fun `locale fallback climbs from country to language to default strings`() {
        val archive = writeZip(
            "locales",
            mapOf(
                "manifest.xml" to minimalManifest(),
                "strings/strings.xml" to stringsXml("Default"),
                "strings/strings_zh.xml" to stringsXml("Chinese"),
            ),
        )

        // No zh_TW → falls back to zh → "Chinese"
        val zhTw = ThemeArchive.parse(archive, "x", Locale.TRADITIONAL_CHINESE)
        assertEquals("Chinese", zhTw.displayName)

        // No ja at all → falls back to default "Default"
        val ja = ThemeArchive.parse(archive, "x", Locale.JAPANESE)
        assertEquals("Default", ja.displayName)
    }

    @Test
    fun `missing strings yield fallback name from caller`() {
        val archive = writeZip(
            "no-strings",
            mapOf(
                "manifest.xml" to minimalManifest(author = null, description = null),
                "assets/ai/ai.png" to pngBytes(),
            ),
        )

        val manifest = ThemeArchive.parse(archive, fallbackName = "My Theme")
        assertEquals("My Theme", manifest.displayName)
        assertNull(manifest.author)
        assertNull(manifest.description)
    }

    @Test
    fun `thumbnail fallback picks any PNG under assets when preferred names absent`() {
        val archive = writeZip(
            "thumb-fallback",
            mapOf(
                "manifest.xml" to minimalManifest(),
                "strings/strings.xml" to stringsXml("X"),
                "assets/misc/other.png" to pngBytes(),
            ),
        )

        val manifest = ThemeArchive.parse(archive, "x")
        assertEquals("assets/misc/other.png", manifest.thumbnailEntryPath)
    }

    @Test
    fun `archive without manifest is rejected`() {
        val archive = writeZip(
            "no-manifest",
            mapOf(
                "strings/strings.xml" to stringsXml("X"),
                "assets/ai/ai.png" to pngBytes(),
            ),
        )

        val ex = assertThrows(InvalidThemeArchiveException::class.java) {
            ThemeArchive.parse(archive, "x")
        }
        assertTrue(ex.message!!.contains("manifest.xml"))
    }

    @Test
    fun `manifest with wrong root element is rejected`() {
        val archive = writeZip(
            "wrong-root",
            mapOf(
                "manifest.xml" to "<NotAWidget version=\"2\"/>".toByteArray(),
            ),
        )

        val ex = assertThrows(InvalidThemeArchiveException::class.java) {
            ThemeArchive.parse(archive, "x")
        }
        assertTrue(ex.message!!.contains("<Widget>"))
    }

    @Test
    fun `readEntryBytes extracts thumbnail after parse`() {
        val pngSample = pngBytes()
        val archive = writeZip(
            "extract",
            mapOf(
                "manifest.xml" to minimalManifest(),
                "assets/ai/ai.png" to pngSample,
            ),
        )

        val manifest = ThemeArchive.parse(archive, "x")
        val bytes = ThemeArchive.readEntryBytes(archive, manifest.thumbnailEntryPath!!)
        assertNotNull(bytes)
        assertEquals(pngSample.size, bytes!!.size)
    }
}

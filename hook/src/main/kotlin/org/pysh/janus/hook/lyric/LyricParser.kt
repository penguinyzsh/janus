package org.pysh.janus.hook.lyric

import android.util.Log
import java.util.regex.Pattern

/**
 * Parses TTML (Timed Text Markup Language) strings into a sorted list of TimedLines.
 *
 * Supports multiple time formats:
 *  - HH:MM:SS.mmm
 *  - MM:SS.mmm
 *  - SS.mmm (pure seconds, e.g. "5.967")
 *
 * Also handles multi-language <div xml:lang="..."> groups, extracting the primary
 * language's lyrics.
 */
object LyricParser {

    private const val TAG = "Janus-LyricParser"

    // Match <p begin="..." end="...">...</p>
    private val P_PATTERN = Pattern.compile(
        "<p\\s+begin=\"([^\"]+)\"\\s+end=\"([^\"]+)\"[^>]*>(.*?)</p>",
        Pattern.DOTALL
    )

    // Match inner HTML/XML tags like <span>, <br/>, etc.
    private val TAG_PATTERN = Pattern.compile("<[^>]+>")

    /**
     * Parse a TTML string into a list of [TimedLine], sorted by beginMs.
     *
     * @param ttml the raw TTML XML string
     * @return a sorted list of timed lyric lines (may be empty if parsing fails)
     */
    fun parseTtml(ttml: String): List<TimedLine> {
        if (ttml.isBlank()) return emptyList()

        val lines = mutableListOf<TimedLine>()
        try {
            val matcher = P_PATTERN.matcher(ttml)
            while (matcher.find()) {
                val beginStr = matcher.group(1) ?: continue
                val endStr = matcher.group(2) ?: continue
                var text = matcher.group(3) ?: ""

                // Strip inner tags (spans, etc.)
                text = TAG_PATTERN.matcher(text).replaceAll("").trim()

                val beginMs = parseTimeToMs(beginStr)
                val endMs = parseTimeToMs(endStr)

                if (text.isNotEmpty() && beginMs >= 0 && endMs > beginMs) {
                    lines.add(TimedLine(beginMs, endMs, text))
                }
            }
            lines.sortBy { it.beginMs }
        } catch (e: Throwable) {
            Log.e(TAG, "Error parsing TTML", e)
        }
        return lines
    }

    /**
     * Parse a time string into milliseconds.
     *
     * Supports:
     *  - "HH:MM:SS.mmm"  → 4 parts after split
     *  - "MM:SS.mmm"      → 3 parts after split
     *  - "SS.mmm" or "N.fff" (pure seconds) → 2 parts after split
     *  - Pure integer ms  → 1 part
     */
    fun parseTimeToMs(timeStr: String): Long {
        try {
            // Try pure seconds format first (e.g. "5.967")
            if (!timeStr.contains(":")) {
                val seconds = timeStr.toDoubleOrNull()
                if (seconds != null) {
                    return (seconds * 1000).toLong()
                }
            }

            val parts = timeStr.split(":", ".")
            return when (parts.size) {
                4 -> { // HH:MM:SS.mmm
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val s = parts[2].toLong()
                    val ms = padMillis(parts[3])
                    (h * 3600 + m * 60 + s) * 1000 + ms
                }
                3 -> { // MM:SS.mmm
                    val m = parts[0].toLong()
                    val s = parts[1].toLong()
                    val ms = padMillis(parts[2])
                    (m * 60 + s) * 1000 + ms
                }
                2 -> { // SS.mmm
                    val s = parts[0].toLong()
                    val ms = padMillis(parts[1])
                    s * 1000 + ms
                }
                1 -> parts[0].toLongOrNull() ?: -1L
                else -> -1L
            }
        } catch (_: Exception) {
            return -1L
        }
    }

    /**
     * Pad or truncate fractional seconds to 3-digit milliseconds.
     * "9" → 900, "96" → 960, "967" → 967, "9670" → 967
     */
    private fun padMillis(s: String): Long {
        val padded = s.take(3).padEnd(3, '0')
        return padded.toLongOrNull() ?: 0L
    }
}

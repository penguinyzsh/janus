package org.pysh.janus.hook

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Lyric format parsers for [LyricInjector] implementations.
 *
 * Supported formats:
 * - TTML (Timed Text Markup Language) — used by Apple Music
 * - LRC — used by most Chinese music apps (QQ Music, NetEase, etc.)
 */
object LyricParser {

    private typealias TimedLine = LyricInjector.TimedLine

    // ── TTML ────────────────────────────────────────────────────────────

    /**
     * Parse TTML lyrics into timed lines + translation map.
     *
     * Multi-div documents are merged by language: the primary language
     * becomes the main lines, remaining languages become translations
     * keyed by beginMs.
     */
    fun parseTtml(ttml: String): Pair<List<TimedLine>, Map<Int, String>> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(ttml))

        var primaryLang: String? = null
        val divGroups = mutableListOf<Pair<String?, MutableList<TimedLine>>>()
        var currentDivLang: String? = null
        var currentDivLines: MutableList<TimedLine>? = null

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "tt" -> primaryLang = getXmlLang(parser)
                    "div" -> {
                        currentDivLang = getXmlLang(parser)
                        currentDivLines = mutableListOf()
                    }
                    "p" -> {
                        val begin = parser.getAttributeValue(null, "begin")
                        val end = parser.getAttributeValue(null, "end")
                        if (begin != null && currentDivLines != null) {
                            val beginMs = parseTtmlTime(begin)
                            val endMs = if (end != null) parseTtmlTime(end) else -1
                            val text = collectText(parser, "p")
                            if (text.isNotBlank() && beginMs >= 0) {
                                currentDivLines.add(TimedLine(beginMs, endMs, text.trim()))
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "div" -> {
                        if (currentDivLines != null && currentDivLines.isNotEmpty()) {
                            divGroups.add(currentDivLang to currentDivLines)
                        }
                        currentDivLines = null
                        currentDivLang = null
                    }
                }
            }
            parser.next()
        }

        if (divGroups.isEmpty()) return emptyList<TimedLine>() to emptyMap()

        val byLang = mutableMapOf<String?, MutableList<TimedLine>>()
        for ((lang, lines) in divGroups) {
            byLang.getOrPut(lang) { mutableListOf() }.addAll(lines)
        }

        val originalLang = when {
            primaryLang != null && primaryLang in byLang -> primaryLang
            null in byLang -> null
            else -> byLang.keys.first()
        }
        val originalLines = (byLang.remove(originalLang) ?: emptyList()).sortedBy { it.beginMs }

        val transMap = mutableMapOf<Int, String>()
        for ((_, lines) in byLang) {
            for (line in lines) {
                transMap[line.beginMs] = line.text
            }
        }

        return originalLines to transMap
    }

    private fun collectText(parser: XmlPullParser, endTag: String): String {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            parser.next()
            when (parser.eventType) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> {
                    depth--
                    if (depth == 0 && parser.name == endTag) break
                }
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return sb.toString()
    }

    private fun getXmlLang(parser: XmlPullParser): String? {
        return parser.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
            ?: parser.getAttributeValue(null, "xml:lang")
    }

    private fun parseTtmlTime(time: String): Int {
        val trimmed = time.trim()
        if (trimmed.endsWith("s", ignoreCase = true)) {
            val secs = trimmed.dropLast(1).toDoubleOrNull() ?: return -1
            return (secs * 1000).toInt()
        }
        val parts = trimmed.split(':')
        return when (parts.size) {
            3 -> {
                val h = parts[0].toIntOrNull() ?: return -1
                val m = parts[1].toIntOrNull() ?: return -1
                val s = parts[2].toDoubleOrNull() ?: return -1
                ((h * 3600 + m * 60) * 1000 + (s * 1000)).toInt()
            }
            2 -> {
                val m = parts[0].toIntOrNull() ?: return -1
                val s = parts[1].toDoubleOrNull() ?: return -1
                (m * 60_000 + (s * 1000)).toInt()
            }
            1 -> {
                val s = parts[0].toDoubleOrNull() ?: return -1
                (s * 1000).toInt()
            }
            else -> -1
        }
    }

    // ── LRC ─────────────────────────────────────────────────────────────

    private val TIMESTAMP_RE = Regex("""\[(\d{1,3}):(\d{2})\.(\d{2,3})]""")
    private val OFFSET_RE = Regex("""\[offset:([+-]?\d+)]""")

    /**
     * Parse LRC format lyrics into timed lines + translation map.
     *
     * Supports:
     * - Standard LRC: `[mm:ss.xx]text`
     * - Multiple timestamps per line: `[01:23.45][02:34.56]text`
     * - Offset tag: `[offset:+500]`
     * - Dual-language: two lines sharing the same timestamp → second is translation
     */
    fun parseLrc(lrc: String): Pair<List<TimedLine>, Map<Int, String>> {
        var offsetMs = 0
        val entries = mutableListOf<Pair<Int, String>>()

        for (rawLine in lrc.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            OFFSET_RE.find(line)?.let {
                offsetMs = it.groupValues[1].toIntOrNull() ?: 0
            }

            val timestamps = TIMESTAMP_RE.findAll(line).toList()
            if (timestamps.isEmpty()) continue

            val textStart = timestamps.last().range.last + 1
            val text = if (textStart < line.length) line.substring(textStart).trim() else ""
            if (text.isEmpty()) continue

            for (match in timestamps) {
                val min = match.groupValues[1].toIntOrNull() ?: continue
                val sec = match.groupValues[2].toIntOrNull() ?: continue
                val frac = match.groupValues[3]
                val ms = (frac.toIntOrNull() ?: 0).let {
                    if (frac.length == 2) it * 10 else it
                }
                val time = ((min * 60 + sec) * 1000 + ms + offsetMs).coerceAtLeast(0)
                entries.add(time to text)
            }
        }

        entries.sortBy { it.first }

        // Group by timestamp: if 2 lines share a timestamp, second is translation
        val grouped = entries.groupBy({ it.first }, { it.second })
        val lines = mutableListOf<TimedLine>()
        val trans = mutableMapOf<Int, String>()

        for ((ts, texts) in grouped.toSortedMap()) {
            lines.add(TimedLine(ts, -1, texts[0]))
            if (texts.size > 1) {
                trans[ts] = texts[1]
            }
        }

        return lines to trans
    }
}

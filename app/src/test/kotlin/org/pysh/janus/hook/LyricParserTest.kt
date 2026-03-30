package org.pysh.janus.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for [LyricParser.parseTtml] and [LyricParser.parseLrc].
 *
 * Uses Robolectric because TTML parsing depends on [XmlPullParser] from the Android SDK.
 */
@RunWith(RobolectricTestRunner::class)
class LyricParserTest {

    // ── TTML ────────────────────────────────────────────────────────────

    @Test
    fun ttml_singleDiv_multipleLines() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000" end="00:00:03.000">Line one</p>
                <p begin="00:00:04.000" end="00:00:06.000">Line two</p>
                <p begin="00:00:07.500" end="00:00:10.000">Line three</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, trans) = LyricParser.parseTtml(ttml)
        assertEquals(3, lines.size)
        assertEquals("Line one", lines[0].text)
        assertEquals(1000, lines[0].beginMs)
        assertEquals(3000, lines[0].endMs)
        assertEquals("Line two", lines[1].text)
        assertEquals("Line three", lines[2].text)
        assertTrue(trans.isEmpty())
    }

    @Test
    fun ttml_multiDiv_translations() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="en">
              <body>
                <div xml:lang="en">
                  <p begin="00:00:01.000" end="00:00:03.000">Hello</p>
                  <p begin="00:00:04.000" end="00:00:06.000">World</p>
                </div>
                <div xml:lang="zh">
                  <p begin="00:00:01.000" end="00:00:03.000">你好</p>
                  <p begin="00:00:04.000" end="00:00:06.000">世界</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val (lines, trans) = LyricParser.parseTtml(ttml)
        assertEquals(2, lines.size)
        assertEquals("Hello", lines[0].text)
        assertEquals("World", lines[1].text)
        assertEquals("你好", trans[1000])
        assertEquals("世界", trans[4000])
    }

    @Test
    fun ttml_emptyInput_returnsEmpty() {
        val (lines, trans) = LyricParser.parseTtml("")
        assertTrue(lines.isEmpty())
        assertTrue(trans.isEmpty())
    }

    @Test
    fun ttml_malformedXml_doesNotCrash() {
        // The key contract: no exception thrown. "nope" is not a valid time,
        // so parseTtmlTime returns -1, and the line is skipped (beginMs >= 0 check).
        val (lines, _) = LyricParser.parseTtml("<tt><body><div><p begin=\"nope\">text</p>")
        assertTrue("Malformed time should produce no valid lines", lines.isEmpty())
    }

    @Test
    fun ttml_secondsFormat() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="12.5s" end="15s">Seconds format</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals(12500, lines[0].beginMs)
        assertEquals(15000, lines[0].endMs)
    }

    @Test
    fun ttml_mmssFormat() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="01:30.500" end="01:35.000">MM:SS format</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals(90500, lines[0].beginMs)
        assertEquals(95000, lines[0].endMs)
    }

    @Test
    fun ttml_blankText_skipped() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000" end="00:00:02.000">   </p>
                <p begin="00:00:03.000" end="00:00:04.000">Real line</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals("Real line", lines[0].text)
    }

    @Test
    fun ttml_nestedSpan_collectsText() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000" end="00:00:03.000">
                  <span>Hello </span><span>World</span>
                </p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertTrue(lines[0].text.contains("Hello"))
        assertTrue(lines[0].text.contains("World"))
    }

    @Test
    fun ttml_outOfOrder_sortedByBeginMs() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:05.000" end="00:00:06.000">Second</p>
                <p begin="00:00:01.000" end="00:00:02.000">First</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(2, lines.size)
        assertEquals("First", lines[0].text)
        assertEquals("Second", lines[1].text)
    }

    @Test
    fun ttml_beginNoEnd_endMsIsNegative() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000">No end</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals(-1, lines[0].endMs)
    }

    // ── LRC ─────────────────────────────────────────────────────────────

    @Test
    fun lrc_standard() {
        val lrc = "[00:01.00]First line\n[00:05.00]Second line\n"
        val (lines, trans) = LyricParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("First line", lines[0].text)
        assertEquals(1000, lines[0].beginMs)
        assertEquals("Second line", lines[1].text)
        assertEquals(5000, lines[1].beginMs)
        assertTrue(trans.isEmpty())
    }

    @Test
    fun lrc_multiTimestamp_expandsToMultipleEntries() {
        val lrc = "[01:23.45][02:34.56]Repeated line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("Repeated line", lines[0].text)
        assertEquals("Repeated line", lines[1].text)
        assertTrue(lines[0].beginMs < lines[1].beginMs)
    }

    @Test
    fun lrc_positiveOffset() {
        val lrc = "[offset:+500]\n[00:01.00]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(1500, lines[0].beginMs)
    }

    @Test
    fun lrc_negativeOffset() {
        val lrc = "[offset:-200]\n[00:01.00]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(800, lines[0].beginMs)
    }

    @Test
    fun lrc_dualLanguage_sameTimestamp() {
        val lrc = "[00:01.00]English line\n[00:01.00]中文翻译\n[00:05.00]Another\n"
        val (lines, trans) = LyricParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("English line", lines[0].text)
        assertEquals("中文翻译", trans[1000])
    }

    @Test
    fun lrc_twoDigitFraction_multipliedBy10() {
        val lrc = "[01:23.45]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        // 1*60*1000 + 23*1000 + 45*10 = 60000 + 23000 + 450 = 83450
        assertEquals(83450, lines[0].beginMs)
    }

    @Test
    fun lrc_threeDigitFraction_usedDirectly() {
        val lrc = "[01:23.456]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        // 1*60*1000 + 23*1000 + 456 = 60000 + 23000 + 456 = 83456
        assertEquals(83456, lines[0].beginMs)
    }

    @Test
    fun lrc_emptyTextAfterTimestamp_skipped() {
        val lrc = "[00:01.00]\n[00:05.00]Real line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals("Real line", lines[0].text)
    }

    @Test
    fun lrc_emptyInput_returnsEmpty() {
        val (lines, trans) = LyricParser.parseLrc("")
        assertTrue(lines.isEmpty())
        assertTrue(trans.isEmpty())
    }

    @Test
    fun lrc_metadataLines_skipped() {
        val lrc = "[ti:Song Title]\n[ar:Artist]\n[00:01.00]Actual lyric\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals("Actual lyric", lines[0].text)
    }

    @Test
    fun lrc_sortedByTimestamp() {
        val lrc = "[00:10.00]Later\n[00:01.00]Earlier\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(2, lines.size)
        assertEquals("Earlier", lines[0].text)
        assertEquals("Later", lines[1].text)
    }

    @Test
    fun lrc_endMs_isAlwaysNegativeOne() {
        val lrc = "[00:01.00]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(-1, lines[0].endMs)
    }

    // ── P17: Missing edge cases ─────────────────────────────────────

    @Test
    fun ttml_singleNumberTime() {
        // parseTtmlTime parts.size == 1 branch: "5.5" → 5500ms
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="5.5" end="8.0">Single number</p>
              </div></body>
            </tt>
        """.trimIndent()
        val (lines, _) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals(5500, lines[0].beginMs)
        assertEquals(8000, lines[0].endMs)
    }

    @Test
    fun lrc_threeDigitMinutes() {
        // Regex allows \d{1,3} for minutes
        val lrc = "[100:00.00]Long song\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        assertEquals(100 * 60 * 1000, lines[0].beginMs)
    }

    @Test
    fun lrc_negativeOffset_clampedToZero() {
        val lrc = "[offset:-5000]\n[00:01.00]Line\n"
        val (lines, _) = LyricParser.parseLrc(lrc)
        assertEquals(1, lines.size)
        // 1000 - 5000 would be -4000, but clamped to 0
        assertEquals(0, lines[0].beginMs)
    }

    @Test
    fun ttml_threeLanguages_onlyFirstIsMain() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xml:lang="en">
              <body>
                <div xml:lang="en">
                  <p begin="00:00:01.000" end="00:00:03.000">English</p>
                </div>
                <div xml:lang="zh">
                  <p begin="00:00:01.000" end="00:00:03.000">中文</p>
                </div>
                <div xml:lang="ja">
                  <p begin="00:00:01.000" end="00:00:03.000">日本語</p>
                </div>
              </body>
            </tt>
        """.trimIndent()
        val (lines, trans) = LyricParser.parseTtml(ttml)
        assertEquals(1, lines.size)
        assertEquals("English", lines[0].text)
        // Both zh and ja become translations; last one wins for same beginMs
        assertTrue(trans.containsKey(1000))
    }
}

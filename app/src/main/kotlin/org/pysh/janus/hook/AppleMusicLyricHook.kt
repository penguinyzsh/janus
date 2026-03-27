package org.pysh.janus.hook

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * Hooks Apple Music to display lyrics on the rear screen.
 *
 * Mechanism: subscreencenter's music template displays the TITLE field from
 * MediaMetadata on the rear screen. Apps like 汽水音乐 show lyrics by writing
 * the current lyric line into the TITLE field, updating every few seconds.
 *
 * This hook does the same for Apple Music:
 * 1. Captures TTML lyrics when parsed by Apple Music's native TTMLParser
 * 2. Converts to a timed lyric list (text + begin/end milliseconds)
 * 3. Periodically reads playback position, finds the current line, and
 *    overwrites the TITLE field in MediaMetadata with the lyric text
 */
object AppleMusicLyricHook {

    private const val TAG = "Janus-AppleMusic"
    private const val UPDATE_INTERVAL_MS = 500L

    data class TimedLine(val beginMs: Int, val endMs: Int, val text: String)

    @Volatile
    private var lyrics: List<TimedLine> = emptyList()

    @Volatile
    private var translations: Map<Int, String> = emptyMap()

    @Volatile
    private var originalTitle: String? = null

    @Volatile
    private var lastLyricLine: String? = null

    private var mediaSessionRef: MediaSession? = null
    private var controllerRef: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updaterRunning = false

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookTtmlParser(lpparam)
        hookSetMetadata(lpparam)
        hookPlaybackState(lpparam)
        XposedBridge.log("[$TAG] Hooks installed")
    }

    // ── Hook 1: Capture TTML, build timed lyric list ──────────────────

    private fun hookTtmlParser(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass(
                "com.apple.android.music.ttml.javanative.TTMLParser\$TTMLParserNative",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                cls, "songInfoFromTTML", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ttml = param.args[0] as? String ?: return
                        try {
                            parseTtml(ttml)
                            XposedBridge.log("[$TAG] Parsed ${lyrics.size} lines, ${translations.size} translations")
                            if (lyrics.isNotEmpty()) startLyricUpdater()
                        } catch (e: Throwable) {
                            XposedBridge.log("[$TAG] TTML parse failed: ${e.message}")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookTtmlParser failed: ${e.message}")
        }
    }

    // ── Hook 2: Capture MediaSession + original title ─────────────────

    private fun hookSetMetadata(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                MediaSession::class.java, "setMetadata",
                MediaMetadata::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val metadata = param.args[0] as? MediaMetadata ?: return
                        val session = param.thisObject as MediaSession

                        if (mediaSessionRef !== session) {
                            mediaSessionRef = session
                            controllerRef = session.controller
                        }

                        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                        // Only save as original title if it's NOT a lyric line we injected
                        if (title != null && title != lastLyricLine) {
                            originalTitle = title
                        }

                        // If lyrics are active, overwrite title with current lyric
                        // to prevent Apple Music's original title from flashing through
                        if (lyrics.isNotEmpty() && updaterRunning && lastLyricLine != null) {
                            try {
                                val bundle = XposedHelpers.getObjectField(metadata, "mBundle")
                                        as android.os.Bundle
                                bundle.putString(MediaMetadata.METADATA_KEY_TITLE, lastLyricLine)
                                bundle.putString(
                                    "android.media.metadata.CUSTOM_FIELD_TITLE", lastLyricLine)
                            } catch (_: Throwable) { }
                        }

                        if (lyrics.isNotEmpty()) startLyricUpdater()
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookSetMetadata failed: ${e.message}")
        }
    }

    // ── Hook 3: Start/stop lyric updater based on playback state ──────

    private fun hookPlaybackState(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                MediaSession::class.java, "setPlaybackState",
                PlaybackState::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val state = param.args[0] as? PlaybackState ?: return
                        when (state.state) {
                            PlaybackState.STATE_PLAYING,
                            PlaybackState.STATE_BUFFERING,
                            PlaybackState.STATE_FAST_FORWARDING,
                            PlaybackState.STATE_REWINDING -> startLyricUpdater()
                            else -> stopLyricUpdater()
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookPlaybackState failed: ${e.message}")
        }
    }

    // ── Smart lyric updater — schedules next update at line boundary ──

    private val updateRunnable = Runnable {
        if (updaterRunning) scheduleNextUpdate()
    }

    private fun startLyricUpdater() {
        if (updaterRunning) return
        if (lyrics.isEmpty()) return
        updaterRunning = true
        handler.post { scheduleNextUpdate() }
    }

    private fun stopLyricUpdater() {
        updaterRunning = false
        handler.removeCallbacks(updateRunnable)
    }

    private fun scheduleNextUpdate() {
        if (!updaterRunning) return
        val session = mediaSessionRef ?: return
        val controller = controllerRef ?: return
        val currentLyrics = lyrics
        if (currentLyrics.isEmpty()) return

        val playbackState = controller.playbackState ?: return
        val position = getPosition(playbackState)

        // Find current line and next line
        val idx = findLyricIndex(currentLyrics, position)
        val line = if (idx >= 0) currentLyrics[idx] else null
        val trans = if (line != null) translations[line.beginMs] else null
        val lyricText = when {
            line == null -> originalTitle
            trans != null -> "${line.text}\n${trans}"
            else -> line.text
        }

        // Only call setMetadata when the displayed line actually changes
        if (lyricText != lastLyricLine) {
            lastLyricLine = lyricText
            val metadata = controller.metadata
            if (metadata != null) {
                try {
                    val bundle = XposedHelpers.getObjectField(metadata, "mBundle")
                            as android.os.Bundle
                    bundle.putString(MediaMetadata.METADATA_KEY_TITLE, lyricText)
                    bundle.putString("android.media.metadata.CUSTOM_FIELD_TITLE", lyricText)
                    session.setMetadata(metadata)
                } catch (_: Throwable) { }
            }
        }

        // Schedule next update at the next line boundary
        val nextIdx = idx + 1
        val delayMs = if (nextIdx in currentLyrics.indices) {
            val nextBegin = currentLyrics[nextIdx].beginMs.toLong()
            val wait = nextBegin - getPosition(playbackState)
            wait.coerceIn(200, 10_000)
        } else {
            2000L // After last line, poll slowly
        }
        handler.postDelayed(updateRunnable, delayMs)
    }

    private fun getPosition(state: PlaybackState): Long {
        return state.position +
                ((android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime)
                        * state.playbackSpeed).toLong()
    }

    /** Returns index of the current lyric line, or -1 if in a gap/before first line. */
    private fun findLyricIndex(lines: List<TimedLine>, positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].beginMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        if (result >= 0) {
            val line = lines[result]
            if (line.endMs > 0 && positionMs > line.endMs) return -1
        }
        return result
    }

    // ── TTML parsing ──────────────────────────────────────────────────

    private fun parseTtml(ttml: String) {
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
                            val beginMs = parseTime(begin)
                            val endMs = if (end != null) parseTime(end) else -1
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

        if (divGroups.isEmpty()) {
            lyrics = emptyList()
            translations = emptyMap()
            return
        }

        // Group divs by language. Divs with same lang (or null) are sections
        // of the same song (Verse, Chorus, etc.), not translations.
        val byLang = mutableMapOf<String?, MutableList<TimedLine>>()
        for ((lang, lines) in divGroups) {
            byLang.getOrPut(lang) { mutableListOf() }.addAll(lines)
        }

        // Primary language group = matching primaryLang, or null, or first
        val originalLang = when {
            primaryLang != null && primaryLang in byLang -> primaryLang
            null in byLang -> null
            else -> byLang.keys.first()
        }
        lyrics = (byLang.remove(originalLang) ?: emptyList()).sortedBy { it.beginMs }

        // Remaining language groups = translations
        val transMap = mutableMapOf<Int, String>()
        for ((_, lines) in byLang) {
            for (line in lines) {
                transMap[line.beginMs] = line.text
            }
        }
        translations = transMap
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

    private fun parseTime(time: String): Int {
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
}

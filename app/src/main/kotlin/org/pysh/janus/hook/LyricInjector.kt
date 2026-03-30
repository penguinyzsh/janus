package org.pysh.janus.hook

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.libxposed.api.XposedInterface

/**
 * Generic lyric injector for the rear screen.
 *
 * Writes the current lyric line into MediaMetadata's TITLE field so that
 * subscreencenter's music template displays it. This is the same mechanism
 * used by QQ Music (Bluetooth lyrics) and 汽水音乐.
 *
 * Hooks MediaSession.setMetadata and setPlaybackState (stable Android API).
 * Lyric sources are handled externally by JSON rule actions (lyric_extract)
 * that call [setLyrics] when timed lyrics become available.
 */
object LyricInjector {

    data class TimedLine(val beginMs: Int, val endMs: Int, val text: String)

    /**
     * The currently active LyricInjector reference for ActionExecutor to call setLyrics().
     * Points to this singleton once hooks are installed.
     */
    @Volatile
    @JvmStatic
    var activeInstance: LyricInjector? = null
        private set

    private const val TAG = "Janus-Lyric"

    @Volatile
    var lyrics: List<TimedLine> = emptyList()
        private set

    @Volatile
    var translations: Map<Int, String> = emptyMap()
        private set

    @Volatile
    private var originalTitle: String? = null

    @Volatile
    private var currentSongKey: String? = null

    @Volatile
    private var lastLyricLine: String? = null

    private var mediaSessionRef: MediaSession? = null
    private var controllerRef: MediaController? = null
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var updaterRunning = false

    // ── Public API ───────────────────────────────────────────────────

    /**
     * Install MediaSession hooks in the current process.
     * Call once per music app process that needs lyric injection.
     */
    fun hookMediaSession(module: XposedInterface) {
        hookSetMetadata(module)
        hookPlaybackState(module)
        activeInstance = this
        Log.d(TAG, "MediaSession hooks installed")
    }

    /** Called when timed lyrics are available (from lyric_extract action or external source). */
    fun setLyrics(
        lines: List<TimedLine>,
        trans: Map<Int, String> = emptyMap()
    ) {
        lyrics = lines
        translations = trans
        Log.d(TAG, "Loaded ${lines.size} lines, ${trans.size} translations")
        if (lines.isNotEmpty()) startLyricUpdater()
    }

    // ── MediaSession hooks (generic, stable Android API) ─────────────

    private fun hookSetMetadata(module: XposedInterface) {
        try {
            val method = MediaSession::class.java.getDeclaredMethod(
                "setMetadata", MediaMetadata::class.java
            )
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val metadata = chain.args[0] as? MediaMetadata
                val session = chain.thisObject as MediaSession

                if (metadata != null) {
                    if (mediaSessionRef !== session) {
                        mediaSessionRef = session
                        controllerRef = session.controller
                    }

                    val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
                    val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)

                    // Detect song change -> clear stale lyrics
                    if (title != null && title != lastLyricLine) {
                        val songKey = "$title|$artist"
                        if (songKey != currentSongKey) {
                            currentSongKey = songKey
                            lyrics = emptyList()
                            translations = emptyMap()
                            lastLyricLine = null
                            stopLyricUpdater()
                        }
                        originalTitle = title
                    }

                    // If lyrics are active, overwrite title with current lyric
                    if (lyrics.isNotEmpty() && lastLyricLine != null) {
                        try {
                            val bundle = ReflectUtils.getField(metadata, "mBundle")
                                    as android.os.Bundle
                            bundle.putString(MediaMetadata.METADATA_KEY_TITLE, lastLyricLine)
                            bundle.putString(
                                "android.media.metadata.CUSTOM_FIELD_TITLE", lastLyricLine)
                        } catch (_: Throwable) { }
                    }

                    if (lyrics.isNotEmpty()) startLyricUpdater()
                }

                chain.proceed()
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookSetMetadata failed: ${e.message}")
        }
    }

    private fun hookPlaybackState(module: XposedInterface) {
        try {
            val method = MediaSession::class.java.getDeclaredMethod(
                "setPlaybackState", PlaybackState::class.java
            )
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                val state = chain.args[0] as? PlaybackState
                if (state != null) {
                    when (state.state) {
                        PlaybackState.STATE_PLAYING,
                        PlaybackState.STATE_BUFFERING,
                        PlaybackState.STATE_FAST_FORWARDING,
                        PlaybackState.STATE_REWINDING -> {
                            startLyricUpdater()
                            if (lyrics.isNotEmpty()) {
                                handler.post { scheduleNextUpdate() }
                            }
                        }
                        else -> stopLyricUpdater()
                    }
                }
                result
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookPlaybackState failed: ${e.message}")
        }
    }

    // ── Lyric updater ────────────────────────────────────────────────

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
        val controller = controllerRef ?: return
        val currentLyrics = lyrics
        if (currentLyrics.isEmpty()) return

        val playbackState = controller.playbackState ?: return
        val position = getPosition(playbackState)

        val idx = findLyricIndex(currentLyrics, position)
        val line = if (idx >= 0) currentLyrics[idx] else null
        val trans = if (line != null) translations[line.beginMs] else null
        val lyricText = when {
            line == null -> originalTitle
            trans != null -> "${line.text}\n${trans}"
            else -> line.text
        }

        // Schedule next line
        val nextIdx = idx + 1
        val nextBeginMs = if (nextIdx in currentLyrics.indices)
            currentLyrics[nextIdx].beginMs.toLong() else -1L

        if (lyricText != lastLyricLine) {
            lastLyricLine = lyricText
            val session = mediaSessionRef ?: return
            val metadata = controller.metadata
            if (metadata != null) try {
                val bundle = ReflectUtils.getField(metadata, "mBundle")
                        as android.os.Bundle
                bundle.putString(MediaMetadata.METADATA_KEY_TITLE, lyricText)
                bundle.putString("android.media.metadata.CUSTOM_FIELD_TITLE", lyricText)
                session.setMetadata(metadata)
            } catch (_: Throwable) { }
        }

        val delayMs = if (nextBeginMs > 0) {
            (nextBeginMs - getPosition(playbackState)).coerceIn(200, 10_000)
        } else {
            2000L
        }
        handler.postDelayed(updateRunnable, delayMs)
    }

    private fun getPosition(state: PlaybackState): Long {
        return state.position +
                ((android.os.SystemClock.elapsedRealtime() - state.lastPositionUpdateTime)
                        * state.playbackSpeed).toLong()
    }

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
}

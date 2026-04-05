package org.pysh.janus.hook.engine.engines

import android.content.SharedPreferences
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hook.engine.HookRule
import org.pysh.janus.hook.engine.RuleEngine
import org.pysh.janus.hook.lyric.LyricParser
import org.pysh.janus.hook.lyric.TimedLine

/**
 * Apple Music lyric engine for Janus.
 *
 * Implementation inspired by SuperLyric's Apple.java:
 *  1. Hook PlaybackState to track playback position
 *  2. Hook MediaSession.setMetadata() to detect song changes + save session reference
 *  3. Hook PlayerLyricsViewModel.buildTimeRangeToLyricsMap() to extract parsed lyrics
 *     from Apple Music's own SongInfo object
 *  4. Fallback: Hook songInfoFromTTML() to parse raw TTML
 *  5. Run a 400ms polling timer to sync current lyric line with playback position
 *  6. Inject current lyric line into METADATA_KEY_TITLE for back screen display
 *
 * The key insight from SuperLyric: Apple Music's TTML parsing produces a SongInfo
 * object with sections→lines, each having getBegin()/getEnd()/getHtmlLineText().
 * Hooking at this level is more robust than parsing raw TTML ourselves.
 */
class AppleMusicLyricEngine : HookEnginePlugin {

    companion object {
        const val ENGINE_NAME = "apple_music_lyric"
        private const val TAG = "Janus-AppleMusicLyric"
        private const val POLL_INTERVAL_MS = 400L
    }

    // --- Lyric state ---
    private val lyricList = mutableListOf<TimedLine>()
    @Volatile private var playbackState: PlaybackState? = null
    @Volatile private var lastShownLyric: TimedLine? = null
    @Volatile private var originalTitle: String = ""
    @Volatile private var isRunning = false

    // --- Session reference for TITLE injection ---
    @Volatile private var activeSession: MediaSession? = null
    @Volatile private var currentMetadata: MediaMetadata? = null

    /**
     * Anti-recursion flag: prevents our setMetadata hook from treating
     * lyric title injection as a "song change". Set to true before we call
     * session.setMetadata(), and checked inside the hook.
     */
    @Volatile
    private var isInjecting = false

    // --- Handlers ---
    private lateinit var mainHandler: Handler
    private lateinit var lyricHandler: Handler

    override fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: SharedPreferences,
    ) {
        // Initialize handlers
        mainHandler = Handler(Looper.getMainLooper())
        val lyricThread = HandlerThread("JanusAppleMusicLyric")
        lyricThread.start()
        lyricHandler = Handler(lyricThread.looper)

        val configFlag = rule.configFlag

        // 1. Hook PlaybackState to track current position
        hookPlaybackState(module, config, configFlag)

        // 2. Hook MediaSession.setMetadata() for song change detection + session reference
        hookMediaSessionSetMetadata(module, config, configFlag)

        // 3. Hook MediaSession.setPlaybackState() for play/pause detection
        hookMediaSessionSetPlaybackState(module, config, configFlag)

        // 4. Try to hook high-level buildTimeRangeToLyricsMap (like SuperLyric)
        val viewModelClass = rule.targets?.get("lyrics_viewmodel")
            ?: "com.apple.android.music.player.viewmodel.PlayerLyricsViewModel"
        val viewModelHooked = tryHookBuildTimeRangeToLyricsMap(module, classLoader, viewModelClass, config, configFlag)

        // 5. Fallback: hook songInfoFromTTML for raw TTML parsing
        if (!viewModelHooked) {
            val parserClass = rule.targets?.get("ttml_parser")
                ?: "com.apple.android.music.ttml.javanative.TTMLParser\$TTMLParserNative"
            hookSongInfoFromTTML(module, classLoader, parserClass, config, configFlag)
        }

        Log.i(TAG, "AppleMusicLyricEngine installed (viewModelHooked=$viewModelHooked)")
    }

    // =========================================================================
    // Hook 1: PlaybackState constructor — always track the latest state
    // =========================================================================

    private fun hookPlaybackState(
        module: XposedInterface,
        config: SharedPreferences,
        configFlag: String?,
    ) {
        try {
            for (ctor in PlaybackState::class.java.declaredConstructors) {
                module.hook(ctor).intercept(XposedInterface.Hooker { chain ->
                    val result = chain.proceed()
                    if (configFlag == null || RuleEngine.isConfigEnabled(config, configFlag)) {
                        playbackState = chain.thisObject as? PlaybackState
                    }
                    result
                })
            }
            HookStatusReporter.report("apple_lyric_playback_state", true, "PlaybackState hooked")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook PlaybackState: ${e.message}")
            HookStatusReporter.report("apple_lyric_playback_state", false, e.message)
        }
    }

    // =========================================================================
    // Hook 2: MediaSession.setMetadata() — detect song changes + save session
    // =========================================================================

    private fun hookMediaSessionSetMetadata(
        module: XposedInterface,
        config: SharedPreferences,
        configFlag: String?,
    ) {
        try {
            val method = MediaSession::class.java.getDeclaredMethod(
                "setMetadata", MediaMetadata::class.java
            )
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                if (configFlag != null && !RuleEngine.isConfigEnabled(config, configFlag)) {
                    return@Hooker chain.proceed()
                }

                // Save session reference
                activeSession = chain.thisObject as? MediaSession

                // Skip song-change detection when WE are injecting lyrics
                if (isInjecting) {
                    return@Hooker chain.proceed()
                }

                val metadata = chain.args[0] as? MediaMetadata
                if (metadata != null) {
                    currentMetadata = metadata
                    val newTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)

                    // Detect song change (compare with original title, not lyric text)
                    if (newTitle != null && originalTitle != newTitle) {
                        Log.d(TAG, "Song changed: '$originalTitle' → '$newTitle'")
                        originalTitle = newTitle

                        // Reset lyric state
                        synchronized(lyricList) {
                            lyricList.clear()
                        }
                        isRunning = false
                        lastShownLyric = null
                        lyricHandler.removeCallbacks(updateRunnable)
                    }
                }

                chain.proceed()
            })
            HookStatusReporter.report("apple_lyric_metadata", true, "setMetadata hooked")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook setMetadata: ${e.message}")
            HookStatusReporter.report("apple_lyric_metadata", false, e.message)
        }
    }

    // =========================================================================
    // Hook 3: MediaSession.setPlaybackState() — start/stop lyric timer
    // =========================================================================

    private fun hookMediaSessionSetPlaybackState(
        module: XposedInterface,
        config: SharedPreferences,
        configFlag: String?,
    ) {
        try {
            val method = MediaSession::class.java.getDeclaredMethod(
                "setPlaybackState", PlaybackState::class.java
            )
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                if (configFlag != null && !RuleEngine.isConfigEnabled(config, configFlag)) {
                    return@Hooker chain.proceed()
                }

                val state = chain.args[0] as? PlaybackState
                if (state != null) {
                    playbackState = state

                    when (state.state) {
                        PlaybackState.STATE_PLAYING -> startLyricUpdater()
                        PlaybackState.STATE_PAUSED,
                        PlaybackState.STATE_STOPPED,
                        PlaybackState.STATE_NONE -> {
                            stopLyricUpdater()
                            // Restore original title when playback stops/pauses
                            restoreOriginalTitle()
                        }
                    }
                }

                chain.proceed()
            })
            HookStatusReporter.report("apple_lyric_playback", true, "setPlaybackState hooked")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook setPlaybackState: ${e.message}")
            HookStatusReporter.report("apple_lyric_playback", false, e.message)
        }
    }

    // =========================================================================
    // Hook 4: buildTimeRangeToLyricsMap — extract lyrics from SongInfo
    // (Preferred path, like SuperLyric's approach)
    // =========================================================================

    private fun tryHookBuildTimeRangeToLyricsMap(
        module: XposedInterface,
        classLoader: ClassLoader,
        className: String,
        config: SharedPreferences,
        configFlag: String?,
    ): Boolean {
        return try {
            val clazz = classLoader.loadClass(className)
            val method = clazz.declaredMethods.find { it.name == "buildTimeRangeToLyricsMap" }
            if (method == null) {
                Log.w(TAG, "buildTimeRangeToLyricsMap not found in $className")
                return false
            }

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                if (configFlag != null && !RuleEngine.isConfigEnabled(config, configFlag)) {
                    return@Hooker chain.proceed()
                }

                val result = chain.proceed()

                try {
                    val songInfoPtr = chain.args[0] ?: return@Hooker result
                    val songInfo = songInfoPtr.javaClass.getMethod("get").invoke(songInfoPtr)
                        ?: return@Hooker result

                    val sectionsVector = songInfo.javaClass.getMethod("getSections").invoke(songInfo)
                        ?: return@Hooker result

                    val newLines = extractLinesFromSections(sectionsVector)
                    if (newLines.isNotEmpty()) {
                        synchronized(lyricList) {
                            // Only update if this looks like a different song's lyrics
                            if (lyricList.isEmpty() ||
                                newLines.first().beginMs != lyricList.first().beginMs
                            ) {
                                lyricList.clear()
                                lyricList.addAll(newLines)
                            }
                        }
                        Log.d(TAG, "Extracted ${newLines.size} lyrics via SongInfo")
                        startLyricUpdater()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error extracting lyrics from SongInfo", e)
                }

                result
            })

            HookStatusReporter.report("apple_lyric_viewmodel", true, "buildTimeRangeToLyricsMap hooked")
            Log.i(TAG, "Successfully hooked buildTimeRangeToLyricsMap")
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Cannot hook buildTimeRangeToLyricsMap: ${e.message}")
            HookStatusReporter.report("apple_lyric_viewmodel", false, e.message)
            false
        }
    }

    /**
     * Traverse the native SongInfo's sections→lines structure using reflection.
     * Based on SuperLyric's LyricsLinePtrHelper logic.
     *
     * Structure: SongInfo.getSections() → Vector<SectionPtr>
     *   SectionPtr.get() → Section
     *     Section.getLines() → Vector<LinePtr>
     *       LinePtr.get() → Line
     *         Line.getBegin() → int (ms)
     *         Line.getEnd() → int (ms)
     *         Line.getHtmlLineText() → String
     */
    private fun extractLinesFromSections(sectionsVector: Any): List<TimedLine> {
        val lines = mutableListOf<TimedLine>()
        try {
            val sizeMethod = sectionsVector.javaClass.getMethod("size")
            val getMethod = sectionsVector.javaClass.getMethod("get", Long::class.javaPrimitiveType)

            val sectionCount = (sizeMethod.invoke(sectionsVector) as Number).toLong()
            for (sectionIdx in 0 until sectionCount) {
                val sectionPtr = getMethod.invoke(sectionsVector, sectionIdx) ?: continue
                val section = sectionPtr.javaClass.getMethod("get").invoke(sectionPtr) ?: continue
                val linesVector = section.javaClass.getMethod("getLines").invoke(section) ?: continue

                val lineSizeMethod = linesVector.javaClass.getMethod("size")
                val lineGetMethod = linesVector.javaClass.getMethod("get", Long::class.javaPrimitiveType)
                val lineCount = (lineSizeMethod.invoke(linesVector) as Number).toLong()

                for (lineIdx in 0 until lineCount) {
                    try {
                        val linePtr = lineGetMethod.invoke(linesVector, lineIdx) ?: continue
                        val line = linePtr.javaClass.getMethod("get").invoke(linePtr) ?: continue

                        val text = line.javaClass.getMethod("getHtmlLineText").invoke(line) as? String
                            ?: continue
                        val begin = (line.javaClass.getMethod("getBegin").invoke(line) as Number).toLong()
                        val end = (line.javaClass.getMethod("getEnd").invoke(line) as Number).toLong()

                        val cleanText = text.replace(Regex("<[^>]+>"), "").trim()
                        if (cleanText.isNotEmpty() && end > begin) {
                            lines.add(TimedLine(begin, end, cleanText))
                        }
                    } catch (e: Throwable) {
                        // Line access failed, skip
                        break
                    }
                }
            }
            lines.sortBy { it.beginMs }
        } catch (e: Throwable) {
            Log.e(TAG, "Error traversing SongInfo sections", e)
        }
        return lines
    }

    // =========================================================================
    // Hook 5 (Fallback): songInfoFromTTML — parse raw TTML
    // =========================================================================

    private fun hookSongInfoFromTTML(
        module: XposedInterface,
        classLoader: ClassLoader,
        className: String,
        config: SharedPreferences,
        configFlag: String?,
    ) {
        try {
            val parserClass = classLoader.loadClass(className)
            val method = parserClass.declaredMethods.find {
                it.name == "songInfoFromTTML" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == String::class.java
            }

            if (method == null) {
                Log.e(TAG, "songInfoFromTTML not found in $className")
                HookStatusReporter.report("apple_lyric_ttml", false, "Method not found")
                return
            }

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                if (configFlag != null && !RuleEngine.isConfigEnabled(config, configFlag)) {
                    return@Hooker chain.proceed()
                }

                try {
                    val ttmlString = chain.args[0] as? String
                    if (!ttmlString.isNullOrEmpty()) {
                        val newLines = LyricParser.parseTtml(ttmlString)
                        if (newLines.isNotEmpty()) {
                            synchronized(lyricList) {
                                lyricList.clear()
                                lyricList.addAll(newLines)
                            }
                            Log.d(TAG, "Parsed ${newLines.size} lines from TTML")
                            startLyricUpdater()
                        }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error in songInfoFromTTML hook", e)
                }

                chain.proceed()
            })

            HookStatusReporter.report("apple_lyric_ttml", true, "songInfoFromTTML hooked")
            Log.i(TAG, "Successfully hooked songInfoFromTTML (fallback)")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook songInfoFromTTML: ${e.message}")
            HookStatusReporter.report("apple_lyric_ttml", false, e.message)
        }
    }

    // =========================================================================
    // Lyric updater loop (runs on lyricHandler thread)
    // =========================================================================

    private fun startLyricUpdater() {
        if (isRunning) return
        val hasLines = synchronized(lyricList) { lyricList.isNotEmpty() }
        if (!hasLines) return

        isRunning = true
        lyricHandler.post(updateRunnable)
    }

    private fun stopLyricUpdater() {
        isRunning = false
        lyricHandler.removeCallbacks(updateRunnable)
    }

    private val updateRunnable: Runnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            val state = playbackState
            if (state == null || state.state != PlaybackState.STATE_PLAYING) {
                isRunning = false
                return
            }

            val currentLines = synchronized(lyricList) { lyricList.toList() }
            if (currentLines.isEmpty()) {
                isRunning = false
                return
            }

            // Compute current playback position (accounting for speed)
            val elapsed = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
            val currentPosition = state.position + (elapsed * state.playbackSpeed).toLong()

            // Find current lyric line
            var currentLine: TimedLine? = null
            for (line in currentLines) {
                if (currentPosition >= line.beginMs && currentPosition < line.endMs) {
                    currentLine = line
                    break
                }
            }

            // Send lyric if changed
            if (currentLine != null && (lastShownLyric == null || lastShownLyric != currentLine)) {
                lastShownLyric = currentLine
                injectLyricIntoTitle(currentLine.text)
            }

            // Continue polling
            lyricHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    // =========================================================================
    // TITLE injection — modify MediaMetadata to display lyrics on back screen
    // =========================================================================

    /**
     * Inject the lyric text into the active MediaSession's metadata TITLE field.
     * This makes the Xiaomi back screen display the lyric as the song title.
     *
     * Uses [isInjecting] flag to prevent the setMetadata hook from treating
     * our injection as a song change event.
     */
    private fun injectLyricIntoTitle(lyric: String) {
        val session = activeSession ?: return
        val baseMetadata = currentMetadata ?: return

        try {
            // Build new metadata with lyric as TITLE, keeping all other fields
            val builder = MediaMetadata.Builder(baseMetadata)
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, lyric)
            val newMetadata = builder.build()

            // Set flag BEFORE calling setMetadata to prevent recursion
            isInjecting = true
            try {
                session.setMetadata(newMetadata)
            } finally {
                isInjecting = false
            }

            Log.d(TAG, "Injected lyric: $lyric")
        } catch (e: Throwable) {
            isInjecting = false
            Log.e(TAG, "Failed to inject lyric title: ${e.message}")
        }
    }

    /**
     * Restore the original song title when playback stops/pauses.
     * This avoids the back screen showing a random lyric line as the song name
     * when the music is paused.
     */
    private fun restoreOriginalTitle() {
        if (originalTitle.isEmpty()) return
        val session = activeSession ?: return
        val baseMetadata = currentMetadata ?: return

        try {
            val currentTitleInMetadata = baseMetadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            // Only restore if the title was actually replaced with a lyric
            if (currentTitleInMetadata != null && currentTitleInMetadata != originalTitle) {
                val builder = MediaMetadata.Builder(baseMetadata)
                builder.putString(MediaMetadata.METADATA_KEY_TITLE, originalTitle)
                val restoredMetadata = builder.build()

                isInjecting = true
                try {
                    session.setMetadata(restoredMetadata)
                } finally {
                    isInjecting = false
                }

                Log.d(TAG, "Restored original title: $originalTitle")
            }
        } catch (e: Throwable) {
            isInjecting = false
            Log.e(TAG, "Failed to restore title: ${e.message}")
        }
    }
}

package org.pysh.janus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import org.pysh.janus.R
import org.pysh.janus.core.util.RootUtils
import org.pysh.janus.data.WallpaperManager
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.core.util.JanusPaths
import org.pysh.janus.util.WallpaperUtils
import java.io.File
import java.util.concurrent.Executors

/**
 * Single foreground service that hosts every persistent background feature
 * Janus ships today: **screen keep-alive** (for casting) and **wallpaper
 * rotation**. Replaces the prior two-service design where each feature had
 * its own FGS + ongoing notification — users running both features used to
 * see two entries in the notification shade.
 *
 * ## Feature multiplexing
 *
 * The service doesn't hold a single "running" bit — it tracks each feature
 * independently via [isKeepAliveActive] and [isRotationActive]. The lifetime
 * follows a reference-count rule: when any feature is active, the service
 * stays in the foreground with a dynamically-composed notification; when
 * every feature has been turned off, [stopSelf] is called and the service
 * exits. Each feature has its own [Handler] timer so they don't interfere.
 *
 * ## Why a single service?
 *
 * 1. **One notification** — on the FGS contract, each service has its own
 *    ongoing notification. Two services = two entries. Users complained.
 * 2. **MIUI resilience** — MIUI's autostart / battery whitelist is checked
 *    per-process. A single long-lived process is easier to whitelist and
 *    less likely to be killed than two intermittently-started services.
 * 3. **Lower startup overhead** — starting a second FGS while one is already
 *    running briefly causes two notifications to flash (until the second's
 *    channel override kicks in).
 *
 * ## Why not start with [startForegroundService] + [stopService] everywhere?
 *
 * `stopService` on a multi-feature service would kill the FGS even when the
 * other feature is still active. Instead we use action-based IPC: the
 * companion helpers wrap `Intent.setAction(...)` and deliver via
 * `startForegroundService` for starts, `startService` for stops.
 */
class JanusBackgroundService : Service() {
    companion object {
        const val CHANNEL_ID = "janus_background"
        private const val NOTIFICATION_ID = 1
        private const val TAG = "Janus-BgService"

        /** Default screen keep-alive interval (seconds) — matches prior behaviour. */
        const val DEFAULT_KEEP_ALIVE_INTERVAL = 10

        private const val ACTION_START_KEEP_ALIVE = "org.pysh.janus.bg.START_KEEP_ALIVE"
        private const val ACTION_STOP_KEEP_ALIVE = "org.pysh.janus.bg.STOP_KEEP_ALIVE"
        private const val ACTION_START_ROTATION = "org.pysh.janus.bg.START_ROTATION"
        private const val ACTION_STOP_ROTATION = "org.pysh.janus.bg.STOP_ROTATION"
        private const val EXTRA_KEEP_ALIVE_INTERVAL = "keep_alive_interval_seconds"

        @Volatile
        var isKeepAliveActive: Boolean = false
            private set

        @Volatile
        var isRotationActive: Boolean = false
            private set

        /** Start or refresh the screen keep-alive feature with the given interval (seconds). */
        fun startKeepAlive(context: Context, intervalSeconds: Int = DEFAULT_KEEP_ALIVE_INTERVAL) {
            val intent =
                Intent(context, JanusBackgroundService::class.java)
                    .setAction(ACTION_START_KEEP_ALIVE)
                    .putExtra(EXTRA_KEEP_ALIVE_INTERVAL, intervalSeconds)
            context.startForegroundService(intent)
        }

        /** Stop the screen keep-alive feature. If rotation is still running, the service stays alive. */
        fun stopKeepAlive(context: Context) {
            if (!isKeepAliveActive) return
            val intent =
                Intent(context, JanusBackgroundService::class.java)
                    .setAction(ACTION_STOP_KEEP_ALIVE)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "stopKeepAlive startService failed: ${it.message}") }
        }

        /** Start or refresh the wallpaper rotation feature. Interval is read from [WhitelistManager]. */
        fun startRotation(context: Context) {
            val intent =
                Intent(context, JanusBackgroundService::class.java)
                    .setAction(ACTION_START_ROTATION)
            context.startForegroundService(intent)
        }

        /** Stop the wallpaper rotation feature. If keep-alive is still running, the service stays alive. */
        fun stopRotation(context: Context) {
            if (!isRotationActive) return
            val intent =
                Intent(context, JanusBackgroundService::class.java)
                    .setAction(ACTION_STOP_ROTATION)
            runCatching { context.startService(intent) }
                .onFailure { Log.w(TAG, "stopRotation startService failed: ${it.message}") }
        }
    }

    private val keepAliveHandler = Handler(Looper.getMainLooper())
    private val rotationHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var keepAliveIntervalMs: Long = DEFAULT_KEEP_ALIVE_INTERVAL * 1000L
    private var rotationIntervalMs: Long = 30_000L

    private val keepAliveRunnable =
        object : Runnable {
            override fun run() {
                executor.execute { RootUtils.exec("input -d 1 keyevent 224") }
                keepAliveHandler.postDelayed(this, keepAliveIntervalMs)
            }
        }

    private val rotationRunnable =
        object : Runnable {
            override fun run() {
                executor.execute { doRotationCycle() }
                rotationHandler.postDelayed(this, rotationIntervalMs)
            }
        }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Recover persisted feature state as early as possible. Two cases:
        //  1. Normal start — the UI writes SP first, then calls startForegroundService.
        //     onCreate sees the new SP state, recover posts the handler. The
        //     subsequent onStartCommand with the explicit action is idempotent
        //     (handlers re-posted after removeCallbacks).
        //  2. START_STICKY restart after the process was killed (MIUI swipe, OOM).
        //     On Android 13+, the system may recreate the service *without*
        //     calling onStartCommand — only onCreate fires. If recovery were
        //     done only in onStartCommand, the service would be a zombie:
        //     foreground + notification visible, but no Handler runnable posted,
        //     so keep-alive and rotation silently stop working until the user
        //     re-opens the app. Doing it here guarantees handlers get re-posted
        //     regardless of whether onStartCommand is later called.
        recoverFromPersistedState()
        if (isKeepAliveActive || isRotationActive) {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            // Defensive: if onCreate's recovery somehow missed (shouldn't normally
            // happen because onCreate runs before every onStartCommand on a fresh
            // service instance), try again here.
            if (!isKeepAliveActive && !isRotationActive) {
                recoverFromPersistedState()
            }
        } else {
            when (action) {
                ACTION_START_KEEP_ALIVE -> {
                    val seconds =
                        intent.getIntExtra(EXTRA_KEEP_ALIVE_INTERVAL, DEFAULT_KEEP_ALIVE_INTERVAL)
                    keepAliveIntervalMs = seconds * 1000L
                    isKeepAliveActive = true
                    keepAliveHandler.removeCallbacks(keepAliveRunnable)
                    keepAliveHandler.post(keepAliveRunnable)
                    Log.i(TAG, "Keep-alive started, interval=${seconds}s")
                }

                ACTION_STOP_KEEP_ALIVE -> {
                    isKeepAliveActive = false
                    keepAliveHandler.removeCallbacks(keepAliveRunnable)
                    Log.i(TAG, "Keep-alive stopped")
                }

                ACTION_START_ROTATION -> {
                    val wm = WhitelistManager(this)
                    val seconds = wm.getAutoRotateInterval().coerceAtLeast(1)
                    rotationIntervalMs = seconds * 1000L
                    isRotationActive = true
                    rotationHandler.removeCallbacks(rotationRunnable)
                    rotationHandler.postDelayed(rotationRunnable, rotationIntervalMs)
                    Log.i(TAG, "Rotation started, interval=${seconds}s")
                }

                ACTION_STOP_ROTATION -> {
                    isRotationActive = false
                    rotationHandler.removeCallbacks(rotationRunnable)
                    Log.i(TAG, "Rotation stopped")
                }
            }
        }

        // Reference-count: keep the service in foreground only while at least
        // one feature is active. Every state change refreshes the notification
        // so its text reflects the currently active feature set.
        if (!isKeepAliveActive && !isRotationActive) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    /**
     * Re-read persisted feature state from [WhitelistManager] and re-activate
     * any features the user had enabled. Called on null-intent restarts
     * (START_STICKY recovery after the process was killed).
     *
     * Must stay in sync with the branches of [onStartCommand] — each feature
     * that can be "on" through normal toggles must also be restorable here.
     */
    private fun recoverFromPersistedState() {
        val wm = WhitelistManager(this)
        if (wm.isKeepAliveEnabled()) {
            val seconds = wm.getKeepAliveInterval().coerceAtLeast(1)
            keepAliveIntervalMs = seconds * 1000L
            isKeepAliveActive = true
            keepAliveHandler.removeCallbacks(keepAliveRunnable)
            keepAliveHandler.post(keepAliveRunnable)
            Log.i(TAG, "Recovered keep-alive from prefs, interval=${seconds}s")
        }
        if (wm.isAutoRotateEnabled()) {
            val seconds = wm.getAutoRotateInterval().coerceAtLeast(1)
            rotationIntervalMs = seconds * 1000L
            isRotationActive = true
            rotationHandler.removeCallbacks(rotationRunnable)
            rotationHandler.postDelayed(rotationRunnable, rotationIntervalMs)
            Log.i(TAG, "Recovered rotation from prefs, interval=${seconds}s")
        }
    }

    override fun onDestroy() {
        keepAliveHandler.removeCallbacks(keepAliveRunnable)
        rotationHandler.removeCallbacks(rotationRunnable)
        executor.shutdownNow()
        isKeepAliveActive = false
        isRotationActive = false
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Wallpaper rotation cycle ─────────────────────────────────

    private fun doRotationCycle() {
        val whitelistManager = WhitelistManager(this)
        val wallpaperManager = WallpaperManager(this)

        // User toggled off while we were waiting — shut rotation down.
        if (!whitelistManager.isAutoRotateEnabled()) {
            rotationHandler.post {
                isRotationActive = false
                rotationHandler.removeCallbacks(rotationRunnable)
                if (!isKeepAliveActive) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
            return
        }

        val wallpapers = wallpaperManager.getWallpapers()
        if (wallpapers.size <= 1) return

        val currentIndex = wallpapers.indexOfFirst { it.isApplied }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % wallpapers.size
        val nextWp = wallpapers[nextIndex]

        // Check if the MRC is already cached; if not, build + deploy.
        val mrcPath = "${JanusPaths.WALLPAPERS_DIR}/wp_${nextWp.id}.mrc"
        if (!RootUtils.exec("test -f '$mrcPath'")) {
            val videoUri = Uri.fromFile(File(nextWp.videoPath))
            // applyWallpaper builds MRC + deploys + sets pointer + restarts
            WallpaperUtils.applyWallpaper(
                this,
                videoUri,
                whitelistManager.isWallpaperLoop(),
                nextWp.id,
            )
        } else {
            // Cache hit — just update pointer and restart.
            WallpaperUtils.setActiveWallpaper(nextWp.id)
            RootUtils.restartBackScreen()
        }

        wallpaperManager.markApplied(nextWp.id)

        val broadcast = Intent("org.pysh.janus.ACTION_WALLPAPER_CHANGED")
        broadcast.setPackage(packageName)
        sendBroadcast(broadcast)
    }

    // ── Notification building ─────────────────────────────────────

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.janus_bg_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.janus_bg_notification_channel_desc)
            }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val title: String
        val text: String
        when {
            isKeepAliveActive && isRotationActive -> {
                title = getString(R.string.janus_bg_notification_title_both)
                val keepSecs = (keepAliveIntervalMs / 1000).toInt()
                val rotSecs = (rotationIntervalMs / 1000).toInt()
                text = getString(R.string.janus_bg_notification_text_both, keepSecs, rotSecs)
            }

            isKeepAliveActive -> {
                title = getString(R.string.keep_alive)
                val secs = (keepAliveIntervalMs / 1000).toInt()
                text = getString(R.string.keep_alive_notification_text, secs)
            }

            isRotationActive -> {
                title = getString(R.string.wp_auto_rotate)
                val secs = (rotationIntervalMs / 1000).toInt()
                text = getString(R.string.wp_auto_rotate_interval_summary, secs)
            }

            else -> {
                // Should never reach here (we already stopSelf), but guard anyway.
                title = getString(R.string.app_name)
                text = ""
            }
        }
        return Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification_keep_alive)
            .setOngoing(true)
            .build()
    }
}

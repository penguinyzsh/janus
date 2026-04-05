package org.pysh.janus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import org.pysh.janus.R
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.RootUtils
import java.util.concurrent.Executors

class ScreenKeepAliveService : Service() {
    companion object {
        const val CHANNEL_ID = "janus_keep_alive"
        const val EXTRA_INTERVAL = "interval_seconds"
        const val DEFAULT_INTERVAL = 10
        private const val NOTIFICATION_ID = 1

        var isRunning = false
            private set

        fun start(
            context: Context,
            intervalSeconds: Int = DEFAULT_INTERVAL,
        ) {
            val intent =
                Intent(context, ScreenKeepAliveService::class.java)
                    .putExtra(EXTRA_INTERVAL, intervalSeconds)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(
                Intent(context, ScreenKeepAliveService::class.java),
            )
        }
    }

    private var intervalMs = DEFAULT_INTERVAL * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val wakeupRunnable =
        object : Runnable {
            override fun run() {
                executor.execute {
                    RootUtils.exec("input -d 1 keyevent 224")
                }
                handler.postDelayed(this, intervalMs)
            }
        }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val seconds =
            intent?.getIntExtra(EXTRA_INTERVAL, DEFAULT_INTERVAL)
                ?: WhitelistManager(this).getKeepAliveInterval()
        intervalMs = seconds * 1000L

        handler.removeCallbacks(wakeupRunnable)
        startForeground(NOTIFICATION_ID, buildNotification(seconds))
        handler.post(wakeupRunnable)

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(wakeupRunnable)
        executor.shutdownNow()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.keep_alive),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.keep_alive_notification)
            }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(intervalSeconds: Int): Notification =
        Notification
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.keep_alive))
            .setContentText(getString(R.string.keep_alive_notification_text, intervalSeconds))
            .setSmallIcon(R.drawable.ic_notification_keep_alive)
            .setOngoing(true)
            .build()
}

package org.pysh.janus.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.pysh.janus.data.WallpaperManager
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.util.JanusPaths

class WallpaperRotationReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ROTATE_WALLPAPER = "org.pysh.janus.ACTION_ROTATE_WALLPAPER"
        private const val REQUEST_CODE = 42

        fun scheduleNext(
            context: Context,
            intervalSeconds: Int,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent =
                Intent(context, WallpaperRotationReceiver::class.java).apply {
                    action = ACTION_ROTATE_WALLPAPER
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val triggerTime = SystemClock.elapsedRealtime() + intervalSeconds * 1000L

            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent,
                )
            } catch (e: SecurityException) {
                // For Android 12+ ifSCHEDULE_EXACT_ALARM is lacking or revoked
                // fall back to inexact or just let it drop if absolutely no perm
                // though we declared the formal permission in Manifest.
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pendingIntent,
                )
            }
        }

        fun cancelAll(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent =
                Intent(context, WallpaperRotationReceiver::class.java).apply {
                    action = ACTION_ROTATE_WALLPAPER
                }
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ROTATE_WALLPAPER) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleRotation(context)
            } catch (e: Exception) {
                Log.e("WallpaperRotation", "Failed to rotate: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleRotation(context: Context) {
        val whitelistManager = WhitelistManager(context)
        val wallpaperManager = WallpaperManager(context)

        if (!whitelistManager.isAutoRotateEnabled()) return

        val wallpapers = wallpaperManager.getWallpapers()
        if (wallpapers.size <= 1) return

        val currentIndex = wallpapers.indexOfFirst { it.isApplied }
        val nextIndex = if (currentIndex == -1) 0 else (currentIndex + 1) % wallpapers.size
        val nextWp = wallpapers[nextIndex]

        val destPath = "${JanusPaths.WALLPAPER_DIR}/wp_${nextWp.id}.mrc"
        val destFile = java.io.File(destPath)

        if (!destFile.exists()) {
            val videoUri = android.net.Uri.fromFile(java.io.File(nextWp.videoPath))
            org.pysh.janus.util.WallpaperUtils.applyDynamicWallpaper(
                context,
                videoUri,
                whitelistManager.isWallpaperLoop(),
                nextWp.id,
            )
        }

        whitelistManager.setActiveWallpaperPath(destPath)
        wallpaperManager.markApplied(nextWp.id)

        // Kill back screen to force visual reload!
        org.pysh.janus.util.RootUtils
            .restartBackScreen()

        // Schedule next rotation immediately after finishing this one!
        val interval = whitelistManager.getAutoRotateInterval().coerceAtLeast(1)
        scheduleNext(context, interval)

        // Notify UI to refresh!
        val intent = Intent("org.pysh.janus.ACTION_WALLPAPER_CHANGED")
        intent.setPackage(context.packageName)
        context.sendBroadcast(intent)
    }
}

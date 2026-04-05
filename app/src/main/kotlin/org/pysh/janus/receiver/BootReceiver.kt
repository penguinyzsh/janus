package org.pysh.janus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.pysh.janus.data.WhitelistManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val wm = WhitelistManager(context)
            if (wm.isAutoRotateEnabled()) {
                WallpaperRotationReceiver.scheduleNext(context, wm.getAutoRotateInterval().coerceAtLeast(1))
            }
        }
    }
}

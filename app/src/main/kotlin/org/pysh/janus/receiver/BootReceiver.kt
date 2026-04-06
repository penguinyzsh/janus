package org.pysh.janus.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.pysh.janus.data.WhitelistManager
import org.pysh.janus.service.JanusBackgroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val wm = WhitelistManager(context)
            if (wm.isAutoRotateEnabled()) {
                // Start the foreground rotation service. Previously scheduled an
                // AlarmManager exact alarm, but that was silently dropped on MIUI
                // when the user killed the task (broadcasts to "stopped" apps
                // are filtered). FGS keeps the process alive so Handler-based
                // rotation works reliably.
                JanusBackgroundService.startRotation(context)
            }
        }
    }
}

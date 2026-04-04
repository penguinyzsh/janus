package org.pysh.janus.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.service.media.MediaBrowserService

data class MediaAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isMediaApp: Boolean,
)

class MediaAppScanner(private val context: Context) {

    companion object {
        private val BUILTIN_WHITELIST = setOf(
            "com.xiaomi.music",
            "com.miui.player",
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.luna.music",
            "com.kugou.android",
            "cn.kuwo.player",
            "com.spotify.music",
        )
    }

    fun scanMediaApps(): List<MediaAppInfo> {
        val pm = context.packageManager
        val candidates = mutableSetOf<String>()

        val browserIntent = Intent(MediaBrowserService.SERVICE_INTERFACE)
        pm.queryIntentServices(browserIntent, 0).forEach { resolveInfo ->
            candidates.add(resolveInfo.serviceInfo.packageName)
        }

        candidates.addAll(BUILTIN_WHITELIST)
        candidates.remove(context.packageName)

        return candidates.mapNotNull { pkg ->
            toMediaAppInfo(pm, pkg, isMediaApp = true)
        }.sortedBy { it.appName }
    }

    fun scanAllApps(): List<MediaAppInfo> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val seen = mutableSetOf<String>()
        return pm.queryIntentActivities(launcherIntent, 0).mapNotNull { resolveInfo ->
            val pkg = resolveInfo.activityInfo.packageName
            if (pkg == context.packageName || !seen.add(pkg)) null
            else toMediaAppInfo(pm, pkg, isMediaApp = false)
        }.sortedBy { it.appName }
    }

    private fun toMediaAppInfo(
        pm: PackageManager,
        pkg: String,
        isMediaApp: Boolean,
    ): MediaAppInfo? {
        return try {
            val appInfo = pm.getApplicationInfo(pkg, 0)
            MediaAppInfo(
                packageName = pkg,
                appName = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo),
                isMediaApp = isMediaApp,
            )
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }
}

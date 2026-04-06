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

class MediaAppScanner(
    private val context: Context,
) {
    /**
     * Scan installed packages for anything that declares [MediaBrowserService].
     * No hardcoded fallback list — if an app doesn't register the service we
     * simply won't tag it as media. This mirrors the system's own criterion
     * and avoids lying to the user about apps Janus can't actually detect.
     */
    fun scanMediaApps(): List<MediaAppInfo> {
        val pm = context.packageManager
        val browserIntent = Intent(MediaBrowserService.SERVICE_INTERFACE)
        return pm
            .queryIntentServices(browserIntent, 0)
            .mapTo(mutableSetOf()) { it.serviceInfo.packageName }
            .apply { remove(context.packageName) }
            .mapNotNull { pkg -> toMediaAppInfo(pm, pkg, isMediaApp = true) }
            .sortedBy { it.appName }
    }

    fun scanAllApps(): List<MediaAppInfo> {
        val pm = context.packageManager
        val launcherIntent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
        val seen = mutableSetOf<String>()
        return pm
            .queryIntentActivities(launcherIntent, 0)
            .mapNotNull { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                if (pkg == context.packageName || !seen.add(pkg)) {
                    null
                } else {
                    toMediaAppInfo(pm, pkg, isMediaApp = false)
                }
            }.sortedBy { it.appName }
    }

    private fun toMediaAppInfo(
        pm: PackageManager,
        pkg: String,
        isMediaApp: Boolean,
    ): MediaAppInfo? =
        try {
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

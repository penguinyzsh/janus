package org.pysh.janus.hook

import android.content.Context
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File

/**
 * Patches p2.a.d for non-music system cards that have custom template overrides.
 * Deploys custom ZIPs from janus/cards/ to janus/templates/ on manager init.
 *
 * Music is handled separately by [MusicTemplatePatch] (which has lyric-specific hooks).
 */
object SystemCardPatch {

    private const val TAG = "Janus-SystemCardPatch"

    // Non-music system card business names (must match p2.a.d keys)
    private val SYSTEM_CARDS = listOf(
        "incall", "alarm", "countdown", "carHailing", "foodDelivery",
        "xiaomiev", "privacy", "stock", "mihomeCamera",
    )

    private const val CARDS_DIR =
        "/data/system/theme_magic/users/0/subscreencenter/janus/cards"
    private const val TEMPLATE_BASE =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/templates"

    @Volatile
    private var templatePatched = false

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val overrides = detectOverrides()
        if (overrides.isEmpty()) {
            XposedBridge.log("[$TAG] No system card overrides found, skipping")
            return
        }

        patchPaths(lpparam, overrides)
        hookManagerInit(lpparam, overrides)

        XposedBridge.log("[$TAG] Hooks installed for ${overrides.size} card(s): ${overrides.keys}")
        HookStatusReporter.report("system_card_patch", true, "${overrides.size} cards")
    }

    /**
     * Scan janus/cards/ for {business}_custom.zip files.
     * Returns map of business name → custom template name.
     */
    private fun detectOverrides(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (biz in SYSTEM_CARDS) {
            val src = File("$CARDS_DIR/${biz}_custom.zip")
            if (src.exists()) {
                result[biz] = "${biz}_custom"
            }
        }
        return result
    }

    /**
     * Redirect p2.a.d entries to custom template paths for overridden system cards.
     * Must run AFTER MusicTemplatePatch.patchMusicPath() to avoid overwriting its changes.
     */
    private fun patchPaths(lpparam: XC_LoadPackage.LoadPackageParam, overrides: Map<String, String>) {
        try {
            val userId = Process.myUid() / 100_000
            val cls = XposedHelpers.findClass("p2.a", lpparam.classLoader)
            @Suppress("UNCHECKED_CAST")
            val origD = XposedHelpers.getStaticObjectField(cls, "d") as Map<String, String>
            val newD = HashMap(origD)
            for ((biz, templateName) in overrides) {
                val path = TEMPLATE_BASE.replace("\$user_id", userId.toString()) + "/$templateName"
                newD[biz] = path
                XposedBridge.log("[$TAG] Patched p2.a.d $biz -> $path")
            }
            XposedHelpers.setStaticObjectField(cls, "d", newD)
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchPaths: ${e.message}")
        }
    }

    /**
     * Hook Z1.d0.l(Context) to deploy custom template ZIPs on manager init.
     */
    private fun hookManagerInit(lpparam: XC_LoadPackage.LoadPackageParam, overrides: Map<String, String>) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "l", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (templatePatched) return
                        templatePatched = true
                        deployOverrides(overrides)
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookManagerInit: ${e.message}")
        }
    }

    private fun deployOverrides(overrides: Map<String, String>) {
        val userId = Process.myUid() / 100_000
        for ((biz, templateName) in overrides) {
            try {
                val src = File("$CARDS_DIR/${biz}_custom.zip")
                if (!src.exists()) {
                    XposedBridge.log("[$TAG] Deploy skipped: ${src.absolutePath} not found")
                    continue
                }
                val destPath = TEMPLATE_BASE.replace("\$user_id", userId.toString()) + "/$templateName"
                val dest = File(destPath)
                if (dest.isDirectory) dest.deleteRecursively()
                if (dest.isFile) dest.delete()
                dest.parentFile?.mkdirs()
                src.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.setReadable(true, false)
                dest.parentFile?.let {
                    it.setReadable(true, false)
                    it.setExecutable(true, false)
                }
                XposedBridge.log("[$TAG] Deployed $biz -> $destPath (${dest.length()} bytes)")
            } catch (e: Throwable) {
                XposedBridge.log("[$TAG] Deploy $biz failed: ${e.message}")
            }
        }
    }
}

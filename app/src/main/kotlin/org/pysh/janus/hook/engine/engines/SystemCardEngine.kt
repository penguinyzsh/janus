package org.pysh.janus.hook.engine.engines

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.json.JSONObject
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.ReflectUtils
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hook.engine.HookRule
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Engine plugin for patching non-music system card templates.
 *
 * Extracted from SystemCardPatch.kt.
 * Detects custom overrides in janus/cards/ and patches p2.a.d to redirect
 * template paths, then deploys custom ZIPs on manager init.
 *
 * Music is handled separately by [MusicTemplateEngine] (which has lyric-specific hooks).
 *
 * Required targets in JSON rule:
 * - `business_path_map_class` (e.g. "p2.a")
 * - `business_path_map_field` (e.g. "d")
 * - `manager_class` (e.g. "Z1.d0")
 * - `manager_init_method` (e.g. "l")
 */
class SystemCardEngine : HookEnginePlugin {

    companion object {
        const val ENGINE_NAME = "system_card"
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
    }

    private val templatePatched = AtomicBoolean(false)

    override fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: SharedPreferences,
    ) {
        val targets = rule.targets!!
        val overrides = detectOverrides()
        if (overrides.isEmpty()) {
            Log.d(TAG, "No system card overrides found, skipping")
            HookStatusReporter.reportSkip("system_card_patch", "no overrides")
            return
        }

        patchPaths(classLoader, targets, overrides)
        hookManagerInit(module, classLoader, targets, overrides)

        Log.d(TAG, "Hooks installed for ${overrides.size} card(s): ${overrides.keys}")
        HookStatusReporter.report("system_card_patch", true, "${overrides.size} cards")
    }

    /**
     * Scan janus/cards/ for {business}_custom.zip files.
     * Returns map of business name -> custom template name.
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
     * Redirect business path map entries to custom template paths for overridden system cards.
     * Must run AFTER MusicTemplateEngine.patchMusicPath() to avoid overwriting its changes.
     */
    private fun patchPaths(
        classLoader: ClassLoader,
        targets: Map<String, String>,
        overrides: Map<String, String>,
    ) {
        try {
            val userId = Process.myUid() / 100_000
            val cls = classLoader.loadClass(targets["business_path_map_class"]!!)
            val fieldName = targets["business_path_map_field"]!!
            @Suppress("UNCHECKED_CAST")
            val origD = ReflectUtils.getStaticField(cls, fieldName) as Map<String, String>
            val newD = HashMap(origD)
            for ((biz, templateName) in overrides) {
                val path = TEMPLATE_BASE.replace("\$user_id", userId.toString()) + "/$templateName"
                newD[biz] = path
                Log.d(TAG, "Patched ${targets["business_path_map_class"]}.$fieldName $biz -> $path")
            }
            ReflectUtils.setStaticField(cls, fieldName, newD)
        } catch (e: Throwable) {
            Log.e(TAG, "patchPaths: ${e.message}")
        }
    }

    /**
     * Hook manager init method to deploy custom template ZIPs.
     */
    private fun hookManagerInit(
        module: XposedInterface,
        classLoader: ClassLoader,
        targets: Map<String, String>,
        overrides: Map<String, String>,
    ) {
        try {
            val mgrCls = classLoader.loadClass(targets["manager_class"]!!)
            val method = mgrCls.getDeclaredMethod(targets["manager_init_method"]!!, Context::class.java)
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (templatePatched.compareAndSet(false, true)) {
                    deployOverrides(overrides)
                    HookStatusReporter.reportBehavior("system_card_deploy", JSONObject().apply {
                        put("action", "deploy_templates")
                        put("deployed_count", overrides.size)
                    })
                }
                result
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookManagerInit: ${e.message}")
        }
    }

    private fun deployOverrides(overrides: Map<String, String>) {
        val userId = Process.myUid() / 100_000
        for ((biz, templateName) in overrides) {
            try {
                val src = File("$CARDS_DIR/${biz}_custom.zip")
                if (!src.exists()) {
                    Log.d(TAG, "Deploy skipped: ${src.absolutePath} not found")
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
                Log.d(TAG, "Deployed $biz -> $destPath (${dest.length()} bytes)")
            } catch (e: Throwable) {
                Log.e(TAG, "Deploy $biz failed: ${e.message}")
            }
        }
    }
}

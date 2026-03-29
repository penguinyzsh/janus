package org.pysh.janus.hook

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Generalised card injection hook for the Smart Assistant panel.
 *
 * Replaces the single-card [WeatherCardHook] with a multi-card system
 * that pre-registers 20 business slots (`weather` + `janus_card_1`…`janus_card_19`).
 * Active cards and their settings are read from the JSON flag file
 * written by [org.pysh.janus.data.CardManager].
 */
object CardHook {

    private const val TAG = "Janus-Card"
    private const val JANUS_PKG = "org.pysh.janus"
    private const val MAX_SLOTS = 20
    private const val BASE_NOTIF_ID = 19990

    private const val CONFIG_PATH_DIR =
        "/data/system/theme_magic/users/0/subscreencenter/config"
    private const val CONFIG_PATH =
        "$CONFIG_PATH_DIR/janus_cards_config"
    private const val TEMPLATE_BASE =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/smart_assistant"

    private var manager: Any? = null
    private var mainHandler: Handler? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    private var initialized = false

    // ── Public entry ────────────────────────────────────────────

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val config = readConfig()
        if (config == null || !config.optBoolean("master_enabled", false)) {
            XposedBridge.log("[$TAG] Card system disabled or config absent, skipping")
            return
        }
        // Collect only active business names from config
        val activeBusinesses = mutableSetOf<String>()
        val cards = config.optJSONArray("cards")
        if (cards != null) {
            for (i in 0 until cards.length()) {
                activeBusinesses.add(cards.getJSONObject(i).getString("business"))
            }
        }
        if (activeBusinesses.isEmpty()) {
            XposedBridge.log("[$TAG] No active cards, skipping")
            return
        }
        patchConstants(lpparam, activeBusinesses)
        patchProtectedMap(lpparam)
        hookFilter(lpparam)
        hookAppList(lpparam, activeBusinesses)
        hookManagerInit(lpparam)
        XposedBridge.log("[$TAG] All hooks installed for ${activeBusinesses.size} card(s)")
    }

    // ── Hook methods ────────────────────────────────────────────

    private fun patchConstants(lpparam: XC_LoadPackage.LoadPackageParam, activeBusinesses: Set<String>) {
        try {
            val cls = XposedHelpers.findClass("p2.a", lpparam.classLoader)

            // p2.a.a  —  package → primary business
            @Suppress("UNCHECKED_CAST")
            val origA = XposedHelpers.getStaticObjectField(cls, "a") as Map<String, String>
            XposedHelpers.setStaticObjectField(cls, "a",
                HashMap(origA).apply { put(JANUS_PKG, "weather") })

            // p2.a.c  —  multi-business: only register active businesses
            @Suppress("UNCHECKED_CAST")
            val origC = XposedHelpers.getStaticObjectField(cls, "c") as Map<String, Set<String>>
            XposedHelpers.setStaticObjectField(cls, "c",
                HashMap(origC).apply { put(JANUS_PKG, activeBusinesses) })

            // p2.a.d  —  business → template path: only active businesses
            @Suppress("UNCHECKED_CAST")
            val origD = XposedHelpers.getStaticObjectField(cls, "d") as Map<String, String>
            XposedHelpers.setStaticObjectField(cls, "d",
                HashMap(origD).apply {
                    for (biz in activeBusinesses) {
                        put(biz, "$TEMPLATE_BASE/$biz")
                    }
                })

            XposedBridge.log("[$TAG] Constants patched: $activeBusinesses")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchConstants: ${e.message}")
        }
    }

    private fun patchProtectedMap(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass("p2.c", lpparam.classLoader)
            @Suppress("UNCHECKED_CAST")
            val map = XposedHelpers.getStaticObjectField(cls, "d") as HashMap<String, Any?>
            map[JANUS_PKG] = null // all businesses persistent
            XposedBridge.log("[$TAG] Protected map patched")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchProtectedMap: ${e.message}")
        }
    }

    private fun hookFilter(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "p2.c", lpparam.classLoader, "k",
                String::class.java, java.util.Set::class.java, java.util.Map::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (param.args[0] as? String == JANUS_PKG) param.result = true
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookFilter: ${e.message}")
        }
    }

    private fun hookAppList(lpparam: XC_LoadPackage.LoadPackageParam, activeBusinesses: Set<String>) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "o", Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        (XposedHelpers.getObjectField(param.thisObject, "r")
                                as ConcurrentHashMap.KeySetView<String, *>).add(JANUS_PKG)
                        val q = XposedHelpers.getObjectField(param.thisObject, "q")
                                as ConcurrentHashMap<String, Boolean>
                        q[JANUS_PKG] = true
                        // Gate 7: only enable active sub-businesses
                        for (biz in activeBusinesses) {
                            q["${JANUS_PKG}_$biz"] = true
                        }
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookAppList: ${e.message}")
        }
    }

    private fun hookManagerInit(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "l", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (initialized) return
                        initialized = true
                        manager = param.thisObject
                        mainHandler = XposedHelpers.getStaticObjectField(mgrCls, "E") as? Handler

                        val config = readConfig() ?: return
                        val cards = config.optJSONArray("cards") ?: return

                        for (i in 0 until cards.length()) {
                            val card = cards.getJSONObject(i)
                            val slot = card.getInt("slot")
                            val business = card.getString("business")
                            val refresh = card.optInt("refresh", 30)
                            val priority = card.optInt("priority", 100)

                            // Deploy template from app storage into subscreencenter's
                            // smart_assistant dir (inside this process, so we have write access).
                            deployTemplate(slot, business)

                            // Schedule injection
                            val refreshMs = refresh * 60 * 1000L
                            uiHandler.postDelayed({
                                injectCard(slot, business, priority)
                            }, 3_000L + slot * 500L)

                            val periodic = object : Runnable {
                                override fun run() {
                                    injectCard(slot, business, priority)
                                    uiHandler.postDelayed(this, refreshMs)
                                }
                            }
                            uiHandler.postDelayed(periodic, refreshMs)
                        }

                        XposedBridge.log("[$TAG] ${cards.length()} card(s) scheduled")
                    }
                },
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookManagerInit: ${e.message}")
        }
    }

    // ── Card injection ──────────────────────────────────────────

    private fun injectCard(slot: Int, business: String, priority: Int) {
        val mgr = manager ?: return
        val h = mainHandler ?: return
        try {
            val rearParam = JSONObject().apply {
                put("business", business)
                put("index", 0)
                put("priority", priority)
            }
            val bundle = Bundle().apply {
                putString("miui.rear.param", rearParam.toString())
            }
            val notifId = BASE_NOTIF_ID + slot
            val notifKey = "janus_$business"
            val runnableCls = XposedHelpers.findClass("Z1.m", mgr::class.java.classLoader)
            val mgrCls = XposedHelpers.findClass("Z1.d0", mgr::class.java.classLoader)
            val ctor = XposedHelpers.findConstructorExact(
                runnableCls, mgrCls,
                Int::class.javaPrimitiveType, String::class.java,
                String::class.java, Bundle::class.java,
            )
            h.post(ctor.newInstance(mgr, notifId, JANUS_PKG, notifKey, bundle) as Runnable)
            XposedBridge.log("[$TAG] Injected card slot=$slot biz=$business pri=$priority")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] injectCard slot=$slot: ${e.message}")
        }
    }

    // ── Template deployment (runs inside subscreencenter process) ──

    private fun deployTemplate(slot: Int, business: String) {
        try {
            val userId = Process.myUid() / 100_000
            val destPath = "$TEMPLATE_BASE/$business".replace("\$user_id", userId.toString())
            val destFile = File(destPath)

            // Source: theme_magic config/cards/ (deployed by app via root)
            val srcFile = File("$CONFIG_PATH_DIR/cards/$slot.zip")
            if (!srcFile.exists()) {
                XposedBridge.log("[$TAG] deployTemplate: source not found: ${srcFile.absolutePath}")
                return
            }

            // Remove old (could be file or directory from earlier bug)
            if (destFile.exists()) {
                if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
            }
            destFile.parentFile?.mkdirs()

            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile.setReadable(true, false)
            destFile.parentFile?.setReadable(true, false)
            destFile.parentFile?.setExecutable(true, false)
            XposedBridge.log("[$TAG] Template deployed: $destPath (${destFile.length()} bytes)")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] deployTemplate slot=$slot: ${e.message}")
        }
    }

    // ── Config reading ──────────────────────────────────────────

    private fun readConfig(): JSONObject? {
        return try {
            val file = File(CONFIG_PATH)
            if (!file.exists()) return null
            JSONObject(file.readText())
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] readConfig: ${e.message}")
            null
        }
    }

}

package org.pysh.janus.hook.engine.engines

import android.content.Context
import org.pysh.janus.hookapi.ConfigSource
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import io.github.libxposed.api.XposedInterface
import org.pysh.janus.hook.HookStatusReporter
import org.pysh.janus.hook.ReflectUtils
import org.pysh.janus.hook.engine.HookEnginePlugin
import org.pysh.janus.hookapi.HookRule
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Engine plugin for generalised card injection into the Smart Assistant panel.
 *
 * Extracted from CardHook.kt.
 * Pre-registers business slots, injects card notifications, and handles
 * priority sorting for custom Janus cards.
 *
 * Required targets in JSON rule:
 * - `constants_class` (e.g. "p2.a") — with fields a, c, d
 * - `constants_field_a` (e.g. "a") — package -> primary business
 * - `constants_field_c` (e.g. "c") — multi-business map
 * - `constants_field_d` (e.g. "d") — business -> template path
 * - `protected_map_class` (e.g. "p2.c") — protected map class
 * - `protected_map_field` (e.g. "d") — protected map field
 * - `filter_class` (e.g. "p2.c") — filter class
 * - `filter_method` (e.g. "k") — filter method
 * - `app_list_class` (e.g. "Z1.d0") — manager class for app list
 * - `app_list_method` (e.g. "o") — method to hook for app list injection
 * - `app_list_field_r` (e.g. "r") — KeySetView field
 * - `app_list_field_q` (e.g. "q") — ConcurrentHashMap<String, Boolean> field
 * - `sorting_class` (e.g. "Z1.d0") — class for card sorting
 * - `sorting_method` (e.g. "n") — method for card sorting
 * - `sorting_entry_class` (e.g. "Z1.c0") — card entry class
 * - `manager_class` (e.g. "Z1.d0") — manager class for init
 * - `manager_init_method` (e.g. "l") — init method name
 * - `manager_handler_field` (e.g. "E") — static Handler field
 * - `manager_list_field` (e.g. "a") — ArrayList<entry> field
 * - `entry_key_field` (e.g. "a") — entry key field (String)
 * - `entry_priority_field` (e.g. "d") — entry priority field (int)
 * - `card_runnable_class` (e.g. "Z1.m") — card injection Runnable class
 *
 * Optional targets (focus notice dynamic allow sub-feature):
 * - `business_resolver_class` / `business_resolver_method` (e.g. "p2.c" / "r") —
 *   resolves (pkg, parser) → U0.b representing the notification's declared business
 * - `u0b_business_field` (e.g. "c") — String field on the U0.b return value
 * - `rv_business_check_class` / `rv_business_check_method` (e.g. "p2.a" / "d") —
 *   static (pkg, biz) → Boolean, true if built-in RemoteView business
 * - `template_path_query_class` / `template_path_query_method` (e.g. "p2.c" / "i") —
 *   static (pkg, biz) → String, null/blank if no MAML template is registered
 * - `focus_notice_config_flag` (e.g. "focus_notice_dynamic_allow") — runtime toggle key
 *   read via ConfigSource; feature is OFF by default when the key is absent
 */
class CardInjectionEngine : HookEnginePlugin {

    companion object {
        const val ENGINE_NAME = "card_injection"
        private const val TAG = "Janus-Card"
        private const val JANUS_PKG = "org.pysh.janus"
        private const val BASE_NOTIF_ID = 19990

        private const val JANUS_BASE =
            "/data/system/theme_magic/users/0/subscreencenter/janus"
        private const val CONFIG_PATH = "$JANUS_BASE/config/cards_config"
        private const val TEMPLATE_BASE =
            "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/templates"
    }

    @Volatile private var manager: Any? = null
    @Volatile private var mainHandler: Handler? = null
    private val uiHandler = Handler(Looper.getMainLooper())
    @Volatile private var initialized = false

    // Cached target names (populated in install)
    private lateinit var targets: Map<String, String>

    override fun install(
        module: XposedInterface,
        rule: HookRule,
        classLoader: ClassLoader,
        config: ConfigSource,
    ) {
        targets = rule.targets!!

        // ── Always-on sub-features (independent of master_enabled) ──
        // hookManagerInit captures the Z1.d0 singleton into `manager`. Both custom
        // card injection AND focus-notice dynamic-allow need this reference, so
        // it must run even when cards_config is absent or disabled.
        hookManagerInit(module, classLoader)
        hookFocusNoticeBridge(module, classLoader, config)

        val cardConfig = readConfig()
        if (cardConfig == null || !cardConfig.optBoolean("master_enabled", false)) {
            Log.d(TAG, "Card system disabled or config absent, custom cards skipped (focus notice bridge still active)")
            HookStatusReporter.reportSkip("card_injection")
            return
        }

        // Collect only active business names from config
        val activeBusinesses = mutableSetOf<String>()
        val cards = cardConfig.optJSONArray("cards")
        if (cards != null) {
            for (i in 0 until cards.length()) {
                activeBusinesses.add(cards.getJSONObject(i).getString("business"))
            }
        }
        if (activeBusinesses.isEmpty()) {
            Log.d(TAG, "No active cards, custom cards skipped (focus notice bridge still active)")
            HookStatusReporter.reportSkip("card_injection")
            return
        }

        patchConstants(classLoader, activeBusinesses)
        patchProtectedMap(classLoader)
        hookFilter(module, classLoader)
        hookAppList(module, classLoader, activeBusinesses)
        hookCardSorting(module, classLoader)

        Log.d(TAG, "All hooks installed for ${activeBusinesses.size} card(s)")
        HookStatusReporter.report("card_injection", true, "${activeBusinesses.size} cards")
    }

    // ── Hook methods ────────────────────────────────────────────────

    private fun patchConstants(classLoader: ClassLoader, activeBusinesses: Set<String>) {
        try {
            val cls = classLoader.loadClass(targets["constants_class"]!!)
            val fieldA = targets["constants_field_a"]!!
            val fieldC = targets["constants_field_c"]!!
            val fieldD = targets["constants_field_d"]!!

            // field a — package -> primary business
            @Suppress("UNCHECKED_CAST")
            val origA = ReflectUtils.getStaticField(cls, fieldA) as Map<String, String>
            ReflectUtils.setStaticField(cls, fieldA,
                HashMap(origA).apply { put(JANUS_PKG, "weather") })

            // field c — multi-business: only register active businesses
            @Suppress("UNCHECKED_CAST")
            val origC = ReflectUtils.getStaticField(cls, fieldC) as Map<String, Set<String>>
            ReflectUtils.setStaticField(cls, fieldC,
                HashMap(origC).apply { put(JANUS_PKG, activeBusinesses) })

            // field d — business -> template path: only active businesses
            val userId = Process.myUid() / 100_000
            @Suppress("UNCHECKED_CAST")
            val origD = ReflectUtils.getStaticField(cls, fieldD) as Map<String, String>
            ReflectUtils.setStaticField(cls, fieldD,
                HashMap(origD).apply {
                    for (biz in activeBusinesses) {
                        val resolvedPath = TEMPLATE_BASE.replace("\$user_id", userId.toString()) + "/$biz"
                        put(biz, resolvedPath)
                    }
                })

            Log.d(TAG, "Constants patched: $activeBusinesses")
        } catch (e: Throwable) {
            Log.e(TAG, "patchConstants: ${e.message}")
        }
    }

    private fun patchProtectedMap(classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(targets["protected_map_class"]!!)
            @Suppress("UNCHECKED_CAST")
            val map = ReflectUtils.getStaticField(cls, targets["protected_map_field"]!!) as HashMap<String, Any?>
            map[JANUS_PKG] = null // all businesses persistent
            Log.d(TAG, "Protected map patched")
        } catch (e: Throwable) {
            Log.e(TAG, "patchProtectedMap: ${e.message}")
        }
    }

    private fun hookFilter(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val cls = classLoader.loadClass(targets["filter_class"]!!)
            val method = cls.getDeclaredMethod(
                targets["filter_method"]!!,
                String::class.java, java.util.Set::class.java, java.util.Map::class.java,
            )
            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val pkg = chain.args[0] as? String
                if (pkg == JANUS_PKG) {
                    HookStatusReporter.reportBehavior("card_filter", JSONObject().apply {
                        put("action", "filter_bypass")
                        put("package", pkg)
                    })
                    return@Hooker true
                }
                chain.proceed()
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookFilter: ${e.message}")
        }
    }

    private fun hookAppList(
        module: XposedInterface,
        classLoader: ClassLoader,
        activeBusinesses: Set<String>,
    ) {
        try {
            val mgrCls = classLoader.loadClass(targets["app_list_class"]!!)
            val method = mgrCls.getDeclaredMethod(targets["app_list_method"]!!, Boolean::class.javaPrimitiveType)
            val fieldR = targets["app_list_field_r"]!!
            val fieldQ = targets["app_list_field_q"]!!

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                @Suppress("UNCHECKED_CAST")
                (ReflectUtils.getField(chain.thisObject, fieldR)
                        as ConcurrentHashMap.KeySetView<String, *>).add(JANUS_PKG)
                @Suppress("UNCHECKED_CAST")
                val q = ReflectUtils.getField(chain.thisObject, fieldQ)
                        as ConcurrentHashMap<String, Boolean>
                q[JANUS_PKG] = true
                // Gate 7: only enable active sub-businesses
                for (biz in activeBusinesses) {
                    q["${JANUS_PKG}_$biz"] = true
                }
                HookStatusReporter.reportBehavior("card_app_list", JSONObject().apply {
                    put("action", "inject_app_list")
                    put("businesses", activeBusinesses.size)
                })
                result
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookAppList: ${e.message}")
        }
    }

    /**
     * Hook sorting method to re-sort Janus cards by priority after each insertion.
     * The original method only sorts after currentIndex, and appends without sorting
     * when currentIndex == -1 (initial state). This hook ensures our cards
     * are always ordered by priority descending regardless of currentIndex.
     */
    private fun hookCardSorting(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val mgrCls = classLoader.loadClass(targets["sorting_class"]!!)
            val entryCls = classLoader.loadClass(targets["sorting_entry_class"]!!)
            val method = mgrCls.getDeclaredMethod(targets["sorting_method"]!!, entryCls)

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                sortJanusCards()
                result
            })
            Log.d(TAG, "Card sorting hook installed")
        } catch (e: Throwable) {
            Log.e(TAG, "hookCardSorting: ${e.message}")
        }
    }

    private fun hookManagerInit(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val mgrCls = classLoader.loadClass(targets["manager_class"]!!)
            val method = mgrCls.getDeclaredMethod(targets["manager_init_method"]!!, Context::class.java)

            module.hook(method).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed()
                if (initialized) return@Hooker result
                initialized = true
                manager = chain.thisObject
                mainHandler = ReflectUtils.getStaticField(mgrCls, targets["manager_handler_field"]!!) as? Handler

                val cardConfig = readConfig() ?: return@Hooker result
                val cards = cardConfig.optJSONArray("cards") ?: return@Hooker result

                for (i in 0 until cards.length()) {
                    val card = cards.getJSONObject(i)
                    val slot = card.getInt("slot")
                    val business = card.getString("business")
                    val refresh = card.optInt("refresh", 30)
                    val priority = card.optInt("priority", 100)

                    // Deploy template from app storage into subscreencenter's
                    // smart_assistant dir (inside this process, so we have write access).
                    deployTemplate(slot, business)

                    // Schedule injection in config order (= priority order)
                    val refreshMs = refresh * 60 * 1000L
                    uiHandler.postDelayed({
                        injectCard(slot, business, priority, classLoader)
                    }, 3_000L + i * 500L)

                    val periodic = object : Runnable {
                        override fun run() {
                            injectCard(slot, business, priority, classLoader)
                            uiHandler.postDelayed(this, refreshMs)
                        }
                    }
                    uiHandler.postDelayed(periodic, refreshMs)
                }

                // Final sort after all injections complete
                val sortDelay = 3_000L + cards.length() * 500L + 500L
                uiHandler.postDelayed({ sortJanusCards() }, sortDelay)

                Log.d(TAG, "${cards.length()} card(s) scheduled")
                result
            })
        } catch (e: Throwable) {
            Log.e(TAG, "hookManagerInit: ${e.message}")
        }
    }

    /**
     * Focus notice dynamic allow — Janus's alternative to REAREye's Z1.m.run() hook.
     *
     * Hooks `p2.c.r(String pkg, Object parser)` after. When a notification carries
     * valid `miui.rear.param` / `miui.focus.param` extras, subscreencenter already
     * calls `r()` to resolve the declared business. We read the returned `U0.b.c`
     * field and, if a matching template exists (`p2.a.d` built-in RV or `p2.c.i`
     * template path — which covers SystemCardEngine-injected MAML), dynamically
     * write `(pkg, business)` into `Z1.d0.f1804r` / `f1803q` so the subsequent
     * `p2.c.k` filter gate passes.
     *
     * Key differentiators vs REAREye:
     * - 2 obfuscation layers (`U0.b.c` + `Z1.d0.r/q`), not 4.
     * - Hard template gate — refuses to allow a package whose business has no
     *   renderable template (avoids polluting the whitelist with dead entries).
     * - Runtime toggle via `ConfigSource` — no app restart required to flip.
     */
    private fun hookFocusNoticeBridge(
        module: XposedInterface,
        classLoader: ClassLoader,
        config: ConfigSource,
    ) {
        val resolverClassName = targets["business_resolver_class"] ?: return
        val resolverMethodName = targets["business_resolver_method"] ?: return
        val bizFieldName = targets["u0b_business_field"] ?: return
        val flagKey = targets["focus_notice_config_flag"] ?: "focus_notice_dynamic_allow"

        try {
            val resolverCls = classLoader.loadClass(resolverClassName)
            // The second parameter is an obfuscated type (e.g. `j2.C0308a` — the parsed
            // notification wrapper). We can't `getDeclaredMethod(name, String, X.class)`
            // without hardcoding that name, so discover the method by shape: declared on
            // p2.c, named `r`, 2 params, first param is String.
            val resolverMethod = resolverCls.declaredMethods.firstOrNull { m ->
                m.name == resolverMethodName &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == String::class.java
            } ?: run {
                Log.e(TAG, "hookFocusNoticeBridge: method $resolverClassName.$resolverMethodName(String, ?) not found")
                return
            }

            val rvCheck = runCatching {
                val cls = classLoader.loadClass(targets["rv_business_check_class"]!!)
                cls.getDeclaredMethod(
                    targets["rv_business_check_method"]!!,
                    String::class.java,
                    String::class.java,
                )
            }.getOrNull()

            val tplQuery = runCatching {
                val cls = classLoader.loadClass(targets["template_path_query_class"]!!)
                cls.getDeclaredMethod(
                    targets["template_path_query_method"]!!,
                    String::class.java,
                    String::class.java,
                )
            }.getOrNull()

            val fieldR = targets["app_list_field_r"] ?: return
            val fieldQ = targets["app_list_field_q"] ?: return

            module.hook(resolverMethod).intercept(XposedInterface.Hooker { chain ->
                val result = chain.proceed() ?: return@Hooker null

                // Runtime toggle — honor ConfigSource without reinstalling the hook.
                if (!config.getBoolean(flagKey, false)) return@Hooker result

                val pkg = chain.args[0] as? String ?: return@Hooker result
                val business = runCatching {
                    ReflectUtils.getField(result, bizFieldName) as? String
                }.getOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: return@Hooker result

                // Template gate: allow only when the business has something to render.
                // 1) p2.a.d(pkg, biz) == true → built-in RemoteView business
                // 2) p2.c.i(pkg, biz) non-blank → MAML template path (covers
                //    SystemCardEngine's injected templates as well)
                val isRv = rvCheck?.let {
                    runCatching { it.invoke(null, pkg, business) as? Boolean }.getOrNull()
                } == true
                val hasPath = tplQuery?.let {
                    runCatching { it.invoke(null, pkg, business) as? String }.getOrNull()
                }?.isNotBlank() == true

                if (!isRv && !hasPath) {
                    HookStatusReporter.reportBehavior("focus_notice", JSONObject().apply {
                        put("action", "skip_no_template")
                        put("package", pkg)
                        put("business", business)
                    })
                    return@Hooker result
                }

                if (isFocusNoticeDenied(pkg, business)) {
                    return@Hooker result
                }

                val mgr = manager ?: return@Hooker result
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val rSet = ReflectUtils.getField(mgr, fieldR)
                        as ConcurrentHashMap.KeySetView<String, *>
                    rSet.add(pkg)

                    @Suppress("UNCHECKED_CAST")
                    val qMap = ReflectUtils.getField(mgr, fieldQ)
                        as ConcurrentHashMap<String, Boolean>
                    qMap[pkg] = true
                    qMap["${pkg}_$business"] = true

                    HookStatusReporter.reportBehavior("focus_notice", JSONObject().apply {
                        put("action", "dynamic_allow")
                        put("package", pkg)
                        put("business", business)
                    })
                }.onFailure {
                    Log.w(TAG, "focus notice allow failed pkg=$pkg biz=$business: ${it.message}")
                }

                result
            })
            Log.d(TAG, "Focus notice bridge installed (toggle key=$flagKey)")
        } catch (e: Throwable) {
            Log.e(TAG, "hookFocusNoticeBridge: ${e.message}")
        }
    }

    /**
     * v1 stub: denylist disabled. A future revision may load a file at
     * `$CONFIG_DIR/focus_notice_denylist` (line-based `pkg` or `pkg:business`).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun isFocusNoticeDenied(pkg: String, business: String): Boolean = false

    /**
     * Sort all Janus cards in the manager list by priority ascending
     * (lower priority earlier in list = displayed below; higher = on top).
     */
    @Suppress("UNCHECKED_CAST")
    private fun sortJanusCards() {
        val mgr = manager ?: return
        val listFieldName = targets["manager_list_field"] ?: return
        val keyFieldName = targets["entry_key_field"] ?: return
        val priorityFieldName = targets["entry_priority_field"] ?: return

        val list = try {
            ReflectUtils.getField(mgr, listFieldName) as? ArrayList<Any>
        } catch (_: Throwable) { null } ?: return

        val janusIndices = mutableListOf<Int>()
        val janusEntries = mutableListOf<Any>()
        for (i in list.indices) {
            val entry = list[i]
            val key = try {
                ReflectUtils.getField(entry, keyFieldName) as? String
            } catch (_: Throwable) { null } ?: continue
            if (JANUS_PKG in key) {
                janusIndices.add(i)
                janusEntries.add(entry)
            }
        }
        if (janusEntries.size < 2) return

        janusEntries.sortBy { ReflectUtils.getIntField(it, priorityFieldName) }
        for (i in janusIndices.indices) {
            list[janusIndices[i]] = janusEntries[i]
        }
        Log.d(TAG, "Sorted ${janusEntries.size} Janus cards by priority")
    }

    // ── Card injection ──────────────────────────────────────────────

    private fun injectCard(slot: Int, business: String, priority: Int, classLoader: ClassLoader) {
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
            val runnableCls = classLoader.loadClass(targets["card_runnable_class"]!!)
            val mgrCls = classLoader.loadClass(targets["manager_class"]!!)
            val ctor = runnableCls.getDeclaredConstructor(
                mgrCls,
                Int::class.javaPrimitiveType, String::class.java,
                String::class.java, Bundle::class.java,
            )
            ctor.isAccessible = true
            h.post(ctor.newInstance(mgr, notifId, JANUS_PKG, notifKey, bundle) as Runnable)
            h.post { sortJanusCards() }
            Log.d(TAG, "Injected card slot=$slot biz=$business pri=$priority")
        } catch (e: Throwable) {
            Log.e(TAG, "injectCard slot=$slot: ${e.message}")
        }
    }

    // ── Template verification (runs inside subscreencenter process) ────
    // Prior to v260408 this copied from `janus/cards/<slot>.zip` to
    // `janus/templates/<business>`. The intermediate `cards/` staging dir has
    // been eliminated — the app deploys directly to `templates/` via root cp
    // in `CardManager.prepareCardsForHook`. This method now only verifies
    // the template file exists and is readable.

    private fun deployTemplate(slot: Int, business: String) {
        val userId = Process.myUid() / 100_000
        val destPath = "$TEMPLATE_BASE/$business".replace("\$user_id", userId.toString())
        val destFile = File(destPath)
        if (destFile.exists()) {
            destFile.setReadable(true, false)
            destFile.parentFile?.setReadable(true, false)
            destFile.parentFile?.setExecutable(true, false)
            Log.d(TAG, "Template deployed: $destPath (${destFile.length()} bytes)")
        } else {
            Log.w(TAG, "Template not found: $destPath — app may not have deployed yet")
        }
    }

    // ── Config reading ──────────────────────────────────────────────

    private fun readConfig(): JSONObject? {
        return try {
            val file = File(CONFIG_PATH)
            if (!file.exists()) return null
            JSONObject(file.readText())
        } catch (e: Throwable) {
            Log.e(TAG, "readConfig: ${e.message}")
            null
        }
    }
}

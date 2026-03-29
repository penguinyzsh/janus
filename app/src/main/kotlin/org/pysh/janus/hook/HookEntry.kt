package org.pysh.janus.hook

import android.annotation.SuppressLint
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "Janus"
        private const val TARGET_PACKAGE = "com.xiaomi.subscreencenter"
        private const val TARGET_CLASS = "p2.a"
        private const val SELF_PACKAGE = "org.pysh.janus"
        private const val PREFS_NAME = "janus_config"
        private const val APPLE_MUSIC_PACKAGE = "com.apple.android.music"
        private const val LUNA_MUSIC_PACKAGE = "com.luna.music"

        /** Package → hook for music apps needing lyric support.
         *  LyricInjector subclasses for apps without native BT lyrics,
         *  simple force-enable hooks for apps that have BT lyrics. */
        private val musicAppHooks: Map<String, (XC_LoadPackage.LoadPackageParam) -> Unit> = mapOf(
            APPLE_MUSIC_PACKAGE to { AppleMusicLyricHook.hook(it) },
            LUNA_MUSIC_PACKAGE to { LunaMusicLyricHook.hook(it) },
        )
        private const val WALLPAPER_LONG_PRESS_GESTURE = "Z1.t" // MainPanel long-press-to-edit handler
        private const val SUB_SCREEN_LAUNCHER = "$TARGET_PACKAGE.SubScreenLauncher"
        private const val JANUS_MRC = "/data/system/theme/rearScreenWhite/janus_custom.mrc"
        private const val CONFIG_DIR =
            "/data/system/theme_magic/users/0/subscreencenter/config"
        private const val WHITELIST_FLAG = "$CONFIG_DIR/janus_whitelist"
        private const val TRACKING_FLAG = "$CONFIG_DIR/janus_tracking_disabled"
        private const val WALLPAPER_KEEP_ALIVE_FLAG = "$CONFIG_DIR/janus_wallpaper_keep_alive"
        private const val WALLPAPER_LOCK_FLAG = "$CONFIG_DIR/janus_wallpaper_lock"
    }

    @Suppress("DEPRECATION")
    private val prefs by lazy { XSharedPreferences(SELF_PACKAGE, PREFS_NAME) }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            SELF_PACKAGE -> hookSelf(lpparam)
            TARGET_PACKAGE -> {
                hookMusicWhitelist(lpparam)
                hookTracking(lpparam)
                hookWallpaperKeepAlive(lpparam)
                hookWallpaperLock(lpparam)
                hookWallpaperPathRedirect(lpparam)
                MusicTemplatePatch.hook(lpparam)
                CardHook.hook(lpparam)
            }
            in musicAppHooks -> {
                musicAppHooks[lpparam.packageName]?.invoke(lpparam)
            }
        }
    }

    private fun hookSelf(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "$SELF_PACKAGE.MainActivity",
                lpparam.classLoader,
                "isModuleActive",
                XC_MethodReplacement.returnConstant(true)
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookSelf failed: ${e.message}")
        }
    }

    private fun hookMusicWhitelist(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val targetClass = XposedHelpers.findClass(TARGET_CLASS, lpparam.classLoader)

            // Hook c(String) -> boolean : single package whitelist check
            XposedHelpers.findAndHookMethod(
                targetClass, "c", String::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? Boolean ?: return
                        if (result) return

                        val packageName = param.args[0] as? String ?: return
                        val customWhitelist = getCustomWhitelist()
                        if (packageName in customWhitelist) {
                            param.result = true
                            XposedBridge.log("[$TAG] Allowed: $packageName")
                        }
                    }
                }
            )

            // Hook b() -> Set<String> : full whitelist retrieval
            XposedHelpers.findAndHookMethod(
                targetClass, "b",
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val customWhitelist = getCustomWhitelist()
                        if (customWhitelist.isEmpty()) return
                        val original = param.result
                        try {
                            (original as MutableCollection<String>).addAll(customWhitelist)
                        } catch (_: Throwable) {
                            val merged = HashSet<String>()
                            if (original is Collection<*>) {
                                @Suppress("UNCHECKED_CAST")
                                merged.addAll(original as Collection<String>)
                            }
                            merged.addAll(customWhitelist)
                            param.result = merged
                        }
                    }
                }
            )

            // Hook MusicController.addMusicPackageList to inject whitelist into
            // MediaSession monitoring. Without this, <MusicControl> MAML element
            // won't track third-party sessions (no lyrics, no progress bar).
            hookMusicControllerPackageList(lpparam)

            XposedBridge.log("[$TAG] Hooks installed successfully")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookMusicWhitelist failed: ${e.message}")
        }
    }

    private fun hookMusicControllerPackageList(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.miui.maml.elements.MusicController",
                lpparam.classLoader,
                "addMusicPackageList",
                java.util.List::class.java,
                object : XC_MethodHook() {
                    @Suppress("UNCHECKED_CAST")
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val list = param.args[0] as? MutableList<String> ?: return
                        val whitelist = getCustomWhitelist()
                        for (pkg in whitelist) {
                            if (pkg !in list) list.add(pkg)
                        }
                        if (whitelist.isNotEmpty()) {
                            XposedBridge.log("[$TAG] Injected ${whitelist.size} packages into MusicController")
                        }
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookMusicControllerPackageList failed: ${e.message}")
        }
    }

    private fun hookTracking(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val receiverClass = XposedHelpers.findClass(
                "$TARGET_PACKAGE.track.DailyTrackReceiver",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                receiverClass, "onReceive",
                android.content.Context::class.java,
                android.content.Intent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isTrackingDisabled()) {
                            param.result = null
                            XposedBridge.log("[$TAG] Blocked daily tracking report")
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Tracking hook installed")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookTracking failed: ${e.message}")
        }
    }

    @SuppressLint("BlockedPrivateApi") // Runs in Xposed hook context, not subject to targetSdk restrictions
    private fun hookWallpaperKeepAlive(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            // Hook SubScreenLauncher.onPause() to prevent MainPanel.A() → widget destruction.
            // onPause triggers: MainPanel.A() → widget cleanup → MamlView.onDestroy()
            // → ScreenElementRoot destroyed → animation restarts from 0 on resume.
            // By skipping onPause body, widgets stay alive and animation continues.
            XposedHelpers.findAndHookMethod(
                SUB_SCREEN_LAUNCHER,
                lpparam.classLoader,
                "onPause",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!isWallpaperKeepAlive()) return
                        try {
                            // Must satisfy Activity lifecycle check to avoid SuperNotCalledException.
                            // Activity.onPause() sets mCalled=true and calls dispatchActivityPaused().
                            val activityClass = android.app.Activity::class.java
                            val dispatchMethod = activityClass.getDeclaredMethod("dispatchActivityPaused")
                            dispatchMethod.isAccessible = true
                            dispatchMethod.invoke(param.thisObject)
                            val mCalledField = activityClass.getDeclaredField("mCalled")
                            mCalledField.isAccessible = true
                            mCalledField.setBoolean(param.thisObject, true)
                        } catch (e: Throwable) {
                            XposedBridge.log("[$TAG] super.onPause simulation failed: ${e.message}")
                            return // Let original onPause run to avoid crash
                        }
                        param.result = null // Skip SubScreenLauncher.onPause() body
                        XposedBridge.log("[$TAG] Blocked SubScreenLauncher.onPause → wallpaper kept alive")
                    }
                }
            )
            XposedBridge.log("[$TAG] Wallpaper keep-alive hook installed on SubScreenLauncher.onPause")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookWallpaperKeepAlive failed: ${e.message}")
        }
    }

    private fun hookWallpaperLock(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Hook Z1.t.e(MotionEvent) — long-press gesture to enter edit mode.
        // Blocking edit mode entry is sufficient: swipe-to-switch only works
        // inside edit mode, so it becomes unreachable.
        try {
            XposedHelpers.findAndHookMethod(
                WALLPAPER_LONG_PRESS_GESTURE, lpparam.classLoader,
                "e", android.view.MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (isWallpaperLocked()) {
                            param.result = false
                        }
                    }
                }
            )
            XposedBridge.log("[$TAG] Wallpaper lock hook installed on Z1.t.e")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookWallpaperLock failed: ${e.message}")
        }
    }

    /**
     * Hook Widget.d()（有效路径获取方法），重定向到 Janus 专用 .mrc。
     * 仅当 Janus 的 janus_custom.mrc 存在时才重定向，不影响原始壁纸。
     * 同时重定向 configPath 到 Janus editConfig（bgmode=2）。
     */
    private fun hookWallpaperPathRedirect(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "m2.a", lpparam.classLoader, "d",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (param.result == null) return
                        val janusFile = java.io.File(JANUS_MRC)
                        if (!janusFile.exists()) return

                        val result = param.result as String
                        if (result != JANUS_MRC) {
                            param.result = JANUS_MRC
                            XposedBridge.log("[$TAG] Redirected wallpaper: $result → $JANUS_MRC")
                        }
                        // 不修改 configPath（字段 g），避免被持久化到 widget.json
                    }
                }
            )
            XposedBridge.log("[$TAG] Wallpaper path redirect hook installed on m2.a.d")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookWallpaperPathRedirect failed: ${e.message}")
        }
    }

    private fun isWallpaperLocked(): Boolean {
        return java.io.File(WALLPAPER_LOCK_FLAG).exists()
    }

    private fun isWallpaperKeepAlive(): Boolean {
        return java.io.File(WALLPAPER_KEEP_ALIVE_FLAG).exists()
    }

    private fun isTrackingDisabled(): Boolean {
        return java.io.File(TRACKING_FLAG).exists()
    }

    private fun getCustomWhitelist(): Set<String> {
        return try {
            val file = java.io.File(WHITELIST_FLAG)
            if (!file.exists()) return emptySet()
            val raw = file.readText().trim()
            if (raw.isEmpty()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] Failed to read whitelist file: ${e.message}")
            emptySet()
        }
    }
}

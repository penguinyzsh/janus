package org.pysh.janus.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Luna Music (汽水音乐) lyric enabler.
 *
 * Forces the Bluetooth lyrics feature to always be active so that
 * Luna Music writes the current lyric line into MediaSession TITLE,
 * which subscreencenter displays on the rear screen.
 *
 * Two hooks:
 * 1. BlueToothLyricsManager singleton.c() (isOpen) → true
 * 2. RemoteControlContext.a(boolean) (setBlueToothLyricOpen) → force true
 */
object LunaMusicLyricHook {

    private const val TAG = "Janus-LunaMusic"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        forceManagerOpen(lpparam)
        forceRemoteControlFlag(lpparam)
    }

    /**
     * Force BlueToothLyricsManager.isOpen() → true.
     *
     * The singleton lives as a static field on BlueToothLyricsManager
     * implementing IBlueToothLyricsManager. We find it by interface type,
     * then hook its concrete c() method. Without this the manager won't
     * dispatch lyric content to listener callbacks.
     */
    private fun forceManagerOpen(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mgrCls = XposedHelpers.findClass(
                "com.luna.biz.playing.lyric.bluetoothlyrics.BlueToothLyricsManager",
                lpparam.classLoader
            )
            val singletonField = mgrCls.declaredFields.firstOrNull { field ->
                field.type.interfaces.any { it.name.contains("IBlueToothLyricsManager") }
            }
            if (singletonField != null) {
                singletonField.isAccessible = true
                val singleton = singletonField.get(null)
                if (singleton != null) {
                    XposedBridge.hookAllMethods(
                        singleton.javaClass, "c",
                        XC_MethodReplacement.returnConstant(true)
                    )
                    XposedBridge.log("[$TAG] Hooked ${singleton.javaClass.name}.c() → true")
                }
            }
            HookStatusReporter.report("luna_music_manager", true, "BlueToothLyricsManager")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] forceManagerOpen failed: ${e.message}")
            HookStatusReporter.report("luna_music_manager", false, e.message)
        }
    }

    /**
     * Force RemoteControlContext.a(boolean) to always receive true.
     * This makes the adapter replace MediaSession TITLE with lyrics.
     */
    private fun forceRemoteControlFlag(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            XposedHelpers.findAndHookMethod(
                "com.luna.biz.playing.player.remote.control.RemoteControlContext",
                lpparam.classLoader,
                "a",
                Boolean::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        param.args[0] = true
                    }
                }
            )
            XposedBridge.log("[$TAG] Force-enabled RemoteControlContext.a(boolean) → true")
            HookStatusReporter.report("luna_music_remote", true, "RemoteControlContext.a")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] forceRemoteControlFlag failed: ${e.message}")
            HookStatusReporter.report("luna_music_remote", false, e.message)
        }
    }
}

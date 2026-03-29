package org.pysh.janus.hook

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Apple Music lyric provider.
 *
 * Captures TTML lyrics from Apple Music's native TTMLParser and feeds
 * timed lines to [LyricInjector] for rear-screen display.
 */
object AppleMusicLyricHook : LyricInjector("Janus-AppleMusic") {

    override fun hookLyricSource(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass(
                "com.apple.android.music.ttml.javanative.TTMLParser\$TTMLParserNative",
                lpparam.classLoader
            )
            XposedHelpers.findAndHookMethod(
                cls, "songInfoFromTTML", String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val ttml = param.args[0] as? String ?: return
                        try {
                            val (lines, trans) = LyricParser.parseTtml(ttml)
                            setLyrics(lines, trans)
                        } catch (e: Throwable) {
                            XposedBridge.log("[Janus-AppleMusic] TTML parse failed: ${e.message}")
                        }
                    }
                }
            )
            HookStatusReporter.report("apple_music_lyric", true, "TTMLParserNative")
        } catch (e: Throwable) {
            XposedBridge.log("[Janus-AppleMusic] hookTtmlParser failed: ${e.message}")
            HookStatusReporter.report("apple_music_lyric", false, e.message)
        }
    }
}

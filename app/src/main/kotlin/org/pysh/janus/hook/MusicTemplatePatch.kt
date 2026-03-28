package org.pysh.janus.hook

import android.animation.ValueAnimator
import android.content.Context
import android.media.MediaMetadata
import android.os.Bundle
import android.os.Process
import android.view.animation.DecelerateInterpolator
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Patches the stock music MAML template and hooks the MAML rendering engine
 * to enable smooth, progress-synced lyric scrolling on the title text.
 *
 * Template patch: removes `textAlign` (fixes marquee lineCount>1 bug) and
 * `enableClip` from the title Text element. Stock `ellipsis="true"` is kept
 * and toggled at runtime.
 *
 * Runtime hooks:
 * - Captures the `album_name_text` TextScreenElement instance
 * - On metadata update, reads the calculated marquee speed from a custom
 *   metadata field and sets `mMarqueeSpeed` + `mEllipsis` on the element
 */
object MusicTemplatePatch {

    private const val TAG = "Janus-MusicTemplate"
    private const val TEMPLATE_ASSET = "smart_assistant/music"
    private const val TEMPLATE_PATH =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/smart_assistant/music"

    @Volatile
    private var titleElement: Any? = null
    private var fadeAnimator: ValueAnimator? = null
    private var templatePatched = false

    private const val LYRIC_FADE_FLAG =
        "/data/system/theme_magic/users/0/subscreencenter/config/janus_lyric_fade"
    private const val LYRIC_THRESHOLD_FLAG =
        "/data/system/theme_magic/users/0/subscreencenter/config/janus_lyric_threshold"

    // Auto-speed: track title changes to detect lyric lines
    private var lastTitle: String? = null
    private var lastTitleChangeMs: Long = 0

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookManagerInit(lpparam)
        hookTextElementInit(lpparam)
        hookMetadataUpdate(lpparam)
        XposedBridge.log("[$TAG] All hooks installed")
    }

    // ── Template deployment ───────────────────────────────────────────

    private fun hookManagerInit(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val mgrCls = XposedHelpers.findClass("Z1.d0", lpparam.classLoader)
            XposedHelpers.findAndHookMethod(mgrCls, "l", Context::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (templatePatched) return
                        templatePatched = true
                        patchTemplate(param.args[0] as Context)
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookManagerInit: ${e.message}")
        }
    }

    private fun patchTemplate(context: Context) {
        try {
            val userId = Process.myUid() / 100_000
            val deployPath = TEMPLATE_PATH.replace("\$user_id", userId.toString())

            val stockBytes = context.assets.open(TEMPLATE_ASSET).use { it.readBytes() }
            val entries = linkedMapOf<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(stockBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) entries[entry.name] = zis.readBytes()
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            val manifestBytes = entries["manifest.xml"] ?: return
            var manifest = String(manifestBytes, Charsets.UTF_8)

            // Remove textAlign and enableClip from normal-mode title.
            // textAlign causes StaticLayout to use element width → text wraps → marquee fails.
            // enableClip is unnecessary (marquee has its own clipRect).
            // Keep ellipsis="true" — toggled at runtime by hookMetadataUpdate.
            manifest = manifest.replace(
                """name="album_name_text" enableClip="'false'" textAlign="'left'" fontFamily""",
                """name="album_name_text" fontFamily"""
            )

            // AOD-mode title (album_name_text_aod) is NOT patched:
            // hookTextElementInit only captures the normal-mode element, so the AOD
            // element never receives runtime marquee control.  Removing its
            // textAlign="'center'" would unconditionally left-align the song title
            // on the AOD screen for all songs (including those without lyrics).

            entries["manifest.xml"] = manifest.toByteArray(Charsets.UTF_8)

            val deployFile = File(deployPath)
            if (deployFile.isDirectory) deployFile.deleteRecursively()
            if (deployFile.isFile) deployFile.delete()
            deployFile.parentFile?.mkdirs()
            ZipOutputStream(FileOutputStream(deployFile)).use { zos ->
                for ((name, data) in entries) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(data)
                    zos.closeEntry()
                }
            }
            deployFile.setReadable(true, false)
            deployFile.parentFile?.let { it.setReadable(true, false); it.setExecutable(true, false) }
            XposedBridge.log("[$TAG] Template deployed → $deployPath")
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] patchTemplate: ${e.message}")
        }
    }

    // ── Capture title TextScreenElement ────────────────────────────────

    private fun hookTextElementInit(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass(
                "com.miui.maml.elements.TextScreenElement", lpparam.classLoader
            )
            XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val name = XposedHelpers.getObjectField(param.thisObject, "mName") as? String
                    if (name == "album_name_text") {
                        titleElement = param.thisObject
                        XposedBridge.log("[$TAG] Captured album_name_text element")
                    }
                }
            })
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookTextElementInit: ${e.message}")
        }
    }

    // ── Dynamic marquee speed on metadata change ──────────────────────

    private fun hookMetadataUpdate(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass(
                "com.miui.maml.elements.MusicController", lpparam.classLoader
            )
            XposedBridge.hookAllMethods(cls, "onClientMetadataUpdate",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val metadata = param.args.getOrNull(1) as? MediaMetadata ?: return
                        val elem = titleElement ?: return
                        val bundle = try {
                            XposedHelpers.getObjectField(metadata, "mBundle") as? Bundle
                        } catch (_: Throwable) { null } ?: return

                        val title = bundle.getString(MediaMetadata.METADATA_KEY_TITLE)
                        if (title == null || title == lastTitle) return

                        val now = android.os.SystemClock.elapsedRealtime()
                        val elapsed = now - lastTitleChangeMs
                        lastTitle = title
                        lastTitleChangeMs = now

                        val explicitSpeed = bundle.getInt(LyricInjector.MARQUEE_SPEED_KEY, 0)
                        if (explicitSpeed > 0) {
                            // Explicit speed from LyricInjector (e.g. Apple Music)
                            applyMarquee(elem, explicitSpeed)
                        } else if (elapsed in 1..MamlConstants.readIntFlag(LYRIC_THRESHOLD_FLAG, 15000).toLong()) {
                            // Title changed rapidly → lyric mode, auto-calculate
                            val autoSpeed = calculateAutoSpeed(
                                title, elapsed.coerceIn(2000, 8000)
                            )
                            if (autoSpeed > 0) {
                                applyMarquee(elem, autoSpeed)
                            } else {
                                applyEllipsis(elem)
                            }
                        } else {
                            // New song or long gap → normal ellipsis
                            applyEllipsis(elem)
                        }

                        XposedHelpers.setObjectField(elem, "mTextLayout", null)
                        fadeIn(elem)
                    }
                }
            )
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] hookMetadataUpdate: ${e.message}")
        }
    }

    private fun applyMarquee(elem: Any, speed: Int) {
        XposedHelpers.setIntField(elem, "mMarqueeSpeed", speed)
        XposedHelpers.setBooleanField(elem, "mEllipsis", false)
    }

    private fun applyEllipsis(elem: Any) {
        XposedHelpers.setIntField(elem, "mMarqueeSpeed", 0)
        XposedHelpers.setBooleanField(elem, "mEllipsis", true)
    }

    /**
     * Calculate marquee speed for text that overflows the element width.
     * Same formula as [LyricInjector.calculateMarqueeSpeed].
     */
    private fun calculateAutoSpeed(text: String, durationMs: Long): Int {
        val textWidthSr = text.sumOf { ch ->
            if (ch.code > 0x7F) MamlConstants.FONT_SIZE_SR else MamlConstants.FONT_SIZE_SR * 0.55
        }
        val overflowSr = textWidthSr - MamlConstants.ELEM_WIDTH_SR
        if (overflowSr <= 0) return 0
        val totalScrollMaml = (overflowSr * MamlConstants.SR) + MamlConstants.MARQUEE_START_OFFSET
        val effectiveSec = (durationMs - 200).coerceAtLeast(200) / 1000.0
        return (totalScrollMaml / effectiveSec).toInt().coerceIn(1, 1000)
    }

    private fun fadeIn(elem: Any) {
        try {
            val alphaProp = XposedHelpers.getObjectField(elem, "mAlphaProperty") ?: return
            fadeAnimator?.cancel()
            val animator = ValueAnimator.ofInt(0, 255).apply {
                duration = MamlConstants.readIntFlag(LYRIC_FADE_FLAG, 700).toLong()
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    try {
                        XposedHelpers.callMethod(alphaProp, "setValue", (anim.animatedValue as Int).toDouble())
                    } catch (_: Throwable) { }
                }
            }
            fadeAnimator = animator
            animator.start()
        } catch (e: Throwable) {
            XposedBridge.log("[$TAG] fadeIn: ${e.message}")
        }
    }
}

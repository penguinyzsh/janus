package org.pysh.janus.data

import org.pysh.janus.util.JanusPaths
import org.pysh.janus.util.RootUtils

/**
 * Versioned storage migration.
 *
 * Each storage layout change bumps [LATEST_VERSION]. On app startup,
 * [migrateIfNeeded] runs all pending migration steps in order.
 *
 * | version | app version | layout |
 * |---------|-------------|--------|
 * | 0 (absent) | ≤1.0.x | config/janus_*, smart_assistant/, janus_custom.mrc |
 * | 1 | 1.1.x | janus/{config,cards,templates}/ |
 */
object JanusMigration {

    private const val LATEST_VERSION = 1
    private const val VERSION_FILE = "${JanusPaths.CONFIG_DIR}/storage_version"

    /** Run on app startup. Performs pending migrations, then writes current version. */
    fun migrateIfNeeded() {
        val current = readVersion()
        if (current >= LATEST_VERSION) return

        // Fresh install: janus/ doesn't exist yet, skip all migrations
        if (current == 0 && !RootUtils.exec("test -d '${JanusPaths.JANUS_BASE}'")) {
            writeVersion(LATEST_VERSION)
            return
        }

        if (current < 1) migrateV0toV1()
        // Future: if (current < 2) migrateV1toV2()

        writeVersion(LATEST_VERSION)
        // Restart subscreencenter so the new Hook loads with updated paths
        RootUtils.restartBackScreen()
    }

    private fun readVersion(): Int {
        val raw = RootUtils.execWithOutput("cat '$VERSION_FILE' 2>/dev/null")
        return raw?.trim()?.toIntOrNull() ?: 0
    }

    private fun writeVersion(version: Int) {
        JanusPaths.ensureAllDirs()
        RootUtils.exec("echo '$version' > '$VERSION_FILE' && chmod 644 '$VERSION_FILE' && chcon u:object_r:theme_data_file:s0 '$VERSION_FILE'")
    }

    // ── V0 → V1: scattered paths → janus/ directory ────────────

    private const val OLD_CONFIG_DIR =
        "/data/system/theme_magic/users/0/subscreencenter/config"
    private const val OLD_SMART_ASSISTANT =
        "/data/system/theme_magic/users/0/subscreencenter/smart_assistant"
    private const val OLD_JANUS_MRC =
        "/data/system/theme/rearScreenWhite/janus_custom.mrc"
    private val REAR_SCREEN_WHITE = JanusPaths.REAR_SCREEN_WHITE

    private val CONFIG_FLAGS = listOf(
        "janus_whitelist" to "whitelist",
        "janus_tracking_disabled" to "tracking_disabled",
        "janus_wallpaper_keep_alive" to "wallpaper_keep_alive",
        "janus_wallpaper_lock" to "wallpaper_lock",
        "janus_lyric_fade" to "lyric_fade",
        "janus_lyric_threshold" to "lyric_threshold",
        "janus_cards_config" to "cards_config",
    )

    private val OBSOLETE_CONFIG_FILES = listOf(
        "janus_weather_priority",
        "janus_weather_refresh",
    )

    private fun migrateV0toV1() {
        JanusPaths.ensureAllDirs()
        JanusPaths.ensureWallpaperDir()

        // Config flags
        for ((oldName, newName) in CONFIG_FLAGS) {
            moveFile("$OLD_CONFIG_DIR/$oldName", "${JanusPaths.CONFIG_DIR}/$newName")
        }
        for (name in OBSOLETE_CONFIG_FILES) {
            RootUtils.exec("rm -f '$OLD_CONFIG_DIR/$name'")
        }

        // Card ZIPs
        val cardFiles = RootUtils.execWithOutput("ls '$OLD_CONFIG_DIR/cards/' 2>/dev/null")
        if (!cardFiles.isNullOrBlank()) {
            for (name in cardFiles.lines().filter { it.isNotBlank() }) {
                moveFile("$OLD_CONFIG_DIR/cards/$name", "${JanusPaths.CARDS_DIR}/$name")
            }
            RootUtils.exec("rmdir '$OLD_CONFIG_DIR/cards' 2>/dev/null")
        }

        // Card templates in smart_assistant/ are ephemeral (Hook redeploys on each
        // startup). Delete old ones — the new Hook will deploy to janus/templates/
        // after the restart at the end of migration.
        RootUtils.exec("rm -f '$OLD_SMART_ASSISTANT/weather'")
        RootUtils.exec("rm -f '$OLD_SMART_ASSISTANT/janus_card_0'")
        for (i in 1..19) RootUtils.exec("rm -f '$OLD_SMART_ASSISTANT/janus_card_$i'")

        // Custom wallpaper
        moveFile(OLD_JANUS_MRC, JanusPaths.CUSTOM_MRC)

        // Backup files
        val bakList = RootUtils.execWithOutput("ls '$REAR_SCREEN_WHITE/'*.janus_bak 2>/dev/null")
        if (!bakList.isNullOrBlank()) {
            for (oldPath in bakList.lines().filter { it.isNotBlank() }) {
                val fileName = oldPath.substringAfterLast('/')
                    .removeSuffix(".janus_bak") + ".bak"
                moveFile(oldPath, "${JanusPaths.WALLPAPER_DIR}/$fileName")
            }
        }
    }

    /** Copy old → new, then delete old. Skip if old absent or new exists. */
    private fun moveFile(old: String, new: String) {
        if (!RootUtils.exec("test -e '$old'")) return
        if (RootUtils.exec("test -e '$new'")) {
            RootUtils.exec("rm -f '$old'")
            return
        }
        RootUtils.exec(
            "cp '$old' '$new' && chmod 644 '$new' && chcon u:object_r:theme_data_file:s0 '$new' && rm -f '$old'"
        )
    }
}

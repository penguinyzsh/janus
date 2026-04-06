package org.pysh.janus.data

import android.util.Log
import org.json.JSONArray
import org.pysh.janus.core.util.JanusPaths
import org.pysh.janus.core.util.RootUtils

/**
 * Versioned storage migration.
 *
 * Versions are monotonically increasing integers. Up through 1.3.x the
 * scheme used sequential integers (1, 2). From 2026-04-06 onward new
 * migrations are tagged with the release date in `yyMMdd` format
 * (e.g. 260406), which keeps integer ordering while making it obvious
 * at a glance when each migration shipped. Legacy sequential numbers
 * (1, 2, …) are always < any date-style number, so comparisons remain
 * trivially correct.
 *
 * ## Adding a new migration
 *
 * 1. Write a `private fun migrateToYyMMdd()` helper that is **idempotent**
 *    (safe to re-run). Prefer `rm -f` and `test -e` guards over bare mv/cp.
 * 2. Append a new [Migration] entry to [MIGRATIONS], using today's date
 *    as the `targetVersion` in `yyMMdd` form.
 * 3. Bump [CURRENT_VERSION] to match the new entry's `targetVersion`.
 *
 * That's the entire change — [migrateIfNeeded] is data-driven and picks
 * up the new entry automatically.
 *
 * ## History
 *
 * | version | shipped | change |
 * |---------|---------|--------|
 * | 1       | 1.1.x   | reorganize scattered paths into `janus/` directory |
 * | 260406  | v260406 | full storage overhaul: RemotePrefs single-source, cards 3→2, wallpaper pointer, .bak removal, orphan sweep |
 */
object JanusMigration {
    private const val TAG = "Janus-Migration"

    /** Latest schema version. Format: `yyMMdd` (or legacy sequential integer). */
    private const val CURRENT_VERSION = 260406

    private const val VERSION_FILE = "${JanusPaths.CONFIG_DIR}/storage_version"

    /**
     * Ordered migration history. Entries MUST be appended in ascending
     * `targetVersion` order. Each step runs when the stored version is
     * strictly less than its [Migration.targetVersion].
     */
    private val MIGRATIONS: List<Migration> =
        listOf(
            Migration(
                targetVersion = 1,
                description = "reorganize scattered paths into janus/ directory",
                run = ::migrateToV1,
            ),
            Migration(
                targetVersion = 260406,
                description = "full storage overhaul for v260406",
                run = ::migrateTo260406,
            ),
        )

    private data class Migration(
        val targetVersion: Int,
        val description: String,
        val run: () -> Unit,
    )

    /** Run on app startup. Applies pending migrations, then records the new version. */
    fun migrateIfNeeded() {
        val current = readVersion()
        if (current >= CURRENT_VERSION) return

        // Fresh install: janus/ doesn't exist yet — stamp current version and skip history
        if (current == 0 && !RootUtils.exec("test -d '${JanusPaths.JANUS_BASE}'")) {
            writeVersion(CURRENT_VERSION)
            return
        }

        var ran = 0
        for (m in MIGRATIONS) {
            if (current < m.targetVersion) {
                Log.i(TAG, "Running migration → ${m.targetVersion}: ${m.description}")
                runCatching { m.run() }
                    .onFailure { Log.e(TAG, "Migration ${m.targetVersion} failed: ${it.message}") }
                ran++
            }
        }

        writeVersion(CURRENT_VERSION)
        if (ran > 0) {
            Log.i(TAG, "$ran migration(s) applied: $current → $CURRENT_VERSION")
            // Restart subscreencenter so the new Hook loads with updated layout
            RootUtils.restartBackScreen()
        }
    }

    // ── Version I/O ─────────────────────────────────────────────

    private fun readVersion(): Int {
        val raw = RootUtils.execWithOutput("cat '$VERSION_FILE' 2>/dev/null")
        return raw?.trim()?.toIntOrNull() ?: 0
    }

    private fun writeVersion(version: Int) {
        JanusPaths.ensureAllDirs()
        RootUtils.exec(
            "echo '$version' > '$VERSION_FILE' && " +
                "chmod 644 '$VERSION_FILE' && " +
                "chcon u:object_r:theme_data_file:s0 '$VERSION_FILE'",
        )
    }

    // ── Migration → 1 : scattered paths → janus/ directory ─────

    private const val OLD_CONFIG_DIR =
        "/data/system/theme_magic/users/0/subscreencenter/config"
    private const val OLD_SMART_ASSISTANT =
        "/data/system/theme_magic/users/0/subscreencenter/smart_assistant"
    private const val OLD_JANUS_MRC =
        "/data/system/theme/rearScreenWhite/janus_custom.mrc"
    private val REAR_SCREEN_WHITE = JanusPaths.REAR_SCREEN_WHITE

    private val CONFIG_FLAGS =
        listOf(
            "janus_whitelist" to "whitelist",
            "janus_tracking_disabled" to "tracking_disabled",
            "janus_wallpaper_keep_alive" to "wallpaper_keep_alive",
            "janus_wallpaper_lock" to "wallpaper_lock",
            "janus_cards_config" to "cards_config",
        )

    private val OBSOLETE_CONFIG_FILES =
        listOf(
            "janus_weather_priority",
            "janus_weather_refresh",
        )

    private fun migrateToV1() {
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
                // Move directly to templates/ (janus/cards/ was an intermediate
                // staging dir that has been eliminated in v260408).
                moveFile("$OLD_CONFIG_DIR/cards/$name", "${JanusPaths.TEMPLATES_DIR}/$name")
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
                val fileName =
                    oldPath
                        .substringAfterLast('/')
                        .removeSuffix(".janus_bak") + ".bak"
                moveFile(oldPath, "${JanusPaths.WALLPAPER_DIR}/$fileName")
            }
        }
    }

    // ── Migration → 260406 : drop on-disk boolean flag files ───
    // Boolean feature flags (tracking_disabled, wallpaper_keep_alive, etc.)
    // are now sourced exclusively from LSPosed RemotePreferences. The matching
    // on-disk files under $CONFIG_DIR are obsolete — remove them so stale
    // values can't accidentally be read by anything else. The `whitelist`
    // file-backed mirror used to be read by WhitelistEngine.getCustomWhitelist;
    // that branch is gone too, so the file is no longer needed.

    // ── Migration → 260406 : full storage overhaul ────────────────
    //
    // Runs for any user upgrading from v1.x.x (storage_version 0 / 1 / 2)
    // to v260406. All operations are idempotent (rm -f / test -e guards),
    // so this single step safely handles any starting state.
    //
    // What it does:
    //  1. Drop on-disk boolean flag files (flags moved to LSPosed RemotePreferences)
    //  2. Sweep orphan wp_*.mrc + debug whitelist_test* files
    //  3. Move surviving wp_*.mrc from old rearScreenWhite/janus/ to janus/wallpapers/
    //  4. Convert custom.mrc → active_wallpaper pointer file
    //  5. Delete janus/cards/ (cards deployed directly to templates/ now)
    //  6. Delete all .bak files (backup mechanism removed)

    private val OBSOLETE_FLAG_FILES =
        listOf(
            "whitelist",
            "tracking_disabled",
            "wallpaper_keep_alive",
            "wallpaper_lock",
            "hide_time_tip",
            "focus_notice_dynamic_allow",
        )

    private const val WALLPAPERS_META_PATH =
        "/data/data/org.pysh.janus/files/wallpapers/wallpapers.json"

    private fun migrateTo260406() {
        JanusPaths.ensureAllDirs()
        val oldWpDir = JanusPaths.WALLPAPER_DIR // rearScreenWhite/janus/

        // ── (1) Drop obsolete on-disk boolean flag files ──
        for (name in OBSOLETE_FLAG_FILES) {
            RootUtils.exec("rm -f '${JanusPaths.CONFIG_DIR}/$name'")
        }
        RootUtils.exec("rm -f '$oldWpDir/active_wallpaper_path.txt'")
        RootUtils.exec("rm -f '${JanusPaths.CONFIG_DIR}/whitelist_test'*")

        // ── (2) Sweep orphan wp_*.mrc in OLD dir ──
        val knownIds = loadKnownWallpaperIds()
        val wpList = RootUtils.execWithOutput("ls '$oldWpDir' 2>/dev/null")
        if (wpList != null && knownIds != null) {
            val wpFiles = wpList.lines().map { it.trim() }
                .filter { it.startsWith("wp_") && it.endsWith(".mrc") }
            var removed = 0
            for (fileName in wpFiles) {
                val id = fileName.removePrefix("wp_").removeSuffix(".mrc")
                if (id !in knownIds) {
                    RootUtils.exec("rm -f '$oldWpDir/$fileName'")
                    removed++
                } else {
                    // ── (3) Move surviving wp_*.mrc to new janus/wallpapers/ ──
                    moveFile("$oldWpDir/$fileName", "${JanusPaths.WALLPAPERS_DIR}/$fileName")
                }
            }
            if (removed > 0) Log.i(TAG, "260406: removed $removed orphan wp_*.mrc files")
        }

        // ── (4) Convert custom.mrc → active_wallpaper pointer ──
        if (RootUtils.exec("test -f '${JanusPaths.CUSTOM_MRC}'")) {
            val appliedId = findAppliedWallpaperId()
            if (appliedId != null) {
                RootUtils.exec(
                    "printf '%s' '$appliedId' > '${JanusPaths.ACTIVE_WALLPAPER}' && " +
                        "chmod 644 '${JanusPaths.ACTIVE_WALLPAPER}' && " +
                        "chcon u:object_r:theme_data_file:s0 '${JanusPaths.ACTIVE_WALLPAPER}'",
                )
                Log.i(TAG, "260406: active_wallpaper pointer set to $appliedId")
            }
            RootUtils.exec("rm -f '${JanusPaths.CUSTOM_MRC}'")
        }

        // ── (5) Migrate janus/cards/ → janus/templates/ then delete ──
        // The old architecture had a "staging" dir janus/cards/ where the app
        // wrote card ZIPs, and the Hook's deployTemplate copied them to
        // janus/templates/ on startup. In edge cases (user imported a card
        // but subscreencenter never restarted), templates/ might be missing
        // the file. Move any remaining cards/ files to templates/ before
        // deleting the directory to avoid data loss.
        val cardsDir = "${JanusPaths.JANUS_BASE}/cards"
        val cardsList = RootUtils.execWithOutput("ls '$cardsDir' 2>/dev/null")
        if (cardsList != null) {
            for (fileName in cardsList.lines().map { it.trim() }.filter { it.isNotBlank() }) {
                // Custom cards: <slot>.zip → janus_card_<slot>
                // System overrides: <biz>_custom.zip → <biz>_custom
                val templateName = fileName.removeSuffix(".zip")
                val destName = if (templateName.all { it.isDigit() }) {
                    "janus_card_$templateName" // slot number → business name
                } else {
                    templateName // already has correct name (e.g., music_custom)
                }
                // Only move if templates/ doesn't already have it
                val dest = "${JanusPaths.TEMPLATES_DIR}/$destName"
                if (!RootUtils.exec("test -e '$dest'")) {
                    RootUtils.exec(
                        "cp '$cardsDir/$fileName' '$dest' && " +
                            "chmod 644 '$dest' && " +
                            "chcon u:object_r:theme_data_file:s0 '$dest'",
                    )
                    Log.i(TAG, "260406: migrated $fileName → $destName")
                }
            }
        }
        RootUtils.exec("rm -rf '$cardsDir'")

        // ── (6) Delete all .bak files ──
        RootUtils.exec("rm -f '$oldWpDir/'*.bak")
        RootUtils.exec("rm -f '${JanusPaths.REAR_SCREEN_WHITE}/'*.janus_bak")

        // Clean up old dir if now empty.
        RootUtils.exec("rmdir '$oldWpDir' 2>/dev/null || true")
    }

    /**
     * Read `wallpapers.json` and extract all `id` values.
     * Returns null if unreadable (caller should skip orphan sweep).
     */
    private fun loadKnownWallpaperIds(): Set<String>? {
        val raw = RootUtils.execWithOutput("cat '$WALLPAPERS_META_PATH' 2>/dev/null") ?: return null
        if (raw.isBlank()) return emptySet()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .mapNotNull { arr.optJSONObject(it)?.optString("id")?.takeIf { s -> s.isNotEmpty() } }
                .toSet()
        } catch (e: Exception) {
            Log.w(TAG, "wallpapers.json parse failed: ${e.message}")
            null
        }
    }

    /** Find the UUID of the wallpaper marked isApplied in wallpapers.json. */
    private fun findAppliedWallpaperId(): String? {
        val raw = RootUtils.execWithOutput("cat '$WALLPAPERS_META_PATH' 2>/dev/null") ?: return null
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .map { arr.getJSONObject(it) }
                .firstOrNull { it.optBoolean("isApplied", false) }
                ?.optString("id")
                ?.takeIf { it.isNotEmpty() }
        } catch (_: Exception) { null }
    }

    // ── Helpers ─────────────────────────────────────────────────

    /** Copy old → new, then delete old. Skip if old absent or new exists. */
    private fun moveFile(
        old: String,
        new: String,
    ) {
        if (!RootUtils.exec("test -e '$old'")) return
        if (RootUtils.exec("test -e '$new'")) {
            RootUtils.exec("rm -f '$old'")
            return
        }
        RootUtils.exec(
            "cp '$old' '$new' && chmod 644 '$new' && chcon u:object_r:theme_data_file:s0 '$new' && rm -f '$old'",
        )
    }
}

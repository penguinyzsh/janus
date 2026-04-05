package org.pysh.janus.util

/**
 * Centralised path constants for all Janus files stored in system directories.
 *
 * App-side (user_id hard-coded to 0) and Hook-side (uses `$user_id` placeholder
 * resolved at runtime via `Process.myUid() / 100_000`) share the same logical
 * structure under `janus/`.
 */
object JanusPaths {
    // ── Base directories ────────────────────────────────────────
    private const val SUBSCREENCENTER_BASE =
        "/data/system/theme_magic/users/0/subscreencenter"
    const val JANUS_BASE = "$SUBSCREENCENTER_BASE/janus"
    const val CONFIG_DIR = "$JANUS_BASE/config"
    const val CARDS_DIR = "$JANUS_BASE/cards"
    const val TEMPLATES_DIR = "$JANUS_BASE/templates"

    /** Hook-side template base — `$user_id` must be replaced at runtime. */
    const val HOOK_TEMPLATES_BASE =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/templates"

    // ── Config flag files (read by Hook, written by App via root) ──
    const val WHITELIST = "$CONFIG_DIR/whitelist"
    const val TRACKING_DISABLED = "$CONFIG_DIR/tracking_disabled"
    const val WALLPAPER_KEEP_ALIVE = "$CONFIG_DIR/wallpaper_keep_alive"
    const val WALLPAPER_LOCK = "$CONFIG_DIR/wallpaper_lock"
    const val LYRIC_FADE = "$CONFIG_DIR/lyric_fade"
    const val LYRIC_THRESHOLD = "$CONFIG_DIR/lyric_threshold"
    const val CARDS_CONFIG = "$CONFIG_DIR/cards_config"
    const val HIDE_TIME_TIP = "$CONFIG_DIR/hide_time_tip"

    // ── Wallpaper ───────────────────────────────────────────────
    const val REAR_SCREEN_WHITE = "/data/system/theme/rearScreenWhite"
    const val WALLPAPER_DIR = "$REAR_SCREEN_WHITE/janus"
    const val CUSTOM_MRC = "$WALLPAPER_DIR/custom.mrc"

    // ── Non-movable system paths (subscreencenter reads directly) ──
    const val RUNTIME_JSON =
        "/data/system/theme_magic/users/0/rearScreen/runtime.json"
    const val RUNTIME_DIR =
        "/data/system/theme_magic/users/0/rearScreen"

    /** Ensure the janus/ base and all subdirectories exist with correct permissions. */
    fun ensureAllDirs() {
        RootUtils.ensureDir(JANUS_BASE)
        RootUtils.ensureDir(CONFIG_DIR)
        RootUtils.ensureDir(CARDS_DIR)
        RootUtils.ensureDir(TEMPLATES_DIR)
    }

    /** Ensure the wallpaper janus/ directory exists. */
    fun ensureWallpaperDir() {
        RootUtils.ensureDir(WALLPAPER_DIR)
    }

    // ── Legacy paths (for cleanup of old versions) ──────────────
    private const val LEGACY_CONFIG_DIR = "$SUBSCREENCENTER_BASE/config"
    const val LEGACY_SMART_ASSISTANT = "$SUBSCREENCENTER_BASE/smart_assistant"
    const val LEGACY_JANUS_MRC = "$REAR_SCREEN_WHITE/janus_custom.mrc"

    /** Remove all legacy Janus files from old path layout. */
    fun cleanLegacyPaths(): Boolean {
        var ok = true
        ok = RootUtils.exec("rm -f '$LEGACY_CONFIG_DIR/janus_'*") && ok
        ok = RootUtils.exec("rm -rf '$LEGACY_CONFIG_DIR/cards'") && ok
        ok = RootUtils.exec("rm -f '$LEGACY_JANUS_MRC'") && ok
        ok = RootUtils.exec("rm -f '$REAR_SCREEN_WHITE/'*.janus_bak") && ok
        // Remove Janus-deployed templates from old smart_assistant location
        // NOTE: smart_assistant/music is NOT deleted — subscreencenter regenerates it on every
        // restart, and without the Hook it's the stock version (harmless)
        ok = RootUtils.exec("rm -f '$LEGACY_SMART_ASSISTANT/weather'") && ok
        for (i in 0..19) { // 0 = obsolete janus_card_0 naming
            RootUtils.exec("rm -f '$LEGACY_SMART_ASSISTANT/janus_card_$i'")
        }
        return ok
    }
}

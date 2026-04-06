package org.pysh.janus.core.util

/**
 * Centralised path constants for all Janus files stored in system directories.
 *
 * App-side (user_id hard-coded to 0) and Hook-side (uses `$user_id` placeholder
 * resolved at runtime via `Process.myUid() / 100_000`) share the same logical
 * structure under `janus/`.
 */
object JanusPaths {

    // ── Base directories ────────────────────────────────────────
    // User 0 is hardcoded because subscreencenter is a system app whose
    // rear-screen theme data always lives under users/0/ regardless of
    // Android multi-user ("手机分身") state. Hook-side paths use $user_id
    // placeholder as a defensive measure for the same logical directory.
    private const val SUBSCREENCENTER_BASE =
        "/data/system/theme_magic/users/0/subscreencenter"
    const val JANUS_BASE = "$SUBSCREENCENTER_BASE/janus"
    const val CONFIG_DIR = "$JANUS_BASE/config"
    const val TEMPLATES_DIR = "$JANUS_BASE/templates"
    const val THEMES_DIR = "$JANUS_BASE/themes"
    const val WALLPAPERS_DIR = "$JANUS_BASE/wallpapers"

    /** Hook-side template base — `$user_id` must be replaced at runtime. */
    const val HOOK_TEMPLATES_BASE =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/templates"

    /** Hook-side themes base — `$user_id` must be replaced at runtime. */
    const val HOOK_THEMES_BASE =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/themes"

    /** Hook-side config dir — `$user_id` must be replaced at runtime. */
    const val HOOK_CONFIG_DIR =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/config"

    /** Hook-side wallpapers base — `$user_id` must be replaced at runtime. */
    const val HOOK_WALLPAPERS_DIR =
        "/data/system/theme_magic/users/\$user_id/subscreencenter/janus/wallpapers"

    /** File name written inside each theme directory (the MRC payload). */
    const val THEME_FILE_NAME = "theme.mrc"

    // ── Config data files (read by Hook directly) ──────────────
    // Boolean feature flags live in LSPosed RemotePreferences ("janus_config"),
    // not on disk. Only non-flag data files that the Hook reads directly are
    // listed here.
    const val CARDS_CONFIG = "$CONFIG_DIR/cards_config"
    const val ACTIVE_THEME = "$CONFIG_DIR/active_theme"
    const val ACTIVE_WALLPAPER = "$CONFIG_DIR/active_wallpaper"

    // ── Non-movable system paths (subscreencenter reads directly) ──
    const val RUNTIME_JSON =
        "/data/system/theme_magic/users/0/rearScreen/runtime.json"
    const val RUNTIME_DIR =
        "/data/system/theme_magic/users/0/rearScreen"

    // ── Wallpaper (legacy, for migration / cleanup only) ──────
    const val REAR_SCREEN_WHITE = "/data/system/theme/rearScreenWhite"
    /** @deprecated Replaced by [WALLPAPERS_DIR] in v260408. Kept for migration/cleanup. */
    const val WALLPAPER_DIR = "$REAR_SCREEN_WHITE/janus"
    /** @deprecated Replaced by [ACTIVE_WALLPAPER] pointer in v260408. Kept for migration. */
    const val CUSTOM_MRC = "$WALLPAPER_DIR/custom.mrc"

    /** @deprecated Use [ensureAllDirs] instead; kept for v0→v1 migration compat. */
    fun ensureWallpaperDir() {
        RootUtils.ensureDir(WALLPAPER_DIR)
    }

    /** Ensure the janus/ base and all subdirectories exist with correct permissions. */
    fun ensureAllDirs() {
        RootUtils.ensureDir(JANUS_BASE)
        RootUtils.ensureDir(CONFIG_DIR)
        RootUtils.ensureDir(TEMPLATES_DIR)
        RootUtils.ensureDir(THEMES_DIR)
        RootUtils.ensureDir(WALLPAPERS_DIR)
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
        for (i in 0..19) {  // 0 = obsolete janus_card_0 naming
            RootUtils.exec("rm -f '$LEGACY_SMART_ASSISTANT/janus_card_$i'")
        }
        return ok
    }
}

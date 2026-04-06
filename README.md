<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="128" height="128" alt="Janus" />

# Janus

**Xiaomi Rear Screen Enhancement LSPosed Module**

**English** | [简体中文](README_CN.md)

[![Android](https://img.shields.io/badge/Android-15+-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/libxposed-API%20101-4285F4?style=flat-square)](https://github.com/libxposed/api)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-EA4335?style=flat-square&logo=gnu&logoColor=white)](LICENSE)

---

*Unlock the full potential of your Xiaomi rear screen*

</div>



## Introduction

Janus is an LSPosed module for Xiaomi phones with a rear screen, designed to enhance the rear screen experience. By hooking `com.xiaomi.subscreencenter`, it bypasses various system restrictions on the rear screen.

## Features

<table>
  <tr>
    <td><b>Music Whitelist Unlock</b></td>
    <td>Remove rear screen music app whitelist restrictions, manage whitelist per app</td>
  </tr>
  <tr>
    <td><b>Live Wallpaper Anti-Interrupt</b></td>
    <td>Prevent live wallpaper from pausing or resetting when rear screen is covered</td>
  </tr>
  <tr>
    <td><b>Lock Wallpaper Switching</b></td>
    <td>Block long-press gesture on rear screen wallpaper to prevent accidental changes</td>
  </tr>
  <tr>
    <td><b>Custom Rear Wallpaper Video</b></td>
    <td>Replace AI-generated wallpaper video with your own, with loop playback toggle and backup/restore</td>
  </tr>
  <tr>
    <td><b>Rear Screen DPI Adjustment</b></td>
    <td>Customize rear screen display density for better small-screen experience</td>
  </tr>
  <tr>
    <td><b>Rear Screen Keep Alive</b></td>
    <td>Foreground service periodically sends key events to prevent auto-sleep</td>
  </tr>
  <tr>
    <td><b>Screen Casting Settings</b></td>
    <td>Screen casting rotation control, keep rear screen on during casting</td>
  </tr>
  <tr>
    <td><b>Smart Assistant Custom Cards</b></td>
    <td>Import and manage custom MAML cards for the Smart Assistant panel, with drag-and-drop reordering, per-card enable/disable, priority and refresh interval settings</td>
  </tr>
  <tr>
    <td><b>Custom Music Card</b></td>
    <td>Replace the stock music card template with a custom MAML template, with optional lyric scrolling patch</td>
  </tr>
  <tr>
    <td><b>Lyric Hook Rules</b></td>
    <td>Per-app lyric hook rules via JSON rule engine. Import community-provided rules to display timed lyrics (TTML/LRC) from any music app on the rear screen</td>
  </tr>
  <tr>
    <td><b>Telemetry Blocking</b></td>
    <td>Intercept <code>DailyTrackReceiver</code> to block data reporting</td>
  </tr>
  <tr>
    <td><b>Quick Switch</b></td>
    <td>Quick settings tile for one-tap casting to rear screen</td>
  </tr>
  <tr>
    <td><b>Hide Launcher Icon</b></td>
    <td>Hide app icon from launcher, open via LSPosed module manager</td>
  </tr>
</table>

## Prerequisites

1. Device must have an unlocked Bootloader and Root access
2. Install [LSPosed](https://github.com/LSPosed/LSPosed/releases) framework
3. Enable Janus module in LSPosed, select scope `com.xiaomi.subscreencenter`
4. Open Janus app and configure features as needed
5. Restart the scope or reboot the device for hooks to take effect

> [!NOTE]
> Keep alive, DPI adjustment, task migration and other features require Root access.

## Scope

| App Name | Package Name |
|:--|:--|
| Rear Display | `com.xiaomi.subscreencenter` |

> [!TIP]
> Additional scopes (e.g. music apps for lyric hooks) are added dynamically via `requestScope()` when importing hook rules.

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (with ProGuard/R8 obfuscation + resource shrinking)
./gradlew assembleRelease

# Clean build artifacts
./gradlew clean
```

<details>
<summary><b>Build Environment</b></summary>

| Requirement | Version |
|:--|:--|
| JDK | 17+ |
| Android SDK | compileSdk 36 |
| Kotlin | 2.3.20 |
| Compose BOM | 2025.03.01 |

</details>

## Project Structure

Janus is split into four Gradle modules to keep the Xposed hook surface free
of UI/AndroidX dependencies and to make the rule contract independently
testable on pure JVM.

```
janus/
├── hook-api/           # Pure Kotlin (java-library) — engine contracts
│   └── org/pysh/janus/hookapi/
│       ├── HookRule.kt       # Rule / HookTarget / HookAction JSON model
│       ├── CardInfo.kt       # Card data contract shared by UI + hook
│       └── ConfigSource.kt   # Abstract K/V config (no SharedPreferences)
│
├── hook/               # Android library — runs inside the hooked host
│   ├── META-INF/xposed/      # module.prop / scope.list / java_init.list
│   │                         # (module.prop generated from version catalog)
│   ├── consumer-rules.pro    # R8 keeps for HookEntry + libxposed API
│   └── org/pysh/janus/hook/
│       ├── HookEntry.kt      # libxposed 101 XposedModule entry point
│       ├── engine/           # RuleEngine, RuleLoader, ActionExecutor
│       ├── engine/engines/   # Whitelist, SystemCard, CardInjection,
│       │                       WallpaperKeepAlive
│       └── config/           # SharedPreferencesConfigSource (RemotePrefs)
│
├── core/               # Android library — UI-side utilities
│   └── org/pysh/janus/core/util/  # DisplayUtils, JanusPaths, RootUtils...
│
└── app/                # Android application — UI / Service / Receiver
    └── org/pysh/janus/
        ├── JanusApplication.kt   # XposedService binding (manager side)
        ├── ui/                   # Compose + MIUIX pages
        ├── data/                 # WhitelistManager, CardManager, etc.
        ├── service/ receiver/ util/
        └── MainActivity.kt
```

Key properties:

- **Module version is a single source of truth** in `gradle/libs.versions.toml`
  (`moduleVersion` / `moduleVersionCode`). It reaches `META-INF/xposed/module.prop`
  via a generated Gradle task and the runtime boot log via `BuildConfig.MODULE_VERSION`.
- **`:hook` is the only module carrying `META-INF/xposed/`** — the `:app`
  APK consumes `:hook` as a library, so the hook classes and metadata are
  merged into the final APK while the Gradle dependency graph keeps
  Compose/MIUIX out of the hook classpath.
- **Engines depend on `ConfigSource`**, not `SharedPreferences`, so they can
  be unit-tested on pure JVM via `./gradlew :hook-api:test` without
  Robolectric.
- **libxposed API 101** (`io.github.libxposed:api`) is the only hook
  framework dependency; no EdXposed/legacy `de.robv.android.xposed` imports.

## Support

If you find this project helpful, consider supporting the development on [Afdian](https://ifdian.net/a/janus).

## Acknowledgements

> Janus uses content from the following open-source projects. Thanks to the developers of these projects.

- [**MIUIX** by YuKongA](https://github.com/compose-miuix-ui/miuix) — Xiaomi-style Compose UI component library
- [**LSPosed** by LSPosed](https://github.com/LSPosed/LSPosed) — Modern Xposed framework

## License

This project is licensed under the [GPL-3.0](LICENSE) license.

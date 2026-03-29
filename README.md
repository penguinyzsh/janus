<div align="center">

<img src="https://raw.githubusercontent.com/penguinyzsh/janus/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="160" height="160" style="display: block; margin: 0 auto;" alt="icon" />

# Janus

### Xiaomi Rear Screen Enhancement LSPosed Module

**English** | [简体中文](README_CN.md)

[![Android](https://img.shields.io/badge/Android-15+-green?logo=android)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/Xposed-API%2082-blue?logo=lsposed)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange?logo=gnu)](LICENSE)

</div>

## Introduction

Janus is an LSPosed module for Xiaomi phones with a rear screen, designed to enhance the rear screen experience. By hooking `com.xiaomi.subscreencenter`, it bypasses various system restrictions on the rear screen.

## Features

- **Music Whitelist Unlock** — Remove rear screen music app whitelist restrictions, manage whitelist per app
- **Live Wallpaper Anti-Interrupt** — Prevent live wallpaper from pausing or resetting when rear screen is covered, keep wallpaper playing continuously
- **Lock Wallpaper Switching** — Block long-press gesture on rear screen wallpaper to prevent accidental wallpaper changes
- **Custom Rear Wallpaper Video** — Replace AI-generated wallpaper video with your own, with loop playback toggle and backup/restore
- **Rear Screen DPI Adjustment** — Customize rear screen display density for better small-screen experience
- **Rear Screen Keep Alive** — Foreground service periodically sends key events to prevent auto-sleep
- **Screen Casting Settings** — Screen casting rotation control, keep rear screen on during casting
- **Smart Assistant Custom Cards** — Import and manage custom MAML cards for the Smart Assistant panel, with drag-and-drop reordering, per-card enable/disable, priority and refresh interval settings
- **Apple Music Rear Screen Lyrics** — Display timed lyrics from Apple Music on the rear screen with smooth progress-synced marquee scrolling and fade-in transitions
- **Luna Music Rear Screen Lyrics** — Force-enable Bluetooth lyrics in Luna Music so lyrics display on the rear screen via MediaSession
- **Telemetry Blocking** — Intercept `DailyTrackReceiver` to block data reporting
- **Quick Switch** — Quick settings tile for one-tap casting to rear screen
- **Hide Launcher Icon** — Hide app icon from launcher, open via LSPosed module manager

## Prerequisites

1. Device must have an unlocked Bootloader and Root access
2. Install [LSPosed](https://github.com/LSPosed/LSPosed/releases) framework
3. Enable Janus module in LSPosed, select scope `com.xiaomi.subscreencenter`
4. Open Janus app and configure features as needed
5. Restart the scope or reboot the device for hooks to take effect

> **Note**: Keep alive, DPI adjustment, task migration and other features require Root access.

## Scope

| App Name          | Package Name                    |
|:------------------|:--------------------------------|
| Rear Display | com.xiaomi.subscreencenter      |
| Apple Music | com.apple.android.music |
| Luna Music | com.luna.music |

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build (with ProGuard/R8 obfuscation + resource shrinking)
./gradlew assembleRelease

# Clean build artifacts
./gradlew clean
```

### Build Environment

- JDK 17+
- Android SDK, compileSdk 36
- Kotlin 2.3.20
- Compose BOM 2025.03.01

## Project Structure

```
app/src/main/kotlin/org/pysh/janus/
├── hook/           # Xposed Hook entry and logic
├── data/           # Data management (whitelist, app scanning, SharedPreferences)
├── service/        # Foreground keep-alive service, quick settings tile
├── ui/             # Compose UI pages (home, apps, features, settings, about, etc.)
├── util/           # Utilities (Root, Display)
└── MainActivity.kt
```

## Support

If you find this project helpful, consider supporting the development on [Afdian](https://ifdian.net/a/janus).

## Acknowledgements

> Janus uses content from the following open-source projects. Thanks to the developers of these projects.

- [MIUIX by YuKongA](https://github.com/compose-miuix-ui/miuix) — Xiaomi-style Compose UI component library
- [LSPosed by LSPosed](https://github.com/LSPosed/LSPosed) — Modern Xposed framework

## License

This project is licensed under the [GPL-3.0](LICENSE) license.

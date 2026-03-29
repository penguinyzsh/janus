<div align="center">

<img src="https://raw.githubusercontent.com/penguinyzsh/janus/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="160" height="160" style="display: block; margin: 0 auto;" alt="icon" />

# Janus

### 小米背屏增强 LSPosed 模块

[English](README.md) | **简体中文**

[![Android](https://img.shields.io/badge/Android-15+-green?logo=android)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/Xposed-API%2082-blue?logo=lsposed)](https://github.com/LSPosed/LSPosed)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-purple?logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-orange?logo=gnu)](LICENSE)

</div>

## 简介

Janus 是一个针对小米手机背屏幕的 LSPosed 增强模块，旨在增强背屏功能体验。通过 Hook `com.xiaomi.subscreencenter`，突破系统对背屏的诸多限制。

## 功能特性

- **音乐白名单解锁** — 解除背屏音乐应用白名单限制，按应用管理白名单
- **动态壁纸防打断** — 阻止背屏遮盖时动态壁纸被暂停或重置，保持壁纸持续播放
- **锁定壁纸切换** — 拦截背屏壁纸长按手势，防止误触进入编辑模式导致壁纸被切换
- **自定义背屏壁纸视频** — 用自己的视频替换 AI 生成的壁纸视频，支持循环播放开关和备份恢复
- **背屏 DPI 调整** — 自定义背屏显示密度，优化小屏显示效果
- **背屏保活** — 前台服务定时发送按键事件，防止背屏自动休眠
- **投屏设置** — 投屏旋转方向控制、投屏时背屏常亮
- **服务助手自定义卡片** — 导入并管理服务助手面板的自定义 MAML 卡片，支持拖拽排序、逐卡启用/禁用、优先级和刷新间隔配置
- **Apple Music 背屏歌词** — 在背屏显示 Apple Music 实时歌词，支持进度同步平滑滚动和淡入切换
- **汽水音乐背屏歌词** — 强制开启汽水音乐蓝牙歌词功能，通过 MediaSession 在背屏显示歌词
- **屏蔽遥测上报** — 拦截 `DailyTrackReceiver`，阻止数据上报
- **快速切换** — 提供快捷设置磁贴，一键投屏至背屏
- **隐藏桌面图标** — 从启动器隐藏应用图标，通过 LSPosed 模块管理器打开

## 使用前说明

1. 设备需要已解锁 Bootloader 并获取 Root 权限
2. 安装 [LSPosed](https://github.com/LSPosed/LSPosed/releases) 框架
3. 在 LSPosed 中启用 Janus 模块，勾选作用域 `com.xiaomi.subscreencenter`
4. 打开 Janus 应用，按需配置功能
5. 重启作用域或重启设备使 Hook 生效

> **注意**：保活、DPI 调整、任务迁移等功能需要 Root 权限。

## 作用域

| 应用名       | 包名                              |
|:----------|:--------------------------------|
| 背屏      | com.xiaomi.subscreencenter      |
| Apple Music | com.apple.android.music |
| 汽水音乐 | com.luna.music |

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（启用 ProGuard/R8 混淆 + 资源压缩）
./gradlew assembleRelease

# 清理构建产物
./gradlew clean
```

### 构建环境

- JDK 17+
- Android SDK，compileSdk 36
- Kotlin 2.3.20
- Compose BOM 2025.03.01

## 项目结构

```
app/src/main/kotlin/org/pysh/janus/
├── hook/           # Xposed Hook 入口与逻辑
├── data/           # 数据管理（白名单、应用扫描、SharedPreferences）
├── service/        # 前台保活服务、快捷设置磁贴
├── ui/             # Compose UI 页面（首页、应用、功能、设置、关于等）
├── util/           # 工具类（Root、Display）
└── MainActivity.kt
```

## 赞助支持

如果觉得这个项目对你有帮助，欢迎在[爱发电](https://ifdian.net/a/janus)上支持开发。

## 感谢

> Janus 使用了以下开源项目的部分内容，感谢这些项目的开发者的提供。

- [「MIUIX」 by YuKongA](https://github.com/compose-miuix-ui/miuix) — 小米风格 Compose UI 组件库
- [「LSPosed」 by LSPosed](https://github.com/LSPosed/LSPosed) — 现代化 Xposed 框架

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证开源。

<div align="center">

<img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="128" height="128" alt="Janus" />

# Janus

**小米背屏增强 LSPosed 模块**

[English](README.md) | **简体中文**

[![Android](https://img.shields.io/badge/Android-15+-34A853?style=flat-square&logo=android&logoColor=white)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/libxposed-API%20101-4285F4?style=flat-square)](https://github.com/libxposed/api)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-GPL--3.0-EA4335?style=flat-square&logo=gnu&logoColor=white)](LICENSE)

---

*释放小米背屏的全部潜力*

</div>



## 简介

Janus 是一个针对小米手机背屏幕的 LSPosed 增强模块，旨在增强背屏功能体验。通过 Hook `com.xiaomi.subscreencenter`，突破系统对背屏的诸多限制。

## 功能特性

<table>
  <tr>
    <td><b>音乐白名单解锁</b></td>
    <td>解除背屏音乐应用白名单限制，按应用管理白名单</td>
  </tr>
  <tr>
    <td><b>动态壁纸防打断</b></td>
    <td>阻止背屏遮盖时动态壁纸被暂停或重置，保持壁纸持续播放</td>
  </tr>
  <tr>
    <td><b>锁定壁纸切换</b></td>
    <td>拦截背屏壁纸长按手势，防止误触进入编辑模式导致壁纸被切换</td>
  </tr>
  <tr>
    <td><b>自定义背屏壁纸视频</b></td>
    <td>用自己的视频替换 AI 生成的壁纸视频，支持循环播放开关和备份恢复</td>
  </tr>
  <tr>
    <td><b>背屏 DPI 调整</b></td>
    <td>自定义背屏显示密度，优化小屏显示效果</td>
  </tr>
  <tr>
    <td><b>背屏保活</b></td>
    <td>前台服务定时发送按键事件，防止背屏自动休眠</td>
  </tr>
  <tr>
    <td><b>投屏设置</b></td>
    <td>投屏旋转方向控制、投屏时背屏常亮</td>
  </tr>
  <tr>
    <td><b>服务助手自定义卡片</b></td>
    <td>导入并管理服务助手面板的自定义 MAML 卡片，支持拖拽排序、逐卡启用/禁用、优先级和刷新间隔配置</td>
  </tr>
  <tr>
    <td><b>自定义音乐卡片</b></td>
    <td>用自定义 MAML 模板替换官方音乐卡片，可选歌词滚动适配</td>
  </tr>
  <tr>
    <td><b>歌词 Hook 规则</b></td>
    <td>通过 JSON 规则引擎按应用管理歌词 Hook。导入社区提供的规则文件，即可在背屏显示任意音乐应用的实时歌词（TTML/LRC 格式）</td>
  </tr>
  <tr>
    <td><b>屏蔽遥测上报</b></td>
    <td>拦截 <code>DailyTrackReceiver</code>，阻止数据上报</td>
  </tr>
  <tr>
    <td><b>快速切换</b></td>
    <td>提供快捷设置磁贴，一键投屏至背屏</td>
  </tr>
  <tr>
    <td><b>隐藏桌面图标</b></td>
    <td>从启动器隐藏应用图标，通过 LSPosed 模块管理器打开</td>
  </tr>
</table>

## 使用前说明

1. 设备需要已解锁 Bootloader 并获取 Root 权限
2. 安装 [LSPosed](https://github.com/LSPosed/LSPosed/releases) 框架
3. 在 LSPosed 中启用 Janus 模块，勾选作用域 `com.xiaomi.subscreencenter`
4. 打开 Janus 应用，按需配置功能
5. 重启作用域或重启设备使 Hook 生效

> [!NOTE]
> 保活、DPI 调整、任务迁移等功能需要 Root 权限。

## 作用域

| 应用名 | 包名 |
|:--|:--|
| 背屏 | `com.xiaomi.subscreencenter` |

> [!TIP]
> 导入歌词 Hook 规则时，如目标音乐应用不在作用域中，会通过 `requestScope()` 自动添加。

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（启用 ProGuard/R8 混淆 + 资源压缩）
./gradlew assembleRelease

# 清理构建产物
./gradlew clean
```

<details>
<summary><b>构建环境</b></summary>

| 依赖项 | 版本 |
|:--|:--|
| JDK | 17+ |
| Android SDK | compileSdk 36 |
| Kotlin | 2.3.20 |
| Compose BOM | 2025.03.01 |

</details>

## 项目结构

```
app/src/main/kotlin/org/pysh/janus/
├── hook/               # Xposed Hook 入口与基础设施
│   └── engine/         # JSON 规则引擎、动作执行器、引擎插件
├── data/               # 数据管理（白名单、Hook 规则、应用扫描）
├── service/            # 前台保活服务、快捷设置磁贴
├── ui/                 # Compose UI 页面（首页、应用、功能、设置等）
├── util/               # 工具类（Root、Display）
└── MainActivity.kt
app/src/main/assets/
└── rules/              # 内置 JSON Hook 规则
```

## 赞助支持

如果觉得这个项目对你有帮助，欢迎在[爱发电](https://ifdian.net/a/janus)上支持开发。

## 感谢

> Janus 使用了以下开源项目的部分内容，感谢这些项目的开发者的提供。

- [**MIUIX** by YuKongA](https://github.com/compose-miuix-ui/miuix) — 小米风格 Compose UI 组件库
- [**LSPosed** by LSPosed](https://github.com/LSPosed/LSPosed) — 现代化 Xposed 框架

## 许可证

本项目基于 [GPL-3.0](LICENSE) 许可证开源。

# 贡献指南 | Contributing Guide

感谢你对 Janus 的关注！以下指南将帮助你顺利参与开发。

Thank you for your interest in Janus! This guide will help you get started with contributing.

---

## 🌐 语言 | Language

本项目 Issue / PR / 代码注释使用**中文或英文**均可，但以下内容**必须双语同步**：

- `values/strings.xml` ↔ `values-zh-rCN/strings.xml`
- `README.md` ↔ `README_CN.md`

## 🚀 快速开始 | Quick Start

### 环境要求

| 依赖项 | 版本 |
|:--|:--|
| JDK | 17+ |
| Android SDK | compileSdk 36 |
| Kotlin | 2.3.20 |
| Compose BOM | 2025.03.01 |

### 开发流程

1. **Fork** 本仓库到你的 GitHub 账号
2. **Clone** 你 fork 的仓库到本地
   ```bash
   git clone https://github.com/<your-username>/janus.git
   cd janus
   ```
3. **创建功能分支**
   ```bash
   git checkout -b feat/your-feature-name
   # 或
   git checkout -b fix/your-bug-fix
   ```
4. **开发和测试**
   ```bash
   ./gradlew assembleDebug       # 构建
   ./gradlew lintDebug           # Lint 检查
   ./gradlew testDebugUnitTest   # 单元测试
   ```
5. **提交变更**（遵循提交规范）
   ```bash
   git commit -m "feat: add new lyric hook rule for Spotify"
   ```
6. **推送到你的 fork**
   ```bash
   git push origin feat/your-feature-name
   ```
7. **创建 Pull Request** 到 `main` 分支

## 📝 提交规范 | Commit Convention

本项目使用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <description>

[optional body]
```

### Type 类型

| 类型 | 说明 | 示例 |
|:--|:--|:--|
| `feat` | 新功能 | `feat: add weather card refresh interval` |
| `fix` | Bug 修复 | `fix: preserve AOD title alignment` |
| `refactor` | 重构 | `refactor: extract lyric parsing logic` |
| `docs` | 文档 | `docs: update hook development guide` |
| `chore` | 构建/工具 | `chore: bump version to 1.2.0` |
| `ci` | CI/CD | `ci: add detekt to CI pipeline` |
| `test` | 测试 | `test: add WhitelistManager unit tests` |
| `style` | 代码格式 | `style: fix indentation in MainScreen` |
| `perf` | 性能优化 | `perf: cache parsed hook rules` |

### Scope（可选）

常用 scope：`hook`、`ui`、`data`、`engine`、`rule`、`card`、`lyric`、`wallpaper`、`casting`

### 注意事项

- **不要添加 Co-Authored-By** 或任何 AI 工具的署名尾部
- 提交信息使用**英文**

## 🏗️ 项目架构 | Architecture

### 双层设计

模块运行在两个独立进程中：

```
┌─────────────────────────────────────────────┐
│                 Hook 侧                      │
│          （目标应用进程中运行）                  │
│                                              │
│  HookEntry → RuleEngine → ActionExecutor     │
│                         → HookEnginePlugin   │
│                                              │
│  配置来源：文件标志 > RemotePreferences > 默认值 │
└─────────────────────────────────────────────┘
        ↑ RemotePreferences / 文件标志
        ↓
┌─────────────────────────────────────────────┐
│                 App 侧                       │
│            （Janus 进程中运行）                 │
│                                              │
│  UI (Compose + MIUIX) → Data Managers        │
│                       → Services             │
│                       → Utilities            │
└─────────────────────────────────────────────┘
```

### 目录结构

```
app/src/main/kotlin/org/pysh/janus/
├── hook/               # Xposed Hook 入口与基础设施
│   └── engine/         # JSON 规则引擎
│       ├── RuleEngine  # 规则分发
│       ├── RuleLoader  # 规则加载与合并
│       ├── ActionExecutor  # 简单动作执行
│       └── engines/    # 复杂 Hook 编排
├── data/               # 数据管理
├── service/            # 前台服务、快捷磁贴
├── ui/                 # Compose UI 页面
├── util/               # 工具类
└── MainActivity.kt
```

## ⚠️ 重要约定 | Important Conventions

### 1. 字符串双语同步

每次新增或修改 `values/strings.xml` 中的字符串时，**必须**同时在 `values-zh-rCN/strings.xml` 中添加或更新对应的中文翻译，反之亦然。

### 2. ProGuard 保留

以下类不可混淆（已在 `proguard-rules.pro` 中配置）：
- `HookEntry` 及所有 `hook/engine/**` 类
- `ReflectUtils`、`LyricInjector`、`LyricParser`
- 服务类和 `WhitelistManager`

### 3. Hook 开发

- 使用 **libxposed API 101**（现代 API），不要使用旧版 `XC_MethodHook`、`XposedHelpers` 等
- 参考文档：https://github.com/libxposed/api
- Hook 侧无法直接访问 App 的类，配置通过 RemotePreferences 读取

### 4. UI 开发

- 所有 UI 基于 **MIUIX** 组件库构建
- 导航使用 `NavDisplay`（Navigation3）
- 页面定义为 sealed `Screen` 接口

## 🧪 测试 | Testing

```bash
# 单元测试（Robolectric，无需设备）
./gradlew testDebugUnitTest

# Lint 检查
./gradlew lintDebug

# ADB E2E 测试（需要连接已激活模块的设备）
./gradlew testE2E

# ADB 行为测试
./gradlew testBehavior
```

## 📋 PR 检查清单 | PR Checklist

提交 PR 前确认：

- [ ] `./gradlew lintDebug` 通过
- [ ] `./gradlew testDebugUnitTest` 通过
- [ ] strings.xml 中英文已同步
- [ ] 无硬编码的用户可见字符串
- [ ] 提交信息遵循 Conventional Commits
- [ ] 在真机上验证过功能（如涉及 Hook 变更）

## 🤝 行为准则 | Code of Conduct

- 尊重每一位贡献者
- 讨论技术问题时保持友善
- 接受建设性的 Code Review 意见
- 不发布与项目无关的内容

## 💡 贡献方式 | Ways to Contribute

不只是代码！你还可以通过以下方式贡献：

- 🐛 报告 Bug（使用 Issue 模板）
- 💡 提出新功能建议
- 📖 改善文档
- 🌍 翻译支持
- 📦 提交 JSON Hook 规则（让更多音乐应用支持歌词显示）
- ⭐ 给项目 Star

## ❓ 有问题？

- 提一个 [Issue](https://github.com/penguinyzsh/janus/issues/new/choose)
- 在 [Discussions](https://github.com/penguinyzsh/janus/discussions) 中讨论

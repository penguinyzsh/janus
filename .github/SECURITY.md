# 安全策略 | Security Policy

## 支持的版本 | Supported Versions

| 版本         | 支持状态   |
| :----------- | :--------- |
| 最新 Release | ✅ 支持     |
| 旧版本       | ❌ 不再维护 |

我们仅对**最新发布版本**提供安全修复。请始终使用最新版本。

We only provide security fixes for the **latest release**. Please always use the latest version.

## 报告漏洞 | Reporting a Vulnerability

如果你发现了安全漏洞，**请不要**通过公开 Issue 报告。

If you discover a security vulnerability, **please do NOT** report it through a public Issue.

请通过以下方式报告：

1. 发送邮件至 **support@pysh.org.com**
2. 邮件标题格式：`[SECURITY] Janus: <简述>`
3. 请包含以下信息：
   - 漏洞描述
   - 复现步骤
   - 影响版本
   - 可能的影响范围

我们会在 **48 小时**内确认收到报告，并在 **7 天**内提供修复计划。

We will acknowledge receipt within **48 hours** and provide a fix plan within **7 days**.

## 安全考虑 | Security Considerations

Janus 作为一个 Xposed 模块，在目标应用进程中运行 Hook 代码。以下是安全相关须知：

- 模块仅 Hook `com.xiaomi.subscreencenter` 以及通过规则引擎动态添加的音乐应用
- 所有配置数据存储在应用本地 SharedPreferences 中
- 文件标志存储在 `/data/system/theme_magic/` 下（需要 Root）
- 不收集或传输任何用户数据（并且提供主动屏蔽原始应用的遥测上报）

## 感谢 | Acknowledgements

感谢所有帮助改善 Janus 安全性的贡献者。

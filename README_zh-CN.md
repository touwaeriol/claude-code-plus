# Claude Code Plus

<p align="center">
  <img src="jetbrains-plugin/src/main/resources/META-INF/pluginIcon.svg" width="80" alt="Claude Code Plus Logo">
</p>

<p align="center">
  <strong>JetBrains IDE 高级 AI 编程助手</strong>
</p>

<p align="center">
  <a href="https://plugins.jetbrains.com/plugin/28343-claude-code-plus">
    <img src="https://img.shields.io/jetbrains/plugin/v/26972-claude-code-plus.svg" alt="JetBrains Plugin">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/releases">
    <img src="https://img.shields.io/github/v/release/touwaeriol/claude-code-plus" alt="GitHub Release">
  </a>
  <a href="https://github.com/touwaeriol/claude-code-plus/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/touwaeriol/claude-code-plus" alt="License">
  </a>
</p>

<p align="center">
  <a href="README.md">English</a> |
  <a href="README_zh-CN.md">简体中文</a> |
  <a href="README_ja.md">日本語</a> |
  <a href="README_ko.md">한국어</a>
</p>

---

Claude Code Plus 是一个 IntelliJ IDEA 插件，将 Claude AI 直接集成到您的开发环境中，通过自然语言交互提供智能代码辅助。

## ✨ 功能特性

- **AI 驱动对话** - 在 IDE 中直接与 Claude AI 对话
- **智能上下文管理** - 使用 @ 提及引用文件和代码片段
- **多会话支持** - 同时管理多个聊天会话
- **丰富的工具集成** - 查看和交互 Claude 的工具使用（文件读写、bash 命令等）
- **IDE 集成** - 点击打开文件、查看差异、跳转到指定行
- **暗色主题支持** - 完全兼容 IntelliJ 的暗色主题
- **导出功能** - 以多种格式保存���话历史

## 📸 截图

### 欢迎界面
![欢迎界面](docs/screenshots/welcome-screen.png)

### 工具执行演示
![工具演示](docs/screenshots/tool-demo.png)

### 权限请求
![权限请求](docs/screenshots/permission-request.png)

### 主界面
![主界面](docs/screenshots/idea-integration.png)

## 📦 安装

### 方式一：JetBrains 插件市场（推荐）
1. 打开您的 JetBrains IDE
2. 前往 **设置** → **插件** → **Marketplace**
3. 搜索 "**Claude Code Plus**"
4. 点击 **安装** 并重启 IDE

### 方式二：GitHub Release（手动安装）
1. 从 [Releases](https://github.com/touwaeriol/claude-code-plus/releases) 下载最新的 `jetbrains-plugin-x.x.x.zip`
2. 在 IDE 中：**设置** → **插件** → ⚙️ → **从磁盘安装插件...**
3. 选择下载的 zip 文件并重启 IDE

## 🔧 系统要求

- **JetBrains IDE**：IntelliJ IDEA 2024.2 - 2025.3.x（Build 242-253）
- **Node.js**：v18 或更高版本（[下载](https://nodejs.org/)）
- **Claude 认证**：需要一次性配置
  - 打开终端运行：`npx @anthropic-ai/claude-code`
  - 按提示完成认证
  - 详细配置指南请参阅[官方文档](https://docs.anthropic.com/en/docs/claude-code/getting-started)

> **提示**：插件内置了 Claude CLI，无需单独安装！

## 🚀 快速开始

1. 按照上述步骤安装插件
2. 确保 Claude CLI 已安装并完成认证
3. 打开 **Claude Code Plus** 工具窗口（右侧边栏）
4. 开始与 Claude 对话！

### 使用技巧
- 使用 `@` 提及文件并将其添加为上下文
- 点击工具输出中的文件路径可在编辑器中打开
- 使用快捷键：
  - `Ctrl+J` - 快捷操作
  - `Ctrl+U` - 常用操作

## 🤝 参与贡献

欢迎贡献代码！请随时提交 Pull Request。

## 📝 开源协议

本项目基于 MIT 许可证开源 - 详情请参阅 [LICENSE](LICENSE) 文件。

## 🔗 相关链接

- [JetBrains 插件市场](https://plugins.jetbrains.com/plugin/28343-claude-code-plus)
- [GitHub 仓库](https://github.com/touwaeriol/claude-code-plus)
- [问题反馈](https://github.com/touwaeriol/claude-code-plus/issues)
- [更新日志](https://github.com/touwaeriol/claude-code-plus/releases)

---

<p align="center">
  用 ❤️ 制作 by <a href="https://github.com/touwaeriol">touwaeriol</a>
</p>
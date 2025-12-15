# Claude Code Plus

<p align="center">
  <img src="jetbrains-plugin/src/main/resources/META-INF/pluginIcon.svg" width="80" alt="Claude Code Plus Logo">
</p>

<p align="center">
  <strong>Advanced AI Programming Assistant for JetBrains IDEs</strong>
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

Claude Code Plus is an IntelliJ IDEA plugin that integrates Claude AI directly into your development environment, providing intelligent code assistance through natural language interaction.

## ✨ Features

- **AI-Powered Conversations** - Chat with Claude AI directly in your IDE
- **Smart Context Management** - Reference files and code snippets with @ mentions
- **Multi-Session Support** - Manage multiple chat sessions simultaneously
- **Rich Tool Integration** - View and interact with Claude's tool usage (file read/write, bash commands, etc.)
- **IDE Integration** - Click to open files, view diffs, and navigate to specific lines
- **Dark Theme Support** - Fully compatible with IntelliJ's dark themes
- **Export Capabilities** - Save conversation history in multiple formats


## 📸 Screenshots

### Tool Calls Demo
View Claude's tool usage with detailed information for Read, Write, and Edit operations.

![Tool Calls Demo](docs/screenshots/tool-calls-demo.png)

### @ Mention File Search
Quickly reference files in your project using the @ mention feature.

![@ Mention File Search](docs/screenshots/at-mention-file-search.png)

### Model Selector
Switch between different Claude models (Opus 4.5, Sonnet 4.5, Haiku 4.5).

![Model Selector](docs/screenshots/model-selector.png)

### Permission Request
Secure authorization dialog for file write operations.

![Permission Request](docs/screenshots/permission-request.png)

### User Question Dialog
Interactive dialog for Claude to ask clarifying questions with model selection and feature settings.

![User Question Dialog](docs/screenshots/user-question-dialog.png)

## 📦 Installation

### Option 1: JetBrains Marketplace (Recommended)
1. Open your JetBrains IDE
2. Go to **Settings** → **Plugins** → **Marketplace**
3. Search for "**Claude Code Plus**"
4. Click **Install** and restart IDE

### Option 2: GitHub Release (Manual)
1. Download the latest `jetbrains-plugin-x.x.x.zip` from [Releases](https://github.com/touwaeriol/claude-code-plus/releases)
2. In your IDE: **Settings** → **Plugins** → ⚙️ → **Install Plugin from Disk...**
3. Select the downloaded zip file and restart IDE

## 🔧 Requirements

- **JetBrains IDE**: IntelliJ IDEA 2024.2 - 2025.3.x (Build 242-253)
- **Node.js**: v18 or higher ([Download](https://nodejs.org/)) - ensure `node` command is available in PATH
- **Claude Code**: One-time setup required
  - Open terminal and run: `npx @anthropic-ai/claude-code`
  - Follow the prompts to authenticate
  - See [Official Documentation](https://docs.anthropic.com/en/docs/claude-code/getting-started) for detailed setup guide

> **Note**: The plugin includes a bundled Claude CLI - no separate CLI installation required!

### Using API Key (Alternative)

If you prefer to use your own Anthropic API key instead of Claude Code subscription, you can use [cc-switch](https://github.com/farion1231/cc-switch) to configure it:

```bash
npx cc-switch
```

This tool helps you switch between different authentication methods for Claude Code.

## 🚀 Quick Start

1. Install the plugin following the installation steps above
2. Ensure Claude CLI is installed and authenticated
3. Open the **Claude Code Plus** tool window (right sidebar)
4. Start chatting with Claude!

### Tips
- Use `@` to mention files and add them as context
- Click on file paths in tool outputs to open them in the editor
- Click on tool cards (Read/Write/Edit) to view diff preview
- Press `ESC` to interrupt AI generation
- Switch models anytime using the model selector (Opus/Sonnet/Haiku)
- Use keyboard shortcuts:
  - `Ctrl+J` - Quick actions
  - `Ctrl+U` - Common operations
  - `Enter` - Send message
  - `Shift+Enter` - New line in input

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🔗 Links

- [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/28343-claude-code-plus)
- [GitHub Repository](https://github.com/touwaeriol/claude-code-plus)
- [Issue Tracker](https://github.com/touwaeriol/claude-code-plus/issues)
- [Changelog](https://github.com/touwaeriol/claude-code-plus/releases)

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/touwaeriol">touwaeriol</a>
</p>

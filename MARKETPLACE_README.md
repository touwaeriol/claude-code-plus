# Claude Code Plus - Advanced AI Programming Assistant

![Claude Code Plus Logo](https://raw.githubusercontent.com/yourusername/claude-code-plus/main/jetbrains-plugin/src/main/resources/META-INF/pluginIcon.svg)

## Overview

Claude Code Plus is an enhanced IntelliJ IDEA plugin that seamlessly integrates Claude AI into your development workflow. Experience the power of AI-assisted coding with natural language interaction, intelligent context management, and advanced code generation capabilities.

## ✨ Key Features

### 🤖 AI-Powered Development
- **Natural Language Coding**: Describe what you want in plain language and let Claude generate the code
- **Intelligent Code Analysis**: Get instant explanations and improvements for your code
- **Multi-Model Support**: Choose from various Claude models based on your needs

### 📁 Smart Context Management
- **@ Mentions**: Reference files and code snippets directly in your conversations
- **Automatic Context**: Claude understands your project structure and current file
- **Context Templates**: Save and reuse common context configurations

### 🛠️ Advanced Tool Integration
- **MCP Protocol Support**: Configure and use Model Context Protocol services
- **Tool Visualization**: See exactly what tools Claude is using with beautiful animations
- **80+ Tool Types**: Professional formatting for file operations, searches, web content, and more

### 💬 Enhanced Chat Experience
- **Multi-Tab Sessions**: Manage multiple conversations simultaneously
- **Session Persistence**: Your conversations are saved and continue even after IDE restart
- **Export Options**: Save conversations in multiple formats for documentation

### 🎨 Beautiful Interface
- **Dark Theme Support**: Fully compatible with all IntelliJ themes
- **Markdown Rendering**: Rich formatting for code blocks and documentation
- **Syntax Highlighting**: Automatic language detection and highlighting

## 🚀 Getting Started

### Prerequisites
1. IntelliJ IDEA 2024.3 or newer
2. Claude CLI installed and configured
   ```bash
   npm install -g @anthropic-ai/claude-cli
   claude login
   ```

### Installation
1. Open IntelliJ IDEA
2. Go to **Settings/Preferences** → **Plugins**
3. Search for "Claude Code Plus"
4. Click **Install** and restart IDE

### First Use
1. Open the Claude Code Plus tool window (right sidebar)
2. Start typing your question or request
3. Use @ to reference files or code
4. Press Enter to send

## 📸 Screenshots

### Main Chat Interface
![Chat Interface](screenshots/chat-interface.png)

### Context Management
![Context Management](screenshots/context-management.png)

### Tool Execution Display
![Tool Display](screenshots/tool-display.png)

## 🔧 Configuration

### MCP Services
Configure Model Context Protocol services for enhanced capabilities:
1. Go to **Settings** → **Tools** → **Claude Code Plus**
2. Add your MCP service configurations
3. Enable/disable services as needed

### Default Settings
- Model selection
- Context window size
- Export preferences
- UI customization

## 📝 Usage Examples

### Code Generation
```
@CurrentFile Can you add error handling to this function?
```

### Code Review
```
@src/main/java Review this class for potential improvements
```

### Documentation
```
@MyClass.java Generate comprehensive JavaDoc for all methods
```

## 🤝 Contributing

We welcome contributions! Please visit our [GitHub repository](https://github.com/yourusername/claude-code-plus) for:
- Bug reports
- Feature requests
- Pull requests
- Documentation improvements

## 📄 License

This project is licensed under the MIT License. See the [LICENSE](https://github.com/yourusername/claude-code-plus/blob/main/LICENSE) file for details.

## 🙏 Acknowledgments

- Claude AI by Anthropic
- IntelliJ Platform SDK
- The amazing open-source community

## 📮 Support

- **GitHub Issues**: [Report bugs or request features](https://github.com/yourusername/claude-code-plus/issues)
- **Email**: support@claudecodeplus.com
- **Documentation**: [Full documentation](https://github.com/yourusername/claude-code-plus/wiki)

---

**Made with ❤️ by the Claude Code Plus Team**
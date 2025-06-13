# Claude Code Plus

一个 IntelliJ IDEA 插件，通过增强的交互界面为 Claude Code 提供更好的用户体验。

## 项目概述

Claude Code Plus 是一个基于 Kotlin 和 JetBrains Compose Multiplatform 开发的 IDEA 插件，它在后台运行 Claude Code 命令行工具，并提供类似 JetBrains AI Assistant 的对话界面。该插件增强了原生 Claude Code 的功能，特别是文件引用的智能化处理。

## 主要功能

### 1. 对话界面
- 提供类似 JetBrains AI Assistant 的浮动对话窗口
- 支持 Markdown 渲染的对话历史
- 实时显示 Claude Code 的响应

### 2. 智能文件引用
- **@文件引用**：输入 `@` 时自动触发文件搜索和补全
- **智能搜索**：基于文件名、路径和内容的模糊搜索
- **路径转换**：自动将相对路径转换为绝对路径
- **预览支持**：悬停显示文件内容预览

### 3. Claude Code 集成
- 后台管理 Claude Code 进程生命周期
- 双向通信：用户输入转发和响应接收
- 错误处理和重试机制

### 4. 增强功能
- 代码高亮显示
- 多会话管理
- 历史记录保存
- 快捷键支持

## 技术栈

- **语言**：Kotlin
- **UI 框架**：JetBrains Compose Multiplatform
- **IDE 平台**：IntelliJ Platform SDK
- **构建工具**：Gradle
- **最低 IDE 版本**：2023.1

## 架构设计

### 组件结构
```
claude-code-plus/
├── src/main/kotlin/
│   ├── com/claudecodeplus/
│   │   ├── ui/              # Compose UI 组件
│   │   │   ├── ChatWindow.kt
│   │   │   ├── MessageList.kt
│   │   │   └── InputField.kt
│   │   ├── service/         # 后台服务
│   │   │   ├── ClaudeCodeService.kt
│   │   │   └── ProcessManager.kt
│   │   ├── completion/      # 代码补全
│   │   │   ├── FileCompletionProvider.kt
│   │   │   └── PathResolver.kt
│   │   ├── action/          # IDE 动作
│   │   │   └── ShowChatAction.kt
│   │   └── model/           # 数据模型
│   │       └── ChatMessage.kt
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml
│       └── icons/
└── build.gradle.kts
```

### 核心模块

1. **ProcessManager**
   - 管理 claude-code 进程的启动、停止和重启
   - 处理标准输入/输出流
   - 监控进程状态

2. **FileCompletionProvider**
   - 实现 `@` 触发的文件补全
   - 集成 IDE 的文件索引系统
   - 提供智能排序和过滤

3. **ChatWindow**
   - 基于 Compose 的聊天界面
   - 支持 Markdown 渲染
   - 处理用户输入和显示响应

4. **ClaudeCodeService**
   - 作为 IDE 服务运行
   - 协调各组件之间的通信
   - 管理会话状态

## 开发计划

### 第一阶段：基础功能（1-2周）
- [ ] 项目初始化和基础配置
- [ ] 实现 ProcessManager 管理 claude-code 进程
- [ ] 创建简单的对话界面
- [ ] 实现基本的消息收发

### 第二阶段：智能补全（2-3周）
- [ ] 集成 IDE 文件索引
- [ ] 实现 @ 触发的补全提供器
- [ ] 添加路径解析和转换逻辑
- [ ] 文件预览功能

### 第三阶段：UI 增强（1-2周）
- [ ] 完善 Compose UI 组件
- [ ] 添加 Markdown 渲染
- [ ] 实现代码高亮
- [ ] 添加历史记录管理

### 第四阶段：优化和测试（1周）
- [ ] 性能优化
- [ ] 错误处理完善
- [ ] 单元测试和集成测试
- [ ] 文档编写

## 使用说明

### 安装要求
- IntelliJ IDEA 2023.1 或更高版本
- 已安装 Claude Code CLI 工具
- JDK 17 或更高版本

### 配置
插件会自动检测系统中的 claude-code 安装，如果需要自定义路径，可以在设置中配置。

### 快捷键
- `Ctrl+Shift+C` (Windows/Linux) 或 `Cmd+Shift+C` (macOS)：打开对话窗口
- `@` + 文件名：触发文件补全
- `Esc`：关闭对话窗口

## 贡献指南

欢迎提交 Issue 和 Pull Request。开发前请确保：
1. 遵循 Kotlin 编码规范
2. 添加必要的测试
3. 更新相关文档

## 许可证

MIT License

## 致谢

- Claude Code 团队提供的优秀命令行工具
- JetBrains 提供的 IntelliJ Platform SDK 和 Compose Multiplatform

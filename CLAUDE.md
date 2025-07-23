# Claude Code Plus 项目文档索引

本文件提供项目内主要文档和配置文件的索引。

## 项目文档

### `docs/` 目录

#### 需求和设计文档
*   **`需求文档.md`**: Claude Code Plus 插件的总体需求文档，包括 UI 布局、消息格式、工具栏和状态栏功能、内联引用系统和主题适配等。
*   **`桌面应用需求.md`**: 独立桌面应用的功能需求和架构规范，包括多标签界面、项目管理和平台支持等。
*   **`工具窗口需求.md`**: 核心 UI 组件的详细需求，涵盖聊天视图、统一输入区域和会话管理等可重用组件。

#### 架构和设计文档
*   **`架构设计.md`**: Claude Code Plus 的整体架构说明，详细介绍直接调用 Claude CLI 的设计，包括 ClaudeCliWrapper、UI 组件、数据模型和 MCP 支持。
*   **`会话管理设计.md`**: 会话管理系统的设计方案，涵盖会话存储、数据模型以及与 Claude CLI 的集成计划。
*   **`Claude消息类型.md`**: Claude Code SDK 使用的 JSONL 消息类型和数据模型，包括用户、助手、系统、结果和摘要消息，以及内容块类型。

#### 功能特性文档
*   **`功能特性.md`**: Claude Code Plus 的核心功能详述，包括流式响应、中断能力、上下文选择（"添加上下文"按钮、`@` 符号和 `⌘K`）、统一的 ChatInputArea 组件、多模型支持和会话管理。
*   **`内联引用扩展.md`**: 可扩展内联引用系统指南，说明如何通过扩展 InlineReferenceScheme 枚举和更新相关逻辑来添加新的引用类型（如数据库、API、Docker 等）。

#### 部署和使用指南
*   **`部署指南.md`**: Claude Code Plus 的部署说明，涵盖作为 IntelliJ IDEA 插件或独立桌面应用程序的部署方式，概述模块结构和功能差异。
*   **`快速开始.md`**: 增强桌面应用的快速入门指南，包括如何运行应用、功能概览（如多标签对话和上下文管理）以及键盘快捷键摘要。

### `.claude/rules`

This directory was ignored as it is not part of the project's source code.

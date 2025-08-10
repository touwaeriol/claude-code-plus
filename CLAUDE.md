# Claude Code Plus 项目文档索引

本文件提供项目内主要文档和配置文件的索引
开发时自行阅读文档，基于文档完成工作
你需要在我提出对应的修改要求时**自动更新**相关文档

## 项目文档

### `docs/` 目录

#### 最新重要发现（必读）
*   **`事件驱动架构设计.md`**: **🔥 最新重要文档** - **已完全校准至 Claudia 项目实现方式**。基于对 Claudia 项目深入分析和实际测试发现的关键机制：
    - **二元会话策略**：新会话用 `executeClaudeCode`（无--resume），所有后续消息用 `resumeClaudeCode`（带--resume sessionId）
    - **关键发现**：Claudia 从不使用 `continueClaudeCode`，同一会话的多轮对话都通过 `resumeClaudeCode` 处理
    - **CLI 参数一致性**：在新会话、延续会话等所有场景下使用完全相同的 CLI 参数组合
    - **事件驱动架构**：完整的进程流监听方案，完全符合 Claudia 的成功模式
    - **历史加载机制**：与 Claudia 完全一致的预加载历史记录策略

#### 需求和设计文档
*   **`toolwindow需求文档.md`**: 工具窗口模块的需求文档，定义了所有可重用的核心 UI 组件、服务接口和组件架构
*   **`desktop需求文档.md`**: 桌面应用的需求文档，包括多标签界面、项目管理面板、窗口管理和平台支持
*   **`plugin需求文档.md`**: IntelliJ 插件的需求文档，涵盖 IDE 集成、编辑器功能、调试支持等特定功能

#### 架构和设计文档
*   **`架构设计.md`**: Claude Code Plus 的整体架构说明，包括 ClaudeCliWrapper、UI 组件、数据模型。**已更新**包含 Claude CLI 会话连续性机制的重要发现和事件驱动架构方案。
*   **`会话管理设计.md`**: 会话管理系统的设计方案，涵盖会话存储、数据模型以及与 Claude CLI 的集成计划。
*   **`会话状态管理系统.md`**: 完整的会话状态管理实现文档，包括 SessionObject、SessionManager、配置持久化和并发支发的详细说明。
*   **`Claude消息类型.md`**: Claude Code SDK 使用的 JSONL 消息类型和数据模型，包括用户、助手、系统、结果和摘要消息，以及内容块类型。

#### 功能特性文档
*   **`功能特性.md`**: Claude Code Plus 的核心功能详述，**已更新**包含会话连续性机制和事件驱动架构的技术特性说明：
    - 基础功能：流式响应、中断能力、多模型支持
    - 上下文管理："添加上下文"按钮、`@` 符号内联引用、`⌘K` 快捷键
    - 界面组件：统一的 ChatInputArea 组件、工具调用展示优化（跳动点动画、单参数内联显示、Diff展示等）
    - **会话管理**：动态会话文件、leafUuid 链接、事件驱动架构、智能历史加载
    - 高级功能：多标签对话、对话分组、全局搜索、导出功能等
*   **`内联引用扩展.md`**: 可扩展内联引用系统指南，说明如何通过扩展 InlineReferenceScheme 枚举和更新相关逻辑来添加新的引用类型（如数据库、API、Docker 等）。

#### 部署和使用指南
*   **`部署指南.md`**: Claude Code Plus 的部署说明，涵盖作为 IntelliJ IDEA 插件或独立桌面应用程序的部署方式，概述模块结构和功能差异。
*   **`快速开始.md`**: 增强桌面应用的快速入门指南，包括如何运行应用、功能概览（如多标签对话和上下文管理）以及键盘快捷键摘要。

#### 实现状态文档
*   **`上下文消息格式设计.md`**: 详细说明上下文消息格式的设计理念和实现方案，包括标签上下文和内联引用的区别

### 组件实现状态

#### ClaudeCliWrapper 重要更新
- **命令切换**：从直接调用 `node cli.js` 改为使用 `claude` 命令
  - 支持自定义命令路径：通过 `QueryOptions.customCommand` 参数
  - 自动查找 claude 命令位置（Windows: claude.cmd, Unix: /usr/local/bin/claude 等）
  - 保留了中断功能：通过 `terminate()` 方法终止进程
- **参数映射调整**：
  - `--custom-system-prompt` → `--append-system-prompt`
  - `--mcp-servers` → `--mcp-config`
  - 添加 `--print` 参数以使用非交互模式

#### 已实现的核心组件
- **工具展示组件**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/tools/`）
  - `LoadingIndicators.kt` - 各种加载动画组件（跳动点、脉冲点、旋转进度等）
  - `CompactToolCallDisplay.kt` - 紧凑的工具调用展示，支持智能参数显示
  - `EnhancedTodoDisplay.kt` - TodoWrite 单列任务列表展示
  - `DiffResultDisplay.kt` - 文件编辑的 Diff 结果展示
  - `ToolExecutionProgress.kt` - 工具执行进度显示
  - `ToolGroupDisplay.kt` - 工具分组展示
  - `AssistantMessageDisplay.kt` - 助手消息展示（集成新的工具展示组件）

- **会话管理组件**
  - `MultiTabChatView.kt` - 多标签聊天视图
  - `ChatTabManager.kt` - 标签管理服务（支持Claude会话链接状态跟踪）
  - `ProjectManager.kt` - 项目管理服务
  - `ChatOrganizer.kt` - 对话组织器
  - `SessionObject.kt` - 会话状态容器（包含所有会话运行时数据）
  - `SessionManager.kt` - 会话生命周期管理（支持多会话并发）
  - `DefaultSessionConfig.kt` - 全局默认会话配置管理
  - `SessionPersistenceService.kt` - 会话配置持久化服务
  - `SessionMetadata.kt` - 会话元数据模型（用于配置序列化）

- **上下文管理组件**
  - `ContextSelectorPopup.kt` - 上下文选择器弹窗
  - `FileContextSelector.kt` - 文件选择器
  - `WebContextSelector.kt` - Web URL 选择器
  - `InlineReferenceManager` - 内联引用管理器

## 最新调查和发现记录

### 2025年8月6日 - Claude CLI 会话连续性机制调查

详细的调查过程、发现和解决方案请参考：**`docs/事件驱动架构设计.md`**

#### 关键结论摘要
基于对 Claudia 项目的深入分析和实际测试，确认了 Claude CLI 的真实行为：
- `--resume` 参数在 `--print` 模式下每次都创建新会话文件
- Claude 通过 `leafUuid` 机制保持会话连续性
- 当前文件监听系统因此失效

#### 解决方案
采用事件驱动架构替代文件监听，详细设计参考 `docs/事件驱动架构设计.md`

### `.claude/rules`

This directory was ignored as it is not part of the project's source code.

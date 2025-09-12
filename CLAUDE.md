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

#### 需求和设计文档（位于 `requirements/` 子目录）
*   **`requirements/README.md`**: 需求文档目录索引，概述各模块需求文档
*   **`requirements/toolwindow-requirements.md`**: **🎯 完整工具窗口需求文档** - 定义所有可重用的核心 UI 组件、服务接口和组件架构。**已整合界面布局设计**，包含：
    - 完整的权限管理系统（权限模式选择器、跳过权限认证开关、权限控制组合逻辑）
    - 聊天界面布局设计（基础界面结构、消息展示布局、工具展示示例、状态指示器系统、响应式布局适配）
    - 所有UI组件的功能需求和交互规范
*   **`requirements/plugin-requirements.md`**: IntelliJ 插件的需求文档，涵盖 IDE 集成、编辑器功能、调试支持等特定功能

#### 架构和设计文档
*   **`架构设计.md`**: Claude Code Plus 的整体架构说明，包括 ClaudeCliWrapper、UI 组件、数据模型。**已更新**包含 Claude CLI 会话连续性机制的重要发现和事件驱动架构方案。
*   **`会话状态管理系统.md`**: 完整的会话状态管理实现文档，包括 SessionObject、SessionManager、配置持久化和并发支持的详细说明。
*   **`Claude消息类型.md`**: Claude Code SDK 使用的 JSONL 消息类型和数据模型，包括用户、助手、系统、结果和摘要消息，以及内容块类型。
*   **`输入框UI设计规范.md`**: 输入框UI的设计规范和实现标准
*   **`日志系统说明.md`**: 项目日志系统的配置和使用说明
*   **`打包部署方案.md`**: 项目的打包和部署方案说明

#### 功能特性文档
*   **`功能特性.md`**: Claude Code Plus 的核心功能详述，**已更新**包含会话连续性机制和事件驱动架构的技术特性说明：
    - 基础功能：流式响应、中断能力、多模型支持
    - 上下文管理："添加上下文"按钮、`@` 符号内联引用、`⌘K` 快捷键
    - 界面组件：统一的 ChatInputArea 组件、工具调用展示优化（跳动点动画、单参数内联显示、Diff展示等）
    - **工具显示系统**：**已大幅增强** - 从6种工具扩展到支持80+工具的专业显示，包含MCP工具统一格式、搜索结果专业展示、网页内容摘要等
    - **会话管理**：动态会话文件、leafUuid 链接、事件驱动架构、智能历史加载
    - 高级功能：多标签对话、对话分组、全局搜索、导出功能等
*   **`内联引用扩展.md`**: 可扩展内联引用系统指南，说明如何通过扩展 InlineReferenceScheme 枚举和更新相关逻辑来添加新的引用类型（如数据库、API、Docker 等）。

#### 技术文档
*   **`SDK迁移指南.md`**: Claude CLI SDK 迁移指南，包含版本更新和API变更说明

#### 部署和使用指南
*   **`部署指南.md`**: Claude Code Plus 的部署说明，作为 IntelliJ IDEA 插件的部署方式和配置。
*   **`快速开始.md`**: 插件快速入门指南，包括如何使用插件、功能概览（如多标签对话和上下文管理）以及键盘快捷键摘要。

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

**工具展示组件**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/`）**已大幅增强**
  - `LoadingIndicators.kt`（tools目录下）- 各种加载动画组件（跳动点、脉冲点、旋转进度等）
  - `CompactToolCallDisplay.kt` - **核心工具调用展示组件**，支持智能参数显示和80+工具专业格式化：
    - 基础工具：Edit/Read/Write/LS/Bash/TodoWrite 专业显示
    - 搜索工具：Glob文件匹配、Grep/Search内容搜索专业展示
    - 网络工具：WebFetch网页内容摘要显示
    - 高级工具：Task子任务处理、NotebookEdit Jupyter操作显示
    - MCP工具：统一的MCP工具展示格式（server.function）
  - `SmartToolCallDisplay.kt` - 智能工具调用展示组件
  - `AssistantMessageDisplay.kt` - 助手消息展示（集成新的工具展示组件）

**消息显示组件**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/jewel/components/`）**已完全优化**
  - `UserMessageDisplay.kt` - **用户消息显示组件**，已优化为与输入框完全一致的主题适配
  - `UnifiedInputArea.kt` - **统一输入区域组件**，支持 INPUT 和 DISPLAY 两种模式，DISPLAY 模式已优化为纯文本显示
  - `AnnotatedMessageDisplay.kt` - 带注解的消息显示组件（已被纯文本方案替代，避免主题适配问题）
  - `CachedMarkdownParser.kt` - 缓存的 Markdown 解析器（性能优化组件）
  - `MarkdownRenderer.kt` - Markdown 渲染组件
  - `ChatInputField.kt` - 聊天输入字段组件

**UI 组件**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/components/`）
  - `MultiTabChatView.kt` - 多标签聊天视图
  - `ChatOrganizer.kt` - 对话组织器
  - `ProjectSelector.kt` - 项目选择器
  - `ProjectTabBar.kt` - 项目标签栏
  - `ProjectListPanel.kt` - 项目列表面板
  - `SessionListPanel.kt` - 会话列表面板
  - `GlobalSearchDialog.kt` - 全局搜索对话框
  - `ContextTemplateDialog.kt` - 上下文模板对话框
  - `BatchQuestionDialog.kt` - 批量问题对话框（已修复主题适配）
  - `ContextPreviewPanel.kt` - 上下文预览面板
  - `InterruptedSessionBanner.kt` - 中断会话横幅
  - `ModernChatView.kt` - 现代聊天视图
  - `ChatViewNew.kt` - 新版聊天视图
  - `ChatViewOptimized.kt` - 优化版聊天视图
  - `StandaloneChatView.kt` - 独立聊天视图
  - `UnifiedChatInput.kt` - 统一聊天输入组件（已修复控件状态管理）
  - `SimpleInlineFileReference.kt` - @ 符号文件引用组件（已修复主题感知高亮）
  - `ChatInputContextSelectorPopup.kt` - 上下文选择弹窗（已确认为未使用的遗留代码）

- **服务层组件**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/services/`）
  - `ChatTabManager.kt` - 标签管理服务（支持Claude会话链接状态跟踪）
  - `ProjectManager.kt` - 项目管理服务
  - `SessionManager.kt` - 会话生命周期管理（支持多会话并发）
  - `DefaultSessionConfig.kt` - 全局默认会话配置管理
  - `SessionPersistenceService.kt` - 会话配置持久化服务
  - `ChatSessionStateManager.kt` - 聊天会话状态管理
  - `UnifiedSessionService.kt` - 统一会话服务
  - `SessionHistoryService.kt` - 会话历史服务
  - `SessionLoader.kt` - 会话加载器
  - `ChatExportService.kt` - 聊天导出服务
  - `ChatSearchEngine.kt` - 聊天搜索引擎
  - `ChatSummaryService.kt` - 聊天摘要服务
  - `ContextManagementService.kt` - 上下文管理服务
  - `ContextProvider.kt` - 上下文提供器
  - `ContextRecommendationEngine.kt` - 上下文推荐引擎
  - `ContextTemplateManager.kt` - 上下文模板管理器
  - `PromptTemplateManager.kt` - 提示模板管理器
  - `QuestionQueueManager.kt` - 问题队列管理器
  - `FileIndexService.kt` - 文件索引服务
  - `FileSearchService.kt` - 文件搜索服务
  - `MessageConverter.kt` - 消息转换器
  - `MessageProcessor.kt` - 消息处理器
  - `ClaudeCliWrapperAdapter.kt` - Claude CLI包装器适配器

- **数据模型**（`toolwindow/src/main/kotlin/com/claudecodeplus/ui/models/`）
  - `SessionObject.kt` - 会话状态容器（包含所有会话运行时数据）
  - `SessionObjectV2.kt` - 会话对象V2版本
  - `SessionMetadata.kt` - 会话元数据模型（用于配置序列化）
  - `ChatModels.kt` - 聊天相关数据模型
  - `ContextModels.kt` - 上下文相关数据模型
  - `ConfigModels.kt` - 配置相关数据模型
  - `LocalConfigModels.kt` - 本地配置数据模型
  - `EnhancedMessage.kt` - 增强消息模型
  - `EnhancedModels.kt` - 增强数据模型
  - `UnifiedModels.kt` - 统一数据模型
  - `GlobalCliWrapper.kt` - 全局CLI包装器模型

## 最新调查和发现记录

### 2025年9月12日 - 上下文使用指示器圆环进度条实现

完成了上下文使用指示器的视觉增强，添加了直观的圆环进度条显示：

#### 实现内容
1. **Jewel组件库调研**（`ContextUsageIndicator.kt`）
   - **发现**：Jewel的CircularProgressIndicator仅支持无限期加载动画，不支持确定进度显示
   - **组件更新**：更新了jewel-components.md和jewel-component-index.md中的进度条文档，澄清了实际API
   - **文档完善**：从43个组件更新为45个组件的完整索引

2. **自定义圆环进度条实现**（`ContextUsageIndicator.kt:452-509`）
   ```kotlin
   @Composable
   private fun CustomCircularProgress(
       percentage: Int,
       modifier: Modifier = Modifier
   ) {
       // 智能颜色系统：根据使用率自动变色
       val progressColor = when {
           percentage >= 95 -> Color(0xFFFF4444) // 危险红色
           percentage >= 80 -> Color(0xFFFF8800) // 警告橙色  
           percentage >= 50 -> Color(0xFFFFA500) // 注意黄色
           else -> JewelTheme.globalColors.text.normal.copy(alpha = 0.6f) // 正常灰色
       }
   }
   ```

3. **视觉效果优化**
   - **圆环尺寸**：14x14 dp 精致尺寸，与现有UI完美融合
   - **主题适配**：使用JewelTheme色彩系统，支持暗色/亮色主题自动切换
   - **渐进式警告**：4级颜色系统，从正常灰色到危险红色的平滑过渡
   - **最终效果**：`[圆环进度] [27k/200k]` 的直观显示格式

#### 技术要点
- **Canvas绘制**：使用Compose Canvas API绘制精确的圆环进度
- **主题一致性**：完全遵循Jewel UI设计系统的色彩规范
- **性能优化**：最小化重绘，仅在百分比变化时更新圆环颜色
- **圆角处理**：使用StrokeCap.Round实现圆润的线条端点

#### 验证结果
- ✅ **IDE成功启动**：插件正常加载，无编译错误
- ✅ **圆环显示正常**：在上下文使用指示器中正确显示百分比圆环
- ✅ **颜色系统工作**：根据使用率自动变色（基础25,926 tokens显示为正常灰色）
- ✅ **主题兼容**：支持IntelliJ的暗色和亮色主题

#### 基于Claudia项目的Token统计优化
根据对Claudia项目（~/codes/webstorm/claudia/）的深入分析，实现了更准确的token统计：

**Claudia的Token统计逻辑**：
```kotlin
// 与Claudia项目完全一致的token统计
val messageTokens = messageHistory.fold(0) { total, msg ->
    if (msg.tokenUsage != null) {
        total + msg.tokenUsage!!.inputTokens + msg.tokenUsage!!.outputTokens
    } else {
        total  // 不进行估算，仅使用精确数据
    }
}
```

**关键改进**：
- **精确统计**：仅累加Claude CLI提供的实际token使用数据
- **去除估算**：不再估算输入文本、上下文文件等token数量
- **简化逻辑**：直接使用input_tokens + output_tokens的累加
- **准确显示**：新会话显示0 tokens，而非预估的25,926 tokens

#### 用户体验改进
- **直观性提升**：用户现在可以一眼看出上下文剩余容量
- **预警功能**：颜色变化提供清晰的使用率警告
- **统计准确**：与Claudia项目保持一致的精确token计算
- **信息透明**：tooltip显示统计方式和消息数量详情

### 2025年9月10日 - UI 控件和主题高亮修复

完成了关键UI交互问题的修复，提升了用户在AI处理期间的操作体验：

#### 修复内容
1. **AI处理期间控件状态修复**（`UnifiedChatInput.kt`）
   - **问题**：AI处理期间，输入框中的所有按钮都被禁用，用户无法修改配置
   - **解决方案**：移除控件的 `&& !isGenerating` 条件，确保所有控件在AI处理期间保持可用
   - **影响文件**：`UnifiedChatInput.kt:732`, `UnifiedChatInput.kt:671`

2. **@ 符号文件引用主题感知高亮修复**（`SimpleInlineFileReference.kt`）
   - **问题**：@符号搜索文件时，关键词高亮颜色固定，在不同主题下可见性差
   - **解决方案**：实现主题感知的高亮颜色系统
   ```kotlin
   @Composable
   private fun getThemeAwareHighlightColor(): androidx.compose.ui.graphics.Color {
       val isDarkTheme = JewelTheme.isDark
       return if (isDarkTheme) {
           androidx.compose.ui.graphics.Color(0xFFFFA500) // 暗主题：亮橙色
       } else {
           androidx.compose.ui.graphics.Color(0xFF0066CC) // 亮主题：深蓝色
       }
   }
   ```
   - **影响文件**：`SimpleInlineFileReference.kt:327-336`, `SimpleInlineFileReference.kt:351`

3. **无用代码清理**
   - **清理目标**：`ChatInputContextSelectorPopup.kt` 中错误添加的主题高亮代码
   - **原因**：错误地在未使用的遗留文件中添加了高亮功能
   - **结果**：确认该文件为遗留代码，已恢复为原始状态

#### 工具集成增强
完成了工具结果在IDE中的展示功能验证：
- **Read 工具**：`ReadToolHandler.kt` 使用 `OpenFileDescriptor` 在IDE中打开文件并定位到指定行
- **Edit 工具**：`EditToolHandler.kt` 使用 `DiffManager` 显示文件变更的完整差异对比
- **工具点击管理**：`ToolClickManager.kt` 统一管理所有工具的点击处理逻辑

### 2025年8月26日 - 暗色主题完全修复

完成了关键的UI主题适配问题全面修复，彻底解决了暗色主题下文字不可见的问题：

#### 术语定义（开发必读）
为避免后续开发中的概念混淆，明确定义以下核心术语：

- **输入框 (Input Box)**：聊天界面底部的文本输入区域，用户在此输入新消息
  - **对应组件**：`UnifiedChatInput.kt`
  - **视觉位置**：界面最底部，包含文本输入域、上下文标签、模型选择器、发送按钮等
  - **用户交互**：打字、选择文件、发送消息的主要区域

- **会话列表 (Message History/Session List)**：聊天界面中间的消息历史显示区域
  - **用户消息组件**：`UserMessageDisplay.kt` - 显示用户之前发送的消息
  - **AI消息组件**：`AssistantMessageDisplay.kt` - 显示AI的回复消息  
  - **视觉位置**：界面中间滚动区域，按时间顺序显示对话历史
  - **用户交互**：查看历史对话、复制消息内容、点击文件引用等

#### 问题描述
- 在暗色主题下，**输入框**文字和光标都显示为黑色，在深色背景下几乎不可见
- **会话列表**中用户消息也存在相同的主题适配问题，历史消息无法清楚阅读
- 影响用户正常输入和阅读体验

#### 第一阶段修复：输入框 (Input Box) 组件
- **UnifiedChatInput.kt**：**输入框**核心组件
  - 添加 `cursorBrush = SolidColor(JewelTheme.globalColors.text.normal)` 配置
  - 确保光标颜色跟随主题色彩系统
  - 修复用户在底部输入区域打字时的可见性问题

- **BatchQuestionDialog.kt**：批量问题对话框
  - 修复两个 BasicTextField 实例的主题适配
  - 统一添加主题感知的文字和光标颜色配置

#### 第二阶段修复：会话列表 (Message History) 中的用户消息
- **UserMessageDisplay.kt**：**会话列表**中用户消息显示组件 **🎯 核心修复**
  - **问题根因**：原先使用 `UnifiedInputArea` → `AnnotatedMessageDisplay` → `ClickableText`，缺少主题色彩配置
  - **解决方案**：简化为直接使用 `UnifiedInputArea` 的 DISPLAY 模式，与**输入框**使用完全相同组件
  - **关键改进**：确保**会话列表**中用户消息与**输入框**视觉效果完全一致，包括主题适配

- **UnifiedInputArea.kt**：统一输入区域组件 **🔧 关键优化**
  - **DISPLAY 模式改进**：将 `AnnotatedMessageDisplay` 替换为纯 Jewel `Text` 组件
  - **主题适配增强**：使用 `JewelTheme.globalColors.text.normal` 确保文字颜色正确
  - **纯文本显示**：避免 Markdown 渲染导致的主题问题，专注于纯文本展示

#### 技术实现要点
```kotlin
// 输入框光标和文字颜色修复
import androidx.compose.ui.graphics.SolidColor

BasicTextField(
    textStyle = JewelTheme.defaultTextStyle.copy(
        color = JewelTheme.globalColors.text.normal
    ),
    cursorBrush = SolidColor(JewelTheme.globalColors.text.normal)
)

// 用户消息显示修复
SelectionContainer {
    Text(
        text = message.content,
        style = JewelTheme.defaultTextStyle.copy(
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = JewelTheme.globalColors.text.normal  // 关键修复点
        )
    )
}
```

#### 架构改进
- **组件统一化**：用户消息展示与输入框使用完全相同的基础组件 `UnifiedInputArea`
- **避免复杂渲染**：放弃 `ClickableText` 和 Markdown 渲染，使用简洁的 Jewel `Text` 组件
- **主题系统一致性**：所有文本组件统一使用 `JewelTheme.globalColors.text.normal`

#### 验证范围
已确认以下组件的主题适配完全正确：
- `AnnotatedChatInputField.kt` ✅
- `ChatInputField.kt` ✅  
- `RichTextInputField.kt` ✅
- `UserMessageDisplay.kt` ✅ **新增修复**
- `UnifiedInputArea.kt` ✅ **DISPLAY模式修复**
- `SimpleInlineFileReference.kt` ✅ **主题感知高亮修复**

#### 修复效果
- ✅ **输入框 (Input Box)** 在暗色主题下文字和光标完全可见，用户可以正常打字输入
- ✅ **会话列表 (Message History)** 中用户消息在暗色主题下完全可见，历史对话清晰可读
- ✅ 两个区域视觉效果完全统一，用户体验一致
- ✅ 避免了 ClickableText 和 Markdown 渲染的主题兼容性问题

### 2025年8月13日 - 工具显示系统全面增强

完成了工具显示系统的全面升级，显著提升了用户体验：

#### 核心改进

* **覆盖率提升**：从支持6种工具类型扩展到80+种工具的专业显示
* **MCP 集成**：为所有 MCP (Model Context Protocol) 工具实现统一展示格式
* **专业格式化**：每种工具类型都有量身定制的显示方式
* **智能分页**：大量结果自动分页，避免界面过载

#### 新增专业显示组件

* `FileMatchResultDisplay()` - Glob 文件匹配结果（列表+统计）
* `SearchResultDisplay()` - Grep/Search 搜索结果（文件:行号:内容格式）
* `WebContentDisplay()` - WebFetch 网页内容摘要
* `SubTaskDisplay()` - Task 子任务处理展示
* `NotebookOperationDisplay()` - NotebookEdit Jupyter 操作
* `MCPToolDisplay()` - MCP 工具统一格式

### 2025年8月6日 - Claude CLI 会话连续性机制调查

详细的调查过程、发现和解决方案请参考：**`docs/事件驱动架构设计.md`**

#### 关键结论摘要

基于对 Claudia 项目的深入分析和实际测试，确认了 Claude CLI 的真实行为：

* `--resume` 参数在 `--print` 模式下每次都创建新会话文件
* Claude 通过 `leafUuid` 机制保持会话连续性
* 当前文件监听系统因此失效

#### 解决方案

采用事件驱动架构替代文件监听，详细设计参考 `docs/事件驱动架构设计.md`

## 组件库

编写 compose ui 代码时，先检查 @.claude/rules/jewel-component-index.md 是否满足我们的需求
如果需要详细阅读某个组件的具体使用方式，到 @.claude/rules/jewel-components.md 中阅读详细说明
只要jewel组件库中有，必须使用 jewel组件来实现功能，实验性质的组件也使用

### 组件使用优先级

1. **优先使用标准Jewel组件** - Button, Text, TextField等
2. **避免自定义实现** - 如果Jewel有对应组件，不要用Box+clickable
3. **实验性组件必须使用** - LazyTree, EditableComboBox等即使需要@ExperimentalJewelApi也必须使用
4. **主题系统统一** - 使用JewelTheme.globalColors而非硬编码颜色

### 常用组件速查

* 按钮: Button, IconButton, IconActionButton  
* 输入: TextField, TextArea, ComboBox
* 布局: ScrollableContainer, Divider, SplitLayout
* 弹窗: Popup, PopupContainer, Tooltip
* 进度: HorizontalProgressBar(确定进度), CircularProgressIndicator(旋转动画)

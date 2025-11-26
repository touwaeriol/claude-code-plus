# IDEA 插件原生 UI ＋ 浏览器模式并行实施计划

> 更新时间：2025-11-23  
> 适用范围：`jetbrains-plugin`、`claude-code-server`、`frontend`

---

## 1. 背景与总体目标

- **现状**：工具窗口 UI 依赖 Vue+JCEF。虽然浏览器复用同一前端，但在 IDEA 内部存在性能、主题、集成度问题。
- **要求**：
  1. **保留浏览器访问能力**，前端继续通过 HTTP/WebSocket 与后端交互。
  2. **抽象统一的 IdeTools 工具层**，浏览器与 IDEA 插件共用同一套业务入口以保持行为一致。
  3. **IDEA 插件改用官方推荐栈**：Gradle + Kotlin + **Swing + IntelliJ JB UI 组件库**（`JPanel`, `JBTextArea`, `JBScrollPane` 等）+ IntelliJ Platform Gradle Plugin 2.x。
     - 可选使用 Kotlin UI DSL（`com.intellij.ui.dsl.builder`）作为声明式构建器
     - **不使用** Compose Multiplatform（非官方推荐方案）
- **成果**：在不影响浏览器体验的前提下，提供性能更佳、主题一致的原生工具窗口，并形成可审计的双端共存架构。

---

## 2. 范围

| 类型 | 说明 |
|------|------|
| ✅ In Scope | 统一 `IdeTools` 接口、插件原生 UI、新 UI DSL 组件、HTTP/WebSocket API 抽象、Gradle 构建链调整、验证策略与文档。 |
| ❌ Out of Scope | Claude SDK 深度改写、模型能力扩展、MCP 生态改造、第三方 IDE 适配。 |

---

## 3. 约束与实施原则

1. **接口查询原则**：所有 IDE API 使用前需在 `jetbrains-plugin/src/main/kotlin` 查阅现有实现或官方文档，禁止猜测。
2. **按需实现**：仅实现支持浏览器与原生 UI 共存所需的最小集合，避免重复造轮子。
3. **复用优先**：`IdeTools` 的 Kotlin 接口（`claude-code-server/src/main/kotlin/com/claudecodeplus/server/tools/IdeTools.kt`）为唯一入口；浏览器端通过 HTTP/WS 包装器调用同一实现。
4. **官方规范**：沿用 IntelliJ Platform Gradle Plugin `2.10.4`、`javaVersion=17`，使用 Swing + IntelliJ JB UI 组件构建界面，可选使用 Kotlin UI DSL（`com.intellij.ui.dsl.builder.panel { row { ... } }`）作为声明式构建器，已内置在 IntelliJ Platform SDK 中。
5. **主动验证**：每一阶段必须附带 `runIde`、`./gradlew :claude-code-server:test` 或前端 `npm run test` 结果。

---

## 4. 现状审计（2025-11-23）

### 4.1 统一工具层

- `claude-code-server/src/main/kotlin/com/claudecodeplus/server/tools/IdeTools.kt`：接口已定义；`IdeToolsMock` 支持浏览器模式降级。
- `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/plugin/tools/IdeToolsImpl.kt`：IDEA 端实现完成，包含 `openFile`、`showDiff`、`searchFiles`、`getTheme`、`setLocale`、`getProjectPath` 等。
- `jetbrains-plugin/src/main/kotlin/com/claudecodeplus/server/HttpServerProjectService.kt`：插件在 Project 服务中启动 `HttpApiServer` 并注入 `IdeToolsImpl`。

### 4.2 插件 UI

- `NativeToolWindowFactory.kt` + `ChatPanel.kt` + `ChatViewModel.kt`：已有 Swing 版本聊天面板、ToolCall 渲染（`MessageDisplay`/`ToolCallDisplay`）。  
- **缺口**：尚未使用 Kotlin UI DSL（`com.intellij.ui.dsl.builder`）；输入框、消息列表等仍为手写 Swing；缺少 `panel { }` DSL 与数据绑定；主题/尺寸逻辑散落多个类。

### 4.3 浏览器与 HTTP/WS

- `HttpApiServer.kt`：统一 HTTP + SSE + WebSocket 入口，复用 `IdeTools`（IDEA 模式）或 `IdeToolsMock`（Standalone 模式）。
- `frontend/src/services/ideaBridge.ts`：HTTP API 包装器已经与 `IdeTools` 动作对齐。
- `frontend/src/services/ClaudeSession.ts`：WebSocket RPC 客户端具备 connect/query/interrupt 等方法，但 IDE 插件尚未使用（仍直接调用 SDK）。

### 4.4 主要缺陷

1. 原生 UI 未转入 UI DSL，布局维护成本高。
2. Tool 调用/消息状态未抽象成 ViewModel + DSL Binding。
3. `IdeTools` 暂未发布到公共 Kotlin module，IDEA 插件与服务器通过源码依赖耦合。
4. 浏览器 WebSocket 与插件 SDK 逻辑分裂，缺少统一仿真层。

### 4.5 Vue vs Swing UI 核心差异（2025-11-24 补充）

- **消息架构**：Vue 前端使用 `DisplayItem` 类型系统（UserMessage、AssistantText、ToolCall、SystemMessage），配合 `displayItemConverter.ts` 与 `MessageList.vue` 实现组件化分发；Swing 侧仍停留在 `Message(type, content)` 的扁平结构，无法区分流式文本与工具调用，导致工具内容展示成纯文本。
- **StreamEvent 交互**：浏览器端的 `streamEventProcessor.ts` 会处理 `message_start/content_block_delta/message_stop`，实时更新 Delta、工具输入 JSON 以及 thinking 块；Swing 版在 `ChatViewModel.receiveResponse()` 中仅记录日志，缺少增量渲染，用户看不到 Claude 的思考过程、工具入参构建，也无法在工具执行前中断。
- **工具卡片**：Vue 端拥有 30+ 专用 Tool 组件（Read/Edit/Write/MultiEdit/Bash/Grep/TodoWrite/MCP 等），同时提供 `CompactToolCard` 折叠展示；Swing 仅有单一 `ToolCallDisplay.kt`，缺乏文件路径、高亮 diff、命令输出等关键信息，也没有可点击打开文件或显示 Diff 的 UI 元素。
- **输入区域**：Vue `ChatInput.vue` 集成上下文标签、拖拽文件、@ 文件引用、模型/权限下拉、token 统计与任务队列；Swing `ChatPanel` 只有 `JBTextArea + JButton`，缺少上下文可视化与模型切换，无法提示当前 token 使用情况。
- **滚动与性能**：Vue 使用虚拟列表（`VirtualList`）保证大量消息时的流畅滚动；Swing 现有 `BoxLayout + JBScrollPane` 会在长会话中频繁触发布局计算，且没有惰性加载策略。
- **主题一致性**：Vue 通过 CSS 变量与 `themeStore` 适配暗/亮主题，工具卡、消息卡具备统一圆角、阴影与 hover 状态；Swing 仍沿用默认 `JPanel` 背景，缺少集中式 `UiPalette` 与指标体系，暗色模式下对比度不足。

---

## 5. 目标架构概览

```
                 +------------------------------+
                 |   claude-code-server (Ktor)  |
                 |  - HttpApiServer             |
                 |  - WebSocketHandler          |
                 |  - IdeTools (interface)      |
                 +---------------+--------------+
                                 |
            +--------------------+--------------------+
            |                                         |
   Browser Frontend (Vue)                  IDEA Plugin (Kotlin)
   - HTTP via ideaBridge.ts                - Kotlin UI DSL ToolWindow
   - WebSocket ClaudeSession               - ChatViewModel + DSL
   - Uses IdeToolsMock actions             - Uses IdeToolsImpl actions
```

- **行为一致**：所有"打开文件/显示 Diff/locale"调用都落在同一 `IdeTools` 接口。
- **UI 双轨**：浏览器仍是 Vue；IDEA 工具窗口使用 **Swing + IntelliJ JB UI 组件**（可选配合 Kotlin UI DSL 作为声明式构建器）。
- **构建链**：Gradle 维持 `org.jetbrains.intellij.platform` 2.x，前端通过 Vite 构建后被 `jetbrains-plugin` 打包。

---

## 6. 里程碑与交付物

| 阶段 | 时间 | 目标 | 关键交付物 | 验收 |
|------|------|------|------------|------|
| Phase 0：基线确认 | 立即（1天） | 梳理依赖、目录、约束 | 本文档、责任人列表、追踪表 | 负责人认可 |
| Phase 1：工具层整合 | 3 天 | `IdeTools` 产物化、公共 module 化 | `claude-code-server` & `jetbrains-plugin` 共用 `:claude-agent-sdk:idetools` 子模块、接口文档 | `./gradlew build` 通过 |
| Phase 2：Kotlin UI DSL 工具窗口 | 5 天 | Swing UI → Kotlin UI DSL v2 | `ChatPanelDsl.kt`、`ChatViewModel` 绑定、DSL 主题策略、快速操作条 | `runIde` 实测、UI 评审 |
| Phase 3：浏览器协同 & API 净化 | 4 天 | HTTP/WS API 对齐 DSL 行为 | `ideaBridge`/`ClaudeSession` 重构、批操作、缓存、Tool 回放 | 前端 `npm run test`、功能对比 |
| Phase 4：验证与交付 | 2 天 | 性能/回归/文档 | Benchmarks、QA checklist、更新 `README/CHANGELOG` | Demo & 文档签收 |

---

## 7. 工作拆解（WBS）

| 编号 | 模块 | 任务 | 输出 | 前置 |
|------|------|------|------|------|
| T1 | 工具层 | 将 `IdeTools`/`DiffRequest` 抽到 `:claude-agent-sdk:idetools`，JetBrains & Ktor 端依赖同一 module | `claude-agent-sdk/idetools/build.gradle.kts`、API 文档 | Phase 0 |
| T2 | 工具层 | 为 `IdeTools` 所有方法编写契约测试（IDEA 使用 Mock 项目，浏览器使用 `IdeToolsMock`） | `IdeToolsContractTest.kt` | T1 |
| T3 | 插件 UI | 构建 `ChatPanel`（使用 Swing + JB UI 组件，可选 Kotlin UI DSL `panel { row { } }`）并迁移输入区域、按钮、快捷操作 | Swing 面板类、`NativeToolWindowFactory` 注入 | T1 |
| T4 | 插件 UI | 将消息流 + ToolCall 渲染改成 Swing 组合组件（`JBScrollPane` + 自定义面板） | `MessageListPanel.kt`、`ToolCallList.kt` | T3 |
| T5 | 插件 UI | 实现 Markdown 渲染（使用 CommonMark + JEditorPane 或自定义渲染器） | Markdown 渲染组件 | T4 |
| T6 | 插件 UI | 将 `ChatViewModel` 连接层抽象成状态容器（`StateFlow<UiState>`），通过 `SwingUtilities.invokeLater` 更新 UI | `ChatUiState.kt`、`MessageStore.kt` | T3 |
| T7 | 插件 UI | 集成 `IdeaBridge` HTTP Server 状态：显示本地 / 浏览器连接状态、提供"在浏览器打开"操作 | 顶部状态条组件 | T3 |
| T8 | 浏览器 | 将 `IdeTools` 相关 HTTP 请求封装成统一 `toolsClient.ts`，并与 Swing 工具按钮动作一致 | `frontend/src/services/toolsClient.ts` | T1 |
| T9 | 浏览器 | `ClaudeSession` 统一 RPC 协议，与 IDE ViewModel 对齐（消息/工具事件） | 统一事件 payload | T8 |
| T10 | QA | 跑通 `./gradlew :jetbrains-plugin:runIde`、`./gradlew :claude-code-server:test`、`npm run test` 并记录 | `docs/verification/native-ui-checklist.md` | Phase 2-4 |
| T11 | 文档 | 更新 `README.md` + `docs/ARCHITECTURE_MIGRATION.md` + 发布说明 | 版本说明 & 对比截图 | Phase 4 |

---

## 8. 验证策略

1. **IDEA 端**：使用 `runIde` + 内置 Sample 项目验证 `openFile`、`showDiff`、ToolCall 显示、主题切换。
2. **浏览器端**：`npm run dev` + `npm run test`，确保 HTTP 工具调用返回值与 IDE 同步。
3. **工具层契约**：新增共享测试，分别注入 `IdeToolsImpl` 与 `IdeToolsMock`，验证 `Result` 行为与异常链。
4. **性能**：记录 JCEF 版 vs Swing 原生版内存/CPU/加载时长，对比 `jstat` + IntelliJ Diagnostic。

---

## 9. 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| IntelliJ Platform API 变更 | 编译失败 | 中 | 锁定 IntelliJ 平台版本 2025.2.4，使用 `intellij-platform` BOM。 |
| Tool 调用与浏览器行为不一致 | 功能缺失 | 高 | 统一 `IdeTools` 接口 + 端到端测试(T2、T8)。 |
| Markdown 渲染性能问题 | 体验差 | 中 | 使用 CommonMark 库 + 异步渲染，并缓存渲染结果。 |
| HTTP/WS 并行导致资源竞争 | 稳定性 | 中 | `HttpServerProjectService` 中加入运行模式检测，避免重复端口。 |

---

## 10. 同步机制

- **日常同步**：每日提交 `docs/MIGRATION_PROGRESS.md`（或在此文档追加日志段）。
- **代码评审**：所有 UI 变更需附带截图/短视频。
- **发布节奏**：Phase 2 完成后可先发布“实验性原生 UI”供内测，浏览器版本继续作为默认。

---

## 11. 计划审核清单

- [ ] 是否明确两个运行面（浏览器 / IDEA）职责边界？
- [ ] `IdeTools` 是否仅在一个 module 定义？
- [ ] 是否列出 Swing + JB UI 组件的使用策略和影响范围？
- [ ] 每个阶段是否有可验证交付物和验收标准？
- [ ] 是否提供了风险及 fallback 策略？

审核者在每项完成后勾选；如有疑问需在实现前澄清。

---

## 12. 进度追踪快照（2025-11-23）

| 项目 | 状态 | 证据/说明 |
|------|------|-----------|
| `IdeTools` 接口 & Mock | ✅ 已完成 | `claude-code-server/.../IdeTools.kt` & `IdeToolsMock.kt`。 |
| IDEA 端 `IdeToolsImpl` | ✅ 已完成 | `jetbrains-plugin/.../IdeToolsImpl.kt`，含 `openFile`、`showDiff`。 |
| HTTP Server 注入统一工具层 | ✅ 已完成 | `HttpServerProjectService.kt` 在启动时构造 `IdeToolsImpl` 并传入 `HttpApiServer`。 |
| 原生 ToolWindow 骨架 | ✅ 已完成 | `NativeToolWindowFactory.kt` + `ChatPanel.kt` + `MessageDisplay.kt` 等。 |
| Swing UI 优化 | ⏳ 进行中 | 当前 UI 是手写 Swing，可选择性引入 Kotlin UI DSL 作为声明式构建器。 |
| ViewModel <-> UI 状态绑定 | ⏳ 未开始 | `ChatViewModel` 暂通过回调更新 Swing 组件，需改为 `StateFlow` + `SwingUtilities.invokeLater` 模式。 |
| 浏览器 Tool API 封装统一 | ⏳ 进行中 | `ideaBridge.ts` 已有 `ide.*` actions，但 WebSocket/Tool 接口仍待合并。 |
| 端到端测试与基准 | ⏳ 未开始 | 尚未看到新的测试脚本/Benchmarks。 |

> 以上状态用于后续每次评审时对照更新。

---

如需补充，请在 `docs/IDEA_NATIVE_UI_AND_BROWSER_PLAN.md` 中继续维护。

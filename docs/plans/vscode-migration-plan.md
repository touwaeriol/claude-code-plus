# IDE API 统一计划（VS Code + IDEA 共用同一套前后端协议：`ide.*`）

本文档是“可执行的迁移计划 + 进度看板”，目标是让 **同一套 `frontend/`** 能在 **IDEA 插件** 与 **VS Code 扩展**里复用：把所有“前后端交互相关”的命名统一为 **`ide`**（不保留任何 `jetbrains` / `idea` 兼容别名）。

约束（按你的最新说明）：
- **只改交互，不强制改内部代码**：内部模块/文件/类名不要求统一或重构；仅在“对外 API 契约”需要时做最小调整。
- **协议统一**：前端 <-> 后端交互只允许 `ide.*`（HTTP action 与 RSocket route），不允许出现 `jetbrains.*` / `idea.*`。
- **内部实现允许平台前缀**：后端内部可以继续使用 `jetbrains/*` 或 `vscode/*` 来承载平台差异，但对外仍是 `ide.*`。
- **不做兼容**：不保留旧路由映射，不同时监听旧 endpoint path。
- **允许简化/替代**：若某能力在 VS Code/Node 或 IntelliJ 平台上无法等价实现，允许做简化/替代，但必须保证“协议不变 + 明确错误/降级行为”。

---

## 0. 使用方式（如何记录进度）

本文件分为两部分：
- **方案**：目标、命名约束、（可选）模块/文件对照。
- **TODO List**：按“可落地的改动点”拆分（命名/路由/注入/Settings UI/能力缺口），每项都给出验收标准与测试建议。

进度记录规则：
- TODO 用 `[ ]`，进行中用 `[~]`，完成用 `[x]`，阻塞用 `[!]`。
- 每次推进后，更新对应 TODO 的状态，并在文末“进度日志”追加一条记录（日期 + 变更摘要 + 影响范围）。

---

## 1. 协议/命名强约束（必须先统一）

### 1.1 HTTP（前端 `fetch`/`ideaBridge.query`）

- 统一入口：`POST /api/`
- action 统一前缀：`ide.*`、`settings.*`、`models.*`、`history.*`、`node.*`、`mcp.*`（允许，但必须清晰归类）
- 禁止：`jetbrains.*`、`idea.*`

### 1.2 RSocket（WebSocket transport）

- AI 会话流式：`/rsocket`
  - route 前缀：`agent.*`
- IDE 集成能力：`/ide-rsocket`
  - route 前缀：`ide.*`
- 禁止：`/jetbrains-rsocket`、`/idea-rsocket`
- 禁止：`jetbrains.*`、`idea.*` route

### 1.3 Webview 注入全局变量

统一为：
- `window.__serverUrl`：HTTP baseUrl（例如 `http://127.0.0.1:<port>`）
- `window.__serverToken`：本地服务 token（HTTP header + WS query param）
- `window.__IDE_MODE__ = true`：表示“在 IDE 宿主内运行”（IDEA / VS Code 都算）

禁止：
- `__IDEA_MODE__`（需要迁移为 `__IDE_MODE__`）

### 1.4 MCP 命名（重要：这是“前后端交互”的一部分）

现状工具名里包含 `mcp__jetbrains-...`，这是宿主绑定命名，计划统一为：
- `mcp__ide-file__*`
- `mcp__ide-terminal__*`
- `mcp__ide-git__*`
- `mcp__ide-lsp__*`（可选：能力取决于 VS Code Provider）
- `mcp__user-interaction__*`（保持通用）

> 注：这需要同时改前端工具卡片展示、拦截器、以及后端 MCP server 注册名。

---

## 2. （可选）后端目录对齐（仅在需要整理 VS Code 后端实现时使用）

如果你的目标仅是“统一前后端交互为 `ide.*`”，这一节可以先跳过；只有当我们希望 VS Code 端后端也按 Kotlin 后端的模块边界拆分、便于长期同步维护时，才建议做目录/文件对齐。

参考目录（可选）：让 TS 目录与 Kotlin `ai-agent-server/src/main/kotlin/com/asakii/server` 同构：

```
vscode-extension/src/server/
  HttpApiServer.ts                // Kotlin: HttpApiServer.kt
  HttpApiModels.ts                // Kotlin: HttpApiModels.kt
  StandaloneServer.ts             // Kotlin: StandaloneServer.kt (dev only)
  ConnectionState.ts              // Kotlin: ConnectionState.kt
  CodexHistoryHelper.ts           // Kotlin: CodexHistoryHelper.kt

  config/
    AiAgentServiceConfig.ts       // Kotlin: config/AiAgentServiceConfig.kt

  rpc/
    AiAgentRpcServiceImpl.ts      // Kotlin: rpc/AiAgentRpcServiceImpl.kt
    ClientCaller.ts               // Kotlin: rpc/ClientCaller.kt
    ClientCallerRegistry.ts       // Kotlin: rpc/ClientCallerRegistry.kt

  rsocket/
    RSocketHandler.ts             // Kotlin: rsocket/RSocketHandler.kt
    RSocketErrorCodes.ts          // Kotlin: rsocket/RSocketErrorCodes.kt
    ProtoConverter.ts             // Kotlin: rsocket/ProtoConverter.kt
    LoadHistoryRequest.ts         // Kotlin: rsocket/LoadHistoryRequest.kt
    transports/                   // TS 专用：ws/http upgrade router

  history/
    HistoryJsonlLoader.ts         // Kotlin: history/HistoryJsonlLoader.kt
    CodexHistoryMapper.ts         // Kotlin: history/CodexHistoryMapper.kt
    HistoryStore.ts               // TS 现有实现将收敛到这里

  services/
    FileContentCache.ts           // Kotlin: services/FileContentCache.kt
    TempImageService.ts           // Kotlin: services/TempImageService.kt

  settings/
    ClaudeSettings.ts             // Kotlin: settings/ClaudeSettings.kt
    ClaudeSettingsLoader.ts       // Kotlin: settings/ClaudeSettingsLoader.kt
    ClaudeSettingsPaths.ts        // Kotlin: settings/ClaudeSettingsPaths.kt

  tools/
    IdeToolsDefault.ts            // Kotlin: tools/IdeToolsDefault.kt

  mcp/
    McpProviders.ts               // Kotlin: mcp/McpProviders.kt
    McpCallContext.ts             // Kotlin: mcp/McpCallContext.kt
    McpHttpGateway.ts             // Kotlin: mcp/McpHttpGateway.kt
    McpServerWithConnectId.ts     // Kotlin: mcp/McpServerWithConnectId.kt
    schema/
      ToolSchemaLoader.ts         // Kotlin: mcp/schema/ToolSchemaLoader.kt
      SchemaValidator.ts          // Kotlin: mcp/schema/SchemaValidator.kt
    vscode/
      IdeFileMcpServerProvider.ts     // Kotlin 对照: JetBrainsFileMcpServerProvider.kt（语义复刻，名字更通用）
      IdeTerminalMcpServerProvider.ts // Kotlin 对照: TerminalMcpServerProvider.kt
      IdeGitMcpServerProvider.ts      // Kotlin 对照: GitMcpServerProvider.kt
      IdeLspMcpServerProvider.ts      // Kotlin 对照: JetBrainsMcpServerProvider.kt（语义复刻）
      UserInteractionMcpServer.ts     // Kotlin 对照: UserInteractionMcpServer.kt

  codex/
    CodexBackendProvider.ts       // Kotlin: codex/CodexBackendProvider.kt
    CodexProcessManager.ts        // Kotlin: codex/CodexProcessManager.kt
    CodexJsonRpcClient.ts         // Kotlin: codex/CodexJsonRpcClient.kt

  util/
    JsonTools.ts                  // Kotlin: util/JsonTools.kt

  vscode/                         // VS Code 宿主实现（对外暴露 ide.*）
    ideBridge.ts                  // openFile/showDiff/search/getTheme/...
    terminalTaskManager.ts        // 复刻“后台终端任务”
    snapshotStore.ts              // 复刻“回滚快照”
```

> 迁移策略：先做“目录/文件重命名与搬迁（不改行为）”，再逐模块实现缺失能力，最后统一协议命名与删掉旧逻辑。

---

## 3. （可选）后端文件对照（Kotlin -> TS）

### 3.0（可选）如果要做 Kotlin -> TS 迁移时的规则（允许差异/简化）

允许“翻译后不完全一致”，但必须遵循以下规则：
- **对外不变**：HTTP action 与 RSocket route 仍以 `ide.*` 为准，且请求/响应结构保持稳定（前端不改或最小改动）。
- **文件仍保留**：即使功能被简化，也要保留对应 TS 文件（不要把多个 Kotlin 文件随意揉成一个 TS 文件）。可以在 TS 文件里委托给公共实现，但文件本身必须存在，便于 1:1 对照与后续补齐。
- **差异可见**：每个有简化/替代的文件必须：
  - 在 TS 文件顶部写明 `// Source: <Kotlin path>`
  - 标注 `// Differences:`（列出：为什么不能等价 + 当前降级行为 + 未来补齐方向）
  - 在本计划 TODO 里将该文件标记为 `DONE (simplified)` 或保留为 `WIP`，并说明缺口。
- **可运行优先**：若某能力在 VS Code 端不存在（例如 IDEA 的 DiffManager/特定 PSI API），优先用 VS Code API 做可用替代（例如 `vscode.commands.executeCommand('vscode.diff', ...)`），其次做“不可用但不崩溃”的降级（返回明确错误码/提示 UI）。
- **测试最小闭环**：每个迁移模块至少提供一个可重复的验证方式（最小脚本/单测/手动步骤），避免“看起来像翻译，实际上跑不起来”。

### 3.1 `ai-agent-server/` 对照表（必须 1:1 覆盖）

状态含义：
- TODO：未开始
- WIP：进行中
- DONE：完成（含测试）

| Kotlin 源文件 | TS 目标文件（vscode-extension/src/server） | 复刻职责要点 | 验收标准 | 状态 |
|---|---|---|---|---|
| ai-agent-server/.../HttpApiServer.kt | HttpApiServer.ts | HTTP server + 静态资源注入 + `/api/` action 分发 + WS/RSocket endpoints | 前端在 VS Code/IDEA 两端都能 `fetch /api/`；token 校验；端口随机；不依赖旧路径 | DONE (simplified) |
| ai-agent-server/.../HttpApiModels.kt | HttpApiModels.ts | `FrontendRequest/FrontendResponse` 等模型对齐 | 前端类型对齐；错误码/字段一致 | DONE (simplified) |
| ai-agent-server/.../StandaloneServer.kt | StandaloneServer.ts | 开发态独立启动（非 VS Code） | `node StandaloneServer.ts` 可在固定端口启动（dev） | TODO |
| ai-agent-server/.../ConnectionState.kt | ConnectionState.ts | 连接状态模型（用于 UI/会话状态） | 状态枚举与转换逻辑一致 | TODO |
| ai-agent-server/.../CodexHistoryHelper.kt | CodexHistoryHelper.ts | Codex 历史/会话辅助逻辑 | 历史列表、删除、metadata 与前端对齐 | TODO |
| ai-agent-server/.../config/AiAgentServiceConfig.kt | config/AiAgentServiceConfig.ts | 服务配置来源与默认值（VS Code: settings/secrets） | 默认值与 IDE 版一致；可覆盖 | TODO |
| ai-agent-server/.../rpc/AiAgentRpcServiceImpl.kt | rpc/AiAgentRpcServiceImpl.ts | 核心 RPC：connect/query/interrupt/history/permissions/tools | Claude/Codex 两后端都能跑通最小对话；流式事件正确 | TODO |
| ai-agent-server/.../rpc/ClientCaller.kt | rpc/ClientCaller.ts | 服务器反向调用前端（client.call）抽象 | tool 卡片点击、回滚/终端推送可用 | TODO |
| ai-agent-server/.../rpc/ClientCallerRegistry.kt | rpc/ClientCallerRegistry.ts | 多 client 连接管理（广播/定向） | 多 webview 实例同时在线不串线 | TODO |
| ai-agent-server/.../rsocket/RSocketHandler.kt | rsocket/RSocketHandler.ts | route 分发与 stream 生命周期管理 | `/rsocket` route 全量对齐；错误码一致 | TODO |
| ai-agent-server/.../rsocket/RSocketErrorCodes.kt | rsocket/RSocketErrorCodes.ts | 错误码表与映射 | 前端错误展示一致 | TODO |
| ai-agent-server/.../rsocket/ProtoConverter.kt | rsocket/ProtoConverter.ts | proto <-> domain 转换层 | 前端 protobuf 解码与字段完全匹配 | TODO |
| ai-agent-server/.../rsocket/LoadHistoryRequest.kt | rsocket/LoadHistoryRequest.ts | load history PB 适配 | history pb 接口可用 | TODO |
| ai-agent-server/.../history/HistoryJsonlLoader.kt | history/HistoryJsonlLoader.ts | JSONL 历史加载与解析 | 能加载/迁移旧历史格式（如存在） | TODO |
| ai-agent-server/.../history/CodexHistoryMapper.kt | history/CodexHistoryMapper.ts | Codex 历史格式映射 | 前端能显示 codex 会话 | TODO |
| ai-agent-server/.../logging/StandaloneLogging.kt | logging/StandaloneLogging.ts | 日志：文件 + 输出面板 + 分级 | 可定位问题；不刷屏；支持 debug 开关 | TODO |
| ai-agent-server/.../services/FileContentCache.kt | services/FileContentCache.ts | 文件内容缓存（减少 IO） | 性能与一致性可控；支持失效 | TODO |
| ai-agent-server/.../services/TempImageService.kt | services/TempImageService.ts | 临时图片处理（如有） | 图片工具链可用/可禁用 | TODO |
| ai-agent-server/.../settings/ClaudeSettings.kt | settings/ClaudeSettings.ts | Claude 配置模型 | 与前端 settings schema 对齐 | TODO |
| ai-agent-server/.../settings/ClaudeSettingsLoader.kt | settings/ClaudeSettingsLoader.ts | Claude 配置加载（IDE/用户配置） | VS Code: workspace/user settings + secret | TODO |
| ai-agent-server/.../settings/ClaudeSettingsPaths.kt | settings/ClaudeSettingsPaths.ts | 配置文件路径策略 | Win/mac/linux 路径正确 | TODO |
| ai-agent-server/.../tools/IdeToolsDefault.kt | tools/IdeToolsDefault.ts | 默认 IDE 工具集合（读写/搜索/终端/回滚等） | 工具列表与权限提示一致 | TODO |
| ai-agent-server/.../util/JsonTools.kt | util/JsonTools.ts | JSON 工具/容错解析 | 解析策略一致（避免 UI 崩） | TODO |
| ai-agent-server/.../codex/CodexBackendProvider.kt | codex/CodexBackendProvider.ts | Codex provider 抽象与调度 | Codex 可 connect/query/stream | TODO |
| ai-agent-server/.../codex/CodexProcessManager.kt | codex/CodexProcessManager.ts | codex app-server 进程管理 | 自动启动/重启/退出清理 | TODO |
| ai-agent-server/.../codex/CodexJsonRpcClient.kt | codex/CodexJsonRpcClient.ts | JSON-RPC 客户端 | 与 `external/openai-codex` 协议对齐 | TODO |
| ai-agent-server/.../mcp/McpProviders.kt | mcp/McpProviders.ts | MCP provider registry | 可按 settings 启停 server | TODO |
| ai-agent-server/.../mcp/McpHttpGateway.kt | mcp/McpHttpGateway.ts | MCP HTTP 网关（如需要） | 与前端/agent 交互一致 | TODO |
| ai-agent-server/.../mcp/McpCallContext.kt | mcp/McpCallContext.ts | MCP 调用上下文（session/project） | tool 执行能拿到上下文 | TODO |
| ai-agent-server/.../mcp/McpServerWithConnectId.kt | mcp/McpServerWithConnectId.ts | MCP server 包装（connectId） | 多 session/多 server 管理一致 | TODO |
| ai-agent-server/.../mcp/schema/ToolSchemaLoader.kt | mcp/schema/ToolSchemaLoader.ts | tool schema 加载 | schema 校验可用 | TODO |
| ai-agent-server/.../mcp/schema/SchemaValidator.kt | mcp/schema/SchemaValidator.ts | schema 校验 | 非法 schema 不致崩 | TODO |
| ai-agent-server/.../mcp/UserInteractionMcpServer.kt | mcp/vscode/UserInteractionMcpServer.ts | 用户交互 MCP（通用） | AskUserQuestion/confirm 等可用 | TODO |
| ai-agent-server/.../mcp/TerminalMcpServerProvider.kt | mcp/vscode/IdeTerminalMcpServerProvider.ts | 终端 MCP（VS Code） | tool: terminal list/read/kill 等可用 | TODO |
| ai-agent-server/.../mcp/GitMcpServerProvider.kt | mcp/vscode/IdeGitMcpServerProvider.ts | Git MCP（VS Code） | commit msg/generate 等可用 | TODO |
| ai-agent-server/.../mcp/JetBrainsFileMcpServerProvider.kt | mcp/vscode/IdeFileMcpServerProvider.ts | 文件 MCP（VS Code） | 读写编辑文件 + 回滚标记 | TODO |
| ai-agent-server/.../mcp/JetBrainsMcpServerProvider.kt | mcp/vscode/IdeLspMcpServerProvider.ts | LSP/索引 MCP（VS Code） | search/refs/rename 等（依赖 provider） | TODO |

### 3.2 SDK 复刻范围（按“逐文件翻译”执行）

> 目的：保证 VS Code 后端的 provider/stream/permission/tool 逻辑不是“临时拼装”，而是复刻 Kotlin SDK 的抽象层与数据流。

- Kotlin: `ai-agent-sdk/`（`src/main/kotlin` 全量翻译；`src/test` 不翻译，改用 TS/Node 的测试体系）
- Kotlin: `claude-agent-sdk/`（同上）
- Kotlin: `codex-agent-sdk/`（同上）

TS 放置建议（仍在 `vscode-extension/src/server` 内）：

```
vscode-extension/src/server/sdk/
  ai-agent/...
  claude-agent/...
  codex-agent/...
```

是否“文件 1:1 对照”：
- 核心文件：要求 1:1（类名/职责可调整，但必须能对照到 Kotlin 源文件）。
- examples：不迁移，但要在 TODO 里标记为 “SKIP（example）”。

---

## 4. VS Code 设置界面（必须按 IDEA 菜单结构复刻）

需求（按你的截图口径）：
- 菜单结构复刻 IDEA：
  - Claude Code Plus
    - Claude Code
    - Codex
    - MCP
    - Git Generate
- 打开方式：
  - 用户点击聊天页的齿轮按钮（或命令）后，
  - **在 VS Code 中央编辑区**打开一个 Settings 页面（不是弹窗/overlay）。

实现方案（推荐）：
- VS Code 侧新增一个 `WebviewPanel`：`claudeCodePlus.settings`
  - `ViewColumn.Active` / `ViewColumn.One` 打开在中间编辑区
  - 加载同一套 `frontend/dist`，但注入一个初始页面标记：
    - `window.__ccpInitialPage = 'settings'`
    - `window.__ccpInitialSettingsSection = 'mcp' | 'claude' | 'codex' | 'git'`
- 前端新增一个“页面级路由/开关”，在 `App.vue` 根据 `__ccpInitialPage` 决定渲染：
  - Chat 主界面（现有）
  - Settings 主界面（新建，包含左侧菜单 + 右侧内容）

> 注意：这里的“Settings 主界面”不是 `SettingsPanel.vue` overlay，而是一个独立的 view（可复用其内部表单组件/字段）。

---

## 5. TODO List（可执行、可验收）

### 5.1 协议统一（只改交互，不做兼容）

- [x] 统一前端/后端所有 `idea.*`/`jetbrains.*` route 为 `ide.*`（HTTP + RSocket）
- [x] RSocket endpoint path：`/idea-rsocket` -> `/ide-rsocket`（全仓库改动，包含注入与 connect url）
- [x] 全局变量：`__IDEA_MODE__` -> `__IDE_MODE__`（前端检测 + VS Code/IDEA 注入）
- [ ] MCP server/tool 名称：`mcp__jetbrains-*` -> `mcp__ide-*`（前端拦截器 + 展示 + 后端 provider 注册）
- [x] 删除所有兼容逻辑（normalize/alias/多 endpoint 同时监听）

验收：
- 全仓库不再出现旧协议字符串：`/jetbrains-rsocket`、`/idea-rsocket`、`jetbrains.*`、`idea.*`（允许文件路径/目录名出现 `jetbrains/`，但不允许出现在 action/route/endpoint 字符串里）
- 前端能正常聊天、打开文件、diff、回滚、终端后台，不依赖任何旧名字

### 5.2（可选）后端目录/结构对齐（不影响 `ide.*` 统一）

- [ ] 在 `vscode-extension/src/server` 建立目标目录结构（见第 2 节）
- [ ] 将现有实现（localServer/apiHandlers/rsocket/*/history/*/rollback/*/terminal/*）搬迁并拆分到对照文件
- [ ] 引入统一日志模块（替换散落的 console.log）
- [ ] 补齐最小单元测试（至少：路由分发、token 校验、history pb 编解码）

验收：
- `npm run compile` 通过
- `frontend build + dev:launch` 可用
- 逻辑行为与当前版本一致（只是文件位置变化）

### 5.3（可选）按 Kotlin 文件逐个复刻（仅当 VS Code 端缺能力时）

按 3.1 表格逐项推进，每完成一项必须：
- [ ] 写清“与 Kotlin 的差异点”（如 VS Code API 限制）
- [ ] 增加最小测试/可复现脚本（能回归）

### 5.4 Settings 主界面（中央编辑区）

- [ ] VS Code：新增 `claudeCodePlus.openSettings` 打开 `WebviewPanel`（中间编辑区）
- [ ] VS Code：在 Chat 右上角齿轮按钮触发打开 Settings 页面（不是 overlay）
- [ ] 前端：新增 Settings 主界面（左侧菜单复刻 IDEA：Claude Code/Codex/MCP/Git Generate）
- [ ] 前端：每个子页字段与现有 settings store 对齐；保存后能回写 VS Code settings + secrets，并通知 Chat 刷新

验收：
- 点击齿轮：中间编辑区出现 Settings Tab
- 左侧菜单与 IDEA 对齐；切换无刷新
- 保存后 Chat 立即生效（model/permission/mcp 开关等）

---

## 6. 进度日志

- 2026-01-26：新增 `HttpApiModels.ts`（FrontendRequest/FrontendResponse TS 版），并在 `apiHandlers.ts` 引用
- 2026-01-26：新增 `HttpApiServer.ts`（VS Code 版），替换 `LocalServer` 引用并删除旧文件；标记为 DONE (simplified)
- 2026-01-26：IDE 模式不再回退：缺失 `window.__serverUrl` 直接报错（`frontend/src/utils/serverUrl.ts`、`frontend/src/services/ideaBridge.ts`、`frontend/src/main.ts`），并同步 VS Code 前端构建产物
- 2026-01-26：创建迁移总计划文档（本文件），作为后续唯一进度来源（TODO/DONE 以此为准）
- 2026-01-25：删除所有 `idea.*`/`jetbrains.*` 协议兼容与自动回退；统一为 `ide.*` + `/ide-rsocket` + `__IDE_MODE__`（前端/VS Code/IDEA 后端均已对齐）

---

## 7. 待你决策（我不擅自定，先记录，等你拍板）

> 说明：这些点都不影响“先把协议统一为 `ide.*`”的主线，但会影响 VS Code 端的实现复杂度、体验与后续维护成本。

### D1：浏览器开发模式是否保留？

背景：当前 `frontend/` 仍支持 Vite + StandaloneServer（8765）调试；这本质是一条“非 IDE 宿主”的运行路径。

可选：
- 方案 A（保留）：继续保留 browser/dev 分支（但 IDE 模式禁止任何自动回退）。
- 方案 B（移除）：只支持 IDE 宿主（IDEA/VS Code），不再承诺浏览器模式。

需要你确认：选 A 还是 B。

### D2：Codex app-server 分发策略（VS Code 侧）

可选：
- 方案 A：扩展内置二进制/脚本（多平台体积大，发布复杂）
- 方案 B：首次启动下载（需要下载源、校验、镜像策略）
- 方案 C：依赖用户本地安装（实现最省事，但体验差）

需要你确认：优先哪一套。

### D3：终端“转后台”在 VS Code 的呈现方式

可选：
- 方案 A：仅在 Webview 内展示任务输出/状态（实现最简单）
- 方案 B：同步到 VS Code Terminal（PseudoTerminal/pty），Webview 也展示（体验更像 IDEA）

需要你确认：优先 A 还是 B。

### D4：文件回滚（快照）存储与清理策略

可选：
- 方案 A：只存 `beforeContent`（节省空间），按 `toolUseId`/timestamp 索引；TTL + 最大条数 + 最大占用清理
- 方案 B：存完整版本链（更强但更占空间）

需要你确认：优先 A 还是 B，以及默认 TTL/上限（例如 7 天 / 500 条 / 200MB）。

### D5：Multi-root 工作区路径契约

可选：
- 方案 A：前端/协议统一使用绝对路径（最简单；UI 显示可额外返回 relativePath）
- 方案 B：允许 `<workspaceFolderName>/<relativePath>`（需要明确解析规则与冲突策略）

需要你确认：优先 A 还是 B（或两者都支持，但不做“自动回退/猜测”）。

### D6：历史数据/旧格式兼容是否移除？

背景：当前前端仍保留部分“旧消息格式/旧 tool 字段”的解析与兼容（不影响 IDE 协议，但会影响历史消息/旧数据能否继续展示）。

可选：
- 方案 A：保留（默认，更安全；不影响旧历史展示）
- 方案 B：移除（更干净，但会导致旧历史/旧格式消息无法读取）

需要你确认：优先 A 还是 B。

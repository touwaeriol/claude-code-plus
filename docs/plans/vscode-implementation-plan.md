# VS Code 版本实现方案（草案）

> 目标：在尽量复用现有 `frontend/` 的前提下，为 VS Code 实现 Claude Code Plus 的等价能力（Claude + Codex 两套后端、工具调用、IDE 集成、回滚、终端后台任务、设置与 MCP）。

> 注意：本草案里涉及“兼容/回退”的表述已过时。最新约束与进度请以 `docs/plans/vscode-migration-plan.md` 为准；协议契约以 `docs/plans/vscode-api-compat.md` 为准。

## 1. 需求与目标

### 1.1 必须实现（与 JetBrains 版对齐）

- **两套后端都要**：Claude（Claude Code / API Key 方式）+ Codex（OpenAI Codex app-server 协议）
- **多会话**：会话列表、切换、历史加载
- **工具调用展示与交互**：Read/Write/Edit/Bash/MultiEdit 等工具卡片、Diff 预览、状态指示
- **IDE 集成功能**：
  - 点击路径打开文件并跳转行列
  - 显示 Diff（含多处编辑）
  - 文件搜索（@ 提及）
  - 获取文件内容
  - 获取主题/配色（用于前端主题同步）
- **回滚能力**：VS Code 无 IntelliJ LocalHistory，需在扩展侧实现“文件快照 + 批量回滚”
- **终端后台任务**：支持将任务“转后台”，并持续推送输出与状态
- **设置**：提供与 JetBrains 版等价的设置项与 UI，并保存到 VS Code 用户设置（必要时用 SecretStorage）
- **MCP**：至少实现一套“VS Code LSP MCP”（能力可弱于 JetBrains LSP MCP，但接口要稳定）

### 1.2 非功能目标

- **最大化复用前端**：尽量不改 `frontend/`；必要改动以“复刻/对齐协议契约”为优先（不保留兼容/回退）
- **本地通信安全**：仅监听 `127.0.0.1`；随机端口；token/nonce 防 CSRF（Webview 与本地服务握手）
- **跨平台**：Windows/macOS/Linux（至少不引入明显平台耦合）

### 1.3 暂不承诺（可能需要讨论/分期）

- 与 IntelliJ 完全等价的索引/重构能力（VS Code 受限于已安装语言扩展的 LSP 能力）
- IntelliJ 特有 UI（ToolWindow、原生设置页）等的 1:1 复刻

## 2. 当前架构（JetBrains 版）与主要功能点

### 2.1 模块划分（现状）

- `frontend/`：Vue Web UI（聊天、工具展示、设置、MCP 配置等）
- `ai-agent-server/`：Kotlin 后端（HTTP + WebSocket/RSocket），聚合 Claude/Codex 能力，向前端提供统一 RPC
- `jetbrains-plugin/`：IDEA 插件侧桥接（打开文件、Diff、搜索、读取文件、主题等）

### 2.2 通信与关键约定（前端已固化）

- HTTP：`POST /api/`（`frontend/src/services/ideaBridge.ts`）
- RSocket：
  - 通用 AI/会话流式：`/rsocket`
  - IDE 集成：`/ide-rsocket`（回滚、终端后台、打开文件等能力）
- 前端环境探测：`window.__serverUrl`、`window.__IDE_MODE__`

### 2.3 回滚与终端后台（前端依赖点）

- 回滚 UI：`frontend/src/components/chat/FileRollbackBar.vue`
- 回滚逻辑：`frontend/src/composables/useFileChanges.ts`（解析 `[jb:*]` 标记并调用批量回滚路由）
- 终端后台 UI：`frontend/src/components/chat/TerminalBackgroundBar.vue`
- 终端后台 Store：`frontend/src/stores/sessionStoreTerminal.ts`

## 3. VS Code 版本总体架构（建议）

### 3.1 选型结论

- **继续使用本地 HTTP + WebSocket/RSocket**：
  - 优点：复用 `frontend/` 的通信代码与数据结构，改动最小
  - 代价：需要在 VS Code Extension Host 中维护一个本地服务（但实现可控）

> 备选方案：Webview `postMessage` 直连 Extension Host（零 HTTP）。但需要重写前端通信与流式协议，代价大，建议后续再优化。

### 3.2 组件拓扑

- VS Code Extension（TypeScript，运行于 Extension Host）
  - 启动本地服务：`127.0.0.1:<randomPort>`
  - 提供：`/api/`、`/ws`、`/rsocket`、`/ide-rsocket`
  - 对接 VS Code API：文件、diff、搜索、终端、主题、设置、诊断
  - 对接 AI Provider：Claude Provider + Codex Provider
  - 内置 MCP Server：VS Code LSP MCP（以及复用/移植现有 MCP 配置逻辑）
- Webview（复用现有 `frontend/dist`）
  - 通过注入 `window.__serverUrl`、`window.__IDE_MODE__` 指向本地服务

### 3.3 端口与注入策略

- Extension 启动时随机选择可用端口，只监听 `127.0.0.1`
- Webview HTML 注入：
  - `window.__serverUrl = 'http://127.0.0.1:<port>'`
  - `window.__IDE_MODE__ = true`（复用前端“IDE 模式”分支，避免大量条件判断）

## 4. 功能迁移映射（JetBrains -> VS Code）

### 4.1 打开文件/跳转行列

- VS Code API：`vscode.window.showTextDocument(uri, { selection })`
- 对齐前端：实现 `ide.openFile`（HTTP action 或 RSocket route，按前端现有调用路径）

### 4.2 Diff 展示

- VS Code API：`vscode.commands.executeCommand('vscode.diff', leftUri, rightUri, title)`
- 多处 edits：可在扩展侧对 `oldContent` 应用 edits 生成临时 right 内容（或反之）

### 4.3 文件搜索（@ 提及）

- 文件名：`vscode.workspace.findFiles('**/*')` + 轻量 fuzzy（或调用 `ripgrep`）
- 内容搜索（如需要）：`vscode.workspace.findTextInFiles`
- 对齐前端：实现 `ide.searchFiles` / `ide.search` 等 action，返回结构与 JetBrains 版保持一致

### 4.4 获取文件内容/写入文件

- 读：`vscode.workspace.fs.readFile`
- 写：`vscode.workspace.fs.writeFile`（写前创建回滚快照，见 4.6）
- 注意：多根工作区（multi-root workspace）需要在返回值中带上 workspace 信息或统一用绝对路径

### 4.5 主题同步

- VS Code API：`vscode.window.activeColorTheme.kind`
- 可选：读取 `workbench.colorCustomizations` 做更细的 token 适配
- 对齐前端：实现 `ide.getTheme`，保持与现有主题数据结构一致（必要时提供字段映射）

### 4.6 文件回滚（快照方案）

- 触发点：任何“写文件工具调用”之前自动保存快照（或用户显式启用时保存）
- 存储位置：扩展 `context.storageUri` 下（按 workspace + sessionId + filePath 做 key）
- 清理策略：
  - TTL（如 7 天）+ 最大条数（如 500）+ 最大占用（如 200MB）
  - “历史会话”不保证回滚：可直接清理其快照
- 对齐前端：实现前端已固定的路由（统一为 `ide.*`）：
  - `ide.getOriginalContent`
  - `ide.getFileHistoryContent`
  - `ide.rollbackFile`
  - `ide.batchRollback`
- 前端标记：扩展侧在工具结果 meta 中输出 `[jb:historyTs=...]`、`[jb:canRollback=true]` 等（满足 `useFileChanges.ts` 解析）

### 4.7 终端后台任务

- 建议实现：独立 `TaskManager`（`child_process.spawn` 执行 + 收集 stdout/stderr）
- 展示方式：
  - 可选 1：通过 `vscode.window.createTerminal({ pty })` 以 Pseudoterminal 镜像输出
  - 可选 2：仅在 Webview 内展示（无需 VS Code 终端），但保留“打开终端”按钮
- 对齐前端：实现路由与事件：
  - `ide.getBackgroundableTerminals`
  - `ide.terminalBackground`
  - 推送：`onTerminalTaskUpdate`（持续推送任务状态/输出）

### 4.8 设置与存储

- 目标：界面与 JetBrains 版设置内容一致，但存储落到 VS Code 体系
- 存储策略：
  - 普通配置：`vscode.workspace.getConfiguration('claudeCodePlus').update(key, value, ConfigurationTarget.Global)`
  - 与项目相关：`ConfigurationTarget.Workspace` 或 `WorkspaceFolder`
  - API Key/Token：`vscode.SecretStorage`
  - 运行时缓存：`context.globalState` / `context.workspaceState`
- UI 方案（你偏好“单独设置界面”）：
  - 仍然在 `package.json contributes.configuration` 声明 settings（好处：可被 Settings UI 检索/同步）
  - 同时提供一个自定义 Settings Webview（复用/移植现有前端设置页），读写上述 settings

### 4.9 MCP（VS Code LSP MCP）

- 目标：提供类似 JetBrains LSP MCP 的一组工具，但底层实现使用 VS Code API/命令
- 建议工具集合（对齐现有 MCP 命名习惯）：
  - `DirectoryTree`：基于 `workspace.fs.readDirectory` + 深度限制
  - `FileIndex`：基于 `workspace.findFiles`、`executeWorkspaceSymbolProvider`
  - `CodeSearch`：基于 `workspace.findTextInFiles`（或 ripgrep）
  - `FileProblems`：基于 `vscode.languages.getDiagnostics(uri)`
  - `FindUsages`：基于 `vscode.executeReferenceProvider`
  - `Rename`：基于 `vscode.executeDocumentRenameProvider`（或 `vscode.executeWorkspaceSymbolProvider` + `WorkspaceEdit`）
- 限制说明：
  - 能力取决于语言扩展/LSP 是否提供相应 provider
  - 大型项目索引质量一般弱于 IntelliJ（需在输出中显式声明降级）

## 5. Node/TS 后端实现方式（适配层策略）

### 5.1 原则

- **前端不动优先**：后端对齐现有 `/api/` action 与 RSocket routes（并统一 IDE 协议为 `ide.*`）
- **接口不稳定点先文档化**：先产出“前端实际调用清单”，再实现最小闭环

### 5.2 建议目录结构（新增）

- `vscode-extension/`
  - `package.json`（contributes、commands、views、configuration）
  - `src/extension.ts`（激活、WebviewPanel、启动/停止本地服务）
  - `src/server/httpServer.ts`（`/api/`、静态资源、注入）
  - `src/server/ws.ts`（WebSocket 推送事件）
  - `src/server/rsocket/`（`/rsocket`、`/ide-rsocket`）
  - `src/bridge/vscodeIdeBridge.ts`（openFile/showDiff/search/getTheme 等）
  - `src/rollback/snapshotStore.ts`
  - `src/terminal/taskManager.ts`
  - `src/providers/claude/*`、`src/providers/codex/*`
  - `src/mcp/vscode-lsp-mcp.ts`

### 5.3 Claude Provider（TS 版）

- 优先复用：`external/claude-agent-sdk-typescript`（若满足当前 JetBrains 版能力）
- 两种接入：
  - Claude Code 订阅：复用/嵌入 CLI（或检测用户已安装）
  - API Key：直接请求 Anthropic API（Key 存 SecretStorage）

### 5.4 Codex Provider（TS 版）

- 复刻 Kotlin 的 `CodexAppServerClient`：在扩展侧 spawn `external/openai-codex` 的 app-server，并通过 JSON-RPC 通信
- 目标：把 Codex 的事件/流式输出映射为前端现有会话流（尽量保持数据结构一致）

## 6. 实施计划（里程碑 + 任务拆分）

> 以“最小可用闭环”优先：先让 VS Code 里能打开 UI、能聊天、能写文件、能回滚，再逐步补齐复杂能力。

### M0：接口盘点与契约文档（1-2 天）

- 输出 `docs/plans/vscode-api-compat.md`：
  - `/api/` action 清单（前端实际调用）
  - `/rsocket`、`/ide-rsocket` route 清单（前端实际调用）
  - 关键 payload/schema 示例
- 明确 VS Code 侧的路径约定：绝对路径 vs `vscode.Uri`，multi-root 如何表达

### M1：VS Code 扩展骨架 + Webview 复用（2-4 天）

- 新增 `vscode-extension/`（TypeScript）
- 实现命令：打开 Claude Code Plus 面板
- Webview 加载 `frontend/dist`，并注入 `__serverUrl`、`__IDE_MODE__`
- 本地服务最小化：`GET /` 返回注入后的 index.html；`POST /api/` 先只做 `ide.ping`

验收：在 VS Code 中能打开 UI，前端能成功调用 `/api/` 并显示“已连接”。

### M2：IDE 集成功能最小集（3-6 天）

- 实现：openFile / getFileContent / searchFiles / showDiff / getTheme
- 前端点击路径可打开文件；工具卡片 Diff 可打开 VS Code diff

验收：UI 中常用交互（打开文件、Diff、@提及搜索）可用。

### M3：设置系统（2-5 天）

- `package.json contributes.configuration` 定义设置项（对齐 JetBrains 版）
- 实现 Settings Webview（复用现有设置组件或单独页面），读写到 VS Code settings
- SecretStorage：保存 API Key/Token（如需要）

验收：设置页可编辑并持久化，重启 VS Code 后仍生效。

### M4：Claude 会话闭环（3-7 天）

- 实现 Claude Provider：
  - 建立会话、流式输出、取消（ESC）
  - 工具调用（至少 file read/write/edit + bash/terminal）
- 与前端会话协议对齐（尽量不改前端）

验收：能正常对话 + 工具调用卡片正常展示。

### M5：回滚能力（2-5 天）

- SnapshotStore：写前快照、查询历史、批量回滚
- 实现 `ide.*` 回滚路由
- 清理策略（TTL/数量/体积）

验收：工具写文件后出现回滚条，可一键回滚并恢复内容。

### M6：终端后台任务（3-7 天）

- TaskManager：spawn 执行、采集输出、支持取消
- 与前端 `TerminalBackgroundBar` 对接：
  - 列出可后台化任务
  - 转后台
  - 推送状态更新

验收：后台任务可运行、可查看输出、可停止。

### M7：Codex 集成（3-10 天）

- 实现 Codex app-server 的 spawn + JSON-RPC client
- 做事件/工具调用映射（对齐现有前端渲染）
- 与 Claude Provider 并列，支持在 UI 切换

验收：在 VS Code 中可选择 Codex 后端并完成一次“读-改-写”闭环。

### M8：VS Code MCP（并行推进，视优先级插入）

- 实现 VSCode LSP MCP server（tools：DirectoryTree/FileIndex/CodeSearch/FileProblems/FindUsages/Rename）
- 将其纳入 MCP 配置体系（对齐 `docs/MCP_CONFIGURABLE_DESIGN.md` 的“Built-in Servers”概念）

验收：AI 能通过 MCP 调用 VS Code 的基础“索引/诊断/重命名”能力（若语言扩展支持）。

### M9：打包、测试与发布准备（持续）

- e2e：关键路径测试（打开 UI、聊天、写文件、回滚、后台任务）
- 安全检查：本地服务 token、CSP、资源加载
- 发布：vsix 打包、README/截图补齐

### 6.1 详细任务计划列表（可直接转 TODO/Jira）

> 说明：以下按里程碑拆成“可交付的最小任务单元”，并尽量标注产出物（文件/接口/验收点）。实现过程中可以并行，但建议按依赖关系推进。

#### M0：接口盘点与契约文档（1-2 天）

- [x] M0-1 盘点前端所有 `POST /api/` 的 `action`（搜索 `ideaBridge.query(` / `ideService.*`），形成 action 列表与请求/响应样例
- [x] M0-2 盘点前端所有 `/ide-rsocket` routes（搜索 `ideaRSocket` / `requestResponse` / `fireAndForget`），形成 route 列表与 payload 样例
- [x] M0-3 盘点前端所有 `/rsocket`（Claude/Codex 会话）相关 routes/事件类型（含流式 chunk、tool、permission、terminal update 等）
- [x] M0-4 明确 VS Code 侧“路径表达”契约：
  - 前端显示与工具入参统一使用绝对路径（推荐），并补充 multi-root 的 workspace 标识字段
  - 约定 path <-> `vscode.Uri` 的转换规则（含 Windows 盘符、URI 编码）
- [x] M0-5 输出文档：`docs/plans/vscode-api-compat.md`（作为协议契约/实现的唯一真相源）

#### M1：VS Code 扩展骨架 + Webview 复用（2-4 天）

- [x] M1-1 新建 `vscode-extension/`（TypeScript）工程骨架：构建（esbuild/tsup）、调试配置、打包 vsix 的基本流程
- [x] M1-2 在 `vscode-extension/package.json` 注册：命令（打开面板）、视图容器/面板入口、必要的 activationEvents
- [x] M1-3 实现 `vscode-extension/src/extension.ts`：
  - 激活时启动本地服务（随机端口，仅 `127.0.0.1`）
  - 打开 WebviewPanel，加载 `frontend/dist`（静态资源）
- [x] M1-4 实现注入：在 Webview HTML 中注入 `window.__serverUrl` 与 `window.__IDE_MODE__`（并考虑 token/nonce 供后端校验）
- [x] M1-5 实现最小 `/api/`：`ide.ping`（前端连接探活），返回 `{success:true}`（或对齐 JetBrains 返回结构）
- [x] M1-6 基础安全：
  - 本地服务仅接受来自本机且带 token 的请求
  - Webview CSP 适配（允许加载本地静态资源/必要脚本）

验收：VS Code 中能打开 UI，前端能成功调用 `/api/` 并显示“已连接”。  
产出：`vscode-extension/` + 可跑通的本地服务 + Webview 注入机制。

#### M2：IDE 集成功能最小集（3-6 天）

- [x] M2-1 实现 `vscode-extension/src/bridge/vscodeIdeBridge.ts`（或同等模块）：openFile / showDiff / searchFiles / getFileContent / getTheme
- [x] M2-2 `ide.openFile`：支持 line/column/selection（前端已有参数），并处理文件不存在/不可读提示
- [x] M2-3 `ide.showDiff`：
  - 支持 title
  - 支持 `rebuildFromFile`（需要时重新读取旧内容）
  - 支持 `edits[]`（多处替换生成新内容；必要时使用临时 URI / 虚拟文件系统 provider）
- [x] M2-4 `ide.searchFiles`：实现文件名搜索（支持 @ 提及），输出结构尽量与 JetBrains 版一致（含 maxResults、排序）
- [x] M2-5 `ide.getFileContent`：支持大文件时的大小限制/截断策略（与前端显示逻辑匹配）
- [x] M2-6 `ide.getTheme`：返回主题 kind +（可选）token 映射，满足前端主题切换

验收：UI 中常用交互（打开文件、Diff、@提及搜索）可用。

#### M3：设置系统（2-5 天）

- [x] M3-1 在 `vscode-extension/package.json contributes.configuration` 定义设置项（字段名/默认值对齐 JetBrains 版设置）
- [x] M3-2 定义敏感字段策略：哪些走 `settings.json`，哪些走 `SecretStorage`（如 Anthropic/OpenAI key、token）
- [x] M3-3 实现 Settings Webview：
  - UI 内容与 JetBrains 版设置项一致（可复用/移植现有前端设置页，或做同款页面）
  - 读：VS Code settings + SecretStorage
  - 写：`workspace.getConfiguration().update(...)` + SecretStorage
- [x] M3-4 支持“项目级配置”：为部分设置提供 Workspace/WorkspaceFolder 级别覆盖（如 MCP、工作目录、工具开关等）
- [x] M3-5 设置变更监听：配置变化后即时通知前端刷新（避免必须重启）

验收：设置页可编辑并持久化，重启 VS Code 后仍生效。

#### M4：Claude 会话闭环（3-7 天）

- [ ] M4-1 Claude Provider 选型落地：
  - 优先评估并接入 `external/claude-agent-sdk-typescript`
  - 若不足，补齐最小能力（流式、取消、工具）
- [x] M4-2 实现 `/rsocket` 或等价流式通道（当前为 mock responder，未接入真实 Claude Provider）：
  - Webview 发起消息 -> 扩展侧 provider
  - provider 流式回传 -> 推送给前端（对齐现有消息渲染协议）
- [x] M4-3 取消/中断：前端 ESC -> 扩展侧 AbortController -> provider 停止 + 通知前端（当前基于 mock stream 实现 interrupt 取消）
- [ ] M4-4 工具调用执行器（最小集）：read/write/edit + bash/terminal
  - 将工具请求映射到 VS Code API（文件/终端）
  - 按现有前端卡片结构返回 tool result（含 diff、path 等）
- [ ] M4-5 权限/确认：实现“写文件/执行命令”授权（对齐 JetBrains 版交互语义），并支持“允许一次/允许本次会话”策略

验收：能正常对话 + 工具调用卡片正常展示，且可中断。

#### M5：回滚能力（2-5 天）

- [ ] M5-1 设计 SnapshotStore 数据模型：snapshotId、filePath、workspaceId、sessionId、ts、hash、contentRef（内容存储方式）
- [ ] M5-2 实现快照创建：任何写文件工具执行前自动保存（只保存旧内容）；新文件标记 `isNewFile`
- [ ] M5-3 实现快照查询：
  - 原始内容（写前版本）
  - 某个历史版本内容（按 ts/snapshotId）
- [ ] M5-4 实现回滚：单文件回滚 + 批量回滚（并处理“新文件回滚=删除文件”）
- [ ] M5-5 实现清理策略：TTL + 最大条数 + 最大占用；支持“历史会话快照可清理”
- [ ] M5-6 实现对齐路由（统一为 `ide.*`）：
  - `ide.getOriginalContent`
  - `ide.getFileHistoryContent`
  - `ide.rollbackFile`
  - `ide.batchRollback`
- [ ] M5-7 输出前端识别的 meta 标记：`[jb:historyTs]`、`[jb:canRollback]`、`[jb:isNewFile]` 等

验收：工具写文件后出现回滚条，可一键回滚并恢复内容。

#### M6：终端后台任务（3-7 天）

- [ ] M6-1 实现 TaskManager：
  - `child_process.spawn` 执行
  - 采集 stdout/stderr
  - 状态机（running/succeeded/failed/canceled）
  - 支持取消（跨平台信号/kill 树）
- [ ] M6-2 定义“后台化”协议：任务列表、任务 id、显示名、启动参数、工作目录等
- [ ] M6-3 实现对齐路由（统一为 `ide.*`）：
  - `ide.getBackgroundableTerminals`
  - `ide.terminalBackground`
- [ ] M6-4 推送事件：实现 `onTerminalTaskUpdate`（增量输出/进度/结束原因），让前端 `TerminalBackgroundBar` 正常工作
- [ ] M6-5（可选）VS Code Terminal 镜像：用 Pseudoterminal 将后台输出同步到 VS Code Terminal，便于用户查看

验收：后台任务可运行、可查看输出、可停止，且 UI 进度实时更新。

#### M7：Codex 集成（3-10 天）

- [ ] M7-1 明确 Codex app-server 分发策略：
  - 打包内置（多平台体积）
  - 首次运行下载（需要下载源与校验）
  - 或依赖用户本地安装（体验较差但最省事）
- [ ] M7-2 实现 app-server 生命周期管理：spawn、健康检查、重启、日志、端口冲突处理
- [ ] M7-3 实现 JSON-RPC client（TS 版 `CodexAppServerClient`）：请求/响应、通知、流式事件订阅
- [ ] M7-4 把 Codex 事件映射到前端现有消息流：
  - 文本流式
  - 工具调用
  - 权限请求
  - 任务状态
- [ ] M7-5 在 UI 层实现“Claude/Codex”切换（尽量不改前端，或通过后端注入/配置让前端识别）

验收：可选择 Codex 后端并完成一次“读-改-写”闭环。

#### M8：VS Code MCP（可并行推进）

- [ ] M8-1 设计 `vscode-lsp-mcp` 工具集合与 schema：DirectoryTree/FileIndex/CodeSearch/FileProblems/FindUsages/Rename
- [ ] M8-2 基于 VS Code API 实现每个工具：
  - DirectoryTree：`workspace.fs.readDirectory`
  - FileIndex：`findFiles` + `executeWorkspaceSymbolProvider`
  - CodeSearch：`findTextInFiles`
  - FileProblems：`languages.getDiagnostics`
  - FindUsages：`executeReferenceProvider`
  - Rename：`executeDocumentRenameProvider`（失败则降级为提示不支持）
- [ ] M8-3 统一降级策略：当 provider 不可用时，返回明确错误 + 可操作建议（安装语言扩展/启用 LSP 等）
- [ ] M8-4 将 VS Code MCP 作为“Built-in Servers”纳入 MCP 配置系统（对齐 `docs/MCP_CONFIGURABLE_DESIGN.md`）：
  - enable/disable
  - appended system prompt
  - reset to default
- [ ] M8-5 文档补充：说明 VS Code LSP MCP 的能力边界与降级策略（对比 JetBrains LSP MCP）

验收：AI 能通过 MCP 调用 VS Code 的基础“索引/诊断/重命名”能力（在语言扩展支持时生效）。

#### M9：打包、测试与发布准备（持续）

- [ ] M9-1 单元测试：SnapshotStore、TaskManager、path/uri 转换、schema 校验
- [ ] M9-2 集成测试：本地服务启动/停止、Webview 注入、关键路由
- [ ] M9-3 手工测试清单：
  - 打开 UI
  - Claude 对话 + 工具写文件
  - 回滚
  - 后台任务
  - 设置持久化
  - MCP 基础工具
  - Codex（若启用）
- [ ] M9-4 安全审计：
  - 本地服务 token/nonce
  - Origin/Host 校验
  - Webview CSP
  - 静态资源路径防穿越
- [ ] M9-5 发布准备：
  - vsix 打包
  - README（VS Code 版）/截图
  - 更新日志（英文）

### 6.2 执行记录（新增需求/设计变更/问题追踪）

> 规则：在实现过程中出现任何“新增需求、重要设计决策、协议变更、行为差异问题、待确定事项”，都必须在这里追加记录，并关联到任务编号（如 M2-3）。

#### 2026-01-24

- 事项：补充 VS Code 版协议契约文档 + 创建 VS Code Extension 骨架（本地 HTTP 服务 + Webview 容器）+ 完成 M2（IDE 集成最小集）
- 类型：设计决策 / 其他
- 背景：需要在“尽量不改前端”的前提下先落地可运行的 VS Code 容器，并固定 HTTP action / RSocket route 的契约清单
- 结论：
  - 新增 `docs/plans/vscode-api-compat.md` 作为协议来源（后端实现以此为准）
  - 新增 `vscode-extension/`（命令 + 本地 HTTP 服务 + Webview Panel；暂未接入真实 Claude/Codex Provider）
  - 补齐 VS Code 本地服务的健康检查接口：`GET /api/health`、`GET /api/codex/health`
  - 补齐 VS Code 版 `POST /api/` 的 IDE 最小集：`ide.openFile/showDiff/searchFiles/getFileContent/getTheme`（含 selection、rebuildFromFile、edits、截断策略）
  - 补齐 VS Code 本地服务的基础安全：注入并校验 `X-Claude-Code-Plus-Token`，CORS 仅放行 `vscode-webview://*`
  - 新增 VS Code 版 `/rsocket`：实现 RSocket 协议最小闭环（connect/query/mock stream/interrupt），并为 WebSocket 连接增加 `?token=<token>` 校验
- 影响范围：后端实现将围绕 `POST /api/` + `GET /api/files/search` + `/rsocket` + `/ide-rsocket` 逐步补齐
- 关联任务：M0、M1、M2
- 后续动作：
  - M1-6：基础安全（token/nonce + 收紧 CORS）
  - M3：设置系统（VS Code settings + SecretStorage）
  - M4：实现 `/rsocket`（Claude/Codex 会话流式）

- 事项：实现 VS Code 自定义 Settings Webview（读写 VS Code Settings + SecretStorage）+ 支持 Workspace/WorkspaceFolder 级别配置覆盖
- 类型：功能实现 / 设计决策
- 背景：内置 Settings UI 不便于与 SecretStorage 统一展示，同时需要补齐“项目级配置”的保存范围选择
- 结论：
  - 新增 `claudeCodePlus.openSettings` 对应的 Settings Webview（可选 User/Workspace/WorkspaceFolder 保存范围）
  - `settings.get` / `settings.getDefault` 读取配置时使用 active editor 作为 scope，支持 WorkspaceFolder 覆盖生效
  - 保存后主动通知 Chat Webview 刷新 settings（包含 SecretStorage 变更）
- 影响范围：`vscode-extension/src/webview/settingsPanel.ts`、`vscode-extension/src/extension.ts`、`vscode-extension/src/server/apiHandlers.ts`
- 关联任务：M3-3、M3-4

- 事项：完善 VS Code `/rsocket` mock：支持流式分片输出 + `agent.interrupt` 取消当前 stream
- 类型：功能实现
- 背景：为后续接入真实 Claude Provider 预先打通“可中断的流式通道”，并让前端 ESC 行为可验证
- 结论：
  - `agent.query` / `agent.queryWithContent` 改为分片流式输出，支持取消
  - `agent.interrupt` 会主动取消当前进行中的 requestStream
- 影响范围：`vscode-extension/src/server/rsocket/agentRSocketServer.ts`
- 关联任务：M4-3

- 事项：补齐最小历史记录闭环（内存版）：`/api/history/*` 从 stub 升级为可返回会话与消息
- 类型：功能实现
- 背景：前端会话列表/历史加载依赖 `/api/history/sessions`、`/api/history/load.pb`、`/api/history/metadata.pb`，stub 会导致历史面板始终为空
- 结论：
  - 新增 `HistoryStore`（extension host 内存存储），在 `agent.connect`/`agent.query` 时写入
  - `/api/history/*` 读取 `HistoryStore` 返回真实 sessions 与 protobuf history result
- 影响范围：`vscode-extension/src/server/history/historyStore.ts`、`vscode-extension/src/server/localServer.ts`、`vscode-extension/src/server/rsocket/agentRSocketServer.ts`
  - 备注：当前仅内存存储，重启 VS Code 后历史会丢失（后续可迁移到磁盘 JSONL 与 JetBrains 版对齐）

- 事项：实现 VS Code `/rsocket` 的 server->client `client.call`（AskUserQuestion/RequestPermission）+ 增加 `/ask`、`/perm` 调试触发
- 类型：功能实现
- 背景：前端已注册 AskUserQuestion/RequestPermission handler，但 VS Code 后端未实现 `client.call`，导致权限/提问交互无法验证
- 结论：
  - RSocket acceptor 保存 `remotePeer`，可通过 `client.call` 向 Webview 发起请求并等待响应
  - 在 `agent.query` 中加入 mock 命令：`/ask`、`/perm`（后续将由真实工具执行器触发）
- 影响范围：`vscode-extension/src/server/rsocket/agentRSocketServer.ts`
- 关联任务：M4-5（权限/确认）

- 事项：补齐最小工具执行器（调试触发）：`/read`、`/write`、`/edit`、`/bash`（并对写文件/执行命令接入 RequestPermission）
- 类型：功能实现
- 背景：在未接入真实 Claude Provider 前，先验证“工具卡片渲染 + 权限弹窗 + 实际文件/命令执行”闭环，避免后续联调成本过高
- 结论：
  - `agent.query` 支持通过斜杠命令触发 Read/Write/Edit/Bash 工具调用，并返回标准 `tool_use` / `tool_result` content blocks
  - `Write/Edit/Bash` 在执行前通过 `client.call(RequestPermission)` 请求用户授权，拒绝时返回 `tool_result(is_error=true)`
- 影响范围：`vscode-extension/src/server/rsocket/agentRSocketServer.ts`
- 关联任务：M4-4、M4-5

#### 2026-01-25

- 事项：补齐 VS Code 版终端后台任务的最小闭环 + 增加“类似 runIde 的 VS Code 启动脚本”
- 类型：功能实现 / Bug
- 背景：
  - 前端 `TerminalBackgroundBar` 依赖 `ide.getBackgroundableTerminals` / `ide.terminalBackground` + `onTerminalTaskUpdate` 推送；此前 VS Code 版为 stub，UI 无法验证
  - JetBrains 手写 varint 编解码在时间戳（毫秒）场景存在 32-bit 溢出风险，可能导致回滚/终端时间字段异常
  - 开发需要一键启动 VS Code（Extension Dev Host）进行联调
- 结论：
  - 新增 `TerminalTaskManager`：跟踪 `/bash` 子进程的 started/completed/backgrounded，并推送给前端
  - 实现 `ide.getBackgroundableTerminals` / `ide.terminalBackground`：支持将长任务标记为后台（UI 隐藏，但不影响子进程继续运行）
  - 修复前端 varint 编解码对大数时间戳的溢出风险；补齐 `terminalTaskUpdate` 的 server-call 解码链路
  - 新增脚本：`vscode-extension/scripts/launch-vscode-dev.mjs`、`scripts/run-vscode-dev.ps1`，用于一键启动 VS Code Dev 环境
- 影响范围：
  - `vscode-extension/src/server/terminal/terminalTaskManager.ts`
  - `vscode-extension/src/server/rsocket/agentRSocketServer.ts`
  - `vscode-extension/src/server/rsocket/ideaRSocketServer.ts`
  - `vscode-extension/scripts/sync-frontend.mjs`
  - `vscode-extension/scripts/launch-vscode-dev.mjs`
  - `scripts/run-vscode-dev.ps1`
  - `frontend/src/services/ideaProtoCodec.ts`
  - `frontend/src/services/rsocket/protoMappers.ts`
  - `frontend/src/services/rsocket/protoCodec.ts`
- 关联任务：M6-3、M6-4（终端后台）、M1（开发联调）

#### 2026-01-26

- 事项：完成 Claude Agent SDK 从 Kotlin 到 TypeScript 的完整翻译
- 类型：功能实现
- 背景：
  - VS Code 版本需要完整的 Claude Agent SDK TypeScript 实现
  - 之前 SDK 目录仅有 `claudeCli.ts` 一个文件（约 35% 覆盖率）
  - Kotlin SDK 共 50 个文件，包含 ControlProtocol、Transport、MCP、Types、Utils 等模块
- 结论：
  - 新增 `ClaudeCodeSdkClient`（567 行）：完整 SDK 客户端，包含 22+ 方法
    - connect/query/receiveResponse/getAllMessages
    - interrupt/runInBackground/runAllInBackground/bashRunToBackground/runToBackground
    - getMcpStatus/getChromeStatus/reconnectMcp/getMcpTools/getCapabilities
    - setPermissionMode/setModel/setMaxThinkingTokens
    - getServerInfo/isConnected/disconnect/use/simpleQuery
  - 新增 `utils/` 模块（4 文件）：
    - `pathUtils.ts` - 项目路径工具（projectPathToDirectoryName、generateProjectId 等）
    - `chromeDetector.ts` - Chrome 扩展检测（isExtensionInstalled、getExtensionInfo）
    - `sessionScanner.ts` - 会话历史扫描（scanHistorySessions、getSessionIds）
    - `index.ts` - 模块导出
  - 扩展 `controlProtocol.ts`：添加 `receiveMessages()` AsyncGenerator 和 `waitForSystemInit()`
  - 新增 `index.ts`：统一 SDK 导出入口
- 影响范围：
  - `vscode-extension/src/sdk/claude/claudeCodeSdkClient.ts`（新增）
  - `vscode-extension/src/sdk/claude/utils/*`（新增 4 文件）
  - `vscode-extension/src/sdk/claude/protocol/controlProtocol.ts`（扩展）
  - `vscode-extension/src/sdk/claude/index.ts`（新增）
- 关联任务：M7（Claude Provider）
- 统计：SDK 目录现有 39 个 TypeScript 文件，构建产物 1.6MB

- 事项：实现 Claude CLI 工具调用时的快照保存和 `[jb:*]` 标记注入
- 类型：功能实现
- 背景：
  - 前端 `FileRollbackBar` 依赖 `[jb:historyTs]`、`[jb:canRollback]` 等标记来识别可回滚的文件修改
  - JetBrains 版本通过 MCP 工具（WriteFileTool/EditFileTool）在工具结果中添加这些标记
  - VS Code 版本使用 Claude CLI 内置工具，需要在权限批准时保存快照，并在工具结果中注入标记
- 结论：
  - 扩展 `ClaudeCliQueryCallbacks` 类型，添加 `ToolPermissionResult.snapshotMeta` 字段
  - 在 `ClaudeCliSession` 中添加 `snapshotMetaMap` 跟踪快照元数据
  - 在 `handleControlRequest` 的 `can_use_tool` 处理中，存储 `snapshotMeta`
  - 在 `onStdoutLine` 中调用 `injectSnapshotMetaIntoToolResults` 注入标记到 `tool_result` 内容块
  - 在 `agentRSocketServer.ts` 的 `requestPermission` 回调中，对写文件工具（Write/Edit/MultiEdit）保存快照到 `snapshotStore`
- 影响范围：
  - `vscode-extension/src/server/claude/claudeCli.ts`
  - `vscode-extension/src/server/rsocket/agentRSocketServer.ts`
- 关联任务：M4-4（工具调用执行器）、M5（回滚能力）

记录模板（复制一段即可）：

```markdown
#### YYYY-MM-DD

- 事项：
- 类型：新增需求 / 设计决策 / 协议变更 / Bug / 风险 / 其他
- 背景：
- 结论：
- 影响范围：
- 关联任务：
- 后续动作：
```
## 7. 待讨论问题（你确认后我再细化方案）

1. VS Code 版是否需要“完全同一套前端 UI”（包含 JetBrains 专用文案/图标），还是允许做轻量 VS Code 风格适配？
2. Claude Provider：优先走 Claude Code 订阅（CLI）还是 API Key？还是两者都支持（默认订阅）？
3. Codex app-server 的分发方式：随扩展打包 vs 首次运行下载（涉及体积与平台差异）。
4. 多根工作区（multi-root）下“文件路径”在前端展示与回滚 key 的约定。

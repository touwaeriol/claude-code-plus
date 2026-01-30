# JB vs VS Code 1:1 实现度验收矩阵（以 `vscode-api-compat.md` 为基准）

目标：对比 **JetBrains 版** 与 **VS Code 版**，逐条核对前端 `frontend/` 依赖的 HTTP / RSocket 契约，判断 VS Code 是否达到“1:1（行为等价）”。

基准文档：
- `docs/plans/vscode-api-compat.md`

说明（状态字段写法）：
- 统一写成：`JB=<OK|PARTIAL|MISSING>; VS=<OK|PARTIAL|MISSING>; Parity=<OK|PARTIAL|MISSING>`
- `Parity` 关注“VS 是否与 JB 行为等价”；`JB/VS` 关注“各自是否满足前端/契约”
- `NEEDS_TEST` 不单独作为状态，而是放到“验收用例”里（表示需要用脚本/手工确认）

---

## 1) HTTP API（非 RSocket）

| 契约项 | 前端调用点 | JB 实现位置 | VS Code 实现位置 | 状态 | 验收用例 |
|---|---|---|---|---|---|
| `GET /health`（调试/存活检查） | 无强依赖（主要用于人工检查） | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`get("/health")`，返回含 `port`） | `vscode-extension/src/server/HttpApiServer.ts`（`GET /health`，返回 `{ success, data }`） | `JB=OK; VS=PARTIAL; Parity=PARTIAL`（响应结构不一致） | `curl` 两端 `/health`，检查是否 200 且字段一致（建议统一为 `{ status:'ok' }` 或文档化差异） |
| `GET /api/health` | `frontend/src/services/backend/BackendSessionFactory.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`route("/api").get("/health")`） | `vscode-extension/src/server/HttpApiServer.ts` | `JB=OK; VS=OK; Parity=OK` | 前端打开后：应能通过 Claude 后端健康检查（返回 `{ status:'ok' }`） |
| `GET /api/codex/health` | `frontend/src/services/backend/BackendSessionFactory.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`route("/api/codex").get("/health")`） | `vscode-extension/src/server/HttpApiServer.ts` + `vscode-extension/src/server/codex/codexBackendProvider.ts` | `JB=OK; VS=OK; Parity=OK` | 当 Codex 后端不可用：两端都应返回 `unavailable`（或非 2xx）；当可用：应返回 `{status:'ok'}` |
| `POST /api/codex/thread/start` | 当前 TS 源码未直接调用（未来 Codex / 单元测试） | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`route("/api/codex").post("/thread/start")`，依赖 `codexBackendProvider`） | `vscode-extension/src/server/HttpApiServer.ts` + `vscode-extension/src/server/codex/codexBackendProvider.ts` | `JB=OK; VS=OK; Parity=OK` | 若后端不可用：应 503；否则应返回 `{ success:true, threadId }` |
| `POST /api/codex/thread/resume` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/thread/resume`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/400/200 分支行为与 JB 一致 |
| `POST /api/codex/thread/archive` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/thread/archive`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/400/200 分支行为与 JB 一致 |
| `POST /api/codex/turn/start` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/turn/start`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/400/200 分支行为与 JB 一致（返回 `{ success:true, turnId }`） |
| `POST /api/codex/turn/interrupt` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/turn/interrupt`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/400/200 分支行为与 JB 一致 |
| `GET /api/codex/config` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/config`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/200 分支行为与 JB 一致（目前返回 `{ success:true, available:true, version }`） |
| `PUT /api/codex/config` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/config`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/200 分支行为与 JB 一致（目前运行时更新不支持） |
| `GET /api/codex/thread/{threadId}/state` | 同上 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`/api/codex/thread/{threadId}/state`） | 同上 | `JB=OK; VS=OK; Parity=OK` | 503/400/404/200 分支行为与 JB 一致 |
| `GET /api/files/search` | `frontend/src/services/fileSearchService.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`route("/api/files").get("/search")`） | `vscode-extension/src/server/HttpApiServer.ts` → `vscode-extension/src/server/apiHandlers.ts:handleFileSearchRequest()` | `JB=OK; VS=PARTIAL; Parity=PARTIAL`（空查询时 JB 仅文件；VS 含目录且字段略有差异） | 空 query：返回根目录可用条目；非空 query：至少返回 name/relativePath/absolutePath/size/lastModified；检查是否出现目录项导致 UI 行为差异 |
| `GET /api/history/sessions` | `frontend/src/services/aiAgentService.ts:getHistorySessions()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（支持 `provider=codex`） | `vscode-extension/src/server/HttpApiServer.ts`（当前忽略 `provider`） | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 用 `provider=codex` 请求：两端应返回一致语义；至少不应导致前端会话列表崩溃 |
| `POST /api/history/load.pb` | `frontend/src/services/aiAgentService.ts:loadHistory()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（支持 `provider=codex`） | `vscode-extension/src/server/HttpApiServer.ts` | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 用同一 `sessionId/offset/limit` 请求：返回 Protobuf 可解码；VS 若不支持 codex，应明确返回错误或空结果并文档化 |
| `POST /api/history/metadata.pb` | `frontend/src/services/aiAgentService.ts:getHistoryMetadata()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（支持 `provider=codex`） | `vscode-extension/src/server/HttpApiServer.ts` | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 返回 Protobuf 可解码；字段 `totalLines/sessionId/projectPath/customTitle` 合理 |
| `DELETE /api/history/sessions/:sessionId` | `frontend/src/services/aiAgentService.ts:deleteHistorySession()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（支持 `provider=codex`） | `vscode-extension/src/server/HttpApiServer.ts` | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 删除同一 session：两端返回 JSON `{ success: true }`；provider=codex 时行为需要对齐 |
| `GET /api/font/{fontFamily}`（前端字体下载） | `frontend/src/services/themeService.ts:loadFont()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`get("/font/{fontFamily}")`） | `vscode-extension/src/server/HttpApiServer.ts` | `JB=OK; VS=OK; Parity=PARTIAL`（字体来源不等价） | 前端切换主题且 `fontFamily` 命中内置字体时：应能 200 返回字体 bytes；404 合理 |

---

## 2) HTTP `POST /api/`（action RPC）

| 契约项 | 前端调用点 | JB 实现位置 | VS Code 实现位置 | 状态 | 验收用例 |
|---|---|---|---|---|---|
| `ide.getProjectPath` | `frontend/src/services/ideaBridge.ts` / `frontend/src/stores/settingsStore.ts` 等 | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`action=="ide.getProjectPath"`） | `vscode-extension/src/server/apiHandlers.ts` | `JB=OK; VS=OK; Parity=OK` | 返回 `projectPath`（NEEDS_TEST）；路径格式与多工作区策略一致 |
| `ide.searchFiles`（旧 HTTP 路径） | `frontend/src/services/ideaBridge.ts:searchFiles()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（返回 `files` 字段为 JSON 字符串，语义可疑） | `vscode-extension/src/server/apiHandlers.ts`（返回 `{ files:[{name,path}] }`） | `JB=PARTIAL; VS=PARTIAL; Parity=PARTIAL` | 明确前端真正消费的结构；建议统一为 `{ files:[{name,path}] }` 并修复 JB 现有返回 |
| `ide.getFileContent` | `frontend/src/services/ideaBridge.ts:getFileContent()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：指定 `lineStart/lineEnd` 返回截断内容；大文件截断策略是否一致（可允许差异但需文档化） |
| `ide.openUrl` | `frontend/src/services/ideaBridge.ts:openUrl()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：非法 URL、空 URL 的错误提示一致；VS 通过 `vscode.env.openExternal` |
| `node.detect` | `frontend/src/services/ideaBridge.ts:detectNode()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：VS 返回 `process.execPath/process.version`；JB 走 `ideTools.detectNode()`；至少字段齐全 |
| `settings.get`（关键） | `frontend/src/stores/settingsStore.ts:loadSettings()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`（`action=="settings.get"`） | `vscode-extension/src/server/apiHandlers.ts`（含 SecretStorage 的 codexApiKey） | `JB=OK; VS=OK; Parity=PARTIAL`（持久化来源不同） | 前端启动加载设置必须成功；返回 `{ settings: any }`；敏感字段（codexApiKey）与持久化语义需在两端统一或文档化差异 |
| `settings.getDefault` | `frontend/src/stores/settingsStore.types.ts`（默认值/迁移） | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts:getHttpDefaultSettings()` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：返回字段集合对齐（尤其 `defaultBackendType/claudeDefaultModelId/codexDefaultModelId/...`） |
| `models.getAvailable` | `frontend/src/stores/settingsStore.ts:loadAvailableModels()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts:getAvailableModels()` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：校验返回字段：`claudeModels/codexModels/defaultBackendType/defaultClaudeModelId/defaultCodexModelId` |
| `ide.hasIdeEnvironment` | `frontend/src/services/aiAgentService.ts:hasIdeEnvironment()` | `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` | `vscode-extension/src/server/apiHandlers.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：IDE 模式必须返回 `{ hasIde:true }`，失败时 UI 明确报错（不允许静默回退） |
| `claude.*`（旧 HTTP Claude 桥接） | `frontend/src/services/ideaBridge.ts:aiAgentBridgeService.*` | **缺失** | `vscode-extension/src/server/apiHandlers.ts`（明确返回 deprecated 错误） | `JB=MISSING; VS=OK(显式失败); Parity=PARTIAL` | 前端不应因调用到旧路径而崩溃；应返回 `success=false` + 清晰 error |

---

## 3) RSocket `/ide-rsocket`（IDE 集成）

| 契约项 | 前端调用点 | JB 实现位置 | VS Code 实现位置 | 状态 | 验收用例 |
|---|---|---|---|---|---|
| `ide.openFile` | `frontend/src/services/ideaRSocket.ts` / `frontend/src/services/ideaApi.ts` | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：打开文件 + 行列定位；offset 选择（start/end）一致 |
| `ide.showDiff` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：同文件 diff 打开；标题一致性可放宽 |
| `ide.showMultiEditDiff` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：多 edit 计算 before/after 一致（至少不反向） |
| `ide.showEditPreviewDiff` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：预览 diff 与权限请求联动正常 |
| `ide.showEditFullDiff` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：oldString/newString/replaceAll 与 originalContent 的回退逻辑一致 |
| `ide.showMarkdown` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：Markdown 预览可打开 |
| `ide.getTheme` | `frontend/src/services/ideaRSocket.ts` / `frontend/src/services/themeService.ts` | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts`（复用 `getIdeTheme()`） | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：颜色字段齐全；fontFamily/fontSize/editorFontFamily/editorFontSize 合理 |
| `ide.getSettings`（关键） | `frontend/src/stores/settingsStore.ts:loadIdeSettings()` | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt`（从 `AgentSettingsService` 生成 thinkingLevels + options） | `vscode-extension/src/server/rsocket/ideRSocketServer.ts:buildIdeSettings()`（已补齐 options，但仍需验收 thinkingLevels/默认值） | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 下拉选项（permission/codex effort/summary/sandbox）不得为空；thinkingLevels/默认值需对齐 |
| `ide.getLocale` / `ide.setLocale` | `frontend/src/services/ideaApi.ts`（初始化同步语言） | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：语言持久化与前端 i18n 同步正常（VS 仅存储） |
| `ide.getProjectPath` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：multi-root 策略：两端需文档化/对齐 |
| `ide.getActiveFile` | 工具卡片/高亮/上下文 | `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：selection / selectedContent 截断策略一致或文档化 |
| `ide.getOriginalContent` | 工具 diff/回滚 | `jetbrains-plugin/...`（LocalHistory label） | `vscode-extension/src/server/rsocket/ideRSocketServer.ts`（SnapshotStore） | `JB=OK; VS=PARTIAL; Parity=PARTIAL`（持久化语义不同） | 重启 IDE 后：JB 应仍能拿到历史/label；VS 目前内存丢失（需 P2 对齐或承认差异） |
| `ide.getFileHistoryContent` | 回滚栏/历史预览 | `jetbrains-plugin/...`（LocalHistory） | `vscode-extension/src/server/rsocket/ideRSocketServer.ts`（SnapshotStore） | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 历史可追溯范围/时间戳语义对齐；重启后一致性 |
| `ide.rollbackFile` / `ide.batchRollback` | 回滚栏 | `jetbrains-plugin/...` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts` | `JB=OK; VS=PARTIAL; Parity=PARTIAL`（VS 无持久化历史） | 同上（重启后仍可 rollback 视为 1:1） |
| `ide.getBackgroundableTerminals` / `ide.terminalBackground` | 终端后台化 | `jetbrains-plugin/...` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts`（TerminalTaskManager） | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：同一 toolUseId：后台化事件流状态一致 |
| `ide.reportSessionState` | `frontend/src/services/ideaRSocket.ts` | `jetbrains-plugin/...` | `vscode-extension/src/server/rsocket/ideRSocketServer.ts`（目前直接 OK） | `JB=OK; VS=PARTIAL; Parity=PARTIAL` | 若 JB 有实际消费逻辑，VS 也应至少记录/转发，否则文档化“忽略” |

---

## 4) RSocket `/rsocket`（AI 会话 + 工具流式）

| 契约项 | 前端调用点 | JB 实现位置 | VS Code 实现位置 | 状态 | 验收用例 |
|---|---|---|---|---|---|
| `agent.connect` | `frontend/src/services/rsocket/RSocketSession.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt` | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：connect 后 sessionId/provider 返回可用；多连接隔离（connectId） |
| `agent.query` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：流式事件序列完整（messageStart/contentBlockStart/delta/stop/result） |
| `agent.queryWithContent` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：content blocks 映射一致；至少不丢用户输入 |
| `agent.events`（关键，全局事件流） | `frontend/src/services/rsocket/RSocketSession.ts` | `ai-agent-server/src/main/kotlin/com/asakii/server/rsocket/RSocketHandler.kt:handleGlobalEvents()`（持续流） | `vscode-extension/src/server/rsocket/agentRSocketServer.ts`（订阅后持续，且会广播 query/tool 等事件） | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：订阅后不得立即 complete；应能收到 query 流中同样的 RpcMessage（tool_use/tool_result/stream_event/result 等） |
| `agent.interrupt` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：interrupt 后应能中断流式输出并回到可交互状态 |
| `agent.disconnect` / `agent.disposeSession` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：资源释放与历史落盘一致（或文档化差异） |
| `agent.setModel` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：setModel 后下一次 query 生效 |
| `agent.setPermissionMode` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：permissionMode 影响工具审批行为（尤其写文件/命令） |
| `agent.setSandboxMode` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：sandboxMode 对文件/命令执行权限生效；边界一致 |
| `agent.setMaxThinkingTokens` | 同上 | 同上 | 同上 | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：thinking token budget 生效；与 ide.getSettings 默认值一致 |
| `agent.getHistory` / `agent.truncateHistory` | `frontend/src/services/rsocket/RSocketSession.ts` + `frontend/src/services/aiAgentService.ts` | `ai-agent-server/...` | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：truncate 后历史行数/返回值一致；与 HTTP history/load.pb 语义一致 |
| `agent.getMcpStatus` / `agent.reconnectMcp` / `agent.getMcpTools` | MCP 面板 | `ai-agent-server/...`（McpProviders + Gateway） | `vscode-extension/src/server/rsocket/agentRSocketServer.ts`（McpHttpGateway） | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：MCP tools 列表字段一致；reconnect 后状态更新 |
| `agent.runToBackground` / `agent.runInBackground` / `agent.bashRunToBackground` | 背景任务 | `ai-agent-server/...` | `vscode-extension/src/server/rsocket/agentRSocketServer.ts` | `JB=OK; VS=OK; Parity=OK` | NEEDS_TEST：任务后台化后：能继续接收事件、任务状态与 UI 同步 |

---

## 5) “1:1 必须项”（不全在 `vscode-api-compat.md` 里，但前端/体验依赖）

| 必须项 | 前端/体验依赖 | JB 现状 | VS Code 现状 | 结论 |
|---|---|---|---|---|
| HTTP Token 策略一致性 | `frontend/src/utils/serverAuth.ts` / 多处 `withServerToken()` | JB HTTP 目前未强制 token | VS HTTP 强制 `X-Claude-Code-Plus-Token`（`HttpApiServer.ts:isAuthorized()`） | 若要“严格 1:1”，需：要么 JB 也引入 token，要么 VS 放宽/白名单部分 endpoint（如 `/api/font/*`）并文档化 |
| `/api/font/*` 与 token 兼容 | `frontend/src/services/themeService.ts:loadFont()` | JB 可工作（无 token 要求，带上也无害） | VS Code 已实现 `GET /api/font/{fontFamily}` 且需要 token（现已在前端请求中补齐 header） | 阻断已解除；但 VS Code 字体来源为“系统字体 best-effort”，与 JB 的“IDE/JBR 内置字体”仍非严格等价 |
| Claude CLI `mcp_message` 支持 | CLI 可能主动发 `mcp_message`（工具/状态） | JB SDK 有解析（`claude-agent-sdk/.../MessageParser.kt`） | VS `vscode-extension/src/sdk/claude/claudeCli.ts` 明确 `not implemented`（需补齐） | VS 相比 JB 不 1:1（P2） |
| Rollback/History 持久化语义 | 回滚栏/历史对比 | JB 基于 LocalHistory/label（可跨重启） | VS 基于内存 Snapshot（重启即丢） | VS 相比 JB 不 1:1（P2，除非明确接受差异） |

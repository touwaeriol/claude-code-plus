# 1:1 收敛路线图（P0/P1/P2）

基准：`docs/plans/vscode-parity-matrix.md` 的每一行都是“验收项”。目标是让：
- `JB=OK; VS=OK; Parity=OK`（契约全绿 + 行为等价）

---

## P0（协议阻断项：先保证“前端不炸 + 契约可用”）

这些不补齐，前端会直接出现不可用/功能缺失（或无法严格宣称 1:1）。

### JetBrains（JB）侧

- 补齐 `GET /api/health` (DONE)
  - 现状：只有 `GET /health`（且返回结构含 `port`）
  - 目标：与 `frontend/src/services/backend/BackendSessionFactory.ts` 对齐，返回 `{ "status":"ok" }`
  - 位置：`ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`

- 补齐 `GET /api/codex/health` (DONE)
  - 现状：缺失（但 JB 已有 `/api/codex/*` 能力，且有 `codexBackendProvider` 概念）
  - 目标：codex 可用时 `{ "status":"ok" }`，不可用 `{ "status":"unavailable" }`（或非 2xx，但需与前端约定）
  - 位置：`ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`

- 补齐 `POST /api/` 的 `settings.get`（关键） (DONE)
  - 现状：`frontend/src/stores/settingsStore.ts:loadSettings()` 依赖 `settings.get`，但 JB action 缺失
  - 目标：返回 `{ success:true, data:{ settings:<Settings> } }`
  - 敏感字段（如 codexApiKey）策略：
    - 可先不返回（前端允许缺省），但必须文档化，并确保 VS/ JB 口径一致
  - 位置：`ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt`

### VS Code 侧

- 实现 `/rsocket` 的 `agent.events`（关键） (DONE)
  - 现状：`vscode-extension/src/server/rsocket/agentRSocketServer.ts` 已实现持续 stream，并会把 query/tool 产生的 RpcMessage 广播到 `agent.events`
  - 目标：继续验收并补齐“后台任务/状态变化”等非 query 产生的全局事件（若需要严格 1:1）

- 补齐 `/ide-rsocket` 的 `ide.getSettings` 选项列表（关键） (DONE)
  - 现状：`vscode-extension/src/server/rsocket/ideRSocketServer.ts:buildIdeSettings()` 已补齐 options 为非空（permission/codex effort/summary/sandbox）
  - 目标：与 JB `jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsRSocketHandler.kt` 输出语义对齐（thinkingLevels/默认值等仍需验收）

- 实现 `GET /api/font/{fontFamily}` (DONE)
  - 现状：JB 已实现；VS 缺失
  - 目标：满足 `frontend/src/services/themeService.ts:loadFont()`（404 合理，200 返回字体 bytes）
  - 注意：VS HTTP 强制 token；前端已在字体下载请求中补齐 `X-Claude-Code-Plus-Token` header

---

## P1（Codex 1:1：对齐 JB 的 `/api/codex/*` 能力）

如果要宣称 VS Code 与 JB 在 Codex 上 1:1，必须补齐这些 HTTP 路由（即使暂时返回 503，也要与 JB 行为一致）。

- VS Code：实现 `/api/codex/*`（对齐 JB 现有路由）
  - 目标：与 `ai-agent-server/src/main/kotlin/com/asakii/server/HttpApiServer.kt` 的 `route("/api/codex")` 行为一致
  - 清单（最少集）：
    - `POST /api/codex/thread/start`
    - `POST /api/codex/thread/resume`
    - `POST /api/codex/thread/archive`
    - `POST /api/codex/turn/start`
    - `POST /api/codex/turn/interrupt`
    - `GET /api/codex/config`
    - `PUT /api/codex/config`
    - `GET /api/codex/thread/{threadId}/state`
  - 位置：`vscode-extension/src/server/HttpApiServer.ts`（新增分支/路由）或拆到 `apiHandlers.ts`
  - 状态：DONE（实现于 `vscode-extension/src/server/HttpApiServer.ts`，内部由 `vscode-extension/src/server/codex/codexBackendProvider.ts` 提供）

- VS Code：让 `GET /api/codex/health` 能真实反映可用性
  - 现状：固定 `{ status:'unavailable' }`
  - 目标：当 VS 侧 Codex 后端（provider/session）可用时返回 `{ status:'ok' }`
  - 状态：DONE（会尝试启动 Codex app-server；启动失败则返回 `unavailable`）

- VS Code：history provider=codex 对齐
  - `GET /api/history/sessions?provider=codex`
  - `POST /api/history/load.pb?provider=codex`
  - `POST /api/history/metadata.pb?provider=codex`
  - `DELETE /api/history/sessions/:id?provider=codex`

---

## P2（语义/体验对齐：减少“看起来能用但不等价”）

- VS Code：Rollback/History 持久化
  - 现状：`vscode-extension/src/ide/rollback/snapshotStore.ts` 以内存为主，重启后丢失；与 JB LocalHistory 语义不等价
  - 目标：至少“重启后仍可 rollback 到 toolUseId 对应快照”，或明确接受差异并写入验收表（不建议）

- VS Code：Claude CLI `mcp_message` 支持
  - 现状：`vscode-extension/src/sdk/claude/claudeCli.ts` 未实现 `mcp_message`
  - 目标：与 JB `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/protocol/MessageParser.kt` 能力等价（至少不崩溃 + 能透传/渲染）

- Token/安全策略口径统一（或清晰文档化）
  - 最终目标：`docs/plans/vscode-parity-matrix.md` 的“必须项”也能变成 OK

---

## 关键决策点（会影响后续走向）

- Token 策略如何 1:1？
  1) **让 JB 也引入 token**（最严格的一致性；但需要改 JB 资源注入/请求头处理）
  2) **VS Code 放宽/白名单部分 endpoint**（例如 `/api/font/*`），并额外做 origin/loopback 校验（可接受但需写清楚）
  3) **改前端**：为字体下载等补上 `withServerToken()`（最少后端改动，但违背“前端尽量不改”）

- Codex 走向：HTTP vs RSocket
  - 推荐默认：**VS Code 侧补齐 HTTP `/api/codex/*`**（最符合“前端尽量不改”的原则，且与 JB 现有实现一致）

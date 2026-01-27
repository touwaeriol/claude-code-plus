# IDE API 契约（VS Code 扩展 / IDEA 插件共用，协议统一为 `ide.*`）

本文档用于约束 **IDE 宿主侧后端（VS Code Extension Host / JetBrains 插件后端）** 与 **现有 `frontend/`** 之间的协议契约：前端尽量不改，后端按 JetBrains 版能力复刻/对齐。

强约束（按最新约定）：
- 只保留 `ide.*`（HTTP action + RSocket route）；不保留 `jetbrains.*` / `idea.*` 兼容别名
- 不做自动回退：IDE 模式连接失败应明确报错，不允许悄悄降级到 browser/default settings

> 说明：前端的调用点以 `frontend/src` 为准（通过 grep 汇总），并非“理想接口设计”。后端实现需以这里的契约为准。

## 1. HTTP API

### 1.1 `POST /api/`

> 安全约定（VS Code 版）：所有 HTTP 请求必须携带 Header：`X-Claude-Code-Plus-Token: <token>`。
> token 由扩展在 Webview HTML 中注入（例如 `window.__serverToken`），用于防止本机其他网页/进程伪造请求（CSRF）。
请求：

```json
{
  "action": "ide.openFile",
  "data": {}
}
```

响应（统一格式，见 `frontend/src/types/bridge.ts`）：

```json
{
  "success": true,
  "data": {},
  "error": ""
}
```

> 注意：`data` 的结构因 `action` 而异；失败时 `success=false` 且应返回 `error` 字符串。

#### 1.1.1 已固化的 `action` 列表（来自前端）

（来源：`grep -R "query('" frontend/src | ... | sort | uniq`；注意：该方式只覆盖 `ideaBridge.query(...)`，不包含少量“直接 fetch /api/”的 action）

- `claude.connect`
- `claude.query`
- `claude.interrupt`
- `claude.disconnect`
- `ide.openFile`
- `ide.showDiff`
- `ide.searchFiles`
- `ide.getFileContent`
- `ide.getLocale`
- `ide.setLocale`
- `ide.getProjectPath`
- `ide.openUrl`
- `ide.hasIdeEnvironment`（来源：`frontend/src/services/ideaApi.ts`，用于检测是否处于 IDE 宿主环境，以及是否启用 `/ide-rsocket` 集成）
- `node.detect`
- `settings.get`
- `settings.getDefault`
- `models.getAvailable`

#### 1.1.2 各 `action` 的数据约定（最小说明）

> 更细的字段/示例可直接参考：`frontend/src/services/ideaBridge.ts`、`frontend/src/App.vue`、`frontend/src/stores/settingsStore.ts`。

- `ide.openFile`
  - `data`: `{ filePath: string, line?: number, column?: number, endLine?: number, selectContent?: boolean, content?: string, selectionStart?: number, selectionEnd?: number }`
  - `success`: 打开文件并定位（line/column 以 1 为基准，后端需自行转换为 0 基准）
- `ide.showDiff`
  - `data`: `{ filePath: string, oldContent: string, newContent: string, title?: string, rebuildFromFile?: boolean, edits?: { oldString: string, newString: string, replaceAll: boolean }[] }`
  - `success`: 打开 Diff 视图（VS Code 侧可使用 `vscode.diff` + `TextDocumentContentProvider`）
- `ide.searchFiles`
  - `data`: `{ query: string, maxResults?: number }`
  - `data`（建议）：`{ files: { name: string, path: string }[] }`
  - 备注：此接口与 `GET /api/files/search` 不同，前者用于旧 UI 调用路径，后者用于 `@` 文件搜索
- `ide.getFileContent`
  - `data`: `{ filePath: string, lineStart?: number, lineEnd?: number }`
  - `data`（建议）：`{ content: string }`（如指定行范围可返回截断内容）
- `ide.getLocale`
  - `data`: `undefined`
  - `data`（建议）：`{ locale: string }`
- `ide.setLocale`
  - `data`: `string`（直接传 locale 字符串）
  - `success`: 更新后端/IDE 语言（VS Code 侧一般只需持久化配置 + 通知前端刷新）
- `ide.getProjectPath`
  - `data`: `{}`
  - `data`: `{ projectPath: string }`
- `ide.openUrl`
  - `data`: `{ url: string }`
  - `success`: 使用系统浏览器打开链接
- `ide.hasIdeEnvironment`
  - `data`: `undefined`
  - `data`: `{ hasIde: boolean }`（VS Code 版可用此字段表示“是否已实现 `/ide-rsocket` 能力”；未实现可返回 `false`）
- `node.detect`
  - `data`: `{}`
  - `data`: `{ found: boolean, path?: string, version?: string, error?: string }`（见 `frontend/src/App.vue`）
- `settings.get`
  - `data`: `undefined`
  - `data`: `{ settings: any }`（前端会做 migrate；建议存储为 `Settings` 结构，见 `frontend/src/stores/settingsStore.types.ts`）
- `settings.getDefault`
  - `data`: `undefined`
  - `data`: `HttpDefaultSettings`（见 `frontend/src/stores/settingsStore.types.ts`）
- `models.getAvailable`
  - `data`: `undefined`
  - `data`：
    - `{ claudeModels: BackendModelInfo[], codexModels: BackendModelInfo[], defaultBackendType: 'claude'|'codex', defaultClaudeModelId: string, defaultCodexModelId: string }`
  - 备注：`BackendModelInfo` 见 `frontend/src/types/backend.ts`；前端用于填充模型选择器与默认值回填
- `claude.*`
  - 说明：这是旧的 HTTP Claude 桥接（非 RSocket 流式）。VS Code 版优先实现 RSocket `/rsocket`，HTTP 仅需保证“不会导致前端崩溃”（可返回 `success=false` + 错误提示）。

### 1.2 `GET /api/files/search`

来源：`frontend/src/services/fileSearchService.ts`

请求：

`GET /api/files/search?query=<string>&maxResults=<number>`

响应：

```json
{
  "success": true,
  "data": [
    {
      "name": "foo.ts",
      "relativePath": "src/foo.ts",
      "absolutePath": "C:/repo/src/foo.ts",
      "fileType": "ts",
      "size": 123,
      "lastModified": 1700000000000,
      "isDirectory": false
    }
  ],
  "error": "",
  "errorCode": ""
}
```

- `errorCode === "INDEXING"`：前端会显示“正在索引”，并重试（VS Code 侧一般不需要该状态，除非自行做索引缓存）

### 1.3 `GET /api/health`

来源：`frontend/src/services/backend/BackendSessionFactory.ts`

响应：

```json
{ "status": "ok" }
```

### 1.4 `GET /api/codex/health`

来源：`frontend/src/services/backend/BackendSessionFactory.ts`

响应：
- 可用：`{ "status": "ok" }`
- 不可用：可返回 `{ "status": "unavailable" }` 或直接用非 2xx（前端会视为不可用）

### 1.5 历史记录（`/api/history/*`）

来源：`frontend/src/services/aiAgentService.ts`

> 说明：历史接口目前在前端用于“会话列表/历史加载/删除/编辑重发（truncate）”。VS Code 版需要至少保证这些接口存在且不会导致前端崩溃。

#### 1.5.1 `GET /api/history/sessions`

请求：

`GET /api/history/sessions?offset=<number>&maxResults=<number>&provider=<claude|codex>`

响应（JSON）：

```json
{
  "sessions": [
    {
      "sessionId": "xxx",
      "firstUserMessage": "preview...",
      "timestamp": 1700000000000,
      "messageCount": 12,
      "projectPath": "C:/repo",
      "customTitle": "optional"
    }
  ]
}
```

#### 1.5.2 `POST /api/history/load.pb`

请求：Protobuf `LoadHistoryRequest`（`frontend/src/services/rsocket/protoCodec.ts` 负责编码）

响应：Protobuf `HistoryResult`（`application/octet-stream`）

#### 1.5.3 `POST /api/history/metadata.pb`

请求：Protobuf `GetHistoryMetadataRequest`

响应：Protobuf `HistoryMetadata`（`application/octet-stream`）

#### 1.5.4 `DELETE /api/history/sessions/<sessionId>`

响应（JSON）：

```json
{ "success": true }
```

## 2. RSocket（WebSocket Transport）

> 前端 RSocket 客户端：`frontend/src/services/rsocket/RSocketClient.ts`

> 安全约定（VS Code 版）：WebSocket 无法自定义 header，因此 `/rsocket`、`/ide-rsocket` 连接需携带 query 参数：
> `?token=<token>`（token 同 `window.__serverToken`，与 HTTP 的 `X-Claude-Code-Plus-Token` 一致）。

### 2.1 `/ide-rsocket`（IDE 集成能力）

前端调用入口：`frontend/src/services/ideaRSocket.ts`（文件名可保留历史命名，但对外路由统一为 `ide.*`）

**路由列表（来自前端 grep）**：

- `ide.openFile`
- `ide.showDiff`
- `ide.showMultiEditDiff`
- `ide.showEditPreviewDiff`
- `ide.showEditFullDiff`
- `ide.showMarkdown`
- `ide.getTheme`
- `ide.getSettings`
- `ide.getLocale`
- `ide.setLocale`
- `ide.getProjectPath`
- `ide.getActiveFile`
- `ide.getOriginalContent`
- `ide.getFileHistoryContent`
- `ide.rollbackFile`
- `ide.batchRollback`
- `ide.getBackgroundableTerminals`
- `ide.terminalBackground`
- `ide.reportSessionState`

**编码/解码**：

- Protobuf schema：`frontend/src/proto/jetbrains_api_pb.ts`
- 编解码封装：`frontend/src/services/ideaProtoCodec.ts`

### 2.2 `/rsocket`（AI 会话 + 工具流式）

前端调用入口：`frontend/src/services/rsocket/RSocketSession.ts`

**路由列表（来自前端 grep）**：

- `agent.connect`
- `agent.query`
- `agent.queryWithContent`
- `agent.events`
- `agent.interrupt`
- `agent.disconnect`
- `agent.disposeSession`
- `agent.setModel`
- `agent.setPermissionMode`
- `agent.setSandboxMode`
- `agent.setMaxThinkingTokens`
- `agent.getHistory`
- `agent.truncateHistory`
- `agent.getMcpStatus`
- `agent.reconnectMcp`
- `agent.getMcpTools`
- `agent.runToBackground`
- `agent.runInBackground`
- `agent.bashRunToBackground`

**编码/解码**：

- Protobuf schema：`frontend/src/proto/ai_agent_rpc_pb.ts`
- 编解码封装：`frontend/src/services/rsocket/protoCodec.ts`

## 3. 约束与注意事项（不做回退/兼容）

- 路径表达（约定）：
  - 推荐：所有 `filePath` 都使用 **绝对路径**（例如 `C:\repo\src\foo.ts` 或 `C:/repo/src/foo.ts`）
  - 允许：使用 **工作区相对路径**（例如 `src/foo.ts`），单工作区时相对 `workspaceFolders[0]`
  - Multi-root：相对路径可写为 `"<workspaceFolderName>/<relativePath>"`（例如 `app/src/foo.ts`）用于消歧；否则默认按第一个 workspace folder 解析
- 也接受 `file://...` URI（将用 `vscode.Uri.parse` 解析）
- IDE 模式要求：当 `window.__IDE_MODE__ = true` 时，必须可用 `/ide-rsocket`，连接失败应在 UI 中明确报错；不允许自动回退到 `settings.getDefault` / browser mode。
- 对于 VS Code 的“设置保存”：
  - 非敏感设置：落地到 VS Code Settings（`workspace.getConfiguration().update(...)`）
  - 敏感信息（API Key/Token）：落地到 `SecretStorage`

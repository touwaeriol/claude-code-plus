# AI Agent SDK 与 Claude Agent SDK 数据模型/转换说明

## Claude Agent SDK 输出（`claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types`）
- Message 类型：`user`、`assistant`、`system`、`result`、`stream_event`，其中 `assistant/user` 持有 `content` 数组，`result` 携带耗时/费用，`stream_event` 内含 Anthropic 流式事件。
- ContentBlock：`text`、`thinking`、`tool_use`（`id/name/input`）、`tool_result`（`tool_use_id/content/is_error`）等。
- StreamEvent：`message_start`（message 对象，可能不含内容/模型）、`content_block_start`、`content_block_delta`（`text_delta`/`thinking_delta`/`input_json_delta`）、`content_block_stop`、`message_delta`（usage）、`message_stop`。

## AI Agent SDK 统一模型（`ai-agent-sdk/src/main/kotlin/com/asakii/ai/agent/sdk/model`）
- NormalizedStreamEvent：`MessageStartedEvent`、`ContentStarted/Delta/CompletedEvent`、`TurnCompleted/FailedEvent`、`ResultSummaryEvent`、`AssistantMessageEvent`、`UserMessageEvent`，保留 `provider` 与 `sessionId`。
- UiStreamEvent（前端直接消费的流式事件）：`UiMessageStart`、`UiTextDelta`、`UiThinkingDelta`、`UiToolStart/Progress/Complete`、`UiMessageComplete`、`UiAssistantMessage`、`UiUserMessage`、`UiResultMessage` 等。
- UnifiedMessage/ContentBlock：前端统一数据结构，`tool_use`、`tool_result`、`command_execution`、`mcp_tool_call`、`todo_list` 等内容类型与 `ContentStatus`。

## 转换链路
1. Claude SDK 消息/流事件 → `ClaudeStreamAdapter`：保持 `tool_use` 原始 `id`，把 Anthropic 流事件转换为 `NormalizedStreamEvent`（`message_start` 可能只有 `message.id` 而 `content/model` 为空）。
2. `NormalizedStreamEvent` → `UiStreamAdapter`：拆分为 UI 级事件（文本/思考增量、工具开始/进度/完成、消息完成/错误等）。
3. `AiAgentStreamBridge` + `ai-agent-rpc-api`：UI 事件被序列化为 RPC 模型 `RpcMessage`（`RpcUserMessage`、`RpcAssistantMessage`、`RpcStreamEvent`、`RpcResultMessage`），通过 WebSocket `{"id":"req-x","type":"stream","data":{...}}` 推送给前端。
4. 前端 `AiAgentSession` → `rpcParser` 校验 → `sessionStore.normalizeRpcMessage` → `mapRpcContentBlock`/`handleStreamEvent` → `displayItemConverter` 渲染。

## 不同消息的典型输出
- **message_start**：新一轮 assistant 开始，`message` 里可能只有 `id`，`content/model` 可为空。
- **content_block_start/delta/stop**：文本/思考增量、`tool_use` 部分 JSON（`input_json_delta`）或文本增量。
- **assistant 消息**：`UiAssistantMessage`，包含完整内容块（例如多个 `tool_use` 起始块或最终文本），通常不携带 message id。
- **user 携带 tool_result**：工具完成后，后端以 `type=user` + `tool_result` 片段推送，更新工具状态。
- **result 消息**：`subtype=success/error`，包含耗时与 token 统计。

## 前端对应关系与发现问题
- `rpcParser` 对 `message_start` 的 `message.content` 采用严格数组校验，实际后端会下发 `content:null` 导致解析报错并中断后续 handler。
- `sessionStore.handleStreamEvent` 维护流式 assistant 占位消息并累积内容；但 `assistant` 消息（无 id）在 `handleNormalMessage` 中被生成新 id 并追加，导致思考/文本重复、工具调用被拆成多条消息，历史记录暴涨。
- 工具调用事件以 `assistant` 消息的 `tool_use` 片段送达；`tool_result` 通过 `user` 消息送达，需按 `tool_use_id` 归并。

## 修复思路（已在代码中落实）
- 放宽 `rpcParser.isMessageStartEvent`：允许 `message` 为空或 `content:null`，防止流式起始报错。
- `sessionStore.handleNormalMessage`：若存在当前流式 assistant 消息，使用 `mergeAssistantMessage` 按 `tool_use`/`thinking`/`text` 合并内容，复用流式消息的 id，并同步 displayItems，避免新增重复消息；仅在无流式消息时才追加新 assistant。

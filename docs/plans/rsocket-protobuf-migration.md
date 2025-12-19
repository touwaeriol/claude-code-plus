# RSocket + Protobuf 迁移计划

## 1. 概述

### 1.1 当前架构
- **协议**: 自定义 JSON-RPC over WebSocket
- **序列化**: Kotlinx Serialization JSON
- **代码量**: ~500 行核心 RPC 代码

### 1.2 目标架构
- **协议**: RSocket over WebSocket
- **序列化**: Protocol Buffers (Protobuf)
- **优势**:
  - 标准化协议，成熟稳定
  - 二进制序列化，更小体积
  - 强类型，跨语言兼容
  - 内置流式支持、背压、取消

---

## 2. 技术选型

### 2.1 后端 (Kotlin/JVM)

| 依赖 | 版本 | 用途 |
|-----|------|------|
| `io.rsocket.kotlin:rsocket-ktor-server` | 0.15.4 | RSocket 服务端 |
| `io.rsocket.kotlin:rsocket-transport-ktor-websocket-server` | 0.15.4 | WebSocket 传输层 |
| `com.google.protobuf:protobuf-kotlin` | 3.25.x | Protobuf 运行时 |
| `com.google.protobuf:protoc` | 3.25.x | Protobuf 编译器 |

### 2.2 前端 (TypeScript)

| 依赖 | 版本 | 用途 |
|-----|------|------|
| `rsocket-core` | 1.0.x | RSocket 核心 |
| `rsocket-websocket-client` | 1.0.x | WebSocket 客户端 |
| `protobufjs` | 7.x | Protobuf 运行时 |
| `ts-proto` | 1.x | TypeScript 代码生成 |

---

## 3. Protobuf Schema 设计

### 3.1 设计原则

- **单文件**: 所有定义放在一个 `ai_agent_rpc.proto` 文件中
- **完全对应**: 与 `RpcModels.kt` 数据结构完全一致
- **字段命名**: 使用 snake_case（Protobuf 惯例），生成代码时自动转换

### 3.2 完整 Schema（单文件）

```protobuf
// ai_agent_rpc.proto
// 与 ai-agent-rpc-api/src/main/kotlin/com/asakii/rpc/api/RpcModels.kt 完全对应

syntax = "proto3";
package aiagent.rpc;

option java_package = "com.asakii.rpc.proto";
option java_multiple_files = true;

// ============================================================================
// 枚举定义 - 对应 RpcModels.kt 中的枚举
// ============================================================================

// 对应 RpcProvider
enum Provider {
  PROVIDER_UNSPECIFIED = 0;
  PROVIDER_CLAUDE = 1;      // claude
  PROVIDER_CODEX = 2;       // codex
}

// 对应 RpcSessionStatus
enum SessionStatus {
  SESSION_STATUS_UNSPECIFIED = 0;
  SESSION_STATUS_CONNECTED = 1;      // connected
  SESSION_STATUS_DISCONNECTED = 2;   // disconnected
  SESSION_STATUS_INTERRUPTED = 3;    // interrupted
  SESSION_STATUS_MODEL_CHANGED = 4;  // model_changed
}

// 对应 RpcContentStatus
enum ContentStatus {
  CONTENT_STATUS_UNSPECIFIED = 0;
  CONTENT_STATUS_IN_PROGRESS = 1;  // in_progress
  CONTENT_STATUS_COMPLETED = 2;    // completed
  CONTENT_STATUS_FAILED = 3;       // failed
}

// 对应 RpcPermissionMode
enum PermissionMode {
  PERMISSION_MODE_UNSPECIFIED = 0;
  PERMISSION_MODE_DEFAULT = 1;              // default
  PERMISSION_MODE_BYPASS_PERMISSIONS = 2;   // bypassPermissions
  PERMISSION_MODE_ACCEPT_EDITS = 3;         // acceptEdits
  PERMISSION_MODE_PLAN = 4;                 // plan
}

// 对应 RpcSandboxMode
enum SandboxMode {
  SANDBOX_MODE_UNSPECIFIED = 0;
  SANDBOX_MODE_READ_ONLY = 1;           // read-only
  SANDBOX_MODE_WORKSPACE_WRITE = 2;     // workspace-write
  SANDBOX_MODE_DANGER_FULL_ACCESS = 3;  // danger-full-access
}

// ============================================================================
// Connect 相关 - 对应 RpcConnectOptions, RpcConnectResult, RpcCapabilities
// ============================================================================

// 对应 RpcConnectOptions
message ConnectOptions {
  // 通用配置
  optional Provider provider = 1;
  optional string model = 2;
  optional string system_prompt = 3;
  optional string initial_prompt = 4;
  optional string session_id = 5;
  optional string resume_session_id = 6;
  map<string, string> metadata = 7;

  // Claude 相关配置
  optional PermissionMode permission_mode = 10;
  optional bool dangerously_skip_permissions = 11;
  optional bool allow_dangerously_skip_permissions = 12;
  optional bool include_partial_messages = 13;
  optional bool continue_conversation = 14;
  optional bool thinking_enabled = 15;

  // Codex 相关配置
  optional string base_url = 20;
  optional string api_key = 21;
  optional SandboxMode sandbox_mode = 22;
}

// 对应 RpcCapabilities
message Capabilities {
  bool can_interrupt = 1;
  bool can_switch_model = 2;
  bool can_switch_permission_mode = 3;
  repeated PermissionMode supported_permission_modes = 4;
  bool can_skip_permissions = 5;
  bool can_send_rich_content = 6;
  bool can_think = 7;
  bool can_resume_session = 8;
}

// 对应 RpcConnectResult
message ConnectResult {
  string session_id = 1;
  Provider provider = 2;
  SessionStatus status = 3;
  optional string model = 4;
  optional Capabilities capabilities = 5;
  optional string cwd = 6;
}

// 对应 RpcSetPermissionModeResult
message SetPermissionModeResult {
  PermissionMode mode = 1;
  bool success = 2;
}

// 对应 RpcStatusResult
message StatusResult {
  SessionStatus status = 1;
}

// 对应 RpcSetModelResult
message SetModelResult {
  SessionStatus status = 1;
  string model = 2;
}

// ============================================================================
// 内容块 - 对应 RpcContentBlock 及其子类型
// ============================================================================

// 对应 RpcContentBlock (sealed interface)
message ContentBlock {
  oneof block {
    TextBlock text = 1;
    ThinkingBlock thinking = 2;
    ToolUseBlock tool_use = 3;
    ToolResultBlock tool_result = 4;
    ImageBlock image = 5;
    CommandExecutionBlock command_execution = 6;
    FileChangeBlock file_change = 7;
    McpToolCallBlock mcp_tool_call = 8;
    WebSearchBlock web_search = 9;
    TodoListBlock todo_list = 10;
    ErrorBlock error = 11;
    UnknownBlock unknown = 12;
  }
}

// 对应 RpcTextBlock
message TextBlock {
  string text = 1;
}

// 对应 RpcThinkingBlock
message ThinkingBlock {
  string thinking = 1;
  optional string signature = 2;
}

// 对应 RpcToolUseBlock
message ToolUseBlock {
  string id = 1;
  string tool_name = 2;
  string tool_type = 3;
  optional bytes input_json = 4;  // JsonElement 序列化为 JSON bytes
  ContentStatus status = 5;
}

// 对应 RpcToolResultBlock
message ToolResultBlock {
  string tool_use_id = 1;
  optional bytes content_json = 2;  // JsonElement 序列化为 JSON bytes
  bool is_error = 3;
}

// 对应 RpcImageBlock
message ImageBlock {
  ImageSource source = 1;
}

// 对应 RpcImageSource
message ImageSource {
  string type = 1;
  string media_type = 2;
  optional string data = 3;
  optional string url = 4;
}

// 对应 RpcCommandExecutionBlock
message CommandExecutionBlock {
  string command = 1;
  optional string output = 2;
  optional int32 exit_code = 3;
  ContentStatus status = 4;
}

// 对应 RpcFileChangeBlock
message FileChangeBlock {
  ContentStatus status = 1;
  repeated FileChange changes = 2;
}

// 对应 RpcFileChange
message FileChange {
  string path = 1;
  string kind = 2;
}

// 对应 RpcMcpToolCallBlock
message McpToolCallBlock {
  optional string server = 1;
  optional string tool = 2;
  optional bytes arguments_json = 3;  // JsonElement
  optional bytes result_json = 4;      // JsonElement
  ContentStatus status = 5;
}

// 对应 RpcWebSearchBlock
message WebSearchBlock {
  string query = 1;
}

// 对应 RpcTodoListBlock
message TodoListBlock {
  repeated TodoItem items = 1;
}

// 对应 RpcTodoItem
message TodoItem {
  string text = 1;
  bool completed = 2;
}

// 对应 RpcErrorBlock
message ErrorBlock {
  string message = 1;
}

// 对应 RpcUnknownBlock
message UnknownBlock {
  string type = 1;
  string data = 2;
}

// ============================================================================
// 消息内容 - 对应 RpcMessageContent
// ============================================================================

// 对应 RpcMessageContent
message MessageContent {
  repeated ContentBlock content = 1;
  optional string model = 2;
}

// ============================================================================
// RPC 消息 - 对应 RpcMessage (sealed interface)
// ============================================================================

// 对应 RpcMessage
message RpcMessage {
  Provider provider = 1;

  oneof message {
    UserMessage user = 2;
    AssistantMessage assistant = 3;
    ResultMessage result = 4;
    StreamEvent stream_event = 5;
    ErrorMessage error = 6;
    StatusSystemMessage status_system = 7;
    CompactBoundaryMessage compact_boundary = 8;
  }
}

// 对应 RpcUserMessage
message UserMessage {
  MessageContent message = 1;
  optional string parent_tool_use_id = 2;
  optional bool is_replay = 3;
}

// 对应 RpcAssistantMessage
message AssistantMessage {
  MessageContent message = 1;
}

// 对应 RpcResultMessage
message ResultMessage {
  string subtype = 1;
  optional int64 duration_ms = 2;
  optional int64 duration_api_ms = 3;
  bool is_error = 4;
  int32 num_turns = 5;
  optional string session_id = 6;
  optional double total_cost_usd = 7;
  optional bytes usage_json = 8;  // JsonElement
  optional string result = 9;
}

// 对应 RpcErrorMessage
message ErrorMessage {
  string message = 1;
}

// 对应 RpcStatusSystemMessage
message StatusSystemMessage {
  string subtype = 1;
  optional string status = 2;
  string session_id = 3;
}

// 对应 RpcCompactBoundaryMessage
message CompactBoundaryMessage {
  string subtype = 1;
  string session_id = 2;
  optional CompactMetadata compact_metadata = 3;
}

// 对应 RpcCompactMetadata
message CompactMetadata {
  optional string trigger = 1;
  optional int32 pre_tokens = 2;
}

// ============================================================================
// 流式事件 - 对应 RpcStreamEvent 及相关类型
// ============================================================================

// 对应 RpcStreamEvent
message StreamEvent {
  string uuid = 1;
  string session_id = 2;
  StreamEventData event = 3;
  optional string parent_tool_use_id = 4;
}

// 对应 RpcStreamEventData (sealed interface)
message StreamEventData {
  oneof event {
    MessageStartEvent message_start = 1;
    ContentBlockStartEvent content_block_start = 2;
    ContentBlockDeltaEvent content_block_delta = 3;
    ContentBlockStopEvent content_block_stop = 4;
    MessageDeltaEvent message_delta = 5;
    MessageStopEvent message_stop = 6;
  }
}

// 对应 RpcMessageStartEvent
message MessageStartEvent {
  optional MessageStartInfo message = 1;
}

// 对应 RpcMessageStartInfo
message MessageStartInfo {
  optional string id = 1;
  optional string model = 2;
  repeated ContentBlock content = 3;
}

// 对应 RpcContentBlockStartEvent
message ContentBlockStartEvent {
  int32 index = 1;
  ContentBlock content_block = 2;
}

// 对应 RpcContentBlockDeltaEvent
message ContentBlockDeltaEvent {
  int32 index = 1;
  Delta delta = 2;
}

// 对应 RpcContentBlockStopEvent
message ContentBlockStopEvent {
  int32 index = 1;
}

// 对应 RpcMessageDeltaEvent
message MessageDeltaEvent {
  optional bytes delta_json = 1;  // JsonElement
  optional Usage usage = 2;
}

// 对应 RpcMessageStopEvent
message MessageStopEvent {}

// ============================================================================
// Delta 类型 - 对应 RpcDelta
// ============================================================================

// 对应 RpcDelta (sealed interface)
message Delta {
  oneof delta {
    TextDelta text_delta = 1;
    ThinkingDelta thinking_delta = 2;
    InputJsonDelta input_json_delta = 3;
  }
}

// 对应 RpcTextDelta
message TextDelta {
  string text = 1;
}

// 对应 RpcThinkingDelta
message ThinkingDelta {
  string thinking = 1;
}

// 对应 RpcInputJsonDelta
message InputJsonDelta {
  string partial_json = 1;
}

// ============================================================================
// Usage 统计 - 对应 RpcUsage
// ============================================================================

// 对应 RpcUsage
message Usage {
  optional int32 input_tokens = 1;
  optional int32 output_tokens = 2;
  optional int32 cached_input_tokens = 3;
  optional Provider provider = 4;
  optional bytes raw_json = 5;  // JsonElement
}

// ============================================================================
// 历史会话 - 对应 RpcHistory, RpcHistorySession
// ============================================================================

// 对应 RpcHistory
message History {
  repeated RpcMessage messages = 1;
}

// 对应 RpcHistorySession
message HistorySession {
  string session_id = 1;
  string first_user_message = 2;
  int64 timestamp = 3;
  int32 message_count = 4;
  string project_path = 5;
}

// 对应 RpcHistorySessionsResult
message HistorySessionsResult {
  repeated HistorySession sessions = 1;
}

// ============================================================================
// RPC 请求/响应（RSocket 路由用）
// ============================================================================

message QueryRequest {
  string message = 1;
}

message QueryWithContentRequest {
  repeated ContentBlock content = 1;
}

message SetModelRequest {
  string model = 1;
}

message SetPermissionModeRequest {
  PermissionMode mode = 1;
}

message GetHistorySessionsRequest {
  int32 max_results = 1;
}
```

### 3.3 对应关系表

| Kotlin (RpcModels.kt) | Protobuf (ai_agent_rpc.proto) |
|----------------------|-------------------------------|
| `RpcProvider` | `Provider` |
| `RpcSessionStatus` | `SessionStatus` |
| `RpcContentStatus` | `ContentStatus` |
| `RpcPermissionMode` | `PermissionMode` |
| `RpcSandboxMode` | `SandboxMode` |
| `RpcConnectOptions` | `ConnectOptions` |
| `RpcCapabilities` | `Capabilities` |
| `RpcConnectResult` | `ConnectResult` |
| `RpcMessage` (sealed) | `RpcMessage` (oneof) |
| `RpcUserMessage` | `UserMessage` |
| `RpcAssistantMessage` | `AssistantMessage` |
| `RpcResultMessage` | `ResultMessage` |
| `RpcStreamEvent` | `StreamEvent` |
| `RpcErrorMessage` | `ErrorMessage` |
| `RpcContentBlock` (sealed) | `ContentBlock` (oneof) |
| `RpcTextBlock` | `TextBlock` |
| `RpcThinkingBlock` | `ThinkingBlock` |
| `RpcToolUseBlock` | `ToolUseBlock` |
| `JsonElement` | `bytes xxx_json` |

---

## 4. RSocket 路由设计

### 4.1 路由表

| 方法 | RSocket 交互模式 | 路由 | 请求类型 | 响应类型 |
|-----|-----------------|------|---------|---------|
| connect | Request-Response | `agent.connect` | `ConnectRequest` | `ConnectResponse` |
| query | Request-Stream | `agent.query` | `QueryRequest` | `Stream<RpcMessage>` |
| queryWithContent | Request-Stream | `agent.queryWithContent` | `QueryWithContentRequest` | `Stream<RpcMessage>` |
| interrupt | Request-Response | `agent.interrupt` | `InterruptRequest` | `InterruptResponse` |
| disconnect | Request-Response | `agent.disconnect` | `DisconnectRequest` | `DisconnectResponse` |
| setModel | Request-Response | `agent.setModel` | `SetModelRequest` | `SetModelResponse` |
| setPermissionMode | Request-Response | `agent.setPermissionMode` | `SetPermissionModeRequest` | `SetPermissionModeResponse` |
| getHistory | Request-Response | `agent.getHistory` | `GetHistoryRequest` | `GetHistoryResponse` |
| getHistorySessions | Request-Response | `agent.getHistorySessions` | `GetHistorySessionsRequest` | `GetHistorySessionsResponse` |

### 4.2 双向通信 (服务端调用客户端)

使用 RSocket 的 Request-Channel 实现双向通信：

```kotlin
// 后端 -> 前端 调用
interface ClientResponder {
    // 请求用户确认
    suspend fun requestConfirmation(request: ConfirmationRequest): ConfirmationResponse

    // 请求用户输入
    suspend fun requestInput(request: InputRequest): InputResponse
}
```

---

## 5. 实现步骤

### Phase 1: 基础设施 (1-2 天)

#### 1.1 创建 proto 模块
```
ai-agent-proto/
├── build.gradle.kts
├── src/main/proto/
│   └── ai_agent_rpc.proto    # 单文件，与 RpcModels.kt 完全对应
└── README.md
```

#### 1.2 配置 Gradle Protobuf 插件

```kotlin
// ai-agent-proto/build.gradle.kts
plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.9.4"
}

dependencies {
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("kotlin")
            }
        }
    }
}
```

#### 1.3 前端 Protobuf 配置

```json
// package.json 新增依赖
{
  "dependencies": {
    "protobufjs": "^7.2.6",
    "rsocket-core": "^1.0.0-alpha.3",
    "rsocket-websocket-client": "^1.0.0-alpha.3"
  },
  "devDependencies": {
    "ts-proto": "^1.174.0"
  }
}
```

### Phase 2: 后端 RSocket 实现 (2-3 天)

#### 2.1 添加 RSocket 依赖

```kotlin
// ai-agent-server/build.gradle.kts
dependencies {
    implementation("io.rsocket.kotlin:rsocket-ktor-server:0.15.4")
    implementation("io.rsocket.kotlin:rsocket-transport-ktor-websocket-server:0.15.4")
}
```

#### 2.2 创建 RSocket 路由处理器

```kotlin
// ai-agent-server/src/main/kotlin/com/asakii/server/RSocketHandler.kt
class RSocketHandler(
    private val rpcService: AiAgentRpcService
) {
    fun createAcceptor(): ConnectionAcceptor = ConnectionAcceptor {
        RSocketRequestHandler {
            // Request-Response
            requestResponse { request ->
                val route = request.metadata?.route ?: error("Missing route")
                when (route) {
                    "agent.connect" -> handleConnect(request)
                    "agent.interrupt" -> handleInterrupt(request)
                    // ...
                }
            }

            // Request-Stream
            requestStream { request ->
                val route = request.metadata?.route ?: error("Missing route")
                when (route) {
                    "agent.query" -> handleQuery(request)
                    "agent.queryWithContent" -> handleQueryWithContent(request)
                }
            }
        }
    }
}
```

#### 2.3 配置 Ktor RSocket 端点

```kotlin
// HttpApiServer.kt 添加
routing {
    rSocket("rsocket") {
        RSocketHandler(rpcService).createAcceptor()
    }
}
```

### Phase 3: 前端 RSocket 客户端 (2-3 天)

#### 3.1 创建 RSocket 客户端

```typescript
// frontend/src/utils/RSocketClient.ts
import { RSocketClient, JsonSerializer } from 'rsocket-core'
import { WebsocketClientTransport } from 'rsocket-websocket-client'

export class AgentRSocketClient {
    private client: RSocketClient
    private socket: RSocket | null = null

    async connect(url: string): Promise<void> {
        this.client = new RSocketClient({
            transport: new WebsocketClientTransport({
                url: url,
                wsCreator: (url) => new WebSocket(url)
            }),
            setup: {
                dataMimeType: 'application/x-protobuf',
                metadataMimeType: 'message/x.rsocket.routing.v0'
            }
        })

        this.socket = await this.client.connect()
    }

    // Request-Response
    async connectSession(request: ConnectRequest): Promise<ConnectResponse> {
        const payload = {
            data: ConnectRequest.encode(request).finish(),
            metadata: encodeRoute('agent.connect')
        }

        const response = await firstValueFrom(
            this.socket!.requestResponse(payload)
        )

        return ConnectResponse.decode(response.data)
    }

    // Request-Stream
    query(request: QueryRequest): Observable<RpcMessage> {
        const payload = {
            data: QueryRequest.encode(request).finish(),
            metadata: encodeRoute('agent.query')
        }

        return this.socket!.requestStream(payload).pipe(
            map(response => RpcMessage.decode(response.data))
        )
    }
}
```

#### 3.2 更新 SessionStore

```typescript
// 将 ClaudeRpcClient 替换为 AgentRSocketClient
// 保持对外 API 不变，内部使用 RSocket
```

### Phase 4: 迁移与兼容 (1-2 天)

#### 4.1 并行运行策略

- 保留旧的 `/ws` WebSocket 端点
- 新增 `/rsocket` RSocket 端点
- 前端通过配置切换

#### 4.2 渐进式迁移

1. 先迁移非流式 API (connect, setModel, etc.)
2. 再迁移流式 API (query, queryWithContent)
3. 最后迁移双向通信

### Phase 5: 清理与优化 (1 天)

- 移除旧的 JSON-RPC 代码
- 移除 `JsonRpcProtocol.kt`
- 更新文档

---

## 6. 文件变更清单

### 新增文件

| 文件 | 描述 |
|-----|------|
| `ai-agent-proto/` | 新模块：Protobuf 定义和生成代码 |
| `ai-agent-proto/src/main/proto/*.proto` | Protobuf schema 文件 |
| `ai-agent-server/src/.../RSocketHandler.kt` | RSocket 路由处理器 |
| `frontend/src/utils/RSocketClient.ts` | RSocket 客户端 |
| `frontend/src/proto/` | 生成的 TypeScript Protobuf 代码 |

### 修改文件

| 文件 | 变更 |
|-----|------|
| `ai-agent-server/build.gradle.kts` | 添加 RSocket 依赖 |
| `ai-agent-rpc-api/build.gradle.kts` | 可能改为依赖 proto 模块 |
| `frontend/package.json` | 添加 rsocket + protobuf 依赖 |
| `frontend/src/stores/sessionStore.ts` | 使用新客户端 |

### 删除文件 (迁移完成后)

| 文件 | 描述 |
|-----|------|
| `ai-agent-server/src/.../WebSocketHandler.kt` | 旧的 WebSocket 处理器 |
| `ai-agent-server/src/.../JsonRpcProtocol.kt` | JSON-RPC 协议定义 |
| `frontend/src/utils/ClaudeRpcClient.ts` | 旧的 RPC 客户端 |

---

## 7. 风险与注意事项

### 7.1 Protobuf 限制

- **JsonElement 处理**: 当前 `RpcToolUseBlock.input` 是 `JsonElement`，需要转为 `bytes` 存储 JSON 字符串
- **sealed interface**: Kotlin 的 sealed interface 用 Protobuf `oneof` 表示
- **可空类型**: Protobuf 3 默认所有字段可选，注意默认值处理

### 7.2 RSocket 注意事项

- **错误处理**: RSocket 错误通过 `onError` 信号传递，需要适配
- **取消传播**: RSocket 的 cancel 信号需要正确传播到 SDK
- **重连机制**: 需要实现自动重连逻辑

### 7.3 向后兼容

- 建议保留旧端点一段时间
- 可通过 feature flag 控制新旧切换

---

## 8. 时间估算

| 阶段 | 预计时间 |
|-----|---------|
| Phase 1: 基础设施 | 1-2 天 |
| Phase 2: 后端实现 | 2-3 天 |
| Phase 3: 前端实现 | 2-3 天 |
| Phase 4: 迁移兼容 | 1-2 天 |
| Phase 5: 清理优化 | 1 天 |
| **总计** | **7-11 天** |

---

## 9. 参考资料

- [RSocket Kotlin](https://github.com/rsocket/rsocket-kotlin)
- [RSocket JS](https://github.com/rsocket/rsocket-js)
- [Protocol Buffers](https://protobuf.dev/)
- [ts-proto](https://github.com/stephenh/ts-proto)

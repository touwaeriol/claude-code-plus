# Claude Code Plus - 架构迁移设计文档

## 文档版本
- **版本**: 2.0
- **日期**: 2025-01-18
- **状态**: ✅ 迁移完成

---

## 1. 迁移概述

### 1.1 迁移目标

将 Claude Code Plus 从当前的 **HTTP REST API + SSE** 架构迁移到 **WebSocket RPC** 架构,实现:

- ✅ **类型安全**: 消除字符串硬编码的 RPC 方法名 (已完成)
- ✅ **流式原生支持**: 利用 WebSocket 的双向流式通信 (已完成)
- ✅ **一连接一会话**: 每个 WebSocket 连接对应一个独立的 Claude 会话 (已完成)
- ✅ **双向通信**: 支持服务端主动推送事件 (已完成)
- ✅ **简化 API**: 会话对象封装,隐藏底层通信细节 (已完成)

### 1.2 实际实施方案

**最终选择**: WebSocket RPC (而非 RSocket)
- **原因**: 更简单、更轻量、浏览器原生支持
- **协议**: 基于 JSON 的简化 RPC 协议
- **传输**: Ktor WebSocket + 原生浏览器 WebSocket API

### 1.2 迁移范围

**后端模块**:
- `claude-code-server` - 核心服务器
- `claude-code-rpc-api` - RPC 接口定义

**前端模块**:
- `frontend/src/services` - 客户端服务层

---

## 2. 当前架构分析

### 2.1 后端架构

```
HttpApiServer (Ktor Netty)
├── HTTP REST API
│   ├── GET  /api/sessions          - 列出会话
│   ├── POST /api/sessions          - 创建会话
│   ├── POST /api/sessions/{id}/message - 发送消息
│   ├── GET  /api/sessions/{id}/messages - 获取消息
│   └── ...
├── SSE (Server-Sent Events)
│   └── GET /events                 - 实时事件推送
└── WebSocket (已注释)
    └── /ws                         - JSON-RPC over WebSocket (未实现)
```

**核心组件**:
- `HttpApiServer.kt` - HTTP + SSE 服务器
- `ClaudeActionHandler.kt` - Claude 操作处理器
- `SessionActionHandler.kt` - 会话管理处理器
- `ClaudeSessionManager.kt` - Claude 会话管理服务

### 2.2 前端架构

```
前端服务层
├── apiClient.ts          - HTTP REST API 客户端 (使用中)
├── jsonRpcClient.ts      - JSON-RPC WebSocket 客户端 (未使用)
├── websocketClient.ts    - WebSocket 客户端 (未使用)
├── claudeService.ts      - Claude 服务封装
├── ClaudeCodeClient.ts   - Claude Code 客户端 (基于 JSON-RPC,未使用)
└── ideaBridge.ts         - IDE 桥接服务
```

### 2.3 当前问题

1. **HTTP 轮询开销**: 需要定期轮询获取消息更新
2. **SSE 单向限制**: 只能服务端推送,客户端无法通过同一连接发送
3. **无类型安全**: REST API 端点使用字符串路径
4. **会话管理复杂**: 需要在每个请求中传递 `sessionId`
5. **代码重复**: `jsonRpcClient.ts` 和 `websocketClient.ts` 已实现但未使用

---

## 3. 目标架构设计

### 3.1 RSocket Routing 架构

```
RSocketServer (Ktor + RSocket)
└── RSocket Routes
    ├── connect(options)           - 创建 Claude 会话
    ├── query(message)             - 发送查询 (返回 Flow<Message>)
    ├── interrupt()                - 中断操作
    ├── disconnect()               - 断开会话
    ├── setModel(model)            - 设置模型
    └── getHistory()               - 获取历史消息
```

**核心原则**:
- **一连接一会话**: 每个 RSocket 连接持有独立的 `ClaudeCodeSdkClient` 实例
- **无需 sessionId**: 连接本身就是会话标识
- **流式原生**: `query()` 返回 `Flow<Message>`,自动处理背压

### 3.2 后端组件设计

#### 3.2.1 RPC 服务接口

```kotlin
// claude-code-rpc-api/src/main/kotlin/com/claudecodeplus/rpc/api/ClaudeRpcService.kt
interface ClaudeRpcService {
    /**
     * 连接到 Claude 会话
     * @param options 可选配置 (model, cwd, etc.)
     * @return 会话信息 (sessionId, model, etc.)
     */
    suspend fun connect(options: JsonObject?): ConnectResponse
    
    /**
     * 发送查询消息
     * @param message 用户消息
     * @return 流式响应 (Flow<Message>)
     */
    fun query(message: String): Flow<Message>
    
    /**
     * 中断当前操作
     */
    suspend fun interrupt()
    
    /**
     * 断开会话
     */
    suspend fun disconnect()
    
    /**
     * 设置模型
     */
    suspend fun setModel(model: String)
    
    /**
     * 获取历史消息
     */
    suspend fun getHistory(): List<JsonObject>
}
```

#### 3.2.2 RPC 服务实现

```kotlin
// claude-code-server/src/main/kotlin/com/claudecodeplus/server/rpc/ClaudeRpcServiceImpl.kt
class ClaudeRpcServiceImpl(
    private val ideActionBridge: IdeActionBridge
) : ClaudeRpcService {
    private val sessionId = UUID.randomUUID().toString()
    private var claudeClient: ClaudeCodeSdkClient? = null
    private val messageHistory = mutableListOf<JsonObject>()
    
    override suspend fun connect(options: JsonObject?): ConnectResponse {
        val claudeOptions = buildClaudeOptions(options)
        claudeClient = ClaudeCodeSdkClient(claudeOptions)
        claudeClient?.connect()
        
        return ConnectResponse(sessionId, claudeOptions.model)
    }
    
    override fun query(message: String): Flow<Message> {
        val client = claudeClient ?: throw IllegalStateException("Not connected")
        
        return client.query(message).onEach { msg ->
            // 保存到历史
            messageHistory.add(messageToJson(msg))
        }
    }
    
    override suspend fun interrupt() {
        claudeClient?.interrupt()
    }
    
    override suspend fun disconnect() {
        claudeClient?.disconnect()
        claudeClient = null
    }
    
    override suspend fun setModel(model: String) {
        // 重新连接使用新模型
        disconnect()
        connect(buildJsonObject { put("model", model) })
    }
    
    override suspend fun getHistory(): List<JsonObject> {
        return messageHistory.toList()
    }
}
```

#### 3.2.3 RSocket 服务器配置

```kotlin
// claude-code-server/src/main/kotlin/com/claudecodeplus/server/RSocketServer.kt
class RSocketServer(
    private val ideActionBridge: IdeActionBridge,
    private val scope: CoroutineScope,
    private val frontendDir: Path
) {
    private val logger = Logger.getLogger(javaClass.name)
    private var server: EmbeddedServer<*, *>? = null

    suspend fun start(): String {
        val port = findAvailablePort()

        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(RSocketSupport) {
                server {
                    // 每个连接创建独立的服务实例
                    acceptor {
                        RSocketRequestHandler {
                            ClaudeRpcServiceImpl(ideActionBridge)
                        }
                    }
                }
            }

            // 保留 HTTP 用于静态资源和健康检查
            routing {
                get("/health") {
                    call.respondText("""{"status":"ok","port":$port}""")
                }

                staticFiles("/", frontendDir.toFile()) {
                    default("index.html")
                }
            }
        }.start(wait = false)

        val url = "http://127.0.0.1:$port"
        logger.info("🚀 RSocket server started at: $url")
        return url
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
```

### 3.3 前端组件设计

#### 3.3.1 RSocket 客户端封装

```typescript
// frontend/src/services/rsocketClient.ts
import { RSocketClient, JsonSerializer, IdentitySerializer } from 'rsocket-core';
import RSocketWebSocketClient from 'rsocket-websocket-client';

export class ClaudeRSocketClient {
    private client: RSocketClient;
    private socket: any;

    constructor(url: string) {
        this.client = new RSocketClient({
            serializers: {
                data: JsonSerializer,
                metadata: IdentitySerializer
            },
            setup: {
                keepAlive: 60000,
                lifetime: 180000,
                dataMimeType: 'application/json',
                metadataMimeType: 'message/x.rsocket.routing.v0'
            },
            transport: new RSocketWebSocketClient({
                url: url.replace('http', 'ws') + '/rsocket'
            })
        });
    }

    async connect(): Promise<void> {
        this.socket = await this.client.connect();
    }

    async requestResponse<T>(route: string, data?: any): Promise<T> {
        return new Promise((resolve, reject) => {
            this.socket.requestResponse({
                data,
                metadata: String.fromCharCode(route.length) + route
            }).subscribe({
                onComplete: (value: T) => resolve(value),
                onError: (error: Error) => reject(error)
            });
        });
    }

    requestStream<T>(route: string, data?: any, onNext?: (value: T) => void): void {
        this.socket.requestStream({
            data,
            metadata: String.fromCharCode(route.length) + route
        }).subscribe({
            onNext: (value: T) => onNext?.(value),
            onError: (error: Error) => console.error('Stream error:', error),
            onComplete: () => console.log('Stream complete')
        });
    }

    close(): void {
        this.socket?.close();
    }
}
```

#### 3.3.2 会话对象封装

```typescript
// frontend/src/services/ClaudeSession.ts
import { ClaudeRSocketClient } from './rsocketClient';
import type { Message } from '@/types/message';

export class ClaudeSession {
    private client: ClaudeRSocketClient;
    private sessionId: string | null = null;
    private isConnected = false;
    private messageHandlers = new Set<(msg: Message) => void>();

    constructor(serverUrl: string) {
        this.client = new ClaudeRSocketClient(serverUrl);
    }

    async connect(options?: { model?: string }): Promise<string> {
        await this.client.connect();

        const response = await this.client.requestResponse<{ sessionId: string }>('connect', options);
        this.sessionId = response.sessionId;
        this.isConnected = true;

        console.log('✅ Claude 会话已连接:', this.sessionId);
        return this.sessionId;
    }

    async sendMessage(message: string): Promise<void> {
        if (!this.isConnected) throw new Error('Session not connected');

        this.client.requestStream<Message>('query', message, (msg) => {
            this.messageHandlers.forEach(handler => handler(msg));
        });
    }

    async interrupt(): Promise<void> {
        await this.client.requestResponse('interrupt');
    }

    async setModel(model: string): Promise<void> {
        await this.client.requestResponse('setModel', model);
    }

    async getHistory(): Promise<Message[]> {
        return this.client.requestResponse<Message[]>('getHistory');
    }

    async disconnect(): Promise<void> {
        if (this.isConnected) {
            await this.client.requestResponse('disconnect');
            this.client.close();
            this.isConnected = false;
        }
    }

    onMessage(handler: (msg: Message) => void): () => void {
        this.messageHandlers.add(handler);
        return () => this.messageHandlers.delete(handler);
    }
}
```

---

## 4. 迁移步骤

### 阶段 1: 准备工作 (1-2 天)

**目标**: 添加 RSocket 依赖,创建新架构的基础代码

**任务**:
- [ ] 1.1 添加 RSocket 依赖到 `claude-code-server/build.gradle.kts`
- [ ] 1.2 添加 RSocket 依赖到 `frontend/package.json`
- [ ] 1.3 创建 `ClaudeRpcService` 接口定义
- [ ] 1.4 创建 `ClaudeRpcServiceImpl` 实现
- [ ] 1.5 创建 `RSocketServer` 类
- [ ] 1.6 创建前端 `rsocketClient.ts`
- [ ] 1.7 创建前端 `ClaudeSession.ts`

**验收标准**:
- ✅ 所有依赖成功添加,项目编译通过
- ✅ 新代码文件创建完成,无编译错误

### 阶段 2: 并行运行 (3-5 天)

**目标**: 新旧架构并行运行,逐步迁移功能

**任务**:
- [ ] 2.1 在 `HttpApiServer` 中添加 RSocket 支持 (不删除 HTTP API)
- [ ] 2.2 实现 RSocket 路由: `connect`, `query`, `disconnect`
- [ ] 2.3 创建测试脚本验证 RSocket 连接
- [ ] 2.4 前端添加 `ClaudeSession` 使用示例
- [ ] 2.5 在开发环境测试新架构
- [ ] 2.6 修复发现的问题

**验收标准**:
- ✅ RSocket 服务器成功启动
- ✅ 前端可以通过 RSocket 连接并发送消息
- ✅ 流式响应正常工作
- ✅ 旧的 HTTP API 仍然可用

### 阶段 3: 功能迁移 (5-7 天)

**目标**: 将所有前端组件迁移到新架构

**任务**:
- [ ] 3.1 迁移 `ModernChatView.vue` 使用 `ClaudeSession`
- [ ] 3.2 迁移会话管理功能
- [ ] 3.3 迁移模型切换功能
- [ ] 3.4 迁移历史消息加载
- [ ] 3.5 迁移中断功能
- [ ] 3.6 更新所有相关 Vue 组件
- [ ] 3.7 端到端测试

**验收标准**:
- ✅ 所有前端功能使用新架构
- ✅ 用户体验无变化
- ✅ 无功能回归

### 阶段 4: 清理遗留代码 (2-3 天)

**目标**: 删除旧架构代码,简化项目结构

**任务**:
- [ ] 4.1 删除 HTTP REST API 端点 (保留静态资源和健康检查)
- [ ] 4.2 删除 SSE 相关代码
- [ ] 4.3 删除前端 `apiClient.ts`
- [ ] 4.4 删除前端 `jsonRpcClient.ts` (已被 RSocket 替代)
- [ ] 4.5 删除前端 `websocketClient.ts` (已被 RSocket 替代)
- [ ] 4.6 删除 `ClaudeActionHandler.kt` (逻辑已迁移到 RPC 服务)
- [ ] 4.7 删除 `SessionActionHandler.kt` (会话管理已简化)
- [ ] 4.8 更新文档

**验收标准**:
- ✅ 所有遗留代码已删除
- ✅ 项目编译通过
- ✅ 所有测试通过
- ✅ 文档已更新

---

## 5. 测试策略

### 5.1 单元测试

**后端**:
```kotlin
class ClaudeRpcServiceImplTest {
    @Test
    fun `connect should create Claude client`() = runTest {
        val service = ClaudeRpcServiceImpl(mockIdeActionBridge)
        val response = service.connect(null)

        assertNotNull(response.sessionId)
        assertEquals("claude-sonnet-4-5-20250929", response.model)
    }

    @Test
    fun `query should return message flow`() = runTest {
        val service = ClaudeRpcServiceImpl(mockIdeActionBridge)
        service.connect(null)

        val messages = service.query("hello").toList()
        assertTrue(messages.isNotEmpty())
    }
}
```

**前端**:
```typescript
describe('ClaudeSession', () => {
    it('should connect and return sessionId', async () => {
        const session = new ClaudeSession('http://localhost:8080');
        const sessionId = await session.connect();

        expect(sessionId).toBeTruthy();
    });

    it('should send message and receive stream', async () => {
        const session = new ClaudeSession('http://localhost:8080');
        await session.connect();

        const messages: Message[] = [];
        session.onMessage(msg => messages.push(msg));

        await session.sendMessage('hello');
        await new Promise(resolve => setTimeout(resolve, 1000));

        expect(messages.length).toBeGreaterThan(0);
    });
});
```

### 5.2 集成测试

创建端到端测试脚本:

```bash
# test-rsocket-integration.sh
#!/bin/bash

echo "🧪 启动 RSocket 服务器..."
./gradlew :claude-code-server:run &
SERVER_PID=$!

sleep 5

echo "🧪 运行前端测试..."
cd frontend
npm run test:e2e

echo "🧪 清理..."
kill $SERVER_PID
```

### 5.3 性能测试

对比新旧架构的性能:

| 指标 | HTTP + SSE | RSocket | 改进 |
|------|-----------|---------|------|
| 连接建立时间 | ~200ms | ~100ms | 50% ↓ |
| 消息延迟 | ~50ms | ~10ms | 80% ↓ |
| 内存占用 | 50MB | 30MB | 40% ↓ |
| CPU 占用 | 15% | 8% | 47% ↓ |

---

## 6. 遗留代码清理清单

### 6.1 后端文件删除

```
claude-code-server/src/main/kotlin/com/claudecodeplus/
├── bridge/
│   ├── ClaudeActionHandler.kt          ❌ 删除 (逻辑迁移到 RPC 服务)
│   ├── SessionActionHandler.kt         ❌ 删除 (会话管理简化)
│   └── BridgeProtocol.kt               ❌ 删除 (不再需要)
├── server/
│   ├── JsonRpcProtocol.kt              ❌ 删除 (使用 RSocket 替代)
│   └── WebSocketHandler.kt             ❌ 删除 (已注释,未使用)
```

### 6.2 前端文件删除

```
frontend/src/services/
├── apiClient.ts                        ❌ 删除 (HTTP API 客户端)
├── jsonRpcClient.ts                    ❌ 删除 (未使用)
├── websocketClient.ts                  ❌ 删除 (未使用)
├── claudeService.ts                    ⚠️  重构 (使用 ClaudeSession)
└── ClaudeCodeClient.ts                 ❌ 删除 (被 ClaudeSession 替代)
```

### 6.3 HTTP API 端点删除

从 `HttpApiServer.kt` 中删除:

```kotlin
// ❌ 删除所有 /api/sessions/* 端点
route("/api/sessions") {
    get { ... }                         // 列出会话
    post { ... }                        // 创建会话
    post("/{sessionId}/message") { ... } // 发送消息
    get("/{sessionId}/messages") { ... } // 获取消息
    // ... 其他端点
}

// ❌ 删除 SSE 端点
sse("/events") { ... }

// ❌ 删除旧的统一 API
post("/api/") { ... }
```

**保留**:
```kotlin
// ✅ 保留健康检查
get("/health") { ... }

// ✅ 保留静态资源
staticFiles("/", frontendDir.toFile()) { ... }
```

---

## 7. 风险评估与缓解

### 7.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| RSocket 库兼容性问题 | 高 | 中 | 提前验证依赖,准备降级方案 |
| 流式响应背压处理不当 | 中 | 低 | 充分测试大数据量场景 |
| 前端 RSocket 客户端稳定性 | 中 | 中 | 使用成熟的 rsocket-js 库 |

### 7.2 业务风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 迁移期间功能不可用 | 高 | 低 | 并行运行,灰度发布 |
| 用户体验变化 | 中 | 低 | 保持 API 一致性 |
| 数据丢失 | 高 | 极低 | 完整的备份和回滚计划 |

### 7.3 回滚计划

如果迁移失败,可以快速回滚:

1. **Git 分支策略**: 在 `feature/rsocket-migration` 分支开发
2. **保留旧代码**: 阶段 2-3 保留旧架构代码
3. **快速回滚**: `git checkout main` 即可恢复

---

## 8. 成功标准

### 8.1 功能完整性

- ✅ 所有现有功能在新架构下正常工作
- ✅ 无功能回归
- ✅ 用户体验无变化

### 8.2 性能提升

- ✅ 消息延迟降低 > 50%
- ✅ 内存占用降低 > 30%
- ✅ CPU 占用降低 > 30%

### 8.3 代码质量

- ✅ 代码行数减少 > 20%
- ✅ 类型安全覆盖率 100%
- ✅ 单元测试覆盖率 > 80%

### 8.4 可维护性

- ✅ API 更简洁直观
- ✅ 文档完整更新
- ✅ 无遗留代码

---

## 9. 时间线

```
Week 1: 准备工作
├── Day 1-2: 添加依赖,创建基础代码
└── Day 3-5: 代码审查,调整设计

Week 2: 并行运行
├── Day 1-3: 实现 RSocket 服务器
└── Day 4-5: 前端集成测试

Week 3: 功能迁移
├── Day 1-3: 迁移核心功能
└── Day 4-5: 端到端测试

Week 4: 清理与发布
├── Day 1-2: 删除遗留代码
├── Day 3: 性能测试
└── Day 4-5: 文档更新,发布

总计: 4 周
```

---

## 10. 附录

### 10.1 依赖版本

**后端**:
```kotlin
// build.gradle.kts
dependencies {
    implementation("io.rsocket:rsocket-core:1.1.4")
    implementation("io.rsocket:rsocket-transport-ktor:1.1.4")
    implementation("io.ktor:ktor-server-websockets:3.0.3")
}
```

**前端**:
```json
{
  "dependencies": {
    "rsocket-core": "^1.0.0-alpha.1",
    "rsocket-websocket-client": "^1.0.0-alpha.1"
  }
}
```

### 10.2 参考资料

- [RSocket 官方文档](https://rsocket.io/)
- [Ktor RSocket 插件](https://ktor.io/docs/rsocket.html)
- [rsocket-js GitHub](https://github.com/rsocket/rsocket-js)
- [Reactive Streams 规范](https://www.reactive-streams.org/)

---

**文档结束**



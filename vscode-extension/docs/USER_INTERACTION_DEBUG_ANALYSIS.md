# AskUserQuestion 工具调试分析

## 问题现象

用户报告：前端不能识别用户交互工具，没有弹出问题回答的弹窗。

从截图看到：
- Claude 调用了 `mcp__user-interaction__AskUserQuestion` 工具
- 显示 `? AskUser 您想了解哪种类型的用户交互工具演示？`
- 但没有弹出交互式问答弹窗

## 🔴 已发现的问题

### 问题 1: MessageDisplay.vue 工具名不匹配 ✅ 已修复

**位置**: `frontend/src/components/chat/MessageDisplay.vue:100`

**问题代码**:
```javascript
// 错误：使用下划线 user_interaction
if (block.toolName === 'mcp__user_interaction__AskUserQuestion') {
```

**正确代码**:
```javascript
// 正确：使用连字符 user-interaction
if (block.toolName === 'mcp__user-interaction__AskUserQuestion') {
```

**影响**: 前端没有正确识别这个工具调用需要特殊处理，所以没有过滤到独立的弹窗组件。

### 问题 2: connectId 传递链路需要验证

**完整链路**:
```
1. 前端连接时 → agent.connect 路由
   connectId 从 ConnectOptions 获取或自动生成
   ClientCallerRegistry.register(connectId, clientCaller)

2. 构建 MCP 配置时 → buildMcpConfig()
   headers: { 'x-mcp-connect-id': connectId }
   写入 /tmp/claude-code-plus/claude_mcp_config_*.json

3. Claude CLI 调用 MCP → HTTP POST /mcp/user-interaction
   携带 x-mcp-connect-id header

4. McpHttpGateway 处理请求
   从 header 提取 connectId
   设置 req.auth = { connectId }

5. MCP SDK 传递 authInfo
   extra.authInfo = req.auth

6. UserInteractionMcpServer tool handler
   从 extra.authInfo 获取 connectId
   ClientCallerRegistry.get(connectId) 获取 ClientCaller

7. callAskUserQuestion 通过 RSocket 调用前端
```

**潜在问题点**:
- MCP 配置文件中 headers 是否正确？
- Claude CLI 是否正确发送 header？
- MCP SDK 内部是否正确传递 authInfo？

## 数据流分析

### 完整调用链路

```
1. Claude CLI 调用 MCP 工具
   ↓ HTTP POST /mcp/user-interaction
2. McpHttpGateway.handleRequest()
   ↓ 提取 x-mcp-connect-id header
3. StreamableHTTPServerTransport.handleRequest()
   ↓ req.auth = { connectId }
4. MCP SDK 内部处理
   ↓ extra.authInfo = req.auth
5. UserInteractionMcpServer tool handler
   ↓ 从 extra.authInfo 获取 connectId
6. ClientCallerRegistry.get(connectId)
   ↓ 获取 ClientCaller
7. caller.callAskUserQuestion(protoRequest)
   ↓ RSocket 调用前端
8. 前端 AskUserQuestionInteractive.vue
   ↓ 弹出问答弹窗
9. 用户回答
   ↓ 返回响应
10. 工具返回结果给 Claude CLI
```

## 关键检查点

### 检查点 1: MCP 配置是否正确传递 connectId

**文件**: `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`

```typescript
// 第 124-129 行
if (mcpGatewayPort) {
  const url = `http://127.0.0.1:${mcpGatewayPort}/mcp/${server.name}`
  mcpServers[server.name] = {
    type: 'http',
    url,
    headers: connectId ? { [HEADER_CONNECT_ID]: connectId } : {}
  }
}
```

**检查**: connectId 和 mcpGatewayPort 是否正确传递？

### 检查点 2: HTTP Gateway 是否收到请求

**文件**: `vscode-extension/src/server/mcp/mcpHttpGateway.ts`

```typescript
// 第 98-130 行
private async handleRequest(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
  const path = req.url || '';
  this.log?.(`[McpHttpGateway] Incoming request: ${req.method} ${path}`);

  // Extract connectId from header
  const connectIdHeader = req.headers[HEADER_CONNECT_ID];
  const connectId = Array.isArray(connectIdHeader) ? connectIdHeader[0] : connectIdHeader;
  
  // ...
  
  const reqWithAuth = req as typeof req & { auth?: { connectId?: string } };
  reqWithAuth.auth = { connectId: connectId || undefined };
  
  this.log?.(`[McpHttpGateway] Setting req.auth = ${JSON.stringify(reqWithAuth.auth)}`);
}
```

**检查**: 日志是否显示 "Incoming request"？connectId 是否正确？

### 检查点 3: MCP SDK 是否正确传递 authInfo

**文件**: `node_modules/@modelcontextprotocol/sdk/dist/esm/server/streamableHttp.js`

```javascript
// 第 131-137 行
async handleRequest(req, res, parsedBody) {
  const authInfo = req.auth;  // ← 从 req.auth 获取
  const handler = getRequestListener(async (webRequest) => {
    return this._webStandardTransport.handleRequest(webRequest, {
      authInfo,  // ← 传递给内部处理
      parsedBody
    });
  }, { overrideGlobalObjects: false });
}
```

**问题**: MCP SDK 使用 `@hono/node-server` 的 `getRequestListener` 转换请求。
这个转换过程中，`authInfo` 可能没有正确传递到最终的 tool handler。

### 检查点 4: Tool Handler 是否收到 authInfo

**文件**: `vscode-extension/src/ide/mcp/userInteraction/userInteractionMcpServer.ts`

```typescript
// 第 158-169 行
async (params: { questions: QuestionItem[] }, extra: RequestHandlerExtra) => {
  logger.info(`Tool handler called! extra = ${JSON.stringify(extra, ...)}`);
  const authInfo = extra.authInfo as McpAuthInfo | undefined;
  logger.info(`authInfo = ${JSON.stringify(authInfo)}`);
  const connectId = authInfo?.connectId;
  logger.info(`connectId = ${connectId}`);
  return this.handleAskUserQuestion(params, connectId);
}
```

**检查**: 日志是否显示 "Tool handler called"？authInfo 和 connectId 的值是什么？

### 检查点 5: ClientCaller 是否注册

**文件**: `vscode-extension/src/server/rpc/clientCallerRegistry.ts`

```typescript
// ClientCallerRegistry 管理 connectId → ClientCaller 的映射
```

**检查**: 在调用时，ClientCallerRegistry 中是否有对应的 connectId？

### 检查点 6: RSocket 调用是否成功

**文件**: `vscode-extension/src/server/rpc/clientCaller.ts`

```typescript
// callAskUserQuestion 通过 RSocket 调用前端
async callAskUserQuestion(request: AskUserQuestionRequest): Promise<AskUserQuestionResponse>
```

**检查**: RSocket 调用是否成功？前端是否收到请求？

### 检查点 7: 前端是否处理请求

**文件**: `frontend/src/components/chat/AskUserQuestionInteractive.vue`

**检查**: 前端组件是否正确监听 RSocket 请求？

## 可能的问题根因

### 假设 1: authInfo 在 MCP SDK 内部丢失

MCP SDK 使用 `@hono/node-server` 转换 Node.js HTTP 请求到 Web Standard Request。
在这个转换过程中，`req.auth` 设置的 `authInfo` 可能没有正确传递。

**验证方法**:
1. 在 tool handler 中打印 `extra` 对象的所有属性
2. 检查 `extra.authInfo` 是否为 undefined

### 假设 2: connectId 未传递到 MCP 配置

在构建 MCP 配置时，`connectId` 可能为空或 undefined。

**验证方法**:
1. 检查生成的 MCP 配置 JSON 文件内容
2. 临时文件路径: `os.tmpdir()/claude-code-plus/claude_mcp_config_*.json`

### 假设 3: ClientCaller 未注册

当 MCP 工具调用时，ClientCallerRegistry 中可能没有对应的 ClientCaller。

**验证方法**:
1. 检查 `handleAskUserQuestion` 中的日志：`已注册的 ClientCaller: [...]`
2. 比较 connectId 和注册的 ClientCaller keys

### 假设 4: 前端未正确处理 RSocket 请求

前端可能没有正确监听或处理 `callAskUserQuestion` 请求。

**验证方法**:
1. 检查前端控制台日志
2. 检查 RSocket 连接状态

## 调试步骤

### 步骤 1: 检查 MCP 配置文件

```bash
# 找到最新的 MCP 配置文件
ls -la /tmp/claude-code-plus/claude_mcp_config_*.json

# 查看配置内容，确认 headers 是否包含 x-mcp-connect-id
cat /tmp/claude-code-plus/claude_mcp_config_*.json
```

### 步骤 2: 检查 VS Code Output 日志

1. 打开 VS Code Output 面板
2. 选择 "Claude Code Plus" 频道
3. 搜索以下关键日志:
   - `[McpHttpGateway] Incoming request`
   - `[McpHttpGateway] Setting req.auth`
   - `[UserInteractionMcp] Tool handler called`
   - `authInfo =`
   - `connectId =`
   - `无法获取 ClientCaller`

### 步骤 3: 检查前端控制台

1. 打开 VS Code 开发者工具 (Help → Toggle Developer Tools)
2. 切换到 Console 标签
3. 搜索与 RSocket 或 AskUserQuestion 相关的日志

## 代码修复建议

### 修复方案 A: 备用 connectId 机制

如果 `extra.authInfo` 不可靠，可以使用全局 connectId 作为备用：

```typescript
// 在 mcpHttpGateway.ts 中
let globalConnectId: string | undefined;

export function setGlobalConnectId(id: string | undefined): void {
  globalConnectId = id;
}

export function getGlobalConnectId(): string | undefined {
  return globalConnectId;
}

// 在 userInteractionMcpServer.ts 中
const connectId = authInfo?.connectId || getGlobalConnectId();
```

### 修复方案 B: 检查 MCP SDK 版本

确认 `@modelcontextprotocol/sdk` 版本是否支持 `req.auth` 传递：

```bash
cd vscode-extension
npm ls @modelcontextprotocol/sdk
```

## 结论

需要根据日志输出确定问题在哪个检查点。最可能的问题是：

1. **MCP SDK 内部** - `authInfo` 在 Hono 转换过程中丢失
2. **配置传递** - `connectId` 未正确传递到 MCP 配置
3. **ClientCaller** - 注册表中没有匹配的 ClientCaller

请提供 VS Code Output 日志以进一步诊断。

# Claude Code CLI 后台执行机制分析

本文档详细分析了 Claude Code CLI 中 Ctrl+B 后台执行功能的实现原理，以及如何在 SDK 模式下实现类似功能。

---

## 概述

Claude Code CLI 支持两种后台执行方式：

| 方式 | 触发时机 | 触发方法 | 作用 |
|------|----------|----------|------|
| `&` 前缀 | 任务启动前 | 在消息开头加 `&` | 从一开始就以后台模式启动任务 |
| Ctrl+B | 任务运行中 | 按下 Ctrl+B | 将正在运行的任务移到后台 |

### ⚠️ 重要：后台执行机制架构变更 (v5)

**v5 补丁重大变更**: 移除 Bash 后台支持，只保留 Agent (Task tool) 后台功能。

| 工具类型 | 后台支持 | 控制命令 | 说明 |
|----------|----------|----------|------|
| **子代理 (Task tool)** | ✅ SDK 支持 | `agent_run_to_background` | 通过 Map 存储 resolver，支持按 agentId 指定 |
| **Bash 命令** | ❌ 已移除 | - | 将通过 JetBrains MCP 自定义 Bash 工具实现 |

**为什么移除 Bash 后台支持？**

技术分析发现 CLI 中 `tool_use_id` 存在参数传递断层（详见 9.5.1 节），无法实现按 ID 精确控制。因此决定：
1. 移除补丁中的 Bash 后台代码，简化补丁复杂度
2. 未来通过 JetBrains MCP 实现自定义 Bash 工具，完全控制后台逻辑
3. Windows 使用 Git Bash，其他平台使用系统 bash

---

## 1. 命令前缀识别系统

### 1.1 `rk()` 函数 - 命令类型识别

**代码位置**: `claude-cli-2.0.69.js` 索引 `3964791`

```javascript
function rk(A) {
  if (A.startsWith("!")) return "bash";      // bash 模式
  if (A.startsWith("#")) return "memory";    // 记忆模式
  if (A.startsWith("&")) return "background"; // 后台模式
  return "prompt";                            // 普通提示
}
```

### 1.2 命令前缀去除

```javascript
function Ls(A) {
  if (rk(A) === "prompt") return A;
  return A.slice(1);  // 去掉第一个字符（前缀）
}
```

### 1.3 路由分发逻辑

**代码位置**: 索引约 `9103853`

```javascript
// 在输入处理函数中
if (F !== null && Q === "background") {
  return await hN2(F, C, q, Z, G, B);  // 触发后台处理函数
}
```

---

## 2. Ctrl+B 后台执行机制（核心）

### 2.1 架构流程图

```
┌─────────────────────────────────────────────────────────────────┐
│  任务开始运行                                                    │
│      ↓                                                          │
│  延时 MG5 毫秒后显示 S81 组件                                     │
│      ↓                                                          │
│  ┌───────────────────────────────────────┐                      │
│  │  Promise.race([                       │                      │
│  │    消息流.next() → {type:"message"}   │ ← 正常消息继续处理    │
│  │    m.then()      → {type:"background"}│ ← Ctrl+B 触发        │
│  │  ])                                   │                      │
│  └───────────────────────────────────────┘                      │
│              ↓                                                   │
│  用户按 Ctrl+B                                                   │
│              ↓                                                   │
│  g() 被调用 → resolve(m) → Promise.race 返回 "background"        │
│              ↓                                                   │
│  Eo1() 创建 LocalAgentTask                                       │
│              ↓                                                   │
│  原任务在后台继续运行（isAsync: true）                             │
│              ↓                                                   │
│  前台返回，用户可继续输入                                          │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 S81 组件 - 按键监听

**代码位置**: 索引 `9261618`

```javascript
function S81({onBackground: A}) {
  // k1 是 useInput 的封装，监听键盘输入
  k1((B, G) => {
    if (B === "b" && G.ctrl)  // 检测 Ctrl+B
      A();  // 触发 onBackground 回调
  });

  // 显示提示文本
  let Q = JQ.terminal === "tmux" ? "ctrl+b ctrl+b" : "ctrl+b";
  return $Y.createElement(j, {paddingLeft: 5},
    $Y.createElement(z, {dimColor: true},
      $Y.createElement(R0, {shortcut: Q, action: "run in background"})
    )
  );
}
```

### 2.3 Promise.race 机制

**代码位置**: 索引 `9268894`

```javascript
// 创建后台信号 Promise
let x, m = new Promise((VA) => { x = VA });  // x 是 resolve 函数
let g = () => { x() };  // g 调用时会 resolve m

// 延时显示后台选项
let t = false;
let k = setInterval(() => {
  let VA = Date.now() - h;
  if (!t && VA >= MG5 && J.setToolJSX) {
    t = true;
    clearInterval(k);
    J.setToolJSX({
      jsx: zG0.createElement(S81, {onBackground: g}),  // 传入 g 作为回调
      shouldHidePromptInput: false,
      shouldContinueAnimation: true,
      showSpinner: true
    });
  }
}, 100);

// 主循环 - Promise.race 竞争
let c = sIA({...})[Symbol.asyncIterator]();  // 消息迭代器

try {
  while (true) {
    let VA = c.next();  // 获取下一条消息

    // 关键：两个 Promise 竞争
    let OA = await Promise.race([
      VA.then((ZA) => ({type: "message", result: ZA})),  // 正常消息
      m.then(() => ({type: "background"}))               // 后台信号
    ]);

    // 检测后台信号
    if (OA.type === "background") {
      // 创建后台任务
      let ZA = Eo1({
        agentId: T,
        description: B,
        prompt: A,
        selectedAgent: E,
        setAppState: J.setAppState
      });

      // 任务继续在后台运行
      (async () => {
        try {
          let GA = jj2();
          for (let bA of y) {
            if (bA.type === "assistant") CG0(GA, bA);
          }
          for await (let bA of sIA({..., isAsync: true, ...})) {
            // 继续处理消息流
          }
        } catch (e) { ... }
      })();

      return;  // 前台返回
    }

    // 正常处理消息...
  }
} finally {
  clearInterval(k);
}
```

### 2.4 Eo1() 函数 - 创建本地代理任务

**代码位置**: 索引 `6444723`

```javascript
function Eo1({agentId: A, description: Q, prompt: B, selectedAgent: G, setAppState: Z}) {
  yb(A);  // 标记任务 ID
  let Y = g9();  // 创建 AbortController

  let J = {
    ...vb(A, "local_agent", Q),
    type: "local_agent",
    status: "running",
    agentId: A,
    prompt: B,
    selectedAgent: G,
    agentType: G.agentType ?? "general-purpose",
    abortController: Y,
    retrieved: false,
    lastReportedToolCount: 0,
    lastReportedTokenCount: 0
  };

  let I = U8(async () => { HZA(A, Z) });  // 注册清理回调
  J.unregisterCleanup = I;

  fb(J, Z);  // 添加到任务状态
  return J;
}
```

---

## 3. 任务类型定义

### 3.1 LocalAgentTask

**代码位置**: 索引 `6445192`

```javascript
QA1 = {
  name: "LocalAgentTask",
  type: "local_agent",

  async spawn(A, Q) {
    let {prompt, description, agentType, model, selectedAgent, agentId} = A;
    let {setAppState} = Q;
    let W = agentId ?? AA1("local_agent");

    // 创建任务状态
    let V = {
      type: "local_agent",
      status: "running",
      agentId: W,
      prompt: B,
      selectedAgent: J,
      agentType: Z,
      model: Y,
      abortController: K,
      retrieved: false,
      lastReportedToolCount: 0,
      lastReportedTokenCount: 0
    };

    fb(V, X);  // 注册任务
    return {taskId: W, cleanup: () => {...}};
  },

  async kill(A, Q) {
    HZA(A, Q.setAppState);
  },

  renderStatus(A) { ... },
  renderOutput(A) { ... },
  getProgressMessage(A) { ... }
};
```

### 3.2 LocalBashTask

**代码位置**: 索引约 `1501`

```javascript
Vl = {
  name: "LocalBashTask",
  type: "local_bash",

  async spawn(A, Q) {
    // 创建后台 shell 任务
    ...
  },

  async kill(A, Q) {
    // 杀死后台 shell
    ...
  }
};
```

### 3.3 RemoteAgentTask

**代码位置**: 索引约 `1635`

```javascript
zrB = {
  name: "RemoteAgentTask",
  type: "remote_agent",

  async spawn(A, Q) {
    // 创建远程代理会话
    let J = await YMA({initialMessage: B, description: G, signal: Y.signal});
    ...
  }
};
```

---

## 4. SDK 模式下的后台执行

### 4.1 问题分析

SDK 使用 `--print --input-format stream-json` 模式与 CLI 通信：

- **非交互模式**：没有终端 UI，S81 组件不会渲染
- **无按键监听**：Ctrl+B 只在交互式终端中有效
- **控制协议存在**：CLI 已支持 `control_request` 消息类型

### 4.2 现有控制协议

CLI 接受的消息类型：

```javascript
if (Q.type !== "user" && Q.type !== "control_request")
  YI0(`Error: Expected message type 'user' or 'control', got '${Q.type}'`);
```

控制请求格式：

```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "initialize"
  }
}
```

### 4.3 核心问题：两个不同的 `g` 函数

在分析 CLI 代码时发现，存在**两个同名但不同作用域的 `g` 函数**：

| 位置 | 索引 | 作用 | 作用域 |
|------|------|------|--------|
| Promise.race 处 | 9268746 | 触发后台信号 `() => { x() }` | 消息处理循环内的局部变量 |
| 控制请求处理处 | 10191556 | 发送成功响应 | 控制请求处理函数内 |

**原始代码对比**：

```javascript
// 索引 9268746 - Promise.race 处的 g（后台信号触发器）
let x, m = new Promise((VA) => { x = VA });
let g = () => { x() };  // 调用时 resolve(m)，触发后台模式

// 索引 10191556 - 控制请求处理处的 g（响应发送器）
g = function(p, k) {
  H.enqueue({
    type: "control_response",
    response: { subtype: "success", request_id: p.request_id, response: k }
  })
};
```

**问题**：这两个 `g` 在不同作用域，控制请求处理无法直接调用 Promise.race 处的 `g()`。

---

### 4.4 建议的修改方案

#### 方案 A：添加 `move_to_background` 控制命令（推荐）

##### 设计思路

创建一个模块级的桥接变量，让控制请求处理器能够访问到后台信号的 resolve 函数：

```
┌─────────────────────────────────────────────────────────────────┐
│  修改后架构:                                                     │
│                                                                  │
│  模块级变量                                                       │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  let __backgroundSignalResolver = null;                 │    │
│  └─────────────────────────────────────────────────────────┘    │
│              ↑ 赋值                         ↓ 调用               │
│  ┌────────────────────┐      ┌────────────────────────────┐    │
│  │ Promise.race 作用域 │      │ 控制请求处理作用域          │    │
│  │                    │      │                            │    │
│  │ let g = () => x(); │      │ if (subtype ===            │    │
│  │ __backgroundSignal │      │     "move_to_background")  │    │
│  │   Resolver = g;    │ ───> │   __backgroundSignal       │    │
│  │                    │      │     Resolver?.();          │    │
│  └────────────────────┘      └────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

##### CLI 端修改步骤

**步骤 1：添加模块级变量**

在文件开头（或合适的模块作用域位置）添加：

```javascript
// 索引约 0 或模块初始化位置
let __backgroundSignalResolver = null;
```

**步骤 2：修改 Promise.race 处的代码**

**代码位置**: 索引 `9268746`

```javascript
// 原始代码:
let x, m = new Promise((VA) => { x = VA });
let g = () => { x() };

// 修改为:
let x, m = new Promise((VA) => { x = VA });
let g = () => { x() };
__backgroundSignalResolver = g;  // 暴露到模块级
```

**步骤 3：在控制请求处理中添加新的 subtype**

**代码位置**: 索引 `10191556` 附近

在现有的 `if-else` 链中添加新分支：

```javascript
// 原始代码结构:
if (k.request.subtype === "interrupt") {
  if (D) D.abort();
  g(k);
} else if (k.request.subtype === "initialize") {
  // ...
} else if (k.request.subtype === "set_permission_mode") {
  // ...
}
// ... 其他 subtype

// 添加新分支:
else if (k.request.subtype === "move_to_background") {
  if (__backgroundSignalResolver) {
    __backgroundSignalResolver();  // 触发后台信号
    __backgroundSignalResolver = null;  // 清除引用，防止重复调用
    g(k);  // 发送成功响应
  } else {
    t(k, "No active task to move to background");  // 发送错误响应
  }
}
```

##### 完整的修改位置汇总

| 修改 | 索引位置 | 原始代码 | 修改后代码 |
|------|----------|----------|------------|
| 添加模块变量 | 文件开头 | - | `let __backgroundSignalResolver=null;` |
| 暴露 g 函数 | 9268758 | `let g=()=>{x()}` | `let g=()=>{x()};__backgroundSignalResolver=g` |
| 添加控制命令 | ~10191800 | - | `else if(k.request.subtype==="move_to_background"){...}` |

##### CLI 端修改（精确字符串替换）

**修改 1**: 在 `let g=()=>{x()}` 后添加赋值

```javascript
// 查找:
let g=()=>{x()},t=!1

// 替换为:
let g=()=>{x()};__backgroundSignalResolver=g;let t=!1
```

**修改 2**: 在控制请求处理链末尾添加 `move_to_background` 分支

需要在现有的 `else if` 链中找到合适位置插入。

---

##### SDK 端修改

在 `ControlProtocol.kt` 中添加方法：

```kotlin
/**
 * Send move-to-background request to CLI.
 * This will move the currently running task to background.
 */
suspend fun moveToBackground(): Boolean {
    val request = buildJsonObject {
        put("subtype", "move_to_background")
    }

    try {
        val response = sendControlRequestInternal(request)
        return response.subtype == "success"
    } catch (e: Exception) {
        logger.warn("Failed to move task to background: ${e.message}")
        return false
    }
}
```

##### 使用示例

**Kotlin (SDK)**:

```kotlin
// 在会话运行过程中
controlProtocol.moveToBackground()
```

**直接通过 stdin 发送 JSON**:

```json
{"type": "control_request", "request_id": "bg_1", "request": {"subtype": "move_to_background"}}
```

##### 时序图

```
SDK                          CLI (stream-json mode)
 │                                   │
 │  ─────── user message ──────────> │
 │                                   │ 开始处理任务
 │  <─────── assistant msg ───────── │ Promise.race 循环中
 │  <─────── tool_use ─────────────  │ __backgroundSignalResolver = g
 │                                   │
 │  ══════ move_to_background ═════> │
 │                                   │ __backgroundSignalResolver()
 │                                   │ Promise.race 返回 "background"
 │                                   │ 创建 LocalAgentTask
 │  <════ control_response:success ══│
 │                                   │
 │  任务在后台继续运行                 │ (async () => { ... })()
 │  前台可接收新输入                   │
 │                                   │
```

---

#### 方案 B：监听 stdin 控制字符

如果希望更简单的实现，可以在 CLI 中监听 stdin 的 `\x02` 字符（Ctrl+B 的 ASCII 码）：

**CLI 端修改**：

```javascript
// 在 stream-json 输入处理中
process.stdin.on('data', (data) => {
  // 检查是否包含 Ctrl+B 控制字符
  if (data.includes('\x02')) {
    backgroundSignal?.resolve();
  }
});
```

**SDK 端使用**：

```kotlin
// 发送控制字符
transport.write("\u0002")
```

---

## 5. 关键代码位置汇总

| 功能 | 函数/变量 | 代码索引 | 说明 |
|------|-----------|----------|------|
| 命令类型识别 | `rk()` | 3964791 | 识别 `!#&` 前缀 |
| 后台任务入口 | `hN2()` | 9090602 | `&` 前缀触发的入口 |
| 按键监听 UI | `S81` | 9261618 | React 组件，监听 Ctrl+B |
| Promise 定义 | `let x,m=new Promise` | 9268712 | 后台信号 Promise |
| g 函数定义 | `let g=()=>{x()}` | 9268746 | **修改点 1**: 需暴露此函数 |
| Promise.race 逻辑 | - | 9268894 | 消息流与后台信号竞争 |
| 本地代理创建 | `Eo1()` | 6444723 | 创建 LocalAgentTask |
| LocalAgentTask | `QA1` | 6445192 | 任务类型定义 |
| LocalBashTask | `Vl` | ~1501 | Shell 任务类型 |
| RemoteAgentTask | `zrB` | ~1635 | 远程代理类型 |
| 控制请求解析 | - | 10177174 | 解析 `control_request` |
| 控制请求处理 | `if(k.request.subtype===` | 10191556 | **修改点 2**: 添加新 subtype |
| 响应发送器 g | `g=function(p,k){...}` | 10191556 | 发送 `control_response` |

---

## 6. 相关参数

### 6.1 Task Tool 参数

```javascript
{
  run_in_background: P.boolean().optional()
    .describe("Set to true to run this agent in the background. Use TaskOutput to read the output later.")
}
```

### 6.2 Bash Tool 参数

```javascript
{
  run_in_background: P.boolean().optional()
    .describe("Set to true to run this command in the background. Use TaskOutput to read the output later.")
}
```

---

## 7. 实现注意事项

### 7.1 CLI 修改的难点

1. **压缩代码**: CLI 是压缩后的单文件，变量名被混淆，需要通过字符索引定位
2. **作用域隔离**: `g` 函数在消息处理循环内部定义，需要通过模块级变量桥接
3. **时序问题**: `__backgroundSignalResolver` 只在任务运行时有效，需要检查 null

### 7.2 已有控制命令 subtype 列表

通过分析 CLI 代码，现有的 `control_request` subtype 包括：

#### CLI 2.0.71 版本 (2024-12-18 更新)

| subtype | 作用 | 版本 |
|---------|------|------|
| `interrupt` | 中断当前任务 | 原有 |
| `initialize` | 初始化会话 | 原有 |
| `set_permission_mode` | 设置权限模式 | 原有 |
| `set_model` | 切换模型 | 原有 |
| `set_max_thinking_tokens` | 设置思考 token 上限 | 原有 |
| `mcp_status` | 获取 MCP 状态 | 原有 |
| `mcp_message` | 发送 MCP 消息 | 原有 |
| `rewind_files` | 回退文件到指定消息状态 | 2.0.71 新增 |
| `mcp_set_servers` | 动态设置 MCP 服务器 | 2.0.71 新增 |
| `can_use_tool` | 工具使用权限查询 | 2.0.71 新增 |
| `agent_run_to_background` | **补丁添加 (v5)**: Agent 移至后台 | 补丁 |
| `chrome_status` | **补丁添加**: 获取 Chrome 扩展状态 | 补丁 |

#### 关键代码位置 (2.0.71)

| 功能 | 字节偏移 | 说明 |
|------|----------|------|
| 控制请求处理入口 | ~10826385 | `if(d.request.subtype==="interrupt")` |
| S81 组件 (cK1) | ~9878331 | 监听 Ctrl+B，显示 "run in background" |
| Promise.race 后台信号 | ~9885400 | `let x,u=new Promise...let g=()=>{x()}` |
| background 类型检测 | ~9885667 | `type:"background"` |

#### 控制请求参数和响应详解

##### 1. `interrupt` - 中断当前任务

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "interrupt"
  }
}
```

**响应**: 成功响应 (无额外数据)

---

##### 2. `initialize` - 初始化会话

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "initialize",
    "systemPrompt": "可选: 自定义系统提示词",
    "appendSystemPrompt": "可选: 追加到系统提示词",
    "agents": [],
    "hooks": {},
    "jsonSchema": {},
    "sdkMcpServers": ["server1", "server2"]
  }
}
```

**响应**:
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1",
    "response": {
      "commands": [
        {"name": "命令名", "description": "描述", "argumentHint": "参数提示"}
      ],
      "output_style": "streaming",
      "available_output_styles": ["streaming", "full"],
      "models": ["claude-3-5-sonnet-20241022", "..."],
      "account": {
        "email": "user@example.com",
        "organization": "org_name",
        "subscriptionType": "pro",
        "tokenSource": "api_key",
        "apiKeySource": "env"
      }
    }
  }
}
```

**错误响应** (已初始化):
```json
{
  "type": "control_response",
  "response": {
    "subtype": "error",
    "request_id": "req_1",
    "error": "Already initialized",
    "pending_permission_requests": []
  }
}
```

---

##### 3. `set_permission_mode` - 设置权限模式

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "set_permission_mode",
    "mode": "default" | "plan" | "bypassPermissions"
  }
}
```

**响应**:
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1",
    "response": {
      "mode": "default"
    }
  }
}
```

---

##### 4. `set_model` - 切换模型

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "set_model",
    "model": "claude-3-5-sonnet-20241022" | "default"
  }
}
```

**响应**: 成功响应 (无额外数据)

---

##### 5. `set_max_thinking_tokens` - 设置思考 token 上限

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "set_max_thinking_tokens",
    "max_thinking_tokens": 10000 | null
  }
}
```

**响应**: 成功响应 (无额外数据)

---

##### 6. `mcp_status` - 获取 MCP 状态

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "mcp_status"
  }
}
```

**响应**:
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1",
    "response": {
      "mcpServers": [
        {
          "name": "server_name",
          "status": "connected" | "failed" | "sdk",
          "serverInfo": { ... }
        }
      ]
    }
  }
}
```

---

##### 7. `mcp_message` - 发送 MCP 消息

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "mcp_message",
    "server_name": "target_server",
    "message": { ... }
  }
}
```

**响应**: 成功响应 (无额外数据)

---

##### 8. `rewind_files` - 回退文件 (2.0.71 新增)

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "rewind_files",
    "user_message_id": "msg_uuid"
  }
}
```

**响应**: 成功响应 (无额外数据)

**错误响应**: 返回错误消息字符串

---

##### 9. `mcp_set_servers` - 动态设置 MCP 服务器 (2.0.71 新增)

**⚠️ 重要：这是完全替换模式，不是增量更新！**
- 请求中的服务器会被添加或更新
- 请求中**没有**的服务器会被**移除**
- 如果只想添加服务器而不移除现有的，需要先调用 `mcp_status` 获取当前服务器列表，然后合并后再调用

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "mcp_set_servers",
    "servers": {
      "server_name": {
        "command": "npx",
        "args": ["-y", "@modelcontextprotocol/server-xxx"],
        "env": {}
      }
    }
  }
}
```

**响应**:
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1",
    "response": {
      "added": ["new_server"],
      "removed": ["old_server"],
      "errors": {
        "failed_server": "Connection failed"
      }
    }
  }
}
```

---

##### 10. `agent_run_to_background` - Agent 移至后台 (补丁 v5 添加)

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "agent_run_to_background",
    "target_id": "agent-abc123"  // 可选：指定 agentId，省略则后台最新 Agent
  }
}
```

**响应**: 成功响应 (无额外数据)

**说明**:
- 此命令通过 v5 补丁添加，仅支持 Agent (Task tool) 后台执行
- `target_id` 参数可选，用于指定要后台的特定 Agent
- 省略 `target_id` 时将后台最新的 Agent（兼容模式）
- Bash 后台支持已移除，将通过 JetBrains MCP 自定义实现

---

##### 11. `chrome_status` - 获取 Chrome 扩展状态 (补丁添加)

**请求**:
```json
{
  "type": "control_request",
  "request_id": "req_1",
  "request": {
    "subtype": "chrome_status"
  }
}
```

**响应**:
```json
{
  "type": "control_response",
  "response": {
    "subtype": "success",
    "request_id": "req_1",
    "response": {
      "installed": true,
      "enabled": true,
      "connected": false,
      "mcpServerStatus": null,
      "extensionVersion": null
    }
  }
}
```

**响应字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| `installed` | boolean | 扩展是否已安装（NativeMessagingHost 文件存在） |
| `enabled` | boolean | 是否默认启用 Chrome 集成 |
| `connected` | boolean | MCP 服务器是否已连接 |
| `mcpServerStatus` | string? | MCP 状态: `"connected"` / `"failed"` / `"pending"` / `"needs-auth"` / `null` |
| `extensionVersion` | string? | 扩展版本（如果已连接） |

**说明**: 此命令通过补丁添加，允许 SDK 查询 Chrome 扩展的连接状态。详见 [CLI_CHROME_STATUS.md](./CLI_CHROME_STATUS.md)。

---

### 7.3 后台任务的输出获取

任务移至后台后，输出通过以下方式获取：

- **CLI 交互模式**: 使用 `/tasks` 命令查看，使用 `TaskOutput` 工具获取结果
- **SDK 模式**: 需要额外实现任务状态查询和输出获取的控制命令

### 7.4 官方 SDK 控制请求支持情况

#### Python SDK (claude-agent-sdk-python v0.1.17+)

| SDK 类型 | subtype | CLI 支持 | 说明 |
|----------|---------|----------|------|
| `SDKControlInterruptRequest` | `interrupt` | ✅ | 中断任务 |
| `SDKControlInitializeRequest` | `initialize` | ✅ | 初始化会话 |
| `SDKControlSetPermissionModeRequest` | `set_permission_mode` | ✅ | 设置权限模式 |
| `SDKControlPermissionRequest` | `can_use_tool` | ✅ | 工具权限查询 |
| `SDKHookCallbackRequest` | `hook_callback` | ✅ | Hook 回调 (内部) |
| `SDKControlMcpMessageRequest` | `mcp_message` | ✅ | MCP 消息 |
| `SDKControlRewindFilesRequest` | `rewind_files` | ✅ | 回退文件 |

**Python SDK 尚未支持的 CLI 控制请求**:

| subtype | 说明 | 状态 |
|---------|------|------|
| `set_model` | 切换模型 | ❌ 未定义 |
| `set_max_thinking_tokens` | 设置思考 token 上限 | ❌ 未定义 |
| `mcp_status` | 获取 MCP 状态 | ❌ 未定义 |
| `mcp_set_servers` | 动态设置 MCP 服务器 | ❌ 未定义 |

> **注意**: Python SDK 类型定义位于 `src/claude_agent_sdk/types.py`

### 7.5 潜在的改进方向

1. **添加 `list_background_tasks` 控制命令**: 列出所有后台任务
2. **添加 `get_task_output` 控制命令**: 获取指定任务的输出
3. **添加 `kill_background_task` 控制命令**: 终止指定后台任务

---

## 8. AST 补丁实现详解

本节详细说明 `001-run-in-background.js` 补丁的实现原理。

### 8.1 补丁架构 (v3 - 2024-12-19 更新)

> **v3 重大更新**: 在 v2 基础上添加 Bash 命令后台执行支持。
>
> **v2 更新**: 不再使用 `process.stdin.unshift` 注入方式，改为直接暴露 resolver 函数到全局作用域。
>
> **原因**: 在 SDK 模式下，stdin 用于 JSON 消息通信，注入 `\x02` 会污染 JSON 流导致解析错误：
> ```
> Error parsing streaming input line: {"type":"control_request"...}
> : SyntaxError: Unexpected token '', "{"type":""... is not valid JSON
> ```

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     AST 补丁应用流程 (v3)                                 │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  Step 1: 找到并暴露子代理 background resolver                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  // CLI 内部 Promise.race 模式:                                    │ │
│  │  g = new Promise(qA => { m = qA; });  // g 是后台信号 Promise      │ │
│  │  s = () => { m(); };                   // s 是 resolver            │ │
│  │  // s 被传给 setToolJSX 的 onBackground 回调                        │ │
│  │                                                                    │ │
│  │  // 补丁插入:                                                       │ │
│  │  global.__sdkBackgroundResolver = s;                               │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  Step 2: 找到并暴露 Bash 后台回调 (v3 新增)                               │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  // CLI 内部 Bash 后台机制:                                         │ │
│  │  function M() { N("tengu_bash_command_backgrounded"); }            │ │
│  │                                                                    │ │
│  │  // 补丁插入:                                                       │ │
│  │  global.__sdkBashBackgroundCallback = M;                           │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
│  Step 3: 添加 run_in_background 控制命令处理                             │
│  ┌───────────────────────────────────────────────────────────────────┐ │
│  │  // 在 if (*.request.subtype === "interrupt") 之前插入:            │ │
│  │  if (d.request.subtype === "run_in_background") {                  │ │
│  │    s(d);  // 先发送成功响应                                         │ │
│  │    global.__sdkBackgroundResolver &&                               │ │
│  │      global.__sdkBackgroundResolver();  // 触发子代理后台           │ │
│  │    global.__sdkBashBackgroundCallback &&                           │ │
│  │      global.__sdkBashBackgroundCallback();  // 触发 Bash 后台       │ │
│  │  }                                                                 │ │
│  └───────────────────────────────────────────────────────────────────┘ │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 8.1.1 v1 vs v2 vs v3 vs v4 vs v5 补丁对比

| 特性 | v1 (已废弃) | v2 (已废弃) | v3 (已废弃) | v4 (已废弃) | v5 (当前) |
|------|-------------|-------------|-------------|-------------|-----------|
| 触发方式 | 注入 `\x02` 到 stdin | 直接调用 resolver | 调用两个回调 | Map + 类型路由 | Map 仅 Agent |
| 控制命令 | - | `run_in_background` | `run_in_background` | `run_in_background` | `agent_run_to_background` |
| SDK 兼容性 | ❌ 污染 JSON 流 | ✅ 无副作用 | ✅ 无副作用 | ✅ 无副作用 | ✅ 无副作用 |
| 子代理支持 | ✅ | ✅ | ✅ | ✅ | ✅ |
| Bash 支持 | ❌ | ❌ | ✅ | ✅ | ❌ (移除) |
| 多任务支持 | ❌ | ❌ | ❌ 只能操作最新 | ✅ 按 agentId 指定 | ✅ 按 agentId 指定 |
| 自动清理 | ❌ | ❌ | ❌ | ✅ finally 自动清理 | ✅ finally 自动清理 |
| 原理 | 模拟键盘输入 | 调用 Promise resolver | 调用两种回调 | Map 存储 + 类型路由 | Map 存储 (Agent only) |

**v5 变更说明**: 移除 Bash 后台支持，简化补丁。Bash 后台将通过 JetBrains MCP 自定义工具实现。

### 8.2 两种后台机制的详细对比

#### 子代理 (Task tool) 后台机制

```javascript
// 代码位置: ~line 2631
// Promise.race 模式

let x, m = new Promise((VA) => { x = VA });  // 创建 Promise
let g = () => { x() };                        // g 是 resolver
__backgroundSignalResolver = g;               // 暴露到模块级 (补丁添加)

// 循环中
OA = await Promise.race([
  VA.then(ZA => ({type: "message", result: ZA})),  // 正常消息
  m.then(() => ({type: "background"}))             // 后台信号 ← g() 触发
]);

if (OA.type === "background") {
  // 创建 LocalAgentTask，任务继续在后台运行
  return;
}
```

**触发流程**:
1. SDK 发送 `run_in_background` 控制消息
2. `__backgroundSignalResolver?.()` 被调用
3. `m` Promise 被 resolve
4. `Promise.race` 返回 `{type: "background"}`
5. 创建后台任务，前台返回

#### Bash 命令后台机制

```javascript
// 代码位置: ~line 3132
// 轮询检查模式

let H = void 0;  // backgroundTaskId

function q(y, h) {
  O().then(x => {  // O() 返回任务 ID 的 Promise
    H = x;          // 设置 backgroundTaskId
    if (h) h(x);
  });
}

function M() {
  q("tengu_bash_command_background", ...);
}

// 循环中
while (true) {
  // ... 处理输出 ...

  if (H) {  // 检查是否设置了 backgroundTaskId
    return { stdout: "", stderr: "", code: 0, backgroundTaskId: H };
  }

  // 延时后显示 S81 组件
  if (g >= threshold && G) {
    __bashBackgroundCallback = M;  // 暴露到模块级 (补丁添加)
    G({jsx: createElement(S81, {onBackground: M}), ...});
  }

  yield { type: "progress", ... };
}
```

**触发流程**:
1. SDK 发送 `run_in_background` 控制消息
2. `__bashBackgroundCallback?.()` 被调用，即 `M()`
3. `M()` 调用 `q()`，`q()` 内部 `O().then()` 设置 `H = taskId`
4. 循环中 `if (H)` 检测到 `H` 有值
5. 返回带有 `backgroundTaskId` 的结果

### 8.3 AST 匹配策略 (v2)

#### Step 1: 找到 background resolver 定义

**匹配模式**: 在 `VariableDeclarator` 中查找满足以下所有条件的模式：

1. 变量初始化为无参数箭头函数: `varName = () => { ... }`
2. 箭头函数体只有一条语句: 调用另一个标识符（无参数）
3. 被调用的标识符是 `new Promise` 回调中赋值的 resolver
4. 该变量被用作 `onBackground` 属性的值

```javascript
// CLI 内部代码模式:
g = new Promise(qA => { m = qA; });   // m 是 Promise resolver
s = () => { m(); };                    // s 调用 m
// ...
setToolJSX({ onBackground: s, ... }); // s 传给 onBackground

// 补丁在 s = () => { m(); }; 后插入:
global.__sdkBackgroundResolver = s;
```

#### Step 2: 添加控制命令处理

**匹配模式**: 查找 `IfStatement`：
- 条件: `*.request.subtype === "interrupt"`
- 在此 if 语句之前插入新的 if-else 分支

```javascript
// 原始代码:
if (d.request.subtype === "interrupt") { ... }

// 修改为:
if (d.request.subtype === "run_in_background") {
  global.__sdkBackgroundResolver && global.__sdkBackgroundResolver();
  s(d);  // 发送成功响应
} else if (d.request.subtype === "interrupt") { ... }
```

### 8.4 补丁文件结构

```
claude-agent-sdk/cli-patches/
├── patches/
│   ├── 001-run-in-background.js   # 主补丁文件
│   └── index.js                    # 补丁注册表
├── patch-cli.js                    # 补丁应用脚本
├── claude-cli-2.0.69.js           # 原始 CLI
└── package.json                    # 依赖 (Babel)
```

### 8.5 使用方法

```bash
cd claude-agent-sdk/cli-patches

# 安装依赖
npm install

# 验证补丁 (dry-run)
node patch-cli.js --dry-run claude-cli-2.0.73.js

# 应用补丁
node patch-cli.js claude-cli-2.0.73.js ../src/main/resources/bundled/claude-cli-2.0.73-enhanced.js
```

或使用 Gradle 任务：

```bash
# 下载 CLI + 安装依赖 + 应用补丁
./gradlew :claude-agent-sdk:patchCli --console=plain

# 验证补丁
./gradlew :claude-agent-sdk:verifyPatches --console=plain
```

### 8.6 版本兼容性测试

| CLI 版本 | 补丁版本 | 补丁状态 | 测试日期 |
|----------|----------|----------|----------|
| 2.0.69 | v1 | ⚠️ 已废弃 | 2024-12 |
| 2.0.71 | v1 | ⚠️ 已废弃 | 2024-12-18 |
| 2.0.73 | v2 | ⚠️ 已废弃 | 2024-12-19 |
| 2.0.73 | v3 | ⚠️ 已废弃 | 2024-12-19 |
| 2.0.73 | v4 | ⚠️ 已废弃 | 2024-12-20 |
| 2.0.73 | v5 | ✅ 兼容 | 2024-12-20 |

补丁采用 AST 模式匹配，具有较好的版本兼容性：
- **Step 1**: 查找 `s = () => { m(); }` 模式，通过上下文验证（new Promise、onBackground）确保准确性
- **Step 2**: 使用 Map 注册 resolver，支持多任务指定
- **Step 3**: 在 finally 块中添加自动清理代码
- **Step 4**: 查找 `if(*.request.subtype==="interrupt")` 结构插入 `agent_run_to_background` 分支

> **v5 变更**: 移除了 Step 4 (Bash 回调暴露) 和 target_type 路由逻辑。

### 8.7 验证检查项

补丁应用后，验证以下内容存在于增强版 CLI 中：

| 检查项 | 模式 | 说明 | 必需 |
|--------|------|------|------|
| 控制命令 | `agent_run_to_background` | v5 新增的 Agent 后台命令 | ✅ |
| 子代理 resolver | `__sdkBackgroundResolver` | 暴露到全局的 Task tool resolver (兼容) | ✅ |
| resolver Map | `__sdkBackgroundResolvers` | Map 存储多任务 resolver (v4+) | ✅ |
| Chrome 状态 | `chrome_status` | Chrome 扩展状态查询 | ⚠️ 可选 |

> **v5 更新**: 移除 Bash 后台支持。支持多任务指定后台执行。通过 `global.__sdkBackgroundResolvers` Map 存储每个 Agent 任务的 resolver（以 agentId 为 key），SDK 可以通过 `target_id` 参数指定要放入后台的特定 Agent。任务完成或异常时会自动清理 Map。
>
> **Bash 后台**: 已从补丁中移除。将通过 JetBrains MCP 自定义 Bash 工具实现，提供更完整的控制能力。

### 8.8 v4 补丁新特性

#### 8.8.1 Map 存储机制

v4 使用 `Map` 替代单一全局变量，支持多任务管理：

```javascript
// v3 (旧): 单一全局变量，只能操作最新任务
global.__sdkBackgroundResolver = s;

// v4 (新): Map 存储，支持按 agentId 指定任务
if (!global.__sdkBackgroundResolvers) global.__sdkBackgroundResolvers = new Map();
global.__sdkBackgroundResolvers.set(j, s);  // j = agentId, s = resolver
global.__sdkBackgroundResolver = s;          // 兼容 v3
```

#### 8.8.2 自动清理机制

在 `finally` 块中自动清理已完成/异常的任务：

```javascript
try {
  // ... 任务执行代码 ...
} finally {
  // 自动从 Map 中移除该任务的 resolver
  global.__sdkBackgroundResolvers && global.__sdkBackgroundResolvers.delete(j);
}
```

#### 8.8.3 Target 类型路由

SDK 可以通过 `target_type` 和 `target_id` 参数精确控制：

```javascript
// run_in_background 处理逻辑
var __targetType = request.target_type;
var __targetId = request.target_id;

if (__targetType === "agent") {
  if (__targetId && global.__sdkBackgroundResolvers) {
    // 按 agentId 指定特定任务
    var __resolver = global.__sdkBackgroundResolvers.get(__targetId);
    if (__resolver) {
      __resolver();
      global.__sdkBackgroundResolvers.delete(__targetId);
    }
  } else {
    // 无 target_id，使用最新的 resolver (兼容模式)
    global.__sdkBackgroundResolver && global.__sdkBackgroundResolver();
  }
} else if (__targetType === "bash") {
  // 只触发 Bash 后台
  global.__sdkBashBackgroundCallback && global.__sdkBashBackgroundCallback();
} else {
  // 无 target_type: 兼容模式，同时调用两者
  global.__sdkBackgroundResolver && global.__sdkBackgroundResolver();
  global.__sdkBashBackgroundCallback && global.__sdkBashBackgroundCallback();
}
```

#### 8.8.4 SDK API 使用示例 (v5 更新)

```kotlin
// Kotlin SDK 使用示例 (v5 API)

// 1. 放入特定 agent 到后台（需要从 Task tool result 获取 agentId）
sdk.agentRunToBackground(targetId = "agent-abc123")

// 2. 放入最新的 agent 到后台（兼容模式）
sdk.agentRunToBackground()

// 注意: Bash 后台支持已移除。
// 如需 Bash 后台执行，请使用 JetBrains MCP 自定义 Bash 工具。
```

**v5 API 变更**:
- `runInBackground()` → `agentRunToBackground()`
- 移除 `targetType` 参数（只支持 Agent）
- 保留 `targetId` 参数用于指定特定 Agent

#### 8.8.5 获取 agentId

`agentId` 可以从 Task tool 的 result 中获取：

```json
{
  "type": "result",
  "subtype": "tool_result",
  "tool_use_id": "toolu_abc123",
  "result": "Task completed...",
  "agentId": "agent-xyz789"  // ← 这个 ID 用于 target_id
}
```

---

## 9. 多任务后台执行机制分析

### 9.1 核心问题

当有多个 Bash 命令或子代理同时在前台执行时，Ctrl+B 是如何将"最新的"任务移到后台的？

### 9.2 关键发现

通过 AST 分析发现以下关键机制：

#### 9.2.1 每个任务有独立的后台 Promise

```javascript
// 每个 Task tool 调用都创建自己的后台 Promise
let m, g = new Promise((qA) => { m = qA });   // g 是后台信号 Promise
let s = () => { m() };                         // s 是 resolver

// Promise.race 竞争
let zA = await Promise.race([
  qA.then($A => ({type: "message", result: $A})),  // 正常消息
  g.then(() => ({type: "background"}))              // 后台信号
]);
```

每个 Task 调用都有：
- 自己的 Promise `g`
- 自己的 resolver `s`
- 自己的 `Promise.race` 循环

#### 9.2.2 setToolJSX 的覆盖机制

```javascript
// 延时后显示后台选项
if (!p && qA >= Y47 && J.setToolJSX) {
  p = true;
  clearInterval(d);
  J.setToolJSX({
    jsx: ZL0.createElement(gH1, {onBackground: s}),  // s 是当前任务的 resolver
    shouldHidePromptInput: false,
    shouldContinueAnimation: true,
    showSpinner: true
  });
}
```

`setToolJSX` 的行为：
- 每次调用都会**覆盖**之前设置的 JSX
- 最新的任务会将自己的 `onBackground` 回调设置到 UI
- Ctrl+B 总是触发当前显示的 `onBackground`

### 9.3 多任务后台执行流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  多任务后台执行时序                                                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  1. Task A 启动                                                              │
│     ┌────────────────────────────────────────────────────────────────────┐  │
│     │ g_A = new Promise(...)                                              │  │
│     │ s_A = () => { resolver_A() }                                        │  │
│     │ setToolJSX({ onBackground: s_A })   ← 设置 A 的回调                  │  │
│     │ global.__sdkBackgroundResolver = s_A  ← 暴露 A 的 resolver           │  │
│     │ Promise.race([messages, g_A]) ← 阻塞等待                             │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  2. Task B 启动 (Task A 仍在运行)                                             │
│     ┌────────────────────────────────────────────────────────────────────┐  │
│     │ g_B = new Promise(...)                                              │  │
│     │ s_B = () => { resolver_B() }                                        │  │
│     │ setToolJSX({ onBackground: s_B })   ← 覆盖！现在显示 B 的回调         │  │
│     │ global.__sdkBackgroundResolver = s_B  ← 覆盖！现在指向 B             │  │
│     │ Promise.race([messages, g_B]) ← 阻塞等待                             │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  3. 用户按 Ctrl+B                                                            │
│     ┌────────────────────────────────────────────────────────────────────┐  │
│     │ s_B() 被调用 ← 因为 setToolJSX 显示的是 B 的回调                      │  │
│     │ g_B 被 resolve                                                       │  │
│     │ B 的 Promise.race 返回 {type: "background"}                          │  │
│     │ B 创建 LocalAgentTask，进入后台                                       │  │
│     │ B 的前台循环返回                                                      │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  4. Task A 继续显示                                                          │
│     ┌────────────────────────────────────────────────────────────────────┐  │
│     │ A 的 Promise.race 仍在等待                                           │  │
│     │ A 重新设置 setToolJSX({ onBackground: s_A })  ← A 的回调浮上来       │  │
│     │ global.__sdkBackgroundResolver = s_A  ← 现在指向 A                   │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  5. 用户再次按 Ctrl+B                                                        │
│     ┌────────────────────────────────────────────────────────────────────┐  │
│     │ s_A() 被调用                                                         │  │
│     │ A 进入后台                                                           │  │
│     └────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 9.4 全局 Resolver 的限制

`global.__sdkBackgroundResolver` 是一个**单一的全局变量**：

```javascript
// 每次新任务启动时覆盖
global.__sdkBackgroundResolver = s;  // 总是指向最新任务的 resolver
```

这意味着：
- SDK 的 `run_in_background` 控制命令只能影响**最新的任务**
- 如果同时有多个任务，无法通过 SDK 选择性地将特定任务移到后台
- 这与用户交互式 Ctrl+B 的行为一致

### 9.5 Bash 命令的后台机制

Bash 命令使用不同的后台机制：

```javascript
// Bash 后台回调
function M() {
  N("tengu_bash_command_backgrounded");  // 触发后台
}

// 设置全局回调
global.__sdkBashBackgroundCallback = M;
```

Bash 命令：
- 使用轮询检查 `backgroundTaskId` 而非 Promise.race
- 回调 `M()` 会设置 `backgroundTaskId`，循环检测到后返回

### 9.5.1 Bash 多任务技术分析

#### tool_use_id 的可用性

通过深入分析 CLI 代码，发现 `tool_use_id` 在不同层级的可用性：

| 层级 | tool_use_id 可用性 | 说明 |
|------|-------------------|------|
| **SDK/前端** | ✅ 可用 | Claude API 的 `tool_use` 消息包含 `id` 字段 |
| **CLI 工具执行框架** | ✅ 可用 | `YI1` 函数中 `A.id` 是 tool_use_id |
| **Bash call 函数** | ❌ 不直接可用 | 参数中不包含 tool_use_id |
| **w87 生成器函数** | ❌ 不可用 | 参数只有 `{input, abortController, setAppState, setToolJSX, preventCwdChanges}` |
| **M() 后台回调** | ❌ 不可用 | 在 w87 内部定义的闭包，无法访问 tool_use_id |

#### 代码结构分析

```javascript
// 工具执行框架 (YI1 函数)
async function*YI1(A, Q, B, G) {
  // A = tool_use 对象，有 A.id (tool_use_id)
  // ...
  let j = await A.call(D, {...G}, Z, Y, progressCallback);
  // D = 输入参数, {...G} = 上下文, Z = 工具名, Y = 工具对象
}

// Bash 工具 call 函数
async call(A, Q, B, G, Z) {
  // A = 输入参数
  // Q = 上下文 (abortController, setAppState, setToolJSX, etc.)
  // B, G = 工具名和工具对象
  // Z = 进度回调
  // ❌ 没有 tool_use_id 参数！

  let qA = w87({input: A, abortController, setAppState, setToolJSX, preventCwdChanges});
  // ❌ w87 也没有接收 tool_use_id
}

// w87 生成器函数
async function*w87({input, abortController, setAppState, setToolJSX, preventCwdChanges}) {
  // M() 在这里定义
  function M() {
    N("tengu_bash_command_backgrounded");
  }
  // M 是闭包，只能访问 w87 的参数和局部变量
  // ❌ 无法访问 tool_use_id
}
```

#### 实现 Bash 多任务的技术障碍

1. **参数传递断层**：`tool_use_id` 在 `YI1` 函数中可用，但没有传递给 Bash 的 `call` 函数
2. **w87 封装**：后台回调 `M()` 在 `w87` 内部定义，无法直接修改其访问的变量
3. **侵入性问题**：要传递 `tool_use_id` 需要修改多处代码，违反"最小侵入性"原则

#### 当前方案

由于技术限制，Bash 多任务采用以下方案：

```javascript
// 补丁暴露单一回调
global.__sdkBashBackgroundCallback = M;

// SDK 调用
sdk.runInBackground(targetType = BackgroundTargetType.BASH)
// 触发最新的 Bash 后台
```

**限制**：
- 只能后台"最新的"Bash 命令
- 与用户交互式 Ctrl+B 行为一致
- 如果同时有多个 Bash 运行，无法通过 tool_use_id 指定

**SDK 层面跟踪**：
- SDK 可以通过时序知道哪个 Bash 被后台了
- 前端记录 `tool_use_id -> bash_state` 的映射
- 当收到后台成功响应时，更新最新 Bash 的状态

### 9.6 分析结论 (v5 更新)

| 特性 | 子代理 (Task tool) | Bash 命令 |
|------|-------------------|-----------|
| 后台机制 | Promise.race | 轮询检查 backgroundTaskId |
| v5 补丁支持 | ✅ SDK 支持 | ❌ 已移除 |
| 控制命令 | `agent_run_to_background` | - |
| 全局变量 | `__sdkBackgroundResolvers` (Map) | - |
| 多任务支持 | ✅ 按 agentId 指定 | - |
| 未来方案 | - | JetBrains MCP 自定义工具 |

**重要结论**：
1. CLI 不维护任务栈或队列来管理多个前台任务
2. **子代理 (Task)**: v5 补丁通过 Map 实现多任务精确控制
3. **Bash**: v5 移除补丁支持，将通过 JetBrains MCP 自定义 Bash 工具实现
4. 多任务依次放入后台是通过 UI 显示的"浮动"机制实现的

**v5 补丁能力总结**：

| 能力 | 支持 | API |
|------|------|-----|
| 后台最新 Agent | ✅ | `agentRunToBackground()` |
| 按 ID 后台指定 Agent | ✅ | `agentRunToBackground(agentId)` |
| Bash 后台 | ❌ | 通过 MCP 自定义实现 |

---

## 10. 参考文档

- [SDK 数据流架构](./DATA_FLOW_ARCHITECTURE.md)
- [SDK 数据模型](./sdk-data-models.md)
- [控制协议实现](../claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/protocol/ControlProtocol.kt)

# Claude Code CLI 后台执行机制分析

本文档详细分析了 Claude Code CLI 中 Ctrl+B 后台执行功能的实现原理，以及如何在 SDK 模式下实现类似功能。

---

## 概述

Claude Code CLI 支持两种后台执行方式：

| 方式 | 触发时机 | 触发方法 | 作用 |
|------|----------|----------|------|
| `&` 前缀 | 任务启动前 | 在消息开头加 `&` | 从一开始就以后台模式启动任务 |
| Ctrl+B | 任务运行中 | 按下 Ctrl+B | 将正在运行的任务移到后台 |

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

| subtype | 作用 |
|---------|------|
| `interrupt` | 中断当前任务（已有） |
| `initialize` | 初始化会话 |
| `set_permission_mode` | 设置权限模式 |
| `set_model` | 切换模型 |
| `set_max_thinking_tokens` | 设置思考 token 上限 |
| `mcp_status` | 获取 MCP 状态 |
| `mcp_message` | 发送 MCP 消息 |
| `move_to_background` | **待添加**: 移至后台 |

### 7.3 后台任务的输出获取

任务移至后台后，输出通过以下方式获取：

- **CLI 交互模式**: 使用 `/tasks` 命令查看，使用 `TaskOutput` 工具获取结果
- **SDK 模式**: 需要额外实现任务状态查询和输出获取的控制命令

### 7.4 潜在的改进方向

1. **添加 `list_background_tasks` 控制命令**: 列出所有后台任务
2. **添加 `get_task_output` 控制命令**: 获取指定任务的输出
3. **添加 `kill_background_task` 控制命令**: 终止指定后台任务

---

## 8. 参考文档

- [SDK 数据流架构](./DATA_FLOW_ARCHITECTURE.md)
- [SDK 数据模型](./sdk-data-models.md)
- [控制协议实现](../claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/protocol/ControlProtocol.kt)

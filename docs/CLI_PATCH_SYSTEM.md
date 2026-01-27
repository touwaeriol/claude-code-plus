# CLI 补丁系统文档

## 概述

Claude Code Plus 使用 AST 补丁系统来增强官方 Claude CLI，添加控制端点和额外功能。由于 Claude CLI 代码是混淆的，变量名会在每个版本中变化，补丁系统需要通过代码特征动态发现关键变量和函数名。

## 架构

```
claude-agent-sdk/cli-patches/
├── patches/                    # 补丁文件
│   ├── 001-run-in-background.js
│   ├── 002-chrome-status.js
│   ├── 003-parent-uuid.js
│   ├── 004-mcp-server-control.js  # [DISABLED] MCP 服务器控制 - 官方 2.1.19 已内置
│   ├── 005-mcp-tools.js
│   ├── 007-run-to-background.js
│   ├── 008-get-capabilities.js
│   ├── 009-skill-parent-tool-use-id.js  # Skill 消息 parent_tool_use_id 输出
│   └── index.js
├── patch-cli.js                # 补丁应用主程序
├── claude-cli-*.js             # 官方 CLI 源码（版本号动态）
└── analyze-*.js                # 分析脚本
```

## 补丁执行流程

1. **解析 AST**: 使用 Babel 解析 CLI 源码为 AST
2. **按优先级应用补丁**: 补丁按 `priority` 字段排序执行
3. **共享上下文**: 补丁通过 `context.foundVariables` 共享发现的变量名
4. **生成代码**: 将修改后的 AST 生成为新的 CLI 代码

## 关键概念

### 变量发现机制

由于 CLI 代码混淆，变量名如 `configs`、`clients` 在每个版本中可能不同。补丁通过代码特征（如对象结构、函数签名）动态发现这些变量名。

**示例**: 在 `mcp_set_servers` 处理代码中查找 `{configs:X,clients:Y,tools:Z}` 对象表达式：

```javascript
// CLI 代码中的模式
{configs:J,clients:S,tools:f}

// 补丁发现逻辑
traverse(ast, {
  ObjectExpression(objPath) {
    const props = objPath.node.properties;
    for (const prop of props) {
      if (prop.key.name === 'configs' && t.isIdentifier(prop.value)) {
        configsVarName = prop.value.name;  // 发现 "J"
      }
      if (prop.key.name === 'clients' && t.isIdentifier(prop.value)) {
        clientsVarName = prop.value.name;  // 发现 "S"
      }
    }
  }
});
```

### 补丁上下文共享

补丁通过 `context.foundVariables` 对象在补丁之间共享发现的变量名：

```javascript
// 004-mcp-server-control.js 中发现并存储
context.foundVariables.mcpConfigsVar = configsVarName;  // "J"
context.foundVariables.mcpClientsVar = clientsVarName;  // "S"
context.foundVariables.reconnectFn = reconnectFnName;   // "x2A"

// 其他补丁中使用
const configsVarName = context.foundVariables?.mcpConfigsVar;
const clientsVarName = context.foundVariables?.mcpClientsVar;
const reconnectFnName = context.foundVariables?.reconnectFn;
```

## MCP 相关补丁

### 004-mcp-server-control.js [DISABLED]

> **状态**: 自 CLI 2.1.19 起已禁用。官方 CLI 现已内置 `mcp_reconnect` 和 `mcp_toggle` 命令。

**原功能**: 添加 MCP 服务器控制端点（重连/禁用/启用）

**原控制命令**: `mcp_reconnect`, `mcp_disable`, `mcp_enable`

**官方实现差异** (2.1.19+):
- 官方使用 `serverName` (camelCase)，原补丁使用 `server_name` (snake_case)
- 官方使用统一的 `mcp_toggle` 命令配合 `enabled` 参数，替代独立的 `mcp_disable`/`mcp_enable`

**SDK 适配**: `ControlProtocol.kt` 已更新使用官方 API 格式。

---

**以下为原补丁文档，仅供参考：**

**发现的变量**:
- `reconnectFn`: 重连函数名 (如 `x2A`)
- `mcpConfigsVar`: configs 变量名 (如 `J`)
- `mcpClientsVar`: clients 变量名 (如 `S`)
- `checkDisabledFn`: 检查禁用状态函数 (如 `lPA`)
- `updateDisabledFn`: 更新禁用配置函数 (如 `CY0`)
- `disconnectFn`: 断开连接函数 (如 `gm`)

**发现逻辑**:
1. 查找 `mcp_set_servers` 处理位置
2. 在其中查找 `{configs:X,clients:Y,...}` 对象表达式
3. 提取 X 作为 configs 变量名，Y 作为 clients 变量名
4. 查找 `disabledMcpServers` 相关函数获取禁用/启用控制函数

## CLI 2.1.19 变量映射

| 用途 | 变量名 | 发现特征 |
|------|--------|----------|
| MCP 重连 (官方内置) | - | `mcp_reconnect` + `serverName` |
| MCP 切换 (官方内置) | - | `mcp_toggle` + `serverName` + `enabled` |
| Task 工具定义 | 待分析 | `="Task"` |
| Skill 工具定义 | 待分析 | `="Skill"` |

> **注意**: CLI 2.1.19 官方已内置 MCP 控制命令，无需补丁发现相关变量。

## CLI 2.1.17 变量映射

| 用途 | 变量名 | 发现特征 |
|------|--------|----------|
| Task 工具定义 | `gq` | `gq="Task"` @ line 198 |
| Skill 工具定义 | `NZ` | `NZ="Skill"` @ line 2468 |
| MCP 重连函数 | `GB` | async 函数，返回 `{client, tools}` |
| 检查禁用函数 | `dbA` | 包含 `disabledMcpServers` 和 `includes` |
| 更新禁用函数 | `njA` | 包含 `disabledMcpServers` 和 `filter` |
| 断开连接函数 | `_B` | async 函数，包含 `cleanup` 调用 |
| id2 函数 | `xp7` | Skill 内部消息 sourceToolUseID 添加 |
| Ts5 输出函数 | `z92` | 消息输出核心函数 |
| background resolvers Map | `SX1` | Task 后台 resolver Map |
| iV1 批量后台化 | `xX1` | 批量后台化所有任务 |
| Me5 Bash 后台化 | `EEY` | 后台化单个 Bash |
| R42 Agent 后台化 | `LW7` | 后台化单个 Agent |
| wt Bash 判断 | `jg6` | 判断是否是 Bash |
| Jr Agent 判断 | `Fp` | 判断是否是 Agent |
| 控制请求变量 | `HA` | for await 循环中的请求对象 |
| 响应函数 | `$A` | `$A(HA, response)` 发送成功响应 |

## 008-get-capabilities.js

**功能**: 查询 CLI 运行时能力状态

**控制命令**: `get_capabilities`

**请求格式**:
```json
{
  "type": "control_request",
  "request_id": "xxx",
  "request": { "subtype": "get_capabilities" }
}
```

**响应格式**:
```json
{
  "capabilities": {
    "background_tasks_enabled": true
  }
}
```

**实现细节**:
- 读取环境变量 `CLAUDE_CODE_DISABLE_BACKGROUND_TASKS`
- 解析布尔值: `"1"`, `"true"`, `"yes"`, `"on"` 视为禁用
- 返回 `background_tasks_enabled: !disabled`

**注入方式**:
- 查找 `<requestVar>.request.subtype === "mcp_status"` 的 `else if` 链
- 遍历到链末尾，插入新的 `else if` 分支
- 使用动态发现的 `requestVar` 和 `responderName`

**关键代码模式**:
```javascript
// CLI 中的 else if 链结构
if (requestVar.request.subtype === "interrupt") { ... }
else if (requestVar.request.subtype === "initialize") { ... }
else if (requestVar.request.subtype === "mcp_status") { ... }  // <- 从这里找到入口
else if (requestVar.request.subtype === "get_capabilities") { ... }  // <- 插入到链末尾
```

## 常见问题

### "y is not defined" 错误

**原因**: 补丁硬编码使用了变量名 `y`，但在当前 CLI 版本中该变量名已变化。

**解决方案**: 使用变量发现机制动态获取正确的变量名，而不是硬编码。

### 补丁应用失败

**排查步骤**:
1. 检查 CLI 版本是否更新
2. 运行 `node patch-cli.js --dry-run <cli.js>` 查看补丁应用状态
3. 使用 `analyze-*.js` 脚本分析 CLI 结构变化
4. 更新补丁的特征匹配逻辑

## 开发指南

### 添加新补丁

1. 在 `patches/` 目录创建新文件
2. 导出包含 `id`, `description`, `priority`, `apply()` 的对象
3. 使用 `context.foundVariables` 访问或存储发现的变量名
4. 在 `patches/index.js` 中注册补丁

### 更新现有补丁

当 CLI 版本更新导致补丁失效时：

1. 使用分析脚本查找新的代码特征
2. 更新补丁中的特征匹配逻辑
3. 确保使用动态变量发现而非硬编码

### 测试补丁

```bash
cd claude-agent-sdk/cli-patches

# 干运行模式（仅验证，不生成文件）
node patch-cli.js --dry-run claude-cli-2.1.19.js

# 应用补丁
node patch-cli.js claude-cli-2.1.19.js patched-cli.js
```

## 变更历史

### 2026-01-25 (CLI 2.1.19)

- **升级**: CLI 版本从 2.1.17 升级到 2.1.19
- **禁用**: `004-mcp-server-control.js` 补丁 - 官方 CLI 2.1.19 已内置 `mcp_reconnect` 和 `mcp_toggle` 命令
- **更新**: `ControlProtocol.kt` 适配官方 API 格式：
  - `server_name` → `serverName` (camelCase)
  - `mcp_disable`/`mcp_enable` → 统一使用 `mcp_toggle` + `enabled` 参数

**官方内置 MCP 控制命令**:
```javascript
// mcp_reconnect - 重连指定 MCP 服务器
{ "subtype": "mcp_reconnect", "serverName": "server-name" }

// mcp_toggle - 启用/禁用 MCP 服务器
{ "subtype": "mcp_toggle", "serverName": "server-name", "enabled": true/false }
```

### 2026-01-23 (CLI 2.1.17)

- **升级**: CLI 版本从 2.1.15 升级到 2.1.17（NPM: `@anthropic-ai/claude-code@2.1.17`）
- **更新**: `cli-version.properties` 同步到 `npm.version=0.2.17`（NPM: `@anthropic-ai/claude-agent-sdk@0.2.17`）
- **更新**: 变量映射表更新为 2.1.17 版本

### 2026-01-23 (CLI 2.1.15)

- **升级**: CLI 版本从 2.1.14 升级到 2.1.15
- **修复**: `008-get-capabilities.js` 补丁重写，正确处理 `else if` 链结构
- **更新**: 变量映射表更新为 2.1.15 版本
- **删除**: 移除旧版本 CLI 文件 (2.1.12, 2.1.14)

**get_capabilities 补丁修复说明**:

原补丁查找独立的 `IfStatement`，但 CLI 使用 `else if` 链，导致无法找到注入位置。

修复方案：
1. 查找 `mcp_status` 的 `if` 语句作为入口
2. 遍历 `else if` 链到末尾
3. 在链末尾插入新的 `else if` 分支
4. 使用 AST 构建代码而非字符串解析

### 2026-01-21

- **重构**: 将 004-mcp-reconnect.js 和 006-mcp-disable-enable.js 合并为 004-mcp-server-control.js
- **删除**: 移除 010-skill-id2-assistant-fix.js（不再需要）
- **文档**: 更新补丁文档以反映当前补丁结构

### 2026-01-20

- **修复**: MCP 相关补丁中的硬编码变量名问题
- **改进**: 添加动态变量发现机制，从 `mcp_set_servers` 处理代码中提取 `configs` 和 `clients` 变量名
- **影响**: 修复 `user_interaction` MCP 服务器连接失败问题 ("y is not defined" 错误)

### 2026-01-21

- **分析**: Skill 工具的 `parent_tool_use_id` 输出问题
- **新增**: 补丁 `009-skill-parent-tool-use-id.js` - 同时修复 `id2`（补齐 assistant 的 sourceToolUseID）并修改 Ts5 (Ls5) 输出

---

## Skill/Task 子会话消息输出分析

### 问题背景

SDK 输出的消息中，Task 子代理的消息有正确的 `parent_tool_use_id`，但 Skill 的内部消息 `parent_tool_use_id` 始终为 `null`。

### AST 分析结果

运行 `ast-analyzer.mjs` 脚本分析 CLI 源码后发现：

- **Task 定义**: `gq = "Task"` (line 198)
- **Skill 定义**: `NZ = "Skill"` (line 2468)

**parent_tool_use_id 属性统计**:
- 非 null 值: 3 处 (均为 `A.parentToolUseID`)
- null 值: 13 处 (硬编码)

**sourceToolUseID 访问**: line 3612

### 消息输出核心函数 (Ts5/Ls5)

位置: line 3039 附近

- 普通 `assistant`/`user` 消息：硬编码 `parent_tool_use_id: null`
- `progress` 类型的 `agent_progress`：使用 `A.parentToolUseID`

### 根本原因

| | Task | Skill |
|---|------|-------|
| 是子会话 | Yes | Yes |
| 消息输出路径 | `progress` -> `agent_progress` | 普通 `assistant`/`user` |
| parent_tool_use_id | `A.parentToolUseID` (有值) | 硬编码 `null` |
| 内部追踪机制 | `parentToolUseID` | `sourceToolUseID` |

**Task 子代理**: 通过 `progress` 类型的 `agent_progress` 事件发送消息，携带 `parentToolUseID`。

**Skill**: 内部通过 `id2` 函数给消息添加 `sourceToolUseID`，但在 `Ts5` 函数输出时被丢弃，硬编码为 `null`。

### Skill 内部追踪机制

**id2 函数** - 为 Skill 内部消息添加 `sourceToolUseID`：

```javascript
// 原始实现（有 bug）
function id2(A, Q) {
  if (!Q) return A;
  return A.map(B => {
    if (B.type === "user") return {...B, sourceToolUseID: Q};
    return B;  // assistant 消息未添加 sourceToolUseID！
  });
}
```

**问题**: `id2` 函数只为 `user` 类型消息添加 `sourceToolUseID`，`assistant` 消息被遗漏。

### 设计不一致

这是 CLI 的一个 **设计不一致** 或 **遗漏**：
- Task 和 Skill 都是子会话
- 两者内部都有追踪机制
- 但只有 Task 在输出时正确传递了 `parent_tool_use_id`
- Skill 的 `id2` 函数遗漏了对 `assistant` 消息的处理

### 补丁方案

需要 **一个补丁** 修复此问题：

#### 补丁 009-skill-parent-tool-use-id.js

**目标**:
1. 修复 `id2`：同时为 `user` 和 `assistant` 消息添加 `sourceToolUseID`
2. 修改 `Ts5` 输出：将 `sourceToolUseID` 透传为 `parent_tool_use_id`

**修改要点**:
- `id2`: `B.type === "user" || B.type === "assistant"`
- `Ts5`: `parent_tool_use_id: K.sourceToolUseID || null`

### 验证方法

运行 Skill 工具后，检查 SDK 输出的消息：
- `assistant` 消息应有非 null 的 `parent_tool_use_id`
- `user` 消息应有非 null 的 `parent_tool_use_id`
- 两者的 `parent_tool_use_id` 应等于 Skill 工具调用的 tool_use_id


---



### 语法验证工具

增强后的 CLI 必须通过语法验证：

```bash
node syntax-validator.mjs [enhanced-cli-path]
```

验证项目：
1. **Babel 严格模式**: 语法错误检测
2. **危险模式**: `undefined.x`, `null.x` 等
3. **补丁完整性**: 控制命令存在性
4. **AST 验证**: 注入代码结构

**背景**: 曾因补丁硬编码变量名导致 "y is not defined" 运行时错误。

## 升级检查流程

### 重要提醒

**每次升级官方 Claude CLI 版本时，必须执行以下检查流程**，确认官方是否已修复我们补丁解决的问题。

### 升级前检查清单

1. **获取新版本 CLI 源码**
2. **使用 AST 分析工具检查关键代码**
3. **对每个补丁检查官方是否已修复**

### 决策流程

- 官方已修复某补丁 -> 移除对应补丁，更新 patches/index.js
- 官方未修复 -> 保留补丁
- 重新生成增强 CLI 并验证功能

### 注意事项

- 不要盲目升级，即使官方版本号更新
- 保持 AST 工具更新
- 升级后必须测试所有增强功能
- 官方每次更新可能改变混淆后的变量名




---

## 升级检查流程

### 重要提醒

**每次升级官方 Claude CLI 版本时，必须执行以下检查流程**，确认官方是否已修复我们补丁解决的问题。

### 升级前检查清单

1. 获取新版本 CLI 源码
2. 使用 AST 分析工具检查关键代码
3. 对每个补丁检查官方是否已修复

### 决策流程

- 官方已修复某补丁 -> 移除对应补丁，更新 patches/index.js
- 官方未修复 -> 保留补丁
- 重新生成增强 CLI 并验证功能

### 补丁检查表

| 补丁 | 检查方法 | 官方已修复标志 | 状态 |
|------|----------|---------------|------|
| 009-skill-parent-tool-use-id | 检查 id2 和 Ts5 函数 | assistant 消息有 sourceToolUseID，输出使用动态值 | 活跃 |
| 007-run-to-background | 检查控制端点 | 官方支持后台化 | 活跃 |
| 004-mcp-server-control | 检查控制端点 | 官方支持 MCP 重连/禁用/启用 | **已禁用 (2.1.19)** |

### 注意事项

- 不要盲目升级，即使官方版本号更新
- 保持 AST 工具更新
- 升级后必须测试所有增强功能
- 官方每次更新可能改变混淆后的变量名

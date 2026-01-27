# Claude Agent SDK TypeScript 翻译计划

## 📋 概述

将 `claude-agent-sdk` (Kotlin) 翻译为 VS Code 扩展可用的 TypeScript 版本。

**源目录**: `claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/`
**目标目录**: `vscode-extension/src/sdk/claude/`

## 📊 当前状态

| 模块 | Kotlin 文件数 | TypeScript 文件数 | 覆盖率 |
|------|--------------|------------------|--------|
| 主客户端 | 3 | 1 (claudeCli.ts) | 100% |
| 控制协议 | 5 | 6 | 100% |
| 传输层 | 2 | 3 | 100% |
| 类型定义 | 11 | 11 | 100% |
| MCP 支持 | 4 | 5 | 100% |
| 工具函数 | 5 | 7 | 100% |
| **总计** | **50** | **33** | **~95%** |

## ✅ 完成状态

- [x] Phase 1: 增强 CLI 集成（复用 cli-patches）
- [x] Phase 1b: 传输层 (transport/)
- [x] Phase 2: 类型定义 (types/)
- [x] Phase 3: 控制协议 (protocol/)
- [x] Phase 4: 主客户端 (claudeCli.ts)
- [x] Phase 5: MCP 支持 (mcp/)
- [x] Phase 6: 工具函数 (callback/, builders/)
- [x] 构建验证通过 (1.6MB)

---

## 📁 文件映射

### Phase 1: 增强 CLI 集成 ✅

| 源文件 | 目标文件 | 状态 |
|--------|----------|------|
| cli-patches/*.js | resources/cli-patches/ | ✅ 已复制 (9 个补丁) |
| claude-cli-*-enhanced.mjs | resources/bundled/claude-cli-enhanced.mjs | ✅ 已复制 (11MB) |

**Gradle 自动复制**: `copyToVsCodeExtension` 任务已添加到 `claude-agent-sdk/build.gradle.kts`

### Phase 1b: 传输层 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| transport/Transport.kt | sdk/claude/transport/transport.ts | ✅ |
| transport/SubprocessTransport.kt | sdk/claude/transport/subprocessTransport.ts | ✅ |
| - | sdk/claude/transport/index.ts | ✅ |

### Phase 2: 类型定义 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| types/Options.kt | sdk/claude/types/options.ts | ✅ |
| types/Messages.kt | sdk/claude/types/messages.ts | ✅ |
| types/ToolTypes.kt | sdk/claude/types/toolTypes.ts | ✅ |
| types/ContentBlocks.kt | sdk/claude/types/contentBlocks.ts | ✅ |
| types/StreamEvents.kt | sdk/claude/types/streamEvents.ts | ✅ |
| types/Hooks.kt | sdk/claude/types/hooks.ts | ✅ |
| types/Permissions.kt | sdk/claude/types/permissions.ts | ✅ |
| types/McpTypes.kt | sdk/claude/types/mcpTypes.ts | ✅ |
| types/Errors.kt | sdk/claude/types/errors.ts | ✅ |
| - | sdk/claude/types/common.ts | ✅ |
| - | sdk/claude/types/index.ts | ✅ |

### Phase 3: 控制协议 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| protocol/ControlProtocol.kt | sdk/claude/protocol/controlProtocol.ts | ✅ |
| protocol/ControlProtocolModels.kt | sdk/claude/protocol/models.ts | ✅ |
| protocol/MessageParser.kt | sdk/claude/protocol/messageParser.ts | ✅ |
| protocol/ToolTypeParser.kt | sdk/claude/protocol/toolTypeParser.ts | ✅ |
| protocol/McpMessageHandler.kt | sdk/claude/protocol/mcpMessageHandler.ts | ✅ |
| - | sdk/claude/protocol/index.ts | ✅ |

### Phase 4: 主客户端 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| ClaudeCodeSdkClient.kt | sdk/claude/claudeCli.ts | ✅ (已有，增强) |
| Query.kt | (内嵌 claudeCli.ts) | ✅ |

### Phase 5: MCP 支持 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| mcp/McpServer.kt | sdk/claude/mcp/mcpServer.ts | ✅ |
| mcp/McpServerBase.kt | sdk/claude/mcp/mcpServerBase.ts | ✅ |
| mcp/ToolUseContext.kt | sdk/claude/mcp/toolUseContext.ts | ✅ |
| mcp/annotations/McpAnnotations.kt | sdk/claude/mcp/decorators.ts | ✅ |
| - | sdk/claude/mcp/index.ts | ✅ |

### Phase 6: 工具函数 ✅

| Kotlin 源文件 | TypeScript 目标文件 | 状态 |
|--------------|-------------------|------|
| callback/ToolCallback.kt | sdk/claude/callback/toolCallback.ts | ✅ |
| callback/ToolCallbackRegistry.kt | sdk/claude/callback/registry.ts | ✅ |
| builders/ClaudeCodeOptionsExtensions.kt | sdk/claude/builders/optionsBuilder.ts | ✅ |
| builders/HookBuilder.kt | sdk/claude/builders/hookBuilder.ts | ✅ |
| builders/McpServerBuilder.kt | sdk/claude/builders/mcpServerBuilder.ts | ✅ |
| - | sdk/claude/callback/index.ts | ✅ |
| - | sdk/claude/builders/index.ts | ✅ |

---

## 🔑 关键控制命令映射

从增强 CLI 补丁提取的控制命令：

| 补丁 | 控制命令 | TypeScript 方法 |
|------|---------|----------------|
| 001 | `agent_run_to_background` | `agentRunToBackground()` |
| 001 | `agents_run_all_to_background` | `agentsRunAllToBackground()` |
| 002 | `get_chrome_status` | `getChromeStatus()` |
| 004 | `mcp_reconnect` | `reconnectMcp()` |
| 004 | `mcp_disable` | `disableMcp()` |
| 004 | `mcp_enable` | `enableMcp()` |
| 005 | `mcp_tools` | `getMcpTools()` |
| 007 | `run_to_background` | `runToBackground()` |
| 008 | `get_capabilities` | `getCapabilities()` |

---

## 📈 进度追踪

### 详细进度日志

| 日期 | 任务 | 状态 | 备注 |
|------|------|------|------|
| 2026-01-26 | 创建计划文档 | ✅ 完成 | |
| 2026-01-26 | 复制增强 CLI | ✅ 完成 | 11.17 MB + 9 个补丁文件 |
| 2026-01-26 | Phase 1b 传输层 | ✅ 完成 | 3 个文件 |
| 2026-01-26 | Phase 2 类型定义 | ✅ 完成 | 11 个文件 |
| 2026-01-26 | Phase 3 控制协议 | ✅ 完成 | 6 个文件 |
| 2026-01-26 | Phase 5 MCP 支持 | ✅ 完成 | 5 个文件 |
| 2026-01-26 | Phase 6 工具函数 | ✅ 完成 | 7 个文件 |
| 2026-01-26 | 构建验证 | ✅ 完成 | 1.6MB 输出 |

---

## 🏗️ 最终目录结构

```
vscode-extension/src/sdk/claude/
├── builders/
│   ├── hookBuilder.ts        (9KB)
│   ├── index.ts              (893B)
│   ├── mcpServerBuilder.ts   (8KB)
│   └── optionsBuilder.ts     (9KB)
├── callback/
│   ├── index.ts              (459B)
│   ├── registry.ts           (5KB)
│   └── toolCallback.ts       (3KB)
├── mcp/
│   ├── decorators.ts         (14KB)
│   ├── index.ts              (3KB)
│   ├── mcpServer.ts          (9KB)
│   ├── mcpServerBase.ts      (15KB)
│   └── toolUseContext.ts     (3KB)
├── protocol/
│   ├── controlProtocol.ts    (29KB)
│   ├── index.ts              (1KB)
│   ├── mcpMessageHandler.ts  (6KB)
│   ├── messageParser.ts      (17KB)
│   ├── models.ts             (12KB)
│   └── toolTypeParser.ts     (17KB)
├── transport/
│   ├── index.ts              (1KB)
│   ├── subprocessTransport.ts (25KB)
│   └── transport.ts          (5KB)
├── types/
│   ├── common.ts             (402B)
│   ├── contentBlocks.ts      (4KB)
│   ├── errors.ts             (4KB)
│   ├── hooks.ts              (3KB)
│   ├── index.ts              (4KB)
│   ├── mcpTypes.ts           (3KB)
│   ├── messages.ts           (4KB)
│   ├── options.ts            (10KB)
│   ├── permissions.ts        (3KB)
│   ├── streamEvents.ts       (4KB)
│   └── toolTypes.ts          (8KB)
└── claudeCli.ts              (16KB)

vscode-extension/resources/
├── bundled/
│   └── claude-cli-enhanced.mjs (11MB)
└── cli-patches/
    ├── 001-run-in-background.js
    ├── 002-chrome-status.js
    ├── 003-parent-uuid.js
    ├── 004-mcp-server-control.js
    ├── 005-mcp-tools.js
    ├── 007-run-to-background.js
    ├── 008-get-capabilities.js
    ├── 009-skill-parent-tool-use-id.js
    └── index.js
```

**总计**: 33 个 TypeScript 文件 + 10 个 JavaScript 资源文件

---

## 📝 翻译规则

| Kotlin 概念 | TypeScript 实现 |
|------------|----------------|
| `sealed class` | discriminated union (使用 `type` 字段) |
| `data class` | `interface` |
| `enum class` | `type` 字面量联合 + `const` 对象 |
| `@SerialName` | 直接使用 snake_case 属性名 |
| `typealias (函数)` | 函数类型 |
| `suspend fun` | `async function` |
| Kotlin 注解 | TypeScript 装饰器 |
| `JsonObject` | `Record<string, unknown>` |
| 协程上下文 | `AsyncLocalStorage` |

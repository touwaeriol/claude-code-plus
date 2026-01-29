# VS Code 设置同步与 MCP 配置传递实现计划

## 📋 问题概述

VS Code 版本与 JetBrains 版本存在以下差异和缺失：

### 1️⃣ 设置差异
| 问题 | 当前状态 | 目标状态 |
|------|----------|----------|
| `terminalReadTimeout` 默认值 | 30 秒 | 10 秒 (与 JB 一致) |
| `terminalDisableInteractive` 字段 | ❌ 缺失 | ✅ 添加 |
| `permissionMode.dontAsk` 选项 | ❌ 缺失 | ✅ 添加 |
| `gitGenerateTools` 字段 | ❌ 缺失 | ✅ 添加 |

### 2️⃣ 关键功能缺失：MCP 配置未传递给 CLI

**当前问题**：
- 前端 UI 有完整的 MCP 设置和 instructions
- 但 `agentRSocketServer.ts` 使用 `ClaudeCliSessionManager`，它**不支持 MCP 配置**
- Claude CLI 启动时没有 `--mcp-config` 参数
- MCP instructions 没有追加到系统提示词

**JetBrains 实现**：
```
McpSettings → buildMcpServersJson() → 临时文件 → --mcp-config <file>
                                                 ↓
                                     instructions → systemPrompt
```

**VS Code 当前**：
```
settingsStore → ❌ 断开 → claudeCli.ts (无 MCP 支持)
```

---

## 🎯 实现计划

### Phase 1: 修复设置差异 (小改动)

#### Task 1.1: 修复 settingsStore.ts 默认值

**文件**: `vscode-extension/webview-ui/src/stores/settingsStore.ts`

**修改内容**:
```typescript
// 修改 Terminal 配置
{ 
  name: 'Terminal', 
  // ...
  terminalReadTimeout: 10,  // 从 30 改为 10 (与 JB 一致)
  terminalDisableInteractive: false,  // 新增字段
  // ...
}
```

#### Task 1.2: 添加 McpServer 接口缺失字段

**文件**: `vscode-extension/webview-ui/src/stores/settingsStore.ts`

```typescript
export interface McpServer {
  // ... 现有字段
  terminalDisableInteractive?: boolean  // 新增
}
```

#### Task 1.3: 添加 dontAsk 权限模式

**文件**: `vscode-extension/webview-ui/src/pages/SettingsPage.vue`

```typescript
const permissionModeOptions = [
  { label: 'Default', value: 'default' },
  { label: 'Accept Edits', value: 'acceptEdits' },
  { label: 'Plan', value: 'plan' },
  { label: 'Bypass Permissions', value: 'bypassPermissions' },
  { label: "Don't Ask", value: 'dontAsk' },  // 新增
]
```

#### Task 1.4: 添加 gitGenerateTools 字段

**文件**: `vscode-extension/webview-ui/src/stores/settingsStore.ts`

```typescript
export interface GitGenerateSettings {
  // ... 现有字段
  tools?: string[]  // 新增：允许的工具列表
}

// 默认值
const gitGenerate = ref<GitGenerateSettings>({
  // ...
  tools: []  // 空数组表示使用默认工具
})
```

---

### Phase 2: 实现 MCP 配置传递给 CLI (核心改动)

#### 架构设计

```
┌─────────────────────┐     ┌──────────────────────┐
│  settingsStore.ts   │────▶│  VS Code Settings    │
│  (前端 UI)           │     │  (持久化存储)          │
└─────────────────────┘     └──────────┬───────────┘
                                       │
                                       ▼
┌─────────────────────┐     ┌──────────────────────┐
│  agentRSocketServer │◀────│  读取 MCP 配置        │
│  .ts                │     │  从 VS Code Settings │
└─────────────────────┘     └──────────────────────┘
           │
           │ 构建 McpServerConfig[]
           ▼
┌─────────────────────┐
│  mcpConfigBuilder   │◀── 新增模块
│  .ts                │
├─────────────────────┤
│ • buildMcpConfig()  │
│ • writeTempFile()   │
│ • buildInstructions │
└─────────────────────┘
           │
           │ 生成 JSON + 临时文件
           ▼
┌─────────────────────┐
│  claudeCli.ts       │
│  buildClaudeArgs()  │
├─────────────────────┤
│ + --mcp-config      │◀── 添加参数
│ + systemPrompt file │◀── 添加 instructions
└─────────────────────┘
           │
           ▼
┌─────────────────────┐
│  Claude CLI Process │
└─────────────────────┘
```

---

#### Task 2.1: 创建 MCP 配置构建器

**新建文件**: `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`

```typescript
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'
import { v4 as uuidv4 } from 'uuid'

export interface McpServerSettings {
  name: string
  enabled: boolean
  backends: string  // "All" | "Claude" | "Codex"
  type: 'http' | 'stdio' | 'sse'
  url?: string
  headers?: Record<string, string>
  command?: string
  args?: string[]
  env?: Record<string, string>
  // Instructions
  instructions?: string
  instructionsClaude?: string
  instructionsCodex?: string
  defaultInstructions?: string
}

export interface McpConfigResult {
  configFilePath: string | null  // MCP 配置 JSON 临时文件路径
  systemPromptAppendix: string   // 需要追加到 systemPrompt 的 instructions
}

/**
 * 构建 MCP 配置 JSON 并写入临时文件
 */
export function buildMcpConfig(
  servers: McpServerSettings[],
  backend: 'claude' | 'codex'
): McpConfigResult {
  const enabledServers = servers.filter(s => {
    if (!s.enabled) return false
    // 检查 backends 是否包含当前后端
    if (s.backends === 'All') return true
    return s.backends.toLowerCase().includes(backend)
  })

  if (enabledServers.length === 0) {
    return { configFilePath: null, systemPromptAppendix: '' }
  }

  // 1. 构建 MCP 服务器 JSON
  const mcpServers: Record<string, any> = {}
  
  for (const server of enabledServers) {
    if (server.type === 'http' && server.url) {
      mcpServers[server.name] = {
        type: 'http',
        url: server.url,
        headers: server.headers ?? {}
      }
    } else if (server.type === 'sse' && server.url) {
      mcpServers[server.name] = {
        type: 'sse',
        url: server.url,
        headers: server.headers ?? {}
      }
    } else if (server.type === 'stdio' && server.command) {
      mcpServers[server.name] = {
        type: 'stdio',
        command: server.command,
        args: server.args ?? [],
        env: server.env ?? {}
      }
    }
  }

  // 2. 写入临时文件
  let configFilePath: string | null = null
  if (Object.keys(mcpServers).length > 0) {
    const tempDir = path.join(os.tmpdir(), 'claude-code-plus')
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true })
    }
    
    const timestamp = new Date().toISOString().replace(/[:.]/g, '-')
    const uuid = uuidv4().substring(0, 8)
    configFilePath = path.join(tempDir, `claude_mcp_config_${timestamp}_${uuid}.json`)
    
    const configJson = JSON.stringify({ mcpServers }, null, 2)
    fs.writeFileSync(configFilePath, configJson, 'utf8')
  }

  // 3. 构建 instructions 追加到 systemPrompt
  const instructionsParts: string[] = []
  
  for (const server of enabledServers) {
    // 优先使用 backend 特定的 instructions
    let instruction: string | undefined
    if (backend === 'claude' && server.instructionsClaude) {
      instruction = server.instructionsClaude
    } else if (backend === 'codex' && server.instructionsCodex) {
      instruction = server.instructionsCodex
    } else {
      // 回退到通用 instructions 或 默认 instructions
      instruction = server.instructions || server.defaultInstructions
    }
    
    if (instruction && instruction.trim()) {
      instructionsParts.push(`## ${server.name} MCP\n\n${instruction}`)
    }
  }

  const systemPromptAppendix = instructionsParts.length > 0
    ? '\n\n---\n\n# MCP Server Instructions\n\n' + instructionsParts.join('\n\n---\n\n')
    : ''

  return { configFilePath, systemPromptAppendix }
}

/**
 * 清理临时 MCP 配置文件
 */
export function cleanupMcpConfigFile(filePath: string | null): void {
  if (filePath && fs.existsSync(filePath)) {
    try {
      fs.unlinkSync(filePath)
    } catch (e) {
      // ignore cleanup errors
    }
  }
}
```

---

#### Task 2.2: 扩展 ClaudeCliSessionConfig

**文件**: `vscode-extension/src/sdk/claude/claudeCli.ts`

```typescript
export type ClaudeCliSessionConfig = {
  sessionId: string
  cwd: string
  model: string
  permissionMode: 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions' | 'dontAsk'  // 添加 dontAsk
  includePartialMessages: boolean
  dangerouslySkipPermissions: boolean
  addDirs?: string[]
  // 新增 MCP 支持
  mcpConfigFilePath?: string       // MCP 配置文件路径
  systemPromptFilePath?: string    // 系统提示词文件路径 (含 MCP instructions)
  appendSystemPrompt?: string      // 追加的系统提示词内容
}
```

---

#### Task 2.3: 修改 buildClaudeArgs 函数

**文件**: `vscode-extension/src/sdk/claude/claudeCli.ts`

```typescript
function buildClaudeArgs(config: ClaudeCliSessionConfig): string[] {
  const args = [
    '--print',
    '--verbose',
    '--output-format', 'stream-json',
    '--input-format', 'stream-json',
    '--session-id', config.sessionId,
    '--model', config.model,
    '--permission-mode', config.permissionMode,
  ]

  if (config.includePartialMessages) {
    args.push('--include-partial-messages')
  }

  if (config.dangerouslySkipPermissions) {
    args.push('--dangerously-skip-permissions')
  }

  // 多工作区支持
  if (config.addDirs && config.addDirs.length > 0) {
    for (const dir of config.addDirs) {
      args.push('--add-dir', dir)
    }
  }

  // ⭐ 新增：MCP 配置文件
  if (config.mcpConfigFilePath) {
    args.push('--mcp-config', config.mcpConfigFilePath)
  }

  // ⭐ 新增：系统提示词文件 (含 MCP instructions)
  if (config.systemPromptFilePath) {
    args.push('--append-system-prompt-file', config.systemPromptFilePath)
  }

  return args
}
```

---

#### Task 2.4: 在 agentRSocketServer.ts 中集成 MCP 配置

**文件**: `vscode-extension/src/sdk/rsocket/agentRSocketServer.ts`

```typescript
import { buildMcpConfig, cleanupMcpConfigFile } from '../claude/mcpConfigBuilder'
import * as fs from 'fs'
import * as path from 'path'
import * as os from 'os'

// 在创建 CLI session 时：
async function createCliSession(sessionId: string, config: SessionConfig) {
  // 1. 从 VS Code settings 读取 MCP 配置
  const mcpServers = await getMcpServersFromSettings()
  
  // 2. 构建 MCP 配置
  const backend = config.provider === 'codex' ? 'codex' : 'claude'
  const mcpResult = buildMcpConfig(mcpServers, backend)
  
  // 3. 如果有 instructions，写入临时文件
  let systemPromptFilePath: string | undefined
  if (mcpResult.systemPromptAppendix) {
    const tempDir = path.join(os.tmpdir(), 'claude-code-plus')
    if (!fs.existsSync(tempDir)) {
      fs.mkdirSync(tempDir, { recursive: true })
    }
    systemPromptFilePath = path.join(tempDir, `system_prompt_${sessionId}.md`)
    fs.writeFileSync(systemPromptFilePath, mcpResult.systemPromptAppendix, 'utf8')
  }
  
  // 4. 创建 CLI session
  const cliSession = await claudeCli.getOrCreate({
    sessionId,
    cwd: getWorkspaceRoot(),
    model: config.model,
    permissionMode: toClaudePermissionMode(config.permissionMode),
    includePartialMessages: config.includePartialMessages,
    dangerouslySkipPermissions: config.dangerouslySkipPermissions,
    addDirs: getAdditionalDirs(),
    // 新增 MCP 参数
    mcpConfigFilePath: mcpResult.configFilePath ?? undefined,
    systemPromptFilePath,
  })
  
  // 5. 注册清理回调
  cliSession.onClose(() => {
    cleanupMcpConfigFile(mcpResult.configFilePath)
    if (systemPromptFilePath && fs.existsSync(systemPromptFilePath)) {
      fs.unlinkSync(systemPromptFilePath)
    }
  })
  
  return cliSession
}

// 从 VS Code settings 读取 MCP 服务器配置
async function getMcpServersFromSettings(): Promise<McpServerSettings[]> {
  const config = vscode.workspace.getConfiguration('claudeCodePlus')
  const mcpServers = config.get<McpServerSettings[]>('mcp.servers', [])
  
  // 合并内置 MCP 服务器 (Terminal, File, LSP, Git)
  const builtInServers = await getBuiltInMcpServers()
  
  return [...builtInServers, ...mcpServers]
}
```

---

### Phase 3: VS Code Settings 持久化

#### Task 3.1: 添加 MCP 设置到 package.json

**文件**: `vscode-extension/package.json`

```json
{
  "contributes": {
    "configuration": {
      "title": "Claude Code Plus",
      "properties": {
        "claudeCodePlus.mcp.servers": {
          "type": "array",
          "default": [],
          "description": "MCP server configurations",
          "items": {
            "type": "object",
            "properties": {
              "name": { "type": "string" },
              "enabled": { "type": "boolean" },
              "backends": { "type": "string" },
              "type": { "type": "string", "enum": ["http", "stdio", "sse"] },
              "url": { "type": "string" },
              "command": { "type": "string" },
              "args": { "type": "array", "items": { "type": "string" } },
              "instructions": { "type": "string" },
              "instructionsClaude": { "type": "string" },
              "instructionsCodex": { "type": "string" }
            }
          }
        },
        "claudeCodePlus.claude.permissionMode": {
          "type": "string",
          "default": "default",
          "enum": ["default", "acceptEdits", "plan", "bypassPermissions", "dontAsk"],
          "description": "Default permission mode for Claude"
        },
        "claudeCodePlus.terminal.readTimeout": {
          "type": "number",
          "default": 10,
          "description": "Terminal read timeout in seconds"
        },
        "claudeCodePlus.terminal.disableInteractive": {
          "type": "boolean",
          "default": false,
          "description": "Disable interactive terminal mode"
        },
        "claudeCodePlus.gitGenerate.tools": {
          "type": "array",
          "default": [],
          "items": { "type": "string" },
          "description": "Allowed tools for Git Generate feature"
        }
      }
    }
  }
}
```

---

#### Task 3.2: 修改 extension.ts 读取设置

**文件**: `vscode-extension/src/extension.ts`

添加设置变更监听和同步逻辑。

---

### Phase 4: 内置 MCP 服务器集成

#### Task 4.1: 实现 getBuiltInMcpServers 函数

为 Terminal, File, LSP, Git 等内置 MCP 服务器生成配置，包括：
- 从 mcpRegistry 获取服务器 URL
- 添加必要的 headers (X-MCP-Connect-Id, X-MCP-Provider)
- 设置默认 instructions

---

## 📊 任务清单

| Phase | Task | 文件 | 优先级 | 复杂度 |
|-------|------|------|--------|--------|
| 1 | 1.1 修复 terminalReadTimeout | settingsStore.ts | 高 | 低 |
| 1 | 1.2 添加 terminalDisableInteractive | settingsStore.ts | 中 | 低 |
| 1 | 1.3 添加 dontAsk 权限模式 | SettingsPage.vue, claudeCli.ts | 中 | 低 |
| 1 | 1.4 添加 gitGenerateTools | settingsStore.ts | 低 | 低 |
| 2 | 2.1 创建 mcpConfigBuilder.ts | 新建文件 | **高** | 中 |
| 2 | 2.2 扩展 ClaudeCliSessionConfig | claudeCli.ts | **高** | 低 |
| 2 | 2.3 修改 buildClaudeArgs | claudeCli.ts | **高** | 低 |
| 2 | 2.4 集成到 agentRSocketServer | agentRSocketServer.ts | **高** | 高 |
| 3 | 3.1 添加 MCP 设置到 package.json | package.json | 中 | 中 |
| 3 | 3.2 设置变更监听 | extension.ts | 中 | 中 |
| 4 | 4.1 内置 MCP 服务器集成 | 新建/修改文件 | 中 | 高 |

---

## ⚠️ 注意事项

1. **临时文件清理**: 必须在会话结束时清理 MCP 配置和 systemPrompt 临时文件
2. **后端区分**: instructions 需要根据 claude/codex 后端选择不同内容
3. **Settings 同步**: 前端 UI 修改需要持久化到 VS Code Settings
4. **向后兼容**: 没有 MCP 配置时应该正常工作

---

## 🧪 测试计划

1. **单元测试**: mcpConfigBuilder.ts 的 buildMcpConfig 函数
2. **集成测试**: 
   - 验证 MCP 配置文件正确生成
   - 验证 CLI 正确接收 --mcp-config 参数
   - 验证 instructions 正确追加到系统提示词
3. **E2E 测试**:
   - 启动会话，验证 MCP 工具可用
   - 修改 MCP 设置，验证新会话生效

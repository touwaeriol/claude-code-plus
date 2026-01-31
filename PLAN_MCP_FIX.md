# VS Code 版本 MCP 配置修复计划

## ✅ 修复已完成 (2026-01-31)

### 修复摘要

| 阶段 | 任务 | 状态 |
|------|------|------|
| Phase 1.1 | 修复 `getMcpServersFromSettings()` 函数 | ✅ 完成 |
| Phase 1.2 | 导出 McpConfigurable 类型 | ✅ 完成 |
| Phase 1.3 | 更新 McpServerSettings 接口 | ✅ 完成 |
| Phase 4 | 服务器名称映射常量 | ✅ 完成 |
| Phase 3 | 系统提示词传递修复 | ✅ 完成 |
| Phase 2 | Context7 MCP 实现 | ✅ 完成 |

### 修改的文件

1. **`vscode-extension/src/server/rsocket/agentRSocketServer.ts`**
   - 修复 `getMcpServersFromSettings()` 函数，正确读取 `McpConfigurable` 配置
   - 添加 `convertBuiltInToSettings()` 和 `convertCustomToSettings()` 转换函数
   - 添加 `formatBackendsArray()` 辅助函数
   - Context7 特殊处理为外部 HTTP 服务器

2. **`vscode-extension/src/ide/settings/configurables/McpConfigurable.ts`**
   - 更新 `McpServerConfig` 接口，添加 `headers` 和 `sse` 类型支持

3. **`vscode-extension/src/ide/mcp/constants.ts`** (新建)
   - `MCP_SERVER_NAMES`: UI 名称到 MCP 名称映射
   - `toMcpServerName()`: 名称转换函数
   - `CONTEXT7_CONFIG`: Context7 服务器配置常量

4. **`vscode-extension/src/ide/mcp/defaults/mcpInstructions.ts`** (新建)
   - 6 个内置 MCP 服务器的默认系统提示词
   - `getDefaultInstructions()`: 获取默认提示词函数

5. **`vscode-extension/src/ide/mcp/index.ts`**
   - 添加 Context7 MCP 日志记录

---

## 📋 问题概述

VS Code 版本的 MCP 配置没有正确传递给 SDK（Claude/Codex），导致用户在设置界面配置的 MCP 服务器完全不生效。

### 根本原因

`agentRSocketServer.ts` 中的 `getMcpServersFromSettings()` 函数试图从不存在的配置项 `claudeCodePlus.mcp.servers` 读取数据，总是返回空数组。

### 影响范围

- ❌ 所有内置 MCP 服务器配置不生效（User Interaction、VS Code LSP、VS Code File、Terminal、Git）
- ❌ Context7 MCP 完全未实现
- ❌ 自定义 MCP 服务器配置不生效
- ❌ 后端选择（Claude/Codex）配置不生效
- ❌ MCP 系统提示词配置不生效

---

## 🏗️ 修复架构

### 数据流对比

**JetBrains 版本（正确）**:
```
Settings UI → AgentSettingsService → loadMcpServersConfig() 
            → ClaudeDefaults.mcpServersConfig → prepareMcpSession()
            → --mcp-config 参数 → Claude CLI ✅
```

**VS Code 版本（错误）**:
```
Settings UI → claudeCodePlus.mcp.* → getMcpServersFromSettings() 
            → 返回 [] → buildMcpConfig([]) 
            → configFilePath = null → 无 --mcp-config ❌
```

**VS Code 版本（修复后）**:
```
Settings UI → claudeCodePlus.mcp.* → McpConfigurable.getAllBuiltInServers()
                                   → McpConfigurable.getCustomServers()
            → getMcpServersFromSettings() → 返回完整配置
            → buildMcpConfig() → --mcp-config 参数 → Claude CLI ✅
```

---

## 📝 修复任务列表

### Phase 1: 核心配置传递修复 [P0]

#### Task 1.1: 修复 getMcpServersFromSettings() 函数

**文件**: `vscode-extension/src/server/rsocket/agentRSocketServer.ts`

**当前实现** (第 322-326 行):
```typescript
const getMcpServersFromSettings = (): McpServerSettings[] => {
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus')
  const mcpSettings = cfg.get<{ servers?: McpServerSettings[] }>('mcp')
  return mcpSettings?.servers ?? []  // ❌ 总是返回空数组
}
```

**修复后实现**:
```typescript
import { McpConfigurable, type McpServerEntry, type CustomMcpServerConfig } from '../../ide/settings/configurables/McpConfigurable'

/**
 * 从 VS Code 设置中读取 MCP 服务器配置
 * 
 * 1. 读取所有内置 MCP 服务器配置（User Interaction, VS Code LSP, VS Code File, Context7, Terminal, Git）
 * 2. 读取自定义 MCP 服务器配置
 * 3. 转换为 McpServerSettings 格式供 buildMcpConfig() 使用
 */
const getMcpServersFromSettings = (): McpServerSettings[] => {
  const settings: McpServerSettings[] = []
  
  // 1. 读取内置服务器配置
  const builtInServers = McpConfigurable.getAllBuiltInServers()
  for (const entry of builtInServers) {
    settings.push(convertBuiltInToSettings(entry))
  }
  
  // 2. 读取自定义服务器配置
  const customServers = McpConfigurable.getCustomServers()
  for (const custom of customServers) {
    settings.push(convertCustomToSettings(custom))
  }
  
  return settings
}

/**
 * 将内置 MCP 服务器条目转换为 McpServerSettings
 */
function convertBuiltInToSettings(entry: McpServerEntry): McpServerSettings {
  // 格式化 backends：["all"] -> "All", ["claude", "codex"] -> "Claude,Codex"
  const backends = formatBackends(entry.enabledBackends)
  
  return {
    name: entry.name,
    enabled: entry.enabled,
    backends,
    level: entry.level,
    isBuiltIn: true,
    type: 'http',  // 内置服务器通过 MCP HTTP Gateway 暴露
    instructions: entry.instructions || undefined,
    instructionsClaude: entry.instructionsClaude || undefined,
    instructionsCodex: entry.instructionsCodex || undefined,
    // 内置服务器特定配置
    ...(entry.apiKey && { headers: { 'X-Context7-Api-Key': entry.apiKey } }),
  }
}

/**
 * 将自定义 MCP 服务器配置转换为 McpServerSettings
 */
function convertCustomToSettings(custom: CustomMcpServerConfig): McpServerSettings {
  const backends = formatBackends(custom.backends)
  
  return {
    name: custom.name,
    enabled: custom.enabled,
    backends,
    level: 'project',
    isBuiltIn: false,
    type: custom.config.type || 'stdio',
    url: custom.config.url,
    command: custom.config.command,
    args: custom.config.args,
    env: custom.config.env,
    headers: custom.config.headers,
    instructions: custom.instructions || undefined,
  }
}

/**
 * 格式化后端配置
 * ["all"] -> "All"
 * ["claude"] -> "Claude"
 * ["codex"] -> "Codex"
 * ["claude", "codex"] -> "Claude,Codex"
 */
function formatBackends(backends: string[]): string {
  if (!backends || backends.length === 0) return 'All'
  
  const normalized = backends.map(b => b.toLowerCase())
  if (normalized.includes('all')) return 'All'
  
  const parts: string[] = []
  if (normalized.includes('claude')) parts.push('Claude')
  if (normalized.includes('codex')) parts.push('Codex')
  
  return parts.length > 0 ? parts.join(',') : 'All'
}
```

**验证标准**:
- [ ] `getMcpServersFromSettings()` 返回非空数组
- [ ] 内置服务器正确转换
- [ ] 自定义服务器正确转换
- [ ] backends 格式正确

---

#### Task 1.2: 导出 McpConfigurable 类型

**文件**: `vscode-extension/src/ide/settings/configurables/McpConfigurable.ts`

确保以下类型被正确导出：

```typescript
export interface McpServerEntry {
  name: string
  enabled: boolean
  enabledBackends: string[]
  level: string
  configSummary: string
  isBuiltIn: boolean
  instructions: string
  instructionsClaude: string
  instructionsCodex: string
  timeout: number
  // 可选属性
  apiKey?: string
  maxOutputLines?: number
  maxOutputChars?: number
  readTimeout?: number
  disableBuiltinBash?: boolean
  disableBuiltinTools?: boolean
  allowExternal?: boolean
  externalRules?: string
  commitLanguage?: string
}

export interface CustomMcpServerConfig {
  name: string
  enabled: boolean
  backends: string[]
  config: {
    command?: string
    args?: string[]
    env?: Record<string, string>
    url?: string
    headers?: Record<string, string>
    type?: 'stdio' | 'http' | 'sse'
  }
  instructions: string
  timeout: number
}

export class McpConfigurable {
  static getAllBuiltInServers(): McpServerEntry[]
  static getCustomServers(): CustomMcpServerConfig[]
  // ... 其他方法
}
```

**验证标准**:
- [ ] 类型可以被 agentRSocketServer.ts 正确导入
- [ ] 无编译错误

---

#### Task 1.3: 更新 mcpConfigBuilder.ts 的 McpServerSettings 接口

**文件**: `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`

确保 `McpServerSettings` 接口包含所有需要的字段：

```typescript
export interface McpServerSettings {
  name: string
  enabled: boolean
  backends: string  // "All" | "Claude" | "Codex" | "Claude,Codex"
  level: string     // "builtin" | "global" | "project"
  isBuiltIn: boolean
  
  // 连接配置
  type?: 'http' | 'stdio' | 'sse'
  url?: string
  headers?: Record<string, string>
  command?: string
  args?: string[]
  env?: Record<string, string>
  
  // Instructions (System Prompts)
  instructions?: string       // 通用提示词
  instructionsClaude?: string // Claude 特定提示词
  instructionsCodex?: string  // Codex 特定提示词
  defaultInstructions?: string // 默认提示词（只读）
  
  // 内置服务器特定配置（可选）
  timeout?: number
  apiKey?: string
  maxOutputLines?: number
  maxOutputChars?: number
  disableBuiltinBash?: boolean
  disableBuiltinTools?: boolean
}
```

**验证标准**:
- [ ] 接口定义完整
- [ ] 与 convertBuiltInToSettings/convertCustomToSettings 返回类型兼容

---

### Phase 2: Context7 MCP 实现 [P1]

#### Task 2.1: 创建 Context7 MCP Provider

**新建文件**: `vscode-extension/src/ide/mcp/context7/context7McpProvider.ts`

```typescript
import { McpServerProvider } from '../mcpServerRegistry'
import { McpConfigurable } from '../../settings/configurables/McpConfigurable'

/**
 * Context7 MCP 服务器提供者
 * 
 * Context7 是一个外部 HTTP MCP 服务器，提供文档搜索功能。
 * 与其他内置 MCP 不同，Context7 不需要本地服务器实现，
 * 而是直接转发到 https://mcp.context7.com/mcp
 */
export class Context7McpServerProvider implements McpServerProvider {
  readonly name = 'context7'
  
  isEnabled(): boolean {
    return McpConfigurable.getContext7Enabled()
  }
  
  getBackends(): string[] {
    return McpConfigurable.getContext7Backends()
  }
  
  getApiKey(): string | undefined {
    const key = McpConfigurable.getContext7ApiKey()
    return key && key.trim() !== '' ? key : undefined
  }
  
  getInstructions(): string {
    return McpConfigurable.getContext7Instructions()
  }
  
  getTimeout(): number {
    return McpConfigurable.getContext7Timeout()
  }
  
  /**
   * Context7 不需要本地 MCP Server 实例
   * 配置直接通过 getMcpServersFromSettings() 传递给 buildMcpConfig()
   */
  getServer(): null {
    return null
  }
  
  async initialize(): Promise<void> {
    // Context7 无需初始化
  }
  
  async dispose(): Promise<void> {
    // Context7 无需清理
  }
}
```

#### Task 2.2: 更新 MCP 初始化逻辑

**文件**: `vscode-extension/src/ide/mcp/index.ts`

在 `initializeMcpServers()` 中添加 Context7 注册：

```typescript
import { Context7McpServerProvider } from './context7/context7McpProvider'

async function initializeMcpServers(): Promise<void> {
  const { mcpRegistry } = await import('./mcpServerRegistry')
  const settings = agentSettingsService
  
  // ... 现有的 MCP 注册代码 ...
  
  // Context7 MCP（特殊处理：不注册到 mcpRegistry，因为它是外部 HTTP 服务）
  // Context7 的配置通过 getMcpServersFromSettings() 直接传递给 buildMcpConfig()
  // 这里只需记录日志
  if (McpConfigurable.getContext7Enabled()) {
    log?.('[MCP] Context7 enabled, will be configured as external HTTP MCP')
  }
}
```

#### Task 2.3: 更新 buildMcpConfig() 处理 Context7

**文件**: `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`

确保 Context7 被正确处理为外部 HTTP MCP：

```typescript
// 在 buildMcpConfig() 中
for (const server of enabledServers) {
  // 内置服务器（通过 MCP HTTP Gateway）
  if (server.isBuiltIn) {
    // 特殊处理 Context7：它是内置配置但使用外部 URL
    if (server.name === 'Context7') {
      mcpServers[server.name] = {
        type: 'http',
        url: 'https://mcp.context7.com/mcp',
        headers: server.headers ?? {}
      }
      continue
    }
    
    // 其他内置服务器通过 MCP HTTP Gateway
    if (mcpGatewayPort) {
      const url = `http://127.0.0.1:${mcpGatewayPort}/mcp/${server.name}`
      mcpServers[server.name] = {
        type: 'http',
        url,
        headers: connectId ? { 'x-mcp-connect-id': connectId } : {}
      }
    }
    continue
  }
  
  // 外部服务器...
}
```

**验证标准**:
- [ ] Context7 启用时，配置正确生成
- [ ] API Key 正确传递到 headers
- [ ] 系统提示词正确追加

---

### Phase 3: 系统提示词传递修复 [P1]

#### Task 3.1: 更新 buildMcpConfig() 的提示词处理

**文件**: `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`

确保按后端区分的提示词正确生成：

```typescript
// 2. 生成系统提示词追加内容
let systemPromptAppendix = ''

for (const server of enabledServers) {
  let instruction: string | undefined
  
  // 优先使用后端特定提示词
  if (backend === 'claude' && server.instructionsClaude) {
    instruction = server.instructionsClaude
  } else if (backend === 'codex' && server.instructionsCodex) {
    instruction = server.instructionsCodex
  } else if (server.instructions) {
    instruction = server.instructions
  } else if (server.defaultInstructions) {
    instruction = server.defaultInstructions
  }
  
  if (instruction && instruction.trim()) {
    if (systemPromptAppendix) {
      systemPromptAppendix += '\n\n'
    }
    systemPromptAppendix += instruction
  }
}
```

#### Task 3.2: 添加默认系统提示词

**新建文件**: `vscode-extension/src/ide/mcp/defaults/mcpInstructions.ts`

从 JetBrains 版本复制默认系统提示词：

```typescript
export const McpInstructions = {
  USER_INTERACTION: `向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。...`,
  
  IDE_LSP: `使用 JetBrains IDE 索引功能进行代码搜索。比文件系统搜索更快，支持模糊匹配。...`,
  
  IDE_FILE: `通过 IDE 的 VFS 读写文件。支持项目文件、JAR/ZIP 条目、JDK 源码和 .class 文件自动反编译。...`,
  
  CONTEXT7: `使用 Context7 查询最新的编程库文档和代码示例。...`,
  
  IDE_TERMINAL: `在 IDEA 集成终端中执行命令。默认等待完成并直接返回输出。...`,
  
  GIT: `获取和管理 VCS 更改。返回文件路径、更改类型，可选包含 diff 内容。...`,
}
```

#### Task 3.3: 更新 McpConfigurable 返回默认提示词

**文件**: `vscode-extension/src/ide/settings/configurables/McpConfigurable.ts`

在 `getAllBuiltInServers()` 中添加 `defaultInstructions` 字段：

```typescript
static getAllBuiltInServers(): McpServerEntry[] {
  return [
    {
      name: BUILTIN_MCP_SERVERS.USER_INTERACTION,
      enabled: this.getUserInteractionEnabled(),
      enabledBackends: this.getUserInteractionBackends(),
      level: 'builtin',
      configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.USER_INTERACTION],
      isBuiltIn: true,
      instructions: this.getUserInteractionInstructions(),
      instructionsClaude: '',
      instructionsCodex: '',
      timeout: this.getUserInteractionTimeout(),
      defaultInstructions: McpInstructions.USER_INTERACTION,  // 添加
    },
    // ... 其他服务器
  ]
}
```

**验证标准**:
- [ ] 自定义提示词优先于默认提示词
- [ ] 后端特定提示词优先于通用提示词
- [ ] 系统提示词正确追加到 CLI

---

### Phase 4: 内置服务器名称映射 [P1]

#### Task 4.1: 定义服务器名称常量

**文件**: `vscode-extension/src/ide/mcp/constants.ts`

```typescript
/**
 * 内置 MCP 服务器名称映射
 * 
 * UI 显示名 -> MCP 服务器名（传递给 CLI）
 */
export const MCP_SERVER_NAMES = {
  // UI 名称 -> MCP 名称
  'User Interaction': 'user-interaction',
  'VS Code LSP': 'ide-lsp',
  'VS Code File': 'ide-file',
  'Context7': 'context7',
  'Terminal': 'ide-terminal',
  'Git': 'ide-git',
} as const

/**
 * 反向映射：MCP 名称 -> UI 名称
 */
export const MCP_SERVER_UI_NAMES = Object.fromEntries(
  Object.entries(MCP_SERVER_NAMES).map(([ui, mcp]) => [mcp, ui])
) as Record<string, string>
```

#### Task 4.2: 更新 convertBuiltInToSettings() 使用正确的名称

```typescript
function convertBuiltInToSettings(entry: McpServerEntry): McpServerSettings {
  // 将 UI 名称转换为 MCP 服务器名称
  const mcpName = MCP_SERVER_NAMES[entry.name as keyof typeof MCP_SERVER_NAMES] || entry.name
  
  return {
    name: mcpName,  // 使用 MCP 名称
    enabled: entry.enabled,
    backends: formatBackends(entry.enabledBackends),
    level: entry.level,
    isBuiltIn: true,
    type: 'http',
    instructions: entry.instructions || undefined,
    // ...
  }
}
```

**验证标准**:
- [ ] MCP 配置文件中使用正确的服务器名称（如 `user-interaction` 而非 `User Interaction`）
- [ ] MCP HTTP Gateway 路由正确匹配

---

### Phase 5: 集成测试 [P2]

#### Task 5.1: 验证配置传递完整链路

```typescript
// 测试用例
describe('MCP Configuration Flow', () => {
  it('should load built-in MCP servers from settings', () => {
    const servers = getMcpServersFromSettings()
    expect(servers.length).toBeGreaterThan(0)
    expect(servers.some(s => s.name === 'user-interaction')).toBe(true)
  })
  
  it('should generate correct MCP config file', () => {
    const servers = getMcpServersFromSettings()
    const result = buildMcpConfig(servers, 'claude', { mcpGatewayPort: 54321 })
    expect(result.configFilePath).not.toBeNull()
  })
  
  it('should pass config to Claude CLI', async () => {
    // 验证 --mcp-config 参数被正确传递
  })
})
```

#### Task 5.2: 端到端测试

1. 启动插件
2. 打开 MCP 设置界面
3. 启用 Context7 MCP
4. 发送一条需要 MCP 工具的消息
5. 验证 MCP 工具被正确调用

**验证标准**:
- [ ] 设置界面的配置变更实时生效
- [ ] Claude CLI 日志显示 MCP 服务器已注册
- [ ] MCP 工具可以被正确调用

---

## 📊 任务优先级总结

| 优先级 | 任务 | 预估时间 | 依赖 |
|--------|------|---------|------|
| **P0** | Task 1.1: 修复 getMcpServersFromSettings() | 1h | - |
| **P0** | Task 1.2: 导出 McpConfigurable 类型 | 0.5h | - |
| **P0** | Task 1.3: 更新 McpServerSettings 接口 | 0.5h | - |
| **P1** | Task 2.1-2.3: Context7 MCP 实现 | 2h | P0 |
| **P1** | Task 3.1-3.3: 系统提示词传递修复 | 1.5h | P0 |
| **P1** | Task 4.1-4.2: 服务器名称映射 | 1h | P0 |
| **P2** | Task 5.1-5.2: 集成测试 | 2h | P1 |

**总预估时间**: 8.5h

---

## 🔧 实施顺序

1. **Phase 1** (P0): 先修复核心配置传递问题，确保现有内置 MCP 可以工作
2. **Phase 4** (P1): 修复服务器名称映射，确保 MCP HTTP Gateway 路由正确
3. **Phase 3** (P1): 修复系统提示词传递
4. **Phase 2** (P1): 实现 Context7 MCP
5. **Phase 5** (P2): 集成测试验证

---

## ⚠️ 注意事项

1. **保持向后兼容**: 修改不应破坏现有功能
2. **日志记录**: 关键步骤添加日志，便于调试
3. **错误处理**: 配置读取失败时应有合理的默认值
4. **类型安全**: 确保 TypeScript 类型定义完整

---

## 📁 涉及文件清单

### 需要修改的文件

1. `vscode-extension/src/server/rsocket/agentRSocketServer.ts`
   - 修复 `getMcpServersFromSettings()` 函数
   - 添加类型导入

2. `vscode-extension/src/ide/settings/configurables/McpConfigurable.ts`
   - 确保类型导出正确
   - 添加 `defaultInstructions` 字段

3. `vscode-extension/src/sdk/claude/mcpConfigBuilder.ts`
   - 更新 `McpServerSettings` 接口
   - 修复 Context7 特殊处理
   - 修复系统提示词生成逻辑

4. `vscode-extension/src/ide/mcp/index.ts`
   - 添加 Context7 日志

### 需要新建的文件

1. `vscode-extension/src/ide/mcp/context7/context7McpProvider.ts`
   - Context7 MCP Provider

2. `vscode-extension/src/ide/mcp/defaults/mcpInstructions.ts`
   - 默认系统提示词

3. `vscode-extension/src/ide/mcp/constants.ts`
   - 服务器名称常量

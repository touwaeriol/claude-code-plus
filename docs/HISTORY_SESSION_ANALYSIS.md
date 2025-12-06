# Claude 历史会话分析文档

## 概述

本文档详细说明如何找到、加载和过滤 Claude CLI 的历史会话数据。

## 1. 历史会话存储位置

### 1.1 目录结构

```
~/.claude/
├── projects/                              # 项目存储根目录
│   └── {project_id}/                      # 项目ID（编码后的项目路径）
│       ├── {session_id}.jsonl             # 会话历史文件
│       ├── {session_id}.jsonl             # 更多会话...
│       └── .timelines/                    # 检查点目录（可选）
│           └── {session_id}/
│               ├── timeline.json
│               └── checkpoints/
├── todos/                                 # Todo 数据目录
│   └── {session_id}.json                  # 会话关联的 todo
└── settings.json                          # 全局设置
```

### 1.2 项目 ID 编码规则

项目路径通过以下方式编码为项目 ID：

```typescript
// 示例：
// 原路径: C:\Users\16790\IdeaProjects\claude-code-plus
// 编码后: C--Users-16790-IdeaProjects-claude-code-plus

function encodeProjectPath(path: string): string {
  return path.replace(/[\\/:]/g, '-').replace(/^-+/, '')
}
```

### 1.3 本项目的历史会话位置

```
C:\Users\16790\.claude\projects\C--Users-16790-IdeaProjects-claude-code-plus\
```

---

## 2. JSONL 文件格式

### 2.1 基本结构

每个 `.jsonl` 文件包含一个会话的完整消息历史，每行是一个 JSON 对象。

### 2.2 消息类型

#### 2.2.1 用户消息

```json
{
  "type": "user",
  "message": {
    "role": "user",
    "content": "用户输入的内容"
  },
  "uuid": "65931c59-9bbf-4855-9dfd-55dac7c54d33",
  "parentUuid": "c99558d6-790f-4adb-8b10-0a08da749da7",
  "sessionId": "29e43f43-1b46-4d1d-9aba-4ee2e600bce7",
  "timestamp": "2025-12-03T09:03:23.995Z",
  "cwd": "C:\\Users\\16790\\IdeaProjects\\claude-code-plus",
  "version": "2.0.57",
  "gitBranch": "feat/vue-frontend-migration",
  "userType": "external",
  "isSidechain": false
}
```

#### 2.2.2 助手消息

```json
{
  "type": "assistant",
  "message": {
    "role": "assistant",
    "content": "Claude 的回复内容..."
  },
  "uuid": "...",
  "parentUuid": "...",
  "sessionId": "...",
  "timestamp": "...",
  "costUSD": 0.0123,
  "durationMs": 5432,
  "model": "claude-sonnet-4-20250514"
}
```

#### 2.2.3 文件快照

```json
{
  "type": "file-history-snapshot",
  "messageId": "...",
  "snapshot": {
    "messageId": "...",
    "trackedFileBackups": {},
    "timestamp": "2025-12-03T09:03:22.772Z"
  },
  "isSnapshotUpdate": false
}
```

#### 2.2.4 摘要消息

```json
{
  "type": "summary",
  "summary": "会话摘要内容...",
  "leafUuid": "...",
  "timestamp": "..."
}
```

### 2.3 关键字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `type` | string | 消息类型：`user`, `assistant`, `file-history-snapshot`, `summary` |
| `message.role` | string | 角色：`user` 或 `assistant` |
| `message.content` | string | 消息内容 |
| `uuid` | string | 消息唯一标识 |
| `parentUuid` | string \| null | 父消息 UUID（用于消息树结构） |
| `sessionId` | string | 会话 ID |
| `timestamp` | string | ISO 8601 时间戳 |
| `cwd` | string | 工作目录 |
| `version` | string | Claude CLI 版本 |
| `gitBranch` | string | 当前 Git 分支 |
| `userType` | string | 用户类型：`external`, `internal` |
| `isSidechain` | boolean | **是否为侧链（子代理标识）** |
| `isMeta` | boolean | 是否为元数据消息 |
| `costUSD` | number | 消息成本（美元） |
| `durationMs` | number | 处理时长（毫秒） |
| `model` | string | 使用的模型 |

---

## 3. 子代理会话识别

### 3.1 识别方法

子代理会话可以通过以下方式识别：

#### 方法 1：`isSidechain` 字段

```typescript
interface JsonlEntry {
  isSidechain?: boolean  // true = 子代理会话
}
```

#### 方法 2：`parentUuid` 链路分析

主会话的第一条消息 `parentUuid` 为 `null`，子代理会话的消息链路会指向父会话。

#### 方法 3：`userType` 字段

```typescript
// external = 用户直接交互的主会话
// internal = 系统内部（可能是子代理）
userType: 'external' | 'internal'
```

#### 方法 4：会话内容分析

子代理会话通常：
- 消息数量较少
- 包含特定的任务指令格式
- 没有用户交互历史

### 3.2 过滤子代理会话的代码示例

```typescript
interface SessionEntry {
  type: string
  sessionId: string
  parentUuid?: string | null
  isSidechain?: boolean
  userType?: string
  message?: {
    role: string
    content: string
  }
}

/**
 * 判断会话是否为子代理会话
 */
function isSubagentSession(entries: SessionEntry[]): boolean {
  // 检查是否有 isSidechain = true 的条目
  const hasSidechain = entries.some(e => e.isSidechain === true)
  if (hasSidechain) return true

  // 检查是否所有用户消息都是内部类型
  const userEntries = entries.filter(e => e.type === 'user')
  const allInternal = userEntries.length > 0 &&
    userEntries.every(e => e.userType === 'internal')
  if (allInternal) return true

  // 检查首条消息的特征
  const firstUserMessage = entries.find(
    e => e.type === 'user' && e.message?.role === 'user'
  )
  if (firstUserMessage?.message?.content) {
    const content = firstUserMessage.message.content
    // 子代理通常有特定的提示格式
    if (content.includes('Task tool') ||
        content.includes('subagent') ||
        content.startsWith('You are a')) {
      return true
    }
  }

  return false
}

/**
 * 过滤出主会话列表
 */
async function filterMainSessions(
  sessionFiles: string[]
): Promise<string[]> {
  const mainSessions: string[] = []

  for (const file of sessionFiles) {
    const entries = await parseJsonlFile(file)
    if (!isSubagentSession(entries)) {
      mainSessions.push(file)
    }
  }

  return mainSessions
}
```

---

## 4. 加载历史会话

### 4.1 加载流程

```
1. 获取项目 ID
   ↓
2. 扫描 ~/.claude/projects/{project_id}/ 目录
   ↓
3. 列出所有 .jsonl 文件
   ↓
4. 过滤子代理会话
   ↓
5. 提取会话元数据（首条消息、时间戳等）
   ↓
6. 按时间排序
   ↓
7. 返回会话列表
```

### 4.2 完整实现代码

```typescript
import * as fs from 'fs'
import * as path from 'path'
import * as readline from 'readline'

// ============ 类型定义 ============

interface SessionMetadata {
  id: string                    // 会话 ID
  projectId: string             // 项目 ID
  firstMessage?: string         // 首条用户消息（摘要）
  firstMessageTimestamp?: string // 首条消息时间
  createdAt: number             // 创建时间（Unix 时间戳）
  modifiedAt: number            // 修改时间
  messageCount: number          // 消息数量
  isSubagent: boolean           // 是否为子代理会话
  filePath: string              // 文件路径
  fileSize: number              // 文件大小
  version?: string              // Claude 版本
  gitBranch?: string            // Git 分支
}

interface JsonlEntry {
  type: string
  message?: {
    role: string
    content: string
  }
  uuid?: string
  parentUuid?: string | null
  sessionId?: string
  timestamp?: string
  cwd?: string
  version?: string
  gitBranch?: string
  userType?: string
  isSidechain?: boolean
  isMeta?: boolean
}

// ============ 工具函数 ============

/**
 * 获取 Claude 配置目录
 */
function getClaudeDir(): string {
  const homeDir = process.env.HOME || process.env.USERPROFILE || ''
  return path.join(homeDir, '.claude')
}

/**
 * 编码项目路径为项目 ID
 */
function encodeProjectPath(projectPath: string): string {
  return projectPath
    .replace(/\\/g, '-')
    .replace(/\//g, '-')
    .replace(/:/g, '-')
    .replace(/^-+/, '')
}

/**
 * 解析 JSONL 文件
 */
async function parseJsonlFile(filePath: string): Promise<JsonlEntry[]> {
  const entries: JsonlEntry[] = []

  const fileStream = fs.createReadStream(filePath)
  const rl = readline.createInterface({
    input: fileStream,
    crlfDelay: Infinity
  })

  for await (const line of rl) {
    if (line.trim()) {
      try {
        const entry = JSON.parse(line) as JsonlEntry
        entries.push(entry)
      } catch (e) {
        // 跳过无效行
      }
    }
  }

  return entries
}

/**
 * 判断是否为子代理会话
 */
function isSubagentSession(entries: JsonlEntry[]): boolean {
  // 1. 检查 isSidechain 字段
  if (entries.some(e => e.isSidechain === true)) {
    return true
  }

  // 2. 检查用户类型
  const userEntries = entries.filter(e => e.type === 'user' && !e.isMeta)
  if (userEntries.length > 0 && userEntries.every(e => e.userType === 'internal')) {
    return true
  }

  // 3. 检查首条消息内容
  const firstUserMessage = entries.find(
    e => e.type === 'user' &&
         e.message?.role === 'user' &&
         !e.isMeta &&
         !e.message.content.includes('Caveat:')
  )

  if (firstUserMessage?.message?.content) {
    const content = firstUserMessage.message.content
    // 子代理任务的典型开头
    const subagentPatterns = [
      /^You are a specialized agent/i,
      /^Task:/i,
      /This is a sub-task/i,
      /\[subagent\]/i
    ]
    if (subagentPatterns.some(p => p.test(content))) {
      return true
    }
  }

  return false
}

/**
 * 提取会话元数据
 */
async function extractSessionMetadata(
  filePath: string,
  projectId: string
): Promise<SessionMetadata | null> {
  const stat = fs.statSync(filePath)
  const sessionId = path.basename(filePath, '.jsonl')

  // 空文件跳过
  if (stat.size === 0) {
    return null
  }

  const entries = await parseJsonlFile(filePath)

  // 提取首条用户消息（排除系统消息）
  const firstUserEntry = entries.find(
    e => e.type === 'user' &&
         e.message?.role === 'user' &&
         !e.isMeta &&
         !e.message.content.includes('Caveat:') &&
         !e.message.content.includes('<command-')
  )

  // 计算消息数量（只计算实际的用户和助手消息）
  const messageCount = entries.filter(
    e => (e.type === 'user' || e.type === 'assistant') && !e.isMeta
  ).length

  // 获取版本和分支信息
  const firstEntry = entries.find(e => e.version)

  return {
    id: sessionId,
    projectId,
    firstMessage: firstUserEntry?.message?.content?.slice(0, 200),
    firstMessageTimestamp: firstUserEntry?.timestamp,
    createdAt: Math.floor(stat.birthtimeMs),
    modifiedAt: Math.floor(stat.mtimeMs),
    messageCount,
    isSubagent: isSubagentSession(entries),
    filePath,
    fileSize: stat.size,
    version: firstEntry?.version,
    gitBranch: firstEntry?.gitBranch
  }
}

// ============ 主要 API ============

/**
 * 获取项目的所有历史会话
 *
 * @param projectPath 项目路径
 * @param includeSubagents 是否包含子代理会话（默认 false）
 * @returns 会话元数据列表（按修改时间降序）
 */
export async function getProjectSessions(
  projectPath: string,
  includeSubagents: boolean = false
): Promise<SessionMetadata[]> {
  const claudeDir = getClaudeDir()
  const projectId = encodeProjectPath(projectPath)
  const projectDir = path.join(claudeDir, 'projects', projectId)

  // 检查目录是否存在
  if (!fs.existsSync(projectDir)) {
    return []
  }

  // 扫描 .jsonl 文件
  const files = fs.readdirSync(projectDir)
    .filter(f => f.endsWith('.jsonl'))
    .map(f => path.join(projectDir, f))

  // 提取元数据
  const sessions: SessionMetadata[] = []
  for (const file of files) {
    const metadata = await extractSessionMetadata(file, projectId)
    if (metadata) {
      // 过滤子代理会话
      if (includeSubagents || !metadata.isSubagent) {
        sessions.push(metadata)
      }
    }
  }

  // 按修改时间降序排序
  sessions.sort((a, b) => b.modifiedAt - a.modifiedAt)

  return sessions
}

/**
 * 加载会话的完整消息历史
 *
 * @param sessionId 会话 ID
 * @param projectPath 项目路径
 * @returns 消息列表
 */
export async function loadSessionHistory(
  sessionId: string,
  projectPath: string
): Promise<JsonlEntry[]> {
  const claudeDir = getClaudeDir()
  const projectId = encodeProjectPath(projectPath)
  const sessionFile = path.join(
    claudeDir, 'projects', projectId, `${sessionId}.jsonl`
  )

  if (!fs.existsSync(sessionFile)) {
    throw new Error(`Session file not found: ${sessionFile}`)
  }

  return parseJsonlFile(sessionFile)
}

/**
 * 恢复会话（获取可用于 resume 的消息格式）
 *
 * @param sessionId 会话 ID
 * @param projectPath 项目路径
 * @returns 可用于恢复的消息数组
 */
export async function getResumableMessages(
  sessionId: string,
  projectPath: string
): Promise<Array<{ role: string; content: string }>> {
  const entries = await loadSessionHistory(sessionId, projectPath)

  // 只提取用户和助手的实际消息
  return entries
    .filter(e =>
      (e.type === 'user' || e.type === 'assistant') &&
      e.message?.role &&
      e.message?.content &&
      !e.isMeta
    )
    .map(e => ({
      role: e.message!.role,
      content: e.message!.content
    }))
}
```

---

## 5. 前端集成建议

### 5.1 会话列表组件

```typescript
// 在 Vue 组件中使用
interface HistorySession {
  id: string
  name: string              // 从 firstMessage 截取
  timestamp: number
  messageCount: number
  isSubagent: boolean
}

// 转换函数
function toHistorySession(meta: SessionMetadata): HistorySession {
  return {
    id: meta.id,
    name: meta.firstMessage?.slice(0, 50) || `会话 ${meta.id.slice(-8)}`,
    timestamp: meta.modifiedAt,
    messageCount: meta.messageCount,
    isSubagent: meta.isSubagent
  }
}
```

### 5.2 后端 API 端点

```kotlin
// 在 Kotlin 后端添加
@Serializable
data class HistorySessionResponse(
    val sessions: List<SessionMetadata>,
    val total: Int,
    val hasMore: Boolean
)

// GET /api/history/sessions?projectPath=...&page=1&pageSize=20
suspend fun getHistorySessions(
    projectPath: String,
    page: Int = 1,
    pageSize: Int = 20,
    includeSubagents: Boolean = false
): HistorySessionResponse
```

---

## 6. 注意事项

### 6.1 性能优化

- **延迟加载**：会话列表只加载元数据，完整消息按需加载
- **分页**：大量会话时使用分页
- **缓存**：缓存已解析的元数据

### 6.2 文件大小

- 部分会话文件可能非常大（10MB+）
- 建议流式解析，不要一次性加载到内存

### 6.3 子代理过滤

- 子代理会话通常是临时的、任务导向的
- 用户一般只关心主会话
- 默认应过滤子代理会话，但提供选项显示

---

## 7. 参考资源

- opcode 项目：`external/opcode/`
  - 会话加载：`src-tauri/src/commands/claude.rs`
  - 状态管理：`src/stores/sessionStore.ts`
  - API 定义：`src/lib/api.ts`

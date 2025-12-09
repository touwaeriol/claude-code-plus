# Claude 鍘嗗彶浼氳瘽鍒嗘瀽鏂囨。

## 姒傝堪

鏈枃妗ｈ缁嗚鏄庡浣曟壘鍒般€佸姞杞藉拰杩囨护 Claude CLI 鐨勫巻鍙蹭細璇濇暟鎹€?

## 1. 鍘嗗彶浼氳瘽瀛樺偍浣嶇疆

### 1.1 鐩綍缁撴瀯

```
~/.claude/
鈹溾攢鈹€ projects/                              # 椤圭洰瀛樺偍鏍圭洰褰?
鈹?  鈹斺攢鈹€ {project_id}/                      # 椤圭洰ID锛堢紪鐮佸悗鐨勯」鐩矾寰勶級
鈹?      鈹溾攢鈹€ {session_id}.jsonl             # 浼氳瘽鍘嗗彶鏂囦欢
鈹?      鈹溾攢鈹€ {session_id}.jsonl             # 鏇村浼氳瘽...
鈹?      鈹斺攢鈹€ .timelines/                    # 妫€鏌ョ偣鐩綍锛堝彲閫夛級
鈹?          鈹斺攢鈹€ {session_id}/
鈹?              鈹溾攢鈹€ timeline.json
鈹?              鈹斺攢鈹€ checkpoints/
鈹溾攢鈹€ todos/                                 # Todo 鏁版嵁鐩綍
鈹?  鈹斺攢鈹€ {session_id}.json                  # 浼氳瘽鍏宠仈鐨?todo
鈹斺攢鈹€ settings.json                          # 鍏ㄥ眬璁剧疆
```

### 1.2 椤圭洰 ID 缂栫爜瑙勫垯

椤圭洰璺緞閫氳繃浠ヤ笅鏂瑰紡缂栫爜涓洪」鐩?ID锛?

```typescript
// 绀轰緥锛?
// 鍘熻矾寰? C:\Users\16790\IdeaProjects\claude-code-plus
// 缂栫爜鍚? C--Users-16790-IdeaProjects-claude-code-plus

function encodeProjectPath(path: string): string {
  return path.replace(/[\\/:]/g, '-').replace(/^-+/, '')
}
```

### 1.3 鏈」鐩殑鍘嗗彶浼氳瘽浣嶇疆

```
C:\Users\16790\.claude\projects\C--Users-16790-IdeaProjects-claude-code-plus\
```

---

## 2. JSONL 鏂囦欢鏍煎紡

### 2.1 鍩烘湰缁撴瀯

姣忎釜 `.jsonl` 鏂囦欢鍖呭惈涓€涓細璇濈殑瀹屾暣娑堟伅鍘嗗彶锛屾瘡琛屾槸涓€涓?JSON 瀵硅薄銆?

### 2.2 娑堟伅绫诲瀷

#### 2.2.1 鐢ㄦ埛娑堟伅

```json
{
  "type": "user",
  "message": {
    "role": "user",
    "content": "鐢ㄦ埛杈撳叆鐨勫唴瀹?
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

#### 2.2.2 鍔╂墜娑堟伅

```json
{
  "type": "assistant",
  "message": {
    "role": "assistant",
    "content": "Claude 鐨勫洖澶嶅唴瀹?.."
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

#### 2.2.3 鏂囦欢蹇収

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

#### 2.2.4 鎽樿娑堟伅

```json
{
  "type": "summary",
  "summary": "浼氳瘽鎽樿鍐呭...",
  "leafUuid": "...",
  "timestamp": "..."
}
```

### 2.3 鍏抽敭瀛楁璇存槑

| 瀛楁 | 绫诲瀷 | 璇存槑 |
|------|------|------|
| `type` | string | 娑堟伅绫诲瀷锛歚user`, `assistant`, `file-history-snapshot`, `summary` |
| `message.role` | string | 瑙掕壊锛歚user` 鎴?`assistant` |
| `message.content` | string | 娑堟伅鍐呭 |
| `uuid` | string | 娑堟伅鍞竴鏍囪瘑 |
| `parentUuid` | string \| null | 鐖舵秷鎭?UUID锛堢敤浜庢秷鎭爲缁撴瀯锛?|
| `sessionId` | string | 浼氳瘽 ID |
| `timestamp` | string | ISO 8601 鏃堕棿鎴?|
| `cwd` | string | 宸ヤ綔鐩綍 |
| `version` | string | Claude CLI 鐗堟湰 |
| `gitBranch` | string | 褰撳墠 Git 鍒嗘敮 |
| `userType` | string | 鐢ㄦ埛绫诲瀷锛歚external`, `internal` |
| `isSidechain` | boolean | **鏄惁涓轰晶閾撅紙瀛愪唬鐞嗘爣璇嗭級** |
| `isMeta` | boolean | 鏄惁涓哄厓鏁版嵁娑堟伅 |
| `costUSD` | number | 娑堟伅鎴愭湰锛堢編鍏冿級 |
| `durationMs` | number | 澶勭悊鏃堕暱锛堟绉掞級 |
| `model` | string | 浣跨敤鐨勬ā鍨?|

---

## 3. 瀛愪唬鐞嗕細璇濊瘑鍒?

### 3.1 璇嗗埆鏂规硶

瀛愪唬鐞嗕細璇濆彲浠ラ€氳繃浠ヤ笅鏂瑰紡璇嗗埆锛?

#### 鏂规硶 1锛歚isSidechain` 瀛楁

```typescript
interface JsonlEntry {
  isSidechain?: boolean  // true = 瀛愪唬鐞嗕細璇?
}
```

#### 鏂规硶 2锛歚parentUuid` 閾捐矾鍒嗘瀽

涓讳細璇濈殑绗竴鏉℃秷鎭?`parentUuid` 涓?`null`锛屽瓙浠ｇ悊浼氳瘽鐨勬秷鎭摼璺細鎸囧悜鐖朵細璇濄€?

#### 鏂规硶 3锛歚userType` 瀛楁

```typescript
// external = 鐢ㄦ埛鐩存帴浜や簰鐨勪富浼氳瘽
// internal = 绯荤粺鍐呴儴锛堝彲鑳芥槸瀛愪唬鐞嗭級
userType: 'external' | 'internal'
```

#### 鏂规硶 4锛氫細璇濆唴瀹瑰垎鏋?

瀛愪唬鐞嗕細璇濋€氬父锛?
- 娑堟伅鏁伴噺杈冨皯
- 鍖呭惈鐗瑰畾鐨勪换鍔℃寚浠ゆ牸寮?
- 娌℃湁鐢ㄦ埛浜や簰鍘嗗彶

### 3.2 杩囨护瀛愪唬鐞嗕細璇濈殑浠ｇ爜绀轰緥

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
 * 鍒ゆ柇浼氳瘽鏄惁涓哄瓙浠ｇ悊浼氳瘽
 */
function isSubagentSession(entries: SessionEntry[]): boolean {
  // 妫€鏌ユ槸鍚︽湁 isSidechain = true 鐨勬潯鐩?
  const hasSidechain = entries.some(e => e.isSidechain === true)
  if (hasSidechain) return true

  // 妫€鏌ユ槸鍚︽墍鏈夌敤鎴锋秷鎭兘鏄唴閮ㄧ被鍨?
  const userEntries = entries.filter(e => e.type === 'user')
  const allInternal = userEntries.length > 0 &&
    userEntries.every(e => e.userType === 'internal')
  if (allInternal) return true

  // 妫€鏌ラ鏉℃秷鎭殑鐗瑰緛
  const firstUserMessage = entries.find(
    e => e.type === 'user' && e.message?.role === 'user'
  )
  if (firstUserMessage?.message?.content) {
    const content = firstUserMessage.message.content
    // 瀛愪唬鐞嗛€氬父鏈夌壒瀹氱殑鎻愮ず鏍煎紡
    if (content.includes('Task tool') ||
        content.includes('subagent') ||
        content.startsWith('You are a')) {
      return true
    }
  }

  return false
}

/**
 * 杩囨护鍑轰富浼氳瘽鍒楄〃
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

## 4. 鍔犺浇鍘嗗彶浼氳瘽

### 4.1 鍔犺浇娴佺▼

```
1. 鑾峰彇椤圭洰 ID
   鈫?
2. 鎵弿 ~/.claude/projects/{project_id}/ 鐩綍
   鈫?
3. 鍒楀嚭鎵€鏈?.jsonl 鏂囦欢
   鈫?
4. 杩囨护瀛愪唬鐞嗕細璇?
   鈫?
5. 鎻愬彇浼氳瘽鍏冩暟鎹紙棣栨潯娑堟伅銆佹椂闂存埑绛夛級
   鈫?
6. 鎸夋椂闂存帓搴?
   鈫?
7. 杩斿洖浼氳瘽鍒楄〃
```

### 4.2 瀹屾暣瀹炵幇浠ｇ爜

```typescript
import * as fs from 'fs'
import * as path from 'path'
import * as readline from 'readline'

// ============ 绫诲瀷瀹氫箟 ============

interface SessionMetadata {
  id: string                    // 浼氳瘽 ID
  projectId: string             // 椤圭洰 ID
  firstMessage?: string         // 棣栨潯鐢ㄦ埛娑堟伅锛堟憳瑕侊級
  firstMessageTimestamp?: string // 棣栨潯娑堟伅鏃堕棿
  createdAt: number             // 鍒涘缓鏃堕棿锛圲nix 鏃堕棿鎴筹級
  modifiedAt: number            // 淇敼鏃堕棿
  messageCount: number          // 娑堟伅鏁伴噺
  isSubagent: boolean           // 鏄惁涓哄瓙浠ｇ悊浼氳瘽
  filePath: string              // 鏂囦欢璺緞
  fileSize: number              // 鏂囦欢澶у皬
  version?: string              // Claude 鐗堟湰
  gitBranch?: string            // Git 鍒嗘敮
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

// ============ 宸ュ叿鍑芥暟 ============

/**
 * 鑾峰彇 Claude 閰嶇疆鐩綍
 */
function getClaudeDir(): string {
  const homeDir = process.env.HOME || process.env.USERPROFILE || ''
  return path.join(homeDir, '.claude')
}

/**
 * 缂栫爜椤圭洰璺緞涓洪」鐩?ID
 */
function encodeProjectPath(projectPath: string): string {
  return projectPath
    .replace(/\\/g, '-')
    .replace(/\//g, '-')
    .replace(/:/g, '-')
    .replace(/^-+/, '')
}

/**
 * 瑙ｆ瀽 JSONL 鏂囦欢
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
        // 璺宠繃鏃犳晥琛?
      }
    }
  }

  return entries
}

/**
 * 鍒ゆ柇鏄惁涓哄瓙浠ｇ悊浼氳瘽
 */
function isSubagentSession(entries: JsonlEntry[]): boolean {
  // 1. 妫€鏌?isSidechain 瀛楁
  if (entries.some(e => e.isSidechain === true)) {
    return true
  }

  // 2. 妫€鏌ョ敤鎴风被鍨?
  const userEntries = entries.filter(e => e.type === 'user' && !e.isMeta)
  if (userEntries.length > 0 && userEntries.every(e => e.userType === 'internal')) {
    return true
  }

  // 3. 妫€鏌ラ鏉℃秷鎭唴瀹?
  const firstUserMessage = entries.find(
    e => e.type === 'user' &&
         e.message?.role === 'user' &&
         !e.isMeta &&
         !e.message.content.includes('Caveat:')
  )

  if (firstUserMessage?.message?.content) {
    const content = firstUserMessage.message.content
    // 瀛愪唬鐞嗕换鍔＄殑鍏稿瀷寮€澶?
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
 * 鎻愬彇浼氳瘽鍏冩暟鎹?
 */
async function extractSessionMetadata(
  filePath: string,
  projectId: string
): Promise<SessionMetadata | null> {
  const stat = fs.statSync(filePath)
  const sessionId = path.basename(filePath, '.jsonl')

  // 绌烘枃浠惰烦杩?
  if (stat.size === 0) {
    return null
  }

  const entries = await parseJsonlFile(filePath)

  // 鎻愬彇棣栨潯鐢ㄦ埛娑堟伅锛堟帓闄ょ郴缁熸秷鎭級
  const firstUserEntry = entries.find(
    e => e.type === 'user' &&
         e.message?.role === 'user' &&
         !e.isMeta &&
         !e.message.content.includes('Caveat:') &&
         !e.message.content.includes('<command-')
  )

  // 璁＄畻娑堟伅鏁伴噺锛堝彧璁＄畻瀹為檯鐨勭敤鎴峰拰鍔╂墜娑堟伅锛?
  const messageCount = entries.filter(
    e => (e.type === 'user' || e.type === 'assistant') && !e.isMeta
  ).length

  // 鑾峰彇鐗堟湰鍜屽垎鏀俊鎭?
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

// ============ 涓昏 API ============

/**
 * 鑾峰彇椤圭洰鐨勬墍鏈夊巻鍙蹭細璇?
 *
 * @param projectPath 椤圭洰璺緞
 * @param includeSubagents 鏄惁鍖呭惈瀛愪唬鐞嗕細璇濓紙榛樿 false锛?
 * @returns 浼氳瘽鍏冩暟鎹垪琛紙鎸変慨鏀规椂闂撮檷搴忥級
 */
export async function getProjectSessions(
  projectPath: string,
  includeSubagents: boolean = false
): Promise<SessionMetadata[]> {
  const claudeDir = getClaudeDir()
  const projectId = encodeProjectPath(projectPath)
  const projectDir = path.join(claudeDir, 'projects', projectId)

  // 妫€鏌ョ洰褰曟槸鍚﹀瓨鍦?
  if (!fs.existsSync(projectDir)) {
    return []
  }

  // 鎵弿 .jsonl 鏂囦欢
  const files = fs.readdirSync(projectDir)
    .filter(f => f.endsWith('.jsonl'))
    .map(f => path.join(projectDir, f))

  // 鎻愬彇鍏冩暟鎹?
  const sessions: SessionMetadata[] = []
  for (const file of files) {
    const metadata = await extractSessionMetadata(file, projectId)
    if (metadata) {
      // 杩囨护瀛愪唬鐞嗕細璇?
      if (includeSubagents || !metadata.isSubagent) {
        sessions.push(metadata)
      }
    }
  }

  // 鎸変慨鏀规椂闂撮檷搴忔帓搴?
  sessions.sort((a, b) => b.modifiedAt - a.modifiedAt)

  return sessions
}

/**
 * 鍔犺浇浼氳瘽鐨勫畬鏁存秷鎭巻鍙?
 *
 * @param sessionId 浼氳瘽 ID
 * @param projectPath 椤圭洰璺緞
 * @returns 娑堟伅鍒楄〃
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
 * 鎭㈠浼氳瘽锛堣幏鍙栧彲鐢ㄤ簬 resume 鐨勬秷鎭牸寮忥級
 *
 * @param sessionId 浼氳瘽 ID
 * @param projectPath 椤圭洰璺緞
 * @returns 鍙敤浜庢仮澶嶇殑娑堟伅鏁扮粍
 */
export async function getResumableMessages(
  sessionId: string,
  projectPath: string
): Promise<Array<{ role: string; content: string }>> {
  const entries = await loadSessionHistory(sessionId, projectPath)

  // 鍙彁鍙栫敤鎴峰拰鍔╂墜鐨勫疄闄呮秷鎭?
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

## 5. 鍓嶇闆嗘垚寤鸿

### 5.1 浼氳瘽鍒楄〃缁勪欢

```typescript
// 鍦?Vue 缁勪欢涓娇鐢?
interface HistorySession {
  id: string
  name: string              // 浠?firstMessage 鎴彇
  timestamp: number
  messageCount: number
  isSubagent: boolean
}

// 杞崲鍑芥暟
function toHistorySession(meta: SessionMetadata): HistorySession {
  return {
    id: meta.id,
    name: meta.firstMessage?.slice(0, 50) || `浼氳瘽 ${meta.id.slice(-8)}`,
    timestamp: meta.modifiedAt,
    messageCount: meta.messageCount,
    isSubagent: meta.isSubagent
  }
}
```

### 5.2 后端 RPC 端点（RSocket）

- 路由：`agent.getHistorySessions`（Request-Response）
- 请求：`GetHistorySessionsRequest { int32 maxResults }`
- 响应：`GetHistorySessionsResponse { repeated HistorySession sessions }`
- 实现链路：`AiAgentRpcServiceImpl.getHistorySessions` -> `ClaudeSessionScanner.scanHistorySessions` -> `RSocketHandler.handleGetHistorySessions`
- 说明：已从 HTTP `/api/history/sessions` 迁移到 RSocket/Protobuf，前端通过 `aiAgentService.getHistorySessions` 调用，无需再保留 REST 端点。

## 6. 娉ㄦ剰浜嬮」

### 6.1 鎬ц兘浼樺寲

- **寤惰繜鍔犺浇**锛氫細璇濆垪琛ㄥ彧鍔犺浇鍏冩暟鎹紝瀹屾暣娑堟伅鎸夐渶鍔犺浇
- **鍒嗛〉**锛氬ぇ閲忎細璇濇椂浣跨敤鍒嗛〉
- **缂撳瓨**锛氱紦瀛樺凡瑙ｆ瀽鐨勫厓鏁版嵁

### 6.2 鏂囦欢澶у皬

- 閮ㄥ垎浼氳瘽鏂囦欢鍙兘闈炲父澶э紙10MB+锛?
- 寤鸿娴佸紡瑙ｆ瀽锛屼笉瑕佷竴娆℃€у姞杞藉埌鍐呭瓨

### 6.3 瀛愪唬鐞嗚繃婊?

- 瀛愪唬鐞嗕細璇濋€氬父鏄复鏃剁殑銆佷换鍔″鍚戠殑
- 鐢ㄦ埛涓€鑸彧鍏冲績涓讳細璇?
- 榛樿搴旇繃婊ゅ瓙浠ｇ悊浼氳瘽锛屼絾鎻愪緵閫夐」鏄剧ず

---

## 7. 鍙傝€冭祫婧?

- opcode 椤圭洰锛歚external/opcode/`
  - 浼氳瘽鍔犺浇锛歚src-tauri/src/commands/claude.rs`
  - 鐘舵€佺鐞嗭細`src/stores/sessionStore.ts`
  - API 瀹氫箟锛歚src/lib/api.ts`


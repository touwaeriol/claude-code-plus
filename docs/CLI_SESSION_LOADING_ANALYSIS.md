# Claude CLI 历史会话加载机制分析

> 本文档基于 claude-cli-2.0.73.js 源码分析，对比项目中的实现差异。

## 1. 概述

Claude CLI 使用**消息树结构**存储会话历史，通过 `parentUuid` 链接构建树状结构，支持会话分支。恢复会话时，CLI 会自动选择正确的分支并重建线性对话历史。

## 2. 官方 CLI 核心机制

### 2.1 JSONL 反向读取机制

CLI 使用反向读取优化，从文件末尾开始读取，快速访问最新内容：

```javascript
// 函数：IT0(A) - 反向读取 JSONL
async function* IT0(A) {
  let B = await jU9(A, "r");  // 打开文件
  try {
    let Z = (await B.stat()).size,  // 获取文件大小
        Y = "",  // 缓冲区
        J = Buffer.alloc(4096);  // 4KB 读取缓冲

    // 从文件末尾开始反向读取
    while(Z > 0) {
      let X = Math.min(4096, Z);
      Z -= X;
      await B.read(J, 0, X, Z);  // 从位置 Z 读取 X 字节

      // 将新读取的内容与之前的缓冲区拼接，按换行符分割
      let W = (J.toString("utf8", 0, X) + Y).split('\n');
      Y = W[0] || "";  // 保留第一个不完整的行

      // 从后往前 yield 每一行（实现反向读取）
      for(let K = W.length - 1; K >= 1; K--) {
        let V = W[K];
        if(V) yield V;
      }
    }
    if(Y) yield Y;  // yield 最后剩余的内容
  } finally {
    await B.close();
  }
}
```

**关键特性**：
- **反向读取**：从文件末尾向开头读取，快速访问最新消息
- **流式处理**：使用生成器逐行 yield，避免一次性加载整个文件到内存
- **4KB 块读取**：性能优化，每次读取固定大小的块

### 2.2 消息树重建机制

CLI 使用 `parentUuid` 链接构建消息树，通过回溯算法重建线性对话：

```javascript
// 函数：RWA(A, Q) - 通过 parentUuid 链重建路径
function RWA(A, Q) {
  let B = [],     // 结果数组
      G = Q;      // 当前消息节点

  // 从叶节点向根节点回溯
  while(G) {
    B.unshift(G);  // 插入到数组开头
    G = G.parentUuid ? A.get(G.parentUuid) : void 0;  // 获取父节点
  }

  return B;  // 返回从根到叶的完整路径
}
```

**工作原理**：
1. 从指定的叶节点开始
2. 通过 `parentUuid` 查找父消息
3. 将每个消息插入数组开头（保证顺序从根到叶）
4. 继续回溯直到根节点（`parentUuid` 为 null）

**消息树示例**：
```
消息树：
  Root (uuid: A, parentUuid: null)
    ├─ User1 (uuid: B, parentUuid: A)
    │   └─ Assistant1 (uuid: C, parentUuid: B)
    └─ User2 (uuid: D, parentUuid: A)  <- 另一个分支

调用 RWA(messages, C) 返回：[Root, User1, Assistant1]
```

### 2.3 JSONL 解析与消息树构建

```javascript
// 函数：Lm(A) - 加载并解析 JSONL 文件
async function Lm(A) {
  let Q = new Map,  // messages: uuid -> message
      B = new Map,  // summaries: leafUuid -> summary
      G = new Map,  // customTitles: sessionId -> title
      Z = new Map,  // tags: sessionId -> tag
      Y = new Map;  // fileHistorySnapshots: messageId -> snapshot

  try {
    let J = await ep(A);  // 读取所有 JSONL 条目

    for(let X of J) {
      // 根据条目类型分类存储
      if(X.type === "user" || X.type === "assistant" ||
         X.type === "attachment" || X.type === "system") {
        Q.set(X.uuid, X);  // 存储消息
      }
      else if(X.type === "summary" && X.leafUuid) {
        B.set(X.leafUuid, X.summary);  // 存储摘要（关联到 leafUuid）
      }
      else if(X.type === "custom-title" && X.sessionId) {
        G.set(X.sessionId, X.customTitle);  // 存储自定义标题
      }
      else if(X.type === "tag" && X.sessionId) {
        Z.set(X.sessionId, X.tag);  // 存储标签
      }
      else if(X.type === "file-history-snapshot") {
        Y.set(X.messageId, X);  // 存储文件历史快照
      }
    }
  } catch {}

  return {
    messages: Q,
    summaries: B,
    customTitles: G,
    tags: Z,
    fileHistorySnapshots: Y
  };
}
```

**JSONL 条目类型**：
| 类型 | 说明 |
|------|------|
| `user` / `assistant` | 实际的对话消息 |
| `attachment` / `system` | 附件和系统消息 |
| `summary` | 会话摘要（关联到 `leafUuid`） |
| `custom-title` | 用户设置的会话标题 |
| `tag` | 会话标签 |
| `file-history-snapshot` | 文件历史快照 |

### 2.4 会话恢复与分支选择

这是 CLI 最核心的会话恢复逻辑：

```javascript
// 函数：Nm(A) - 恢复会话并选择正确分支
async function Nm(A) {
  if(!Ok(A)) return A;  // 如果已有消息，直接返回

  let {leafUuid: Q, fullPath: B} = A;

  try {
    let {messages: G, fileHistorySnapshots: Z} = await Lm(B);

    if(G.size === 0) return A;

    // 情况1：有指定的 leafUuid
    let Y = Q ? G.get(Q) : void 0;

    if(!Y) {
      // 情况2：没有 leafUuid，自动选择最新的分支
      let X = [...G.values()],
          I = new Set(X.map((H) => H.parentUuid)),  // 所有父节点ID集合
          K = X.filter((H) => !I.has(H.uuid))       // 找到所有叶节点
               .sort((H, D) => new Date(D.timestamp).getTime() -
                                new Date(H.timestamp).getTime())[0];  // 选择最新的叶节点

      if(!K) return A;

      let V = RWA(G, K);  // 重建从根到叶的路径
      return {
        ...A,
        messages: M31(V),  // 移除内部字段（isSidechain, parentUuid）
        fileHistorySnapshots: pTA(Z, V)  // 关联文件快照
      };
    }

    // 使用指定的 leafUuid 重建路径
    let J = RWA(G, Y);
    return {
      ...A,
      messages: M31(J),
      fileHistorySnapshots: pTA(Z, J)
    };
  } catch {
    return A;
  }
}
```

**核心逻辑**：

1. **检查是否需要恢复**：如果会话已有消息（`!Ok(A)`），直接返回
2. **加载 JSONL 文件**：调用 `Lm(fullPath)` 解析所有消息和元数据
3. **选择正确的分支**：
   - **有 `leafUuid`**：直接使用指定的叶节点
   - **无 `leafUuid`**：自动选择时间戳最新的分支
4. **重建消息链**：使用 `RWA(messages, leafNode)` 从叶节点回溯到根节点

**叶节点选择算法**：
```javascript
// 找到所有叶节点
let allMessages = [...G.values()];
let allParentUuids = new Set(allMessages.map(m => m.parentUuid));
let leafNodes = allMessages.filter(m => !allParentUuids.has(m.uuid));

// 按时间戳排序，选择最新的
let latestLeaf = leafNodes.sort((a, b) => b.timestamp - a.timestamp)[0];
```

### 2.5 辅助函数

```javascript
// 从消息树中找到主分支
function m02(A) {
  let Q = [...A.values()],  // 所有消息
      B = new Set(Q.map((J) => J.parentUuid)),  // 所有父节点UUID
      G = Q.filter((J) => !B.has(J.uuid));  // 叶节点（没有子节点的消息）

  if(G.length === 0) return null;

  // 选择时间戳最新的叶节点
  let Z = G.sort((J, X) => new Date(X.timestamp).getTime() -
                           new Date(J.timestamp).getTime())[0],
      Y = RWA(A, Z);  // 重建路径

  return Y.length > 0 ? Y : null;
}

// 清理内部字段（返回给 API 时不需要）
function M31(A) {
  return A.map((Q) => {
    let {isSidechain: B, parentUuid: G, ...Z} = Q;
    return Z;  // 移除 isSidechain 和 parentUuid
  });
}

// 关联文件历史快照
function pTA(A, Q) {
  let B = [];
  for(let G of Q) {
    let Z = A.get(G.uuid);
    if(!Z) continue;

    if(!Z.isSnapshotUpdate) {
      B.push(Z.snapshot);  // 完整快照
    } else {
      // 更新现有快照
      let Y = B.findLastIndex((J) => J.messageId === Z.snapshot.messageId);
      if(Y === -1)
        B.push(Z.snapshot);
      else
        B[Y] = Z.snapshot;  // 替换
    }
  }
  return B;
}
```

## 3. 完整工作流程示例

假设有如下会话树结构：

```
sessionId: "abc-123"
消息树：
  Message1 (uuid: m1, type: user, parentUuid: null, timestamp: T1)
    ├─ Message2 (uuid: m2, type: assistant, parentUuid: m1, timestamp: T2)
    │   └─ Message3 (uuid: m3, type: user, parentUuid: m2, timestamp: T3)
    │       └─ Message4 (uuid: m4, type: assistant, parentUuid: m3, timestamp: T4)
    └─ Message5 (uuid: m5, type: user, parentUuid: m1, timestamp: T5) [分支]
        └─ Message6 (uuid: m6, type: assistant, parentUuid: m5, timestamp: T6)

JSONL 文件内容：
{"type":"user","uuid":"m1","parentUuid":null,"timestamp":"T1",...}
{"type":"assistant","uuid":"m2","parentUuid":"m1","timestamp":"T2",...}
{"type":"user","uuid":"m3","parentUuid":"m2","timestamp":"T3",...}
{"type":"assistant","uuid":"m4","parentUuid":"m3","timestamp":"T4",...}
{"type":"user","uuid":"m5","parentUuid":"m1","timestamp":"T5",...}
{"type":"assistant","uuid":"m6","parentUuid":"m5","timestamp":"T6",...}
{"type":"summary","leafUuid":"m6","summary":"用户尝试了另一个方案"}
{"type":"custom-title","sessionId":"abc-123","customTitle":"实验会话"}
```

### 恢复流程：

1. **调用 `Nm({ sessionId: "abc-123", leafUuid: undefined, fullPath: "..." })`**

2. **`Lm()` 解析 JSONL**：
   ```javascript
   messages: Map {
     m1 => {uuid: "m1", parentUuid: null, ...},
     m2 => {uuid: "m2", parentUuid: "m1", ...},
     ...
   }
   summaries: Map { m6 => "用户尝试了另一个方案" }
   customTitles: Map { "abc-123" => "实验会话" }
   ```

3. **找到叶节点**：
   ```javascript
   allParentUuids = Set(["m1", "m2", "m3", "m5"])
   leafNodes = [m4, m6]  // m4 和 m6 都不在 parentUuids 中
   latestLeaf = m6  // T6 > T4
   ```

4. **重建路径 `RWA(messages, m6)`**：
   ```javascript
   回溯过程：
   m6 (parentUuid: m5) -> m5 (parentUuid: m1) -> m1 (parentUuid: null)

   结果：[m1, m5, m6]
   ```

5. **返回结果**：
   ```javascript
   {
     sessionId: "abc-123",
     messages: [m1清理后, m5清理后, m6清理后],
     customTitle: "实验会话",
     summary: "用户尝试了另一个方案"
   }
   ```

## 4. 项目实现（已复刻官方逻辑）

### 4.1 消息树算法实现

项目已在 `HistoryJsonlLoader.kt` 中实现了与官方 CLI 完全一致的消息树算法：

```kotlin
// HistoryJsonlLoader.kt

/**
 * JSONL 条目数据结构
 */
private data class JsonlEntry(
    val uuid: String,
    val parentUuid: String?,
    val type: String,
    val timestamp: String?,
    val json: JsonObject  // 原始 JSON，用于后续转换
)

/**
 * Step 1: 解析 JSONL 文件，构建消息树
 */
private fun parseMessageTree(file: File): Map<String, JsonlEntry> {
    val messages = mutableMapOf<String, JsonlEntry>()
    file.bufferedReader().use { reader ->
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val json = parser.parseToJsonElement(line).jsonObject
            val type = json["type"]?.jsonPrimitive?.contentOrNull ?: continue
            if (type == "user" || type == "assistant") {
                val uuid = json["uuid"]?.jsonPrimitive?.contentOrNull ?: continue
                val parentUuid = json["parentUuid"]?.jsonPrimitive?.contentOrNull
                val timestamp = json["timestamp"]?.jsonPrimitive?.contentOrNull
                messages[uuid] = JsonlEntry(uuid, parentUuid, type, timestamp, json)
            }
        }
    }
    return messages
}

/**
 * Step 2: 找到所有叶节点（没有子节点的消息）
 */
private fun findLeafNodes(messages: Map<String, JsonlEntry>): List<JsonlEntry> {
    val referencedAsParent = messages.values.mapNotNull { it.parentUuid }.toSet()
    return messages.values.filter { it.uuid !in referencedAsParent }
}

/**
 * Step 3: 选择时间戳最新的叶节点
 */
private fun selectLatestLeaf(leafNodes: List<JsonlEntry>): JsonlEntry? {
    return leafNodes.maxByOrNull { entry ->
        entry.timestamp?.let { java.time.Instant.parse(it).toEpochMilli() } ?: 0L
    }
}

/**
 * Step 4: 从叶节点回溯到根节点，构建线性路径
 */
private fun buildPathFromLeaf(messages: Map<String, JsonlEntry>, leaf: JsonlEntry): List<JsonlEntry> {
    val path = mutableListOf<JsonlEntry>()
    var current: JsonlEntry? = leaf
    while (current != null) {
        path.add(0, current)  // 头部插入，保证从根到叶的顺序
        current = current.parentUuid?.let { messages[it] }
    }
    return path
}
```

### 4.2 算法流程

```
JSONL 文件:
{"type":"user","uuid":"m1","parentUuid":null,...}
{"type":"assistant","uuid":"m2","parentUuid":"m1",...}
{"type":"user","uuid":"m3","parentUuid":"m2",...}      <- 原始分支
{"type":"user","uuid":"m4","parentUuid":"m1",...}      <- 编辑重发后的新分支
{"type":"assistant","uuid":"m5","parentUuid":"m4",...}

消息树结构:
  m1 (root)
  ├─ m2 → m3  <- 旧分支
  └─ m4 → m5  <- 新分支（最新）

算法执行:
1. parseMessageTree() -> Map { m1, m2, m3, m4, m5 }
2. findLeafNodes() -> [m3, m5]  // 叶节点
3. selectLatestLeaf() -> m5     // 选择最新的
4. buildPathFromLeaf(m5) -> [m1, m4, m5]  // 回溯到根

返回结果: [m1, m4, m5]  <- 只包含最新分支
```

### 4.3 与官方 CLI 的对比

| 方面 | 官方 CLI | 项目实现 |
|------|---------|---------|
| **消息树处理** | 内部处理 `parentUuid` 链 | ✅ 已实现 |
| **分支选择** | 自动选择最新分支 | ✅ 已实现 |
| **叶节点算法** | `!parentUuids.has(uuid)` | ✅ 相同逻辑 |
| **回溯算法** | `unshift()` 头部插入 | ✅ `add(0, ...)` 相同效果 |
| **会话恢复** | 委托给 CLI | 委托给 CLI |

### 4.4 回退机制

对于早期没有 `uuid` 字段的历史文件，算法会自动回退到线性读取：

```kotlin
private fun loadWithMessageTree(file: File): List<UiStreamEvent> {
    val messages = parseMessageTree(file)
    if (messages.isEmpty()) {
        return emptyList()
    }

    val leafNodes = findLeafNodes(messages)
    if (leafNodes.isEmpty()) {
        log.warn("[History] 未找到叶节点，回退到线性读取")
        return loadLinear(file)  // 回退方案
    }
    // ...
}
```

### 4.5 会话列表扫描

项目通过 `ClaudeSessionScanner` 高效扫描会话列表：

```kotlin
// ClaudeSessionScanner.kt
object ClaudeSessionScanner {
    fun scanHistorySessions(projectPath: String, maxResults: Int, offset: Int = 0): List<SessionMetadata>

    // 从文件尾部查找 custom-title（高效）
    fun findCustomTitleFromFile(file: File): String?
}
```

### 4.6 会话恢复流程

会话恢复时，项目将 `sessionId` 传递给 CLI：

```kotlin
// SubprocessTransport.kt
private fun buildCommand(): List<String> {
    options.resume?.let { sessionId ->
        command.addAll(listOf("--resume", sessionId))
    }
}
```

CLI 内部会执行相同的消息树算法来恢复正确的分支。

## 5. 设计特点总结

### 官方 CLI 的设计特点

1. **树状结构存储，线性恢复**
   - 存储：所有消息以树状结构保存（通过 `parentUuid` 链接）
   - 恢复：选择一个分支，重建为线性消息历史

2. **`leafUuid` 的作用**
   - 标记用户最后查看的分支
   - 如果没有 `leafUuid`，自动选择时间戳最新的分支
   - 允许在多个分支之间切换

3. **反向读取优化**
   - `IT0` 从文件末尾开始读取，快速访问最新内容
   - 适合显示"最近会话"列表的场景

4. **元数据分离**
   - 消息本身 vs. 会话元数据（summary, title, tags）
   - 元数据通过专门的 JSONL 条目类型存储
   - `leafUuid` 用于关联摘要到特定分支

5. **文件快照管理**
   - 每个消息可以关联文件历史快照
   - 支持增量更新（`isSnapshotUpdate`）
   - 重建时自动合并快照历史

## 6. 已完成的改进

### 6.1 消息树算法（2024-12）

✅ **消息树算法已实现**

项目已在 `HistoryJsonlLoader.kt` 中实现了完整的消息树处理逻辑，与官方 CLI 行为一致：

- ✅ `parseMessageTree()` - 构建 uuid → message 映射
- ✅ `findLeafNodes()` - 查找叶节点
- ✅ `selectLatestLeaf()` - 选择最新分支
- ✅ `buildPathFromLeaf()` - 回溯重建线性历史
- ✅ `loadLinear()` - 回退方案（兼容旧文件）

### 6.2 SDK parentUuid 支持（编辑重发功能）（2024-12）

✅ **SDK 现已支持通过 `parentUuid` 实现编辑重发功能**

#### 官方 CLI 内部算法

通过 AST 分析，发现官方 CLI 使用以下核心函数：

**1. VH7 - 叶节点查找：**
```javascript
function VH7(A) {  // A = messages Map
  // 收集所有被引用为 parentUuid 的 uuid
  let Q = new Set([...A.values()].map(B => B.parentUuid).filter(B => B !== null));
  // 叶节点 = uuid 不在 Q 中的消息
  return [...A.values()].filter(B => !Q.has(B.uuid));
}
```

**2. RWA - 消息路径回溯：**
```javascript
function RWA(A, Q) {  // A = messages Map, Q = 叶节点
  let B = [];        // 路径
  let G = Q;         // 当前消息
  while (G) {
    B.unshift(G);    // 头部插入
    G = G.parentUuid ? A.get(G.parentUuid) : void 0;  // 跟随 parentUuid 回溯
  }
  return B;          // 从根到叶的路径
}
```

**3. insertMessageChain - 消息链插入：**
```javascript
async insertMessageChain(A, Q = false, B, G, Z) {
  // A = 消息数组
  // Q = isSidechain（是否是侧链）
  // B = agentId
  // G = parentUuid  ← 关键参数！
  // Z = teamInfo

  let Y = G ?? null;  // 使用传入的 parentUuid

  for (let W of A) {
    let K = Om(W);  // 检查是否是根消息
    let V = {
      parentUuid: K ? null : Y,  // 第一条消息使用传入的 parentUuid
      // ...其他字段
    };
    await this.appendEntry(V);
    Y = W.uuid;  // 后续消息形成链式结构
  }
}
```

#### parentUuid 的语义

**`parentUuid` 指向新消息的父消息**，可以是任何类型（user 或 assistant）：

```
编辑重发场景：
原对话: m1(user) → m2(assistant) → m3(user) → m4(assistant)

用户想编辑 m3：
- 新消息的 parentUuid 设置为 m2（m3 的父消息）
- CLI 创建新消息 m5，其 parentUuid = m2

消息树变化：
  m1 → m2 → m3 → m4  (旧分支)
       └─→ m5 → m6  (新分支，由 SDK 创建)
```

#### SDK 实现

**1. CLI Patch (`003-parent-uuid.js`)：**

修改 CLI 以读取用户消息中的 `parentUuid` 字段：
```javascript
// 修改前（原始 Q2A 函数）
async function Q2A(A, Q) {
  let B = f02(A);
  return await dR().insertMessageChain(B, !1, void 0, void 0, Q);
  //                                              ^^^^^^^^ 始终为 void 0
}

// 修改后（增强版）
async function Q2A(A, Q) {
  let __parentUuid = A[0]?.parentUuid || A[0]?.parent_uuid || void 0;
  let B = f02(A);
  return await dR().insertMessageChain(B, !1, void 0, __parentUuid, Q);
  //                                              ^^^^^^^^^^^^ 使用用户传入的值
}
```

**2. SDK 数据结构 (`ContentBlocks.kt`)：**
```kotlin
@Serializable
data class StreamJsonUserMessage(
    val type: String = "user",
    val message: UserMessagePayload,
    @SerialName("session_id")
    val sessionId: String = "default",
    @SerialName("parent_tool_use_id")
    val parentToolUseId: String? = null,
    // 新增：编辑重发支持
    val parentUuid: String? = null
)
```

**3. 使用示例：**
```kotlin
// 编辑重发消息
val editedMessage = StreamJsonUserMessage(
    message = UserMessagePayload("新的消息内容"),
    sessionId = currentSessionId,
    parentUuid = "m2"  // 指向被编辑消息的父消息
)
```

#### 与历史加载的一致性

我们的 `HistoryJsonlLoader` 实现与官方 CLI 完全一致：

| 算法 | 官方 CLI | 项目实现 |
|------|---------|---------|
| 叶节点查找 | `VH7`: `!Q.has(B.uuid)` | `findLeafNodes`: `uuid !in referencedAsParent` |
| 路径回溯 | `RWA`: `B.unshift(G)` | `buildPathFromLeaf`: `path.add(0, current)` |
| parentUuid 跟随 | `A.get(G.parentUuid)` | `messages[it.parentUuid]` |

### 6.3 CLI Resume 消息加载流程（AST 分析）（2024-12）

通过 AST 分析发现 CLI 的 `Nm` 函数是 resume 会话时加载消息的核心：

```javascript
// CLI 函数 Nm - Resume 加载核心
async function Nm(A) {
  let { leafUuid: Q, fullPath: B } = A;

  // 1. 加载 JSONL 到 Map
  let { messages: G } = await Lm(B);
  if (G.size === 0) return A;

  // 2. 如果有 leafUuid，使用它定位分支
  let Y = Q ? G.get(Q) : void 0;

  if (!Y) {
    // 3. 没有 leafUuid → 自动选择最新分支
    let X = [...G.values()];
    let I = new Set(X.map(H => H.parentUuid));  // 收集所有 parentUuid

    // 找叶节点 + 按时间戳排序 + 选最新
    let K = X.filter(H => !I.has(H.uuid))
             .sort((H, D) => new Date(D.timestamp).getTime() - new Date(H.timestamp).getTime())[0];

    // 4. 从叶节点回溯到根
    let V = RWA(G, K);
    return { ...A, messages: M31(V) };
  }

  // 有 leafUuid 时直接用它回溯
  let J = RWA(G, Y);
  return { ...A, messages: M31(J) };
}
```

#### 详细对比

| 步骤 | CLI (`Nm`) | 我们的实现 (`HistoryJsonlLoader`) | 状态 |
|------|-----------|----------------------------------|------|
| 1. 解析 JSONL | `await Lm(B)` → Map | `parseMessageTree()` → Map | ✅ 一致 |
| 2. 找叶节点 | `!I.has(H.uuid)` | `uuid !in referencedAsParent` | ✅ 一致 |
| 3. 按时间排序选最新 | `.sort(...)[0]` | `maxByOrNull { timestamp }` | ✅ 一致 |
| 4. 回溯到根 | `RWA(G, K)` | `buildPathFromLeaf()` | ✅ 一致 |
| 5. leafUuid 支持 | ✅ 支持 | ✅ 已实现 | ✅ 一致 |

### 可能的后续改进

1. ~~**支持 `leafUuid` 参数**~~：✅ 已实现
2. **分支可视化**：在 UI 中显示会话的分支结构，让用户选择
3. **历史分支切换**：允许用户在不同分支之间切换查看

## 7. 参考文件

- 官方 CLI：`claude-agent-sdk/cli-patches/claude-cli-2.0.73.js`
- **消息树算法实现**：`ai-agent-server/src/main/kotlin/com/asakii/server/history/HistoryJsonlLoader.kt`
- 会话扫描：`claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/utils/ClaudeSessionScanner.kt`
- 参数传递：`claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/transport/SubprocessTransport.kt:470-478`
- **parentUuid Patch**：`claude-agent-sdk/cli-patches/patches/003-parent-uuid.js`
- SDK 数据结构：`claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/ContentBlocks.kt:174-186`
- AST 分析脚本：`claude-agent-sdk/cli-patches/analyze-parentUuid.js`, `analyze-history-loading.js`

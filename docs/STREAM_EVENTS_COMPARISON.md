# Stream Events 定义对比分析

## 📋 概述

本文档对比了 Anthropic Claude API 官方文档中的 Stream Event 类型定义与项目中 Kotlin 版本的实现，确保定义的完备性。

## 🔍 对比结果

### ✅ 已实现的 Stream Event 类型

根据 Anthropic API 官方文档（https://docs.anthropic.com/claude/reference/streaming），以下 Stream Event 类型已在 Kotlin SDK 中完整实现：

#### 1. Message 级别事件

| 事件类型 | Kotlin 类 | 状态 | 说明 |
|---------|----------|------|------|
| `message_start` | `MessageStartEvent` | ✅ 已实现 | 新消息开始时发送 |
| `message_delta` | `MessageDeltaEvent` | ✅ 已实现 | 消息元数据变化时发送（如 usage） |
| `message_stop` | `MessageStopEvent` | ✅ 已实现 | 消息结束时发送 |

#### 2. Content Block 级别事件

| 事件类型 | Kotlin 类 | 状态 | 说明 |
|---------|----------|------|------|
| `content_block_start` | `ContentBlockStartEvent` | ✅ 已实现 | 新内容块开始时发送 |
| `content_block_delta` | `ContentBlockDeltaEvent` | ✅ 已实现 | 内容块内容变化时发送 |
| `content_block_stop` | `ContentBlockStopEvent` | ✅ 已实现 | 内容块结束时发送 |

### ✅ 已实现的 Delta 类型

| Delta 类型 | Kotlin 类 | 状态 | 说明 |
|-----------|----------|------|------|
| `text_delta` | `TextDelta` | ✅ 已实现 | 文本内容的增量更新 |
| `input_json_delta` | `InputJsonDelta` | ✅ 已实现 | 工具输入 JSON 的增量更新 |
| `thinking_delta` | `ThinkingDelta` | ✅ 已补充 | Thinking 内容的增量更新 |

## 📝 详细说明

### Stream Event 类型

所有 Stream Event 都实现了 `StreamEventType` 接口，该接口定义了 `type: String` 字段。

#### MessageStartEvent
```kotlin
@SerialName("message_start")
data class MessageStartEvent(
    val message: JsonElement
) : StreamEventType
```

**字段说明：**
- `message`: 包含消息的完整信息（id、type、role、content、model 等）

#### MessageDeltaEvent
```kotlin
@SerialName("message_delta")
data class MessageDeltaEvent(
    val delta: JsonElement,
    val usage: JsonElement? = null
) : StreamEventType
```

**字段说明：**
- `delta`: 包含消息元数据的变化（如 stop_reason、stop_sequence）
- `usage`: 可选的 token 使用统计信息

#### MessageStopEvent
```kotlin
@SerialName("message_stop")
data class MessageStopEvent() : StreamEventType
```

**说明：** 消息结束事件，不包含额外字段。

#### ContentBlockStartEvent
```kotlin
@SerialName("content_block_start")
data class ContentBlockStartEvent(
    val index: Int,
    @SerialName("content_block")
    val contentBlock: JsonElement
) : StreamEventType
```

**字段说明：**
- `index`: 内容块在消息 content 数组中的索引
- `contentBlock`: 内容块的完整信息（type、text/thinking/tool_use 等）

#### ContentBlockDeltaEvent
```kotlin
@SerialName("content_block_delta")
data class ContentBlockDeltaEvent(
    val index: Int,
    val delta: JsonElement  // 可以是 TextDelta、InputJsonDelta 或 ThinkingDelta
) : StreamEventType
```

**字段说明：**
- `index`: 内容块在消息 content 数组中的索引
- `delta`: 增量数据，类型取决于内容块类型：
  - `TextDelta`: 文本增量
  - `InputJsonDelta`: 工具输入 JSON 增量
  - `ThinkingDelta`: Thinking 内容增量

#### ContentBlockStopEvent
```kotlin
@SerialName("content_block_stop")
data class ContentBlockStopEvent(
    val index: Int
) : StreamEventType
```

**字段说明：**
- `index`: 内容块在消息 content 数组中的索引

### Delta 类型

#### TextDelta
```kotlin
@SerialName("text_delta")
data class TextDelta(
    val text: String
)
```

**说明：** 文本内容的增量字符串。

#### InputJsonDelta
```kotlin
@SerialName("input_json_delta")
data class InputJsonDelta(
    @SerialName("partial_json")
    val partialJson: String
)
```

**说明：** 工具输入 JSON 的增量字符串。注意：`partial_json` 是增量字符串，需要累积后才能解析为完整 JSON。

#### ThinkingDelta
```kotlin
@SerialName("thinking_delta")
data class ThinkingDelta(
    val delta: String
)
```

**说明：** Thinking 内容的增量字符串。当 Claude 使用 thinking 模式时，thinking 内容会通过流式传输。

## 🔄 与 ContentBlocks 的对应关系

| ContentBlock 类型 | 对应的 Delta 类型 | 说明 |
|------------------|------------------|------|
| `TextBlock` | `TextDelta` | 文本内容块使用 text_delta |
| `ToolUseBlock` | `InputJsonDelta` | 工具使用块的输入使用 input_json_delta |
| `ThinkingBlock` | `ThinkingDelta` | Thinking 内容块使用 thinking_delta |
| `ToolResultBlock` | 无 | 工具结果块不通过流式传输 |

## ✅ 完备性检查

### Stream Event 类型完备性
- ✅ `message_start` - 已实现
- ✅ `message_delta` - 已实现
- ✅ `message_stop` - 已实现
- ✅ `content_block_start` - 已实现
- ✅ `content_block_delta` - 已实现
- ✅ `content_block_stop` - 已实现

### Delta 类型完备性
- ✅ `text_delta` - 已实现
- ✅ `input_json_delta` - 已实现
- ✅ `thinking_delta` - 已补充（2025-01-XX）

## 📚 参考文档

- [Anthropic API Streaming Reference](https://docs.anthropic.com/claude/reference/streaming)
- [Kotlin SDK StreamEvents.kt](../claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/types/StreamEvents.kt)
- [Kotlin SDK ContentBlocks.kt](../claude-code-sdk/src/main/kotlin/com/claudecodeplus/sdk/types/ContentBlocks.kt)

## 🎯 结论

**Kotlin SDK 的 Stream Event 定义已完备** ✅

所有官方文档中定义的 Stream Event 类型和 Delta 类型都已实现：
- 6 种 Stream Event 类型全部实现
- 3 种 Delta 类型全部实现（包括最新补充的 `thinking_delta`）

所有类型定义都遵循了：
- Kotlinx Serialization 的序列化规范
- Anthropic API 的官方文档规范
- 与 ContentBlocks 类型的对应关系

## 📝 更新记录

- **2025-01-XX**: 补充了 `ThinkingDelta` 类型定义，确保与 `ThinkingBlock` 的对应关系完整。


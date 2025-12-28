# Multi-Backend ToolUseDisplay 设计文档

## 概述

`ToolUseDisplay.vue` 是一个支持多后端（Claude + Codex）的工具调用显示组件，负责将不同后端的工具类型映射到统一的显示组件。

## 文件位置

```
analysis/codex-integration-analysis/frontend/src/components/tools/ToolUseDisplay.vue
```

## 设计原则

### 1. 适配器模式

该组件采用**适配器模式**，将 Codex 后端的工具格式转换为 Claude 格式，复用现有的显示组件：

```
Codex CommandExecution → 转换 → Claude Bash 格式 → BashToolDisplay
Codex FileChange      → 转换 → Claude Write/Edit 格式 → WriteToolDisplay/EditToolDisplay
Codex McpToolCall     → 转换 → MCP 格式 → GenericMcpToolDisplay
```

**优势**：
- ✅ 无需为 Codex 工具创建新的显示组件
- ✅ 保持 UI 一致性
- ✅ 减少代码重复
- ✅ 易于维护

### 2. 后端感知

组件通过 `backendType` prop 区分不同后端的工具：

```vue
<ToolUseDisplay
  :tool-call="toolCall"
  :backend-type="'codex'"
/>
```

### 3. 渐进式增强

- 默认后端为 `claude`（向后兼容）
- Claude 工具直接使用现有的 `ToolCallDisplay`
- Codex 工具通过格式转换后复用现有组件
- 未知工具显示兜底 UI

## 架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      ToolUseDisplay.vue                      │
│                    (多后端工具调度器)                         │
└────────────────┬────────────────────────────────────────────┘
                 │
     ┌───────────┴───────────┐
     │                       │
     ▼                       ▼
┌─────────┐           ┌──────────────┐
│ Claude  │           │    Codex     │
│ 工具    │           │   工具适配   │
└────┬────┘           └──────┬───────┘
     │                       │
     │                       ├─ CommandExecution → Bash 格式
     │                       ├─ FileChange → Write/Edit 格式
     │                       ├─ McpToolCall → MCP 格式
     │                       └─ Reasoning → Thinking 显示
     │                       │
     ▼                       ▼
┌──────────────────────────────────────┐
│       现有工具显示组件                │
├──────────────────────────────────────┤
│ - BashToolDisplay                    │
│ - WriteToolDisplay                   │
│ - EditToolDisplay                    │
│ - GenericMcpToolDisplay              │
│ - ...其他 Claude 工具                │
└──────────────────────────────────────┘
```

## Codex 工具类型映射

### 1. CommandExecution → Bash 显示

**Codex 输入格式**：
```typescript
{
  type: "CommandExecution",
  command: "ls -la",
  cwd: "/path/to/dir",
  timeout?: 30000
}
```

**Codex 输出格式**：
```typescript
{
  success: true,
  output: "文件列表...",
  exitCode: 0
}
```

**转换为 Claude Bash 格式**：
```typescript
{
  toolType: 'CLAUDE_BASH',
  input: {
    command: "ls -la",
    cwd: "/path/to/dir",
    timeout: 30000
  },
  result: {
    content: "文件列表...",
    is_error: false
  }
}
```

**使用组件**：`BashToolDisplay.vue`

---

### 2. FileChange (create) → Write 显示

**Codex 输入格式**：
```typescript
{
  type: "FileChange",
  operation: "create",
  path: "/path/to/file.ts",
  content: "console.log('Hello')"
}
```

**转换为 Claude Write 格式**：
```typescript
{
  toolType: 'CLAUDE_WRITE',
  input: {
    file_path: "/path/to/file.ts",
    content: "console.log('Hello')"
  },
  result: {
    content: "File created successfully",
    is_error: false
  }
}
```

**使用组件**：`WriteToolDisplay.vue`

---

### 3. FileChange (edit) → Edit 显示

**Codex 输入格式**：
```typescript
{
  type: "FileChange",
  operation: "edit",
  path: "/path/to/file.ts",
  oldContent: "console.log('Hello')",
  newContent: "console.log('Hi')",
  replaceAll: false
}
```

**转换为 Claude Edit 格式**：
```typescript
{
  toolType: 'CLAUDE_EDIT',
  input: {
    file_path: "/path/to/file.ts",
    old_string: "console.log('Hello')",
    new_string: "console.log('Hi')",
    replace_all: false
  },
  result: {
    content: "Edit applied",
    is_error: false
  }
}
```

**使用组件**：`EditToolDisplay.vue`

---

### 4. McpToolCall → MCP 显示

**Codex 输入格式**：
```typescript
{
  type: "McpToolCall",
  toolName: "excel__read",
  parameters: {
    file: "/path/to/data.xlsx",
    sheet: "Sheet1"
  }
}
```

**转换为 MCP 格式**：
```typescript
{
  toolType: 'MCP',
  toolName: "mcp__excel__read",
  input: {
    file: "/path/to/data.xlsx",
    sheet: "Sheet1"
  },
  result: {
    content: "...",
    is_error: false
  }
}
```

**使用组件**：`GenericMcpToolDisplay.vue`

---

### 5. Reasoning → Thinking 显示

**注意**：Codex 的 Reasoning 项通常应该作为 `ThinkingContent` 显示，而非 `ToolCall`。

但如果后端错误地将 Reasoning 作为工具调用发送，组件会显示一个兜底卡片：

```vue
<CompactToolCard
  :display-info="{
    icon: '🧠',
    actionType: 'Reasoning',
    primaryInfo: 'Thinking',
    status: 'success'
  }"
/>
```

**建议**：后端应将 Reasoning 映射为 `ThinkingDeltaEvent` → `ThinkingContent`。

---

## 结果格式适配

### Codex 结果格式
```typescript
{
  success: boolean,
  output?: string,
  error?: string,
  exitCode?: number
}
```

### Claude 结果格式
```typescript
{
  content: string | unknown[],
  is_error: boolean
}
```

### 转换逻辑
```typescript
function adaptCodexResultToClaudeFormat(codexResult: any) {
  if ('success' in codexResult || 'error' in codexResult) {
    const isError = codexResult.success === false || !!codexResult.error
    const content = isError
      ? (codexResult.error || 'Unknown error')
      : (codexResult.output || codexResult.result || '')

    return {
      content,
      is_error: isError
    }
  }

  return codexResult // 已经是 Claude 格式
}
```

---

## 状态指示器

组件复用 `CompactToolCard` 的状态指示系统：

| 状态 | 颜色 | 动画 | 说明 |
|------|------|------|------|
| `pending` | 绿色 | 转圈 | 执行中 |
| `success` | 绿色 | 实心点 | 成功 |
| `error` | 红色 | 实心点 | 失败 |

**后端状态映射**：
- Codex `success: true` → Claude `status: 'success'`
- Codex `success: false` → Claude `status: 'error'`
- Codex 执行中（无结果） → Claude `status: 'pending'`

---

## 集成到主项目

### Step 1: 更新 `DisplayItemRenderer.vue`

当前 `DisplayItemRenderer.vue` 使用 `ToolCallDisplay` 显示所有工具调用：

```vue
<!-- 工具调用 -->
<ToolCallDisplay
  v-else-if="item.displayType === 'toolCall'"
  :tool-call="item"
/>
```

**更新为**：

```vue
<!-- 工具调用 - 支持多后端 -->
<ToolUseDisplay
  v-else-if="item.displayType === 'toolCall'"
  :tool-call="item"
  :backend-type="currentBackendType"
/>
```

### Step 2: 提供后端上下文

在 `MessageList.vue` 或 `DisplayItemRenderer.vue` 中，从 session store 获取当前后端类型：

```vue
<script setup lang="ts">
import { useSessionStore } from '@/stores/sessionStore'

const sessionStore = useSessionStore()

// 从当前 tab 获取后端类型
const currentBackendType = computed(() => {
  const tab = sessionStore.getCurrentTab()
  return tab?.backendType || 'claude'
})
</script>
```

### Step 3: 导入组件

```vue
<script setup lang="ts">
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'
</script>
```

---

## 测试场景

### 1. Claude 工具（向后兼容）

```typescript
{
  toolType: 'CLAUDE_BASH',
  toolName: 'Bash',
  input: { command: 'ls -la' },
  result: { content: '文件列表', is_error: false }
}
```

**预期**：正常显示 Bash 工具卡片

---

### 2. Codex CommandExecution

```typescript
{
  toolType: 'CODEX_TOOL', // 或其他 Codex 工具类型
  toolName: 'CommandExecution',
  input: {
    command: 'npm install',
    cwd: '/project'
  },
  result: {
    success: true,
    output: 'added 100 packages'
  }
}
```

**预期**：显示为 Bash 风格卡片，显示命令和输出

---

### 3. Codex FileChange (create)

```typescript
{
  toolType: 'CODEX_TOOL',
  toolName: 'FileChange',
  input: {
    operation: 'create',
    path: '/src/App.vue',
    content: '<template>...</template>'
  }
}
```

**预期**：显示为 Write 风格卡片，显示文件路径和内容预览

---

### 4. Codex FileChange (edit)

```typescript
{
  toolType: 'CODEX_TOOL',
  toolName: 'FileChange',
  input: {
    operation: 'edit',
    path: '/src/utils.ts',
    oldContent: 'const foo = 1',
    newContent: 'const foo = 2'
  }
}
```

**预期**：显示为 Edit 风格卡片，点击可显示 Diff

---

## 后续优化

### 1. 专用 Codex 显示组件（可选）

如果 Codex 工具需要特殊的 UI 样式（如 Reasoning 显示），可以创建专用组件：

```
frontend/src/components/tools/codex/
  ├── CodexCommandDisplay.vue
  ├── CodexFileChangeDisplay.vue
  └── CodexReasoningDisplay.vue
```

### 2. 后端特定的图标和主题

在 `CompactToolCard` 中根据 `backendType` 调整图标和颜色：

```typescript
const toolIcon = computed(() => {
  if (props.backendType === 'codex') {
    return '⚡' // Codex 特定图标
  }
  return displayInfo.value.icon
})
```

### 3. 工具参数验证

添加运行时验证，确保 Codex 工具参数完整：

```typescript
function validateCodexCommandExecution(input: any) {
  if (!input.command) {
    console.warn('[ToolUseDisplay] Codex CommandExecution missing command')
  }
}
```

---

## 常见问题

### Q1: Codex Reasoning 为什么不显示为 ThinkingContent？

**A**: 正确的流程是：
1. Codex JSON-RPC 发送 `item/reasoning/summaryTextDelta` 通知
2. 后端映射为 `ThinkingDeltaEvent`
3. 前端转换为 `ThinkingContent`
4. 使用 `ThinkingDisplay.vue` 显示

如果 Reasoning 错误地作为 ToolCall 发送，`ToolUseDisplay` 会显示兜底 UI 并在控制台警告。

### Q2: 为什么不直接修改 Claude 工具组件支持 Codex 格式？

**A**: 保持关注点分离：
- Claude 工具组件：专注于 Claude SDK 格式
- ToolUseDisplay：负责格式适配和调度
- 便于维护和测试

### Q3: 如何处理 Codex 特有的字段（如 exitCode）？

**A**: 当前版本将额外字段忽略。如需显示，可以：
1. 扩展 Claude 工具组件的 input 类型
2. 在转换函数中保留额外字段
3. 在工具卡片的 details 区域显示

---

## 相关文件

- **主项目工具显示组件**：`frontend/src/components/tools/`
  - `BashToolDisplay.vue`
  - `WriteToolDisplay.vue`
  - `EditToolDisplay.vue`
  - `GenericMcpToolDisplay.vue`
  - `CompactToolCard.vue`

- **类型定义**：
  - `frontend/src/types/backend.ts` - 后端类型定义
  - `frontend/src/types/display.ts` - 显示项类型

- **调度器**：
  - `frontend/src/components/chat/ToolCallDisplay.vue` - Claude 工具调度器
  - `frontend/src/components/chat/DisplayItemRenderer.vue` - 顶层显示项渲染器

---

## 总结

`ToolUseDisplay.vue` 通过**适配器模式**实现了多后端工具的统一显示：

1. ✅ 支持 Claude 和 Codex 两种后端
2. ✅ 复用现有的工具显示组件
3. ✅ 向后兼容（默认 Claude）
4. ✅ 易于扩展（添加新后端工具类型）
5. ✅ 类型安全（TypeScript 类型检查）

集成到主项目后，用户可以无缝切换后端，UI 保持一致。

# Multi-Backend Tool Display Components

这个目录包含支持多后端（Claude + Codex）的工具显示组件。

## 核心组件

### ToolUseDisplay.vue

**位置**：`frontend/src/components/tools/ToolUseDisplay.vue`

**作用**：多后端工具调用的统一调度器，负责：
1. 检测后端类型（Claude / Codex）
2. 将 Codex 工具格式转换为 Claude 格式
3. 调度到对应的工具显示组件

**Props**：
```typescript
interface Props {
  toolCall: ToolCall          // 工具调用数据
  backendType?: BackendType   // 后端类型（默认 'claude'）
}
```

**使用示例**：
```vue
<ToolUseDisplay
  :tool-call="toolCall"
  :backend-type="'codex'"
/>
```

## 支持的工具类型

### Claude 工具（直接透传）

- Bash
- Read / Write / Edit / MultiEdit
- Grep / Glob
- WebSearch / WebFetch
- Task / TodoWrite
- MCP 工具
- 其他所有 Claude SDK 工具

### Codex 工具（格式转换）

| Codex 工具 | 映射到 | 显示组件 |
|-----------|--------|---------|
| CommandExecution | Claude Bash | BashToolDisplay |
| FileChange (create) | Claude Write | WriteToolDisplay |
| FileChange (edit) | Claude Edit | EditToolDisplay |
| McpToolCall | MCP | GenericMcpToolDisplay |
| Reasoning | Thinking | 兜底显示（建议作为 ThinkingContent） |

## 架构图

```
ToolUseDisplay (调度器)
    │
    ├─ isCodexBackend? ──┐
    │                     │
    │                     ├─ CommandExecution → BashToolDisplay
    │                     ├─ FileChange → Write/EditToolDisplay
    │                     ├─ McpToolCall → GenericMcpToolDisplay
    │                     └─ Reasoning → 兜底卡片
    │
    └─ isClaudeBackend? ──┐
                          │
                          └─ ToolCallDisplay (现有逻辑)
```

## 格式转换示例

### Codex CommandExecution → Claude Bash

**Codex 输入**：
```json
{
  "command": "npm install",
  "cwd": "/project"
}
```

**Codex 输出**：
```json
{
  "success": true,
  "output": "added 100 packages"
}
```

**转换为 Claude 格式**：
```json
{
  "input": {
    "command": "npm install",
    "cwd": "/project"
  },
  "result": {
    "content": "added 100 packages",
    "is_error": false
  }
}
```

## 集成到主项目

### Step 1: 更新 DisplayItemRenderer.vue

```vue
<ToolUseDisplay
  v-else-if="item.displayType === 'toolCall'"
  :tool-call="item"
  :backend-type="currentBackendType"
/>
```

### Step 2: 获取后端类型

```typescript
const sessionStore = useSessionStore()

const currentBackendType = computed(() => {
  const tab = sessionStore.getCurrentTab()
  return tab?.backendType || 'claude'
})
```

## 文档

- **设计文档**：[TOOL_USE_DISPLAY_DESIGN.md](../../TOOL_USE_DISPLAY_DESIGN.md)
- **使用示例**：[TOOL_USE_DISPLAY_EXAMPLES.md](../../TOOL_USE_DISPLAY_EXAMPLES.md)
- **实现计划**：[TODO_MULTI_BACKEND.md](../../TODO_MULTI_BACKEND.md)

## 依赖的主项目组件

- `CompactToolCard.vue` - 工具卡片基础组件
- `BashToolDisplay.vue` - Bash 工具显示
- `WriteToolDisplay.vue` - Write 工具显示
- `EditToolDisplay.vue` - Edit 工具显示
- `GenericMcpToolDisplay.vue` - MCP 工具显示
- `ToolCallDisplay.vue` - Claude 工具调度器

## 类型定义

- `frontend/src/types/backend.ts` - 后端类型
- `frontend/src/types/display.ts` - 显示项类型
- `frontend/src/constants/toolTypes.ts` - 工具类型常量

## 测试

运行测试前确保：
1. 主项目的工具显示组件可用
2. 类型定义文件已更新
3. Session store 提供 `backendType` 字段

## 常见问题

**Q: 为什么不为 Codex 工具创建专用组件？**

A: 通过格式转换复用现有组件可以：
- 保持 UI 一致性
- 减少代码重复
- 简化维护

**Q: Reasoning 为什么不作为 ThinkingContent 显示？**

A: Codex 的 Reasoning 应该在后端映射为 `ThinkingDeltaEvent`，前端转为 `ThinkingContent`。如果错误地作为 ToolCall 发送，组件会显示兜底 UI。

**Q: 如何添加新的 Codex 工具类型？**

A: 在 `ToolUseDisplay.vue` 中：
1. 添加类型判断 computed property
2. 添加格式转换函数
3. 在 template 中添加对应的 v-else-if 分支

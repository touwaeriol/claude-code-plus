# ToolUseDisplay 使用示例

本文档提供 `ToolUseDisplay.vue` 组件的完整使用示例，包括 Claude 和 Codex 两种后端的工具调用。

## 目录

1. [基础用法](#基础用法)
2. [Claude 工具示例](#claude-工具示例)
3. [Codex 工具示例](#codex-工具示例)
4. [集成到现有代码](#集成到现有代码)
5. [完整的组件示例](#完整的组件示例)

---

## 基础用法

### 导入组件

```vue
<script setup lang="ts">
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'
import type { ToolCall } from '@/types/display'
import type { BackendType } from '@/types/backend'
</script>
```

### 基本使用

```vue
<template>
  <ToolUseDisplay
    :tool-call="toolCall"
    :backend-type="backendType"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolCall } from '@/types/display'
import type { BackendType } from '@/types/backend'

const backendType = ref<BackendType>('claude')
const toolCall = ref<ToolCall>({
  id: 'tool-1',
  displayType: 'toolCall',
  toolName: 'Bash',
  toolType: 'CLAUDE_BASH',
  status: 'SUCCESS',
  startTime: Date.now(),
  input: {
    command: 'ls -la'
  },
  result: {
    content: 'total 24\ndrwxr-xr-x 5 user user 4096 ...',
    is_error: false
  },
  timestamp: Date.now()
})
</script>
```

---

## Claude 工具示例

### 1. Bash 工具

```typescript
const claudeBashTool: ToolCall = {
  id: 'bash-1',
  displayType: 'toolCall',
  toolName: 'Bash',
  toolType: 'CLAUDE_BASH',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    command: 'npm install',
    cwd: '/path/to/project',
    timeout: 30000
  },
  result: {
    content: 'added 100 packages in 5s',
    is_error: false
  },
  timestamp: 1234567890
}
```

**显示效果**：
```
🔧 Bash  npm install  📁 /path/to/project  ✅

[展开后显示]
Command: npm install
cwd: /path/to/project
timeout: 30s

Output:
added 100 packages in 5s
```

---

### 2. Write 工具

```typescript
const claudeWriteTool: ToolCall = {
  id: 'write-1',
  displayType: 'toolCall',
  toolName: 'Write',
  toolType: 'CLAUDE_WRITE',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    file_path: '/src/App.vue',
    content: `<template>
  <div>Hello World</div>
</template>

<script setup lang="ts">
console.log('App loaded')
</script>`
  },
  result: {
    content: 'File written successfully',
    is_error: false
  },
  timestamp: 1234567890
}
```

**显示效果**：
```
📝 Write  App.vue  📁 /src/  +5 lines  ✅

[展开后显示代码预览和语法高亮]
```

---

### 3. Edit 工具

```typescript
const claudeEditTool: ToolCall = {
  id: 'edit-1',
  displayType: 'toolCall',
  toolName: 'Edit',
  toolType: 'CLAUDE_EDIT',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    file_path: '/src/utils.ts',
    old_string: 'const API_URL = "http://localhost:3000"',
    new_string: 'const API_URL = "https://api.example.com"',
    replace_all: false
  },
  result: {
    content: 'Edit applied successfully',
    is_error: false
  },
  timestamp: 1234567890
}
```

**显示效果**：
```
✏️ Edit  utils.ts  📁 /src/  -1 +1  ✅

[点击后显示 Diff 视图]
```

---

### 4. Read 工具

```typescript
const claudeReadTool: ToolCall = {
  id: 'read-1',
  displayType: 'toolCall',
  toolName: 'Read',
  toolType: 'CLAUDE_READ',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    file_path: '/src/config.ts',
    offset: 10,
    limit: 20
  },
  result: {
    content: `    10→export const config = {
    11→  apiUrl: 'https://api.example.com',
    12→  timeout: 5000,
    13→  retries: 3
    14→}`,
    is_error: false
  },
  timestamp: 1234567890
}
```

**显示效果**：
```
📖 Read  config.ts  📁 /src/  5 lines  ✅

[展开后显示带行号的代码]
```

---

## Codex 工具示例

### 1. CommandExecution (转换为 Bash 显示)

```typescript
const codexCommandTool: ToolCall = {
  id: 'cmd-1',
  displayType: 'toolCall',
  toolName: 'CommandExecution',
  toolType: 'CODEX_TOOL', // 或其他 Codex 工具类型
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    type: 'CommandExecution',
    command: 'git status',
    cwd: '/workspace/project'
  },
  result: {
    success: true,
    output: `On branch main
Your branch is up to date with 'origin/main'.

nothing to commit, working tree clean`,
    exitCode: 0
  },
  timestamp: 1234567890
}
```

**内部转换**：
```typescript
// ToolUseDisplay 内部转换为 Claude Bash 格式
{
  toolType: 'CLAUDE_BASH',
  input: {
    command: 'git status',
    cwd: '/workspace/project'
  },
  result: {
    content: 'On branch main\nYour branch is up to date...',
    is_error: false
  }
}
```

**显示效果**：
```
🔧 Bash  git status  📁 /workspace/project  ✅

[展开后显示命令和输出，与 Claude Bash 工具一致]
```

---

### 2. FileChange - Create (转换为 Write 显示)

```typescript
const codexFileCreateTool: ToolCall = {
  id: 'file-1',
  displayType: 'toolCall',
  toolName: 'FileChange',
  toolType: 'CODEX_TOOL',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    type: 'FileChange',
    operation: 'create',
    path: '/src/components/NewComponent.vue',
    content: `<template>
  <div class="new-component">
    <h1>New Component</h1>
  </div>
</template>

<script setup lang="ts">
// Component logic
</script>`
  },
  result: {
    success: true,
    output: 'File created successfully'
  },
  timestamp: 1234567890
}
```

**内部转换**：
```typescript
{
  toolType: 'CLAUDE_WRITE',
  input: {
    file_path: '/src/components/NewComponent.vue',
    content: '<template>...'
  },
  result: {
    content: 'File created successfully',
    is_error: false
  }
}
```

**显示效果**：
```
📝 Write  NewComponent.vue  📁 /src/components/  +10 lines  ✅
```

---

### 3. FileChange - Edit (转换为 Edit 显示)

```typescript
const codexFileEditTool: ToolCall = {
  id: 'file-2',
  displayType: 'toolCall',
  toolName: 'FileChange',
  toolType: 'CODEX_TOOL',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    type: 'FileChange',
    operation: 'edit',
    path: '/src/store/index.ts',
    oldContent: 'const initialState = { count: 0 }',
    newContent: 'const initialState = { count: 0, user: null }',
    replaceAll: false
  },
  result: {
    success: true,
    output: 'File edited successfully'
  },
  timestamp: 1234567890
}
```

**内部转换**：
```typescript
{
  toolType: 'CLAUDE_EDIT',
  input: {
    file_path: '/src/store/index.ts',
    old_string: 'const initialState = { count: 0 }',
    new_string: 'const initialState = { count: 0, user: null }',
    replace_all: false
  },
  result: {
    content: 'File edited successfully',
    is_error: false
  }
}
```

**显示效果**：
```
✏️ Edit  index.ts  📁 /src/store/  -1 +1  ✅
```

---

### 4. McpToolCall (转换为 MCP 显示)

```typescript
const codexMcpTool: ToolCall = {
  id: 'mcp-1',
  displayType: 'toolCall',
  toolName: 'McpToolCall',
  toolType: 'CODEX_TOOL',
  status: 'SUCCESS',
  startTime: 1234567890,
  input: {
    type: 'McpToolCall',
    toolName: 'excel__read',
    parameters: {
      file: '/data/sales.xlsx',
      sheet: 'Q1 Sales',
      range: 'A1:D100'
    }
  },
  result: {
    success: true,
    output: JSON.stringify({
      rows: 100,
      columns: ['Date', 'Product', 'Quantity', 'Revenue']
    })
  },
  timestamp: 1234567890
}
```

**内部转换**：
```typescript
{
  toolType: 'MCP',
  toolName: 'mcp__excel__read',
  input: {
    file: '/data/sales.xlsx',
    sheet: 'Q1 Sales',
    range: 'A1:D100'
  },
  result: {
    content: '{"rows":100,"columns":[...]}',
    is_error: false
  }
}
```

**显示效果**：
```
📊 MCP  excel__read  📄 sales.xlsx  ✅

[展开后显示 MCP 工具参数和结果]
```

---

### 5. 错误处理示例

```typescript
const codexErrorTool: ToolCall = {
  id: 'error-1',
  displayType: 'toolCall',
  toolName: 'CommandExecution',
  toolType: 'CODEX_TOOL',
  status: 'FAILED',
  startTime: 1234567890,
  endTime: 1234567895,
  input: {
    type: 'CommandExecution',
    command: 'npm run build'
  },
  result: {
    success: false,
    error: 'Build failed: TypeScript compilation errors',
    exitCode: 1
  },
  timestamp: 1234567890
}
```

**内部转换**：
```typescript
{
  toolType: 'CLAUDE_BASH',
  input: {
    command: 'npm run build'
  },
  result: {
    content: 'Build failed: TypeScript compilation errors',
    is_error: true
  }
}
```

**显示效果**：
```
🔧 Bash  npm run build  ❌

[展开后显示]
⚠️ Error
Build failed: TypeScript compilation errors
```

---

## 集成到现有代码

### 方案 1: 更新 DisplayItemRenderer.vue（推荐）

**位置**：`frontend/src/components/chat/DisplayItemRenderer.vue`

```vue
<template>
  <div class="display-item-renderer">
    <!-- ... 其他 displayType ... -->

    <!-- 工具调用 - 使用新的 ToolUseDisplay -->
    <ToolUseDisplay
      v-else-if="item.displayType === 'toolCall'"
      :tool-call="item"
      :backend-type="currentBackendType"
    />

    <!-- ... -->
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { DisplayItem } from '@/types/display'
import { useSessionStore } from '@/stores/sessionStore'
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'

const sessionStore = useSessionStore()

interface Props {
  source: DisplayItem
}

const props = defineProps<Props>()

const item = computed(() => props.source)

// 从当前 tab 获取后端类型
const currentBackendType = computed(() => {
  const tab = sessionStore.getCurrentTab()
  return tab?.backendType || 'claude'
})
</script>
```

---

### 方案 2: 在 MessageList 中直接使用

```vue
<template>
  <div class="message-list">
    <div
      v-for="item in displayItems"
      :key="item.id"
    >
      <!-- 工具调用 -->
      <ToolUseDisplay
        v-if="item.displayType === 'toolCall'"
        :tool-call="item"
        :backend-type="backendType"
      />

      <!-- 其他类型 -->
      <DisplayItemRenderer
        v-else
        :source="item"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { DisplayItem } from '@/types/display'
import type { BackendType } from '@/types/backend'
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'
import DisplayItemRenderer from './DisplayItemRenderer.vue'

interface Props {
  displayItems: DisplayItem[]
  backendType: BackendType
}

const props = defineProps<Props>()
</script>
```

---

## 完整的组件示例

### Vue 组件示例

```vue
<template>
  <div class="tool-demo">
    <h2>Tool Display Demo</h2>

    <!-- 后端选择器 -->
    <div class="backend-selector">
      <label>
        <input
          v-model="backendType"
          type="radio"
          value="claude"
        >
        Claude
      </label>
      <label>
        <input
          v-model="backendType"
          type="radio"
          value="codex"
        >
        Codex
      </label>
    </div>

    <!-- 工具类型选择 -->
    <div class="tool-selector">
      <button @click="showClaudeBash">Claude Bash</button>
      <button @click="showClaudeWrite">Claude Write</button>
      <button @click="showClaudeEdit">Claude Edit</button>
      <button @click="showCodexCommand">Codex Command</button>
      <button @click="showCodexFileCreate">Codex File Create</button>
      <button @click="showCodexFileEdit">Codex File Edit</button>
    </div>

    <!-- 工具显示 -->
    <div class="tool-display">
      <ToolUseDisplay
        v-if="currentTool"
        :tool-call="currentTool"
        :backend-type="backendType"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { ToolCall } from '@/types/display'
import type { BackendType } from '@/types/backend'
import ToolUseDisplay from '@/components/tools/ToolUseDisplay.vue'

const backendType = ref<BackendType>('claude')
const currentTool = ref<ToolCall | null>(null)

function showClaudeBash() {
  backendType.value = 'claude'
  currentTool.value = {
    id: 'bash-1',
    displayType: 'toolCall',
    toolName: 'Bash',
    toolType: 'CLAUDE_BASH',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: { command: 'ls -la' },
    result: { content: 'total 24\n...', is_error: false },
    timestamp: Date.now()
  }
}

function showClaudeWrite() {
  backendType.value = 'claude'
  currentTool.value = {
    id: 'write-1',
    displayType: 'toolCall',
    toolName: 'Write',
    toolType: 'CLAUDE_WRITE',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: {
      file_path: '/src/App.vue',
      content: '<template>...</template>'
    },
    result: { content: 'File written', is_error: false },
    timestamp: Date.now()
  }
}

function showClaudeEdit() {
  backendType.value = 'claude'
  currentTool.value = {
    id: 'edit-1',
    displayType: 'toolCall',
    toolName: 'Edit',
    toolType: 'CLAUDE_EDIT',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: {
      file_path: '/src/utils.ts',
      old_string: 'const foo = 1',
      new_string: 'const foo = 2'
    },
    result: { content: 'Edit applied', is_error: false },
    timestamp: Date.now()
  }
}

function showCodexCommand() {
  backendType.value = 'codex'
  currentTool.value = {
    id: 'cmd-1',
    displayType: 'toolCall',
    toolName: 'CommandExecution',
    toolType: 'CODEX_TOOL',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: {
      type: 'CommandExecution',
      command: 'npm install'
    },
    result: {
      success: true,
      output: 'added 100 packages'
    },
    timestamp: Date.now()
  }
}

function showCodexFileCreate() {
  backendType.value = 'codex'
  currentTool.value = {
    id: 'file-1',
    displayType: 'toolCall',
    toolName: 'FileChange',
    toolType: 'CODEX_TOOL',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: {
      type: 'FileChange',
      operation: 'create',
      path: '/src/New.vue',
      content: '<template>...</template>'
    },
    result: { success: true, output: 'File created' },
    timestamp: Date.now()
  }
}

function showCodexFileEdit() {
  backendType.value = 'codex'
  currentTool.value = {
    id: 'file-2',
    displayType: 'toolCall',
    toolName: 'FileChange',
    toolType: 'CODEX_TOOL',
    status: 'SUCCESS',
    startTime: Date.now(),
    input: {
      type: 'FileChange',
      operation: 'edit',
      path: '/src/store.ts',
      oldContent: 'const x = 1',
      newContent: 'const x = 2'
    },
    result: { success: true, output: 'File edited' },
    timestamp: Date.now()
  }
}
</script>

<style scoped>
.tool-demo {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.backend-selector,
.tool-selector {
  margin-bottom: 20px;
}

.backend-selector label {
  margin-right: 20px;
}

.tool-selector button {
  margin-right: 10px;
  margin-bottom: 10px;
}

.tool-display {
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 20px;
  background: #f6f8fa;
}
</style>
```

---

## 测试检查清单

### Claude 工具测试

- [ ] Bash 工具正常显示命令和输出
- [ ] Write 工具显示文件路径和内容预览
- [ ] Edit 工具点击可显示 Diff
- [ ] Read 工具显示带行号的代码
- [ ] 所有工具的状态指示器正确（pending/success/error）

### Codex 工具测试

- [ ] CommandExecution 转换为 Bash 显示正确
- [ ] FileChange (create) 转换为 Write 显示正确
- [ ] FileChange (edit) 转换为 Edit 显示正确
- [ ] McpToolCall 转换为 MCP 显示正确
- [ ] Codex 错误结果正确显示为红色错误状态

### 后端切换测试

- [ ] 默认后端为 Claude（向后兼容）
- [ ] 显式设置 `backend-type="codex"` 生效
- [ ] 同一页面可以同时显示 Claude 和 Codex 工具
- [ ] 后端类型从 session store 正确读取

### UI 一致性测试

- [ ] Codex 工具使用与 Claude 工具相同的 UI 样式
- [ ] 工具卡片的展开/折叠行为一致
- [ ] 点击文件路径在 IDEA 中正确打开文件
- [ ] 点击卡片显示 Diff 功能正常

---

## 相关文档

- [TOOL_USE_DISPLAY_DESIGN.md](./TOOL_USE_DISPLAY_DESIGN.md) - 设计文档
- [TODO_MULTI_BACKEND.md](./TODO_MULTI_BACKEND.md) - 实现计划
- [主项目工具显示规范](../../docs/tool-display-specification.md)

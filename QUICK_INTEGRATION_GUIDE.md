# ToolUseDisplay 快速集成指南

本指南帮助你快速将多后端支持的 `ToolUseDisplay.vue` 组件集成到主项目中。

## 前置条件

确保以下 Phase 已完成：

- ✅ **Phase 1**: 类型系统（`frontend/src/types/backend.ts`）
- ✅ **Phase 3**: Session Store 更新（`backendType` 字段）
- ⚠️ **Phase 4.6**: 工具显示组件更新（本任务）

## 文件清单

### 新增文件

```
analysis/codex-integration-analysis/
├── frontend/src/
│   ├── components/tools/
│   │   ├── ToolUseDisplay.vue          # 主组件
│   │   └── README.md                    # 组件说明
│   └── types/
│       └── codex-tools.ts               # Codex 工具类型定义
├── TOOL_USE_DISPLAY_DESIGN.md           # 设计文档
├── TOOL_USE_DISPLAY_EXAMPLES.md         # 使用示例
└── QUICK_INTEGRATION_GUIDE.md           # 本文档
```

### 需要修改的主项目文件

```
frontend/src/
├── components/chat/
│   └── DisplayItemRenderer.vue          # 集成 ToolUseDisplay
└── composables/
    └── useSessionTab.ts                 # 确保 backendType 可用
```

---

## 集成步骤

### Step 1: 复制文件到主项目

```bash
# 从 analysis 目录复制到主项目
cp analysis/codex-integration-analysis/frontend/src/components/tools/ToolUseDisplay.vue \
   frontend/src/components/tools/

cp analysis/codex-integration-analysis/frontend/src/types/codex-tools.ts \
   frontend/src/types/
```

### Step 2: 更新 DisplayItemRenderer.vue

**位置**：`frontend/src/components/chat/DisplayItemRenderer.vue`

**原代码**：
```vue
<template>
  <div class="display-item-renderer">
    <!-- ... -->

    <!-- 工具调用 -->
    <ToolCallDisplay
      v-else-if="item.displayType === 'toolCall'"
      :tool-call="item"
    />

    <!-- ... -->
  </div>
</template>

<script setup lang="ts">
import ToolCallDisplay from './ToolCallDisplay.vue'
// ...
</script>
```

**修改为**：
```vue
<template>
  <div class="display-item-renderer">
    <!-- ... -->

    <!-- 工具调用 - 支持多后端 -->
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
// ... 其他导入 ...

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

### Step 3: 确保 Session Store 提供 backendType

**位置**：`frontend/src/stores/sessionStore.ts`

确保 `TabInfo` 接口包含 `backendType` 字段：

```typescript
interface TabInfo {
  id: string
  name: string
  backendType: BackendType  // ← 确保存在
  // ... 其他字段
}
```

确保 `getCurrentTab()` 方法返回包含 `backendType` 的对象：

```typescript
function getCurrentTab() {
  const currentTabId = currentTab.value
  const tab = tabs.value.find(t => t.id === currentTabId)
  return tab  // 应包含 backendType 字段
}
```

---

## 验证集成

### 测试 1: Claude 工具（向后兼容）

1. 启动项目
2. 创建新会话（默认 Claude 后端）
3. 发送消息触发工具调用（如 "读取 package.json"）
4. 验证工具卡片正常显示

**预期**：与之前完全一致的 UI 和行为

---

### 测试 2: Codex CommandExecution

**模拟数据**（用于测试）：

```typescript
// 在浏览器控制台或测试代码中
const codexCommandTool = {
  id: 'cmd-test-1',
  displayType: 'toolCall',
  toolName: 'CommandExecution',
  toolType: 'CODEX_TOOL',
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'CommandExecution',
    command: 'ls -la',
    cwd: '/workspace'
  },
  result: {
    success: true,
    output: 'total 24\ndrwxr-xr-x 5 user user 4096 ...'
  }
}

// 手动添加到消息列表测试显示
```

**预期**：显示为 Bash 风格卡片

---

### 测试 3: Codex FileChange (create)

**模拟数据**：

```typescript
const codexFileCreateTool = {
  id: 'file-test-1',
  displayType: 'toolCall',
  toolName: 'FileChange',
  toolType: 'CODEX_TOOL',
  status: 'SUCCESS',
  startTime: Date.now(),
  timestamp: Date.now(),
  input: {
    type: 'FileChange',
    operation: 'create',
    path: '/src/NewFile.ts',
    content: 'export const foo = "bar"'
  },
  result: {
    success: true,
    output: 'File created'
  }
}
```

**预期**：显示为 Write 风格卡片，显示文件路径和内容预览

---

### 测试 4: 后端切换

1. 创建 Claude 会话，发送消息触发工具调用
2. 创建 Codex 会话（如果后端支持），发送消息触发工具调用
3. 在两个会话之间切换

**预期**：
- Claude 会话显示 Claude 工具样式
- Codex 会话显示 Codex 工具（转换后）样式
- 切换无延迟，UI 一致

---

## 调试技巧

### 1. 检查后端类型

在浏览器控制台：

```javascript
// 检查当前 tab 的后端类型
$store.sessionStore.getCurrentTab()?.backendType

// 应输出: 'claude' 或 'codex'
```

### 2. 检查工具调用数据

在 `ToolUseDisplay.vue` 中添加调试日志：

```vue
<script setup lang="ts">
// ...

watchEffect(() => {
  console.log('[ToolUseDisplay] Backend:', props.backendType)
  console.log('[ToolUseDisplay] ToolCall:', props.toolCall)
  console.log('[ToolUseDisplay] Is Codex Command:', isCodexCommandExecution.value)
  console.log('[ToolUseDisplay] Is Codex FileChange:', isCodexFileChange.value)
})
</script>
```

### 3. 检查格式转换

在转换函数中添加日志：

```typescript
const asClaudeBashToolCall = computed(() => {
  const converted = {
    // ... 转换逻辑
  }
  console.log('[ToolUseDisplay] Converted to Bash:', converted)
  return converted
})
```

### 4. 检查组件渲染

在浏览器开发工具中：

1. 打开 Vue DevTools
2. 找到 `ToolUseDisplay` 组件
3. 检查 props 和 computed properties
4. 验证条件渲染逻辑

---

## 常见问题

### Q1: 工具显示空白

**可能原因**：
- `backendType` 未正确传递
- 工具类型判断条件不匹配
- 显示组件未正确导入

**解决方法**：
1. 检查 `DisplayItemRenderer.vue` 是否传递 `backend-type` prop
2. 在 `ToolUseDisplay.vue` 中添加调试日志
3. 检查浏览器控制台是否有错误

---

### Q2: Codex 工具显示为"未知工具"

**可能原因**：
- 工具名称格式不匹配
- `input.type` 字段缺失

**解决方法**：

检查工具数据格式：
```typescript
// 正确格式
{
  toolName: 'CommandExecution',
  input: {
    type: 'CommandExecution',  // ← 必须存在
    command: '...'
  }
}
```

如果后端返回的格式不同，调整 `ToolUseDisplay.vue` 中的判断逻辑。

---

### Q3: 点击工具卡片无反应

**可能原因**：
- `CompactToolCard` 的 `clickable` prop 为 false
- 拦截器阻止了点击

**解决方法**：
1. 检查 `hasDetails` prop 是否为 true
2. 检查 `displayInfo.status` 是否正确
3. 确认点击事件处理器存在

---

### Q4: 文件路径点击无法跳转

**可能原因**：
- 拦截器未正确配置
- IDEA 集成服务不可用

**解决方法**：
1. 检查 `toolShowInterceptor.ts` 配置
2. 确认在 IDEA 插件模式下运行
3. 查看 IDEA 日志中的错误

---

## 回滚方案

如果集成后出现问题，可以快速回滚：

### 1. 恢复 DisplayItemRenderer.vue

```bash
git checkout HEAD -- frontend/src/components/chat/DisplayItemRenderer.vue
```

### 2. 删除新增文件

```bash
rm frontend/src/components/tools/ToolUseDisplay.vue
rm frontend/src/types/codex-tools.ts
```

### 3. 重启开发服务器

```bash
cd frontend
npm run dev
```

---

## 性能优化建议

### 1. 懒加载工具显示组件

如果 Codex 工具较少使用，可以懒加载：

```vue
<script setup lang="ts">
// 动态导入
const ToolCallDisplay = defineAsyncComponent(() =>
  import('./ToolCallDisplay.vue')
)
</script>
```

### 2. 缓存转换结果

对于复杂的格式转换，使用 `computed` 缓存：

```typescript
const convertedToolCall = computed(() => {
  // 转换逻辑
})
```

### 3. 避免不必要的重渲染

使用 `v-memo` 指令（Vue 3.2+）：

```vue
<ToolUseDisplay
  v-memo="[toolCall.id, toolCall.status]"
  :tool-call="toolCall"
/>
```

---

## 下一步

集成完成后，继续 Phase 4 的其他任务：

- [ ] **4.7**: Backend Settings Dialog
- [ ] **Phase 5**: Kotlin 后端集成
- [ ] **Phase 6**: 测试与优化

---

## 相关资源

- **设计文档**：[TOOL_USE_DISPLAY_DESIGN.md](./TOOL_USE_DISPLAY_DESIGN.md)
- **使用示例**：[TOOL_USE_DISPLAY_EXAMPLES.md](./TOOL_USE_DISPLAY_EXAMPLES.md)
- **类型定义**：`frontend/src/types/codex-tools.ts`
- **实现计划**：[TODO_MULTI_BACKEND.md](./TODO_MULTI_BACKEND.md)

---

## 联系方式

如有问题或建议，请：

1. 查看 [TOOL_USE_DISPLAY_DESIGN.md](./TOOL_USE_DISPLAY_DESIGN.md) 中的"常见问题"部分
2. 检查浏览器控制台和 IDEA 日志
3. 在项目中创建 Issue

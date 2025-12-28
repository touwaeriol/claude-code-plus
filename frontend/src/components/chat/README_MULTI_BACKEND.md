# ChatHeader 多后端支持更新说明

本文档说明了 ChatHeader 及相关组件支持多后端架构的更新内容。

## 📋 更新概览

根据 `TODO_MULTI_BACKEND.md` 中 **Phase 4.4** 的要求，我们对 ChatHeader 及相关组件进行了以下更新：

### 1. ChatHeader.vue 更新

**文件位置**: `frontend/src/components/chat/ChatHeader.vue`

**主要变更**:

- ✅ 添加了 `BackendType` 和 `BackendTypes` 类型导入
- ✅ 添加了 `NewSessionDialog` 组件导入
- ✅ 添加了 `showNewSessionDialog` 状态管理
- ✅ 添加了 `currentBackendType` 计算属性（获取当前会话的后端类型）
- ✅ 添加了 `hasActiveSession` 计算属性（判断是否有活动会话）
- ✅ 在 `sessionTabList` 中添加了 `backendType` 字段
- ✅ 修改了 `handleNewSession` 函数，支持显示后端选择对话框
- ✅ 添加了 `handleNewSessionConfirm` 和 `handleNewSessionCancel` 函数

**功能说明**:

1. **新会话对话框触发逻辑**:
   - 如果当前会话正在生成或连接中 → 显示对话框让用户选择后端
   - 如果当前会话空闲 → 直接重置当前会话（保持当前后端类型）

2. **后端类型传递**:
   - 通过 `sessionTabList` 将每个 Tab 的 `backendType` 传递给 `SessionTabs` 组件
   - 通过 `currentBackendType` 传递给 `NewSessionDialog` 组件

3. **会话限制**:
   - 通过 `hasActiveSession` 判断是否禁用后端切换
   - 活动会话（连接中或已连接）不允许切换后端

---

### 2. SessionTabs.vue 更新

**文件位置**: `frontend/src/components/chat/SessionTabs.vue`

**主要变更**:

- ✅ 添加了 `BackendType` 类型导入
- ✅ 从 `backendCapabilities` 导入 `getBackendIcon` 和 `getBackendDisplayName`
- ✅ 在 `SessionTabInfo` 接口中添加了 `backendType?: BackendType` 字段
- ✅ 在模板中添加了后端图标显示
- ✅ 添加了 `getTabTooltip` 函数，显示详细的 Tooltip 信息

**UI 展示**:

每个 Tab 现在显示：
```
[后端图标] [状态指示器] [会话名称] [关闭按钮]
```

**Tooltip 内容**:
```
后端: Claude Code | 会话: session-123 | 状态: 已连接
```

**样式调整**:

- 后端图标默认透明度 0.8，激活时 1.0
- 后端图标尺寸 12px，与状态指示器保持一致
- 添加了 `.backend-icon` 样式类

---

### 3. NewSessionDialog.vue (新建)

**文件位置**: `frontend/src/components/chat/NewSessionDialog.vue`

**组件功能**:

- ✅ 显示新会话对话框
- ✅ 集成 `BackendSelector` 组件，支持后端选择
- ✅ 根据 `disabledBackendSwitch` 属性禁用或启用后端切换
- ✅ 显示警告消息（当后端切换被禁用时）
- ✅ 提供"确认"和"取消"按钮

**Props**:

```typescript
interface Props {
  disabledBackendSwitch: boolean  // 是否禁用后端切换
  currentBackend: BackendType     // 当前后端类型
}
```

**Events**:

```typescript
interface Emits {
  (e: 'confirm', backendType: BackendType): void  // 确认创建新会话
  (e: 'cancel'): void                              // 取消
}
```

**UI 设计**:

- 模态对话框，带有遮罩层
- 包含标题、主体和底部按钮区域
- 当禁用后端切换时，显示黄色警告框
- 动画效果：淡入 + 滑入

**交互逻辑**:

1. 如果 `disabledBackendSwitch` 为 `true`：
   - 后端选择器被禁用
   - 显示警告消息
   - 强制使用当前后端类型

2. 如果 `disabledBackendSwitch` 为 `false`：
   - 后端选择器可用
   - 用户可以自由选择后端

---

## 🔗 依赖关系

### 组件依赖链

```
ChatHeader.vue
├── SessionTabs.vue
│   └── BackendSelector (通过 BackendCapabilities)
└── NewSessionDialog.vue
    └── BackendSelector.vue
```

### 类型依赖

```
@/types/backend
├── BackendType
├── BackendTypes
└── (通过 backendCapabilities 服务使用)
```

### 服务依赖

```
@/services/backendCapabilities
├── getBackendIcon(type: BackendType): string
├── getBackendDisplayName(type: BackendType): string
└── getAvailableBackends(): BackendType[]
```

---

## 🎯 实现的 Phase 4.4 要求

| 要求 | 状态 | 实现位置 |
|------|------|---------|
| 在每个 Tab 上添加后端类型指示器 | ✅ | SessionTabs.vue - `.backend-icon` |
| 在新会话对话框中添加后端选择器 | ✅ | NewSessionDialog.vue - `BackendSelector` |
| 对已存在的会话禁用后端切换 | ✅ | ChatHeader.vue - `hasActiveSession` |
| 在 Tab 中显示后端图标 | ✅ | SessionTabs.vue - template |
| 添加带有后端信息的 tooltip | ✅ | SessionTabs.vue - `getTabTooltip()` |

---

## 📝 使用示例

### 在 ChatView 中使用

```vue
<template>
  <ChatHeader @toggle-history="handleToggleHistory" />
</template>

<script setup>
import ChatHeader from '@/components/chat/ChatHeader.vue'

function handleToggleHistory() {
  // 处理历史记录切换
}
</script>
```

### SessionStore 需要的扩展

```typescript
// sessionStore.ts 需要添加以下功能

interface TabInfo {
  // 现有字段...
  backendType: Ref<BackendType>  // 添加后端类型字段
}

interface CreateTabOptions {
  backendType?: BackendType  // 可选的后端类型参数
}

function createTab(options?: CreateTabOptions): Promise<void> {
  const backendType = options?.backendType ?? BackendTypes.CLAUDE
  // 创建 Tab 时使用指定的后端类型
}
```

---

## 🔮 后续工作

根据 `TODO_MULTI_BACKEND.md`，接下来需要完成：

1. **Phase 3: Frontend Store & Composable Updates**
   - 更新 `useSessionTab` composable 添加 `backendType` 字段
   - 更新 `sessionStore` 支持后端类型管理
   - 更新 `settingsStore` 添加后端配置

2. **Phase 4.5-4.7: 其他 UI 组件**
   - 更新 ChatInput.vue（集成 ThinkingConfigPanel）
   - 更新 ToolUseDisplay.vue（支持 Codex 工具类型）
   - 创建 BackendSettingsDialog.vue

3. **Phase 2: Backend Session Implementations**
   - 实现 ClaudeSession.ts
   - 实现 CodexSession.ts
   - 实现 BackendSessionFactory.ts

---

## ✅ 验收标准

- [x] 每个 Tab 显示后端图标
- [x] Tab 的 Tooltip 显示后端类型
- [x] 新会话对话框包含后端选择器
- [x] 活动会话时后端选择器被禁用
- [x] 显示禁用原因的警告消息
- [x] 所有组件使用统一的类型定义
- [x] 样式与现有 UI 一致
- [x] 支持主题切换

---

## 📚 参考文档

- `analysis/codex-integration-analysis/TODO_MULTI_BACKEND.md` - 多后端实现总览
- `frontend/src/types/backend.ts` - 后端类型定义
- `frontend/src/services/backendCapabilities.ts` - 后端能力服务
- `frontend/src/components/settings/BackendSelector.vue` - 后端选择器组件

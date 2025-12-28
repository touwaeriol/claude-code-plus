# ModelSelector 使用文档

## 概述

`ModelSelector.vue` 是一个支持多后端架构的模型选择器组件，能够根据后端类型动态过滤可用模型，并显示后端特定的模型信息。

## 功能特性

- ✅ **后端感知**: 根据 `backendType` 自动过滤可用模型
- ✅ **模型验证**: 后端切换时自动验证并修正模型选择
- ✅ **跨后端支持**: 可选支持显示其他后端的模型（标记为"不可用"）
- ✅ **详细信息**: 显示模型描述、是否支持 Thinking 等信息
- ✅ **警告提示**: 当选中的模型不属于当前后端时显示警告
- ✅ **响应式**: 监听后端类型变更，自动切换到合适的模型

## Props

| Prop | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `modelValue` | `string` | ✅ | - | 当前选中的模型 ID |
| `backendType` | `BackendType` | ❌ | - | 后端类型 (`'claude'` 或 `'codex'`) |
| `disabled` | `boolean` | ❌ | `false` | 是否禁用选择器 |
| `showDetails` | `boolean` | ❌ | `false` | 是否显示模型详细信息 |
| `allowCrossBackend` | `boolean` | ❌ | `false` | 是否允许显示其他后端的模型（标记为不可用） |

## Events

| Event | 参数 | 说明 |
|-------|------|------|
| `update:modelValue` | `(value: string)` | 模型选择变更 |
| `backend-mismatch` | `(modelId: string, expectedBackend: BackendType)` | 选中的模型不属于当前后端 |

## 使用示例

### 基础用法

```vue
<template>
  <ModelSelector
    v-model="selectedModel"
    :backend-type="currentBackend"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ModelSelector from '@/components/chat/ModelSelector.vue'
import type { BackendType } from '@/types/backend'

const currentBackend = ref<BackendType>('claude')
const selectedModel = ref('claude-sonnet-4-5-20251101')
</script>
```

### 显示详细信息

```vue
<ModelSelector
  v-model="selectedModel"
  :backend-type="currentBackend"
  :show-details="true"
/>
```

### 处理后端不匹配

```vue
<template>
  <ModelSelector
    v-model="selectedModel"
    :backend-type="currentBackend"
    @backend-mismatch="handleBackendMismatch"
  />
</template>

<script setup lang="ts">
import { useToastStore } from '@/stores/toastStore'

const toastStore = useToastStore()

function handleBackendMismatch(modelId: string, expectedBackend: BackendType) {
  toastStore.warning(
    `模型 ${modelId} 不属于当前后端，请选择 ${expectedBackend} 的模型`
  )
}
</script>
```

### 在设置对话框中使用

```vue
<template>
  <div class="settings-dialog">
    <div class="setting-group">
      <h3>后端选择</h3>
      <BackendSelector v-model="backendType" />
    </div>

    <div class="setting-group">
      <h3>模型选择</h3>
      <ModelSelector
        v-model="modelId"
        :backend-type="backendType"
        :show-details="true"
      />
    </div>

    <div class="setting-group">
      <h3>Thinking 配置</h3>
      <ThinkingConfigPanel
        v-model="thinkingConfig"
        :backend-type="backendType"
      />
    </div>
  </div>
</template>
```

### 会话创建时使用

```vue
<template>
  <div class="new-session-dialog">
    <ModelSelector
      v-model="newSessionModel"
      :backend-type="newSessionBackend"
      :disabled="isCreating"
    />

    <button @click="createSession">创建会话</button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'

const sessionStore = useSessionStore()
const newSessionBackend = ref<BackendType>('claude')
const newSessionModel = ref('claude-sonnet-4-5-20251101')
const isCreating = ref(false)

async function createSession() {
  isCreating.value = true
  try {
    await sessionStore.createTab({
      backendType: newSessionBackend.value,
      modelId: newSessionModel.value,
    })
  } finally {
    isCreating.value = false
  }
}
</script>
```

## 自动模型切换逻辑

组件会自动处理以下场景：

### 场景 1: 后端类型变更

当 `backendType` 从 `'claude'` 切换到 `'codex'` 时：
- 如果当前选中的模型不属于新后端，自动切换到新后端的默认模型
- 在控制台输出警告信息
- 发出 `update:modelValue` 事件

```typescript
// 示例：从 Claude 切换到 Codex
backendType.value = 'codex'
// => selectedModel 自动从 'claude-sonnet-4-5-20251101' 切换到 'gpt-5.1-codex-max'
```

### 场景 2: 模型不可用

当用户尝试选择不属于当前后端的模型时：
- 发出 `backend-mismatch` 事件
- 不会更新 `modelValue`
- UI 显示警告横幅

```typescript
// 用户在 Claude 后端下选择 Codex 模型
// => 触发 @backend-mismatch 事件
// => UI 显示警告
```

## 国际化

组件使用以下 i18n 键：

```typescript
{
  'settings.model': '模型',
  'settings.modelSupportsThinking': '支持 Thinking',
  'settings.supportsThinking': '支持扩展思考',
  'settings.defaultModel': '默认模型',
  'settings.modelBackendMismatch': '选中的模型不属于当前后端',
  'common.yes': '是',
  'common.no': '否',
}
```

## 样式定制

组件使用 CSS 变量进行主题适配：

```css
.model-selector {
  /* 主题变量 */
  --theme-foreground: #24292e;
  --theme-background: #ffffff;
  --theme-border: #d0d7de;
  --theme-accent: #0366d6;
  --theme-hover-background: rgba(0, 0, 0, 0.04);
  --theme-muted-foreground: #656d76;
  --theme-muted-background: #f6f8fa;
  --theme-code-background: #f6f8fa;
}
```

## 与其他组件配合

### BackendSelector

```vue
<!-- 后端选择器 + 模型选择器 -->
<BackendSelector v-model="backendType" :disabled="sessionActive" />
<ModelSelector v-model="modelId" :backend-type="backendType" />
```

### ThinkingConfigPanel

```vue
<!-- 模型选择器 + Thinking 配置 -->
<ModelSelector v-model="modelId" :backend-type="backendType" />
<ThinkingConfigPanel
  v-if="currentModelSupportsThinking"
  v-model="thinkingConfig"
  :backend-type="backendType"
/>
```

## 测试建议

### 单元测试

```typescript
import { mount } from '@vue/test-utils'
import ModelSelector from './ModelSelector.vue'

test('should filter models by backend type', () => {
  const wrapper = mount(ModelSelector, {
    props: {
      modelValue: 'claude-sonnet-4-5-20251101',
      backendType: 'claude',
    },
  })

  const options = wrapper.findAll('option')
  expect(options.every(opt => !opt.text().includes('GPT'))).toBe(true)
})

test('should auto-switch model when backend changes', async () => {
  const wrapper = mount(ModelSelector, {
    props: {
      modelValue: 'claude-sonnet-4-5-20251101',
      backendType: 'claude',
    },
  })

  await wrapper.setProps({ backendType: 'codex' })

  expect(wrapper.emitted('update:modelValue')).toBeTruthy()
  expect(wrapper.emitted('update:modelValue')[0][0]).toMatch(/gpt/)
})
```

### E2E 测试

```typescript
test('model selector should work with backend switching', async () => {
  // 1. 选择 Claude 后端
  await page.click('[data-testid="backend-selector"]')
  await page.click('[data-value="claude"]')

  // 2. 验证只显示 Claude 模型
  const claudeModels = await page.$$eval(
    '[data-testid="model-selector"] option',
    options => options.map(opt => opt.textContent)
  )
  expect(claudeModels.every(text => text.includes('Claude'))).toBe(true)

  // 3. 切换到 Codex 后端
  await page.click('[data-value="codex"]')

  // 4. 验证模型自动切换
  const selectedModel = await page.$eval(
    '[data-testid="model-selector"]',
    select => select.value
  )
  expect(selectedModel).toMatch(/gpt|o3/)
})
```

## 相关文件

- `frontend/src/types/backend.ts` - 后端类型定义
- `frontend/src/services/backendCapabilities.ts` - 后端能力服务
- `frontend/src/components/settings/BackendSelector.vue` - 后端选择器
- `frontend/src/components/settings/ThinkingConfigPanel.vue` - Thinking 配置面板

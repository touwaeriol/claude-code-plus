<template>
  <div class="backend-selector">
    <div class="selector-label">后端选择</div>
    <div class="selector-container">
      <select
        :value="modelValue"
        @change="handleChange"
        class="backend-select"
      >
        <option
          v-for="backend in availableBackends"
          :key="backend"
          :value="backend"
        >
          {{ getBackendDisplayName(backend) }}
        </option>
      </select>
    </div>

    <!-- 会话活跃时显示提示 -->
    <div v-if="isSessionActive" class="info-message">
      <span class="info-icon">ℹ️</span>
      <span>切换后端将重置当前会话</span>
    </div>

    <div v-if="modelValue" class="backend-info">
      <BackendIcon :type="modelValue" :size="24" class="backend-icon-svg" />
      <div class="backend-details">
        <div class="backend-name">{{ getBackendDisplayName(modelValue) }}</div>
        <div class="backend-description">
          {{ getBackendDescription(modelValue) }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BackendType } from '@/types/backend'
import { BackendTypes } from '@/types/backend'
import {
  getAvailableBackends,
  getBackendDisplayName,
  getCapabilities
} from '@/services/backendCapabilities'
import BackendIcon from '@/components/icons/BackendIcon.vue'

// Props
interface Props {
  modelValue: BackendType
  /** 当前会话是否活跃（用于显示提示信息） */
  isSessionActive?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isSessionActive: false,
})

// Emits
interface Emits {
  (e: 'update:modelValue', value: BackendType): void
}

const emit = defineEmits<Emits>()

// Computed
const availableBackends = computed(() => getAvailableBackends())

// Methods
function handleChange(event: Event) {
  const target = event.target as HTMLSelectElement
  emit('update:modelValue', target.value as BackendType)
}

function getBackendDescription(type: BackendType): string {
  const capabilities = getCapabilities(type)

  if (type === BackendTypes.CLAUDE) {
    return 'Anthropic Claude - 强大的代码助手，支持扩展思考和子任务'
  } else if (type === BackendTypes.CODEX) {
    return 'OpenAI Codex - 代码生成专家，支持沙盒模式'
  }

  return capabilities.displayName
}
</script>

<style scoped>
.backend-selector {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.selector-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--vscode-foreground);
  margin-bottom: 4px;
}

.selector-container {
  position: relative;
  width: 100%;
}

.backend-select {
  width: 100%;
  padding: 8px 32px 8px 12px;
  font-size: 13px;
  font-family: inherit;
  color: var(--vscode-foreground);
  background-color: var(--vscode-input-background);
  border: 1px solid var(--vscode-input-border, #3c3c3c);
  border-radius: 4px;
  cursor: pointer;
  outline: none;
  transition: all 0.2s ease;
}

.backend-select:hover:not(:disabled) {
  border-color: var(--vscode-focusBorder);
}

.backend-select:focus {
  border-color: var(--vscode-focusBorder);
  box-shadow: 0 0 0 1px var(--vscode-focusBorder);
}

.backend-select:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.backend-select option {
  padding: 8px;
  background-color: var(--vscode-dropdown-background);
  color: var(--vscode-dropdown-foreground);
}

.info-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(59, 130, 246, 0.1);
  border: 1px solid rgba(59, 130, 246, 0.3);
  border-radius: 4px;
  font-size: 12px;
  color: var(--vscode-foreground);
}

.info-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.backend-info {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 12px;
  background-color: var(--vscode-editor-background);
  border: 1px solid var(--vscode-input-border, #3c3c3c);
  border-radius: 4px;
}

.backend-icon-svg {
  flex-shrink: 0;
  margin-right: 0; /* Override default margin from component */
}

.backend-details {
  flex: 1;
  min-width: 0;
}

.backend-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--vscode-foreground);
  margin-bottom: 4px;
}

.backend-description {
  font-size: 12px;
  color: var(--vscode-descriptionForeground);
  line-height: 1.4;
}

/* Dark theme adjustments */
@media (prefers-color-scheme: dark) {
  .backend-select {
    background-color: var(--vscode-input-background, #3c3c3c);
  }

  .backend-info {
    background-color: var(--vscode-editor-background, #1e1e1e);
  }
}
</style>

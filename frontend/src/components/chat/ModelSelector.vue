<template>
  <div class="model-selector">
    <label class="selector-label">
      {{ t('settings.model') }}
      <span v-if="backendType" class="backend-badge" :class="`backend-${backendType}`">
        {{ getBackendDisplayName(backendType) }}
      </span>
    </label>

    <div class="selector-wrapper">
      <select
        :value="modelValue"
        class="model-select"
        :disabled="disabled"
        @change="handleModelChange"
      >
        <optgroup
          v-for="group in modelGroups"
          :key="group.label"
          :label="group.label"
        >
          <option
            v-for="model in group.models"
            :key="model.id"
            :value="model.id"
            :disabled="model.unavailable"
          >
            {{ model.displayName }}
            <template v-if="model.isDefault"> (默认)</template>
            <template v-if="model.unavailable"> (不可用)</template>
          </option>
        </optgroup>
      </select>

      <!-- 模型信息图标 -->
      <el-tooltip
        v-if="currentModel"
        placement="top"
        :content="getModelTooltip(currentModel)"
      >
        <button class="info-icon" type="button" tabindex="-1">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="16" x2="12" y2="12"/>
            <line x1="12" y1="8" x2="12.01" y2="8"/>
          </svg>
        </button>
      </el-tooltip>
    </div>

    <!-- 模型详细信息 -->
    <div v-if="currentModel && showDetails" class="model-details">
      <div class="detail-row">
        <span class="detail-label">{{ t('settings.modelSupportsThinking') }}:</span>
        <span class="detail-value">
          {{ currentModel.supportsThinking ? t('common.yes') : t('common.no') }}
        </span>
      </div>
      <div v-if="currentModel.description" class="detail-row">
        <span class="detail-description">{{ currentModel.description }}</span>
      </div>
    </div>

    <!-- 后端不匹配警告 -->
    <div v-if="showBackendMismatchWarning" class="warning-banner">
      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
        <line x1="12" y1="9" x2="12" y2="13"/>
        <line x1="12" y1="17" x2="12.01" y2="17"/>
      </svg>
      <span>{{ t('settings.modelBackendMismatch') }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BackendType, BackendModelInfo } from '@/types/backend'
import {
  getModels,
  getModelById,
  getBackendDisplayName,
  isValidModel,
} from '@/services/backendCapabilities'

/**
 * ModelSelector Component - 多后端模型选择器
 *
 * 支持根据后端类型动态过滤可用模型，显示后端特定的模型信息
 */

const props = defineProps<{
  /** 当前选中的模型 ID */
  modelValue: string

  /** 后端类型 - 用于过滤可用模型 */
  backendType?: BackendType

  /** 是否禁用 */
  disabled?: boolean

  /** 是否显示详细信息 */
  showDetails?: boolean

  /** 是否允许选择其他后端的模型（用于迁移场景） */
  allowCrossBackend?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'backend-mismatch', modelId: string, expectedBackend: BackendType): void
}>()

const { t } = useI18n()

/**
 * 获取当前后端可用的模型列表
 */
const availableModels = computed<BackendModelInfo[]>(() => {
  if (!props.backendType) {
    return []
  }
  return getModels(props.backendType)
})

/**
 * 获取所有后端的模型（用于显示"不可用"状态）
 */
const allModels = computed(() => {
  const allBackendModels: Array<BackendModelInfo & { backendType: BackendType; unavailable?: boolean }> = []

  // 添加当前后端的可用模型
  if (props.backendType) {
    const currentBackendModels = getModels(props.backendType)
    allBackendModels.push(...currentBackendModels.map(m => ({
      ...m,
      backendType: props.backendType!,
      unavailable: false,
    })))
  }

  // 如果允许跨后端或需要显示不可用模型，添加其他后端的模型
  if (props.allowCrossBackend) {
    const otherBackendType: BackendType = props.backendType === 'claude' ? 'codex' : 'claude'
    const otherBackendModels = getModels(otherBackendType)
    allBackendModels.push(...otherBackendModels.map(m => ({
      ...m,
      backendType: otherBackendType,
      unavailable: true, // 标记为不可用
    })))
  }

  return allBackendModels
})

/**
 * 分组显示模型（按后端类型）
 */
const modelGroups = computed(() => {
  const groups: Array<{ label: string; models: Array<BackendModelInfo & { unavailable?: boolean }> }> = []

  if (!props.backendType) {
    return groups
  }

  // 当前后端的模型
  const currentBackendLabel = getBackendDisplayName(props.backendType)
  groups.push({
    label: currentBackendLabel,
    models: allModels.value.filter(m => m.backendType === props.backendType),
  })

  // 其他后端的模型（如果允许跨后端）
  if (props.allowCrossBackend) {
    const otherBackendType: BackendType = props.backendType === 'claude' ? 'codex' : 'claude'
    const otherBackendLabel = getBackendDisplayName(otherBackendType) + ' (不可用)'
    const otherModels = allModels.value.filter(m => m.backendType === otherBackendType)

    if (otherModels.length > 0) {
      groups.push({
        label: otherBackendLabel,
        models: otherModels,
      })
    }
  }

  return groups
})

/**
 * 当前选中的模型信息
 */
const currentModel = computed<BackendModelInfo | undefined>(() => {
  if (!props.backendType || !props.modelValue) {
    return undefined
  }
  return getModelById(props.backendType, props.modelValue)
})

/**
 * 是否显示后端不匹配警告
 * 当选中的模型不属于当前后端时显示
 */
const showBackendMismatchWarning = computed(() => {
  if (!props.backendType || !props.modelValue) {
    return false
  }

  // 检查当前模型是否在当前后端的可用模型列表中
  return !isValidModel(props.backendType, props.modelValue)
})

/**
 * 获取模型的工具提示文本
 */
function getModelTooltip(model: BackendModelInfo): string {
  const parts: string[] = []

  if (model.description) {
    parts.push(model.description)
  }

  if (model.supportsThinking) {
    parts.push(t('settings.supportsThinking'))
  }

  if (model.isDefault) {
    parts.push(t('settings.defaultModel'))
  }

  return parts.join(' • ')
}

/**
 * 处理模型变更
 */
function handleModelChange(event: Event) {
  const target = event.target as HTMLSelectElement
  const newModelId = target.value

  // 检查是否是跨后端选择
  if (props.backendType && !isValidModel(props.backendType, newModelId)) {
    // 发出后端不匹配事件
    const otherBackendType: BackendType = props.backendType === 'claude' ? 'codex' : 'claude'
    emit('backend-mismatch', newModelId, otherBackendType)
    return
  }

  emit('update:modelValue', newModelId)
}

/**
 * 监听后端类型变更，自动验证并修正模型选择
 */
watch(
  () => props.backendType,
  (newBackendType) => {
    if (!newBackendType) {
      return
    }

    // 检查当前选中的模型是否在新后端中可用
    if (props.modelValue && !isValidModel(newBackendType, props.modelValue)) {
      // 选择新后端的默认模型
      const models = getModels(newBackendType)
      const defaultModel = models.find(m => m.isDefault) || models[0]

      if (defaultModel) {
        console.warn(
          `[ModelSelector] 后端切换: ${props.modelValue} 在 ${newBackendType} 中不可用，切换到默认模型 ${defaultModel.id}`
        )
        emit('update:modelValue', defaultModel.id)
      }
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.model-selector {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selector-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
}

.backend-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.backend-badge.backend-claude {
  background: rgba(130, 80, 223, 0.12);
  color: #8250df;
  border: 1px solid rgba(130, 80, 223, 0.25);
}

.backend-badge.backend-codex {
  background: rgba(16, 163, 127, 0.12);
  color: #10a37f;
  border: 1px solid rgba(16, 163, 127, 0.25);
}

.selector-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
}

.model-select {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 6px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
  font-size: 13px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s ease;
}

.model-select:hover:not(:disabled) {
  border-color: var(--theme-accent, #0366d6);
}

.model-select:focus {
  outline: none;
  border-color: var(--theme-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.12);
}

.model-select:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--theme-muted-background, #f6f8fa);
}

.model-select option:disabled {
  color: var(--theme-muted-foreground, #656d76);
  font-style: italic;
}

.info-icon {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--theme-muted-foreground, #656d76);
  cursor: help;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  transition: all 0.15s ease;
}

.info-icon:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  color: var(--theme-foreground, #24292e);
}

.model-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 8px 12px;
  background: var(--theme-code-background, #f6f8fa);
  border-radius: 6px;
  font-size: 12px;
}

.detail-row {
  display: flex;
  align-items: center;
  gap: 6px;
}

.detail-label {
  font-weight: 500;
  color: var(--theme-muted-foreground, #656d76);
}

.detail-value {
  color: var(--theme-foreground, #24292e);
}

.detail-description {
  color: var(--theme-muted-foreground, #656d76);
  line-height: 1.4;
}

.warning-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: rgba(255, 193, 7, 0.12);
  border: 1px solid rgba(255, 193, 7, 0.3);
  border-radius: 6px;
  color: #d39e00;
  font-size: 12px;
  font-weight: 500;
}

.warning-banner svg {
  flex-shrink: 0;
}
</style>

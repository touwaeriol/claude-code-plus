<template>
  <div
    class="compact-tool-card"
    :class="[
      `status-${displayInfo?.status || 'pending'}`,
      { clickable: isClickable, expanded: isExpanded }
    ]"
  >
    <!-- 单行布局 - 只在标题区域绑定点击事件 -->
    <div class="card-content" @click="handleClick">
      <!-- 图标 -->
      <span class="tool-icon">{{ displayInfo?.icon || '🔧' }}</span>

      <!-- 操作类型 -->
      <span class="action-type">{{ displayInfo?.actionType || 'Unknown' }}</span>

      <!-- 主要信息（或 loading 状态） -->
      <span v-if="displayInfo?.isInputLoading" class="primary-info loading">
        {{ t('tools.parsingParams') }}
      </span>
      <span v-else-if="displayInfo?.primaryInfo" class="primary-info">
        {{ displayInfo.primaryInfo }}
      </span>

      <!-- 次要信息（路径） -->
      <span v-if="displayInfo?.secondaryInfo" class="secondary-info">
        {{ displayInfo.secondaryInfo }}
      </span>

      <!-- 行数变化徽章 -->
      <template v-if="displayInfo">
        <span
          v-if="displayInfo.removedLines"
          class="line-changes-badge badge-remove"
        >
          -{{ displayInfo.removedLines }}
        </span>
        <span
          v-if="displayInfo.addedLines"
          class="line-changes-badge badge-add"
        >
          +{{ displayInfo.addedLines }}
        </span>
        <span
          v-if="displayInfo.readLines && !displayInfo.addedLines && !displayInfo.removedLines"
          class="line-changes-badge badge-read"
        >
          {{ displayInfo.readLines }} lines
        </span>
        <span
          v-else-if="displayInfo.lineChanges"
          class="line-changes-badge"
          :class="getBadgeClass(displayInfo.lineChanges)"
        >
          {{ displayInfo.lineChanges }}
        </span>
      </template>

      <!-- 状态指示器 -->
      <span class="status-indicator" :class="`status-${displayInfo?.status || 'pending'}`">
        <span v-if="displayInfo?.isInputLoading || displayInfo?.status === 'pending' || !displayInfo?.status" class="spinner" />
        <span v-else class="dot" />
      </span>
    </div>

    <!-- 展开内容（可选） - 点击不会触发折叠 -->
    <div v-if="isExpanded && (hasDetails || displayInfo?.errorMessage)" class="expanded-content" @click.stop>
      <slot name="details" />
      <!-- 错误信息展示 -->
      <div v-if="displayInfo?.errorMessage" class="error-message-box">
        <div class="error-header">
          <span class="error-icon">⚠️</span>
          <span class="error-title">{{ t('tools.error') }}</span>
        </div>
        <pre class="error-content">{{ displayInfo.errorMessage }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ToolDisplayInfo } from '@/utils/toolDisplayInfo'
import { toolShowInterceptor } from '@/services/toolShowInterceptor'

const { t } = useI18n()

/**
 * 工具调用数据（用于拦截器）
 */
export interface ToolCallData {
  toolType: string
  input: Record<string, unknown>
  result?: {
    content?: string | unknown[]
    is_error?: boolean
  }
}

interface Props {
  displayInfo: ToolDisplayInfo
  isExpanded?: boolean
  hasDetails?: boolean
  clickable?: boolean
  /** 工具调用数据（用于拦截器） */
  toolCall?: ToolCallData
}

const props = withDefaults(defineProps<Props>(), {
  isExpanded: false,
  hasDetails: false,
  clickable: true,
})

const emit = defineEmits<{
  click: []
  /** 拦截器放行后触发，用于展开/折叠 */
  toggle: []
}>()

const isClickable = computed(() => {
  return props.clickable && (props.hasDetails || props.displayInfo.status !== 'pending')
})

function handleClick() {
  if (!isClickable.value) {
    return
  }

  // 如果提供了 toolCall，尝试拦截
  if (props.toolCall) {
    const intercepted = toolShowInterceptor.intercept({
      toolType: props.toolCall.toolType,
      input: props.toolCall.input,
      result: props.toolCall.result
    })

    if (intercepted) {
      // 拦截成功，IDEA 已处理，不触发任何事件
      return
    }
  }

  // 放行，触发 toggle 事件（展开/折叠）
  emit('toggle')
  emit('click') // 保持向后兼容
}

function getBadgeClass(changes: string): string {
  if (!changes) return ''
  if (changes.startsWith('+')) return 'badge-add'
  if (changes.startsWith('-')) return 'badge-remove'
  return ''
}
</script>

<style scoped>
.compact-tool-card {
  display: flex;
  flex-direction: column;
  padding: 6px 10px;
  margin-bottom: 2px;
  border-radius: 6px;
  background: var(--theme-panel-background);
  border: 1px solid var(--theme-border);
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}

.compact-tool-card.clickable {
  cursor: pointer;
}

.compact-tool-card.clickable:hover {
  background: var(--theme-hover-background);
  border-color: var(--theme-accent);
}

.card-content {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: var(--theme-font-size, 13px);
  line-height: 1.4;
  min-height: 20px;
}

.tool-icon {
  font-size: 16px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
}

.action-type {
  font-weight: 600;
  color: var(--theme-foreground);
  flex-shrink: 0;
  font-size: 12px;
  letter-spacing: 0.02em;
}

.primary-info {
  font-weight: 400;
  color: var(--theme-foreground);
  flex-shrink: 0;
}

.primary-info.loading {
  color: var(--theme-secondary-foreground);
  font-style: italic;
}

.secondary-info {
  font-size: 12px;
  color: var(--theme-secondary-foreground);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.line-changes-badge {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}

.badge-add {
  background: color-mix(in srgb, var(--theme-success) 15%, transparent);
  color: var(--theme-success);
}

.badge-remove {
  background: color-mix(in srgb, var(--theme-error) 15%, transparent);
  color: var(--theme-error);
}

.badge-read {
  background: color-mix(in srgb, var(--theme-accent) 12%, transparent);
  color: var(--theme-accent);
}

.status-indicator {
  margin-left: auto;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.status-indicator .dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  transition: all 0.2s ease;
  position: relative;
}

.status-success .dot {
  background-color: var(--theme-success);
  box-shadow: 0 0 6px color-mix(in srgb, var(--theme-success) 50%, transparent);
}

.status-error .dot {
  background-color: var(--theme-error);
  box-shadow: 0 0 6px color-mix(in srgb, var(--theme-error) 50%, transparent);
}

.status-pending .dot {
  background-color: var(--theme-secondary-foreground);
  opacity: 0.5;
}

/* 加载动画 - 旋转的圆圈 */
.status-indicator .spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--theme-border);
  border-top-color: var(--theme-accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 展开内容 */
.expanded-content {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--theme-border);
  animation: slideDown 0.2s ease;
}

@keyframes slideDown {
  from {
    opacity: 0;
    transform: translateY(-8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 错误信息框样式 */
.error-message-box {
  margin-top: 10px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--theme-error) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-error) 30%, transparent);
  border-radius: 6px;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.error-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.error-icon {
  font-size: 14px;
}

.error-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-error);
}

.error-content {
  margin: 0;
  padding: 8px 10px;
  background: color-mix(in srgb, var(--theme-error) 5%, transparent);
  border-radius: 4px;
  font-size: var(--theme-editor-font-size, 12px);
  font-family: var(--theme-editor-font-family);
  color: var(--theme-error);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.5;
}
</style>

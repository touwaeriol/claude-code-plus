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
      <!-- 图标（支持 emoji 或 SVG） -->
      <span
        v-if="isSvgIcon"
        class="tool-icon"
        v-html="displayInfo?.icon"
      />
      <span v-else class="tool-icon">{{ displayInfo?.icon || '🔧' }}</span>

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

      <!-- 自定义操作按钮插槽 -->
      <span v-if="$slots['header-actions']" class="header-actions" @click.stop>
        <slot name="header-actions" />
      </span>

      <!-- 后台运行提示 -->
      <span v-if="showBackgroundHint" class="background-hint">
        {{ t('tools.ctrlBToBackground') }}
      </span>

      <!-- 状态指示器 -->
      <span class="status-indicator" :class="`status-${displayInfo?.status || 'pending'}`">
        <!-- 解析参数中：蓝色转圈 -->
        <span v-if="displayInfo?.isInputLoading" class="spinner spinner-parsing" />
        <!-- 执行中：绿色转圈 -->
        <span v-else-if="!displayInfo?.status || displayInfo?.status === 'pending'" class="spinner spinner-running" />
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
import { computed, ref, watch, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ToolDisplayInfo } from '@/utils/toolDisplayInfo'
import { toolShowInterceptor } from '@/services/toolShowInterceptor'
import { useSessionStore } from '@/stores/sessionStore'

const { t } = useI18n()
const sessionStore = useSessionStore()

// 后台运行提示相关
const showBackgroundHint = ref(false)
let backgroundHintTimer: ReturnType<typeof setTimeout> | null = null

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
  /** 是否支持后台运行（仅 Bash/Task 支持） */
  supportsBackground?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isExpanded: false,
  hasDetails: false,
  clickable: true,
  supportsBackground: false,
})

const emit = defineEmits<{
  click: []
  /** 拦截器放行后触发，用于展开/折叠 */
  toggle: []
}>()

const isClickable = computed(() => {
  return props.clickable && (props.hasDetails || props.displayInfo.status !== 'pending')
})

// 判断图标是否为 SVG
const isSvgIcon = computed(() => {
  const icon = props.displayInfo?.icon
  return icon && icon.trim().startsWith('<svg')
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

  // 展开时切换到浏览模式，防止自动滚动打断阅读
  if (!props.isExpanded) {
    sessionStore.switchToBrowseMode()
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

// 监听状态变化，启动/清除后台运行提示计时器
watch(
  () => props.displayInfo?.status,
  (status) => {
    // 清除之前的计时器
    if (backgroundHintTimer) {
      clearTimeout(backgroundHintTimer)
      backgroundHintTimer = null
    }
    showBackgroundHint.value = false

    // 只有支持后台运行的工具（Bash/Task）且状态是 pending 时，5秒后显示提示
    if (props.supportsBackground && (status === 'pending' || !status)) {
      backgroundHintTimer = setTimeout(() => {
        // 再次检查状态，确保仍在执行中
        if (props.displayInfo?.status === 'pending' || !props.displayInfo?.status) {
          showBackgroundHint.value = true
        }
      }, 5000)
    }
  },
  { immediate: true }
)

// 组件卸载时清除计时器
onUnmounted(() => {
  if (backgroundHintTimer) {
    clearTimeout(backgroundHintTimer)
    backgroundHintTimer = null
  }
})
</script>

<style scoped>
.compact-tool-card {
  display: flex;
  flex-direction: column;
  padding: 2px 6px;
  margin-bottom: 0;
  border-radius: 4px;
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
  gap: 4px;
  font-size: var(--theme-font-size, 13px);
  line-height: 1.2;
  min-height: 16px;
}

.tool-icon {
  font-size: 14px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
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
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 10px;
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: auto;
  flex-shrink: 0;
}

.status-indicator {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  margin-left: auto;
}

.status-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: all 0.2s ease;
}

/* 绿色 - 完成 */
.status-success .dot {
  background-color: var(--theme-success);
}

/* 红色 - 失败 */
.status-error .dot {
  background-color: var(--theme-error);
}

/* 转圈基础样式 */
.status-indicator .spinner {
  width: 10px;
  height: 10px;
  border: 1.5px solid transparent;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

/* 解析参数中（默认蓝色） */
.status-indicator .spinner-parsing {
  border-top-color: var(--theme-pending);
  border-right-color: var(--theme-pending);
}

/* 执行中（默认绿色） */
.status-indicator .spinner-running {
  border-top-color: var(--theme-running);
  border-right-color: var(--theme-running);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 展开内容 */
.expanded-content {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--theme-border);
  animation: slideDown 0.15s ease;
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
  font-family: var(--theme-editor-font-family), monospace;
  color: var(--theme-error);
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.5;
}

/* 后台运行提示样式 */
.background-hint {
  font-size: 11px;
  color: var(--theme-secondary-foreground);
  background: color-mix(in srgb, var(--theme-accent) 10%, transparent);
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: auto;
  flex-shrink: 0;
  animation: fadeIn 0.3s ease;
}
</style>

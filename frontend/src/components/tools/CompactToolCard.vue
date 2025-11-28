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
      <span
        v-if="displayInfo?.lineChanges"
        class="line-changes-badge"
        :class="getBadgeClass(displayInfo.lineChanges)"
      >
        {{ displayInfo.lineChanges }}
      </span>

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

const { t } = useI18n()

interface Props {
  displayInfo: ToolDisplayInfo
  isExpanded?: boolean
  hasDetails?: boolean
  clickable?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isExpanded: false,
  hasDetails: false,
  clickable: true,
})

const emit = defineEmits<{
  click: []
}>()

const isClickable = computed(() => {
  return props.clickable && (props.hasDetails || props.displayInfo.status !== 'pending')
})

function handleClick() {
  if (isClickable.value) {
    emit('click')
  }
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
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, rgba(0, 0, 0, 0.08));
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.02);
}

.compact-tool-card.clickable {
  cursor: pointer;
}

.compact-tool-card.clickable:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.02));
  border-color: var(--ide-accent, rgba(0, 102, 214, 0.4));
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.06);
  transform: translateX(2px);
}

.card-content {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
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
  filter: drop-shadow(0 1px 1px rgba(0, 0, 0, 0.1));
}

.action-type {
  font-weight: 600;
  color: var(--ide-foreground, #1a1a1a);
  flex-shrink: 0;
  font-size: 12px;
  letter-spacing: 0.02em;
}

.primary-info {
  font-weight: 400;
  color: var(--ide-foreground, #1a1a1a);
  flex-shrink: 0;
}

.primary-info.loading {
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.5));
  font-style: italic;
}

.secondary-info {
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.5));
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
  background: rgba(40, 167, 69, 0.1);
  color: #28a745;
}

.badge-remove {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
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
  background-color: #28a745;
  box-shadow: 0 0 0 2px rgba(40, 167, 69, 0.15),
              0 0 6px rgba(40, 167, 69, 0.3);
  animation: pulse-success 2s ease-in-out infinite;
}

.status-error .dot {
  background-color: #dc3545;
  box-shadow: 0 0 0 2px rgba(220, 53, 69, 0.15),
              0 0 6px rgba(220, 53, 69, 0.3);
}

.status-pending .dot {
  background-color: rgba(0, 0, 0, 0.25);
  box-shadow: 0 0 0 2px rgba(0, 0, 0, 0.08);
}

/* 成功状态脉冲动画 */
@keyframes pulse-success {
  0%, 100% {
    box-shadow: 0 0 0 2px rgba(40, 167, 69, 0.15),
                0 0 6px rgba(40, 167, 69, 0.3);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(40, 167, 69, 0.2),
                0 0 8px rgba(40, 167, 69, 0.4);
  }
}

/* 加载动画 - 旋转的圆圈 */
.status-indicator .spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(0, 0, 0, 0.08);
  border-top-color: var(--ide-accent, #0366d6);
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
  border-top: 1px solid var(--ide-border, rgba(0, 0, 0, 0.1));
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

/* 暗色主题适配 */
.theme-dark .compact-tool-card {
  background: var(--ide-background, #1e1e1e);
  border-color: var(--ide-border, rgba(255, 255, 255, 0.1));
}

.theme-dark .compact-tool-card.clickable:hover {
  background: var(--ide-hover-background, rgba(255, 255, 255, 0.05));
  border-color: var(--ide-accent, rgba(88, 166, 255, 0.3));
}

.theme-dark .action-type,
.theme-dark .primary-info {
  color: var(--ide-foreground, #e0e0e0);
}

.theme-dark .secondary-info {
  color: var(--ide-secondary-foreground, rgba(255, 255, 255, 0.5));
}

.theme-dark .expanded-content {
  border-top-color: var(--ide-border, rgba(255, 255, 255, 0.1));
}

.theme-dark .status-indicator .spinner {
  border-color: rgba(255, 255, 255, 0.1);
  border-top-color: var(--ide-accent, #58a6ff);
}

/* 错误信息框样式 */
.error-message-box {
  margin-top: 10px;
  padding: 10px 12px;
  background: rgba(220, 53, 69, 0.08);
  border: 1px solid rgba(220, 53, 69, 0.3);
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
  color: #dc3545;
}

.error-content {
  margin: 0;
  padding: 8px 10px;
  background: rgba(220, 53, 69, 0.05);
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  color: #c53030;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
  line-height: 1.5;
}

/* 暗色主题错误样式 */
.theme-dark .error-message-box {
  background: rgba(248, 81, 73, 0.1);
  border-color: rgba(248, 81, 73, 0.3);
}

.theme-dark .error-title {
  color: #f85149;
}

.theme-dark .error-content {
  background: rgba(248, 81, 73, 0.08);
  color: #ffa7a3;
}
</style>


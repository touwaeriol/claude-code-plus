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
        正在解析参数...
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
    <div v-if="isExpanded && hasDetails" class="expanded-content" @click.stop>
      <slot name="details" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ToolDisplayInfo } from '@/utils/toolDisplayInfo'

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
  padding: 3px 6px;
  margin-bottom: 1px;
  border-radius: 4px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, rgba(0, 0, 0, 0.1));
  transition: all 0.2s ease;
}

.compact-tool-card.clickable {
  cursor: pointer;
}

.compact-tool-card.clickable:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.03));
  border-color: var(--ide-accent, rgba(0, 102, 214, 0.3));
}

.card-content {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 1.3;
}

.tool-icon {
  font-size: 14px;
  flex-shrink: 0;
}

.action-type {
  font-weight: 500;
  color: var(--ide-foreground, #1a1a1a);
  flex-shrink: 0;
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
  width: 16px;
  height: 16px;
}

.status-indicator .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  transition: background-color 0.2s ease;
}

.status-success .dot {
  background-color: #28a745;
}

.status-error .dot {
  background-color: #dc3545;
}

.status-pending .dot {
  background-color: rgba(0, 0, 0, 0.3);
}

/* 加载动画 - 旋转的圆圈 */
.status-indicator .spinner {
  width: 12px;
  height: 12px;
  border: 2px solid rgba(0, 0, 0, 0.1);
  border-top-color: var(--ide-accent, #0366d6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
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
</style>


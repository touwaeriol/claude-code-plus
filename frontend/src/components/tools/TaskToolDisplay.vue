<template>
  <div class="tool-display task-tool" :class="statusClass">
    <div class="tool-header">
      <span class="tool-icon">🤖</span>
      <span class="tool-name">{{ t('tools.subtask') }}</span>
      <span class="task-info">
        <span class="task-description">{{ description }}</span>
        <span class="agent-type-badge">{{ subagentType }}</span>
        <span class="status-badge" :class="statusClass">{{ statusText }}</span>
      </span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="task-meta">
        <div class="meta-row">
          <span class="meta-label">{{ t('tools.label.agentType') }}:</span>
          <span class="meta-value agent-type">{{ subagentType }}</span>
        </div>
        <div v-if="model" class="meta-row">
          <span class="meta-label">{{ t('tools.label.model') }}:</span>
          <span class="meta-value">{{ model }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">{{ t('tools.status.pending').split(' ')[0] }}:</span>
          <span class="meta-value" :class="statusClass">{{ statusText }}</span>
        </div>
      </div>
      <div class="task-details">
        <div class="detail-row">
          <span class="detail-label">{{ t('tools.label.prompt') }}:</span>
          <span class="detail-value">{{ prompt || '—' }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { GenericToolCall } from '@/types/display'
import { ToolCallStatus } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看任务详情
const expanded = ref(false)

const description = computed(() => (props.toolCall.input as any)?.description || t('tools.subtask'))
const prompt = computed(() => (props.toolCall.input as any)?.prompt || '')
const subagentType = computed(() => (props.toolCall.input as any)?.subagent_type || 'general-purpose')
const model = computed(() => (props.toolCall.input as any)?.model || '')

const statusClass = computed(() => {
  switch (props.toolCall.status) {
    case ToolCallStatus.SUCCESS:
      return 'success'
    case ToolCallStatus.FAILED:
      return 'failed'
    case ToolCallStatus.RUNNING:
    default:
      return 'running'
  }
})

const statusText = computed(() => {
  switch (props.toolCall.status) {
    case ToolCallStatus.SUCCESS:
      return t('tools.status.success')
    case ToolCallStatus.FAILED:
      return t('tools.status.failed')
    case ToolCallStatus.RUNNING:
    default:
      return t('tools.status.running')
  }
})
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
  padding: 8px 12px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
}

.task-info {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.task-description {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-type-badge {
  padding: 2px 6px;
  border-radius: 10px;
  background: #e6f7ff;
  color: #0366d6;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 10px;
  font-weight: 600;
}

.status-badge.success {
  background: #e6ffed;
  color: #22863a;
}

.status-badge.failed {
  background: #ffeef0;
  color: #d73a49;
}

.status-badge.running {
  background: #fff8e1;
  color: #b26a00;
}

.tool-content {
  margin-top: 8px;
}

.task-meta,
.task-details {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.meta-row,
.detail-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
}

.meta-label,
.detail-label {
  color: #586069;
  min-width: 64px;
}

.meta-value,
.detail-value {
  color: #24292e;
}

.success {
  border-color: #34d058;
}

.failed {
  border-color: #d73a49;
}
</style>

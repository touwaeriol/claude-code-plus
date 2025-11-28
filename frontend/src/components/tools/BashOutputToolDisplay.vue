<template>
  <div class="tool-display bash-output-tool">
    <div class="tool-header">
      <span class="tool-icon">📟</span>
      <span class="tool-name">BashOutput</span>
      <code class="shell-id">Shell: {{ shellId }}</code>
      <span v-if="filter" class="filter-badge">🔍 {{ filter }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="tool-meta">
        <div class="info-row">
          <span class="label">Shell ID:</span>
          <span class="value">{{ shellId }}</span>
        </div>
        <div v-if="filter" class="info-row">
          <span class="label">{{ t('tools.label.filter') }}:</span>
          <code class="value filter-text">{{ filter }}</code>
        </div>
      </div>

      <div v-if="stdout || stderr" class="output-section">
        <div class="section-header">
          <span>{{ t('tools.label.output') }}</span>
          <div class="header-actions">
            <span
              v-if="status"
              class="status-badge"
              :class="statusClass"
            >{{ status }}</span>
          </div>
        </div>

        <pre v-if="stdout" class="output stdout">{{ stdout }}</pre>
        <pre v-if="stderr" class="output stderr">{{ stderr }}</pre>
        <div v-if="!stdout && !stderr" class="no-output">{{ t('tools.noOutput') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { GenericToolCall } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看输出
const expanded = ref(false)

const shellId = computed(() => (props.toolCall.input as any)?.bash_id || '')
const filter = computed(() => (props.toolCall.input as any)?.filter || '')

const stdout = computed(() => {
  const r = props.toolCall.result
  if (!r || r.type !== 'success') return ''
  const content = r.output
  if (typeof content === 'string') return content
  if (content && typeof content === 'object' && 'stdout' in content) {
    return (content as any).stdout || ''
  }
  return ''
})

const stderr = computed(() => {
  const r = props.toolCall.result
  if (!r || r.type !== 'error') return ''
  return r.error || ''
})

const status = computed(() => props.toolCall.status)
const statusClass = computed(() => {
  switch (props.toolCall.status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'error'
    default:
      return 'info'
  }
})
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-panel-background, #f6f8fa);
  margin: 8px 0;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
}

.shell-id {
  margin-left: auto;
  font-family: monospace;
  color: #586069;
}

.filter-badge {
  font-size: 12px;
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 6px;
  border-radius: 10px;
}

.tool-content {
  padding: 8px 12px 12px;
}

.tool-meta {
  margin-bottom: 8px;
}

.info-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
  margin-bottom: 4px;
}

.label {
  color: #586069;
  min-width: 70px;
}

.value {
  color: #24292e;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 10px;
  font-size: 11px;
}

.status-badge.success {
  background: #e6ffed;
  color: #22863a;
}

.status-badge.error {
  background: #ffeef0;
  color: #d73a49;
}

.status-badge.info {
  background: #fff8e1;
  color: #b26a00;
}

.output-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.output {
  margin: 0;
  padding: 8px;
  background: #111;
  color: #eee;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  white-space: pre-wrap;
}

.output.stderr {
  background: #2d1a1a;
  color: #ffb3b3;
}

.no-output {
  font-size: 12px;
  color: #888;
}
</style>

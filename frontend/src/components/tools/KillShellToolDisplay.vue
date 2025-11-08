<template>
  <div class="tool-display kill-shell-tool">
    <div class="tool-header">
      <span class="tool-icon">⛔</span>
      <span class="tool-name">KillShell</span>
      <code class="shell-id">Shell: {{ shellId }}</code>
      <span
        v-if="result"
        class="status-badge"
        :class="statusClass"
      >{{ statusText }}</span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="tool-meta">
        <div class="info-row">
          <span class="label">Shell ID:</span>
          <span class="value">{{ shellId }}</span>
        </div>
      </div>

      <div
        v-if="result"
        class="result-section"
      >
        <div class="section-header">
          终止结果
        </div>
        <div
          class="result-message"
          :class="messageClass"
        >
          <span class="result-icon">{{ resultIcon }}</span>
          <span class="message-text">{{ message }}</span>
        </div>
      </div>
    </div>
    <button
      class="expand-btn"
      @click="expanded = !expanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()
const expanded = ref(false)

const shellId = computed(() => props.toolUse.input.shell_id || '')

const resultContent = computed(() => {
  if (!props.result?.content) return null
  if (typeof props.result.content === 'string') {
    try {
      return JSON.parse(props.result.content)
    } catch {
      return { message: props.result.content }
    }
  }
  return props.result.content
})

const success = computed(() => {
  if (!resultContent.value) return false
  return resultContent.value.success === true
})

const message = computed(() => {
  if (!resultContent.value) return '执行中...'
  return resultContent.value.message || (success.value ? 'Shell 已成功终止' : '终止失败')
})

const statusClass = computed(() => {
  if (!props.result) return 'status-pending'
  return success.value ? 'status-success' : 'status-error'
})

const statusText = computed(() => {
  if (!props.result) return '执行中'
  return success.value ? '已终止' : '失败'
})

const messageClass = computed(() => {
  if (!props.result) return 'message-pending'
  return success.value ? 'message-success' : 'message-error'
})

const resultIcon = computed(() => {
  if (!props.result) return '⏳'
  return success.value ? '✅' : '❌'
})
</script>

<style scoped>
.kill-shell-tool {
  border-color: #dc3545;
}

.kill-shell-tool .tool-name {
  color: #dc3545;
}

.shell-id {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(220, 53, 69, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  margin-left: auto;
}

.status-pending {
  background: #f1f8ff;
  color: #0366d6;
}

.status-success {
  background: #e6ffed;
  color: #22863a;
}

.status-error {
  background: #ffeef0;
  color: #d73a49;
}

.tool-meta {
  margin-bottom: 12px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 13px;
}

.info-row .label {
  font-weight: 600;
  color: #586069;
  min-width: 80px;
}

.info-row .value {
  font-family: monospace;
  color: #24292e;
}

.result-section {
  margin-top: 12px;
}

.section-header {
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 8px;
}

.result-message {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border-radius: 4px;
  font-size: 13px;
}

.message-pending {
  background: #f1f8ff;
  border: 1px solid #c8e1ff;
  color: #0366d6;
}

.message-success {
  background: #e6ffed;
  border: 1px solid #34d058;
  color: #22863a;
}

.message-error {
  background: #ffeef0;
  border: 1px solid #f97583;
  color: #d73a49;
}

.result-icon {
  font-size: 16px;
}

.message-text {
  font-weight: 500;
}
</style>

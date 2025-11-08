<template>
  <div class="tool-display bash-output-tool">
    <div class="tool-header">
      <span class="tool-icon">📟</span>
      <span class="tool-name">BashOutput</span>
      <code class="shell-id">Shell: {{ shellId }}</code>
      <span
        v-if="filter"
        class="filter-badge"
      >🔍 {{ filter }}</span>
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
        <div
          v-if="filter"
          class="info-row"
        >
          <span class="label">过滤器:</span>
          <code class="value filter-text">{{ filter }}</code>
        </div>
      </div>

      <div
        v-if="result"
        class="output-section"
      >
        <div class="section-header">
          <span>输出内容</span>
          <div class="header-actions">
            <span
              v-if="status"
              class="status-badge"
              :class="statusClass"
            >{{ status }}</span>
            <button
              v-if="output"
              class="copy-btn"
              @click="copyOutput"
            >
              复制
            </button>
          </div>
        </div>
        <pre
          v-if="output"
          class="output-content"
        >{{ output }}</pre>
        <div
          v-else
          class="no-output"
        >
          无输出
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

const shellId = computed(() => props.toolUse.input.bash_id || '')
const filter = computed(() => props.toolUse.input.filter || '')

const resultContent = computed(() => {
  if (!props.result?.content) return null
  if (typeof props.result.content === 'string') {
    try {
      return JSON.parse(props.result.content)
    } catch {
      return { output: props.result.content }
    }
  }
  return props.result.content
})

const output = computed(() => {
  if (!resultContent.value) return ''
  return resultContent.value.output || ''
})

const status = computed(() => {
  if (!resultContent.value) return ''
  return resultContent.value.status || ''
})

const statusClass = computed(() => {
  const s = status.value.toLowerCase()
  if (s.includes('running') || s.includes('active')) return 'status-running'
  if (s.includes('completed') || s.includes('done')) return 'status-success'
  if (s.includes('failed') || s.includes('error')) return 'status-error'
  return 'status-neutral'
})

async function copyOutput() {
  if (output.value) {
    await navigator.clipboard.writeText(output.value)
  }
}
</script>

<style scoped>
.bash-output-tool {
  border-color: #17a2b8;
}

.bash-output-tool .tool-name {
  color: #17a2b8;
}

.shell-id {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  background: rgba(23, 162, 184, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
  color: #24292e;
}

.filter-badge {
  font-size: 11px;
  background: #fff3cd;
  color: #856404;
  padding: 2px 6px;
  border-radius: 3px;
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

.filter-text {
  background: #f6f8fa;
  padding: 2px 4px;
  border-radius: 2px;
}

.output-section {
  margin-top: 12px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  font-weight: 600;
  color: #586069;
  margin-bottom: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.status-running {
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

.status-neutral {
  background: #f6f8fa;
  color: #586069;
}

.copy-btn {
  padding: 2px 8px;
  font-size: 11px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  cursor: pointer;
}

.copy-btn:hover {
  background: #f6f8fa;
}

.output-content {
  margin: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  max-height: 400px;
  overflow: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.no-output {
  padding: 12px;
  text-align: center;
  color: #586069;
  font-size: 13px;
  font-style: italic;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}
</style>

<template>
  <div class="tool-display read-mcp-tool">
    <div class="tool-header">
      <span class="tool-icon">🧩</span>
      <span class="tool-name">ReadMcpResource</span>
      <span class="server">{{ serverName }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="info-row">
        <span class="label">Server:</span>
        <span class="value">{{ serverName }}</span>
      </div>
      <div class="info-row">
        <span class="label">URI:</span>
        <span class="value">{{ uri }}</span>
      </div>
      <div v-if="resultText" class="result">
        <div class="section-title">Result</div>
        <pre>{{ resultText }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// 默认折叠，点击后展开查看资源内容
const expanded = ref(false)

const serverName = computed(() => (props.toolCall.input as any)?.server || '')
const uri = computed(() => (props.toolCall.input as any)?.uri || '')

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r) return ''
  if (r.type === 'success') return typeof r.output === 'string' ? r.output : JSON.stringify(r.output)
  if (r.type === 'error') return r.error || ''
  return ''
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

.server {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
}

.tool-content {
  margin-top: 8px;
}

.info-row {
  display: flex;
  gap: 6px;
  font-size: 12px;
}

.label {
  color: #586069;
  min-width: 60px;
}

.value {
  color: #24292e;
}

.section-title {
  font-weight: 600;
  font-size: 12px;
  margin-top: 6px;
}

pre {
  margin: 0;
  padding: 6px;
  background: #fff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
}
</style>

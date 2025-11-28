<template>
  <div class="tool-display generic-tool">
    <div class="tool-header">
      <span class="tool-icon">🧩</span>
      <span class="tool-name">{{ fullToolName }}</span>
      <span class="tool-status">{{ toolCall.status }}</span>
    </div>
    <div v-if="expanded" class="tool-content">
      <div class="params">
        <div class="section-title">Params</div>
        <pre>{{ paramsFormatted }}</pre>
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
// 默认折叠，点击后展开查看参数和结果
const expanded = ref(false)

const fullToolName = computed(() => props.toolCall.toolType || 'unknown')
const params = computed(() => props.toolCall.input || {})
const paramsFormatted = computed(() => {
  try {
    return JSON.stringify(params.value, null, 2)
  } catch {
    return String(params.value)
  }
})

const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r) return ''
  // 使用后端格式：直接读取 content
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return JSON.stringify(r.content, null, 2)
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

.tool-status {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
}

.tool-content {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.section-title {
  font-weight: 600;
  font-size: 12px;
  margin-bottom: 4px;
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

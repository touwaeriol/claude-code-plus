<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="glob-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div class="info-row">
            <span class="label">Pattern:</span>
            <code class="value">{{ pattern }}</code>
          </div>
          <div v-if="path" class="info-row">
            <span class="label">Path:</span>
            <span class="value path">{{ path }}</span>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">匹配文件</div>
          <pre class="result-content">{{ resultText }}</pre>
        </div>
      </div>
    </template>
  </CompactToolCard>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { GenericToolCall } from '@/types/display'
import CompactToolCard from './CompactToolCard.vue'
import { extractToolDisplayInfo } from '@/utils/toolDisplayInfo'

interface Props {
  toolCall: GenericToolCall
}

const props = defineProps<Props>()
// Glob 默认折叠（搜索结果可能很长）
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const pattern = computed(() => props.toolCall.input?.pattern || '')
const path = computed(() => props.toolCall.input?.path || '')

// 结果文本
const resultText = computed(() => {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return JSON.stringify(r.content, null, 2)
})

// 是否有结果
const hasResult = computed(() => {
  const r = props.toolCall.result
  return r && !r.is_error && resultText.value
})

// 始终有参数可展示
const hasDetails = computed(() => !!pattern.value)
</script>

<style scoped>
.glob-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-row {
  display: flex;
  gap: 8px;
  font-size: 12px;
  align-items: baseline;
}

.label {
  color: var(--ide-secondary-foreground, #586069);
  min-width: 60px;
  flex-shrink: 0;
}

.value {
  color: var(--ide-foreground, #24292e);
}

.value.path {
  font-family: monospace;
  word-break: break-all;
}

code.value {
  background: var(--ide-code-background, #f0f4f8);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.result-section {
  border-top: 1px solid var(--ide-border, #e1e4e8);
  padding-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--ide-code-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>

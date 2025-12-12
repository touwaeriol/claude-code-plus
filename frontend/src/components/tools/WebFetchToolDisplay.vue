<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="webfetch-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div class="info-row">
            <span class="label">URL:</span>
            <a :href="url" target="_blank" class="value link">{{ url }}</a>
          </div>
          <div v-if="prompt" class="info-row">
            <span class="label">Prompt:</span>
            <span class="value">{{ prompt }}</span>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
<div class="section-title">Content</div>
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
// WebFetch 默认折叠（抓取结果可能很长）
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const url = computed(() => (props.toolCall.input?.url as string) || '')
const prompt = computed(() => props.toolCall.input?.prompt || '')

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
const hasDetails = computed(() => !!url.value)
</script>

<style scoped>
.webfetch-details {
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
  color: var(--theme-secondary-foreground, #586069);
  min-width: 60px;
  flex-shrink: 0;
}

.value {
  color: var(--theme-foreground, #24292e);
  word-break: break-all;
}

.value.link {
  color: var(--theme-link, #0366d6);
  text-decoration: none;
}

.value.link:hover {
  text-decoration: underline;
}

.result-section {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.result-content {
  margin: 0;
  padding: 8px;
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow-y: auto;
}
</style>

<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="grep-details">
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
          <div v-if="glob" class="info-row">
            <span class="label">Glob:</span>
            <code class="value">{{ glob }}</code>
          </div>
          <div v-if="fileType" class="info-row">
            <span class="label">Type:</span>
            <span class="value">{{ fileType }}</span>
          </div>
          <div v-if="outputMode" class="info-row">
            <span class="label">Mode:</span>
            <span class="value">{{ outputModeText }}</span>
          </div>
          <div v-if="options.length > 0" class="options-row">
            <span v-for="opt in options" :key="opt" class="option-badge">{{ opt }}</span>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">搜索结果</div>
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
// Grep 默认折叠（搜索结果可能很长）
const expanded = ref(false)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const pattern = computed(() => props.toolCall.input?.pattern || '')
const path = computed(() => props.toolCall.input?.path || '')
const glob = computed(() => props.toolCall.input?.glob || '')
const fileType = computed(() => props.toolCall.input?.type || '')
const outputMode = computed(() => props.toolCall.input?.output_mode || '')

const outputModeText = computed(() => {
  const map: Record<string, string> = {
    'content': '显示内容',
    'files_with_matches': '仅文件名',
    'count': '匹配计数'
  }
  return map[outputMode.value as string] || outputMode.value
})

const options = computed(() => {
  const opts: string[] = []
  const input = props.toolCall.input as any
  if (input?.['-i']) opts.push('忽略大小写')
  if (input?.['-n']) opts.push('显示行号')
  if (input?.multiline) opts.push('多行匹配')
  return opts
})

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
.grep-details {
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
}

.value.path {
  font-family: monospace;
  word-break: break-all;
}

code.value {
  background: var(--theme-code-background, #f0f4f8);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.options-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 4px;
}

.option-badge {
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--theme-badge-background, #eef2ff);
  color: var(--theme-badge-foreground, #4338ca);
  font-size: 11px;
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

<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="task-details">
        <!-- 参数区域 -->
        <div class="params-section">
          <div v-if="description" class="info-row">
            <span class="label">描述:</span>
            <span class="value">{{ description }}</span>
          </div>
          <div v-if="subagentType" class="info-row">
            <span class="label">Agent:</span>
            <span class="value badge">{{ subagentType }}</span>
          </div>
          <div v-if="model" class="info-row">
            <span class="label">Model:</span>
            <span class="value">{{ model }}</span>
          </div>
          <div v-if="prompt" class="prompt-section">
            <div class="section-title">Prompt</div>
            <pre class="prompt-content">{{ prompt }}</pre>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">执行结果</div>
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
// Task 默认展开（用户关注任务进度）
const expanded = ref(true)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const description = computed(() => props.toolCall.input?.description || '')
const subagentType = computed(() => props.toolCall.input?.subagent_type || '')
const model = computed(() => props.toolCall.input?.model || '')
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
const hasDetails = computed(() => !!description.value || !!prompt.value)
</script>

<style scoped>
.task-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.params-section {
  display: flex;
  flex-direction: column;
  gap: 6px;
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

.value.badge {
  background: var(--ide-badge-background, #eef2ff);
  color: var(--ide-badge-foreground, #4338ca);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
}

.prompt-section {
  margin-top: 8px;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--ide-secondary-foreground, #586069);
  margin-bottom: 6px;
  text-transform: uppercase;
}

.prompt-content {
  margin: 0;
  padding: 8px;
  background: var(--ide-code-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.result-section {
  border-top: 1px solid var(--ide-border, #e1e4e8);
  padding-top: 8px;
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

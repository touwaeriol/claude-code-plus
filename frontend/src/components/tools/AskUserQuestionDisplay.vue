<template>
  <CompactToolCard
    :display-info="displayInfo"
    :is-expanded="expanded"
    :has-details="hasDetails"
    @click="expanded = !expanded"
  >
    <template #details>
      <div class="askuser-details">
        <!-- 问题列表 -->
        <div v-for="(q, index) in questions" :key="index" class="question-item">
          <div class="question-header">
            <span class="question-badge">Q{{ index + 1 }}</span>
            <span class="question-text">{{ q.question }}</span>
          </div>
          <div v-if="q.options && q.options.length > 0" class="options-list">
            <div v-for="(opt, optIndex) in q.options" :key="optIndex" class="option-item">
              <span class="option-label">{{ opt.label || opt }}</span>
              <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
            </div>
          </div>
        </div>
        <!-- 结果区域 -->
        <div v-if="hasResult" class="result-section">
          <div class="section-title">用户回答</div>
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
// AskUserQuestion 默认展开（用户需要看到问题）
const expanded = ref(true)

const displayInfo = computed(() => extractToolDisplayInfo(props.toolCall as any, props.toolCall.result as any))

const questions = computed(() => props.toolCall.input?.questions || [])

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
const hasDetails = computed(() => questions.value.length > 0)
</script>

<style scoped>
.askuser-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-item {
  padding: 8px;
  background: var(--theme-panel-background, #f6f8fa);
  border-radius: 6px;
}

.question-header {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 8px;
}

.question-badge {
  background: var(--theme-accent, #0366d6);
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
}

.question-text {
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 36px;
}

.option-item {
  display: flex;
  flex-direction: column;
  padding: 4px 8px;
  background: var(--theme-background, #ffffff);
  border-radius: 4px;
  font-size: 12px;
}

.option-label {
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.option-desc {
  color: var(--theme-secondary-foreground, #586069);
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
  max-height: 200px;
  overflow-y: auto;
}
</style>

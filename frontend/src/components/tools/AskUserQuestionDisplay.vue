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
            <div
              v-for="(opt, optIndex) in q.options"
              :key="optIndex"
              class="option-item"
              :class="{ 'option-selected': isOptionSelected(q, opt) }"
            >
              <span class="option-label">{{ opt.label || opt }}</span>
              <span v-if="opt.description" class="option-desc">{{ opt.description }}</span>
            </div>
          </div>
          <!-- 结构化回答（每个问题独立展示） -->
          <div v-if="getAnswer(q)" class="answer-inline">
            <span class="answer-label">A:</span>
            <span class="answer-text">{{ getAnswer(q) }}</span>
          </div>
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

interface AskUserQuestionInput {
  questions?: Array<{
    question: string
    header?: string
    options?: Array<{ label: string; description?: string }>
    multiSelect?: boolean
  }>
}

const questions = computed(() => (props.toolCall.input as AskUserQuestionInput)?.questions || [])

/**
 * Parse answers from the result.
 * Supports two formats:
 * 1. MCP format: Markdown with "## User Answers" / "**A:** answer"
 * 2. canUseTool format: answers are in input.answers (injected by updatedInput)
 */
const parsedAnswers = computed((): Record<string, string> => {
  const answers: Record<string, string> = {}

  // Try input.answers (canUseTool updatedInput format)
  const inputAnswers = (props.toolCall.input as any)?.answers
  if (inputAnswers && typeof inputAnswers === 'object') {
    for (const [key, value] of Object.entries(inputAnswers)) {
      if (key !== 'reason' && typeof value === 'string') {
        answers[key] = value
      }
    }
    if (Object.keys(answers).length > 0) return answers
  }

  // Try parsing result text (MCP Markdown format: "**A:** answer")
  const resultText = getRawResultText()
  if (resultText) {
    const answerRegex = /\*\*Q:\*\*\s*(.+?)\n\*\*A:\*\*\s*(.+?)(?:\n|$)/g
    let match
    while ((match = answerRegex.exec(resultText)) !== null) {
      answers[match[1].trim()] = match[2].trim()
    }
  }

  return answers
})

function getRawResultText(): string {
  const r = props.toolCall.result
  if (!r || r.is_error) return ''
  if (typeof r.content === 'string') return r.content
  if (Array.isArray(r.content)) {
    return (r.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }
  return ''
}

function getAnswer(q: { question: string }): string {
  return parsedAnswers.value[q.question] || ''
}

function isOptionSelected(q: { question: string }, opt: { label: string } | string): boolean {
  const answer = getAnswer(q)
  if (!answer) return false
  const label = typeof opt === 'string' ? opt : opt.label
  // Check if this option's label appears in the answer
  return answer.includes(label)
}

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
  border: 1px solid transparent;
  transition: border-color 0.15s, background-color 0.15s;
}

.option-item.option-selected {
  border-color: var(--theme-accent, #0366d6);
  background: color-mix(in srgb, var(--theme-accent, #0366d6) 8%, transparent);
}

.option-label {
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.option-desc {
  color: var(--theme-secondary-foreground, #586069);
  font-size: 11px;
}

.answer-inline {
  margin-top: 6px;
  margin-left: 36px;
  padding: 4px 8px;
  background: color-mix(in srgb, var(--theme-success, #28a745) 10%, transparent);
  border-radius: 4px;
  border-left: 3px solid var(--theme-success, #28a745);
  font-size: 12px;
}

.answer-label {
  font-weight: 600;
  color: var(--theme-success, #28a745);
  margin-right: 4px;
}

.answer-text {
  color: var(--theme-foreground, #24292e);
}
</style>

<template>
  <div
    class="tool-display ask-question-tool"
    :class="{ 'answered': hasAnswered }"
  >
    <div class="tool-header">
      <span class="tool-icon">❓</span>
      <span class="tool-name">用户问答</span>
      <span
        v-if="hasAnswered"
        class="answered-badge"
      >✓ 已回答</span>
      <span
        v-if="questionsData.length > 1"
        class="question-count"
      >
        {{ questionsData.length }} 个问题
      </span>
    </div>

    <div
      v-if="expanded"
      class="tool-content"
    >
      <!-- 多个问题 -->
      <div
        v-for="(question, qIndex) in questionsData"
        :key="qIndex"
        class="question-block"
      >
        <!-- 问题标题 -->
        <div class="question-header">
          <span
            v-if="question.header"
            class="question-tag"
          >{{ question.header }}</span>
          <span class="question-text">{{ question.question }}</span>
        </div>

        <!-- 选项列表 -->
        <div class="options-list">
          <label
            v-for="(option, oIndex) in question.options"
            :key="oIndex"
            class="option-item"
            :class="{
              selected: isOptionSelected(qIndex, option.label),
              disabled: hasAnswered
            }"
            @click="handleOptionClick(qIndex, option.label)"
          >
            <input
              :type="question.multiSelect ? 'checkbox' : 'radio'"
              :name="`question-${qIndex}`"
              :value="option.label"
              :checked="isOptionSelected(qIndex, option.label)"
              :disabled="hasAnswered"
              @click.stop
              @change="handleOptionChange(qIndex, option.label)"
            >
            <div class="option-content">
              <span class="option-label">{{ option.label }}</span>
              <span
                v-if="option.description"
                class="option-description"
              >
                {{ option.description }}
              </span>
            </div>
          </label>

          <!-- Other 自定义输入 -->
          <div
            v-if="showCustomInput(qIndex)"
            class="custom-input-wrapper"
          >
            <label class="custom-input-label">自定义答案:</label>
            <input
              v-model="customAnswers[qIndex]"
              type="text"
              class="custom-input"
              placeholder="请输入自定义答案..."
              :disabled="hasAnswered"
            >
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div
        v-if="!hasAnswered"
        class="action-buttons"
      >
        <button
          class="btn btn-primary"
          :disabled="!canSubmit || isSubmitting"
          @click="handleSubmit"
        >
          {{ isSubmitting ? '提交中...' : '提交答案' }}
        </button>
        <button
          v-if="questionsData.length > 1"
          class="btn btn-secondary"
          :disabled="isSubmitting"
          @click="clearSelection"
        >
          清除选择
        </button>
      </div>

      <!-- 已回答状态 -->
      <div
        v-else
        class="answered-state"
      >
        <div class="answer-preview">
          <strong>您的答案:</strong>
          <div class="answer-list">
            <div
              v-for="(answer, header) in submittedAnswers"
              :key="header"
              class="answer-item"
            >
              <span class="answer-header">{{ header }}:</span>
              <span class="answer-value">{{ answer }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 错误提示 -->
      <div
        v-if="errorMessage"
        class="error-message"
      >
        <span class="error-icon">⚠️</span>
        <span class="error-text">{{ errorMessage }}</span>
        <button
          class="btn-retry"
          @click="retrySubmit"
        >
          重试
        </button>
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
import { ideaBridge } from '@/services/ideaBridge'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface QuestionOption {
  label: string
  description?: string
}

interface Question {
  question: string
  header?: string
  options: QuestionOption[]
  multiSelect: boolean
}

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()

// 状态
const expanded = ref(true)
const selectedOptions = ref<Map<number, Set<string>>>(new Map())
const customAnswers = ref<Record<number, string>>({})
const isSubmitting = ref(false)
const hasAnswered = ref(false)
const submittedAnswers = ref<Record<string, string>>({})
const errorMessage = ref('')

// 解析问题数据
const questionsData = computed((): Question[] => {
  const input = props.toolUse.input

  // 支持两种格式:
  // 1. 新格式: { questions: [...] }
  // 2. 旧格式: { question, options, multiSelect }
  if (input.questions && Array.isArray(input.questions)) {
    return input.questions.map((q: any) => ({
      question: q.question || '',
      header: q.header || '问题',
      options: q.options || [],
      multiSelect: q.multiSelect || false
    }))
  } else {
    // 兼容旧格式
    return [{
      question: input.question || '',
      header: '问题',
      options: (input.options || []).map((opt: string | QuestionOption) =>
        typeof opt === 'string' ? { label: opt } : opt
      ),
      multiSelect: input.multiSelect || false
    }]
  }
})

// 初始化选项集合
questionsData.value.forEach((_, index) => {
  if (!selectedOptions.value.has(index)) {
    selectedOptions.value.set(index, new Set())
  }
})

// 判断选项是否被选中
function isOptionSelected(questionIndex: number, optionLabel: string): boolean {
  return selectedOptions.value.get(questionIndex)?.has(optionLabel) || false
}

// 是否显示自定义输入
function showCustomInput(questionIndex: number): boolean {
  const question = questionsData.value[questionIndex]
  const hasOtherOption = question.options.some(opt =>
    opt.label.toLowerCase() === 'other' || opt.label === '其他'
  )
  return hasOtherOption && (
    isOptionSelected(questionIndex, 'Other') ||
    isOptionSelected(questionIndex, '其他')
  )
}

// 处理选项点击(整个 label 可点击)
function handleOptionClick(questionIndex: number, optionLabel: string) {
  if (hasAnswered.value) return
  handleOptionChange(questionIndex, optionLabel)
}

// 处理选项变化
function handleOptionChange(questionIndex: number, optionLabel: string) {
  if (hasAnswered.value) return

  const question = questionsData.value[questionIndex]
  const options = selectedOptions.value.get(questionIndex)!

  if (question.multiSelect) {
    // 多选模式
    if (options.has(optionLabel)) {
      options.delete(optionLabel)
    } else {
      options.add(optionLabel)
    }
  } else {
    // 单选模式
    options.clear()
    options.add(optionLabel)
  }

  // 触发响应式更新
  selectedOptions.value = new Map(selectedOptions.value)
}

// 清除所有选择
function clearSelection() {
  selectedOptions.value.forEach((options) => options.clear())
  customAnswers.value = {}
  selectedOptions.value = new Map(selectedOptions.value)
}

// 是否可以提交
const canSubmit = computed(() => {
  // 至少有一个问题有答案
  for (let i = 0; i < questionsData.value.length; i++) {
    const options = selectedOptions.value.get(i)
    const hasSelection = options && options.size > 0
    const hasCustom = customAnswers.value[i]?.trim()

    if (hasSelection || hasCustom) {
      return true
    }
  }
  return false
})

// 提交答案
async function handleSubmit() {
  if (!canSubmit.value || isSubmitting.value) return

  // 构建答案对象 { header: answer }
  const answers: Record<string, string> = {}

  questionsData.value.forEach((question, index) => {
    const header = question.header || `问题${index + 1}`
    const options = selectedOptions.value.get(index)
    const custom = customAnswers.value[index]?.trim()

    if (options && options.size > 0) {
      const selected = Array.from(options)

      // 如果有自定义输入,替换 Other
      if (custom && (selected.includes('Other') || selected.includes('其他'))) {
        const filtered = selected.filter(s => s !== 'Other' && s !== '其他')
        filtered.push(custom)
        answers[header] = question.multiSelect ? filtered.join(', ') : filtered[0]
      } else {
        answers[header] = question.multiSelect ? selected.join(', ') : selected[0]
      }
    } else if (custom) {
      answers[header] = custom
    }
  })

  // 提交答案
  isSubmitting.value = true
  errorMessage.value = ''

  try {
    const response = await ideaBridge.query('tool.askUserQuestion.answer', {
      toolUseId: props.toolUse.id,
      answers: answers
    })

    if (response.success) {
      hasAnswered.value = true
      submittedAnswers.value = answers
      console.log('答案已提交:', answers)
    } else {
      errorMessage.value = response.error || '提交失败'
    }
  } catch (error) {
    console.error('Failed to submit answer:', error)
    errorMessage.value = String(error)
  } finally {
    isSubmitting.value = false
  }
}

// 重试提交
function retrySubmit() {
  errorMessage.value = ''
  handleSubmit()
}
</script>

<style scoped>
.ask-question-tool {
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  background: var(--ide-background, #ffffff);
  margin: 8px 0;
  transition: opacity 0.3s;
}

.ask-question-tool.answered {
  opacity: 0.85;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: var(--ide-accent, #8b5cf6);
}

.answered-badge {
  padding: 2px 8px;
  background: var(--ide-success, #22863a);
  color: white;
  border-radius: 12px;
  font-size: 11px;
  margin-left: auto;
}

.question-count {
  font-size: 11px;
  font-weight: 600;
  background: var(--ide-accent, #8b5cf6);
  color: white;
  padding: 2px 8px;
  border-radius: 10px;
}

.tool-content {
  padding: 12px;
}

.question-block {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.question-block:last-of-type {
  border-bottom: none;
  margin-bottom: 0;
}

.question-header {
  margin-bottom: 2px;
}

.question-tag {
  display: inline-block;
  padding: 3px 8px;
  background: var(--ide-selection-background, #e3f2fd);
  color: var(--ide-accent, #8b5cf6);
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  margin-right: 8px;
  letter-spacing: 0.5px;
}

.question-text {
  font-size: 15px;
  font-weight: 500;
  line-height: 1.5;
  color: var(--ide-foreground, #24292e);
}

.options-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 2px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: var(--ide-editor-background, #ffffff);
}

.option-item:hover:not(.disabled) {
  background: var(--ide-hover-background, #f6f8fa);
  border-color: var(--ide-accent, #8b5cf6);
  transform: translateX(2px);
}

.option-item.selected {
  background: var(--ide-selection-background, #e3f2fd);
  border-color: var(--ide-accent, #8b5cf6);
  box-shadow: 0 0 0 1px var(--ide-accent, #8b5cf6);
}

.option-item.disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.option-item input[type="radio"],
.option-item input[type="checkbox"] {
  margin-top: 2px;
  cursor: pointer;
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.option-item.disabled input {
  cursor: not-allowed;
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.option-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--ide-foreground, #24292e);
}

.option-description {
  font-size: 12px;
  color: var(--ide-secondary-text, #6a737d);
  line-height: 1.4;
}

.custom-input-wrapper {
  margin-top: 4px;
  padding: 12px;
  background: var(--ide-hover-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
}

.custom-input-label {
  display: block;
  font-size: 12px;
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
  margin-bottom: 6px;
}

.custom-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  background: var(--ide-editor-background, #ffffff);
  color: var(--ide-foreground, #24292e);
  font-size: 14px;
  font-family: inherit;
  transition: border-color 0.2s;
}

.custom-input:focus {
  outline: none;
  border-color: var(--ide-accent, #8b5cf6);
  box-shadow: 0 0 0 1px var(--ide-accent, #8b5cf6);
}

.custom-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.action-buttons {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 16px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: var(--ide-accent, #8b5cf6);
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: var(--ide-accent-dark, #7c3aed);
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(139, 92, 246, 0.3);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--ide-button-background, #f6f8fa);
  color: var(--ide-foreground, #24292e);
  border: 1px solid var(--ide-border, #e1e4e8);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--ide-hover-background, #e1e4e8);
}

.answered-state {
  padding: 6px 8px;
  background: var(--ide-success-background, #e6ffed);
  border: 1px solid var(--ide-success, #22863a);
  border-radius: 6px;
  margin-top: 12px;
}

.answer-preview strong {
  display: block;
  margin-bottom: 2px;
  color: var(--ide-success, #22863a);
  font-size: 14px;
}

.answer-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.answer-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
}

.answer-header {
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
  min-width: 80px;
}

.answer-value {
  color: var(--ide-secondary-text, #6a737d);
}

.error-message {
  padding: 12px;
  background: var(--ide-error-background, #ffeef0);
  color: var(--ide-error, #d73a49);
  border: 1px solid var(--ide-error, #d73a49);
  border-radius: 6px;
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.error-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.error-text {
  flex: 1;
  font-size: 13px;
}

.btn-retry {
  padding: 4px 12px;
  background: var(--ide-error, #d73a49);
  color: white;
  border: none;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.btn-retry:hover {
  background: var(--ide-error-dark, #cb2431);
}

.expand-btn {
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-button-background, #fafbfc);
  color: var(--ide-secondary-text, #586069);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.expand-btn:hover {
  background: var(--ide-hover-background, #f6f8fa);
}
</style>

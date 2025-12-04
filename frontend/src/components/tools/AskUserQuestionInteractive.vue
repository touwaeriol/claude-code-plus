<template>
  <div v-if="pendingQuestion" class="ask-user-container">
    <div class="ask-user-card">
      <div class="ask-user-header">
        <span class="ask-user-icon">❓</span>
        <span class="ask-user-title">{{ $t('askUser.title', 'Claude 需要您的回答') }}</span>
      </div>

      <div class="questions-container">
        <div
          v-for="(q, qIndex) in pendingQuestion.questions"
          :key="qIndex"
          class="question-block"
        >
          <div class="question-label">
            <span class="question-header-tag">{{ q.header }}</span>
            <span class="question-text">{{ q.question }}</span>
          </div>

          <div class="options-container">
            <template v-if="q.multiSelect">
              <!-- 多选模式 -->
              <label
                v-for="(opt, optIndex) in q.options"
                :key="optIndex"
                class="option-item option-checkbox"
                :class="{ selected: isMultiSelected(q.header, opt.label) }"
              >
                <span class="checkbox-indicator">{{ isMultiSelected(q.header, opt.label) ? '[√]' : '[ ]' }}</span>
                <input
                  type="checkbox"
                  :checked="isMultiSelected(q.header, opt.label)"
                  class="hidden-input"
                  @change="toggleMultiOption(q.header, opt.label)"
                />
                <span class="option-content">
                  <span class="option-label">{{ opt.label }}</span>
                  <span v-if="opt.description" class="option-description">{{ opt.description }}</span>
                </span>
              </label>

              <!-- 多选模式的自定义输入 -->
              <div class="option-item option-other-multi">
                <span class="checkbox-indicator">{{ multiOtherInputs[q.header] ? '[√]' : '[ ]' }}</span>
                <input
                  v-model="multiOtherInputs[q.header]"
                  type="text"
                  class="other-input-inline"
                  :placeholder="$t('askUser.typeSomething', 'Type something')"
                  @input="updateMultiOtherAnswer(q.header)"
                />
              </div>
            </template>

            <template v-else>
              <!-- 单选模式：点击后立即提交 -->
              <div
                v-for="(opt, optIndex) in q.options"
                :key="optIndex"
                class="option-item option-radio clickable"
                :class="{ selected: selectedAnswers[q.header] === opt.label }"
                @click="selectAndSubmitSingle(q.header, opt.label)"
              >
                <span class="radio-indicator">{{ selectedAnswers[q.header] === opt.label ? '(●)' : '( )' }}</span>
                <span class="option-content">
                  <span class="option-label">{{ opt.label }}</span>
                  <span v-if="opt.description" class="option-description">{{ opt.description }}</span>
                </span>
              </div>

              <!-- 单选模式的自定义输入 -->
              <div class="option-item option-other-single">
                <span class="radio-indicator">{{ showOtherInput[q.header] ? '(●)' : '( )' }}</span>
                <input
                  v-model="otherInputs[q.header]"
                  type="text"
                  class="other-input-inline"
                  :placeholder="$t('askUser.typeSomething', 'Type something')"
                  @focus="enableOtherInput(q.header)"
                  @keydown.enter="submitSingleOther(q.header)"
                />
                <button
                  v-if="showOtherInput[q.header] && otherInputs[q.header]"
                  class="btn-submit-inline"
                  @click="submitSingleOther(q.header)"
                >
                  ↵
                </button>
              </div>
            </template>
          </div>
        </div>
      </div>

      <!-- 底部按钮：仅多选模式显示提交按钮 -->
      <div class="ask-user-actions">
        <button class="btn-cancel" @click="handleCancel">
          {{ $t('askUser.cancel', '取消') }}
        </button>
        <button
          v-if="hasMultiSelectQuestion"
          class="btn-submit"
          :disabled="!canSubmit"
          @click="handleSubmit"
        >
          {{ $t('askUser.submit', 'Submit') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, reactive } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'

const sessionStore = useSessionStore()

// 获取当前待回答的问题（只取第一个）
const pendingQuestion = computed(() => {
  const questions = sessionStore.getCurrentPendingQuestions()
  return questions.length > 0 ? questions[0] : null
})

// 用户选择的答案
const selectedAnswers = reactive<Record<string, string>>({})

// 多选模式的选中项
const multiSelectedAnswers = reactive<Record<string, string[]>>({})

// 单选模式 Other 输入状态
const showOtherInput = reactive<Record<string, boolean>>({})
const otherInputs = reactive<Record<string, string>>({})

// 多选模式 Other 输入
const multiOtherInputs = reactive<Record<string, string>>({})

// 是否有多选问题
const hasMultiSelectQuestion = computed(() => {
  return pendingQuestion.value?.questions.some(q => q.multiSelect) ?? false
})

// 当问题变化时重置状态
watch(pendingQuestion, (newQuestion) => {
  if (newQuestion) {
    // 清空之前的选择
    Object.keys(selectedAnswers).forEach(key => delete selectedAnswers[key])
    Object.keys(multiSelectedAnswers).forEach(key => delete multiSelectedAnswers[key])
    Object.keys(showOtherInput).forEach(key => delete showOtherInput[key])
    Object.keys(otherInputs).forEach(key => delete otherInputs[key])
    Object.keys(multiOtherInputs).forEach(key => delete multiOtherInputs[key])

    // 初始化多选数组
    newQuestion.questions.forEach(q => {
      if (q.multiSelect) {
        multiSelectedAnswers[q.header] = []
      }
    })
  }
})

// 单选：选择选项并立即提交
function selectAndSubmitSingle(header: string, label: string) {
  selectedAnswers[header] = label
  showOtherInput[header] = false

  // 检查是否所有单选问题都已回答
  const allSingleAnswered = pendingQuestion.value?.questions
    .filter(q => !q.multiSelect)
    .every(q => selectedAnswers[q.header]) ?? false

  // 如果没有多选问题，且所有单选都已回答，立即提交
  if (!hasMultiSelectQuestion.value && allSingleAnswered) {
    handleSubmit()
  }
}

// 单选模式 Other：启用输入
function enableOtherInput(header: string) {
  showOtherInput[header] = true
  delete selectedAnswers[header]
}

// 单选模式 Other：提交
function submitSingleOther(header: string) {
  if (otherInputs[header]?.trim()) {
    selectedAnswers[header] = otherInputs[header].trim()

    // 如果没有多选问题，立即提交
    if (!hasMultiSelectQuestion.value) {
      handleSubmit()
    }
  }
}

// 多选：切换选项
function toggleMultiOption(header: string, label: string) {
  if (!multiSelectedAnswers[header]) {
    multiSelectedAnswers[header] = []
  }
  const index = multiSelectedAnswers[header].indexOf(label)
  if (index === -1) {
    multiSelectedAnswers[header].push(label)
  } else {
    multiSelectedAnswers[header].splice(index, 1)
  }
  // 同步到 selectedAnswers
  syncMultiSelectAnswer(header)
}

// 检查多选是否已选中
function isMultiSelected(header: string, label: string): boolean {
  return multiSelectedAnswers[header]?.includes(label) ?? false
}

// 多选模式 Other：更新
function updateMultiOtherAnswer(header: string) {
  syncMultiSelectAnswer(header)
}

// 同步多选答案到 selectedAnswers
function syncMultiSelectAnswer(header: string) {
  const selected = [...(multiSelectedAnswers[header] || [])]
  const otherText = multiOtherInputs[header]?.trim()
  if (otherText) {
    selected.push(otherText)
  }
  selectedAnswers[header] = selected.join(', ')
}

// 是否可以提交
const canSubmit = computed(() => {
  if (!pendingQuestion.value) return false
  // 检查所有问题是否都有答案
  return pendingQuestion.value.questions.every(q => {
    const answer = selectedAnswers[q.header]
    return answer && answer.trim().length > 0
  })
})

// 提交答案
function handleSubmit() {
  if (!pendingQuestion.value) return

  // 对于没有多选的情况，检查是否有答案
  const hasAnswers = Object.keys(selectedAnswers).length > 0
  if (!hasAnswers) return

  // 将 answers 的 key 从 header 转换为 question
  const answersWithQuestionKey: Record<string, string> = {}
  for (const q of pendingQuestion.value.questions) {
    const answer = selectedAnswers[q.header]
    if (answer) {
      answersWithQuestionKey[q.question] = answer
    }
  }

  sessionStore.answerQuestion(pendingQuestion.value.id, answersWithQuestionKey)
}

// 取消
function handleCancel() {
  if (!pendingQuestion.value) return

  sessionStore.cancelQuestion(pendingQuestion.value.id)
}
</script>

<style scoped>
.ask-user-container {
  position: fixed;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  max-width: 600px;
  width: calc(100% - 32px);
}

.ask-user-card {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.ask-user-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--theme-accent, #0366d6);
  color: white;
}

.ask-user-icon {
  font-size: 18px;
}

.ask-user-title {
  font-size: 14px;
  font-weight: 600;
}

.questions-container {
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.question-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.question-label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.question-header-tag {
  background: var(--theme-accent-subtle, #f1f8ff);
  color: var(--theme-accent, #0366d6);
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
}

.question-text {
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.options-container {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-left: 4px;
}

.option-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 10px;
  background: var(--theme-panel-background, #f6f8fa);
  border: 1px solid transparent;
  border-radius: 6px;
  transition: all 0.15s ease;
}

.option-item.clickable {
  cursor: pointer;
}

.option-item.clickable:hover {
  background: var(--theme-hover-background, #e8e8e8);
}

.option-item.selected {
  background: var(--theme-accent-subtle, #f1f8ff);
  border-color: var(--theme-accent, #0366d6);
}

.hidden-input {
  position: absolute;
  opacity: 0;
  pointer-events: none;
}

.checkbox-indicator,
.radio-indicator {
  font-family: monospace;
  font-size: 13px;
  color: var(--theme-accent, #0366d6);
  min-width: 24px;
  user-select: none;
}

.option-content {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
}

.option-label {
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  font-weight: 500;
}

.option-description {
  font-size: 11px;
  color: var(--theme-secondary-foreground, #586069);
}

.option-other-single,
.option-other-multi {
  display: flex;
  align-items: center;
  gap: 8px;
}

.other-input-inline {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  background: var(--theme-background, #ffffff);
  color: var(--theme-foreground, #24292e);
}

.other-input-inline:focus {
  outline: none;
  border-color: var(--theme-accent, #0366d6);
}

.other-input-inline::placeholder {
  color: var(--theme-secondary-foreground, #586069);
  font-style: italic;
}

.btn-submit-inline {
  padding: 4px 8px;
  background: var(--theme-accent, #0366d6);
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}

.btn-submit-inline:hover {
  background: var(--theme-accent-hover, #0256b9);
}

.ask-user-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.btn-cancel,
.btn-submit {
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-cancel {
  background: transparent;
  border: 1px solid var(--theme-border, #e1e4e8);
  color: var(--theme-foreground, #24292e);
}

.btn-cancel:hover {
  background: var(--theme-hover-background, #f0f0f0);
}

.btn-submit {
  background: var(--theme-accent, #0366d6);
  border: 1px solid var(--theme-accent, #0366d6);
  color: white;
}

.btn-submit:hover:not(:disabled) {
  background: var(--theme-accent-hover, #0256b9);
}

.btn-submit:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

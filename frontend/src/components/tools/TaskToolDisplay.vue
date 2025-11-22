<template>
  <div
    class="tool-display task-tool"
    :class="statusClass"
  >
    <div class="tool-header">
      <span class="tool-icon">🤖</span>
      <span class="tool-name">子任务</span>
      <span class="task-info">
        <span class="task-description">{{ description }}</span>
        <span class="agent-type-badge">{{ subagentType }}</span>
        <span
          class="status-badge"
          :class="statusClass"
        >{{ statusText }}</span>
      </span>
    </div>
    <div
      v-if="expanded"
      class="tool-content"
    >
      <!-- 任务元信息 -->
      <div class="task-meta">
        <div class="meta-row">
          <span class="meta-label">代理类型:</span>
          <span class="meta-value agent-type">{{ subagentType }}</span>
        </div>
        <div
          v-if="model"
          class="meta-row"
        >
          <span class="meta-label">模型:</span>
          <span class="meta-value">{{ model }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label">状态:</span>
          <span
            class="meta-value"
            :class="statusClass"
          >
            {{ statusIcon }} {{ statusText }}
          </span>
        </div>
        <div
          v-if="progress !== null"
          class="meta-row"
        >
          <span class="meta-label">进度:</span>
          <div class="progress-bar">
            <div
              class="progress-fill"
              :style="{ width: `${progress}%` }"
            />
            <span class="progress-text">{{ progress }}%</span>
          </div>
        </div>
      </div>

      <!-- 任务提示 -->
      <div class="task-prompt-section">
        <div
          class="section-header"
          @click="promptExpanded = !promptExpanded"
        >
          <span class="section-title">任务提示</span>
          <span class="toggle-icon">{{ promptExpanded ? '▼' : '▶' }}</span>
        </div>
        <div
          v-if="promptExpanded"
          class="prompt-content"
        >
          <pre>{{ displayPrompt }}</pre>
          <button
            v-if="isPromptTruncated"
            class="show-more-btn"
            @click="showFullPrompt = !showFullPrompt"
          >
            {{ showFullPrompt ? '收起' : '查看完整提示' }}
          </button>
        </div>
      </div>

      <!-- 执行步骤 -->
      <div
        v-if="steps && steps.length > 0"
        class="steps-section"
      >
        <div class="section-header">
          <span class="section-title">执行步骤</span>
        </div>
        <div class="steps-tree">
          <div
            v-for="(step, index) in steps"
            :key="index"
            class="step-item"
            :class="`status-${step.status}`"
          >
            <div
              class="step-header"
              @click="toggleStep(index)"
            >
              <span class="step-icon">{{ getStepIcon(step.status) }}</span>
              <span class="step-number">{{ step.step }}.</span>
              <span class="step-action">{{ step.action }}</span>
              <span
                v-if="step.output"
                class="expand-icon"
              >
                {{ expandedSteps.has(index) ? '▼' : '▶' }}
              </span>
            </div>

            <div
              v-if="expandedSteps.has(index) && step.output"
              class="step-output"
            >
              <pre>{{ step.output }}</pre>
            </div>
          </div>
        </div>
      </div>

      <!-- 任务输出 -->
      <div
        v-if="output"
        class="output-section"
      >
        <div class="section-header">
          <span class="section-title">输出</span>
          <button
            class="copy-btn"
            title="复制"
            @click="copyOutput"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
              <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
            </svg>
          </button>
        </div>
        <div class="output-content">
          <MarkdownRenderer
            v-if="isMarkdownOutput"
            :content="output"
            :is-dark="isDark"
          />
          <pre v-else>{{ output }}</pre>
        </div>
      </div>

      <!-- 错误信息 -->
      <div
        v-if="error"
        class="error-section"
      >
        <div class="error-header">
          <span class="error-icon">❌</span>
          <span class="error-title">执行失败</span>
        </div>
        <div class="error-message">
          {{ error }}
        </div>
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
import { useToastStore } from '@/stores/toastStore'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
  isDark?: boolean
}

interface TaskStep {
  step: number
  action: string
  status: 'pending' | 'running' | 'completed' | 'failed'
  output?: string
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

const toastStore = useToastStore()
const expanded = ref(false)
const promptExpanded = ref(true)
const showFullPrompt = ref(false)
const expandedSteps = ref(new Set<number>())

// 解析工具参数
const description = computed(() => props.toolUse.input.description || '未命名任务')
const prompt = computed(() => props.toolUse.input.prompt || '')
const subagentType = computed(() => props.toolUse.input.subagent_type || 'general-purpose')
const model = computed(() => props.toolUse.input.model)

// 解析结果
const result = computed(() => {
  if (!props.result) return null
  const content = props.result.content
  if (typeof content === 'string') {
    try {
      return JSON.parse(content)
    } catch {
      return { output: content }
    }
  }
  return content
})

const output = computed(() => result.value?.output || '')
const error = computed(() => result.value?.error || '')
const steps = computed(() => result.value?.steps || [])

// 状态计算
const status = computed(() => {
  if (error.value) return 'failed'
  if (!result.value) return 'pending'
  if (steps.value.some((s: TaskStep) => s.status === 'running')) return 'running'
  if (steps.value.length > 0 && steps.value.every((s: TaskStep) => s.status === 'completed')) return 'completed'
  if (result.value?.success === false) return 'failed'
  if (result.value?.success === true) return 'completed'
  return 'running'
})

const statusText = computed(() => {
  const texts: Record<string, string> = {
    pending: '等待中',
    running: '执行中',
    completed: '已完成',
    failed: '失败'
  }
  return texts[status.value] || status.value
})

const statusIcon = computed(() => {
  const icons: Record<string, string> = {
    pending: '⏳',
    running: '⚙️',
    completed: '✅',
    failed: '❌'
  }
  return icons[status.value] || '●'
})

const statusClass = computed(() => `status-${status.value}`)

// 进度计算
const progress = computed(() => {
  if (steps.value.length === 0) return null
  const completed = steps.value.filter((s: TaskStep) => s.status === 'completed').length
  return Math.round((completed / steps.value.length) * 100)
})

// 提示显示
const displayPrompt = computed(() => {
  const lines = prompt.value.split('\n')
  if (showFullPrompt.value || lines.length <= 10) {
    return prompt.value
  }
  return lines.slice(0, 10).join('\n') + '\n...'
})

const isPromptTruncated = computed(() => {
  return prompt.value.split('\n').length > 10
})

// 输出格式检测
const isMarkdownOutput = computed(() => {
  const text = output.value
  return text.includes('#') || text.includes('```') || text.includes('*') || text.includes('-')
})

// 方法
function toggleStep(index: number) {
  if (expandedSteps.value.has(index)) {
    expandedSteps.value.delete(index)
  } else {
    expandedSteps.value.add(index)
  }
  expandedSteps.value = new Set(expandedSteps.value)
}

function getStepIcon(status: string): string {
  const icons: Record<string, string> = {
    pending: '⏳',
    running: '⚙️',
    completed: '✅',
    failed: '❌'
  }
  return icons[status] || '●'
}

async function copyOutput() {
  try {
    await navigator.clipboard.writeText(output.value)
    toastStore.success('输出已复制')
  } catch (error) {
    console.error('Failed to copy:', error)
    toastStore.error('复制失败')
  }
}
</script>

<style scoped>
.task-tool {
  border-left: 4px solid #6c757d;
}

.task-tool.status-pending {
  border-left-color: #6c757d;
}

.task-tool.status-running {
  border-left-color: #0366d6;
}

.task-tool.status-completed {
  border-left-color: #22863a;
}

.task-tool.status-failed {
  border-left-color: #d73a49;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.task-tool .tool-name {
  color: #6f42c1;
}

.task-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.task-description {
  font-weight: 600;
  color: #24292e;
}

.agent-type-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  background: #6f42c1;
  color: white;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
}

.status-badge.status-pending {
  background: #6c757d;
  color: white;
}

.status-badge.status-running {
  background: #0366d6;
  color: white;
}

.status-badge.status-completed {
  background: #22863a;
  color: white;
}

.status-badge.status-failed {
  background: #d73a49;
  color: white;
}

.task-meta {
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.meta-label {
  color: #586069;
  font-weight: 600;
  min-width: 70px;
}

.meta-value {
  color: #24292e;
}

.agent-type {
  font-family: 'Consolas', 'Monaco', monospace;
  font-weight: 600;
}

.progress-bar {
  position: relative;
  flex: 1;
  height: 20px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 10px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: #22863a;
  transition: width 0.3s;
}

.progress-text {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 11px;
  font-weight: 600;
  color: #24292e;
}

.task-prompt-section,
.steps-section,
.output-section,
.error-section {
  margin: 12px 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
  cursor: pointer;
  user-select: none;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #586069;
}

.toggle-icon {
  font-size: 12px;
  color: #586069;
}

.prompt-content pre {
  margin: 0;
  padding: 8px;
  background: #f6f8fa;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
}

.show-more-btn {
  margin-top: 6px;
  padding: 4px 8px;
  font-size: 11px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  cursor: pointer;
  color: #0366d6;
}

.show-more-btn:hover {
  background: #f6f8fa;
}

.steps-tree {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-item {
  padding: 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.step-item:hover {
  background: #f6f8fa;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  user-select: none;
}

.step-icon {
  font-size: 16px;
}

.step-number {
  font-weight: 600;
  color: #586069;
}

.step-action {
  flex: 1;
  font-size: 13px;
  color: #24292e;
}

.expand-icon {
  font-size: 12px;
  color: #586069;
}

.step-output {
  margin-top: 8px;
  padding: 8px;
  background: #f6f8fa;
  border-radius: 4px;
  border-left: 3px solid #0366d6;
}

.step-output pre {
  margin: 0;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
}

.output-content pre {
  margin: 0;
  padding: 8px;
  background: #f6f8fa;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
}

.error-section {
  background: #ffeef0;
  border-color: #d73a49;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.error-icon {
  font-size: 18px;
}

.error-title {
  font-size: 14px;
  font-weight: 600;
  color: #d73a49;
}

.error-message {
  padding: 8px;
  background: #ffffff;
  border-radius: 4px;
  font-size: 13px;
  color: #d73a49;
}

.copy-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
  border: none;
  border-radius: 3px;
  background: transparent;
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
  opacity: 0.6;
}

.copy-btn:hover {
  opacity: 1;
  background: var(--ide-panel-background, #f6f8fa);
}
</style>

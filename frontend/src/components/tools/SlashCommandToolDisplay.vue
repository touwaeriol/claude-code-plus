<template>
  <div
    class="tool-display slashcommand-tool"
    :class="statusClass"
  >
    <!-- 工具头部 -->
    <div class="tool-header">
      <span class="tool-icon">⚡</span>
      <span class="tool-name">斜杠命令</span>
      <code class="command-text">{{ command }}</code>
      <span
        class="status-badge"
        :class="statusClass"
      >{{ statusText }}</span>
    </div>

    <!-- 展开内容 -->
    <div
      v-if="expanded"
      class="tool-content"
    >
      <!-- 命令元信息 -->
      <div class="command-meta">
        <div class="meta-row">
          <span class="meta-label">命令:</span>
          <code class="meta-value command-code">{{ command }}</code>
        </div>
        <div
          v-if="commandName"
          class="meta-row"
        >
          <span class="meta-label">命令名:</span>
          <span class="meta-value">{{ commandName }}</span>
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
      </div>

      <!-- 展开的提示 -->
      <div
        v-if="expandedPrompt"
        class="expanded-prompt-section"
      >
        <div
          class="section-header"
          @click="promptExpanded = !promptExpanded"
        >
          <span class="section-title">展开的提示</span>
          <span class="toggle-icon">{{ promptExpanded ? '▼' : '▶' }}</span>
        </div>
        <div
          v-if="promptExpanded"
          class="prompt-content"
        >
          <MarkdownRenderer
            v-if="isMarkdownPrompt"
            :content="displayPrompt"
            :is-dark="isDark"
          />
          <pre
            v-else
            class="prompt-text"
          >{{ displayPrompt }}</pre>

          <button
            v-if="isPromptTruncated"
            class="toggle-btn"
            @click="showFullPrompt = !showFullPrompt"
          >
            {{ showFullPrompt ? '收起' : '查看完整提示' }}
          </button>
        </div>
      </div>

      <!-- 命令输出 -->
      <div
        v-if="output"
        class="output-section"
      >
        <div class="section-header">
          <span class="section-title">输出</span>
          <button
            class="btn-copy"
            @click="copyOutput"
          >
            📋 复制
          </button>
        </div>
        <div class="output-content">
          <MarkdownRenderer
            v-if="isMarkdownOutput"
            :content="displayOutput"
            :is-dark="isDark"
          />
          <pre
            v-else
            class="output-text"
          >{{ displayOutput }}</pre>

          <button
            v-if="isOutputTruncated"
            class="toggle-btn"
            @click="showFullOutput = !showFullOutput"
          >
            {{ showFullOutput ? '收起' : '查看完整输出' }}
          </button>
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

      <!-- 操作按钮 -->
      <div
        v-if="output"
        class="actions"
      >
        <button
          class="btn btn-primary"
          @click="copyOutput"
        >
          📋 复制输出
        </button>
      </div>
    </div>

    <!-- 展开/收起按钮 -->
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
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

const expanded = ref(false)
const promptExpanded = ref(false)
const showFullPrompt = ref(false)
const showFullOutput = ref(false)

// 解析工具参数
const command = computed(() => props.toolUse.input.command || '')

// 解析结果
const parsedResult = computed(() => {
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

const output = computed(() => parsedResult.value?.output || '')
const error = computed(() => parsedResult.value?.error || '')
const commandName = computed(() => parsedResult.value?.command_name || '')
const expandedPrompt = computed(() => parsedResult.value?.expanded_prompt || '')

// 状态计算
const status = computed(() => {
  if (error.value) return 'failed'
  if (!parsedResult.value) return 'pending'
  return parsedResult.value.success ? 'completed' : 'failed'
})

const statusText = computed(() => {
  const texts: Record<string, string> = {
    pending: '等待中',
    completed: '执行成功',
    failed: '执行失败'
  }
  return texts[status.value] || status.value
})

const statusIcon = computed(() => {
  const icons: Record<string, string> = {
    pending: '⏳',
    completed: '✅',
    failed: '❌'
  }
  return icons[status.value] || '●'
})

const statusClass = computed(() => `status-${status.value}`)

// 提示显示
const displayPrompt = computed(() => {
  const text = expandedPrompt.value
  const lines = text.split('\n')

  if (showFullPrompt.value || lines.length <= 15) {
    return text
  }

  return lines.slice(0, 15).join('\n') + '\n...'
})

const isPromptTruncated = computed(() => {
  return expandedPrompt.value.split('\n').length > 15
})

const isMarkdownPrompt = computed(() => {
  const text = expandedPrompt.value
  return text.includes('#') || text.includes('```') || text.includes('*') || text.includes('-')
})

// 输出显示
const displayOutput = computed(() => {
  const text = output.value
  const lines = text.split('\n')

  if (showFullOutput.value || lines.length <= 20) {
    return text
  }

  return lines.slice(0, 20).join('\n') + '\n...'
})

const isOutputTruncated = computed(() => {
  return output.value.split('\n').length > 20
})

const isMarkdownOutput = computed(() => {
  const text = output.value
  return text.includes('#') || text.includes('```') || text.includes('*') || text.includes('-')
})

// 方法
async function copyOutput() {
  try {
    await navigator.clipboard.writeText(output.value)
  } catch (error) {
    console.error('Failed to copy:', error)
  }
}
</script>

<style scoped>
.slashcommand-tool {
  border-left-width: 4px;
}

.slashcommand-tool.status-pending {
  border-left-color: #6c757d;
}

.slashcommand-tool.status-completed {
  border-left-color: #28a745;
}

.slashcommand-tool.status-failed {
  border-left-color: #d73a49;
}

.tool-name {
  color: #6f42c1;
}

.command-text {
  padding: 2px 6px;
  background: rgba(111, 66, 193, 0.1);
  border: 1px solid rgba(111, 66, 193, 0.3);
  border-radius: 3px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  color: #6f42c1;
  font-weight: 600;
  margin-left: auto;
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

.status-badge.status-completed {
  background: #28a745;
  color: white;
}

.status-badge.status-failed {
  background: #d73a49;
  color: white;
}

.command-meta {
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
  min-width: 60px;
}

.meta-value {
  color: #24292e;
}

.command-code {
  padding: 2px 6px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  color: #6f42c1;
}

.expanded-prompt-section,
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

.prompt-content,
.output-content {
  position: relative;
}

.prompt-text,
.output-text {
  margin: 0;
  padding: 8px;
  background: #f6f8fa;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
  max-height: 400px;
  overflow-y: auto;
}

.toggle-btn {
  margin-top: 8px;
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  background: #fafbfc;
  color: #586069;
  cursor: pointer;
  transition: all 0.2s;
}

.toggle-btn:hover {
  background: #f6f8fa;
  color: #24292e;
}

.error-section {
  background: rgba(203, 36, 49, 0.05);
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
  white-space: pre-wrap;
  word-wrap: break-word;
}

.btn-copy {
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  background: #fafbfc;
  color: #586069;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-copy:hover {
  background: #f6f8fa;
  color: #24292e;
}

.actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
}

.btn {
  padding: 6px 12px;
  font-size: 13px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: #0366d6;
  color: white;
  border-color: #0366d6;
}

.btn-primary:hover {
  background: #0256c0;
  border-color: #0256c0;
}

/* 暗色主题适配 */
.theme-dark .meta-value,
.theme-dark .prompt-text,
.theme-dark .output-text {
  color: #e1e4e8;
}

.theme-dark .command-code {
  background: #2d333b;
  border-color: #444d56;
  color: #a371f7;
}

.theme-dark .expanded-prompt-section,
.theme-dark .output-section {
  background: #1e1e1e;
  border-color: #444d56;
}

.theme-dark .prompt-text,
.theme-dark .output-text {
  background: #2d333b;
}

.theme-dark .error-section {
  background: rgba(248, 81, 73, 0.1);
  border-color: #f85149;
}

.theme-dark .error-title {
  color: #f85149;
}

.theme-dark .error-message {
  background: #1e1e1e;
  color: #f85149;
}

.theme-dark .toggle-btn,
.theme-dark .btn-copy {
  background: #2d333b;
  border-color: #444d56;
  color: #e1e4e8;
}

.theme-dark .toggle-btn:hover,
.theme-dark .btn-copy:hover {
  background: #373e47;
}

.theme-dark .btn-primary {
  background: #238636;
  border-color: #238636;
}

.theme-dark .btn-primary:hover {
  background: #2ea043;
  border-color: #2ea043;
}
</style>

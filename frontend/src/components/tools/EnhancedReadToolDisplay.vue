<template>
  <div
    class="tool-display read-tool"
    :class="`status-${toolCall.status.toLowerCase()}`"
  >
    <div
      class="tool-header"
      @click="toggleExpanded"
    >
      <span class="tool-icon">📄</span>
      <span class="tool-name">Read</span>
      <span class="tool-file">{{ fileName }}</span>
      <span
        v-if="lineRange"
        class="tool-lines"
      >{{ lineRange }}</span>
      <span
        class="status-badge"
        :class="`status-${toolCall.status.toLowerCase()}`"
      >
        {{ getStatusText(toolCall.status) }}
      </span>
      <button class="expand-btn-inline">
        {{ expanded ? '▼' : '▶' }}
      </button>
    </div>

    <div
      v-if="expanded"
      class="tool-content"
    >
      <div class="file-info">
        <div class="info-row">
          <span class="label">{{ t('tools.label.path') }}:</span>
          <span
            class="value clickable"
            @click="openFile"
          >{{ filePath }}</span>
        </div>
        <div
          v-if="hasLineRange"
          class="info-row"
        >
          <span class="label">{{ t('tools.label.lines') }}:</span>
          <span class="value">{{ lineRange }}</span>
        </div>
        <div
          v-if="toolCall.startTime"
          class="info-row"
        >
          <span class="label">{{ t('tools.label.start') }}:</span>
          <span class="value">{{ formatTime(toolCall.startTime) }}</span>
        </div>
        <div
          v-if="toolCall.endTime"
          class="info-row"
        >
          <span class="label">{{ t('tools.label.duration') }}:</span>
          <span class="value">{{ duration }}ms</span>
        </div>
      </div>

      <!-- 成功结果 -->
      <div
        v-if="toolCall.result && toolCall.status === 'SUCCESS'"
        class="tool-result success"
      >
        <div class="result-header">
          <span>{{ t('tools.readTool.readResult') }}</span>
          <div class="result-actions">
            <button
              class="copy-btn"
              :title="t('tools.copyContent')"
              @click="copyContent"
            >
              📋
            </button>
            <button
              class="view-btn"
              :title="t('tools.openInEditor')"
              @click="openFile"
            >
              📂
            </button>
          </div>
        </div>
        <pre class="result-content">{{ getResultContent() }}</pre>
      </div>

      <!-- 失败结果 -->
      <div
        v-else-if="toolCall.result && toolCall.status === 'FAILED'"
        class="tool-result failure"
      >
        <div class="result-header error">
          <span>❌ {{ t('tools.status.failed') }}</span>
        </div>
        <pre class="result-content">{{ getErrorMessage() }}</pre>
      </div>

      <!-- 运行中 -->
      <div
        v-else-if="toolCall.status === 'RUNNING'"
        class="tool-result running"
      >
        <div class="loading-indicator">
          <div class="loading-spinner" />
          <span>{{ t('tools.readTool.reading') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from '@/composables/useI18n'
import { ideService } from '@/services/ideaBridge'
import type { ToolCall, ToolCallStatus } from '@/types/display'

const { t } = useI18n()

interface Props {
  toolCall: ToolCall
}

const props = defineProps<Props>()
const expanded = ref(false)

// 从 viewModel 中获取参数
const parameters = computed(() => {
  return props.toolCall.viewModel?.toolDetail?.parameters || {}
})

const filePath = computed(() => parameters.value.file_path || '')
const fileName = computed(() => {
  const path = filePath.value
  return path.split(/[\\/]/).pop() || path
})

const offset = computed(() => parameters.value.offset)
const limit = computed(() => parameters.value.limit)

const hasLineRange = computed(() => offset.value !== undefined || limit.value !== undefined)

const lineRange = computed(() => {
  if (!hasLineRange.value) return ''
  const start = offset.value || 1
  const end = limit.value ? start + limit.value - 1 : '∞'
  return `L${start}-${end}`
})

const duration = computed(() => {
  if (!props.toolCall.endTime) return 0
  return props.toolCall.endTime - props.toolCall.startTime
})

function toggleExpanded() {
  expanded.value = !expanded.value
}

function getStatusText(status: ToolCallStatus): string {
  const statusMap: Record<ToolCallStatus, string> = {
    PENDING: t('tools.status.pending'),
    RUNNING: t('tools.status.running'),
    SUCCESS: t('tools.status.success'),
    FAILED: t('tools.status.failed'),
    CANCELLED: t('tools.status.cancelled')
  }
  return statusMap[status] || status
}

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

function getResultContent(): string {
  const result = props.toolCall.result
  if (!result) return ''

  // 使用后端格式：直接读取 content
  if (typeof result.content === 'string') {
    return result.content
  }
  if (Array.isArray(result.content)) {
    return (result.content as any[])
      .filter((item: any) => item.type === 'text')
      .map((item: any) => item.text)
      .join('\n')
  }

  return JSON.stringify(result, null, 2)
}

function getErrorMessage(): string {
  const result = props.toolCall.result
  if (!result) return ''

  // 使用后端格式：is_error 为 true 时读取 content
  if (result.is_error) {
    if (typeof result.content === 'string') {
      return result.content
    }
    if (Array.isArray(result.content)) {
      return (result.content as any[])
        .filter((item: any) => item.type === 'text')
        .map((item: any) => item.text)
        .join('\n')
    }
  }

  return 'Unknown error'
}

async function openFile() {
  const line = offset.value || 1
  await ideService.openFile(filePath.value, line)
}

async function copyContent() {
  const content = getResultContent()
  if (content) {
    await navigator.clipboard.writeText(content)
  }
}
</script>

<style scoped>
.tool-display {
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  background: var(--theme-panel-background, #f6f8fa);
  margin: 8px 0;
  overflow: hidden;
  transition: all 0.2s ease;
}

.tool-display:hover {
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

/* 状态颜色 */
.status-pending {
  border-left: 3px solid var(--theme-warning, #ffc107);
}

.status-running {
  border-left: 3px solid var(--theme-accent, #0366d6);
}

.status-success {
  border-left: 3px solid var(--theme-success, #28a745);
}

.status-failed {
  border-left: 3px solid var(--theme-error, #d73a49);
}

.status-cancelled {
  border-left: 3px solid var(--theme-secondary-foreground, #6a737d);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  background: var(--theme-hover-background, #f6f8fa);
}

.tool-header:hover {
  background: var(--theme-hover-background, #e1e4e8);
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: var(--theme-accent, #0366d6);
}

.tool-file {
  font-family: 'Consolas', 'Monaco', monospace;
  color: var(--theme-foreground, #24292e);
  flex: 1;
}

.tool-lines {
  color: var(--theme-secondary-foreground, #586069);
  font-size: 11px;
  font-family: monospace;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
}

.status-badge.status-pending {
  background: var(--theme-warning, #ffc107);
  color: #000;
}

.status-badge.status-running {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.status-badge.status-success {
  background: var(--theme-success, #28a745);
  color: #fff;
}

.status-badge.status-failed {
  background: var(--theme-error, #d73a49);
  color: #fff;
}

.status-badge.status-cancelled {
  background: var(--theme-secondary-foreground, #6a737d);
  color: #fff;
}

.expand-btn-inline {
  padding: 0;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--theme-secondary-foreground, #586069);
  cursor: pointer;
  font-size: 10px;
}

.expand-btn-inline:hover {
  color: var(--theme-foreground, #24292e);
}

.tool-content {
  border-top: 1px solid var(--theme-border, #e1e4e8);
  padding: 12px;
  background: var(--theme-background, #ffffff);
}

.file-info {
  margin-bottom: 2px;
}

.info-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  font-size: 12px;
}

.info-row .label {
  font-weight: 600;
  color: var(--theme-secondary-foreground, #586069);
  min-width: 50px;
}

.info-row .value {
  font-family: 'Consolas', 'Monaco', monospace;
  color: var(--theme-foreground, #24292e);
}

.clickable {
  cursor: pointer;
  color: var(--theme-link, #0366d6);
  text-decoration: underline;
}

.clickable:hover {
  color: var(--theme-link, #0256c0);
}

.tool-result {
  background: var(--theme-code-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  overflow: hidden;
}

.tool-result.success {
  border-color: var(--theme-success, #28a745);
}

.tool-result.failure {
  border-color: var(--theme-error, #d73a49);
  background: rgba(215, 58, 73, 0.05);
}

.tool-result.running {
  border-color: var(--theme-accent, #0366d6);
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: var(--theme-panel-background, #f6f8fa);
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  font-size: 11px;
  font-weight: 600;
}

.result-header.error {
  background: rgba(215, 58, 73, 0.1);
  color: var(--theme-error, #d73a49);
}

.result-actions {
  display: flex;
  gap: 4px;
}

.copy-btn,
.view-btn {
  padding: 2px 6px;
  font-size: 11px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 3px;
  background: var(--theme-background, #ffffff);
  cursor: pointer;
  transition: all 0.2s;
}

.copy-btn:hover,
.view-btn:hover {
  background: var(--theme-hover-background, #f6f8fa);
  transform: translateY(-1px);
}

.result-content {
  margin: 0;
  padding: 12px;
  font-size: 11px;
  font-family: 'Consolas', 'Monaco', monospace;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.loading-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  justify-content: center;
}

.loading-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--theme-border, #e1e4e8);
  border-top-color: var(--theme-accent, #0366d6);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 滚动条样式 */
.result-content::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.result-content::-webkit-scrollbar-track {
  background: transparent;
}

.result-content::-webkit-scrollbar-thumb {
  background: var(--theme-scrollbar-thumb, #d1d5da);
  border-radius: 4px;
}

.result-content::-webkit-scrollbar-thumb:hover {
  background: var(--theme-scrollbar-thumb-hover, #959da5);
}
</style>

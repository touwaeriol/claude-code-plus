<template>
  <div
    class="tool-display mcp-tool"
    :class="statusClass"
  >
    <!-- 工具头部 -->
    <div class="tool-header">
      <span class="tool-icon">🔧</span>
      <span class="tool-name">MCP 工具</span>
      <code class="tool-name-code">{{ simplifiedToolName }}</code>
      <span
        v-if="serverName"
        class="server-badge"
      >{{ serverName }}</span>
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
      <!-- 工具元信息 -->
      <div class="tool-meta">
        <div class="meta-row">
          <span class="meta-label">工具名:</span>
          <code class="meta-value tool-code">{{ fullToolName }}</code>
        </div>
        <div
          v-if="serverName"
          class="meta-row"
        >
          <span class="meta-label">服务器:</span>
          <span class="meta-value server-name">{{ serverName }}</span>
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

      <!-- 参数显示 -->
      <div class="params-section">
        <div
          class="section-header"
          @click="paramsExpanded = !paramsExpanded"
        >
          <span class="section-title">参数</span>
          <span class="toggle-icon">{{ paramsExpanded ? '▼' : '▶' }}</span>
        </div>
        <div
          v-if="paramsExpanded"
          class="params-content"
        >
          <div class="content-actions">
            <button
              class="copy-btn"
              title="复制"
              @click="copyParams"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
          </div>
          <pre class="json-content">{{ formattedParams }}</pre>
        </div>
      </div>

      <!-- 结果显示 -->
      <div
        v-if="hasResult"
        class="result-section"
      >
        <div
          class="section-header"
          @click="resultExpanded = !resultExpanded"
        >
          <span class="section-title">结果</span>
          <span class="toggle-icon">{{ resultExpanded ? '▼' : '▶' }}</span>
        </div>
        <div
          v-if="resultExpanded"
          class="result-content"
        >
          <div class="content-actions">
            <button
              class="copy-btn"
              title="复制"
              @click="copyResult"
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect>
                <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path>
              </svg>
            </button>
          </div>
          <pre
            v-if="!isError"
            class="json-content"
          >{{ formattedResult }}</pre>
          <div
            v-else
            class="error-message"
          >
            {{ errorMessage }}
          </div>
        </div>
      </div>

      <!-- 错误信息（独立显示） -->
      <div
        v-if="hasError && !hasResult"
        class="error-section"
      >
        <div class="error-header">
          <span class="error-icon">❌</span>
          <span class="error-title">执行错误</span>
        </div>
        <div class="error-message">
          {{ errorMessage }}
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
import type { ToolUseBlock, ToolResultBlock } from '@/types/message'

interface Props {
  toolUse: ToolUseBlock
  result?: ToolResultBlock
}

const props = defineProps<Props>()

const toastStore = useToastStore()
const expanded = ref(false)
const paramsExpanded = ref(true)  // 默认展开参数
const resultExpanded = ref(true)  // 默认展开结果

// 工具名称解析
const fullToolName = computed(() => props.toolUse.name || 'unknown')

const serverName = computed(() => {
  // 从 "mcp__server-name__tool-name" 提取 "server-name"
  const name = fullToolName.value
  const match = name.match(/^mcp__([^_]+(?:_[^_]+)*)__/)
  return match ? match[1] : null
})

const simplifiedToolName = computed(() => {
  // 从 "mcp__server-name__tool-name" 提取 "tool-name"
  const name = fullToolName.value
  const match = name.match(/^mcp__[^_]+(?:_[^_]+)*__(.+)$/)
  return match ? match[1] : name
})

// 参数处理
const params = computed(() => props.toolUse.input || {})

const formattedParams = computed(() => {
  try {
    return JSON.stringify(params.value, null, 2)
  } catch (error) {
    console.error('Failed to format params:', error)
    return String(params.value)
  }
})

// 结果处理
const resultContent = computed(() => {
  if (!props.result) return null
  const content = props.result.content

  // 如果是字符串，尝试解析为 JSON
  if (typeof content === 'string') {
    try {
      return JSON.parse(content)
    } catch {
      // 无法解析为 JSON，返回原始字符串
      return content
    }
  }

  // 如果是数组，取第一个元素（通常是文本内容）
  if (Array.isArray(content) && content.length > 0) {
    const firstItem = content[0]
    if (typeof firstItem === 'object' && firstItem.type === 'text' && 'text' in firstItem) {
      try {
        return JSON.parse(firstItem.text)
      } catch {
        return firstItem.text
      }
    }
    return firstItem
  }

  return content
})

const hasResult = computed(() => resultContent.value !== null)

const formattedResult = computed(() => {
  if (!resultContent.value) return ''

  // 如果已经是字符串（解析失败的情况）
  if (typeof resultContent.value === 'string') {
    return resultContent.value
  }

  // 否则格式化为 JSON
  try {
    return JSON.stringify(resultContent.value, null, 2)
  } catch (error) {
    console.error('Failed to format result:', error)
    return String(resultContent.value)
  }
})

// 错误处理
const isError = computed(() => {
  if (!props.result) return false
  return props.result.is_error === true
})

const hasError = computed(() => {
  if (isError.value) return true
  if (!resultContent.value) return false

  // 检查结果中的错误字段
  if (typeof resultContent.value === 'object') {
    return 'error' in resultContent.value ||
           ('success' in resultContent.value && resultContent.value.success === false)
  }

  return false
})

const errorMessage = computed(() => {
  if (!resultContent.value) return '执行失败'

  // 如果是字符串，直接返回
  if (typeof resultContent.value === 'string') {
    return resultContent.value
  }

  // 尝试提取错误信息
  if (typeof resultContent.value === 'object') {
    return resultContent.value.error ||
           resultContent.value.message ||
           JSON.stringify(resultContent.value, null, 2)
  }

  return '执行失败'
})

// 状态计算
const status = computed(() => {
  if (!hasResult.value) return 'pending'
  if (hasError.value) return 'failed'

  // 检查 success 字段
  if (typeof resultContent.value === 'object' && 'success' in resultContent.value) {
    return resultContent.value.success ? 'completed' : 'failed'
  }

  // 默认：有结果就认为成功
  return 'completed'
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

// 方法
async function copyParams() {
  try {
    await navigator.clipboard.writeText(formattedParams.value)
    toastStore.success('参数已复制')
  } catch (error) {
    console.error('Failed to copy params:', error)
    toastStore.error('复制失败')
  }
}

async function copyResult() {
  try {
    await navigator.clipboard.writeText(formattedResult.value)
    toastStore.success('结果已复制')
  } catch (error) {
    console.error('Failed to copy result:', error)
    toastStore.error('复制失败')
  }
}
</script>

<style scoped>
.mcp-tool {
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  background: #f6f8fa;
  margin: 8px 0;
}

.mcp-tool.status-pending {
  border-left: 4px solid #6c757d;
}

.mcp-tool.status-completed {
  border-left: 4px solid #28a745;
}

.mcp-tool.status-failed {
  border-left: 4px solid #cb2431;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
  font-size: 13px;
}

.tool-header:hover {
  background: rgba(0, 0, 0, 0.03);
}

.tool-icon {
  font-size: 16px;
}

.tool-name {
  font-weight: 600;
  color: #0969da;
}

.tool-name-code {
  padding: 2px 6px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  color: #0969da;
  font-weight: 600;
}

.server-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  background: #0969da;
  color: white;
}

.status-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  margin-left: auto;
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
  background: #cb2431;
  color: white;
}

.tool-content {
  border-top: 1px solid #e1e4e8;
  padding: 12px;
}

.tool-meta {
  padding: 0 0 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
}

.meta-label {
  color: #586069;
  font-weight: 600;
  min-width: 60px;
  flex-shrink: 0;
}

.meta-value {
  color: #24292e;
  word-break: break-all;
}

.tool-code {
  padding: 2px 6px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 11px;
  color: #0969da;
}

.server-name {
  font-weight: 600;
  color: #0969da;
}

.params-section,
.result-section {
  margin: 12px 0;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}

.error-section {
  margin: 12px 0;
  padding: 12px;
  background: rgba(203, 36, 49, 0.1);
  border: 1px solid #cb2431;
  border-radius: 4px;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
  transition: background 0.2s;
}

.section-header:hover {
  background: #f0f2f5;
}

.section-title {
  font-size: 12px;
  font-weight: 600;
  color: #586069;
}

.toggle-icon {
  font-size: 10px;
  color: #586069;
}

.params-content,
.result-content {
  padding: 12px;
}

.content-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
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

.json-content {
  margin: 0;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #24292e;
  overflow-x: auto;
  max-height: 400px;
  overflow-y: auto;
  white-space: pre;
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.error-icon {
  font-size: 16px;
}

.error-title {
  font-size: 13px;
  font-weight: 600;
  color: #cb2431;
}

.error-message {
  padding: 8px 12px;
  background: #ffffff;
  border-radius: 4px;
  font-size: 12px;
  color: #cb2431;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Consolas', 'Monaco', monospace;
}

.expand-btn {
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid #e1e4e8;
  background: #fafbfc;
  color: #586069;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.2s;
}

.expand-btn:hover {
  background: #f6f8fa;
}
</style>

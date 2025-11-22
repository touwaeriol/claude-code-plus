<template>
  <div
    class="tool-display notebook-edit-tool"
    :class="statusClass"
  >
    <!-- 工具头部 -->
    <div class="tool-header">
      <span class="tool-icon">📓</span>
      <span class="tool-name">NotebookEdit</span>
      <span class="notebook-info">
        <span class="file-name">{{ fileName }}</span>
        <span class="edit-mode-badge">{{ editModeText }}</span>
        <span class="cell-type-badge">{{ cellTypeText }}</span>
      </span>
    </div>

    <!-- 展开内容 -->
    <div
      v-if="expanded"
      class="tool-content"
    >
      <!-- 文件信息 -->
      <div class="notebook-meta">
        <div class="meta-row">
          <span class="meta-label">文件:</span>
          <span
            class="meta-value file-path"
            @click="openNotebook"
          >
            {{ notebookPath }}
          </span>
        </div>
        <div class="meta-row">
          <span class="meta-label">操作:</span>
          <span class="meta-value">{{ operationText }}</span>
        </div>
        <div
          v-if="cellId"
          class="meta-row"
        >
          <span class="meta-label">单元格:</span>
          <span class="meta-value">{{ cellId }}</span>
        </div>
      </div>

      <!-- 单元格内容 -->
      <div
        v-if="editMode !== 'delete'"
        class="cell-content"
      >
        <div class="content-header">
          <span class="content-label">{{ cellTypeText }} 单元格内容</span>
          <button
            class="btn-copy"
            @click="copyContent"
          >
            📋 复制
          </button>
        </div>

        <!-- Code 单元格 - 语法高亮 -->
        <div
          v-if="cellType === 'code'"
          class="code-cell"
        >
          <pre><code class="language-python">{{ displayContent }}</code></pre>
          <button
            v-if="isTruncated"
            class="toggle-btn"
            @click="showFullContent = !showFullContent"
          >
            {{ showFullContent ? '收起' : '查看完整内容' }}
          </button>
        </div>

        <!-- Markdown 单元格 -->
        <div
          v-else
          class="markdown-cell"
        >
          <div
            v-if="showMarkdownPreview"
            class="markdown-preview"
          >
            <MarkdownRenderer
              :content="displayContent"
              :is-dark="isDark"
            />
          </div>
          <pre
            v-else
            class="markdown-source"
          >{{ displayContent }}</pre>

          <button
            class="toggle-btn"
            @click="showMarkdownPreview = !showMarkdownPreview"
          >
            {{ showMarkdownPreview ? '查看源码' : '预览 Markdown' }}
          </button>
        </div>
      </div>

      <!-- 结果状态 -->
      <div
        v-if="result"
        class="result-status"
        :class="result.success ? 'success' : 'error'"
      >
        <span class="status-icon">{{ result.success ? '✅' : '❌' }}</span>
        <span class="status-message">{{ result.message || (result.success ? '编辑成功' : '编辑失败') }}</span>
      </div>

      <!-- 操作按钮 -->
      <div class="actions">
        <button
          class="btn btn-primary"
          @click="openNotebook"
        >
          📂 打开笔记本
        </button>
      </div>
    </div>

    <!-- 展开/收起按钮 -->
    <button
      class="expand-btn"
      @click="toggleExpanded"
    >
      {{ expanded ? '收起' : '展开' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ideService } from '@/services/ideaBridge'
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
const showFullContent = ref(false)
const showMarkdownPreview = ref(false)

// 解析工具参数
const notebookPath = computed(() => props.toolUse.input.notebook_path || '')
const cellId = computed(() => props.toolUse.input.cell_id)
const newSource = computed(() => props.toolUse.input.new_source || '')
const cellType = computed(() => props.toolUse.input.cell_type || 'code')
const editMode = computed(() => props.toolUse.input.edit_mode || 'replace')

// 解析结果
const result = computed(() => {
  if (!props.result) return null

  try {
    if (typeof props.result.content === 'string') {
      return JSON.parse(props.result.content)
    }
    // 如果是数组，取第一个元素（可能是 text content block）
    if (Array.isArray(props.result.content) && props.result.content.length > 0) {
      const firstBlock = props.result.content[0]
      if (firstBlock.type === 'text') {
        return JSON.parse(firstBlock.text)
      }
    }
    return props.result.content
  } catch (error) {
    console.error('Failed to parse result:', error)
    return null
  }
})

// 计算属性
const fileName = computed(() => {
  const path = notebookPath.value
  return path.split(/[\\/]/).pop() || path
})

const editModeText = computed(() => {
  const modes: Record<string, string> = {
    replace: '替换',
    insert: '插入',
    delete: '删除'
  }
  return modes[editMode.value] || editMode.value
})

const cellTypeText = computed(() => {
  return cellType.value === 'code' ? 'Code' : 'Markdown'
})

const operationText = computed(() => {
  const mode = editModeText.value
  const cellInfo = cellId.value ? `单元格 ${cellId.value}` : '单元格'
  return `${mode} ${cellInfo}`
})

const displayContent = computed(() => {
  const content = newSource.value
  const lines = content.split('\n')

  if (showFullContent.value || lines.length <= 20) {
    return content
  }

  return lines.slice(0, 20).join('\n') + '\n...'
})

const isTruncated = computed(() => {
  return newSource.value.split('\n').length > 20
})

const statusClass = computed(() => {
  if (!result.value) return ''
  return result.value.success ? 'status-success' : 'status-error'
})

// 方法
function toggleExpanded() {
  expanded.value = !expanded.value
}

async function openNotebook() {
  try {
    const response = await ideService.openFile(notebookPath.value)
    if (!response.success && response.error) {
      console.error('Failed to open notebook:', response.error)
    }
  } catch (error) {
    console.error('Failed to open notebook:', error)
  }
}

async function copyContent() {
  try {
    await navigator.clipboard.writeText(newSource.value)
  } catch (error) {
    console.error('Failed to copy:', error)
  }
}
</script>

<style scoped>
.notebook-edit-tool {
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  background: #f6f8fa;
  margin: 8px 0;
}

.notebook-edit-tool.status-success {
  border-left: 4px solid #28a745;
}

.notebook-edit-tool.status-error {
  border-left: 4px solid #cb2431;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  user-select: none;
}

.tool-icon {
  font-size: 18px;
}

.tool-name {
  font-weight: 600;
  font-size: 14px;
  color: #0366d6;
}

.notebook-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #586069;
}

.file-name {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #24292e;
}

.edit-mode-badge,
.cell-type-badge {
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  background: #0366d6;
  color: white;
}

.cell-type-badge {
  background: #6f42c1;
}

.tool-content {
  padding: 0 12px 12px;
  border-top: 1px solid #e1e4e8;
}

.notebook-meta {
  padding: 12px 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.meta-row {
  display: flex;
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

.file-path {
  font-family: 'Consolas', 'Monaco', monospace;
  color: #0366d6;
  cursor: pointer;
  text-decoration: underline;
}

.file-path:hover {
  color: #0256c0;
}

.cell-content {
  margin: 12px 0;
}

.content-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.content-label {
  font-size: 13px;
  font-weight: 600;
  color: #586069;
}

.btn-copy {
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  background: white;
  color: #24292e;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-copy:hover {
  background: #f6f8fa;
  border-color: #0366d6;
}

.code-cell,
.markdown-cell {
  position: relative;
}

.code-cell pre {
  margin: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
}

.code-cell code {
  color: #24292e;
}

.markdown-source {
  margin: 0;
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
  overflow-x: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-wrap: break-word;
  color: #24292e;
}

.markdown-preview {
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e1e4e8;
  border-radius: 4px;
}

.toggle-btn {
  margin-top: 8px;
  padding: 4px 8px;
  font-size: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 3px;
  background: white;
  cursor: pointer;
  color: #586069;
}

.toggle-btn:hover {
  background: #f6f8fa;
}

.result-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin: 12px 0;
  border-radius: 4px;
  font-size: 13px;
}

.result-status.success {
  background: rgba(34, 134, 58, 0.1);
  border: 1px solid #28a745;
  color: #22863a;
}

.result-status.error {
  background: rgba(203, 36, 49, 0.1);
  border: 1px solid #cb2431;
  color: #cb2431;
}

.status-icon {
  font-size: 16px;
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

.expand-btn {
  width: 100%;
  padding: 6px;
  border: none;
  border-top: 1px solid #e1e4e8;
  background: #fafbfc;
  color: #586069;
  font-size: 12px;
  cursor: pointer;
}

.expand-btn:hover {
  background: #f6f8fa;
}

/* 暗色主题适配 */
@media (prefers-color-scheme: dark) {
  .notebook-edit-tool {
    background: #1e1e1e;
    border-color: #3e3e42;
  }

  .tool-name {
    color: #4fc3f7;
  }

  .file-name,
  .meta-value {
    color: #cccccc;
  }

  .file-path {
    color: #4fc3f7;
  }

  .file-path:hover {
    color: #81d4fa;
  }

  .code-cell pre,
  .markdown-source,
  .markdown-preview,
  .btn-copy,
  .toggle-btn,
  .btn {
    background: #2d2d30;
    border-color: #3e3e42;
    color: #cccccc;
  }

  .btn-copy:hover,
  .toggle-btn:hover {
    background: #3e3e42;
  }

  .expand-btn {
    background: #252526;
    border-color: #3e3e42;
    color: #cccccc;
  }

  .expand-btn:hover {
    background: #2d2d30;
  }
}
</style>

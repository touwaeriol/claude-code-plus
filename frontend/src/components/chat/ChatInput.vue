<template>
  <div
    class="unified-chat-input-container"
    :class="{ focused: isFocused }"
  >
    <!-- Pending Task Bar (任务队列显示) -->
    <div
      v-if="visibleTasks.length > 0"
      class="pending-task-bar"
    >
      <div class="task-header">
        任务队列 ({{ visibleTasks.length }})
      </div>
      <div
        v-for="task in visibleTasks"
        :key="task.id"
        class="task-row"
      >
        <span class="task-label">{{ getTaskLabel(task) }}</span>
        <span
          class="task-status"
          :class="`status-${task.status.toLowerCase()}`"
        >
          {{ getTaskStatusText(task.status) }}
        </span>
      </div>
    </div>

    <!-- Top Toolbar (上下文管理工具栏) -->
    <div
      v-if="showContextControls && (contexts.length > 0 || enabled)"
      class="top-toolbar"
    >
      <!-- Add Context 按钮 -->
      <button
        ref="addContextButtonRef"
        class="add-context-btn"
        :disabled="!enabled"
        @click="handleAddContextClick"
      >
        <span class="btn-icon">📎</span>
        <span class="btn-text">添加上下文</span>
      </button>

      <!-- Context Tags (上下文标签) -->
      <div
        v-for="(context, index) in contexts"
        :key="`context-${index}`"
        class="context-tag"
      >
        <span class="tag-icon">{{ getContextIcon(context) }}</span>
        <span class="tag-text">{{ getContextDisplay(context) }}</span>
        <button
          class="tag-remove"
          title="移除"
          @click="removeContext(context)"
        >
          ×
        </button>
      </div>
    </div>

    <!-- 输入区域 -->
    <div
      class="input-area"
      @click="focusInput"
    >
      <textarea
        ref="textareaRef"
        v-model="inputText"
        class="message-textarea"
        :placeholder="placeholderText"
        :disabled="!enabled"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @keydown="handleKeydown"
        @input="adjustHeight"
      />
    </div>

    <!-- Bottom Toolbar (底部工具栏) -->
    <div class="bottom-toolbar">
      <!-- 左侧控件组 -->
      <div class="toolbar-left">
        <!-- 模型选择器 -->
        <div
          v-if="showModelSelector"
          class="model-selector-wrapper"
        >
          <el-select
            v-model="selectedModelValue"
            class="model-selector"
            :disabled="!enabled || isGenerating"
            placement="top-start"
            :teleported="true"
            popper-class="chat-input-select-dropdown"
            :popper-options="{
              modifiers: [
                {
                  name: 'preventOverflow',
                  options: { boundary: 'viewport' }
                },
                {
                  name: 'flip',
                  options: {
                    fallbackPlacements: ['top-start', 'top'],
                  }
                }
              ]
            }"
            @change="$emit('model-change', selectedModelValue)"
          >
            <el-option
              value="DEFAULT"
              label="默认"
            />
            <el-option
              value="OPUS"
              label="Opus"
            />
            <el-option
              value="SONNET"
              label="Sonnet"
            />
            <el-option
              value="OPUS_PLAN"
              label="Opus Plan"
            />
          </el-select>
          <span
            v-if="actualModelId"
            class="actual-model-hint"
            :title="actualModelId"
          >
            实际模型: {{ actualModelId }}
          </span>
        </div>

        <!-- 权限选择器 -->
        <el-select
          v-if="showPermissionControls"
          v-model="selectedPermissionValue"
          class="permission-selector"
          :disabled="!enabled || isGenerating"
          placement="top-start"
          :teleported="true"
          popper-class="chat-input-select-dropdown"
          :popper-options="{
            modifiers: [
              {
                name: 'preventOverflow',
                options: { boundary: 'viewport' }
              },
              {
                name: 'flip',
                options: {
                  fallbackPlacements: ['top-start', 'top'],
                }
              }
            ]
          }"
          @change="$emit('permission-change', selectedPermissionValue)"
        >
          <el-option
            value="DEFAULT"
            label="默认权限"
          />
          <el-option
            value="ACCEPT"
            label="接受编辑"
          />
          <el-option
            value="BYPASS"
            label="绕过权限"
          />
          <el-option
            value="PLAN"
            label="计划模式"
          />
        </el-select>

        <!-- Skip Permissions 复选框 -->
        <label
          v-if="showPermissionControls"
          class="checkbox-label"
        >
          <input
            v-model="skipPermissionsValue"
            type="checkbox"
            :disabled="!enabled || isGenerating"
            @change="$emit('skip-permissions-change', skipPermissionsValue)"
          >
          <span>跳过权限</span>
        </label>

        <!-- Auto Cleanup Contexts 复选框 (暂时隐藏) -->
        <!-- <label v-if="showPermissionControls" class="checkbox-label">
          <input
            type="checkbox"
            v-model="autoCleanupValue"
            :disabled="!enabled || isGenerating"
            @change="$emit('auto-cleanup-change', autoCleanupValue)"
          />
          <span>自动清理</span>
        </label> -->
      </div>

      <!-- 右侧按钮组 -->
      <div class="toolbar-right">
        <!-- 统计信息 -->
        <div
          v-if="tokenUsage"
          class="token-stats"
          :title="getTokenTooltip()"
        >
          {{ formatTokenUsage(tokenUsage) }}
        </div>

        <!-- 发送/停止按钮 -->
        <button
          v-if="!isGenerating"
          class="send-btn"
          :disabled="!canSend"
          title="发送消息 (Enter)"
          @click="handleSend"
        >
          <span class="btn-icon">📤</span>
          <span class="btn-text">发送</span>
        </button>

        <!-- 停止按钮 -->
        <button
          v-else
          class="stop-btn"
          title="停止生成"
          @click="$emit('stop')"
        >
          <span class="btn-icon">⏸</span>
          <span class="btn-text">停止</span>
        </button>

        <!-- 打断并发送按钮 -->
        <button
          v-if="isGenerating && hasInput"
          class="interrupt-send-btn"
          title="打断并发送 (Alt+Enter)"
          @click="handleInterruptAndSend"
        >
          <span class="btn-icon">⚡</span>
          <span class="btn-text">打断发送</span>
        </button>
      </div>
    </div>

    <!-- Context Selector Popup (上下文选择器弹窗) -->
    <div
      v-if="showContextSelectorPopup"
      ref="contextPopupRef"
      class="context-selector-popup"
    >
      <div class="popup-header">
        <span>添加上下文</span>
        <button
          class="close-btn"
          @click="showContextSelectorPopup = false"
        >
          ×
        </button>
      </div>
      <div class="popup-content">
        <input
          v-model="contextSearchQuery"
          type="text"
          class="context-search-input"
          placeholder="搜索文件..."
          @input="handleContextSearch"
        >
        <div class="context-results">
          <div
            v-for="result in contextSearchResults"
            :key="result.path"
            class="context-result-item"
            @click="handleContextSelect(result)"
          >
            <span class="result-icon">📄</span>
            <span class="result-name">{{ result.name }}</span>
            <span class="result-path">{{ result.relativePath }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- @ Symbol File Popup (@ 符号文件选择弹窗) -->
    <AtSymbolFilePopup
      :visible="showAtSymbolPopup"
      :files="atSymbolSearchResults"
      :anchor-element="textareaRef"
      :at-position="atSymbolPosition"
      @select="handleAtSymbolFileSelect"
      @dismiss="dismissAtSymbolPopup"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted } from 'vue'
import type { ContextReference, AiModel, PermissionMode } from '@/types/enhancedMessage'
import AtSymbolFilePopup from '@/components/input/AtSymbolFilePopup.vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
import { isInAtQuery, replaceAtQuery } from '@/utils/atSymbolDetector'

interface PendingTask {
  id: string
  type: 'SWITCH_MODEL' | 'QUERY'
  text: string
  alias?: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED'
  realModelId?: string
  error?: string
}

interface TokenUsage {
  inputTokens: number
  outputTokens: number
  cacheCreationTokens: number
  cacheReadTokens: number
  totalTokens: number
}

interface Props {
  pendingTasks?: PendingTask[]
  contexts?: ContextReference[]
  isGenerating?: boolean
  enabled?: boolean
  selectedModel?: AiModel
  actualModelId?: string  // 实际模型ID
  selectedPermission?: PermissionMode
  skipPermissions?: boolean
  autoCleanupContexts?: boolean
  showContextControls?: boolean
  showModelSelector?: boolean
  showPermissionControls?: boolean
  showSendButton?: boolean
  tokenUsage?: TokenUsage
  placeholderText?: string
}

interface Emits {
  (e: 'send', text: string): void
  (e: 'interrupt-and-send', text: string): void
  (e: 'stop'): void
  (e: 'context-add', context: ContextReference): void
  (e: 'context-remove', context: ContextReference): void
  (e: 'model-change', model: AiModel): void
  (e: 'permission-change', permission: PermissionMode): void
  (e: 'skip-permissions-change', skip: boolean): void
  (e: 'auto-cleanup-change', cleanup: boolean): void
}

const props = withDefaults(defineProps<Props>(), {
  pendingTasks: () => [],
  contexts: () => [],
  isGenerating: false,
  enabled: true,
  selectedModel: 'SONNET',
  selectedPermission: 'DEFAULT',
  skipPermissions: true,
  autoCleanupContexts: false,
  showContextControls: true,
  showModelSelector: true,
  showPermissionControls: true,
  showSendButton: true,
  placeholderText: '输入消息... (Enter 发送, Shift+Enter 换行, Alt+Enter 打断发送)'
})

const emit = defineEmits<Emits>()

// Refs
const textareaRef = ref<HTMLTextAreaElement>()
const addContextButtonRef = ref<HTMLButtonElement>()
const contextPopupRef = ref<HTMLDivElement>()

// State
const inputText = ref('')
const isFocused = ref(false)
const showContextSelectorPopup = ref(false)
const contextSearchQuery = ref('')
const contextSearchResults = ref<any[]>([])

// @ Symbol File Popup State
const showAtSymbolPopup = ref(false)
const atSymbolPosition = ref(0)
const atSymbolSearchResults = ref<IndexedFileInfo[]>([])

// Local state for props
const selectedModelValue = ref(props.selectedModel)
const selectedPermissionValue = ref(props.selectedPermission)
const skipPermissionsValue = ref(props.skipPermissions)
const autoCleanupValue = ref(props.autoCleanupContexts)

// Computed
const visibleTasks = computed(() => {
  return props.pendingTasks.filter(
    task => task.status === 'PENDING' || task.status === 'RUNNING'
  )
})

const hasInput = computed(() => inputText.value.trim().length > 0)

const canSend = computed(() => {
  return hasInput.value && props.enabled && !props.isGenerating
})

// Watch props changes
watch(() => props.selectedModel, (newValue) => {
  selectedModelValue.value = newValue
})

watch(() => props.selectedPermission, (newValue) => {
  selectedPermissionValue.value = newValue
})

watch(() => props.skipPermissions, (newValue) => {
  skipPermissionsValue.value = newValue
})

watch(() => props.autoCleanupContexts, (newValue) => {
  autoCleanupValue.value = newValue
})

// Watch input text and cursor position for @ symbol detection
watch([inputText, () => textareaRef.value?.selectionStart], () => {
  checkAtSymbol()
})

// Methods
function focusInput() {
  textareaRef.value?.focus()
}

function adjustHeight() {
  nextTick(() => {
    const textarea = textareaRef.value
    if (!textarea) return

    textarea.style.height = 'auto'
    const newHeight = Math.min(textarea.scrollHeight, 300)
    textarea.style.height = `${newHeight}px`
  })
}

// @ Symbol File Reference Functions
async function checkAtSymbol() {
  const textarea = textareaRef.value
  if (!textarea) return

  const cursorPosition = textarea.selectionStart
  const atResult = isInAtQuery(inputText.value, cursorPosition)

  if (atResult) {
    // 在 @ 查询中
    atSymbolPosition.value = atResult.atPosition

    // 搜索文件
    try {
      if (atResult.query.length === 0) {
        // 空查询，显示最近文件
        atSymbolSearchResults.value = await fileSearchService.getRecentFiles(10)
      } else {
        // 搜索文件
        atSymbolSearchResults.value = await fileSearchService.searchFiles(atResult.query, 10)
      }
      showAtSymbolPopup.value = atSymbolSearchResults.value.length > 0
    } catch (error) {
      console.error('文件搜索失败:', error)
      atSymbolSearchResults.value = []
      showAtSymbolPopup.value = false
    }
  } else {
    // 不在 @ 查询中
    showAtSymbolPopup.value = false
    atSymbolSearchResults.value = []
  }
}

function handleAtSymbolFileSelect(file: IndexedFileInfo) {
  const textarea = textareaRef.value
  if (!textarea) return

  const fileReference = `@${file.relativePath}`
  const cursorPosition = textarea.selectionStart

  const { newText, newCursorPosition } = replaceAtQuery(
    inputText.value,
    atSymbolPosition.value,
    cursorPosition,
    fileReference
  )

  inputText.value = newText

  // 更新光标位置
  nextTick(() => {
    textarea.selectionStart = textarea.selectionEnd = newCursorPosition
    textarea.focus()
  })

  // 关闭弹窗
  dismissAtSymbolPopup()
}

function dismissAtSymbolPopup() {
  showAtSymbolPopup.value = false
  atSymbolSearchResults.value = []
}

function handleKeydown(event: KeyboardEvent) {
  // 如果 @ 符号弹窗显示，键盘事件由弹窗组件处理
  // 这里不需要额外处理，因为 AtSymbolFilePopup 组件会监听全局键盘事件

  // Alt+Enter - 打断并发送
  if (event.key === 'Enter' && event.altKey) {
    event.preventDefault()
    handleInterruptAndSend()
    return
  }

  // Shift+Enter 或 Ctrl+J - 插入换行
  if (
    (event.key === 'Enter' && event.shiftKey) ||
    (event.key === 'j' && event.ctrlKey)
  ) {
    // 默认行为已经会插入换行，不需要额外处理
    return
  }

  // Ctrl+U - 清空光标位置到行首
  if (event.key === 'u' && event.ctrlKey) {
    event.preventDefault()
    const textarea = textareaRef.value
    if (!textarea) return

    const text = textarea.value
    const cursorPos = textarea.selectionStart

    // 找到当前行的开始位置
    const lineStart = text.lastIndexOf('\n', cursorPos - 1) + 1

    // 删除从行首到光标位置的文本
    inputText.value = text.substring(0, lineStart) + text.substring(cursorPos)

    // 更新光标位置
    nextTick(() => {
      textarea.selectionStart = textarea.selectionEnd = lineStart
    })
    return
  }

  // Enter - 发送消息
  if (event.key === 'Enter' && !event.shiftKey && !event.altKey) {
    event.preventDefault()
    handleSend()
    return
  }
}

function handleSend() {
  if (!canSend.value) return

  const text = inputText.value.trim()
  if (text) {
    emit('send', text)
    inputText.value = ''
    adjustHeight()
  }
}

function handleInterruptAndSend() {
  if (!hasInput.value || !props.isGenerating) return

  const text = inputText.value.trim()
  if (text) {
    emit('interrupt-and-send', text)
    inputText.value = ''
    adjustHeight()
  }
}

function removeContext(context: ContextReference) {
  emit('context-remove', context)
}

function handleAddContextClick() {
  showContextSelectorPopup.value = true
}

function handleContextSearch() {
  // TODO: 实现文件搜索逻辑
  // 暂时返回空结果
  contextSearchResults.value = []
}

function handleContextSelect(_result: any) {
  // TODO: 将搜索结果转换为 ContextReference
  // emit('context-add', contextRef)
  showContextSelectorPopup.value = false
}

function getContextDisplay(context: ContextReference): string {
  if ('path' in context) {
    const pathStr = (context as any).path
    return pathStr.split(/[\\/]/).pop() || pathStr
  }
  if ('url' in context) {
    return (context as any).title || (context as any).url
  }
  return context.uri
}

function getContextIcon(context: ContextReference): string {
  if ('path' in context) return '📄'
  if ('url' in context) return '🌐'
  return '📎'
}

function getTaskLabel(task: PendingTask): string {
  if (task.type === 'SWITCH_MODEL') {
    return `/model ${task.alias}`
  }
  return task.text.trim()
}

function getTaskStatusText(status: string): string {
  const map: Record<string, string> = {
    PENDING: '排队中',
    RUNNING: '执行中',
    SUCCESS: '成功',
    FAILED: '失败'
  }
  return map[status] || status
}

function formatTokenUsage(usage: TokenUsage): string {
  return `${usage.totalTokens} tokens`
}

function getTokenTooltip(): string {
  if (!props.tokenUsage) return ''
  const u = props.tokenUsage
  return `输入: ${u.inputTokens}, 输出: ${u.outputTokens}, 缓存创建: ${u.cacheCreationTokens}, 缓存读取: ${u.cacheReadTokens}`
}

// Lifecycle
onMounted(() => {
  nextTick(() => {
    setTimeout(() => {
      focusInput()
    }, 200)
  })
})
</script>

<style scoped>
.unified-chat-input-container {
  display: flex;
  flex-direction: column;
  background: var(--ide-panel-background, #f6f8fa);
  border: 1.5px solid var(--ide-border, #e1e4e8);
  border-radius: 12px;
  overflow: hidden;
  transition: all 0.2s ease;
}

.unified-chat-input-container.focused {
  border-color: var(--ide-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

/* Pending Task Bar */
.pending-task-bar {
  padding: 12px 16px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-info-background, #f0f8ff);
}

.task-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--ide-text-info, #0366d6);
  margin-bottom: 8px;
}

.task-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  margin-bottom: 4px;
  background: var(--ide-background, #ffffff);
  border-radius: 6px;
}

.task-label {
  flex: 1;
  font-size: 13px;
  color: var(--ide-foreground, #24292e);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.task-status.status-pending {
  background: var(--ide-warning, #ffc107);
  color: #000;
}

.task-status.status-running {
  background: var(--ide-accent, #0366d6);
  color: #fff;
}

/* Top Toolbar */
.top-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.add-context-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  height: 20px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  background: var(--ide-background, #ffffff);
  color: var(--ide-foreground, #24292e);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.add-context-btn:hover:not(:disabled) {
  background: var(--ide-hover-background, #f6f8fa);
  border-color: var(--ide-accent, #0366d6);
}

.add-context-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.context-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
}

.tag-icon {
  font-size: 14px;
}

.tag-text {
  color: var(--ide-link, #0366d6);
  font-family: monospace;
}

.tag-remove {
  padding: 0;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--ide-secondary-foreground, #586069);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
}

.tag-remove:hover {
  color: var(--ide-error, #d73a49);
}

/* Input Area */
.input-area {
  padding: 12px 16px;
  cursor: text;
  min-height: 50px;
  max-height: 300px;
}

.message-textarea {
  width: 100%;
  min-height: 50px;
  max-height: 300px;
  border: none;
  outline: none;
  resize: none;
  font-size: 14px;
  line-height: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: transparent;
  color: var(--ide-foreground, #24292e);
}

.message-textarea::placeholder {
  color: var(--ide-text-disabled, #6a737d);
}

.message-textarea:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* Bottom Toolbar */
.bottom-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-top: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-panel-background, #f6f8fa);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.model-selector-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
}

.model-selector,
.permission-selector {
  width: 140px;
  font-size: 12px;
}

/* Element Plus el-select 样式覆盖 */
.model-selector :deep(.el-select__wrapper),
.permission-selector :deep(.el-select__wrapper) {
  padding: 4px 10px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  background: var(--ide-background, #ffffff);
  box-shadow: none;
  min-height: 28px;
}

.model-selector :deep(.el-select__wrapper):hover,
.permission-selector :deep(.el-select__wrapper):hover {
  border-color: var(--ide-accent, #0366d6);
}

.model-selector :deep(.el-select__wrapper.is-focused),
.permission-selector :deep(.el-select__wrapper.is-focused) {
  border-color: var(--ide-accent, #0366d6);
  box-shadow: none;
}

.model-selector :deep(.el-select__placeholder),
.permission-selector :deep(.el-select__placeholder) {
  color: var(--ide-secondary-foreground, #6a737d);
  font-size: 12px;
}

.model-selector :deep(.el-select__selection),
.permission-selector :deep(.el-select__selection) {
  color: var(--ide-foreground, #24292e);
  font-size: 12px;
}

.model-selector :deep(.el-select__suffix),
.permission-selector :deep(.el-select__suffix) {
  color: var(--ide-secondary-foreground, #6a737d);
}

.model-selector.is-disabled :deep(.el-select__wrapper),
.permission-selector.is-disabled :deep(.el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--ide-panel-background, #f6f8fa);
}

.actual-model-hint {
  font-size: 10px;
  color: var(--ide-secondary-foreground, #6a737d);
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ide-foreground, #24292e);
  cursor: pointer;
  user-select: none;
}

.checkbox-label input[type="checkbox"] {
  cursor: pointer;
}

.checkbox-label input[type="checkbox"]:disabled {
  cursor: not-allowed;
}

.token-stats {
  font-size: 11px;
  color: var(--ide-secondary-foreground, #6a737d);
  padding: 4px 8px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
}

.send-btn,
.stop-btn,
.interrupt-send-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.send-btn {
  background: var(--ide-accent, #0366d6);
  color: white;
}

.send-btn:hover:not(:disabled) {
  background: var(--ide-accent, #0256c2);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(3, 102, 214, 0.3);
}

.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.stop-btn {
  background: var(--ide-error, #d73a49);
  color: white;
}

.stop-btn:hover {
  background: var(--ide-error, #c82333);
  transform: translateY(-1px);
  box-shadow: 0 4px 8px rgba(220, 53, 69, 0.3);
}

.interrupt-send-btn {
  background: var(--ide-warning, #ffc107);
  color: #000;
}

.interrupt-send-btn:hover {
  background: var(--ide-warning, #ffb300);
  transform: translateY(-1px);
}

.btn-icon {
  font-size: 16px;
}

.btn-text {
  font-size: 13px;
}

/* Context Selector Popup */
.context-selector-popup {
  position: absolute;
  bottom: 100%;
  left: 12px;
  right: 12px;
  margin-bottom: 8px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  max-height: 400px;
  overflow: auto;
}

.popup-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  font-weight: 600;
  font-size: 14px;
}

.close-btn {
  padding: 0;
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  color: var(--ide-secondary-foreground, #586069);
  font-size: 20px;
  cursor: pointer;
}

.close-btn:hover {
  color: var(--ide-error, #d73a49);
}

.popup-content {
  padding: 16px;
}

.context-search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 14px;
  margin-bottom: 12px;
}

.context-results {
  max-height: 300px;
  overflow-y: auto;
}

.context-result-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
}

.context-result-item:hover {
  background: var(--ide-hover-background, #f6f8fa);
}

.result-icon {
  font-size: 16px;
}

.result-name {
  font-weight: 600;
  color: var(--ide-foreground, #24292e);
}

.result-path {
  font-size: 12px;
  color: var(--ide-secondary-foreground, #6a737d);
  font-family: monospace;
}

/* 暗色主题适配 */
:global(.theme-dark) .unified-chat-input-container {
  background: var(--ide-panel-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}

:global(.theme-dark) .top-toolbar,
:global(.theme-dark) .bottom-toolbar {
  border-color: var(--ide-border, #3c3c3c);
}

:global(.theme-dark) .add-context-btn,
:global(.theme-dark) .context-tag,
:global(.theme-dark) .model-selector,
:global(.theme-dark) .permission-selector,
:global(.theme-dark) .token-stats {
  background: var(--ide-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}

:global(.theme-dark) .context-selector-popup {
  background: var(--ide-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}
</style>

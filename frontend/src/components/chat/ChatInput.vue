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
        {{ t('chat.taskQueueCount', { count: visibleTasks.length }) }}
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
        <span class="btn-text">{{ t('chat.addContext') }}</span>
      </button>

      <!-- Context Tags (上下文标签) -->
      <div
        v-for="(context, index) in contexts"
        :key="`context-${index}`"
        class="context-tag"
        :class="{ 'image-tag': isImageContext(context) }"
      >
        <!-- 图片预览 -->
        <img
          v-if="isImageContext(context)"
          :src="getImagePreviewUrl(context)"
          class="tag-image-preview"
          :alt="getContextDisplay(context)"
        >
        <span class="tag-icon">{{ getContextIcon(context) }}</span>
        <span class="tag-text">{{ getContextDisplay(context) }}</span>
        <button
          class="tag-remove"
          :title="t('common.remove')"
          @click="removeContext(context)"
        >
          ×
        </button>
      </div>
    </div>

    <!-- 拖放区域提示 -->
    <div
      v-if="isDragging"
      class="drop-zone-overlay"
      @drop.prevent="handleDrop"
      @dragover.prevent
      @dragleave="handleDragLeave"
    >
      <div class="drop-zone-content">
        <span class="drop-icon">📁</span>
        <span class="drop-text">{{ t('chat.dropFileToAddContext') }}</span>
      </div>
    </div>

    <!-- 输入区域 -->
    <div
      class="input-area"
      @click="focusInput"
      @drop.prevent="handleDrop"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
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
        @paste="handlePaste"
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

        <!-- Auto Cleanup Contexts 复选框 -->
        <label
          v-if="showPermissionControls"
          class="checkbox-label"
          :title="t('chat.autoCleanupContextTooltip')"
        >
          <input
            v-model="autoCleanupContextsValue"
            type="checkbox"
            :disabled="!enabled || isGenerating"
            @change="handleAutoCleanupChange"
          >
          <span>{{ t('chat.autoCleanupContext') }}</span>
        </label>
      </div>

      <!-- 右侧按钮组 -->
      <div class="toolbar-right">
        <!-- 上下文使用量指示器 -->
        <ContextUsageIndicator
          v-if="messageHistory && messageHistory.length > 0"
          :current-model="selectedModelValue"
          :message-history="messageHistory"
          :session-token-usage="sessionTokenUsage"
        />

        <!-- 统计信息 -->
        <div
          v-if="tokenUsage"
          class="token-stats"
          :title="getTokenTooltip()"
        >
          {{ formatTokenUsage(tokenUsage) }}
        </div>

        <!-- 图片上传按钮 -->
        <button
          class="image-upload-btn"
          :disabled="!enabled || isGenerating"
          title="上传图片"
          @click="handleImageUploadClick"
        >
          <span class="btn-icon">📷</span>
        </button>
        <input
          ref="imageInputRef"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/bmp,image/webp"
          multiple
          style="display: none"
          @change="handleImageFileSelect"
        >

        <!-- 发送/停止按钮 -->
        <button
          v-if="!isGenerating"
          class="send-btn"
          :disabled="!canSend"
          :title="t('chat.sendMessageShortcut')"
          @click="handleSend"
          @contextmenu="handleSendButtonContextMenu"
        >
          <span class="btn-icon">📤</span>
          <span class="btn-text">{{ t('common.send') }}</span>
        </button>

        <!-- 停止按钮 -->
        <button
          v-else
          class="stop-btn"
          :title="t('chat.stopGenerating')"
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

    <!-- Send Button Context Menu (发送按钮右键菜单) -->
    <div
      v-if="showSendContextMenu"
      class="send-context-menu"
      :style="{
        left: sendContextMenuPosition.x + 'px',
        top: sendContextMenuPosition.y + 'px'
      }"
      @click.stop
    >
      <div
        class="context-menu-item"
        @click="handleSendFromContextMenu"
      >
        <span class="menu-icon">📤</span>
        <span class="menu-text">发送</span>
      </div>
      <div
        v-if="isGenerating && hasInput"
        class="context-menu-item"
        @click="handleInterruptAndSendFromContextMenu"
      >
        <span class="menu-icon">⚡</span>
        <span class="menu-text">打断并发送</span>
      </div>
    </div>

    <!-- Context Menu Backdrop (点击外部关闭菜单) -->
    <div
      v-if="showSendContextMenu"
      class="context-menu-backdrop"
      @click="closeSendContextMenu"
    />

    <!-- Context Selector Popup (上下文选择器弹窗) -->
    <div
      v-if="showContextSelectorPopup"
      ref="contextPopupRef"
      class="context-selector-popup"
    >
      <div class="popup-header">
        <span>{{ t('chat.addContext') }}</span>
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
          :placeholder="t('tools.search')"
          @input="handleContextSearch"
        >
        <div class="context-results">
          <div
            v-for="(result, index) in contextSearchResults"
            :key="result.path"
            :class="['context-result-item', { selected: index === contextSelectedIndex }]"
            @click="handleContextSelect(result)"
            @mouseenter="contextSelectedIndex = index"
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
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { ContextReference, AiModel, PermissionMode, EnhancedMessage, TokenUsage as EnhancedTokenUsage, ImageReference } from '@/types/enhancedMessage'
import AtSymbolFilePopup from '@/components/input/AtSymbolFilePopup.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
import { isInAtQuery, replaceAtQuery } from '@/utils/atSymbolDetector'
import { ContextDisplayType } from '@/types/enhancedMessage'

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
  messageHistory?: EnhancedMessage[]  // 消息历史（用于Token计算）
  sessionTokenUsage?: EnhancedTokenUsage | null  // 会话级Token使用量
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
  placeholderText: ''
})

const emit = defineEmits<Emits>()

// i18n
const { t } = useI18n()

// Refs
const textareaRef = ref<HTMLTextAreaElement>()
const addContextButtonRef = ref<HTMLButtonElement>()
const contextPopupRef = ref<HTMLDivElement>()
const imageInputRef = ref<HTMLInputElement>()

// State
const inputText = ref('')
const isFocused = ref(false)
const showContextSelectorPopup = ref(false)
const contextSearchQuery = ref('')
const contextSearchResults = ref<any[]>([])
const contextSelectedIndex = ref(0)

// @ Symbol File Popup State
const showAtSymbolPopup = ref(false)
const atSymbolPosition = ref(0)
const atSymbolSearchResults = ref<IndexedFileInfo[]>([])

// Drag and Drop State
const isDragging = ref(false)

// Send Button Context Menu State
const showSendContextMenu = ref(false)
const sendContextMenuPosition = ref({ x: 0, y: 0 })

// Local state for props
const selectedModelValue = ref(props.selectedModel)
const selectedPermissionValue = ref(props.selectedPermission)
const skipPermissionsValue = ref(props.skipPermissions)

// 自动清理上下文选项 - 从 localStorage 读取
const AUTO_CLEANUP_KEY = 'claude-code-plus-auto-cleanup-contexts'
const autoCleanupContextsValue = ref(
  localStorage.getItem(AUTO_CLEANUP_KEY) === 'true' || props.autoCleanupContexts
)

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

const placeholderText = computed(() => {
  if (props.placeholderText) {
    return props.placeholderText
  }
  // 根据操作系统使用不同的快捷键提示
  const isMac = navigator.platform.toUpperCase().indexOf('MAC') >= 0
  return isMac
    ? t('chat.placeholderWithShortcuts')
    : t('chat.placeholderWithShortcutsCtrl')
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
  autoCleanupContextsValue.value = newValue
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

/**
 * 处理粘贴事件
 * 检测粘贴内容是否包含图片，如果是则转为 base64 添加到上下文
 */
async function handlePaste(event: ClipboardEvent) {
  console.log('📋 [handlePaste] 粘贴事件触发')

  const items = event.clipboardData?.items
  if (!items) {
    console.log('📋 [handlePaste] 没有 clipboardData.items')
    return
  }

  console.log(`📋 [handlePaste] 检测到 ${items.length} 个粘贴项`)

  // 检查是否包含图片
  for (let i = 0; i < items.length; i++) {
    const item = items[i]
    console.log(`📋 [handlePaste] 项 ${i}: kind=${item.kind}, type=${item.type}`)

    if (item.type && item.type.startsWith('image/')) {
      console.log(`📋 [handlePaste] 检测到图片: ${item.type}`)

      // 阻止默认粘贴行为
      event.preventDefault()

      const file = item.getAsFile()
      if (!file) {
        console.log('📋 [handlePaste] getAsFile() 返回 null')
        continue
      }

      console.log(`📋 [handlePaste] 获取到文件: name=${file.name}, size=${file.size}, type=${file.type}`)

      // 直接添加到上下文（转为 base64）
      await addImageToContext(file)
    }
  }
}

/**
 * 在光标位置插入文本
 */
function insertAtCursor(text: string) {
  const textarea = textareaRef.value
  if (!textarea) return

  const start = textarea.selectionStart
  const end = textarea.selectionEnd
  const currentText = inputText.value

  // 插入文本
  inputText.value = currentText.substring(0, start) + text + currentText.substring(end)

  // 更新光标位置
  nextTick(() => {
    const newPos = start + text.length
    textarea.selectionStart = textarea.selectionEnd = newPos
    textarea.focus()
  })
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

// 发送按钮右键菜单处理
function handleSendButtonContextMenu(event: MouseEvent) {
  event.preventDefault()
  showSendContextMenu.value = true
  sendContextMenuPosition.value = {
    x: event.clientX,
    y: event.clientY
  }
}

function handleSendFromContextMenu() {
  showSendContextMenu.value = false
  handleSend()
}

function handleInterruptAndSendFromContextMenu() {
  showSendContextMenu.value = false
  handleInterruptAndSend()
}

function closeSendContextMenu() {
  showSendContextMenu.value = false
}

function removeContext(context: ContextReference) {
  emit('context-remove', context)
}

async function handleAddContextClick() {
  showContextSelectorPopup.value = true
  contextSearchQuery.value = ''
  contextSelectedIndex.value = 0

  // 显示最近文件
  try {
    const recentFiles = await fileSearchService.getRecentFiles(10)
    contextSearchResults.value = recentFiles
  } catch (error) {
    console.error('获取最近文件失败:', error)
    contextSearchResults.value = []
  }
}

async function handleContextSearch() {
  const query = contextSearchQuery.value.trim()

  if (query.length === 0) {
    // 空查询，显示最近文件
    try {
      const recentFiles = await fileSearchService.getRecentFiles(10)
      contextSearchResults.value = recentFiles
    } catch (error) {
      console.error('获取最近文件失败:', error)
      contextSearchResults.value = []
    }
  } else {
    // 搜索文件
    try {
      const results = await fileSearchService.searchFiles(query, 10)
      contextSearchResults.value = results
    } catch (error) {
      console.error('文件搜索失败:', error)
      contextSearchResults.value = []
    }
  }

  // 重置选中索引
  contextSelectedIndex.value = 0
}

function handleContextSelect(result: IndexedFileInfo) {
  // 将文件转换为 ContextReference
  const contextRef: ContextReference = {
    type: 'file',
    path: result.relativePath,
    name: result.name
  }

  emit('context-add', contextRef)
  showContextSelectorPopup.value = false
  contextSearchQuery.value = ''
  contextSearchResults.value = []
  contextSelectedIndex.value = 0
}

/**
 * 处理 Context Selector 弹窗的键盘事件
 */
function handleContextPopupKeyDown(event: KeyboardEvent) {
  if (!showContextSelectorPopup.value || contextSearchResults.value.length === 0) {
    return
  }

  switch (event.key) {
    case 'ArrowDown':
      event.preventDefault()
      contextSelectedIndex.value = Math.min(
        contextSelectedIndex.value + 1,
        contextSearchResults.value.length - 1
      )
      break
    case 'ArrowUp':
      event.preventDefault()
      contextSelectedIndex.value = Math.max(contextSelectedIndex.value - 1, 0)
      break
    case 'Enter':
      event.preventDefault()
      if (
        contextSelectedIndex.value >= 0 &&
        contextSelectedIndex.value < contextSearchResults.value.length
      ) {
        handleContextSelect(contextSearchResults.value[contextSelectedIndex.value])
      }
      break
    case 'Escape':
      event.preventDefault()
      showContextSelectorPopup.value = false
      contextSearchQuery.value = ''
      contextSearchResults.value = []
      contextSelectedIndex.value = 0
      break
  }
}

/**
 * 获取上下文显示文本（使用类型守卫）
 */
function getContextDisplay(context: ContextReference): string {
  if (isImageReference(context)) {
    return context.name
  }
  if (isFileReference(context)) {
    return context.path.split(/[\\/]/).pop() || context.path
  }
  if (isUrlReference(context)) {
    return context.title || context.url
  }
  return context.uri
}

/**
 * 获取图片预览 URL（使用类型守卫）
 */
function getImagePreviewUrl(context: ContextReference): string {
  if (isImageReference(context)) {
    return `data:${context.mimeType};base64,${context.base64Data}`
  }
  return ''
}

/**
 * 获取上下文图标（使用类型守卫）
 */
function getContextIcon(context: ContextReference): string {
  if (isImageReference(context)) return '�️'
  if (isFileReference(context)) return '�'
  if (isUrlReference(context)) return '🌐'
  if ('type' in context && (context as any).type === 'folder') return '📁'
  if ('path' in context) return '📄'
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
    PENDING: t('chat.taskStatus.pending'),
    RUNNING: t('chat.taskStatus.running'),
    SUCCESS: t('chat.taskStatus.success'),
    FAILED: t('chat.taskStatus.failed')
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

// Drag and Drop Functions
function handleDragOver(event: DragEvent) {
  event.preventDefault()
  isDragging.value = true
}

function handleDragLeave(event: DragEvent) {
  event.preventDefault()
  // 只有当离开整个拖放区域时才设置为 false
  if (event.target === event.currentTarget) {
    isDragging.value = false
  }
}

async function handleDrop(event: DragEvent) {
  event.preventDefault()
  isDragging.value = false

  const files = event.dataTransfer?.files
  if (!files || files.length === 0) return

  for (let i = 0; i < files.length; i++) {
    const file = files[i]
    await addFileToContext(file)
  }
}

async function addFileToContext(file: File) {
  try {
    // 检查是否为图片文件
    if (file.type && file.type.startsWith('image/')) {
      await addImageToContext(file)
      return
    }

    // 读取文件内容
    const content = await readFileContent(file)

    // 创建上下文引用
    const contextRef: ContextReference = {
      type: 'file',
      name: file.name,
      path: file.name, // 在实际项目中应该获取相对路径
      content: content
    } as any

    // 添加到上下文列表
    emit('context-add', contextRef)
  } catch (error) {
    console.error('Failed to read file:', error)
    // 可以添加错误提示
  }
}

function readFileContent(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target?.result as string)
    reader.onerror = reject
    reader.readAsText(file)
  })
}

// 图片上传功能
function handleImageUploadClick() {
  imageInputRef.value?.click()
}

async function handleImageFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  for (let i = 0; i < files.length; i++) {
    await addImageToContext(files[i])
  }

  // 清空 input，允许重复选择同一文件
  input.value = ''
}

// 支持的图片 MIME 类型常量
const VALID_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'] as const

/**
 * 类型守卫：检查是否为图片上下文
 */
function isImageReference(context: ContextReference): context is ImageReference {
  return 'type' in context && (context as any).type === 'image'
}

/**
 * 类型守卫：检查是否为文件上下文
 */
function isFileReference(context: ContextReference): context is { type: 'file'; path: string; name: string } {
  return 'type' in context && (context as any).type === 'file'
}

/**
 * 类型守卫：检查是否为 URL 上下文
 */
function isUrlReference(context: ContextReference): context is { type: 'web'; url: string; title?: string } {
  return 'url' in context || ('type' in context && (context as any).type === 'web')
}

async function addImageToContext(file: File) {
  console.log(`🖼️ [addImageToContext] 开始处理图片: ${file.name}`)

  try {
    // 验证文件类型
    if (!VALID_IMAGE_TYPES.includes(file.type as any)) {
      console.error(`🖼️ [addImageToContext] 不支持的图片格式: ${file.type}`)
      return
    }

    // 读取图片为 base64
    console.log('🖼️ [addImageToContext] 读取图片为 base64...')
    const base64Data = await readImageAsBase64(file)
    console.log(`🖼️ [addImageToContext] base64 长度: ${base64Data.length}`)

    // 创建图片引用
    const imageRef: ImageReference = {
      type: 'image',
      displayType: ContextDisplayType.TAG,
      uri: `image://${file.name}`,
      name: file.name,
      mimeType: file.type,
      base64Data: base64Data,
      size: file.size
    }

    console.log('🖼️ [addImageToContext] 创建图片引用:', {
      type: imageRef.type,
      name: imageRef.name,
      mimeType: imageRef.mimeType,
      size: imageRef.size,
      base64Length: base64Data.length
    })

    // 添加到上下文列表
    emit('context-add', imageRef as any)
    console.log('🖼️ [addImageToContext] 已发送 context-add 事件')
  } catch (error) {
    console.error('🖼️ [addImageToContext] 读取图片失败:', error)
  }
}

function readImageAsBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      const result = e.target?.result as string
      // 移除 data:image/xxx;base64, 前缀
      const base64 = result.split(',')[1]
      resolve(base64)
    }
    reader.onerror = reject
    reader.readAsDataURL(file)
  })
}

// 自动清理上下文选项
function handleAutoCleanupChange() {
  localStorage.setItem(AUTO_CLEANUP_KEY, autoCleanupContextsValue.value.toString())
  emit('auto-cleanup-change', autoCleanupContextsValue.value)
}

// Watch for popup visibility changes
watch(() => showContextSelectorPopup.value, (newVisible) => {
  if (newVisible) {
    contextSelectedIndex.value = 0
  }
})

watch(() => contextSearchResults.value, () => {
  contextSelectedIndex.value = 0
})

// Lifecycle
onMounted(() => {
  nextTick(() => {
    setTimeout(() => {
      focusInput()
    }, 200)
  })

  // 添加 Context Selector 键盘事件监听
  document.addEventListener('keydown', handleContextPopupKeyDown)
})

onUnmounted(() => {
  // 移除 Context Selector 键盘事件监听
  document.removeEventListener('keydown', handleContextPopupKeyDown)
})
</script>

<style scoped>
.unified-chat-input-container {
  position: relative;
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

/* Drop Zone Overlay */
.drop-zone-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(3, 102, 214, 0.1);
  border: 2px dashed var(--ide-accent, #0366d6);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
  pointer-events: none;
}

.drop-zone-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px;
  background: var(--ide-background, #ffffff);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.drop-icon {
  font-size: 48px;
}

.drop-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-accent, #0366d6);
}

/* Pending Task Bar */
.pending-task-bar {
  padding: 6px 12px;
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
  gap: 6px;
  padding: 6px 12px;
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

.add-image-btn {
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

.add-image-btn:hover:not(:disabled) {
  background: var(--ide-hover-background, #f6f8fa);
  border-color: var(--ide-accent, #0366d6);
}

.add-image-btn:disabled {
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

.context-tag.image-tag {
  padding: 4px;
}

.tag-image-preview {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 3px;
  border: 1px solid var(--ide-border, #e1e4e8);
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
  padding: 8px 12px;
  cursor: text;
  min-height: 40px;
  max-height: 300px;
}

.message-textarea {
  width: 100%;
  min-height: 40px;
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
  padding: 6px 12px;
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

/* 图片上传按钮 (底部工具栏) */
.image-upload-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: var(--ide-accent, #0366d6);
  opacity: 0.15;
  cursor: pointer;
  transition: all 0.2s;
}

.image-upload-btn:hover:not(:disabled) {
  opacity: 0.25;
  transform: scale(1.05);
}

.image-upload-btn:disabled {
  opacity: 0.05;
  cursor: not-allowed;
}

.image-upload-btn .btn-icon {
  font-size: 18px;
  color: var(--ide-accent, #0366d6);
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
  padding: 6px 8px;
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

.context-result-item:hover,
.context-result-item.selected {
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

/* Send Button Context Menu (发送按钮右键菜单) */
.send-context-menu {
  position: fixed;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 10000;
  min-width: 160px;
  padding: 4px;
  transform: translate(-50%, -100%);
  margin-top: -8px;
}

.context-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.2s;
  font-size: 14px;
}

.context-menu-item:hover {
  background: var(--ide-hover-background, #f6f8fa);
}

.menu-icon {
  font-size: 16px;
}

.menu-text {
  font-weight: 500;
  color: var(--ide-foreground, #24292e);
}

.context-menu-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 9999;
  background: transparent;
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

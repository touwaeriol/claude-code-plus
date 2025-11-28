<template>
  <div
    class="unified-chat-input-container"
    :class="{ focused: isFocused, generating: isGenerating, 'inline-mode': inline }"
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

      <!-- Context Tags (上下文标签) - 只显示前三个 -->
      <div
        v-for="(context, index) in visibleContexts"
        :key="`context-${index}`"
        class="context-tag"
        :class="{ 'image-tag': isImageContext(context) }"
      >
        <!-- 图片：只显示缩略图，点击可预览 -->
        <template v-if="isImageContext(context)">
          <img
            :src="getImagePreviewUrl(context)"
            class="tag-image-preview"
            alt="图片"
            @click="openImagePreview(context)"
          >
        </template>
        <!-- 非图片：显示图标和文字 -->
        <template v-else>
          <span class="tag-icon">{{ getContextIcon(context) }}</span>
          <span class="tag-text">{{ getContextDisplay(context) }}</span>
        </template>
        <button
          class="tag-remove"
          :title="t('common.remove')"
          @click="removeContext(context)"
        >
          ×
        </button>
      </div>

      <!-- 更多 Context 提示 -->
      <div
        v-if="hiddenContextsCount > 0"
        class="context-more-hint"
        :title="t('chat.moreContexts', { count: hiddenContextsCount })"
      >
        +{{ hiddenContextsCount }}
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
      :class="{ 'generating-state': isGenerating }"
      @click="focusInput"
      @drop.prevent="handleDrop"
      @dragover.prevent="handleDragOver"
      @dragleave="handleDragLeave"
    >
      <!-- 生成中指示器 -->
      <div
        v-if="isGenerating"
        class="generating-indicator"
      >
        <div class="generating-spinner" />
        <span class="generating-text">{{ t('chat.generating') }}</span>
      </div>

      <RichTextInput
        ref="richTextInputRef"
        v-model="inputText"
        class="message-textarea"
        :placeholder="placeholderText"
        :disabled="!enabled || isGenerating"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @keydown="handleKeydown"
        @paste-image="handlePasteImage"
        @submit="handleRichTextSubmit"
      />

      <!-- 内嵌图片预览（在文字下方） -->
      <div
        v-if="inlineImages.length > 0"
        class="inline-images-preview"
      >
        <div
          v-for="(image, index) in inlineImages"
          :key="index"
          class="inline-image-item"
        >
          <img
            :src="getInlineImagePreviewUrl(image)"
            class="inline-image-preview"
            :alt="image.name"
          >
          <button
            class="inline-image-remove"
            :title="t('common.remove')"
            @click="removeInlineImage(index)"
          >
            ×
          </button>
        </div>
      </div>
    </div>

    <!-- Bottom Toolbar (底部工具栏) -->
    <div class="bottom-toolbar">
      <!-- 左侧控件组 - Cursor 风格紧凑布局 -->
      <div class="toolbar-left">
        <div class="cursor-style-selectors">
          <!-- 模式选择器 - Cursor 风格（带灰色背景） -->
          <el-select
            v-if="showPermissionControls"
            v-model="selectedPermissionValue"
            class="cursor-selector mode-selector"
            :disabled="!enabled"
            placement="top-start"
            :teleported="true"
            popper-class="chat-input-select-dropdown mode-dropdown"
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
            <template #prefix>
              <span class="mode-prefix-icon">{{ getModeIcon(selectedPermissionValue) }}</span>
            </template>
            <el-option value="default" label="Default">
              <span class="mode-option-label">
                <span class="mode-icon">?</span>
                <span>Default</span>
              </span>
            </el-option>
            <el-option value="acceptEdits" label="Accept Edits">
              <span class="mode-option-label">
                <span class="mode-icon">✎</span>
                <span>Accept Edits</span>
              </span>
            </el-option>
            <el-option value="bypassPermissions" label="Bypass">
              <span class="mode-option-label">
                <span class="mode-icon">∞</span>
                <span>Bypass</span>
              </span>
            </el-option>
            <el-option value="plan" label="Plan">
              <span class="mode-option-label">
                <span class="mode-icon">☰</span>
                <span>Plan</span>
              </span>
            </el-option>
            <el-option value="dontAsk" label="Don't Ask">
              <span class="mode-option-label">
                <span class="mode-icon">🔇</span>
                <span>Don't Ask</span>
              </span>
            </el-option>
          </el-select>

          <!-- 模型选择器 - Cursor 风格 -->
          <el-select
            v-if="showModelSelector"
            v-model="selectedModelValue"
            class="cursor-selector model-selector"
            :disabled="!enabled"
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
            @change="handleUiModelChange"
          >
            <el-option
              v-for="option in uiModelOptions"
              :key="option"
              :value="option"
              :label="getUiModelLabel(option)"
            >
              <span class="model-option-label">
                {{ getUiModelLabel(option) }}
                <span v-if="isThinkingOption(option)" class="model-brain-icon">🧠</span>
              </span>
            </el-option>
          </el-select>

          <!-- Skip Permissions 复选框 - Cursor 风格 -->
          <label
            v-if="showPermissionControls"
            class="cursor-checkbox"
            :class="{ checked: skipPermissionsValue, disabled: !enabled }"
          >
            <input
              v-model="skipPermissionsValue"
              type="checkbox"
              :disabled="!enabled"
              @change="$emit('skip-permissions-change', skipPermissionsValue)"
            >
            <span class="checkbox-icon">{{ skipPermissionsValue ? '☑' : '☐' }}</span>
            <span class="checkbox-text">Skip</span>
          </label>
        </div>
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

        <!-- 图片上传按钮 - 简洁图标 -->
        <button
          class="icon-btn attach-btn"
          :disabled="!enabled || isGenerating"
          :title="t('chat.uploadImage')"
          @click="handleImageUploadClick"
        >
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/>
          </svg>
        </button>
        <input
          ref="imageInputRef"
          type="file"
          accept="image/jpeg,image/png,image/gif,image/bmp,image/webp"
          multiple
          style="display: none"
          @change="handleImageFileSelect"
        >

        <!-- 发送按钮 - 简洁图标 (三角形播放图标) -->
        <button
          v-if="!isGenerating"
          class="icon-btn send-icon-btn"
          :class="{ active: canSend }"
          :disabled="!canSend"
          :title="t('chat.sendMessageShortcut')"
          @click="handleSend"
          @contextmenu="handleSendButtonContextMenu"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <path d="M8 5.14v14l11-7-11-7z"/>
          </svg>
        </button>

        <!-- 停止按钮 - 简洁图标 -->
        <button
          v-else
          class="icon-btn stop-icon-btn"
          :title="t('chat.stopGenerating')"
          @click="$emit('stop')"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor">
            <rect x="4" y="4" width="16" height="16" rx="2"/>
          </svg>
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
        <span class="menu-text">{{ t('common.send') }}</span>
      </div>
      <div
        v-if="isGenerating && hasInput"
        class="context-menu-item"
        @click="handleInterruptAndSendFromContextMenu"
      >
        <span class="menu-icon">⚡</span>
        <span class="menu-text">{{ t('chat.interruptAndSend') }}</span>
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

    <!-- 图片预览模态框 -->
    <ImagePreviewModal
      :visible="previewVisible"
      :image-src="previewImageSrc"
      image-alt="图片预览"
      @close="closeImagePreview"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from '@/composables/useI18n'
import type { AiModel, PermissionMode, EnhancedMessage, TokenUsage as EnhancedTokenUsage, ImageReference } from '@/types/enhancedMessage'
import type { ContextReference, ContextDisplayType } from '@/types/display'
import type { ContentBlock } from '@/types/message'
import AtSymbolFilePopup from '@/components/input/AtSymbolFilePopup.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'
import RichTextInput from './RichTextInput.vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
import { isInAtQuery, replaceAtQuery } from '@/utils/atSymbolDetector'
import { useSettingsStore } from '@/stores/settingsStore'
import { useSessionStore } from '@/stores/sessionStore'
import { UiModelOption, UI_MODEL_LABELS, UI_MODEL_SHOW_BRAIN, MODEL_RESOLUTION_MAP } from '@/constants/models'

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
  showContextControls?: boolean
  showModelSelector?: boolean
  showPermissionControls?: boolean
  showSendButton?: boolean
  tokenUsage?: TokenUsage
  placeholderText?: string
  messageHistory?: EnhancedMessage[]  // 消息历史（用于Token计算）
  sessionTokenUsage?: EnhancedTokenUsage | null  // 会话级Token使用量
  // 内嵌编辑模式相关
  inline?: boolean           // 是否为内嵌模式（用于编辑消息）
  editDisabled?: boolean     // 是否禁用发送（当前阶段用于编辑模式）
}

interface Emits {
  (e: 'send', contents: ContentBlock[]): void
  (e: 'interrupt-and-send', contents: ContentBlock[]): void
  (e: 'stop'): void
  (e: 'context-add', context: ContextReference): void
  (e: 'context-remove', context: ContextReference): void
  (e: 'model-change', model: AiModel): void
  (e: 'permission-change', permission: PermissionMode): void
  (e: 'skip-permissions-change', skip: boolean): void
  (e: 'inline-images-change', images: File[]): void
  (e: 'cancel'): void  // 取消编辑（仅 inline 模式）
}

const props = withDefaults(defineProps<Props>(), {
  pendingTasks: () => [],
  contexts: () => [],
  isGenerating: false,
  enabled: true,
  selectedModel: 'SONNET',
  selectedPermission: 'default',
  skipPermissions: true,
  showContextControls: true,
  showModelSelector: true,
  showPermissionControls: true,
  showSendButton: true,
  placeholderText: '',
  inline: false,
  editDisabled: false
})

const emit = defineEmits<Emits>()

// i18n & settings & session
const { t } = useI18n()
const settingsStore = useSettingsStore()
const sessionStore = useSessionStore()
const settingsState = settingsStore.settings

// 安全获取当前 UI 模型，避免 settingsState 还未初始化时访问 undefined.model
function getSafeUiModel(): UiModelOption {
  try {
    const allOptions = Object.values(UiModelOption) as UiModelOption[]
    const raw = settingsState.value?.model as UiModelOption | undefined
    if (raw && allOptions.includes(raw)) {
      return raw
    }
  } catch (e) {
    console.warn('⚠️ getSafeUiModel 读取 settingsState 失败，使用默认模型:', e)
  }
  // 默认使用 Opus 4.5 思考模型，和 DEFAULT_SETTINGS 保持一致
  return UiModelOption.OPUS_45_THINKING
}
const thinkingTogglePending = ref(false)
const thinkingEnabled = computed(() => {
  const current = getSafeUiModel()
  return UI_MODEL_SHOW_BRAIN[current] ?? false
})

// Refs
const richTextInputRef = ref<InstanceType<typeof RichTextInput>>()
const textareaRef = ref<HTMLTextAreaElement>() // 保留用于兼容 @ 符号检测
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

// Inline Images State (内嵌图片，当输入框有文本时粘贴的图片)
const inlineImages = ref<File[]>([])
// 缓存内嵌图片的 URL 对象，用于预览和清理
const inlineImageUrls = new Map<File, string>()

// Image Preview State (图片预览)
const previewVisible = ref(false)
const previewImageSrc = ref('')

// Local state for props
const selectedModelValue = ref<UiModelOption>(getSafeUiModel())
const selectedPermissionValue = ref(props.selectedPermission)
const skipPermissionsValue = ref(props.skipPermissions)


// Computed
const visibleTasks = computed(() => {
  return props.pendingTasks.filter(
    task => task.status === 'PENDING' || task.status === 'RUNNING'
  )
})

const hasInput = computed(() => inputText.value.trim().length > 0)

const canSend = computed(() => {
  // 如果是编辑模式且禁用发送，则不能发送
  if (props.editDisabled) return false
  return (hasInput.value || inlineImages.value.length > 0) && props.enabled && !props.isGenerating
})

// 只显示前三个 context
const visibleContexts = computed(() => {
  return props.contexts.slice(0, 3)
})

// 隐藏的 context 数量
const hiddenContextsCount = computed(() => {
  return Math.max(0, props.contexts.length - 3)
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
// Model selection is now driven by settingsStore (UiModelOption)，不再直接依赖 props.selectedModel
watch(() => props.selectedPermission, (newValue) => {
  selectedPermissionValue.value = newValue
})

watch(() => props.skipPermissions, (newValue) => {
  skipPermissionsValue.value = newValue
})

// Watch input text and cursor position for @ symbol detection
watch([inputText, () => textareaRef.value?.selectionStart], () => {
  checkAtSymbol()
})

// Methods
function focusInput() {
  richTextInputRef.value?.focus()
}

function adjustHeight() {
  // RichTextInput 自动处理高度，这里保留空实现以兼容现有调用
}

/**
 * 处理 RichTextInput 的图片粘贴事件
 */
function handlePasteImage(file: File) {
  console.log('📋 [handlePasteImage] 接收到粘贴图片:', file.name)

  // 判断是否应该作为上下文还是内嵌图片
  // 如果没有文本内容，作为上下文；否则作为内嵌图片
  const text = inputText.value.trim()

  if (!text) {
    // 没有文本，作为上下文
    console.log('📋 [handlePasteImage] 没有文本，将图片作为上下文')
    addImageToContext(file)
  } else {
    // 有文本，作为内嵌图片
    console.log('📋 [handlePasteImage] 有文本，将图片作为内嵌图片')
    inlineImages.value.push(file)
    emit('inline-images-change', inlineImages.value)
  }
}

/**
 * 处理 RichTextInput 的提交事件
 */
async function handleRichTextSubmit(content: { text: string; images: { id: string; data: string; mimeType: string; name: string }[] }) {
  if (!props.enabled || props.isGenerating) return

  const text = content.text.trim()
  const hasContent = text || content.images.length > 0 || inlineImages.value.length > 0

  if (!hasContent) return

  // 构建 ContentBlock[]
  const contents: ContentBlock[] = []

  // 文本块
  if (text) {
    contents.push({ type: 'text', text } as ContentBlock)
  }

  // RichTextInput 中的图片
  for (const img of content.images) {
    contents.push({
      type: 'image',
      source: {
        type: 'base64',
        media_type: img.mimeType,
        data: img.data
      }
    } as ContentBlock)
  }

  // 内嵌图片（从 inlineImages 数组）
  for (const file of inlineImages.value) {
    const base64 = await readImageAsBase64(file)
    contents.push({
      type: 'image',
      source: {
        type: 'base64',
        media_type: file.type,
        data: base64
      }
    } as ContentBlock)
  }

  emit('send', contents)

  // 清理
  richTextInputRef.value?.clear()
  clearInlineImages()
  inputText.value = ''
  emit('inline-images-change', [])
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

async function handleKeydown(event: KeyboardEvent) {
  // ESC 键 - 取消编辑（仅 inline 模式）
  if (event.key === 'Escape' && props.inline) {
    event.preventDefault()
    emit('cancel')
    return
  }

  if (
    event.key === 'Tab' &&
    !event.shiftKey &&
    !event.ctrlKey &&
    !event.metaKey
  ) {
    event.preventDefault()
    await toggleThinkingEnabled('keyboard')
    return
  }
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

async function toggleThinkingEnabled(source: 'click' | 'keyboard' = 'click') {
  if (thinkingTogglePending.value) return
  thinkingTogglePending.value = true
  try {
    const nextValue = !thinkingEnabled.value
    console.log(`🧠 [ThinkingToggle] ${source} -> ${nextValue}`)
    await settingsStore.saveSettings({ thinkingEnabled: nextValue })
  } catch (error) {
    console.error('❌ 切换思考开关失败:', error)
  } finally {
    thinkingTogglePending.value = false
  }
}

const uiModelOptions = Object.values(UiModelOption)

function getUiModelLabel(option: UiModelOption): string {
  return UI_MODEL_LABELS[option] ?? option
}

// 获取模式对应的图标
function getModeIcon(mode: string): string {
  const icons: Record<string, string> = {
    'default': '?',
    'acceptEdits': '✎',
    'bypassPermissions': '∞',
    'plan': '☰',
    'dontAsk': '🔇'
  }
  return icons[mode] ?? '?'
}

function isThinkingOption(option: UiModelOption): boolean {
  return UI_MODEL_SHOW_BRAIN[option] ?? false
}

function handleUiModelChange(option: UiModelOption) {
  selectedModelValue.value = option

  // 解析模型配置
  const config = MODEL_RESOLUTION_MAP[option]
  if (config) {
    // 更新本地期望配置（Query 前会通过 RPC 同步到后端）
    sessionStore.setModel({
      modelId: config.modelId,
      thinkingEnabled: config.thinkingEnabled
    })
    console.log(`🔄 [handleUiModelChange] 模型配置已更新: ${config.modelId}, thinking=${config.thinkingEnabled}`)
  }
}

/**
 * 处理粘贴事件
 * 检测粘贴内容是否包含图片：
 * - 如果输入框有文本，图片作为内嵌图片（添加到用户消息内容中）
 * - 如果输入框为空，图片作为上下文（添加到 contexts）
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

      // 判断光标是否在最前面
      const cursorAtStart = textareaRef.value?.selectionStart === 0

      if (cursorAtStart) {
        // 光标在最前面：作为上下文处理
        console.log('📋 [handlePaste] 光标在最前面，将图片作为上下文')
        await addImageToContext(file)
      } else {
        // 光标不在最前面：作为内嵌图片处理
        console.log('📋 [handlePaste] 光标不在最前面，将图片作为内嵌图片')
        inlineImages.value.push(file)
        emit('inline-images-change', inlineImages.value)
      }
    }
  }
}

async function handleSend() {
  if (!canSend.value) return

  const text = inputText.value.trim()
  if (text || inlineImages.value.length > 0) {
    // 构建 ContentBlock[]
    const contents: ContentBlock[] = []

    // 文本块
    if (text) {
      contents.push({ type: 'text', text } as ContentBlock)
    }

    // 内嵌图片转换为 ImageBlock
    for (const file of inlineImages.value) {
      const base64 = await readImageAsBase64(file)
      contents.push({
        type: 'image',
        source: {
          type: 'base64',
          media_type: file.type,
          data: base64
        }
      } as ContentBlock)
    }

    emit('send', contents)

    // 清理内嵌图片和 URL
    clearInlineImages()
    richTextInputRef.value?.clear()
    inputText.value = ''
    emit('inline-images-change', [])
    adjustHeight()
  }
}

async function handleInterruptAndSend() {
  if ((!hasInput.value && inlineImages.value.length === 0) || !props.isGenerating) return

  const text = inputText.value.trim()

  // 构建 ContentBlock[]
  const contents: ContentBlock[] = []

  // 文本块
  if (text) {
    contents.push({ type: 'text', text } as ContentBlock)
  }

  // 内嵌图片转换为 ImageBlock
  for (const file of inlineImages.value) {
    const base64 = await readImageAsBase64(file)
    contents.push({
      type: 'image',
      source: {
        type: 'base64',
        media_type: file.type,
        data: base64
      }
    } as ContentBlock)
  }

  emit('interrupt-and-send', contents)

  // 清理内嵌图片和 URL
  clearInlineImages()
  richTextInputRef.value?.clear()
  inputText.value = ''
  emit('inline-images-change', [])
  adjustHeight()
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
    uri: result.relativePath,
    displayType: 'TAG',
    path: result.relativePath,
    fullPath: result.relativePath,
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
    return '图片'  // 简化显示，不显示无意义的文件名
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
 * 打开图片预览
 */
function openImagePreview(context: ContextReference) {
  if (isImageReference(context)) {
    previewImageSrc.value = getImagePreviewUrl(context)
    previewVisible.value = true
  }
}

/**
 * 关闭图片预览
 */
function closeImagePreview() {
  previewVisible.value = false
  previewImageSrc.value = ''
}

/**
 * 获取上下文图标（使用类型守卫）
 */
function getContextIcon(context: ContextReference): string {
  if (isImageReference(context)) return '🖼️'
  if (isFileReference(context)) return '📄'
  if (isUrlReference(context)) return '🌐'
  if (context.type === 'folder') return '📁'
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
  return t('chat.tokenTooltip', {
    input: u.inputTokens,
    output: u.outputTokens,
    cacheCreation: u.cacheCreationTokens,
    cacheRead: u.cacheReadTokens
  })
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

  // 判断光标是否在最前面
  const cursorAtStart = textareaRef.value?.selectionStart === 0

  for (let i = 0; i < files.length; i++) {
    const file = files[i]

    // 检查是否为图片文件
    if (file.type && file.type.startsWith('image/')) {
      if (cursorAtStart) {
        // 光标在最前面：作为上下文处理
        console.log('📋 [handleDrop] 光标在最前面，将图片作为上下文')
      await addImageToContext(file)
      } else {
        // 光标不在最前面：作为内嵌图片处理
        console.log('📋 [handleDrop] 光标不在最前面，将图片作为内嵌图片')
        inlineImages.value.push(file)
      }
    } else {
      // 非图片文件：作为上下文处理
      await addFileToContext(file)
    }
  }

  if (!cursorAtStart) {
    emit('inline-images-change', inlineImages.value)
  }
}

async function addFileToContext(file: File) {
  try {
    // 创建上下文引用
    const contextRef: ContextReference = {
      type: 'file',
      uri: file.name,
      displayType: 'TAG',
      path: file.name, // 在实际项目中应该获取相对路径
      fullPath: file.name
    }

    // 添加到上下文列表
    emit('context-add', contextRef)
  } catch (error) {
    console.error('Failed to read file:', error)
    // 可以添加错误提示
  }
}

// 图片上传功能
function handleImageUploadClick() {
  imageInputRef.value?.click()
}

async function handleImageFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files
  if (!files || files.length === 0) return

  // 判断光标是否在最前面
  const cursorAtStart = textareaRef.value?.selectionStart === 0

  for (let i = 0; i < files.length; i++) {
    if (cursorAtStart) {
      // 光标在最前面：作为上下文处理
    await addImageToContext(files[i])
    } else {
      // 光标不在最前面：作为内嵌图片处理
      inlineImages.value.push(files[i])
    }
  }

  if (!cursorAtStart) {
    emit('inline-images-change', inlineImages.value)
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
  return context.type === 'image'
}

// 别名，用于模板中调用
const isImageContext = isImageReference

/**
 * 类型守卫：检查是否为文件上下文
 */
function isFileReference(context: ContextReference): boolean {
  return context.type === 'file'
}

/**
 * 类型守卫：检查是否为 URL 上下文
 */
function isUrlReference(context: ContextReference): boolean {
  return 'url' in context || context.type === 'web'
}

async function addImageToContext(file: File) {
  console.log(`🖼️ [addImageToContext] 开始处理图片: ${file.name}`)

  try {
    // 验证文件类型
    if (!VALID_IMAGE_TYPES.includes(file.type as typeof VALID_IMAGE_TYPES[number])) {
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
      displayType: 'TAG' as ContextDisplayType,
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
    emit('context-add', imageRef)
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

/**
 * 获取内嵌图片预览 URL（用于内嵌图片预览）
 */
function getInlineImagePreviewUrl(file: File): string {
  if (!inlineImageUrls.has(file)) {
    const url = URL.createObjectURL(file)
    inlineImageUrls.set(file, url)
  }
  return inlineImageUrls.get(file)!
}

/**
 * 移除内嵌图片
 */
function removeInlineImage(index: number) {
  const image = inlineImages.value[index]
  if (image) {
    // 清理 URL 对象
    const url = inlineImageUrls.get(image)
    if (url) {
      URL.revokeObjectURL(url)
      inlineImageUrls.delete(image)
    }
    inlineImages.value.splice(index, 1)
    emit('inline-images-change', inlineImages.value)
  }
}

/**
 * 清空所有内嵌图片
 */
function clearInlineImages() {
  inlineImages.value.forEach(image => {
    const url = inlineImageUrls.get(image)
    if (url) {
      URL.revokeObjectURL(url)
      inlineImageUrls.delete(image)
    }
  })
  inlineImages.value = []
}

/**
 * 辅助函数：base64 转 File
 */
function base64ToFile(base64: string, filename: string, mimeType: string): File {
  const byteString = atob(base64)
  const ab = new ArrayBuffer(byteString.length)
  const ia = new Uint8Array(ab)
  for (let i = 0; i < byteString.length; i++) {
    ia[i] = byteString.charCodeAt(i)
  }
  return new File([ab], filename, { type: mimeType })
}

/**
 * 暴露方法供父组件调用（用于编辑队列消息时恢复内容）
 */
defineExpose({
  /**
   * 设置输入框内容（从 ContentBlock[] 恢复）
   */
  setContent(contents: ContentBlock[]) {
    // 清空当前状态
    inputText.value = ''
    clearInlineImages()

    // 解析 contents 填充到对应状态
    for (const block of contents) {
      if (block.type === 'text' && 'text' in block) {
        // 文本块：追加到 inputText（多个文本块用换行连接）
        if (inputText.value) inputText.value += '\n'
        inputText.value += (block as any).text
      } else if (block.type === 'image' && 'source' in block) {
        // 图片块：转换为 File 对象添加到 inlineImages
        const imageBlock = block as any
        if (imageBlock.source?.type === 'base64') {
          const ext = imageBlock.source.media_type.split('/')[1] || 'png'
          const file = base64ToFile(
            imageBlock.source.data,
            `image-${Date.now()}.${ext}`,
            imageBlock.source.media_type
          )
          inlineImages.value.push(file)
        }
      }
    }

    // 调整高度并通知图片变化
    adjustHeight()
    emit('inline-images-change', inlineImages.value)
  }
})

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
  
  // 清理内嵌图片的 URL 对象，避免内存泄漏
  inlineImageUrls.forEach(url => {
    URL.revokeObjectURL(url)
  })
  inlineImageUrls.clear()
  inlineImages.value = []
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

.unified-chat-input-container.generating {
  border-color: var(--ide-accent, #0366d6);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.15);
  animation: generating-pulse 2s ease-in-out infinite;
}

/* Inline 模式样式 - 用于编辑消息 */
.unified-chat-input-container.inline-mode {
  border-radius: 8px;
  margin: 0;
}

@keyframes generating-pulse {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.15);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.25);
  }
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
  position: relative;
  padding: 2px;
}

/* 图片标签的删除按钮 - 右上角叠加 */
.context-tag.image-tag .tag-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 14px;
  height: 14px;
  font-size: 10px;
  background: var(--ide-error, #d73a49);
  color: white;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s;
}

.context-tag.image-tag:hover .tag-remove {
  opacity: 1;
}

.tag-image-preview {
  width: 32px;
  height: 32px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid var(--ide-border, #e1e4e8);
  cursor: pointer;
  transition: transform 0.15s;
}

.tag-image-preview:hover {
  transform: scale(1.05);
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

.context-more-hint {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  color: var(--ide-secondary-foreground, #6a737d);
  cursor: default;
}

/* Input Area */
.input-area {
  position: relative;
  padding: 8px 12px;
  cursor: text;
  min-height: 40px;
  max-height: 300px;
}

.input-area.generating-state {
  padding-top: 32px;
}

/* 生成中指示器 */
.generating-indicator {
  position: absolute;
  top: 8px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  z-index: 1;
}

.generating-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--ide-border, #e1e4e8);
  border-top-color: var(--ide-accent, #0366d6);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.generating-text {
  font-size: 12px;
  color: var(--ide-accent, #0366d6);
  font-weight: 500;
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

/* 内嵌图片预览 */
.inline-images-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
  margin-bottom: 4px;
}

.inline-image-item {
  position: relative;
  display: inline-block;
}

.inline-image-preview {
  width: 64px;
  height: 64px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--ide-border, #e1e4e8);
  cursor: pointer;
  transition: transform 0.2s;
}

.inline-image-preview:hover {
  transform: scale(1.05);
}

.inline-image-remove {
  position: absolute;
  top: -8px;
  right: -8px;
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: var(--ide-error, #d73a49);
  color: white;
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.2);
  transition: transform 0.2s;
}

.inline-image-remove:hover {
  transform: scale(1.1);
}

:global(.theme-dark) .inline-image-preview {
  border-color: var(--ide-border, #3c3c3c);
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
  gap: 4px;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ========== Cursor 风格选择器容器 ========== */
.cursor-style-selectors {
  display: flex;
  align-items: center;
  gap: 2px;
}

/* ========== Cursor 风格选择器 - 无边框紧凑样式 ========== */
.cursor-selector {
  font-size: 13px;
}

/* 模式选择器 - 带灰色背景 */
.cursor-selector.mode-selector {
  width: auto;
  min-width: 100px;
}

.cursor-selector.mode-selector :deep(.el-select__wrapper) {
  background: rgba(0, 0, 0, 0.08) !important;
  border-radius: 6px;
  padding: 4px 8px;
}

/* 模式选择器前缀图标 */
.mode-prefix-icon {
  font-size: 14px;
  color: var(--ide-secondary-foreground, #6a737d);
  margin-right: 2px;
}

.cursor-selector.model-selector {
  width: auto;
  min-width: 90px;
}

/* 移除边框和背景，使用纯文字样式 */
.cursor-selector :deep(.el-select__wrapper) {
  padding: 4px 6px;
  border: none !important;
  border-radius: 4px;
  background: transparent !important;
  box-shadow: none !important;
  min-height: 24px;
  gap: 2px;
}

.cursor-selector :deep(.el-select__wrapper):hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.05)) !important;
}

.cursor-selector :deep(.el-select__wrapper.is-focused) {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.05)) !important;
  box-shadow: none !important;
}

.cursor-selector :deep(.el-select__placeholder) {
  color: var(--ide-secondary-foreground, #6a737d);
  font-size: 13px;
}

.cursor-selector :deep(.el-select__selection) {
  color: var(--ide-secondary-foreground, #6a737d);
  font-size: 13px;
}

.cursor-selector :deep(.el-select__suffix) {
  color: var(--ide-secondary-foreground, #9ca3af);
  margin-left: 0;
}

.cursor-selector :deep(.el-select__suffix .el-icon) {
  font-size: 12px;
}

.cursor-selector.is-disabled :deep(.el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
}

/* ========== Cursor 风格复选框 ========== */
.cursor-checkbox {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  border-radius: 4px;
  font-size: 13px;
  color: var(--ide-secondary-foreground, #6a737d);
  cursor: pointer;
  user-select: none;
  transition: background 0.15s ease;
}

.cursor-checkbox:hover:not(.disabled) {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.05));
}

.cursor-checkbox.checked {
  color: var(--ide-accent, #0366d6);
}

.cursor-checkbox.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.cursor-checkbox input[type="checkbox"] {
  display: none;
}

.cursor-checkbox .checkbox-icon {
  font-size: 14px;
}

.cursor-checkbox .checkbox-text {
  font-size: 13px;
}

/* ========== 模式选择器下拉选项样式 ========== */
.mode-option-label {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.mode-option-label .mode-icon {
  font-size: 14px;
  width: 16px;
  text-align: center;
  color: var(--ide-secondary-foreground, #6a737d);
}

/* 模式下拉弹层样式 */
.mode-dropdown .el-select-dropdown__item.is-selected .mode-icon {
  color: var(--ide-background, #ffffff);
}

/* 模型下拉弹层基础样式，使用主题变量 */
.chat-input-select-dropdown {
  background-color: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
}

.chat-input-select-dropdown .el-select-dropdown__item {
  color: var(--ide-foreground, #24292e);
}

.chat-input-select-dropdown .el-select-dropdown__item.hover,
.chat-input-select-dropdown .el-select-dropdown__item:hover {
  background-color: var(--ide-hover-background, #f6f8fa);
}

/* 选中项高亮：背景用 accent，文字用背景色（形成对比） */
.chat-input-select-dropdown .el-select-dropdown__item.is-selected {
  background-color: var(--ide-accent, #0366d6);
  color: var(--ide-background, #ffffff) !important;
}

.chat-input-select-dropdown .el-select-dropdown__item.is-selected .model-option-label {
  color: var(--ide-background, #ffffff);
}

.chat-input-select-dropdown .model-option-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.chat-input-select-dropdown .model-brain-icon {
  font-size: 14px;
}

/* 暗色主题下的模型下拉弹层适配 */
::global(.theme-dark) .chat-input-select-dropdown {
  background-color: var(--ide-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}

::global(.theme-dark) .chat-input-select-dropdown .el-select-dropdown__item {
  color: var(--ide-foreground, #e6edf3);
}

::global(.theme-dark) .chat-input-select-dropdown .el-select-dropdown__item.hover,
::global(.theme-dark) .chat-input-select-dropdown .el-select-dropdown__item:hover {
  background-color: var(--ide-hover-background, #30363d);
}

::global(.theme-dark) .chat-input-select-dropdown .el-select-dropdown__item.is-selected {
  background-color: var(--ide-accent, #58a6ff);
  color: var(--ide-background, #0d1117) !important;
}

::global(.theme-dark) .chat-input-select-dropdown .el-select-dropdown__item.is-selected .model-option-label {
  color: var(--ide-background, #0d1117);
}

.model-selector :deep(.el-select__suffix),
.mode-selector :deep(.el-select__suffix) {
  color: var(--ide-secondary-foreground, #6a737d);
}

.model-selector.is-disabled :deep(.el-select__wrapper),
.mode-selector.is-disabled :deep(.el-select__wrapper) {
  opacity: 0.5;
  cursor: not-allowed;
  background: var(--ide-panel-background, #f6f8fa);
}


.thinking-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 999px;
  background: var(--ide-background, #ffffff);
  font-size: 12px;
  color: var(--ide-secondary-foreground, #6a737d);
  cursor: pointer;
  transition: all 0.2s ease;
}

.thinking-toggle .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ide-border, #d0d7de);
  transition: background 0.2s ease;
}

.thinking-toggle.active {
  border-color: var(--ide-accent, #0366d6);
  color: var(--ide-accent, #0366d6);
  background: rgba(3, 102, 214, 0.08);
}

.thinking-toggle.active .status-dot {
  background: var(--ide-accent, #0366d6);
}

.thinking-toggle:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.thinking-toggle .status-text {
  font-weight: 500;
}

.token-stats {
  font-size: 11px;
  color: var(--ide-secondary-foreground, #6a737d);
  padding: 4px 8px;
  background: var(--ide-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 4px;
}

/* ========== 简洁图标按钮 (Augment Code 风格) ========== */
.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--ide-secondary-foreground, #6a737d);
  cursor: pointer;
  transition: all 0.15s ease;
}

.icon-btn:hover:not(:disabled) {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.06));
  color: var(--ide-foreground, #24292e);
}

.icon-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* 附件按钮 */
.icon-btn.attach-btn {
  color: var(--ide-secondary-foreground, #6a737d);
}

.icon-btn.attach-btn:hover:not(:disabled) {
  color: var(--ide-accent, #0366d6);
}

/* 发送按钮 */
.icon-btn.send-icon-btn {
  color: var(--ide-secondary-foreground, #9ca3af);
}

.icon-btn.send-icon-btn.active {
  color: var(--ide-foreground, #24292e);
}

.icon-btn.send-icon-btn.active:hover {
  color: var(--ide-accent, #0366d6);
  background: rgba(3, 102, 214, 0.1);
}

/* 停止按钮 */
.icon-btn.stop-icon-btn {
  color: var(--ide-error, #d73a49);
}

.icon-btn.stop-icon-btn:hover {
  background: rgba(215, 58, 73, 0.1);
}

/* 暗色主题 */
:global(.theme-dark) .icon-btn {
  color: var(--ide-secondary-foreground, #8b949e);
}

:global(.theme-dark) .icon-btn:hover:not(:disabled) {
  background: var(--ide-hover-background, rgba(255, 255, 255, 0.08));
  color: var(--ide-foreground, #e6edf3);
}

:global(.theme-dark) .icon-btn.send-icon-btn.active {
  color: var(--ide-foreground, #e6edf3);
}

:global(.theme-dark) .icon-btn.send-icon-btn.active:hover {
  color: var(--ide-accent, #58a6ff);
  background: rgba(88, 166, 255, 0.15);
}

:global(.theme-dark) .icon-btn.stop-icon-btn {
  color: var(--ide-error, #f85149);
}

:global(.theme-dark) .icon-btn.stop-icon-btn:hover {
  background: rgba(248, 81, 73, 0.15);
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

:global(.theme-dark) .unified-chat-input-container.generating {
  border-color: var(--ide-accent, #58a6ff);
  box-shadow: 0 0 0 3px rgba(88, 166, 255, 0.15);
}

:global(.theme-dark) .generating-spinner {
  border-color: var(--ide-border, #3c3c3c);
  border-top-color: var(--ide-accent, #58a6ff);
}

:global(.theme-dark) .generating-text {
  color: var(--ide-accent, #58a6ff);
}

:global(.theme-dark) .top-toolbar,
:global(.theme-dark) .bottom-toolbar {
  border-color: var(--ide-border, #3c3c3c);
}

:global(.theme-dark) .add-context-btn,
:global(.theme-dark) .context-tag,
:global(.theme-dark) .token-stats {
  background: var(--ide-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}

/* Cursor 风格选择器暗色主题 */
:global(.theme-dark) .cursor-selector :deep(.el-select__wrapper):hover,
:global(.theme-dark) .cursor-selector :deep(.el-select__wrapper.is-focused) {
  background: var(--ide-hover-background, rgba(255, 255, 255, 0.08)) !important;
}

/* 模式选择器暗色主题 - 灰色背景 */
:global(.theme-dark) .cursor-selector.mode-selector :deep(.el-select__wrapper) {
  background: rgba(255, 255, 255, 0.12) !important;
}

:global(.theme-dark) .cursor-selector :deep(.el-select__selection) {
  color: var(--ide-secondary-foreground, #9ca3af);
}

:global(.theme-dark) .mode-option-label .mode-icon {
  color: var(--ide-secondary-foreground, #9ca3af);
}

:global(.theme-dark) .cursor-checkbox {
  color: var(--ide-secondary-foreground, #9ca3af);
}

:global(.theme-dark) .cursor-checkbox:hover:not(.disabled) {
  background: var(--ide-hover-background, rgba(255, 255, 255, 0.08));
}

:global(.theme-dark) .cursor-checkbox.checked {
  color: var(--ide-accent, #58a6ff);
}

:global(.theme-dark) .context-selector-popup {
  background: var(--ide-background, #2b2b2b);
  border-color: var(--ide-border, #3c3c3c);
}
</style>

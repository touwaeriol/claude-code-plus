<template>
  <div
    class="unified-chat-input-container"
    :class="{ focused: isFocused, generating: isGenerating, 'inline-mode': inline }"
    :style="{
      height: containerHeight ? containerHeight + 'px' : undefined,
      '--input-max-height': INPUT_MAX_HEIGHT + 'px'
    }"
  >
    <!-- 顶部拖拽条 -->
    <div
      class="resize-handle"
      @mousedown="startResize"
    >
      <div class="resize-handle-bar" />
    </div>

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
        <span class="btn-text">{{ t('chat.addContext') }}</span>
      </button>

      <!-- Active File Tag (当前打开的文件 - 由 IDEA 推送) -->
      <el-tooltip
        v-if="hasActiveFile"
        :content="activeFileTooltip"
        placement="top"
        :show-after="300"
      >
        <div
          class="context-tag active-file-tag"
          :class="{ 'active-file-disabled': activeFileDisabled }"
          @click.stop="toggleActiveFileEnabled"
        >
          <span class="tag-file-name">{{ activeFileName }}</span>
          <span v-if="activeFileLineRange" class="tag-line-range">{{ activeFileLineRange }}</span>
        </div>
      </el-tooltip>

      <!-- Context Tags (上下文标签) - 全部显示，自然换行 -->
      <el-tooltip
        v-for="(context, index) in contexts"
        :key="`context-${index}`"
        :content="getContextTooltip(context)"
        placement="top"
        :show-after="300"
      >
        <div
          class="context-tag"
          :class="{
            'image-tag': isImageContext(context)
          }"
        >
          <!-- 图片：只显示缩略图，点击可预览 -->
          <template v-if="isImageContext(context)">
            <img
              :src="getContextImagePreviewUrl(context)"
              class="tag-image-preview"
              @click.stop="openContextImagePreview(context)"
              alt="图片"
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
            @click.stop="removeContext(context)"
          >
            ×
          </button>
        </div>
      </el-tooltip>
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

    <!-- 错误提示区域 -->
    <div
      v-if="currentError"
      class="error-banner"
      @click="handleClearError"
    >
      <span class="error-icon">⚠️</span>
      <span class="error-text">{{ currentError }}</span>
      <button class="error-dismiss" title="关闭">×</button>
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

      <RichTextInput
        ref="richTextInputRef"
        v-model="inputText"
        class="message-textarea"
        :placeholder="placeholderText"
        :disabled="!enabled"
        @focus="isFocused = true"
        @blur="isFocused = false"
        @keydown="handleKeydown"
        @paste-image="handlePasteImage"
        @preview-image="handleInputImagePreview"
        @submit="handleRichTextSubmit"
      />
    </div>

    <!-- Bottom Toolbar (底部工具栏) -->
    <div class="bottom-toolbar">
      <!-- 左侧控件组 - Cursor 风格紧凑布局 -->
      <div class="toolbar-left">
        <div class="cursor-style-selectors">
          <!-- 后端切换器 - 放在最左侧 -->
          <div class="backend-switcher-inline" @click.stop="toggleBackendSwitcher">
            <BackendIcon :type="backendType" :size="14" />
            <span class="backend-name">{{ getBackendDisplayName(backendType) }}</span>
            <svg class="dropdown-arrow" width="8" height="8" viewBox="0 0 8 8" fill="currentColor">
              <path d="M1 2.5L4 5.5L7 2.5" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round"/>
            </svg>
            <!-- 下拉菜单 -->
            <div v-if="showBackendSwitcher" class="backend-dropdown">
              <div
                v-for="backend in availableBackends"
                :key="backend"
                class="backend-option"
                :class="{ active: backend === backendType }"
                @click.stop="handleBackendChange(backend)"
              >
                <BackendIcon :type="backend" :size="14" />
                <span>{{ getBackendDisplayName(backend) }}</span>
                <span v-if="backend === backendType" class="check-icon">✓</span>
              </div>
            </div>
          </div>

          <!-- Claude 权限模式选择器 - Cursor 风格（带灰色背景） -->
          <el-select
            v-if="showPermissionControls && backendType === 'claude'"
            v-model="selectedPermissionValue"
            class="cursor-selector mode-selector"
            :disabled="!enabled"
            placement="top-start"
            :teleported="true"
            popper-class="chat-input-select-dropdown mode-dropdown claude-mode-dropdown"
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
            @change="setPermissionMode(selectedPermissionValue)"
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
          </el-select>

          <!-- ========== Claude 后端控件组 ========== -->
          <template v-if="showModelSelector && backendType === 'claude'">
            <!-- Claude 模型选择器 -->
            <el-select
              v-model="selectedModelValue"
              class="cursor-selector model-selector"
              :disabled="!enabled"
              placement="top-start"
              :teleported="true"
              popper-class="chat-input-select-dropdown claude-model-dropdown"
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
              @change="handleBaseModelChange"
            >
              <el-option
                v-for="model in claudeModelOptions"
                :key="model.modelId"
                :value="model.modelId"
                :label="model.displayName"
              >
                <span class="model-option-label">
                  {{ model.displayName }}
                </span>
              </el-option>
            </el-select>

            <!-- Claude 思考级别控件 -->
            <ThinkingToggle
              :thinking-mode="currentThinkingMode"
              :thinking-tokens="thinkingLevel"
              :thinking-levels="thinkingLevels"
              @change="handleThinkingLevelChange"
            />
          </template>

        </div>

        <!-- ========== Codex 工具栏（独立组件） ========== -->
        <CodexToolbar
          v-if="backendType === 'codex'"
          :model="codexModel"
          :sandbox-mode="codexSandboxMode"
          :reasoning-effort="codexReasoningEffort"
          :disabled="!enabled"
          @update:model="handleCodexModelChange"
          @update:sandbox-mode="handleCodexSandboxModeChange"
          @update:reasoning-effort="handleCodexReasoningEffortChange"
        />

        <!-- Skip Permissions 开关（Claude 和 Codex 共用） -->
        <StatusToggle
          v-if="showPermissionControls"
          :label="t('permission.mode.bypass')"
          :enabled="skipPermissionsValue"
          :disabled="!enabled"
          :show-icon="false"
          :tooltip="t('permission.mode.bypassTooltip')"
          @toggle="handleSkipPermissionsChange"
        />
      </div>

      <!-- 右侧按钮组 -->
      <div class="toolbar-right">
        <!-- 上下文使用量指示器 -->
        <ContextUsageIndicator
          :session-token-usage="sessionTokenUsage"
        />

        <StatusToggle
          v-if="showContextControls && !inline"
          class="auto-cleanup-toggle"
          :label="t('chat.autoCleanupContext')"
          :enabled="autoCleanupContexts"
          :disabled="!enabled"
          :show-icon="false"
          :tooltip="t('chat.autoCleanupContextTooltip')"
          @toggle="handleAutoCleanupChange"
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
          :disabled="!enabled"
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

    <!-- Thinking Config Panel (思考配置面板) - Codex 后端使用 -->
    <div
      v-if="showThinkingConfig && backendType === 'codex'"
      class="thinking-config-dropdown"
    >
      <ThinkingConfigPanel
        :backend-type="backendType"
        :model-value="currentThinkingConfig"
        @update:model-value="handleThinkingConfigUpdate"
      />
    </div>

    <!-- Send Button Context Menu (发送按钮右键菜单) -->
    <div
      v-if="showSendContextMenu"
      ref="sendContextMenuRef"
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
        @click="handleForceSendFromContextMenu"
      >
        <span class="menu-icon">⚡</span>
        <span class="menu-text">{{ t('chat.interruptAndSend') }}</span>
      </div>
    </div>

    <!-- Context menu：通过全局监听关闭（不使用全屏遮罩） -->
    <!-- Context Selector Popup (上下文选择器弹窗) - 使用统一组件 -->
    <FileSelectPopup
      :visible="showContextSelectorPopup"
      :files="contextSearchResults"
      :anchor-element="addContextButtonRef ?? null"
      :show-search-input="true"
      :placeholder="t('tools.search')"
      :is-indexing="contextIsIndexing"
      @select="handleContextSelect"
      @dismiss="handleContextDismiss"
      @search="handleContextSearch"
    />

    <!-- @ Symbol File Popup (@ 符号文件选择弹窗) -->
    <AtSymbolFilePopup
      :visible="showAtSymbolPopup"
      :files="atSymbolSearchResults"
      :anchor-element="richTextInputElement"
      :at-position="atSymbolPosition"
      :is-indexing="atSymbolIsIndexing"
      @select="handleAtSymbolFileSelect"
      @dismiss="dismissAtSymbolPopup"
    />

    <!-- Slash Command Popup (斜杠命令弹窗) -->
    <SlashCommandPopup
      :visible="showSlashCommandPopup"
      :query="slashCommandQuery"
      :anchor-element="richTextInputElement"
      @select="handleSlashCommandSelect"
      @dismiss="dismissSlashCommandPopup"
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
import { AiModel, type PermissionMode, type EnhancedMessage, type TokenUsage as EnhancedTokenUsage, type ImageReference } from '@/types/enhancedMessage'
import type { ContextReference } from '@/types/display'
import type { ContentBlock } from '@/types/message'
import type { ActiveFileInfo } from '@/composables/useActiveFile'
export type { ActiveFileInfo }  // 重新导出供父组件使用
import AtSymbolFilePopup from '@/components/input/AtSymbolFilePopup.vue'
import FileSelectPopup from '@/components/input/FileSelectPopup.vue'
import SlashCommandPopup from '@/components/input/SlashCommandPopup.vue'
import ContextUsageIndicator from './ContextUsageIndicator.vue'
import ImagePreviewModal from '@/components/common/ImagePreviewModal.vue'
import RichTextInput from './RichTextInput.vue'
import ThinkingToggle from './ThinkingToggle.vue'
import StatusToggle from './StatusToggle.vue'
import ThinkingConfigPanel from '@/components/settings/ThinkingConfigPanel.vue'
import CodexToolbar from './CodexToolbar.vue'
import { fileSearchService, type IndexedFileInfo } from '@/services/fileSearchService'
// ideaBridgeService 保留供未来使用
// import { ideaBridgeService } from '@/services/ideaApi'
// isInAtQuery 现在由 useAtSymbol composable 内部使用
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
// Composables
import { useImageHandling } from '@/composables/useImageHandling'
import { useDragAndDrop } from '@/composables/useDragAndDrop'
import { useInputResize, INPUT_MAX_HEIGHT } from '@/composables/useInputResize'
import { useModelSelection } from '@/composables/useModelSelection'
import { useContextMenu } from '@/composables/useContextMenu'
import { useCodexConfig } from '@/composables/useCodexConfig'
import { useActiveFile } from '@/composables/useActiveFile'
import { useAtSymbol } from '@/composables/useAtSymbol'
import { useSlashCommands, type SlashCommand } from '@/composables/useSlashCommands'
import { useSendMessage } from '@/composables/useSendMessage'
import { useThinkingConfig } from '@/composables/useThinkingConfig'
import { usePendingTasks, type PendingTask } from '@/composables/usePendingTasks'
import { useTokenDisplay, type TokenUsage } from '@/composables/useTokenDisplay'
import { useContextHandling } from '@/composables/useContextHandling'
import { useKeyboardShortcuts } from '@/composables/useKeyboardShortcuts'
// Multi-backend types
import type { BackendType } from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import { getAvailableBackends, getBackendDisplayName } from '@/services/backendCapabilities'
import BackendIcon from '@/components/icons/BackendIcon.vue'
import { isCodexThinking, getCodexEffortLevels } from '@/types/thinking'
// Codex types
import type { CodexSandboxMode, CodexReasoningEffort } from '@/types/codex'

// PendingTask and TokenUsage types imported from composables

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
  autoCleanupContexts?: boolean
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
  // Toast 函数
  showToast?: (message: string, duration?: number) => void
  // v-model 支持：输入框文本（用于 Tab 切换时保持状态）
  modelValue?: string
  // Multi-backend support
  backendType?: BackendType  // 后端类型
  thinkingConfig?: ThinkingConfig  // 思考配置
}

interface SendOptions {
  /** 是否是斜杠命令（斜杠命令不发送 contexts） */
  isSlashCommand?: boolean
  /** IDE 上下文（当前打开的文件信息，结构化数据，发送后端时才转换为 XML） */
  ideContext?: ActiveFileInfo | null
}

interface Emits {
  (e: 'send', contents: ContentBlock[], options?: SendOptions): void
  (e: 'force-send', contents: ContentBlock[], options?: SendOptions): void
  (e: 'stop'): void
  (e: 'context-add', context: ContextReference): void
  (e: 'context-remove', context: ContextReference): void
  (e: 'auto-cleanup-change', value: boolean): void
  (e: 'skip-permissions-change', skip: boolean): void
  (e: 'cancel'): void  // 取消编辑（仅 inline 模式）
  (e: 'update:modelValue', value: string): void  // v-model 支持
  (e: 'update:thinkingConfig', value: ThinkingConfig): void  // 思考配置更新
  (e: 'update:backendType', value: BackendType): void  // 后端类型切换
}

const props = withDefaults(defineProps<Props>(), {
  pendingTasks: () => [],
  contexts: () => [],
  isGenerating: false,
  enabled: true,
  selectedModel: AiModel.SONNET,
  selectedPermission: 'default',
  skipPermissions: false,
  showContextControls: true,
  autoCleanupContexts: false,
  showModelSelector: true,
  showPermissionControls: true,
  showSendButton: true,
  placeholderText: '',
  inline: false,
  editDisabled: false,
  modelValue: '',
  backendType: 'claude'  // 默认为 Claude 后端
})

const emit = defineEmits<Emits>()

// i18n & stores
const { t } = useI18n()
const sessionStore = useSessionStore()
const settingsStore = useSettingsStore()

// ========== 初始化 Composables ==========

// 模型选择 composable
const {
  currentModel,
  currentThinkingMode,
  canToggleThinkingComputed: _canToggleThinkingComputed,
  thinkingLevel,
  thinkingLevels,
  thinkingEnabled: _thinkingEnabled,
  selectedPermissionValue,
  skipPermissionsValue,
  baseModelOptions: _baseModelOptions,
  claudeModelOptions,  // Claude 模型列表
  getBaseModelLabel: _getBaseModelLabel,
  getModeIcon,
  handleBaseModelChange,
  handleThinkingLevelChange,
  toggleThinkingEnabled,
  handleSkipPermissionsChange,
  cyclePermissionMode,
  setPermissionMode,
  updatePermission,
  updateSkipPermissions
} = useModelSelection({
  initialPermission: props.selectedPermission,
  initialSkipPermissions: props.skipPermissions,
  onSkipPermissionsChange: (skip) => emit('skip-permissions-change', skip)
})

// ========== Codex 配置（使用 composable） ==========
const {
  codexModel,
  codexSandboxMode,
  codexReasoningEffort,
  handleCodexModelChange,
  handleCodexSandboxModeChange,
  handleCodexReasoningEffortChange,
  cycleCodexReasoningEffort,
  cycleCodexSandboxMode
} = useCodexConfig()

// ========== 后端切换器 ==========

const showBackendSwitcher = ref(false)
const availableBackends = computed(() => getAvailableBackends())

function toggleBackendSwitcher() {
  showBackendSwitcher.value = !showBackendSwitcher.value
}

function closeBackendSwitcher() {
  showBackendSwitcher.value = false
}

function handleBackendChange(newBackend: BackendType) {
  showBackendSwitcher.value = false
  if (newBackend !== props.backendType) {
    emit('update:backendType', newBackend)
  }
}

// 点击外部关闭后端切换器
onMounted(() => {
  document.addEventListener('click', closeBackendSwitcher)
})

onUnmounted(() => {
  document.removeEventListener('click', closeBackendSwitcher)
})

// ========== Multi-Backend Thinking Config (使用 composable) ==========
const {
  showThinkingConfig,
  currentThinkingConfig,
  codexThinkingEnabled: _codexThinkingEnabled,
  codexEffortLabel: _codexEffortLabel,
  codexThinkingTooltip: _codexThinkingTooltip,
  toggleThinkingConfigPanel: _toggleThinkingConfigPanel,
  handleThinkingConfigUpdate,
  handleGlobalThinkingConfigClick
} = useThinkingConfig({
  backendType: computed(() => props.backendType),
  thinkingConfig: computed(() => props.thinkingConfig),
  codexReasoningEffort,
  getTabReasoningEffort: () => sessionStore.currentTab?.initialConnectOptions?.value?.reasoningEffort as CodexReasoningEffort | undefined,
  onConfigUpdate: (config) => emit('update:thinkingConfig', config)
})

// 当前错误信息（从 sessionStore 读取）
const currentError = computed(() => sessionStore.currentLastError)

// 清除错误
function handleClearError() {
  sessionStore.clearCurrentError()
}

// Refs
const richTextInputRef = ref<InstanceType<typeof RichTextInput>>()
// 获取 RichTextInput 的 DOM 元素用于 @ 符号弹窗定位
const richTextInputElement = computed(() => {
  return richTextInputRef.value?.$el as HTMLElement | null
})
const addContextButtonRef = ref<HTMLButtonElement>()
const imageInputRef = ref<HTMLInputElement>()

// State
// inputText 使用 computed 支持 v-model，实现多 Tab 输入框状态隔离
const inputText = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value)
})
const isFocused = ref(false)

// ========== Context Handling (使用 composable) ==========
const {
  showContextSelectorPopup,
  contextSearchResults,
  contextIsIndexing,
  isImageReference,
  isFileReference,
  isUrlReference,
  isImageContext,
  getContextFullPath,
  getContextDisplay,
  getContextIcon,
  handleAddContextClick,
  handleContextSearch,
  handleContextDismiss,
  handleContextSelect,
  removeContext
} = useContextHandling({
  onContextAdd: (ctx) => emit('context-add', ctx),
  onContextRemove: (ctx) => emit('context-remove', ctx)
})

// ========== Active File (使用 composable) ==========
const {
  currentActiveFile,
  activeFileDisabled,
  hasActiveFile,
  activeFileEnabled,
  activeFileName,
  activeFileLineRange,
  activeFileTooltip,
  toggleActiveFileEnabled,
  getActiveFileForSending
} = useActiveFile()

// ========== @ Symbol (使用 composable) ==========
const {
  showAtSymbolPopup,
  atSymbolPosition,
  atSymbolSearchResults,
  atSymbolIsIndexing,
  checkAtSymbol,
  handleAtSymbolFileSelect,
  dismissAtSymbolPopup
} = useAtSymbol({
  getCursorPosition: () => richTextInputRef.value?.getCursorPosition() ?? 0,
  getPlainText: () => richTextInputRef.value?.getText() ?? '',
  replaceRangeWithFileReference: (start, end, filePath) => {
    richTextInputRef.value?.replaceRangeWithFileReference(start, end, filePath)
  }
})

// ========== Slash Commands (使用 composable) ==========
const {
  showSlashCommandPopup,
  slashCommandQuery,
  isSlashCommandText,
  checkSlashCommand,
  handleSlashCommandSelect,
  dismissSlashCommandPopup
} = useSlashCommands({
  getPlainText: () => richTextInputRef.value?.getText() ?? '',
  setContent: (content) => {
    richTextInputRef.value?.setContent(content)
    inputText.value = content
  },
  focusInput: () => richTextInputRef.value?.focus()
})

// ========== Send Message (使用 composable) ==========
const {
  hasInput,
  canSend,
  handleSend,
  handleForceSend,
  handleRichTextSubmit: handleRichTextSubmitFromComposable
} = useSendMessage({
  richTextInputRef: richTextInputRef as any,
  inputText,
  enabled: computed(() => props.enabled),
  isGenerating: computed(() => props.isGenerating),
  editDisabled: computed(() => props.editDisabled),
  isSlashCommandText,
  dismissSlashCommandPopup,
  activeFileEnabled,
  currentActiveFile,
  onSend: (contents, options) => emit('send', contents, options),
  onForceSend: (contents, options) => emit('force-send', contents, options)
})

// ========== Keyboard Shortcuts (使用 composable) ==========
const { handleKeydown } = useKeyboardShortcuts({
  // Props (reactive)
  isGenerating: computed(() => props.isGenerating),
  enabled: computed(() => props.enabled),
  inline: computed(() => props.inline),
  backendType: computed(() => props.backendType),
  // Refs
  richTextInputRef: richTextInputRef as any,
  showThinkingConfig,
  // Composable functions
  checkAtSymbol,
  cyclePermissionMode,
  cycleCodexSandboxMode,
  toggleThinkingEnabled,
  cycleCodexReasoningEffort,
  handleForceSend,
  // Emits
  onStop: () => emit('stop'),
  onCancel: () => emit('cancel')
})

// 输入框大小调整 composable
const { containerHeight, startResize } = useInputResize()

// 右键菜单 composable
const {
  showSendContextMenu,
  sendContextMenuPosition,
  handleSendButtonContextMenu,
  handleSendFromContextMenu,
  handleForceSendFromContextMenu,
  closeSendContextMenu
} = useContextMenu({
  onSend: () => handleSend(),
  onForceSend: () => handleForceSend()
})

// 图片处理 composable
const {
  previewVisible,
  previewImageSrc,
  readImageAsBase64,
  addImageToContext,
  handlePasteImage: _handlePasteImage,
  handleImageFileSelect: _handleImageFileSelect,
  openImagePreview,
  closeImagePreview,
  getImagePreviewUrl
} = useImageHandling({
  onContextAdd: (ctx) => emit('context-add', ctx),
  onInsertToEditor: (base64, mimeType) => richTextInputRef.value?.insertImage(base64, mimeType),
  isCursorAtStart: () => richTextInputRef.value?.isCursorAtStart() ?? true,
  showToast: props.showToast
})

// 拖放 composable
const {
  isDragging,
  handleDragOver,
  handleDragLeave,
  handleDrop: _handleDrop
} = useDragAndDrop({
  onContextAdd: (ctx) => emit('context-add', ctx),
  onImageAdd: addImageToContext,
  onInsertImageToEditor: (base64, mimeType) => richTextInputRef.value?.insertImage(base64, mimeType),
  isCursorAtStart: () => richTextInputRef.value?.isCursorAtStart() ?? true,
  readImageAsBase64
})

// Local state for props
// selectedModelValue 直接绑定 currentModel（响应会话切换）
const selectedModelValue = computed({
  get: () => currentModel.value,
  set: (_val) => {
    // setter 由 handleBaseModelChange 处理
  }
})

// Computed
const sendContextMenuRef = ref<HTMLElement | null>(null)

function handleGlobalSendContextMenuMouseDown(event: MouseEvent) {
  if (!showSendContextMenu.value) return

  const targetNode = event.target instanceof Node ? event.target : null
  if (targetNode && sendContextMenuRef.value?.contains(targetNode)) {
    return
  }

  // 不吞事件：让本次点击继续传递给真实目标
  closeSendContextMenu()
}

function handleGlobalSendContextMenuContextMenu(event: MouseEvent) {
  if (!showSendContextMenu.value) return

  const targetNode = event.target instanceof Node ? event.target : null
  if (targetNode && sendContextMenuRef.value?.contains(targetNode)) {
    return
  }

  closeSendContextMenu()
}

function bindSendContextMenuGlobalHandlers() {
  document.addEventListener('mousedown', handleGlobalSendContextMenuMouseDown, true)
  document.addEventListener('contextmenu', handleGlobalSendContextMenuContextMenu, true)
  window.addEventListener('blur', closeSendContextMenu)
}

function unbindSendContextMenuGlobalHandlers() {
  document.removeEventListener('mousedown', handleGlobalSendContextMenuMouseDown, true)
  document.removeEventListener('contextmenu', handleGlobalSendContextMenuContextMenu, true)
  window.removeEventListener('blur', closeSendContextMenu)
}

watch(showSendContextMenu, (isOpen) => {
  if (isOpen) {
    bindSendContextMenuGlobalHandlers()
  } else {
    unbindSendContextMenuGlobalHandlers()
  }
})

// ========== Pending Tasks (使用 composable) ==========
const {
  visibleTasks,
  getTaskLabel,
  getTaskStatusText
} = usePendingTasks({
  pendingTasks: computed(() => props.pendingTasks)
})

// hasInput and canSend are now provided by useSendMessage composable


const placeholderText = computed(() => {
  return props.placeholderText || ''
})

// XML 转换逻辑已移至 userMessageBuilder.ts 的 ideContextToContentBlocks 函数
// ChatInput 现在传递结构化的 ideContext，由 useSessionMessages 在发送时转换为 XML

// Watch props changes
// Model selection is now driven by settingsStore (UiModelOption)，不再直接依赖 props.selectedModel
watch(() => props.selectedPermission, (newValue) => {
  updatePermission(newValue)
})

watch(() => props.skipPermissions, (newValue) => {
  updateSkipPermissions(newValue)
})

// Watch input text for @ symbol and slash command detection
// 光标位置变化通过 keydown 事件触发检测
watch(inputText, () => {
  checkAtSymbol()
  checkSlashCommand()
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
async function handlePasteImage(file: File) {
  await _handlePasteImage(file)
}

/**
 * 处理 RichTextInput 的提交事件
 * 使用 useSendMessage composable 提供的方法
 */
async function handleRichTextSubmit(_content: { text: string; images: { id: string; data: string; mimeType: string; name: string }[] }) {
  await handleRichTextSubmitFromComposable()
}

// handleKeydown is now provided by useKeyboardShortcuts composable
// toggleThinkingEnabled, cyclePermissionMode, getBaseModelLabel, getModeIcon,
// handleBaseModelChange, handleThinkingToggle, handleSkipPermissionsChange
// 这些函数现在由 useModelSelection composable 提供

// handleSend is now provided by useSendMessage composable

// handleForceSend is now provided by useSendMessage composable

// handleSendButtonContextMenu, handleSendFromContextMenu, handleForceSendFromContextMenu, closeSendContextMenu
// 这些函数现在由 useContextMenu composable 提供

// removeContext now provided by useContextHandling composable

function handleAutoCleanupChange(value: boolean) {
  emit('auto-cleanup-change', value)
}

// handleAddContextClick, handleContextSearch, handleContextDismiss, handleContextSelect
// now provided by useContextHandling composable


// Context display functions (getContextFullPath, getContextDisplay, getContextIcon)
// now provided by useContextHandling composable

/**
 * 获取上下文 tooltip（包含路径，图片可点击预览，文件可点击打开）
 */
function getContextTooltip(context: ContextReference): string {
  const path = getContextFullPath(context)
  if (isImageContext(context)) {
    return `${path}\n${t('chat.clickToPreview')}`
  }
  if (isFileReference(context)) {
    return `${path}\n${t('chat.clickToOpenFile')}`
  }
  return path
}

/**
 * 获取图片预览 URL（使用类型守卫）
 */
function getContextImagePreviewUrl(context: ContextReference): string {
  if (isImageReference(context)) {
    return getImagePreviewUrl(context as ImageReference)
  }
  return ''
}

/**
 * 打开上下文中的图片预览
 */
function openContextImagePreview(context: ContextReference) {
  if (isImageReference(context)) {
    openImagePreview(getContextImagePreviewUrl(context))
  }
}

/**
 * 处理输入框中图片预览
 */
function handleInputImagePreview(src: string) {
  openImagePreview(src)
}

// getTaskLabel and getTaskStatusText now provided by usePendingTasks composable

// ========== Token Display (使用 composable) ==========
const {
  formatTokenUsage,
  getTokenTooltip
} = useTokenDisplay({
  tokenUsage: computed(() => props.tokenUsage),
  backendType: computed(() => props.backendType)
})

// handleDragOver, handleDragLeave, handleDrop, addFileToContext
// 这些函数现在由 useDragAndDrop composable 提供

// 包装 _handleDrop 用于模板
async function handleDrop(event: DragEvent) {
  await _handleDrop(event)
}

// 图片上传功能
function handleImageUploadClick() {
  imageInputRef.value?.click()
}

async function handleImageFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  await _handleImageFileSelect(input.files)
  // 清空 input，允许重复选择同一文件
  input.value = ''
}

// Type guards (isImageReference, isFileReference, isUrlReference, isImageContext)
// now provided by useContextHandling composable

// addImageToContext 和 readImageAsBase64 现在由 useImageHandling composable 提供

/**
 * 暴露方法供父组件调用（用于编辑队列消息时恢复内容）
 */
defineExpose({
  /**
   * 设置输入框内容（从 ContentBlock[] 恢复）
   */
  setContent(contents: ContentBlock[]) {
    // 清空当前状态
    richTextInputRef.value?.clear()
    inputText.value = ''

    // 解析 contents 填充到编辑器
    for (const block of contents) {
      if (block.type === 'text' && 'text' in block) {
        // 文本块：设置到编辑器
        richTextInputRef.value?.setContent((block as any).text)
      } else if (block.type === 'image' && 'source' in block) {
        // 图片块：插入到编辑器
        const imageBlock = block as any
        if (imageBlock.source?.type === 'base64') {
          richTextInputRef.value?.insertImage(
            imageBlock.source.data,
            imageBlock.source.media_type
          )
        }
      }
    }

    // 调整高度
    adjustHeight()
  }
})

// handleGlobalKeydown is now handled by useKeyboardShortcuts composable

// Lifecycle
onMounted(() => {
  nextTick(() => {
    setTimeout(() => {
      focusInput()
    }, 200)
  })

  // 全局键盘监听由 useKeyboardShortcuts composable 处理

  // 添加全局点击监听（关闭思考配置面板）
  document.addEventListener('mousedown', handleGlobalThinkingConfigClick)

  // 活跃文件订阅由 useActiveFile composable 自动处理
})

onUnmounted(() => {
  unbindSendContextMenuGlobalHandlers()
  // 全局键盘监听由 useKeyboardShortcuts composable 处理
  // 移除全局点击监听
  document.removeEventListener('mousedown', handleGlobalThinkingConfigClick)
  // 活跃文件取消订阅由 useActiveFile composable 自动处理
})
</script>

<!-- Import external styles -->
<style src="./ChatInput.css" scoped></style>
<style src="./ChatInputSelectors.css" scoped></style>
<style src="./ChatInputDropdown.css"></style>

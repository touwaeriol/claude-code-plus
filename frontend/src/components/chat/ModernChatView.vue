<template>
  <div class="modern-chat-view">
    <ChatHeader
      v-if="!isIdeMode"
      class="chat-header-bar"
      @toggle-history="toggleHistoryOverlay"
    />

    <!-- 聊天界面内容 -->
    <div class="chat-screen-content">
      <!-- 消息列表 -->
      <MessageList
        :display-items="displayItems"
        :is-loading="uiState.isLoadingHistory"
        :is-streaming="currentSessionIsStreaming"
        :streaming-start-time="streamingStartTime"
        :input-tokens="streamingInputTokens"
        :output-tokens="streamingOutputTokens"
        class="message-list-area"
      />

      <!-- 会话统计栏 -->
      <SessionStatsBar :stats="toolStats" />

      <!-- 待发送队列（生成中时显示） -->
      <PendingMessageQueue
        @edit="handleEditPendingMessage"
        @remove="handleRemovePendingMessage"
      />

      <!-- 工具权限确认（输入框上方） -->
      <ToolPermissionInteractive />

      <!-- 用户问题（输入框上方） -->
      <AskUserQuestionInteractive />

      <!-- 输入区域 -->
      <ChatInput
        ref="chatInputRef"
        :pending-tasks="pendingTasks"
        :contexts="uiState.contexts"
        :is-generating="currentSessionIsStreaming"
        :actual-model-id="sessionStore.currentTab?.modelId.value || undefined"
        :selected-permission="sessionStore.currentTab?.permissionMode.value || 'default'"
        :skip-permissions="uiState.skipPermissions"
        :selected-model="uiState.selectedModel"
        :auto-cleanup-contexts="uiState.autoCleanupContexts"
        :message-history="[]"
        :session-token-usage="sessionTokenUsage"
        :show-context-controls="true"
        :show-model-selector="true"
        :show-permission-controls="true"
        :show-send-button="true"
        class="input-area"
        @send="handleSendMessage"
        @force-send="handleForceSend"
        @stop="handleStopGeneration"
        @context-add="handleAddContext"
        @context-remove="handleRemoveContext"
        @update:selected-model="handleModelChange"
        @update:selected-permission="handlePermissionModeChange"
        @update:skip-permissions="handleSkipPermissionsChange"
        @auto-cleanup-change="handleAutoCleanupChange"
      />
    </div>

    <!-- 错误对话框 -->
    <div
      v-if="uiState.hasError"
      class="error-dialog"
    >
      <div
        class="error-overlay"
        @click="handleClearError"
      />
      <div class="error-content">
        <div class="error-header">
          <span class="error-title">{{ t('chat.error.title') }}</span>
        </div>
        <div class="error-message">
          {{ uiState.errorMessage || t('chat.error.unknown') }}
        </div>
        <div class="error-actions">
          <button
            class="error-dismiss-btn"
            @click="handleClearError"
          >
            {{ t('common.ok') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 调试面板 (开发时使用) -->
    <div
      v-if="showDebug"
      class="debug-panel"
    >
      <div
        class="debug-header"
        @click="debugExpanded = !debugExpanded"
      >
        {{ t('chat.debug.title') }} {{ debugExpanded ? '▼' : '▶' }}
      </div>
      <div
        v-show="debugExpanded"
        class="debug-content"
      >
        <div class="debug-item">
          {{ t('chat.debug.sessionId') }}: {{ sessionId || t('chat.debug.notSet') }}
        </div>
        <div class="debug-item">
          {{ t('chat.debug.projectPath') }}: {{ projectPath }}
        </div>
        <div class="debug-item">
          {{ t('chat.debug.messageCount') }}: {{ displayItems.length }}
        </div>
        <div class="debug-item">
          {{ t('chat.debug.generating') }}: {{ uiState.isGenerating ? t('common.yes') : t('common.no') }}
        </div>
        <div class="debug-item">
          {{ t('chat.debug.pendingTasks') }}: {{ pendingTasks.length }}
        </div>
        <div class="debug-item">
          {{ t('chat.debug.contexts') }}: {{ uiState.contexts.length }}
        </div>
      </div>
    </div>

    <SessionListOverlay
      :visible="isHistoryOverlayVisible"
      :sessions="historySessions"
      :current-session-id="sessionStore.currentTabId"
      :loading="sessionStore.loading"
      @close="isHistoryOverlayVisible = false"
      @select-session="handleHistorySelect"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
import { useI18n } from '@/composables/useI18n'
import { useEnvironment } from '@/composables/useEnvironment'
import { setupIdeSessionBridge, onIdeHostCommand } from '@/bridges/ideSessionBridge'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import ChatHeader from './ChatHeader.vue'
import SessionListOverlay from './SessionListOverlay.vue'
import SessionStatsBar from './SessionStatsBar.vue'
import PendingMessageQueue from './PendingMessageQueue.vue'
import ToolPermissionInteractive from '@/components/tools/ToolPermissionInteractive.vue'
import AskUserQuestionInteractive from '@/components/tools/AskUserQuestionInteractive.vue'
import { calculateToolStats } from '@/utils/toolStatistics'
import type { ContentBlock } from '@/types/message'
import type { ContextReference, AiModel, PermissionMode, TokenUsage as EnhancedTokenUsage } from '@/types/enhancedMessage'
import type { PendingTask } from '@/types/pendingTask'

// Props 定义
interface Props {
  sessionId?: string
  projectPath?: string
  showDebug?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  sessionId: undefined,
  projectPath: () => process.cwd?.() || '/default/project',
  showDebug: false
})

// 使用 stores
const sessionStore = useSessionStore()
const settingsStore = useSettingsStore()
const { t } = useI18n()
const { isInIde, detectEnvironment } = useEnvironment()
const isIdeMode = isInIde
let disposeIdeBridge: (() => void) | null = null
let disposeHostCommand: (() => void) | null = null
const isHistoryOverlayVisible = ref(false)

// UI State 接口定义
interface ChatUiState {
  contexts: ContextReference[]
  isGenerating: boolean
  isLoadingHistory: boolean
  hasError: boolean
  errorMessage?: string
  actualModelId?: string
  selectedModel: AiModel
  selectedPermissionMode: PermissionMode
  skipPermissions: boolean
  autoCleanupContexts: boolean
}

// 状态定义
const uiState = ref<ChatUiState>({
  contexts: [],
  isGenerating: false,
  isLoadingHistory: false,
  hasError: false,
  errorMessage: undefined,
  actualModelId: undefined,
  selectedModel: 'DEFAULT' as AiModel,
  selectedPermissionMode: 'default' as PermissionMode,
  skipPermissions: false,  // 初始值，会在 onMounted 中从 settingsStore 同步
  autoCleanupContexts: false
})

// 从 sessionStore 获取 displayItems
const displayItems = computed(() => sessionStore.currentDisplayItems)

// 计算工具使用统计
const toolStats = computed(() => calculateToolStats(displayItems.value))

const historySessions = computed(() => {
  // 新架构：Tab 即会话，直接使用 activeTabs
  return sessionStore.activeTabs.map(tab => ({
    id: tab.tabId,
    name: tab.name.value,
    timestamp: tab.lastActiveAt.value,
    messageCount: tab.displayItems.length,
    isGenerating: tab.isGenerating.value,
    isConnected: tab.isConnected.value
  }))
})

const sessionTokenUsage = computed<EnhancedTokenUsage | null>(() => {
  return null
})

// 连接状态 - 直接从 Tab 的 connectionState 获取
const isConnected = computed(() => sessionStore.currentTab?.connectionState.status === 'CONNECTED')
const isConnecting = computed(() => sessionStore.currentTab?.connectionState.status === 'CONNECTING')

// Streaming 状态相关的计算属性
const currentSessionIsStreaming = computed(() => {
  return sessionStore.currentIsGenerating
})

const currentRequestTracker = computed(() => {
  return sessionStore.currentTab?.stats.getCurrentTracker() ?? null
})

const streamingStartTime = computed(() => {
  return currentRequestTracker.value?.requestStartTime ?? Date.now()
})

const streamingInputTokens = computed(() => {
  return currentRequestTracker.value?.inputTokens ?? 0
})

const streamingOutputTokens = computed(() => {
  return currentRequestTracker.value?.outputTokens ?? 0
})

const pendingTasks = ref<PendingTask[]>([])
const debugExpanded = ref(false)
const chatInputRef = ref<InstanceType<typeof ChatInput>>()

// 生命周期钩子
onMounted(async () => {
  console.log('ModernChatView mounted')

  // 从 settingsStore 同步 skipPermissions 初始值
  uiState.value.skipPermissions = settingsStore.settings.skipPermissions

  await detectEnvironment()
  if (isIdeMode.value) {
    disposeIdeBridge = setupIdeSessionBridge(sessionStore as any) // TODO: 更新 ideSessionBridge 类型
    disposeHostCommand = onIdeHostCommand((command) => {
      if (command.type === 'toggleHistory') {
        toggleHistoryOverlay()
      } else if (command.type === 'openHistory') {
        isHistoryOverlayVisible.value = true
      }
    })
  }

  try {
    if (props.sessionId) {
      console.log('External session detected:', props.sessionId)
      // 尝试找到已有的 Tab
      const existingTab = sessionStore.tabs.find(
        t => t.tabId === props.sessionId || t.sessionId.value === props.sessionId
      )
      if (existingTab) {
        await sessionStore.switchTab(existingTab.tabId)
      } else {
        // 尝试恢复会话
        const resumed = await sessionStore.resumeSession(props.sessionId)
        if (!resumed) {
          throw new Error('无法恢复指定会话')
        }
      }
      return
    }

    // 没有 Tab 时创建默认会话
    if (!sessionStore.hasTabs) {
      console.log('No existing tabs, creating default...')
      const tab = await sessionStore.createTab()
      console.log('Default tab created:', tab.tabId)
    }
  } catch (error) {
    console.error('Failed to initialize session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.initSessionFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
})

onBeforeUnmount(() => {
  console.log('ModernChatView unmounting')
  disposeIdeBridge?.()
  disposeIdeBridge = null
  disposeHostCommand?.()
  disposeHostCommand = null
})

// 监听外部传入的 sessionId 变化
watch(() => props.sessionId, async (newSessionId) => {
  if (!newSessionId) return
  console.log('Session ID changed:', newSessionId)
  try {
    // 尝试找到已有的 Tab
    const existingTab = sessionStore.tabs.find(
      t => t.tabId === newSessionId || t.sessionId.value === newSessionId
    )
    if (existingTab) {
      await sessionStore.switchTab(existingTab.tabId)
      return
    }
    // 尝试恢复会话
    const resumed = await sessionStore.resumeSession(newSessionId)
    if (!resumed) {
      throw new Error('无法恢复指定会话')
    }
  } catch (error) {
    console.error('Failed to switch session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.switchSessionFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
})

// 事件处理器
async function handleSendMessage(contents?: ContentBlock[]) {
  const safeContents = Array.isArray(contents) ? contents : []
  console.log('handleSendMessage:', safeContents.length, 'content blocks')

  try {
    // 没有当前 Tab 时创建新的
    if (!sessionStore.currentTab) {
      console.log('No active tab, creating new...')
      const newTab = await sessionStore.createTab()
      if (!newTab) {
        throw new Error('无法创建会话')
      }
    }

    if (!sessionStore.currentTab) {
      console.error('No active tab')
      uiState.value.hasError = true
      uiState.value.errorMessage = '当前没有激活的会话'
      return
    }

    // 检查连接状态
    if (isConnecting.value) {
      uiState.value.hasError = true
      uiState.value.errorMessage = t('chat.error.connecting')
      return
    }

    if (!isConnected.value) {
      // 连接断开，尝试自动重连
      uiState.value.hasError = true
      uiState.value.errorMessage = t('chat.error.disconnected')
      // 触发自动重连
      sessionStore.currentTab.reconnect()
      return
    }

    const currentContexts = [...uiState.value.contexts]
    uiState.value.contexts = []

    console.log('Sending message via currentTab')
    sessionStore.currentTab.sendMessage({
      contexts: currentContexts,
      contents: safeContents
    })
  } catch (error) {
    console.error('Failed to send message:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.sendMessageFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
}

async function handleForceSend(contents?: ContentBlock[]) {
  const safeContents = Array.isArray(contents) ? contents : []
  console.log('Force send:', safeContents.length, 'content blocks')
  // 强制发送：先打断当前生成，再插队发送
  await sessionStore.currentTab?.interrupt()
  await handleSendMessage(safeContents)
}

function handleEditPendingMessage(id: string) {
  console.log('Edit pending message:', id)
  const msg = sessionStore.currentTab?.editQueueMessage(id)
  if (msg) {
    uiState.value.contexts = [...msg.contexts]
    chatInputRef.value?.setContent(msg.contents)
  }
}

function handleRemovePendingMessage(id: string) {
  console.log('Remove pending message:', id)
  sessionStore.currentTab?.removeFromQueue(id)
}

async function handleStopGeneration() {
  console.log('🛑 Stopping generation via Esc key')
  try {
    // 清空消息队列（丢弃待发送的消息）
    sessionStore.currentTab?.clearQueue()
    // 调用后端中断
    await sessionStore.currentTab?.interrupt()
    console.log('✅ Interrupt request sent successfully')
  } catch (error) {
    console.error('❌ Failed to interrupt:', error)
  }
  // UI 状态更新
  uiState.value.isGenerating = false
}

function handleAddContext(context: ContextReference) {
  console.log('Adding context:', context)
  uiState.value.contexts.push(context)
}

function handleRemoveContext(context: ContextReference) {
  console.log('Removing context:', context)
  const index = uiState.value.contexts.findIndex(c => c.uri === context.uri)
  if (index !== -1) {
    uiState.value.contexts.splice(index, 1)
  }
}

function handleModelChange(model: AiModel) {
  console.log('Changing model:', model)
  uiState.value.selectedModel = model
}

function handlePermissionModeChange(mode: PermissionMode) {
  console.log('Changing permission mode:', mode)
  // 直接更新 Tab 的 permissionMode
  const tab = sessionStore.currentTab
  if (tab) {
    tab.permissionMode.value = mode as any
  }
  // 保存到设置
  settingsStore.updatePermissionMode(mode)
}

function handleSkipPermissionsChange(skip: boolean) {
  console.log('Toggle skip permissions:', skip)
  uiState.value.skipPermissions = skip
  // 延迟同步：只保存设置，发送消息时才同步到后端
  settingsStore.saveSettings({ skipPermissions: skip })
}

function handleAutoCleanupChange(cleanup: boolean) {
  console.log('Changing auto cleanup contexts:', cleanup)
  uiState.value.autoCleanupContexts = cleanup
}

function handleClearError() {
  console.log('Clearing error')
  uiState.value.hasError = false
  uiState.value.errorMessage = undefined
}

function toggleHistoryOverlay() {
  isHistoryOverlayVisible.value = !isHistoryOverlayVisible.value
}

async function handleHistorySelect(tabId: string) {
  await sessionStore.switchTab(tabId)
  isHistoryOverlayVisible.value = false
}
</script>

<style scoped>
.modern-chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%;
  background: var(--theme-background);
  color: var(--theme-foreground);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.chat-header-bar {
  flex-shrink: 0;
  border-bottom: 1px solid var(--theme-border);
}

/* 聊天界面内容 */
.chat-screen-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  flex: 1;
  min-height: 0;
  padding: 8px 12px;
  box-sizing: border-box;
  gap: 8px;
}

/* 消息列表区域 */
.message-list-area {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  background: var(--theme-card-background);
}

/* 输入区域 */
.input-area {
  flex-shrink: 0;
  width: 100%;
  padding: 0;
  box-sizing: border-box;
}

/* 错误对话框 */
.error-dialog {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
}

.error-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(2px);
}

.error-content {
  position: relative;
  background: var(--theme-card-background);
  border: 1px solid var(--theme-error);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  max-width: 500px;
  width: 90%;
  padding: 24px;
  animation: errorSlideIn 0.3s ease-out;
}

@keyframes errorSlideIn {
  from {
    opacity: 0;
    transform: translateY(-20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.error-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--theme-error);
}

.error-message {
  font-size: 14px;
  line-height: 1.6;
  color: var(--theme-foreground);
  margin-bottom: 20px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.error-actions {
  display: flex;
  justify-content: flex-end;
}

.error-dismiss-btn {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 6px;
  background: var(--theme-accent);
  color: var(--theme-selection-foreground);
  cursor: pointer;
  transition: all 0.2s;
}

.error-dismiss-btn:hover {
  opacity: 0.9;
  transform: translateY(-1px);
}

.error-dismiss-btn:active {
  transform: translateY(0);
}

/* 调试面板 */
.debug-panel {
  position: fixed;
  bottom: 16px;
  right: 16px;
  background: var(--theme-card-background);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  max-width: 300px;
  z-index: 100;
}

.debug-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  background: var(--theme-panel-background);
  transition: background 0.2s;
}

.debug-header:hover {
  background: var(--theme-hover-background);
}

.debug-content {
  padding: 12px 16px;
  font-size: 12px;
  border-top: 1px solid var(--theme-border);
}

.debug-item {
  margin-bottom: 6px;
  color: var(--theme-secondary-foreground);
}
</style>

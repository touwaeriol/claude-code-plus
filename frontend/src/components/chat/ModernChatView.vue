<template>
  <div class="modern-chat-view">
    <!-- 会话标签栏（仅 Web 模式显示，IDE 模式在标题栏） -->
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
        :is-loading="isHistoryLoading"
        :has-more-history="hasMoreHistory"
        :is-streaming="currentSessionIsStreaming"
        :streaming-start-time="streamingStartTime"
        :input-tokens="streamingInputTokens"
        :output-tokens="streamingOutputTokens"
        :connection-status="connectionStatusForDisplay"
        class="message-list-area"
        @load-more-history="handleLoadMoreHistory"
      />

      <!-- 压缩进行中状态 -->
      <CompactingCard v-if="isCompacting" />

      <!-- 待发送队列（生成中时显示） -->
      <PendingMessageQueue
        @edit="handleEditPendingMessage"
        @remove="handleRemovePendingMessage"
        @force-send="handleForceSendPendingMessage"
      />

      <!-- 悬浮层容器：权限请求 + 用户问题（覆盖在输入框上方） -->
      <div class="floating-overlay-container">
        <!-- 工具权限确认 -->
        <ToolPermissionInteractive />
        <!-- 用户问题 -->
        <AskUserQuestionInteractive />
      </div>

      <!-- 输入区域 -->
      <ChatInput
        ref="chatInputRef"
        v-model="currentTabInputText"
        :pending-tasks="pendingTasks"
        :contexts="currentTabContexts"
        :is-generating="currentSessionIsStreaming"
        :enabled="true"
        :show-toast="showToast"
        :actual-model-id="sessionStore.currentTab?.modelId.value || undefined"
        :selected-permission="sessionStore.currentTab?.permissionMode.value || 'default'"
        :skip-permissions="sessionStore.currentTab?.skipPermissions.value ?? false"
        :selected-model="uiState.selectedModel"
        :auto-cleanup-contexts="uiState.autoCleanupContexts"
        :message-history="[]"
        :session-token-usage="sessionTokenUsage"
        :streaming-start-time="streamingStartTime"
        :streaming-input-tokens="streamingInputTokens"
        :streaming-output-tokens="streamingOutputTokens"
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
          {{ t('chat.debug.contexts') }}: {{ currentTabContexts.length }}
        </div>
      </div>
    </div>

    <SessionListOverlay
      :visible="isHistoryOverlayVisible"
      :sessions="historySessions"
      :current-session-id="sessionStore.currentTabId"
      :loading="historyLoading"
      :loading-more="historyLoadingMore"
      :has-more="historyHasMore"
      @close="isHistoryOverlayVisible = false"
      @select-session="handleHistorySelect"
      @load-more="handleLoadMoreHistorySessions"
    />

    <!-- Toast 提示 -->
    <Transition name="toast">
      <div v-if="toastVisible" class="toast-container">
        <div class="toast-message">{{ toastMessage }}</div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, provide } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import { useEnvironment } from '@/composables/useEnvironment'
import { setupIdeSessionBridge, onIdeHostCommand } from '@/bridges/ideSessionBridge'
import { aiAgentService } from '@/services/aiAgentService'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import ChatHeader from './ChatHeader.vue'
import SessionListOverlay from './SessionListOverlay.vue'
import PendingMessageQueue from './PendingMessageQueue.vue'
import CompactingCard from './CompactingCard.vue'
import ToolPermissionInteractive from '@/components/tools/ToolPermissionInteractive.vue'
import AskUserQuestionInteractive from '@/components/tools/AskUserQuestionInteractive.vue'
import { calculateToolStats } from '@/utils/toolStatistics'
import type { ContentBlock } from '@/types/message'
import type { ContextReference, AiModel, PermissionMode, TokenUsage as EnhancedTokenUsage } from '@/types/enhancedMessage'
import type { PendingTask } from '@/types/pendingTask'
import type { HistorySessionMetadata } from '@/types/session'

// Props 定义
interface Props {
  sessionId?: string
  projectPath?: string
  showDebug?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  sessionId: undefined,
  projectPath: '/default/project',
  showDebug: false
})

// 提供上下文给子组件（如 TaskToolDisplay）
provide('projectPath', computed(() => props.projectPath))
provide('aiAgentService', aiAgentService)

// 使用 stores
const sessionStore = useSessionStore()
const { t } = useI18n()
const { isInIde, detectEnvironment } = useEnvironment()
const isIdeMode = isInIde
let disposeIdeBridge: (() => void) | null = null
let disposeHostCommand: (() => void) | null = null
const isHistoryOverlayVisible = ref(false)

// 历史会话列表状态
const historySessionList = ref<HistorySessionMetadata[]>([])
const historyLoading = ref(false)
const historyLoadingMore = ref(false)
const historyHasMore = ref(true)
const historyOffset = ref(0)
const HISTORY_PAGE_SIZE = 10

// Toast 提示状态
const toastMessage = ref('')
const toastVisible = ref(false)
let toastTimer: ReturnType<typeof setTimeout> | null = null

function showToast(message: string, duration = 2000) {
  toastMessage.value = message
  toastVisible.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastVisible.value = false
  }, duration)
}

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
  autoCleanupContexts: false
})

// 从 sessionStore 获取 displayItems
const displayItems = computed(() => sessionStore.currentDisplayItems)
const isHistoryLoading = computed(() =>
  uiState.value.isLoadingHistory || sessionStore.currentIsLoadingHistory
)
const hasMoreHistory = computed(() => sessionStore.currentHasMoreHistory)

// 是否正在压缩会话
const isCompacting = computed(() => sessionStore.currentTab?.isCompacting.value ?? false)

// 计算工具使用统计（保留以备将来使用）
const _toolStats = computed(() => calculateToolStats(displayItems.value))

const historySessions = computed(() => {
  // 活跃 Tab 列表
  const activeTabs = sessionStore.activeTabs.map(tab => ({
    id: tab.tabId,
    name: tab.name.value,
    timestamp: tab.lastActiveAt.value,
    messageCount: tab.displayItems.length,
    isGenerating: tab.isGenerating.value,
    isConnected: tab.isConnected.value,
    isActive: true
  }))

  // 历史会话列表（排除已激活的）
  const activeTabIds = new Set(activeTabs.map(t => t.id))
  const activeSessionIds = new Set(
    sessionStore.activeTabs
      .map(t => t.sessionId.value)
      .filter((id): id is string => id !== null)
  )
  const activeResumeIds = new Set(
    sessionStore.activeTabs
      .map((t: any) => t.resumeFromSessionId?.value ?? t.resumeFromSessionId ?? null)
      .filter((id): id is string => !!id)
  )

  const historyItems = historySessionList.value
    .filter(h => !activeTabIds.has(h.sessionId) && !activeSessionIds.has(h.sessionId) && !activeResumeIds.has(h.sessionId))
    .map(h => ({
      id: h.sessionId,
      name: h.customTitle || h.firstUserMessage || t('session.unnamed'),
      timestamp: h.timestamp,
      messageCount: h.messageCount,
      isGenerating: false,
      isConnected: false,
      isActive: false
    }))

  // 合并并按时间降序排序
  return [...activeTabs, ...historyItems].sort((a, b) => b.timestamp - a.timestamp)
})

const sessionTokenUsage = computed<EnhancedTokenUsage | null>(() => {
  const stats = sessionStore.currentTab?.stats.getCumulativeStats()
  if (!stats) return null
  return {
    inputTokens: stats.totalInputTokens,
    outputTokens: stats.totalOutputTokens,
    cacheCreationTokens: 0,
    cacheReadTokens: 0,
    totalTokens: stats.totalInputTokens + stats.totalOutputTokens
  }
})

// 当前 Tab 的输入框文本（双向绑定，实现多 Tab 输入框状态隔离）
const currentTabInputText = computed({
  get: () => sessionStore.currentTab?.uiState.inputText ?? '',
  set: (value: string) => {
    if (sessionStore.currentTab) {
      sessionStore.currentTab.uiState.inputText = value
    }
  }
})

// 当前 Tab 的 contexts（实现多 Tab contexts 状态隔离）
const currentTabContexts = computed({
  get: () => sessionStore.currentTab?.uiState.contexts ?? [],
  set: (value: any[]) => {
    if (sessionStore.currentTab) {
      sessionStore.currentTab.uiState.contexts = value
    }
  }
})

// 连接状态（用于显示）
const connectionStatusForDisplay = computed(() => {
  const status = sessionStore.currentTab?.connectionState.status
  if (status === 'CONNECTED') return 'CONNECTED'
  if (status === 'CONNECTING') return 'CONNECTING'
  return 'DISCONNECTED'
})

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

      // 并行执行：创建 Tab（RSocket 连接）+ 加载历史会话列表（HTTP）
      const [tab] = await Promise.all([
        sessionStore.createTab(),
        loadHistorySessions(true)  // 提前加载历史会话列表
      ])

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

// 发送选项接口
interface SendOptions {
  isSlashCommand?: boolean
}

// 事件处理器
async function handleSendMessage(contents?: ContentBlock[], options?: SendOptions) {
  const safeContents = Array.isArray(contents) ? contents : []
  console.log('handleSendMessage:', safeContents.length, 'content blocks', options?.isSlashCommand ? '(slash command)' : '')

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

    // 连接状态检查已移至 ChatInput.handleSend，此处不再重复检查

    // 如果是斜杠命令，不发送 contexts
    const currentContexts = options?.isSlashCommand ? [] : [...currentTabContexts.value]
    // 清空当前 Tab 的 contexts
    if (sessionStore.currentTab) {
      sessionStore.currentTab.uiState.contexts = []
    }

    console.log('Sending message via currentTab', options?.isSlashCommand ? '(no contexts for slash command)' : `(${currentContexts.length} contexts)`)
    sessionStore.currentTab.sendMessage({
      contexts: currentContexts,
      contents: safeContents
    }, { isSlashCommand: options?.isSlashCommand })
  } catch (error) {
    console.error('Failed to send message:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.sendMessageFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
}

async function handleForceSend(contents?: ContentBlock[], options?: SendOptions) {
  const safeContents = Array.isArray(contents) ? contents : []
  console.log('Force send:', safeContents.length, 'content blocks', options?.isSlashCommand ? '(slash command)' : '')

  // 如果是斜杠命令，不发送 contexts
  const currentContexts = options?.isSlashCommand ? [] : [...currentTabContexts.value]

  // 使用 forceSendMessage：打断 + 立即发送（跳过队列）
  await sessionStore.currentTab?.forceSendMessage({
    contexts: currentContexts,
    contents: safeContents
  }, { isSlashCommand: options?.isSlashCommand })

  // 发送后清空当前 Tab 的上下文
  if (sessionStore.currentTab) {
    sessionStore.currentTab.uiState.contexts = []
  }
}

function handleEditPendingMessage(id: string) {
  console.log('Edit pending message:', id)
  const msg = sessionStore.currentTab?.editQueueMessage(id)
  if (msg && sessionStore.currentTab) {
    sessionStore.currentTab.uiState.contexts = [...msg.contexts]
    chatInputRef.value?.setContent(msg.contents)
  }
}

function handleRemovePendingMessage(id: string) {
  console.log('Remove pending message:', id)
  sessionStore.currentTab?.removeFromQueue(id)
}

async function handleForceSendPendingMessage(id: string) {
  console.log('Force send pending message:', id)
  // editQueueMessage 会从队列中移除并返回消息
  const msg = sessionStore.currentTab?.editQueueMessage(id)
  if (msg) {
    // 使用 forceSendMessage：打断 + 立即发送（跳过队列检查）
    await sessionStore.currentTab?.forceSendMessage({
      contexts: msg.contexts,
      contents: msg.contents
    })
  }
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
  if (sessionStore.currentTab) {
    sessionStore.currentTab.uiState.contexts = [...currentTabContexts.value, context]
  }
}

function handleRemoveContext(context: ContextReference) {
  console.log('Removing context:', context)
  if (sessionStore.currentTab) {
    sessionStore.currentTab.uiState.contexts = currentTabContexts.value.filter(c => c.uri !== context.uri)
  }
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

/**
 * 加载历史会话列表（通过 WebSocket RPC）
 */
async function loadHistorySessions(reset = false) {
  if (historyLoading.value || historyLoadingMore.value) return
  if (reset) {
    historySessionList.value = []
    historyOffset.value = 0
    historyHasMore.value = true
  }
  if (!historyHasMore.value) return

  const isFirstPage = historyOffset.value === 0
  if (isFirstPage) historyLoading.value = true
  else historyLoadingMore.value = true

  try {
    const sessions = await aiAgentService.getHistorySessions(HISTORY_PAGE_SIZE, historyOffset.value)
    const merged = [...historySessionList.value, ...sessions]
    const dedup = Array.from(new Map(merged.map(s => [s.sessionId, s])).values())
    historySessionList.value = dedup.sort((a, b) => b.timestamp - a.timestamp)
    historyOffset.value += sessions.length
    historyHasMore.value = sessions.length === HISTORY_PAGE_SIZE
    console.log('📋 Loaded', sessions.length, 'history sessions (total', historySessionList.value.length, ')')
    // 调试日志：打印每个会话的 customTitle
    sessions.forEach((s, i) => {
      console.log(`📋 [${i}] sessionId=${s.sessionId.substring(0, 8)}... customTitle=${s.customTitle || '(无)'} firstMsg=${(s.firstUserMessage || '').substring(0, 30)}...`)
    })
  } catch (error) {
    console.error('❗Error loading history sessions:', error)
  } finally {
    historyLoading.value = false
    historyLoadingMore.value = false
  }
}

function toggleHistoryOverlay() {
  const willOpen = !isHistoryOverlayVisible.value
  isHistoryOverlayVisible.value = !isHistoryOverlayVisible.value
  if (willOpen && !historyLoading.value) {
    loadHistorySessions(true)
  }
}

async function handleLoadMoreHistory() {
  if (isHistoryLoading.value) return
  uiState.value.isLoadingHistory = true
  try {
    await sessionStore.loadMoreHistory()
  } finally {
    uiState.value.isLoadingHistory = false
  }
}

async function handleLoadMoreHistorySessions() {
  await loadHistorySessions(false)
}

async function handleHistorySelect(sessionId: string) {
  // 立刻关闭弹窗，提供即时反馈
  isHistoryOverlayVisible.value = false
  uiState.value.isLoadingHistory = true

  try {
    // 检查是否是活跃 Tab
    const activeTab = sessionStore.tabs.find(t => t.tabId === sessionId || t.sessionId.value === sessionId)
    if (activeTab) {
      // 切换到已有 Tab
      await sessionStore.switchTab(activeTab.tabId)
    } else {
      // 恢复历史会话
      const historySession = historySessionList.value.find(h => h.sessionId === sessionId)
      if (historySession) {
        console.log('🔄 Resuming history session:', sessionId)
        // 优先使用 customTitle（重命名后的标题），否则使用 firstUserMessage
        const sessionTitle = historySession.customTitle || historySession.firstUserMessage
        await sessionStore.resumeSession(
          sessionId,
          sessionTitle,
          historySession.projectPath,
          historySession.messageCount
        )
      }
    }

    const historyPromise = (sessionStore.currentTab as any)?.__historyPromise as Promise<void> | undefined
    if (historyPromise) {
      await historyPromise
    }
  } finally {
    uiState.value.isLoadingHistory = false
  }
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
  position: relative; /* 为悬浮层提供定位上下文 */
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

/* 悬浮层容器：权限请求 + 用户问题（底部与输入框底部对齐） */
.floating-overlay-container {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  z-index: 100;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 8px;
  pointer-events: none;
}

.floating-overlay-container > * {
  pointer-events: auto;
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

/* Toast 提示样式 */
.toast-container {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10000;
  pointer-events: none;
}

.toast-message {
  background: rgba(0, 0, 0, 0.8);
  color: #fff;
  padding: 12px 24px;
  border-radius: 8px;
  font-size: 14px;
  white-space: nowrap;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

/* Toast 动画 */
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.9);
}
</style>

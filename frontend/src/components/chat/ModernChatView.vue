<template>
  <div
    class="modern-chat-view"
    :class="{ 'theme-dark': isDark }"
  >
    <ChatHeader
      v-if="!isIdeMode"
      class="chat-header-bar"
      :is-dark="isDark"
      @toggle-history="toggleHistoryOverlay"
    />

    <!-- 聊天界面内容 -->
    <div class="chat-screen-content">
      <!-- 消息列表 -->
      <MessageList
        :display-items="displayItems"
        :is-loading="uiState.isLoadingHistory"
        :is-dark="isDark"
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

      <!-- 输入区域 -->
      <ChatInput
        ref="chatInputRef"
        :pending-tasks="pendingTasks"
        :contexts="uiState.contexts"
        :is-generating="uiState.isGenerating"
        :enabled="true"
        :actual-model-id="sessionStore.currentModelId || undefined"
        :selected-permission="uiState.selectedPermissionMode"
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
        @interrupt-and-send="handleInterruptAndSend"
        @stop="handleStopGeneration"
        @context-add="handleAddContext"
        @context-remove="handleRemoveContext"
        @update:selected-model="handleModelChange"
        @update:selected-permission="handlePermissionModeChange"
        @update:skip-permissions="handleSkipPermissionsChange"
        @auto-cleanup-change="handleAutoCleanupChange"
      />
    </div>

    <!-- 流式状态指示器已移至 MessageList 底部 -->

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
          <span class="error-icon">⚠️</span>
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
        🐛 {{ t('chat.debug.title') }} {{ debugExpanded ? '▼' : '▶' }}
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
        <!-- 使用 displayItems 估算消息数量（更贴近 UI 展示层） -->
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
      :current-session-id="sessionStore.currentSessionId"
      :loading="sessionStore.loading"
      :is-dark="isDark"
      @close="isHistoryOverlayVisible = false"
      @select-session="handleHistorySelect"
      @new-session="handleCreateNewSession"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import { useEnvironment } from '@/composables/useEnvironment'
import { setupIdeSessionBridge, onIdeHostCommand } from '@/bridges/ideSessionBridge'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import ChatHeader from './ChatHeader.vue'
import SessionListOverlay from './SessionListOverlay.vue'
import SessionStatsBar from './SessionStatsBar.vue'
import PendingMessageQueue from './PendingMessageQueue.vue'
import { calculateToolStats } from '@/utils/toolStatistics'
import type { ContentBlock } from '@/types/message'
import type { ContextReference, AiModel, PermissionMode, TokenUsage as EnhancedTokenUsage } from '@/types/enhancedMessage'
import type { PendingTask } from '@/types/pendingTask'
import { buildUserMessageContent } from '@/utils/userMessageBuilder'

// Props 定义
interface Props {
  sessionId?: string
  projectPath?: string
  isDark?: boolean
  showDebug?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  sessionId: undefined,
  projectPath: () => process.cwd?.() || '/default/project',
  isDark: false,
  showDebug: false
})

// 使用 sessionStore
const sessionStore = useSessionStore()
const { t } = useI18n()
const { isInIde, detectEnvironment } = useEnvironment()
const isIdeMode = isInIde
let disposeIdeBridge: (() => void) | null = null
let disposeHostCommand: (() => void) | null = null
const isHistoryOverlayVisible = ref(false)

// UI State 接口定义 (对应 ChatUiState)
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

// 状态定义 (messages 从 sessionStore 获取)
const uiState = ref<ChatUiState>({
  contexts: [],
  isGenerating: false,
  isLoadingHistory: false,
  hasError: false,
  errorMessage: undefined,
  actualModelId: undefined,
  selectedModel: 'DEFAULT' as AiModel,
  selectedPermissionMode: 'default' as PermissionMode,
  skipPermissions: true,  // 默认跳过权限
  autoCleanupContexts: false
})

// 从 sessionStore 获取 displayItems（用于新的 UI 组件）
const displayItems = computed(() => sessionStore.currentDisplayItems)

// 计算工具使用统计
const toolStats = computed(() => calculateToolStats(displayItems.value))

const historySessions = computed(() => {
  return sessionStore.allSessions.map(session => ({
    id: session.id,
    name: session.name,
    timestamp: session.lastActiveAt ?? session.updatedAt,
    messageCount: session.messages.length,
    isGenerating: session.isGenerating
  }))
})

// 计算会话级别的 Token 使用量（暂时由 ContextUsageIndicator 内部基于 messageHistory 计算，这里返回 null）
const sessionTokenUsage = computed<EnhancedTokenUsage | null>(() => {
  return null
})

// Streaming 状态相关的计算属性
const currentSessionIsStreaming = computed(() => {
  return sessionStore.currentSession?.isGenerating ?? false
})

const currentRequestTracker = computed(() => {
  const sessionId = sessionStore.currentSessionId
  if (!sessionId) return null
  return sessionStore.requestTracker.get(sessionId) ?? null
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
  console.log('🚀 ModernChatView mounted (Live Mode)')

  await detectEnvironment()
  if (isIdeMode.value) {
    disposeIdeBridge = setupIdeSessionBridge(sessionStore)
    disposeHostCommand = onIdeHostCommand((command) => {
      if (command.type === 'toggleHistory') {
        toggleHistoryOverlay()
      } else if (command.type === 'openHistory') {
        isHistoryOverlayVisible.value = true
      }
    })
  }

  try {
    // 会话数据由后端 SDK 管理，前端不需要加载
    // 如果有指定的 sessionId，切换到该会话
    if (props.sessionId) {
      console.log('📡 External session detected:', props.sessionId)
      const resolvedId = sessionStore.resolveSessionIdentifier(props.sessionId)
      if (resolvedId) {
        await sessionStore.switchSession(resolvedId)
      } else {
        const resumed = await sessionStore.resumeSession(props.sessionId)
        if (!resumed) {
          throw new Error('无法恢复指定会话')
        }
      }
      return
    }

    // 没有传入 sessionId 时，第一次进入需要自动创建一个连接好的会话
    const hasSessions = sessionStore.allSessions.length > 0
    if (!sessionStore.currentSessionId && !hasSessions) {
      console.log('🆕 No existing sessions detected, creating one by default...')
      const createFn = sessionStore.startNewSession ?? sessionStore.createSession
      const session = await createFn?.()
      if (!session) {
        throw new Error('自动创建会话失败')
      }
      console.log('✅ Default session created:', session.id)
    }
  } catch (error) {
    console.error('❌ Failed to initialize session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.initSessionFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
})

onBeforeUnmount(() => {
  console.log('🧹 ModernChatView unmounting')
  // 清理工作由 sessionStore 和 ClaudeCodeClient 内部处理
  disposeIdeBridge?.()
  disposeIdeBridge = null
  disposeHostCommand?.()
  disposeHostCommand = null
})

// 监听外部传入的 sessionId 变化
// 注意：onMounted 中的自动创建逻辑不会修改 props.sessionId，因此不会触发此 watcher，避免了冲突
watch(() => props.sessionId, async (newSessionId) => {
  if (!newSessionId) return
  console.log('🔄 Session ID changed:', newSessionId)
  try {
    const resolvedId = sessionStore.resolveSessionIdentifier(newSessionId)
    if (resolvedId) {
      await sessionStore.switchSession(resolvedId)
      return
    }
    const resumed = await sessionStore.resumeSession(newSessionId)
    if (!resumed) {
      throw new Error('无法恢复指定会话')
    }
  } catch (error) {
    console.error('❌ Failed to switch session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.switchSessionFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
})

// ============================================
// 事件处理器
// ============================================

/**
 * 处理发送消息
 * 逻辑：入队到 sessionStore，由 sessionStore 统一处理发送
 *
 * sessionStore.enqueueMessage 会：
 * 1. 将消息加入队列
 * 2. 自动调用 processMessageQueue 检查并发送
 */
async function handleSendMessage(contents: ContentBlock[]) {
  console.log('📤 handleSendMessage:', contents.length, 'content blocks')

  try {
    // ✅ 懒加载：检查是否有会话，没有则创建
    if (!sessionStore.currentSessionId) {
      console.log('🆕 没有活跃会话，创建新会话...')
      const newSession = await sessionStore.createSession()
      if (!newSession) {
        throw new Error('无法创建会话')
      }
    }

    if (!sessionStore.currentSessionId) {
      console.error('❌ No active session')
      uiState.value.hasError = true
      uiState.value.errorMessage = '当前没有激活的会话'
      return
    }

    const currentContexts = [...uiState.value.contexts]

    // 清空上下文
    uiState.value.contexts = []

    // 入队（sessionStore 会自动处理发送）
    console.log('📋 消息入队')
    sessionStore.enqueueMessage({
      contexts: currentContexts,
      contents
    })
  } catch (error) {
    console.error('❌ Failed to send message:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.sendMessageFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
  }
}

/**
 * 处理打断并发送
 */
async function handleInterruptAndSend(contents: ContentBlock[]) {
  console.log('⛔ Interrupt and send:', contents.length, 'content blocks')
  // 先停止当前生成
  await sessionStore.interrupt()
  // 然后发送新消息（入队后会自动发送，因为 isGenerating 已经变为 false）
  await handleSendMessage(contents)
}

/**
 * 处理编辑队列消息
 */
function handleEditPendingMessage(id: string) {
  console.log('✏️ Edit pending message:', id)
  const msg = sessionStore.editQueueMessage(id)
  if (msg) {
    // 恢复 contexts 到 uiState
    uiState.value.contexts = [...msg.contexts]
    // 调用 ChatInput 的 setContent 方法恢复 contents
    chatInputRef.value?.setContent(msg.contents)
  }
}

/**
 * 处理删除队列消息
 */
function handleRemovePendingMessage(id: string) {
  console.log('🗑️ Remove pending message:', id)
  sessionStore.removeFromQueue(id)
}

function handleStopGeneration() {
  console.log('🛑 Stopping generation')
  uiState.value.isGenerating = false
  // TODO: 调用后端 API 停止生成
}

function handleAddContext(context: ContextReference) {
  console.log('➕ Adding context:', context)
  uiState.value.contexts.push(context)
}

/**
 * 移除上下文引用
 * 使用 uri 作为唯一标识符，因为不是所有上下文都有 path 属性（如 ImageReference）
 */
function handleRemoveContext(context: ContextReference) {
  console.log('➖ Removing context:', context)
  const index = uiState.value.contexts.findIndex(c => c.uri === context.uri)
  if (index !== -1) {
    uiState.value.contexts.splice(index, 1)
  } else {
    console.warn('⚠️ Context not found for removal:', context)
  }
}

function handleModelChange(model: AiModel) {
  console.log('🤖 Changing model:', model)
  uiState.value.selectedModel = model
  // TODO: 通知后端切换模型
}

function handlePermissionModeChange(mode: PermissionMode) {
  console.log('🔐 Changing permission mode:', mode)
  uiState.value.selectedPermissionMode = mode
  sessionStore.setPermissionMode(mode)
}

function handleSkipPermissionsChange(skip: boolean) {
  console.log('⏭️ Toggle skip permissions:', skip)
  uiState.value.skipPermissions = skip
  sessionStore.setSkipPermissions(skip)
}

function handleAutoCleanupChange(cleanup: boolean) {
  console.log('🧹 Changing auto cleanup contexts:', cleanup)
  uiState.value.autoCleanupContexts = cleanup
}

function handleClearError() {
  console.log('✅ Clearing error')
  uiState.value.hasError = false
  uiState.value.errorMessage = undefined
}

function toggleHistoryOverlay() {
  isHistoryOverlayVisible.value = !isHistoryOverlayVisible.value
}

async function handleHistorySelect(sessionId: string) {
  await sessionStore.switchSession(sessionId)
  isHistoryOverlayVisible.value = false
}

async function handleCreateNewSession() {
  const session = await sessionStore.startNewSession?.()
  if (session?.id) {
    await sessionStore.switchSession(session.id)
  }
  isHistoryOverlayVisible.value = false
}

</script>

<style scoped>
.modern-chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 100%; /* 防止塌陷 */
  background: var(--ide-background, #fafbfc);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.chat-header-bar {
  flex-shrink: 0;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
}

.theme-dark .chat-header-bar {
  border-color: var(--ide-border, #30363d);
}

.modern-chat-view.theme-dark {
  background: var(--ide-background, #1e1e1e);
  color: var(--ide-foreground, #e0e0e0);
}

/* 聊天界面内容 (对应 ChatScreenContent) */
.chat-screen-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  width: 100%;
  flex: 1; /* 确保占据剩余空间 */
  min-height: 0; /* 允许内容滚动 */
  padding: 8px 12px; /* 左右边距 */
  box-sizing: border-box;
  gap: 8px; /* 消息列表和输入框之间的间距 */
}

/* 消息列表区域 (对应 Modifier.weight(1f)) */
.message-list-area {
  flex: 1;
  overflow: hidden;
  min-height: 0; /* 防止 flex 溢出 */
  display: flex; /* 确保虚拟列表有容器 */
  flex-direction: column;
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 8px;
  background: var(--ide-card-background, #ffffff);
}

/* 输入区域 (对应 Modifier.fillMaxWidth()) */
.input-area {
  flex-shrink: 0;
  width: 100%;
  padding: 0; /* 移除内边距，由 chat-screen-content 的 padding 控制 */
  box-sizing: border-box;
}

/* 错误对话框 (对应 ErrorDialog) */
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
  background: var(--ide-card-background, #ffffff);
  border: 1px solid var(--ide-error, #d73a49);
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

.theme-dark .error-content {
  background: var(--ide-card-background, #2b2b2b);
  border-color: var(--ide-error, #f85149);
}

.error-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
}

.error-icon {
  font-size: 24px;
}

.error-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--ide-error, #d73a49);
}

.theme-dark .error-title {
  color: var(--ide-error, #f85149);
}

.error-message {
  font-size: 14px;
  line-height: 1.6;
  color: var(--ide-foreground, #24292e);
  margin-bottom: 20px;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.theme-dark .error-message {
  color: var(--ide-foreground, #e0e0e0);
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
  background: var(--ide-accent, #0366d6);
  color: white;
  cursor: pointer;
  transition: all 0.2s;
}

.error-dismiss-btn:hover {
  background: var(--ide-accent-hover, #0256c2);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(3, 102, 214, 0.3);
}

.error-dismiss-btn:active {
  transform: translateY(0);
}

/* 调试面板 */
.debug-panel {
  position: fixed;
  bottom: 16px;
  right: 16px;
  background: var(--ide-card-background, #ffffff);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  max-width: 300px;
  z-index: 100;
}

.theme-dark .debug-panel {
  background: var(--ide-card-background, #252525);
  border-color: var(--ide-border, #3c3c3c);
}

.debug-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  background: var(--ide-panel-background, #f6f8fa);
  transition: background 0.2s;
}

.theme-dark .debug-header {
  background: var(--ide-panel-background, #2a2a2a);
}

.debug-header:hover {
  background: var(--ide-hover-background, #e1e4e8);
}

.theme-dark .debug-header:hover {
  background: var(--ide-hover-background, #323232);
}

.debug-content {
  padding: 12px 16px;
  font-size: 12px;
  border-top: 1px solid var(--ide-border, #e1e4e8);
}

.theme-dark .debug-content {
  border-top-color: var(--ide-border, #3c3c3c);
}

.debug-item {
  margin-bottom: 6px;
  color: var(--ide-secondary-foreground, #586069);
}

.theme-dark .debug-item {
  color: var(--ide-secondary-foreground, #8b949e);
}
</style>

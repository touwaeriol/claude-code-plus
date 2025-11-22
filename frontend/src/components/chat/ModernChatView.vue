<template>
  <div
    class="modern-chat-view"
    :class="{ 'theme-dark': isDark }"
  >
    <ChatHeader
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

      <!-- 输入区域 -->
      <ChatInput
        :pending-tasks="pendingTasks"
        :contexts="uiState.contexts"
        :is-generating="uiState.isGenerating"
        :enabled="true"
        :actual-model-id="sessionStore.currentModelId || undefined"
        :selected-permission="uiState.selectedPermissionMode"
        :skip-permissions="uiState.skipPermissions"
        :selected-model="uiState.selectedModel"
        :auto-cleanup-contexts="uiState.autoCleanupContexts"
        :message-history="messages"
        :session-token-usage="sessionTokenUsage"
        :show-context-controls="true"
        :show-model-selector="true"
        :show-permission-controls="true"
        :show-send-button="true"
        class="input-area"
        @send="handleSendMessage"
        @interrupt-and-send="handleInterruptAndSend"
        @stop="handleStopGeneration"
        @add-context="handleAddContext"
        @remove-context="handleRemoveContext"
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
        <div class="debug-item">
          {{ t('chat.debug.messageCount') }}: {{ messages.length }}
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
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import ChatHeader from './ChatHeader.vue'
import SessionListOverlay from './SessionListOverlay.vue'
import type { Message } from '@/types/message'
import type { ContextReference, AiModel, PermissionMode, EnhancedMessage, TokenUsage as EnhancedTokenUsage } from '@/types/enhancedMessage'
import { MessageRole } from '@/types/enhancedMessage'
import type { PendingTask } from '@/types/pendingTask'

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
  selectedPermissionMode: 'DEFAULT' as PermissionMode,
  skipPermissions: false,
  autoCleanupContexts: false
})

// 从 sessionStore 获取真实消息
const messages = computed<Message[]>(() => sessionStore.currentMessages)

// 从 sessionStore 获取 displayItems（用于新的 UI 组件）
const displayItems = computed(() => sessionStore.currentDisplayItems)

const historySessions = computed(() => {
  return sessionStore.allSessions.map(session => ({
    id: session.id,
    name: session.name,
    timestamp: session.lastActiveAt ?? session.updatedAt,
    messageCount: session.messages.length,
    isGenerating: session.isGenerating
  }))
})

// 计算会话级别的 Token 使用量（从最新的 assistant 消息中提取）
const sessionTokenUsage = computed((): EnhancedTokenUsage | null => {
  const enhancedMessages = messages.value as EnhancedMessage[]
  for (let i = enhancedMessages.length - 1; i >= 0; i--) {
    const msg = enhancedMessages[i]
    if (msg.role === MessageRole.ASSISTANT && msg.tokenUsage) {
      return msg.tokenUsage
    }
  }
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

// 生命周期钩子
onMounted(async () => {
  console.log('🚀 ModernChatView mounted (Live Mode)')

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

async function handleSendMessage(text: string) {
  console.log('📤 Sending message:', text)

  try {
    // ✅ 懒加载：检查是否有会话，没有则创建
    if (!sessionStore.currentSessionId) {
      console.log('🆕 没有活跃会话，创建新会话...')
      const newSession = await sessionStore.createSession()
      if (!newSession) {
        throw new Error('无法创建会话')
      }
    }

    const sessionId = sessionStore.currentSessionId
    if (!sessionId) {
      console.error('❌ No active session')
      uiState.value.hasError = true
      uiState.value.errorMessage = '当前没有激活的会话'
      return
    }

    // 1. 收集上下文中的图片
    const imageContexts = uiState.value.contexts.filter(
      (c: any) => c.type === 'image' && c.base64Data
    )
    console.log(`📸 收集到 ${imageContexts.length} 张图片`)

    // 2. 立即添加用户消息到 UI
    const userMessageId = `user-${Date.now()}`
    const userMessage: Message = {
      id: userMessageId,
      role: 'user',
      content: [{ type: 'text', text }],
      timestamp: Date.now()
    }
    sessionStore.addMessage(sessionId, userMessage)
    console.log('👤 用户消息已添加到UI')

    // 3. 添加助手占位符消息到消息列表（显示为气泡）
    const placeholderMessageId = `assistant-placeholder-${Date.now()}`
    const placeholderMessage: Message = {
      id: placeholderMessageId,
      role: 'assistant',
      content: [],  // 真正的空内容，processMessageStart 会检查 content.length === 0
      timestamp: Date.now(),
      isStreaming: true  // 标记为流式消息，用于显示加载动画
    }
    sessionStore.addMessage(sessionId, placeholderMessage)
    console.log('🤖 助手占位符消息已添加到UI')

    // 开始追踪请求统计（传入占位符消息 ID）
    sessionStore.startRequestTracking(sessionId, userMessageId, placeholderMessageId)

    // 4. 发送消息到后端（包含图片时使用 sendMessageWithContent）
    if (imageContexts.length > 0) {
      // 构建 stream-json content 数组
      const content: any[] = [{ type: 'text', text }]
      imageContexts.forEach((img: any) => {
        content.push({
          type: 'image',
          data: img.base64Data,
          mimeType: img.mimeType || 'image/png'
        })
      })
      console.log('📤 发送带图片的消息')
      sessionStore.sendMessageWithContent(content)
    } else {
      sessionStore.sendMessage(text)
    }

    // 4. 自动清理上下文（如果启用）
    if (uiState.value.autoCleanupContexts) {
      console.log('🧹 Auto-cleaning contexts after send')
      uiState.value.contexts = []
    }
  } catch (error) {
    console.error('❌ Failed to send message:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = t('chat.error.sendMessageFailed', {
      message: error instanceof Error ? error.message : t('chat.error.unknown')
    })
    // 移除占位符消息
    const sessionId = sessionStore.currentSessionId
    if (sessionId) {
      const messages = sessionStore.getMessages(sessionId)
      const placeholderIndex = messages.findIndex(m => m.id && m.id.startsWith('assistant-placeholder-'))
      if (placeholderIndex !== -1) {
        sessionStore.removeMessage(sessionId, placeholderIndex)
      }
    }
  }
}

function handleInterruptAndSend(text: string) {
  console.log('⛔ Interrupt and send:', text)
  // TODO: 实现打断并发送新消息的逻辑
  // 先停止当前生成,然后发送新消息
  handleStopGeneration()
  handleSendMessage(text)
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
  // TODO: 通知后端切换权限模式
}

function handleSkipPermissionsChange(skip: boolean) {
  console.log('⏭️ Toggle skip permissions:', skip)
  uiState.value.skipPermissions = skip
  // TODO: 通知后端切换跳过权限设置
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
}

/* 消息列表区域 (对应 Modifier.weight(1f)) */
.message-list-area {
  flex: 1;
  overflow: hidden;
  min-height: 0; /* 防止 flex 溢出 */
  display: flex; /* 确保虚拟列表有容器 */
  flex-direction: column;
}

/* 输入区域 (对应 Modifier.fillMaxWidth()) */
.input-area {
  flex-shrink: 0;
  width: 100%;
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

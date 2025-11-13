<template>
  <div
    class="modern-chat-view"
    :class="{ 'theme-dark': isDark }"
  >
    <!-- 聊天界面内容 -->
    <div class="chat-screen-content">
      <!-- 消息列表 -->
      <MessageList
        :messages="messages"
        :is-loading="uiState.isLoadingHistory"
        :is-dark="isDark"
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
          <span class="error-icon">⚠️</span>
          <span class="error-title">错误</span>
        </div>
        <div class="error-message">
          {{ uiState.errorMessage || '未知错误' }}
        </div>
        <div class="error-actions">
          <button
            class="error-dismiss-btn"
            @click="handleClearError"
          >
            确定
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
        🐛 调试信息 {{ debugExpanded ? '▼' : '▶' }}
      </div>
      <div
        v-show="debugExpanded"
        class="debug-content"
      >
        <div class="debug-item">
          会话ID: {{ sessionId || '未设置' }}
        </div>
        <div class="debug-item">
          项目路径: {{ projectPath }}
        </div>
        <div class="debug-item">
          消息数: {{ messages.length }}
        </div>
        <div class="debug-item">
          生成中: {{ uiState.isGenerating ? '是' : '否' }}
        </div>
        <div class="debug-item">
          待处理任务: {{ pendingTasks.length }}
        </div>
        <div class="debug-item">
          上下文: {{ uiState.contexts.length }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { claudeService } from '@/services/claudeService'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import type { Message } from '@/types/message'
import type { ContextReference, AiModel, PermissionMode } from '@/types/enhancedMessage'
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
  skipPermissions: false
})

// 从 sessionStore 获取真实消息
const messages = computed<Message[]>(() => sessionStore.currentMessages)

const pendingTasks = ref<PendingTask[]>([])
const debugExpanded = ref(false)

// 生命周期钩子
onMounted(async () => {
  console.log('🚀 ModernChatView mounted (Live Mode)')

  try {
    await sessionStore.loadSessions()

    if (props.sessionId) {
      console.log('📡 Switching to session:', props.sessionId)
      await sessionStore.switchSession(props.sessionId)
    } else if (!sessionStore.currentSessionId && sessionStore.sessions.length === 0) {
      const newSession = await sessionStore.createSession()
      if (!newSession) {
        throw new Error('无法创建会话')
      }
    }
  } catch (error) {
    console.error('❌ Failed to initialize session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = `初始化会话失败: ${error instanceof Error ? error.message : '未知错误'}`
  }
})

onBeforeUnmount(() => {
  console.log('🧹 ModernChatView unmounting')
  // 清理工作由 sessionStore 和 claudeService 内部处理
})

// 监听外部传入的 sessionId 变化
watch(() => props.sessionId, async (newSessionId) => {
  if (!newSessionId) return
  console.log('🔄 Session ID changed:', newSessionId)
  try {
    await sessionStore.switchSession(newSessionId)
  } catch (error) {
    console.error('❌ Failed to switch session:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = `切换会话失败: ${error instanceof Error ? error.message : '未知错误'}`
  }
})

// ============================================
// 事件处理器
// ============================================

function handleSendMessage(text: string) {
  console.log('📤 Sending message:', text)

  try {
    const sessionId = sessionStore.currentSessionId
    if (!sessionId) {
      console.error('❌ No active session')
      uiState.value.hasError = true
      uiState.value.errorMessage = '当前没有激活的会话'
      return
    }

    uiState.value.isGenerating = true
    claudeService.sendMessage(sessionId, text)
  } catch (error) {
    console.error('❌ Failed to send message:', error)
    uiState.value.hasError = true
    uiState.value.errorMessage = `发送消息失败: ${error instanceof Error ? error.message : '未知错误'}`
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

function handleRemoveContext(context: ContextReference) {
  console.log('➖ Removing context:', context)
  const index = uiState.value.contexts.findIndex(c =>
    c.type === context.type && c.path === context.path
  )
  if (index !== -1) {
    uiState.value.contexts.splice(index, 1)
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

function handleClearError() {
  console.log('✅ Clearing error')
  uiState.value.hasError = false
  uiState.value.errorMessage = undefined
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

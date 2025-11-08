<template>
  <div
    class="modern-chat-view"
    :class="{ 'theme-dark': isDark }"
  >
    <!-- 聊天界面内容 -->
    <div class="chat-screen-content">
      <!-- 消息列表 -->
      <MessageList
        :messages="uiState.messages"
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
        :actual-model-id="uiState.actualModelId"
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
          消息数: {{ uiState.messages.length }}
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
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import MessageList from './MessageList.vue'
import ChatInput from './ChatInput.vue'
import type { EnhancedMessage, ContextReference, AiModel, PermissionMode } from '@/types/enhancedMessage'
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

// UI State 接口定义 (对应 ChatUiState)
interface ChatUiState {
  messages: EnhancedMessage[]
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

// 状态定义
const uiState = ref<ChatUiState>({
  messages: [],
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

const pendingTasks = ref<PendingTask[]>([])
const debugExpanded = ref(false)

// ViewModel 引用 (模拟 ChatViewModel)
let viewModel: any = null

// 生命周期钩子
onMounted(async () => {
  console.log('🚀 ModernChatView mounted')

  // 确保应用程序已初始化 (对应 ApplicationInitializer.initialize())
  await initializeApplication()

  // 创建 ViewModel (对应 remember { ChatViewModel() })
  viewModel = await createChatViewModel()

  // 收集 UI 状态 (对应 viewModel.uiState.collectAsState())
  subscribeToUiState()

  // 收集待处理任务 (对应 viewModel.taskState.collectAsState())
  subscribeToTaskState()

  // 处理副作用 (对应 viewModel.effects.collect)
  subscribeToEffects()

  // 初始化会话 (对应 LaunchedEffect(sessionId, projectPath))
  await initializeSession()
})

onBeforeUnmount(() => {
  // 清理 ViewModel (对应 DisposableEffect onDispose)
  if (viewModel && typeof viewModel.onCleared === 'function') {
    viewModel.onCleared()
  }
})

// 监听 sessionId 和 projectPath 变化
watch([() => props.sessionId, () => props.projectPath], async () => {
  await initializeSession()
})

// ============================================
// 初始化函数
// ============================================

async function initializeApplication() {
  // 对应 ApplicationInitializer.initialize()
  // 这里可以初始化全局服务、主题等
  console.log('📦 Initializing application...')
}

async function createChatViewModel() {
  // 对应 remember { ChatViewModel() }
  // 这里应该创建实际的 ViewModel 或使用 Pinia store
  console.log('🎨 Creating ChatViewModel...')

  // 暂时返回一个 mock ViewModel
  return {
    handleEvent: (event: ChatUiEvent) => {
      console.log('📨 Handling event:', event)
      handleChatEvent(event)
    },
    onCleared: () => {
      console.log('🧹 Cleaning up ViewModel')
    }
  }
}

function subscribeToUiState() {
  // 对应 val uiState by viewModel.uiState.collectAsState()
  // 这里应该订阅实际的 ViewModel 状态变化
  console.log('👂 Subscribing to UI state')
}

function subscribeToTaskState() {
  // 对应 val pendingTasks by viewModel.taskState.collectAsState()
  console.log('👂 Subscribing to task state')
}

function subscribeToEffects() {
  // 对应 LaunchedEffect(Unit) { viewModel.effects.collect { effect -> handleEffect(effect) } }
  console.log('👂 Subscribing to effects')
}

async function initializeSession() {
  // 对应 viewModel.handleEvent(ChatUiEvent.InitializeSession(sessionId, projectPath))
  console.log('🔌 Initializing session:', props.sessionId, props.projectPath)

  if (viewModel) {
    viewModel.handleEvent({
      type: 'InitializeSession',
      sessionId: props.sessionId,
      projectPath: props.projectPath
    })
  }
}

// ============================================
// ChatUiEvent 类型定义和处理
// ============================================

interface ChatUiEvent {
  type: string
  [key: string]: any
}

function handleChatEvent(event: ChatUiEvent) {
  switch (event.type) {
    case 'InitializeSession':
      // 初始化会话逻辑
      break
    case 'SendMessage':
      // 发送消息逻辑
      break
    case 'InterruptAndSend':
      // 打断并发送逻辑
      break
    case 'StopGeneration':
      // 停止生成逻辑
      break
    case 'AddContext':
      // 添加上下文逻辑
      break
    case 'RemoveContext':
      // 移除上下文逻辑
      break
    case 'ChangeModel':
      // 切换模型逻辑
      break
    case 'ChangePermissionMode':
      // 切换权限模式逻辑
      break
    case 'ToggleSkipPermissions':
      // 切换跳过权限逻辑
      break
    case 'ClearError':
      // 清除错误逻辑
      uiState.value.hasError = false
      uiState.value.errorMessage = undefined
      break
    default:
      console.warn('Unknown event type:', event.type)
  }
}

// ============================================
// 事件处理器 (对应 onEvent 回调)
// ============================================

function handleSendMessage(text: string) {
  // 对应 onEvent(ChatUiEvent.SendMessage(text))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'SendMessage',
      text
    })
  }
}

function handleInterruptAndSend(text: string) {
  // 对应 onEvent(ChatUiEvent.InterruptAndSend(text))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'InterruptAndSend',
      text
    })
  }
}

function handleStopGeneration() {
  // 对应 onEvent(ChatUiEvent.StopGeneration)
  if (viewModel) {
    viewModel.handleEvent({
      type: 'StopGeneration'
    })
  }
}

function handleAddContext(context: ContextReference) {
  // 对应 onEvent(ChatUiEvent.AddContext(context))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'AddContext',
      context
    })
  }
}

function handleRemoveContext(context: ContextReference) {
  // 对应 onEvent(ChatUiEvent.RemoveContext(context))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'RemoveContext',
      context
    })
  }
}

function handleModelChange(model: AiModel) {
  // 对应 onEvent(ChatUiEvent.ChangeModel(model))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'ChangeModel',
      model
    })
  }
}

function handlePermissionModeChange(mode: PermissionMode) {
  // 对应 onEvent(ChatUiEvent.ChangePermissionMode(mode))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'ChangePermissionMode',
      mode
    })
  }
}

function handleSkipPermissionsChange(skip: boolean) {
  // 对应 onEvent(ChatUiEvent.ToggleSkipPermissions(skip))
  if (viewModel) {
    viewModel.handleEvent({
      type: 'ToggleSkipPermissions',
      skip
    })
  }
}

function handleClearError() {
  // 对应 onEvent(ChatUiEvent.ClearError)
  if (viewModel) {
    viewModel.handleEvent({
      type: 'ClearError'
    })
  }
}

// ============================================
// 副作用处理 (对应 handleEffect)
// ============================================

interface ChatUiEffect {
  type: string
  [key: string]: any
}

function _handleEffect(effect: ChatUiEffect) {
  switch (effect.type) {
    case 'ScrollToBottom':
      // 滚动到底部的逻辑已在MessageList中处理
      break
    case 'FocusInput':
      // 输入框焦点的逻辑在ChatInput中处理
      break
    case 'ShowSnackbar':
      console.log('提示:', effect.message)
      break
    case 'NavigateToSession':
      console.log('导航到会话:', effect.sessionId)
      break
    default:
      console.warn('Unknown effect type:', effect.type)
  }
}
</script>

<style scoped>
.modern-chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
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
}

/* 消息列表区域 (对应 Modifier.weight(1f)) */
.message-list-area {
  flex: 1;
  overflow: hidden;
  min-height: 0; /* 防止 flex 溢出 */
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

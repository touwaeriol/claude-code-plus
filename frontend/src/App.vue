<template>
  <div class="app" :class="{ 'theme-dark': isDark }">
    <div class="header">
      <h1>Claude Code Plus</h1>
      <div class="status">
        <span v-if="!connected" class="status-disconnected">⚪ 未连接</span>
        <span v-else class="status-connected">🟢 已连接</span>
      </div>
    </div>

    <MessageList
      :messages="messages"
      :is-loading="isLoading"
      :is-dark="isDark"
    />

    <div class="input-area">
      <textarea
        v-model="inputMessage"
        placeholder="输入消息... (Ctrl+Enter 发送)"
        @keydown="handleKeyDown"
        :disabled="!connected || isLoading"
        class="input-textarea"
      />
      <div class="input-actions">
        <button
          @click="connect"
          v-if="!connected"
          class="btn btn-primary"
        >
          连接 Claude
        </button>
        <button
          @click="sendMessage"
          v-if="connected"
          :disabled="!canSend"
          class="btn btn-primary"
        >
          {{ isLoading ? '发送中...' : '发送' }}
        </button>
        <button
          @click="interrupt"
          v-if="connected && isLoading"
          class="btn btn-danger"
        >
          中断
        </button>
      </div>
    </div>

    <!--调试面板-->
    <div v-if="showDebug" class="debug-panel">
      <div class="debug-header" @click="debugExpanded = !debugExpanded">
        <span>🐛 调试信息</span>
        <span>{{ debugExpanded ? '▼' : '▶' }}</span>
      </div>
      <div v-if="debugExpanded" class="debug-content">
        <div class="debug-item">
          <strong>桥接状态:</strong> {{ bridgeReady ? '✅ 就绪' : '⏳ 加载中' }}
        </div>
        <div class="debug-item">
          <strong>连接状态:</strong> {{ connected ? '✅ 已连接' : '⚪ 未连接' }}
        </div>
        <div class="debug-item">
          <strong>消息数量:</strong> {{ messages.length }}
        </div>
        <div class="debug-item">
          <strong>主题模式:</strong> {{ isDark ? '🌙 暗色' : '☀️ 亮色' }}
        </div>
        <button @click="getTheme" class="btn btn-small">获取主题</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ideaBridge, claudeService, ideService } from '@/services/ideaBridge'
import type { Message } from '@/types/message'
import MessageList from '@/components/chat/MessageList.vue'

const messages = ref<Message[]>([])
const inputMessage = ref('')
const isLoading = ref(false)
const connected = ref(false)
const bridgeReady = ref(false)
const isDark = ref(false)
const showDebug = ref(true)
const debugExpanded = ref(false)

const canSend = computed(() => {
  return connected.value && !isLoading.value && inputMessage.value.trim().length > 0
})

onMounted(async () => {
  console.log('🚀 App mounted')

  try {
    await ideaBridge.waitForReady()
    bridgeReady.value = true
    console.log('✅ Bridge ready')

    const themeResponse = await ideService.getTheme()
    if (themeResponse.success && themeResponse.data) {
      isDark.value = themeResponse.data.theme.isDark
      console.log('🎨 Theme loaded:', isDark.value ? 'dark' : 'light')
    }

    setupClaudeListeners()
  } catch (error) {
    console.error('❌ Failed to initialize:', error)
  }
})

function setupClaudeListeners() {
  claudeService.onConnected((data) => {
    console.log('✅ Claude connected:', data)
    connected.value = true
  })

  claudeService.onDisconnected(() => {
    console.log('🔌 Claude disconnected')
    connected.value = false
  })

  claudeService.onMessage((data) => {
    console.log('📨 Received message:', data)
    const msg = data.message

    const message: Message = {
      id: `${Date.now()}-${Math.random()}`,
      role: msg.type,
      content: msg.content || [],
      timestamp: Date.now()
    }

    messages.value.push(message)
    isLoading.value = false
  })

  claudeService.onError((error) => {
    console.error('❌ Claude error:', error)
    isLoading.value = false

    messages.value.push({
      id: `error-${Date.now()}`,
      role: 'system',
      content: [{
        type: 'text',
        text: `❌ 错误: ${error}`
      }],
      timestamp: Date.now()
    })
  })
}

async function connect() {
  try {
    console.log('🔌 Connecting to Claude...')
    const response = await claudeService.connect()

    if (!response.success) {
      console.error('❌ Connection failed:', response.error)
      alert(`连接失败: ${response.error}`)
    }
  } catch (error) {
    console.error('❌ Connection error:', error)
    alert(`连接错误: ${error}`)
  }
}

async function sendMessage() {
  if (!canSend.value) return

  const message = inputMessage.value.trim()
  inputMessage.value = ''
  isLoading.value = true

  messages.value.push({
    id: `user-${Date.now()}`,
    role: 'user',
    content: [{
      type: 'text',
      text: message
    }],
    timestamp: Date.now()
  })

  try {
    console.log('📤 Sending message:', message)
    const response = await claudeService.query(message)

    if (!response.success) {
      console.error('❌ Failed to send message:', response.error)
      isLoading.value = false

      messages.value.push({
        id: `error-${Date.now()}`,
        role: 'system',
        content: [{
          type: 'text',
          text: `❌ 发送失败: ${response.error}`
        }],
        timestamp: Date.now()
      })
    }
  } catch (error) {
    console.error('❌ Send error:', error)
    isLoading.value = false
  }
}

async function interrupt() {
  try {
    console.log('⏸️ Interrupting...')
    await claudeService.interrupt()
    isLoading.value = false
  } catch (error) {
    console.error('❌ Interrupt error:', error)
  }
}

async function getTheme() {
  try {
    const response = await ideService.getTheme()
    if (response.success) {
      console.log('🎨 Theme:', response.data.theme)
      isDark.value = response.data.theme.isDark
    }
  } catch (error) {
    console.error('❌ Failed to get theme:', error)
  }
}

function handleKeyDown(event: KeyboardEvent) {
  if (event.ctrlKey && event.key === 'Enter') {
    event.preventDefault()
    sendMessage()
  }
}
</script>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #ffffff;
  color: #24292e;
}

.app.theme-dark {
  background: #1e1e1e;
  color: #e1e4e8;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #f6f8fa;
  border-bottom: 1px solid #e1e4e8;
}

.theme-dark .header {
  background: #24292e;
  border-bottom-color: #444d56;
}

.header h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}

.status {
  font-size: 13px;
}

.status-connected {
  color: #22863a;
}

.status-disconnected {
  color: #6a737d;
}

.input-area {
  padding: 16px;
  background: #f6f8fa;
  border-top: 1px solid #e1e4e8;
}

.theme-dark .input-area {
  background: #24292e;
  border-top-color: #444d56;
}

.input-textarea {
  width: 100%;
  min-height: 80px;
  max-height: 200px;
  padding: 12px;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  background: #ffffff;
  color: #24292e;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  font-size: 14px;
  resize: vertical;
  outline: none;
}

.theme-dark .input-textarea {
  background: #1e1e1e;
  color: #e1e4e8;
  border-color: #444d56;
}

.input-textarea:focus {
  border-color: #0366d6;
}

.input-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.input-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-primary {
  background: #0366d6;
  color: white;
}

.btn-primary:hover:not(:disabled) {
  background: #0256c0;
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-danger {
  background: #d73a49;
  color: white;
}

.btn-danger:hover {
  background: #cb2431;
}

.btn-small {
  padding: 4px 8px;
  font-size: 12px;
}

.debug-panel {
  border-top: 1px solid #e1e4e8;
  background: #f6f8fa;
}

.theme-dark .debug-panel {
  background: #24292e;
  border-top-color: #444d56;
}

.debug-header {
  display: flex;
  justify-content: space-between;
  padding: 8px 16px;
  cursor: pointer;
  user-select: none;
  font-size: 13px;
  font-weight: 600;
}

.debug-header:hover {
  background: rgba(0, 0, 0, 0.05);
}

.debug-content {
  padding: 12px 16px;
  border-top: 1px solid #e1e4e8;
}

.theme-dark .debug-content {
  border-top-color: #444d56;
}

.debug-item {
  margin-bottom: 8px;
  font-size: 13px;
}

.debug-item strong {
  color: #0366d6;
}

.theme-dark .debug-item strong {
  color: #58a6ff;
}
</style>

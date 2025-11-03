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

    <div v-if="!connected" class="connect-area">
      <button @click="connect" class="btn btn-primary btn-large">
        <span class="btn-icon">🔌</span>
        <span>连接 Claude</span>
      </button>
    </div>

    <InputArea
      v-else
      v-model="inputMessage"
      :disabled="isLoading"
      :is-dark="isDark"
      :references="contextReferences"
      :send-button-text="isLoading ? '发送中...' : '发送'"
      @send="handleSendMessage"
      @update:references="contextReferences = $event"
    />

    <div v-if="connected && isLoading" class="interrupt-area">
      <button @click="interrupt" class="btn btn-danger">
        <span class="btn-icon">⏸️</span>
        <span>中断执行</span>
      </button>
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
import type { ContextReference } from '@/components/input/InputArea.vue'
import MessageList from '@/components/chat/MessageList.vue'
import InputArea from '@/components/input/InputArea.vue'

const messages = ref<Message[]>([])
const inputMessage = ref('')
const contextReferences = ref<ContextReference[]>([])
const isLoading = ref(false)
const connected = ref(false)
const bridgeReady = ref(false)
const isDark = ref(false)
const showDebug = ref(true)
const debugExpanded = ref(false)

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

async function handleSendMessage(message: string, references: ContextReference[]) {
  isLoading.value = true

  // 构建用户消息
  const userMessage: Message = {
    id: `user-${Date.now()}`,
    role: 'user',
    content: [{
      type: 'text',
      text: message
    }],
    timestamp: Date.now()
  }

  // 如果有引用，添加到消息内容中
  if (references.length > 0) {
    const refContext = references.map(ref => {
      if (ref.content) {
        return `\n\n@${ref.name}:\n\`\`\`\n${ref.content}\n\`\`\``
      } else {
        return `\n@${ref.name}: ${ref.path}`
      }
    }).join('\n')

    userMessage.content[0].text = message + refContext
  }

  messages.value.push(userMessage)

  try {
    console.log('📤 Sending message with references:', { message, references })
    const response = await claudeService.query(userMessage.content[0].text)

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

.connect-area {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: #f6f8fa;
  border-top: 1px solid #e1e4e8;
}

.theme-dark .connect-area {
  background: #24292e;
  border-top-color: #444d56;
}

.interrupt-area {
  display: flex;
  justify-content: center;
  padding: 8px 16px;
  background: #fff8dc;
  border-top: 1px solid #ffc107;
}

.theme-dark .interrupt-area {
  background: #3d3518;
  border-top-color: #856404;
}

.btn {
  display: inline-flex;
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

.btn-icon {
  font-size: 16px;
}

.btn-large {
  padding: 12px 24px;
  font-size: 16px;
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

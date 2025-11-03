<template>
  <div class="app">
    <h1>Claude Code Plus - Vue Frontend POC</h1>

    <div class="section">
      <h2>1. 通信桥接测试</h2>
      <button @click="testBridge" :disabled="!bridgeReady">
        测试桥接
      </button>
      <div v-if="bridgeStatus" class="status" :class="{ success: bridgeSuccess, error: !bridgeSuccess }">
        {{ bridgeStatus }}
      </div>
    </div>

    <div class="section">
      <h2>2. 获取 IDE 主题</h2>
      <button @click="getTheme">获取主题</button>
      <pre v-if="theme" class="code">{{ JSON.stringify(theme, null, 2) }}</pre>
    </div>

    <div class="section">
      <h2>3. Claude 消息测试</h2>
      <div class="input-group">
        <input
          v-model="message"
          placeholder="输入消息..."
          @keydown.enter="sendMessage"
        />
        <button @click="sendMessage" :disabled="sending">
          {{ sending ? '发送中...' : '发送' }}
        </button>
      </div>
      <div class="messages">
        <div v-for="(msg, idx) in messages" :key="idx" class="message">
          <div class="message-header">{{ msg.role }}</div>
          <div class="message-content">{{ msg.content }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ideaBridge, ideService, claudeService } from '@/services/ideaBridge'
import type { IdeTheme } from '@/types/bridge'

const bridgeReady = ref(false)
const bridgeStatus = ref('')
const bridgeSuccess = ref(false)
const theme = ref<IdeTheme | null>(null)
const message = ref('Hello Claude!')
const messages = ref<Array<{ role: string; content: string }>>([])
const sending = ref(false)

onMounted(async () => {
  console.log('🚀 Vue App mounted')

  try {
    await ideaBridge.waitForReady()
    bridgeReady.value = true
    bridgeStatus.value = '✅ 桥接已就绪'
    bridgeSuccess.value = true
    console.log('✅ Bridge is ready')

    // 监听 Claude 消息
    claudeService.onMessage((data) => {
      console.log('📨 Received Claude message:', data)
      messages.value.push({
        role: 'assistant',
        content: JSON.stringify(data.message, null, 2)
      })
    })

    claudeService.onError((error) => {
      console.error('❌ Claude error:', error)
      messages.value.push({
        role: 'error',
        content: error
      })
    })

  } catch (error) {
    bridgeStatus.value = `❌ 桥接失败: ${error}`
    bridgeSuccess.value = false
    console.error('❌ Bridge initialization failed:', error)
  }
})

async function testBridge() {
  bridgeStatus.value = '⏳ 测试中...'
  try {
    const response = await ideaBridge.query('test.ping', { timestamp: Date.now() })
    if (response.success) {
      bridgeStatus.value = `✅ 桥接正常: ${JSON.stringify(response.data)}`
      bridgeSuccess.value = true
    } else {
      bridgeStatus.value = `❌ 桥接失败: ${response.error}`
      bridgeSuccess.value = false
    }
  } catch (error) {
    bridgeStatus.value = `❌ 异常: ${error}`
    bridgeSuccess.value = false
  }
}

async function getTheme() {
  try {
    const response = await ideService.getTheme()
    if (response.success) {
      theme.value = response.data.theme
      console.log('Theme:', theme.value)
    } else {
      console.error('Failed to get theme:', response.error)
    }
  } catch (error) {
    console.error('Error getting theme:', error)
  }
}

async function sendMessage() {
  if (!message.value.trim() || sending.value) return

  sending.value = true
  const userMessage = message.value

  try {
    // 添加用户消息
    messages.value.push({
      role: 'user',
      content: userMessage
    })

    // 发送到 Claude
    const response = await claudeService.query(userMessage)
    if (!response.success) {
      messages.value.push({
        role: 'error',
        content: response.error || 'Failed to send message'
      })
    }

    message.value = ''
  } catch (error) {
    console.error('Error sending message:', error)
    messages.value.push({
      role: 'error',
      content: String(error)
    })
  } finally {
    sending.value = false
  }
}
</script>

<style scoped>
.app {
  padding: 20px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  max-width: 800px;
  margin: 0 auto;
}

h1 {
  color: #2c3e50;
  border-bottom: 2px solid #42b983;
  padding-bottom: 10px;
}

.section {
  margin: 30px 0;
  padding: 20px;
  border: 1px solid #ddd;
  border-radius: 8px;
  background: #f9f9f9;
}

h2 {
  margin-top: 0;
  color: #42b983;
  font-size: 18px;
}

button {
  padding: 8px 16px;
  background: #42b983;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

button:hover:not(:disabled) {
  background: #35a372;
}

button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.status {
  margin-top: 10px;
  padding: 10px;
  border-radius: 4px;
  font-family: monospace;
}

.status.success {
  background: #d4edda;
  color: #155724;
  border: 1px solid #c3e6cb;
}

.status.error {
  background: #f8d7da;
  color: #721c24;
  border: 1px solid #f5c6cb;
}

.code {
  background: #282c34;
  color: #abb2bf;
  padding: 15px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
  margin-top: 10px;
}

.input-group {
  display: flex;
  gap: 10px;
  margin-bottom: 15px;
}

input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
}

.messages {
  max-height: 400px;
  overflow-y: auto;
  border: 1px solid #ddd;
  border-radius: 4px;
  background: white;
}

.message {
  padding: 12px;
  border-bottom: 1px solid #eee;
}

.message:last-child {
  border-bottom: none;
}

.message-header {
  font-weight: bold;
  color: #42b983;
  margin-bottom: 5px;
  text-transform: uppercase;
  font-size: 12px;
}

.message-content {
  font-family: monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>

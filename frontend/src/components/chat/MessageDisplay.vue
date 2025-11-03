<template>
  <div class="message" :class="`message-${message.role}`">
    <div class="message-header">
      <span class="role-icon">{{ roleIcon }}</span>
      <span class="role-name">{{ roleName }}</span>
      <span class="timestamp">{{ formattedTime }}</span>
    </div>

    <div class="message-content">
      <!-- 文本内容 -->
      <MarkdownRenderer
        v-if="textContent"
        :content="textContent"
        :isDark="isDark"
      />

      <!-- 工具调用 -->
      <div v-for="tool in toolUses" :key="tool.id" class="tool-call">
        <div class="tool-header">
          <span class="tool-icon">🔧</span>
          <span class="tool-name">{{ tool.name }}</span>
        </div>
        <pre class="tool-input">{{ JSON.stringify(tool.input, null, 2) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Message, ToolUseBlock } from '@/types/message'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  message: Message
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

const roleIcon = computed(() => {
  switch (props.message.role) {
    case 'user': return '👤'
    case 'assistant': return '🤖'
    case 'system': return '⚙️'
    default: return '💬'
  }
})

const roleName = computed(() => {
  switch (props.message.role) {
    case 'user': return '你'
    case 'assistant': return 'Claude'
    case 'system': return '系统'
    default: return '未知'
  }
})

const formattedTime = computed(() => {
  const date = new Date(props.message.timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
})

const textContent = computed(() => {
  const textBlocks = props.message.content.filter(block => block.type === 'text')
  return textBlocks.map(block => (block as any).text).join('\n\n')
})

const toolUses = computed(() => {
  return props.message.content.filter(block => block.type === 'tool_use') as ToolUseBlock[]
})
</script>

<style scoped>
.message {
  padding: 16px;
  margin-bottom: 12px;
  border-radius: 8px;
  border: 1px solid #e1e4e8;
  background: #ffffff;
}

.message-user {
  background: #f6f8fa;
}

.message-assistant {
  background: #ffffff;
}

.message-system {
  background: #fff8dc;
  border-color: #ffc107;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #e1e4e8;
}

.role-icon {
  font-size: 18px;
}

.role-name {
  font-weight: 600;
  font-size: 14px;
  color: #24292e;
}

.timestamp {
  margin-left: auto;
  font-size: 12px;
  color: #586069;
}

.message-content {
  color: #24292e;
}

.tool-call {
  margin-top: 12px;
  padding: 12px;
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-weight: 600;
  color: #0366d6;
}

.tool-icon {
  font-size: 16px;
}

.tool-input {
  font-family: 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  background: #ffffff;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0;
  color: #24292e;
}
</style>

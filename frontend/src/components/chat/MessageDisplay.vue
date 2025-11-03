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

      <!-- 工具调用 - 使用专业化组件 -->
      <component
        v-for="tool in toolUses"
        :key="tool.id"
        :is="getToolComponent(tool.name)"
        :toolUse="tool"
        :result="getToolResult(tool.id)"
      />

      <!-- 工具结果(如果没有对应的 tool_use) -->
      <div v-for="result in orphanResults" :key="result.tool_use_id" class="tool-result-orphan">
        <div class="result-header">
          <span class="result-icon">📊</span>
          <span class="result-id">结果: {{ result.tool_use_id }}</span>
        </div>
        <pre class="result-content">{{ formatResult(result) }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, Component } from 'vue'
import type { Message, ToolUseBlock, ToolResultBlock } from '@/types/message'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import ReadToolDisplay from '@/components/tools/ReadToolDisplay.vue'
import EditToolDisplay from '@/components/tools/EditToolDisplay.vue'
import WriteToolDisplay from '@/components/tools/WriteToolDisplay.vue'
import BashToolDisplay from '@/components/tools/BashToolDisplay.vue'

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

const toolResults = computed(() => {
  return props.message.content.filter(block => block.type === 'tool_result') as ToolResultBlock[]
})

const orphanResults = computed(() => {
  // 找出没有对应 tool_use 的结果
  const toolUseIds = new Set(toolUses.value.map(t => t.id))
  return toolResults.value.filter(r => !toolUseIds.has(r.tool_use_id))
})

function getToolComponent(toolName: string): Component {
  const componentMap: Record<string, Component> = {
    'Read': ReadToolDisplay,
    'Edit': EditToolDisplay,
    'Write': WriteToolDisplay,
    'Bash': BashToolDisplay
  }

  // 返回对应组件,如果没有则返回 ReadToolDisplay 作为默认(可以改为通用组件)
  return componentMap[toolName] || ReadToolDisplay
}

function getToolResult(toolUseId: string): ToolResultBlock | undefined {
  return toolResults.value.find(r => r.tool_use_id === toolUseId)
}

function formatResult(result: ToolResultBlock): string {
  if (typeof result.content === 'string') {
    return result.content
  }
  return JSON.stringify(result.content, null, 2)
}
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

.tool-result-orphan {
  margin-top: 12px;
  padding: 12px;
  background: #fff8dc;
  border: 1px solid #ffc107;
  border-radius: 6px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-weight: 600;
  color: #856404;
}

.result-icon {
  font-size: 16px;
}

.result-id {
  font-size: 13px;
  font-family: monospace;
}

.result-content {
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

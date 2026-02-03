<template>
  <!-- 用户消息 - 使用新的气泡组件 -->
  <UserMessageBubble
    v-if="message.role === 'user'"
    :message="message as any"
  />

  <!-- AI 助手消息 - 使用新的 AssistantMessageDisplay 组件 -->
  <AssistantMessageDisplay
    v-else-if="message.role === 'assistant'"
    :message="enhancedMessage"
    :expanded-tools="expandedTools"
    @expanded-change="handleExpandedChange"
  />

  <!-- 系统消息 - 使用原有样式 -->
  <div
    v-else
    class="message"
    :class="`message-${message.role}`"
  >
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
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Message } from '@/types/message'
import type { EnhancedMessage, MessageTimelineItem } from '@/types/enhancedMessage'
import { MessageRole, MessageStatus } from '@/types/enhancedMessage'
import { ToolCallStatus, type ToolCall } from '@/types/display'
import UserMessageBubble from './UserMessageBubble.vue'
import AssistantMessageDisplay from './AssistantMessageDisplay.vue'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'
import { buildToolViewModel } from '@/utils/ToolViewModelBuilder'
import { useSessionStore } from '@/stores/sessionStore'
import { resolveToolStatus, toToolCallStatus } from '@/utils/toolStatusResolver'

interface Props {
  // VirtualList 会把当前项作为 source 传入
  source: Message
  index?: number
}

const props = defineProps<Props>()

// 为了模板可读性,提供一个 message 计算属性
const message = computed(() => props.source)

// 工具展开状态（本地管理）
const expandedTools = ref<Map<string, boolean>>(new Map())

// 获取 sessionStore（用于读取工具状态）
const sessionStore = useSessionStore()

// 处理工具展开状态变化
function handleExpandedChange(toolId: string, expanded: boolean) {
  expandedTools.value.set(toolId, expanded)
}

// 将 Message 转换为 EnhancedMessage
const enhancedMessage = computed((): EnhancedMessage => {
  const msg = message.value
  // 获取当前会话的消息列表，用于 resolveToolStatus 查找 tool_result
  const messages = sessionStore.currentMessages

  // 提取所有工具结果（用于查找）
  const toolResults = msg.content.filter(block => block.type === 'tool_result')

  // 构造 orderedElements（按原始顺序遍历）
  const orderedElements: MessageTimelineItem[] = []
  let allTextContent = '' // 用于 EnhancedMessage.content 字段

  // 按原始顺序遍历 content 数组
  msg.content.forEach((block: any, index: number) => {
    if (block.type === 'text') {
      // 文本块：添加到 orderedElements
      orderedElements.push({
        displayType: 'content',
        content: block.text,
        timestamp: msg.timestamp
      })
      // 同时累积到 allTextContent
      if (allTextContent) allTextContent += '\n\n'
      allTextContent += block.text
    } else if (block.type === 'tool_use' || block.type.endsWith('_tool_use')) {
      // 过滤掉 AskUserQuestion MCP 工具，它由独立组件处理
      // 注意：MCP 服务器名称是 user-interaction（使用连字符）
      if (block.toolName === 'mcp__user-interaction__AskUserQuestion') {
        return
      }
      // 工具调用块：查找对应的结果
      // 支持两种格式：
      // 1. 通用格式: type="tool_use"
      // 2. 具体工具格式: type="todo_write_tool_use", "write_tool_use" 等
      const result = toolResults.find((r: any) => r.tool_use_id === block.id)

      // 🎯 构建 ViewModel
      const viewModel = buildToolViewModel(block)

      // 🔧 使用 resolveToolStatus 从消息列表实时计算工具状态
      const statusInfo = resolveToolStatus(block.id, messages)
      const toolResult = statusInfo.result

      // 将状态转换为 EnhancedMessage 期望的格式
      const status = toToolCallStatus(statusInfo.status)

      orderedElements.push({
        displayType: 'toolCall',
        toolCall: {
          id: block.id,
          toolName: block.toolName,
          toolType: (block as any).toolType || 'GENERIC',
          input: (block as any).input || {},
          viewModel: viewModel, // ✅ 使用构建的 ViewModel
          displayName: block.toolName,
          status: status, // ✅ 使用 store 中的实时状态
          result: toolResult ? {
            type: 'tool_result',
            tool_use_id: block.id,
            content: toolResult.content,
            is_error: toolResult.is_error ?? status === ToolCallStatus.FAILED
          } : undefined,
          startTime: msg.timestamp,
          endTime: toolResult ? msg.timestamp : undefined
        } as ToolCall,
        timestamp: msg.timestamp
      })
    } else if (block.type === 'thinking') {
      // 思考链块：添加到 orderedElements
      const thinkingContent = (block as any).thinking || ''
      orderedElements.push({
        displayType: 'thinking',
        content: thinkingContent,
        timestamp: msg.timestamp
      })
    }
    // tool_result 块和未知块类型不需要单独渲染
  })

  const roleMap: Record<string, MessageRole> = {
    user: MessageRole.USER,
    assistant: MessageRole.ASSISTANT,
    system: MessageRole.SYSTEM
  }

  return {
    id: msg.id,
    role: roleMap[msg.role] || MessageRole.ASSISTANT,
    timestamp: msg.timestamp,
    contexts: [],
    model: undefined,
    status: msg.isStreaming ? MessageStatus.STREAMING : MessageStatus.COMPLETE,
    isStreaming: msg.isStreaming || false,
    isError: false,
    orderedElements: orderedElements,
    isCompactSummary: false
  } as EnhancedMessage
})

const roleIcon = computed(() => {
  switch (message.value.role) {
    case 'user': return '👤'
    case 'assistant': return '🤖'
    case 'system': return '⚙️'
    default: return '💬'
  }
})

const roleName = computed(() => {
  switch (message.value.role) {
    case 'user': return '你'
    case 'assistant': return 'Claude'
    case 'system': return '系统'
    default: return '未知'
  }
})

const formattedTime = computed(() => {
  const date = new Date(message.value.timestamp)
  return date.toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit'
  })
})

const textContent = computed(() => {
  const textBlocks = message.value.content.filter(block => block.type === 'text')
  return textBlocks.map(block => {
    if (block.type === 'text' && 'text' in block) {
      return block.text
    }
    return ''
  }).join('\n\n')
})
</script>

<style scoped>
.message {
  padding: 16px 0;
  margin-bottom: 20px;
  transition: opacity 0.2s;
}

.message:hover {
  opacity: 0.95;
}

/* AI 助手消息 - 极简设计，无背景 */
.message-assistant {
  background: transparent;
  border: none;
  padding: 12px 0;
}

/* 系统消息 - 保留轻微背景提示 */
.message-system {
  background: var(--theme-warning-background, rgba(255, 193, 7, 0.1));
  border-left: 3px solid var(--theme-warning, #ffc107);
  padding: 12px 16px;
  border-radius: 4px;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 0;
  border-bottom: none;
}

/* AI 助手消息头部 - 更简洁 */
.message-assistant .message-header {
  margin-bottom: 8px;
  opacity: 0.7;
}

.role-icon {
  font-size: 16px;
}

.role-name {
  font-weight: 500;
  font-size: 13px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.6));
}

.timestamp {
  margin-left: auto;
  font-size: 11px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
}

.message-content {
  color: var(--theme-foreground, #24292e);
  line-height: 1.6;
}

/* AI 助手消息内容 - 优化排版 */
.message-assistant .message-content {
  font-size: 14px;
  line-height: 1.7;
}

/* 加载占位符样式 */
.loading-placeholder {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}

.loading-dots {
  display: flex;
  gap: 6px;
  align-items: center;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--theme-primary, #0366d6);
  animation: bounce 1.4s infinite ease-in-out both;
}

.dot:nth-child(1) {
  animation-delay: -0.32s;
}

.dot:nth-child(2) {
  animation-delay: -0.16s;
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.5;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.loading-text {
  font-size: 14px;
  color: var(--theme-foreground, #586069);
  opacity: 0.8;
}

.tool-result-orphan {
  margin-top: 12px;
  padding: 6px 8px;
  background: var(--theme-warning-background, #fff8dc);
  border: 1px solid var(--theme-warning, #ffc107);
  border-radius: 6px;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
  font-weight: 600;
  color: var(--theme-warning, #856404);
}

.result-icon {
  font-size: 16px;
}

.result-id {
  font-size: 13px;
  font-family: var(--theme-editor-font-family);
}

.result-content {
  font-family: var(--theme-editor-font-family);
  font-size: 12px;
  background: var(--theme-code-background, #ffffff);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0;
  color: var(--theme-code-foreground, #24292e);
}
</style>

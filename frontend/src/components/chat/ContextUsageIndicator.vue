<template>
  <div
    v-if="totalTokens > 0"
    class="context-usage-indicator"
    :class="statusClass"
    :title="tooltipText"
  >
    <span class="usage-text">[{{ formattedTokens }}/{{ formattedMaxTokens }}]</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { EnhancedMessage, AiModel, TokenUsage } from '@/types/enhancedMessage'
import { MessageRole } from '@/types/enhancedMessage'
import { getModelContextLength } from '@/config/modelConfig'

interface Props {
  currentModel: AiModel
  messageHistory?: EnhancedMessage[]
  sessionTokenUsage?: TokenUsage | null
}

const props = withDefaults(defineProps<Props>(), {
  messageHistory: () => [],
  sessionTokenUsage: null
})

/**
 * 🎯 基于 Claude Code 原理的精确 Token 统计
 * 实现 VE→HY5→zY5 函数链
 */
const totalTokens = computed(() => {
  return calculateAccurateTokens(
    props.messageHistory,
    props.sessionTokenUsage
  )
})

const maxTokens = computed(() => {
  return getModelContextLength(props.currentModel)
})

const percentage = computed(() => {
  if (maxTokens.value === 0) return 0
  return Math.round((totalTokens.value / maxTokens.value) * 100)
})

/**
 * 状态颜色类名
 * 基于 Claude Code 的 92% 阈值系统
 */
const statusClass = computed(() => {
  const p = percentage.value
  if (p >= 95) return 'status-critical'  // 危险红色
  if (p >= 92) return 'status-warning'   // 警告橙色 - Claude Code 自动压缩阈值
  if (p >= 75) return 'status-caution'   // 注意黄色
  return 'status-normal'                 // 正常灰色
})

/**
 * 格式化 Token 数量显示
 */
const formattedTokens = computed(() => formatTokenCount(totalTokens.value))
const formattedMaxTokens = computed(() => formatTokenCount(maxTokens.value))

/**
 * 悬浮提示文本
 */
const tooltipText = computed(() => {
  let text = `上下文使用: ${totalTokens.value.toLocaleString()} / ${maxTokens.value.toLocaleString()} tokens (${percentage.value}%)`
  
  text += '\n\n📊 统计原理:'
  if (props.messageHistory.length > 0) {
    text += '\n• 基于 Claude Code 的 VE→HY5→zY5 函数链'
    text += '\n• VE: 逆序遍历找最新 assistant 消息'
    text += '\n• HY5: 过滤 synthetic 消息，取真实 API 调用'
    text += '\n• zY5: 累加 input+output+cache_creation+cache_read'
  } else {
    text += '\n• 新会话，暂无 API 调用数据'
  }
  
  // 缓存优化说明
  if (props.sessionTokenUsage && props.sessionTokenUsage.cacheCreationTokens > 0) {
    text += '\n\n⚡ 缓存优化:'
    text += `\n• 缓存创建: ${props.sessionTokenUsage.cacheCreationTokens.toLocaleString()} tokens`
    if (props.sessionTokenUsage.cacheReadTokens > 0) {
      text += `\n• 缓存复用: ${props.sessionTokenUsage.cacheReadTokens.toLocaleString()} tokens`
    }
  }
  
  // 状态提示
  const p = percentage.value
  if (p >= 95) text += '\n\n🚨 上下文窗口即将用完！建议立即开启新对话'
  else if (p >= 92) text += '\n\n⚠️ 已达到 Claude Code 的 92% 自动压缩阈值'
  else if (p >= 75) text += '\n\n💡 接近 92% 阈值，可考虑开启新对话'
  else if (p >= 50) text += '\n\n💡 上下文已使用一半，注意管理'
  
  return text
})

/**
 * 🎯 实现 Claude Code 的 VE 函数：逆序遍历找最新 token usage
 */
function findLatestTokenUsage(messageHistory: EnhancedMessage[]): TokenUsage | null {
  for (let i = messageHistory.length - 1; i >= 0; i--) {
    const message = messageHistory[i]
    if (isValidAssistantMessage(message)) {
      return message.tokenUsage || null
    }
  }
  return null
}

/**
 * 🎯 实现 Claude Code 的 HY5 函数：验证 assistant 消息有效性
 */
function isValidAssistantMessage(message: EnhancedMessage): boolean {
  return (
    message.role === MessageRole.ASSISTANT &&
    message.tokenUsage != null &&
    !message.orderedElements.some(item => 
      item.type === 'content' && (item as any).content?.includes('<synthetic>')
    )
  )
}

/**
 * 🎯 实现 Claude Code 的 zY5 函数：计算总 token 数
 */
function calculateTotalTokens(usage: TokenUsage): number {
  return (
    usage.inputTokens +
    usage.outputTokens +
    usage.cacheCreationTokens +
    usage.cacheReadTokens
  )
}

/**
 * 基于 Claude Code 原理的精确 Token 统计
 */
function calculateAccurateTokens(
  messageHistory: EnhancedMessage[],
  sessionTokenUsage: TokenUsage | null
): number {
  const latestUsage = findLatestTokenUsage(messageHistory)
  
  if (latestUsage) {
    return calculateTotalTokens(latestUsage)
  }
  
  return 0
}

/**
 * 格式化 token 数量显示
 */
function formatTokenCount(tokens: number): string {
  if (tokens < 1000) return tokens.toString()
  if (tokens < 10000) return (tokens / 1000).toFixed(1) + 'k'
  return Math.round(tokens / 1000) + 'k'
}
</script>

<style scoped>
.context-usage-indicator {
  display: inline-flex;
  align-items: center;
  padding: 2px 4px;
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
  cursor: help;
  transition: all 0.3s ease;
}

.context-usage-indicator:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.05));
}

.usage-text {
  white-space: nowrap;
}

/* 状态颜色 */
.status-normal {
  color: var(--ide-text-secondary, #6a737d);
}

.status-caution {
  color: #FFA500;
}

.status-warning {
  color: #FF8800;
}

.status-critical {
  color: #FF4444;
  font-weight: 600;
}
</style>


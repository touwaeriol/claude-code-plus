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
import type { EnhancedMessage, TokenUsage } from '@/types/enhancedMessage'
import { MessageRole } from '@/types/enhancedMessage'
import { getModelContextLength } from '@/config/modelConfig'

// Token 使用量阈值常量（基于 Claude Code 的设计）
const TOKEN_USAGE_THRESHOLDS = {
  CRITICAL: 95,  // 危险红色 - 上下文窗口即将用完
  WARNING: 92,   // 警告橙色 - Claude Code 自动压缩阈值
  CAUTION: 75,   // 注意黄色 - 接近压缩阈值
  NORMAL: 0      // 正常灰色
} as const

interface Props {
  currentModel: string
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
  if (p >= TOKEN_USAGE_THRESHOLDS.CRITICAL) return 'status-critical'
  if (p >= TOKEN_USAGE_THRESHOLDS.WARNING) return 'status-warning'
  if (p >= TOKEN_USAGE_THRESHOLDS.CAUTION) return 'status-caution'
  return 'status-normal'
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
  const sections = [
    getUsageText(),
    getStatisticsText(),
    getCacheOptimizationText(),
    getStatusHintText()
  ].filter(Boolean)

  return sections.join('\n\n')
})

/**
 * 获取使用量文本
 */
function getUsageText(): string {
  return `上下文使用: ${totalTokens.value.toLocaleString()} / ${maxTokens.value.toLocaleString()} tokens (${percentage.value}%)`
}

/**
 * 获取统计原理说明文本
 */
function getStatisticsText(): string {
  let text = '📊 统计原理:'
  if (props.messageHistory.length > 0) {
    text += '\n• 基于 Claude Code 的 VE→HY5→zY5 函数链'
    text += '\n• VE: 逆序遍历找最新 assistant 消息'
    text += '\n• HY5: 过滤 synthetic 消息，取真实 API 调用'
    text += '\n• zY5: 累加 input（上行）+ output（下行）tokens'
  } else {
    text += '\n• 新会话，暂无 API 调用数据'
  }
  return text
}

/**
 * 获取缓存优化说明文本
 */
function getCacheOptimizationText(): string {
  if (!props.sessionTokenUsage || props.sessionTokenUsage.cacheCreationTokens === 0) {
    return ''
  }

  let text = '⚡ 缓存优化:'
  text += `\n• 缓存创建: ${props.sessionTokenUsage.cacheCreationTokens.toLocaleString()} tokens`
  if (props.sessionTokenUsage.cacheReadTokens > 0) {
    text += `\n• 缓存复用: ${props.sessionTokenUsage.cacheReadTokens.toLocaleString()} tokens`
  }
  return text
}

/**
 * 获取状态提示文本
 */
function getStatusHintText(): string {
  const p = percentage.value
  if (p >= TOKEN_USAGE_THRESHOLDS.CRITICAL) {
    return '🚨 上下文窗口即将用完！建议立即开启新对话'
  }
  if (p >= TOKEN_USAGE_THRESHOLDS.WARNING) {
    return '⚠️ 已达到 Claude Code 的 92% 自动压缩阈值'
  }
  if (p >= TOKEN_USAGE_THRESHOLDS.CAUTION) {
    return '💡 接近 92% 阈值，可考虑开启新对话'
  }
  if (p >= 50) {
    return '💡 上下文已使用一半，注意管理'
  }
  return ''
}

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
 * 过滤掉合成消息，只使用真实 API 调用的数据
 */
function isValidAssistantMessage(message: EnhancedMessage): boolean {
  // 必须是 assistant 消息且有 token 使用量
  if (message.role !== MessageRole.ASSISTANT || !message.tokenUsage) {
    return false
  }

  // 检查是否包含 synthetic 标记（合成消息）
  const hasSyntheticContent = message.orderedElements.some(item => {
    if (item.type === 'content') {
      const contentItem = item as { type: 'content'; content: string }
      return contentItem.content?.includes('<synthetic>')
    }
    return false
  })

  return !hasSyntheticContent
}

/**
 * 🎯 计算此次请求的上下行 token 消耗
 * inputTokens: 上行（上传）token
 * outputTokens: 下行（下载）token
 */
function calculateTotalTokens(usage: TokenUsage): number {
  return usage.inputTokens + usage.outputTokens
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

  if (sessionTokenUsage) {
    return calculateTotalTokens(sessionTokenUsage)
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
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05));
}

.usage-text {
  white-space: nowrap;
}

/* 状态颜色 - 使用 CSS 变量以支持主题 */
.status-normal {
  color: var(--theme-text-secondary, #6a737d);
}

.status-caution {
  color: var(--theme-warning-caution, #ffa500);
}

.status-warning {
  color: var(--theme-warning, #ff8800);
}

.status-critical {
  color: var(--theme-error, #ff4444);
  font-weight: 600;
}
</style>


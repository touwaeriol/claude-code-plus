<template>
  <div
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
import { MessageRole, getTotalTokens } from '@/types/enhancedMessage'
import { getModelContextLength } from '@/config/modelConfig'

/**
 * Token 使用量阈值常量（参考 opcode）
 */
const TOKEN_USAGE_THRESHOLDS = {
  CRITICAL: 95,  // 危险红色
  WARNING: 90,   // 警告橙色
  CAUTION: 75,   // 注意黄色
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
 * 获取最新的 TokenUsage
 */
const latestTokenUsage = computed((): TokenUsage | null => {
  return findLatestTokenUsage(props.messageHistory) || props.sessionTokenUsage || null
})

/**
 * 模型的上下文窗口大小
 */
const maxTokens = computed(() => {
  return getModelContextLength(props.currentModel)
})

/**
 * 当前已使用的 token 数量
 * 参考 opcode: input_tokens + output_tokens
 */
const totalTokens = computed(() => {
  if (!latestTokenUsage.value) return 0
  return getTotalTokens(latestTokenUsage.value)
})

/**
 * 使用百分比
 */
const percentage = computed(() => {
  if (maxTokens.value === 0) return 0
  return Math.round((totalTokens.value / maxTokens.value) * 100)
})

/**
 * 状态颜色类名
 */
const statusClass = computed(() => {
  const p = percentage.value
  if (p >= TOKEN_USAGE_THRESHOLDS.CRITICAL) return 'status-critical'
  if (p >= TOKEN_USAGE_THRESHOLDS.WARNING) return 'status-warning'
  if (p >= TOKEN_USAGE_THRESHOLDS.CAUTION) return 'status-caution'
  return 'status-normal'
})

/**
 * 格式化显示的 token 数量
 */
const formattedTokens = computed(() => formatTokenCount(totalTokens.value))
const formattedMaxTokens = computed(() => formatTokenCount(maxTokens.value))

/**
 * 悬浮提示文本（参考 opcode 简洁风格）
 */
const tooltipText = computed(() => {
  if (!latestTokenUsage.value) {
    return `上下文: 0 / ${maxTokens.value.toLocaleString()} tokens (0%)`
  }

  const usage = latestTokenUsage.value
  let text = `上下文: ${totalTokens.value.toLocaleString()} / ${maxTokens.value.toLocaleString()} tokens (${percentage.value}%)`
  text += `\n\n📊 Token 统计:`
  text += `\n• 输入: ${usage.inputTokens.toLocaleString()}`
  text += `\n• 输出: ${usage.outputTokens.toLocaleString()}`

  if (usage.cacheReadTokens > 0 || usage.cacheCreationTokens > 0) {
    text += `\n\n⚡ 缓存:`
    if (usage.cacheCreationTokens > 0) {
      text += `\n• 创建: ${usage.cacheCreationTokens.toLocaleString()}`
    }
    if (usage.cacheReadTokens > 0) {
      text += `\n• 命中: ${usage.cacheReadTokens.toLocaleString()}`
    }
  }

  // 状态提示
  const p = percentage.value
  if (p >= TOKEN_USAGE_THRESHOLDS.CRITICAL) {
    text += `\n\n🚨 上下文即将用完！`
  } else if (p >= TOKEN_USAGE_THRESHOLDS.WARNING) {
    text += `\n\n⚠️ 建议开启新对话`
  }

  return text
})

/**
 * 逆序遍历找最新的 token usage
 */
function findLatestTokenUsage(messageHistory: EnhancedMessage[]): TokenUsage | null {
  for (let i = messageHistory.length - 1; i >= 0; i--) {
    const message = messageHistory[i]
    if (message.role === MessageRole.ASSISTANT && message.tokenUsage) {
      return message.tokenUsage
    }
  }
  return null
}

/**
 * 格式化 token 数量显示（参考 opcode）
 * - >= 1,000,000 → X.XXM
 * - >= 1,000 → X.XK
 * - < 1,000 → X
 */
function formatTokenCount(tokens: number): string {
  if (tokens >= 1_000_000) {
    return `${(tokens / 1_000_000).toFixed(2)}M`
  } else if (tokens >= 1_000) {
    return `${(tokens / 1_000).toFixed(1)}K`
  }
  return tokens.toLocaleString()
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

/* 状态颜色 */
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

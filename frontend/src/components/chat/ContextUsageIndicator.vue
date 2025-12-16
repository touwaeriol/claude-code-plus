<template>
  <div
    class="context-usage-indicator"
    :title="tooltipText"
  >
    <span class="usage-text">{{ formattedTokens }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TokenUsage } from '@/types/enhancedMessage'

interface Props {
  sessionTokenUsage?: TokenUsage | null
}

const props = withDefaults(defineProps<Props>(), {
  sessionTokenUsage: null
})

/**
 * 当前上下文大小
 * sessionTokenUsage.inputTokens 已经是完整上下文大小（input + cached）
 */
const totalTokens = computed(() => {
  if (props.sessionTokenUsage) {
    return props.sessionTokenUsage.inputTokens
  }
  return 0
})

/**
 * 格式化显示
 */
const formattedTokens = computed(() => formatTokenCount(totalTokens.value))

/**
 * 悬浮提示
 */
const tooltipText = computed(() => `上下文: ${totalTokens.value.toLocaleString()} tokens`)

/**
 * 格式化 token 数量
 */
function formatTokenCount(tokens: number): string {
  if (tokens >= 1_000_000) {
    return `${(tokens / 1_000_000).toFixed(1)}M`
  } else if (tokens >= 1_000) {
    return `${(tokens / 1_000).toFixed(1)}K`
  }
  return String(tokens)
}
</script>

<style scoped>
.context-usage-indicator {
  display: inline-flex;
  align-items: center;
  padding: 2px 6px;
  font-size: 12px;
  font-family: var(--theme-font-family);
  font-weight: 500;
  color: var(--theme-text-secondary, #57606a);
  cursor: help;
}

.usage-text {
  white-space: nowrap;
}
</style>

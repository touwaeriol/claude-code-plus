<template>
  <div class="assistant-text">
    <div class="message-content">
      <MarkdownRenderer
        :content="message.content"
        :is-dark="isDark"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantText, RequestStats } from '@/types/display'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  message: AssistantText
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  isDark: false
})

// 是否显示统计信息
const showStats = computed(() => {
  return props.message.isLastInMessage && props.message.stats
})

// 统计信息
const stats = computed((): RequestStats => {
  return props.message.stats || {
    requestDuration: 0,
    inputTokens: 0,
    outputTokens: 0
  }
})

// 统计信息 tooltip
const statsTooltip = computed(() => {
  if (!props.message.stats) return ''
  const s = props.message.stats
  return `请求耗时: ${formatDuration(s.requestDuration)}\n上行: ${s.inputTokens.toLocaleString()} tokens\n下行: ${s.outputTokens.toLocaleString()} tokens`
})

/**
 * 格式化时间
 */
function formatDuration(ms: number): string {
  if (ms <= 0) return '0s'
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60
  return `${minutes}m ${remainingSeconds}s`
}

/**
 * 格式化数字（支持 k 单位）
 */
function formatNumber(num: number): string {
  if (num < 1000) return num.toString()
  if (num < 10000) return (num / 1000).toFixed(1) + 'k'
  return Math.round(num / 1000) + 'k'
}
</script>

<style scoped>
.assistant-text {
  margin: 16px 0;
  padding: 8px 0;
  max-width: 100%;
}

.message-content {
  line-height: 1.6;
}

/* 统计信息样式 */
.message-stats {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 10px;
  background: var(--ide-panel-background, #f6f8fa);
  border: 1px solid var(--ide-border, #e1e4e8);
  border-radius: 12px;
  font-size: 11px;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, monospace;
  color: var(--ide-secondary-foreground, #586069);
  cursor: default;
  user-select: none;
}

.stat-item {
  color: var(--ide-foreground, #24292e);
}

.stat-item.duration {
  color: var(--ide-accent, #0366d6);
}

.stat-item.tokens {
  color: var(--ide-secondary-foreground, #586069);
}

.stat-separator {
  color: var(--ide-border, #e1e4e8);
  margin: 0 2px;
}

.stat-label {
  color: var(--ide-secondary-foreground, #586069);
  opacity: 0.7;
  margin-left: 2px;
}

/* 暗色主题 */
:global(.theme-dark) .message-stats {
  background: var(--ide-panel-background, #21262d);
  border-color: var(--ide-border, #30363d);
}

:global(.theme-dark) .stat-item {
  color: var(--ide-foreground, #c9d1d9);
}

:global(.theme-dark) .stat-item.duration {
  color: var(--ide-accent, #58a6ff);
}

:global(.theme-dark) .stat-item.tokens {
  color: var(--ide-secondary-foreground, #8b949e);
}
</style>

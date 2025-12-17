<template>
  <div class="assistant-text">
    <div class="message-content">
      <MarkdownRenderer
        :content="message.content"
      />
    </div>
    <div
      v-if="showStats"
      class="message-stats"
      :title="statsTooltip"
    >
      <span class="stat-item duration">
        {{ formatDuration(stats.requestDuration) }}
      </span>
      <span class="stat-separator">•</span>
      <span class="stat-item tokens">
        ↑ {{ formatNumber(stats.inputTokens) }}
        <span class="stat-label">tokens</span>
      </span>
      <span class="stat-separator">/</span>
      <span class="stat-item tokens">
        ↓ {{ formatNumber(stats.outputTokens) }}
        <span class="stat-label">tokens</span>
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AssistantText, RequestStats } from '@/types/display'
import MarkdownRenderer from '@/components/markdown/MarkdownRenderer.vue'

interface Props {
  message: AssistantText
}

const props = defineProps<Props>()

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
  margin: 0;
  padding: 2px 0;
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
  background: var(--theme-panel-background, #f6f8fa);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  font-size: 11px;
  font-family: var(--theme-editor-font-family);
  color: var(--theme-secondary-foreground);
  cursor: default;
  user-select: none;
}

.stat-item {
  color: var(--theme-foreground, #24292e);
}

.stat-item.duration {
  color: var(--theme-accent, #0366d6);
}

.stat-item.tokens {
  color: var(--theme-secondary-foreground, #586069);
}

.stat-separator {
  color: var(--theme-border, #e1e4e8);
  margin: 0 2px;
}

.stat-label {
  color: var(--theme-secondary-foreground, #586069);
  opacity: 0.7;
  margin-left: 2px;
}
</style>

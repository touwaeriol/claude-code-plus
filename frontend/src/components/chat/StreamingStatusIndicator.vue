<template>
  <div
    v-if="isVisible"
    class="streaming-status"
  >
    <div class="status-content">
      <!-- 状态指示器 -->
      <div class="status-indicator">
        <div class="spinner" />
        <span class="status-text">{{ statusText }}</span>
      </div>

      <!-- Token 使用统计 -->
      <div
        v-if="tokenUsage"
        class="token-stats"
      >
        <span class="token-label">Tokens:</span>
        <span class="token-value">{{ formatTokens(tokenUsage.inputTokens) }} in</span>
        <span class="token-separator">·</span>
        <span class="token-value">{{ formatTokens(tokenUsage.outputTokens) }} out</span>
        <span class="token-separator">·</span>
        <span class="token-total">{{ formatTokens(calculatedTotal) }} total</span>
      </div>

      <!-- 耗时统计 -->
      <div
        v-if="elapsedTime > 0"
        class="time-stats"
      >
        <span class="time-icon">⏱</span>
        <span class="time-value">{{ formatTime(elapsedTime) }}</span>
      </div>

      <!-- 当前前缀 (如果有) -->
      <div
        v-if="currentPrefix"
        class="current-prefix"
      >
        <span class="prefix-label">Prefix:</span>
        <code class="prefix-value">{{ currentPrefix }}</code>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue'
import type { TokenUsage } from '@/types/enhancedMessage'

interface Props {
  isGenerating: boolean
  tokenUsage?: TokenUsage | null
  currentPrefix?: string
}

const props = withDefaults(defineProps<Props>(), {
  tokenUsage: null,
  currentPrefix: ''
})

const elapsedTime = ref(0)
let startTime = 0
let timer: ReturnType<typeof setInterval> | null = null

const isVisible = computed(() => props.isGenerating)

const statusText = computed(() => {
  if (!props.isGenerating) return ''
  if (props.currentPrefix) return `正在生成 ${props.currentPrefix}...`
  return ''
})

/**
 * 计算此次请求的上下行 token 消耗
 * 只累加 inputTokens（上行）+ outputTokens（下行）
 */
const calculatedTotal = computed(() => {
  if (!props.tokenUsage) return 0
  return props.tokenUsage.inputTokens + props.tokenUsage.outputTokens
})

// 监听生成状态
watch(() => props.isGenerating, (newVal) => {
  if (newVal) {
    // 开始生成
    startTime = Date.now()
    elapsedTime.value = 0
    timer = setInterval(() => {
      elapsedTime.value = Date.now() - startTime
    }, 100)
  } else {
    // 停止生成
    if (timer) {
      clearInterval(timer)
      timer = null
    }
  }
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})

function formatTokens(count: number): string {
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)}k`
  }
  return count.toString()
}

function formatTime(ms: number): string {
  if (ms < 1000) {
    return `${ms}ms`
  }
  const seconds = Math.floor(ms / 1000)
  const minutes = Math.floor(seconds / 60)
  const remainingSeconds = seconds % 60

  if (minutes > 0) {
    return `${minutes}m ${remainingSeconds}s`
  }
  return `${seconds}s`
}
</script>

<style scoped>
.streaming-status {
  position: fixed;
  bottom: 100px;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  z-index: 1000;
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.status-content {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 13px;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid var(--el-color-primary-light-5);
  border-top-color: var(--el-color-primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.status-text {
  color: var(--el-text-color-regular);
  font-weight: 500;
}

.token-stats {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
}

.token-label {
  font-weight: 600;
  color: var(--el-text-color-regular);
}

.token-value {
  color: var(--el-color-primary);
}

.token-separator {
  color: var(--el-text-color-placeholder);
}

.token-total {
  font-weight: 600;
  color: var(--el-color-success);
}

.time-stats {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-light);
  color: var(--el-text-color-secondary);
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
}

.time-icon {
  font-size: 12px;
}

.time-value {
  color: var(--el-color-info);
  font-weight: 500;
}

.current-prefix {
  display: flex;
  align-items: center;
  gap: 6px;
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color-light);
}

.prefix-label {
  font-weight: 600;
  color: var(--el-text-color-regular);
  font-size: 12px;
}

.prefix-value {
  background: var(--el-fill-color-light);
  color: var(--el-color-primary);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'SF Mono', Monaco, 'Cascadia Code', 'Roboto Mono', Consolas, 'Courier New', monospace;
}
</style>

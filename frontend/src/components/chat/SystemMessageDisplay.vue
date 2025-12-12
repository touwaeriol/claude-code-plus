<template>
  <div
    class="system-message"
    :class="`level-${message.level}`"
  >
    <div class="message-header">
      <span class="level-icon">{{ levelIcon }}</span>
      <span class="level-name">{{ levelName }}</span>
      <!-- 隐藏时间戳，使消息展示更紧凑 -->
      <!-- <span class="timestamp">{{ formattedTime }}</span> -->
    </div>

    <div class="message-content">
      {{ message.content }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { SystemMessage } from '@/types/display'

interface Props {
  message: SystemMessage
}

const props = defineProps<Props>()

const levelIcon = computed(() => {
  switch (props.message.level) {
    case 'info':
      return 'ℹ️'
    case 'warning':
      return '⚠️'
    case 'error':
      return '❌'
    default:
      return 'ℹ️'
  }
})

const levelName = computed(() => {
  switch (props.message.level) {
    case 'info':
      return 'Info'
    case 'warning':
      return 'Warning'
    case 'error':
      return 'Error'
    default:
      return 'System'
  }
})
</script>

<style scoped>
.system-message {
  margin: 12px 0;
  padding: 10px 14px;
  border-radius: 6px;
  font-size: 13px;
  max-width: 70%;
  margin-left: auto;
  margin-right: auto;
}

.system-message.level-info {
  background: color-mix(in srgb, var(--theme-accent) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-accent) 40%, transparent);
  color: var(--theme-accent);
}

.system-message.level-warning {
  background: color-mix(in srgb, var(--theme-warning) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-warning) 40%, transparent);
  color: var(--theme-warning);
}

.system-message.level-error {
  background: color-mix(in srgb, var(--theme-error) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--theme-error) 40%, transparent);
  color: var(--theme-error);
}

.message-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 11px;
  opacity: 0.9;
}

.level-icon {
  font-size: 14px;
}

.level-name {
  font-weight: 600;
}

.timestamp {
  margin-left: auto;
  opacity: 0.7;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.4;
}
</style>


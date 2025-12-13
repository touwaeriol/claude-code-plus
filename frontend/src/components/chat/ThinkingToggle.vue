<template>
  <div
    class="thinking-toggle"
    :class="{
      'is-enabled': isEnabled,
      'is-disabled': !canToggle
    }"
    :title="tooltipText"
    @click="handleClick"
  >
    <span v-if="thinkingMode !== 'never'" class="thinking-icon">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
        <path v-if="isEnabled" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        <path v-else d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
      </svg>
    </span>
    <span class="thinking-label">{{ $t('thinking.label') }}</span>
    <span class="thinking-indicator">
      <span v-if="isEnabled" class="dot active" />
      <span v-else class="dot inactive" />
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import type { ThinkingMode } from '@/constants/models'

const { t } = useI18n()

interface Props {
  /** 思考模式 */
  thinkingMode: ThinkingMode
  /** 当前是否开启（仅 optional 模式有效） */
  enabled: boolean
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'toggle', value: boolean): void
}>()

/** 是否可以切换 */
const canToggle = computed(() => props.thinkingMode === 'optional')

/** 实际的开启状态 */
const isEnabled = computed(() => {
  switch (props.thinkingMode) {
    case 'always':
      return true
    case 'never':
      return false
    case 'optional':
      return props.enabled
  }
})

/** 提示文本 */
const tooltipText = computed(() => {
  switch (props.thinkingMode) {
    case 'always':
      return t('thinking.alwaysOn')
    case 'never':
      return t('thinking.notSupported')
    case 'optional':
      return props.enabled ? t('thinking.toggleOff') : t('thinking.toggleOn')
  }
})

function handleClick() {
  if (canToggle.value) {
    emit('toggle', !props.enabled)
  }
}
</script>

<style scoped>
/* 紧凑样式 */
.thinking-toggle {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  color: var(--theme-secondary-foreground, #6b7280);
  background: transparent;
}

.thinking-toggle:hover:not(.is-disabled) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05));
}

.thinking-toggle.is-enabled {
  color: var(--theme-accent, #0366d6);
}

.thinking-toggle.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.thinking-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
}

.thinking-icon svg {
  width: 12px;
  height: 12px;
}

.thinking-label {
  font-weight: 500;
}

.thinking-indicator {
  display: flex;
  align-items: center;
  margin-left: 2px;
}

.dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  transition: background-color 0.2s ease;
}

.dot.active {
  background: var(--theme-success, #22c55e);
}

.dot.inactive {
  background: var(--theme-secondary-foreground, #9ca3af);
  opacity: 0.5;
}
</style>

<template>
  <div
    class="status-toggle"
    :class="{
      'is-enabled': enabled,
      'is-disabled': disabled
    }"
    :title="tooltip"
    @click="handleClick"
  >
    <span v-if="showIcon" class="toggle-icon">
      <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor">
        <path v-if="enabled" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/>
        <path v-else d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/>
      </svg>
    </span>
    <span class="toggle-label">{{ label }}</span>
    <span class="toggle-indicator">
      <span :class="['dot', enabled ? 'active' : 'inactive']" />
    </span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  /** 显示文字 */
  label: string
  /** 是否启用 */
  enabled: boolean
  /** 是否禁用（不可点击） */
  disabled?: boolean
  /** 是否显示图标 */
  showIcon?: boolean
  /** 提示文字 */
  tooltip?: string
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  showIcon: true,
  tooltip: ''
})

const emit = defineEmits<{
  (e: 'toggle', value: boolean): void
}>()

function handleClick() {
  if (!props.disabled) {
    emit('toggle', !props.enabled)
  }
}
</script>

<style scoped>
.status-toggle {
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

.status-toggle:hover:not(.is-disabled) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.05));
}

.status-toggle.is-enabled {
  color: var(--theme-accent, #0366d6);
}

.status-toggle.is-disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.toggle-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 12px;
  height: 12px;
}

.toggle-icon svg {
  width: 12px;
  height: 12px;
}

.toggle-label {
  font-weight: 500;
}

.toggle-indicator {
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

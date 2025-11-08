<template>
  <div
    class="toast-container"
    :class="{ 'theme-dark': isDark }"
  >
    <TransitionGroup
      name="toast"
      tag="div"
    >
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast-item"
        :class="`toast-${toast.type}`"
        @click="handleToastClick(toast.id)"
      >
        <div class="toast-icon">
          {{ TOAST_ICONS[toast.type] }}
        </div>
        <div class="toast-content">
          <span class="toast-message">{{ toast.message }}</span>
        </div>
        <button
          class="toast-close"
          title="关闭"
          @click.stop="removeToast(toast.id)"
        >
          ✕
        </button>
      </div>
    </TransitionGroup>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { TOAST_ICONS } from '@/types/toast'

interface Props {
  isDark?: boolean
}

const _props = withDefaults(defineProps<Props>(), {
  isDark: false
})

const toastStore = useToastStore()
const toasts = computed(() => toastStore.toasts)

function removeToast(id: string) {
  toastStore.removeToast(id)
}

function handleToastClick(id: string) {
  // 点击通知也可以关闭
  removeToast(id)
}
</script>

<style scoped>
.toast-container {
  position: fixed;
  top: 20px;
  right: 20px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  gap: 12px;
  pointer-events: none;
}

.toast-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 300px;
  max-width: 400px;
  padding: 12px 16px;
  background: var(--ide-background);
  border: 1px solid var(--ide-border);
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  cursor: pointer;
  pointer-events: auto;
  transition: all 0.3s;
}

.toast-item:hover {
  transform: translateX(-4px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

/* Toast 类型样式 */
.toast-info {
  border-left: 4px solid var(--ide-info);
}

.toast-success {
  border-left: 4px solid var(--ide-success);
}

.toast-warning {
  border-left: 4px solid var(--ide-warning);
}

.toast-error {
  border-left: 4px solid var(--ide-error);
}

.toast-icon {
  font-size: 20px;
  flex-shrink: 0;
  line-height: 1;
}

.toast-content {
  flex: 1;
  min-width: 0;
}

.toast-message {
  display: block;
  font-size: 14px;
  line-height: 1.5;
  color: var(--ide-foreground);
  word-wrap: break-word;
}

.toast-close {
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 3px;
  font-size: 14px;
  color: var(--ide-foreground);
  opacity: 0.5;
  transition: all 0.2s;
  flex-shrink: 0;
}

.toast-close:hover {
  opacity: 1;
  background: rgba(0, 0, 0, 0.1);
}

.theme-dark .toast-close:hover {
  background: rgba(255, 255, 255, 0.1);
}

/* 进入/离开动画 */
.toast-enter-active {
  animation: toastSlideIn 0.3s;
}

.toast-leave-active {
  animation: toastSlideOut 0.3s;
}

@keyframes toastSlideIn {
  from {
    transform: translateX(100%);
    opacity: 0;
  }
  to {
    transform: translateX(0);
    opacity: 1;
  }
}

@keyframes toastSlideOut {
  from {
    transform: translateX(0);
    opacity: 1;
  }
  to {
    transform: translateX(100%);
    opacity: 0;
  }
}

/* 移动动画 */
.toast-move {
  transition: all 0.3s;
}
</style>

<template>
  <div class="dialog-overlay" @click.self="handleCancel">
    <div class="dialog-container">
      <div class="dialog-header">
        <h3 class="dialog-title">新建会话</h3>
        <button class="close-btn" type="button" @click="handleCancel">
          ×
        </button>
      </div>

      <div class="dialog-body">
        <BackendSelector
          v-model="selectedBackend"
          :is-session-active="isSessionActive"
        />
      </div>

      <div class="dialog-footer">
        <button
          class="btn btn-secondary"
          type="button"
          @click="handleCancel"
        >
          取消
        </button>
        <button
          class="btn btn-primary"
          type="button"
          @click="handleConfirm"
        >
          确认
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { BackendType } from '@/types/backend'
import BackendSelector from '@/components/settings/BackendSelector.vue'

interface Props {
  /** 当前会话是否活跃 */
  isSessionActive?: boolean
  /** 当前后端类型 */
  currentBackend: BackendType
}

const props = withDefaults(defineProps<Props>(), {
  isSessionActive: false,
})

interface Emits {
  (e: 'confirm', backendType: BackendType): void
  (e: 'cancel'): void
}

const emit = defineEmits<Emits>()

// 选中的后端类型
const selectedBackend = ref<BackendType>(props.currentBackend)

// 当 currentBackend 改变时更新 selectedBackend
watch(
  () => props.currentBackend,
  (newBackend) => {
    selectedBackend.value = newBackend
  }
)

function handleConfirm() {
  emit('confirm', selectedBackend.value)
}

function handleCancel() {
  emit('cancel')
}
</script>

<style scoped>
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.dialog-container {
  background: var(--theme-card-background, #ffffff);
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.2);
  max-width: 500px;
  width: 90%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  animation: slideIn 0.2s ease;
}

@keyframes slideIn {
  from {
    transform: translateY(-20px);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.dialog-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
}

.close-btn {
  width: 28px;
  height: 28px;
  border-radius: 4px;
  border: none;
  background: transparent;
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s ease, color 0.15s ease;
}

.close-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  color: var(--theme-foreground, #24292e);
}

.dialog-body {
  padding: 20px;
  flex: 1;
  overflow-y: auto;
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  padding: 16px 20px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.btn {
  padding: 8px 16px;
  border-radius: 6px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  outline: none;
}

.btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--theme-background, #ffffff);
  border-color: var(--theme-border, #d0d7de);
  color: var(--theme-foreground, #24292e);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
}

.btn-primary {
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--theme-accent-hover, #0256c2);
}

.btn-primary:active:not(:disabled) {
  transform: scale(0.98);
}
</style>

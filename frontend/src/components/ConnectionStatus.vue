<template>
  <div class="connection-status" :class="statusClass">
    <div class="status-icon">{{ statusIcon }}</div>
    <div class="status-text">
      <div class="status-mode">{{ modeText }}</div>
      <div class="status-detail">{{ statusDetail }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ideaBridge } from '@/services/ideaBridge'

const mode = ref<string>('unknown')
const isReady = ref<boolean>(false)
const serverPort = ref<string>('8765')

const statusClass = computed(() => {
  if (!isReady.value) return 'status-connecting'
  return mode.value === 'ide' ? 'status-ide' : 'status-http'
})

const statusIcon = computed(() => {
  if (!isReady.value) return '🔄'
  return mode.value === 'ide' ? '🔌' : '🌐'
})

const modeText = computed(() => {
  if (!isReady.value) return 'Connecting...'
  return mode.value === 'ide' ? 'Plugin Mode' : 'Browser Mode'
})

const statusDetail = computed(() => {
  if (!isReady.value) return 'Initializing bridge...'
  return mode.value === 'ide'
    ? `IDE Mode (Port: ${serverPort.value})`
    : `Browser Mode (Port: ${serverPort.value})`
})

onMounted(async () => {
  await ideaBridge.waitForReady()
  mode.value = ideaBridge.getMode()
  isReady.value = ideaBridge.checkReady()
  serverPort.value = ideaBridge.getServerPort()
})
</script>

<style scoped>
.connection-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 12px;
  transition: all 0.3s ease;
}

.status-connecting {
  background-color: rgba(255, 193, 7, 0.1);
  border: 1px solid rgba(255, 193, 7, 0.3);
}

.status-ide {
  background-color: rgba(76, 175, 80, 0.1);
  border: 1px solid rgba(76, 175, 80, 0.3);
}

.status-http {
  background-color: rgba(33, 150, 243, 0.1);
  border: 1px solid rgba(33, 150, 243, 0.3);
}

.status-icon {
  font-size: 16px;
}

.status-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.status-mode {
  font-weight: 600;
  color: var(--foreground, #333);
}

.status-detail {
  font-size: 10px;
  opacity: 0.7;
  color: var(--foreground, #666);
}

/* 暗色主题适配 */
@media (prefers-color-scheme: dark) {
  .status-mode {
    color: var(--foreground, #eee);
  }

  .status-detail {
    color: var(--foreground, #aaa);
  }
}
</style>

<template>
  <div v-if="visible" class="chrome-popup-overlay" @click.self="close">
    <div class="chrome-popup">
      <div class="popup-header">
        <span class="popup-title">Chrome Extension</span>
        <div class="popup-actions">
          <button class="close-btn" @click="close" title="Close">×</button>
        </div>
      </div>
      <div class="popup-content">
        <div v-if="!isConnected" class="empty-state">
          Not connected
        </div>
        <template v-else>
          <!-- 状态列表 -->
          <div class="status-list">
            <div class="status-item">
              <span class="status-label">Installed</span>
              <span class="status-value" :class="status?.installed ? 'value-success' : 'value-warning'">
                {{ status?.installed ? 'Yes' : 'No' }}
              </span>
            </div>
            <div v-if="status?.extensionVersion" class="status-item">
              <span class="status-label">Version</span>
              <span class="status-value">{{ status.extensionVersion }}</span>
            </div>
            <div v-if="status?.mcpServerStatus" class="status-item">
              <span class="status-label">MCP Server</span>
              <span class="status-value" :class="getMcpStatusClass(status.mcpServerStatus)">
                {{ status.mcpServerStatus }}
              </span>
            </div>
            <div class="status-item">
              <span class="status-label">Connected</span>
              <span class="status-value" :class="status?.connected ? 'value-success' : 'value-error'">
                {{ status?.connected ? 'Yes' : 'No' }}
              </span>
            </div>
          </div>

          <!-- 分隔线 -->
          <div class="divider"></div>

          <!-- Chrome 开关 -->
          <div class="toggle-section">
            <div class="toggle-row">
              <span class="toggle-label">Enable Chrome</span>
              <label class="toggle-switch">
                <input
                  type="checkbox"
                  :checked="chromeEnabled"
                  :disabled="!status?.installed"
                  @change="handleToggle"
                >
                <span class="toggle-slider"></span>
              </label>
            </div>
            <div class="toggle-hint">
              {{ chromeEnabled ? 'Will use --chrome flag' : 'Will use --no-chrome flag' }}
            </div>
            <div v-if="needsReconnect" class="reconnect-hint">
              Reconnect required to apply
            </div>
          </div>

          <!-- 安装按钮 -->
          <div v-if="!status?.installed" class="install-section">
            <button class="install-btn" @click="handleInstall">
              <span class="install-icon">🌐</span>
              Install Extension
            </button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

export interface ChromeStatus {
  installed: boolean
  enabled: boolean
  connected: boolean
  mcpServerStatus?: string
  extensionVersion?: string
}

const props = defineProps<{
  visible: boolean
  status: ChromeStatus | null
  isConnected: boolean
  chromeEnabled: boolean
  connectedChromeEnabled?: boolean  // 当前连接使用的 Chrome 状态
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'toggle-enabled', value: boolean): void
  (e: 'install'): void
}>()

const CHROME_WEBSTORE_URL = 'https://chromewebstore.google.com/detail/claude/fcoeoabgfenejglbffodgkkbkcdhcgfn'

// 是否需要重连（当前设置与连接时的设置不同）
const needsReconnect = computed(() => {
  if (props.connectedChromeEnabled === undefined) return false
  return props.chromeEnabled !== props.connectedChromeEnabled
})

function getMcpStatusClass(status: string): string {
  switch (status) {
    case 'connected': return 'value-success'
    case 'failed': return 'value-error'
    default: return ''
  }
}

function handleToggle(event: Event) {
  const target = event.target as HTMLInputElement
  emit('toggle-enabled', target.checked)
}

function handleInstall() {
  window.open(CHROME_WEBSTORE_URL, '_blank')
  emit('install')
}

function close() {
  emit('close')
}
</script>

<style scoped>
.chrome-popup-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: flex-start;
  justify-content: flex-end;
  padding: 40px 10px;
  z-index: 1000;
}

.chrome-popup {
  background: var(--theme-panel-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 260px;
  max-width: 320px;
}

.popup-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.popup-title {
  font-weight: 600;
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
}

.popup-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.close-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: var(--theme-muted-foreground, #656d76);
  transition: background 0.15s;
  font-size: 18px;
  font-weight: 300;
}

.close-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
}

.popup-content {
  padding: 12px;
}

.empty-state {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-muted-foreground, #656d76);
}

.status-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
}

.status-label {
  color: var(--theme-muted-foreground, #656d76);
}

.status-value {
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
}

.value-success {
  color: #28a745;
}

.value-warning {
  color: #f59e0b;
}

.value-error {
  color: #dc3545;
}

.divider {
  height: 1px;
  background: var(--theme-border, #e1e4e8);
  margin: 12px 0;
}

.toggle-section {
  margin-bottom: 8px;
}

.toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.toggle-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
}

.toggle-switch {
  position: relative;
  display: inline-block;
  width: 36px;
  height: 20px;
}

.toggle-switch input {
  opacity: 0;
  width: 0;
  height: 0;
}

.toggle-slider {
  position: absolute;
  cursor: pointer;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: var(--theme-muted-foreground, #ccc);
  transition: 0.2s;
  border-radius: 20px;
}

.toggle-slider:before {
  position: absolute;
  content: "";
  height: 16px;
  width: 16px;
  left: 2px;
  bottom: 2px;
  background-color: white;
  transition: 0.2s;
  border-radius: 50%;
}

input:checked + .toggle-slider {
  background-color: var(--theme-accent, #0366d6);
}

input:disabled + .toggle-slider {
  opacity: 0.5;
  cursor: not-allowed;
}

input:checked + .toggle-slider:before {
  transform: translateX(16px);
}

.toggle-hint {
  font-size: 11px;
  color: var(--theme-muted-foreground, #656d76);
}

.reconnect-hint {
  font-size: 11px;
  color: #f59e0b;
  margin-top: 4px;
}

.install-section {
  margin-top: 12px;
}

.install-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  background: var(--theme-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.install-btn:hover {
  background: var(--theme-accent, #0366d6);
  border-color: var(--theme-accent, #0366d6);
  color: white;
}

.install-icon {
  font-size: 14px;
}
</style>

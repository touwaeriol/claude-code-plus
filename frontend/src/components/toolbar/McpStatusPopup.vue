<template>
  <div v-if="visible" class="mcp-popup-overlay" @click.self="close">
    <div class="mcp-popup">
      <div class="popup-header">
        <span class="popup-title">MCP Servers</span>
        <div class="popup-actions">
          <button class="close-btn" @click="close" title="Close">×</button>
        </div>
      </div>
      <div class="popup-content">
        <div v-if="!isConnected" class="empty-state">
          未连接
        </div>
        <div v-else-if="servers.length === 0" class="empty-state">
          No MCP servers configured
        </div>
        <div v-else class="server-list">
          <div v-for="server in servers" :key="server.name" class="server-item">
            <span class="status-dot" :class="getStatusClass(server.status)"></span>
            <span class="server-name">{{ server.name }}</span>
            <span class="server-status">{{ server.status }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
interface McpServerStatus {
  name: string
  status: string
}

const props = defineProps<{
  visible: boolean
  servers: McpServerStatus[]
  isConnected: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

function getStatusClass(status: string): string {
  switch (status) {
    case 'connected': return 'status-connected'
    case 'sdk': return 'status-sdk'
    case 'failed': return 'status-failed'
    default: return 'status-unknown'
  }
}

function close() {
  emit('close')
}
</script>

<style scoped>
.mcp-popup-overlay {
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

.mcp-popup {
  background: var(--theme-panel-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  min-width: 240px;
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
  padding: 8px;
  max-height: 300px;
  overflow-y: auto;
}

.empty-state {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-muted-foreground, #656d76);
}

.server-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.server-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--theme-background, #f6f8fa);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-connected {
  background: #28a745;
  box-shadow: 0 0 4px #28a745;
}

.status-sdk {
  background: #0366d6;
  box-shadow: 0 0 4px #0366d6;
}

.status-failed {
  background: #dc3545;
  box-shadow: 0 0 4px #dc3545;
}

.status-unknown {
  background: #6c757d;
}

.server-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.server-status {
  font-size: 11px;
  color: var(--theme-muted-foreground, #656d76);
}
</style>

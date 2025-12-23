<template>
  <div v-if="visible" class="mcp-popup-overlay" @click.self="close">
    <div class="mcp-popup">
      <div class="popup-header">
        <template v-if="selectedServer">
          <button class="back-btn" @click="goBack" title="Back">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M19 12H5M12 19l-7-7 7-7"/>
            </svg>
          </button>
          <span class="popup-title">{{ selectedServer }}</span>
        </template>
        <span v-else class="popup-title">MCP Servers</span>
        <div class="popup-actions">
          <button class="close-btn" @click="close" title="Close">×</button>
        </div>
      </div>
      <div class="popup-content">
        <!-- 未连接状态 -->
        <div v-if="!isConnected" class="empty-state">
          未连接
        </div>

        <!-- 工具列表视图 -->
        <template v-else-if="selectedServer">
          <div v-if="loadingTools" class="loading-state">
            加载中...
          </div>
          <div v-else-if="toolsError" class="error-state">
            <span>{{ toolsError }}</span>
            <button class="retry-btn" @click="selectServer({ name: selectedServer!, status: 'connected' })">重试</button>
          </div>
          <div v-else-if="tools.length === 0" class="empty-state">
            No tools available
            <button class="retry-btn" @click="selectServer({ name: selectedServer!, status: 'connected' })">刷新</button>
          </div>
          <div v-else class="tool-list">
            <div
              v-for="tool in tools"
              :key="tool.name"
              class="tool-item"
              :class="{ expanded: expandedTool === tool.name }"
              @click="toggleTool(tool.name)"
            >
              <div class="tool-header">
                <span class="tool-name">{{ tool.name }}</span>
                <svg
                  class="expand-icon"
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M6 9l6 6 6-6"/>
                </svg>
              </div>
              <div v-if="expandedTool === tool.name" class="tool-details">
                <!-- Full name -->
                <div class="tool-info-row">
                  <span class="info-label">Full name:</span>
                  <code class="tool-full-name">mcp__{{ selectedServer }}__{{ tool.name }}</code>
                </div>

                <!-- Description -->
                <div class="tool-info-row">
                  <span class="info-label">Description:</span>
                  <p class="tool-description">{{ tool.description || 'No description' }}</p>
                </div>

                <!-- Parameters -->
                <div class="tool-params-section">
                  <span class="info-label">Parameters:</span>
                  <div v-if="getToolParameters(tool).length > 0" class="params-list">
                    <div
                      v-for="param in getToolParameters(tool)"
                      :key="param.name"
                      class="param-item"
                    >
                      <span class="param-name">{{ param.name }}</span>
                      <span class="param-required" :class="{ required: param.required }">
                        ({{ param.required ? 'required' : 'optional' }})
                      </span>
                      <span class="param-type">: {{ param.type }}</span>
                      <span v-if="param.description" class="param-desc">
                        - {{ param.description }}
                      </span>
                      <span v-if="param.default !== undefined" class="param-default">
                        [default: {{ JSON.stringify(param.default) }}]
                      </span>
                    </div>
                  </div>
                  <div v-else class="no-params">No parameters defined</div>
                </div>
              </div>
            </div>
          </div>
        </template>

        <!-- 服务器列表视图 -->
        <template v-else>
          <div v-if="servers.length === 0" class="empty-state">
            No MCP servers configured
          </div>
          <div v-else class="server-list">
            <div
              v-for="server in servers"
              :key="server.name"
              class="server-item"
              :class="{ clickable: server.status === 'connected' }"
              @click="selectServer(server)"
            >
              <span class="status-dot" :class="getStatusClass(server.status)"></span>
              <span class="server-name">{{ server.name }}</span>
              <span class="server-status">{{ server.status }}</span>
              <!-- 重连按钮 -->
              <button
                class="reconnect-btn"
                :class="{ loading: reconnecting === server.name }"
                @click.stop="reconnectServer(server)"
                :disabled="reconnecting !== null"
                title="重连"
              >
                <svg
                  v-if="reconnecting !== server.name"
                  width="12"
                  height="12"
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <path d="M1 4v6h6M23 20v-6h-6"/>
                  <path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"/>
                </svg>
                <span v-else class="spinner"></span>
              </button>
              <svg
                v-if="server.status === 'connected'"
                class="arrow-icon"
                width="12"
                height="12"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
              >
                <path d="M9 18l6-6-6-6"/>
              </svg>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'

interface McpServerStatus {
  name: string
  status: string
}

interface McpToolParameter {
  type: string
  description?: string
  default?: any
  enum?: string[]
  minimum?: number
  maximum?: number
  minLength?: number
  maxLength?: number
  format?: string
}

interface McpToolInputSchema {
  type: string
  properties?: Record<string, McpToolParameter>
  required?: string[]
  additionalProperties?: boolean
}

interface McpToolInfo {
  name: string
  description: string
  inputSchema?: McpToolInputSchema
}

const props = defineProps<{
  visible: boolean
  servers: McpServerStatus[]
  isConnected: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
}>()

const sessionStore = useSessionStore()
const selectedServer = ref<string | null>(null)
const tools = ref<McpToolInfo[]>([])
const loadingTools = ref(false)
const expandedTool = ref<string | null>(null)
const toolsError = ref<string | null>(null)
const reconnecting = ref<string | null>(null)

// 重置状态当弹窗关闭
watch(() => props.visible, (visible) => {
  if (!visible) {
    selectedServer.value = null
    tools.value = []
    expandedTool.value = null
    toolsError.value = null
  }
})

function getStatusClass(status: string): string {
  switch (status) {
    case 'connected': return 'status-connected'
    case 'sdk': return 'status-sdk'
    case 'failed': return 'status-failed'
    default: return 'status-unknown'
  }
}

async function selectServer(server: McpServerStatus) {
  console.log('[McpStatusPopup] selectServer:', server.name, 'status:', server.status)

  // 允许 connected 状态的服务器查看工具
  if (server.status !== 'connected') {
    console.log('[McpStatusPopup] 服务器状态不是 connected，跳过')
    return
  }

  selectedServer.value = server.name
  loadingTools.value = true
  tools.value = []
  toolsError.value = null

  try {
    const session = sessionStore.currentTab?.session
    console.log('[McpStatusPopup] session:', session ? 'exists' : 'null', 'isConnected:', session?.isConnected)

    if (session?.isConnected) {
      console.log('[McpStatusPopup] 调用 getMcpTools:', server.name)
      const result = await session.getMcpTools(server.name)
      console.log('[McpStatusPopup] getMcpTools 结果:', result)
      tools.value = result.tools
    } else {
      toolsError.value = 'Session 未连接'
      console.warn('[McpStatusPopup] Session 未连接，无法获取工具')
    }
  } catch (err) {
    console.error('[McpStatusPopup] Failed to get tools:', err)
    toolsError.value = err instanceof Error ? err.message : '获取工具失败'
  } finally {
    loadingTools.value = false
  }
}

async function reconnectServer(server: McpServerStatus) {
  console.log('[McpStatusPopup] reconnectServer:', server.name)
  reconnecting.value = server.name

  try {
    const session = sessionStore.currentTab?.session
    if (!session?.isConnected) {
      console.warn('[McpStatusPopup] Session 未连接，无法重连 MCP')
      return
    }

    const result = await session.reconnectMcp(server.name)
    console.log('[McpStatusPopup] reconnectMcp 结果:', result)

    if (result.success) {
      // 重连成功后，如果当前选中的是这个服务器，刷新工具列表
      if (selectedServer.value === server.name) {
        await selectServer({ ...server, status: 'connected' })
      }
    }
  } catch (err) {
    console.error('[McpStatusPopup] Failed to reconnect:', err)
  } finally {
    reconnecting.value = null
  }
}

function goBack() {
  selectedServer.value = null
  tools.value = []
  expandedTool.value = null
}

function toggleTool(toolName: string) {
  expandedTool.value = expandedTool.value === toolName ? null : toolName
}

interface ParsedParam {
  name: string
  type: string
  required: boolean
  description?: string
  default?: any
}

function getToolParameters(tool: McpToolInfo): ParsedParam[] {
  if (!tool.inputSchema?.properties) return []

  const requiredSet = new Set(tool.inputSchema.required || [])

  return Object.entries(tool.inputSchema.properties)
    .map(([name, schema]) => ({
      name,
      type: schema.type || 'any',
      required: requiredSet.has(name),
      description: schema.description,
      default: schema.default
    }))
    .sort((a, b) => {
      // Required parameters first
      if (a.required !== b.required) return a.required ? -1 : 1
      return a.name.localeCompare(b.name)
    })
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
  min-width: 280px;
  max-width: 360px;
}

.popup-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
}

.popup-title {
  flex: 1;
  font-weight: 600;
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
}

.popup-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.back-btn {
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
}

.back-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
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
  max-height: 400px;
  overflow-y: auto;
}

.empty-state,
.loading-state,
.error-state {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-muted-foreground, #656d76);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.error-state {
  color: #dc3545;
}

.retry-btn {
  padding: 4px 12px;
  font-size: 11px;
  border: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-background, #f6f8fa);
  border-radius: 4px;
  cursor: pointer;
  color: var(--theme-foreground, #24292e);
  transition: background 0.15s;
}

.retry-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.08));
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
  transition: background 0.15s;
}

.server-item.clickable {
  cursor: pointer;
}

.server-item.clickable:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.08));
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

.arrow-icon {
  color: var(--theme-muted-foreground, #656d76);
  flex-shrink: 0;
}

.reconnect-btn {
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  color: var(--theme-muted-foreground, #656d76);
  transition: background 0.15s, color 0.15s;
  flex-shrink: 0;
}

.reconnect-btn:hover:not(:disabled) {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.08));
  color: var(--theme-foreground, #24292e);
}

.reconnect-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.reconnect-btn .spinner {
  width: 12px;
  height: 12px;
  border: 2px solid var(--theme-muted-foreground, #656d76);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* Tool list styles */
.tool-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.tool-item {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--theme-background, #f6f8fa);
  cursor: pointer;
  transition: background 0.15s;
}

.tool-item:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.08));
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tool-name {
  flex: 1;
  font-size: 12px;
  font-weight: 500;
  color: var(--theme-foreground, #24292e);
  font-family: monospace;
}

.expand-icon {
  color: var(--theme-muted-foreground, #656d76);
  flex-shrink: 0;
  transition: transform 0.15s;
}

.tool-item.expanded .expand-icon {
  transform: rotate(180deg);
}

.tool-details {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.tool-description {
  font-size: 11px;
  color: var(--theme-muted-foreground, #656d76);
  line-height: 1.5;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Tool info rows */
.tool-info-row {
  margin-bottom: 8px;
}

.info-label {
  font-size: 10px;
  font-weight: 600;
  color: var(--theme-muted-foreground, #656d76);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  display: block;
  margin-bottom: 2px;
}

.tool-full-name {
  font-size: 10px;
  color: var(--theme-foreground, #24292e);
  background: var(--theme-code-background, rgba(0, 0, 0, 0.05));
  padding: 2px 6px;
  border-radius: 3px;
  display: inline-block;
}

/* Parameters section */
.tool-params-section {
  margin-top: 8px;
}

.params-list {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-item {
  font-size: 11px;
  line-height: 1.4;
  padding: 4px 6px;
  background: var(--theme-code-background, rgba(0, 0, 0, 0.03));
  border-radius: 4px;
  border-left: 2px solid var(--theme-border, #e1e4e8);
}

.param-name {
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
  font-family: monospace;
}

.param-required {
  font-size: 10px;
  color: var(--theme-muted-foreground, #656d76);
}

.param-required.required {
  color: #d73a49;
  font-weight: 500;
}

.param-type {
  font-size: 10px;
  color: #0366d6;
  font-family: monospace;
}

.param-desc {
  color: var(--theme-muted-foreground, #656d76);
  display: block;
  margin-top: 2px;
  padding-left: 8px;
}

.param-default {
  font-size: 10px;
  color: #6f42c1;
  font-family: monospace;
  display: block;
  margin-top: 2px;
  padding-left: 8px;
}

.no-params {
  font-size: 11px;
  color: var(--theme-muted-foreground, #656d76);
  font-style: italic;
  margin-top: 4px;
}
</style>

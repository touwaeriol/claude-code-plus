
<template>
  <div
    class="chat-header"
  >
    <!-- 左侧：会话 Tab 列表 -->
    <SessionTabs
      :sessions="sessionTabList"
      :current-session-id="currentTabId"
      :can-close="true"
      :initializing="!settingsStore.settingsReady"
      @switch="handleSwitchTab"
      @close="handleCloseTab"
      @reorder="handleReorder"
      @toggle-list="emit('toggle-history')"
      @rename="handleRename"
    />

    <!-- 右侧：功能按钮 -->
    <div class="header-actions">
      <!-- 新建会话 -->
      <button
        class="new-session-btn"
        type="button"
        title="New Session"
        @click="handleNewSession"
      >
        <svg width="14" height="14" viewBox="0 0 14 14" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M7 1v12M1 7h12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
        </svg>
      </button>
      <!-- 会话操作下拉菜单 -->
      <div class="session-menu-trigger" @click="toggleSessionMenu">
        <button
          class="icon-btn"
          type="button"
          :class="{ loading: isReconnecting }"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 12a9 9 0 1 1-3-6.7"/>
            <polyline points="21 3 21 9 15 9"/>
          </svg>
        </button>
        <svg class="dropdown-arrow" width="8" height="8" viewBox="0 0 8 8" fill="currentColor">
          <path d="M1 2.5L4 5.5L7 2.5" stroke="currentColor" stroke-width="1.5" fill="none" stroke-linecap="round"/>
        </svg>
        <!-- 下拉菜单 -->
        <div v-if="showSessionMenu" class="session-menu">
          <div
            class="session-menu-item"
            :class="{ disabled: !canReconnect }"
            @click.stop="canReconnect && handleReconnect()"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-3-6.7"/>
              <polyline points="21 3 21 9 15 9"/>
            </svg>
            <span>{{ t('session.reloadSession') }}</span>
          </div>
          <div
            class="session-menu-item danger"
            @click.stop="handleResetSession"
          >
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M3 6h18"/>
              <path d="M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"/>
              <path d="M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"/>
            </svg>
            <span>{{ t('session.resetSession') }}</span>
          </div>
        </div>
      </div>
      <!-- 历史会话 -->
      <button
        class="icon-btn"
        type="button"
        title="History"
        @click="emit('toggle-history')"
      >
        📋
      </button>

      <button
        class="icon-btn server-btn"
        type="button"
        title="MCP Servers"
        @click="showMcpStatus = true"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="2" y="2" width="20" height="8" rx="2"/>
          <rect x="2" y="14" width="20" height="8" rx="2"/>
          <circle cx="6" cy="6" r="1" fill="currentColor"/>
          <circle cx="6" cy="18" r="1" fill="currentColor"/>
        </svg>
      </button>
      <ThemeSwitcher />
      <LanguageSwitcher />
    </div>

    <!-- MCP 状态弹窗 -->
    <McpStatusPopup
      :visible="showMcpStatus"
      :servers="currentMcpServers"
      :is-connected="isCurrentConnected"
      @close="handleCloseMcpPopup"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useToastStore } from '@/stores/toastStore'
import { useSettingsStore } from '@/stores/settingsStore'
import { ConnectionStatus } from '@/types/display'
import { BackendTypes } from '@/types/backend'

import SessionTabs, { type SessionTabInfo } from './SessionTabs.vue'

import ThemeSwitcher from '@/components/toolbar/ThemeSwitcher.vue'
import LanguageSwitcher from '@/components/toolbar/LanguageSwitcher.vue'
import McpStatusPopup from '@/components/toolbar/McpStatusPopup.vue'
import { useI18n } from '@/composables/useI18n'

// MCP 状态弹窗
const showMcpStatus = ref(false)
const fetchedMcpServers = ref<Array<{ name: string; status: string }> | null>(null)
const mcpRefreshIntervalMs = 5000
let mcpRefreshTimer: ReturnType<typeof setInterval> | null = null

// i18n
const { t } = useI18n()


const isReconnecting = ref(false)

// 会话操作菜单
const showSessionMenu = ref(false)

const canReconnect = computed(() => {
  const tab = sessionStore.currentTab
  if (!tab) return false
  if (isReconnecting.value) return false
  return !tab.isConnecting.value
})

function toggleSessionMenu() {
  showSessionMenu.value = !showSessionMenu.value
}

function closeSessionMenu() {
  showSessionMenu.value = false
}

async function handleReconnect() {
  closeSessionMenu()
  const tab = sessionStore.currentTab
  if (!tab || isReconnecting.value) return
  isReconnecting.value = true
  try {
    // 使用 reloadSession：重置 UI + 恢复原会话 + 加载历史
    await tab.reloadSession()
  } finally {
    isReconnecting.value = false
  }
}

async function handleResetSession() {
  closeSessionMenu()
  // IDEA 插件环境不支持 confirm 弹窗，直接执行重置
  await sessionStore.resetCurrentTab()
}

// 点击外部关闭菜单
function handleClickOutside(event: MouseEvent) {
  const target = event.target as HTMLElement
  if (!target.closest('.session-menu-trigger')) {
    closeSessionMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  stopMcpRefresh()
})

// 打开弹窗时调用 getMcpStatus API
async function refreshMcpStatus() {
  const session = sessionStore.currentTab?.session
  if (!session?.isConnected) {
    fetchedMcpServers.value = []
    return
  }

  try {
    const result = await session.getMcpStatus()
    fetchedMcpServers.value = result.servers
    console.log('[MCP] getMcpStatus result:', result)
  } catch (err) {
    console.error('[ChatHeader] getMcpStatus failed:', err)
  }
}

function startMcpRefresh() {
  if (mcpRefreshTimer) return
  mcpRefreshTimer = setInterval(() => {
    void refreshMcpStatus()
  }, mcpRefreshIntervalMs)
}

function stopMcpRefresh() {
  if (!mcpRefreshTimer) return
  clearInterval(mcpRefreshTimer)
  mcpRefreshTimer = null
}

watch(showMcpStatus, (visible) => {
  if (visible) {
    fetchedMcpServers.value = []
    void refreshMcpStatus()
    startMcpRefresh()
  } else {
    stopMcpRefresh()
  }
})

function handleCloseMcpPopup() {
  showMcpStatus.value = false
  fetchedMcpServers.value = null
}

const emit = defineEmits<{
  (e: 'toggle-history'): void
}>()

const sessionStore = useSessionStore()
const toastStore = useToastStore()
const settingsStore = useSettingsStore()

const activeTabs = computed(() => sessionStore.activeTabs)
const currentTabId = computed(() => sessionStore.currentTabId)

watch(currentTabId, () => {
  if (showMcpStatus.value) {
    fetchedMcpServers.value = []
    void refreshMcpStatus()
  }
})

// 当前 Tab 的 MCP 服务器状态（优先使用 API 获取的数据）
const currentMcpServers = computed(() => {
  // null 表示还没获取过，空数组表示获取到了但没有服务器
  if (fetchedMcpServers.value !== null) {
    return fetchedMcpServers.value
  }
  return sessionStore.currentTab?.mcpServers.value ?? []
})
const isCurrentConnected = computed(() => sessionStore.currentTab?.connectionState.status === ConnectionStatus.CONNECTED)

// 转换为 SessionTabInfo 格式（添加 backendType）
const sessionTabList = computed<SessionTabInfo[]>(() => {
  return activeTabs.value.map(tab => ({
    id: tab.tabId,
    name: tab.name.value,
    sessionId: tab.sessionId.value,
    resumeFromSessionId: (tab as any).resumeFromSessionId?.value ?? null,
    isGenerating: tab.isGenerating.value,
    isConnected: tab.connectionState.status === ConnectionStatus.CONNECTED,
    connectionStatus: tab.connectionState.status,
    error: tab.connectionState.lastError,
    backendType: tab.backendType?.value ?? BackendTypes.CLAUDE, // 添加后端类型
  }))
})

async function handleSwitchTab(tabId: string) {
  if (tabId === currentTabId.value) return
  await sessionStore.switchTab(tabId)
}

async function handleCloseTab(tabId: string) {
  // 如果只有一个会话，重置为空的新会话（无视生成中状态）
  if (activeTabs.value.length <= 1) {
    await sessionStore.resetCurrentTab()
    return
  }

  // 如果关闭的是当前会话，先切换到其他会话
  if (tabId === currentTabId.value) {
    const otherTab = activeTabs.value.find(tab => tab.tabId !== tabId)
    if (otherTab) {
      await sessionStore.switchTab(otherTab.tabId)
    }
  }

  // 关闭 Tab
  await sessionStore.closeTab(tabId)
}

async function handleNewSession() {
  const defaultBackend = settingsStore.settings.defaultBackendType || BackendTypes.CLAUDE
  // 每次都创建新的 Tab
  await sessionStore.createTab(undefined, { backendType: defaultBackend })
}

function handleReorder(newOrder: string[]) {
  sessionStore.updateTabOrder(newOrder)
}

function handleRename(tabId: string, newName: string) {
  const tab = activeTabs.value.find(t => t.tabId === tabId)
  if (tab) {
    // 1. 立即更新 UI
    tab.rename(newName)
    // 2. 直接发送 /rename 命令到后端（通过 Tab 实例，绕过队列）
    if (tab.session?.isConnected) {
      tab.sendTextMessageDirect(`/rename ${newName}`)
        .then(() => {
          toastStore.success(`Rename success: "${newName}"`)
        })
        .catch((err: Error) => {
          console.error('[ChatHeader] 发送 /rename 命令失败:', err)
          toastStore.error('Rename failed')
        })
    } else {
      // 未连接时，UI 已更新，显示成功提示
      toastStore.success(`Rename success: "${newName}"`)
    }
  }
}
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 3px 6px;
  height: 32px;
  border-bottom: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #f6f8fa);
  box-sizing: border-box;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.connection-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  border: 1px solid transparent;
  background: rgba(0, 0, 0, 0.03);
  color: var(--theme-foreground, #24292e);
}

.pill-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  box-shadow: 0 0 6px currentColor;
}

.status-connected {
  background: rgba(40, 167, 69, 0.12);
  border-color: rgba(40, 167, 69, 0.3);
  color: #28a745;
}

.status-connecting {
  background: rgba(255, 193, 7, 0.14);
  border-color: rgba(255, 193, 7, 0.35);
  color: #d39e00;
}

.status-disconnected {
  background: rgba(220, 53, 69, 0.12);
  border-color: rgba(220, 53, 69, 0.3);
  color: #dc3545;
}

.icon-btn {
  width: 24px;
  height: 24px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--theme-foreground, #24292e);
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.1s ease;
}

.icon-tooltip-wrapper {
  display: inline-flex;
}

.icon-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-border, #e1e4e8);
}

.icon-btn:active {
  transform: translateY(1px);
}

.icon-btn:disabled {
  cursor: not-allowed;
  opacity: 0.4;
}

.icon-btn.loading svg,
.icon-btn.loading polyline {
  animation: icon-spin 0.9s linear infinite;
}

.icon-btn.primary {
  background: var(--theme-accent, #0366d6);
  color: #ffffff;
}

.icon-btn.primary:hover {
  background: var(--theme-accent-hover, #0256c2);
  border-color: transparent;
}

.new-session-btn {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: 1px solid var(--theme-border, #d0d7de);
  background: var(--theme-background, #ffffff);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--theme-muted-foreground, #656d76);
  transition: all 0.15s ease;
}

.new-session-btn:hover {
  background: var(--theme-accent, #0366d6);
  border-color: var(--theme-accent, #0366d6);
  color: #ffffff;
}

.new-session-btn:active {
  transform: scale(0.95);
}

.new-session-btn svg {
  flex-shrink: 0;
}

@keyframes icon-spin {
  to {
    transform: rotate(360deg);
  }
}

/* 会话操作菜单 */
.session-menu-trigger {
  position: relative;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 2px 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.session-menu-trigger:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
}

.session-menu-trigger .dropdown-arrow {
  color: var(--theme-muted-foreground, #656d76);
}

.session-menu {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  min-width: 160px;
  background: var(--theme-card-background, #ffffff);
  border: 1px solid var(--theme-border, #d0d7de);
  border-radius: 8px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  z-index: 1000;
  overflow: hidden;
  animation: menuFadeIn 0.15s ease;
}

.session-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  cursor: pointer;
  transition: background 0.1s ease;
}

.session-menu-item:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
}

.session-menu-item.disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.session-menu-item.disabled:hover {
  background: transparent;
}

.session-menu-item.danger {
  color: var(--theme-error, #dc3545);
}

.session-menu-item.danger:hover {
  background: rgba(220, 53, 69, 0.08);
}
</style>

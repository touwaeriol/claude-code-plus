<template>
  <div
    class="chat-header"
  >
    <!-- 左侧：会话 Tab 列表 -->
    <SessionTabs
      :sessions="sessionTabList"
      :current-session-id="currentTabId"
      :can-close="true"
      @switch="handleSwitchTab"
      @close="handleCloseTab"
      @reorder="handleReorder"
      @toggle-list="emit('toggle-history')"
      @rename="handleRename"
    />

    <!-- 右侧：功能按钮 -->
    <div class="header-actions">
      <button
        class="icon-btn"
        type="button"
        title="History"
        @click="emit('toggle-history')"
      >
        📋
      </button>
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
      <ThemeSwitcher />
      <LanguageSwitcher />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useToastStore } from '@/stores/toastStore'
import { ConnectionStatus } from '@/types/display'
import SessionTabs, { type SessionTabInfo } from './SessionTabs.vue'
import ThemeSwitcher from '@/components/toolbar/ThemeSwitcher.vue'
import LanguageSwitcher from '@/components/toolbar/LanguageSwitcher.vue'

// No props needed

const emit = defineEmits<{
  (e: 'toggle-history'): void
}>()

const sessionStore = useSessionStore()
const toastStore = useToastStore()

const activeTabs = computed(() => sessionStore.activeTabs)
const currentTabId = computed(() => sessionStore.currentTabId)

// 转换为 SessionTabInfo 格式
const sessionTabList = computed<SessionTabInfo[]>(() => {
  return activeTabs.value.map(tab => ({
    id: tab.tabId,
    name: tab.name.value,
    sessionId: tab.sessionId.value,
    resumeFromSessionId: (tab as any).resumeFromSessionId?.value ?? null,
    isGenerating: tab.isGenerating.value,
    isConnected: tab.connectionState.status === ConnectionStatus.CONNECTED,
    connectionStatus: tab.connectionState.status,
    error: tab.connectionState.lastError
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
  // 如果当前正在生成中或正在连接中，新建一个新的 Tab
  // 如果没有正在生成中且已完成连接，直接清空当前 Tab 变成空的新会话
  // 注意：直接从 Tab 实例读取状态，避免 shallowRef 响应性问题
  const isCurrentGenerating = sessionStore.currentTab?.isGenerating.value ?? false
  const isCurrentConnecting = sessionStore.currentTab?.connectionState.status === ConnectionStatus.CONNECTING
  if (isCurrentGenerating || isCurrentConnecting) {
    await sessionStore.createTab()
  } else {
    await sessionStore.resetCurrentTab()
  }
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

.icon-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-border, #e1e4e8);
}

.icon-btn:active {
  transform: translateY(1px);
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
</style>

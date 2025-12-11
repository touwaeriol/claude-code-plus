<template>
  <div
    class="chat-header"
  >
    <!-- 左侧：会话 Tab 列表 -->
    <SessionTabs
      :sessions="sessionTabList"
      :current-session-id="currentTabId"
      :can-close="activeTabs.length > 1"
      @switch="handleSwitchTab"
      @close="handleCloseTab"
      @reorder="handleReorder"
      @toggle-list="emit('toggle-history')"
    />

    <!-- 右侧：功能按钮 -->
    <div class="header-actions">
      <button
        class="icon-btn"
        type="button"
        title="历史会话"
        @click="emit('toggle-history')"
      >
        📋
      </button>
      <button
        class="icon-btn primary"
        type="button"
        title="新建会话"
        @click="handleNewSession"
      >
        ➕
      </button>
      <ThemeSwitcher />
      <LanguageSwitcher />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { ConnectionStatus } from '@/types/display'
import SessionTabs, { type SessionTabInfo } from './SessionTabs.vue'
import ThemeSwitcher from '@/components/toolbar/ThemeSwitcher.vue'
import LanguageSwitcher from '@/components/toolbar/LanguageSwitcher.vue'

// No props needed

const emit = defineEmits<{
  (e: 'toggle-history'): void
}>()

const sessionStore = useSessionStore()

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
  // 如果只有一个会话，不允许关闭
  if (activeTabs.value.length <= 1) return

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
  await sessionStore.createTab()
}

function handleReorder(newOrder: string[]) {
  sessionStore.updateTabOrder(newOrder)
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
</style>

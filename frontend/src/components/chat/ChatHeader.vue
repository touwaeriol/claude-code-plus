<template>
  <div
    class="chat-header"
    :class="{ 'theme-dark': isDark }"
  >
    <!-- 左侧：会话 Tab 列表 -->
    <SessionTabs
      :sessions="sessionTabList"
      :current-session-id="currentSessionId"
      :can-close="activeTabs.length > 1"
      @switch="handleSwitchSession"
      @close="handleCloseSession"
      @reorder="handleReorder"
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
import SessionTabs, { type SessionTabInfo } from './SessionTabs.vue'
import ThemeSwitcher from '@/components/toolbar/ThemeSwitcher.vue'
import LanguageSwitcher from '@/components/toolbar/LanguageSwitcher.vue'

withDefaults(defineProps<{
  isDark?: boolean
}>(), {
  isDark: false
})

const emit = defineEmits<{
  (e: 'toggle-history'): void
}>()

const sessionStore = useSessionStore()

const activeTabs = computed(() => sessionStore.activeTabs || [])
const currentSessionId = computed(() => sessionStore.currentSessionId)

// 转换为 SessionTabInfo 格式
const sessionTabList = computed<SessionTabInfo[]>(() => {
  return activeTabs.value.map(tab => ({
    id: tab.id,
    name: tab.name,
    isGenerating: tab.isGenerating,
    isConnected: tab.connectionStatus === 'CONNECTED'
  }))
})

async function handleSwitchSession(sessionId: string) {
  if (sessionId === currentSessionId.value) return
  await sessionStore.switchSession(sessionId)
}

async function handleCloseSession(sessionId: string) {
  // 如果只有一个会话，不允许关闭
  if (activeTabs.value.length <= 1) return

  // 如果关闭的是当前会话，先切换到其他会话
  if (sessionId === currentSessionId.value) {
    const otherSession = activeTabs.value.find(tab => tab.id !== sessionId)
    if (otherSession) {
      await sessionStore.switchSession(otherSession.id)
    }
  }

  // 删除会话
  await sessionStore.deleteSession(sessionId)
}

async function handleNewSession() {
  if (typeof sessionStore.startNewSession === 'function') {
    await sessionStore.startNewSession()
  } else {
    await sessionStore.createSession()
  }
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
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-panel-background, #f6f8fa);
  box-sizing: border-box;
}

.theme-dark.chat-header {
  background: var(--ide-panel-background, #1f2428);
  border-color: var(--ide-border, #30363d);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
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
  color: var(--ide-foreground, #24292e);
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.1s ease;
}

.icon-btn:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--ide-border, #e1e4e8);
}

.icon-btn:active {
  transform: translateY(1px);
}

.icon-btn.primary {
  background: var(--ide-accent, #0366d6);
  color: #ffffff;
}

.icon-btn.primary:hover {
  background: var(--ide-accent-hover, #0256c2);
  border-color: transparent;
}

.theme-dark .icon-btn {
  color: var(--ide-foreground, #e6edf3);
}

.theme-dark .icon-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  border-color: var(--ide-border, #30363d);
}

.theme-dark .icon-btn.primary {
  background: var(--ide-accent, #1f6feb);
}

.theme-dark .icon-btn.primary:hover {
  background: var(--ide-accent-hover, #388bfd);
}
</style>

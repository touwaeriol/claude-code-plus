<template>
  <Teleport to="body">
    <Transition name="session-overlay">
      <div
        v-if="visible"
        class="session-overlay"
        :class="{ 'theme-dark': isDark }"
      >
        <div
          class="overlay-backdrop"
          @click="handleClose"
        />

        <div class="overlay-panel">
          <div class="overlay-header">
            <div class="overlay-title-group">
              <h3>会话历史</h3>
              <p>{{ sessions.length }} 个会话</p>
            </div>
            <div class="overlay-actions">
              <button
                class="overlay-btn primary"
                type="button"
                @click="handleNewSession"
              >
                新建会话
              </button>
              <button
                class="overlay-btn ghost"
                type="button"
                aria-label="关闭"
                @click="handleClose"
              >
                ✕
              </button>
            </div>
          </div>

          <div class="overlay-body">
            <div
              v-if="loading"
              class="state-block"
            >
              <span class="spinner" />
              <span>加载中...</span>
            </div>

            <div
              v-else-if="sessions.length === 0"
              class="state-block empty"
            >
              <span class="empty-icon">📭</span>
              <span>暂无历史会话</span>
            </div>

            <div
              v-else
              class="session-list"
            >
              <button
                v-for="session in sessions"
                :key="session.id"
                type="button"
                class="session-item"
                :class="{ active: session.id === currentSessionId }"
                @click="handleSelect(session.id)"
              >
                <div class="session-item-main">
                  <div class="session-name">
                    <span>{{ session.name || '未命名会话' }}</span>
                    <span
                      v-if="session.isGenerating"
                      class="session-dot"
                      title="生成中"
                    />
                  </div>
                  <div class="session-meta">
                    <span>{{ formatRelativeTime(session.timestamp) }}</span>
                    <span>·</span>
                    <span>{{ session.messageCount }} 条消息</span>
                  </div>
                </div>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onBeforeUnmount } from 'vue'

interface SessionListItem {
  id: string
  name: string
  timestamp: number
  messageCount: number
  isGenerating?: boolean
}

interface Props {
  visible: boolean
  sessions: SessionListItem[]
  currentSessionId?: string | null
  loading?: boolean
  isDark?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  isDark: false,
  currentSessionId: null
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select-session', sessionId: string): void
  (e: 'new-session'): void
}>()

function handleClose() {
  emit('close')
}

function handleSelect(sessionId: string) {
  emit('select-session', sessionId)
}

function handleNewSession() {
  emit('new-session')
}

function formatRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`

  return new Date(timestamp).toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric'
  })
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) {
    emit('close')
  }
}

watch(
  () => props.visible,
  visible => {
    if (visible) {
      window.addEventListener('keydown', handleKeydown)
    } else {
      window.removeEventListener('keydown', handleKeydown)
    }
  },
  { immediate: true }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.session-overlay {
  position: fixed;
  inset: 0;
  z-index: 1300;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}

.overlay-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
}

.overlay-panel {
  position: absolute;
  top: 60px;
  right: 32px;
  width: 360px;
  max-height: calc(100vh - 120px);
  border-radius: 16px;
  background: var(--ide-panel-background, #ffffff);
  border: 1px solid var(--ide-border, rgba(0, 0, 0, 0.08));
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.theme-dark .overlay-panel {
  background: var(--ide-panel-background, #161b22);
  border-color: var(--ide-border, #30363d);
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.6);
}

.overlay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--ide-border, rgba(0, 0, 0, 0.08));
}

.overlay-title-group h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-foreground, #111);
}

.overlay-title-group p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
}

.overlay-actions {
  display: flex;
  gap: 8px;
}

.overlay-btn {
  border-radius: 999px;
  border: 1px solid transparent;
  padding: 6px 14px;
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.overlay-btn.primary {
  background: var(--ide-accent, #0366d6);
  color: #fff;
}

.overlay-btn.primary:hover {
  background: var(--ide-accent-hover, #0256c2);
}

.overlay-btn.ghost {
  background: transparent;
  color: var(--ide-foreground, #333);
  border-color: var(--ide-border, rgba(0, 0, 0, 0.1));
}

.overlay-btn.ghost:hover {
  background: rgba(0, 0, 0, 0.04);
}

.theme-dark .overlay-btn.ghost {
  color: var(--ide-foreground, #e6edf3);
  border-color: rgba(255, 255, 255, 0.15);
}

.theme-dark .overlay-btn.ghost:hover {
  background: rgba(255, 255, 255, 0.08);
}

.overlay-body {
  padding: 12px 0 12px 12px;
  overflow: hidden;
  flex: 1;
}

.state-block {
  min-height: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.5));
}

.state-block .spinner {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--ide-border, rgba(0, 0, 0, 0.15));
  border-top-color: var(--ide-accent, #0366d6);
  animation: spin 0.8s linear infinite;
}

.state-block.empty .empty-icon {
  font-size: 40px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-right: 12px;
  max-height: calc(100% - 12px);
  overflow-y: auto;
}

.session-item {
  display: flex;
  width: 100%;
  border: 1px solid transparent;
  border-radius: 12px;
  padding: 6px 8px;
  background: transparent;
  cursor: pointer;
  text-align: left;
  transition: background 0.15s ease, border-color 0.15s ease;
  color: inherit;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.04);
}

.session-item.active {
  border-color: var(--ide-accent, #0366d6);
  background: rgba(3, 102, 214, 0.08);
}

.theme-dark .session-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

.theme-dark .session-item.active {
  background: rgba(65, 132, 228, 0.18);
  border-color: var(--ide-accent, #58a6ff);
}

.session-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.session-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ide-foreground, #111);
}

.theme-dark .session-name {
  color: var(--ide-foreground, #e6edf3);
}

.session-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ide-success, #1a7f37);
}

.session-meta {
  font-size: 12px;
  color: var(--ide-secondary-foreground, rgba(0, 0, 0, 0.6));
  display: flex;
  align-items: center;
  gap: 6px;
}

.theme-dark .session-meta {
  color: rgba(255, 255, 255, 0.6);
}

.session-overlay-enter-active,
.session-overlay-leave-active {
  transition: opacity 0.2s ease;
}

.session-overlay-enter-from,
.session-overlay-leave-to {
  opacity: 0;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>



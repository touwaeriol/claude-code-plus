<template>
  <Teleport to="body">
    <Transition name="session-overlay">
      <div
        v-if="visible"
        class="session-overlay"
      >
        <div
          class="overlay-backdrop"
          @click="handleClose"
        />

        <div class="overlay-panel">
          <div class="overlay-header">
            <div class="overlay-title-group">
              <h3>{{ $t('session.history') }}</h3>
              <p>{{ sessions.length }} {{ $t('session.sessionCount') }}</p>
            </div>
            <div class="overlay-actions">
              <button
                class="overlay-btn ghost"
                type="button"
                :aria-label="$t('common.close')"
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
              <span>{{ $t('common.loading') }}</span>
            </div>

            <div
              v-else-if="sessions.length === 0"
              class="state-block empty"
            >
              <span class="empty-icon">📭</span>
              <span>{{ $t('session.noHistory') }}</span>
            </div>

            <div
              v-else
              class="session-list"
            >
              <!-- 激活会话分组 -->
              <div class="session-group">
                <div class="session-group-header">{{ $t('session.active') }}</div>
                <div
                  v-if="activeSessions.length === 0"
                  class="session-group-empty"
                >
                  {{ $t('session.noActive') }}
                </div>
                <button
                  v-for="session in activeSessions"
                  :key="session.id"
                  type="button"
                  class="session-item"
                  :class="{ active: session.id === currentSessionId }"
                  @click="handleSelect(session.id)"
                >
                  <span class="session-status-icon active">🟢</span>
                  <div class="session-item-main">
                    <div class="session-name">
                      <span>{{ session.name || $t('session.unnamed') }}</span>
                      <span
                        v-if="session.id === currentSessionId"
                        class="session-current-mark"
                      >✓</span>
                    </div>
                    <div class="session-meta">
                      <span>{{ formatRelativeTime(session.timestamp) }}</span>
                      <span>·</span>
                      <span>{{ session.messageCount }} {{ $t('session.messages') }}</span>
                    </div>
                  </div>
                </button>
              </div>

              <!-- 历史会话分组 -->
              <div class="session-group">
                <div class="session-group-header">{{ $t('session.historySection') }}</div>
                <div
                  v-if="historySessions.length === 0"
                  class="session-group-empty"
                >
                  {{ $t('session.noHistory') }}
                </div>
                <button
                  v-for="session in historySessions"
                  :key="session.id"
                  type="button"
                  class="session-item"
                  :class="{ active: session.id === currentSessionId }"
                  @click="handleSelect(session.id)"
                >
                  <span class="session-status-icon history">📝</span>
                  <div class="session-item-main">
                    <div class="session-name">
                      <span>{{ session.name || $t('session.unnamed') }}</span>
                    </div>
                    <div class="session-meta">
                      <span>{{ formatRelativeTime(session.timestamp) }}</span>
                      <span>·</span>
                      <span>{{ session.messageCount }} {{ $t('session.messages') }}</span>
                    </div>
                  </div>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onBeforeUnmount, computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface SessionListItem {
  id: string
  name: string
  timestamp: number
  messageCount: number
  isGenerating?: boolean
  isConnected?: boolean
}

interface Props {
  visible: boolean
  sessions: SessionListItem[]
  currentSessionId?: string | null
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  currentSessionId: null
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select-session', sessionId: string): void
}>()

// 激活的会话（已连接）
const activeSessions = computed(() =>
  props.sessions.filter(s => s.isConnected)
)

// 历史会话（未连接）
const historySessions = computed(() =>
  props.sessions.filter(s => !s.isConnected)
)

function handleClose() {
  emit('close')
}

function handleSelect(sessionId: string) {
  emit('select-session', sessionId)
}

function formatRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return t('time.justNow')
  if (minutes < 60) return t('time.minutesAgo', { n: minutes })
  if (hours < 24) return t('time.hoursAgo', { n: hours })
  if (days < 7) return t('time.daysAgo', { n: days })

  return new Date(timestamp).toLocaleDateString(undefined, {
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
  background: var(--theme-panel-background, #ffffff);
  border: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
  box-shadow: 0 30px 60px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.overlay-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
}

.overlay-title-group h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-foreground, #111);
}

.overlay-title-group p {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.6));
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
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.overlay-btn.primary:hover {
  background: var(--theme-accent-hover, #0256c2);
}

.overlay-btn.ghost {
  background: transparent;
  color: var(--theme-foreground, #333);
  border-color: var(--theme-border, rgba(0, 0, 0, 0.1));
}

.overlay-btn.ghost:hover {
  background: rgba(0, 0, 0, 0.04);
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
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
}

.state-block .spinner {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--theme-border, rgba(0, 0, 0, 0.15));
  border-top-color: var(--theme-accent, #0366d6);
  animation: spin 0.8s linear infinite;
}

.state-block.empty .empty-icon {
  font-size: 40px;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 12px;
  max-height: calc(100% - 12px);
  overflow-y: auto;
}

.session-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.session-group-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  padding: 0 8px 4px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
  margin-bottom: 4px;
}

.session-group-empty {
  font-size: 12px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.4));
  padding: 8px;
  text-align: center;
}

.session-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
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
  border-color: var(--theme-accent, #0366d6);
  background: rgba(3, 102, 214, 0.08);
}

.session-status-icon {
  font-size: 14px;
  line-height: 1.5;
  flex-shrink: 0;
}

.session-item-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.session-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 500;
  color: var(--theme-foreground, #111);
}

.session-name span:first-child {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-current-mark {
  color: var(--theme-accent, #0366d6);
  font-weight: 600;
  flex-shrink: 0;
}

.session-meta {
  font-size: 12px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.6));
  display: flex;
  align-items: center;
  gap: 6px;
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



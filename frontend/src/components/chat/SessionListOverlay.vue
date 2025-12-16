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
            <h3 class="overlay-title">{{ $t('session.history') }} <span class="session-count">({{ historySessions.length }})</span></h3>
            <button
              class="close-btn"
              type="button"
              :aria-label="$t('common.close')"
              @click="handleClose"
            >✕</button>
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
              ref="sessionListRef"
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
                  :title="session.id"
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
                <div
                  v-for="session in historySessions"
                  :key="session.id"
                  class="session-item-wrapper"
                >
                  <button
                    type="button"
                    class="session-item"
                    :title="session.id"
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
                  <div class="session-item-actions">
                    <button
                      type="button"
                      class="item-action-btn copy-btn"
                      :title="$t('common.copySessionId')"
                      @click.stop="copySessionId(session.id)"
                    >
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                        <rect x="9" y="9" width="13" height="13" rx="2" ry="2"/>
                        <path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/>
                      </svg>
                    </button>
                    <button
                      type="button"
                      class="item-action-btn delete-btn"
                      :title="$t('common.delete')"
                      @click.stop="handleDeleteSession(session.id)"
                    >
                      <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                        <polyline points="3 6 5 6 21 6"/>
                        <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
                      </svg>
                    </button>
                  </div>
                </div>
              </div>

              <div class="session-list-footer">
                <span v-if="loadingMore" class="loading-text">
                  <span class="footer-spinner" />
                  {{ $t('common.loading') }}
                </span>
                <!-- 没有滚动条时显示按钮，让用户手动加载 -->
                <button
                  v-else-if="hasMore && !canScroll"
                  type="button"
                  class="load-more-btn"
                  @click="emit('load-more')"
                >
                  {{ $t('session.loadMore') }}
                </button>
                <!-- 有滚动条时显示提示文字 -->
                <span v-else-if="hasMore && canScroll" class="scroll-hint">
                  {{ $t('session.scrollToLoadMore') }}
                </span>
                <span v-else class="no-more-text">{{ $t('common.noMore') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { watch, onBeforeUnmount, computed, ref, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

interface SessionListItem {
  id: string
  name: string
  timestamp: number
  messageCount: number
  isGenerating?: boolean
  isConnected?: boolean
   isActive?: boolean
}

interface Props {
  visible: boolean
  sessions: SessionListItem[]
  currentSessionId?: string | null
  loading?: boolean
  loadingMore?: boolean
  hasMore?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  loadingMore: false,
  hasMore: true,
  currentSessionId: null
})

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'select-session', sessionId: string): void
  (e: 'load-more'): void
  (e: 'delete-session', sessionId: string): void
}>()

// 激活的会话（已连接）
const activeSessions = computed(() =>
  props.sessions.filter(s => s.isActive || s.isConnected)
)

const activeIds = computed(() => new Set(activeSessions.value.map(s => s.id)))

// 历史会话（未连接）
const historySessions = computed(() =>
  props.sessions.filter(s => !activeIds.value.has(s.id) && s.id !== props.currentSessionId)
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

const sessionListRef = ref<HTMLElement | null>(null)
const canScroll = ref(false)

function checkCanScroll() {
  const el = sessionListRef.value
  if (!el) {
    canScroll.value = false
    return
  }
  // 如果 scrollHeight > clientHeight，说明有滚动条
  canScroll.value = el.scrollHeight > el.clientHeight + 10
}

function handleScroll() {
  if (!props.hasMore || props.loading || props.loadingMore) return
  const el = sessionListRef.value
  if (!el) return
  const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  if (distanceToBottom < 160) {
    emit('load-more')
  }
}

watch(
  () => props.visible,
  visible => {
    if (visible) {
      window.addEventListener('keydown', handleKeydown)
      nextTick(() => {
        sessionListRef.value?.addEventListener('scroll', handleScroll, { passive: true })
        checkCanScroll()
      })
    } else {
      window.removeEventListener('keydown', handleKeydown)
      sessionListRef.value?.removeEventListener('scroll', handleScroll)
    }
  },
  { immediate: true }
)

// 监听 sessions 变化，重新检测是否可滚动
watch(
  () => props.sessions.length,
  () => {
    nextTick(checkCanScroll)
  }
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  sessionListRef.value?.removeEventListener('scroll', handleScroll)
})

async function copySessionId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
  } catch (error) {
    console.error('Failed to copy sessionId:', error)
  }
}

function handleDeleteSession(sessionId: string) {
  emit('delete-session', sessionId)
}
</script>

<style scoped>
.session-overlay {
  position: fixed;
  inset: 0;
  z-index: 1300;
  font-family: var(--theme-font-family);
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
  max-height: calc(100vh - 100px);
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
  padding: 10px 16px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
}

.overlay-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-foreground, #111);
}

.session-count {
  font-weight: 400;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  margin-left: 4px;
}

.close-btn {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
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
  display: flex;
  flex-direction: column;
  min-height: 0; /* 允许子元素正确滚动 */
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
  gap: 12px;
  padding-right: 12px;
  flex: 1;
  min-height: 0; /* 保证 flex 子元素滚动 */
  overflow-y: auto;
}

.session-list-footer {
  padding: 12px 8px;
  text-align: center;
  font-size: 12px;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
}

.loading-text {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.footer-spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid var(--theme-border, rgba(0, 0, 0, 0.15));
  border-top-color: var(--theme-accent, #0366d6);
  animation: spin 0.8s linear infinite;
}

.load-more-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 16px;
  border: 1px solid var(--theme-border, rgba(0, 0, 0, 0.15));
  border-radius: 6px;
  background: var(--theme-background, #fff);
  color: var(--theme-foreground, #333);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.load-more-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.04));
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.load-more-btn:active {
  transform: scale(0.98);
}

.no-more-text {
  opacity: 0.6;
}

.scroll-hint {
  opacity: 0.5;
  font-size: 11px;
}

.session-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-group-header {
  font-size: 12px;
  font-weight: 600;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  padding: 0 8px 2px;
  border-bottom: 1px solid var(--theme-border, rgba(0, 0, 0, 0.08));
  margin-bottom: 2px;
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
  gap: 4px;
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

/* 选中状态 - 保留用于可能的未来需求 */
.session-item.selected {
  background: rgba(3, 102, 214, 0.12);
  border-color: var(--theme-accent, #0366d6);
}

/* 历史会话项容器 */
.session-item-wrapper {
  display: flex;
  align-items: center;
  gap: 4px;
}

.session-item-wrapper .session-item {
  flex: 1;
  min-width: 0;
}

/* 行内操作按钮 */
.session-item-actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  padding-right: 4px;
}

.item-action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  color: var(--theme-secondary-foreground, rgba(0, 0, 0, 0.5));
  transition: all 0.15s ease;
}

.item-action-btn:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.08));
  color: var(--theme-foreground, #333);
}

.item-action-btn.copy-btn:hover {
  color: var(--theme-accent, #0366d6);
}

.item-action-btn.delete-btn:hover {
  background: rgba(220, 53, 69, 0.1);
  color: #dc3545;
}

.item-action-btn svg {
  flex-shrink: 0;
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

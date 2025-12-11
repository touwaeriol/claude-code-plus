<template>
  <div class="session-tabs">
    <div class="tabs-scroll">
      <draggable
        v-model="localTabs"
        :animation="200"
        :delay="150"
        :delay-on-touch-only="true"
        item-key="id"
        class="draggable-tabs"
        @end="handleDragEnd"
      >
        <template #item="{ element: tab }">
          <div
            class="session-tab"
            :class="{
              active: tab.id === currentSessionId,
              generating: tab.isGenerating,
              connecting: tab.connectionStatus === 'CONNECTING',
              error: tab.connectionStatus === 'ERROR'
            }"
            :title="displaySessionId(tab)
              ? `${displaySessionId(tab)} · ${t('session.copyHint') || '再次单击或双击复制'}`
              : t('chat.connectionStatus.disconnected')"
            @click="handleTabClick(tab)"
            @dblclick.stop="handleTabDblClick(tab)"
            @click.middle.prevent="handleCloseTab(tab.id)"
          >
            <span
              class="status-indicator"
              :class="{
                connecting: tab.connectionStatus === 'CONNECTING',
                connected: tab.connectionStatus === 'CONNECTED',
                disconnected: tab.connectionStatus === 'DISCONNECTED',
                error: tab.connectionStatus === 'ERROR'
              }"
              :title="statusTitle(tab)"
            >
              <span v-if="tab.connectionStatus === 'CONNECTING'" class="dot-pulse" />
              <span v-else class="dot-solid" />
            </span>

            <span class="tab-name">{{ tab.name || t('session.unnamed') }}</span>

            <span
              v-if="tab.isGenerating"
              class="generating-dot"
              :title="t('chat.connectionStatus.generating') || 'Generating'"
            />

            <button
              v-if="canClose"
              class="close-btn"
              type="button"
              :title="t('session.close') || '关闭会话'"
              @click.stop="handleCloseTab(tab.id)"
            >
              ×
            </button>
          </div>
        </template>
      </draggable>

      <span v-if="sessions.length === 0" class="tab-placeholder">
        {{ t('session.empty') || '暂无活动会话' }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import draggable from 'vuedraggable'
import { useI18n } from '@/composables/useI18n'

export interface SessionTabInfo {
  id: string  // tabId
  name: string
  sessionId?: string | null
  resumeFromSessionId?: string | null
  isGenerating?: boolean
  isConnected?: boolean
  connectionStatus?: 'CONNECTED' | 'CONNECTING' | 'DISCONNECTED' | 'ERROR'
  error?: string | null
}

const props = withDefaults(defineProps<{
  sessions: SessionTabInfo[]
  currentSessionId: string | null
  canClose?: boolean
}>(), {
  canClose: true
})

const emit = defineEmits<{
  (e: 'switch', sessionId: string): void
  (e: 'close', sessionId: string): void
  (e: 'reorder', order: string[]): void
  (e: 'toggle-list'): void
}>()

const { t } = useI18n()

const localTabs = ref<SessionTabInfo[]>([...props.sessions])

watch(() => props.sessions, (newSessions) => {
  localTabs.value = [...newSessions]
}, { deep: true })

function handleTabClick(tab: SessionTabInfo) {
  // 当前激活且有 sessionId 时，单击直接复制
  const copyId = displaySessionId(tab)
  if (tab.id === props.currentSessionId && copyId) {
    copySessionId(copyId)
    return
  }
  if (tab.id === props.currentSessionId) {
    emit('toggle-list')
    return
  }
  emit('switch', tab.id)
}

function handleTabDblClick(tab: SessionTabInfo) {
  const copyId = displaySessionId(tab)
  if (copyId) {
    copySessionId(copyId)
  }
}

function handleCloseTab(sessionId: string) {
  emit('close', sessionId)
}

function handleDragEnd() {
  const newOrder = localTabs.value.map(tab => tab.id)
  emit('reorder', newOrder)
}

function statusTitle(tab: SessionTabInfo): string {
  if (tab.connectionStatus === 'CONNECTED') return t('chat.connectionStatus.connected')
  if (tab.connectionStatus === 'CONNECTING') return t('chat.connectionStatus.connecting')
  if (tab.connectionStatus === 'DISCONNECTED') return t('chat.connectionStatus.disconnected')
  if (tab.connectionStatus === 'ERROR') return tab.error || (t('chat.connectionStatus.error') || '连接错误')
  return t('chat.connectionStatus.disconnected')
}

async function copySessionId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
  } catch (error) {
    console.error('复制会话 ID 失败:', error)
  }
}

function displaySessionId(tab: SessionTabInfo): string | null {
  return tab.sessionId || tab.resumeFromSessionId || null
}
</script>

<style scoped>
.session-tabs {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.tabs-scroll {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  scrollbar-width: thin;
}

.draggable-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.session-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  max-width: 200px;
  padding: 3px 8px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--theme-foreground, #24292e);
  font-size: 11px;
  cursor: grab;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
  user-select: none;
}

.session-tab:active {
  cursor: grabbing;
}

.session-tab:hover {
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.03));
}

.session-tab:hover .close-btn {
  opacity: 1;
}

.session-tab.active {
  background: var(--theme-card-background, #ffffff);
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.session-tab.connecting {
  opacity: 0.75;
}

.session-tab.error {
  border-color: var(--theme-error, #d73a49);
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
  flex-shrink: 0;
  width: 12px;
  height: 12px;
}

.status-indicator .dot-solid {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.connected .dot-solid {
  background: var(--theme-success, #28a745);
  box-shadow: 0 0 6px rgba(40, 167, 69, 0.6);
}

.status-indicator.disconnected .dot-solid {
  background: var(--theme-error, #d73a49);
  box-shadow: 0 0 4px rgba(215, 58, 73, 0.5);
}

.status-indicator.error .dot-solid {
  background: var(--theme-error, #d73a49);
  box-shadow: 0 0 6px rgba(215, 58, 73, 0.8);
}

.status-indicator.connecting .dot-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--theme-accent, #0366d6);
  animation: dot-pulse 1s ease-in-out infinite;
}

@keyframes dot-pulse {
  0%, 100% {
    opacity: 0.4;
    transform: scale(0.8);
  }
  50% {
    opacity: 1;
    transform: scale(1);
  }
}

.tab-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.generating-dot {
  width: 6px;
  height: 6px;
  margin-left: 6px;
  border-radius: 50%;
  background: var(--theme-success, #28a745);
  animation: pulse 1.5s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes pulse {
  0%, 100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.6;
    transform: scale(0.85);
  }
}

.close-btn {
  width: 14px;
  height: 14px;
  margin-left: 4px;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease, color 0.15s ease;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-btn:hover {
  background: var(--theme-error, #d73a49);
  color: #ffffff;
}

.session-tab.active .close-btn {
  opacity: 0.6;
}

.session-tab.active .close-btn:hover {
  opacity: 1;
}

.tab-placeholder {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #6a737d);
  opacity: 0.8;
}
</style>

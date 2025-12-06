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
            @click="handleTabClick(tab.id)"
            @click.middle.prevent="handleCloseTab(tab.id)"
          >
            <!-- 连接中状态指示器 -->
            <span
              v-if="tab.connectionStatus === 'CONNECTING'"
              class="status-indicator connecting"
              title="正在连接..."
            >
              <span class="dot-pulse" />
            </span>
            <!-- 错误状态指示器 -->
            <span
              v-else-if="tab.connectionStatus === 'ERROR'"
              class="status-indicator error"
              :title="tab.error || '连接失败'"
            >⚠</span>
            <span class="tab-name">{{ tab.name || '未命名会话' }}</span>
            <span
              v-if="tab.isGenerating"
              class="generating-dot"
              title="正在生成中"
            />
            <button
              v-if="canClose"
              class="close-btn"
              type="button"
              title="关闭会话"
              @click.stop="handleCloseTab(tab.id)"
            >
              ×
            </button>
          </div>
        </template>
      </draggable>

      <span v-if="sessions.length === 0" class="tab-placeholder">
        暂无活动会话
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import draggable from 'vuedraggable'

export interface SessionTabInfo {
  id: string  // tabId
  name: string
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

// 本地 tabs 列表，用于拖拽
const localTabs = ref<SessionTabInfo[]>([...props.sessions])

// 监听外部 sessions 变化，同步到本地
watch(() => props.sessions, (newSessions) => {
  localTabs.value = [...newSessions]
}, { deep: true })

function handleTabClick(sessionId: string) {
  if (sessionId === props.currentSessionId) {
    // 点击当前会话时，展开会话列表
    emit('toggle-list')
    return
  }
  emit('switch', sessionId)
}

function handleCloseTab(sessionId: string) {
  emit('close', sessionId)
}

function handleDragEnd() {
  const newOrder = localTabs.value.map(tab => tab.id)
  emit('reorder', newOrder)
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
  max-width: 180px;
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

.session-tab.generating {
  /* 生成中的会话边框带动画 */
}

.session-tab.connecting {
  opacity: 0.7;
  border-style: dashed;
}

.session-tab.error {
  border-color: var(--theme-error, #d73a49);
}

.status-indicator {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-right: 4px;
  font-size: 10px;
  flex-shrink: 0;
}

.status-indicator.connecting {
  width: 12px;
  height: 12px;
}

.status-indicator.error {
  color: var(--theme-error, #d73a49);
  font-size: 12px;
}

.dot-pulse {
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

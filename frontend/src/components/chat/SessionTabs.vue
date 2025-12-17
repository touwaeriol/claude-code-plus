<template>
  <div class="session-tabs" :class="{ 'has-overflow': hasOverflow }">
    <!-- 左侧滚动指示器 -->
    <button
      v-if="hasOverflow && canScrollLeft"
      class="scroll-indicator scroll-left"
      @click="scrollLeft"
    >
      ‹
    </button>

    <div
      ref="scrollContainer"
      class="tabs-scroll"
      @scroll="updateScrollState"
    >
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
            :title="displaySessionId(tab) || t('chat.connectionStatus.disconnected')"
            @click="handleTabClick(tab)"
            @dblclick.stop="handleTabDblClick(tab)"
            @click.middle.prevent="handleCloseTab(tab.id)"
          >
            <span
              class="status-indicator"
              :class="{
                connecting: tab.connectionStatus === 'CONNECTING',
                generating: tab.isGenerating,
                connected: tab.connectionStatus === 'CONNECTED',
                disconnected: tab.connectionStatus === 'DISCONNECTED',
                error: tab.connectionStatus === 'ERROR'
              }"
              :title="statusTitle(tab)"
            >
              <span v-if="tab.connectionStatus === 'CONNECTING'" class="spinner connecting" />
              <span v-else-if="tab.isGenerating" class="spinner generating" />
              <span v-else class="dot-solid" />
            </span>

            <!-- 编辑模式 -->
            <input
              v-if="editingTabId === tab.id"
              v-model="editingName"
              class="tab-name-input"
              @blur="confirmRename(tab)"
              @keyup.enter="confirmRename(tab)"
              @keyup.escape="cancelEditing"
              @click.stop
            />
            <!-- 显示模式 -->
            <span v-else class="tab-name">{{ tab.name || t('session.unnamed') }}</span>

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

    <!-- 右侧滚动指示器 -->
    <button
      v-if="hasOverflow && canScrollRight"
      class="scroll-indicator scroll-right"
      @click="scrollRight"
    >
      ›
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
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
  (e: 'rename', tabId: string, newName: string): void
}>()

const { t } = useI18n()

const localTabs = ref<SessionTabInfo[]>([...props.sessions])

// 编辑状态
const editingTabId = ref<string | null>(null)
const editingName = ref('')

// 滚动状态
const scrollContainer = ref<HTMLElement | null>(null)
const hasOverflow = ref(false)
const canScrollLeft = ref(false)
const canScrollRight = ref(false)

let resizeObserver: ResizeObserver | null = null

function updateScrollState() {
  const el = scrollContainer.value
  if (!el) return

  hasOverflow.value = el.scrollWidth > el.clientWidth
  canScrollLeft.value = el.scrollLeft > 0
  canScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 1
}

function scrollLeft() {
  const el = scrollContainer.value
  if (!el) return
  el.scrollBy({ left: -120, behavior: 'smooth' })
}

function scrollRight() {
  const el = scrollContainer.value
  if (!el) return
  el.scrollBy({ left: 120, behavior: 'smooth' })
}

onMounted(() => {
  nextTick(() => {
    // 延迟一帧确保 draggable 组件完全渲染
    requestAnimationFrame(() => {
      updateScrollState()
    })
    // 监听容器大小变化
    if (scrollContainer.value) {
      resizeObserver = new ResizeObserver(() => {
        // 使用 requestAnimationFrame 避免频繁更新
        requestAnimationFrame(updateScrollState)
      })
      resizeObserver.observe(scrollContainer.value)
    }
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
})

watch(() => props.sessions, (newSessions, oldSessions) => {
  const wasAdded = newSessions.length > (oldSessions?.length || 0)
  localTabs.value = [...newSessions]
  // 延迟两帧确保 DOM 更新完成
  nextTick(() => {
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        updateScrollState()
        // 如果新增了标签，滚动到最右边
        if (wasAdded && scrollContainer.value) {
          scrollContainer.value.scrollTo({
            left: scrollContainer.value.scrollWidth,
            behavior: 'smooth'
          })
        }
      })
    })
  })
}, { deep: true })

function handleTabClick(tab: SessionTabInfo) {
  if (tab.id === props.currentSessionId) {
    // 点击当前激活的 tab 不做任何事
    return
  }
  emit('switch', tab.id)
}

function handleTabDblClick(tab: SessionTabInfo) {
  // 进入编辑模式
  editingTabId.value = tab.id
  editingName.value = tab.name || ''
  nextTick(() => {
    const input = document.querySelector('.tab-name-input') as HTMLInputElement
    input?.focus()
    input?.select()
  })
}

function confirmRename(tab: SessionTabInfo) {
  const newName = editingName.value.trim()
  if (newName && newName !== tab.name) {
    emit('rename', tab.id, newName)
  }
  cancelEditing()
}

function cancelEditing() {
  editingTabId.value = null
  editingName.value = ''
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

function displaySessionId(tab: SessionTabInfo): string | null {
  return tab.sessionId || tab.resumeFromSessionId || null
}
</script>

<style scoped>
.session-tabs {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  position: relative;
}

.tabs-scroll {
  display: flex;
  align-items: center;
  gap: 4px;
  overflow-x: auto;
  flex: 1;
  min-width: 0;
  /* 隐藏滚动条但保持滚动功能 */
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.tabs-scroll::-webkit-scrollbar {
  display: none;
}

.draggable-tabs {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
  flex-shrink: 0;
}

/* 滚动指示器 */
.scroll-indicator {
  width: 20px;
  height: 20px;
  border: none;
  border-radius: 50%;
  background: var(--theme-hover-background, rgba(0, 0, 0, 0.06));
  color: var(--theme-secondary-foreground, #6a737d);
  font-size: 14px;
  font-weight: bold;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
  z-index: 1;
}

.scroll-indicator:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.scroll-left {
  margin-right: 2px;
}

.scroll-right {
  margin-left: 2px;
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

/* 绿色 - 连接完成/生成完成 */
.status-indicator.connected .dot-solid {
  background: var(--theme-success);
}

/* 红色 - 未连接 */
.status-indicator.disconnected .dot-solid {
  background: var(--theme-error);
}

/* 红色 - 错误 */
.status-indicator.error .dot-solid {
  background: var(--theme-error);
}

.status-indicator .spinner {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid transparent;
  box-sizing: border-box;
}

/* 蓝色转圈 - 连接中 */
.status-indicator .spinner.connecting {
  border-top-color: var(--theme-accent);
  border-right-color: var(--theme-accent);
  animation: spin 0.8s linear infinite;
}

/* 绿色转圈 - 生成中 */
.status-indicator .spinner.generating {
  border-top-color: var(--theme-success);
  border-right-color: var(--theme-success);
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

.tab-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tab-name-input {
  max-width: 120px;
  padding: 0 2px;
  border: 1px solid var(--theme-accent, #0366d6);
  border-radius: 2px;
  background: var(--theme-background, #fff);
  color: inherit;
  font-size: inherit;
  outline: none;
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

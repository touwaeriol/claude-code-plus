<template>
  <div
    class="chat-header"
    :class="{ 'theme-dark': isDark }"
  >
    <div class="tab-container">
      <div class="tab-scroll">
        <draggable
          v-model="tabsList"
          :animation="200"
          item-key="id"
          class="draggable-tabs"
          @end="handleDragEnd"
        >
          <template #item="{ element: tab }">
            <button
              class="chat-tab"
              :class="{ active: tab.id === currentSessionId }"
              @click="handleTabClick(tab.id)"
            >
              <span class="tab-name">
                {{ tab.name || '未命名会话' }}
              </span>
              <span
                v-if="tab.isGenerating"
                class="tab-dot"
                title="正在生成中"
              />
            </button>
          </template>
        </draggable>

        <span
          v-if="activeTabs.length === 0"
          class="tab-placeholder"
        >
          暂无活动会话
        </span>
      </div>
    </div>

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
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import draggable from 'vuedraggable'
import { useSessionStore } from '@/stores/sessionStore'
import type { SessionState } from '@/types/session'

const props = withDefaults(defineProps<{
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

// 用于vuedraggable的双向绑定
const tabsList = computed({
  get: () => activeTabs.value,
  set: (newList: SessionState[]) => {
    // 更新order
    const newOrder = newList.map(tab => tab.id)
    sessionStore.updateTabOrder(newOrder)
  }
})

async function handleTabClick(sessionId: string) {
  if (sessionId === currentSessionId.value) return
  await sessionStore.switchSession(sessionId)
}

async function handleNewSession() {
  if (typeof sessionStore.startNewSession === 'function') {
    await sessionStore.startNewSession()
  } else {
    await sessionStore.createSession()
  }
}

function handleDragEnd() {
  // 拖拽结束后，order已经通过tabsList的setter更新了
  // 这里可以添加额外的逻辑，比如保存到本地存储
}
</script>

<style scoped>
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  height: 40px;
  border-bottom: 1px solid var(--ide-border, #e1e4e8);
  background: var(--ide-panel-background, #f6f8fa);
  box-sizing: border-box;
}

.theme-dark.chat-header {
  background: var(--ide-panel-background, #1f2428);
  border-color: var(--ide-border, #30363d);
}

.tab-container {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.tab-scroll {
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

.draggable-tabs .chat-tab {
  cursor: grab;
}

.draggable-tabs .chat-tab:active {
  cursor: grabbing;
}

.chat-tab {
  position: relative;
  display: inline-flex;
  align-items: center;
  max-width: 180px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  color: var(--ide-foreground, #24292e);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.chat-tab:hover {
  background: var(--ide-hover-background, rgba(0, 0, 0, 0.03));
}

.chat-tab.active {
  background: var(--ide-card-background, #ffffff);
  border-color: var(--ide-accent, #0366d6);
  color: var(--ide-accent, #0366d6);
}

.theme-dark .chat-tab {
  color: var(--ide-foreground, #e6edf3);
}

.theme-dark .chat-tab:hover {
  background: rgba(255, 255, 255, 0.06);
}

.theme-dark .chat-tab.active {
  background: var(--ide-card-background, #161b22);
  border-color: var(--ide-accent, #58a6ff);
  color: var(--ide-accent, #58a6ff);
}

.tab-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tab-dot {
  width: 8px;
  height: 8px;
  margin-left: 6px;
  border-radius: 50%;
  background: var(--ide-success, #28a745);
}

.tab-placeholder {
  font-size: 12px;
  color: var(--ide-secondary-foreground, #6a737d);
  opacity: 0.8;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.icon-btn {
  width: 26px;
  height: 26px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: transparent;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
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



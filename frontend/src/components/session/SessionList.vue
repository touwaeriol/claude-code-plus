<template>
  <div
    class="session-list"
    :class="{ 'theme-dark': isDark }"
  >
    <div class="session-header">
      <h3 class="header-title">
        会话历史
      </h3>
      <button
        class="btn-icon"
        title="新建会话"
        @click="$emit('new-session')"
      >
        <span>➕</span>
      </button>
    </div>

    <div
      v-if="loading"
      class="session-loading"
    >
      <div class="loading-spinner" />
      <span>加载中...</span>
    </div>

    <div
      v-else-if="sessions.length === 0"
      class="session-empty"
    >
      <span class="empty-icon">📭</span>
      <span class="empty-text">暂无会话</span>
    </div>

    <div
      v-else
      class="session-items"
    >
      <div
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === currentSessionId }"
        @click="$emit('select-session', session)"
      >
        <div class="session-main">
          <div class="session-name">
            {{ session.name || '未命名会话' }}
          </div>
          <div class="session-meta">
            <span class="session-time">{{ formatTime(session.timestamp) }}</span>
            <span class="session-count">{{ session.messageCount || 0 }} 条消息</span>
          </div>
        </div>

        <div
          class="session-actions"
          @click.stop
        >
          <button
            class="btn-icon-small"
            title="重命名"
            @click="startRename(session)"
          >
            ✏️
          </button>
          <button
            class="btn-icon-small"
            title="删除"
            @click="$emit('delete-session', session)"
          >
            🗑️
          </button>
        </div>
      </div>
    </div>

    <!-- 重命名对话框 -->
    <div
      v-if="renamingSession"
      class="rename-dialog"
      @click.self="cancelRename"
    >
      <div class="dialog-content">
        <h4>重命名会话</h4>
        <input
          ref="renameInputRef"
          v-model="newName"
          type="text"
          class="rename-input"
          placeholder="输入新名称"
          @keydown.enter="confirmRename"
          @keydown.esc="cancelRename"
        >
        <div class="dialog-actions">
          <button
            class="btn btn-secondary"
            @click="cancelRename"
          >
            取消
          </button>
          <button
            class="btn btn-primary"
            @click="confirmRename"
          >
            确定
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

export interface Session {
  id: string
  name: string
  timestamp: number
  messageCount?: number
}

interface Props {
  sessions: Session[]
  currentSessionId?: string
  loading?: boolean
  isDark?: boolean
}

interface Emits {
  (e: 'select-session', session: Session): void
  (e: 'new-session'): void
  (e: 'delete-session', session: Session): void
  (e: 'rename-session', sessionId: string, newName: string): void
}

const _props = withDefaults(defineProps<Props>(), {
  loading: false,
  isDark: false
})

const emit = defineEmits<Emits>()

const renamingSession = ref<Session | null>(null)
const newName = ref('')
const renameInputRef = ref<HTMLInputElement>()

function formatTime(timestamp: number): string {
  const date = new Date(timestamp)
  const now = new Date()
  const diff = now.getTime() - date.getTime()

  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  if (hours < 24) return `${hours} 小时前`
  if (days < 7) return `${days} 天前`

  return date.toLocaleDateString('zh-CN', {
    month: 'short',
    day: 'numeric'
  })
}

async function startRename(session: Session) {
  renamingSession.value = session
  newName.value = session.name || ''
  await nextTick()
  renameInputRef.value?.focus()
  renameInputRef.value?.select()
}

function confirmRename() {
  if (renamingSession.value && newName.value.trim()) {
    emit('rename-session', renamingSession.value.id, newName.value.trim())
    cancelRename()
  }
}

function cancelRename() {
  renamingSession.value = null
  newName.value = ''
}
</script>

<style scoped>
.session-list {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--ide-panel-background);
  border-right: 1px solid var(--ide-border);
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--ide-border);
}

.header-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--ide-foreground);
}

.btn-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 4px;
  font-size: 16px;
  transition: background 0.2s;
}

.btn-icon:hover {
  background: rgba(0, 0, 0, 0.05);
}

.theme-dark .btn-icon:hover {
  background: rgba(255, 255, 255, 0.05);
}

/* 加载状态 */
.session-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px 16px;
  color: var(--ide-foreground);
  opacity: 0.6;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--ide-border);
  border-top-color: var(--ide-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 空状态 */
.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 32px 16px;
  color: var(--ide-foreground);
  opacity: 0.6;
}

.empty-icon {
  font-size: 48px;
}

.empty-text {
  font-size: 14px;
}

/* 会话列表 */
.session-items {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
  border: 1px solid transparent;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.03);
}

.theme-dark .session-item:hover {
  background: rgba(255, 255, 255, 0.03);
}

.session-item.active {
  background: var(--ide-accent);
  color: white;
  border-color: var(--ide-accent);
}

.session-item.active .session-meta {
  color: rgba(255, 255, 255, 0.8);
}

.session-main {
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--ide-foreground);
  opacity: 0.6;
}

.session-actions {
  display: none;
  align-items: center;
  gap: 4px;
}

.session-item:hover .session-actions {
  display: flex;
}

.session-item.active .session-actions {
  display: flex;
}

.btn-icon-small {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: rgba(0, 0, 0, 0.1);
  cursor: pointer;
  border-radius: 4px;
  font-size: 14px;
  transition: background 0.2s;
}

.btn-icon-small:hover {
  background: rgba(0, 0, 0, 0.2);
}

.session-item.active .btn-icon-small {
  background: rgba(255, 255, 255, 0.2);
}

.session-item.active .btn-icon-small:hover {
  background: rgba(255, 255, 255, 0.3);
}

/* 重命名对话框 */
.rename-dialog {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog-content {
  background: var(--ide-background);
  border: 1px solid var(--ide-border);
  border-radius: 8px;
  padding: 20px;
  min-width: 300px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.dialog-content h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--ide-foreground);
}

.rename-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid var(--ide-input-border);
  border-radius: 4px;
  background: var(--ide-input-background);
  color: var(--ide-input-foreground);
  font-size: 14px;
  outline: none;
  margin-bottom: 16px;
}

.rename-input:focus {
  border-color: var(--ide-accent);
  box-shadow: 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn {
  padding: 6px 16px;
  border: none;
  border-radius: 4px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-secondary {
  background: var(--ide-panel-background);
  color: var(--ide-foreground);
  border: 1px solid var(--ide-border);
}

.btn-secondary:hover {
  background: var(--ide-border);
}

.btn-primary {
  background: var(--ide-button-background);
  color: var(--ide-button-foreground);
}

.btn-primary:hover {
  background: var(--ide-button-hover-background);
}
</style>

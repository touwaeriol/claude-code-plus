<template>
  <div
    class="session-list"
  >
    <div class="session-header">
      <h3 class="header-title">
        会话历史
      </h3>
      <el-button
        class="btn-icon"
        title="新建会话"
        text
        circle
        @click="$emit('new-session')"
      >
        <span>➕</span>
      </el-button>
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
          <el-button
            class="btn-icon-small"
            title="重命名"
            text
            size="small"
            @click="startRename(session)"
          >
            ✏️
          </el-button>
          <el-button
            class="btn-icon-small"
            title="删除"
            text
            size="small"
            @click="$emit('delete-session', session)"
          >
            🗑️
          </el-button>
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
        <el-input
          ref="renameInputRef"
          v-model="newName"
          class="rename-input"
          placeholder="输入新名称"
          @keydown.enter="confirmRename"
          @keydown.esc="cancelRename"
        />
        <div class="dialog-actions">
          <el-button
            class="btn-secondary"
            @click="cancelRename"
          >
            取消
          </el-button>
          <el-button
            class="btn-primary"
            type="primary"
            @click="confirmRename"
          >
            确定
          </el-button>
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
}

interface Emits {
  (e: 'select-session', session: Session): void
  (e: 'new-session'): void
  (e: 'delete-session', session: Session): void
  (e: 'rename-session', sessionId: string, newName: string): void
}

withDefaults(defineProps<Props>(), {
  loading: false
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
  background: var(--theme-panel-background);
  border-right: 1px solid var(--theme-border);
}

.session-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--theme-border);
}

.header-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--theme-foreground);
}

/* Element Plus 按钮样式覆盖 */
.btn-icon :deep(.el-button) {
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 16px;
}

.btn-icon:hover :deep(.el-button) {
  background: rgba(0, 0, 0, 0.05);
}

/* 加载状态 */
.session-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 32px 16px;
  color: var(--theme-foreground);
  opacity: 0.6;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--theme-border);
  border-top-color: var(--theme-accent);
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
  color: var(--theme-foreground);
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

.session-item.active {
  background: var(--theme-accent);
  color: white;
  border-color: var(--theme-accent);
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
  color: var(--theme-foreground);
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

.btn-icon-small :deep(.el-button) {
  width: 24px;
  height: 24px;
  padding: 0;
  font-size: 14px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 4px;
}

.btn-icon-small:hover :deep(.el-button) {
  background: rgba(0, 0, 0, 0.2);
}

.session-item.active .btn-icon-small :deep(.el-button) {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.session-item.active .btn-icon-small:hover :deep(.el-button) {
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
  background: var(--theme-background);
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: 20px;
  min-width: 300px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.dialog-content h4 {
  margin: 0 0 16px 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--theme-foreground);
}

/* Element Plus input 样式覆盖 */
.rename-input {
  width: 100%;
  margin-bottom: 16px;
}

.rename-input :deep(.el-input__wrapper) {
  padding: 8px 12px;
  background: var(--theme-input-background);
  box-shadow: 0 0 0 1px var(--theme-input-border) inset;
}

.rename-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--theme-accent) inset, 0 0 0 3px rgba(3, 102, 214, 0.1);
}

.rename-input :deep(.el-input__inner) {
  color: var(--theme-input-foreground);
  font-size: 14px;
}

.dialog-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

/* Element Plus 对话框按钮样式覆盖 */
.btn-secondary :deep(.el-button) {
  background: var(--theme-panel-background);
  color: var(--theme-foreground);
  border: 1px solid var(--theme-border);
  padding: 6px 16px;
  font-size: 14px;
  font-weight: 600;
}

.btn-secondary:hover :deep(.el-button) {
  background: var(--theme-border);
}

.btn-primary :deep(.el-button) {
  background: var(--theme-button-background);
  color: var(--theme-button-foreground);
  border: none;
  padding: 6px 16px;
  font-size: 14px;
  font-weight: 600;
}

.btn-primary:hover :deep(.el-button) {
  background: var(--theme-button-hover-background);
}
</style>

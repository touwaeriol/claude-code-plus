<template>
  <div v-if="pendingPermission" class="permission-request">
    <div class="permission-card">
      <!-- 工具信息头部 -->
      <div class="permission-header">
        <span class="tool-icon">{{ getToolIcon(pendingPermission.toolName) }}</span>
        <span class="tool-name">{{ getToolDisplayName(pendingPermission.toolName) }}</span>
      </div>

      <!-- 工具参数预览 -->
      <div class="permission-content">
        <template v-if="pendingPermission.toolName === 'Bash'">
          <pre class="command-preview">{{ pendingPermission.toolInput.command }}</pre>
        </template>
        <template v-else-if="pendingPermission.toolName === 'Write'">
          <div class="file-info">
            <span class="file-icon">📄</span>
            <span class="file-path">{{ pendingPermission.toolInput.file_path }}</span>
          </div>
          <div v-if="pendingPermission.toolInput.content" class="content-preview">
            <pre class="content-text">{{ truncateContent(pendingPermission.toolInput.content) }}</pre>
          </div>
        </template>
        <template v-else-if="pendingPermission.toolName === 'Edit'">
          <div class="file-info">
            <span class="file-icon">✏️</span>
            <span class="file-path">{{ pendingPermission.toolInput.file_path }}</span>
          </div>
          <div v-if="pendingPermission.toolInput.old_string" class="edit-preview">
            <div class="edit-section">
              <span class="edit-label">替换:</span>
              <pre class="edit-text old">{{ truncateContent(pendingPermission.toolInput.old_string) }}</pre>
            </div>
            <div class="edit-section">
              <span class="edit-label">为:</span>
              <pre class="edit-text new">{{ truncateContent(pendingPermission.toolInput.new_string) }}</pre>
            </div>
          </div>
        </template>
        <template v-else>
          <pre class="params-preview">{{ formatParams(pendingPermission.toolInput) }}</pre>
        </template>
      </div>

      <!-- 等待提示 -->
      <div class="waiting-hint">
        <span class="waiting-icon">⏳</span>
        <span>{{ $t('permission.waitingForInput', 'Waiting for user input...') }}</span>
      </div>

      <!-- 操作按钮 -->
      <div class="permission-actions">
        <button class="btn-skip" @click="handleSkip">
          {{ $t('permission.skip', 'Skip') }}
        </button>
        <button class="btn-approve" @click="handleApprove">
          <span class="approve-icon">⊙</span>
          {{ $t('permission.approve', 'Approve') }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import type { PermissionToolInput } from '@/types/permission'

const sessionStore = useSessionStore()

// 获取当前会话的第一个待处理授权请求
const pendingPermission = computed(() => {
  const permissions = sessionStore.getCurrentPendingPermissions()
  return permissions.length > 0 ? permissions[0] : null
})

function handleSkip() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, false)
  }
}

function handleApprove() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, true)
  }
}

function getToolDisplayName(name: string): string {
  const names: Record<string, string> = {
    'Bash': 'Terminal',
    'Write': 'Write File',
    'Edit': 'Edit File',
    'Read': 'Read File',
    'MultiEdit': 'Multi Edit',
    'Glob': 'Find Files',
    'Grep': 'Search Content'
  }
  return names[name] || name
}

function getToolIcon(name: string): string {
  const icons: Record<string, string> = {
    'Bash': '🖥',
    'Write': '📝',
    'Edit': '✏️',
    'Read': '📖',
    'MultiEdit': '📋',
    'Glob': '🔍',
    'Grep': '🔎'
  }
  return icons[name] || '🔧'
}

function truncateContent(content: string, maxLength: number = 200): string {
  if (!content) return ''
  if (content.length <= maxLength) return content
  return content.substring(0, maxLength) + '...'
}

function formatParams(params: PermissionToolInput): string {
  try {
    return JSON.stringify(params, null, 2)
  } catch {
    return String(params)
  }
}
</script>

<style scoped>
.permission-request {
  position: fixed;
  bottom: 80px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 1000;
  max-width: 600px;
  width: calc(100% - 32px);
}

.permission-card {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  overflow: hidden;
}

.permission-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--theme-accent, #0366d6);
  color: white;
}

.tool-icon {
  font-size: 18px;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
}

.permission-content {
  padding: 16px;
  max-height: 300px;
  overflow-y: auto;
}

.command-preview {
  background: #2d2d2d;
  color: #e6e6e6;
  padding: 12px;
  border-radius: 6px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--theme-panel-background, #f6f8fa);
  border-radius: 6px;
  margin-bottom: 8px;
}

.file-icon {
  font-size: 16px;
}

.file-path {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
  color: var(--theme-foreground, #24292e);
  word-break: break-all;
}

.content-preview,
.edit-preview {
  margin-top: 8px;
}

.content-text {
  background: #f6f8fa;
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 100px;
  overflow-y: auto;
}

.edit-section {
  margin-bottom: 8px;
}

.edit-label {
  font-size: 12px;
  color: var(--theme-secondary-foreground, #586069);
  display: block;
  margin-bottom: 4px;
}

.edit-text {
  background: #f6f8fa;
  padding: 8px;
  border-radius: 4px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 60px;
  overflow-y: auto;
}

.edit-text.old {
  background: #ffeef0;
  border-left: 3px solid #cb2431;
}

.edit-text.new {
  background: #e6ffed;
  border-left: 3px solid #28a745;
}

.params-preview {
  background: #f6f8fa;
  padding: 12px;
  border-radius: 6px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 150px;
  overflow-y: auto;
}

.waiting-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  color: var(--theme-secondary-foreground, #586069);
  font-size: 13px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.waiting-icon {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.permission-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  border-top: 1px solid var(--theme-border, #e1e4e8);
}

.btn-skip,
.btn-approve {
  padding: 8px 20px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}

.btn-skip {
  background: transparent;
  border: 1px solid var(--theme-border, #e1e4e8);
  color: var(--theme-foreground, #24292e);
}

.btn-skip:hover {
  background: var(--theme-hover-background, #f0f0f0);
  border-color: var(--theme-secondary-foreground, #586069);
}

.btn-approve {
  background: var(--theme-accent, #0366d6);
  border: 1px solid var(--theme-accent, #0366d6);
  color: white;
}

.btn-approve:hover {
  background: var(--theme-accent-hover, #0256b9);
  border-color: var(--theme-accent-hover, #0256b9);
}

.approve-icon {
  font-size: 14px;
}
</style>

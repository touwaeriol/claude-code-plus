<template>
  <div
    v-if="pendingPermission"
    ref="containerRef"
    class="permission-request"
    tabindex="0"
    @keydown.esc="handleDeny"
  >
    <div class="permission-card">
      <!-- 工具信息头部 -->
      <div class="permission-header">
        <span class="tool-icon">{{ getToolIcon(pendingPermission.toolName) }}</span>
        <span class="tool-name">{{ getToolDisplayName(pendingPermission.toolName) }}</span>
        <span class="permission-label">{{ t('permission.needsAuth') }}</span>
      </div>

      <!-- 工具参数预览 -->
      <div class="permission-content">
        <template v-if="pendingPermission.toolName === 'Bash'">
          <pre class="command-preview">{{ pendingPermission.input.command }}</pre>
        </template>
        <template v-else-if="pendingPermission.toolName === 'Write'">
          <div class="file-info">
            <span class="file-icon">📄</span>
            <span class="file-path">{{ pendingPermission.input.file_path }}</span>
          </div>
          <div v-if="pendingPermission.input.content" class="content-preview">
            <pre class="content-text">{{ truncateContent(pendingPermission.input.content) }}</pre>
          </div>
        </template>
        <template v-else-if="pendingPermission.toolName === 'Edit'">
          <div class="file-info">
            <span class="file-icon">✏️</span>
            <span class="file-path">{{ pendingPermission.input.file_path }}</span>
          </div>
          <div v-if="pendingPermission.input.old_string" class="edit-preview">
            <div class="edit-section">
              <span class="edit-label">{{ t('permission.replace') }}:</span>
              <pre class="edit-text old">{{ truncateContent(pendingPermission.input.old_string) }}</pre>
            </div>
            <div class="edit-section">
              <span class="edit-label">{{ t('permission.with') }}:</span>
              <pre class="edit-text new">{{ truncateContent(pendingPermission.input.new_string || '') }}</pre>
            </div>
          </div>
        </template>
        <template v-else>
          <pre class="params-preview">{{ formatParams(pendingPermission.input) }}</pre>
        </template>
      </div>

      <!-- 操作选项 -->
      <div class="permission-options">
        <!-- 允许（仅本次） -->
        <button class="btn-option btn-allow" @click="handleApprove">
          {{ t('permission.allow') }}
        </button>

        <!-- 动态渲染 permissionSuggestions -->
        <button
          v-for="(suggestion, index) in pendingPermission.permissionSuggestions"
          :key="index"
          class="btn-option btn-allow-rule"
          @click="handleAllowWithUpdate(suggestion)"
        >
          {{ t('permission.allow') }}，{{ formatSuggestion(suggestion) }}
        </button>

        <!-- 不允许（带输入框） -->
        <div class="deny-inline">
          <input
            v-model="denyReason"
            class="deny-input"
            :placeholder="t('permission.denyReasonPlaceholder')"
            @keydown.enter="handleDeny"
          />
          <button class="btn-option btn-deny" @click="handleDeny">
            {{ t('permission.deny') }}
          </button>
        </div>
      </div>

      <!-- 快捷键提示 -->
      <div class="shortcut-hint">{{ t('permission.escToDeny') }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useSessionStore } from '@/stores/sessionStore'
import { useI18n } from '@/composables/useI18n'
import type { PermissionUpdate } from '@/types/permission'

const { t } = useI18n()
const sessionStore = useSessionStore()

const containerRef = ref<HTMLElement | null>(null)
const denyReason = ref('')

// 获取当前会话的第一个待处理授权请求
const pendingPermission = computed(() => {
  const permissions = sessionStore.getCurrentPendingPermissions()
  return permissions.length > 0 ? permissions[0] : null
})

// 当有新的权限请求时，自动聚焦并清空拒绝原因
watch(pendingPermission, (newVal) => {
  if (newVal) {
    denyReason.value = ''
    nextTick(() => {
      containerRef.value?.focus()
    })
  }
})

function handleApprove() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, { approved: true })
  }
}

function handleAllowWithUpdate(update: PermissionUpdate) {
  if (pendingPermission.value) {
    // 如果是 setMode 类型，只更新本地 UI 状态
    // 不需要调用 setPermissionMode RPC，SDK 收到响应后会自行切换
    if (update.type === 'setMode' && update.mode) {
      sessionStore.setLocalPermissionMode(update.mode)
    }

    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: true,
      permissionUpdates: [update]
    })
  }
}

function handleDeny() {
  if (pendingPermission.value) {
    sessionStore.respondPermission(pendingPermission.value.id, {
      approved: false,
      denyReason: denyReason.value || undefined
    })
  }
}

function formatSuggestion(suggestion: PermissionUpdate): string {
  const dest = t(`permission.destination.${suggestion.destination || 'session'}`)

  switch (suggestion.type) {
    case 'addRules':
      if (suggestion.rules?.length) {
        const rule = suggestion.rules[0]
        if (rule.ruleContent) {
          return t('permission.suggestion.rememberWithRuleTo', {
            tool: rule.toolName,
            rule: rule.ruleContent,
            dest
          })
        }
        return t('permission.suggestion.rememberTo', { tool: rule.toolName, dest })
      }
      break

    case 'replaceRules':
      return t('permission.suggestion.replaceTo', { dest })

    case 'removeRules':
      if (suggestion.rules?.length) {
        return t('permission.suggestion.removeFrom', { tool: suggestion.rules[0].toolName, dest })
      }
      return t('permission.suggestion.removeRulesFrom', { dest })

    case 'setMode':
      const mode = t(`permission.mode.${suggestion.mode || 'default'}`)
      return t('permission.suggestion.switchTo', { mode })

    case 'addDirectories':
      if (suggestion.directories?.length) {
        return t('permission.suggestion.addDirTo', { dir: suggestion.directories[0], dest })
      }
      break

    case 'removeDirectories':
      if (suggestion.directories?.length) {
        return t('permission.suggestion.removeDirFrom', { dir: suggestion.directories[0], dest })
      }
      break
  }

  return t('permission.suggestion.applyTo', { dest })
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

function formatParams(params: Record<string, unknown>): string {
  try {
    return JSON.stringify(params, null, 2)
  } catch {
    return String(params)
  }
}
</script>

<style scoped>
.permission-request {
  margin: 4px 0;
  outline: none;
}

.permission-request:focus .permission-card {
  box-shadow: 0 0 0 2px var(--theme-accent, #0366d6), 0 8px 32px rgba(0, 0, 0, 0.15);
}

.permission-card {
  background: var(--theme-background, #ffffff);
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}

.permission-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
}

.tool-icon {
  font-size: 18px;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
}

.permission-label {
  font-size: 12px;
  background: var(--theme-accent-subtle, #e8f1fb);
  color: var(--theme-accent, #0366d6);
  padding: 2px 8px;
  border-radius: 999px;
  margin-left: auto;
  border: 1px solid var(--theme-accent, #0366d6);
}

.permission-content {
  padding: 16px;
  max-height: 220px;
  overflow-y: auto;
  background: var(--theme-background, #fff);
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
  max-height: 80px;
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
  max-height: 50px;
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
  max-height: 100px;
  overflow-y: auto;
}

.permission-options {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-background, #fff);
}

.btn-option {
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
  text-align: left;
  border: 1px solid var(--theme-border, #e1e4e8);
  background: var(--theme-panel-background, #f6f8fa);
  color: var(--theme-foreground, #24292e);
}

.btn-option:hover {
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
  background: var(--theme-accent-subtle, #e8f1fb);
}

.btn-allow {
  background: var(--theme-accent-subtle, #e8f1fb);
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.btn-allow:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.btn-allow-rule {
  background: var(--theme-panel-background, #f6f8fa);
  border-color: var(--theme-accent, #0366d6);
  color: var(--theme-accent, #0366d6);
}

.btn-allow-rule:hover {
  background: var(--theme-accent, #0366d6);
  color: #fff;
}

.deny-inline {
  display: flex;
  align-items: center;
  gap: 8px;
}

.deny-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 6px;
  font-size: 13px;
  background: var(--theme-background, #fff);
  color: var(--theme-foreground, #24292e);
}

.deny-input:focus {
  outline: none;
  border-color: var(--theme-error, #dc3545);
}

.btn-deny {
  background: #fff;
  border: 1px solid var(--theme-error, #dc3545);
  color: var(--theme-error, #dc3545);
  flex-shrink: 0;
}

.btn-deny:hover {
  background: var(--theme-error, #dc3545);
  color: white;
}

.shortcut-hint {
  font-size: 11px;
  color: var(--theme-muted, #6a737d);
  text-align: right;
  padding: 8px 16px;
  background: var(--theme-panel-background, #f6f8fa);
  border-top: 1px solid var(--theme-border, #e1e4e8);
}
</style>

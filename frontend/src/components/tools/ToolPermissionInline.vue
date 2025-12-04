<template>
  <div class="permission-inline" tabindex="0" ref="containerRef" @keydown.esc="handleDeny">
    <!-- 头部信息 -->
    <div class="permission-header">
      <span class="permission-icon">🔐</span>
      <span class="tool-name">{{ permission.toolName }}</span>
      <span class="permission-label">{{ t('permission.needsAuth') }}</span>
    </div>

    <!-- 选项列表 -->
    <div class="permission-options">
      <!-- 允许（仅本次） -->
      <button class="btn-option btn-allow" @click="handleAllow">
        {{ t('permission.allow') }}
      </button>

      <!-- 动态渲染 permissionSuggestions -->
      <button
        v-for="(suggestion, index) in permission.permissionSuggestions"
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
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import type { PendingPermissionRequest, PermissionUpdate } from '@/types/permission'
import { useI18n } from '@/composables/useI18n'

const { t } = useI18n()

const { permission } = defineProps<{
  permission: PendingPermissionRequest
}>()

const emit = defineEmits<{
  allow: []
  allowWithUpdate: [update: PermissionUpdate]
  deny: [reason: string]
}>()

const containerRef = ref<HTMLElement | null>(null)
const denyReason = ref('')

function handleAllow() {
  emit('allow')
}

function handleAllowWithUpdate(suggestion: PermissionUpdate) {
  emit('allowWithUpdate', suggestion)
}

function handleDeny() {
  emit('deny', denyReason.value)
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

onMounted(() => {
  // 自动聚焦以便接收键盘事件
  containerRef.value?.focus()
})
</script>

<style scoped>
.permission-inline {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  margin-top: 8px;
  background: var(--theme-warning-background, #fff8e6);
  border: 1px solid var(--theme-warning-border, #f0c36d);
  border-radius: 6px;
  outline: none;
}

.permission-inline:focus {
  box-shadow: 0 0 0 2px var(--theme-accent, #0366d6);
}

.permission-header {
  display: flex;
  align-items: center;
  gap: 8px;
}

.permission-icon {
  font-size: 14px;
}

.tool-name {
  font-weight: 600;
  color: var(--theme-foreground, #24292e);
}

.permission-label {
  font-size: 12px;
  color: var(--theme-warning-foreground, #856404);
  background: var(--theme-warning-badge, #ffeeba);
  padding: 2px 6px;
  border-radius: 4px;
}

.permission-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.btn-option {
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.btn-allow {
  background: var(--theme-success, #28a745);
  border: 1px solid var(--theme-success, #28a745);
  color: white;
}

.btn-allow:hover {
  background: var(--theme-success-hover, #218838);
}

.btn-allow-rule {
  background: var(--theme-accent, #0366d6);
  border: 1px solid var(--theme-accent, #0366d6);
  color: white;
}

.btn-allow-rule:hover {
  background: var(--theme-accent-hover, #0256b9);
}

.deny-inline {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 200px;
}

.deny-input {
  flex: 1;
  padding: 5px 8px;
  border: 1px solid var(--theme-border, #e1e4e8);
  border-radius: 4px;
  font-size: 12px;
  background: var(--theme-background, #fff);
  color: var(--theme-foreground, #24292e);
}

.deny-input:focus {
  outline: none;
  border-color: var(--theme-error, #dc3545);
}

.btn-deny {
  background: transparent;
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
}
</style>

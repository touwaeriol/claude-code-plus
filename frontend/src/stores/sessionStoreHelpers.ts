/**
 * Session Store 辅助函数
 */

import type { RpcPermissionMode } from '@/types/rpc'
import type { BackendType } from '@/types/backend'
import type { TabConnectOptions } from '@/composables/useSessionTab'
import { useSettingsStore } from '@/stores/settingsStore'

/**
 * 获取特定后端类型的连接选项
 */
export function getConnectOptionsForBackend(
  backendType: BackendType,
  settingsStore: ReturnType<typeof useSettingsStore>
): TabConnectOptions {
  const globalSettings = settingsStore.settings

  if (backendType === 'claude') {
    return {
      model: globalSettings.claudeModel || 'claude-opus-4-6',
      thinkingLevel: globalSettings.claudeThinkingTokens ?? undefined,
      permissionMode: globalSettings.permissionMode as RpcPermissionMode,
      skipPermissions: globalSettings.skipPermissions,
    }
  } else {
    // Codex 后端
    return {
      model: globalSettings.codexModel || 'gpt-5.2-codex',
      thinkingLevel: undefined, // Codex 使用 effort level，不是 token budget
      reasoningEffort: globalSettings.codexReasoningEffort ?? undefined,
      permissionMode: globalSettings.permissionMode as RpcPermissionMode,
      skipPermissions: globalSettings.skipPermissions,
      sandboxMode: globalSettings.codexSandboxMode || 'workspace-write',
    }
  }
}

/**
 * 获取默认的上下文自动清理开关（按后端区分）
 */
export function getDefaultAutoCleanupContexts(
  backendType: BackendType,
  settingsStore: ReturnType<typeof useSettingsStore>
): boolean {
  if (backendType === 'claude') {
    return settingsStore.settings.claudeDefaultAutoCleanupContexts
  }
  return settingsStore.settings.codexDefaultAutoCleanupContexts
}

/**
 * 从 sessionId 推断后端类型
 *
 * 规则：
 * - Claude sessionId 格式：uuid-v4
 * - Codex sessionId 格式：thread_xxxxx
 */
export function inferBackendType(sessionId: string): BackendType | null {
  if (!sessionId) return null

  // Codex 使用 thread_ 前缀
  if (sessionId.startsWith('thread_')) {
    return 'codex'
  }

  // 默认为 Claude
  return 'claude'
}

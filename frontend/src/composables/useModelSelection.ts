/**
 * 模型选择相关的 composable
 * 处理模型切换、思考级别、权限模式
 *
 * 简化策略：
 * - model/permissionMode/thinkingLevel 切换：直接调用 RPC（立即生效于下一轮对话）
 * - skipPermissions 切换：纯前端行为，不需要重连
 */
import { ref, computed } from 'vue'
import type { PermissionMode } from '@/types/enhancedMessage'
import {
  BaseModel,
  MODEL_CAPABILITIES,
  AVAILABLE_MODELS,
  canToggleThinking,
  getEffectiveThinkingEnabled
} from '@/constants/models'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { ThinkingLevel } from '@/composables/useSessionTab'
import type { ThinkingLevelConfig } from '@/services/jetbrainsRSocket'

// 默认思考级别列表
const DEFAULT_THINKING_LEVELS: ThinkingLevelConfig[] = [
  { id: 'off', name: 'Off', tokens: 0, isCustom: false },
  { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
  { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
]

// 权限模式列表
const PERMISSION_MODES: PermissionMode[] = ['default', 'acceptEdits', 'bypassPermissions', 'plan']

// 模式图标映射
const MODE_ICONS: Record<string, string> = {
  'default': '?',
  'acceptEdits': '✎',
  'bypassPermissions': '∞',
  'plan': '☰'
}

export interface UseModelSelectionOptions {
  /** 初始权限模式 */
  initialPermission?: PermissionMode
  /** 初始跳过权限状态 */
  initialSkipPermissions?: boolean
  /** 跳过权限变更回调 */
  onSkipPermissionsChange?: (skip: boolean) => void
}

export function useModelSelection(options: UseModelSelectionOptions = {}) {
  const sessionStore = useSessionStore()
  const settingsStore = useSettingsStore()

  // 权限相关状态
  const selectedPermissionValue = ref<PermissionMode>(options.initialPermission ?? 'default')
  const skipPermissionsValue = ref(options.initialSkipPermissions ?? false)

  // 思考开关等待状态
  const thinkingTogglePending = ref(false)

  // 当前模型（直接绑定到 Tab 状态）
  const currentModel = computed(() => {
    const modelId = sessionStore.currentTab?.modelId.value
    if (!modelId) {
      return BaseModel.OPUS_45
    }
    // 从 modelId 反查 BaseModel
    const entry = Object.entries(MODEL_CAPABILITIES).find(
      ([, cap]) => cap.modelId === modelId
    )
    return (entry?.[0] as BaseModel) ?? BaseModel.OPUS_45
  })

  // 当前思考级别（直接绑定到 Tab 状态）
  const thinkingLevel = computed((): ThinkingLevel => {
    const tab = sessionStore.currentTab
    if (!tab) {
      return 8096  // 默认 Ultra
    }
    return tab.thinkingLevel.value
  })

  // 当前模型的思考模式
  const currentThinkingMode = computed(() => {
    return MODEL_CAPABILITIES[currentModel.value].thinkingMode
  })

  // 思考开关是否可操作
  const canToggleThinkingComputed = computed(() => {
    return canToggleThinking(currentModel.value)
  })

  // 当前思考是否启用（用于 UI 显示兼容）
  const thinkingEnabled = computed(() => {
    return thinkingLevel.value > 0
  })

  // 可用思考级别列表（从 IDE 设置获取）
  const thinkingLevels = computed((): ThinkingLevelConfig[] => {
    return settingsStore.ideSettings?.thinkingLevels || DEFAULT_THINKING_LEVELS
  })

  // 可用模型列表
  const baseModelOptions = AVAILABLE_MODELS

  /**
   * 获取模型显示名称
   */
  function getBaseModelLabel(model: BaseModel): string {
    return MODEL_CAPABILITIES[model]?.displayName ?? model
  }

  /**
   * 获取模式对应的图标
   */
  function getModeIcon(mode: string): string {
    return MODE_ICONS[mode] ?? '?'
  }

  /**
   * 处理模型切换
   * 直接调用 RPC/重连（立即生效于下一轮对话）
   */
  async function handleBaseModelChange(model: BaseModel) {
    const capability = MODEL_CAPABILITIES[model]

    // 根据模型能力自动设置思考级别
    let newThinkingLevel: ThinkingLevel
    switch (capability.thinkingMode) {
      case 'always':
        newThinkingLevel = 8096  // Ultra
        break
      case 'never':
        newThinkingLevel = 0     // Off
        break
      case 'optional':
        // 保持当前级别，如果当前是 0 则设为默认
        newThinkingLevel = thinkingLevel.value > 0 ? thinkingLevel.value : 8096
        break
    }

    console.log(`🔄 [handleBaseModelChange] 切换模型: ${capability.displayName}, thinkingLevel=${newThinkingLevel}`)

    // 直接调用 updateSettings，它会智能处理 RPC
    const tab = sessionStore.currentTab
    if (tab) {
      await tab.updateSettings({
        model: capability.modelId,
        thinkingLevel: newThinkingLevel
      })
      console.log(`✅ [handleBaseModelChange] 模型切换完成`)
    }
  }

  /**
   * 处理思考级别切换
   * 直接调用 RPC（立即生效于下一轮对话）
   */
  async function handleThinkingLevelChange(level: ThinkingLevel) {
    if (!canToggleThinkingComputed.value) {
      return
    }

    console.log(`🧠 [handleThinkingLevelChange] 切换思考级别: ${level}`)

    // 直接调用 updateSettings
    const tab = sessionStore.currentTab
    if (tab) {
      await tab.updateSettings({ thinkingLevel: level })
      console.log(`✅ [handleThinkingLevelChange] 思考级别切换完成`)
    }
  }

  /**
   * 处理思考开关切换（向后兼容）
   */
  async function handleThinkingToggle(enabled: boolean) {
    const level: ThinkingLevel = enabled ? 8096 : 0
    await handleThinkingLevelChange(level)
  }

  /**
   * 切换思考级别（用于键盘快捷键）
   * 在 Off -> Think -> Ultra 之间循环
   */
  async function toggleThinkingEnabled(source: 'click' | 'keyboard' = 'click') {
    // 检查是否可以切换
    if (!canToggleThinkingComputed.value) {
      console.log(`🧠 [ThinkingToggle] ${source} - 当前模型不支持切换思考`)
      return
    }

    if (thinkingTogglePending.value) return

    // 在三个级别之间循环：0 -> 2048 -> 8096 -> 0
    const levels: ThinkingLevel[] = [0, 2048, 8096]
    const currentIndex = levels.indexOf(thinkingLevel.value)
    const nextIndex = (currentIndex + 1) % levels.length
    const nextLevel = levels[nextIndex]

    console.log(`🧠 [ThinkingToggle] ${source} -> ${nextLevel}`)
    await handleThinkingLevelChange(nextLevel)
  }

  /**
   * 处理跳过权限开关切换
   * skipPermissions 是纯前端行为，只更新本地状态，不需要重连
   */
  function handleSkipPermissionsChange(enabled: boolean) {
    console.log(`🔓 [handleSkipPermissionsChange] 切换跳过权限: ${enabled}`)
    skipPermissionsValue.value = enabled

    // 直接更新 Tab 的本地状态（不触发重连）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting('skipPermissions', enabled)
      console.log(`✅ [handleSkipPermissionsChange] 跳过权限已更新（纯前端，无需重连）`)
    }

    // 保存到全局设置（供新 Tab 继承）
    settingsStore.saveSettings({ skipPermissions: enabled })

    // 触发回调
    options.onSkipPermissionsChange?.(enabled)
  }

  /**
   * 轮换切换权限模式
   * 直接调用 RPC（立即生效于下一轮对话）
   */
  async function cyclePermissionMode() {
    const currentIndex = PERMISSION_MODES.indexOf(selectedPermissionValue.value)
    const nextIndex = (currentIndex + 1) % PERMISSION_MODES.length
    const nextMode = PERMISSION_MODES[nextIndex]

    console.log(`🔄 [cyclePermissionMode] 切换权限模式: ${nextMode}`)

    // 直接调用 RPC
    const tab = sessionStore.currentTab
    if (tab) {
      await tab.setPermissionMode(nextMode)
      console.log(`✅ [cyclePermissionMode] 权限模式切换完成`)
    }

    // 保存到全局设置（供新 Tab 继承）
    settingsStore.updatePermissionMode(nextMode)
  }

  /**
   * 设置权限模式
   * 直接调用 RPC（立即生效于下一轮对话）
   */
  async function setPermissionMode(mode: PermissionMode) {
    console.log(`🔒 [setPermissionMode] 设置权限模式: ${mode}`)

    // 直接调用 RPC
    const tab = sessionStore.currentTab
    if (tab) {
      await tab.setPermissionMode(mode)
      console.log(`✅ [setPermissionMode] 权限模式切换完成`)
    }

    // 保存到全局设置（供新 Tab 继承）
    settingsStore.updatePermissionMode(mode)
  }

  /**
   * 更新权限状态（用于 watch props）
   */
  function updatePermission(permission: PermissionMode) {
    selectedPermissionValue.value = permission
  }

  /**
   * 更新跳过权限状态（用于 watch props）
   */
  function updateSkipPermissions(skip: boolean) {
    skipPermissionsValue.value = skip
  }

  return {
    // 状态
    currentModel,
    thinkingLevel,
    thinkingLevels,
    currentThinkingMode,
    canToggleThinkingComputed,
    thinkingEnabled,
    thinkingTogglePending,
    selectedPermissionValue,
    skipPermissionsValue,
    // 常量
    baseModelOptions,
    PERMISSION_MODES,
    // 方法
    getBaseModelLabel,
    getModeIcon,
    handleBaseModelChange,
    handleThinkingLevelChange,
    handleThinkingToggle,
    toggleThinkingEnabled,
    handleSkipPermissionsChange,
    cyclePermissionMode,
    setPermissionMode,
    updatePermission,
    updateSkipPermissions
  }
}

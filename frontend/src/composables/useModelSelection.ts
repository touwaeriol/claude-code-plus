/**
 * 模型选择相关的 composable
 * 处理模型切换、思考开关、权限模式
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
import { SETTING_KEYS } from '@/composables/useSessionTab'

// 权限模式列表
const PERMISSION_MODES: PermissionMode[] = ['default', 'acceptEdits', 'bypassPermissions', 'plan', 'dontAsk']

// 模式图标映射
const MODE_ICONS: Record<string, string> = {
  'default': '?',
  'acceptEdits': '✎',
  'bypassPermissions': '∞',
  'plan': '☰',
  'dontAsk': '🔇'
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

  // 当前思考开关状态（直接绑定到 Tab 状态）
  const currentThinkingEnabled = computed(() => {
    const tab = sessionStore.currentTab
    if (!tab) {
      return MODEL_CAPABILITIES[BaseModel.OPUS_45].defaultThinkingEnabled
    }
    return tab.thinkingEnabled.value
  })

  // 当前模型的思考模式
  const currentThinkingMode = computed(() => {
    return MODEL_CAPABILITIES[currentModel.value].thinkingMode
  })

  // 思考开关是否可操作
  const canToggleThinkingComputed = computed(() => {
    return canToggleThinking(currentModel.value)
  })

  // 当前思考开关状态（用于 UI 显示）
  const thinkingEnabled = computed(() => {
    return getEffectiveThinkingEnabled(currentModel.value, currentThinkingEnabled.value)
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
   * 保存到 pending，下次 query 时才应用
   */
  function handleBaseModelChange(model: BaseModel) {
    const capability = MODEL_CAPABILITIES[model]

    // 根据模型能力自动设置思考开关
    let newThinkingEnabled: boolean
    switch (capability.thinkingMode) {
      case 'always':
        newThinkingEnabled = true
        break
      case 'never':
        newThinkingEnabled = false
        break
      case 'optional':
        newThinkingEnabled = capability.defaultThinkingEnabled
        break
    }

    console.log(`🔄 [handleBaseModelChange] 切换模型: ${capability.displayName}, thinking=${newThinkingEnabled}`)

    // 保存到 pending（下次 query 时应用）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting(SETTING_KEYS.MODEL, capability.modelId)
      tab.setPendingSetting(SETTING_KEYS.THINKING_ENABLED, newThinkingEnabled)
      console.log(`📝 [handleBaseModelChange] 已保存到 pending，下次 query 时应用`)
    }
  }

  /**
   * 处理思考开关切换
   * 只保存到 pending，下次 query 时才应用
   */
  function handleThinkingToggle(enabled: boolean) {
    if (!canToggleThinkingComputed.value) {
      return
    }

    console.log(`🧠 [handleThinkingToggle] 切换思考: ${enabled}`)

    // 保存到 pending（下次 query 时应用）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting(SETTING_KEYS.THINKING_ENABLED, enabled)
      console.log(`📝 [handleThinkingToggle] 已保存到 pending，下次 query 时应用`)
    }
  }

  /**
   * 切换思考开关（简化版本，用于键盘快捷键）
   */
  async function toggleThinkingEnabled(source: 'click' | 'keyboard' = 'click') {
    // 检查是否可以切换
    if (!canToggleThinkingComputed.value) {
      console.log(`🧠 [ThinkingToggle] ${source} - 当前模型不支持切换思考`)
      return
    }

    if (thinkingTogglePending.value) return

    // 调用处理函数
    const nextValue = !thinkingEnabled.value
    console.log(`🧠 [ThinkingToggle] ${source} -> ${nextValue}`)
    handleThinkingToggle(nextValue)
  }

  /**
   * 处理跳过权限开关切换
   * 只保存到 pending，下次 query 时才应用
   */
  function handleSkipPermissionsChange(enabled: boolean) {
    console.log(`🔓 [handleSkipPermissionsChange] 切换跳过权限: ${enabled}`)
    skipPermissionsValue.value = enabled

    // 保存到 pending（下次 query 时应用）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting(SETTING_KEYS.SKIP_PERMISSIONS, enabled)
      console.log(`📝 [handleSkipPermissionsChange] 已保存到 pending，下次 query 时应用`)
    }

    // 保存到全局设置（供新 Tab 继承）
    settingsStore.saveSettings({ skipPermissions: enabled })

    // 触发回调
    options.onSkipPermissionsChange?.(enabled)
  }

  /**
   * 轮换切换权限模式
   * 直接保存到 pending，下次 query 时应用
   */
  function cyclePermissionMode() {
    const currentIndex = PERMISSION_MODES.indexOf(selectedPermissionValue.value)
    const nextIndex = (currentIndex + 1) % PERMISSION_MODES.length
    const nextMode = PERMISSION_MODES[nextIndex]

    console.log(`🔄 [cyclePermissionMode] 切换权限模式: ${nextMode}`)

    // 保存到 pending（下次 query 时应用）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting(SETTING_KEYS.PERMISSION_MODE, nextMode)
      console.log(`📝 [cyclePermissionMode] 已保存到 pending，下次 query 时应用`)
    }

    // 保存到全局设置（供新 Tab 继承）
    settingsStore.updatePermissionMode(nextMode)
  }

  /**
   * 设置权限模式
   * 直接保存到 pending，下次 query 时应用
   */
  function setPermissionMode(mode: PermissionMode) {
    console.log(`🔒 [setPermissionMode] 设置权限模式: ${mode}`)

    // 保存到 pending（下次 query 时应用）
    const tab = sessionStore.currentTab
    if (tab) {
      tab.setPendingSetting(SETTING_KEYS.PERMISSION_MODE, mode)
      console.log(`📝 [setPermissionMode] 已保存到 pending，下次 query 时应用`)
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
    currentThinkingEnabled,
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
    handleThinkingToggle,
    toggleThinkingEnabled,
    handleSkipPermissionsChange,
    cyclePermissionMode,
    setPermissionMode,
    updatePermission,
    updateSkipPermissions
  }
}

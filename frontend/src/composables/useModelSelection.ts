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
  getAllModels,
  getModelCapability,
  getModelDisplayName,
  type ModelInfo
} from '@/constants/models'
import { useSessionStore } from '@/stores/sessionStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { ThinkingLevel } from '@/composables/useSessionTab'
import type { ThinkingLevelConfig } from '@/services/ideaRSocket'

// 默认思考级别列表
const DEFAULT_THINKING_LEVELS: ThinkingLevelConfig[] = [
  { id: 'off', name: 'Off', tokens: 0, isCustom: false },
  { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
  { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
]

// 默认权限模式列表（后备，当后端未返回时使用）
const DEFAULT_PERMISSION_MODES: PermissionMode[] = ['default', 'acceptEdits', 'bypassPermissions', 'plan']

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

  // 乐观更新：临时存储用户选择的模型（在 RPC 完成前立即更新 UI）
  const optimisticModel = ref<string | null>(null)

  // 当前后端类型（从当前 Tab 读取，确保与 Header 显示一致）
  // 优先使用当前 Tab 的后端类型，如果没有 Tab 则使用全局默认值
  const currentBackendType = computed(() => {
    const tabBackendType = sessionStore.currentTab?.backendType?.value
    const globalDefault = settingsStore.currentBackendType
    console.log('🔄 [currentBackendType] Tab:', tabBackendType, 'Global:', globalDefault)
    return tabBackendType ?? globalDefault
  })

  // 当前模型（直接绑定到 Tab 状态，支持内置和自定义模型）
  const currentModel = computed((): string => {
    // 优先使用乐观更新的值（立即响应用户操作）
    if (optimisticModel.value) {
      return optimisticModel.value
    }

    const backendType = currentBackendType.value
    const modelId = sessionStore.currentTab?.modelId.value

    if (backendType === 'codex') {
      // Codex 后端：modelId 即为模型 ID
      if (!modelId) {
        return settingsStore.settings.codexModel || 'gpt-5.2-codex'
      }
      return modelId
    }

    // Claude 后端
    if (!modelId) {
      return settingsStore.settings.claudeModel
    }
    return modelId
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
    const capability = getModelCapability(currentModel.value)
    return capability.thinkingMode
  })

  // 思考开关是否可操作
  const canToggleThinkingComputed = computed(() => {
    return currentThinkingMode.value === 'optional'
  })

  // 当前思考是否启用（用于 UI 显示兼容）
  const thinkingEnabled = computed(() => {
    return thinkingLevel.value > 0
  })

  // 可用思考级别列表（从 IDE 设置获取）
  const thinkingLevels = computed((): ThinkingLevelConfig[] => {
    return settingsStore.ideSettings?.thinkingLevels || DEFAULT_THINKING_LEVELS
  })

  // 可用权限模式列表（从 IDE 设置获取）
  const permissionModes = computed((): PermissionMode[] => {
    const options = settingsStore.ideSettings?.permissionModeOptions
    if (options && options.length > 0) {
      return options.map(opt => opt.id as PermissionMode)
    }
    return DEFAULT_PERMISSION_MODES
  })

  // ========== 分离的模型列表（根据后端类型） ==========

  // Claude 模型列表（始终返回 Claude 模型）
  const claudeModelOptions = computed((): ModelInfo[] => {
    return getAllModels()
  })

  // Codex 模型列表（始终返回 Codex 模型）
  const codexModelOptions = computed((): ModelInfo[] => {
    return settingsStore.getModelsForBackend('codex').map(m => ({
      modelId: m.modelId,
      displayName: m.displayName,
      isBuiltIn: true,
      description: m.description
    }))
  })

  // 当前选中的 Codex 模型（用于 Codex 模型选择器）
  const selectedCodexModel = computed({
    get: () => {
      const modelId = sessionStore.currentTab?.modelId.value
      return modelId || settingsStore.settings.codexModel || 'gpt-5.2-codex'
    },
    set: (_val: string) => {
      // setter 由 handleCodexModelChange 处理
    }
  })

  // 兼容旧代码：baseModelOptions 根据后端类型动态返回
  const baseModelOptions = computed((): ModelInfo[] => {
    const backendType = currentBackendType.value
    return backendType === 'codex' ? codexModelOptions.value : claudeModelOptions.value
  })

  /**
   * 获取模型显示名称（支持内置和自定义模型）
   */
  function getBaseModelLabel(modelId: string): string {
    return getModelDisplayName(modelId)
  }

  /**
   * 获取模式对应的图标
   */
  function getModeIcon(mode: string): string {
    return MODE_ICONS[mode] ?? '?'
  }

  /**
   * 处理模型切换（支持内置和自定义模型）
   * 使用乐观更新：立即更新 UI，然后异步调用 RPC
   */
  async function handleBaseModelChange(modelId: string) {
    const capability = getModelCapability(modelId)

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

    // 🎯 乐观更新：立即设置选中状态（UI 立即响应）
    optimisticModel.value = modelId

    // 异步调用 RPC（不阻塞 UI）
    const tab = sessionStore.currentTab
    if (tab) {
      try {
        await tab.updateSettings({
          model: capability.modelId,
          thinkingLevel: newThinkingLevel
        })
        console.log(`✅ [handleBaseModelChange] 模型切换完成`)
      } catch (error) {
        console.error(`❌ [handleBaseModelChange] 模型切换失败:`, error)
      } finally {
        // ✅ RPC 完成后，清除乐观更新（仅当值未被后续操作覆盖时）
        // 防止旧请求清除新的乐观值
        if (optimisticModel.value === modelId) {
          optimisticModel.value = null
        }
      }
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
    const modes = permissionModes.value
    const currentIndex = modes.indexOf(selectedPermissionValue.value)
    const nextIndex = (currentIndex + 1) % modes.length
    const nextMode = modes[nextIndex]

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
    claudeModelOptions,  // Claude 模型列表
    codexModelOptions,   // Codex 模型列表
    selectedCodexModel,  // 当前选中的 Codex 模型
    currentBackendType,  // 当前后端类型
    permissionModes,     // 权限模式列表（从后端获取）
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

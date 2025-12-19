import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ideaBridge } from '@/services/ideaBridge'
import { jetbrainsRSocket, type IdeSettings } from '@/services/jetbrainsRSocket'
import { DEFAULT_SETTINGS, type Settings, PermissionMode } from '@/types/settings'
import { BaseModel, MODEL_CAPABILITIES, migrateModelSettings, findBaseModelByModelId } from '@/constants/models'

/**
 * HTTP 获取的默认设置（用于浏览器模式）
 */
interface HttpDefaultSettings {
  defaultModelId: string
  defaultBypassPermissions: boolean
  includePartialMessages: boolean
  // 思考配置（新增）
  defaultThinkingLevel: string  // 思考等级枚举名称
  defaultThinkingTokens: number // 思考 token 数量
}

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<Settings>({ ...DEFAULT_SETTINGS })
  const ideSettings = ref<IdeSettings | null>(null)
  const loading = ref(false)
  const showPanel = ref(false)
  let settingsChangeUnsubscribe: (() => void) | null = null

  /**
   * 迁移旧设置到新格式
   */
  function migrateSettings(rawSettings: any): Settings {
    // 检查是否需要迁移模型设置
    if (rawSettings.model && !(rawSettings.model in BaseModel)) {
      console.log('🔄 [migrateSettings] 检测到旧模型格式，开始迁移:', rawSettings.model)
      const migrated = migrateModelSettings(rawSettings.model)
      return {
        ...DEFAULT_SETTINGS,
        ...rawSettings,
        model: migrated.model,
        thinkingEnabled: migrated.thinkingEnabled
      }
    }
    return {
      ...DEFAULT_SETTINGS,
      ...rawSettings
    }
  }

  /**
   * 加载设置
   */
  async function loadSettings() {
    loading.value = true
    try {
      console.log('⚙️ Loading settings...')
      const response = await ideaBridge.query('settings.get')

      if (response.success && response.data?.settings) {
        // 合并远程设置到本地（包含迁移逻辑）
        settings.value = migrateSettings(response.data.settings)
        console.log('✅ Settings loaded:', settings.value)
      } else {
        console.warn('⚠️ Failed to load settings, using defaults')
      }
    } catch (error) {
      console.error('❌ Error loading settings:', error)
    } finally {
      loading.value = false
    }
  }

  /**
   * 从 IDEA 加载 IDE 设置
   */
  async function loadIdeSettings() {
    try {
      console.log('⚙️ Loading IDE settings from JetBrains...')
      const result = await jetbrainsRSocket.getSettings()

      if (result) {
        ideSettings.value = result
        console.log('✅ IDE settings loaded:', result)
        applyIdeSettings(result)
      } else {
        console.warn('⚠️ Failed to load IDE settings')
      }
    } catch (error) {
      console.error('❌ Error loading IDE settings:', error)
    }
  }

  /**
   * 应用 IDE 设置到前端
   * 将后端 IDEA 的默认设置应用为前端的默认设置
   */
  function applyIdeSettings(newIdeSettings: IdeSettings) {
    const updates: Partial<Settings> = {}

    // 1. 应用默认模型设置
    if (newIdeSettings.defaultModelId) {
      const baseModel = findBaseModelByModelId(newIdeSettings.defaultModelId)
      if (baseModel) {
        updates.model = baseModel
        console.log('🎯 [IdeSettings] 应用默认模型:', baseModel)
      } else {
        console.warn('⚠️ [IdeSettings] 未知的模型 ID:', newIdeSettings.defaultModelId)
      }
    }

    // 2. 应用思考配置
    const thinkingLevelId = newIdeSettings.defaultThinkingLevelId || 'ultra'
    const thinkingTokens = newIdeSettings.defaultThinkingTokens ?? 8096
    updates.thinkingEnabled = thinkingLevelId !== 'off' && thinkingTokens > 0
    updates.maxThinkingTokens = thinkingTokens
    console.log('🧠 [IdeSettings] 思考配置:', {
      levelId: thinkingLevelId,
      tokens: thinkingTokens,
      enabled: updates.thinkingEnabled,
      levels: newIdeSettings.thinkingLevels
    })

    // 3. 应用 ByPass 权限设置（同步到当前会话）
    const newBypassValue = newIdeSettings.defaultBypassPermissions ?? false
    updates.skipPermissions = newBypassValue
    console.log('🔓 [IdeSettings] ByPass 权限设置:', newBypassValue)

    // 4. 应用 includePartialMessages 设置
    if (newIdeSettings.includePartialMessages !== undefined) {
      updates.includePartialMessages = newIdeSettings.includePartialMessages
      console.log('📡 [IdeSettings] Include Partial Messages:', newIdeSettings.includePartialMessages)
    }

    // 5. 应用权限模式设置
    if (newIdeSettings.permissionMode) {
      updates.permissionMode = newIdeSettings.permissionMode as PermissionMode
      console.log('🔒 [IdeSettings] 权限模式:', newIdeSettings.permissionMode)
    }

    // 如果有更新，合并到设置中
    if (Object.keys(updates).length > 0) {
      settings.value = {
        ...settings.value,
        ...updates
      }
      console.log('✅ [IdeSettings] 已应用 IDE 默认设置:', updates)
    }
  }

  /**
   * 处理 IDE 设置变更（从后端推送）
   */
  function handleIdeSettingsChange(newIdeSettings: IdeSettings) {
    console.log('📥 [IdeSettings] 收到设置变更推送:', newIdeSettings)
    ideSettings.value = newIdeSettings
    applyIdeSettings(newIdeSettings)
  }

  /**
   * 初始化 IDE 设置监听
   */
  function initIdeSettingsListener() {
    if (settingsChangeUnsubscribe) {
      settingsChangeUnsubscribe()
    }
    settingsChangeUnsubscribe = jetbrainsRSocket.onSettingsChange(handleIdeSettingsChange)
    console.log('👂 [IdeSettings] 已注册设置变更监听器')
  }

  /**
   * 清理 IDE 设置监听
   */
  function cleanupIdeSettingsListener() {
    if (settingsChangeUnsubscribe) {
      settingsChangeUnsubscribe()
      settingsChangeUnsubscribe = null
      console.log('🧹 [IdeSettings] 已移除设置变更监听器')
    }
  }

  /**
   * 从 HTTP API 加载默认设置（用于浏览器模式）
   *
   * 当不在 IDE 环境中时，通过 HTTP API 获取后端配置的默认设置
   */
  async function loadDefaultSettings() {
    try {
      console.log('⚙️ Loading default settings from HTTP API...')
      const response = await ideaBridge.query('settings.getDefault')

      if (response.success && response.data) {
        const httpSettings = response.data as HttpDefaultSettings
        const updates: Partial<Settings> = {}

        // 1. 应用默认模型设置
        if (httpSettings.defaultModelId) {
          const baseModel = findBaseModelByModelId(httpSettings.defaultModelId)
          if (baseModel) {
            updates.model = baseModel
            console.log('🎯 [DefaultSettings] 应用默认模型:', baseModel)
          } else {
            console.warn('⚠️ [DefaultSettings] 未知的模型 ID:', httpSettings.defaultModelId)
          }
        }

        // 2. 应用思考配置
        const thinkingLevel = httpSettings.defaultThinkingLevel || 'HIGH'
        const thinkingTokens = httpSettings.defaultThinkingTokens ?? 8192
        updates.thinkingEnabled = thinkingLevel !== 'OFF' && thinkingTokens > 0
        updates.maxThinkingTokens = thinkingTokens
        console.log('🧠 [DefaultSettings] 思考配置:', {
          level: thinkingLevel,
          tokens: thinkingTokens,
          enabled: updates.thinkingEnabled
        })

        // 3. 应用 ByPass 权限设置
        updates.skipPermissions = httpSettings.defaultBypassPermissions ?? false
        console.log('🔓 [DefaultSettings] ByPass 权限设置:', updates.skipPermissions)

        // 4. 应用 includePartialMessages 设置
        if (httpSettings.includePartialMessages !== undefined) {
          updates.includePartialMessages = httpSettings.includePartialMessages
          console.log('📡 [DefaultSettings] Include Partial Messages:', httpSettings.includePartialMessages)
        }

        // 合并到设置中
        if (Object.keys(updates).length > 0) {
          settings.value = {
            ...settings.value,
            ...updates
          }
          console.log('✅ [DefaultSettings] 已应用默认设置:', updates)
        }
      } else {
        console.warn('⚠️ Failed to load default settings from HTTP API')
      }
    } catch (error) {
      console.error('❌ Error loading default settings:', error)
    }
  }

  /**
   * 保存设置（仅本地）
   *
   * 延迟同步策略：设置变更只保存到本地 ref，
   * 实际同步在发送消息时由 sessionStore.syncSettingsIfNeeded() 通过 RPC 进行
   */
  function saveSettings(newSettings: Partial<Settings>) {
    settings.value = {
      ...settings.value,
      ...newSettings
    }
    console.log('💾 Settings saved locally:', newSettings)
    return true
  }

  /**
   * 更新模型
   */
  async function updateModel(model: Settings['model']) {
    return await saveSettings({ model })
  }

  /**
   * 更新权限模式
   */
  async function updatePermissionMode(mode: PermissionMode | string) {
    return await saveSettings({ permissionMode: mode as PermissionMode })
  }

  /**
   * 更新最大轮次
   */
  async function updateMaxTurns(maxTurns: number | null) {
    return await saveSettings({ maxTurns })
  }

  /**
   * 更新思考开关
   */
  async function updateThinkingEnabled(enabled: boolean) {
    return await saveSettings({ thinkingEnabled: enabled })
  }

  /**
   * 同时更新模型和思考开关
   */
  async function updateModelWithThinking(model: BaseModel, thinkingEnabled?: boolean) {
    const capability = MODEL_CAPABILITIES[model]
    const effectiveThinking = thinkingEnabled ?? capability.defaultThinkingEnabled
    return await saveSettings({
      model,
      thinkingEnabled: effectiveThinking
    })
  }

  /**
   * 重置为默认设置
   */
  async function resetToDefaults() {
    return await saveSettings(DEFAULT_SETTINGS)
  }

  /**
   * 打开设置面板
   */
  function openPanel() {
    showPanel.value = true
  }

  /**
   * 关闭设置面板
   */
  function closePanel() {
    showPanel.value = false
  }

  /**
   * 切换设置面板
   */
  function togglePanel() {
    showPanel.value = !showPanel.value
  }

  return {
    settings,
    ideSettings,
    loading,
    showPanel,
    loadSettings,
    loadIdeSettings,
    loadDefaultSettings,
    initIdeSettingsListener,
    cleanupIdeSettingsListener,
    saveSettings,
    updateModel,
    updatePermissionMode,
    updateMaxTurns,
    updateThinkingEnabled,
    updateModelWithThinking,
    resetToDefaults,
    openPanel,
    closePanel,
    togglePanel
  }
})

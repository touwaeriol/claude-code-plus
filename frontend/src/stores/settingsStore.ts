import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ideaBridge } from '@/services/ideaBridge'
import { DEFAULT_SETTINGS, type Settings, PermissionMode } from '@/types/settings'
import { BaseModel, MODEL_CAPABILITIES, migrateModelSettings } from '@/constants/models'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<Settings>({ ...DEFAULT_SETTINGS })
  const loading = ref(false)
  const showPanel = ref(false)

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
  async function updatePermissionMode(mode: PermissionMode) {
    return await saveSettings({ permissionMode: mode })
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
    loading,
    showPanel,
    loadSettings,
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

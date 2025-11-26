import { ref } from 'vue'
import { defineStore } from 'pinia'
import { ideaBridge } from '@/services/ideaBridge'
import { DEFAULT_SETTINGS, type Settings, PermissionMode } from '@/types/settings'

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<Settings>({ ...DEFAULT_SETTINGS })
  const loading = ref(false)
  const showPanel = ref(false)

  /**
   * 加载设置
   */
  async function loadSettings() {
    loading.value = true
    try {
      console.log('⚙️ Loading settings...')
      const response = await ideaBridge.query('settings.get')

      if (response.success && response.data?.settings) {
        // 合并远程设置到本地
        settings.value = {
          ...DEFAULT_SETTINGS,
          ...response.data.settings
        }
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
   * 保存设置
   */
  async function saveSettings(newSettings: Partial<Settings>) {
    loading.value = true
    try {
      console.log('💾 Saving settings...', newSettings)

      // 更新本地设置
      settings.value = {
        ...settings.value,
        ...newSettings
      }

      // 保存到后端
      const response = await ideaBridge.query('settings.update', {
        settings: settings.value
      })

      if (response.success) {
        console.log('✅ Settings saved')
        return true
      } else {
        console.error('❌ Failed to save settings:', response.error)
        return false
      }
    } catch (error) {
      console.error('❌ Error saving settings:', error)
      return false
    } finally {
      loading.value = false
    }
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
    resetToDefaults,
    openPanel,
    closePanel,
    togglePanel
  }
})

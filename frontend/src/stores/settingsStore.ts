import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { ideaBridge } from '@/services/ideaBridge'
import { jetbrainsRSocket, type IdeSettings as BaseIdeSettings } from '@/services/jetbrainsRSocket'
import { PermissionMode } from '@/types/settings'
import type {
  BackendType,
  CodexReasoningEffort,
  SandboxMode,
  ClaudeBackendConfig,
  CodexBackendConfig
} from '@/types/backend'
import {
  BackendTypes,
  DEFAULT_CLAUDE_CONFIG,
  DEFAULT_CODEX_CONFIG
} from '@/types/backend'
import type { ThinkingConfig } from '@/types/thinking'
import { createClaudeThinkingConfig, createCodexThinkingConfig } from '@/types/thinking'
import { CLAUDE_MODELS, CODEX_MODELS, type BackendModelInfo } from '@/services/backendCapabilities'

/**
 * 多后端设置接口
 */
export interface Settings {
  // === 通用设置 ===
  defaultBackendType: BackendType           // 默认后端类型
  permissionMode: PermissionMode            // 权限模式（通用）
  skipPermissions: boolean                  // Bypass 模式（通用）
  includePartialMessages: boolean           // 包含部分消息（通用）
  maxTurns: number | null                   // 最大轮次（通用）

  // === 后端配置对象 ===
  claudeConfig: ClaudeBackendConfig         // Claude 后端配置
  codexConfig: CodexBackendConfig           // Codex 后端配置

  // === Claude 特定设置（向后兼容，将迁移到 claudeConfig）===
  claudeModel: string                       // Claude 模型 ID
  claudeThinkingEnabled: boolean            // 是否启用思考
  claudeThinkingTokens: number              // 思考 token 预算

  // === Codex 特定设置（向后兼容，将迁移到 codexConfig）===
  codexModel: string                        // Codex 模型 ID
  codexReasoningEffort: CodexReasoningEffort | null  // 推理努力级别
  codexReasoningSummary: 'auto' | 'concise' | 'detailed' | 'none'  // 推理总结模式
  codexSandboxMode: SandboxMode             // 沙盒模式
  codexApiKey?: string                      // Codex API Key（可选）

  // === 兼容旧版本（迁移后可移除）===
  model?: string                            // @deprecated 使用 claudeModel
  thinkingEnabled?: boolean                 // @deprecated 使用 claudeThinkingEnabled
  maxThinkingTokens?: number                // @deprecated 使用 claudeThinkingTokens
}

/**
 * 扩展的 IDE 设置接口（支持多后端）
 */
export interface IdeSettings extends BaseIdeSettings {
  // === 通用 ===
  defaultBackendType?: BackendType
  permissionMode?: string  // 传输时使用字符串，会转换为 PermissionMode
  defaultBypassPermissions?: boolean
  includePartialMessages?: boolean

  // === Claude 特定 ===
  claudeDefaultModelId?: string
  claudeThinkingLevelId?: string
  claudeThinkingTokens?: number
  claudeThinkingLevels?: Array<{ levelId: string; tokens: number }>

  // === Codex 特定 ===
  codexDefaultModelId?: string
  codexReasoningEffort?: CodexReasoningEffort
  codexSandboxMode?: SandboxMode
  codexApiKey?: string

  // === 兼容旧版本 ===
  defaultModelId?: string
  defaultThinkingLevelId?: string
  defaultThinkingTokens?: number
}

/**
 * HTTP 获取的默认设置（用于浏览器模式）
 */
interface HttpDefaultSettings {
  // 通用
  defaultBackendType?: BackendType
  defaultBypassPermissions: boolean
  includePartialMessages: boolean

  // Claude 配置
  claudeDefaultModelId?: string
  claudeDefaultThinkingLevel?: string
  claudeDefaultThinkingTokens?: number

  // Codex 配置
  codexDefaultModelId?: string
  codexReasoningEffort?: CodexReasoningEffort
  codexSandboxMode?: SandboxMode

  // 兼容旧版本
  defaultModelId?: string
  defaultThinkingLevel?: string
  defaultThinkingTokens?: number
}

/**
 * 默认设置
 */
const DEFAULT_SETTINGS: Settings = {
  // 通用设置
  defaultBackendType: 'claude',
  permissionMode: 'default',
  skipPermissions: false,
  includePartialMessages: true,
  maxTurns: null,

  // 后端配置对象
  claudeConfig: {
    ...DEFAULT_CLAUDE_CONFIG,
    permissionMode: 'default',
    skipPermissions: false,
    maxTurns: null,
  },
  codexConfig: {
    ...DEFAULT_CODEX_CONFIG,
    permissionMode: 'default',
    skipPermissions: false,
    maxTurns: null,
  },

  // Claude 设置（向后兼容）
  claudeModel: 'claude-opus-4-5-20251101',
  claudeThinkingEnabled: true,
  claudeThinkingTokens: 8096,

  // Codex 设置（向后兼容）
  codexModel: 'gpt-5.1-codex-max',
  codexReasoningEffort: 'medium',
  codexReasoningSummary: 'auto',
  codexSandboxMode: 'workspace-write',
}

export const useSettingsStore = defineStore('settings', () => {
  const settings = ref<Settings>({ ...DEFAULT_SETTINGS })
  const ideSettings = ref<IdeSettings | null>(null)
  const loading = ref(false)
  const showPanel = ref(false)
  let settingsChangeUnsubscribe: (() => void) | null = null

  // 后端特定的模型列表
  const claudeModels = ref<BackendModelInfo[]>(CLAUDE_MODELS)
  const codexModels = ref<BackendModelInfo[]>(CODEX_MODELS)

  /**
   * 迁移旧设置到新格式
   */
  function migrateSettings(rawSettings: any): Settings {
    // 迁移旧的单后端设置到新的多后端结构
    return {
      ...DEFAULT_SETTINGS,
      ...rawSettings,

      // 迁移旧字段到 Claude 特定字段
      claudeModel: rawSettings.claudeModel || rawSettings.model || DEFAULT_SETTINGS.claudeModel,
      claudeThinkingEnabled: rawSettings.claudeThinkingEnabled ?? rawSettings.thinkingEnabled ?? true,
      claudeThinkingTokens: rawSettings.claudeThinkingTokens ?? rawSettings.maxThinkingTokens ?? 8096,

      // 新字段使用默认值（如果没有）
      codexModel: rawSettings.codexModel || DEFAULT_SETTINGS.codexModel,
      codexReasoningEffort: rawSettings.codexReasoningEffort || DEFAULT_SETTINGS.codexReasoningEffort,
      codexReasoningSummary: rawSettings.codexReasoningSummary || DEFAULT_SETTINGS.codexReasoningSummary,
      codexSandboxMode: rawSettings.codexSandboxMode || DEFAULT_SETTINGS.codexSandboxMode,

      // 保持通用设置
      defaultBackendType: rawSettings.defaultBackendType || 'claude',
      permissionMode: rawSettings.permissionMode || DEFAULT_SETTINGS.permissionMode,
      skipPermissions: rawSettings.skipPermissions ?? DEFAULT_SETTINGS.skipPermissions,
      includePartialMessages: rawSettings.includePartialMessages ?? DEFAULT_SETTINGS.includePartialMessages,
      maxTurns: rawSettings.maxTurns ?? DEFAULT_SETTINGS.maxTurns,
    }
  }

  /**
   * 根据后端类型获取模型列表
   */
  function getModelsForBackend(backendType: BackendType): BackendModelInfo[] {
    return backendType === 'claude' ? claudeModels.value : codexModels.value
  }

  /**
   * 根据后端类型获取当前选中的模型
   */
  function getCurrentModelForBackend(backendType: BackendType): string {
    return backendType === 'claude'
      ? settings.value.claudeModel
      : settings.value.codexModel
  }

  /**
   * 设置后端的模型
   */
  function setModelForBackend(backendType: BackendType, modelId: string) {
    if (backendType === 'claude') {
      settings.value.claudeModel = modelId
    } else {
      settings.value.codexModel = modelId
    }
    console.log(`🎯 [${backendType}] 模型已更新:`, modelId)
  }

  /**
   * 获取当前后端的思考配置
   */
  function getThinkingConfigForBackend(backendType: BackendType): ThinkingConfig {
    if (backendType === 'claude') {
      return createClaudeThinkingConfig(
        settings.value.claudeThinkingEnabled,
        settings.value.claudeThinkingTokens
      )
    } else {
      return createCodexThinkingConfig(
        settings.value.codexReasoningEffort,
        settings.value.codexReasoningSummary
      )
    }
  }

  /**
   * 设置后端的思考配置
   */
  function setThinkingConfigForBackend(backendType: BackendType, config: ThinkingConfig) {
    if (config.type === 'claude') {
      settings.value.claudeThinkingEnabled = config.enabled
      settings.value.claudeThinkingTokens = config.tokenBudget
      console.log('🧠 [Claude] 思考配置已更新:', { enabled: config.enabled, tokens: config.tokenBudget })
    } else {
      settings.value.codexReasoningEffort = config.effort
      settings.value.codexReasoningSummary = config.summary
      console.log('🧠 [Codex] 推理配置已更新:', { effort: config.effort, summary: config.summary })
    }
  }

  /**
   * 设置 Codex 沙盒模式
   */
  function setCodexSandboxMode(mode: SandboxMode) {
    settings.value.codexSandboxMode = mode
    console.log('📦 [Codex] 沙盒模式已更新:', mode)
  }

  /**
   * 设置 Codex API Key
   */
  function setCodexApiKey(apiKey: string | undefined) {
    settings.value.codexApiKey = apiKey
    console.log('🔑 [Codex] API Key 已更新')
  }

  /**
   * Computed: Codex 沙盒模式
   */
  const codexSandboxMode = computed(() => settings.value.codexSandboxMode)

  /**
   * Computed: 当前后端类型
   */
  const currentBackendType = computed(() => settings.value.defaultBackendType)

  /**
   * Computed: 当前后端的模型 ID
   */
  const currentModel = computed(() => getCurrentModelForBackend(settings.value.defaultBackendType))

  /**
   * Computed: 当前后端的思考配置
   */
  const currentThinkingConfig = computed(() =>
    getThinkingConfigForBackend(settings.value.defaultBackendType)
  )

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

      // 同时加载 IDE 设置和可用模型列表
      const [settingsResult, modelsResult] = await Promise.all([
        jetbrainsRSocket.getSettings(),
        loadAvailableModels()
      ])

      if (settingsResult) {
        ideSettings.value = settingsResult as IdeSettings
        console.log('✅ IDE settings loaded:', settingsResult)
        applyIdeSettings(settingsResult as IdeSettings)
      } else {
        console.warn('⚠️ Failed to load IDE settings')
      }
    } catch (error) {
      console.error('❌ Error loading IDE settings:', error)
    }
  }

  /**
   * 从后端加载可用模型列表（内置 + 自定义）
   */
  async function loadAvailableModels(): Promise<boolean> {
    try {
      console.log('📦 Loading available models from backend...')
      const response = await ideaBridge.query('models.getAvailable')

      if (response.success && response.data) {
        const { claudeModels: claudeList, codexModels: codexList, defaultBackendType, defaultClaudeModelId, defaultCodexModelId } = response.data as {
          claudeModels: BackendModelInfo[]
          codexModels: BackendModelInfo[]
          defaultBackendType: BackendType
          defaultClaudeModelId: string
          defaultCodexModelId: string
        }

        // 更新模型列表
        claudeModels.value = claudeList
        codexModels.value = codexList

        console.log('✅ Available models loaded:', {
          claude: claudeList.length,
          codex: codexList.length,
          defaultBackend: defaultBackendType,
          defaultClaude: defaultClaudeModelId,
          defaultCodex: defaultCodexModelId
        })

        // 检查当前选中的模型是否仍存在，如果被删除则切换到默认模型
        const currentClaudeModel = settings.value.claudeModel
        const claudeModelExists = claudeList.some(m => m.id === currentClaudeModel)
        if (!claudeModelExists && currentClaudeModel) {
          console.log('⚠️ Current Claude model not found, switching to default:', defaultClaudeModelId)
          settings.value.claudeModel = defaultClaudeModelId
        }

        const currentCodexModel = settings.value.codexModel
        const codexModelExists = codexList.some(m => m.id === currentCodexModel)
        if (!codexModelExists && currentCodexModel) {
          console.log('⚠️ Current Codex model not found, switching to default:', defaultCodexModelId)
          settings.value.codexModel = defaultCodexModelId
        }

        return true
      } else {
        console.warn('⚠️ Failed to load available models')
        return false
      }
    } catch (error) {
      console.error('❌ Error loading available models:', error)
      return false
    }
  }

  /**
   * 应用 IDE 设置到前端
   * 将后端 IDEA 的默认设置应用为前端的默认设置
   */
  function applyIdeSettings(newIdeSettings: IdeSettings) {
    const updates: Partial<Settings> = {}

    // 0. 应用默认后端类型
    if (newIdeSettings.defaultBackendType) {
      updates.defaultBackendType = newIdeSettings.defaultBackendType
      console.log('🔄 [IdeSettings] 默认后端类型:', newIdeSettings.defaultBackendType)
    }

    // 1. 应用 Claude 默认模型设置
    if (newIdeSettings.claudeDefaultModelId) {
      const modelInfo = claudeModels.value.find(m => m.id === newIdeSettings.claudeDefaultModelId)
      if (modelInfo) {
        updates.claudeModel = modelInfo.id
        console.log('🎯 [IdeSettings] Claude 默认模型:', modelInfo.displayName, `(${modelInfo.id})`)
      } else {
        console.warn('⚠️ [IdeSettings] 未知的 Claude 模型 ID:', newIdeSettings.claudeDefaultModelId)
      }
    } else if (newIdeSettings.defaultModelId) {
      // 兼容旧版本：如果只有 defaultModelId，应用到 Claude
      const modelInfo = claudeModels.value.find(m => m.id === newIdeSettings.defaultModelId)
      if (modelInfo) {
        updates.claudeModel = modelInfo.id
        console.log('🎯 [IdeSettings] Claude 默认模型 (兼容):', modelInfo.displayName)
      }
    }

    // 2. 应用 Claude 思考配置
    const claudeThinkingLevelId = newIdeSettings.claudeThinkingLevelId || newIdeSettings.defaultThinkingLevelId || 'ultra'
    const claudeThinkingTokens = newIdeSettings.claudeThinkingTokens ?? newIdeSettings.defaultThinkingTokens ?? 8096
    updates.claudeThinkingEnabled = claudeThinkingLevelId !== 'off' && claudeThinkingTokens > 0
    updates.claudeThinkingTokens = claudeThinkingTokens
    console.log('🧠 [IdeSettings] Claude 思考配置:', {
      levelId: claudeThinkingLevelId,
      tokens: claudeThinkingTokens,
      enabled: updates.claudeThinkingEnabled,
      levels: newIdeSettings.claudeThinkingLevels
    })

    // 3. 应用 Codex 默认模型设置
    if (newIdeSettings.codexDefaultModelId) {
      const modelInfo = codexModels.value.find(m => m.id === newIdeSettings.codexDefaultModelId)
      if (modelInfo) {
        updates.codexModel = modelInfo.id
        console.log('🎯 [IdeSettings] Codex 默认模型:', modelInfo.displayName, `(${modelInfo.id})`)
      } else {
        console.warn('⚠️ [IdeSettings] 未知的 Codex 模型 ID:', newIdeSettings.codexDefaultModelId)
      }
    }

    // 4. 应用 Codex 推理配置
    if (newIdeSettings.codexReasoningEffort) {
      updates.codexReasoningEffort = newIdeSettings.codexReasoningEffort
      console.log('🧠 [IdeSettings] Codex 推理努力级别:', newIdeSettings.codexReasoningEffort)
    }

    // 5. 应用 Codex 沙盒模式
    if (newIdeSettings.codexSandboxMode) {
      updates.codexSandboxMode = newIdeSettings.codexSandboxMode
      console.log('📦 [IdeSettings] Codex 沙盒模式:', newIdeSettings.codexSandboxMode)
    }

    // 6. 应用 Codex API Key
    if (newIdeSettings.codexApiKey !== undefined) {
      updates.codexApiKey = newIdeSettings.codexApiKey
      console.log('🔑 [IdeSettings] Codex API Key 已配置')
    }

    // 7. 应用 ByPass 权限设置（同步到当前会话）
    const newBypassValue = newIdeSettings.defaultBypassPermissions ?? false
    updates.skipPermissions = newBypassValue
    console.log('🔓 [IdeSettings] ByPass 权限设置:', newBypassValue)

    // 8. 应用 includePartialMessages 设置
    if (newIdeSettings.includePartialMessages !== undefined) {
      updates.includePartialMessages = newIdeSettings.includePartialMessages
      console.log('📡 [IdeSettings] Include Partial Messages:', newIdeSettings.includePartialMessages)
    }

    // 9. 应用权限模式设置
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
  async function handleIdeSettingsChange(newIdeSettings: BaseIdeSettings) {
    console.log('📥 [IdeSettings] 收到设置变更推送:', newIdeSettings)
    ideSettings.value = newIdeSettings as IdeSettings

    // 重新加载模型列表（自定义模型可能已添加/删除）
    await loadAvailableModels()

    applyIdeSettings(newIdeSettings as IdeSettings)
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

        // 0. 应用默认后端类型
        if (httpSettings.defaultBackendType) {
          updates.defaultBackendType = httpSettings.defaultBackendType
        }

        // 1. 应用 Claude 默认模型设置
        if (httpSettings.claudeDefaultModelId) {
          const modelInfo = claudeModels.value.find(m => m.id === httpSettings.claudeDefaultModelId)
          if (modelInfo) {
            updates.claudeModel = modelInfo.id
            console.log('🎯 [DefaultSettings] Claude 默认模型:', modelInfo.displayName, `(${modelInfo.id})`)
          }
        } else if (httpSettings.defaultModelId) {
          // 兼容旧版本
          const modelInfo = claudeModels.value.find(m => m.id === httpSettings.defaultModelId)
          if (modelInfo) {
            updates.claudeModel = modelInfo.id
            console.log('🎯 [DefaultSettings] Claude 默认模型 (兼容):', modelInfo.displayName)
          }
        }

        // 2. 应用 Claude 思考配置
        const claudeThinkingLevel = httpSettings.claudeDefaultThinkingLevel || httpSettings.defaultThinkingLevel || 'HIGH'
        const claudeThinkingTokens = httpSettings.claudeDefaultThinkingTokens ?? httpSettings.defaultThinkingTokens ?? 8192
        updates.claudeThinkingEnabled = claudeThinkingLevel !== 'OFF' && claudeThinkingTokens > 0
        updates.claudeThinkingTokens = claudeThinkingTokens
        console.log('🧠 [DefaultSettings] Claude 思考配置:', {
          level: claudeThinkingLevel,
          tokens: claudeThinkingTokens,
          enabled: updates.claudeThinkingEnabled
        })

        // 3. 应用 Codex 默认模型设置
        if (httpSettings.codexDefaultModelId) {
          const modelInfo = codexModels.value.find(m => m.id === httpSettings.codexDefaultModelId)
          if (modelInfo) {
            updates.codexModel = modelInfo.id
            console.log('🎯 [DefaultSettings] Codex 默认模型:', modelInfo.displayName, `(${modelInfo.id})`)
          }
        }

        // 4. 应用 Codex 推理配置
        if (httpSettings.codexReasoningEffort) {
          updates.codexReasoningEffort = httpSettings.codexReasoningEffort
          console.log('🧠 [DefaultSettings] Codex 推理努力级别:', httpSettings.codexReasoningEffort)
        }

        // 5. 应用 Codex 沙盒模式
        if (httpSettings.codexSandboxMode) {
          updates.codexSandboxMode = httpSettings.codexSandboxMode
          console.log('📦 [DefaultSettings] Codex 沙盒模式:', httpSettings.codexSandboxMode)
        }

        // 6. 应用 ByPass 权限设置
        updates.skipPermissions = httpSettings.defaultBypassPermissions ?? false
        console.log('🔓 [DefaultSettings] ByPass 权限设置:', updates.skipPermissions)

        // 7. 应用 includePartialMessages 设置
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
   * 更新默认后端类型
   */
  async function updateDefaultBackendType(backendType: BackendType) {
    return await saveSettings({ defaultBackendType: backendType })
  }

  /**
   * 更新模型（当前后端）
   */
  async function updateModel(modelId: string) {
    setModelForBackend(settings.value.defaultBackendType, modelId)
    return true
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
   * 更新思考开关（当前后端）
   */
  async function updateThinkingEnabled(enabled: boolean) {
    const backendType = settings.value.defaultBackendType
    if (backendType === 'claude') {
      return await saveSettings({ claudeThinkingEnabled: enabled })
    } else {
      // Codex 的思考是通过 reasoningEffort 控制的
      return await saveSettings({
        codexReasoningEffort: enabled ? 'medium' : null
      })
    }
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

  /**
   * 加载后端设置（Phase 3.3）
   * 根据当前设置构建完整的后端配置对象
   */
  function loadBackendSettings() {
    // 同步分散的设置到配置对象
    settings.value.claudeConfig = {
      ...settings.value.claudeConfig,
      type: BackendTypes.CLAUDE,
      modelId: settings.value.claudeModel,
      thinkingEnabled: settings.value.claudeThinkingEnabled,
      thinkingTokenBudget: settings.value.claudeThinkingTokens,
      permissionMode: settings.value.permissionMode,
      skipPermissions: settings.value.skipPermissions,
      includePartialMessages: settings.value.includePartialMessages,
      maxTurns: settings.value.maxTurns,
    }

    settings.value.codexConfig = {
      ...settings.value.codexConfig,
      type: BackendTypes.CODEX,
      modelId: settings.value.codexModel,
      reasoningEffort: settings.value.codexReasoningEffort,
      reasoningSummary: settings.value.codexReasoningSummary,
      sandboxMode: settings.value.codexSandboxMode,
      permissionMode: settings.value.permissionMode,
      skipPermissions: settings.value.skipPermissions,
      maxTurns: settings.value.maxTurns,
    }

    console.log('✅ [BackendSettings] 后端配置已同步:', {
      claude: settings.value.claudeConfig,
      codex: settings.value.codexConfig
    })
  }

  /**
   * 更新 Claude 配置（Phase 3.3）
   */
  async function updateClaudeConfig(config: Partial<ClaudeBackendConfig>) {
    settings.value.claudeConfig = {
      ...settings.value.claudeConfig,
      ...config,
      type: BackendTypes.CLAUDE, // 确保类型不变
    }

    // 同步回分散的字段（向后兼容）
    if (config.modelId !== undefined) {
      settings.value.claudeModel = config.modelId
    }
    if (config.thinkingEnabled !== undefined) {
      settings.value.claudeThinkingEnabled = config.thinkingEnabled
    }
    if (config.thinkingTokenBudget !== undefined) {
      settings.value.claudeThinkingTokens = config.thinkingTokenBudget
    }
    if (config.permissionMode !== undefined) {
      settings.value.permissionMode = config.permissionMode as PermissionMode
    }
    if (config.skipPermissions !== undefined) {
      settings.value.skipPermissions = config.skipPermissions
    }
    if (config.includePartialMessages !== undefined) {
      settings.value.includePartialMessages = config.includePartialMessages
    }
    if (config.maxTurns !== undefined) {
      settings.value.maxTurns = config.maxTurns
    }

    console.log('✅ [ClaudeConfig] Claude 配置已更新:', settings.value.claudeConfig)
    return true
  }

  /**
   * 更新 Codex 配置（Phase 3.3）
   */
  async function updateCodexConfig(config: Partial<CodexBackendConfig>) {
    settings.value.codexConfig = {
      ...settings.value.codexConfig,
      ...config,
      type: BackendTypes.CODEX, // 确保类型不变
    }

    // 同步回分散的字段（向后兼容）
    if (config.modelId !== undefined) {
      settings.value.codexModel = config.modelId
    }
    if (config.reasoningEffort !== undefined) {
      settings.value.codexReasoningEffort = config.reasoningEffort
    }
    if (config.reasoningSummary !== undefined) {
      settings.value.codexReasoningSummary = config.reasoningSummary
    }
    if (config.sandboxMode !== undefined) {
      settings.value.codexSandboxMode = config.sandboxMode
    }
    if (config.permissionMode !== undefined) {
      settings.value.permissionMode = config.permissionMode as PermissionMode
    }
    if (config.skipPermissions !== undefined) {
      settings.value.skipPermissions = config.skipPermissions
    }
    if (config.maxTurns !== undefined) {
      settings.value.maxTurns = config.maxTurns
    }

    console.log('✅ [CodexConfig] Codex 配置已更新:', settings.value.codexConfig)
    return true
  }

  /**
   * 获取后端配置（Phase 3.3）
   * @param type 后端类型
   * @returns 对应后端的配置对象
   */
  function getBackendConfig(type: BackendType): ClaudeBackendConfig | CodexBackendConfig {
    if (type === BackendTypes.CLAUDE) {
      return settings.value.claudeConfig
    } else {
      return settings.value.codexConfig
    }
  }

  return {
    // 状态
    settings,
    ideSettings,
    loading,
    showPanel,

    // 后端特定的模型列表
    claudeModels,
    codexModels,

    // Computed
    codexSandboxMode,
    currentBackendType,
    currentModel,
    currentThinkingConfig,

    // 后端感知的方法
    getModelsForBackend,
    getCurrentModelForBackend,
    setModelForBackend,
    getThinkingConfigForBackend,
    setThinkingConfigForBackend,
    setCodexSandboxMode,
    setCodexApiKey,

    // 设置加载
    loadSettings,
    loadIdeSettings,
    loadAvailableModels,
    loadDefaultSettings,
    initIdeSettingsListener,
    cleanupIdeSettingsListener,

    // 设置更新
    saveSettings,
    updateDefaultBackendType,
    updateModel,
    updatePermissionMode,
    updateMaxTurns,
    updateThinkingEnabled,
    resetToDefaults,

    // Phase 3.3: 多后端配置方法
    loadBackendSettings,
    updateClaudeConfig,
    updateCodexConfig,
    getBackendConfig,

    // 面板控制
    openPanel,
    closePanel,
    togglePanel
  }
})

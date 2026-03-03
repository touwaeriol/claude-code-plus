import { ref } from 'vue'

// ==================== 模型信息与能力 ====================

/**
 * 思考模式
 * - always: 强制开启，不可切换
 * - never: 强制关闭，不可切换
 * - optional: 可选，用户可自由切换
 */
export type ThinkingMode = 'always' | 'never' | 'optional'

/**
 * 模型信息（来自后端，包含内置和自定义模型）
 */
export interface ModelInfo {
  modelId: string      // 实际模型 ID（如 "claude-opus-4-5-20251101"）
  displayName: string  // 显示名称
  isBuiltIn: boolean   // 是否为内置模型
  description?: string
}

/**
 * 模型能力定义
 */
export interface ModelCapability {
  /** 实际模型 ID（发送到后端） */
  modelId: string

  /** UI 显示名称 */
  displayName: string

  /** 思考模式 */
  thinkingMode: ThinkingMode

  /** 默认是否开启思考（仅 thinkingMode === 'optional' 时有意义） */
  defaultThinkingEnabled: boolean

  /** 模型描述（可选，用于 tooltip） */
  description?: string
}

/**
 * 内置模型能力映射（以 modelId 作为唯一键）
 */
const MODEL_CAPABILITIES: Record<string, ModelCapability> = {
  'claude-opus-4-6': {
    modelId: 'claude-opus-4-6',
    displayName: 'Opus 4.6',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Most powerful model for complex tasks',
  },
  'claude-sonnet-4-6': {
    modelId: 'claude-sonnet-4-6',
    displayName: 'Sonnet 4.6',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Balanced performance and cost',
  },
  'claude-opus-4-5-20251101': {
    modelId: 'claude-opus-4-5-20251101',
    displayName: 'Opus 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Most powerful model for complex tasks',
  },
  'claude-sonnet-4-5-20250929': {
    modelId: 'claude-sonnet-4-5-20250929',
    displayName: 'Sonnet 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Balanced performance and cost',
  },
  'claude-haiku-4-5-20251001': {
    modelId: 'claude-haiku-4-5-20251001',
    displayName: 'Haiku 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: false,
    description: 'Fast responses for simple tasks',
  },
}

// ==================== 动态模型列表支持 ====================

/**
 * 所有可用模型列表（包含内置和自定义）
 * 通过 updateAllModels() 从后端刷新
 */
const _allModels = ref<ModelInfo[]>([
  { modelId: 'claude-opus-4-6', displayName: 'Opus 4.6', isBuiltIn: true },
  { modelId: 'claude-sonnet-4-6', displayName: 'Sonnet 4.6', isBuiltIn: true },
  { modelId: 'claude-opus-4-5-20251101', displayName: 'Opus 4.5', isBuiltIn: true },
  { modelId: 'claude-sonnet-4-5-20250929', displayName: 'Sonnet 4.5', isBuiltIn: true },
  { modelId: 'claude-haiku-4-5-20251001', displayName: 'Haiku 4.5', isBuiltIn: true },
])

/**
 * 当前默认模型 ID
 */
const _defaultModelId = ref<string>('claude-opus-4-6')

/**
 * 模型列表变化回调列表
 */
const _modelListChangeCallbacks: Array<(models: ModelInfo[], defaultModelId: string) => void> = []

/**
 * 获取所有可用模型
 */
export function getAllModels(): ModelInfo[] {
  return _allModels.value
}

/**
 * 获取当前默认模型 ID
 */
export function getDefaultModelId(): string {
  return _defaultModelId.value
}

/**
 * 更新可用模型列表（从后端获取）
 */
export function updateAllModels(models: ModelInfo[], defaultModelId: string): void {
  _allModels.value = models
  _defaultModelId.value = defaultModelId
  console.log('[models] Updated available models:', models.length, 'default:', defaultModelId)

  // 通知所有监听者模型列表已变化
  _modelListChangeCallbacks.forEach(callback => {
    try {
      callback(models, defaultModelId)
    } catch (e) {
      console.error('[models] Error in model list change callback:', e)
    }
  })
}

/**
 * 注册模型列表变化回调
 * @returns 取消注册的函数
 */
export function onModelListChange(callback: (models: ModelInfo[], defaultModelId: string) => void): () => void {
  _modelListChangeCallbacks.push(callback)
  return () => {
    const index = _modelListChangeCallbacks.indexOf(callback)
    if (index >= 0) {
      _modelListChangeCallbacks.splice(index, 1)
    }
  }
}

/**
 * 根据 modelId 获取模型信息
 */
export function getModelByModelId(modelId: string): ModelInfo | undefined {
  return _allModels.value.find(m => m.modelId === modelId)
}

/**
 * 验证模型 ID 是否有效，如无效返回默认模型
 */
export function validateAndFallbackModel(modelId: string): ModelInfo {
  const models = _allModels.value
  const defaultId = _defaultModelId.value

  // 模型存在，直接返回
  const existingModel = models.find(m => m.modelId === modelId)
  if (existingModel) {
    return existingModel
  }

  // 回退到当前默认模型
  const defaultModel = models.find(m => m.modelId === defaultId)
  if (defaultModel) {
    return defaultModel
  }

  // 回退到第一个可用模型
  if (models.length > 0) {
    return models[0]
  }

  // 无可用模型时，返回原值占位
  return {
    modelId,
    displayName: modelId,
    isBuiltIn: false
  }
}

/**
 * 获取模型显示名称
 */
export function getModelDisplayName(modelId: string): string {
  const model = getModelByModelId(modelId)
  return model?.displayName ?? modelId
}

/**
 * 获取模型能力配置（内置模型返回真实能力，自定义模型返回默认能力）
 */
export function getModelCapability(modelId: string): ModelCapability {
  const builtIn = MODEL_CAPABILITIES[modelId]
  if (builtIn) {
    return builtIn
  }

  const model = getModelByModelId(modelId)
  return {
    modelId,
    displayName: model?.displayName ?? modelId,
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: model?.isBuiltIn ? undefined : 'Custom model'
  }
}

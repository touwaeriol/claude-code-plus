import { AiModel } from '@/types/enhancedMessage'
import { ref } from 'vue'

// ==================== 新架构：BaseModel + ThinkingMode ====================

/**
 * 思考模式
 * - always: 强制开启，不可切换（如未来的高级推理模型）
 * - never: 强制关闭，不可切换（如不支持思考的轻量模型）
 * - optional: 可选，用户可自由切换
 */
export type ThinkingMode = 'always' | 'never' | 'optional'

/**
 * 模型信息（从后端获取，包含内置和自定义模型）
 */
export interface ModelInfo {
  id: string           // 唯一标识：内置模型用枚举名（如 "OPUS_45"），自定义用 "custom_xxx"
  displayName: string  // 显示名称
  modelId: string      // 实际模型 ID（如 "claude-opus-4-5-20250929"）
  isBuiltIn: boolean   // 是否为内置模型
}

/**
 * 基础模型枚举（简化版，不再区分思考/非思考）
 * 注意：这只包含内置模型，自定义模型通过 ModelInfo 表示
 */
export enum BaseModel {
  OPUS_45 = 'OPUS_45',
  SONNET_45 = 'SONNET_45',
  HAIKU_45 = 'HAIKU_45',
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
 * 模型能力映射表
 */
export const MODEL_CAPABILITIES: Record<BaseModel, ModelCapability> = {
  [BaseModel.OPUS_45]: {
    modelId: 'claude-opus-4-5-20251101',
    displayName: 'Opus 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Most powerful model for complex tasks',
  },
  [BaseModel.SONNET_45]: {
    modelId: 'claude-sonnet-4-5-20250929',
    displayName: 'Sonnet 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: 'Balanced performance and cost',
  },
  [BaseModel.HAIKU_45]: {
    modelId: 'claude-haiku-4-5-20251001',
    displayName: 'Haiku 4.5',
    thinkingMode: 'optional',
    defaultThinkingEnabled: false,
    description: 'Fast responses for simple tasks',
  },
}

/**
 * 检查模型是否支持切换思考
 * @deprecated 建议使用 getModelCapability(modelId).thinkingMode === 'optional'
 */
export function canToggleThinking(model: BaseModel): boolean {
  return MODEL_CAPABILITIES[model].thinkingMode === 'optional'
}

// ==================== 动态模型列表支持 ====================

/**
 * 所有可用模型列表（包含内置和自定义模型）
 * 通过 updateAllModels() 从后端刷新
 * 使用响应式 ref 确保 UI 能够追踪变化
 */
const _allModels = ref<ModelInfo[]>([
  // 默认内置模型
  { id: 'OPUS_45', displayName: 'Opus 4.5', modelId: 'claude-opus-4-5-20250929', isBuiltIn: true },
  { id: 'SONNET_45', displayName: 'Sonnet 4.5', modelId: 'claude-sonnet-4-5-20250929', isBuiltIn: true },
  { id: 'HAIKU_45', displayName: 'Haiku 4.5', modelId: 'claude-haiku-4-5-20250929', isBuiltIn: true },
])

/**
 * 当前默认模型 ID
 */
const _defaultModelId = ref<string>('OPUS_45')

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
 * 根据模型 ID 获取模型信息
 */
export function getModelById(id: string): ModelInfo | undefined {
  return _allModels.value.find(m => m.id === id)
}

/**
 * 验证模型 ID（通过 modelId）是否有效，如果无效返回回退的模型
 * @param modelId 实际的模型 ID（如 "claude-opus-4-5-20251101"）
 * @returns 有效的模型信息，如果原模型无效则返回回退模型
 */
export function validateAndFallbackModel(modelId: string): ModelInfo {
  const models = _allModels.value
  const defaultId = _defaultModelId.value

  // 检查模型是否存在
  const existingModel = models.find(m => m.modelId === modelId)
  if (existingModel) {
    return existingModel
  }

  // 模型不存在，尝试回退到默认模型
  console.warn(`[models] Model "${modelId}" not found, falling back...`)
  const defaultModel = models.find(m => m.id === defaultId)
  if (defaultModel) {
    console.log(`[models] Falling back to default model: ${defaultModel.displayName}`)
    return defaultModel
  }

  // 默认模型也不存在，回退到第一个可用模型
  if (models.length > 0) {
    console.log(`[models] Falling back to first available model: ${models[0].displayName}`)
    return models[0]
  }

  // 没有任何可用模型，返回硬编码的内置模型
  console.warn('[models] No models available, using hardcoded fallback')
  return {
    id: 'OPUS_45',
    displayName: 'Opus 4.5',
    modelId: 'claude-opus-4-5-20251101',
    isBuiltIn: true
  }
}

/**
 * 根据实际 modelId 获取模型信息
 */
export function getModelByModelId(modelId: string): ModelInfo | undefined {
  return _allModels.value.find(m => m.modelId === modelId)
}

/**
 * 判断模型 ID 是否为内置模型
 */
export function isBuiltInModel(id: string): boolean {
  const model = getModelById(id)
  return model?.isBuiltIn ?? false
}

/**
 * 获取模型的显示名称
 */
export function getModelDisplayName(id: string): string {
  const model = getModelById(id)
  return model?.displayName ?? id
}

/**
 * 获取模型的能力配置（仅适用于内置模型）
 * 自定义模型返回默认能力
 */
export function getModelCapability(id: string): ModelCapability {
  if (id in BaseModel) {
    return MODEL_CAPABILITIES[id as BaseModel]
  }
  // 自定义模型：返回默认能力（optional thinking）
  const model = getModelById(id)
  return {
    modelId: model?.modelId ?? id,
    displayName: model?.displayName ?? id,
    thinkingMode: 'optional',
    defaultThinkingEnabled: true,
    description: model?.isBuiltIn ? undefined : 'Custom model'
  }
}






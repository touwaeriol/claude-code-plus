import { AiModel } from '@/types/enhancedMessage'

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
 * 所有可用的基础模型列表
 */
export const AVAILABLE_MODELS: BaseModel[] = [
  BaseModel.OPUS_45,
  BaseModel.SONNET_45,
  BaseModel.HAIKU_45,
]

/**
 * 根据模型和思考状态获取有效的思考开关值
 */
export function getEffectiveThinkingEnabled(model: BaseModel, userChoice: boolean): boolean {
  const capability = MODEL_CAPABILITIES[model]
  switch (capability.thinkingMode) {
    case 'always':
      return true
    case 'never':
      return false
    case 'optional':
      return userChoice
  }
}

/**
 * 检查模型是否支持切换思考
 */
export function canToggleThinking(model: BaseModel): boolean {
  return MODEL_CAPABILITIES[model].thinkingMode === 'optional'
}

/**
 * 根据 modelId 查找对应的 BaseModel
 * @param modelId 模型 ID（如 'claude-opus-4-5-20251101'）
 * @returns 对应的 BaseModel，未找到返回 undefined
 */
export function findBaseModelByModelId(modelId: string): BaseModel | undefined {
  for (const [key, capability] of Object.entries(MODEL_CAPABILITIES)) {
    if (capability.modelId === modelId) {
      return key as BaseModel
    }
  }
  return undefined
}

// ==================== 旧架构（向后兼容，标记废弃）====================

/**
 * @deprecated 使用 BaseModel + thinkingEnabled 代替
 */
export enum UiModelOption {
  OPUS_45 = 'OPUS_45',
  OPUS_45_THINKING = 'OPUS_45_THINKING',
  SONNET_45 = 'SONNET_45',
  SONNET_45_THINKING = 'SONNET_45_THINKING',
  HAIKU_45 = 'HAIKU_45',
  HAIKU_45_THINKING = 'HAIKU_45_THINKING',
}

/**
 * @deprecated 使用 ModelCapability 代替
 */
export interface ModelResolvedConfig {
  provider: 'claude' | 'codex'
  modelId: string
  thinkingEnabled: boolean
}

/**
 * @deprecated 使用 MODEL_CAPABILITIES 代替
 */
export const MODEL_RESOLUTION_MAP: Record<UiModelOption, ModelResolvedConfig> = {
  [UiModelOption.OPUS_45]: {
    provider: 'claude',
    modelId: 'claude-opus-4-5-20251101',
    thinkingEnabled: false
  },
  [UiModelOption.OPUS_45_THINKING]: {
    provider: 'claude',
    modelId: 'claude-opus-4-5-20251101',
    thinkingEnabled: true
  },
  [UiModelOption.SONNET_45]: {
    provider: 'claude',
    modelId: 'claude-sonnet-4-5-20250929',
    thinkingEnabled: false
  },
  [UiModelOption.SONNET_45_THINKING]: {
    provider: 'claude',
    modelId: 'claude-sonnet-4-5-20250929',
    thinkingEnabled: true
  },
  [UiModelOption.HAIKU_45]: {
    provider: 'claude',
    modelId: 'claude-haiku-4-5-20251001',
    thinkingEnabled: false
  },
  [UiModelOption.HAIKU_45_THINKING]: {
    provider: 'claude',
    modelId: 'claude-haiku-4-5-20251001',
    thinkingEnabled: true
  }
}

/**
 * @deprecated 使用 MODEL_CAPABILITIES[model].displayName 代替
 */
export const UI_MODEL_LABELS: Record<UiModelOption, string> = {
  [UiModelOption.OPUS_45]: 'Opus 4.5',
  [UiModelOption.OPUS_45_THINKING]: 'Opus 4.5',
  [UiModelOption.SONNET_45]: 'Sonnet 4.5',
  [UiModelOption.SONNET_45_THINKING]: 'Sonnet 4.5',
  [UiModelOption.HAIKU_45]: 'Haiku 4.5',
  [UiModelOption.HAIKU_45_THINKING]: 'Haiku 4.5',
}

/**
 * @deprecated 使用新架构的 thinkingEnabled 状态代替
 */
export const UI_MODEL_SHOW_BRAIN: Record<UiModelOption, boolean> = {
  [UiModelOption.OPUS_45]: false,
  [UiModelOption.OPUS_45_THINKING]: true,
  [UiModelOption.SONNET_45]: false,
  [UiModelOption.SONNET_45_THINKING]: true,
  [UiModelOption.HAIKU_45]: false,
  [UiModelOption.HAIKU_45_THINKING]: true,
}

/**
 * @deprecated
 */
export function uiOptionToAiModel(option: UiModelOption): AiModel {
  switch (option) {
    case UiModelOption.OPUS_45:
    case UiModelOption.OPUS_45_THINKING:
      return AiModel.OPUS
    case UiModelOption.HAIKU_45:
    case UiModelOption.HAIKU_45_THINKING:
      return AiModel.DEFAULT
    case UiModelOption.SONNET_45:
    case UiModelOption.SONNET_45_THINKING:
    default:
      return AiModel.SONNET
  }
}

// ==================== 迁移工具 ====================

/**
 * 将旧的 UiModelOption 映射到新的 BaseModel + thinkingEnabled
 */
export const LEGACY_MODEL_MAP: Record<UiModelOption, { model: BaseModel; thinkingEnabled: boolean }> = {
  [UiModelOption.OPUS_45]: { model: BaseModel.OPUS_45, thinkingEnabled: false },
  [UiModelOption.OPUS_45_THINKING]: { model: BaseModel.OPUS_45, thinkingEnabled: true },
  [UiModelOption.SONNET_45]: { model: BaseModel.SONNET_45, thinkingEnabled: false },
  [UiModelOption.SONNET_45_THINKING]: { model: BaseModel.SONNET_45, thinkingEnabled: true },
  [UiModelOption.HAIKU_45]: { model: BaseModel.HAIKU_45, thinkingEnabled: false },
  [UiModelOption.HAIKU_45_THINKING]: { model: BaseModel.HAIKU_45, thinkingEnabled: true },
}

/**
 * 迁移旧设置到新格式
 */
export function migrateModelSettings(oldModel: string | UiModelOption): { model: BaseModel; thinkingEnabled: boolean } {
  // 如果已经是新格式
  if (oldModel in BaseModel) {
    const capability = MODEL_CAPABILITIES[oldModel as BaseModel]
    return {
      model: oldModel as BaseModel,
      thinkingEnabled: capability.defaultThinkingEnabled
    }
  }

  // 迁移旧格式
  const mapped = LEGACY_MODEL_MAP[oldModel as UiModelOption]
  if (mapped) {
    return mapped
  }

  // 默认值
  return {
    model: BaseModel.OPUS_45,
    thinkingEnabled: true
  }
}

// ==================== 动态模型列表支持 ====================

/**
 * 所有可用模型列表（包含内置和自定义模型）
 * 通过 updateAllModels() 从后端刷新
 */
let _allModels: ModelInfo[] = [
  // 默认内置模型
  { id: 'OPUS_45', displayName: 'Opus 4.5', modelId: 'claude-opus-4-5-20250929', isBuiltIn: true },
  { id: 'SONNET_45', displayName: 'Sonnet 4.5', modelId: 'claude-sonnet-4-5-20250929', isBuiltIn: true },
  { id: 'HAIKU_45', displayName: 'Haiku 4.5', modelId: 'claude-haiku-4-5-20250929', isBuiltIn: true },
]

/**
 * 当前默认模型 ID
 */
let _defaultModelId = 'OPUS_45'

/**
 * 获取所有可用模型
 */
export function getAllModels(): ModelInfo[] {
  return _allModels
}

/**
 * 获取当前默认模型 ID
 */
export function getDefaultModelId(): string {
  return _defaultModelId
}

/**
 * 更新可用模型列表（从后端获取）
 */
export function updateAllModels(models: ModelInfo[], defaultModelId: string): void {
  _allModels = models
  _defaultModelId = defaultModelId
  console.log('[models] Updated available models:', models.length, 'default:', defaultModelId)
}

/**
 * 根据模型 ID 获取模型信息
 */
export function getModelById(id: string): ModelInfo | undefined {
  return _allModels.find(m => m.id === id)
}

/**
 * 根据实际 modelId 获取模型信息
 */
export function getModelByModelId(modelId: string): ModelInfo | undefined {
  return _allModels.find(m => m.modelId === modelId)
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






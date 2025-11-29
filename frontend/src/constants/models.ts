import type { AiModel } from '@/types/enhancedMessage'

// ==================== 新架构：BaseModel + ThinkingMode ====================

/**
 * 思考模式
 * - always: 强制开启，不可切换（如未来的高级推理模型）
 * - never: 强制关闭，不可切换（如不支持思考的轻量模型）
 * - optional: 可选，用户可自由切换
 */
export type ThinkingMode = 'always' | 'never' | 'optional'

/**
 * 基础模型枚举（简化版，不再区分思考/非思考）
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
      return 'OPUS'
    case UiModelOption.HAIKU_45:
    case UiModelOption.HAIKU_45_THINKING:
      return 'DEFAULT'
    case UiModelOption.SONNET_45:
    case UiModelOption.SONNET_45_THINKING:
    default:
      return 'SONNET'
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

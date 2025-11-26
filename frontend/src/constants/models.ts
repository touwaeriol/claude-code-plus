import type { AiModel } from '@/types/enhancedMessage'

export enum UiModelOption {
  OPUS_45 = 'OPUS_45',
  OPUS_45_THINKING = 'OPUS_45_THINKING',
  SONNET_45 = 'SONNET_45',
  SONNET_45_THINKING = 'SONNET_45_THINKING',
  HAIKU_45 = 'HAIKU_45',
  HAIKU_45_THINKING = 'HAIKU_45_THINKING',
}

export interface ModelResolvedConfig {
  provider: 'claude' | 'codex'
  modelId: string
  thinkingEnabled: boolean
}

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

export const UI_MODEL_LABELS: Record<UiModelOption, string> = {
  [UiModelOption.OPUS_45]: 'Opus 4.5',
  [UiModelOption.OPUS_45_THINKING]: 'Opus 4.5',
  [UiModelOption.SONNET_45]: 'Sonnet 4.5',
  [UiModelOption.SONNET_45_THINKING]: 'Sonnet 4.5',
  [UiModelOption.HAIKU_45]: 'Haiku 4.5',
  [UiModelOption.HAIKU_45_THINKING]: 'Haiku 4.5',
}

export const UI_MODEL_SHOW_BRAIN: Record<UiModelOption, boolean> = {
  [UiModelOption.OPUS_45]: false,
  [UiModelOption.OPUS_45_THINKING]: true,
  [UiModelOption.SONNET_45]: false,
  [UiModelOption.SONNET_45_THINKING]: true,
  [UiModelOption.HAIKU_45]: false,
  [UiModelOption.HAIKU_45_THINKING]: true,
}

export function uiOptionToAiModel(option: UiModelOption): AiModel {
  // 映射到 ChatInput 用的 AiModel，用于兼容现有逻辑
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








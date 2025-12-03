/**
 * AI 模型配置
 * 定义每个模型的上下文长度和其他属性
 */

import { AiModel } from '@/types/enhancedMessage'

export interface ModelConfig {
  id: AiModel
  name: string
  contextLength: number
  description?: string
}

/**
 * 模型配置映射
 * 基于 Claude API 的实际上下文窗口大小
 */
export const MODEL_CONFIGS: Record<AiModel, ModelConfig> = {
  [AiModel.DEFAULT]: {
    id: AiModel.DEFAULT,
    name: 'Claude Sonnet 4.5',
    contextLength: 200000,
    description: '默认模型 - Claude 3.5 Sonnet (2024-10-22)'
  },
  [AiModel.OPUS]: {
    id: AiModel.OPUS,
    name: 'Claude Opus',
    contextLength: 200000,
    description: 'Claude 3 Opus - 最强大的模型'
  },
  [AiModel.SONNET]: {
    id: AiModel.SONNET,
    name: 'Claude Sonnet',
    contextLength: 200000,
    description: 'Claude 3.5 Sonnet - 平衡性能和成本'
  },
  [AiModel.OPUS_PLAN]: {
    id: AiModel.OPUS_PLAN,
    name: 'Claude Opus (Plan Mode)',
    contextLength: 200000,
    description: 'Claude 3 Opus - 计划模式'
  }
}

/**
 * 获取模型配置
 * @throws {Error} 如果模型不存在
 */
export function getModelConfig(model: AiModel): ModelConfig {
  const config = MODEL_CONFIGS[model]
  if (!config) {
    throw new Error(`Unknown AI model: ${model}. Available models: ${Object.keys(MODEL_CONFIGS).join(', ')}`)
  }
  return config
}

/**
 * 获取模型的上下文长度
 * @throws {Error} 如果模型不存在
 */
export function getModelContextLength(model: AiModel | string): number {
  return MODEL_CONFIGS[model as AiModel]?.contextLength ?? 200000
}


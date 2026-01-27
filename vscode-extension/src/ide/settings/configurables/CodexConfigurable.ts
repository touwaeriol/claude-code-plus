/**
 * Codex 配置逻辑
 * 
 * 翻译自: jetbrains-plugin/src/main/kotlin/com/asakii/settings/CodexConfigurable.kt
 * 
 * 负责管理 Codex 相关的设置:
 * - 默认权限 (bypass permissions, auto cleanup contexts)
 * - 运行时设置 (Codex 路径, Web 搜索)
 * - 模型设置 (默认模型, 自定义模型)
 * - 会话默认值 (reasoning effort, summary, sandbox mode)
 */

import * as vscode from 'vscode'

// ============================================================================
// Types
// ============================================================================

/** 模型信息 */
export interface CodexModelInfo {
  displayName: string
  modelId: string
  isBuiltIn: boolean
}

/** 自定义模型配置 */
export interface CodexCustomModelConfig {
  id: string
  displayName: string
  modelId: string
}

/** 沙箱选项 */
export interface SandboxOption {
  id: string
  label: string
}

// ============================================================================
// Constants
// ============================================================================

/** 内置 Codex 模型列表 */
export const CODEX_BUILT_IN_MODELS: CodexModelInfo[] = [
  { displayName: 'GPT-5.2 Codex', modelId: 'gpt-5.2-codex', isBuiltIn: true },
  { displayName: 'GPT-5.2', modelId: 'gpt-5.2', isBuiltIn: true },
  { displayName: 'O3', modelId: 'o3', isBuiltIn: true },
  { displayName: 'O4-mini', modelId: 'o4-mini', isBuiltIn: true }
]

/** Reasoning effort 选项 */
export const REASONING_EFFORT_OPTIONS = ['minimal', 'low', 'medium', 'high', 'xhigh'] as const
export type ReasoningEffort = typeof REASONING_EFFORT_OPTIONS[number]

/** Reasoning summary 选项 */
export const REASONING_SUMMARY_OPTIONS = ['auto', 'concise', 'detailed', 'none'] as const
export type ReasoningSummary = typeof REASONING_SUMMARY_OPTIONS[number]

/** Sandbox mode 选项 */
export const SANDBOX_MODE_OPTIONS: SandboxOption[] = [
  { id: 'read-only', label: 'Chat' },
  { id: 'workspace-write', label: 'Agent' },
  { id: 'danger-full-access', label: 'Agent (full access)' }
]
export type SandboxMode = 'read-only' | 'workspace-write' | 'danger-full-access'

// ============================================================================
// Configuration Service
// ============================================================================

/**
 * Codex 配置服务
 * 
 * 提供对 VS Code 设置的统一访问接口
 */
export class CodexConfigurable {
  private static readonly SECTION = 'claudeCodePlus.codex'

  /**
   * 获取配置对象
   */
  private static getConfig(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration(this.SECTION)
  }

  // ========== Runtime Settings ==========

  /**
   * 获取 Codex 路径
   */
  static getCodexPath(): string {
    return this.getConfig().get<string>('path', '')
  }

  /**
   * 设置 Codex 路径
   */
  static async setCodexPath(path: string): Promise<void> {
    await this.getConfig().update('path', path, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取是否启用 Web 搜索
   */
  static getWebSearchEnabled(): boolean {
    return this.getConfig().get<boolean>('webSearchEnabled', false)
  }

  /**
   * 设置是否启用 Web 搜索
   */
  static async setWebSearchEnabled(enabled: boolean): Promise<void> {
    await this.getConfig().update('webSearchEnabled', enabled, vscode.ConfigurationTarget.Global)
  }

  // ========== Model Settings ==========

  /**
   * 获取默认模型 ID
   */
  static getDefaultModelId(): string {
    return this.getConfig().get<string>('defaultModelId', 'gpt-5.2-codex')
  }

  /**
   * 设置默认模型 ID
   */
  static async setDefaultModelId(modelId: string): Promise<void> {
    await this.getConfig().update('defaultModelId', modelId, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取自定义模型列表
   */
  static getCustomModels(): CodexCustomModelConfig[] {
    const models = this.getConfig().get<{ displayName: string; modelId: string }[]>('customModels', [])
    return models.map((m, i) => ({
      id: `custom_${i}_${m.modelId}`,
      displayName: m.displayName,
      modelId: m.modelId
    }))
  }

  /**
   * 设置自定义模型列表
   */
  static async setCustomModels(models: CodexCustomModelConfig[]): Promise<void> {
    const configModels = models.map(m => ({
      displayName: m.displayName,
      modelId: m.modelId
    }))
    await this.getConfig().update('customModels', configModels, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取所有可用模型 (内置 + 自定义)
   */
  static getAllCodexModels(): CodexModelInfo[] {
    const custom = this.getCustomModels().map(m => ({
      displayName: m.displayName,
      modelId: m.modelId,
      isBuiltIn: false
    }))
    return [...CODEX_BUILT_IN_MODELS, ...custom]
  }

  // ========== Session Defaults ==========

  /**
   * 获取默认 reasoning effort
   */
  static getReasoningEffort(): ReasoningEffort {
    const value = this.getConfig().get<string>('reasoningEffort', 'medium')
    return this.isValidReasoningEffort(value) ? value : 'medium'
  }

  /**
   * 设置默认 reasoning effort
   */
  static async setReasoningEffort(effort: ReasoningEffort): Promise<void> {
    await this.getConfig().update('reasoningEffort', effort, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取默认 reasoning summary
   */
  static getReasoningSummary(): ReasoningSummary {
    const value = this.getConfig().get<string>('reasoningSummary', 'auto')
    return this.isValidReasoningSummary(value) ? value : 'auto'
  }

  /**
   * 设置默认 reasoning summary
   */
  static async setReasoningSummary(summary: ReasoningSummary): Promise<void> {
    await this.getConfig().update('reasoningSummary', summary, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取默认 sandbox mode
   */
  static getSandboxMode(): SandboxMode {
    const value = this.getConfig().get<string>('sandboxMode', 'workspace-write')
    return this.isValidSandboxMode(value) ? value : 'workspace-write'
  }

  /**
   * 设置默认 sandbox mode
   */
  static async setSandboxMode(mode: SandboxMode): Promise<void> {
    await this.getConfig().update('sandboxMode', mode, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取 sandbox mode 标签
   */
  static getSandboxModeLabel(mode: SandboxMode): string {
    const option = SANDBOX_MODE_OPTIONS.find(o => o.id === mode)
    return option?.label ?? mode
  }

  // ========== Auto Cleanup ==========

  /**
   * 获取默认自动清理上下文设置
   */
  static getDefaultAutoCleanupContexts(): boolean {
    return this.getConfig().get<boolean>('defaultAutoCleanupContexts', false)
  }

  /**
   * 设置默认自动清理上下文设置
   */
  static async setDefaultAutoCleanupContexts(value: boolean): Promise<void> {
    await this.getConfig().update('defaultAutoCleanupContexts', value, vscode.ConfigurationTarget.Global)
  }

  // ========== Validation ==========

  /**
   * 验证模型 ID 是否有效
   */
  static isValidModelId(modelId: string): boolean {
    if (!modelId || modelId.trim().length === 0) {
      return false
    }
    return CODEX_BUILT_IN_MODELS.some(m => m.modelId === modelId) ||
           this.getCustomModels().some(m => m.modelId === modelId)
  }

  /**
   * 验证 reasoning effort 是否有效
   */
  static isValidReasoningEffort(effort: string): effort is ReasoningEffort {
    return REASONING_EFFORT_OPTIONS.includes(effort as ReasoningEffort)
  }

  /**
   * 验证 reasoning summary 是否有效
   */
  static isValidReasoningSummary(summary: string): summary is ReasoningSummary {
    return REASONING_SUMMARY_OPTIONS.includes(summary as ReasoningSummary)
  }

  /**
   * 验证 sandbox mode 是否有效
   */
  static isValidSandboxMode(mode: string): mode is SandboxMode {
    return SANDBOX_MODE_OPTIONS.some(o => o.id === mode)
  }

  // ========== Change Listeners ==========

  /**
   * 监听配置变更
   */
  static onDidChangeConfiguration(
    callback: (e: vscode.ConfigurationChangeEvent) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus.codex')) {
        callback(e)
      }
    })
  }
}

// ============================================================================
// Exports
// ============================================================================

export default CodexConfigurable

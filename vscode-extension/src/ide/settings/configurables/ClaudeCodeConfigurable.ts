/**
 * Claude Code 配置逻辑
 * 
 * 翻译自: jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodeConfigurable.kt
 * 
 * 负责管理 Claude Code 相关的设置:
 * - 默认权限 (bypass permissions, auto cleanup contexts)
 * - 运行时设置 (Node.js 路径, 默认模型)
 * - 自定义模型
 * - 思考配置 (thinking level, tokens)
 * - Agents 配置
 */

import * as vscode from 'vscode'

// ============================================================================
// Types
// ============================================================================

/** 模型信息 */
export interface ModelInfo {
  displayName: string
  modelId: string
  isBuiltIn: boolean
}

/** 自定义模型配置 */
export interface CustomModelConfig {
  id: string
  displayName: string
  modelId: string
}

/** 思考级别配置 */
export interface ThinkingLevelConfig {
  id: string
  name: string
  tokens: number
  isBuiltIn: boolean
}

/** Agent 配置项 */
export interface AgentConfigItem {
  enabled: boolean
  description: string
  prompt: string
  tools: string[]
  model: string
  selectionHint: string
}

/** Agents 配置数据 */
export interface AgentsConfigData {
  agents: Record<string, AgentConfigItem>
}

// ============================================================================
// Constants
// ============================================================================

/** 内置模型列表 */
export const BUILT_IN_MODELS: ModelInfo[] = [
  { displayName: 'Claude Opus 4.5', modelId: 'claude-opus-4-5-20251101', isBuiltIn: true },
  { displayName: 'Claude Sonnet 4.5', modelId: 'claude-sonnet-4-5-20250929', isBuiltIn: true },
  { displayName: 'Claude Sonnet 4', modelId: 'claude-sonnet-4-20250514', isBuiltIn: true },
  { displayName: 'Claude Haiku 4.5', modelId: 'claude-haiku-4-5-20251101', isBuiltIn: true },
  { displayName: 'Claude Opus 4.6', modelId: 'claude-opus-4-6', isBuiltIn: true },
  { displayName: 'Claude Sonnet 4.6', modelId: 'claude-sonnet-4-6', isBuiltIn: true },
]

/** 权限模式选项 */
export const PERMISSION_MODES = ['default', 'acceptEdits', 'plan', 'bypassPermissions'] as const
export type PermissionMode = typeof PERMISSION_MODES[number]

/** 思考级别选项 */
export const THINKING_LEVELS = ['off', 'think', 'ultra'] as const
export type ThinkingLevel = typeof THINKING_LEVELS[number]

/** 已知工具列表 (用于 Agent 配置) */
export const KNOWN_TOOLS = [
  'Read', 'Write', 'Edit', 'MultiEdit',
  'Bash', 'Glob', 'Grep', 'LS',
  'Task', 'Skill', 'WebFetch', 'WebSearch',
  'mcp__jetbrains-lsp__*', 'mcp__jetbrains-file__*',
  'mcp__jetbrains-terminal__*', 'mcp__jetbrains_git__*',
  'mcp__context7__*', 'mcp__user_interaction__*'
]

/** ExploreWithVscode Agent 默认配置 */
export const EXPLORE_WITH_VSCODE_DEFAULTS: AgentConfigItem = {
  enabled: true,
  description: 'Explore and analyze the codebase using VS Code LSP capabilities.',
  prompt: `You are an expert code explorer with deep knowledge of VS Code IDE capabilities.
Your goal is to help users understand and navigate codebases efficiently.

Use the available VS Code tools to:
1. Search for files and symbols using FileIndex
2. Find usages and references with FindUsages
3. Analyze code problems with FileProblems
4. Navigate directory structures with DirectoryTree
5. Search code content with CodeSearch

Provide clear, structured responses about code architecture and relationships.`,
  selectionHint: `When the user wants to explore, understand, or analyze their codebase structure,
or needs help navigating and finding code elements, use this agent.`,
  tools: [
    'mcp__jetbrains-lsp__DirectoryTree',
    'mcp__jetbrains-lsp__FileIndex',
    'mcp__jetbrains-lsp__CodeSearch',
    'mcp__jetbrains-lsp__FindUsages',
    'mcp__jetbrains-lsp__FileProblems',
    'mcp__jetbrains-file__ReadFile'
  ],
  model: ''
}

// ============================================================================
// Configuration Service
// ============================================================================

/**
 * Claude Code 配置服务
 * 
 * 提供对 VS Code 设置的统一访问接口
 */
export class ClaudeCodeConfigurable {
  private static readonly SECTION = 'claudeCodePlus.claude'

  /**
   * 获取配置对象
   */
  private static getConfig(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration(this.SECTION)
  }

  // ========== Runtime Settings ==========

  /**
   * 获取 Node.js 路径
   */
  static getNodePath(): string {
    return this.getConfig().get<string>('nodePath', '')
  }

  /**
   * 设置 Node.js 路径
   */
  static async setNodePath(path: string): Promise<void> {
    await this.getConfig().update('nodePath', path, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取默认模型 ID
   */
  static getDefaultModelId(): string {
    return this.getConfig().get<string>('defaultModelId', 'claude-opus-4-5-20251101')
  }

  /**
   * 设置默认模型 ID
   */
  static async setDefaultModelId(modelId: string): Promise<void> {
    await this.getConfig().update('defaultModelId', modelId, vscode.ConfigurationTarget.Global)
  }

  // ========== Custom Models ==========

  /**
   * 获取自定义模型列表
   */
  static getCustomModels(): CustomModelConfig[] {
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
  static async setCustomModels(models: CustomModelConfig[]): Promise<void> {
    const configModels = models.map(m => ({
      displayName: m.displayName,
      modelId: m.modelId
    }))
    await this.getConfig().update('customModels', configModels, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取所有可用模型 (内置 + 自定义)
   */
  static getAllAvailableModels(): ModelInfo[] {
    const custom = this.getCustomModels().map(m => ({
      displayName: m.displayName,
      modelId: m.modelId,
      isBuiltIn: false
    }))
    return [...BUILT_IN_MODELS, ...custom]
  }

  // ========== Thinking Configuration ==========

  /**
   * 获取默认思考级别 ID
   */
  static getDefaultThinkingLevelId(): ThinkingLevel {
    return this.getConfig().get<ThinkingLevel>('defaultThinkingLevel', 'ultra')
  }

  /**
   * 设置默认思考级别 ID
   */
  static async setDefaultThinkingLevelId(levelId: ThinkingLevel): Promise<void> {
    await this.getConfig().update('defaultThinkingLevel', levelId, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取 think 级别的 token 数
   */
  static getThinkTokens(): number {
    return this.getConfig().get<number>('thinkTokens', 2048)
  }

  /**
   * 设置 think 级别的 token 数
   */
  static async setThinkTokens(tokens: number): Promise<void> {
    await this.getConfig().update('thinkTokens', tokens, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取 ultra 级别的 token 数
   */
  static getUltraTokens(): number {
    return this.getConfig().get<number>('ultraTokens', 8096)
  }

  /**
   * 设置 ultra 级别的 token 数
   */
  static async setUltraTokens(tokens: number): Promise<void> {
    await this.getConfig().update('ultraTokens', tokens, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取所有思考级别配置
   */
  static getAllThinkingLevels(): ThinkingLevelConfig[] {
    return [
      { id: 'off', name: 'Off', tokens: 0, isBuiltIn: true },
      { id: 'think', name: 'Think', tokens: this.getThinkTokens(), isBuiltIn: true },
      { id: 'ultra', name: 'Ultra', tokens: this.getUltraTokens(), isBuiltIn: true }
    ]
  }

  /**
   * 根据 ID 获取思考级别配置
   */
  static getThinkingLevelById(id: string): ThinkingLevelConfig | undefined {
    return this.getAllThinkingLevels().find(l => l.id === id)
  }

  // ========== Permissions ==========

  /**
   * 获取权限模式
   */
  static getPermissionMode(): PermissionMode {
    return this.getConfig().get<PermissionMode>('permissionMode', 'default')
  }

  /**
   * 设置权限模式
   */
  static async setPermissionMode(mode: PermissionMode): Promise<void> {
    await this.getConfig().update('permissionMode', mode, vscode.ConfigurationTarget.Global)
  }

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

  // ========== Agents Configuration ==========

  /**
   * 获取 ExploreWithVscode Agent 配置
   */
  static getExploreWithVscodeAgent(): AgentConfigItem {
    const section = vscode.workspace.getConfiguration('claudeCodePlus.claude.agents.exploreWithVscode')
    
    return {
      enabled: section.get<boolean>('enabled', EXPLORE_WITH_VSCODE_DEFAULTS.enabled),
      model: section.get<string>('model', EXPLORE_WITH_VSCODE_DEFAULTS.model),
      description: section.get<string>('description', '') || EXPLORE_WITH_VSCODE_DEFAULTS.description,
      prompt: section.get<string>('prompt', '') || EXPLORE_WITH_VSCODE_DEFAULTS.prompt,
      selectionHint: section.get<string>('selectionHint', '') || EXPLORE_WITH_VSCODE_DEFAULTS.selectionHint,
      tools: section.get<string[]>('tools', []).length > 0 
        ? section.get<string[]>('tools', [])
        : EXPLORE_WITH_VSCODE_DEFAULTS.tools
    }
  }

  /**
   * 设置 ExploreWithVscode Agent 配置
   */
  static async setExploreWithVscodeAgent(config: Partial<AgentConfigItem>): Promise<void> {
    const section = vscode.workspace.getConfiguration('claudeCodePlus.claude.agents.exploreWithVscode')
    
    if (config.enabled !== undefined) {
      await section.update('enabled', config.enabled, vscode.ConfigurationTarget.Global)
    }
    if (config.model !== undefined) {
      await section.update('model', config.model, vscode.ConfigurationTarget.Global)
    }
    if (config.description !== undefined) {
      await section.update('description', config.description, vscode.ConfigurationTarget.Global)
    }
    if (config.prompt !== undefined) {
      await section.update('prompt', config.prompt, vscode.ConfigurationTarget.Global)
    }
    if (config.selectionHint !== undefined) {
      await section.update('selectionHint', config.selectionHint, vscode.ConfigurationTarget.Global)
    }
    if (config.tools !== undefined) {
      await section.update('tools', config.tools, vscode.ConfigurationTarget.Global)
    }
  }

  /**
   * 重置 ExploreWithVscode Agent 为默认值
   */
  static async resetExploreWithVscodeAgent(): Promise<void> {
    await this.setExploreWithVscodeAgent(EXPLORE_WITH_VSCODE_DEFAULTS)
  }

  // ========== Validation ==========

  /**
   * 验证模型 ID 是否有效
   */
  static isValidModelId(modelId: string): boolean {
    if (!modelId || modelId.trim().length === 0) {
      return false
    }
    // 基本格式检查: 应该包含 claude 或是自定义模型
    return modelId.includes('claude') || 
           this.getCustomModels().some(m => m.modelId === modelId)
  }

  /**
   * 验证 token 数是否在有效范围内
   */
  static isValidTokenCount(tokens: number): boolean {
    return tokens >= 1 && tokens <= 128000
  }

  /**
   * 验证权限模式是否有效
   */
  static isValidPermissionMode(mode: string): mode is PermissionMode {
    return PERMISSION_MODES.includes(mode as PermissionMode)
  }

  /**
   * 验证思考级别是否有效
   */
  static isValidThinkingLevel(level: string): level is ThinkingLevel {
    return THINKING_LEVELS.includes(level as ThinkingLevel)
  }

  // ========== Change Listeners ==========

  /**
   * 监听配置变更
   */
  static onDidChangeConfiguration(
    callback: (e: vscode.ConfigurationChangeEvent) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus.claude')) {
        callback(e)
      }
    })
  }
}

// ============================================================================
// Exports
// ============================================================================

export default ClaudeCodeConfigurable

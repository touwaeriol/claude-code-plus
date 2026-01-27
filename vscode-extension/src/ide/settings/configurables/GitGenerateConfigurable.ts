/**
 * Git Generate 配置逻辑
 * 
 * 翻译自: jetbrains-plugin/src/main/kotlin/com/asakii/settings/GitGenerateConfigurable.kt
 * 
 * 负责管理 Git 提交消息自动生成功能的设置:
 * - 功能开关
 * - 后端选择 (Claude 或 Codex)
 * - 模型选择
 * - 思考/推理配置
 * - 自定义提示词
 */

import * as vscode from 'vscode'
import { BackendType } from './ClaudeCodePlusConfigurable'
import { ThinkingLevel } from './ClaudeCodeConfigurable'
import { ReasoningEffort } from './CodexConfigurable'

// ============================================================================
// Constants
// ============================================================================

/** Git Generate 默认系统提示词 */
export const GIT_GENERATE_DEFAULT_SYSTEM_PROMPT = `You are an expert at writing clear, concise Git commit messages.
Follow these guidelines:
1. Use the imperative mood (e.g., "Add feature" not "Added feature")
2. Keep the subject line under 50 characters
3. Separate subject from body with a blank line
4. Wrap the body at 72 characters
5. Use the body to explain what and why, not how
6. Reference relevant issues or tickets when applicable

Format: 
<type>(<scope>): <subject>

<body>

Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert`

/** Git Generate 默认用户提示词 */
export const GIT_GENERATE_DEFAULT_USER_PROMPT = `Based on the following code changes, generate a clear and descriptive commit message.
Focus on the purpose and impact of the changes.

Code changes:
{changes}

Generate the commit message:`

// ============================================================================
// Configuration Service
// ============================================================================

/**
 * Git Generate 配置服务
 * 
 * 提供对 VS Code 设置的统一访问接口
 */
export class GitGenerateConfigurable {
  private static readonly SECTION = 'claudeCodePlus.gitGenerate'

  /**
   * 获取配置对象
   */
  private static getConfig(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration(this.SECTION)
  }

  // ========== Feature Settings ==========

  /**
   * 获取是否启用 Git Generate
   */
  static getEnabled(): boolean {
    return this.getConfig().get<boolean>('enabled', false)
  }

  /**
   * 设置是否启用 Git Generate
   */
  static async setEnabled(enabled: boolean): Promise<void> {
    await this.getConfig().update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取后端类型
   */
  static getBackend(): BackendType {
    const value = this.getConfig().get<string>('backend', 'claude')
    return value === 'codex' ? 'codex' : 'claude'
  }

  /**
   * 设置后端类型
   */
  static async setBackend(backend: BackendType): Promise<void> {
    await this.getConfig().update('backend', backend, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取模型 ID
   */
  static getModel(): string {
    return this.getConfig().get<string>('model', '')
  }

  /**
   * 设置模型 ID
   */
  static async setModel(model: string): Promise<void> {
    await this.getConfig().update('model', model, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取是否保存会话到历史
   */
  static getSaveSession(): boolean {
    return this.getConfig().get<boolean>('saveSession', false)
  }

  /**
   * 设置是否保存会话到历史
   */
  static async setSaveSession(save: boolean): Promise<void> {
    await this.getConfig().update('saveSession', save, vscode.ConfigurationTarget.Global)
  }

  // ========== Claude-specific Settings ==========

  /**
   * 获取 Claude 思考级别
   */
  static getClaudeThinkingLevel(): ThinkingLevel {
    const value = this.getConfig().get<string>('claudeThinkingLevel', 'ultra')
    if (value === 'off' || value === 'think' || value === 'ultra') {
      return value
    }
    return 'ultra'
  }

  /**
   * 设置 Claude 思考级别
   */
  static async setClaudeThinkingLevel(level: ThinkingLevel): Promise<void> {
    await this.getConfig().update('claudeThinkingLevel', level, vscode.ConfigurationTarget.Global)
  }

  // ========== Codex-specific Settings ==========

  /**
   * 获取 Codex reasoning effort
   */
  static getCodexReasoningEffort(): ReasoningEffort {
    const value = this.getConfig().get<string>('codexReasoningEffort', 'xhigh')
    const validOptions: ReasoningEffort[] = ['minimal', 'low', 'medium', 'high', 'xhigh']
    return validOptions.includes(value as ReasoningEffort) ? value as ReasoningEffort : 'xhigh'
  }

  /**
   * 设置 Codex reasoning effort
   */
  static async setCodexReasoningEffort(effort: ReasoningEffort): Promise<void> {
    await this.getConfig().update('codexReasoningEffort', effort, vscode.ConfigurationTarget.Global)
  }

  // ========== Prompt Settings ==========

  /**
   * 获取系统提示词
   */
  static getSystemPrompt(): string {
    return this.getConfig().get<string>('systemPrompt', '')
  }

  /**
   * 获取有效的系统提示词 (自定义或默认)
   */
  static getEffectiveSystemPrompt(): string {
    const custom = this.getSystemPrompt()
    return custom.trim() || GIT_GENERATE_DEFAULT_SYSTEM_PROMPT
  }

  /**
   * 设置系统提示词
   */
  static async setSystemPrompt(prompt: string): Promise<void> {
    // 如果与默认值相同，存储空字符串
    const value = prompt.trim() === GIT_GENERATE_DEFAULT_SYSTEM_PROMPT.trim() ? '' : prompt
    await this.getConfig().update('systemPrompt', value, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取用户提示词
   */
  static getUserPrompt(): string {
    return this.getConfig().get<string>('userPrompt', '')
  }

  /**
   * 获取有效的用户提示词 (自定义或默认)
   */
  static getEffectiveUserPrompt(): string {
    const custom = this.getUserPrompt()
    return custom.trim() || GIT_GENERATE_DEFAULT_USER_PROMPT
  }

  /**
   * 设置用户提示词
   */
  static async setUserPrompt(prompt: string): Promise<void> {
    // 如果与默认值相同，存储空字符串
    const value = prompt.trim() === GIT_GENERATE_DEFAULT_USER_PROMPT.trim() ? '' : prompt
    await this.getConfig().update('userPrompt', value, vscode.ConfigurationTarget.Global)
  }

  // ========== Reset ==========

  /**
   * 重置为默认值
   */
  static async resetToDefault(): Promise<void> {
    await this.setEnabled(false)
    await this.setBackend('claude')
    await this.setModel('')
    await this.setSaveSession(false)
    await this.setClaudeThinkingLevel('ultra')
    await this.setCodexReasoningEffort('xhigh')
    await this.setSystemPrompt('')
    await this.setUserPrompt('')
  }

  // ========== Change Listeners ==========

  /**
   * 监听配置变更
   */
  static onDidChangeConfiguration(
    callback: (e: vscode.ConfigurationChangeEvent) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus.gitGenerate')) {
        callback(e)
      }
    })
  }
}

// ============================================================================
// Exports
// ============================================================================

export default GitGenerateConfigurable

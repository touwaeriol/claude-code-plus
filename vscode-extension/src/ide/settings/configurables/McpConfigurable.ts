/**
 * MCP 配置逻辑
 * 
 * 翻译自: jetbrains-plugin/src/main/kotlin/com/asakii/settings/McpConfigurable.kt
 * 
 * 负责管理 MCP (Model Context Protocol) 服务器配置:
 * - 内置 MCP 服务器 (User Interaction, VS Code LSP, VS Code File, Context7, Terminal, Git)
 * - 自定义 MCP 服务器
 * - 服务器启用/禁用
 * - 后端选择
 * - 自定义指令
 */

import * as vscode from 'vscode'
import { MCP_BACKEND_ALL, MCP_BACKEND_CLAUDE, MCP_BACKEND_CODEX } from './ClaudeCodePlusConfigurable'

// ============================================================================
// Types
// ============================================================================

/** MCP 服务器级别 */
export type McpServerLevel = 'builtin' | 'global' | 'project'

/** MCP 服务器条目 */
export interface McpServerEntry {
  name: string
  enabled: boolean
  enabledBackends: string[]
  level: McpServerLevel
  configSummary: string
  isBuiltIn: boolean
  instructions: string
  instructionsClaude: string
  instructionsCodex: string
  timeout: number
  // 内置服务器特定属性
  apiKey?: string
  maxOutputLines?: number
  maxOutputChars?: number
  readTimeout?: number
  disableBuiltinBash?: boolean
  disableBuiltinTools?: boolean
  allowExternal?: boolean
  externalRules?: string
  commitLanguage?: string
  // 自定义服务器属性
  config?: McpServerConfig
}

/** MCP 服务器配置 */
export interface McpServerConfig {
  command?: string
  args?: string[]
  env?: Record<string, string>
  url?: string
  headers?: Record<string, string>
  type?: 'stdio' | 'http' | 'sse'
}

/** 自定义 MCP 服务器配置 */
export interface CustomMcpServerConfig {
  name: string
  enabled: boolean
  backends: string[]
  config: McpServerConfig
  instructions: string
  timeout: number
}

// ============================================================================
// Constants
// ============================================================================

/** 内置 MCP 服务器名称 */
export const BUILTIN_MCP_SERVERS = {
  USER_INTERACTION: 'User Interaction',
  VSCODE_LSP: 'VS Code LSP',
  VSCODE_FILE: 'VS Code File',
  CONTEXT7: 'Context7',
  TERMINAL: 'Terminal',
  GIT: 'Git'
} as const

/** 内置 MCP 服务器描述 */
export const BUILTIN_MCP_DESCRIPTIONS: Record<string, string> = {
  [BUILTIN_MCP_SERVERS.USER_INTERACTION]: 'Ask user questions and get interactive feedback',
  [BUILTIN_MCP_SERVERS.VSCODE_LSP]: 'VS Code Language Server Protocol features',
  [BUILTIN_MCP_SERVERS.VSCODE_FILE]: 'VS Code file operations (read, write, edit)',
  [BUILTIN_MCP_SERVERS.CONTEXT7]: 'Context7 documentation retrieval',
  [BUILTIN_MCP_SERVERS.TERMINAL]: 'VS Code integrated terminal',
  [BUILTIN_MCP_SERVERS.GIT]: 'Git version control operations'
}

// ============================================================================
// Configuration Service
// ============================================================================

/**
 * MCP 配置服务
 * 
 * 提供对 VS Code 设置的统一访问接口
 */
export class McpConfigurable {
  private static readonly SECTION = 'claudeCodePlus.mcp'

  /**
   * 获取配置对象
   */
  private static getConfig(subsection?: string): vscode.WorkspaceConfiguration {
    const section = subsection ? `${this.SECTION}.${subsection}` : this.SECTION
    return vscode.workspace.getConfiguration(section)
  }

  // ========== Helper Methods ==========

  /**
   * 规范化后端键
   */
  private static normalizeBackendKeys(keys: string[]): string[] {
    const normalized = keys
      .map(k => k.trim().toLowerCase())
      .filter(k => k.length > 0)
    
    if (normalized.includes(MCP_BACKEND_ALL)) {
      return [MCP_BACKEND_ALL]
    }
    
    return normalized.filter(k => 
      k === MCP_BACKEND_CLAUDE || k === MCP_BACKEND_CODEX
    )
  }

  /**
   * 格式化后端标签
   */
  static formatBackendLabel(keys: string[]): string {
    const normalized = this.normalizeBackendKeys(keys)
    if (normalized.length === 0) return '-'
    if (normalized.includes(MCP_BACKEND_ALL)) return 'All'
    
    const labels: string[] = []
    if (normalized.includes(MCP_BACKEND_CLAUDE)) labels.push('Claude Code')
    if (normalized.includes(MCP_BACKEND_CODEX)) labels.push('Codex')
    
    return labels.length > 0 ? labels.join('/') : '-'
  }

  // ========== User Interaction MCP ==========

  static getUserInteractionEnabled(): boolean {
    return this.getConfig('userInteraction').get<boolean>('enabled', true)
  }

  static async setUserInteractionEnabled(enabled: boolean): Promise<void> {
    await this.getConfig('userInteraction').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getUserInteractionBackends(): string[] {
    return this.getConfig('userInteraction').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setUserInteractionBackends(backends: string[]): Promise<void> {
    await this.getConfig('userInteraction').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getUserInteractionInstructions(): string {
    return this.getConfig('userInteraction').get<string>('instructions', '')
  }

  static async setUserInteractionInstructions(instructions: string): Promise<void> {
    await this.getConfig('userInteraction').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getUserInteractionTimeout(): number {
    return this.getConfig('userInteraction').get<number>('timeout', 3600)
  }

  static async setUserInteractionTimeout(timeout: number): Promise<void> {
    await this.getConfig('userInteraction').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  // ========== VS Code LSP MCP ==========

  static getVscodeLspEnabled(): boolean {
    return this.getConfig('vscodeLsp').get<boolean>('enabled', true)
  }

  static async setVscodeLspEnabled(enabled: boolean): Promise<void> {
    await this.getConfig('vscodeLsp').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getVscodeLspBackends(): string[] {
    return this.getConfig('vscodeLsp').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setVscodeLspBackends(backends: string[]): Promise<void> {
    await this.getConfig('vscodeLsp').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getVscodeLspInstructions(): string {
    return this.getConfig('vscodeLsp').get<string>('instructions', '')
  }

  static async setVscodeLspInstructions(instructions: string): Promise<void> {
    await this.getConfig('vscodeLsp').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getVscodeLspTimeout(): number {
    return this.getConfig('vscodeLsp').get<number>('timeout', 60)
  }

  static async setVscodeLspTimeout(timeout: number): Promise<void> {
    await this.getConfig('vscodeLsp').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  // ========== VS Code File MCP ==========

  static getVscodeFileEnabled(): boolean {
    return this.getConfig('vscodeFile').get<boolean>('enabled', true)
  }

  static async setVscodeFileEnabled(enabled: boolean): Promise<void> {
    await this.getConfig('vscodeFile').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileBackends(): string[] {
    return this.getConfig('vscodeFile').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setVscodeFileBackends(backends: string[]): Promise<void> {
    await this.getConfig('vscodeFile').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileInstructions(): string {
    return this.getConfig('vscodeFile').get<string>('instructions', '')
  }

  static async setVscodeFileInstructions(instructions: string): Promise<void> {
    await this.getConfig('vscodeFile').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileTimeout(): number {
    return this.getConfig('vscodeFile').get<number>('timeout', 60)
  }

  static async setVscodeFileTimeout(timeout: number): Promise<void> {
    await this.getConfig('vscodeFile').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileDisableBuiltinTools(): boolean {
    return this.getConfig('vscodeFile').get<boolean>('disableBuiltinTools', false)
  }

  static async setVscodeFileDisableBuiltinTools(disable: boolean): Promise<void> {
    await this.getConfig('vscodeFile').update('disableBuiltinTools', disable, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileAllowExternal(): boolean {
    return this.getConfig('vscodeFile').get<boolean>('allowExternal', true)
  }

  static async setVscodeFileAllowExternal(allow: boolean): Promise<void> {
    await this.getConfig('vscodeFile').update('allowExternal', allow, vscode.ConfigurationTarget.Global)
  }

  static getVscodeFileExternalRules(): string {
    return this.getConfig('vscodeFile').get<string>('externalRules', '[]')
  }

  static async setVscodeFileExternalRules(rules: string): Promise<void> {
    await this.getConfig('vscodeFile').update('externalRules', rules, vscode.ConfigurationTarget.Global)
  }

  // ========== Context7 MCP ==========

  static getContext7Enabled(): boolean {
    return this.getConfig('context7').get<boolean>('enabled', false)
  }

  static async setContext7Enabled(enabled: boolean): Promise<void> {
    await this.getConfig('context7').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getContext7Backends(): string[] {
    return this.getConfig('context7').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setContext7Backends(backends: string[]): Promise<void> {
    await this.getConfig('context7').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getContext7Instructions(): string {
    return this.getConfig('context7').get<string>('instructions', '')
  }

  static async setContext7Instructions(instructions: string): Promise<void> {
    await this.getConfig('context7').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getContext7Timeout(): number {
    return this.getConfig('context7').get<number>('timeout', 60)
  }

  static async setContext7Timeout(timeout: number): Promise<void> {
    await this.getConfig('context7').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  static getContext7ApiKey(): string {
    return this.getConfig('context7').get<string>('apiKey', '')
  }

  static async setContext7ApiKey(apiKey: string): Promise<void> {
    await this.getConfig('context7').update('apiKey', apiKey, vscode.ConfigurationTarget.Global)
  }

  // ========== Terminal MCP ==========

  static getTerminalEnabled(): boolean {
    return this.getConfig('terminal').get<boolean>('enabled', false)
  }

  static async setTerminalEnabled(enabled: boolean): Promise<void> {
    await this.getConfig('terminal').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getTerminalBackends(): string[] {
    return this.getConfig('terminal').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setTerminalBackends(backends: string[]): Promise<void> {
    await this.getConfig('terminal').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getTerminalInstructions(): string {
    return this.getConfig('terminal').get<string>('instructions', '')
  }

  static async setTerminalInstructions(instructions: string): Promise<void> {
    await this.getConfig('terminal').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getTerminalTimeout(): number {
    return this.getConfig('terminal').get<number>('timeout', 60)
  }

  static async setTerminalTimeout(timeout: number): Promise<void> {
    await this.getConfig('terminal').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  static getTerminalMaxOutputLines(): number {
    return this.getConfig('terminal').get<number>('maxOutputLines', 500)
  }

  static async setTerminalMaxOutputLines(lines: number): Promise<void> {
    await this.getConfig('terminal').update('maxOutputLines', lines, vscode.ConfigurationTarget.Global)
  }

  static getTerminalMaxOutputChars(): number {
    return this.getConfig('terminal').get<number>('maxOutputChars', 50000)
  }

  static async setTerminalMaxOutputChars(chars: number): Promise<void> {
    await this.getConfig('terminal').update('maxOutputChars', chars, vscode.ConfigurationTarget.Global)
  }

  static getTerminalReadTimeout(): number {
    return this.getConfig('terminal').get<number>('readTimeout', 30)
  }

  static async setTerminalReadTimeout(timeout: number): Promise<void> {
    await this.getConfig('terminal').update('readTimeout', timeout, vscode.ConfigurationTarget.Global)
  }

  static getTerminalDisableBuiltinBash(): boolean {
    return this.getConfig('terminal').get<boolean>('disableBuiltinBash', false)
  }

  static async setTerminalDisableBuiltinBash(disable: boolean): Promise<void> {
    await this.getConfig('terminal').update('disableBuiltinBash', disable, vscode.ConfigurationTarget.Global)
  }

  // ========== Git MCP ==========

  static getGitEnabled(): boolean {
    return this.getConfig('git').get<boolean>('enabled', false)
  }

  static async setGitEnabled(enabled: boolean): Promise<void> {
    await this.getConfig('git').update('enabled', enabled, vscode.ConfigurationTarget.Global)
  }

  static getGitBackends(): string[] {
    return this.getConfig('git').get<string[]>('backends', [MCP_BACKEND_ALL])
  }

  static async setGitBackends(backends: string[]): Promise<void> {
    await this.getConfig('git').update('backends', backends, vscode.ConfigurationTarget.Global)
  }

  static getGitInstructions(): string {
    return this.getConfig('git').get<string>('instructions', '')
  }

  static async setGitInstructions(instructions: string): Promise<void> {
    await this.getConfig('git').update('instructions', instructions, vscode.ConfigurationTarget.Global)
  }

  static getGitTimeout(): number {
    return this.getConfig('git').get<number>('timeout', 60)
  }

  static async setGitTimeout(timeout: number): Promise<void> {
    await this.getConfig('git').update('timeout', timeout, vscode.ConfigurationTarget.Global)
  }

  static getGitCommitLanguage(): string {
    return this.getConfig('git').get<string>('commitLanguage', 'en')
  }

  static async setGitCommitLanguage(language: string): Promise<void> {
    await this.getConfig('git').update('commitLanguage', language, vscode.ConfigurationTarget.Global)
  }

  // ========== Custom MCP Servers ==========

  /**
   * 获取自定义 MCP 服务器列表
   */
  static getCustomServers(): CustomMcpServerConfig[] {
    return this.getConfig().get<CustomMcpServerConfig[]>('customServers', [])
  }

  /**
   * 设置自定义 MCP 服务器列表
   */
  static async setCustomServers(servers: CustomMcpServerConfig[]): Promise<void> {
    await this.getConfig().update('customServers', servers, vscode.ConfigurationTarget.Global)
  }

  /**
   * 添加自定义 MCP 服务器
   */
  static async addCustomServer(server: CustomMcpServerConfig): Promise<void> {
    const servers = this.getCustomServers()
    servers.push(server)
    await this.setCustomServers(servers)
  }

  /**
   * 更新自定义 MCP 服务器
   */
  static async updateCustomServer(name: string, server: Partial<CustomMcpServerConfig>): Promise<void> {
    const servers = this.getCustomServers()
    const index = servers.findIndex(s => s.name === name)
    if (index >= 0) {
      servers[index] = { ...servers[index], ...server }
      await this.setCustomServers(servers)
    }
  }

  /**
   * 删除自定义 MCP 服务器
   */
  static async removeCustomServer(name: string): Promise<void> {
    const servers = this.getCustomServers()
    const filtered = servers.filter(s => s.name !== name)
    await this.setCustomServers(filtered)
  }

  // ========== All Built-in Servers ==========

  /**
   * 获取所有内置 MCP 服务器条目
   */
  static getAllBuiltInServers(): McpServerEntry[] {
    return [
      {
        name: BUILTIN_MCP_SERVERS.USER_INTERACTION,
        enabled: this.getUserInteractionEnabled(),
        enabledBackends: this.getUserInteractionBackends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.USER_INTERACTION],
        isBuiltIn: true,
        instructions: this.getUserInteractionInstructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getUserInteractionTimeout()
      },
      {
        name: BUILTIN_MCP_SERVERS.VSCODE_LSP,
        enabled: this.getVscodeLspEnabled(),
        enabledBackends: this.getVscodeLspBackends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.VSCODE_LSP],
        isBuiltIn: true,
        instructions: this.getVscodeLspInstructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getVscodeLspTimeout()
      },
      {
        name: BUILTIN_MCP_SERVERS.VSCODE_FILE,
        enabled: this.getVscodeFileEnabled(),
        enabledBackends: this.getVscodeFileBackends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.VSCODE_FILE],
        isBuiltIn: true,
        instructions: this.getVscodeFileInstructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getVscodeFileTimeout(),
        disableBuiltinTools: this.getVscodeFileDisableBuiltinTools(),
        allowExternal: this.getVscodeFileAllowExternal(),
        externalRules: this.getVscodeFileExternalRules()
      },
      {
        name: BUILTIN_MCP_SERVERS.CONTEXT7,
        enabled: this.getContext7Enabled(),
        enabledBackends: this.getContext7Backends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.CONTEXT7],
        isBuiltIn: true,
        instructions: this.getContext7Instructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getContext7Timeout(),
        apiKey: this.getContext7ApiKey()
      },
      {
        name: BUILTIN_MCP_SERVERS.TERMINAL,
        enabled: this.getTerminalEnabled(),
        enabledBackends: this.getTerminalBackends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.TERMINAL],
        isBuiltIn: true,
        instructions: this.getTerminalInstructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getTerminalTimeout(),
        maxOutputLines: this.getTerminalMaxOutputLines(),
        maxOutputChars: this.getTerminalMaxOutputChars(),
        readTimeout: this.getTerminalReadTimeout(),
        disableBuiltinBash: this.getTerminalDisableBuiltinBash()
      },
      {
        name: BUILTIN_MCP_SERVERS.GIT,
        enabled: this.getGitEnabled(),
        enabledBackends: this.getGitBackends(),
        level: 'builtin',
        configSummary: BUILTIN_MCP_DESCRIPTIONS[BUILTIN_MCP_SERVERS.GIT],
        isBuiltIn: true,
        instructions: this.getGitInstructions(),
        instructionsClaude: '',
        instructionsCodex: '',
        timeout: this.getGitTimeout(),
        commitLanguage: this.getGitCommitLanguage()
      }
    ]
  }

  // ========== Change Listeners ==========

  /**
   * 监听配置变更
   */
  static onDidChangeConfiguration(
    callback: (e: vscode.ConfigurationChangeEvent) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus.mcp')) {
        callback(e)
      }
    })
  }
}

// ============================================================================
// Exports
// ============================================================================

export default McpConfigurable

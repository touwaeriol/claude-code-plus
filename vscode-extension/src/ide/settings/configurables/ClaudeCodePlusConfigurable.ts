/**
 * Claude Code Plus 主配置逻辑
 * 
 * 翻译自: jetbrains-plugin/src/main/kotlin/com/asakii/settings/ClaudeCodePlusConfigurable.kt
 * 
 * 负责管理 Claude Code Plus 的主要设置:
 * - 默认后端类型 (claude 或 codex)
 * - 默认权限绕过
 * - 部分消息包含
 */

import * as vscode from 'vscode'

// ============================================================================
// Types
// ============================================================================

/** 后端类型 */
export const BACKEND_TYPES = ['claude', 'codex'] as const
export type BackendType = typeof BACKEND_TYPES[number]

/** 后端选项 */
export interface BackendOption {
  key: BackendType
  label: string
}

// ============================================================================
// Constants
// ============================================================================

/** 后端选项列表 */
export const BACKEND_OPTIONS: BackendOption[] = [
  { key: 'claude', label: 'Claude Code' },
  { key: 'codex', label: 'Codex' }
]

/** 后端类型常量 */
export const MCP_BACKEND_CLAUDE = 'claude'
export const MCP_BACKEND_CODEX = 'codex'
export const MCP_BACKEND_ALL = 'all'

// ============================================================================
// Configuration Service
// ============================================================================

/**
 * Claude Code Plus 主配置服务
 * 
 * 提供对 VS Code 设置的统一访问接口
 */
export class ClaudeCodePlusConfigurable {
  private static readonly SECTION = 'claudeCodePlus'

  /**
   * 获取配置对象
   */
  private static getConfig(): vscode.WorkspaceConfiguration {
    return vscode.workspace.getConfiguration(this.SECTION)
  }

  // ========== Backend Settings ==========

  /**
   * 获取默认后端类型
   */
  static getDefaultBackendType(): BackendType {
    const value = this.getConfig().get<string>('defaultBackendType', 'claude')
    return this.isValidBackendType(value) ? value : 'claude'
  }

  /**
   * 设置默认后端类型
   */
  static async setDefaultBackendType(type: BackendType): Promise<void> {
    await this.getConfig().update('defaultBackendType', type, vscode.ConfigurationTarget.Global)
  }

  /**
   * 获取后端标签
   */
  static getBackendLabel(type: BackendType): string {
    const option = BACKEND_OPTIONS.find(o => o.key === type)
    return option?.label ?? type
  }

  // ========== Permission Settings ==========

  /**
   * 获取默认绕过权限设置
   */
  static getDefaultBypassPermissions(): boolean {
    return this.getConfig().get<boolean>('defaultBypassPermissions', false)
  }

  /**
   * 设置默认绕过权限设置
   */
  static async setDefaultBypassPermissions(value: boolean): Promise<void> {
    await this.getConfig().update('defaultBypassPermissions', value, vscode.ConfigurationTarget.Global)
  }

  // ========== Stream Settings ==========

  /**
   * 获取是否包含部分消息
   */
  static getIncludePartialMessages(): boolean {
    return this.getConfig().get<boolean>('includePartialMessages', true)
  }

  /**
   * 设置是否包含部分消息
   */
  static async setIncludePartialMessages(value: boolean): Promise<void> {
    await this.getConfig().update('includePartialMessages', value, vscode.ConfigurationTarget.Global)
  }

  // ========== Validation ==========

  /**
   * 验证后端类型是否有效
   */
  static isValidBackendType(type: string): type is BackendType {
    return BACKEND_TYPES.includes(type as BackendType)
  }

  // ========== Change Listeners ==========

  /**
   * 监听配置变更
   */
  static onDidChangeConfiguration(
    callback: (e: vscode.ConfigurationChangeEvent) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus')) {
        callback(e)
      }
    })
  }

  /**
   * 监听后端类型变更
   */
  static onDidChangeBackendType(
    callback: (newType: BackendType) => void
  ): vscode.Disposable {
    return vscode.workspace.onDidChangeConfiguration(e => {
      if (e.affectsConfiguration('claudeCodePlus.defaultBackendType')) {
        callback(this.getDefaultBackendType())
      }
    })
  }
}

// ============================================================================
// Exports
// ============================================================================

export default ClaudeCodePlusConfigurable

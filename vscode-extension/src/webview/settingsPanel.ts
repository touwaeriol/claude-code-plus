import * as crypto from 'crypto'
import * as vscode from 'vscode'

import { generateSettingsHtml } from './settings/settingsHtml'

/**
 * Settings data structure matching the settings pages
 */
interface SettingsData {
  claude: {
    defaultModelId: string
    defaultThinkingLevel: string
    defaultThinkingTokens: number
    nodePath: string
    defaultAutoCleanupContexts: boolean
  }
  defaultBypassPermissions: boolean
  includePartialMessages: boolean
  codex: {
    defaultModelId: string
    path: string
    webSearchEnabled: boolean
    reasoningEffort: string
    reasoningSummary: string
    sandboxMode: string
    defaultAutoCleanupContexts: boolean
  }
  gitGenerate: {
    backend: string
    model: string
    language: string
  }
  agent: {
    enableJetBrainsMcp: boolean
    enableJetBrainsFileMcp: boolean
    enableTerminalMcp: boolean
    enableGitMcp: boolean
    terminalDisableBuiltinBash: boolean
    jetbrainsFileDisableBuiltinTools: boolean
    jetbrainsFileDisabledTools: string
  }
  mcp: {
    servers: McpServerConfig[]
  }
}

interface McpServerConfig {
  name: string
  type: 'builtin' | 'custom'
  enabled: boolean
  backend: 'all' | 'claude' | 'codex'
  status?: 'connected' | 'disconnected' | 'error'
}

/**
 * Message types from webview to extension
 */
type WebviewMessage =
  | { type: 'getSettings' }
  | { type: 'updateSetting'; key: string; value: unknown }
  | { type: 'resetSettings' }
  | { type: 'browsePath'; target: string }
  | { type: 'getMcpServers' }
  | { type: 'refreshMcpStatus' }

/**
 * Settings Panel - displays independent settings UI in editor area
 */
export class SettingsPanel {
  private static currentPanel: vscode.WebviewPanel | undefined

  static show(context: vscode.ExtensionContext) {
    const existing = SettingsPanel.currentPanel
    if (existing) {
      existing.reveal(existing.viewColumn)
      return
    }

    const panel = vscode.window.createWebviewPanel(
      'claudeCodePlus.settings',
      'Claude Code Plus Settings',
      vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
      }
    )

    const nonce = crypto.randomBytes(16).toString('base64')
    panel.webview.html = generateSettingsHtml({
      nonce,
      cspSource: panel.webview.cspSource,
    })

    // Handle messages from webview
    const messageDisposer = panel.webview.onDidReceiveMessage(
      async (message: WebviewMessage) => {
        switch (message.type) {
          case 'getSettings':
            await SettingsPanel.sendSettings(panel.webview)
            break
          case 'updateSetting':
            await SettingsPanel.updateSetting(message.key, message.value)
            void panel.webview.postMessage({ type: 'settingUpdated', key: message.key, success: true })
            break
          case 'resetSettings':
            await SettingsPanel.resetSettings()
            await SettingsPanel.sendSettings(panel.webview)
            break
          case 'browsePath':
            await SettingsPanel.handleBrowsePath(panel.webview, message.target)
            break
          case 'getMcpServers':
            await SettingsPanel.sendMcpServers(panel.webview)
            break
          case 'refreshMcpStatus':
            await SettingsPanel.sendMcpServers(panel.webview)
            break
        }
      }
    )

    // Listen for configuration changes
    const configDisposer = vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('claudeCodePlus')) {
        void SettingsPanel.sendSettings(panel.webview)
      }
    })

    panel.onDidDispose(() => {
      SettingsPanel.currentPanel = undefined
      messageDisposer.dispose()
      configDisposer.dispose()
    })

    SettingsPanel.currentPanel = panel
  }

  /**
   * Send current settings to webview
   */
  private static async sendSettings(webview: vscode.Webview): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')

    const settings: SettingsData = {
      claude: {
        defaultModelId: config.get('claude.defaultModelId', 'claude-opus-4-6'),
        defaultThinkingLevel: config.get('claude.defaultThinkingLevel', 'HIGH'),
        defaultThinkingTokens: config.get('claude.defaultThinkingTokens', 8192),
        nodePath: config.get('claude.nodePath', ''),
        defaultAutoCleanupContexts: config.get('claude.defaultAutoCleanupContexts', true),
      },
      defaultBypassPermissions: config.get('defaultBypassPermissions', false),
      includePartialMessages: config.get('includePartialMessages', true),
      codex: {
        defaultModelId: config.get('codex.defaultModelId', 'gpt-5.2-codex'),
        path: config.get('codex.path', ''),
        webSearchEnabled: config.get('codex.webSearchEnabled', false),
        reasoningEffort: config.get('codex.reasoningEffort', 'medium'),
        reasoningSummary: config.get('codex.reasoningSummary', 'auto'),
        sandboxMode: config.get('codex.sandboxMode', 'workspace-write'),
        defaultAutoCleanupContexts: config.get('codex.defaultAutoCleanupContexts', true),
      },
      gitGenerate: {
        backend: config.get('gitGenerate.backend', 'claude'),
        model: config.get('gitGenerate.model', 'default'),
        language: config.get('gitGenerate.language', 'en'),
      },
      agent: {
        enableJetBrainsMcp: config.get('agent.enableJetBrainsMcp', true),
        enableJetBrainsFileMcp: config.get('agent.enableJetBrainsFileMcp', true),
        enableTerminalMcp: config.get('agent.enableTerminalMcp', false),
        enableGitMcp: config.get('agent.enableGitMcp', false),
        terminalDisableBuiltinBash: config.get('agent.terminalDisableBuiltinBash', true),
        jetbrainsFileDisableBuiltinTools: config.get('agent.jetbrainsFileDisableBuiltinTools', true),
        jetbrainsFileDisabledTools: config.get('agent.jetbrainsFileDisabledTools', 'Read,Write,Edit'),
      },
      mcp: {
        servers: [],
      },
    }

    await webview.postMessage({ type: 'settings', data: settings })
  }

  /**
   * Update a single setting
   */
  private static async updateSetting(key: string, value: unknown): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')
    await config.update(key, value, vscode.ConfigurationTarget.Global)
  }

  /**
   * Reset all settings to defaults
   */
  private static async resetSettings(): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')
    const keysToReset = [
      'claude.defaultModelId',
      'claude.defaultThinkingLevel',
      'claude.defaultThinkingTokens',
      'claude.nodePath',
      'claude.defaultAutoCleanupContexts',
      'defaultBypassPermissions',
      'includePartialMessages',
      'codex.defaultModelId',
      'codex.path',
      'codex.webSearchEnabled',
      'codex.reasoningEffort',
      'codex.reasoningSummary',
      'codex.sandboxMode',
      'codex.defaultAutoCleanupContexts',
      'gitGenerate.backend',
      'gitGenerate.model',
      'gitGenerate.language',
      'agent.enableJetBrainsMcp',
      'agent.enableJetBrainsFileMcp',
      'agent.enableTerminalMcp',
      'agent.enableGitMcp',
      'agent.terminalDisableBuiltinBash',
      'agent.jetbrainsFileDisableBuiltinTools',
      'agent.jetbrainsFileDisabledTools',
    ]

    for (const key of keysToReset) {
      await config.update(key, undefined, vscode.ConfigurationTarget.Global)
    }
  }

  /**
   * Handle file/folder browse request
   */
  private static async handleBrowsePath(webview: vscode.Webview, target: string): Promise<void> {
    const options: vscode.OpenDialogOptions = {
      canSelectMany: false,
      openLabel: 'Select',
    }

    // Determine if we're selecting a file or folder based on target
    if (target.includes('Path') || target.includes('path')) {
      options.canSelectFiles = true
      options.canSelectFolders = false
    } else {
      options.canSelectFiles = false
      options.canSelectFolders = true
    }

    const result = await vscode.window.showOpenDialog(options)
    if (result && result.length > 0) {
      const path = result[0].fsPath
      await webview.postMessage({ type: 'pathSelected', target, path })
    }
  }

  /**
   * Send MCP server list to webview
   */
  private static async sendMcpServers(webview: vscode.Webview): Promise<void> {
    // Built-in servers (always available)
    const builtinServers: McpServerConfig[] = [
      { name: 'ide-terminal', type: 'builtin', enabled: true, backend: 'all', status: 'connected' },
      { name: 'ide-file', type: 'builtin', enabled: true, backend: 'all', status: 'connected' },
      { name: 'ide-lsp', type: 'builtin', enabled: true, backend: 'all', status: 'connected' },
      { name: 'ide-git', type: 'builtin', enabled: true, backend: 'all', status: 'connected' },
    ]

    // TODO: Load custom servers from configuration
    const customServers: McpServerConfig[] = []

    await webview.postMessage({
      type: 'mcpServers',
      data: {
        builtin: builtinServers,
        custom: customServers,
      },
    })
  }
}

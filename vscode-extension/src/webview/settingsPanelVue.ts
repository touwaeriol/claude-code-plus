import * as crypto from 'crypto'
import * as fs from 'fs'
import * as path from 'path'
import * as vscode from 'vscode'
import { execSync } from 'child_process'

/**
 * Detected executable info
 */
interface DetectedInfo {
  path: string
  version?: string
}

/**
 * Message types from webview to extension
 */
type WebviewMessage =
  | { type: 'getSettings' }
  | { type: 'saveSetting'; payload: { key: string; value: unknown } }
  | { type: 'browseFile'; payload: { settingKey: string } }
  | { type: 'detectNode' }
  | { type: 'detectCodex' }

/**
 * Settings Panel - displays Vue-based settings UI
 */
export class SettingsPanel {
  private static currentPanel: vscode.WebviewPanel | undefined
  private static extensionUri: vscode.Uri

  static show(context: vscode.ExtensionContext) {
    SettingsPanel.extensionUri = context.extensionUri

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
        localResourceRoots: [
          vscode.Uri.joinPath(context.extensionUri, 'out', 'webview-ui'),
        ],
      }
    )

    panel.webview.html = SettingsPanel.getWebviewContent(panel.webview, context.extensionUri)

    // Handle messages from webview
    const messageDisposer = panel.webview.onDidReceiveMessage(
      async (message: WebviewMessage) => {
        switch (message.type) {
          case 'getSettings':
            await SettingsPanel.sendSettings(panel.webview)
            break
          case 'saveSetting':
            await SettingsPanel.saveSetting(message.payload.key, message.payload.value)
            break
          case 'browseFile':
            await SettingsPanel.handleBrowseFile(panel.webview, message.payload.settingKey)
            break
          case 'detectNode':
            await SettingsPanel.detectNode(panel.webview)
            break
          case 'detectCodex':
            await SettingsPanel.detectCodex(panel.webview)
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
   * Generate webview HTML that loads Vue app
   */
  private static getWebviewContent(webview: vscode.Webview, extensionUri: vscode.Uri): string {
    const webviewUiPath = vscode.Uri.joinPath(extensionUri, 'out', 'webview-ui')
    
    // Get URIs for the built assets
    const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(webviewUiPath, 'assets', 'index.js'))
    const styleUri = webview.asWebviewUri(vscode.Uri.joinPath(webviewUiPath, 'assets', 'index.css'))

    const nonce = crypto.randomBytes(16).toString('base64')

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src ${webview.cspSource} 'unsafe-inline'; script-src 'nonce-${nonce}' 'wasm-unsafe-eval'; font-src ${webview.cspSource}; img-src ${webview.cspSource} data:; connect-src ${webview.cspSource};">
  <title>Claude Code Plus Settings</title>
  <link rel="stylesheet" href="${styleUri}">
</head>
<body>
  <div id="app"></div>
  <script type="module" nonce="${nonce}" src="${scriptUri}"></script>
</body>
</html>`
  }

  /**
   * Send current settings to webview
   */
  private static async sendSettings(webview: vscode.Webview): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')

    // Load MCP servers from config, with defaults
    const savedMcpServers = config.get<unknown[]>('mcp.servers', [])
    const mcpServers = savedMcpServers.length > 0 ? savedMcpServers : SettingsPanel.getDefaultMcpServers(config)

    const settings = {
      claude: {
        defaultBypassPermissions: config.get('defaultBypassPermissions', false),
        defaultAutoCleanupContexts: config.get('claude.defaultAutoCleanupContexts', true),
        permissionMode: config.get('claude.permissionMode', 'default'),
        includePartialMessages: config.get('includePartialMessages', true),
        nodePath: config.get('claude.nodePath', ''),
        defaultModelId: config.get('claude.defaultModelId', 'claude-opus-4-6'),
        defaultThinkingLevel: config.get('claude.defaultThinkingLevel', 'ultra'),
        thinkTokens: config.get('claude.thinkTokens', 2048),
        ultraTokens: config.get('claude.ultraTokens', 8096),
        customModels: config.get('claude.customModels', []),
      },
      codex: {
        defaultBypassPermissions: config.get('codex.defaultBypassPermissions', false),
        defaultAutoCleanupContexts: config.get('codex.defaultAutoCleanupContexts', true),
        codexPath: config.get('codex.path', ''),
        webSearch: config.get('codex.webSearchEnabled', false),
        defaultModelId: config.get('codex.defaultModelId', 'gpt-5.2-codex'),
        customModels: config.get('codex.customModels', []),
        reasoningEffort: config.get('codex.reasoningEffort', 'medium'),
        reasoningSummary: config.get('codex.reasoningSummary', 'auto'),
        sandboxMode: config.get('codex.sandboxMode', 'workspace-write'),
      },
      gitGenerate: {
        enabled: config.get('gitGenerate.enabled', false),
        backend: config.get('gitGenerate.backend', 'claude'),
        modelId: config.get('gitGenerate.model', ''),
        claudeThinkingLevel: config.get('gitGenerate.claudeThinkingLevel', 'ultra'),
        codexReasoningEffort: config.get('gitGenerate.codexReasoningEffort', 'xhigh'),
        saveSession: config.get('gitGenerate.saveSession', false),
        systemPrompt: config.get('gitGenerate.systemPrompt', ''),
        userPrompt: config.get('gitGenerate.userPrompt', ''),
        tools: config.get('gitGenerate.tools', []),
      },
      mcp: {
        servers: mcpServers,
      },
    }

    await webview.postMessage({ type: 'settingsLoaded', payload: settings })
  }

  /**
   * Get default MCP servers configuration
   */
  private static getDefaultMcpServers(config: vscode.WorkspaceConfiguration): unknown[] {
    return [
      { 
        name: 'User Interaction', 
        enabled: true, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 3600,
        defaultAutoApprovedTools: ['AskUserQuestion'],
      },
      { 
        name: 'JetBrains LSP', 
        enabled: config.get('agent.enableJetBrainsMcp', true), 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 60,
        disabledTools: ['Glob', 'Grep'],
        defaultAutoApprovedTools: ['DirectoryTree', 'FileProblems', 'FileIndex', 'CodeSearch', 'FindUsages', 'Rename'],
      },
      { 
        name: 'JetBrains File', 
        enabled: config.get('agent.enableJetBrainsFileMcp', true), 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 60,
        hasDisableToolsToggle: true,
        defaultDisabledTools: ['Read', 'Write', 'Edit'],
        defaultCodexDisabledFeatures: ['apply_patch_freeform'],
        defaultAutoApprovedTools: ['ReadFile'],
        fileAllowExternal: true,
        fileExternalRules: '[]',
      },
      { 
        name: 'Context7', 
        enabled: false, 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 60,
        apiKey: '',
      },
      { 
        name: 'Terminal', 
        enabled: config.get('agent.enableTerminalMcp', true), 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 60,
        hasDisableToolsToggle: true,
        defaultDisabledTools: ['Bash'],
        defaultCodexDisabledFeatures: ['shell_tool'],
        defaultAutoApprovedTools: ['TerminalRead', 'TerminalList', 'TerminalKill', 'TerminalTypes', 'TerminalRename', 'TerminalInterrupt'],
        terminalMaxOutputLines: 500,
        terminalMaxOutputChars: 50000,
        terminalReadTimeout: 10,
        terminalDefaultShell: '',
        terminalAvailableShells: '',
        terminalDisableInteractive: false,
      },
      { 
        name: 'Git', 
        enabled: config.get('agent.enableGitMcp', true), 
        backends: 'All', 
        level: 'Global', 
        isBuiltIn: true, 
        configuration: 'Built-in',
        toolTimeoutSec: 60,
        defaultAutoApprovedTools: ['GetVcsChanges', 'GetCommitMessage', 'SetCommitMessage', 'GetVcsStatus', 'SelectFiles', 'DeselectFiles', 'SelectAllFiles', 'DeselectAllFiles'],
        gitCommitLanguage: 'en',
      },
    ]
  }

  /**
   * Save a setting to VS Code configuration
   * Handles both simple key-value pairs and complex nested objects
   */
  private static async saveSetting(key: string, value: unknown): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')
    
    // Handle top-level settings categories (claude, codex, gitGenerate, mcp)
    if (key === 'claude' && typeof value === 'object' && value !== null) {
      await SettingsPanel.saveClaudeSettings(config, value as Record<string, unknown>)
      return
    }
    
    if (key === 'codex' && typeof value === 'object' && value !== null) {
      await SettingsPanel.saveCodexSettings(config, value as Record<string, unknown>)
      return
    }
    
    if (key === 'gitGenerate' && typeof value === 'object' && value !== null) {
      await SettingsPanel.saveGitGenerateSettings(config, value as Record<string, unknown>)
      return
    }
    
    if (key === 'mcp' && typeof value === 'object' && value !== null) {
      await SettingsPanel.saveMcpSettings(config, value as Record<string, unknown>)
      return
    }
    
    // Map the settings store keys to VS Code config keys for simple values
    const keyMappings: Record<string, string> = {
      'claude.defaultBypassPermissions': 'defaultBypassPermissions',
      'claude.includePartialMessages': 'includePartialMessages',
      'codex.codexPath': 'codex.path',
      'codex.webSearch': 'codex.webSearchEnabled',
    }

    const configKey = keyMappings[key] || key
    await config.update(configKey, value, vscode.ConfigurationTarget.Global)
  }
  
  /**
   * Save Claude settings
   */
  private static async saveClaudeSettings(config: vscode.WorkspaceConfiguration, claude: Record<string, unknown>): Promise<void> {
    // Map frontend keys to VS Code config keys
    if ('defaultBypassPermissions' in claude) {
      await config.update('defaultBypassPermissions', claude.defaultBypassPermissions, vscode.ConfigurationTarget.Global)
    }
    if ('defaultAutoCleanupContexts' in claude) {
      await config.update('claude.defaultAutoCleanupContexts', claude.defaultAutoCleanupContexts, vscode.ConfigurationTarget.Global)
    }
    if ('permissionMode' in claude) {
      await config.update('claude.permissionMode', claude.permissionMode, vscode.ConfigurationTarget.Global)
    }
    if ('includePartialMessages' in claude) {
      await config.update('includePartialMessages', claude.includePartialMessages, vscode.ConfigurationTarget.Global)
    }
    if ('nodePath' in claude) {
      await config.update('claude.nodePath', claude.nodePath, vscode.ConfigurationTarget.Global)
    }
    if ('defaultModelId' in claude) {
      await config.update('claude.defaultModelId', claude.defaultModelId, vscode.ConfigurationTarget.Global)
    }
    if ('defaultThinkingLevel' in claude) {
      await config.update('claude.defaultThinkingLevel', claude.defaultThinkingLevel, vscode.ConfigurationTarget.Global)
    }
    if ('thinkTokens' in claude) {
      await config.update('claude.thinkTokens', claude.thinkTokens, vscode.ConfigurationTarget.Global)
    }
    if ('ultraTokens' in claude) {
      await config.update('claude.ultraTokens', claude.ultraTokens, vscode.ConfigurationTarget.Global)
    }
    if ('customModels' in claude) {
      await config.update('claude.customModels', claude.customModels, vscode.ConfigurationTarget.Global)
    }
  }
  
  /**
   * Save Codex settings
   */
  private static async saveCodexSettings(config: vscode.WorkspaceConfiguration, codex: Record<string, unknown>): Promise<void> {
    if ('defaultBypassPermissions' in codex) {
      await config.update('codex.defaultBypassPermissions', codex.defaultBypassPermissions, vscode.ConfigurationTarget.Global)
    }
    if ('defaultAutoCleanupContexts' in codex) {
      await config.update('codex.defaultAutoCleanupContexts', codex.defaultAutoCleanupContexts, vscode.ConfigurationTarget.Global)
    }
    if ('codexPath' in codex) {
      await config.update('codex.path', codex.codexPath, vscode.ConfigurationTarget.Global)
    }
    if ('webSearch' in codex) {
      await config.update('codex.webSearchEnabled', codex.webSearch, vscode.ConfigurationTarget.Global)
    }
    if ('defaultModelId' in codex) {
      await config.update('codex.defaultModelId', codex.defaultModelId, vscode.ConfigurationTarget.Global)
    }
    if ('customModels' in codex) {
      await config.update('codex.customModels', codex.customModels, vscode.ConfigurationTarget.Global)
    }
    if ('reasoningEffort' in codex) {
      await config.update('codex.reasoningEffort', codex.reasoningEffort, vscode.ConfigurationTarget.Global)
    }
    if ('reasoningSummary' in codex) {
      await config.update('codex.reasoningSummary', codex.reasoningSummary, vscode.ConfigurationTarget.Global)
    }
    if ('sandboxMode' in codex) {
      await config.update('codex.sandboxMode', codex.sandboxMode, vscode.ConfigurationTarget.Global)
    }
  }
  
  /**
   * Save Git Generate settings
   */
  private static async saveGitGenerateSettings(config: vscode.WorkspaceConfiguration, gitGenerate: Record<string, unknown>): Promise<void> {
    if ('enabled' in gitGenerate) {
      await config.update('gitGenerate.enabled', gitGenerate.enabled, vscode.ConfigurationTarget.Global)
    }
    if ('backend' in gitGenerate) {
      await config.update('gitGenerate.backend', gitGenerate.backend, vscode.ConfigurationTarget.Global)
    }
    if ('modelId' in gitGenerate) {
      await config.update('gitGenerate.model', gitGenerate.modelId, vscode.ConfigurationTarget.Global)
    }
    if ('claudeThinkingLevel' in gitGenerate) {
      await config.update('gitGenerate.claudeThinkingLevel', gitGenerate.claudeThinkingLevel, vscode.ConfigurationTarget.Global)
    }
    if ('codexReasoningEffort' in gitGenerate) {
      await config.update('gitGenerate.codexReasoningEffort', gitGenerate.codexReasoningEffort, vscode.ConfigurationTarget.Global)
    }
    if ('saveSession' in gitGenerate) {
      await config.update('gitGenerate.saveSession', gitGenerate.saveSession, vscode.ConfigurationTarget.Global)
    }
    if ('systemPrompt' in gitGenerate) {
      await config.update('gitGenerate.systemPrompt', gitGenerate.systemPrompt, vscode.ConfigurationTarget.Global)
    }
    if ('userPrompt' in gitGenerate) {
      await config.update('gitGenerate.userPrompt', gitGenerate.userPrompt, vscode.ConfigurationTarget.Global)
    }
    if ('tools' in gitGenerate) {
      await config.update('gitGenerate.tools', gitGenerate.tools, vscode.ConfigurationTarget.Global)
    }
  }
  
  /**
   * Save MCP settings
   */
  private static async saveMcpSettings(config: vscode.WorkspaceConfiguration, mcp: Record<string, unknown>): Promise<void> {
    if ('servers' in mcp && Array.isArray(mcp.servers)) {
      // Save entire MCP servers array
      await config.update('mcp.servers', mcp.servers, vscode.ConfigurationTarget.Global)
      
      // Also update legacy individual enable flags for built-in servers
      for (const server of mcp.servers as Array<{ name: string; enabled: boolean; isBuiltIn?: boolean }>) {
        if (server.isBuiltIn) {
          switch (server.name) {
            case 'JetBrains LSP':
              await config.update('agent.enableJetBrainsMcp', server.enabled, vscode.ConfigurationTarget.Global)
              break
            case 'JetBrains File':
              await config.update('agent.enableJetBrainsFileMcp', server.enabled, vscode.ConfigurationTarget.Global)
              break
            case 'Terminal':
              await config.update('agent.enableTerminalMcp', server.enabled, vscode.ConfigurationTarget.Global)
              break
            case 'Git':
              await config.update('agent.enableGitMcp', server.enabled, vscode.ConfigurationTarget.Global)
              break
          }
        }
      }
    }
  }

  /**
   * Handle file browse request
   */
  private static async handleBrowseFile(webview: vscode.Webview, settingKey: string): Promise<void> {
    const result = await vscode.window.showOpenDialog({
      canSelectMany: false,
      canSelectFiles: true,
      canSelectFolders: false,
      openLabel: 'Select Executable',
      filters: process.platform === 'win32' 
        ? { 'Executables': ['exe', 'cmd', 'bat'], 'All Files': ['*'] }
        : undefined,
    })

    if (result && result.length > 0) {
      const filePath = result[0].fsPath
      await webview.postMessage({ 
        type: 'fileSelected', 
        payload: { settingKey, path: filePath } 
      })
    }
  }

  /**
   * Detect Node.js installation
   */
  private static async detectNode(webview: vscode.Webview): Promise<void> {
    let info: DetectedInfo | null = null

    try {
      // Try to find node in PATH
      const nodePath = process.platform === 'win32' 
        ? execSync('where node', { encoding: 'utf8' }).trim().split('\n')[0]
        : execSync('which node', { encoding: 'utf8' }).trim()

      if (nodePath) {
        // Get version
        const version = execSync(`"${nodePath}" --version`, { encoding: 'utf8' }).trim()
        info = { path: nodePath, version }
      }
    } catch {
      // Node not found in PATH
      info = null
    }

    await webview.postMessage({ type: 'nodeDetected', payload: info })
  }

  /**
   * Detect Codex installation
   */
  private static async detectCodex(webview: vscode.Webview): Promise<void> {
    let info: DetectedInfo | null = null

    try {
      // Check for bundled Codex first
      const bundledPath = path.join(
        SettingsPanel.extensionUri.fsPath,
        'node_modules',
        '@anthropic',
        'codex-cli',
        'bin',
        'codex'
      )

      if (fs.existsSync(bundledPath)) {
        try {
          const version = execSync(`"${bundledPath}" --version`, { encoding: 'utf8' }).trim()
          info = { path: 'bundled', version }
        } catch {
          info = { path: 'bundled' }
        }
      } else {
        // Try to find codex in PATH
        const codexPath = process.platform === 'win32'
          ? execSync('where codex', { encoding: 'utf8' }).trim().split('\n')[0]
          : execSync('which codex', { encoding: 'utf8' }).trim()

        if (codexPath) {
          const version = execSync(`"${codexPath}" --version`, { encoding: 'utf8' }).trim()
          info = { path: codexPath, version }
        }
      }
    } catch {
      // Codex not found
      info = null
    }

    await webview.postMessage({ type: 'codexDetected', payload: info })
  }
}

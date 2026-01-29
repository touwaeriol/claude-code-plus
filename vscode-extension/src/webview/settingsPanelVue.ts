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

    const settings = {
      claude: {
        defaultBypassPermissions: config.get('defaultBypassPermissions', false),
        defaultAutoCleanupContexts: config.get('claude.defaultAutoCleanupContexts', true),
        permissionMode: config.get('claude.permissionMode', 'default'),
        includePartialMessages: config.get('includePartialMessages', true),
        nodePath: config.get('claude.nodePath', ''),
        defaultModelId: config.get('claude.defaultModelId', 'claude-opus-4-5-20251101'),
        defaultThinkingLevel: config.get('claude.defaultThinkingLevel', 'HIGH'),
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
        sandboxMode: config.get('codex.sandboxMode', 'auto'),
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
      },
      mcp: {
        servers: [
          { name: 'JetBrains LSP', enabled: config.get('agent.enableJetBrainsMcp', true), backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
          { name: 'JetBrains File', enabled: config.get('agent.enableJetBrainsFileMcp', true), backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
          { name: 'Terminal', enabled: config.get('agent.enableTerminalMcp', true), backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
          { name: 'Git', enabled: config.get('agent.enableGitMcp', true), backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
          { name: 'User Interaction', enabled: true, backends: 'All', level: 'Global', isBuiltIn: true, configuration: 'Built-in' },
        ],
      },
    }

    await webview.postMessage({ type: 'settingsLoaded', payload: settings })
  }

  /**
   * Save a setting to VS Code configuration
   */
  private static async saveSetting(key: string, value: unknown): Promise<void> {
    const config = vscode.workspace.getConfiguration('claudeCodePlus')
    
    // Map the settings store keys to VS Code config keys
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

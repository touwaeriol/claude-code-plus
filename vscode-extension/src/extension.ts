import * as vscode from 'vscode'

import { DiffContentProvider, DIFF_SCHEME } from './ide/diffContentProvider'
import { createDevLogger } from './logging/devLogger'
import { Logger, getLogger } from './logging/logger'
import { HttpApiServer } from './server/HttpApiServer'
import { initializeMcpServers, disposeMcpServers } from './ide/mcp'
import { ChatPanel } from './webview/chatPanel'
import { ChatViewProvider } from './webview/chatViewProvider'
import { broadcastChatMessage } from './webview/chatWebviewNotifier'

let server: HttpApiServer | undefined

export async function activate(context: vscode.ExtensionContext) {
  // Initialize unified logger system
  Logger.initialize(context, {
    channelName: 'Claude Code Plus',
    minLevel: context.extensionMode === vscode.ExtensionMode.Development ? 'debug' : 'info',
    enableFileLog: true,
  })
  
  const logger = getLogger('Extension')
  const output = Logger.getOutputChannel()!
  context.subscriptions.push(output)

  // Keep devLogger for backward compatibility
  const devLogger = createDevLogger(context)
  logger.info(`Activating: mode=${vscode.ExtensionMode[context.extensionMode]} logUri=${context.logUri.fsPath}`)

  process.on('uncaughtException', (err) => {
    logger.error('uncaughtException', err instanceof Error ? err : new Error(String(err)))
  })
  process.on('unhandledRejection', (reason) => {
    const err = reason instanceof Error ? reason : new Error(typeof reason === 'string' ? reason : JSON.stringify(reason))
    logger.error('unhandledRejection', err)
  })

  const statusItem = vscode.window.createStatusBarItem(vscode.StatusBarAlignment.Right, 100)
  statusItem.text = 'Claude Code Plus'
  statusItem.command = 'claudeCodePlus.openChat'
  statusItem.tooltip = 'Open Claude Code Plus Chat'
  statusItem.show()
  context.subscriptions.push(statusItem)

  const diffProvider = new DiffContentProvider()
  context.subscriptions.push(
    vscode.workspace.registerTextDocumentContentProvider(DIFF_SCHEME, diffProvider)
  )

  server = new HttpApiServer(context, { diffProvider }, devLogger)
  try {
    logger.info('Starting local server...')
    await server.start()
    logger.info(`Local server started: ${server.getBaseUrl()}`)
    context.subscriptions.push(server)
    
    // Initialize MCP servers
    logger.info('Initializing MCP servers...')
    await initializeMcpServers()
    logger.info('MCP servers initialized')
  } catch (err) {
    const error = err instanceof Error ? err : new Error(String(err))
    logger.error('Local server failed to start', error)
    void vscode.window.showErrorMessage(`Claude Code Plus: failed to start local server: ${error.message}`)
    server.dispose()
    server = undefined
  }

  const chatViewProvider = new ChatViewProvider(context, () => {
    if (!server) return undefined
    try {
      return { serverUrl: server.getBaseUrl(), serverToken: server.getToken() }
    } catch {
      return undefined
    }
  }, output, devLogger)
  context.subscriptions.push(
    vscode.window.registerWebviewViewProvider(ChatViewProvider.viewType, chatViewProvider, {
      webviewOptions: { retainContextWhenHidden: true },
    })
  )
  logger.info('Webview view provider registered')
  context.subscriptions.push(chatViewProvider)

  context.subscriptions.push(
    vscode.commands.registerCommand('claudeCodePlus.openChat', async () => {
      try {
        await vscode.commands.executeCommand('workbench.view.extension.claudeCodePlus')
        try {
          await vscode.commands.executeCommand('claudeCodePlus.chatView.focus')
        } catch {
          // Some VS Code builds might not expose a focus command for webview views.
        }
        return
      } catch {
        // Fall back to opening a standalone panel.
      }

      if (!server) {
        void vscode.window.showWarningMessage('Claude Code Plus: local server not started')
        return
      }
      ChatPanel.show(context, { serverUrl: server.getBaseUrl(), serverToken: server.getToken() })
    })
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('claudeCodePlus.openChatPanel', async () => {
      if (!server) {
        void vscode.window.showWarningMessage('Claude Code Plus: local server not started')
        return
      }
      ChatPanel.show(context, { serverUrl: server.getBaseUrl(), serverToken: server.getToken() })
    })
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('claudeCodePlus.openSettings', async () => {
      // Reuse the same Vue UI for settings (same as IDEA): open the chat view and ask it to show the settings panel.
      await vscode.commands.executeCommand('claudeCodePlus.openChat')

      // Let the webview bootstrap/register before we post a command.
      setTimeout(() => {
        void broadcastChatMessage({ type: 'ccp-open-settings' })
      }, 50)
    })
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('claudeCodePlus.setCodexApiKey', async () => {
      const input = await vscode.window.showInputBox({
        title: 'Claude Code Plus',
        prompt: '请输入 Codex API Key（留空表示清除）',
        password: true,
        ignoreFocusOut: true,
      })

      if (input === undefined) return
      const value = input.trim()

      if (!value) {
        await context.secrets.delete('claudeCodePlus.codex.apiKey')
        void vscode.window.showInformationMessage('Codex API Key 已清除')
      } else {
        await context.secrets.store('claudeCodePlus.codex.apiKey', value)
        void vscode.window.showInformationMessage('Codex API Key 已保存')
      }

      ChatPanel.notifySettingsChanged()
    })
  )

  // Dev convenience: open the chat view on startup so webview issues are visible immediately.
  if (context.extensionMode === vscode.ExtensionMode.Development) {
    setTimeout(() => {
      logger.debug('Dev auto-open chat')
      void vscode.commands.executeCommand('claudeCodePlus.openChat')
    }, 300)
  }

  logger.info('Activation complete')
}

export function deactivate() {
  disposeMcpServers()
  server?.dispose()
  server = undefined
}

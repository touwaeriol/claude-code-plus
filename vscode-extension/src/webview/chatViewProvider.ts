import * as vscode from 'vscode'

import { getIdeTheme } from '../server/apiHandlers'
import type { DevLogger } from '../logging/devLogger'
import { renderChatHtml } from './chatPanel'
import { registerChatWebview } from './chatWebviewNotifier'

export class ChatViewProvider implements vscode.WebviewViewProvider, vscode.Disposable {
  static readonly viewType = 'claudeCodePlus.chatView'

  private view: vscode.WebviewView | undefined
  private viewDisposables: vscode.Disposable[] = []
  private webviewRegistration: vscode.Disposable | undefined

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly getServerParams: () => { serverUrl: string; serverToken?: string } | undefined,
    private readonly output: vscode.OutputChannel,
    private readonly devLogger?: DevLogger
  ) {}

  resolveWebviewView(webviewView: vscode.WebviewView): void {
    this.disposeViewResources()

    this.view = webviewView
    const { webview } = webviewView

    this.output.appendLine('[chatView] resolveWebviewView()')
    this.devLogger?.write('[chatView] resolveWebviewView()')

    webview.options = {
      enableScripts: true,
      localResourceRoots: [
        vscode.Uri.joinPath(this.context.extensionUri, 'media'),
        vscode.Uri.joinPath(this.context.extensionUri, 'media', 'dist'),
      ],
    }

    const params = this.getServerParams()
    if (params) {
      this.output.appendLine(`[chatView] serverUrl=${params.serverUrl}`)
      this.devLogger?.write(`[chatView] serverUrl=${params.serverUrl} cspSource=${webview.cspSource}`)
      const html = renderChatHtml(webview, this.context.extensionUri, params.serverUrl, params.serverToken)
      this.output.appendLine(`[chatView] htmlLength=${html.length}`)
      this.devLogger?.write(`[chatView] htmlLength=${html.length}`)
      webview.html = html
    } else {
      webview.html = fallbackHtml('Local server not started')
    }

    this.webviewRegistration = registerChatWebview(webview)

    const messageDisposer = webview.onDidReceiveMessage((raw: unknown) => {
      const msg = raw as any
      if (!msg || typeof msg !== 'object') return
      if (msg.type !== 'ccp-webview-log') return

      const level = typeof msg.level === 'string' ? msg.level : 'info'
      const text = typeof msg.message === 'string' ? msg.message : ''
      const extra = msg.extra ? safeJson(msg.extra) : ''
      const line = `[webview:${level}] ${text}${extra ? ` ${extra}` : ''}`

      this.output.appendLine(line)
      this.devLogger?.write(line)
      if (level === 'error') {
        console.error(line)
      } else {
        console.log(line)
      }
      if (level === 'error') {
        this.output.show(true)
      }
    })

    const themeDisposer = vscode.window.onDidChangeActiveColorTheme(() => {
      void webview.postMessage({ type: 'ccp-theme', theme: getIdeTheme() })
    })

    const configDisposer = vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('editor.fontFamily') || e.affectsConfiguration('editor.fontSize')) {
        void webview.postMessage({ type: 'ccp-theme', theme: getIdeTheme() })
      }
      if (e.affectsConfiguration('claudeCodePlus')) {
        void webview.postMessage({ type: 'ccp-settings-changed' })
      }
    })

    const disposeDisposer = webviewView.onDidDispose(() => {
      this.disposeViewResources()
    })

    this.viewDisposables = [messageDisposer, themeDisposer, configDisposer, disposeDisposer]

    // Ensure UI gets at least one theme payload (some pages load before listeners are ready).
    void webview.postMessage({ type: 'ccp-theme', theme: getIdeTheme() })
  }

  dispose() {
    this.disposeViewResources()
  }

  private disposeViewResources() {
    for (const d of this.viewDisposables) d.dispose()
    this.viewDisposables = []
    this.webviewRegistration?.dispose()
    this.webviewRegistration = undefined
    this.view = undefined
  }
}

function fallbackHtml(message: string): string {
  const escaped = escapeHtml(message)
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Claude Code Plus</title>
</head>
<body style="font-family: -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif; padding: 16px;">
  <h2>Claude Code Plus (VS Code)</h2>
  <p style="opacity: 0.8;">${escaped}</p>
</body>
</html>`
}

function escapeHtml(input: string): string {
  return input.replace(/[&<>"']/g, (ch) => {
    switch (ch) {
      case '&':
        return '&amp;'
      case '<':
        return '&lt;'
      case '>':
        return '&gt;'
      case '"':
        return '&quot;'
      case "'":
        return '&#39;'
      default:
        return ch
    }
  })
}

function safeJson(value: unknown): string {
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}

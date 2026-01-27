import * as crypto from 'crypto'
import * as fs from 'fs'
import * as vscode from 'vscode'

import { getIdeTheme } from '../server/apiHandlers'
import { notifyChatSettingsChanged, registerChatWebview } from './chatWebviewNotifier'

export class ChatPanel {
  private static currentPanel: vscode.WebviewPanel | undefined
  private static currentPanelRegistration: vscode.Disposable | undefined

  static notifySettingsChanged() {
    notifyChatSettingsChanged()
  }

  static show(context: vscode.ExtensionContext, params: { serverUrl: string; serverToken?: string }) {
    const existing = ChatPanel.currentPanel
    if (existing) {
      existing.reveal(existing.viewColumn)
      existing.webview.html = renderChatHtml(existing.webview, context.extensionUri, params.serverUrl, params.serverToken)
      if (!ChatPanel.currentPanelRegistration) {
        ChatPanel.currentPanelRegistration = registerChatWebview(existing.webview)
      }
      return
    }

    const panel = vscode.window.createWebviewPanel(
      'claudeCodePlus.chat',
      'Claude Code Plus',
      vscode.ViewColumn.Beside,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [
          vscode.Uri.joinPath(context.extensionUri, 'media'),
          vscode.Uri.joinPath(context.extensionUri, 'media', 'dist'),
        ],
      }
    )

    panel.webview.html = renderChatHtml(panel.webview, context.extensionUri, params.serverUrl, params.serverToken)
    ChatPanel.currentPanelRegistration?.dispose()
    ChatPanel.currentPanelRegistration = registerChatWebview(panel.webview)

    const themeDisposer = vscode.window.onDidChangeActiveColorTheme(() => {
      void panel.webview.postMessage({ type: 'ccp-theme', theme: getIdeTheme() })
    })
    const configDisposer = vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('editor.fontFamily') || e.affectsConfiguration('editor.fontSize')) {
        void panel.webview.postMessage({ type: 'ccp-theme', theme: getIdeTheme() })
      }
      if (e.affectsConfiguration('claudeCodePlus')) {
        void panel.webview.postMessage({ type: 'ccp-settings-changed' })
      }
    })
    panel.onDidDispose(() => {
      ChatPanel.currentPanel = undefined
      ChatPanel.currentPanelRegistration?.dispose()
      ChatPanel.currentPanelRegistration = undefined
      themeDisposer.dispose()
      configDisposer.dispose()
    })

    ChatPanel.currentPanel = panel
  }
}

export function renderChatHtml(
  webview: vscode.Webview,
  extensionUri: vscode.Uri,
  serverUrl: string,
  serverToken?: string
): string {
  try {
    const distDir = vscode.Uri.joinPath(extensionUri, 'media', 'dist')
    const indexPath = vscode.Uri.joinPath(distDir, 'index.html').fsPath

    if (!fs.existsSync(indexPath)) {
      return fallbackHtml(serverUrl, serverToken)
    }

    let html = fs.readFileSync(indexPath, 'utf8')

    const rawBaseHref = webview.asWebviewUri(distDir).toString()
    const baseHref = rawBaseHref.endsWith('/') ? rawBaseHref : `${rawBaseHref}/`
    const initialTheme = getIdeTheme()

    // favicon 使用绝对路径时会失效，改成相对 + base
    html = html.replace('href="/favicon.svg"', 'href="favicon.svg"')

    const nonce = crypto.randomBytes(16).toString('base64')

    // 为所有 <script> 标签补 nonce（包含 index.html 自带的内联脚本和 module 脚本）。
    // 这样可以避免不同 VS Code 版本对 unsafe-inline 的限制差异导致“纯空白”。
    html = html.replace(/<script\b(?![^>]*\bnonce=)/gi, `<script nonce="${nonce}"`)

    // 注入 base + serverUrl（需在前端初始化前可读）
    // NOTE: vue-vendor (lodash) uses `new Function(...)` to resolve the global object,
    // which requires `unsafe-eval` in webviews.
    const csp = [
      `default-src 'none';`,
      `base-uri ${webview.cspSource};`,
      `img-src ${webview.cspSource} https: data: blob:;`,
      `style-src ${webview.cspSource} 'unsafe-inline';`,
      `font-src ${webview.cspSource} https: data: blob:;`,
      `script-src ${webview.cspSource} 'nonce-${nonce}' 'unsafe-eval' blob:;`,
      `worker-src ${webview.cspSource} blob:;`,
      `connect-src ${webview.cspSource} ${serverUrl} http://127.0.0.1:* http://localhost:* ws://127.0.0.1:* ws://localhost:* wss://127.0.0.1:* wss://localhost:* https:;`,
    ].join(' ')

    // Inject CSP + base + bridge globals before any app scripts run.
    const injection = [
      `<meta http-equiv="Content-Security-Policy" content="${csp}">`,
      `<base href="${baseHref}">`,
      `<script nonce="${nonce}">`,
      `window.__serverUrl = ${JSON.stringify(serverUrl)};`,
      serverToken ? `window.__serverToken = ${JSON.stringify(serverToken)};` : '',
      `window.__initialTheme = ${JSON.stringify(initialTheme)};`,
      'window.__IDE_MODE__ = true;',
      ';(() => {',
      "  const vscodeApi = typeof acquireVsCodeApi === 'function' ? acquireVsCodeApi() : null;",
      '  const postLog = (level, message, extra) => {',
      '    try {',
      "      vscodeApi && vscodeApi.postMessage({ type: 'ccp-webview-log', level, message, extra });",
      '    } catch {}',
      '  };',
      '  const safeArg = (v) => {',
      '    try {',
      "      if (v instanceof Error) return v.stack || v.message || String(v);",
      "      if (typeof v === 'string') return v;",
      "      return JSON.stringify(v);",
      '    } catch {',
      '      try { return String(v); } catch { return "[unprintable]"; }',
      '    }',
      '  };',
      '  const hookConsole = () => {',
      '    try {',
      '      const c = console;',
      "      const wrap = (level, fn) => (...args) => {",
      '        try { fn.apply(c, args); } catch {}',
      "        try { postLog(level, args.map(safeArg).join(' ')); } catch {}",
      '      };',
      "      if (c && typeof c.log === 'function') c.log = wrap('info', c.log);",
      "      if (c && typeof c.info === 'function') c.info = wrap('info', c.info);",
      "      if (c && typeof c.debug === 'function') c.debug = wrap('info', c.debug);",
      "      if (c && typeof c.warn === 'function') c.warn = wrap('warn', c.warn);",
      "      if (c && typeof c.error === 'function') c.error = wrap('error', c.error);",
      '    } catch {}',
      '  };',
      '  hookConsole();',
      "  const showOverlay = (title, message, stack) => {",
      '    const render = () => {',
      "      const id = 'ccp-webview-error-overlay';",
      '      let el = document.getElementById(id);',
      '      if (!el) {',
      "        el = document.createElement('div');",
      '        el.id = id;',
      "        el.style.cssText = 'position:fixed;z-index:999999;top:0;left:0;right:0;bottom:0;padding:12px;overflow:auto;background:#111;color:#e5e7eb;font:12px/1.4 ui-monospace,SFMono-Regular,Menlo,Monaco,Consolas,\"Liberation Mono\",\"Courier New\",monospace;';",
      '        document.body && document.body.appendChild(el);',
      '      }',
      '      const parts = [title, message, stack].filter(Boolean);',
      "      el.textContent = parts.join('\\n\\n');",
      '    };',
      '    try {',
      '      if (document.body) render();',
      "      else window.addEventListener('DOMContentLoaded', render, { once: true });",
      '    } catch {}',
      '  };',
      "  postLog('info', 'webview bootstrap', { origin: window.location.origin });",
      "  window.addEventListener('error', (ev) => {",
      "    const msg = (ev && ev.message) ? ev.message : 'Unknown error';",
      '    const stack = ev && ev.error && ev.error.stack ? ev.error.stack : undefined;',
      "    postLog('error', msg, stack ? { stack } : undefined);",
      "    showOverlay('Uncaught error', msg, stack);",
      '  });',
      "  window.addEventListener('unhandledrejection', (ev) => {",
      '    const reason = ev && ev.reason;',
      "    const msg = reason instanceof Error ? reason.message : String(reason);",
      '    const stack = reason instanceof Error ? reason.stack : undefined;',
      "    postLog('error', 'Unhandled promise rejection: ' + msg, stack ? { stack } : undefined);",
      "    showOverlay('Unhandled promise rejection', msg, stack);",
      '  });',
      "  window.addEventListener('DOMContentLoaded', () => {",
      "    const root = document.getElementById('app');",
      '    if (root && !root.firstChild) {',
      "      const div = document.createElement('div');",
      "      div.style.cssText = 'padding:12px;opacity:0.75;font-family:system-ui,-apple-system,Segoe UI,sans-serif;';",
      "      div.textContent = 'Loading...';",
      '      root.appendChild(div);',
      '    }',
      '  });',
      '})();',
      '</script>',
    ].join('\n')

    // Keep <meta charset> as early as possible to avoid encoding issues.
    if (/<meta\s+charset=/i.test(html)) {
      html = html.replace(/<meta\s+charset=[^>]*>/i, (m) => `${m}\n${injection}\n`)
    } else {
      html = html.replace('<head>', `<head>\n${injection}\n`)
    }

    return html
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error)
    const stack = error instanceof Error ? error.stack : undefined
    return renderErrorHtml(message, stack)
  }
}

function fallbackHtml(serverUrl: string, serverToken?: string): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Claude Code Plus</title>
  <script>
    window.__serverUrl = ${JSON.stringify(serverUrl)};
    ${serverToken ? `window.__serverToken = ${JSON.stringify(serverToken)};` : ''}
    window.__IDE_MODE__ = true;
  </script>
</head>
<body style="font-family: -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif; padding: 16px;">
  <h2>Claude Code Plus（VS Code）</h2>
  <p>未找到前端构建产物：<code>vscode-extension/media/dist/index.html</code></p>
  <p>serverUrl：<code>${escapeHtml(serverUrl)}</code></p>
  <p>下一步：将 <code>frontend</code> build 输出复制到 <code>vscode-extension/media/dist</code> 后再打开。</p>
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

function renderErrorHtml(message: string, stack?: string): string {
  const escapedMessage = escapeHtml(message)
  const escapedStack = stack ? escapeHtml(stack) : ''
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Claude Code Plus</title>
</head>
<body style="font-family: -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif; padding: 16px;">
  <h2>Claude Code Plus（VS Code）</h2>
  <p style="color:#b91c1c;">Webview 渲染失败：${escapedMessage}</p>
  ${escapedStack ? `<pre style="white-space:pre-wrap; opacity:0.8;">${escapedStack}</pre>` : ''}
</body>
</html>`
}

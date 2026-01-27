import * as vscode from 'vscode'

import { ChatPanel } from './chatPanel'

type SettingsTarget = 'global' | 'workspace' | 'workspaceFolder'

type SettingsMessageFromWebview =
  | {
      type: 'ccp-settings-save'
      target: SettingsTarget
      workspaceFolderUri?: string
      values: Record<string, unknown>
      codexApiKey?: string
      clearCodexApiKey?: boolean
    }
  | { type: 'ccp-settings-request' }

type SettingsMessageToWebview =
  | { type: 'ccp-settings-data'; data: SettingsWebviewData }
  | { type: 'ccp-settings-saved' }
  | { type: 'ccp-settings-error'; error: string }

interface SettingsWebviewData {
  settings: PersistedSettingsSnapshot
  workspaceFolders: Array<{ name: string; uri: string }>
  defaultTarget: SettingsTarget
  defaultWorkspaceFolderUri?: string
}

interface PersistedSettingsSnapshot {
  // General
  defaultBackendType: 'claude' | 'codex'
  defaultBypassPermissions: boolean
  includePartialMessages: boolean

  // Claude
  claudeDefaultModelId: string
  claudeDefaultThinkingLevel: string
  claudeDefaultThinkingTokens: number
  claudeDefaultAutoCleanupContexts: boolean

  // Codex
  codexDefaultModelId: string
  codexReasoningEffort: string
  codexReasoningSummary: string
  codexSandboxMode: string
  codexDefaultAutoCleanupContexts: boolean
  hasCodexApiKey: boolean
}

export class SettingsPanel {
  private static currentPanel: vscode.WebviewPanel | undefined

  static async show(context: vscode.ExtensionContext) {
    const existing = SettingsPanel.currentPanel
    if (existing) {
      existing.reveal(existing.viewColumn)
      await SettingsPanel.refresh(context, existing)
      return
    }

    const panel = vscode.window.createWebviewPanel(
      'claudeCodePlus.settings',
      'Claude Code Plus: Settings',
      vscode.ViewColumn.Active,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [context.extensionUri],
      }
    )

    panel.onDidDispose(() => {
      SettingsPanel.currentPanel = undefined
    })

    panel.webview.onDidReceiveMessage(async (raw: unknown) => {
      const message = raw as SettingsMessageFromWebview | undefined
      if (!message || typeof message !== 'object') return

      try {
        if (message.type === 'ccp-settings-request') {
          await SettingsPanel.postData(context, panel)
          return
        }

        if (message.type !== 'ccp-settings-save') return

        await saveSettingsFromWebview(context, message)
        await panel.webview.postMessage({ type: 'ccp-settings-saved' } satisfies SettingsMessageToWebview)
        ChatPanel.notifySettingsChanged()
      } catch (err) {
        const msg = err instanceof Error ? err.message : String(err)
        await panel.webview.postMessage({ type: 'ccp-settings-error', error: msg } satisfies SettingsMessageToWebview)
      }
    })

    SettingsPanel.currentPanel = panel
    await SettingsPanel.refresh(context, panel)
  }

  private static async refresh(context: vscode.ExtensionContext, panel: vscode.WebviewPanel) {
    const data = await buildWebviewData(context)
    panel.webview.html = renderHtml(panel.webview, data)
  }

  private static async postData(context: vscode.ExtensionContext, panel: vscode.WebviewPanel) {
    const data = await buildWebviewData(context)
    await panel.webview.postMessage({ type: 'ccp-settings-data', data } satisfies SettingsMessageToWebview)
  }
}

async function buildWebviewData(context: vscode.ExtensionContext): Promise<SettingsWebviewData> {
  const folders = vscode.workspace.workspaceFolders ?? []
  const workspaceFolders = folders.map((f) => ({ name: f.name, uri: f.uri.toString() }))

  const defaultWorkspaceFolderUri = folders[0]?.uri.toString()
  const settings = await getPersistedSettingsSnapshot(context)

  return {
    settings,
    workspaceFolders,
    defaultTarget: 'global',
    defaultWorkspaceFolderUri,
  }
}

function renderHtml(webview: vscode.Webview, data: SettingsWebviewData): string {
  const nonce = createNonce()
  const initialData = JSON.stringify(data)

  const csp = [
    `default-src 'none';`,
    `img-src ${webview.cspSource} https: data:;`,
    `style-src ${webview.cspSource} 'unsafe-inline';`,
    `script-src 'nonce-${nonce}';`,
  ].join(' ')

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <meta http-equiv="Content-Security-Policy" content="${csp}">
  <title>Claude Code Plus Settings</title>
  <style>
    :root {
      color-scheme: light dark;
      --bg: var(--vscode-editor-background, #1e1e1e);
      --fg: var(--vscode-editor-foreground, #d4d4d4);
      --muted: color-mix(in srgb, var(--fg) 70%, transparent);
      --border: color-mix(in srgb, var(--fg) 15%, transparent);
      --card: color-mix(in srgb, var(--bg) 85%, #ffffff 15%);
      --accent: var(--vscode-button-background, #0e639c);
      --accent-fg: var(--vscode-button-foreground, #ffffff);
      --danger: var(--vscode-errorForeground, #f48771);
    }
    body {
      margin: 0;
      padding: 0;
      font-family: var(--vscode-font-family, system-ui);
      font-size: 13px;
      color: var(--fg);
      background: var(--bg);
    }
    header {
      padding: 16px 18px;
      border-bottom: 1px solid var(--border);
      display: flex;
      gap: 12px;
      align-items: center;
      justify-content: space-between;
    }
    header h1 {
      margin: 0;
      font-size: 14px;
      font-weight: 600;
    }
    .container {
      padding: 18px;
      max-width: 880px;
    }
    .row {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      align-items: center;
      margin-bottom: 12px;
    }
    .card {
      border: 1px solid var(--border);
      border-radius: 10px;
      padding: 14px;
      background: var(--card);
      margin-bottom: 12px;
    }
    .card h2 {
      margin: 0 0 10px 0;
      font-size: 13px;
      font-weight: 600;
    }
    label {
      display: block;
      margin: 10px 0 6px 0;
      color: var(--muted);
    }
    input[type="text"], input[type="number"], input[type="password"], select {
      width: 100%;
      box-sizing: border-box;
      padding: 8px 10px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: transparent;
      color: var(--fg);
      outline: none;
    }
    input[type="checkbox"] {
      transform: translateY(1px);
    }
    .inline {
      display: flex;
      gap: 10px;
      align-items: center;
    }
    .inline label {
      margin: 0;
      color: var(--fg);
      display: inline-flex;
      gap: 8px;
      align-items: center;
    }
    .muted {
      color: var(--muted);
      font-size: 12px;
      margin-top: 6px;
    }
    .actions {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
      align-items: center;
      margin-top: 14px;
    }
    button {
      appearance: none;
      border: 1px solid var(--border);
      background: transparent;
      color: var(--fg);
      padding: 8px 12px;
      border-radius: 8px;
      cursor: pointer;
      font-size: 13px;
    }
    button.primary {
      background: var(--accent);
      color: var(--accent-fg);
      border-color: color-mix(in srgb, var(--accent) 60%, #000 40%);
    }
    button.danger {
      border-color: color-mix(in srgb, var(--danger) 40%, var(--border));
      color: var(--danger);
    }
    .toast {
      position: fixed;
      right: 12px;
      bottom: 12px;
      padding: 10px 12px;
      border-radius: 10px;
      border: 1px solid var(--border);
      background: color-mix(in srgb, var(--bg) 88%, #ffffff 12%);
      display: none;
      max-width: 360px;
    }
    .toast.show {
      display: block;
    }
  </style>
</head>
<body>
  <header>
    <h1>Claude Code Plus Settings</h1>
    <div class="row" style="margin:0; justify-content:flex-end;">
      <button id="reloadBtn" type="button">Reload</button>
    </div>
  </header>
  <div class="container">
    <div class="card">
      <h2>Save Target</h2>
      <div class="row">
        <select id="targetSelect">
          <option value="global">User</option>
          <option value="workspace">Workspace</option>
          <option value="workspaceFolder">Workspace Folder</option>
        </select>
        <select id="folderSelect" style="min-width: 260px; display:none;"></select>
      </div>
      <div class="muted">Secrets (Codex API Key) are always stored in SecretStorage.</div>
    </div>

    <div class="card">
      <h2>General</h2>
      <label for="defaultBackendType">Default backend</label>
      <select id="defaultBackendType">
        <option value="claude">Claude</option>
        <option value="codex">Codex</option>
      </select>

      <div class="row" style="margin-top: 10px;">
        <div class="inline">
          <label><input id="defaultBypassPermissions" type="checkbox" /> Bypass permissions by default</label>
        </div>
        <div class="inline">
          <label><input id="includePartialMessages" type="checkbox" /> Include partial messages</label>
        </div>
      </div>
    </div>

    <div class="card">
      <h2>Claude Defaults</h2>
      <label for="claudeDefaultModelId">Default model ID</label>
      <input id="claudeDefaultModelId" type="text" />

      <div class="row">
        <div style="flex: 1 1 240px;">
          <label for="claudeDefaultThinkingLevel">Default thinking level</label>
          <select id="claudeDefaultThinkingLevel">
            <option value="OFF">OFF</option>
            <option value="LOW">LOW</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="HIGH">HIGH</option>
            <option value="VERY_HIGH">VERY_HIGH</option>
            <option value="ULTRA">ULTRA</option>
          </select>
        </div>
        <div style="flex: 1 1 240px;">
          <label for="claudeDefaultThinkingTokens">Default thinking tokens</label>
          <input id="claudeDefaultThinkingTokens" type="number" min="0" step="1" />
        </div>
      </div>

      <div class="inline" style="margin-top: 10px;">
        <label><input id="claudeDefaultAutoCleanupContexts" type="checkbox" /> Auto-cleanup contexts</label>
      </div>
    </div>

    <div class="card">
      <h2>Codex Defaults</h2>
      <label for="codexDefaultModelId">Default model ID</label>
      <input id="codexDefaultModelId" type="text" />

      <div class="row">
        <div style="flex: 1 1 240px;">
          <label for="codexReasoningEffort">Reasoning effort</label>
          <select id="codexReasoningEffort">
            <option value="none">none</option>
            <option value="minimal">minimal</option>
            <option value="low">low</option>
            <option value="medium">medium</option>
            <option value="high">high</option>
            <option value="xhigh">xhigh</option>
          </select>
        </div>
        <div style="flex: 1 1 240px;">
          <label for="codexReasoningSummary">Reasoning summary</label>
          <select id="codexReasoningSummary">
            <option value="auto">auto</option>
            <option value="concise">concise</option>
            <option value="detailed">detailed</option>
            <option value="none">none</option>
          </select>
        </div>
      </div>

      <label for="codexSandboxMode">Sandbox mode</label>
      <select id="codexSandboxMode">
        <option value="read-only">read-only</option>
        <option value="workspace-write">workspace-write</option>
        <option value="danger-full-access">danger-full-access</option>
      </select>

      <div class="inline" style="margin-top: 10px;">
        <label><input id="codexDefaultAutoCleanupContexts" type="checkbox" /> Auto-cleanup contexts</label>
      </div>

      <div style="margin-top: 14px;">
        <div class="row" style="align-items:flex-end;">
          <div style="flex: 1 1 320px;">
            <label for="codexApiKey">Codex API Key</label>
            <input id="codexApiKey" type="password" placeholder="Leave empty to keep unchanged" />
            <div id="codexApiKeyStatus" class="muted"></div>
          </div>
          <button id="clearCodexApiKeyBtn" type="button" class="danger">Clear API Key</button>
        </div>
      </div>
    </div>

    <div class="actions">
      <button id="saveBtn" type="button" class="primary">Save</button>
    </div>
  </div>

  <div id="toast" class="toast"></div>

  <script nonce="${nonce}">
    const vscode = acquireVsCodeApi();

    /** @type {${'SettingsWebviewData'}} */
    let state = ${initialData};

    const el = (id) => /** @type {HTMLElement} */(document.getElementById(id));
    const val = (id) => /** @type {HTMLInputElement|HTMLSelectElement} */(el(id)).value;
    const setVal = (id, v) => { /** @type {HTMLInputElement|HTMLSelectElement} */(el(id)).value = String(v); };
    const checked = (id) => /** @type {HTMLInputElement} */(el(id)).checked;
    const setChecked = (id, v) => { /** @type {HTMLInputElement} */(el(id)).checked = Boolean(v); };

    let clearCodexApiKey = false;

    function showToast(message) {
      const toast = el('toast');
      toast.textContent = message;
      toast.classList.add('show');
      setTimeout(() => toast.classList.remove('show'), 2500);
    }

    function renderFolders() {
      const folderSelect = /** @type {HTMLSelectElement} */(el('folderSelect'));
      folderSelect.innerHTML = '';
      for (const f of state.workspaceFolders) {
        const opt = document.createElement('option');
        opt.value = f.uri;
        opt.textContent = f.name;
        folderSelect.appendChild(opt);
      }
      if (state.defaultWorkspaceFolderUri) {
        folderSelect.value = state.defaultWorkspaceFolderUri;
      }
    }

    function applySettingsToForm() {
      const s = state.settings;
      setVal('defaultBackendType', s.defaultBackendType);
      setChecked('defaultBypassPermissions', s.defaultBypassPermissions);
      setChecked('includePartialMessages', s.includePartialMessages);

      setVal('claudeDefaultModelId', s.claudeDefaultModelId);
      setVal('claudeDefaultThinkingLevel', s.claudeDefaultThinkingLevel);
      setVal('claudeDefaultThinkingTokens', s.claudeDefaultThinkingTokens);
      setChecked('claudeDefaultAutoCleanupContexts', s.claudeDefaultAutoCleanupContexts);

      setVal('codexDefaultModelId', s.codexDefaultModelId);
      setVal('codexReasoningEffort', s.codexReasoningEffort);
      setVal('codexReasoningSummary', s.codexReasoningSummary);
      setVal('codexSandboxMode', s.codexSandboxMode);
      setChecked('codexDefaultAutoCleanupContexts', s.codexDefaultAutoCleanupContexts);

      el('codexApiKeyStatus').textContent = s.hasCodexApiKey ? 'Status: saved in SecretStorage' : 'Status: not set';
      clearCodexApiKey = false;
      /** @type {HTMLInputElement} */(el('codexApiKey')).value = '';
    }

    function updateTargetVisibility() {
      const target = val('targetSelect');
      const folderSelect = el('folderSelect');
      folderSelect.style.display = target === 'workspaceFolder' ? 'block' : 'none';
    }

    function buildPayload() {
      const target = /** @type {SettingsTarget} */(val('targetSelect'));
      const folderUri = val('folderSelect');

      const values = {
        'defaultBackendType': val('defaultBackendType'),
        'defaultBypassPermissions': checked('defaultBypassPermissions'),
        'includePartialMessages': checked('includePartialMessages'),
        'claude.defaultModelId': val('claudeDefaultModelId'),
        'claude.defaultThinkingLevel': val('claudeDefaultThinkingLevel'),
        'claude.defaultThinkingTokens': Number(val('claudeDefaultThinkingTokens')),
        'claude.defaultAutoCleanupContexts': checked('claudeDefaultAutoCleanupContexts'),
        'codex.defaultModelId': val('codexDefaultModelId'),
        'codex.reasoningEffort': val('codexReasoningEffort'),
        'codex.reasoningSummary': val('codexReasoningSummary'),
        'codex.sandboxMode': val('codexSandboxMode'),
        'codex.defaultAutoCleanupContexts': checked('codexDefaultAutoCleanupContexts'),
      };

      /** @type {HTMLInputElement} */(el('codexApiKey'));
      const apiKey = /** @type {HTMLInputElement} */(el('codexApiKey')).value.trim();

      return {
        type: 'ccp-settings-save',
        target,
        workspaceFolderUri: target === 'workspaceFolder' ? folderUri : undefined,
        values,
        codexApiKey: apiKey ? apiKey : undefined,
        clearCodexApiKey: clearCodexApiKey ? true : undefined,
      };
    }

    function requestReload() {
      vscode.postMessage({ type: 'ccp-settings-request' });
    }

    // Events
    el('targetSelect').addEventListener('change', updateTargetVisibility);
    el('reloadBtn').addEventListener('click', () => requestReload());
    el('saveBtn').addEventListener('click', () => {
      showToast('Saving...');
      vscode.postMessage(buildPayload());
    });
    el('clearCodexApiKeyBtn').addEventListener('click', () => {
      clearCodexApiKey = true;
      /** @type {HTMLInputElement} */(el('codexApiKey')).value = '';
      showToast('Codex API Key will be cleared on save');
    });

    window.addEventListener('message', (event) => {
      const msg = event.data;
      if (!msg || typeof msg !== 'object') return;
      if (msg.type === 'ccp-settings-data') {
        state = msg.data;
        renderFolders();
        applySettingsToForm();
        updateTargetVisibility();
        showToast('Reloaded');
        return;
      }
      if (msg.type === 'ccp-settings-saved') {
        requestReload();
        showToast('Saved');
        return;
      }
      if (msg.type === 'ccp-settings-error') {
        showToast('Error: ' + (msg.error || 'unknown'));
      }
    });

    // Init
    renderFolders();
    applySettingsToForm();
    setVal('targetSelect', state.defaultTarget || 'global');
    updateTargetVisibility();
  </script>
</body>
</html>`
}

async function saveSettingsFromWebview(
  context: vscode.ExtensionContext,
  message: Extract<SettingsMessageFromWebview, { type: 'ccp-settings-save' }>
) {
  const target = message.target
  const configTarget = toConfigurationTarget(target)
  const scope = target === 'workspaceFolder' ? toOptionalUri(message.workspaceFolderUri) : undefined
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus', scope)

  const values = message.values ?? {}
  for (const [key, value] of Object.entries(values)) {
    await cfg.update(key, value, configTarget)
  }

  if (message.clearCodexApiKey) {
    await context.secrets.delete('claudeCodePlus.codex.apiKey')
  } else if (message.codexApiKey) {
    await context.secrets.store('claudeCodePlus.codex.apiKey', message.codexApiKey)
  }
}

function toConfigurationTarget(target: SettingsTarget): vscode.ConfigurationTarget {
  switch (target) {
    case 'workspace':
      return vscode.ConfigurationTarget.Workspace
    case 'workspaceFolder':
      return vscode.ConfigurationTarget.WorkspaceFolder
    case 'global':
    default:
      return vscode.ConfigurationTarget.Global
  }
}

function toOptionalUri(value: unknown): vscode.Uri | undefined {
  if (!value || typeof value !== 'string') return undefined
  try {
    return vscode.Uri.parse(value)
  } catch {
    return undefined
  }
}

async function getPersistedSettingsSnapshot(context: vscode.ExtensionContext): Promise<PersistedSettingsSnapshot> {
  const scopeUri = getConfigScopeUri()
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus', scopeUri)

  const defaultBackendType = normalizeBackendType(cfg.get('defaultBackendType'), 'claude')
  const defaultBypassPermissions = Boolean(cfg.get('defaultBypassPermissions') ?? false)
  const includePartialMessages = Boolean(cfg.get('includePartialMessages') ?? true)

  const claudeDefaultModelId = String(cfg.get('claude.defaultModelId') ?? 'claude-opus-4-5-20251101')
  const claudeDefaultThinkingLevel = String(cfg.get('claude.defaultThinkingLevel') ?? 'HIGH').toUpperCase()
  const claudeDefaultThinkingTokens = toNumberOrDefault(cfg.get('claude.defaultThinkingTokens'), 8192)
  const claudeDefaultAutoCleanupContexts = Boolean(cfg.get('claude.defaultAutoCleanupContexts') ?? true)

  const codexDefaultModelId = String(cfg.get('codex.defaultModelId') ?? 'gpt-5.2-codex')
  const codexReasoningEffort = String(cfg.get('codex.reasoningEffort') ?? 'medium')
  const codexReasoningSummary = String(cfg.get('codex.reasoningSummary') ?? 'auto')
  const codexSandboxMode = String(cfg.get('codex.sandboxMode') ?? 'workspace-write')
  const codexDefaultAutoCleanupContexts = Boolean(cfg.get('codex.defaultAutoCleanupContexts') ?? true)

  const hasCodexApiKey = Boolean(await context.secrets.get('claudeCodePlus.codex.apiKey'))

  return {
    defaultBackendType,
    defaultBypassPermissions,
    includePartialMessages,
    claudeDefaultModelId,
    claudeDefaultThinkingLevel,
    claudeDefaultThinkingTokens,
    claudeDefaultAutoCleanupContexts,
    codexDefaultModelId,
    codexReasoningEffort,
    codexReasoningSummary,
    codexSandboxMode,
    codexDefaultAutoCleanupContexts,
    hasCodexApiKey,
  }
}

function getConfigScopeUri(): vscode.Uri | undefined {
  const active = vscode.window.activeTextEditor?.document?.uri
  if (active) {
    const folder = vscode.workspace.getWorkspaceFolder(active)
    if (folder) return active
  }
  return vscode.workspace.workspaceFolders?.[0]?.uri
}

function normalizeBackendType(value: unknown, fallback: 'claude' | 'codex'): 'claude' | 'codex' {
  return value === 'codex' || value === 'claude' ? value : fallback
}

function toNumberOrDefault(value: unknown, fallback: number): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function createNonce(): string {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
  let result = ''
  for (let i = 0; i < 16; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length))
  }
  return result
}

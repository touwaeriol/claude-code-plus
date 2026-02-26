import * as path from 'path'
import * as vscode from 'vscode'

import type { DiffContentProvider } from '../ide/diffContentProvider'
import type { FrontendRequest, FrontendResponse } from './HttpApiModels'

export async function handleApiRequest(
  payload: unknown,
  ctx: { context: vscode.ExtensionContext; deps?: { diffProvider?: DiffContentProvider } }
): Promise<FrontendResponse> {
  if (!isFrontendRequest(payload)) {
    return { success: false, error: 'Invalid request payload' }
  }

  const { action, data } = payload

  try {
    switch (action) {
      case 'test.ping': {
        // Align with JetBrains backend semantics: top-level { success, message }
        return { success: true, message: 'pong' } as any
      }
      case 'ide.ping': {
        return { success: true, data: { ok: true } }
      }
      case 'ide.hasIdeEnvironment': {
        // VS Code implements /ide-rsocket: tool cards, rollback bar, theme sync, etc.
        return { success: true, data: { hasIde: true } }
      }
      case 'ide.getProjectPath': {
        return { success: true, data: { projectPath: getWorkspaceRootFsPath() } }
      }
      case 'ide.getWorkspaceInfo': {
        const folders = vscode.workspace.workspaceFolders ?? []
        return {
          success: true,
          data: {
            hasWorkspace: folders.length > 0,
            isMultiRoot: folders.length > 1,
            workspaceFolders: folders.map((f, i) => ({
              name: f.name,
              path: f.uri.fsPath,
              index: f.index,
              isPrimary: i === 0,
            })),
            primaryFolder: folders[0]?.uri.fsPath ?? null,
            additionalFolders: folders.slice(1).map(f => f.uri.fsPath),
          },
        }
      }
      case 'node.detect': {
        return {
          success: true,
          data: {
            found: true,
            path: process.execPath,
            version: process.version,
          },
        }
      }
      case 'settings.getDefault': {
        return { success: true, data: getHttpDefaultSettings() }
      }
      case 'settings.get': {
        const settings = await getPersistedSettings(ctx.context)
        return { success: true, data: { settings } }
      }
      case 'models.getAvailable': {
        return {
          success: true,
          data: getAvailableModels(),
        }
      }
      case 'ide.openUrl': {
        const url = String((data ?? {}).url ?? '')
        if (!url) return { success: false, error: 'Missing url' }
        await vscode.env.openExternal(vscode.Uri.parse(url))
        return { success: true }
      }
      case 'ide.getLocale': {
        return { success: true, data: { locale: vscode.env.language } }
      }
      case 'ide.setLocale': {
        // VS Code can't switch extension UI language; persist locale so the frontend can render localized strings.
        const locale = typeof data === 'string' ? data : String(data?.locale ?? '')
        await ctx.context.globalState.update('claudeCodePlus.locale', locale)
        return { success: true }
      }
      case 'ide.openFile': {
        const filePath = String((data ?? {}).filePath ?? '')
        if (!filePath) return { success: false, error: 'Missing filePath' }

        const line = toOptionalNumber((data ?? {}).line)
        const endLine = toOptionalNumber((data ?? {}).endLine)
        const column = toOptionalNumber((data ?? {}).column)
        const selectContent = (data ?? {}).selectContent !== false
        const contentToSelect = typeof (data ?? {}).content === 'string' ? (data ?? {}).content : undefined
        const selectionStart = toOptionalNumber((data ?? {}).selectionStart)
        const selectionEnd = toOptionalNumber((data ?? {}).selectionEnd)

        const uri = toWorkspaceFileUri(filePath)
        let doc: vscode.TextDocument
        try {
          doc = await vscode.workspace.openTextDocument(uri)
        } catch (error) {
          const message = error instanceof Error ? error.message : String(error)
          void vscode.window.showWarningMessage(`无法打开文件：${filePath}（${message}）`)
          return { success: false, error: `Failed to open file: ${message}` }
        }

        const editor = await vscode.window.showTextDocument(doc, { preview: false })

        // 优先级：selectionStart/End > selectContent(content) > line/column/endLine
        if (selectionStart !== undefined && selectionEnd !== undefined) {
          const startOffset = clamp(selectionStart, 0, doc.getText().length)
          const endOffset = clamp(selectionEnd, startOffset, doc.getText().length)
          const startPos = doc.positionAt(startOffset)
          const endPos = doc.positionAt(endOffset)
          editor.selection = new vscode.Selection(startPos, endPos)
          editor.revealRange(new vscode.Range(startPos, endPos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
          return { success: true }
        }

        if (selectContent && contentToSelect) {
          const fullText = doc.getText()
          const idx = fullText.indexOf(contentToSelect)
          if (idx >= 0) {
            const startPos = doc.positionAt(idx)
            const endPos = doc.positionAt(idx + contentToSelect.length)
            editor.selection = new vscode.Selection(startPos, endPos)
            editor.revealRange(new vscode.Range(startPos, endPos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
            return { success: true }
          }
        }

        if (line !== undefined) {
          const startLine0 = clamp(line - 1, 0, Math.max(doc.lineCount - 1, 0))
          const startCol0 = Math.max((column ?? 1) - 1, 0)
          const startPos = new vscode.Position(startLine0, startCol0)

          if (endLine !== undefined && endLine >= line) {
            const endLine0 = clamp(endLine - 1, startLine0, Math.max(doc.lineCount - 1, 0))
            const endPos = doc.lineAt(endLine0).range.end
            editor.selection = new vscode.Selection(startPos, endPos)
            editor.revealRange(new vscode.Range(startPos, endPos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
          } else {
            editor.selection = new vscode.Selection(startPos, startPos)
            editor.revealRange(new vscode.Range(startPos, startPos), vscode.TextEditorRevealType.InCenterIfOutsideViewport)
          }
        }

        return { success: true }
      }
      case 'ide.getFileContent': {
        const filePath = String((data ?? {}).filePath ?? '')
        if (!filePath) return { success: false, error: 'Missing filePath' }

        const lineStart = toOptionalNumber((data ?? {}).lineStart)
        const lineEnd = toOptionalNumber((data ?? {}).lineEnd)

        const uri = toWorkspaceFileUri(filePath)
        const stat = await vscode.workspace.fs.stat(uri)
        const raw = await vscode.workspace.fs.readFile(uri)
        const text = Buffer.from(raw).toString('utf8')

        if (lineStart !== undefined || lineEnd !== undefined) {
          const lines = text.split(/\r?\n/)
          const startIdx = Math.max((lineStart ?? 1) - 1, 0)
          const endIdx = Math.min((lineEnd ?? lines.length), lines.length)
          return { success: true, data: { content: lines.slice(startIdx, endIdx).join('\n') } }
        }

        if (stat.size > MAX_FILE_BYTES && text.length > MAX_FILE_CHARS) {
          const truncated = text.slice(0, MAX_FILE_CHARS)
          return {
            success: true,
            data: {
              content:
                truncated +
                `\n\n... (truncated: file is ${stat.size} bytes, showing first ${MAX_FILE_CHARS} chars) ...\n`,
            },
          }
        }

        return { success: true, data: { content: text } }
      }
      case 'ide.searchFiles': {
        const query = String((data ?? {}).query ?? '').trim()
        const maxResults = toOptionalNumber((data ?? {}).maxResults) ?? 20
        if (!query) return { success: true, data: { files: '[]', filesV2: [] } }

        const candidateLimit = Math.min(Math.max(maxResults * 50, 200), 2000)
        const uris = await vscode.workspace.findFiles('**/*', '**/{node_modules,.git}/**', candidateLimit)

        const q = query.toLowerCase()
        const scored = uris
          .map((u) => {
            const rel = vscode.workspace.asRelativePath(u, false)
            const idx = rel.toLowerCase().indexOf(q)
            if (idx < 0) return null
            return { uri: u, rel, idx }
          })
          .filter((x): x is { uri: vscode.Uri; rel: string; idx: number } => x !== null)
          .sort((a, b) => a.idx - b.idx || a.rel.length - b.rel.length)

        const filePaths = scored.slice(0, maxResults).map(({ uri }) => uri.fsPath)
        return {
          success: true,
          data: {
            // JetBrains backend returns a JSON-stringified string array in data.files
            files: JSON.stringify(filePaths),
            // Back-compat for any VS Code-only callers (non-JB semantics)
            filesV2: filePaths.map((p) => ({ name: path.basename(p), path: p })),
          },
        }
      }
      case 'ide.showDiff': {
        const { diffProvider } = ctx.deps ?? {}
        if (!diffProvider) return { success: false, error: 'Diff provider not available' }

        const filePath = String((data ?? {}).filePath ?? '')
        if (!filePath) return { success: false, error: 'Missing filePath' }

        const oldContent = String((data ?? {}).oldContent ?? '')
        const newContent = String((data ?? {}).newContent ?? '')
        const title = String((data ?? {}).title ?? '')
        const rebuildFromFile = Boolean((data ?? {}).rebuildFromFile)
        const edits = parseEditOperations((data ?? {}).edits)

        const baseName = filePath ? path.basename(filePath) : 'diff'

        let finalOldContent = oldContent
        let finalNewContent = newContent
        let finalTitle = title || `File Diff: ${baseName}`

        if (rebuildFromFile) {
          const uri = toWorkspaceFileUri(filePath)
          const raw = await vscode.workspace.fs.readFile(uri)
          const currentContent = Buffer.from(raw).toString('utf8')

          const finalEdits =
            edits.length > 0
              ? edits
              : [
                  {
                    oldString: oldContent,
                    newString: newContent,
                    replaceAll: false,
                  },
                ]

          finalOldContent = rebuildBeforeContent(currentContent, finalEdits)
          finalNewContent = currentContent
          finalTitle = title || `File Changes: ${baseName} (${finalEdits.length} edits)`
        }

        const left = diffProvider.createUri(finalOldContent, `${baseName} (before)`)
        const right = diffProvider.createUri(finalNewContent, `${baseName} (after)`)
        await vscode.commands.executeCommand('vscode.diff', left, right, finalTitle)

        return { success: true }
      }
      case 'ide.getTheme': {
        return { success: true, data: getIdeTheme() }
      }
      case 'claude.connect':
      case 'claude.query':
      case 'claude.interrupt':
      case 'claude.disconnect': {
        return {
          success: false,
          error: 'Claude HTTP bridge is deprecated; use RSocket /rsocket instead',
        }
      }
      default:
        return { success: false, error: `Unknown action: ${action}` }
    }
  } catch (error) {
    return {
      success: false,
      error: error instanceof Error ? error.message : String(error),
    }
  }
}

export async function handleFileSearchRequest(
  url: URL,
  _ctx: { context: vscode.ExtensionContext }
): Promise<{ success: boolean; data?: any; error?: string; errorCode?: string }> {
  try {
    const query = (url.searchParams.get('query') ?? '').trim()
    const maxResults = Number(url.searchParams.get('maxResults') ?? '10') || 10

    const folder = vscode.workspace.workspaceFolders?.[0]
    if (!folder) return { success: true, data: [] }

    // 空查询：返回项目根目录（第一工作区）的直接子项，便于 @ 提及时快速选文件
    if (!query) {
      const entries = await vscode.workspace.fs.readDirectory(folder.uri)
      const fileEntries = entries.filter(
        ([, type]) =>
          (type & vscode.FileType.File) === vscode.FileType.File &&
          (type & vscode.FileType.Directory) !== vscode.FileType.Directory
      )

      const candidates = await Promise.all(
        fileEntries.map(async ([name]) => {
          try {
            const uri = vscode.Uri.joinPath(folder.uri, name)
            const stat = await vscode.workspace.fs.stat(uri)
            return { name, uri, stat }
          } catch {
            return null
          }
        })
      )

      const sorted = candidates
        .filter((x): x is { name: string; uri: vscode.Uri; stat: vscode.FileStat } => x !== null)
        .sort((a, b) => b.stat.mtime - a.stat.mtime)
        .slice(0, maxResults)

      const files = sorted.map(({ name, uri, stat }) => {
        const ext = path.extname(name).replace(/^\./, '')
        return {
          name,
          relativePath: name,
          absolutePath: uri.fsPath,
          fileType: ext || 'unknown',
          size: stat.size,
          lastModified: stat.mtime,
          isDirectory: false,
        }
      })

      return { success: true, data: files }
    }

    const candidateLimit = Math.min(Math.max(maxResults * 50, 200), 2000)
    const uris = await vscode.workspace.findFiles('**/*', '**/{node_modules,.git}/**', candidateLimit)

    const q = query.toLowerCase()
    const filtered = uris.filter((u) => vscode.workspace.asRelativePath(u, false).toLowerCase().includes(q))

    const files = await Promise.all(
      filtered.slice(0, maxResults).map(async (uri) => {
        const stat = await vscode.workspace.fs.stat(uri)
        const name = path.basename(uri.fsPath)
        const relativePath = vscode.workspace.asRelativePath(uri, false)
        const ext = path.extname(name).replace(/^\./, '')
        return {
          name,
          relativePath,
          absolutePath: uri.fsPath,
          fileType: ext || 'unknown',
          size: stat.size,
          lastModified: stat.mtime,
          isDirectory: stat.type === vscode.FileType.Directory,
        }
      })
    )

    return { success: true, data: files }
  } catch (error) {
    return { success: false, error: error instanceof Error ? error.message : String(error) }
  }
}

function isFrontendRequest(payload: unknown): payload is FrontendRequest {
  if (!payload || typeof payload !== 'object') return false
  const rec = payload as Record<string, unknown>
  return typeof rec.action === 'string'
}

function toOptionalNumber(value: unknown): number | undefined {
  if (value === null || value === undefined) return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

function getWorkspaceRootFsPath(): string {
  const folder = vscode.workspace.workspaceFolders?.[0]
  return folder?.uri.fsPath ?? ''
}

function toWorkspaceFileUri(filePath: string): vscode.Uri {
  if (/^file:/i.test(filePath)) {
    return vscode.Uri.parse(filePath)
  }

  if (path.isAbsolute(filePath)) {
    return vscode.Uri.file(filePath)
  }

  const folders = vscode.workspace.workspaceFolders ?? []
  if (folders.length === 0) return vscode.Uri.file(filePath)

  if (folders.length === 1) {
    return vscode.Uri.file(path.join(folders[0].uri.fsPath, filePath))
  }

  // Multi-root: allow "<workspaceFolderName>/<relativePath>" to disambiguate
  const normalized = filePath.replace(/\\/g, '/')
  const parts = normalized.split('/').filter(Boolean)
  if (parts.length >= 2) {
    const folderName = parts[0]
    const folder = folders.find((f) => f.name === folderName)
    if (folder) {
      return vscode.Uri.file(path.join(folder.uri.fsPath, ...parts.slice(1)))
    }
  }

  // Fallback: resolve against the first workspace folder
  return vscode.Uri.file(path.join(folders[0].uri.fsPath, filePath))
}

type EditOperation = { oldString: string; newString: string; replaceAll: boolean }

const MAX_FILE_BYTES = 2 * 1024 * 1024
const MAX_FILE_CHARS = 200_000

const DARK_THEME = {
  background: '#1e1e1e',
  foreground: '#d4d4d4',
  panelBackground: '#252526',
  borderColor: '#3c3c3c',
  textFieldBackground: '#3c3c3c',
  selectionBackground: '#264f78',
  selectionForeground: '#ffffff',
  linkColor: '#3794ff',
  errorColor: '#f14c4c',
  warningColor: '#cca700',
  successColor: '#89d185',
  separatorColor: '#3c3c3c',
  hoverBackground: '#2a2d2e',
  accentColor: '#0e639c',
  infoBackground: '#2d2d2d',
  codeBackground: '#1e1e1e',
  secondaryForeground: '#858585',
}

const LIGHT_THEME = {
  background: '#ffffff',
  foreground: '#24292e',
  panelBackground: '#fafbfc',
  borderColor: '#e1e4e8',
  textFieldBackground: '#ffffff',
  selectionBackground: '#d2e7ff',
  selectionForeground: '#0b3d91',
  linkColor: '#0366d6',
  errorColor: '#d73a49',
  warningColor: '#ffc107',
  successColor: '#28a745',
  separatorColor: '#e1e4e8',
  hoverBackground: '#f3f4f6',
  accentColor: '#0366d6',
  infoBackground: '#f5f5f5',
  codeBackground: '#f8f9fa',
  secondaryForeground: '#6a737d',
}

function clamp(n: number, min: number, max: number): number {
  return Math.min(Math.max(n, min), max)
}

function parseEditOperations(value: unknown): EditOperation[] {
  if (!Array.isArray(value)) return []
  return value
    .map((item) => {
      if (!item || typeof item !== 'object') return null
      const rec = item as Record<string, unknown>
      const oldString = typeof rec.oldString === 'string' ? rec.oldString : null
      const newString = typeof rec.newString === 'string' ? rec.newString : null
      if (oldString === null || newString === null) return null
      return { oldString, newString, replaceAll: Boolean(rec.replaceAll) }
    })
    .filter((x): x is EditOperation => x !== null)
}

function rebuildBeforeContent(afterContent: string, operations: EditOperation[]): string {
  let content = afterContent
  for (const op of [...operations].reverse()) {
    if (!op.newString) continue
    if (op.replaceAll) {
      content = content.replaceAll(op.newString, op.oldString)
      continue
    }
    const index = content.indexOf(op.newString)
    if (index < 0) continue
    content = content.slice(0, index) + op.oldString + content.slice(index + op.newString.length)
  }
  return content
}

export function getIdeTheme() {
  const kind = vscode.window.activeColorTheme.kind
  const isDark = kind === vscode.ColorThemeKind.Dark || kind === vscode.ColorThemeKind.HighContrast

  const editorConfig = vscode.workspace.getConfiguration('editor')
  const editorFontFamily = String(editorConfig.get('fontFamily') ?? '')
  const editorFontSize = Number(editorConfig.get('fontSize') ?? 14)

  return {
    ...(isDark ? DARK_THEME : LIGHT_THEME),
    isDarkTheme: isDark,
    // Return font fields to match jetbrainsTypes.IdeTheme (includes font metadata).
    fontFamily: editorFontFamily || 'system-ui',
    fontSize: Number.isFinite(editorFontSize) ? editorFontSize : 14,
    editorFontFamily: editorFontFamily || 'monospace',
    editorFontSize: Number.isFinite(editorFontSize) ? editorFontSize : 14,
  }
}

function getHttpDefaultSettings() {
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus', getConfigScopeUri())

  const defaultBackendType = normalizeBackendType(cfg.get('defaultBackendType'), 'claude')
  const defaultBypassPermissions = Boolean(cfg.get('defaultBypassPermissions') ?? false)
  const includePartialMessages = Boolean(cfg.get('includePartialMessages') ?? true)

  const claudeDefaultModelId = String(cfg.get('claude.defaultModelId') ?? 'claude-opus-4-6')
  const claudeDefaultThinkingLevel = String(cfg.get('claude.defaultThinkingLevel') ?? 'HIGH').toUpperCase()
  const claudeDefaultThinkingTokens = toNumberOrDefault(cfg.get('claude.defaultThinkingTokens'), 8192)
  const claudeDefaultAutoCleanupContexts = Boolean(cfg.get('claude.defaultAutoCleanupContexts') ?? true)

  const codexDefaultModelId = String(cfg.get('codex.defaultModelId') ?? 'gpt-5.2-codex')
  const codexReasoningEffort = String(cfg.get('codex.reasoningEffort') ?? 'medium')
  const codexReasoningSummary = String(cfg.get('codex.reasoningSummary') ?? 'auto')
  const codexSandboxMode = String(cfg.get('codex.sandboxMode') ?? 'workspace-write')
  const codexDefaultAutoCleanupContexts = Boolean(cfg.get('codex.defaultAutoCleanupContexts') ?? true)

  return {
    defaultBackendType,
    defaultBypassPermissions,
    includePartialMessages,

    // Claude
    claudeDefaultModelId,
    claudeDefaultThinkingLevel,
    claudeDefaultThinkingTokens,
    defaultThinkingLevel: claudeDefaultThinkingLevel,
    defaultThinkingTokens: claudeDefaultThinkingTokens,
    claudeDefaultAutoCleanupContexts,

    // Codex
    codexDefaultModelId,
    codexReasoningEffort,
    codexReasoningSummary,
    codexSandboxMode,
    codexDefaultAutoCleanupContexts,
  }
}

function getAvailableModels() {
  const claudeModels = [
    {
      modelId: 'claude-haiku-4-5-20251001',
      displayName: 'Claude Haiku 4.5',
      description: 'Fast and efficient for simple tasks',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
    {
      modelId: 'claude-opus-4-6',
      displayName: 'Claude Opus 4.6',
      description: 'Next-gen most powerful model',
      supportsThinking: true,
      isDefault: true,
      isBuiltIn: true,
    },
    {
      modelId: 'claude-sonnet-4-6',
      displayName: 'Claude Sonnet 4.6',
      description: 'Next-gen balanced performance',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
  ]

  const codexModels = [
    {
      modelId: 'gpt-5.2-codex',
      displayName: 'GPT-5.2-Codex',
      description: 'Default Codex model',
      supportsThinking: true,
      isDefault: true,
      isBuiltIn: true,
    },
    {
      modelId: 'gpt-5.1-codex-max',
      displayName: 'GPT-5.1-Codex-Max',
      description: 'Best coding model',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
    {
      modelId: 'gpt-5.2',
      displayName: 'GPT-5.2',
      description: 'General Codex model',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
    {
      modelId: 'gpt-5.3-codex',
      displayName: 'GPT-5.3-Codex',
      description: 'Next-gen Codex model',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
    {
      modelId: 'gpt-5.3-codex-spark',
      displayName: 'GPT-5.3-Codex-Spark',
      description: 'Lightweight next-gen Codex model',
      supportsThinking: true,
      isDefault: false,
      isBuiltIn: true,
    },
  ]

  return {
    claudeModels,
    codexModels,
    defaultBackendType: 'claude',
    defaultClaudeModelId: claudeModels.find((m) => m.isDefault)?.modelId ?? claudeModels[0].modelId,
    defaultCodexModelId: codexModels.find((m) => m.isDefault)?.modelId ?? codexModels[0].modelId,
  }
}

async function getPersistedSettings(context: vscode.ExtensionContext): Promise<Record<string, unknown>> {
  const cfg = vscode.workspace.getConfiguration('claudeCodePlus', getConfigScopeUri())

  const defaultBackendType = normalizeBackendType(cfg.get('defaultBackendType'), 'claude')
  const includePartialMessages = Boolean(cfg.get('includePartialMessages') ?? true)
  const skipPermissions = Boolean(cfg.get('defaultBypassPermissions') ?? false)

  const claudeModel = String(cfg.get('claude.defaultModelId') ?? 'claude-opus-4-6')
  const claudeThinkingLevel = String(cfg.get('claude.defaultThinkingLevel') ?? 'HIGH').toUpperCase()
  const claudeThinkingTokens = toNumberOrDefault(cfg.get('claude.defaultThinkingTokens'), 8192)
  const claudeThinkingEnabled = claudeThinkingLevel !== 'OFF' && claudeThinkingTokens > 0
  const claudeDefaultAutoCleanupContexts = Boolean(cfg.get('claude.defaultAutoCleanupContexts') ?? true)

  const codexModel = String(cfg.get('codex.defaultModelId') ?? 'gpt-5.2-codex')
  const codexReasoningEffort = String(cfg.get('codex.reasoningEffort') ?? 'medium')
  const codexReasoningSummary = String(cfg.get('codex.reasoningSummary') ?? 'auto')
  const codexSandboxMode = String(cfg.get('codex.sandboxMode') ?? 'workspace-write')
  const codexDefaultAutoCleanupContexts = Boolean(cfg.get('codex.defaultAutoCleanupContexts') ?? true)

  // Sensitive fields: VS Code SecretStorage
  const codexApiKey = await context.secrets.get('claudeCodePlus.codex.apiKey')

  return {
    defaultBackendType,
    permissionMode: 'default',
    skipPermissions,
    includePartialMessages,
    maxTurns: null,

    // 会话默认设置
    claudeDefaultAutoCleanupContexts,
    codexDefaultAutoCleanupContexts,

    // Claude
    claudeModel,
    claudeThinkingEnabled,
    claudeThinkingTokens,

    // Codex
    codexModel,
    codexReasoningEffort,
    codexReasoningSummary,
    codexSandboxMode,
    ...(codexApiKey ? { codexApiKey } : {}),

    // 高级设置（VS Code 版暂不持久化，保持与前端默认一致）
    systemPrompt: null,
    continueConversation: false,
    maxTokens: null,
    temperature: null,
    verbose: false,
  }
}

function normalizeBackendType(value: unknown, fallback: 'claude' | 'codex'): 'claude' | 'codex' {
  return value === 'codex' || value === 'claude' ? value : fallback
}

function getConfigScopeUri(): vscode.Uri | undefined {
  const active = vscode.window.activeTextEditor?.document?.uri
  if (active) {
    const folder = vscode.workspace.getWorkspaceFolder(active)
    if (folder) return active
  }
  return vscode.workspace.workspaceFolders?.[0]?.uri
}

function toNumberOrDefault(value: unknown, fallback: number): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

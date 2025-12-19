/**
 * JetBrains IDE 集成 RSocket 服务
 *
 * 使用 RSocket + Protobuf 与后端通信
 * 支持双向调用：
 * - 前端 → 后端：openFile, showDiff, getTheme 等
 * - 后端 → 前端：onThemeChanged, onSessionCommand 等
 */

import { RSocketClient } from './rsocket/RSocketClient'
import { resolveServerHttpUrl } from '@/utils/serverUrl'
import { create, toBinary, fromBinary } from '@bufbuild/protobuf'
import {
  JetBrainsOpenFileRequestSchema,
  JetBrainsShowDiffRequestSchema,
  JetBrainsShowMultiEditDiffRequestSchema,
  JetBrainsShowEditPreviewRequestSchema,
  JetBrainsShowMarkdownRequestSchema,
  JetBrainsEditOperationSchema,
  JetBrainsOperationResponseSchema,
  JetBrainsGetThemeResponseSchema,
  JetBrainsGetLocaleResponseSchema,
  JetBrainsGetProjectPathResponseSchema,
  JetBrainsSetLocaleRequestSchema,
  JetBrainsSessionStateSchema,
  JetBrainsSessionSummarySchema
} from '@/proto/jetbrains_api_pb'
import {
  GetIdeSettingsResponseSchema,
  ActiveFileChangedNotifySchema
} from '@/proto/ai_agent_rpc_pb'
import type {
  OpenFileRequest,
  ShowDiffRequest,
  ShowMultiEditDiffRequest,
  ShowEditPreviewRequest,
  ShowMarkdownRequest
} from './jetbrainsApi'

// ========== Protobuf 编解码（使用官方库）==========

/**
 * 编码 JetBrainsOpenFileRequest
 */
function encodeOpenFileRequest(request: OpenFileRequest): Uint8Array {
  const proto = create(JetBrainsOpenFileRequestSchema, {
    filePath: request.filePath,
    line: request.line,
    column: request.column,
    startOffset: request.startOffset,
    endOffset: request.endOffset
  })
  return toBinary(JetBrainsOpenFileRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowDiffRequest
 */
function encodeShowDiffRequest(request: ShowDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowDiffRequestSchema, {
    filePath: request.filePath,
    oldContent: request.oldContent,
    newContent: request.newContent,
    title: request.title
  })
  return toBinary(JetBrainsShowDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowMultiEditDiffRequest
 */
function encodeShowMultiEditDiffRequest(request: ShowMultiEditDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowMultiEditDiffRequestSchema, {
    filePath: request.filePath,
    edits: request.edits.map(edit => create(JetBrainsEditOperationSchema, {
      oldString: edit.oldString,
      newString: edit.newString,
      replaceAll: edit.replaceAll
    })),
    currentContent: request.currentContent
  })
  return toBinary(JetBrainsShowMultiEditDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowEditPreviewRequest
 */
function encodeShowEditPreviewRequest(request: ShowEditPreviewRequest): Uint8Array {
  const proto = create(JetBrainsShowEditPreviewRequestSchema, {
    filePath: request.filePath,
    edits: request.edits.map(edit => create(JetBrainsEditOperationSchema, {
      oldString: edit.oldString,
      newString: edit.newString,
      replaceAll: edit.replaceAll
    })),
    title: request.title
  })
  return toBinary(JetBrainsShowEditPreviewRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowMarkdownRequest
 */
function encodeShowMarkdownRequest(request: ShowMarkdownRequest): Uint8Array {
  const proto = create(JetBrainsShowMarkdownRequestSchema, {
    content: request.content,
    title: request.title
  })
  return toBinary(JetBrainsShowMarkdownRequestSchema, proto)
}

/**
 * 编码 JetBrainsSetLocaleRequest
 */
function encodeSetLocaleRequest(locale: string): Uint8Array {
  const proto = create(JetBrainsSetLocaleRequestSchema, { locale })
  return toBinary(JetBrainsSetLocaleRequestSchema, proto)
}

/**
 * 解码 JetBrainsOperationResponse
 */
function decodeOperationResponse(data: Uint8Array): { success: boolean; error?: string } {
  const proto = fromBinary(JetBrainsOperationResponseSchema, data)
  return { success: proto.success, error: proto.error || undefined }
}

/**
 * 解码 JetBrainsGetThemeResponse -> IdeTheme
 */
function decodeThemeResponse(data: Uint8Array): IdeTheme | null {
  const proto = fromBinary(JetBrainsGetThemeResponseSchema, data)
  if (!proto.theme) return null

  const theme = proto.theme
  return {
    background: theme.background,
    foreground: theme.foreground,
    borderColor: theme.borderColor,
    panelBackground: theme.panelBackground,
    textFieldBackground: theme.textFieldBackground,
    selectionBackground: theme.selectionBackground,
    selectionForeground: theme.selectionForeground,
    linkColor: theme.linkColor,
    errorColor: theme.errorColor,
    warningColor: theme.warningColor,
    successColor: theme.successColor,
    separatorColor: theme.separatorColor,
    hoverBackground: theme.hoverBackground,
    accentColor: theme.accentColor,
    infoBackground: theme.infoBackground,
    codeBackground: theme.codeBackground,
    secondaryForeground: theme.secondaryForeground,
    fontFamily: theme.fontFamily,
    fontSize: theme.fontSize,
    editorFontFamily: theme.editorFontFamily,
    editorFontSize: theme.editorFontSize
  }
}

/**
 * 解码 JetBrainsGetLocaleResponse
 */
function decodeLocaleResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetLocaleResponseSchema, data)
  return proto.locale || 'en-US'
}

/**
 * 解码 JetBrainsGetProjectPathResponse
 */
function decodeProjectPathResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetProjectPathResponseSchema, data)
  return proto.projectPath || ''
}

/**
 * 解码 ActiveFileChangedNotify（用于 getActiveFile 响应）
 */
function decodeActiveFileResponse(data: Uint8Array): ActiveFileInfo | null {
  const proto = fromBinary(ActiveFileChangedNotifySchema, data)

  if (!proto.hasActiveFile) {
    return null
  }

  return {
    path: proto.path || '',
    relativePath: proto.relativePath || '',
    name: proto.name || '',
    line: proto.line || undefined,
    column: proto.column || undefined,
    hasSelection: proto.hasSelection,
    startLine: proto.startLine || undefined,
    startColumn: proto.startColumn || undefined,
    endLine: proto.endLine || undefined,
    endColumn: proto.endColumn || undefined,
    selectedContent: proto.selectedContent || undefined
  }
}

/**
 * 解码 GetIdeSettingsResponse
 */
function decodeSettingsResponse(data: Uint8Array): IdeSettings | null {
  // 默认思考级别列表
  const defaultThinkingLevels: ThinkingLevelConfig[] = [
    { id: 'off', name: 'Off', tokens: 0, isCustom: false },
    { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
    { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
  ]

  const proto = fromBinary(GetIdeSettingsResponseSchema, data)
  const s = proto.settings

  if (!s) {
    return {
      defaultModelId: '',
      defaultModelName: '',
      defaultBypassPermissions: false,
      enableUserInteractionMcp: true,
      enableJetbrainsMcp: true,
      includePartialMessages: true,
      defaultThinkingLevel: 'ULTRA',
      defaultThinkingTokens: 8096,
      defaultThinkingLevelId: 'ultra',
      thinkingLevels: defaultThinkingLevels,
      permissionMode: 'default'
    }
  }

  return {
    defaultModelId: s.defaultModelId || '',
    defaultModelName: s.defaultModelName || '',
    defaultBypassPermissions: s.defaultBypassPermissions,
    enableUserInteractionMcp: s.enableUserInteractionMcp,
    enableJetbrainsMcp: s.enableJetbrainsMcp,
    includePartialMessages: s.includePartialMessages,
    defaultThinkingLevel: s.defaultThinkingLevel || 'ULTRA',
    defaultThinkingTokens: s.defaultThinkingTokens || 8096,
    defaultThinkingLevelId: s.defaultThinkingLevelId || 'ultra',
    thinkingLevels: s.thinkingLevels.length > 0
      ? s.thinkingLevels.map(level => ({
          id: level.id,
          name: level.name,
          tokens: level.tokens,
          isCustom: level.isCustom
        }))
      : defaultThinkingLevels,
    permissionMode: s.permissionMode || 'default'
  }
}

/**
 * 编码 JetBrainsSessionState
 */
function encodeSessionState(state: SessionState): Uint8Array {
  const proto = create(JetBrainsSessionStateSchema, {
    sessions: state.sessions.map(session => create(JetBrainsSessionSummarySchema, {
      id: session.id,
      title: session.title,
      sessionId: session.sessionId || undefined,
      isGenerating: session.isGenerating,
      isConnected: session.isConnected,
      isConnecting: session.isConnecting
    })),
    activeSessionId: state.activeSessionId || undefined
  })
  return toBinary(JetBrainsSessionStateSchema, proto)
}

// ========== 类型定义 ==========

export interface IdeTheme {
  background: string
  foreground: string
  borderColor: string
  panelBackground: string
  textFieldBackground: string
  selectionBackground: string
  selectionForeground: string
  linkColor: string
  errorColor: string
  warningColor: string
  successColor: string
  separatorColor: string
  hoverBackground: string
  accentColor: string
  infoBackground: string
  codeBackground: string
  secondaryForeground: string
  fontFamily: string
  fontSize: number
  editorFontFamily: string
  editorFontSize: number
}

export interface SessionCommand {
  type: 'switch' | 'create' | 'close' | 'rename' | 'toggleHistory' | 'setLocale' | 'delete' | 'reset'
  sessionId?: string
  newName?: string
  locale?: string
}

/**
 * 映射 protoCodec 的 SessionCommandParams.type 到 SessionCommand.type
 */
function mapSessionCommandType(type: string): SessionCommand['type'] {
  switch (type) {
    case 'switch': return 'switch'
    case 'create': return 'create'
    case 'close': return 'close'
    case 'rename': return 'rename'
    case 'toggleHistory': return 'toggleHistory'
    case 'setLocale': return 'setLocale'
    case 'delete': return 'delete'
    case 'reset': return 'reset'
    default:
      console.warn(`[JetBrainsRSocket] Unknown session command type: ${type}`)
      return type as SessionCommand['type'] // 保持原值，避免错误转换
  }
}

export interface SessionSummary {
  id: string
  title: string
  sessionId?: string | null
  isGenerating: boolean
  isConnected: boolean
  isConnecting: boolean
}

export interface SessionState {
  sessions: SessionSummary[]
  activeSessionId?: string | null
}

export type ThemeChangeHandler = (theme: IdeTheme) => void
export type SessionCommandHandler = (command: SessionCommand) => void
export type SettingsChangeHandler = (settings: IdeSettings) => void
export type ActiveFileChangeHandler = (activeFile: ActiveFileInfo | null) => void

// 当前活跃文件信息
export interface ActiveFileInfo {
  path: string           // 文件绝对路径
  relativePath: string   // 相对于项目根目录的路径
  name: string           // 文件名
  line?: number          // 当前光标所在行（1-based）
  column?: number        // 当前光标所在列（1-based）
  // 选区信息
  hasSelection: boolean
  startLine?: number     // 选区起始行（1-based）
  startColumn?: number   // 选区起始列（1-based）
  endLine?: number       // 选区结束行（1-based）
  endColumn?: number     // 选区结束列（1-based）
  selectedContent?: string // 选中的文本内容（可选）
  // 文件类型相关字段
  fileType?: string      // 文件类型: "text", "diff", "image", "binary"
  // Diff 视图专用字段
  diffOldContent?: string  // Diff 旧内容（左侧）
  diffNewContent?: string  // Diff 新内容（右侧）
  diffTitle?: string       // Diff 标题
}

// IDE 设置接口
export interface IdeSettings {
  defaultModelId: string
  defaultModelName: string
  defaultBypassPermissions: boolean
  enableUserInteractionMcp: boolean
  enableJetbrainsMcp: boolean
  includePartialMessages: boolean
  // 思考配置
  defaultThinkingLevelId: string  // 默认思考级别 ID（如 "off", "think", "ultra", "custom_xxx"）
  defaultThinkingTokens: number   // 默认思考 token 数量
  thinkingLevels: ThinkingLevelConfig[]  // 所有可用的思考级别
  // 旧字段，保留向后兼容
  defaultThinkingLevel?: string  // 思考等级枚举名称（如 "HIGH", "MEDIUM", "OFF"）
  // 权限模式
  permissionMode?: string  // 权限模式（default, acceptEdits, plan, bypassPermissions, dontAsk）
}

// 思考级别配置
export interface ThinkingLevelConfig {
  id: string        // 唯一标识：off, think, ultra, custom_xxx
  name: string      // 显示名称
  tokens: number    // token 数量
  isCustom: boolean // 是否为自定义级别
}

// ========== RSocket 服务 ==========

class JetBrainsRSocketService {
  private client: RSocketClient | null = null
  private themeChangeHandlers: ThemeChangeHandler[] = []
  private sessionCommandHandlers: SessionCommandHandler[] = []
  private settingsChangeHandlers: SettingsChangeHandler[] = []
  private activeFileChangeHandlers: ActiveFileChangeHandler[] = []
  private connected = false

  /**
   * 连接到 JetBrains RSocket 端点
   */
  async connect(): Promise<boolean> {
    if (this.connected) return true

    try {
      const httpUrl = resolveServerHttpUrl()
      const wsUrl = httpUrl.replace(/^http/, 'ws') + '/jetbrains-rsocket'

      this.client = new RSocketClient({ url: wsUrl })

      // 注册 ServerCall handler（统一 Protobuf 格式）
      // 后端通过 client.call 路由发送，RSocketClient 解码后按 method 分发
      this.client.registerHandler('onThemeChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到主题变化推送 (Protobuf)')
        // params 已经是 ThemeChangedParams 类型
        const theme: IdeTheme = {
          background: params.background,
          foreground: params.foreground,
          borderColor: params.borderColor,
          panelBackground: params.panelBackground,
          textFieldBackground: params.textFieldBackground,
          selectionBackground: params.selectionBackground,
          selectionForeground: params.selectionForeground,
          linkColor: params.linkColor,
          errorColor: params.errorColor,
          warningColor: params.warningColor,
          successColor: params.successColor,
          separatorColor: params.separatorColor,
          hoverBackground: params.hoverBackground,
          accentColor: params.accentColor,
          infoBackground: params.infoBackground,
          codeBackground: params.codeBackground,
          secondaryForeground: params.secondaryForeground,
          fontFamily: params.fontFamily,
          fontSize: params.fontSize,
          editorFontFamily: params.editorFontFamily,
          editorFontSize: params.editorFontSize
        }
        this.themeChangeHandlers.forEach(h => h(theme))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSessionCommand', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到会话命令推送 (Protobuf)')
        // params 已经是 SessionCommandParams 类型
        const command: SessionCommand = {
          type: mapSessionCommandType(params.type),
          sessionId: params.sessionId,
          newName: params.newName,
          locale: params.locale
        }
        console.log('[JetBrainsRSocket] 会话命令:', command)
        this.sessionCommandHandlers.forEach(h => h(command))
        return {} // 返回空响应
      })

      this.client.registerHandler('onSettingsChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到设置变更推送 (Protobuf)')
        // params.settings 包含 IdeSettings
        const settingsData = params.settings || params

        // 默认思考级别列表
        const defaultThinkingLevels: ThinkingLevelConfig[] = [
          { id: 'off', name: 'Off', tokens: 0, isCustom: false },
          { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
          { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
        ]

        const settings: IdeSettings = {
          defaultModelId: settingsData.defaultModelId || '',
          defaultModelName: settingsData.defaultModelName || '',
          defaultBypassPermissions: settingsData.defaultBypassPermissions || false,
          enableUserInteractionMcp: settingsData.enableUserInteractionMcp ?? true,
          enableJetbrainsMcp: settingsData.enableJetbrainsMcp ?? true,
          includePartialMessages: settingsData.includePartialMessages ?? true,
          defaultThinkingLevel: settingsData.defaultThinkingLevel || 'ULTRA',
          defaultThinkingTokens: settingsData.defaultThinkingTokens ?? 8096,
          defaultThinkingLevelId: settingsData.defaultThinkingLevelId || 'ultra',
          thinkingLevels: settingsData.thinkingLevels || defaultThinkingLevels,
          permissionMode: settingsData.permissionMode || 'default'
        }
        console.log('[JetBrainsRSocket] 设置变更:', settings)
        this.settingsChangeHandlers.forEach(h => h(settings))
        return {} // 返回空响应
      })

      this.client.registerHandler('onActiveFileChanged', async (params: any) => {
        console.log('[JetBrainsRSocket] 收到活跃文件变更推送 (Protobuf)')
        // params 是 ActiveFileChangedNotify 类型
        let activeFile: ActiveFileInfo | null = null
        if (params.hasActiveFile) {
          activeFile = {
            path: params.path || '',
            relativePath: params.relativePath || '',
            name: params.name || '',
            line: params.line || undefined,
            column: params.column || undefined,
            hasSelection: params.hasSelection || false,
            startLine: params.startLine || undefined,
            startColumn: params.startColumn || undefined,
            endLine: params.endLine || undefined,
            endColumn: params.endColumn || undefined,
            selectedContent: params.selectedContent || undefined
          }
          console.log('[JetBrainsRSocket] 活跃文件:', activeFile.relativePath,
            activeFile.hasSelection ? `(selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn}, content: ${activeFile.selectedContent?.substring(0, 50)}...)` : '')
        } else {
          console.log('[JetBrainsRSocket] 无活跃文件')
        }
        this.activeFileChangeHandlers.forEach(h => h(activeFile))
        return {} // 返回空响应
      })

      await this.client.connect()
      this.connected = true
      console.log('[JetBrainsRSocket] Connected')
      return true
    } catch (error) {
      console.error('[JetBrainsRSocket] Connection failed:', error)
      return false
    }
  }

  /**
   * 断开连接
   */
  disconnect(): void {
    if (this.client) {
      this.client.disconnect()
      this.client = null
      this.connected = false
      console.log('[JetBrainsRSocket] Disconnected')
    }
  }

  /**
   * 检查是否已连接
   */
  isConnected(): boolean {
    return this.connected
  }

  // ========== 前端 → 后端 调用 ==========

  /**
   * 打开文件
   */
  async openFile(request: OpenFileRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeOpenFileRequest(request)
      const response = await this.client.requestResponse('jetbrains.openFile', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Opened file:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to open file:', error)
      return false
    }
  }

  /**
   * 显示 Diff
   */
  async showDiff(request: ShowDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowDiffRequest(request)
      const response = await this.client.requestResponse('jetbrains.showDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show diff:', error)
      return false
    }
  }

  /**
   * 显示多编辑 Diff
   */
  async showMultiEditDiff(request: ShowMultiEditDiffRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMultiEditDiffRequest(request)
      const response = await this.client.requestResponse('jetbrains.showMultiEditDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing multi-edit diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show multi-edit diff:', error)
      return false
    }
  }

  /**
   * 显示编辑预览 Diff（权限请求时使用）
   */
  async showEditPreviewDiff(request: ShowEditPreviewRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowEditPreviewRequest(request)
      const response = await this.client.requestResponse('jetbrains.showEditPreviewDiff', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing edit preview diff for:', request.filePath)
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show edit preview diff:', error)
      return false
    }
  }

  /**
   * 显示 Markdown 内容（计划预览）
   */
  async showMarkdown(request: ShowMarkdownRequest): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeShowMarkdownRequest(request)
      const response = await this.client.requestResponse('jetbrains.showMarkdown', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Showing markdown:', request.title || 'Plan Preview')
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to show markdown:', error)
      return false
    }
  }

  /**
   * 获取主题
   */
  async getTheme(): Promise<IdeTheme | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getTheme', new Uint8Array())
      return decodeThemeResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get theme:', error)
      return null
    }
  }

  /**
   * 获取 IDE 设置
   */
  async getSettings(): Promise<IdeSettings | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getSettings', new Uint8Array())
      return decodeSettingsResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get settings:', error)
      return null
    }
  }

  /**
   * 获取语言设置
   */
  async getLocale(): Promise<string> {
    if (!this.client) return 'en-US'

    try {
      const response = await this.client.requestResponse('jetbrains.getLocale', new Uint8Array())
      return decodeLocaleResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get locale:', error)
      return 'en-US'
    }
  }

  /**
   * 设置语言
   */
  async setLocale(locale: string): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSetLocaleRequest(locale)
      const response = await this.client.requestResponse('jetbrains.setLocale', data)
      const result = decodeOperationResponse(response)
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to set locale:', error)
      return false
    }
  }

  /**
   * 获取项目路径
   */
  async getProjectPath(): Promise<string | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getProjectPath', new Uint8Array())
      return decodeProjectPathResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get project path:', error)
      return null
    }
  }

  /**
   * 获取当前活跃文件
   */
  async getActiveFile(): Promise<ActiveFileInfo | null> {
    if (!this.client) return null

    try {
      const response = await this.client.requestResponse('jetbrains.getActiveFile', new Uint8Array())
      return decodeActiveFileResponse(response)
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to get active file:', error)
      return null
    }
  }

  /**
   * 上报会话状态到后端
   */
  async reportSessionState(state: SessionState): Promise<boolean> {
    if (!this.client) return false

    try {
      const data = encodeSessionState(state)
      const response = await this.client.requestResponse('jetbrains.reportSessionState', data)
      const result = decodeOperationResponse(response)
      if (result.success) {
        console.log('[JetBrainsRSocket] Reported session state:', state.sessions.length, 'sessions')
      }
      return result.success
    } catch (error) {
      console.error('[JetBrainsRSocket] Failed to report session state:', error)
      return false
    }
  }

  // ========== 后端 → 前端 事件监听 ==========

  /**
   * 添加主题变化监听器
   */
  onThemeChange(handler: ThemeChangeHandler): () => void {
    this.themeChangeHandlers.push(handler)
    return () => {
      const index = this.themeChangeHandlers.indexOf(handler)
      if (index >= 0) this.themeChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加会话命令监听器
   */
  onSessionCommand(handler: SessionCommandHandler): () => void {
    this.sessionCommandHandlers.push(handler)
    return () => {
      const index = this.sessionCommandHandlers.indexOf(handler)
      if (index >= 0) this.sessionCommandHandlers.splice(index, 1)
    }
  }

  /**
   * 添加设置变更监听器
   */
  onSettingsChange(handler: SettingsChangeHandler): () => void {
    this.settingsChangeHandlers.push(handler)
    return () => {
      const index = this.settingsChangeHandlers.indexOf(handler)
      if (index >= 0) this.settingsChangeHandlers.splice(index, 1)
    }
  }

  /**
   * 添加活跃文件变更监听器
   */
  onActiveFileChange(handler: ActiveFileChangeHandler): () => void {
    this.activeFileChangeHandlers.push(handler)
    return () => {
      const index = this.activeFileChangeHandlers.indexOf(handler)
      if (index >= 0) this.activeFileChangeHandlers.splice(index, 1)
    }
  }
}

// ========== 单例导出 ==========

export const jetbrainsRSocket = new JetBrainsRSocketService()

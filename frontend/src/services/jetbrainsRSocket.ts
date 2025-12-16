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
import type {
  OpenFileRequest,
  ShowDiffRequest,
  ShowMultiEditDiffRequest,
  ShowEditPreviewRequest,
  ShowMarkdownRequest,
  EditOperation
} from './jetbrainsApi'

// ========== Protobuf 轻量编解码 ==========

/**
 * 手写 Protobuf 编码工具
 * 避免生成完整的 proto 定义
 */
const ProtoEncoder = {
  /**
   * 写入 varint
   */
  writeVarint(buffer: number[], value: number): void {
    let v = value >>> 0
    while (v > 0x7f) {
      buffer.push((v & 0x7f) | 0x80)
      v >>>= 7
    }
    buffer.push(v)
  },

  /**
   * 写入字符串字段（field_number << 3 | 2）
   */
  writeString(buffer: number[], fieldNumber: number, value: string): void {
    const tag = (fieldNumber << 3) | 2
    buffer.push(tag)
    const bytes = new TextEncoder().encode(value)
    this.writeVarint(buffer, bytes.length)
    bytes.forEach(b => buffer.push(b))
  },

  /**
   * 写入 int32 字段（field_number << 3 | 0）
   */
  writeInt32(buffer: number[], fieldNumber: number, value: number): void {
    const tag = (fieldNumber << 3) | 0
    buffer.push(tag)
    this.writeVarint(buffer, value)
  },

  /**
   * 写入 bool 字段
   */
  writeBool(buffer: number[], fieldNumber: number, value: boolean): void {
    if (value) {
      const tag = (fieldNumber << 3) | 0
      buffer.push(tag)
      buffer.push(1)
    }
  },

  /**
   * 写入嵌套消息字段
   */
  writeMessage(buffer: number[], fieldNumber: number, messageBytes: number[]): void {
    const tag = (fieldNumber << 3) | 2
    buffer.push(tag)
    this.writeVarint(buffer, messageBytes.length)
    messageBytes.forEach(b => buffer.push(b))
  }
}

/**
 * 编码 JetBrainsOpenFileRequest
 */
function encodeOpenFileRequest(request: OpenFileRequest): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, request.filePath)
  if (request.line !== undefined) ProtoEncoder.writeInt32(buffer, 2, request.line)
  if (request.column !== undefined) ProtoEncoder.writeInt32(buffer, 3, request.column)
  if (request.startOffset !== undefined) ProtoEncoder.writeInt32(buffer, 4, request.startOffset)
  if (request.endOffset !== undefined) ProtoEncoder.writeInt32(buffer, 5, request.endOffset)
  return new Uint8Array(buffer)
}

/**
 * 编码 JetBrainsShowDiffRequest
 */
function encodeShowDiffRequest(request: ShowDiffRequest): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, request.filePath)
  ProtoEncoder.writeString(buffer, 2, request.oldContent)
  ProtoEncoder.writeString(buffer, 3, request.newContent)
  if (request.title) ProtoEncoder.writeString(buffer, 4, request.title)
  return new Uint8Array(buffer)
}

/**
 * 编码单个 EditOperation
 */
function encodeEditOperation(edit: EditOperation): number[] {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, edit.oldString)
  ProtoEncoder.writeString(buffer, 2, edit.newString)
  if (edit.replaceAll) ProtoEncoder.writeBool(buffer, 3, edit.replaceAll)
  return buffer
}

/**
 * 编码 JetBrainsShowMultiEditDiffRequest
 */
function encodeShowMultiEditDiffRequest(request: ShowMultiEditDiffRequest): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, request.filePath)
  // repeated edits = 2
  request.edits.forEach(edit => {
    const editBytes = encodeEditOperation(edit)
    ProtoEncoder.writeMessage(buffer, 2, editBytes)
  })
  if (request.currentContent) ProtoEncoder.writeString(buffer, 3, request.currentContent)
  return new Uint8Array(buffer)
}

/**
 * 编码 JetBrainsShowEditPreviewRequest
 */
function encodeShowEditPreviewRequest(request: ShowEditPreviewRequest): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, request.filePath)
  // repeated edits = 2
  request.edits.forEach(edit => {
    const editBytes = encodeEditOperation(edit)
    ProtoEncoder.writeMessage(buffer, 2, editBytes)
  })
  if (request.title) ProtoEncoder.writeString(buffer, 3, request.title)
  return new Uint8Array(buffer)
}

/**
 * 编码 JetBrainsShowMarkdownRequest
 */
function encodeShowMarkdownRequest(request: ShowMarkdownRequest): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, request.content)
  if (request.title) ProtoEncoder.writeString(buffer, 2, request.title)
  return new Uint8Array(buffer)
}

/**
 * 编码 JetBrainsSetLocaleRequest
 */
function encodeSetLocaleRequest(locale: string): Uint8Array {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, locale)
  return new Uint8Array(buffer)
}

/**
 * 解码 JetBrainsOperationResponse
 */
function decodeOperationResponse(data: Uint8Array): { success: boolean; error?: string } {
  let success = false
  let error: string | undefined

  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 0) {
      // varint
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        value |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      if (fieldNumber === 1) success = value !== 0
    } else if (wireType === 2) {
      // length-delimited
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const stringBytes = data.slice(offset, offset + length)
      offset += length
      if (fieldNumber === 2) error = new TextDecoder().decode(stringBytes)
    }
  }

  return { success, error }
}

/**
 * 解码 IdeThemeProto
 */
function decodeThemeResponse(data: Uint8Array): IdeTheme | null {
  // 解码 JetBrainsGetThemeResponse，其中 theme = 1
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 2 && fieldNumber === 1) {
      // 嵌套消息
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const themeBytes = data.slice(offset, offset + length)
      return decodeIdeTheme(themeBytes)
    }
  }
  return null
}

/**
 * 解码 IdeThemeProto 内部
 */
function decodeIdeTheme(data: Uint8Array): IdeTheme {
  const theme: any = {}
  const fieldNames: Record<number, string> = {
    1: 'background', 2: 'foreground', 3: 'borderColor', 4: 'panelBackground',
    5: 'textFieldBackground', 6: 'selectionBackground', 7: 'selectionForeground',
    8: 'linkColor', 9: 'errorColor', 10: 'warningColor', 11: 'successColor',
    12: 'separatorColor', 13: 'hoverBackground', 14: 'accentColor',
    15: 'infoBackground', 16: 'codeBackground', 17: 'secondaryForeground',
    18: 'fontFamily', 19: 'fontSize', 20: 'editorFontFamily', 21: 'editorFontSize'
  }

  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 0) {
      // varint (fontSize, editorFontSize)
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        value |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const name = fieldNames[fieldNumber]
      if (name) theme[name] = value
    } else if (wireType === 2) {
      // length-delimited (string)
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const stringBytes = data.slice(offset, offset + length)
      offset += length
      const name = fieldNames[fieldNumber]
      if (name) theme[name] = new TextDecoder().decode(stringBytes)
    }
  }

  return theme as IdeTheme
}

/**
 * 解码 JetBrainsGetLocaleResponse
 */
function decodeLocaleResponse(data: Uint8Array): string {
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 2 && fieldNumber === 1) {
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const stringBytes = data.slice(offset, offset + length)
      return new TextDecoder().decode(stringBytes)
    }
  }
  return 'en-US'
}

/**
 * 解码 JetBrainsGetProjectPathResponse
 */
function decodeProjectPathResponse(data: Uint8Array): string {
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 2 && fieldNumber === 1) {
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const stringBytes = data.slice(offset, offset + length)
      return new TextDecoder().decode(stringBytes)
    }
  }
  return ''
}

/**
 * 解码 GetIdeSettingsResponse
 *
 * Proto 定义：
 * message GetIdeSettingsResponse {
 *   IdeSettings settings = 1;
 * }
 * message IdeSettings {
 *   string default_model_id = 1;
 *   string default_model_name = 2;
 *   bool default_bypass_permissions = 3;
 *   bool enable_user_interaction_mcp = 4;
 *   bool enable_jetbrains_mcp = 5;
 * }
 */
function decodeSettingsResponse(data: Uint8Array): IdeSettings | null {
  const settings: Partial<IdeSettings> = {
    defaultModelId: '',
    defaultModelName: '',
    defaultBypassPermissions: false,
    enableUserInteractionMcp: true,
    enableJetbrainsMcp: true,
    includePartialMessages: true
  }

  // 内部解析 IdeSettings 消息
  function parseIdeSettings(settingsBytes: Uint8Array) {
    let offset = 0
    while (offset < settingsBytes.length) {
      const tag = settingsBytes[offset++]
      const fieldNumber = tag >> 3
      const wireType = tag & 0x7

      if (wireType === 0) {
        // varint (bool)
        let value = 0
        let shift = 0
        while (offset < settingsBytes.length) {
          const byte = settingsBytes[offset++]
          value |= (byte & 0x7f) << shift
          if ((byte & 0x80) === 0) break
          shift += 7
        }
        if (fieldNumber === 3) settings.defaultBypassPermissions = value !== 0
        if (fieldNumber === 4) settings.enableUserInteractionMcp = value !== 0
        if (fieldNumber === 5) settings.enableJetbrainsMcp = value !== 0
        if (fieldNumber === 6) settings.includePartialMessages = value !== 0
      } else if (wireType === 2) {
        // length-delimited (string)
        let length = 0
        let shift = 0
        while (offset < settingsBytes.length) {
          const byte = settingsBytes[offset++]
          length |= (byte & 0x7f) << shift
          if ((byte & 0x80) === 0) break
          shift += 7
        }
        const stringBytes = settingsBytes.slice(offset, offset + length)
        offset += length
        const str = new TextDecoder().decode(stringBytes)
        if (fieldNumber === 1) settings.defaultModelId = str
        if (fieldNumber === 2) settings.defaultModelName = str
      }
    }
  }

  // 解析外层 GetIdeSettingsResponse
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 2 && fieldNumber === 1) {
      // nested message (IdeSettings)
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const settingsBytes = data.slice(offset, offset + length)
      offset += length
      parseIdeSettings(settingsBytes)
    }
  }

  return settings as IdeSettings
}

/**
 * 编码单个 SessionSummary
 */
function encodeSessionSummary(session: SessionSummary): number[] {
  const buffer: number[] = []
  ProtoEncoder.writeString(buffer, 1, session.id)
  ProtoEncoder.writeString(buffer, 2, session.title)
  if (session.sessionId) ProtoEncoder.writeString(buffer, 3, session.sessionId)
  ProtoEncoder.writeBool(buffer, 4, session.isGenerating)
  ProtoEncoder.writeBool(buffer, 5, session.isConnected)
  ProtoEncoder.writeBool(buffer, 6, session.isConnecting)
  return buffer
}

/**
 * 编码 JetBrainsSessionState
 */
function encodeSessionState(state: SessionState): Uint8Array {
  const buffer: number[] = []
  // repeated sessions = 1
  state.sessions.forEach(session => {
    const sessionBytes = encodeSessionSummary(session)
    ProtoEncoder.writeMessage(buffer, 1, sessionBytes)
  })
  if (state.activeSessionId) ProtoEncoder.writeString(buffer, 2, state.activeSessionId)
  return new Uint8Array(buffer)
}

/**
 * 解码 SessionCommand（从 Protobuf 字节）
 */
function decodeSessionCommandProto(data: Uint8Array): SessionCommand {
  const typeMap: Record<number, SessionCommand['type']> = {
    1: 'switch',
    2: 'create',
    3: 'close',
    4: 'rename',
    5: 'toggleHistory',
    6: 'setLocale',
    7: 'delete',
    8: 'reset'
  }

  let type: SessionCommand['type'] = 'switch'
  let sessionId: string | undefined
  let newName: string | undefined
  let locale: string | undefined

  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNumber = tag >> 3
    const wireType = tag & 0x7

    if (wireType === 0) {
      // varint
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        value |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      if (fieldNumber === 1) {
        type = typeMap[value] || 'switch'
      }
    } else if (wireType === 2) {
      // length-delimited
      let length = 0
      let shift = 0
      while (offset < data.length) {
        const byte = data[offset++]
        length |= (byte & 0x7f) << shift
        if ((byte & 0x80) === 0) break
        shift += 7
      }
      const stringBytes = data.slice(offset, offset + length)
      offset += length
      const stringValue = new TextDecoder().decode(stringBytes)

      switch (fieldNumber) {
        case 2: sessionId = stringValue; break
        case 3: newName = stringValue; break
        case 4: locale = stringValue; break
      }
    }
  }

  return { type, sessionId, newName, locale }
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

// IDE 设置接口
export interface IdeSettings {
  defaultModelId: string
  defaultModelName: string
  defaultBypassPermissions: boolean
  enableUserInteractionMcp: boolean
  enableJetbrainsMcp: boolean
  includePartialMessages: boolean
}

// ========== RSocket 服务 ==========

class JetBrainsRSocketService {
  private client: RSocketClient | null = null
  private themeChangeHandlers: ThemeChangeHandler[] = []
  private sessionCommandHandlers: SessionCommandHandler[] = []
  private settingsChangeHandlers: SettingsChangeHandler[] = []
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
        const settings: IdeSettings = {
          defaultModelId: settingsData.defaultModelId || '',
          defaultModelName: settingsData.defaultModelName || '',
          defaultBypassPermissions: settingsData.defaultBypassPermissions || false,
          enableUserInteractionMcp: settingsData.enableUserInteractionMcp ?? true,
          enableJetbrainsMcp: settingsData.enableJetbrainsMcp ?? true,
          includePartialMessages: settingsData.includePartialMessages ?? true
        }
        console.log('[JetBrainsRSocket] 设置变更:', settings)
        this.settingsChangeHandlers.forEach(h => h(settings))
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
   * 解码会话命令（从 Protobuf 字节）
   * 保留以备将来使用
   */
  private _decodeSessionCommand(data: any): SessionCommand {
    // 如果是 Uint8Array，使用 Protobuf 解码
    if (data instanceof Uint8Array) {
      return decodeSessionCommandProto(data)
    }
    // 如果是 ArrayBuffer 或类似对象，转换为 Uint8Array
    if (data && typeof data === 'object' && !(data instanceof Array)) {
      const bytes = new Uint8Array(Object.values(data))
      return decodeSessionCommandProto(bytes)
    }
    // 回退到简单解析
    const typeMap: Record<number, SessionCommand['type']> = {
      1: 'switch',
      2: 'create',
      3: 'close',
      4: 'rename',
      5: 'toggleHistory',
      6: 'setLocale',
      7: 'delete',
      8: 'reset'
    }
    return {
      type: typeMap[data?.type] || 'switch',
      sessionId: data?.sessionId,
      newName: data?.newName,
      locale: data?.locale
    }
  }
}

// ========== 单例导出 ==========

export const jetbrainsRSocket = new JetBrainsRSocketService()

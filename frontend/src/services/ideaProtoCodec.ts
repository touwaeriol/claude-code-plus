/**
 * IDE integration Protobuf codec (JetBrains proto schema).
 *
 * Extracted from ideaRSocket.ts.
 */

import { create, toBinary, fromBinary } from '@bufbuild/protobuf'
import {
  JetBrainsOpenFileRequestSchema,
  JetBrainsShowDiffRequestSchema,
  JetBrainsShowMultiEditDiffRequestSchema,
  JetBrainsShowEditPreviewRequestSchema,
  JetBrainsShowEditFullDiffRequestSchema,
  JetBrainsShowMarkdownRequestSchema,
  JetBrainsEditOperationSchema,
  JetBrainsOperationResponseSchema,
  JetBrainsGetThemeResponseSchema,
  JetBrainsGetLocaleResponseSchema,
  JetBrainsGetProjectPathResponseSchema,
  JetBrainsSetLocaleRequestSchema,
  JetBrainsSessionStateSchema,
  JetBrainsSessionSummarySchema,
  JetBrainsGetOriginalContentResponseSchema,
  JetBrainsGetFileHistoryContentRequestSchema,
  JetBrainsGetFileHistoryContentResponseSchema,
  JetBrainsRollbackFileRequestSchema,
  JetBrainsRollbackFileResponseSchema
} from '@proto'
import {
  GetIdeSettingsResponseSchema,
  ActiveFileChangedNotifySchema
} from '@proto'
import type {
  OpenFileRequest,
  ShowDiffRequest,
  ShowMultiEditDiffRequest,
  ShowEditPreviewRequest,
  ShowEditFullDiffRequest,
  ShowMarkdownRequest
} from './ideaApi'
import type {
  IdeTheme,
  IdeSettings,
  ThinkingLevelConfig,
  OptionConfig,
  SessionState,
  ActiveFileInfo,
  BatchRollbackItem,
  BatchRollbackEvent,
  RollbackStatus,
  TerminalBackgroundItem,
  TerminalBackgroundEvent,
  TerminalBackgroundStatus
} from './ideaTypes'

// ========== 基础编码工具函数 ==========

/**
 * 编码字段（带 wire type）
 */
export function encodeField(fieldNum: number, wireType: number, data: Uint8Array): Uint8Array {
  const tag = (fieldNum << 3) | wireType
  const tagBytes = encodeVarint(tag)
  const lenBytes = encodeVarint(data.length)
  return concatUint8Arrays([tagBytes, lenBytes, data])
}

/**
 * 编码 varint 字段
 */
export function encodeVarintField(fieldNum: number, value: number): Uint8Array {
  const tag = (fieldNum << 3) | 0 // wire type 0 = varint
  const tagBytes = encodeVarint(tag)
  const valueBytes = encodeVarint(value)
  return concatUint8Arrays([tagBytes, valueBytes])
}

/**
 * 编码 varint
 */
export function encodeVarint(value: number): Uint8Array {
  const bytes: number[] = []
  let v = Math.floor(value)
  while (v > 127) {
    // Avoid bitwise ops on large numbers (timestamps may exceed 32-bit).
    bytes.push((v % 128) | 0x80)
    v = Math.floor(v / 128)
  }
  bytes.push(v % 128)
  return new Uint8Array(bytes)
}

/**
 * 合并 Uint8Array 数组
 */
export function concatUint8Arrays(arrays: Uint8Array[]): Uint8Array {
  const totalLength = arrays.reduce((sum, arr) => sum + arr.length, 0)
  const result = new Uint8Array(totalLength)
  let offset = 0
  for (const arr of arrays) {
    result.set(arr, offset)
    offset += arr.length
  }
  return result
}

// ========== JetBrains API 编码函数 ==========

/**
 * 编码 JetBrainsOpenFileRequest
 */
export function encodeOpenFileRequest(request: OpenFileRequest): Uint8Array {
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
export function encodeShowDiffRequest(request: ShowDiffRequest): Uint8Array {
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
export function encodeShowMultiEditDiffRequest(request: ShowMultiEditDiffRequest): Uint8Array {
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
export function encodeShowEditPreviewRequest(request: ShowEditPreviewRequest): Uint8Array {
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
export function encodeShowMarkdownRequest(request: ShowMarkdownRequest): Uint8Array {
  const proto = create(JetBrainsShowMarkdownRequestSchema, {
    content: request.content,
    title: request.title
  })
  return toBinary(JetBrainsShowMarkdownRequestSchema, proto)
}

/**
 * 编码 JetBrainsShowEditFullDiffRequest
 */
export function encodeShowEditFullDiffRequest(request: ShowEditFullDiffRequest): Uint8Array {
  const proto = create(JetBrainsShowEditFullDiffRequestSchema, {
    filePath: request.filePath,
    oldString: request.oldString,
    newString: request.newString,
    replaceAll: request.replaceAll,
    title: request.title
  })
  return toBinary(JetBrainsShowEditFullDiffRequestSchema, proto)
}

/**
 * 编码 JetBrainsSetLocaleRequest
 */
export function encodeSetLocaleRequest(locale: string): Uint8Array {
  const proto = create(JetBrainsSetLocaleRequestSchema, { locale })
  return toBinary(JetBrainsSetLocaleRequestSchema, proto)
}

/**
 * 编码 JetBrainsGetFileHistoryContentRequest
 */
export function encodeGetFileHistoryContentRequest(filePath: string, beforeTimestamp: number): Uint8Array {
  const proto = create(JetBrainsGetFileHistoryContentRequestSchema, {
    filePath,
    beforeTimestamp: BigInt(beforeTimestamp)
  })
  return toBinary(JetBrainsGetFileHistoryContentRequestSchema, proto)
}

/**
 * 编码 JetBrainsRollbackFileRequest
 */
export function encodeRollbackFileRequest(filePath: string, beforeTimestamp: number): Uint8Array {
  const proto = create(JetBrainsRollbackFileRequestSchema, {
    filePath,
    beforeTimestamp: BigInt(beforeTimestamp)
  })
  return toBinary(JetBrainsRollbackFileRequestSchema, proto)
}

/**
 * 编码 JetBrainsSessionState
 */
export function encodeSessionState(state: SessionState): Uint8Array {
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

// ========== JetBrains API 解码函数 ==========

/**
 * 解码 JetBrainsOperationResponse
 */
export function decodeOperationResponse(data: Uint8Array): { success: boolean; error?: string } {
  const proto = fromBinary(JetBrainsOperationResponseSchema, data)
  return { success: proto.success, error: proto.error || undefined }
}

/**
 * 解码 JetBrainsGetThemeResponse -> IdeTheme
 */
export function decodeThemeResponse(data: Uint8Array): IdeTheme | null {
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
export function decodeLocaleResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetLocaleResponseSchema, data)
  return proto.locale || 'en-US'
}

/**
 * 解码 JetBrainsGetProjectPathResponse
 */
export function decodeProjectPathResponse(data: Uint8Array): string {
  const proto = fromBinary(JetBrainsGetProjectPathResponseSchema, data)
  return proto.projectPath || ''
}

/**
 * 解码 JetBrainsGetOriginalContentResponse
 */
export function decodeGetOriginalContentResponse(data: Uint8Array): { success: boolean; found: boolean; content?: string; error?: string } {
  const proto = fromBinary(JetBrainsGetOriginalContentResponseSchema, data)
  return {
    success: proto.success,
    found: proto.found,
    content: proto.content || undefined,
    error: proto.error || undefined
  }
}

/**
 * 解码 JetBrainsGetFileHistoryContentResponse
 */
export function decodeGetFileHistoryContentResponse(data: Uint8Array): { success: boolean; found: boolean; content?: string; error?: string } {
  const proto = fromBinary(JetBrainsGetFileHistoryContentResponseSchema, data)
  return {
    success: proto.success,
    found: proto.found,
    content: proto.content || undefined,
    error: proto.error || undefined
  }
}

/**
 * 解码 JetBrainsRollbackFileResponse
 */
export function decodeRollbackFileResponse(data: Uint8Array): { success: boolean; error?: string } {
  const proto = fromBinary(JetBrainsRollbackFileResponseSchema, data)
  return {
    success: proto.success,
    error: proto.error || undefined
  }
}

/**
 * 解码 ActiveFileChangedNotify（用于 getActiveFile 响应）
 */
export function decodeActiveFileResponse(data: Uint8Array): ActiveFileInfo | null {
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
export function decodeSettingsResponse(data: Uint8Array): IdeSettings | null {
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
      claudeDefaultAutoCleanupContexts: true,
      codexDefaultAutoCleanupContexts: true,
      enableUserInteractionMcp: true,
      enableJetbrainsMcp: true,
      includePartialMessages: true,
      codexDefaultReasoningEffort: 'medium',
      codexDefaultReasoningSummary: 'auto',
      codexDefaultSandboxMode: 'workspace-write',
      defaultThinkingLevel: 'ULTRA',
      defaultThinkingTokens: 8096,
      defaultThinkingLevelId: 'ultra',
      thinkingLevels: defaultThinkingLevels,
      permissionMode: 'default'
    }
  }

  // 辅助函数：映射 OptionConfig
  const mapOptionConfig = (opt: any): OptionConfig => ({
    id: opt.id || '',
    label: opt.label || '',
    description: opt.description || '',
    isDefault: opt.isDefault ?? false
  })

  return {
    defaultModelId: s.defaultModelId || '',
    defaultModelName: s.defaultModelName || '',
    defaultBypassPermissions: s.defaultBypassPermissions,
    claudeDefaultAutoCleanupContexts: s.claudeDefaultAutoCleanupContexts,
    codexDefaultAutoCleanupContexts: s.codexDefaultAutoCleanupContexts,
    enableUserInteractionMcp: s.enableUserInteractionMcp,
    enableJetbrainsMcp: s.enableJetbrainsMcp,
    includePartialMessages: s.includePartialMessages,
    codexDefaultModelId: s.codexDefaultModelId || undefined,
    codexDefaultReasoningEffort: s.codexDefaultReasoningEffort || undefined,
    codexDefaultReasoningSummary: s.codexDefaultReasoningSummary || undefined,
    codexDefaultSandboxMode: s.codexDefaultSandboxMode || undefined,
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
    permissionMode: s.permissionMode || 'default',
    // 配置选项列表
    codexReasoningEffortOptions: s.codexReasoningEffortOptions?.map(mapOptionConfig) || [],
    codexReasoningSummaryOptions: s.codexReasoningSummaryOptions?.map(mapOptionConfig) || [],
    codexSandboxModeOptions: s.codexSandboxModeOptions?.map(mapOptionConfig) || [],
    permissionModeOptions: s.permissionModeOptions?.map(mapOptionConfig) || []
  }
}

// ========== 批量回滚编解码（手动 Protobuf）==========

/**
 * 手动编码 BatchRollbackRequest (简单 Protobuf 编码)
 * message JetBrainsBatchRollbackRequest { repeated JetBrainsBatchRollbackItem items = 1; }
 * message JetBrainsBatchRollbackItem { string file_path=1; int64 before_timestamp=2; string tool_use_id=3; }
 */
export function encodeBatchRollbackRequest(items: BatchRollbackItem[]): Uint8Array {
  const parts: Uint8Array[] = []
  
  for (const item of items) {
    // 编码单个 item
    const itemParts: Uint8Array[] = []
    
    // field 1: file_path (string)
    const filePathBytes = new TextEncoder().encode(item.filePath)
    itemParts.push(encodeField(1, 2, filePathBytes)) // wire type 2 = length-delimited
    
    // field 2: before_timestamp (int64 as varint)
    itemParts.push(encodeVarintField(2, item.beforeTimestamp))
    
    // field 3: tool_use_id (string)
    const toolUseIdBytes = new TextEncoder().encode(item.toolUseId)
    itemParts.push(encodeField(3, 2, toolUseIdBytes))
    
    // 合并 item 字节
    const itemBytes = concatUint8Arrays(itemParts)
    
    // 将 item 作为嵌套消息添加到 items (field 1)
    parts.push(encodeField(1, 2, itemBytes))
  }
  
  return concatUint8Arrays(parts)
}

/**
 * 手动解码 BatchRollbackEvent
 * message JetBrainsBatchRollbackEvent { string file_path=1; string tool_use_id=2; RollbackStatus status=3; optional string error=4; }
 */
export function decodeBatchRollbackEvent(data: Uint8Array, RollbackStatusEnum: typeof RollbackStatus): BatchRollbackEvent {
  const result: BatchRollbackEvent = {
    filePath: '',
    toolUseId: '',
    status: RollbackStatusEnum.STARTED
  }
  
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNum = tag >> 3
    const wireType = tag & 0x7
    
    if (wireType === 2) {
      // length-delimited (string)
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      const strBytes = data.slice(offset, offset + len)
      offset += len
      const str = new TextDecoder().decode(strBytes)
      
      if (fieldNum === 1) result.filePath = str
      else if (fieldNum === 2) result.toolUseId = str
      else if (fieldNum === 4) result.error = str
    } else if (wireType === 0) {
      // varint
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        value |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      
      if (fieldNum === 3) result.status = value as typeof RollbackStatusEnum[keyof typeof RollbackStatusEnum]
    }
  }
  
  return result
}

// ========== Terminal 后台执行编解码 ==========

/**
 * 编码 TerminalBackgroundRequest
 * message JetBrainsTerminalBackgroundRequest { repeated JetBrainsTerminalBackgroundItem items = 1; }
 * message JetBrainsTerminalBackgroundItem { string session_id=1; string tool_use_id=2; }
 */
export function encodeTerminalBackgroundRequest(items: TerminalBackgroundItem[]): Uint8Array {
  const parts: Uint8Array[] = []
  
  for (const item of items) {
    const itemParts: Uint8Array[] = []
    
    // field 1: session_id (string)
    const sessionIdBytes = new TextEncoder().encode(item.sessionId)
    itemParts.push(encodeField(1, 2, sessionIdBytes))
    
    // field 2: tool_use_id (string)
    const toolUseIdBytes = new TextEncoder().encode(item.toolUseId)
    itemParts.push(encodeField(2, 2, toolUseIdBytes))
    
    const itemBytes = concatUint8Arrays(itemParts)
    parts.push(encodeField(1, 2, itemBytes))
  }
  
  return concatUint8Arrays(parts)
}

/**
 * 解码 TerminalBackgroundEvent
 * message JetBrainsTerminalBackgroundEvent { string session_id=1; string tool_use_id=2; TerminalBackgroundStatus status=3; optional string error=4; }
 */
export function decodeTerminalBackgroundEvent(data: Uint8Array, TerminalBackgroundStatusEnum: typeof TerminalBackgroundStatus): TerminalBackgroundEvent {
  const result: TerminalBackgroundEvent = {
    sessionId: '',
    toolUseId: '',
    status: TerminalBackgroundStatusEnum.STARTED
  }
  
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNum = tag >> 3
    const wireType = tag & 0x7
    
    if (wireType === 2) {
      // length-delimited (string)
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      const strBytes = data.slice(offset, offset + len)
      offset += len
      const str = new TextDecoder().decode(strBytes)
      
      if (fieldNum === 1) result.sessionId = str
      else if (fieldNum === 2) result.toolUseId = str
      else if (fieldNum === 4) result.error = str
    } else if (wireType === 0) {
      // varint
      let value = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        value |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      
      if (fieldNum === 3) result.status = value as typeof TerminalBackgroundStatusEnum[keyof typeof TerminalBackgroundStatusEnum]
    }
  }
  
  return result
}

/**
 * 解码 GetBackgroundableTerminalsResponse
 * message JetBrainsGetBackgroundableTerminalsResponse { bool success=1; repeated JetBrainsBackgroundableTerminal terminals=2; optional string error=3; }
 * message JetBrainsBackgroundableTerminal { string session_id=1; string tool_use_id=2; string command=3; int64 start_time=4; int64 elapsed_ms=5; }
 */
export function decodeGetBackgroundableTerminalsResponse(data: Uint8Array): import('./ideaTypes').BackgroundableTerminal[] {
  const terminals: import('./ideaTypes').BackgroundableTerminal[] = []
  
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNum = tag >> 3
    const wireType = tag & 0x7
    
    if (wireType === 2 && fieldNum === 2) {
      // terminals (repeated message)
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      const terminalBytes = data.slice(offset, offset + len)
      offset += len
      
      // 解码单个 terminal
      const terminal = decodeBackgroundableTerminal(terminalBytes)
      terminals.push(terminal)
    } else if (wireType === 2) {
      // skip other length-delimited fields
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      offset += len
    } else if (wireType === 0) {
      // skip varint
      while (offset < data.length && (data[offset++] & 0x80)) {}
    }
  }
  
  return terminals
}

/**
 * 解码单个 BackgroundableTerminal
 */
function decodeBackgroundableTerminal(data: Uint8Array): import('./ideaTypes').BackgroundableTerminal {
  const result: import('./ideaTypes').BackgroundableTerminal = {
    sessionId: '',
    toolUseId: '',
    command: '',
    startTime: 0,
    elapsedMs: 0
  }
  
  let offset = 0
  while (offset < data.length) {
    const tag = data[offset++]
    const fieldNum = tag >> 3
    const wireType = tag & 0x7
    
    if (wireType === 2) {
      // length-delimited (string)
      let len = 0
      let shift = 0
      while (offset < data.length) {
        const b = data[offset++]
        len |= (b & 0x7f) << shift
        if (!(b & 0x80)) break
        shift += 7
      }
      const strBytes = data.slice(offset, offset + len)
      offset += len
      const str = new TextDecoder().decode(strBytes)
      
      if (fieldNum === 1) result.sessionId = str
      else if (fieldNum === 2) result.toolUseId = str
      else if (fieldNum === 3) result.command = str
    } else if (wireType === 0) {
      // varint
      let value = 0
      let multiplier = 1
      while (offset < data.length) {
        const b = data[offset++]
        value += (b & 0x7f) * multiplier
        if (!(b & 0x80)) break
        multiplier *= 128
      }
      
      if (fieldNum === 4) result.startTime = value
      else if (fieldNum === 5) result.elapsedMs = value
    }
  }
  
  return result
}

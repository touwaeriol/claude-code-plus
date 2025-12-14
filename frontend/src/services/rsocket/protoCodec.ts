/**
 * Protobuf 编解码工具
 *
 * 封装 @bufbuild/protobuf 的序列化/反序列化操作
 */

import { create, toBinary, fromBinary } from '@bufbuild/protobuf'
import {
  ConnectOptionsSchema,
  ConnectResultSchema,
  QueryRequestSchema,
  QueryWithContentRequestSchema,
  SetModelRequestSchema,
  SetPermissionModeRequestSchema,
  LoadHistoryRequestSchema,
  GetHistoryMetadataRequestSchema,
  StatusResultSchema,
  SetModelResultSchema,
  SetPermissionModeResultSchema,
  HistorySchema,
  HistorySessionsResultSchema,
  HistoryMetadataSchema,
  HistoryResultSchema,
  RpcMessageSchema,
  ContentBlockSchema,
  TextBlockSchema,
  ThinkingBlockSchema,
  ImageBlockSchema,
  ImageSourceSchema,
  // ServerCall 相关
  ServerCallRequestSchema,
  ServerCallResponseSchema,
  AskUserQuestionResponseSchema,
  RequestPermissionResponseSchema,
  UserAnswerItemSchema,
  PermissionUpdateSchema,
  PermissionRuleValueSchema,
  // JetBrains 集成（统一 ServerCall）
  type SessionCommandNotify,
  type ThemeChangedNotify,
  SessionCommandType,
  type ContentBlock,
  type AskUserQuestionRequest,
  type RequestPermissionRequest,
  Provider,
  PermissionMode,
  SandboxMode,
  SessionStatus,
  ContentStatus,
  PermissionBehavior,
  PermissionUpdateType,
  PermissionUpdateDestination
} from '@/proto/ai_agent_rpc_pb'
import type {
  RpcConnectOptions,
  RpcConnectResult,
  RpcPermissionMode,
  RpcContentBlock,
  RpcMessage as RpcMessageType,
  RpcSessionStatus,
  RpcContentStatus
} from '@/types/rpc'

// 重新导出 Protobuf 枚举
export { Provider, PermissionMode, SandboxMode, SessionCommandType }

/**
 * RPC 类型到 Protobuf 类型的转换
 */
export const ProtoCodec = {
  // ==================== 编码（RPC -> Protobuf bytes）====================

  /**
   * 编码 ConnectOptions
   */
  encodeConnectOptions(options?: RpcConnectOptions): Uint8Array | undefined {
    if (!options) return undefined

    const proto = create(ConnectOptionsSchema, {
      provider: options.provider ? mapProviderToProto(options.provider) : undefined,
      model: options.model,
      systemPrompt: options.systemPrompt,
      initialPrompt: options.initialPrompt,
      sessionId: options.sessionId,
      resumeSessionId: options.resumeSessionId,
      metadata: options.metadata || {},
      permissionMode: options.permissionMode ? mapPermissionModeToProto(options.permissionMode) : undefined,
      dangerouslySkipPermissions: options.dangerouslySkipPermissions,
      allowDangerouslySkipPermissions: options.allowDangerouslySkipPermissions,
      includePartialMessages: options.includePartialMessages,
      continueConversation: options.continueConversation,
      thinkingEnabled: options.thinkingEnabled,
      baseUrl: options.baseUrl,
      apiKey: options.apiKey,
      sandboxMode: options.sandboxMode ? mapSandboxModeToProto(options.sandboxMode) : undefined
    })

    return toBinary(ConnectOptionsSchema, proto)
  },

  /**
   * 编码 QueryRequest
   */
  encodeQueryRequest(message: string): Uint8Array {
    const proto = create(QueryRequestSchema, { message })
    return toBinary(QueryRequestSchema, proto)
  },

  /**
   * 编码 QueryWithContentRequest
   */
  encodeQueryWithContentRequest(content: RpcContentBlock[]): Uint8Array {
    const proto = create(QueryWithContentRequestSchema, {
      content: content.map(mapContentBlockToProto)
    })
    return toBinary(QueryWithContentRequestSchema, proto)
  },

  /**
   * 编码 SetModelRequest
   */
  encodeSetModelRequest(model: string): Uint8Array {
    const proto = create(SetModelRequestSchema, { model })
    return toBinary(SetModelRequestSchema, proto)
  },

  /**
   * 编码 SetPermissionModeRequest
   */
  encodeSetPermissionModeRequest(mode: RpcPermissionMode): Uint8Array {
    const proto = create(SetPermissionModeRequestSchema, {
      mode: mapPermissionModeToProto(mode)
    })
    return toBinary(SetPermissionModeRequestSchema, proto)
  },

  /**
   * 编码 GetHistorySessionsRequest
   */
  encodeGetHistorySessionsRequest(maxResults: number, offset = 0): Uint8Array {
    // 手写轻量编码，避免前端重新生成 proto 定义
    const buffer: number[] = []
    const writeVarint = (value: number) => {
      let v = value >>> 0
      while (v > 0x7f) {
        buffer.push((v & 0x7f) | 0x80)
        v >>>= 7
      }
      buffer.push(v)
    }
    const writeField = (tag: number, value: number) => {
      buffer.push(tag)
      writeVarint(value)
    }
    writeField(8, maxResults)
    if (offset > 0) {
      writeField(16, offset)
    }
    return new Uint8Array(buffer)
  },

  /**
   * 编码 LoadHistoryRequest
   */
  encodeLoadHistoryRequest(params: { sessionId?: string; projectPath?: string; offset?: number; limit?: number }): Uint8Array {
    const request = create(LoadHistoryRequestSchema, {
      sessionId: params.sessionId ?? '',
      projectPath: params.projectPath ?? '',
      offset: params.offset ?? 0,
      limit: params.limit ?? 0
    })
    return toBinary(LoadHistoryRequestSchema, request)
  },

  /**
   * 编码 GetHistoryMetadataRequest
   */
  encodeGetHistoryMetadataRequest(params: { sessionId?: string; projectPath?: string }): Uint8Array {
    const request = create(GetHistoryMetadataRequestSchema, {
      sessionId: params.sessionId,
      projectPath: params.projectPath
    })
    return toBinary(GetHistoryMetadataRequestSchema, request)
  },

  // ==================== 解码（Protobuf bytes -> RPC）====================

  /**
   * 解码 ConnectResult
   */
  decodeConnectResult(data: Uint8Array): RpcConnectResult {
    const proto = fromBinary(ConnectResultSchema, data)
    return {
      sessionId: proto.sessionId,
      provider: mapProviderFromProto(proto.provider),
      status: mapSessionStatusFromProto(proto.status),
      model: proto.model,
      capabilities: proto.capabilities ? {
        canInterrupt: proto.capabilities.canInterrupt,
        canSwitchModel: proto.capabilities.canSwitchModel,
        canSwitchPermissionMode: proto.capabilities.canSwitchPermissionMode,
        supportedPermissionModes: proto.capabilities.supportedPermissionModes.map(mapPermissionModeFromProto),
        canSkipPermissions: proto.capabilities.canSkipPermissions,
        canSendRichContent: proto.capabilities.canSendRichContent,
        canThink: proto.capabilities.canThink,
        canResumeSession: proto.capabilities.canResumeSession
      } : undefined,
      cwd: proto.cwd
    }
  },

  /**
   * 解码 StatusResult
   */
  decodeStatusResult(data: Uint8Array): { status: RpcSessionStatus } {
    const proto = fromBinary(StatusResultSchema, data)
    return { status: mapSessionStatusFromProto(proto.status) }
  },

  /**
   * 解码 SetModelResult
   */
  decodeSetModelResult(data: Uint8Array): { model: string } {
    const proto = fromBinary(SetModelResultSchema, data)
    return { model: proto.model }
  },

  /**
   * 解码 SetPermissionModeResult
   */
  decodeSetPermissionModeResult(data: Uint8Array): { mode: RpcPermissionMode; success: boolean } {
    const proto = fromBinary(SetPermissionModeResultSchema, data)
    return {
      mode: mapPermissionModeFromProto(proto.mode),
      success: proto.success
    }
  },

  /**
   * 解码 History
   */
  decodeHistory(data: Uint8Array): { messages: RpcMessageType[] } {
    const proto = fromBinary(HistorySchema, data)
    return {
      messages: proto.messages.map(mapRpcMessageFromProto)
    }
  },

  /**
   * 解码 HistorySessionsResult
   */
  decodeHistorySessionsResult(data: Uint8Array): { sessions: any[] } {
    const proto = fromBinary(HistorySessionsResultSchema, data)
    return {
      sessions: proto.sessions.map(s => ({
        sessionId: s.sessionId,
        firstUserMessage: s.firstUserMessage,
        timestamp: Number(s.timestamp),
        messageCount: s.messageCount,
        projectPath: s.projectPath,
        customTitle: s.customTitle || undefined
      }))
    }
  },

  /**
   * 解码历史会话元数据
   */
  decodeHistoryMetadata(data: Uint8Array): { totalLines: number; sessionId: string; projectPath: string; customTitle?: string } {
    const proto = fromBinary(HistoryMetadataSchema, data)
    return {
      totalLines: proto.totalLines,
      sessionId: proto.sessionId,
      projectPath: proto.projectPath,
      customTitle: proto.customTitle || undefined
    }
  },

  /**
   * 解码历史加载结果（分页查询）
   */
  decodeHistoryResult(data: Uint8Array): { messages: RpcMessageType[]; offset: number; count: number; availableCount: number } {
    const proto = fromBinary(HistoryResultSchema, data)
    return {
      messages: proto.messages.map(mapRpcMessageFromProto),
      offset: proto.offset,
      count: proto.count,
      availableCount: proto.availableCount
    }
  },

  /**
   * 解码 RpcMessage（流式事件）
   */
  decodeRpcMessage(data: Uint8Array): RpcMessageType {
    const proto = fromBinary(RpcMessageSchema, data)
    return mapRpcMessageFromProto(proto)
  }
}

// ==================== 辅助映射函数 ====================

function mapProviderToProto(provider: string): Provider {
  switch (provider) {
    case 'claude': return Provider.CLAUDE
    case 'codex': return Provider.CODEX
    default: return Provider.UNSPECIFIED
  }
}

function mapProviderFromProto(provider: Provider): 'claude' | 'codex' {
  switch (provider) {
    case Provider.CLAUDE: return 'claude'
    case Provider.CODEX: return 'codex'
    default: return 'claude'
  }
}

function mapPermissionModeToProto(mode: RpcPermissionMode): PermissionMode {
  switch (mode) {
    case 'default': return PermissionMode.DEFAULT
    case 'bypassPermissions': return PermissionMode.BYPASS_PERMISSIONS
    case 'acceptEdits': return PermissionMode.ACCEPT_EDITS
    case 'plan': return PermissionMode.PLAN
    case 'dontAsk': return PermissionMode.DONT_ASK
    default: return PermissionMode.DEFAULT
  }
}

function mapPermissionModeFromProto(mode: PermissionMode): RpcPermissionMode {
  switch (mode) {
    case PermissionMode.DEFAULT: return 'default'
    case PermissionMode.BYPASS_PERMISSIONS: return 'bypassPermissions'
    case PermissionMode.ACCEPT_EDITS: return 'acceptEdits'
    case PermissionMode.PLAN: return 'plan'
    case PermissionMode.DONT_ASK: return 'dontAsk'
    default: return 'default'
  }
}

function mapSandboxModeToProto(mode: string): SandboxMode {
  switch (mode) {
    case 'read-only': return SandboxMode.READ_ONLY
    case 'workspace-write': return SandboxMode.WORKSPACE_WRITE
    case 'danger-full-access': return SandboxMode.DANGER_FULL_ACCESS
    default: return SandboxMode.READ_ONLY
  }
}

function mapSessionStatusFromProto(status: SessionStatus): RpcSessionStatus {
  switch (status) {
    case SessionStatus.CONNECTED: return 'connected'
    case SessionStatus.DISCONNECTED: return 'disconnected'
    case SessionStatus.INTERRUPTED: return 'interrupted'
    case SessionStatus.MODEL_CHANGED: return 'model_changed'
    default: return 'disconnected'
  }
}

function mapContentStatusFromProto(status: ContentStatus): RpcContentStatus {
  switch (status) {
    case ContentStatus.IN_PROGRESS: return 'in_progress'
    case ContentStatus.COMPLETED: return 'completed'
    case ContentStatus.FAILED: return 'failed'
    default: return 'in_progress'
  }
}

function mapContentBlockToProto(block: RpcContentBlock): ContentBlock {
  const proto = create(ContentBlockSchema)

  switch (block.type) {
    case 'text':
      proto.block = {
        case: 'text',
        value: create(TextBlockSchema, { text: block.text || '' })
      }
      break
    case 'thinking':
      proto.block = {
        case: 'thinking',
        value: create(ThinkingBlockSchema, {
          thinking: block.thinking || '',
          signature: block.signature
        })
      }
      break
    case 'image':
      proto.block = {
        case: 'image',
        value: create(ImageBlockSchema, {
          source: create(ImageSourceSchema, {
            type: block.source?.type || 'base64',
            mediaType: block.source?.media_type || 'image/png',
            data: block.source?.data,
            url: block.source?.url
          })
        })
      }
      break
    default:
      proto.block = {
        case: 'text',
        value: create(TextBlockSchema, { text: '' })
      }
  }

  return proto
}

function mapRpcMessageFromProto(proto: any): RpcMessageType {
  const provider = mapProviderFromProto(proto.provider)

  switch (proto.message?.case) {
    case 'user':
      return {
        type: 'user',
        provider,
        message: {
          content: proto.message.value.message?.content.map(mapContentBlockFromProto) || []
        },
        parentToolUseId: proto.message.value.parentToolUseId,
        isReplay: proto.message.value.isReplay
      } as any

    case 'assistant':
      return {
        type: 'assistant',
        provider,
        message: {
          content: proto.message.value.message?.content.map(mapContentBlockFromProto) || []
        },
        id: proto.message.value.id,
        parentToolUseId: proto.message.value.parentToolUseId
      } as any

    case 'result':
      return {
        type: 'result',
        provider,
        subtype: proto.message.value.subtype,
        duration_ms: proto.message.value.durationMs ? Number(proto.message.value.durationMs) : undefined,
        duration_api_ms: proto.message.value.durationApiMs ? Number(proto.message.value.durationApiMs) : undefined,
        is_error: proto.message.value.isError,
        num_turns: proto.message.value.numTurns,
        session_id: proto.message.value.sessionId,
        total_cost_usd: proto.message.value.totalCostUsd,
        result: proto.message.value.result
      } as any

    case 'streamEvent':
      return mapStreamEventFromProto(proto.message.value, provider)

    case 'error':
      return {
        type: 'error',
        provider,
        message: proto.message.value.errorMessage
      } as any

    case 'statusSystem':
      return {
        type: 'status_system',
        provider,
        subtype: proto.message.value.subtype,
        status: proto.message.value.status,
        session_id: proto.message.value.sessionId
      } as any

    case 'compactBoundary':
      return {
        type: 'compact_boundary',
        provider,
        subtype: proto.message.value.subtype,
        session_id: proto.message.value.sessionId,
        compact_metadata: proto.message.value.compactMetadata ? {
          trigger: proto.message.value.compactMetadata.trigger,
          pre_tokens: proto.message.value.compactMetadata.preTokens
        } : undefined
      } as any

    default:
      return { type: 'unknown', provider } as any
  }
}

function mapStreamEventFromProto(proto: any, provider: 'claude' | 'codex'): RpcMessageType {
  const base = {
    type: 'stream_event' as const,
    provider,
    uuid: proto.uuid,
    session_id: proto.sessionId,
    parentToolUseId: proto.parentToolUseId
  }

  // proto.event 是 StreamEventData，其中的 oneof event 字段包含 case 和 value
  const eventData = proto.event
  if (!eventData) {
    console.warn('[protoCodec] stream_event has no event field:', proto)
    return { ...base, event: { type: 'unknown' } } as any
  }

  // StreamEventData.event 是 oneof 字段
  const event = eventData.event
  if (!event) {
    console.warn('[protoCodec] StreamEventData has no event oneof:', eventData)
    return { ...base, event: { type: 'unknown' } } as any
  }

  switch (event.case) {
    case 'messageStart':
      return {
        ...base,
        event: {
          type: 'message_start',
          message: event.value.messageInfo ? {
            id: event.value.messageInfo.id,
            model: event.value.messageInfo.model,
            content: event.value.messageInfo.content?.map(mapContentBlockFromProto) || []
          } : undefined
        }
      } as any

    case 'contentBlockStart':
      return {
        ...base,
        event: {
          type: 'content_block_start',
          index: event.value.index,
          content_block: mapContentBlockFromProto(event.value.contentBlock)
        }
      } as any

    case 'contentBlockDelta':
      return {
        ...base,
        event: {
          type: 'content_block_delta',
          index: event.value.index,
          delta: mapDeltaFromProto(event.value.delta)
        }
      } as any

    case 'contentBlockStop':
      return {
        ...base,
        event: {
          type: 'content_block_stop',
          index: event.value.index
        }
      } as any

    case 'messageDelta':
      return {
        ...base,
        event: {
          type: 'message_delta',
          usage: event.value.usage ? {
            input_tokens: event.value.usage.inputTokens,
            output_tokens: event.value.usage.outputTokens
          } : undefined
        }
      } as any

    case 'messageStop':
      return {
        ...base,
        event: { type: 'message_stop' }
      } as any

    default:
      console.warn('[protoCodec] unknown stream_event.event.case:', event.case, event)
      return { ...base, event: { type: 'unknown' } } as any
  }
}

function mapContentBlockFromProto(proto: ContentBlock): RpcContentBlock {
  if (!proto.block) {
    return { type: 'text', text: '' }
  }

  switch (proto.block.case) {
    case 'text':
      return { type: 'text', text: proto.block.value.text }

    case 'thinking':
      return {
        type: 'thinking',
        thinking: proto.block.value.thinking,
        signature: proto.block.value.signature
      }

    case 'toolUse':
      return {
        type: 'tool_use',
        id: proto.block.value.id,
        toolName: proto.block.value.toolName,
        toolType: proto.block.value.toolType,
        input: proto.block.value.inputJson ? JSON.parse(new TextDecoder().decode(proto.block.value.inputJson)) : undefined,
        status: mapContentStatusFromProto(proto.block.value.status)
      }

    case 'toolResult':
      return {
        type: 'tool_result',
        tool_use_id: proto.block.value.toolUseId,
        content: proto.block.value.contentJson ? JSON.parse(new TextDecoder().decode(proto.block.value.contentJson)) : undefined,
        is_error: proto.block.value.isError,
        agent_id: proto.block.value.agentId
      }

    case 'image':
      return {
        type: 'image',
        source: {
          type: proto.block.value.source?.type || 'base64',
          media_type: proto.block.value.source?.mediaType || 'image/png',
          data: proto.block.value.source?.data,
          url: proto.block.value.source?.url
        }
      }

    case 'commandExecution':
      return {
        type: 'command_execution',
        command: proto.block.value.command,
        output: proto.block.value.output,
        exitCode: proto.block.value.exitCode,
        status: mapContentStatusFromProto(proto.block.value.status)
      }

    case 'error':
      return {
        type: 'error',
        message: proto.block.value.message
      }

    default:
      return { type: 'text', text: '' }
  }
}

function mapDeltaFromProto(proto: any): any {
  if (!proto?.delta) {
    return { type: 'unknown' }
  }

  switch (proto.delta.case) {
    case 'textDelta':
      return { type: 'text_delta', text: proto.delta.value.text }
    case 'thinkingDelta':
      return { type: 'thinking_delta', thinking: proto.delta.value.thinking }
    case 'inputJsonDelta':
      return { type: 'input_json_delta', partial_json: proto.delta.value.partialJson }
    default:
      return { type: 'unknown' }
  }
}

// ==================== ServerCall 编解码（反向调用）====================

/**
 * 解码后的 ServerCall 请求类型
 */
export interface DecodedServerCallRequest {
  callId: string
  method: string
  params: AskUserQuestionParams | RequestPermissionParams | SessionCommandParams | ThemeChangedParams | unknown
  paramsCase: 'askUserQuestion' | 'requestPermission' | 'sessionCommand' | 'themeChanged' | 'paramsJson' | undefined
}

/**
 * SessionCommand 参数类型
 */
export interface SessionCommandParams {
  type: 'switch' | 'create' | 'close' | 'rename' | 'toggleHistory' | 'setLocale' | 'unspecified'
  sessionId?: string
  newName?: string
  locale?: string
}

/**
 * ThemeChanged 参数类型
 */
export interface ThemeChangedParams {
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

/**
 * AskUserQuestion 参数类型
 */
export interface AskUserQuestionParams {
  questions: Array<{
    question: string
    header?: string
    options: Array<{ label: string; description?: string }>
    multiSelect: boolean
  }>
}

/**
 * RequestPermission 参数类型
 */
export interface RequestPermissionParams {
  toolName: string
  input: unknown
  toolUseId?: string
  permissionSuggestions?: PermissionUpdateParams[]
}

/**
 * PermissionUpdate 参数类型
 */
export interface PermissionUpdateParams {
  type: 'addRules' | 'replaceRules' | 'removeRules' | 'setMode' | 'addDirectories' | 'removeDirectories'
  rules?: Array<{ toolName: string; ruleContent?: string }>
  behavior?: 'allow' | 'deny' | 'ask'
  mode?: RpcPermissionMode
  directories?: string[]
  destination?: 'userSettings' | 'projectSettings' | 'localSettings' | 'session'
}

/**
 * UserAnswerItem 类型
 */
export interface UserAnswerItemType {
  question: string
  header?: string
  answer: string
}

/**
 * PermissionResponse 类型
 */
export interface PermissionResponseType {
  approved: boolean
  permissionUpdates?: PermissionUpdateParams[]
  denyReason?: string
}

/**
 * 解码 ServerCallRequest
 */
export function decodeServerCallRequest(data: Uint8Array): DecodedServerCallRequest {
  const proto = fromBinary(ServerCallRequestSchema, data)

  let params: unknown
  let paramsCase: DecodedServerCallRequest['paramsCase']

  if (proto.params.case === 'askUserQuestion') {
    paramsCase = 'askUserQuestion'
    params = mapAskUserQuestionRequestFromProto(proto.params.value)
  } else if (proto.params.case === 'requestPermission') {
    paramsCase = 'requestPermission'
    params = mapRequestPermissionRequestFromProto(proto.params.value)
  } else if (proto.params.case === 'sessionCommand') {
    paramsCase = 'sessionCommand'
    params = mapSessionCommandFromProto(proto.params.value)
  } else if (proto.params.case === 'themeChanged') {
    paramsCase = 'themeChanged'
    params = mapThemeChangedFromProto(proto.params.value)
  } else if (proto.params.case === 'paramsJson') {
    paramsCase = 'paramsJson'
    try {
      params = JSON.parse(new TextDecoder().decode(proto.params.value))
    } catch {
      params = {}
    }
  } else {
    paramsCase = undefined
    params = {}
  }

  return {
    callId: proto.callId,
    method: proto.method,
    params,
    paramsCase
  }
}

/**
 * 从 Proto 映射 SessionCommandNotify
 */
function mapSessionCommandFromProto(proto: SessionCommandNotify): SessionCommandParams {
  return {
    type: mapSessionCommandTypeFromProto(proto.type),
    sessionId: proto.sessionId,
    newName: proto.newName,
    locale: proto.locale
  }
}

/**
 * 映射 SessionCommandType
 */
function mapSessionCommandTypeFromProto(type: SessionCommandType): SessionCommandParams['type'] {
  switch (type) {
    case SessionCommandType.SESSION_CMD_SWITCH: return 'switch'
    case SessionCommandType.SESSION_CMD_CREATE: return 'create'
    case SessionCommandType.SESSION_CMD_CLOSE: return 'close'
    case SessionCommandType.SESSION_CMD_RENAME: return 'rename'
    case SessionCommandType.SESSION_CMD_TOGGLE_HISTORY: return 'toggleHistory'
    case SessionCommandType.SESSION_CMD_SET_LOCALE: return 'setLocale'
    default: return 'unspecified'
  }
}

/**
 * 从 Proto 映射 ThemeChangedNotify
 */
function mapThemeChangedFromProto(proto: ThemeChangedNotify): ThemeChangedParams {
  return {
    background: proto.background,
    foreground: proto.foreground,
    borderColor: proto.borderColor,
    panelBackground: proto.panelBackground,
    textFieldBackground: proto.textFieldBackground,
    selectionBackground: proto.selectionBackground,
    selectionForeground: proto.selectionForeground,
    linkColor: proto.linkColor,
    errorColor: proto.errorColor,
    warningColor: proto.warningColor,
    successColor: proto.successColor,
    separatorColor: proto.separatorColor,
    hoverBackground: proto.hoverBackground,
    accentColor: proto.accentColor,
    infoBackground: proto.infoBackground,
    codeBackground: proto.codeBackground,
    secondaryForeground: proto.secondaryForeground,
    fontFamily: proto.fontFamily,
    fontSize: proto.fontSize,
    editorFontFamily: proto.editorFontFamily,
    editorFontSize: proto.editorFontSize
  }
}

/**
 * 编码 ServerCallResponse
 */
export function encodeServerCallResponse(
  callId: string,
  method: string,
  success: boolean,
  result?: UserAnswerItemType[] | PermissionResponseType | unknown,
  error?: string
): Uint8Array {
  const response = create(ServerCallResponseSchema, {
    callId,
    success,
    error
  })

  if (success && result !== undefined) {
    if (method === 'AskUserQuestion') {
      response.result = {
        case: 'askUserQuestion',
        value: create(AskUserQuestionResponseSchema, {
          answers: (result as UserAnswerItemType[]).map(item =>
            create(UserAnswerItemSchema, {
              question: item.question,
              header: item.header,
              answer: item.answer
            })
          )
        })
      }
    } else if (method === 'RequestPermission') {
      const permResult = result as PermissionResponseType
      response.result = {
        case: 'requestPermission',
        value: create(RequestPermissionResponseSchema, {
          approved: permResult.approved,
          permissionUpdates: permResult.permissionUpdates?.map(mapPermissionUpdateToProto) || [],
          denyReason: permResult.denyReason
        })
      }
    } else {
      // fallback to JSON
      response.result = {
        case: 'resultJson',
        value: new TextEncoder().encode(JSON.stringify(result))
      }
    }
  }

  return toBinary(ServerCallResponseSchema, response)
}

/**
 * 从 Proto 映射 AskUserQuestionRequest
 */
function mapAskUserQuestionRequestFromProto(proto: AskUserQuestionRequest): AskUserQuestionParams {
  return {
    questions: proto.questions.map(q => ({
      question: q.question,
      header: q.header,
      options: q.options.map(opt => ({
        label: opt.label,
        description: opt.description
      })),
      multiSelect: q.multiSelect
    }))
  }
}

/**
 * 从 Proto 映射 RequestPermissionRequest
 */
function mapRequestPermissionRequestFromProto(proto: RequestPermissionRequest): RequestPermissionParams {
  let input: unknown = {}
  if (proto.inputJson.length > 0) {
    try {
      input = JSON.parse(new TextDecoder().decode(proto.inputJson))
    } catch {
      input = {}
    }
  }

  return {
    toolName: proto.toolName,
    input,
    toolUseId: proto.toolUseId,
    permissionSuggestions: proto.permissionSuggestions.map(mapPermissionUpdateFromProto)
  }
}

/**
 * 从 Proto 映射 PermissionUpdate
 */
function mapPermissionUpdateFromProto(proto: any): PermissionUpdateParams {
  return {
    type: mapPermissionUpdateTypeFromProto(proto.type),
    rules: proto.rules?.map((r: any) => ({
      toolName: r.toolName,
      ruleContent: r.ruleContent
    })),
    behavior: proto.behavior ? mapPermissionBehaviorFromProto(proto.behavior) : undefined,
    mode: proto.mode ? mapPermissionModeFromProto(proto.mode) : undefined,
    directories: proto.directories,
    destination: proto.destination ? mapPermissionDestinationFromProto(proto.destination) : undefined
  }
}

/**
 * 映射 PermissionUpdate 到 Proto
 */
function mapPermissionUpdateToProto(update: PermissionUpdateParams) {
  return create(PermissionUpdateSchema, {
    type: mapPermissionUpdateTypeToProto(update.type),
    rules: update.rules?.map(r =>
      create(PermissionRuleValueSchema, {
        toolName: r.toolName,
        ruleContent: r.ruleContent
      })
    ) || [],
    behavior: update.behavior ? mapPermissionBehaviorToProto(update.behavior) : undefined,
    mode: update.mode ? mapPermissionModeToProto(update.mode) : undefined,
    directories: update.directories || [],
    destination: update.destination ? mapPermissionDestinationToProto(update.destination) : undefined
  })
}

/**
 * PermissionUpdateType 映射
 */
function mapPermissionUpdateTypeFromProto(type: PermissionUpdateType): PermissionUpdateParams['type'] {
  switch (type) {
    case PermissionUpdateType.ADD_RULES: return 'addRules'
    case PermissionUpdateType.REPLACE_RULES: return 'replaceRules'
    case PermissionUpdateType.REMOVE_RULES: return 'removeRules'
    case PermissionUpdateType.SET_MODE: return 'setMode'
    case PermissionUpdateType.ADD_DIRECTORIES: return 'addDirectories'
    case PermissionUpdateType.REMOVE_DIRECTORIES: return 'removeDirectories'
    default: return 'addRules'
  }
}

function mapPermissionUpdateTypeToProto(type: PermissionUpdateParams['type']): PermissionUpdateType {
  switch (type) {
    case 'addRules': return PermissionUpdateType.ADD_RULES
    case 'replaceRules': return PermissionUpdateType.REPLACE_RULES
    case 'removeRules': return PermissionUpdateType.REMOVE_RULES
    case 'setMode': return PermissionUpdateType.SET_MODE
    case 'addDirectories': return PermissionUpdateType.ADD_DIRECTORIES
    case 'removeDirectories': return PermissionUpdateType.REMOVE_DIRECTORIES
    default: return PermissionUpdateType.UNSPECIFIED
  }
}

/**
 * PermissionBehavior 映射
 */
function mapPermissionBehaviorFromProto(behavior: PermissionBehavior): 'allow' | 'deny' | 'ask' {
  switch (behavior) {
    case PermissionBehavior.ALLOW: return 'allow'
    case PermissionBehavior.DENY: return 'deny'
    case PermissionBehavior.ASK: return 'ask'
    default: return 'ask'
  }
}

function mapPermissionBehaviorToProto(behavior: 'allow' | 'deny' | 'ask'): PermissionBehavior {
  switch (behavior) {
    case 'allow': return PermissionBehavior.ALLOW
    case 'deny': return PermissionBehavior.DENY
    case 'ask': return PermissionBehavior.ASK
    default: return PermissionBehavior.UNSPECIFIED
  }
}

/**
 * PermissionUpdateDestination 映射
 */
function mapPermissionDestinationFromProto(dest: PermissionUpdateDestination): PermissionUpdateParams['destination'] {
  switch (dest) {
    case PermissionUpdateDestination.USER_SETTINGS: return 'userSettings'
    case PermissionUpdateDestination.PROJECT_SETTINGS: return 'projectSettings'
    case PermissionUpdateDestination.LOCAL_SETTINGS: return 'localSettings'
    case PermissionUpdateDestination.SESSION: return 'session'
    default: return 'session'
  }
}

function mapPermissionDestinationToProto(dest: NonNullable<PermissionUpdateParams['destination']>): PermissionUpdateDestination {
  switch (dest) {
    case 'userSettings': return PermissionUpdateDestination.USER_SETTINGS
    case 'projectSettings': return PermissionUpdateDestination.PROJECT_SETTINGS
    case 'localSettings': return PermissionUpdateDestination.LOCAL_SETTINGS
    case 'session': return PermissionUpdateDestination.SESSION
    default: return PermissionUpdateDestination.UNSPECIFIED
  }
}

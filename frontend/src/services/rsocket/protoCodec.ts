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
  GetHistorySessionsRequestSchema,
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
  type ContentBlock,
  Provider,
  PermissionMode,
  SandboxMode,
  SessionStatus,
  ContentStatus
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
export { Provider, PermissionMode, SandboxMode }

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
        projectPath: s.projectPath
      }))
    }
  },

  /**
   * 解码历史会话元数据
   */
  decodeHistoryMetadata(data: Uint8Array): { totalLines: number; sessionId: string; projectPath: string } {
    const proto = fromBinary(HistoryMetadataSchema, data)
    return {
      totalLines: proto.totalLines,
      sessionId: proto.sessionId,
      projectPath: proto.projectPath
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

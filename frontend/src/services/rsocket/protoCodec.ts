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
  TruncateHistoryRequestSchema,
  TruncateHistoryResultSchema,
  McpStatusResultSchema,
  ReconnectMcpRequestSchema,
  ReconnectMcpResultSchema,
  GetMcpToolsRequestSchema,
  GetMcpToolsResultSchema,
  StatusResultSchema,
  SetModelResultSchema,
  SetPermissionModeResultSchema,
  HistorySchema,
  HistorySessionsResultSchema,
  HistoryMetadataSchema,
  HistoryResultSchema,
  RpcMessageSchema,
  // ServerCall 相关
  ServerCallRequestSchema,
  ServerCallResponseSchema,
  AskUserQuestionResponseSchema,
  RequestPermissionResponseSchema,
  UserAnswerItemSchema,
  PermissionUpdateSchema,
  PermissionRuleValueSchema,
  type AskUserQuestionRequest,
  type RequestPermissionRequest
} from '@proto'
import type {
  RpcConnectOptions,
  RpcConnectResult,
  RpcPermissionMode,
  RpcContentBlock,
  RpcSessionStatus
} from '@/types/rpc'
import { RpcMessage } from '@/types/rpc/index'

// 导入映射函数
import {
  Provider,
  PermissionMode,
  SandboxMode,
  SessionCommandType,
  mapProviderToProto,
  mapProviderFromProto,
  mapPermissionModeToProto,
  mapPermissionModeFromProto,
  mapSandboxModeToProto,
  mapSessionStatusFromProto,
  mapContentStatusFromProto,
  mapContentBlockToProto,
  mapContentBlockFromProto,
  mapContentBlockFromProtoAsClass,
  mapDeltaFromProto,
  mapRpcMessageFromProto,
  mapSessionCommandFromProto,
  mapSessionCommandTypeFromProto,
  mapThemeChangedFromProto,
  mapActiveFileChangedFromProto,
  mapTerminalTaskUpdateFromProto,
  mapOptionConfigFromProto,
  mapSettingsChangedFromProto,
  mapPermissionUpdateFromProto,
  mapPermissionUpdateTypeFromProto,
  mapPermissionUpdateTypeToProto,
  mapPermissionBehaviorFromProto,
  mapPermissionBehaviorToProto,
  mapPermissionDestinationFromProto,
  mapPermissionDestinationToProto,
  type SessionCommandParams,
  type ThemeChangedParams,
  type ActiveFileChangedParams,
  type TerminalTaskUpdateParams,
  type SettingsChangedParams,
  type OptionConfigItem,
  type PermissionUpdateParams
} from './protoMappers'

// 重新导出 Protobuf 枚举和映射类型
export { Provider, PermissionMode, SandboxMode, SessionCommandType }
export type {
  SessionCommandParams,
  ThemeChangedParams,
  ActiveFileChangedParams,
  TerminalTaskUpdateParams,
  SettingsChangedParams,
  OptionConfigItem,
  PermissionUpdateParams
}

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
      sandboxMode: options.sandboxMode ? mapSandboxModeToProto(options.sandboxMode) : undefined,
      codexReasoningEffort: options.codexReasoningEffort,
      codexReasoningSummary: options.codexReasoningSummary,
      replayUserMessages: options.replayUserMessages,
      connectId: options.connectId
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
   * 编码 SetMaxThinkingTokensRequest
   * 手写轻量编码，避免前端重新生成 proto 定义
   *
   * Proto 定义:
   * message SetMaxThinkingTokensRequest {
   *   optional int32 max_thinking_tokens = 1;  // null = 禁用, 0 = 禁用, 正数 = 设置上限
   * }
   */
  encodeSetMaxThinkingTokensRequest(maxThinkingTokens: number | null): Uint8Array {
    if (maxThinkingTokens === null) {
      // null 值：发送空消息（不设置任何字段）
      return new Uint8Array(0)
    }

    // 手写 protobuf 编码: field 1 (tag=8), varint
    const buffer: number[] = []
    const writeVarint = (value: number) => {
      let v = value >>> 0
      while (v > 0x7f) {
        buffer.push((v & 0x7f) | 0x80)
        v >>>= 7
      }
      buffer.push(v)
    }

    // Field 1, wire type 0 (varint): tag = (1 << 3) | 0 = 8
    buffer.push(8)
    writeVarint(maxThinkingTokens)

    return new Uint8Array(buffer)
  },

  /**
   * 解码 SetMaxThinkingTokensResult
   * 手写轻量解码
   *
   * Proto 定义:
   * message SetMaxThinkingTokensResult {
   *   SessionStatus status = 1;
   *   optional int32 max_thinking_tokens = 2;
   * }
   */
  decodeSetMaxThinkingTokensResult(data: Uint8Array): { status: RpcSessionStatus; maxThinkingTokens: number | null } {
    let status: RpcSessionStatus = 'connected'
    let maxThinkingTokens: number | null = null

    let offset = 0
    while (offset < data.length) {
      const tag = data[offset++]
      const fieldNumber = tag >> 3
      const wireType = tag & 0x07

      if (wireType === 0) {
        // varint
        let value = 0
        let shift = 0
        let b: number
        do {
          b = data[offset++]
          value |= (b & 0x7f) << shift
          shift += 7
        } while (b & 0x80)

        if (fieldNumber === 1) {
          // status: SessionStatus enum
          status = mapSessionStatusFromProto(value)
        } else if (fieldNumber === 2) {
          // max_thinking_tokens
          maxThinkingTokens = value
        }
      }
    }

    return { status, maxThinkingTokens }
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

  /**
   * 编码 TruncateHistoryRequest（用于编辑重发功能）
   */
  encodeTruncateHistoryRequest(params: { sessionId: string; messageUuid: string; projectPath: string }): Uint8Array {
    const request = create(TruncateHistoryRequestSchema, {
      sessionId: params.sessionId,
      messageUuid: params.messageUuid,
      projectPath: params.projectPath
    })
    return toBinary(TruncateHistoryRequestSchema, request)
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
        canResumeSession: proto.capabilities.canResumeSession,
        canRunInBackground: proto.capabilities.canRunInBackground
      } : undefined,
      cwd: proto.cwd,
      connectId: proto.connectId
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
   * 解码 McpStatusResult
   */
  decodeMcpStatusResult(data: Uint8Array): { servers: Array<{ name: string; status: string; serverInfo?: string }> } {
    const proto = fromBinary(McpStatusResultSchema, data)
    return {
      servers: proto.servers.map(s => ({
        name: s.name,
        status: s.status,
        serverInfo: s.serverInfo || undefined
      }))
    }
  },

  /**
   * 编码 ReconnectMcpRequest
   */
  encodeReconnectMcpRequest(serverName: string): Uint8Array {
    const msg = create(ReconnectMcpRequestSchema, { serverName })
    return toBinary(ReconnectMcpRequestSchema, msg)
  },

  /**
   * 解码 ReconnectMcpResult
   */
  decodeReconnectMcpResult(data: Uint8Array): {
    success: boolean
    serverName: string
    status?: string
    toolsCount: number
    error?: string
  } {
    const proto = fromBinary(ReconnectMcpResultSchema, data)
    return {
      success: proto.success,
      serverName: proto.serverName,
      status: proto.status || undefined,
      toolsCount: proto.toolsCount,
      error: proto.error || undefined
    }
  },

  /**
   * 编码 GetMcpToolsRequest
   */
  encodeGetMcpToolsRequest(serverName?: string): Uint8Array {
    const msg = create(GetMcpToolsRequestSchema, { serverName })
    return toBinary(GetMcpToolsRequestSchema, msg)
  },

  /**
   * 解码 GetMcpToolsResult
   */
  decodeGetMcpToolsResult(data: Uint8Array): {
    serverName?: string
    tools: Array<{
      name: string
      description: string
      inputSchema?: string
    }>
    count: number
  } {
    const proto = fromBinary(GetMcpToolsResultSchema, data)
    return {
      serverName: proto.serverName || undefined,
      tools: proto.tools.map(tool => ({
        name: tool.name,
        description: tool.description,
        inputSchema: tool.inputSchema || undefined
      })),
      count: proto.count
    }
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
   * 编码 SetSandboxModeRequest（手写轻量编码）
   *
   * Proto 定义:
   * message SetSandboxModeRequest {
   *   SandboxMode mode = 1;
   * }
   *
   * SandboxMode 枚举:
   *   READ_ONLY = 0
   *   WORKSPACE_WRITE = 1
   *   DANGER_FULL_ACCESS = 2
   */
  encodeSetSandboxModeRequest(mode: string): Uint8Array {
    const protoMode = mapSandboxModeToProto(mode)
    // 手写 protobuf 编码: field 1 (tag=8), varint
    const buffer: number[] = []
    const writeVarint = (value: number) => {
      let v = value >>> 0
      while (v > 0x7f) {
        buffer.push((v & 0x7f) | 0x80)
        v >>>= 7
      }
      buffer.push(v)
    }
    // Field 1, wire type 0 (varint): tag = (1 << 3) | 0 = 8
    buffer.push(8)
    writeVarint(protoMode)
    return new Uint8Array(buffer)
  },

  /**
   * 解码 SetSandboxModeResult（手写轻量解码）
   *
   * Proto 定义:
   * message SetSandboxModeResult {
   *   SandboxMode mode = 1;
   *   bool success = 2;
   * }
   */
  decodeSetSandboxModeResult(data: Uint8Array): { mode: string; success: boolean } {
    let mode = 'read-only'
    let success = true

    // 手写 protobuf 解码
    let offset = 0
    while (offset < data.length) {
      const tag = data[offset++]
      const fieldNumber = tag >> 3
      const wireType = tag & 0x07

      if (wireType === 0) { // varint
        let value = 0
        let shift = 0
        while (offset < data.length) {
          const byte = data[offset++]
          value |= (byte & 0x7f) << shift
          if ((byte & 0x80) === 0) break
          shift += 7
        }

        if (fieldNumber === 1) {
          // mode enum
          switch (value) {
            case 0: mode = 'read-only'; break
            case 1: mode = 'workspace-write'; break
            case 2: mode = 'danger-full-access'; break
          }
        } else if (fieldNumber === 2) {
          // success bool
          success = value !== 0
        }
      }
    }

    return { mode, success }
  },

  /**
   * 解码 History
   * 返回的消息数组中每个元素都是 RpcMessage 类实例，支持 instanceof 判断
   */
  decodeHistory(data: Uint8Array): { messages: RpcMessage[] } {
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
   * 返回的消息数组中每个元素都是 RpcMessage 类实例，支持 instanceof 判断
   */
  decodeHistoryResult(data: Uint8Array): { messages: RpcMessage[]; offset: number; count: number; availableCount: number } {
    const proto = fromBinary(HistoryResultSchema, data)
    return {
      messages: proto.messages.map(mapRpcMessageFromProto),
      offset: proto.offset,
      count: proto.count,
      availableCount: proto.availableCount
    }
  },

  /**
   * 解码 TruncateHistoryResult（用于编辑重发功能）
   */
  decodeTruncateHistoryResult(data: Uint8Array): { success: boolean; remainingLines: number; error?: string } {
    const proto = fromBinary(TruncateHistoryResultSchema, data)
    return {
      success: proto.success,
      remainingLines: proto.remainingLines,
      error: proto.error || undefined
    }
  },

  /**
   * 解码 RpcMessage（流式事件）
   * 返回 RpcMessage 类实例，支持 instanceof 判断
   */
  decodeRpcMessage(data: Uint8Array): RpcMessage {
    const proto = fromBinary(RpcMessageSchema, data)
    return mapRpcMessageFromProto(proto)
  },

  /**
   * 编码 BashRunToBackgroundRequest（手写轻量编码）
   *
   * Proto 定义:
   * message BashRunToBackgroundRequest {
   *   string task_id = 1;
   * }
   */
  encodeBashRunToBackgroundRequest(taskId: string): Uint8Array {
    // 手写 protobuf 编码: field 1, wire type 2 (length-delimited string)
    const encoder = new TextEncoder()
    const taskIdBytes = encoder.encode(taskId)
    const buffer: number[] = []

    // 写入 varint 辅助函数
    const writeVarint = (value: number) => {
      let v = value >>> 0
      while (v > 0x7f) {
        buffer.push((v & 0x7f) | 0x80)
        v >>>= 7
      }
      buffer.push(v)
    }

    // Field 1, wire type 2 (length-delimited): tag = (1 << 3) | 2 = 10
    buffer.push(10)
    writeVarint(taskIdBytes.length)
    for (const byte of taskIdBytes) {
      buffer.push(byte)
    }

    return new Uint8Array(buffer)
  },

  /**
   * 解码 BashBackgroundResult（手写轻量解码）
   *
   * Proto 定义:
   * message BashBackgroundResult {
   *   bool success = 1;
   *   optional string task_id = 2;
   *   optional string command = 3;
   *   optional string error = 4;
   * }
   */
  decodeBashBackgroundResult(data: Uint8Array): {
    success: boolean
    taskId?: string
    command?: string
    error?: string
  } {
    let success = false
    let taskId: string | undefined
    let command: string | undefined
    let error: string | undefined

    const decoder = new TextDecoder()
    let offset = 0

    while (offset < data.length) {
      const tag = data[offset++]
      const fieldNumber = tag >> 3
      const wireType = tag & 0x07

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
          success = value !== 0
        }
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
        const stringValue = decoder.decode(stringBytes)

        if (fieldNumber === 2) {
          taskId = stringValue
        } else if (fieldNumber === 3) {
          command = stringValue
        } else if (fieldNumber === 4) {
          error = stringValue
        }
      }
    }

    return { success, taskId, command, error }
  },

  /**
   * 编码 RunToBackgroundRequest（统一后台运行请求）
   *
   * Proto 定义:
   * message RunToBackgroundRequest {
   *   optional string task_id = 1;
   * }
   */
  encodeRunToBackgroundRequest(taskId?: string): Uint8Array {
    if (!taskId) {
      // 空消息
      return new Uint8Array(0)
    }

    const encoder = new TextEncoder()
    const taskIdBytes = encoder.encode(taskId)
    const buffer: number[] = []

    const writeVarint = (value: number) => {
      let v = value >>> 0
      while (v > 0x7f) {
        buffer.push((v & 0x7f) | 0x80)
        v >>>= 7
      }
      buffer.push(v)
    }

    // Field 1, wire type 2 (length-delimited): tag = (1 << 3) | 2 = 10
    buffer.push(10)
    writeVarint(taskIdBytes.length)
    for (const byte of taskIdBytes) {
      buffer.push(byte)
    }

    return new Uint8Array(buffer)
  },

  /**
   * 解码 UnifiedBackgroundResult（统一后台运行结果）
   *
   * Proto 定义:
   * message UnifiedBackgroundResult {
   *   bool success = 1;
   *   optional bool is_bash = 2;
   *   optional string task_id = 3;
   *   optional string command = 4;
   *   int32 bash_count = 5;
   *   int32 agent_count = 6;
   *   repeated string backgrounded_bash_ids = 7;
   *   repeated string backgrounded_agent_ids = 8;
   *   optional string error = 9;
   * }
   */
  decodeUnifiedBackgroundResult(data: Uint8Array): {
    success: boolean
    isBash?: boolean
    taskId?: string
    command?: string
    bashCount: number
    agentCount: number
    backgroundedBashIds: string[]
    backgroundedAgentIds: string[]
    error?: string
  } {
    let success = false
    let isBash: boolean | undefined
    let taskId: string | undefined
    let command: string | undefined
    let bashCount = 0
    let agentCount = 0
    const backgroundedBashIds: string[] = []
    const backgroundedAgentIds: string[] = []
    let error: string | undefined

    const decoder = new TextDecoder()
    let offset = 0

    while (offset < data.length) {
      const tag = data[offset++]
      const fieldNumber = tag >>> 3
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
          success = value !== 0
        } else if (fieldNumber === 2) {
          isBash = value !== 0
        } else if (fieldNumber === 5) {
          bashCount = value
        } else if (fieldNumber === 6) {
          agentCount = value
        }
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
        const stringValue = decoder.decode(stringBytes)

        if (fieldNumber === 3) {
          taskId = stringValue
        } else if (fieldNumber === 4) {
          command = stringValue
        } else if (fieldNumber === 7) {
          backgroundedBashIds.push(stringValue)
        } else if (fieldNumber === 8) {
          backgroundedAgentIds.push(stringValue)
        } else if (fieldNumber === 9) {
          error = stringValue
        }
      }
    }

    return { success, isBash, taskId, command, bashCount, agentCount, backgroundedBashIds, backgroundedAgentIds, error }
  }
}
// ==================== ServerCall 编解码（反向调用）====================

/**
 * 解码后的 ServerCall 请求类型
 */
export interface DecodedServerCallRequest {
  callId: string
  method: string
  params:
    | AskUserQuestionParams
    | RequestPermissionParams
    | SessionCommandParams
    | ThemeChangedParams
    | ActiveFileChangedParams
    | TerminalTaskUpdateParams
    | SettingsChangedParams
    | unknown
  paramsCase:
    | 'askUserQuestion'
    | 'requestPermission'
    | 'sessionCommand'
    | 'themeChanged'
    | 'activeFileChanged'
    | 'terminalTaskUpdate'
    | 'settingsChanged'
    | 'paramsJson'
    | undefined
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
  } else if (proto.params.case === 'activeFileChanged') {
    paramsCase = 'activeFileChanged'
    params = mapActiveFileChangedFromProto(proto.params.value)
  } else if (proto.params.case === 'terminalTaskUpdate') {
    paramsCase = 'terminalTaskUpdate'
    params = mapTerminalTaskUpdateFromProto(proto.params.value)
  } else if (proto.params.case === 'settingsChanged') {
    paramsCase = 'settingsChanged'
    params = mapSettingsChangedFromProto(proto.params.value)
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

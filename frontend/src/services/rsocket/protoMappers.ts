/**
 * Protobuf 映射函数
 * 
 * 提供 RPC 类型与 Protobuf 类型之间的转换
 */

import { create } from '@bufbuild/protobuf'
import {
  ContentBlockSchema,
  TextBlockSchema,
  ThinkingBlockSchema,
  ImageBlockSchema,
  ImageSourceSchema,
  Provider,
  PermissionMode,
  SandboxMode,
  SessionStatus,
  ContentStatus,
  PermissionBehavior,
  PermissionUpdateType,
  PermissionUpdateDestination,
  SessionCommandType,
  TerminalTaskAction,
  type ContentBlock,
  type SessionCommandNotify,
  type ThemeChangedNotify,
  type ActiveFileChangedNotify,
  type TerminalTaskUpdateNotify,
  type IdeSettingsChangedNotify,
  type IdeSettings,
  type ThinkingLevelConfig as ProtoThinkingLevelConfig,
  type OptionConfig as ProtoOptionConfig
} from '@proto'
import type {
  RpcPermissionMode,
  RpcContentBlock,
  RpcSessionStatus,
  RpcContentStatus
} from '@/types/rpc'

// 导入 RPC 类系统
import {
  RpcMessage,
  RpcUserMessage,
  RpcAssistantMessage,
  RpcResultMessage,
  RpcStreamEventMessage,
  RpcErrorMessage,
  RpcStatusSystemMessage,
  RpcCompactBoundaryMessage,
  RpcSystemInitMessage,
  RpcUnknownMessage,
  ContentBlock as RpcContentBlockClass,
  TextBlock,
  ThinkingBlock,
  ToolUseBlock,
  ToolResultBlock,
  ImageBlock,
  CommandExecutionBlock,
  ErrorBlock,
  UnknownBlock,
  StreamEventData,
  MessageStartEvent,
  ContentBlockStartEvent,
  ContentBlockDeltaEvent,
  ContentBlockStopEvent,
  MessageDeltaEvent,
  MessageStopEvent,
  UnknownEvent
} from '@/types/rpc/index'

// 重新导出枚举以供其他模块使用
export { Provider, PermissionMode, SandboxMode, SessionCommandType }

// ==================== Provider 映射 ====================

export function mapProviderToProto(provider: string): Provider {
  switch (provider) {
    case 'claude': return Provider.CLAUDE
    case 'codex': return Provider.CODEX
    default: return Provider.UNSPECIFIED
  }
}

export function mapProviderFromProto(provider: Provider): 'claude' | 'codex' {
  switch (provider) {
    case Provider.CLAUDE: return 'claude'
    case Provider.CODEX: return 'codex'
    default: return 'claude'
  }
}

// ==================== PermissionMode 映射 ====================

export function mapPermissionModeToProto(mode: RpcPermissionMode): PermissionMode {
  switch (mode) {
    case 'default': return PermissionMode.DEFAULT
    case 'bypassPermissions': return PermissionMode.BYPASS_PERMISSIONS
    case 'acceptEdits': return PermissionMode.ACCEPT_EDITS
    case 'plan': return PermissionMode.PLAN
    default: return PermissionMode.DEFAULT
  }
}

export function mapPermissionModeFromProto(mode: PermissionMode): RpcPermissionMode {
  switch (mode) {
    case PermissionMode.DEFAULT: return 'default'
    case PermissionMode.BYPASS_PERMISSIONS: return 'bypassPermissions'
    case PermissionMode.ACCEPT_EDITS: return 'acceptEdits'
    case PermissionMode.PLAN: return 'plan'
    default: return 'default'
  }
}

// ==================== SandboxMode 映射 ====================

export function mapSandboxModeToProto(mode: string): SandboxMode {
  switch (mode) {
    case 'read-only': return SandboxMode.READ_ONLY
    case 'workspace-write': return SandboxMode.WORKSPACE_WRITE
    case 'full-access': return SandboxMode.DANGER_FULL_ACCESS
    case 'danger-full-access': return SandboxMode.DANGER_FULL_ACCESS
    default: return SandboxMode.READ_ONLY
  }
}

// ==================== Status 映射 ====================

export function mapSessionStatusFromProto(status: SessionStatus): RpcSessionStatus {
  switch (status) {
    case SessionStatus.CONNECTED: return 'connected'
    case SessionStatus.DISCONNECTED: return 'disconnected'
    case SessionStatus.INTERRUPTED: return 'interrupted'
    case SessionStatus.MODEL_CHANGED: return 'model_changed'
    default: return 'disconnected'
  }
}

export function mapContentStatusFromProto(status: ContentStatus): RpcContentStatus {
  switch (status) {
    case ContentStatus.IN_PROGRESS: return 'in_progress'
    case ContentStatus.COMPLETED: return 'completed'
    case ContentStatus.FAILED: return 'failed'
    default: return 'in_progress'
  }
}

// ==================== ContentBlock 映射 ====================

export function mapContentBlockToProto(block: RpcContentBlock): ContentBlock {
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

export function mapContentBlockFromProto(proto: any): any {
  if (!proto?.block) {
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

/**
 * 将 Protobuf ContentBlock 转换为 RPC 类实例
 */
export function mapContentBlockFromProtoAsClass(proto: any): RpcContentBlockClass {
  if (!proto?.block) {
    return new TextBlock({ type: 'text', text: '' })
  }

  switch (proto.block.case) {
    case 'text':
      return new TextBlock({
        type: 'text',
        text: proto.block.value.text
      })

    case 'thinking':
      return new ThinkingBlock({
        type: 'thinking',
        thinking: proto.block.value.thinking,
        signature: proto.block.value.signature
      })

    case 'toolUse': {
      let input: Record<string, unknown> | undefined
      if (proto.block.value.inputJson && proto.block.value.inputJson.length > 0) {
        try {
          input = JSON.parse(new TextDecoder().decode(proto.block.value.inputJson))
        } catch {
          input = undefined
        }
      }
      return new ToolUseBlock({
        type: 'tool_use',
        id: proto.block.value.id,
        toolName: proto.block.value.toolName,
        toolType: proto.block.value.toolType,
        input,
        status: mapContentStatusFromProto(proto.block.value.status)
      })
    }

    case 'toolResult': {
      let content: unknown
      if (proto.block.value.contentJson && proto.block.value.contentJson.length > 0) {
        try {
          content = JSON.parse(new TextDecoder().decode(proto.block.value.contentJson))
        } catch {
          content = undefined
        }
      }
      return new ToolResultBlock({
        type: 'tool_result',
        tool_use_id: proto.block.value.toolUseId,
        content,
        is_error: proto.block.value.isError,
        agent_id: proto.block.value.agentId
      })
    }

    case 'image':
      return new ImageBlock({
        type: 'image',
        source: {
          type: proto.block.value.source?.type || 'base64',
          media_type: proto.block.value.source?.mediaType || 'image/png',
          data: proto.block.value.source?.data,
          url: proto.block.value.source?.url
        }
      })

    case 'commandExecution':
      return new CommandExecutionBlock({
        type: 'command_execution',
        command: proto.block.value.command,
        output: proto.block.value.output,
        exitCode: proto.block.value.exitCode,
        status: mapContentStatusFromProto(proto.block.value.status)
      })

    case 'error':
      return new ErrorBlock({
        type: 'error',
        message: proto.block.value.message
      })

    default:
      return new UnknownBlock({
        type: 'unknown',
        raw: proto.block
      })
  }
}

// ==================== Delta 映射 ====================

export function mapDeltaFromProto(proto: any): any {
  // proto 是 ContentBlockDeltaEvent 类型
  // proto.delta 是 Delta 类型
  // proto.delta.delta 是 oneof 字段（包含 case 和 value）
  if (!proto?.delta?.delta) {
    return { type: 'unknown' }
  }

  const deltaData = proto.delta.delta

  switch (deltaData.case) {
    case 'textDelta':
      return { type: 'text_delta', text: deltaData.value.text }
    case 'thinkingDelta':
      return { type: 'thinking_delta', thinking: deltaData.value.thinking }
    case 'inputJsonDelta':
      return { type: 'input_json_delta', partial_json: deltaData.value.partialJson }
    default:
      return { type: 'unknown' }
  }
}

// ==================== RpcMessage 映射 ====================

export function mapRpcMessageFromProto(proto: any): RpcMessage {
  const provider = mapProviderFromProto(proto.provider)

  switch (proto.message?.case) {
    case 'user':
      return new RpcUserMessage({
        type: 'user',
        provider,
        message: {
          content: proto.message.value.message?.content.map(mapContentBlockFromProtoAsClass) || []
        },
        parentToolUseId: proto.message.value.parentToolUseId,
        isReplay: proto.message.value.isReplay,
        uuid: proto.message.value.uuid
      })

    case 'assistant':
      return new RpcAssistantMessage({
        type: 'assistant',
        provider,
        message: {
          content: proto.message.value.message?.content.map(mapContentBlockFromProtoAsClass) || []
        },
        id: proto.message.value.id,
        parentToolUseId: proto.message.value.parentToolUseId,
        uuid: proto.message.value.uuid
      })

    case 'result':
      return new RpcResultMessage({
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
      })

    case 'streamEvent': {
      const eventProto = proto.message.value
      return new RpcStreamEventMessage({
        type: 'stream_event',
        provider,
        uuid: eventProto.uuid,
        session_id: eventProto.sessionId,
        parentToolUseId: eventProto.parentToolUseId,
        event: mapStreamEventFromProto(eventProto)
      })
    }

    case 'error':
      return new RpcErrorMessage({
        type: 'error',
        provider,
        error: {
          code: proto.message.value.error?.code,
          message: proto.message.value.error?.message
        }
      })

    case 'statusSystem':
      return new RpcStatusSystemMessage({
        type: 'status_system',
        provider,
        status: proto.message.value.status
      })

    case 'compactBoundary':
      return new RpcCompactBoundaryMessage({
        type: 'compact_boundary',
        provider,
        compact_metadata: proto.message.value.compactMetadata
          ? {
              trigger: proto.message.value.compactMetadata.trigger,
              pre_tokens: proto.message.value.compactMetadata.preTokens
            }
          : undefined
      })

    case 'systemInit':
      return new RpcSystemInitMessage({
        type: 'system_init',
        provider,
        session_id: proto.message.value.sessionId,
        cwd: proto.message.value.cwd,
        model: proto.message.value.model,
        permissionMode: proto.message.value.permissionMode
          ? mapPermissionModeFromProto(proto.message.value.permissionMode)
          : undefined,
        apiKeySource: proto.message.value.apiKeySource,
        tools: proto.message.value.tools,
        mcpServers: proto.message.value.mcpServers?.map((s: any) => ({
          name: s.name,
          status: s.status
        }))
      })

    default:
      return new RpcUnknownMessage({
        type: 'unknown',
        provider,
        raw: proto
      })
  }
}

// ==================== StreamEvent 映射 ====================

export function mapStreamEventFromProto(proto: any): StreamEventData {
  // proto 是 StreamEvent 类型
  // proto.event 是 StreamEventData 类型
  // proto.event.event 是 oneof 字段（包含 case 和 value）
  if (!proto.event?.event) {
    return new UnknownEvent({ type: 'unknown', raw: proto })
  }

  const eventData = proto.event.event

  switch (eventData.case) {
    case 'messageStart':
      return new MessageStartEvent({
        type: 'message_start',
        message: {
          id: eventData.value.messageInfo?.id,
          model: eventData.value.messageInfo?.model,
          content: eventData.value.messageInfo?.content?.map(mapContentBlockFromProtoAsClass) || []
        }
      })

    case 'contentBlockStart':
      return new ContentBlockStartEvent({
        type: 'content_block_start',
        index: eventData.value.index,
        content_block: mapContentBlockFromProtoAsClass(eventData.value.contentBlock)
      })

    case 'contentBlockDelta':
      return new ContentBlockDeltaEvent({
        type: 'content_block_delta',
        index: eventData.value.index,
        delta: mapDeltaFromProto(eventData.value)
      })

    case 'contentBlockStop':
      return new ContentBlockStopEvent({
        type: 'content_block_stop',
        index: eventData.value.index
      })

    case 'messageDelta':
      return new MessageDeltaEvent({
        type: 'message_delta',
        delta: (() => {
          const raw = eventData.value.deltaJson
          if (!raw || raw.length === 0) return undefined
          try {
            const jsonText = new TextDecoder().decode(raw)
            return JSON.parse(jsonText)
          } catch {
            return undefined
          }
        })(),
        usage: eventData.value.usage
          ? {
              input_tokens: eventData.value.usage.inputTokens,
              output_tokens: eventData.value.usage.outputTokens,
              cached_input_tokens: eventData.value.usage.cachedInputTokens,
              cache_creation_tokens: eventData.value.usage.cacheCreationTokens,
              cache_read_tokens: eventData.value.usage.cacheReadTokens
            }
          : undefined
      })

    case 'messageStop':
      return new MessageStopEvent({ type: 'message_stop' })

    default:
      return new UnknownEvent({ type: 'unknown', raw: eventData })
  }
}

// ==================== SessionCommand 映射 ====================

export interface SessionCommandParams {
  type: 'switch' | 'create' | 'close' | 'rename' | 'toggleHistory' | 'setLocale' | 'delete' | 'reset' | 'unspecified'
  sessionId?: string
  newName?: string
  locale?: string
}

export function mapSessionCommandFromProto(proto: SessionCommandNotify): SessionCommandParams {
  return {
    type: mapSessionCommandTypeFromProto(proto.type),
    sessionId: proto.sessionId,
    newName: proto.newName,
    locale: proto.locale
  }
}

export function mapSessionCommandTypeFromProto(type: SessionCommandType): SessionCommandParams['type'] {
  switch (type) {
    case SessionCommandType.SESSION_CMD_SWITCH: return 'switch'
    case SessionCommandType.SESSION_CMD_CREATE: return 'create'
    case SessionCommandType.SESSION_CMD_CLOSE: return 'close'
    case SessionCommandType.SESSION_CMD_RENAME: return 'rename'
    case SessionCommandType.SESSION_CMD_TOGGLE_HISTORY: return 'toggleHistory'
    case SessionCommandType.SESSION_CMD_SET_LOCALE: return 'setLocale'
    case SessionCommandType.SESSION_CMD_DELETE: return 'delete'
    case SessionCommandType.SESSION_CMD_RESET: return 'reset'
    default: return 'unspecified'
  }
}

// ==================== Theme 映射 ====================

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

export function mapThemeChangedFromProto(proto: ThemeChangedNotify): ThemeChangedParams {
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

// ==================== ActiveFile 映射 ====================

export interface ActiveFileChangedParams {
  hasActiveFile: boolean
  path?: string
  relativePath?: string
  name?: string
  line?: number
  column?: number
  hasSelection: boolean
  startLine?: number
  startColumn?: number
  endLine?: number
  endColumn?: number
  selectedContent?: string
}

export function mapActiveFileChangedFromProto(proto: ActiveFileChangedNotify): ActiveFileChangedParams {
  return {
    hasActiveFile: proto.hasActiveFile,
    path: proto.path,
    relativePath: proto.relativePath,
    name: proto.name,
    line: proto.line,
    column: proto.column,
    hasSelection: proto.hasSelection,
    startLine: proto.startLine,
    startColumn: proto.startColumn,
    endLine: proto.endLine,
    endColumn: proto.endColumn,
    selectedContent: proto.selectedContent
  }
}

// ==================== TerminalTaskUpdate 映射 ====================

export interface TerminalTaskUpdateParams {
  toolUseId: string
  sessionId: string
  action: TerminalTaskAction
  command: string
  isBackground: boolean
  startTime: number
  elapsedMs?: number
}

export function mapTerminalTaskUpdateFromProto(proto: TerminalTaskUpdateNotify): TerminalTaskUpdateParams {
  return {
    toolUseId: proto.toolUseId,
    sessionId: proto.sessionId,
    action: proto.action,
    command: proto.command,
    isBackground: proto.isBackground,
    startTime: Number(proto.startTime),
    elapsedMs: proto.elapsedMs !== undefined ? Number(proto.elapsedMs) : undefined
  }
}

// ==================== Settings 映射 ====================

export interface OptionConfigItem {
  id: string
  label: string
  description: string
  isDefault: boolean
}

export interface SettingsChangedParams {
  settings: {
    defaultModelId: string
    defaultModelName: string
    defaultBypassPermissions: boolean
    enableUserInteractionMcp: boolean
    enableJetbrainsMcp: boolean
    includePartialMessages: boolean
    codexDefaultModelId?: string
    codexDefaultReasoningEffort?: string
    codexDefaultReasoningSummary?: string
    codexDefaultSandboxMode?: string
    defaultThinkingLevel: string
    defaultThinkingTokens: number
    defaultThinkingLevelId: string
    thinkingLevels: Array<{
      id: string
      name: string
      tokens: number
      isCustom: boolean
    }>
    permissionMode: string
    codexReasoningEffortOptions: OptionConfigItem[]
    codexReasoningSummaryOptions: OptionConfigItem[]
    codexSandboxModeOptions: OptionConfigItem[]
    permissionModeOptions: OptionConfigItem[]
  }
}

export function mapOptionConfigFromProto(opt: ProtoOptionConfig): OptionConfigItem {
  return {
    id: opt.id || '',
    label: opt.label || '',
    description: opt.description || '',
    isDefault: opt.isDefault ?? false
  }
}

export function mapSettingsChangedFromProto(proto: IdeSettingsChangedNotify): SettingsChangedParams {
  const s = proto.settings as IdeSettings | undefined
  const defaultThinkingLevels = [
    { id: 'off', name: 'Off', tokens: 0, isCustom: false },
    { id: 'think', name: 'Think', tokens: 2048, isCustom: false },
    { id: 'ultra', name: 'Ultra', tokens: 8096, isCustom: false }
  ]

  return {
    settings: {
      defaultModelId: s?.defaultModelId || '',
      defaultModelName: s?.defaultModelName || '',
      defaultBypassPermissions: s?.defaultBypassPermissions ?? false,
      enableUserInteractionMcp: s?.enableUserInteractionMcp ?? true,
      enableJetbrainsMcp: s?.enableJetbrainsMcp ?? true,
      includePartialMessages: s?.includePartialMessages ?? true,
      codexDefaultModelId: s?.codexDefaultModelId || '',
      codexDefaultReasoningEffort: s?.codexDefaultReasoningEffort || undefined,
      codexDefaultReasoningSummary: s?.codexDefaultReasoningSummary || undefined,
      codexDefaultSandboxMode: s?.codexDefaultSandboxMode || undefined,
      defaultThinkingLevel: s?.defaultThinkingLevel || 'ULTRA',
      defaultThinkingTokens: s?.defaultThinkingTokens ?? 0,
      defaultThinkingLevelId: s?.defaultThinkingLevelId || 'ultra',
      thinkingLevels: s?.thinkingLevels && s.thinkingLevels.length > 0
        ? s.thinkingLevels.map((level: ProtoThinkingLevelConfig) => ({
            id: level.id,
            name: level.name,
            tokens: level.tokens,
            isCustom: level.isCustom
          }))
        : defaultThinkingLevels,
      permissionMode: s?.permissionMode || 'default',
      codexReasoningEffortOptions: s?.codexReasoningEffortOptions?.map(mapOptionConfigFromProto) || [],
      codexReasoningSummaryOptions: s?.codexReasoningSummaryOptions?.map(mapOptionConfigFromProto) || [],
      codexSandboxModeOptions: s?.codexSandboxModeOptions?.map(mapOptionConfigFromProto) || [],
      permissionModeOptions: s?.permissionModeOptions?.map(mapOptionConfigFromProto) || []
    }
  }
}

// ==================== Permission 映射 ====================

export interface PermissionUpdateParams {
  type: 'addRules' | 'replaceRules' | 'removeRules' | 'setMode' | 'addDirectories' | 'removeDirectories'
  rules?: Array<{ toolName: string; ruleContent?: string }>
  behavior?: 'allow' | 'deny' | 'ask'
  mode?: RpcPermissionMode
  directories?: string[]
  destination?: 'userSettings' | 'projectSettings' | 'localSettings' | 'session'
}

export function mapPermissionUpdateTypeFromProto(type: PermissionUpdateType): PermissionUpdateParams['type'] {
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

export function mapPermissionUpdateTypeToProto(type: PermissionUpdateParams['type']): PermissionUpdateType {
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

export function mapPermissionBehaviorFromProto(behavior: PermissionBehavior): 'allow' | 'deny' | 'ask' {
  switch (behavior) {
    case PermissionBehavior.ALLOW: return 'allow'
    case PermissionBehavior.DENY: return 'deny'
    case PermissionBehavior.ASK: return 'ask'
    default: return 'ask'
  }
}

export function mapPermissionBehaviorToProto(behavior: 'allow' | 'deny' | 'ask'): PermissionBehavior {
  switch (behavior) {
    case 'allow': return PermissionBehavior.ALLOW
    case 'deny': return PermissionBehavior.DENY
    case 'ask': return PermissionBehavior.ASK
    default: return PermissionBehavior.UNSPECIFIED
  }
}

export function mapPermissionDestinationFromProto(dest: PermissionUpdateDestination): PermissionUpdateParams['destination'] {
  switch (dest) {
    case PermissionUpdateDestination.USER_SETTINGS: return 'userSettings'
    case PermissionUpdateDestination.PROJECT_SETTINGS: return 'projectSettings'
    case PermissionUpdateDestination.LOCAL_SETTINGS: return 'localSettings'
    case PermissionUpdateDestination.SESSION: return 'session'
    default: return 'session'
  }
}

export function mapPermissionDestinationToProto(dest: NonNullable<PermissionUpdateParams['destination']>): PermissionUpdateDestination {
  switch (dest) {
    case 'userSettings': return PermissionUpdateDestination.USER_SETTINGS
    case 'projectSettings': return PermissionUpdateDestination.PROJECT_SETTINGS
    case 'localSettings': return PermissionUpdateDestination.LOCAL_SETTINGS
    case 'session': return PermissionUpdateDestination.SESSION
    default: return PermissionUpdateDestination.UNSPECIFIED
  }
}

export function mapPermissionUpdateFromProto(proto: any): PermissionUpdateParams {
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

import type {
  RpcAssistantMessage,
  RpcContentBlock,
  RpcMessage,
  RpcStreamEvent,
  RpcToolUseBlock,
  RpcUserMessage
} from '@/types/rpc'
import type { ContentBlock, Message } from '@/types/message'

type ToolUseWithName = RpcToolUseBlock & { name?: string; toolName?: string; toolType?: string }

function mapToolUse(block: ToolUseWithName): ContentBlock {
  const toolName = block.toolName ?? block.name ?? ''
  return {
    type: 'tool_use',
    id: block.id,
    toolName,
    toolType: block.toolType,
    input: block.input,
    status: block.status
  }
}

/**
 * 将 RpcContentBlock 转为前端统一的 ContentBlock
 */
export function mapRpcContentBlock(block: RpcContentBlock): ContentBlock | null {
  switch (block.type) {
    case 'text':
      return { type: 'text', text: block.text }
    case 'thinking':
      return { type: 'thinking', thinking: block.thinking, signature: block.signature }
    case 'tool_use':
      return mapToolUse(block)
    case 'tool_result':
      return {
        type: 'tool_result',
        tool_use_id: block.tool_use_id,
        content: block.content,
        is_error: block.is_error
      }
    case 'image':
      return { type: 'image', source: block.source }
    case 'command_execution':
      return {
        type: 'command_execution',
        command: block.command,
        output: block.output,
        exitCode: block.exitCode,
        status: block.status
      }
    case 'file_change':
      return { type: 'file_change', changes: block.changes, status: block.status }
    case 'mcp_tool_call':
      return {
        type: 'mcp_tool_call',
        server: block.server,
        tool: block.tool,
        arguments: block.arguments,
        result: block.result,
        status: block.status
      }
    case 'web_search':
      return { type: 'web_search', query: block.query }
    case 'todo_list':
      return { type: 'todo_list', items: block.items }
    case 'error':
      return { type: 'error', message: block.message }
    case 'unknown':
      return { type: 'error', message: `Unknown content type: ${block.originalType}` }
    default:
      return null
  }
}

/**
 * 将 Rpc user/assistant 消息映射为前端统一 Message
 */
export function mapRpcMessageToMessage(msg: RpcAssistantMessage | RpcUserMessage): Message | null {
  const rawContent = msg.message?.content ?? []
  const contentBlocks = rawContent
    .map(mapRpcContentBlock)
    .filter((b): b is ContentBlock => !!b)

  if (contentBlocks.length === 0) return null

  return {
    id: 'id' in msg && msg.id ? msg.id : '',
    role: msg.type,
    timestamp: Date.now(),
    content: contentBlocks,
    metadata: {
      model: msg.message?.model,
      provider: msg.provider,
      raw: msg
    }
  }
}

/**
 * 仅导出类型，方便调用处书写
 */
export type { RpcMessage, RpcStreamEvent }

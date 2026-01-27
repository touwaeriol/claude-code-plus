/**
 * Message Parser for Claude CLI Messages
 * 
 * This module parses raw JSON messages from Claude CLI into typed Message objects.
 */

import type {
  Message,
  UserMessage,
  AssistantMessage,
  SystemMessage,
  StatusSystemMessage,
  CompactBoundaryMessage,
  ResultMessage,
  StreamEvent,
  ContentBlock,
  TextBlock,
  ThinkingBlock,
  ToolUseBlock,
  ToolResultBlock,
  TokenUsage,
  ControlResponse,
  ControlRequest,
  PermissionRequest,
  HookCallbackRequest,
  McpMessageRequest,
} from './models';
import { ToolTypeParser, type SpecificToolUse } from './toolTypeParser';

/**
 * Exception for message parsing errors.
 */
export class MessageParsingException extends Error {
  constructor(message: string, public data?: unknown, public cause?: Error) {
    super(message);
    this.name = 'MessageParsingException';
  }
}

/**
 * Parser for converting raw JSON messages to typed objects.
 */
export class MessageParser {
  /**
   * Parse a JSON element into a typed Message object.
   */
  parseMessage(data: unknown): Message {
    try {
      if (typeof data !== 'object' || data === null) {
        throw new MessageParsingException('Invalid message format: not an object');
      }

      const jsonObject = data as Record<string, unknown>;
      const type = jsonObject.type as string | undefined;

      if (!type) {
        throw new MessageParsingException("Missing 'type' field in message");
      }

      switch (type) {
        case 'user':
          return this.parseUserMessage(jsonObject);
        case 'assistant':
          return this.parseAssistantMessage(jsonObject);
        case 'system':
          return this.parseSystemMessage(jsonObject);
        case 'result':
          return this.parseResultMessage(jsonObject);
        case 'stream_event':
          return this.parseStreamEvent(jsonObject);
        default:
          throw new MessageParsingException(`Unknown message type: ${type}`);
      }
    } catch (e) {
      if (e instanceof MessageParsingException) {
        throw e;
      }
      throw new MessageParsingException(
        `Failed to parse message: ${e instanceof Error ? e.message : 'Unknown error'}`,
        data,
        e instanceof Error ? e : undefined
      );
    }
  }

  /**
   * Parse user message.
   * 
   * Supports isReplay field for distinguishing compressed summary messages:
   * - isReplay = false: Compressed summary (newly generated context)
   * - isReplay = true: Confirmation message (e.g., "Compacted")
   */
  private parseUserMessage(jsonObject: Record<string, unknown>): UserMessage {
    // Check for nested message structure (Claude CLI stream-json format)
    const messageObject = jsonObject.message as Record<string, unknown> | undefined;
    let content: unknown;

    if (messageObject) {
      // New format: {"type": "user", "message": {"role": "user", "content": [...]}}
      content = messageObject.content ?? '';
    } else {
      // Old format: {"type": "user", "content": "..."}
      content = jsonObject.content ?? '';
    }

    return {
      type: 'user',
      content,
      parent_tool_use_id: jsonObject.parent_tool_use_id as string | undefined,
      session_id: (jsonObject.session_id as string) || 'default',
      isReplay: jsonObject.isReplay as boolean | undefined,
      uuid: jsonObject.uuid as string | undefined,
    };
  }

  /**
   * Parse assistant message.
   */
  private parseAssistantMessage(jsonObject: Record<string, unknown>): AssistantMessage {
    // Check if content and model are directly in the object (old format)
    const directContent = jsonObject.content as unknown[] | undefined;
    const directModel = jsonObject.model as string | undefined;

    // Or check if they're nested in a "message" object (new format)
    const messageObject = jsonObject.message as Record<string, unknown> | undefined;
    const nestedContent = messageObject?.content as unknown[] | undefined;
    const nestedModel = messageObject?.model as string | undefined;

    const contentArray = directContent ?? nestedContent;
    const model = directModel ?? nestedModel;

    if (!contentArray) {
      throw new MessageParsingException("Missing 'content' array in assistant message");
    }
    if (!model) {
      throw new MessageParsingException("Missing 'model' in assistant message");
    }

    const content = contentArray.map((item) => this.parseContentBlock(item));

    // Try to get token usage from either location
    const tokenUsage = jsonObject.token_usage
      ? this.parseTokenUsage(jsonObject.token_usage)
      : messageObject?.usage
        ? this.parseTokenUsage(messageObject.usage)
        : undefined;

    // Get message id from nested message object
    const id = messageObject?.id as string | undefined;

    return {
      type: 'assistant',
      id,
      content,
      model,
      token_usage: tokenUsage,
      parent_tool_use_id: jsonObject.parent_tool_use_id as string | undefined,
      uuid: jsonObject.uuid as string | undefined,
    };
  }

  /**
   * Parse system message.
   * 
   * Supports multiple system message types:
   * - General system message (with data field)
   * - Status message (subtype=status, with status field)
   * - Compact boundary message (subtype=compact_boundary, with compact_metadata field)
   */
  private parseSystemMessage(jsonObject: Record<string, unknown>): Message {
    const subtype = jsonObject.subtype as string | undefined;

    if (!subtype) {
      throw new MessageParsingException("Missing 'subtype' in system message");
    }

    switch (subtype) {
      case 'status': {
        // Status message: {"type":"system","subtype":"status","status":"compacting","session_id":"..."}
        return {
          type: 'system',
          subtype: 'status',
          status: jsonObject.status as string | undefined,
          session_id: (jsonObject.session_id as string) || 'default',
          uuid: jsonObject.uuid as string | undefined,
        } as StatusSystemMessage;
      }

      case 'compact_boundary': {
        // Compact boundary message
        const compactMetadata = jsonObject.compact_metadata as Record<string, unknown> | undefined;
        return {
          type: 'system',
          subtype: 'compact_boundary',
          session_id: (jsonObject.session_id as string) || 'default',
          uuid: jsonObject.uuid as string | undefined,
          compact_metadata: compactMetadata
            ? {
                trigger: compactMetadata.trigger as string | undefined,
                pre_tokens: compactMetadata.pre_tokens as number | undefined,
              }
            : undefined,
        } as CompactBoundaryMessage;
      }

      case 'init': {
        // System init message - handled separately but include for completeness
        return {
          type: 'system',
          subtype: 'init',
          data: jsonObject,
        } as SystemMessage;
      }

      default: {
        // General system message (needs data field)
        const data = jsonObject.data;
        if (data === undefined) {
          throw new MessageParsingException(`Missing 'data' in system message (subtype=${subtype})`);
        }
        return {
          type: 'system',
          subtype,
          data,
        } as SystemMessage;
      }
    }
  }

  /**
   * Parse stream event message.
   */
  private parseStreamEvent(jsonObject: Record<string, unknown>): StreamEvent {
    const uuid = jsonObject.uuid as string | undefined;
    const sessionId = jsonObject.session_id as string | undefined;
    const event = jsonObject.event;

    if (!uuid) {
      throw new MessageParsingException("Missing 'uuid' in stream_event message");
    }
    if (!sessionId) {
      throw new MessageParsingException("Missing 'session_id' in stream_event message");
    }
    if (event === undefined) {
      throw new MessageParsingException("Missing 'event' in stream_event message");
    }

    return {
      type: 'stream_event',
      uuid,
      session_id: sessionId,
      event,
      parent_tool_use_id: jsonObject.parent_tool_use_id as string | undefined,
    };
  }

  /**
   * Parse result message.
   */
  private parseResultMessage(jsonObject: Record<string, unknown>): ResultMessage {
    const subtype = jsonObject.subtype as string | undefined;
    const durationMs = jsonObject.duration_ms as number | undefined;
    const durationApiMs = jsonObject.duration_api_ms as number | undefined;
    const isError = jsonObject.is_error as boolean | undefined;
    const numTurns = jsonObject.num_turns as number | undefined;
    const sessionId = jsonObject.session_id as string | undefined;

    if (!subtype) {
      throw new MessageParsingException("Missing 'subtype' in result message");
    }
    if (durationMs === undefined) {
      throw new MessageParsingException("Missing 'duration_ms' in result message");
    }
    if (durationApiMs === undefined) {
      throw new MessageParsingException("Missing 'duration_api_ms' in result message");
    }
    if (isError === undefined) {
      throw new MessageParsingException("Missing 'is_error' in result message");
    }
    if (numTurns === undefined) {
      throw new MessageParsingException("Missing 'num_turns' in result message");
    }
    if (!sessionId) {
      throw new MessageParsingException("Missing 'session_id' in result message");
    }

    return {
      type: 'result',
      subtype,
      duration_ms: durationMs,
      duration_api_ms: durationApiMs,
      is_error: isError,
      num_turns: numTurns,
      session_id: sessionId,
      total_cost_usd: jsonObject.total_cost_usd as number | undefined,
      usage: jsonObject.usage,
      result: jsonObject.result as string | undefined,
    };
  }

  /**
   * Parse content block from JSON.
   */
  parseContentBlock(data: unknown): ContentBlock {
    if (typeof data !== 'object' || data === null) {
      throw new MessageParsingException('Invalid content block format');
    }

    const jsonObject = data as Record<string, unknown>;
    const type = jsonObject.type as string | undefined;

    if (!type) {
      throw new MessageParsingException("Missing 'type' field in content block");
    }

    switch (type) {
      case 'text': {
        const text = jsonObject.text as string | undefined;
        if (text === undefined) {
          throw new MessageParsingException("Missing 'text' in text block");
        }
        return { type: 'text', text } as TextBlock;
      }

      case 'thinking': {
        const thinking = jsonObject.thinking as string | undefined;
        if (thinking === undefined) {
          throw new MessageParsingException("Missing 'thinking' in thinking block");
        }
        return {
          type: 'thinking',
          thinking,
          signature: jsonObject.signature as string | undefined,
        } as ThinkingBlock;
      }

      case 'tool_use': {
        const id = jsonObject.id as string | undefined;
        const name = jsonObject.name as string | undefined;
        const input = jsonObject.input;

        if (!id) {
          throw new MessageParsingException("Missing 'id' in tool_use block");
        }
        if (!name) {
          throw new MessageParsingException("Missing 'name' in tool_use block");
        }
        if (input === undefined) {
          throw new MessageParsingException("Missing 'input' in tool_use block");
        }

        // Create basic ToolUseBlock
        const basicToolUse: ToolUseBlock = { type: 'tool_use', id, name, input };

        // Use ToolTypeParser to convert to specific tool type
        return ToolTypeParser.parseToolUseBlock(basicToolUse);
      }

      case 'tool_result': {
        const toolUseId = jsonObject.tool_use_id as string | undefined;
        if (!toolUseId) {
          throw new MessageParsingException("Missing 'tool_use_id' in tool_result block");
        }
        return {
          type: 'tool_result',
          tool_use_id: toolUseId,
          content: jsonObject.content,
          is_error: jsonObject.is_error as boolean | undefined,
        } as ToolResultBlock;
      }

      default:
        throw new MessageParsingException(`Unknown content block type: ${type}`);
    }
  }

  /**
   * Parse token usage information.
   * 
   * Note: In streaming responses, `output_tokens` may be missing during intermediate states.
   * In such cases, we use 0 as the default value instead of throwing an exception.
   */
  private parseTokenUsage(data: unknown): TokenUsage {
    if (typeof data !== 'object' || data === null) {
      throw new MessageParsingException('Invalid token usage format');
    }

    const jsonObject = data as Record<string, unknown>;
    const inputTokens = jsonObject.input_tokens as number | undefined;

    if (inputTokens === undefined) {
      throw new MessageParsingException("Missing 'input_tokens' in token usage");
    }

    // output_tokens may be missing in streaming intermediate states, use 0 as default
    const outputTokens = (jsonObject.output_tokens as number) ?? 0;

    return {
      input_tokens: inputTokens,
      output_tokens: outputTokens,
      cache_creation_input_tokens: jsonObject.cache_creation_input_tokens as number | undefined,
      cache_read_input_tokens: jsonObject.cache_read_input_tokens as number | undefined,
    };
  }

  /**
   * Check if a JSON element represents a control message.
   */
  isControlMessage(data: unknown): boolean {
    if (typeof data !== 'object' || data === null) {
      return false;
    }
    const jsonObject = data as Record<string, unknown>;
    const type = jsonObject.type;
    return type === 'control_request' || type === 'control_response';
  }

  /**
   * Parse control request message.
   */
  parseControlRequest(data: unknown): { requestId: string; request: ControlRequest } {
    if (typeof data !== 'object' || data === null) {
      throw new MessageParsingException('Invalid control request format');
    }

    const jsonObject = data as Record<string, unknown>;
    const requestId = jsonObject.request_id as string | undefined;
    const request = jsonObject.request as Record<string, unknown> | undefined;

    if (!requestId) {
      throw new MessageParsingException("Missing 'request_id' in control request");
    }
    if (!request) {
      throw new MessageParsingException("Missing 'request' in control request");
    }

    const subtype = request.subtype as string | undefined;
    if (!subtype) {
      throw new MessageParsingException("Missing 'subtype' in control request");
    }

    let controlRequest: ControlRequest;

    switch (subtype) {
      case 'interrupt':
        controlRequest = { subtype: 'interrupt' };
        break;

      case 'can_use_tool': {
        const toolName = request.tool_name as string | undefined;
        const input = request.input;
        if (!toolName) {
          throw new MessageParsingException("Missing 'tool_name' in permission request");
        }
        if (input === undefined) {
          throw new MessageParsingException("Missing 'input' in permission request");
        }
        controlRequest = {
          subtype: 'can_use_tool',
          tool_name: toolName,
          input,
          permission_suggestions: request.permission_suggestions as unknown[] | undefined,
          blocked_path: request.blocked_path as string | undefined,
          tool_use_id: request.tool_use_id as string | undefined,
          agent_id: request.agent_id as string | undefined,
        } as PermissionRequest;
        break;
      }

      case 'hook_callback': {
        const callbackId = request.callback_id as string | undefined;
        const input = request.input;
        if (!callbackId) {
          throw new MessageParsingException("Missing 'callback_id' in hook callback request");
        }
        if (input === undefined) {
          throw new MessageParsingException("Missing 'input' in hook callback request");
        }
        controlRequest = {
          subtype: 'hook_callback',
          callback_id: callbackId,
          input,
          tool_use_id: request.tool_use_id as string | undefined,
        } as HookCallbackRequest;
        break;
      }

      case 'mcp_message': {
        const serverName = request.server_name as string | undefined;
        const message = request.message;
        if (!serverName) {
          throw new MessageParsingException("Missing 'server_name' in MCP message request");
        }
        if (message === undefined) {
          throw new MessageParsingException("Missing 'message' in MCP message request");
        }
        controlRequest = {
          subtype: 'mcp_message',
          server_name: serverName,
          message,
        } as McpMessageRequest;
        break;
      }

      default:
        throw new MessageParsingException(`Unknown control request subtype: ${subtype}`);
    }

    return { requestId, request: controlRequest };
  }

  /**
   * Parse control response message.
   */
  parseControlResponse(data: unknown): ControlResponse {
    if (typeof data !== 'object' || data === null) {
      throw new MessageParsingException('Invalid control response format');
    }

    const jsonObject = data as Record<string, unknown>;
    const response = jsonObject.response as Record<string, unknown> | undefined;

    if (!response) {
      throw new MessageParsingException("Missing 'response' in control response");
    }

    const subtype = response.subtype as string | undefined;
    const requestId = response.request_id as string | undefined;

    if (!subtype) {
      throw new MessageParsingException("Missing 'subtype' in control response");
    }
    if (!requestId) {
      throw new MessageParsingException("Missing 'request_id' in control response");
    }

    return {
      subtype,
      request_id: requestId,
      response: response.response,
      error: response.error as string | undefined,
    };
  }
}

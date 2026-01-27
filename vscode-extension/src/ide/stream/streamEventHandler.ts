/**
 * Stream Event 处理工具函数
 * 
 * 翻译自: jetbrains-plugin/.../stream/StreamEventHandler.kt
 * 
 * 提供类型安全的 stream event 解析和处理功能
 */

// 内容块类型定义
export interface TextBlock {
  type: 'text';
  text: string;
}

export interface ToolUseBlock {
  type: 'tool_use';
  id: string;
  name: string;
  input: Record<string, unknown>;
}

export interface ThinkingBlock {
  type: 'thinking';
  thinking: string;
  signature: string;
}

export type ContentBlock = TextBlock | ToolUseBlock | ThinkingBlock;

/**
 * Token 使用统计
 */
export interface TokenUsage {
  inputTokens: number;
  outputTokens: number;
}

/**
 * 可变的 AssistantMessage（用于流式更新）
 */
export interface MutableAssistantMessage {
  content: ContentBlock[];
  model: string;
  tokenUsage?: TokenUsage;
}

/**
 * Delta 对象类型定义
 */
export interface Delta {
  type?: string;
  text?: string;
  partial_json?: string;
  thinking?: string;
}

/**
 * Stream Event 处理工具
 */
export const StreamEventHandler = {
  /**
   * 类型守卫：检查是否为 message_start 事件
   */
  isMessageStartEvent(event: Record<string, unknown>): boolean {
    return event.type === 'message_start';
  },

  /**
   * 类型守卫：检查是否为 message_delta 事件
   */
  isMessageDeltaEvent(event: Record<string, unknown>): boolean {
    return event.type === 'message_delta';
  },

  /**
   * 类型守卫：检查是否为 message_stop 事件
   */
  isMessageStopEvent(event: Record<string, unknown>): boolean {
    return event.type === 'message_stop';
  },

  /**
   * 类型守卫：检查是否为 content_block_start 事件
   */
  isContentBlockStartEvent(event: Record<string, unknown>): boolean {
    return event.type === 'content_block_start';
  },

  /**
   * 类型守卫：检查是否为 content_block_delta 事件
   */
  isContentBlockDeltaEvent(event: Record<string, unknown>): boolean {
    return event.type === 'content_block_delta';
  },

  /**
   * 类型守卫：检查是否为 content_block_stop 事件
   */
  isContentBlockStopEvent(event: Record<string, unknown>): boolean {
    return event.type === 'content_block_stop';
  },

  /**
   * 类型守卫：检查是否为 text_delta
   */
  isTextDelta(delta: Delta | null | undefined): delta is Delta & { text: string } {
    if (!delta) return false;
    return delta.type === 'text_delta' && 'text' in delta;
  },

  /**
   * 类型守卫：检查是否为 input_json_delta
   */
  isInputJsonDelta(delta: Delta | null | undefined): delta is Delta & { partial_json: string } {
    if (!delta) return false;
    return delta.type === 'input_json_delta' && 'partial_json' in delta;
  },

  /**
   * 类型守卫：检查是否为 thinking_delta
   */
  isThinkingDelta(delta: Delta | null | undefined): delta is Delta & { thinking: string } {
    if (!delta) return false;
    return delta.type === 'thinking_delta' && 'thinking' in delta;
  },

  /**
   * 处理文本增量更新
   */
  applyTextDelta(
    message: MutableAssistantMessage,
    index: number,
    delta: Delta
  ): boolean {
    const text = delta.text;
    if (text === undefined) return false;

    const existingBlock = message.content[index];

    if (existingBlock && existingBlock.type === 'text') {
      // 追加到现有文本块
      message.content[index] = {
        type: 'text',
        text: existingBlock.text + text,
      };
      return true;
    } else {
      // 创建新的文本块
      const newBlock: TextBlock = { type: 'text', text };

      if (index >= message.content.length) {
        message.content.push(newBlock);
      } else {
        message.content[index] = newBlock;
      }
      return true;
    }
  },

  /**
   * 处理工具输入 JSON 增量更新
   */
  applyInputJsonDelta(
    message: MutableAssistantMessage,
    index: number,
    delta: Delta,
    accumulator: Map<string, string>
  ): boolean {
    const partialJson = delta.partial_json;
    if (partialJson === undefined) return false;

    // 查找对应的 tool_use 块
    let toolUseBlock: ToolUseBlock | null = null;
    let toolIndex = -1;

    if (index < message.content.length) {
      const block = message.content[index];
      if (block.type === 'tool_use') {
        toolUseBlock = block;
        toolIndex = index;
      }
    }

    // 如果通过 index 找不到，尝试查找最后一个 tool_use 块
    if (!toolUseBlock) {
      for (let i = message.content.length - 1; i >= 0; i--) {
        const block = message.content[i];
        if (block.type === 'tool_use') {
          toolUseBlock = block;
          toolIndex = i;
          break;
        }
      }
    }

    if (!toolUseBlock || toolIndex === -1) {
      return false;
    }

    // 累积 partial_json
    const accumulatorKey = `tool_input_${toolUseBlock.id}`;
    const accumulatedJson = (accumulator.get(accumulatorKey) || '') + partialJson;
    accumulator.set(accumulatorKey, accumulatedJson);

    // 尝试解析累积的 JSON
    try {
      const parsed = JSON.parse(accumulatedJson);

      // 更新工具调用块的 input
      message.content[toolIndex] = {
        type: 'tool_use',
        id: toolUseBlock.id,
        name: toolUseBlock.name,
        input: parsed,
      };

      return true;
    } catch {
      // JSON 可能还不完整，暂时不更新
      // 但保留累积的字符串，等待更多增量
      return false;
    }
  },

  /**
   * 处理 Thinking 增量更新
   */
  applyThinkingDelta(
    message: MutableAssistantMessage,
    index: number,
    delta: Delta
  ): boolean {
    const thinkingText = delta.thinking;
    if (thinkingText === undefined) return false;

    const existingBlock = message.content[index];

    if (existingBlock && existingBlock.type === 'thinking') {
      // 追加到现有 thinking 块
      message.content[index] = {
        type: 'thinking',
        thinking: existingBlock.thinking + thinkingText,
        signature: existingBlock.signature,
      };
      return true;
    } else {
      // 创建新的 thinking 块
      const newBlock: ThinkingBlock = {
        type: 'thinking',
        thinking: thinkingText,
        signature: '',
      };

      if (index >= message.content.length) {
        message.content.push(newBlock);
      } else {
        message.content[index] = newBlock;
      }
      return true;
    }
  },

  /**
   * 查找或创建最后一个 assistant 消息
   */
  findOrCreateLastAssistantMessage(
    messages: MutableAssistantMessage[]
  ): MutableAssistantMessage {
    const lastAssistant = messages[messages.length - 1];

    if (lastAssistant) {
      return lastAssistant;
    }

    // 创建新的 assistant 消息
    const newMessage: MutableAssistantMessage = {
      content: [],
      model: 'unknown',
    };
    messages.push(newMessage);
    return newMessage;
  },

  /**
   * 判断消息内容是否实际为空
   */
  isMessageContentEmpty(content: ContentBlock[]): boolean {
    if (content.length === 0) return true;

    // 检查是否只有空文本块
    return content.every(block => {
      if (block.type === 'text') {
        return block.text.trim() === '';
      }
      return false; // 其他类型的块（如 tool_use）不算空
    });
  },
};

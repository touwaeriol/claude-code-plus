/**
 * Stream Event 处理器
 * 
 * 翻译自: jetbrains-plugin/.../stream/StreamEventProcessor.kt
 * 
 * 负责处理各种 stream event 的业务逻辑
 */

import {
  StreamEventHandler,
  MutableAssistantMessage,
  ContentBlock,
  TextBlock,
  ToolUseBlock,
  ThinkingBlock,
  TokenUsage,
} from './streamEventHandler';

// 日志工具
const log = {
  info: (msg: string) => console.log(`[StreamEventProcessor] ${msg}`),
  warn: (msg: string) => console.warn(`[StreamEventProcessor] ${msg}`),
};

/**
 * AssistantMessage（不可变版本，用于返回）
 */
export interface AssistantMessage {
  content: ContentBlock[];
  model: string;
  tokenUsage?: TokenUsage;
}

/**
 * Stream Event 处理结果
 */
export interface StreamEventProcessResult {
  shouldUpdateMessages: boolean;
  shouldSetGenerating: boolean | null; // null = 不改变
  messageUpdated: boolean;
  newMessage?: AssistantMessage; // 新创建的消息
}

/**
 * Stream Event 处理上下文
 */
export interface StreamEventContext {
  messages: MutableAssistantMessage[];
  toolInputJsonAccumulator: Map<string, string>;
  registerToolCall?: (toolUse: ToolUseBlock) => void;
}

/**
 * Stream Event 类型定义
 */
export interface StreamEvent {
  event: Record<string, unknown>;
}

/**
 * 创建空操作结果
 */
function createNoOpResult(): StreamEventProcessResult {
  return {
    shouldUpdateMessages: false,
    shouldSetGenerating: null,
    messageUpdated: false,
  };
}

/**
 * 确保 content 列表有足够的大小
 */
function ensureContentSize(message: MutableAssistantMessage, index: number): void {
  while (message.content.length <= index) {
    message.content.push({ type: 'text', text: '' });
  }
}

/**
 * Stream Event 处理器
 */
export const StreamEventProcessor = {
  /**
   * 处理 StreamEvent
   */
  process(streamEvent: StreamEvent, context: StreamEventContext): StreamEventProcessResult {
    const event = streamEvent.event;
    if (!event || typeof event !== 'object') {
      return createNoOpResult();
    }

    const eventType = event.type as string | undefined;
    log.info(`处理 StreamEvent: type=${eventType}`);

    switch (eventType) {
      case 'message_start':
        return this.processMessageStart(event, context);
      case 'content_block_start':
        return this.processContentBlockStart(event, context);
      case 'content_block_delta':
        return this.processContentBlockDelta(event, context);
      case 'content_block_stop':
        return this.processContentBlockStop(event, context);
      case 'message_delta':
        return this.processMessageDelta(event, context);
      case 'message_stop':
        return this.processMessageStop(event, context);
      default:
        log.warn(`未知的 StreamEvent 类型: ${eventType}`);
        return createNoOpResult();
    }
  },

  /**
   * 处理 message_start 事件
   */
  processMessageStart(
    event: Record<string, unknown>,
    context: StreamEventContext
  ): StreamEventProcessResult {
    const messageObj = event.message as Record<string, unknown> | undefined;
    const eventMessageId = messageObj?.id as string | undefined;

    log.info(`processMessageStart: id=${eventMessageId}`);

    // 查找最后一个 assistant 消息
    const lastMessage = context.messages[context.messages.length - 1];

    // 情况1：有空的占位符消息，继续使用它
    if (lastMessage && StreamEventHandler.isMessageContentEmpty(lastMessage.content)) {
      log.info('复用现有空消息');
      return {
        shouldUpdateMessages: true,
        shouldSetGenerating: true,
        messageUpdated: true,
      };
    }

    // 情况2：没有消息或最后一条消息已有实际内容，创建新消息
    const newMessage: MutableAssistantMessage = {
      content: [],
      model: (messageObj?.model as string) || 'unknown',
    };
    context.messages.push(newMessage);

    log.info('创建新的 assistant 消息');

    // 转换为 AssistantMessage 用于返回
    const assistantMessage: AssistantMessage = {
      content: newMessage.content,
      model: newMessage.model,
      tokenUsage: newMessage.tokenUsage,
    };

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true,
      newMessage: assistantMessage,
    };
  },

  /**
   * 处理 content_block_start 事件
   */
  processContentBlockStart(
    event: Record<string, unknown>,
    context: StreamEventContext
  ): StreamEventProcessResult {
    const index = event.index as number | undefined;
    const contentBlock = event.content_block as Record<string, unknown> | undefined;

    if (index === undefined || !contentBlock) {
      return createNoOpResult();
    }

    const blockType = contentBlock.type as string | undefined;
    log.info(`processContentBlockStart: index=${index}, type=${blockType}`);

    const lastMessage = context.messages[context.messages.length - 1];
    if (!lastMessage) {
      return createNoOpResult();
    }

    switch (blockType) {
      case 'text': {
        // 创建空文本块占位符
        const textBlock: TextBlock = { type: 'text', text: '' };
        ensureContentSize(lastMessage, index);
        lastMessage.content[index] = textBlock;
        break;
      }

      case 'tool_use': {
        // 创建工具使用块
        const toolId = (contentBlock.id as string) || 'unknown';
        const toolName = (contentBlock.name as string) || 'unknown';

        const toolUseBlock: ToolUseBlock = {
          type: 'tool_use',
          id: toolId,
          name: toolName,
          input: {},
        };

        ensureContentSize(lastMessage, index);
        lastMessage.content[index] = toolUseBlock;

        // 注册工具调用
        context.registerToolCall?.(toolUseBlock);
        break;
      }

      case 'thinking': {
        // 创建 thinking 块
        const thinkingBlock: ThinkingBlock = {
          type: 'thinking',
          thinking: '',
          signature: '',
        };

        ensureContentSize(lastMessage, index);
        lastMessage.content[index] = thinkingBlock;
        break;
      }
    }

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true,
    };
  },

  /**
   * 处理 content_block_delta 事件
   */
  processContentBlockDelta(
    event: Record<string, unknown>,
    context: StreamEventContext
  ): StreamEventProcessResult {
    const index = event.index as number | undefined;
    const delta = event.delta as Record<string, unknown> | undefined;

    if (index === undefined || !delta) {
      return createNoOpResult();
    }

    const lastMessage = context.messages[context.messages.length - 1];
    if (!lastMessage) {
      return createNoOpResult();
    }

    let success = false;

    if (StreamEventHandler.isTextDelta(delta)) {
      // 处理文本增量
      success = StreamEventHandler.applyTextDelta(lastMessage, index, delta);
    } else if (StreamEventHandler.isInputJsonDelta(delta)) {
      // 处理工具输入 JSON 增量
      success = StreamEventHandler.applyInputJsonDelta(
        lastMessage,
        index,
        delta,
        context.toolInputJsonAccumulator
      );
    } else if (StreamEventHandler.isThinkingDelta(delta)) {
      // 处理 Thinking 增量
      success = StreamEventHandler.applyThinkingDelta(lastMessage, index, delta);
    }

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: success,
    };
  },

  /**
   * 处理 content_block_stop 事件
   */
  processContentBlockStop(
    _event: Record<string, unknown>,
    _context: StreamEventContext
  ): StreamEventProcessResult {
    log.info('processContentBlockStop');

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: false,
    };
  },

  /**
   * 处理 message_delta 事件
   */
  processMessageDelta(
    event: Record<string, unknown>,
    context: StreamEventContext
  ): StreamEventProcessResult {
    // 处理消息级别的增量（如 token usage 更新）
    const delta = event.delta as Record<string, unknown> | undefined;
    const usage = delta?.usage as Record<string, unknown> | undefined;

    if (usage) {
      const lastMessage = context.messages[context.messages.length - 1];
      if (lastMessage) {
        const inputTokens = (usage.input_tokens as number) || 0;
        const outputTokens = (usage.output_tokens as number) || 0;

        lastMessage.tokenUsage = {
          inputTokens,
          outputTokens,
        };
      }
    }

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: true,
      messageUpdated: true,
    };
  },

  /**
   * 处理 message_stop 事件
   */
  processMessageStop(
    _event: Record<string, unknown>,
    _context: StreamEventContext
  ): StreamEventProcessResult {
    log.info('processMessageStop: 消息完成');

    return {
      shouldUpdateMessages: true,
      shouldSetGenerating: false,
      messageUpdated: false,
    };
  },
};

export {
  MutableAssistantMessage,
  ContentBlock,
  TextBlock,
  ToolUseBlock,
  ThinkingBlock,
  TokenUsage,
} from './streamEventHandler';

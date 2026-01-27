/**
 * DisplayItem 转换器 - 服务端版本
 *
 * 将后端消息格式转换为前端 DisplayItem 格式
 * 主要用于 RSocket 或 HTTP 响应的数据预处理
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/converters/DisplayItemConverter.kt
 */

import type {
    ToolCallItem,
    DisplayItem,
    ToolCallStatus,
    ToolResult,
    UserMessageItem,
    AssistantTextItem,
    SystemMessageItem,
    SystemMessageLevel,
    RequestStats,
} from './types';

/**
 * 工具类型常量
 */
export const TOOL_CONSTANTS = {
    READ: 'Read',
    WRITE: 'Write',
    EDIT: 'Edit',
    MULTI_EDIT: 'MultiEdit',
    TODO_WRITE: 'TodoWrite',
    BASH: 'Bash',
    GREP: 'Grep',
    GLOB: 'Glob',
    WEB_SEARCH: 'WebSearch',
    WEB_FETCH: 'WebFetch',
    TASK: 'Task',
    NOTEBOOK_EDIT: 'NotebookEdit',
    BASH_OUTPUT: 'TaskOutput',
    KILL_SHELL: 'KillShell',
    EXIT_PLAN_MODE: 'ExitPlanMode',
    ASK_USER_QUESTION: 'AskUserQuestion',
    SKILL: 'Skill',
    SLASH_COMMAND: 'SlashCommand',
    LIST_MCP_RESOURCES: 'ListMcpResources',
    READ_MCP_RESOURCE: 'ReadMcpResource',
} as const;

/**
 * 工具名称到类型的映射
 */
export const TOOL_NAME_TO_TYPE: Record<string, string> = {
    'Read': TOOL_CONSTANTS.READ,
    'Write': TOOL_CONSTANTS.WRITE,
    'Edit': TOOL_CONSTANTS.EDIT,
    'MultiEdit': TOOL_CONSTANTS.MULTI_EDIT,
    'TodoWrite': TOOL_CONSTANTS.TODO_WRITE,
    'Bash': TOOL_CONSTANTS.BASH,
    'Grep': TOOL_CONSTANTS.GREP,
    'Glob': TOOL_CONSTANTS.GLOB,
    'WebSearch': TOOL_CONSTANTS.WEB_SEARCH,
    'WebFetch': TOOL_CONSTANTS.WEB_FETCH,
    'Task': TOOL_CONSTANTS.TASK,
    'NotebookEdit': TOOL_CONSTANTS.NOTEBOOK_EDIT,
    'TaskOutput': TOOL_CONSTANTS.BASH_OUTPUT,
    'KillShell': TOOL_CONSTANTS.KILL_SHELL,
    'ExitPlanMode': TOOL_CONSTANTS.EXIT_PLAN_MODE,
    'AskUserQuestion': TOOL_CONSTANTS.ASK_USER_QUESTION,
    'Skill': TOOL_CONSTANTS.SKILL,
    'SlashCommand': TOOL_CONSTANTS.SLASH_COMMAND,
    'ListMcpResources': TOOL_CONSTANTS.LIST_MCP_RESOURCES,
    'ReadMcpResource': TOOL_CONSTANTS.READ_MCP_RESOURCE,
};

/**
 * 工具使用块接口
 */
export interface ToolUseBlock {
    id: string;
    name: string;
    input: Record<string, any>;
}

/**
 * 工具结果块接口
 */
export interface ToolResultBlock {
    tool_use_id: string;
    content?: any;
    isError?: boolean;
    is_error?: boolean;
}

/**
 * 文本块接口
 */
export interface TextBlock {
    type: 'text';
    text: string;
}

/**
 * 内容块联合类型
 */
export type ContentBlock = TextBlock | ToolUseBlock | { type: string; [key: string]: any };

/**
 * Token 使用信息
 */
export interface TokenUsage {
    inputTokens: number;
    outputTokens: number;
}

/**
 * 用户消息接口
 */
export interface UserMessage {
    role: 'user';
    sessionId?: string;
    content: any;
}

/**
 * 助手消息接口
 */
export interface AssistantMessage {
    role: 'assistant';
    content: ContentBlock[];
    tokenUsage?: TokenUsage;
}

/**
 * 系统消息接口
 */
export interface SystemMessage {
    role: 'system';
    subtype?: string;
    data: any;
}

/**
 * 消息联合类型
 */
export type Message = UserMessage | AssistantMessage | SystemMessage;

/**
 * DisplayItem 转换器
 */
export class DisplayItemConverter {
    /**
     * 从 ToolUseBlock 创建 ToolCall
     *
     * @param block 工具使用块
     * @param pendingToolCalls 待处理的工具调用 Map
     * @returns ToolCallItem 对象
     */
    static createToolCall(
        block: ToolUseBlock,
        pendingToolCalls: Map<string, ToolCallItem>
    ): ToolCallItem {
        // 检查是否已存在
        const existing = pendingToolCalls.get(block.id);
        if (existing) {
            return existing;
        }

        const toolType = TOOL_NAME_TO_TYPE[block.name] || block.name;
        const timestamp = Date.now();

        const toolCall: ToolCallItem = {
            id: block.id,
            displayType: 'toolCall',
            toolType,
            toolName: block.name,
            status: 'running' as ToolCallStatus,
            startTime: timestamp,
            timestamp,
            input: block.input || {},
        };

        pendingToolCalls.set(block.id, toolCall);
        return toolCall;
    }

    /**
     * 更新工具调用结果
     *
     * @param toolCall 工具调用对象
     * @param resultBlock 工具结果块
     */
    static updateToolCallResult(
        toolCall: ToolCallItem,
        resultBlock: ToolResultBlock
    ): ToolCallItem {
        const isError = resultBlock.isError === true || resultBlock.is_error === true;
        const newStatus: ToolCallStatus = isError ? 'failed' : 'success';
        const endTime = Date.now();

        const result: ToolResult = isError
            ? { is_error: true, content: resultBlock.content?.toString() || 'Unknown error' }
            : { is_error: false, content: resultBlock.content?.toString() || '' };

        return {
            ...toolCall,
            status: newStatus,
            endTime,
            result,
        };
    }

    /**
     * 将 Message 数组转换为 DisplayItem 数组
     *
     * @param messages 原始消息数组
     * @param pendingToolCalls 待处理的工具调用 Map
     * @returns DisplayItem 数组
     */
    static convertToDisplayItems(
        messages: Message[],
        pendingToolCalls: Map<string, ToolCallItem>
    ): DisplayItem[] {
        const displayItems: DisplayItem[] = [];

        for (let messageIdx = 0; messageIdx < messages.length; messageIdx++) {
            const message = messages[messageIdx];
            const items = this.convertMessageToDisplayItems(
                message,
                pendingToolCalls,
                messageIdx,
                messages
            );
            displayItems.push(...items);
        }

        return displayItems;
    }

    /**
     * 将单个 Message 转换为 DisplayItem 数组
     *
     * @param message 单个消息
     * @param pendingToolCalls 待处理的工具调用 Map
     * @param messageIdx 消息索引
     * @param allMessages 所有消息（用于统计计算）
     * @returns DisplayItem 数组
     */
    static convertMessageToDisplayItems(
        message: Message,
        pendingToolCalls: Map<string, ToolCallItem>,
        messageIdx: number = 0,
        allMessages?: Message[]
    ): DisplayItem[] {
        const displayItems: DisplayItem[] = [];

        switch (message.role) {
            case 'user': {
                const content = this.parseUserMessageContent(message.content);
                if (content.trim()) {
                    const userMessageItem: UserMessageItem = {
                        id: this.generateMessageId(message, messageIdx),
                        displayType: 'userMessage',
                        content,
                        timestamp: Date.now(),
                    };
                    displayItems.push(userMessageItem);
                }
                break;
            }

            case 'assistant': {
                const textBlockIndices = message.content
                    .map((block, idx) => ({ block, idx }))
                    .filter(({ block }) => this.isTextBlock(block) && (block as TextBlock).text.trim())
                    .map(({ idx }) => idx);
                const lastTextBlockIndex = textBlockIndices[textBlockIndices.length - 1] ?? -1;

                for (let blockIdx = 0; blockIdx < message.content.length; blockIdx++) {
                    const block = message.content[blockIdx];

                    if (this.isTextBlock(block) && (block as TextBlock).text.trim()) {
                        const isLastTextBlock = blockIdx === lastTextBlockIndex;

                        let stats: RequestStats | undefined;
                        if (isLastTextBlock && message.tokenUsage) {
                            const usage = message.tokenUsage;
                            stats = {
                                requestDuration: 0,
                                inputTokens: usage.inputTokens,
                                outputTokens: usage.outputTokens,
                            };
                        }

                        const assistantText: AssistantTextItem = {
                            id: `${this.generateMessageId(message, messageIdx)}-text-${blockIdx}`,
                            displayType: 'assistantText',
                            content: (block as TextBlock).text,
                            timestamp: Date.now(),
                            isLastInMessage: isLastTextBlock,
                            stats,
                        };
                        displayItems.push(assistantText);
                    } else if (this.isToolUseBlock(block)) {
                        const toolCall = this.createToolCall(block as ToolUseBlock, pendingToolCalls);
                        displayItems.push(toolCall);
                    }
                }
                break;
            }

            case 'system': {
                const textContent = message.data?.toString() || '';
                if (textContent.trim()) {
                    const level: SystemMessageLevel =
                        message.subtype === 'error' ? 'error' :
                        message.subtype === 'warning' ? 'warning' : 'info';

                    const systemMessageItem: SystemMessageItem = {
                        id: this.generateMessageId(message, messageIdx),
                        displayType: 'systemMessage',
                        content: textContent,
                        level,
                        timestamp: Date.now(),
                    };
                    displayItems.push(systemMessageItem);
                }
                break;
            }
        }

        return displayItems;
    }

    /**
     * 生成消息 ID
     */
    private static generateMessageId(message: Message, index: number): string {
        switch (message.role) {
            case 'user':
                return (message as UserMessage).sessionId || `user-${index}-${Date.now()}`;
            case 'assistant':
                return `assistant-${index}-${Date.now()}`;
            case 'system':
                return `system-${(message as SystemMessage).subtype || 'info'}-${index}`;
            default:
                return `message-${index}-${Date.now()}`;
        }
    }

    /**
     * 解析 UserMessage.content 为纯文本
     */
    private static parseUserMessageContent(content: any): string {
        if (typeof content === 'string') {
            return content;
        }

        if (Array.isArray(content)) {
            return content
                .filter(item => item && typeof item === 'object' && item.type === 'text')
                .map(item => item.text || '')
                .join('\n');
        }

        return '';
    }

    /**
     * 判断是否为文本块
     */
    private static isTextBlock(block: ContentBlock): block is TextBlock {
        return block && (block as any).type === 'text' && typeof (block as TextBlock).text === 'string';
    }

    /**
     * 判断是否为工具使用块
     */
    private static isToolUseBlock(block: ContentBlock): block is ToolUseBlock {
        return block && (block as any).type === 'tool_use' && typeof (block as ToolUseBlock).id === 'string';
    }
}

/**
 * 导出便捷函数
 */
export const createToolCall = DisplayItemConverter.createToolCall.bind(DisplayItemConverter);
export const updateToolCallResult = DisplayItemConverter.updateToolCallResult.bind(DisplayItemConverter);
export const convertToDisplayItems = DisplayItemConverter.convertToDisplayItems.bind(DisplayItemConverter);
export const convertMessageToDisplayItems = DisplayItemConverter.convertMessageToDisplayItems.bind(DisplayItemConverter);

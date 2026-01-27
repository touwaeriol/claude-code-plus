/**
 * DisplayItem 相关类型定义
 * 
 * 用于服务端消息转换
 */

/**
 * 工具调用状态
 */
export type ToolCallStatus = 'running' | 'success' | 'failed' | 'cancelled';

/**
 * 系统消息级别
 */
export type SystemMessageLevel = 'info' | 'warning' | 'error';

/**
 * 请求统计信息
 */
export interface RequestStats {
    requestDuration: number;
    inputTokens: number;
    outputTokens: number;
}

/**
 * 工具结果
 */
export interface ToolResult {
    is_error: boolean;
    content: string | any[];
}

/**
 * DisplayItem 基础接口
 */
export interface BaseDisplayItem {
    id: string;
    displayType: string;
    timestamp: number;
}

/**
 * 用户消息项
 */
export interface UserMessageItem extends BaseDisplayItem {
    displayType: 'userMessage';
    content: string;
}

/**
 * 助手文本项
 */
export interface AssistantTextItem extends BaseDisplayItem {
    displayType: 'assistantText';
    content: string;
    isLastInMessage?: boolean;
    stats?: RequestStats;
}

/**
 * 系统消息项
 */
export interface SystemMessageItem extends BaseDisplayItem {
    displayType: 'systemMessage';
    content: string;
    level: SystemMessageLevel;
}

/**
 * 工具调用项
 */
export interface ToolCallItem extends BaseDisplayItem {
    displayType: 'toolCall';
    toolType: string;
    toolName: string;
    status: ToolCallStatus;
    startTime: number;
    endTime?: number;
    input: Record<string, any>;
    result?: ToolResult;
}

/**
 * 思考内容项
 */
export interface ThinkingItem extends BaseDisplayItem {
    displayType: 'thinking';
    content: string;
    isComplete?: boolean;
}

/**
 * 压缩摘要项
 */
export interface CompactSummaryItem extends BaseDisplayItem {
    displayType: 'compactSummary';
    content: any;
    preTokens?: number;
    trigger?: string;
}

/**
 * 本地命令输出项
 */
export interface LocalCommandOutputItem extends BaseDisplayItem {
    displayType: 'localCommandOutput';
    command: string;
    outputType: string;
}

/**
 * DisplayItem 联合类型
 */
export type DisplayItem =
    | UserMessageItem
    | AssistantTextItem
    | SystemMessageItem
    | ToolCallItem
    | ThinkingItem
    | CompactSummaryItem
    | LocalCommandOutputItem;

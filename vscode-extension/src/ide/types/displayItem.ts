/**
 * Display Item Types
 * 
 * Frontend display layer type definitions
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/types/DisplayItem.kt
 * 
 * These types are ViewModels converted from backend Messages for UI display
 */

import { ToolConstants } from './toolConstants';

// ============ Basic Types ============

/**
 * Tool call status
 */
export enum ToolCallStatus {
    RUNNING = 'RUNNING',
    SUCCESS = 'SUCCESS',
    FAILED = 'FAILED'
}

/**
 * Connection status
 */
export enum ConnectionStatus {
    DISCONNECTED = 'DISCONNECTED',
    CONNECTING = 'CONNECTING',
    CONNECTED = 'CONNECTED',
    ERROR = 'ERROR'
}

/**
 * Context reference type
 */
export enum ContextType {
    FILE = 'FILE',
    WEB = 'WEB',
    FOLDER = 'FOLDER',
    IMAGE = 'IMAGE'
}

/**
 * Context display type
 */
export enum ContextDisplayType {
    TAG = 'TAG',
    INLINE = 'INLINE'
}

/**
 * Context reference
 */
export interface ContextReference {
    type: ContextType;
    uri: string;
    displayType: ContextDisplayType;
    // Type-specific extra fields
    path?: string;
    fullPath?: string;
    url?: string;
    title?: string;
    fileCount?: number;
    totalSize?: number;
    name?: string;
    mimeType?: string;
    base64Data?: string;
    size?: number;
}

// ============ DisplayItem Base Interface ============

/**
 * Base interface for all DisplayItem types
 */
export interface DisplayItemBase {
    id: string;
    timestamp: number;
}

// ============ Message Types ============

/**
 * Request statistics
 */
export interface RequestStats {
    requestDuration: number;  // Request duration in milliseconds
    inputTokens: number;      // Input tokens
    outputTokens: number;     // Output tokens
}

/**
 * Image block
 */
export interface ImageBlock {
    type: string;
    mediaType: string;
    data: string;
}

/**
 * User message
 */
export interface UserMessageItem extends DisplayItemBase {
    kind: 'UserMessageItem';
    content: string;
    images: ImageBlock[];
    contexts: ContextReference[];
    requestStats?: RequestStats;
    isStreaming: boolean;
}

/**
 * AI text reply
 */
export interface AssistantTextItem extends DisplayItemBase {
    kind: 'AssistantTextItem';
    content: string;
    stats?: RequestStats;
    isLastInMessage: boolean;
}

/**
 * System message level
 */
export enum SystemMessageLevel {
    INFO = 'INFO',
    WARNING = 'WARNING',
    ERROR = 'ERROR'
}

/**
 * System message
 */
export interface SystemMessageItem extends DisplayItemBase {
    kind: 'SystemMessageItem';
    content: string;
    level: SystemMessageLevel;
}

// ============ Tool Call Results ============

/**
 * Successful tool result
 */
export interface ToolResultSuccess {
    kind: 'Success';
    output: string;
    summary?: string;
    details?: string;
    affectedFiles: string[];
}

/**
 * Error tool result
 */
export interface ToolResultError {
    kind: 'Error';
    error: string;
    details?: string;
}

/**
 * Tool call result (union type)
 */
export type ToolResult = ToolResultSuccess | ToolResultError;

// ============ Tool Call Base Interface ============

/**
 * Tool call base interface
 */
export interface ToolCallItemBase extends DisplayItemBase {
    toolType: string;
    status: ToolCallStatus;
    startTime: number;
    endTime?: number;
    input: Record<string, unknown>;
    result?: ToolResult;
}

// ============ Specific Tool Calls ============

/**
 * Read tool call
 */
export interface ReadToolCall extends ToolCallItemBase {
    kind: 'ReadToolCall';
    toolType: typeof ToolConstants.READ;
}

/**
 * Get Read tool specific fields
 */
export function getReadToolFields(input: Record<string, unknown>) {
    return {
        filePath: (input.file_path as string) ?? (input.path as string) ?? undefined,
        offset: input.offset as number | undefined,
        limit: input.limit as number | undefined,
        viewRange: getViewRange(input)
    };
}

function getViewRange(input: Record<string, unknown>): [number, number] | undefined {
    const array = input.view_range as number[] | undefined;
    if (!array || array.length < 2) return undefined;
    return [array[0], array[1]];
}

/**
 * Write tool call
 */
export interface WriteToolCall extends ToolCallItemBase {
    kind: 'WriteToolCall';
    toolType: typeof ToolConstants.WRITE;
}

export function getWriteToolFields(input: Record<string, unknown>) {
    return {
        filePath: (input.file_path as string) ?? (input.path as string) ?? undefined,
        content: input.content as string | undefined
    };
}

/**
 * Edit tool call
 */
export interface EditToolCall extends ToolCallItemBase {
    kind: 'EditToolCall';
    toolType: typeof ToolConstants.EDIT;
}

export function getEditToolFields(input: Record<string, unknown>) {
    return {
        filePath: (input.file_path as string) ?? '',
        oldString: (input.old_string as string) ?? '',
        newString: (input.new_string as string) ?? '',
        replaceAll: (input.replace_all as boolean) ?? false
    };
}

/**
 * Edit operation for MultiEdit
 */
export interface EditOperation {
    oldString: string;
    newString: string;
    replaceAll: boolean;
}

/**
 * MultiEdit tool call
 */
export interface MultiEditToolCall extends ToolCallItemBase {
    kind: 'MultiEditToolCall';
    toolType: typeof ToolConstants.MULTI_EDIT;
}

export function getMultiEditToolFields(input: Record<string, unknown>) {
    const editsArray = input.edits as Array<Record<string, unknown>> | undefined;
    const edits: EditOperation[] = editsArray?.map(edit => ({
        oldString: (edit.old_string as string) ?? '',
        newString: (edit.new_string as string) ?? '',
        replaceAll: (edit.replace_all as boolean) ?? false
    })) ?? [];
    
    return {
        filePath: (input.file_path as string) ?? '',
        edits
    };
}

/**
 * Todo item
 */
export interface TodoItem {
    content: string;
    status: 'pending' | 'in_progress' | 'completed';
    activeForm: string;
}

/**
 * TodoWrite tool call
 */
export interface TodoWriteToolCall extends ToolCallItemBase {
    kind: 'TodoWriteToolCall';
    toolType: typeof ToolConstants.TODO_WRITE;
}

export function getTodoWriteToolFields(input: Record<string, unknown>) {
    const todosArray = input.todos as Array<Record<string, unknown>> | undefined;
    const todos: TodoItem[] = todosArray?.map(todo => ({
        content: (todo.content as string) ?? '',
        status: (todo.status as TodoItem['status']) ?? 'pending',
        activeForm: (todo.activeForm as string) ?? ''
    })) ?? [];
    
    return { todos };
}

/**
 * Bash tool call
 */
export interface BashToolCall extends ToolCallItemBase {
    kind: 'BashToolCall';
    toolType: typeof ToolConstants.BASH;
}

export function getBashToolFields(input: Record<string, unknown>) {
    return {
        command: (input.command as string) ?? '',
        description: input.description as string | undefined,
        cwd: input.cwd as string | undefined,
        timeout: input.timeout as number | undefined
    };
}

/**
 * Grep tool call
 */
export interface GrepToolCall extends ToolCallItemBase {
    kind: 'GrepToolCall';
    toolType: typeof ToolConstants.GREP;
}

export function getGrepToolFields(input: Record<string, unknown>) {
    return {
        pattern: (input.pattern as string) ?? '',
        path: input.path as string | undefined,
        glob: input.glob as string | undefined,
        type: input.type as string | undefined,
        outputMode: input.output_mode as string | undefined
    };
}

/**
 * Glob tool call
 */
export interface GlobToolCall extends ToolCallItemBase {
    kind: 'GlobToolCall';
    toolType: typeof ToolConstants.GLOB;
}

export function getGlobToolFields(input: Record<string, unknown>) {
    return {
        pattern: (input.pattern as string) ?? '',
        path: input.path as string | undefined
    };
}

/**
 * WebSearch tool call
 */
export interface WebSearchToolCall extends ToolCallItemBase {
    kind: 'WebSearchToolCall';
    toolType: typeof ToolConstants.WEB_SEARCH;
}

export function getWebSearchToolFields(input: Record<string, unknown>) {
    return {
        query: (input.query as string) ?? '',
        allowedDomains: (input.allowed_domains as string[]) ?? [],
        blockedDomains: (input.blocked_domains as string[]) ?? []
    };
}

/**
 * WebFetch tool call
 */
export interface WebFetchToolCall extends ToolCallItemBase {
    kind: 'WebFetchToolCall';
    toolType: typeof ToolConstants.WEB_FETCH;
}

export function getWebFetchToolFields(input: Record<string, unknown>) {
    return {
        url: (input.url as string) ?? '',
        prompt: (input.prompt as string) ?? ''
    };
}

/**
 * Task tool call
 */
export interface TaskToolCall extends ToolCallItemBase {
    kind: 'TaskToolCall';
    toolType: typeof ToolConstants.TASK;
}

/**
 * NotebookEdit tool call
 */
export interface NotebookEditToolCall extends ToolCallItemBase {
    kind: 'NotebookEditToolCall';
    toolType: typeof ToolConstants.NOTEBOOK_EDIT;
}

/**
 * BashOutput tool call
 */
export interface BashOutputToolCall extends ToolCallItemBase {
    kind: 'BashOutputToolCall';
    toolType: typeof ToolConstants.BASH_OUTPUT;
}

/**
 * KillShell tool call
 */
export interface KillShellToolCall extends ToolCallItemBase {
    kind: 'KillShellToolCall';
    toolType: typeof ToolConstants.KILL_SHELL;
}

/**
 * ExitPlanMode tool call
 */
export interface ExitPlanModeToolCall extends ToolCallItemBase {
    kind: 'ExitPlanModeToolCall';
    toolType: typeof ToolConstants.EXIT_PLAN_MODE;
}

/**
 * AskUserQuestion tool call
 */
export interface AskUserQuestionToolCall extends ToolCallItemBase {
    kind: 'AskUserQuestionToolCall';
    toolType: typeof ToolConstants.ASK_USER_QUESTION;
}

/**
 * Skill tool call
 */
export interface SkillToolCall extends ToolCallItemBase {
    kind: 'SkillToolCall';
    toolType: typeof ToolConstants.SKILL;
}

/**
 * SlashCommand tool call
 */
export interface SlashCommandToolCall extends ToolCallItemBase {
    kind: 'SlashCommandToolCall';
    toolType: typeof ToolConstants.SLASH_COMMAND;
}

/**
 * ListMcpResources tool call
 */
export interface ListMcpResourcesToolCall extends ToolCallItemBase {
    kind: 'ListMcpResourcesToolCall';
    toolType: typeof ToolConstants.LIST_MCP_RESOURCES;
}

/**
 * ReadMcpResource tool call
 */
export interface ReadMcpResourceToolCall extends ToolCallItemBase {
    kind: 'ReadMcpResourceToolCall';
    toolType: typeof ToolConstants.READ_MCP_RESOURCE;
}

/**
 * Generic tool call (for unknown or other MCP tools)
 */
export interface GenericToolCall extends ToolCallItemBase {
    kind: 'GenericToolCall';
}

// ============ Union Types ============

/**
 * All tool call types
 */
export type ToolCallItem =
    | ReadToolCall
    | WriteToolCall
    | EditToolCall
    | MultiEditToolCall
    | TodoWriteToolCall
    | BashToolCall
    | GrepToolCall
    | GlobToolCall
    | WebSearchToolCall
    | WebFetchToolCall
    | TaskToolCall
    | NotebookEditToolCall
    | BashOutputToolCall
    | KillShellToolCall
    | ExitPlanModeToolCall
    | AskUserQuestionToolCall
    | SkillToolCall
    | SlashCommandToolCall
    | ListMcpResourcesToolCall
    | ReadMcpResourceToolCall
    | GenericToolCall;

/**
 * All display item types
 */
export type DisplayItem =
    | UserMessageItem
    | AssistantTextItem
    | SystemMessageItem
    | ToolCallItem;

// ============ Factory Functions ============

/**
 * Create a tool call item with common fields
 */
export function createToolCallItem<T extends ToolCallItem['kind']>(
    kind: T,
    id: string,
    toolType: string,
    status: ToolCallStatus = ToolCallStatus.RUNNING,
    input: Record<string, unknown> = {}
): ToolCallItemBase & { kind: T } {
    const now = Date.now();
    return {
        kind,
        id,
        timestamp: now,
        toolType,
        status,
        startTime: now,
        endTime: undefined,
        input,
        result: undefined
    } as ToolCallItemBase & { kind: T };
}

/**
 * Create a user message item
 */
export function createUserMessageItem(
    id: string,
    content: string,
    images: ImageBlock[] = [],
    contexts: ContextReference[] = []
): UserMessageItem {
    return {
        kind: 'UserMessageItem',
        id,
        timestamp: Date.now(),
        content,
        images,
        contexts,
        requestStats: undefined,
        isStreaming: false
    };
}

/**
 * Create an assistant text item
 */
export function createAssistantTextItem(
    id: string,
    content: string,
    isLastInMessage: boolean = false
): AssistantTextItem {
    return {
        kind: 'AssistantTextItem',
        id,
        timestamp: Date.now(),
        content,
        stats: undefined,
        isLastInMessage
    };
}

/**
 * Create a system message item
 */
export function createSystemMessageItem(
    id: string,
    content: string,
    level: SystemMessageLevel = SystemMessageLevel.INFO
): SystemMessageItem {
    return {
        kind: 'SystemMessageItem',
        id,
        timestamp: Date.now(),
        content,
        level
    };
}

/**
 * Create a success tool result
 */
export function createSuccessResult(
    output: string,
    summary?: string,
    details?: string,
    affectedFiles: string[] = []
): ToolResultSuccess {
    return {
        kind: 'Success',
        output,
        summary,
        details,
        affectedFiles
    };
}

/**
 * Create an error tool result
 */
export function createErrorResult(
    error: string,
    details?: string
): ToolResultError {
    return {
        kind: 'Error',
        error,
        details
    };
}

// ============ Type Guards ============

/**
 * Check if display item is a user message
 */
export function isUserMessageItem(item: DisplayItem): item is UserMessageItem {
    return (item as UserMessageItem).kind === 'UserMessageItem';
}

/**
 * Check if display item is an assistant text
 */
export function isAssistantTextItem(item: DisplayItem): item is AssistantTextItem {
    return (item as AssistantTextItem).kind === 'AssistantTextItem';
}

/**
 * Check if display item is a system message
 */
export function isSystemMessageItem(item: DisplayItem): item is SystemMessageItem {
    return (item as SystemMessageItem).kind === 'SystemMessageItem';
}

/**
 * Check if display item is a tool call
 */
export function isToolCallItem(item: DisplayItem): item is ToolCallItem {
    return 'toolType' in item && 'status' in item;
}

/**
 * Check if tool result is success
 */
export function isSuccessResult(result: ToolResult): result is ToolResultSuccess {
    return result.kind === 'Success';
}

/**
 * Check if tool result is error
 */
export function isErrorResult(result: ToolResult): result is ToolResultError {
    return result.kind === 'Error';
}

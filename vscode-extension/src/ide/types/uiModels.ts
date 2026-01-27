/**
 * UI Models
 * 
 * UI model type definitions (backward compatible)
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/types/UiModels.kt
 */

/**
 * AI Model enum
 */
export enum AiModel {
    SONNET = 'SONNET',
    OPUS = 'OPUS',
    HAIKU = 'HAIKU',
    OPUS_PLAN = 'OPUS_PLAN'
}

/**
 * UI Permission Mode enum
 */
export enum UiPermissionMode {
    DEFAULT = 'DEFAULT',
    ACCEPT = 'ACCEPT',
    BYPASS = 'BYPASS',
    PLAN = 'PLAN'
}

/**
 * Message role
 */
export enum MessageRole {
    USER = 'USER',
    ASSISTANT = 'ASSISTANT',
    SYSTEM = 'SYSTEM',
    ERROR = 'ERROR'
}

/**
 * Message status
 */
export enum MessageStatus {
    PENDING = 'PENDING',
    STREAMING = 'STREAMING',
    COMPLETE = 'COMPLETE',
    ERROR = 'ERROR'
}

/**
 * Token usage info (UI side, different from SDK's TokenUsage)
 */
export interface UiTokenUsage {
    inputTokens: number;
    outputTokens: number;
    cacheCreationTokens: number;
    cacheReadTokens: number;
    totalTokens: number;
}

export const defaultUiTokenUsage: UiTokenUsage = {
    inputTokens: 0,
    outputTokens: 0,
    cacheCreationTokens: 0,
    cacheReadTokens: 0,
    totalTokens: 0
};

/**
 * Session object (legacy, backward compatible)
 */
export interface SessionObject {
    id: string;
    name: string;
    createdAt: number;
    modelId?: string;
}

/**
 * Context reference
 */
export interface ContextReference {
    type: string;
    uri: string;
    displayType: string;
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

/**
 * Enhanced message (legacy, backward compatible)
 */
export interface EnhancedMessage {
    id: string;
    role: MessageRole;
    content: string;
    timestamp: number;
    contexts?: ContextReference[];
    tokenUsage?: UiTokenUsage;
}

/**
 * Legacy tool call (for adapter layer)
 */
export interface LegacyToolCall {
    name: string;
    id: string;
    status: string;
    input?: Record<string, unknown>;
    result?: string;
    viewModel?: unknown;
}

/**
 * Create default session object
 */
export function createSessionObject(
    id: string,
    name: string,
    modelId?: string
): SessionObject {
    return {
        id,
        name,
        createdAt: Date.now(),
        modelId
    };
}

/**
 * Create enhanced message
 */
export function createEnhancedMessage(
    id: string,
    role: MessageRole,
    content: string,
    contexts?: ContextReference[],
    tokenUsage?: UiTokenUsage
): EnhancedMessage {
    return {
        id,
        role,
        content,
        timestamp: Date.now(),
        contexts,
        tokenUsage
    };
}

/**
 * Session Types
 * 
 * Simplified session types for VS Code extension
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/types/SessionTypes.kt
 */

import { ContextReference, EnhancedMessage, AiModel, UiPermissionMode } from './uiModels';

/**
 * Session state
 * 
 * Represents the state of a Claude session
 */
export interface SessionState {
    sessionId: string;
    messages: EnhancedMessage[];
    contexts: ContextReference[];
    isGenerating: boolean;
    selectedModel: AiModel;
    permissionMode: UiPermissionMode;
}

/**
 * Session update event
 * 
 * Used for session state synchronization
 */
export interface SessionUpdate {
    sessionId: string;
    isActive: boolean;
}

/**
 * Create default session state
 */
export function createDefaultSessionState(sessionId: string): SessionState {
    return {
        sessionId,
        messages: [],
        contexts: [],
        isGenerating: false,
        selectedModel: AiModel.OPUS,
        permissionMode: UiPermissionMode.DEFAULT
    };
}

/**
 * Create session update
 */
export function createSessionUpdate(sessionId: string, isActive: boolean): SessionUpdate {
    return {
        sessionId,
        isActive
    };
}

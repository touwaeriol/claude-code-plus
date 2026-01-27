/**
 * Session State Sync Interface
 * 
 * Defines the state sync contract between UI and background service
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/interfaces/SessionStateSync.kt
 */

import { SessionState, SessionUpdate } from '../types';

/**
 * Query options for session execution
 */
export interface QueryOptions {
    model?: string;
    permissionMode?: string;
    maxThinkingTokens?: number;
    [key: string]: unknown;
}

/**
 * Service statistics
 */
export interface ServiceStats {
    activeSessions: number;
    totalMessages: number;
    [key: string]: unknown;
}

/**
 * Session State Sync interface
 * 
 * Defines the state sync contract between UI and background service,
 * ensuring session data consistency across different components.
 */
export interface SessionStateSync {
    /**
     * Save session state to background service
     * 
     * @param sessionId Session ID
     * @param state Session state
     */
    saveSessionState(sessionId: string, state: SessionState): Promise<void>;
    
    /**
     * Load session state from background service
     * 
     * @param sessionId Session ID
     * @returns Session state, or undefined if not exists
     */
    loadSessionState(sessionId: string): Promise<SessionState | undefined>;
    
    /**
     * Observe session state updates
     * 
     * @param sessionId Session ID
     * @param callback Callback for state updates
     * @returns Unsubscribe function
     */
    observeSessionUpdates(
        sessionId: string,
        callback: (state: SessionState) => void
    ): () => void;
    
    /**
     * Observe all session updates in project
     * 
     * @param projectPath Project path
     * @param callback Callback for state updates
     * @returns Unsubscribe function
     */
    observeProjectUpdates(
        projectPath: string,
        callback: (states: Map<string, SessionState>) => void
    ): () => void;
    
    /**
     * Start background session execution
     * 
     * @param sessionId Session ID (auto-generated if undefined)
     * @param projectPath Project path
     * @param prompt User input
     * @param options Execution options
     * @param callback Callback for session updates
     * @returns Unsubscribe function
     */
    startBackgroundExecution(
        sessionId: string | undefined,
        projectPath: string,
        prompt: string,
        options: QueryOptions,
        callback: (update: SessionUpdate) => void
    ): Promise<() => void>;
    
    /**
     * Terminate background session
     * 
     * @param sessionId Session ID
     */
    terminateBackgroundSession(sessionId: string): Promise<void>;
    
    /**
     * Check if session is running in background
     * 
     * @param sessionId Session ID
     * @returns true if session is running in background
     */
    isSessionRunningInBackground(sessionId: string): Promise<boolean>;
    
    /**
     * Get background service statistics
     * 
     * @returns Statistics object
     */
    getBackgroundServiceStats(): Promise<ServiceStats>;
    
    /**
     * Recover session history on demand
     * 
     * @param sessionId Session ID
     * @param projectPath Project path
     * @returns true if recovery was successful
     */
    recoverSessionHistory(sessionId: string, projectPath: string): Promise<boolean>;
}

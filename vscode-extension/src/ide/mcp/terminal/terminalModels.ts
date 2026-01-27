/**
 * Terminal Models
 * 
 * Data models and types for Terminal MCP server.
 * Translated from JetBrains plugin's TerminalModels.kt
 */

import * as vscode from 'vscode';

/**
 * Command wait result types
 */
export type CommandWaitResult = 
    | { type: 'completed' }
    | { type: 'timeout' }
    | { type: 'interrupted' }
    | { type: 'apiUnavailable' };

/**
 * Terminal session information
 */
export interface TerminalSession {
    /** Unique session ID */
    id: string;
    /** Display name */
    name: string;
    /** Shell type (e.g., 'bash', 'powershell', 'git-bash') */
    shellType: string;
    /** VS Code terminal instance */
    terminal: vscode.Terminal;
    /** Creation timestamp */
    createdAt: number;
    /** Last command execution timestamp */
    lastCommandAt: number;
    /** Whether the session is running in background */
    isBackground: boolean;
    /** Output buffer (limited by VS Code API) */
    outputBuffer: string[];
    /** Last executed command */
    lastCommand?: string;
}

/**
 * Search match result
 */
export interface SearchMatch {
    /** Line number (1-based) */
    lineNumber: number;
    /** The matched line content */
    line: string;
    /** Context including surrounding lines */
    context: string;
}

/**
 * Terminal task update listener callback type
 */
export type TerminalTaskUpdateListener = (
    toolUseId: string,
    sessionId: string,
    action: 'started' | 'completed' | 'backgrounded',
    command: string,
    isBackground: boolean,
    startTime: number,
    elapsedMs?: number
) => Promise<void>;

/**
 * Command execution result
 */
export interface ExecuteResult {
    success: boolean;
    sessionId: string;
    sessionName?: string;
    background?: boolean;
    output?: string;
    truncated?: boolean;
    totalLines?: number;
    totalChars?: number;
    error?: string;
}

/**
 * Command interrupt result
 */
export interface InterruptResult {
    success: boolean;
    sessionId: string;
    /** Signal type sent */
    signal?: string;
    /** Whether a command was running before interrupt */
    wasRunning?: boolean;
    /** Whether the command is still running after interrupt */
    isStillRunning?: boolean;
    message?: string;
    error?: string;
}

/**
 * Output read result
 */
export interface ReadResult {
    success: boolean;
    sessionId: string;
    output?: string;
    /** Whether a command is currently running (undefined = unknown) */
    isRunning?: boolean;
    lineCount: number;
    searchMatches?: SearchMatch[];
    error?: string;
    /** Whether wait timed out */
    waitTimedOut?: boolean;
    /** Wait-related message */
    waitMessage?: string;
}

/**
 * Shell type information
 */
export interface ShellTypeInfo {
    name: string;
    displayName: string;
    command?: string;
    isDefault: boolean;
}

/**
 * Terminal background task information
 * Used for tracking running MCP tool calls
 */
export interface TerminalBackgroundTask {
    /** Terminal session ID */
    sessionId: string;
    /** MCP tool use ID */
    toolUseId: string;
    /** Command being executed */
    command: string;
    /** Start timestamp (milliseconds) */
    startTime: number;
    /** Whether moved to background */
    isBackground: boolean;
    /** Timestamp when moved to background */
    backgroundTime?: number;
}

/**
 * Get elapsed time for a background task
 */
export function getTaskElapsedMs(task: TerminalBackgroundTask): number {
    return Date.now() - task.startTime;
}

/**
 * Session info for list results
 */
export interface SessionInfo {
    id: string;
    name: string;
    shellType: string;
    isRunning: boolean;
    outputPreview?: string;
}

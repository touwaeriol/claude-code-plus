/**
 * Terminal Background Manager
 * 
 * Handles terminal task background operations.
 * Extracts runToBackground logic for reuse.
 * Translated from JetBrains plugin's TerminalBackgroundManager.kt
 */

import type { TerminalSessionManager } from './terminalSessionManager';

/**
 * Terminal background operation result
 */
export interface TerminalBackgroundResult {
    success: boolean;
    backgroundedIds: string[];
    count: number;
    error?: string;
}

/**
 * Manager for handling terminal task background operations.
 */
export class TerminalBackgroundManager {
    private static instance: TerminalBackgroundManager | null = null;

    private constructor() {}

    /**
     * Get singleton instance
     */
    static getInstance(): TerminalBackgroundManager {
        if (!TerminalBackgroundManager.instance) {
            TerminalBackgroundManager.instance = new TerminalBackgroundManager();
        }
        return TerminalBackgroundManager.instance;
    }

    /**
     * Move terminal tasks to background.
     * 
     * @param servers Map of session ID to TerminalSessionManager instances
     * @param toolUseId Optional specific task ID to background. If undefined, backgrounds all running tasks.
     * @returns TerminalBackgroundResult with status and list of backgrounded task IDs
     */
    runToBackground(
        servers: Map<string, TerminalSessionManager>,
        toolUseId?: string
    ): TerminalBackgroundResult {
        const backgroundedIds: string[] = [];

        if (toolUseId !== undefined) {
            // Single task mode: background specific task
            for (const [_sessionId, server] of servers) {
                if (server.markTaskAsBackground(toolUseId)) {
                    backgroundedIds.push(toolUseId);
                    console.log(`[TerminalBackgroundManager] Terminal task moved to background: ${toolUseId}`);
                    break;
                }
            }

            if (backgroundedIds.length > 0) {
                return {
                    success: true,
                    backgroundedIds,
                    count: 1
                };
            } else {
                return {
                    success: false,
                    backgroundedIds: [],
                    count: 0,
                    error: `Task not found: ${toolUseId}`
                };
            }
        } else {
            // Batch mode: background all running tasks
            for (const [sessionId, server] of servers) {
                const tasks = server.getBackgroundableTasks(0); // Get all running tasks
                for (const task of tasks) {
                    if (server.markTaskAsBackground(task.toolUseId)) {
                        backgroundedIds.push(task.toolUseId);
                        console.log(`[TerminalBackgroundManager] Terminal task moved to background: ${task.toolUseId} (session: ${sessionId})`);
                    }
                }
            }

            return {
                success: true,
                backgroundedIds,
                count: backgroundedIds.length
            };
        }
    }
}

// Export singleton instance for convenience
export const terminalBackgroundManager = TerminalBackgroundManager.getInstance();

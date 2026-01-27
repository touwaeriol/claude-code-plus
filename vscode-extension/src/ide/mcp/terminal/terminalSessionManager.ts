/**
 * Terminal Session Manager
 * 
 * Manages VS Code integrated terminal sessions, supporting command execution,
 * output reading, session management, and background task tracking.
 * Translated from JetBrains plugin's TerminalSessionManager.kt
 */

import * as vscode from 'vscode';
import * as os from 'os';
import { ShellResolver } from './shellResolver';
import {
    TerminalSession,
    TerminalBackgroundTask,
    TerminalTaskUpdateListener,
    ExecuteResult,
    InterruptResult,
    ReadResult,
    SearchMatch,
    ShellTypeInfo,
    CommandWaitResult,
    getTaskElapsedMs
} from './terminalModels';

/**
 * Terminal Session Manager
 * 
 * Manages VS Code integrated terminal sessions.
 */
export class TerminalSessionManager {
    private sessions: Map<string, TerminalSession> = new Map();
    private sessionCounter = 0;

    // AI session -> default terminal ID mapping
    private aiSessionDefaultTerminals: Map<string, string> = new Map();

    // AI session -> overflow terminal list (created when default is busy)
    private aiSessionOverflowTerminals: Map<string, string[]> = new Map();

    // Running tasks tracking (toolUseId -> TerminalBackgroundTask)
    private runningTasks: Map<string, TerminalBackgroundTask> = new Map();

    // Task update listener
    private taskUpdateListener: TerminalTaskUpdateListener | null = null;

    // Current AI session ID
    private _currentAiSessionId: string | null = null;

    // Terminal close event subscription
    private terminalCloseListener: vscode.Disposable | null = null;

    constructor() {
        // Subscribe to terminal close events
        this.terminalCloseListener = vscode.window.onDidCloseTerminal(terminal => {
            this.onTerminalClosed(terminal);
        });
    }

    /**
     * Get current AI session ID
     */
    get currentAiSessionId(): string | null {
        return this._currentAiSessionId;
    }

    /**
     * Set task update listener
     * Used for notifying frontend of task status changes
     */
    setTaskUpdateListener(listener: TerminalTaskUpdateListener | null): void {
        this.taskUpdateListener = listener;
        console.log(`[TerminalSessionManager] Task update listener ${listener ? 'registered' : 'unregistered'}`);
    }

    /**
     * Notify task update (internal use)
     */
    private async notifyTaskUpdate(
        toolUseId: string,
        sessionId: string,
        action: 'started' | 'completed' | 'backgrounded',
        command: string,
        isBackground: boolean,
        startTime: number,
        elapsedMs?: number
    ): Promise<void> {
        const listener = this.taskUpdateListener;
        if (!listener) return;

        try {
            await listener(toolUseId, sessionId, action, command, isBackground, startTime, elapsedMs);
        } catch (e) {
            console.warn(`[TerminalSessionManager] Failed to notify task update: ${e}`);
        }
    }

    /**
     * Set current AI session ID
     */
    setCurrentAiSession(aiSessionId: string | null): void {
        this._currentAiSessionId = aiSessionId;
        console.log(`[TerminalSessionManager] Set current AI session: ${aiSessionId}`);
    }

    /**
     * Terminal close cleanup callback
     */
    private onTerminalClosed(terminal: vscode.Terminal): void {
        // Find and remove session for this terminal
        for (const [sessionId, session] of this.sessions) {
            if (session.terminal === terminal) {
                this.sessions.delete(sessionId);

                // Remove from default terminal mapping
                for (const [aiSession, termId] of this.aiSessionDefaultTerminals) {
                    if (termId === sessionId) {
                        this.aiSessionDefaultTerminals.delete(aiSession);
                    }
                }

                // Remove from overflow terminal lists
                for (const [_aiSession, overflowList] of this.aiSessionOverflowTerminals) {
                    const index = overflowList.indexOf(sessionId);
                    if (index !== -1) {
                        overflowList.splice(index, 1);
                    }
                }

                console.log(`[TerminalSessionManager] Cleaned up mappings for closed terminal: ${sessionId}`);
                break;
            }
        }
    }

    /**
     * Get default terminal ID for current AI session
     */
    getDefaultTerminalId(): string | null {
        const aiSessionId = this._currentAiSessionId;
        if (!aiSessionId) return null;
        return this.aiSessionDefaultTerminals.get(aiSessionId) ?? null;
    }

    /**
     * Check if terminal belongs to current AI session
     */
    isSessionOwnedByCurrentAiSession(terminalId: string): boolean {
        const currentTerminalIds = new Set(this.getCurrentSessionTerminals().map(s => s.id));
        return currentTerminalIds.has(terminalId);
    }

    /**
     * Validate session ownership, returns error response if validation fails
     */
    validateSessionOwnership(sessionId: string): { success: false; error: string } | null {
        if (!this.isSessionOwnedByCurrentAiSession(sessionId)) {
            return {
                success: false,
                error: `Session not found or not owned by current AI session: ${sessionId}`
            };
        }
        return null;
    }

    /**
     * Get or create default terminal for current AI session
     * If default terminal is busy, creates overflow terminal
     */
    getOrCreateDefaultTerminal(shellName?: string): TerminalSession | null {
        const aiSessionId = this._currentAiSessionId;
        if (!aiSessionId) {
            return this.createSession(undefined, shellName);
        }

        // Check if default terminal exists
        const existingTerminalId = this.aiSessionDefaultTerminals.get(aiSessionId);
        if (existingTerminalId) {
            const existingSession = this.sessions.get(existingTerminalId);
            if (existingSession) {
                // Check if default terminal is busy (assume not busy for VS Code)
                // VS Code doesn't have a reliable way to check if a command is running
                console.log(`[TerminalSessionManager] Using existing default terminal for AI session ${aiSessionId}: ${existingTerminalId}`);
                return existingSession;
            }
            // Terminal was deleted, remove mapping
            this.aiSessionDefaultTerminals.delete(aiSessionId);
            console.log(`[TerminalSessionManager] Default terminal ${existingTerminalId} was deleted, creating new one`);
        }

        // Create new default terminal
        const newSession = this.createSession('Default Terminal', shellName);
        if (newSession) {
            this.aiSessionDefaultTerminals.set(aiSessionId, newSession.id);
            console.log(`[TerminalSessionManager] Created default terminal for AI session ${aiSessionId}: ${newSession.id}`);
        }
        return newSession;
    }

    /**
     * Find available overflow terminal (not running command)
     */
    private findAvailableOverflowTerminal(aiSessionId: string): TerminalSession | null {
        const overflowIds = this.aiSessionOverflowTerminals.get(aiSessionId);
        if (!overflowIds) return null;

        // Clean up deleted terminals
        const validIds = overflowIds.filter(id => this.sessions.has(id));
        if (validIds.length !== overflowIds.length) {
            this.aiSessionOverflowTerminals.set(aiSessionId, validIds);
        }

        // Find idle overflow terminal (simplified - assume first one is available)
        for (const terminalId of validIds) {
            const session = this.sessions.get(terminalId);
            if (session) {
                return session;
            }
        }
        return null;
    }

    /**
     * Get all terminals for current AI session (default + overflow)
     */
    getCurrentSessionTerminals(): TerminalSession[] {
        const aiSessionId = this._currentAiSessionId;
        if (!aiSessionId) return [];

        const result: TerminalSession[] = [];

        // Add default terminal
        const defaultId = this.aiSessionDefaultTerminals.get(aiSessionId);
        if (defaultId) {
            const session = this.sessions.get(defaultId);
            if (session) result.push(session);
        }

        // Add overflow terminals
        const overflowIds = this.aiSessionOverflowTerminals.get(aiSessionId);
        if (overflowIds) {
            for (const terminalId of overflowIds) {
                const session = this.sessions.get(terminalId);
                if (session) result.push(session);
            }
        }

        return result;
    }

    /**
     * Create new terminal session
     * 
     * @param name Session name
     * @param shellName Shell name (e.g., "git-bash", "powershell"), uses default if null
     */
    createSession(name?: string, shellName?: string): TerminalSession | null {
        try {
            const sessionId = `terminal-${++this.sessionCounter}`;
            const sessionName = name ?? `Claude Terminal ${this.sessionCounter}`;

            // Determine actual shell name
            const actualShellName = shellName ?? this.getDefaultShellName();

            // Get shell path
            const shellPath = ShellResolver.getShellPath(actualShellName);
            console.log(`[TerminalSessionManager] createSession - requested: ${shellName}, actual: ${actualShellName}, path: ${shellPath}`);

            // Create terminal with options
            const terminalOptions: vscode.TerminalOptions = {
                name: sessionName
            };

            if (shellPath) {
                terminalOptions.shellPath = shellPath;
            }

            // Get working directory
            const workspaceFolders = vscode.workspace.workspaceFolders;
            if (workspaceFolders && workspaceFolders.length > 0) {
                terminalOptions.cwd = workspaceFolders[0].uri;
            }

            const terminal = vscode.window.createTerminal(terminalOptions);

            const session: TerminalSession = {
                id: sessionId,
                name: sessionName,
                shellType: actualShellName,
                terminal,
                createdAt: Date.now(),
                lastCommandAt: Date.now(),
                isBackground: false,
                outputBuffer: [],
                lastCommand: undefined
            };

            this.sessions.set(sessionId, session);
            console.log(`[TerminalSessionManager] Created terminal session: ${sessionId} (${sessionName}), shell=${actualShellName}`);

            return session;
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to create terminal session:`, e);
            return null;
        }
    }

    /**
     * Get or create session
     */
    getOrCreateSession(sessionId?: string): TerminalSession | null {
        if (sessionId && this.sessions.has(sessionId)) {
            return this.sessions.get(sessionId) ?? null;
        }
        return this.createSession();
    }

    /**
     * Get session by ID
     */
    getSession(sessionId: string): TerminalSession | undefined {
        return this.sessions.get(sessionId);
    }

    /**
     * Get all sessions
     */
    getAllSessions(): TerminalSession[] {
        return Array.from(this.sessions.values());
    }

    /**
     * Execute command asynchronously (returns immediately, doesn't wait for completion)
     * 
     * @param sessionId Session ID
     * @param command Command to execute
     * @returns Execution result (only indicates if command was sent successfully)
     */
    executeCommandAsync(sessionId: string, command: string): ExecuteResult {
        const session = this.getSession(sessionId);
        if (!session) {
            return {
                success: false,
                sessionId,
                error: `Session not found: ${sessionId}`
            };
        }

        try {
            session.lastCommandAt = Date.now();
            session.lastCommand = command;

            session.terminal.show();
            session.terminal.sendText(command);

            return {
                success: true,
                sessionId: session.id,
                sessionName: session.name,
                background: true // Always treated as background execution
            };
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to execute command in session ${session.id}:`, e);
            return {
                success: false,
                sessionId: session.id,
                error: e instanceof Error ? e.message : 'Unknown error'
            };
        }
    }

    /**
     * Execute command (with optional wait)
     * 
     * @param sessionId Session ID, creates new if undefined
     * @param command Command to execute
     * @param background Whether to execute in background
     * @param timeoutMs Timeout for foreground execution (milliseconds)
     */
    async executeCommand(
        sessionId: string | undefined,
        command: string,
        background: boolean = false,
        timeoutMs: number = 300000
    ): Promise<ExecuteResult> {
        const session = this.getOrCreateSession(sessionId);
        if (!session) {
            return {
                success: false,
                sessionId: '',
                error: 'Failed to create terminal session'
            };
        }

        try {
            session.isBackground = background;
            session.lastCommandAt = Date.now();
            session.lastCommand = command;

            session.terminal.show();
            session.terminal.sendText(command);

            if (background) {
                // Background execution: return immediately
                return {
                    success: true,
                    sessionId: session.id,
                    sessionName: session.name,
                    background: true
                };
            } else {
                // Foreground execution: wait a bit and return
                // Note: VS Code Terminal API doesn't support command completion detection
                const waitTime = Math.min(timeoutMs, 5000);
                await new Promise(resolve => setTimeout(resolve, waitTime));

                return {
                    success: true,
                    sessionId: session.id,
                    sessionName: session.name,
                    background: false,
                    output: '(Output capture not available in VS Code Terminal API. Check terminal window.)'
                };
            }
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to execute command in session ${session.id}:`, e);
            return {
                success: false,
                sessionId: session.id,
                error: e instanceof Error ? e.message : 'Unknown error'
            };
        }
    }

    /**
     * Read session output
     * 
     * Note: VS Code Terminal API has limited output capture support.
     * 
     * @param sessionId Session ID
     * @param maxLines Maximum lines
     * @param search Search pattern (regex)
     * @param contextLines Context lines for search results
     * @param waitForIdle Wait for command completion
     * @param timeoutMs Wait timeout (milliseconds)
     */
    readOutput(
        sessionId: string,
        maxLines: number = 1000,
        search?: string,
        contextLines: number = 2,
        waitForIdle: boolean = false,
        timeoutMs: number = 30000
    ): ReadResult {
        const session = this.getSession(sessionId);
        if (!session) {
            return {
                success: false,
                sessionId,
                lineCount: 0,
                error: `Session not found: ${sessionId}`
            };
        }

        try {
            // VS Code Terminal API doesn't provide direct output access
            // Return a note indicating this limitation
            if (search) {
                return {
                    success: true,
                    sessionId,
                    isRunning: undefined, // Unknown
                    lineCount: 0,
                    searchMatches: [],
                    waitMessage: 'Direct output capture not available in VS Code Terminal API. Check terminal window.'
                };
            } else {
                return {
                    success: true,
                    sessionId,
                    output: '(Direct output capture not available in VS Code Terminal API. Check terminal window.)',
                    isRunning: undefined, // Unknown
                    lineCount: 0
                };
            }
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to read output from session ${sessionId}:`, e);
            return {
                success: false,
                sessionId,
                lineCount: 0,
                error: e instanceof Error ? e.message : 'Unknown error'
            };
        }
    }

    /**
     * Interrupt currently running command
     * 
     * @param sessionId Session ID
     * @param signal Signal type: SIGINT (Ctrl+C), SIGQUIT (Ctrl+\), SIGTSTP (Ctrl+Z)
     */
    interruptCommand(sessionId: string, signal: string = 'SIGINT'): InterruptResult {
        const session = this.getSession(sessionId);
        if (!session) {
            return {
                success: false,
                sessionId,
                signal,
                error: `Session not found: ${sessionId}`
            };
        }

        try {
            // Send appropriate control character
            let controlChar: string;
            switch (signal.toUpperCase()) {
                case 'SIGINT':
                    controlChar = '\x03'; // Ctrl+C
                    break;
                case 'SIGQUIT':
                    controlChar = '\x1c'; // Ctrl+\
                    break;
                case 'SIGTSTP':
                    controlChar = '\x1a'; // Ctrl+Z
                    break;
                default:
                    controlChar = '\x03'; // Default to Ctrl+C
            }

            session.terminal.sendText(controlChar, false);

            const signalDesc = signal.toUpperCase() === 'SIGINT' ? 'SIGINT (Ctrl+C)' :
                             signal.toUpperCase() === 'SIGQUIT' ? 'SIGQUIT (Ctrl+\\)' :
                             signal.toUpperCase() === 'SIGTSTP' ? 'SIGTSTP (Ctrl+Z)' : signal;

            return {
                success: true,
                sessionId,
                signal,
                wasRunning: undefined, // Unknown
                isStillRunning: undefined, // Unknown
                message: `${signalDesc} sent (command status unknown)`
            };
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to send ${signal} to session ${sessionId}:`, e);
            return {
                success: false,
                sessionId,
                signal,
                error: e instanceof Error ? e.message : 'Unknown error'
            };
        }
    }

    /**
     * Kill session
     */
    killSession(sessionId: string): boolean {
        const session = this.sessions.get(sessionId);
        if (!session) return false;

        try {
            session.terminal.dispose();
            this.sessions.delete(sessionId);
            console.log(`[TerminalSessionManager] Killed terminal session: ${sessionId}`);
            return true;
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to kill session ${sessionId}:`, e);
            return false;
        }
    }

    /**
     * Rename session
     */
    renameSession(sessionId: string, newName: string): boolean {
        const session = this.sessions.get(sessionId);
        if (!session) return false;

        try {
            // VS Code Terminal doesn't support direct renaming
            // We update our internal tracking only
            session.name = newName;
            console.log(`[TerminalSessionManager] Renamed terminal session ${sessionId} to: ${newName}`);
            return true;
        } catch (e) {
            console.error(`[TerminalSessionManager] Failed to rename session ${sessionId}:`, e);
            return false;
        }
    }

    /**
     * Get available shell types
     */
    getAvailableShellTypes(): ShellTypeInfo[] {
        const defaultShell = this.getDefaultShellName();
        const detectedShells = ShellResolver.detectInstalledShells();

        return detectedShells.map(shell => {
            const normalizedName = ShellResolver.normalizeShellName(shell.name);
            return {
                name: normalizedName,
                displayName: shell.name,
                command: normalizedName,
                isDefault: normalizedName === defaultShell
            };
        });
    }

    /**
     * Get default shell name
     */
    private getDefaultShellName(): string {
        const config = vscode.workspace.getConfiguration('claudeCodePlus');
        const configuredDefault = config.get<string>('terminal.defaultShell');
        
        if (configuredDefault) {
            return configuredDefault;
        }

        // Platform default
        const platform = os.platform();
        if (platform === 'win32') {
            return 'git-bash';
        }
        return 'bash';
    }

    // ==================== Background Task Tracking ====================

    /**
     * Record task start
     * Called when MCP tool call starts
     */
    recordTaskStart(sessionId: string, toolUseId: string, command: string): void {
        const startTime = Date.now();
        const task: TerminalBackgroundTask = {
            sessionId,
            toolUseId,
            command,
            startTime,
            isBackground: false
        };
        this.runningTasks.set(toolUseId, task);
        console.log(`[TerminalSessionManager] Recorded task start: toolUseId=${toolUseId}, sessionId=${sessionId}, command=${command.substring(0, 50)}...`);

        // Notify frontend that task has started
        this.notifyTaskUpdate(
            toolUseId,
            sessionId,
            'started',
            command,
            false,
            startTime
        );
    }

    /**
     * Record task completion (remove tracking)
     */
    recordTaskComplete(toolUseId: string): void {
        const task = this.runningTasks.get(toolUseId);
        if (task) {
            this.runningTasks.delete(toolUseId);
            const elapsedMs = getTaskElapsedMs(task);
            console.log(`[TerminalSessionManager] Task completed: toolUseId=${toolUseId}, elapsed=${elapsedMs}ms`);

            // Notify frontend that task has completed
            this.notifyTaskUpdate(
                toolUseId,
                task.sessionId,
                'completed',
                task.command,
                task.isBackground,
                task.startTime,
                elapsedMs
            );
        }
    }

    /**
     * Mark task as background execution
     * @returns true if successful, false if task not found
     */
    markTaskAsBackground(toolUseId: string): boolean {
        const task = this.runningTasks.get(toolUseId);
        if (!task) return false;

        if (task.isBackground) {
            console.log(`[TerminalSessionManager] Task already in background: toolUseId=${toolUseId}`);
            return true;
        }

        task.isBackground = true;
        task.backgroundTime = Date.now();
        console.log(`[TerminalSessionManager] Task moved to background: toolUseId=${toolUseId}, sessionId=${task.sessionId}`);

        // Notify frontend that task has been backgrounded
        this.notifyTaskUpdate(
            toolUseId,
            task.sessionId,
            'backgrounded',
            task.command,
            true,
            task.startTime,
            getTaskElapsedMs(task)
        );
        return true;
    }

    /**
     * Check if task is in background
     */
    isTaskInBackground(toolUseId: string): boolean {
        return this.runningTasks.get(toolUseId)?.isBackground === true;
    }

    /**
     * Get backgroundable tasks list
     * Returns tasks running longer than threshold and not yet backgrounded
     */
    getBackgroundableTasks(thresholdMs: number = 5000): TerminalBackgroundTask[] {
        const now = Date.now();
        const allTasks = Array.from(this.runningTasks.values());
        const result = allTasks.filter(task => 
            !task.isBackground && (now - task.startTime) >= thresholdMs
        );
        console.log(`[TerminalSessionManager] getBackgroundableTasks: runningTasks=${allTasks.length}, filtered=${result.length}, threshold=${thresholdMs}ms`);
        return result;
    }

    /**
     * Get running task for specified session
     */
    getRunningTaskBySession(sessionId: string): TerminalBackgroundTask | undefined {
        for (const task of this.runningTasks.values()) {
            if (task.sessionId === sessionId && !task.isBackground) {
                return task;
            }
        }
        return undefined;
    }

    /**
     * Get task by toolUseId
     */
    getTask(toolUseId: string): TerminalBackgroundTask | undefined {
        return this.runningTasks.get(toolUseId);
    }

    /**
     * Clean up all sessions
     */
    dispose(): void {
        // Unsubscribe from terminal close events
        this.terminalCloseListener?.dispose();

        // Kill all sessions
        for (const sessionId of this.sessions.keys()) {
            this.killSession(sessionId);
        }
        this.sessions.clear();
        this.runningTasks.clear();
        this.aiSessionDefaultTerminals.clear();
        this.aiSessionOverflowTerminals.clear();
    }
}

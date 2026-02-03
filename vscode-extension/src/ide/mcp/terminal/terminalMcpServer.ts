/**
 * Terminal MCP Server for VS Code
 * 
 * Provides terminal command execution and session management.
 */

import * as vscode from 'vscode';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import * as z from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';

/**
 * Terminal session info
 */
interface TerminalSession {
    id: string;
    name: string;
    terminal: vscode.Terminal;
    output: string[];
    isRunning: boolean;
    lastCommand?: string;
    createdAt: number;
}

/**
 * Terminal Session Manager
 */
class TerminalSessionManager {
    private sessions: Map<string, TerminalSession> = new Map();
    private defaultSessionId: string | null = null;
    private sessionCounter = 0;

    getOrCreateDefault(): TerminalSession {
        if (this.defaultSessionId) {
            const session = this.sessions.get(this.defaultSessionId);
            if (session) return session;
        }
        return this.createSession('MCP Terminal');
    }

    createSession(name: string): TerminalSession {
        const id = `session-${++this.sessionCounter}`;
        const terminal = vscode.window.createTerminal({ name });
        
        const session: TerminalSession = {
            id,
            name,
            terminal,
            output: [],
            isRunning: false,
            createdAt: Date.now()
        };

        this.sessions.set(id, session);
        
        if (!this.defaultSessionId) {
            this.defaultSessionId = id;
        }

        return session;
    }

    getSession(sessionId: string): TerminalSession | undefined {
        return this.sessions.get(sessionId);
    }

    listSessions(): TerminalSession[] {
        return Array.from(this.sessions.values());
    }

    killSession(sessionId: string): boolean {
        const session = this.sessions.get(sessionId);
        if (session) {
            session.terminal.dispose();
            this.sessions.delete(sessionId);
            if (this.defaultSessionId === sessionId) {
                this.defaultSessionId = null;
            }
            return true;
        }
        return false;
    }

    dispose(): void {
        for (const session of this.sessions.values()) {
            session.terminal.dispose();
        }
        this.sessions.clear();
        this.defaultSessionId = null;
    }
}

/**
 * Terminal MCP Server Implementation
 */
export class TerminalMcpServer extends McpServerBase {
    private sessionManager = new TerminalSessionManager();

    constructor() {
        super({
            name: 'ide-terminal',
            version: '1.0.0',
            description: 'VS Code integrated terminal tool server'
        });
    }

    getSystemPromptAppendix(): string {
        const platform = process.platform === 'win32' ? 'Windows' : process.platform === 'darwin' ? 'macOS' : 'Linux';
        const defaultShell = process.platform === 'win32' ? 'PowerShell' : 'bash';

        return `
### When to Use

Use VS Code's integrated terminal for command execution.

### Best Practices

- **Reuse sessions**: Always reuse existing sessions via \`session_id\`
- **Multiple terminals**: Only create multiple sessions for concurrent commands

**Current Environment:**
- Platform: ${platform}
- Default Shell: ${defaultShell}

**Note**: VS Code Terminal API has limited output capture.
`;
    }

    getAllowedTools(): string[] {
        return ['TerminalList', 'TerminalTypes'];
    }

    async initialize(): Promise<void> {
        // Terminal - Execute command
        this.server.registerTool(
            'Terminal',
            {
                description: 'Execute command in terminal. By default waits for completion.',
                inputSchema: {
                    command: z.string().describe('Command to execute'),
                    session_id: z.string().optional().describe('Session ID to reuse'),
                    session_name: z.string().optional().describe('Name for new session'),
                    wait: z.boolean().default(true).describe('Wait for completion'),
                    timeout: z.number().default(30).describe('Timeout in seconds')
                }
            },
            async ({ command, session_id, session_name, wait = true, timeout = 30 }) => {
                let session: TerminalSession;
                
                if (session_id) {
                    const existing = this.sessionManager.getSession(session_id);
                    if (!existing) {
                        return createToolResult({ error: `Session not found: ${session_id}` }, true);
                    }
                    session = existing;
                } else if (session_name) {
                    session = this.sessionManager.createSession(session_name);
                } else {
                    session = this.sessionManager.getOrCreateDefault();
                }

                session.terminal.show();
                session.terminal.sendText(command);
                session.lastCommand = command;
                session.isRunning = true;

                if (wait) {
                    await new Promise(resolve => setTimeout(resolve, Math.min(timeout * 1000, 5000)));
                    session.isRunning = false;
                }

                return createToolResult({
                    session_id: session.id,
                    session_name: session.name,
                    command,
                    status: wait ? 'completed' : 'running',
                    note: 'Check terminal window for output.'
                });
            }
        );

        // TerminalRead
        this.server.registerTool(
            'TerminalRead',
            {
                description: 'Read terminal output. Note: Limited support in VS Code.',
                inputSchema: {
                    session_id: z.string().optional().describe('Session ID to read from'),
                    max_lines: z.number().default(100).describe('Max lines to return')
                }
            },
            async ({ session_id }) => {
                let session: TerminalSession | undefined;
                if (session_id) {
                    session = this.sessionManager.getSession(session_id);
                } else {
                    session = this.sessionManager.getOrCreateDefault();
                }

                if (!session) {
                    return createToolResult({ error: 'No terminal session found' }, true);
                }

                return createToolResult({
                    session_id: session.id,
                    lastCommand: session.lastCommand,
                    isRunning: session.isRunning,
                    note: 'Direct output capture not available. Check terminal window.'
                });
            }
        );

        // TerminalList
        this.server.registerTool(
            'TerminalList',
            {
                description: 'List all terminal sessions.',
                inputSchema: {}
            },
            async () => {
                const sessions = this.sessionManager.listSessions().map(s => ({
                    session_id: s.id,
                    name: s.name,
                    isRunning: s.isRunning,
                    lastCommand: s.lastCommand,
                    createdAt: new Date(s.createdAt).toISOString()
                }));

                return createToolResult({ sessions, count: sessions.length });
            }
        );

        // TerminalKill
        this.server.registerTool(
            'TerminalKill',
            {
                description: 'Close and destroy terminal session(s).',
                inputSchema: {
                    session_ids: z.array(z.string()).optional().describe('Sessions to close'),
                    all: z.boolean().default(false).describe('Close all sessions')
                }
            },
            async ({ session_ids, all = false }) => {
                if (all) {
                    const count = this.sessionManager.listSessions().length;
                    this.sessionManager.dispose();
                    return createToolResult({ success: true, closedCount: count });
                }

                if (session_ids) {
                    let closedCount = 0;
                    for (const id of session_ids) {
                        if (this.sessionManager.killSession(id)) {
                            closedCount++;
                        }
                    }
                    return createToolResult({ success: true, closedCount });
                }

                return createToolResult({ success: true, closedCount: 0 });
            }
        );

        // TerminalInterrupt
        this.server.registerTool(
            'TerminalInterrupt',
            {
                description: 'Send interrupt signal (Ctrl+C) to terminal.',
                inputSchema: {
                    session_id: z.string().describe('Session ID to interrupt')
                }
            },
            async ({ session_id }) => {
                const session = this.sessionManager.getSession(session_id);
                if (!session) {
                    return createToolResult({ error: `Session not found: ${session_id}` }, true);
                }

                session.terminal.sendText('\x03', false);
                session.isRunning = false;

                return createToolResult({ success: true, session_id: session.id });
            }
        );

        // TerminalRename
        this.server.registerTool(
            'TerminalRename',
            {
                description: 'Rename a terminal session.',
                inputSchema: {
                    session_id: z.string().describe('Session ID'),
                    new_name: z.string().describe('New name')
                }
            },
            async ({ session_id, new_name }) => {
                const session = this.sessionManager.getSession(session_id);
                if (!session) {
                    return createToolResult({ error: `Session not found: ${session_id}` }, true);
                }

                session.name = new_name;
                return createToolResult({ success: true, session_id: session.id, name: session.name });
            }
        );

        // TerminalTypes
        this.server.registerTool(
            'TerminalTypes',
            {
                description: 'Get available shell types for the current platform.',
                inputSchema: {}
            },
            async () => {
                const platform = process.platform;
                let shells: string[];

                if (platform === 'win32') {
                    shells = ['powershell', 'cmd', 'git-bash', 'wsl'];
                } else if (platform === 'darwin') {
                    shells = ['bash', 'zsh', 'fish'];
                } else {
                    shells = ['bash', 'sh', 'zsh', 'fish'];
                }

                return createToolResult({
                    platform,
                    availableShells: shells,
                    defaultShell: shells[0]
                });
            }
        );

        console.log('[Terminal MCP] Registered 7 tools');
    }

    dispose(): void {
        this.sessionManager.dispose();
        super.dispose();
    }
}

/**
 * Terminal MCP Server Provider
 */
export class TerminalMcpServerProvider implements McpServerProvider {
    name = 'ide-terminal';
    private server: TerminalMcpServer | null = null;

    async initialize(): Promise<void> {
        if (!this.server) {
            this.server = new TerminalMcpServer();
        }
        await this.server.initialize();
    }

    getServer(): McpServer {
        if (!this.server) {
            this.server = new TerminalMcpServer();
        }
        return this.server.getServer();
    }

    getDisallowedBuiltinTools(): string[] {
        // Import settings dynamically to avoid circular dependencies
        const { agentSettingsService } = require('../../settings');
        const settings = agentSettingsService;
        
        // When Terminal MCP is enabled and configured to disable built-in Bash
        if (settings.enableTerminalMcp && settings.terminalDisableBuiltinBash) {
            return ['Bash'];
        }
        return [];
    }

    dispose(): void {
        this.server?.dispose();
        this.server = null;
    }
}

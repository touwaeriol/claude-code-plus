/**
 * Terminal Tool Schema Manager
 * 
 * Handles loading, caching, and dynamic configuration of tool schemas.
 * Translated from JetBrains plugin's TerminalToolSchemaManager.kt
 */

import * as os from 'os';
import * as vscode from 'vscode';
import { ShellResolver } from './shellResolver';

/**
 * Tool input schema property
 */
export interface ToolSchemaProperty {
    type: string;
    description?: string;
    default?: string | number | boolean;
    enum?: string[];
    minimum?: number;
    items?: { type: string };
}

/**
 * Tool schema definition
 */
export interface ToolSchema {
    description: string;
    properties: Record<string, ToolSchemaProperty>;
    required?: string[];
}

/**
 * Default terminal tools schema (matching JetBrains McpDefaults)
 */
const DEFAULT_TERMINAL_TOOLS_SCHEMA: Record<string, ToolSchema> = {
    Terminal: {
        description: 'Execute commands in VS Code integrated terminal. By default waits for completion and returns output directly. Set wait=false to return immediately (use TerminalRead to get output).',
        properties: {
            command: {
                type: 'string',
                description: 'The command to execute (required)'
            },
            session_id: {
                type: 'string',
                description: 'Session ID to reuse. If not provided, uses the default terminal (creates one if needed)'
            },
            session_name: {
                type: 'string',
                description: 'Name for new terminal session. If provided without session_id, creates a new named session'
            },
            shell_type: {
                type: 'string',
                description: 'Shell type. Available shells will be filled dynamically.',
                enum: ['bash'],
                default: 'bash'
            },
            wait: {
                type: 'boolean',
                description: 'If true, wait for command completion and return output directly. Default is true.',
                default: true
            },
            timeout: {
                type: 'number',
                description: 'Timeout in seconds for waiting (only used when wait=true). -1 means wait indefinitely.',
                default: 30,
                minimum: -1
            }
        },
        required: ['command']
    },
    TerminalRead: {
        description: 'Read output from a terminal session. By default reads immediately without waiting. Use wait=true to wait for command completion.',
        properties: {
            session_id: {
                type: 'string',
                description: 'Session ID to read from. If not provided, reads from the default terminal'
            },
            max_lines: {
                type: 'number',
                description: 'Maximum number of lines to return',
                default: 1000,
                minimum: 1
            },
            search: {
                type: 'string',
                description: 'Regex pattern to search in output. Returns matching lines with context'
            },
            context_lines: {
                type: 'number',
                description: 'Number of context lines before and after each search match',
                default: 2,
                minimum: 0
            },
            wait: {
                type: 'boolean',
                description: 'If true, wait until the running command completes before reading output.',
                default: false
            },
            timeout: {
                type: 'number',
                description: 'Timeout in seconds for waiting (only used when wait=true).',
                default: 30,
                minimum: -1
            }
        }
    },
    TerminalList: {
        description: 'List terminal sessions for the current AI session only.',
        properties: {
            include_output_preview: {
                type: 'boolean',
                description: 'Include a preview of recent output for each session',
                default: false
            },
            preview_lines: {
                type: 'number',
                description: 'Number of lines for output preview',
                default: 5,
                minimum: 1
            }
        }
    },
    TerminalKill: {
        description: 'Close and destroy terminal session(s) completely.',
        properties: {
            session_ids: {
                type: 'array',
                description: 'Session IDs to close',
                items: { type: 'string' }
            },
            all: {
                type: 'boolean',
                description: 'Close all sessions of the current AI session',
                default: false
            }
        }
    },
    TerminalTypes: {
        description: 'Get available shell types for the current platform.',
        properties: {}
    },
    TerminalRename: {
        description: 'Rename a terminal session.',
        properties: {
            session_id: {
                type: 'string',
                description: 'Session ID to rename (required)'
            },
            new_name: {
                type: 'string',
                description: 'New name for the session (required)'
            }
        },
        required: ['session_id', 'new_name']
    },
    TerminalInterrupt: {
        description: 'Stop or pause the currently running command by sending a terminal signal.',
        properties: {
            session_id: {
                type: 'string',
                description: 'Session ID to interrupt (required)'
            },
            signal: {
                type: 'string',
                description: 'Signal to send. SIGINT (Ctrl+C, default): interrupt.',
                enum: ['SIGINT', 'SIGQUIT', 'SIGTSTP'],
                default: 'SIGINT'
            }
        },
        required: ['session_id']
    }
};

/**
 * Terminal Tool Schema Manager
 */
export class TerminalToolSchemaManager {
    private static instance: TerminalToolSchemaManager | null = null;
    private baseSchemas: Record<string, ToolSchema>;

    private constructor() {
        console.log('[TerminalToolSchemaManager] Loading terminal tool schemas');
        this.baseSchemas = { ...DEFAULT_TERMINAL_TOOLS_SCHEMA };
        console.log(`[TerminalToolSchemaManager] Loaded ${Object.keys(this.baseSchemas).length} terminal tool schemas`);
    }

    /**
     * Get singleton instance
     */
    static getInstance(): TerminalToolSchemaManager {
        if (!TerminalToolSchemaManager.instance) {
            TerminalToolSchemaManager.instance = new TerminalToolSchemaManager();
        }
        return TerminalToolSchemaManager.instance;
    }

    /**
     * Get all tool schemas with dynamic shell configuration applied
     */
    getToolSchemas(): Record<string, ToolSchema> {
        const schemas = { ...this.baseSchemas };
        
        const terminalSchema = schemas['Terminal'];
        if (!terminalSchema) return schemas;

        const shellTypeProperty = terminalSchema.properties['shell_type'];
        if (!shellTypeProperty) return schemas;

        // Get available shells and default shell
        const availableShells = this.getAvailableShells();
        const defaultShell = this.getDefaultShell();

        console.log(`[TerminalToolSchemaManager] Dynamic shell config - available: ${availableShells.join(', ')}, default: ${defaultShell}`);

        // Update shell_type property
        shellTypeProperty.enum = availableShells;
        shellTypeProperty.default = defaultShell;

        const isWindows = os.platform() === 'win32';
        const platform = isWindows ? 'Windows' : 'Unix';
        shellTypeProperty.description = `Shell type. Platform: ${platform}. Available: ${availableShells.join(', ')}. Default: ${defaultShell}`;

        return schemas;
    }

    /**
     * Get schema for a specific tool by name
     */
    getToolSchema(toolName: string): ToolSchema | undefined {
        const schemas = this.getToolSchemas();
        return schemas[toolName];
    }

    /**
     * Get available shells for the current platform
     */
    private getAvailableShells(): string[] {
        const config = vscode.workspace.getConfiguration('claudeCodePlus');
        const configuredShells = config.get<string[]>('terminal.availableShells');
        
        if (configuredShells && configuredShells.length > 0) {
            return configuredShells;
        }

        // Detect from system
        const detected = ShellResolver.detectInstalledShells();
        return detected.map(s => ShellResolver.normalizeShellName(s.name));
    }

    /**
     * Get default shell
     */
    private getDefaultShell(): string {
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

    /**
     * Property accessor for all tool schemas (for compatibility)
     */
    get TOOL_SCHEMAS(): Record<string, ToolSchema> {
        return this.getToolSchemas();
    }
}

// Export singleton instance for convenience
export const terminalToolSchemaManager = TerminalToolSchemaManager.getInstance();

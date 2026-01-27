/**
 * File MCP Server for VS Code
 * 
 * Provides file operations: ReadFile, WriteFile, EditFile
 */

import * as vscode from 'vscode';
import * as fs from 'fs/promises';
import * as path from 'path';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import * as z from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';
import { PathResolver } from '../../util/pathResolver';

/**
 * Resolve file path (relative to workspace or absolute)
 * Supports multi-root workspace: "folderName/path" format
 */
function resolvePath(filePath: string): string {
    return PathResolver.resolveMultiRoot(filePath);
}

/**
 * File MCP Server Implementation
 */
export class FileMcpServer extends McpServerBase {
    constructor() {
        super({
            name: 'ide-file',
            version: '1.0.0',
            description: 'VS Code file operations MCP server'
        });
    }

    getSystemPromptAppendix(): string {
        return `
### When to Use

Use for file operations with relative path support (to workspace root).
`;
    }

    getAllowedTools(): string[] {
        return ['ReadFile']; // ReadFile auto-approved, Write/Edit need permission
    }

    async initialize(): Promise<void> {
        // ReadFile - Read file content with pagination
        this.server.registerTool(
            'ReadFile',
            {
                description: 'Read file content. Supports pagination with offset and maxLines.',
                inputSchema: {
                    filePath: z.string().describe('File path (relative or absolute)'),
                    maxLines: z.number().default(500).describe('Maximum lines to return'),
                    offset: z.number().default(0).describe('Line offset for pagination')
                }
            },
            async ({ filePath, maxLines = 500, offset = 0 }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    
                    const content = await fs.readFile(absolutePath, 'utf-8');
                    const lines = content.split('\n');
                    const totalLines = lines.length;
                    
                    const selectedLines = lines.slice(offset, offset + maxLines);
                    const hasMore = offset + maxLines < totalLines;

                    return createToolResult({
                        filePath: absolutePath,
                        content: selectedLines.join('\n'),
                        totalLines,
                        offset,
                        returnedLines: selectedLines.length,
                        hasMore
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to read file: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // WriteFile - Write content to file
        this.server.registerTool(
            'WriteFile',
            {
                description: 'Write content to a file. Creates the file if it doesn\'t exist.',
                inputSchema: {
                    filePath: z.string().describe('File path (relative or absolute)'),
                    content: z.string().describe('Content to write')
                }
            },
            async ({ filePath, content }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    
                    // Ensure directory exists
                    const dir = path.dirname(absolutePath);
                    await fs.mkdir(dir, { recursive: true });
                    
                    // Check if file exists
                    let isNewFile = false;
                    try {
                        await fs.access(absolutePath);
                    } catch {
                        isNewFile = true;
                    }
                    
                    await fs.writeFile(absolutePath, content, 'utf-8');

                    return createToolResult({
                        success: true,
                        filePath: absolutePath,
                        isNewFile,
                        bytesWritten: Buffer.byteLength(content, 'utf-8')
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to write file: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // EditFile - Find and replace in file
        this.server.registerTool(
            'EditFile',
            {
                description: 'Edit a file by replacing text. oldString must be unique unless replaceAll is true.',
                inputSchema: {
                    filePath: z.string().describe('File path'),
                    oldString: z.string().describe('Text to replace'),
                    newString: z.string().describe('Replacement text'),
                    replaceAll: z.boolean().default(false).describe('Replace all occurrences')
                }
            },
            async ({ filePath, oldString, newString, replaceAll = false }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    
                    const content = await fs.readFile(absolutePath, 'utf-8');
                    
                    // Check uniqueness if not replaceAll
                    if (!replaceAll) {
                        const occurrences = content.split(oldString).length - 1;
                        if (occurrences === 0) {
                            return createToolResult({
                                error: 'oldString not found in file'
                            }, true);
                        }
                        if (occurrences > 1) {
                            return createToolResult({
                                error: `oldString found ${occurrences} times. Set replaceAll=true to replace all, or make oldString more specific.`
                            }, true);
                        }
                    }
                    
                    let newContent: string;
                    let replacements: number;
                    
                    if (replaceAll) {
                        const parts = content.split(oldString);
                        replacements = parts.length - 1;
                        newContent = parts.join(newString);
                    } else {
                        newContent = content.replace(oldString, newString);
                        replacements = 1;
                    }
                    
                    await fs.writeFile(absolutePath, newContent, 'utf-8');

                    return createToolResult({
                        success: true,
                        filePath: absolutePath,
                        replacements
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to edit file: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        console.log('[File MCP] Registered 3 tools');
    }
}

/**
 * File MCP Server Provider
 */
export class FileMcpServerProvider implements McpServerProvider {
    name = 'ide-file';
    private server: FileMcpServer | null = null;

    getServer(): McpServer {
        if (!this.server) {
            this.server = new FileMcpServer();
        }
        return this.server.getServer();
    }

    getDisallowedBuiltinTools(): string[] {
        // Import settings dynamically to avoid circular dependencies
        const { agentSettingsService } = require('../../settings');
        const settings = agentSettingsService;
        
        // When File MCP is enabled and configured to disable built-in tools
        if (settings.enableJetBrainsFileMcp && settings.jetbrainsFileDisableBuiltinTools) {
            // Parse the disabled tools list from settings
            const disabledTools = settings.jetbrainsFileDisabledTools;
            if (disabledTools && typeof disabledTools === 'string') {
                return disabledTools.split(',').map((t: string) => t.trim()).filter(Boolean);
            }
            // Default: disable Read, Write, Edit when File MCP replaces them
            return ['Read', 'Write', 'Edit'];
        }
        return [];
    }

    dispose(): void {
        this.server?.dispose();
        this.server = null;
    }
}

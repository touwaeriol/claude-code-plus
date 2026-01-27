/**
 * Git MCP Server for VS Code
 * 
 * Provides Git/VCS integration tools using VS Code Git Extension API.
 */

import * as vscode from 'vscode';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import * as z from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';
import { PathResolver } from '../../util/pathResolver';
import type { GitExtension, Repository, API, Status, Change } from '../../../types/git.d';

/**
 * Get Git API from VS Code Git extension
 */
function getGitAPI(): API | undefined {
    const gitExtension = vscode.extensions.getExtension<GitExtension>('vscode.git');
    if (!gitExtension?.isActive) {
        return undefined;
    }
    return gitExtension.exports.getAPI(1);
}

/**
 * Get the primary repository
 */
function getRepository(): Repository | undefined {
    const git = getGitAPI();
    return git?.repositories[0];
}

/**
 * Convert Change status to string
 */
function statusToString(status: Status): string {
    const statusMap: Record<number, string> = {
        0: 'INDEX_MODIFIED',
        1: 'INDEX_ADDED',
        2: 'INDEX_DELETED',
        3: 'INDEX_RENAMED',
        4: 'INDEX_COPIED',
        5: 'MODIFIED',
        6: 'DELETED',
        7: 'UNTRACKED',
        8: 'IGNORED',
        9: 'INTENT_TO_ADD',
    };
    return statusMap[status] || 'UNKNOWN';
}

/**
 * In-memory commit message storage
 */
let pendingCommitMessage: string = '';

/**
 * Selected files for commit (tracked separately since VS Code doesn't have commit panel)
 */
const selectedFiles: Set<string> = new Set();

/**
 * Git MCP Server Implementation
 */
export class GitMcpServer extends McpServerBase {
    constructor() {
        super({
            name: 'ide-git',
            version: '1.0.0',
            description: 'VS Code Git/VCS integration tools'
        });
    }

    getSystemPromptAppendix(): string {
        return `
### Git Commit Policy

**IMPORTANT**: Use VS Code Git MCP tools for version control operations.

### Commit Workflow

1. \`GetVcsChanges()\` → Get list of changes
2. Analyze changes, use \`SelectFiles\` / \`DeselectFiles\` to adjust file selection
3. \`SetCommitMessage()\` → Set commit message
4. **MUST** confirm with user before committing
5. Call \`CommitChanges()\` to execute

### Commit Message Conventions (Conventional Commits)

Follow the format: \`<type>(<scope>): <description>\`

**Types**: feat, fix, docs, style, refactor, perf, test, chore, ci, build
`;
    }

    getAllowedTools(): string[] {
        return ['GetVcsStatus', 'GetVcsChanges', 'GetCommitMessage'];
    }

    async initialize(): Promise<void> {
        // GetVcsStatus - Get current branch and status overview
        this.server.registerTool(
            'GetVcsStatus',
            {
                description: 'Get VCS status overview: current branch, number of changes, etc.',
                inputSchema: {}
            },
            async () => {
                const repo = getRepository();
                if (!repo) {
                    return createToolResult({ error: 'No Git repository found' }, true);
                }

                const state = repo.state;
                const result = {
                    branch: state.HEAD?.name || 'detached',
                    commit: state.HEAD?.commit?.substring(0, 8) || 'unknown',
                    workingTreeChanges: state.workingTreeChanges.length,
                    indexChanges: state.indexChanges.length,
                    mergeChanges: state.mergeChanges.length,
                    totalChanges: state.workingTreeChanges.length + state.indexChanges.length,
                    remotes: state.remotes.map(r => r.name),
                    ahead: state.HEAD?.ahead,
                    behind: state.HEAD?.behind
                };

                return createToolResult(result);
            }
        );

        // GetVcsChanges - Get detailed list of uncommitted changes
        this.server.registerTool(
            'GetVcsChanges',
            {
                description: 'Get uncommitted VCS changes with optional diff content.',
                inputSchema: {
                    includeDiff: z.boolean().default(true).describe('Include diff content'),
                    maxDiffLines: z.number().default(100).describe('Max diff lines per file'),
                    maxFiles: z.number().default(50).describe('Max files to return')
                }
            },
            async ({ includeDiff = true, maxDiffLines = 100, maxFiles = 50 }) => {
                const repo = getRepository();
                if (!repo) {
                    return createToolResult({ error: 'No Git repository found' }, true);
                }

                const state = repo.state;
                const allChanges: Change[] = [...state.indexChanges, ...state.workingTreeChanges];
                const limitedChanges = allChanges.slice(0, maxFiles);

                const changes = await Promise.all(limitedChanges.map(async (change) => {
                    const relativePath = vscode.workspace.asRelativePath(change.uri);
                    const isSelected = selectedFiles.has(relativePath) || selectedFiles.size === 0;
                    
                    let diff: string | undefined;
                    if (includeDiff) {
                        try {
                            const diffContent = await repo.diff(true);
                            const lines = diffContent.split('\n').slice(0, maxDiffLines);
                            diff = lines.join('\n');
                        } catch {
                            diff = undefined;
                        }
                    }

                    return {
                        path: relativePath,
                        status: statusToString(change.status),
                        staged: state.indexChanges.includes(change),
                        selected: isSelected,
                        diff
                    };
                }));

                return createToolResult({
                    changes,
                    totalCount: allChanges.length,
                    returnedCount: changes.length,
                    selectedCount: selectedFiles.size || allChanges.length
                });
            }
        );

        // GetCommitMessage - Get current commit message
        this.server.registerTool(
            'GetCommitMessage',
            {
                description: 'Get the current commit message from the input box.',
                inputSchema: {}
            },
            async () => {
                const repo = getRepository();
                const message = repo?.inputBox.value || pendingCommitMessage;
                return createToolResult({ message });
            }
        );

        // SetCommitMessage - Set commit message
        this.server.registerTool(
            'SetCommitMessage',
            {
                description: 'Set or append to the commit message.',
                inputSchema: {
                    message: z.string().describe('The commit message'),
                    mode: z.enum(['replace', 'append']).default('replace').describe('Mode')
                }
            },
            async ({ message, mode = 'replace' }) => {
                const repo = getRepository();

                if (mode === 'append') {
                    pendingCommitMessage = (pendingCommitMessage ? pendingCommitMessage + '\n' : '') + message;
                } else {
                    pendingCommitMessage = message;
                }

                if (repo) {
                    repo.inputBox.value = pendingCommitMessage;
                }

                return createToolResult({ success: true, message: pendingCommitMessage });
            }
        );

        // SelectFiles - Select files for commit
        this.server.registerTool(
            'SelectFiles',
            {
                description: 'Select files for the next commit operation.',
                inputSchema: {
                    paths: z.array(z.string()).describe('File paths to select'),
                    mode: z.enum(['replace', 'add']).default('add').describe('Mode')
                }
            },
            async ({ paths, mode = 'add' }) => {
                if (mode === 'replace') {
                    selectedFiles.clear();
                }

                for (const p of paths) {
                    selectedFiles.add(p);
                }

                return createToolResult({
                    success: true,
                    selectedFiles: Array.from(selectedFiles),
                    count: selectedFiles.size
                });
            }
        );

        // DeselectFiles - Remove files from selection
        this.server.registerTool(
            'DeselectFiles',
            {
                description: 'Deselect files from the commit selection.',
                inputSchema: {
                    paths: z.array(z.string()).describe('File paths to deselect')
                }
            },
            async ({ paths }) => {
                for (const p of paths) {
                    selectedFiles.delete(p);
                }

                return createToolResult({
                    success: true,
                    selectedFiles: Array.from(selectedFiles),
                    count: selectedFiles.size
                });
            }
        );

        // SelectAllFiles - Select all changed files
        this.server.registerTool(
            'SelectAllFiles',
            {
                description: 'Select all changed files for commit.',
                inputSchema: {}
            },
            async () => {
                const repo = getRepository();
                if (!repo) {
                    return createToolResult({ error: 'No Git repository found' }, true);
                }

                selectedFiles.clear();
                const allChanges = [...repo.state.indexChanges, ...repo.state.workingTreeChanges];
                for (const change of allChanges) {
                    selectedFiles.add(vscode.workspace.asRelativePath(change.uri));
                }

                return createToolResult({
                    success: true,
                    selectedFiles: Array.from(selectedFiles),
                    count: selectedFiles.size
                });
            }
        );

        // DeselectAllFiles - Clear selection
        this.server.registerTool(
            'DeselectAllFiles',
            {
                description: 'Deselect all files.',
                inputSchema: {}
            },
            async () => {
                selectedFiles.clear();
                return createToolResult({ success: true, count: 0 });
            }
        );

        // CommitChanges - Commit selected files
        this.server.registerTool(
            'CommitChanges',
            {
                description: 'Commit selected files to the repository.',
                inputSchema: {
                    message: z.string().optional().describe('Commit message (uses pending message if not provided)'),
                    amend: z.boolean().default(false).describe('Amend previous commit'),
                    push: z.boolean().default(false).describe('Push after commit')
                }
            },
            async ({ message, amend = false, push = false }) => {
                const repo = getRepository();
                if (!repo) {
                    return createToolResult({ error: 'No Git repository found' }, true);
                }

                const commitMessage = message || pendingCommitMessage || repo.inputBox.value;

                if (!commitMessage) {
                    return createToolResult({ error: 'No commit message provided' }, true);
                }

                try {
                    // Stage selected files if any
                    if (selectedFiles.size > 0) {
                        const uris = Array.from(selectedFiles).map(p => {
                            const absolutePath = PathResolver.resolveMultiRoot(p);
                            return vscode.Uri.file(absolutePath);
                        });
                        await repo.add(uris);
                    }

                    // Commit
                    await repo.commit(commitMessage, { amend });

                    // Push if requested
                    if (push) {
                        await repo.push();
                    }

                    // Clear state
                    pendingCommitMessage = '';
                    selectedFiles.clear();
                    repo.inputBox.value = '';

                    return createToolResult({
                        success: true,
                        message: commitMessage,
                        pushed: push
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Commit failed: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        console.log('[Git MCP] Registered 9 tools');
    }
}

/**
 * Git MCP Server Provider
 */
export class GitMcpServerProvider implements McpServerProvider {
    name = 'ide-git';
    private server: GitMcpServer | null = null;

    getServer(): McpServer {
        if (!this.server) {
            this.server = new GitMcpServer();
        }
        return this.server.getServer();
    }

    dispose(): void {
        this.server?.dispose();
        this.server = null;
    }
}

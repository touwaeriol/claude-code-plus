/**
 * LSP MCP Server for VS Code
 * 
 * Provides code analysis tools: DirectoryTree, FileIndex, CodeSearch, FileProblems, FindUsages, Rename
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs/promises';
import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import * as z from 'zod';
import { McpServerProvider, McpServerBase, createToolResult } from '../mcpServerRegistry';

function getWorkspaceRoot(): string | undefined {
    return vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
}

function resolvePath(filePath: string): string {
    if (path.isAbsolute(filePath)) {
        return filePath;
    }
    const root = getWorkspaceRoot();
    if (!root) {
        throw new Error('No workspace folder open');
    }
    return path.join(root, filePath);
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes}B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

/**
 * LSP MCP Server Implementation
 */
export class LspMcpServer extends McpServerBase {
    constructor() {
        super({
            name: 'ide-lsp',
            version: '1.0.0',
            description: 'VS Code language server and code analysis tools'
        });
    }

    getSystemPromptAppendix(): string {
        return `
### When to Use

Use VS Code LSP tools for code exploration and refactoring:
- \`DirectoryTree\`: Get project structure
- \`FileIndex\`: Search for files/symbols
- \`CodeSearch\`: Search code content
- \`FileProblems\`: Get diagnostics/errors
- \`FindUsages\`: Find all references
- \`Rename\`: Safely rename a symbol
`;
    }

    getAllowedTools(): string[] {
        return ['DirectoryTree', 'FileIndex', 'CodeSearch', 'FileProblems', 'FindUsages'];
    }

    async initialize(): Promise<void> {
        // DirectoryTree
        this.server.registerTool(
            'DirectoryTree',
            {
                description: 'Get the tree structure of a directory.',
                inputSchema: {
                    path: z.string().default('.').describe('Path relative to workspace'),
                    maxDepth: z.number().default(3).describe('Maximum recursion depth'),
                    maxEntries: z.number().default(100).describe('Maximum entries to return'),
                    pattern: z.string().optional().describe('Glob pattern filter'),
                    filesOnly: z.boolean().default(false).describe('Show only files'),
                    includeHidden: z.boolean().default(false).describe('Include hidden files')
                }
            },
            async ({ path: dirPath = '.', maxDepth = 3, maxEntries = 100, pattern, filesOnly = false, includeHidden = false }) => {
                try {
                    const absolutePath = resolvePath(dirPath);
                    const entries: string[] = [];
                    let count = 0;

                    async function traverse(currentPath: string, depth: number, prefix: string): Promise<void> {
                        if (depth > maxDepth || count >= maxEntries) return;

                        const items = await fs.readdir(currentPath, { withFileTypes: true });
                        const filtered = items.filter(item => {
                            if (!includeHidden && item.name.startsWith('.')) return false;
                            if (pattern && item.isFile()) {
                                const regex = new RegExp(pattern.replace(/\*/g, '.*').replace(/\?/g, '.'));
                                if (!regex.test(item.name)) return false;
                            }
                            if (filesOnly && item.isDirectory()) return false;
                            return true;
                        });

                        for (const item of filtered) {
                            if (count >= maxEntries) break;
                            
                            const itemPath = path.join(currentPath, item.name);
                            const isDir = item.isDirectory();
                            
                            if (isDir) {
                                entries.push(`${prefix}${item.name}/`);
                            } else {
                                try {
                                    const stats = await fs.stat(itemPath);
                                    entries.push(`${prefix}${item.name} (${formatSize(stats.size)})`);
                                } catch {
                                    entries.push(`${prefix}${item.name}`);
                                }
                            }
                            count++;

                            if (isDir && depth < maxDepth) {
                                await traverse(itemPath, depth + 1, prefix + '  ');
                            }
                        }
                    }

                    await traverse(absolutePath, 1, '');

                    return createToolResult({
                        path: dirPath,
                        entries,
                        totalEntries: count,
                        truncated: count >= maxEntries
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to read directory: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // FileIndex
        this.server.registerTool(
            'FileIndex',
            {
                description: 'Search files, classes, and symbols in the workspace.',
                inputSchema: {
                    query: z.string().describe('Search query'),
                    searchType: z.enum(['All', 'Files', 'Symbols']).default('All').describe('Search type'),
                    maxResults: z.number().default(20).describe('Max results')
                }
            },
            async ({ query, searchType = 'All', maxResults = 20 }) => {
                const results: { type: string; name: string; path: string; line?: number }[] = [];

                try {
                    if (searchType === 'All' || searchType === 'Files') {
                        const files = await vscode.workspace.findFiles(`**/*${query}*`, '**/node_modules/**', maxResults);
                        for (const file of files) {
                            results.push({
                                type: 'file',
                                name: path.basename(file.fsPath),
                                path: vscode.workspace.asRelativePath(file)
                            });
                        }
                    }

                    if (searchType === 'All' || searchType === 'Symbols') {
                        const symbols = await vscode.commands.executeCommand<vscode.SymbolInformation[]>(
                            'vscode.executeWorkspaceSymbolProvider',
                            query
                        );
                        if (symbols) {
                            for (const symbol of symbols.slice(0, maxResults - results.length)) {
                                results.push({
                                    type: vscode.SymbolKind[symbol.kind],
                                    name: symbol.name,
                                    path: vscode.workspace.asRelativePath(symbol.location.uri),
                                    line: symbol.location.range.start.line + 1
                                });
                            }
                        }
                    }

                    return createToolResult({
                        query,
                        searchType,
                        results: results.slice(0, maxResults),
                        totalCount: results.length
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Search failed: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // CodeSearch
        this.server.registerTool(
            'CodeSearch',
            {
                description: 'Search code content across project files.',
                inputSchema: {
                    query: z.string().describe('Search text or regex pattern'),
                    fileMask: z.string().optional().describe('File pattern'),
                    isRegex: z.boolean().default(false).describe('Is regex'),
                    caseSensitive: z.boolean().default(false).describe('Case sensitive'),
                    maxResults: z.number().default(20).describe('Max results')
                }
            },
            async ({ query, fileMask, isRegex = false, caseSensitive = false, maxResults = 20 }) => {
                try {
                    const includePattern = fileMask ? `**/{${fileMask.split(',').join(',')}}` : '**/*';
                    const results: { file: string; line: number; text: string }[] = [];

                    const files = await vscode.workspace.findFiles(includePattern, '**/node_modules/**', 100);
                    
                    const regex = isRegex 
                        ? new RegExp(query, caseSensitive ? 'g' : 'gi')
                        : new RegExp(query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), caseSensitive ? 'g' : 'gi');

                    for (const file of files) {
                        if (results.length >= maxResults) break;
                        
                        try {
                            const content = await fs.readFile(file.fsPath, 'utf-8');
                            const lines = content.split('\n');
                            
                            for (let i = 0; i < lines.length && results.length < maxResults; i++) {
                                if (regex.test(lines[i])) {
                                    results.push({
                                        file: vscode.workspace.asRelativePath(file),
                                        line: i + 1,
                                        text: lines[i].trim().substring(0, 200)
                                    });
                                }
                                regex.lastIndex = 0;
                            }
                        } catch {
                            // Skip unreadable files
                        }
                    }

                    return createToolResult({
                        query,
                        results,
                        totalCount: results.length,
                        truncated: results.length >= maxResults
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Search failed: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // FileProblems
        this.server.registerTool(
            'FileProblems',
            {
                description: 'Get diagnostics (errors, warnings) for a file.',
                inputSchema: {
                    filePath: z.string().describe('File path'),
                    includeWarnings: z.boolean().default(true).describe('Include warnings'),
                    includeSuggestions: z.boolean().default(false).describe('Include suggestions'),
                    maxProblems: z.number().default(50).describe('Max problems')
                }
            },
            async ({ filePath, includeWarnings = true, includeSuggestions = false, maxProblems = 50 }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    const uri = vscode.Uri.file(absolutePath);
                    const diagnostics = vscode.languages.getDiagnostics(uri);

                    const filtered = diagnostics.filter(d => {
                        if (d.severity === vscode.DiagnosticSeverity.Error) return true;
                        if (d.severity === vscode.DiagnosticSeverity.Warning && includeWarnings) return true;
                        if (d.severity === vscode.DiagnosticSeverity.Information && includeSuggestions) return true;
                        if (d.severity === vscode.DiagnosticSeverity.Hint && includeSuggestions) return true;
                        return false;
                    });

                    const problems = filtered.slice(0, maxProblems).map(d => ({
                        severity: vscode.DiagnosticSeverity[d.severity],
                        message: d.message,
                        line: d.range.start.line + 1,
                        column: d.range.start.character + 1,
                        source: d.source,
                        code: d.code?.toString()
                    }));

                    return createToolResult({
                        filePath,
                        problems,
                        errorCount: problems.filter(p => p.severity === 'Error').length,
                        warningCount: problems.filter(p => p.severity === 'Warning').length,
                        totalCount: problems.length
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to get diagnostics: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // FindUsages
        this.server.registerTool(
            'FindUsages',
            {
                description: 'Find all usages/references of a symbol.',
                inputSchema: {
                    filePath: z.string().describe('File containing the symbol'),
                    line: z.number().describe('Line number (1-based)'),
                    column: z.number().default(1).describe('Column number (1-based)'),
                    maxResults: z.number().default(20).describe('Max results')
                }
            },
            async ({ filePath, line, column = 1, maxResults = 20 }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    const uri = vscode.Uri.file(absolutePath);
                    const position = new vscode.Position(line - 1, column - 1);

                    const locations = await vscode.commands.executeCommand<vscode.Location[]>(
                        'vscode.executeReferenceProvider',
                        uri,
                        position
                    );

                    if (!locations || locations.length === 0) {
                        return createToolResult({ usages: [], totalCount: 0 });
                    }

                    const usages = locations.slice(0, maxResults).map(loc => ({
                        file: vscode.workspace.asRelativePath(loc.uri),
                        line: loc.range.start.line + 1,
                        column: loc.range.start.character + 1
                    }));

                    return createToolResult({
                        usages,
                        totalCount: locations.length,
                        truncated: locations.length > maxResults
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Failed to find usages: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        // Rename
        this.server.registerTool(
            'Rename',
            {
                description: 'Safely rename a symbol and update all references.',
                inputSchema: {
                    filePath: z.string().describe('File containing the symbol'),
                    line: z.number().describe('Line number (1-based)'),
                    column: z.number().default(1).describe('Column number (1-based)'),
                    newName: z.string().describe('New name for the symbol')
                }
            },
            async ({ filePath, line, column = 1, newName }) => {
                try {
                    const absolutePath = resolvePath(filePath);
                    const uri = vscode.Uri.file(absolutePath);
                    const position = new vscode.Position(line - 1, column - 1);

                    const edit = await vscode.commands.executeCommand<vscode.WorkspaceEdit>(
                        'vscode.executeDocumentRenameProvider',
                        uri,
                        position,
                        newName
                    );

                    if (!edit) {
                        return createToolResult({ error: 'Rename not available at this location' }, true);
                    }

                    const affectedFiles = edit.entries().map(([uri]) => vscode.workspace.asRelativePath(uri));
                    const success = await vscode.workspace.applyEdit(edit);

                    return createToolResult({
                        success,
                        newName,
                        affectedFiles,
                        filesCount: affectedFiles.length
                    });
                } catch (error) {
                    return createToolResult({
                        error: `Rename failed: ${error instanceof Error ? error.message : String(error)}`
                    }, true);
                }
            }
        );

        console.log('[LSP MCP] Registered 6 tools');
    }
}

/**
 * LSP MCP Server Provider
 */
export class LspMcpServerProvider implements McpServerProvider {
    name = 'ide-lsp';
    private server: LspMcpServer | null = null;

    getServer(): McpServer {
        if (!this.server) {
            this.server = new LspMcpServer();
        }
        return this.server.getServer();
    }

    dispose(): void {
        this.server?.dispose();
        this.server = null;
    }
}

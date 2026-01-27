/**
 * Generate Commit Message Service
 * 
 * Source: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/vcs/GenerateCommitMessageService.kt
 * 
 * Uses AI to analyze code changes and generate commit messages via Git MCP tools.
 */

import * as vscode from 'vscode';
import { getLogger } from '../../logging/logger';
import type { GitExtension, Repository, API } from '../../types/git.d';

const logger = getLogger('GenerateCommitMessageService');

/**
 * Default system prompt for commit message generation
 */
const DEFAULT_SYSTEM_PROMPT = `You are a helpful assistant that generates concise and meaningful git commit messages.
Follow the Conventional Commits format: <type>(<scope>): <description>

Types: feat, fix, docs, style, refactor, perf, test, chore, ci, build

Guidelines:
- Be concise but descriptive
- Use imperative mood ("add" not "added")
- Focus on what changed and why
- Keep the first line under 72 characters`;

/**
 * Default user prompt
 */
const DEFAULT_USER_PROMPT = `Please analyze the staged changes and generate a commit message.

Use the GetVcsChanges tool to see what files are modified, then generate an appropriate commit message following Conventional Commits format.

After analyzing, call SetCommitMessage with your generated message.`;

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

export interface GenerateCommitMessageOptions {
    /** Custom system prompt */
    systemPrompt?: string;
    /** Custom user prompt */
    userPrompt?: string;
    /** Model to use */
    model?: string;
    /** Progress callback */
    onProgress?: (message: string) => void;
}

export interface GenerateCommitMessageResult {
    success: boolean;
    message?: string;
    error?: string;
}

/**
 * Generate Commit Message Service
 */
export class GenerateCommitMessageService {
    private static instance: GenerateCommitMessageService | undefined;

    static getInstance(): GenerateCommitMessageService {
        if (!this.instance) {
            this.instance = new GenerateCommitMessageService();
        }
        return this.instance;
    }

    /**
     * Generate commit message using AI
     */
    async generateCommitMessage(
        options: GenerateCommitMessageOptions = {}
    ): Promise<GenerateCommitMessageResult> {
        const {
            onProgress = () => {},
        } = options;

        try {
            onProgress('Checking for changes...');

            const repo = getRepository();
            if (!repo) {
                return {
                    success: false,
                    error: 'No Git repository found'
                };
            }

            // Get staged changes or all changes
            const changes = repo.state.indexChanges.length > 0
                ? repo.state.indexChanges
                : repo.state.workingTreeChanges;

            if (changes.length === 0) {
                return {
                    success: false,
                    error: 'No changes to commit'
                };
            }

            onProgress('Analyzing changes...');

            // Build diff summary
            const diffSummary = await this.buildDiffSummary(repo, changes);

            onProgress('Generating commit message...');

            // Generate commit message using simple heuristics
            // In a full implementation, this would call the AI agent
            const message = this.generateMessageFromDiff(diffSummary);

            // Set the commit message in the SCM input
            if (repo.inputBox) {
                repo.inputBox.value = message;
            }

            logger.info('Generated commit message', { message });

            return {
                success: true,
                message
            };

        } catch (error) {
            const errorMessage = error instanceof Error ? error.message : String(error);
            logger.error('Failed to generate commit message', error instanceof Error ? error : new Error(errorMessage));
            return {
                success: false,
                error: errorMessage
            };
        }
    }

    /**
     * Build a summary of the diff for analysis
     */
    private async buildDiffSummary(
        repo: Repository,
        changes: readonly { uri: vscode.Uri; status: number }[]
    ): Promise<DiffSummary> {
        const summary: DiffSummary = {
            added: [],
            modified: [],
            deleted: [],
            renamed: []
        };

        for (const change of changes) {
            const relativePath = vscode.workspace.asRelativePath(change.uri);
            
            // Status codes from git extension
            switch (change.status) {
                case 1: // INDEX_ADDED
                case 7: // UNTRACKED
                    summary.added.push(relativePath);
                    break;
                case 0: // INDEX_MODIFIED
                case 5: // MODIFIED
                    summary.modified.push(relativePath);
                    break;
                case 2: // INDEX_DELETED
                case 6: // DELETED
                    summary.deleted.push(relativePath);
                    break;
                case 3: // INDEX_RENAMED
                    summary.renamed.push(relativePath);
                    break;
            }
        }

        return summary;
    }

    /**
     * Generate commit message from diff summary using heuristics
     */
    private generateMessageFromDiff(summary: DiffSummary): string {
        const { added, modified, deleted, renamed } = summary;
        const allFiles = [...added, ...modified, ...deleted, ...renamed];

        if (allFiles.length === 0) {
            return 'chore: update files';
        }

        // Detect common patterns
        const patterns = this.detectPatterns(allFiles);

        // Single file change
        if (allFiles.length === 1) {
            const file = allFiles[0];
            const action = added.length > 0 ? 'add' : 
                          deleted.length > 0 ? 'remove' : 'update';
            return `${patterns.type}: ${action} ${this.getFileName(file)}`;
        }

        // Multiple files in same directory
        const dirs = this.getCommonDirectories(allFiles);
        if (dirs.length === 1 && allFiles.length <= 5) {
            const fileList = allFiles.map(f => this.getFileName(f)).join(', ');
            return `${patterns.type}(${dirs[0]}): update ${fileList}`;
        }

        // General description
        const parts: string[] = [];
        if (added.length > 0) parts.push(`add ${added.length} file(s)`);
        if (modified.length > 0) parts.push(`update ${modified.length} file(s)`);
        if (deleted.length > 0) parts.push(`remove ${deleted.length} file(s)`);

        const scope = patterns.scope ? `(${patterns.scope})` : '';
        return `${patterns.type}${scope}: ${parts.join(', ')}`;
    }

    /**
     * Detect patterns from file paths to determine commit type
     */
    private detectPatterns(files: string[]): { type: string; scope?: string } {
        const hasTests = files.some(f => f.includes('test') || f.includes('spec'));
        const hasDocs = files.some(f => 
            f.endsWith('.md') || f.includes('docs/') || f.includes('README')
        );
        const hasConfig = files.some(f => 
            f.includes('config') || f.endsWith('.json') || f.endsWith('.yaml') || f.endsWith('.yml')
        );
        const hasSrc = files.some(f => 
            f.includes('src/') || f.endsWith('.ts') || f.endsWith('.js') || f.endsWith('.kt')
        );

        // Determine type
        let type = 'chore';
        if (hasTests && !hasSrc) type = 'test';
        else if (hasDocs && !hasSrc) type = 'docs';
        else if (hasConfig && !hasSrc) type = 'build';
        else if (hasSrc) type = 'feat';

        // Determine scope
        const commonDirs = this.getCommonDirectories(files);
        const scope = commonDirs.length === 1 ? commonDirs[0] : undefined;

        return { type, scope };
    }

    /**
     * Get common directory from file paths
     */
    private getCommonDirectories(files: string[]): string[] {
        const dirs = files
            .map(f => f.split('/').slice(0, -1).join('/'))
            .filter(d => d.length > 0);
        
        const uniqueDirs = [...new Set(dirs)];
        
        // Return leaf directories
        return uniqueDirs
            .map(d => d.split('/').pop() || d)
            .filter((d, i, arr) => arr.indexOf(d) === i)
            .slice(0, 2);
    }

    /**
     * Get file name from path
     */
    private getFileName(filePath: string): string {
        return filePath.split('/').pop() || filePath;
    }
}

interface DiffSummary {
    added: string[];
    modified: string[];
    deleted: string[];
    renamed: string[];
}

/**
 * VS Code command to generate commit message
 */
export async function executeGenerateCommitMessage(): Promise<void> {
    const service = GenerateCommitMessageService.getInstance();
    
    await vscode.window.withProgress(
        {
            location: vscode.ProgressLocation.Notification,
            title: 'Generating Commit Message',
            cancellable: true
        },
        async (progress, token) => {
            const result = await service.generateCommitMessage({
                onProgress: (message) => {
                    progress.report({ message });
                }
            });

            if (!result.success) {
                vscode.window.showErrorMessage(
                    `Failed to generate commit message: ${result.error}`
                );
            } else {
                vscode.window.showInformationMessage(
                    'Commit message generated successfully'
                );
            }
        }
    );
}

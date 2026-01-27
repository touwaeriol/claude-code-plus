/**
 * Commit Panel Accessor (Project-level singleton)
 * 
 * Source: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/mcp/git/CommitPanelAccessor.kt
 * 
 * Problem: MCP tools run in background threads and cannot directly access SCM UI components.
 * Solution: Capture SCM input box reference through Git extension events.
 * 
 * Uses VS Code Git Extension public API (inputBox.value) instead of internal access.
 */

import * as vscode from 'vscode';
import { getLogger } from '../../logging/logger';
import type { GitExtension, Repository, API, Change, Status } from '../../types/git.d';

const logger = getLogger('CommitPanelAccessor');

/**
 * Change information interface
 */
export interface ChangeInfo {
    /** File path relative to workspace */
    path: string;
    /** Change status */
    status: ChangeStatus;
    /** Whether the change is staged */
    staged: boolean;
    /** Original URI for renamed files */
    originalPath?: string;
}

export type ChangeStatus = 
    | 'ADDED' 
    | 'MODIFIED' 
    | 'DELETED' 
    | 'RENAMED' 
    | 'COPIED' 
    | 'UNTRACKED'
    | 'IGNORED'
    | 'CONFLICT'
    | 'UNKNOWN';

/**
 * Convert VS Code Git status to ChangeStatus
 */
function convertStatus(status: Status): ChangeStatus {
    // Status enum values from git.d.ts
    switch (status) {
        case 0: // INDEX_MODIFIED
        case 5: // MODIFIED
            return 'MODIFIED';
        case 1: // INDEX_ADDED
        case 9: // INTENT_TO_ADD
            return 'ADDED';
        case 2: // INDEX_DELETED
        case 6: // DELETED
            return 'DELETED';
        case 3: // INDEX_RENAMED
        case 10: // INTENT_TO_RENAME
            return 'RENAMED';
        case 4: // INDEX_COPIED
            return 'COPIED';
        case 7: // UNTRACKED
            return 'UNTRACKED';
        case 8: // IGNORED
            return 'IGNORED';
        case 12: // ADDED_BY_US
        case 13: // ADDED_BY_THEM
        case 14: // DELETED_BY_US
        case 15: // DELETED_BY_THEM
        case 16: // BOTH_ADDED
        case 17: // BOTH_DELETED
        case 18: // BOTH_MODIFIED
            return 'CONFLICT';
        default:
            return 'UNKNOWN';
    }
}

/**
 * Commit Panel Accessor
 * 
 * Provides access to VS Code Git extension's SCM input and change tracking.
 */
export class CommitPanelAccessor {
    private static instance: CommitPanelAccessor | undefined;
    
    /** Currently tracked repository */
    private currentRepository: Repository | undefined;
    
    /** Selected files for commit (managed separately since VS Code doesn't have explicit selection) */
    private selectedChanges: Set<string> = new Set();
    
    /** Disposables for event subscriptions */
    private disposables: vscode.Disposable[] = [];

    private constructor() {
        this.initializeRepositoryTracking();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): CommitPanelAccessor {
        if (!this.instance) {
            this.instance = new CommitPanelAccessor();
        }
        return this.instance;
    }

    /**
     * Dispose the instance
     */
    static dispose(): void {
        if (this.instance) {
            this.instance.clear();
            this.instance.disposables.forEach(d => d.dispose());
            this.instance.disposables = [];
            this.instance = undefined;
            logger.info('CommitPanelAccessor disposed');
        }
    }

    /**
     * Initialize repository tracking
     */
    private initializeRepositoryTracking(): void {
        const git = this.getGitAPI();
        if (!git) {
            logger.warn('Git extension not available');
            return;
        }

        // Track repository changes
        this.disposables.push(
            git.onDidOpenRepository((repo) => {
                logger.info('Repository opened', { root: repo.rootUri.fsPath });
                this.setRepository(repo);
            })
        );

        this.disposables.push(
            git.onDidCloseRepository((repo) => {
                logger.info('Repository closed', { root: repo.rootUri.fsPath });
                if (this.currentRepository === repo) {
                    this.currentRepository = undefined;
                }
            })
        );

        // Set initial repository
        if (git.repositories.length > 0) {
            this.setRepository(git.repositories[0]);
        }
    }

    /**
     * Get Git API from VS Code Git extension
     */
    private getGitAPI(): API | undefined {
        const gitExtension = vscode.extensions.getExtension<GitExtension>('vscode.git');
        if (!gitExtension?.isActive) {
            return undefined;
        }
        return gitExtension.exports.getAPI(1);
    }

    /**
     * Set the current repository to track
     */
    setRepository(repository: Repository): void {
        this.currentRepository = repository;
        logger.info('Repository set', { root: repository.rootUri.fsPath });

        // Listen for state changes
        this.disposables.push(
            repository.state.onDidChange(() => {
                logger.debug('Repository state changed');
            })
        );
    }

    /**
     * Update selected changes list
     */
    updateSelectedChanges(paths: string[]): void {
        this.selectedChanges = new Set(paths);
        logger.debug('Selected changes updated', { count: paths.length });
    }

    /**
     * Clear all references
     */
    clear(): void {
        this.selectedChanges.clear();
        logger.info('CommitPanelAccessor cleared');
    }

    /**
     * Get all uncommitted changes
     */
    getAllChanges(): ChangeInfo[] {
        const repo = this.currentRepository || this.getGitAPI()?.repositories[0];
        if (!repo) {
            return [];
        }

        const changes: ChangeInfo[] = [];

        // Add index (staged) changes
        for (const change of repo.state.indexChanges) {
            changes.push(this.convertChange(change, true));
        }

        // Add working tree (unstaged) changes
        for (const change of repo.state.workingTreeChanges) {
            changes.push(this.convertChange(change, false));
        }

        return changes;
    }

    /**
     * Get selected changes
     * Returns null if no explicit selection (meaning all changes are selected)
     */
    getSelectedChanges(): ChangeInfo[] | null {
        if (this.selectedChanges.size === 0) {
            return null; // No explicit selection
        }

        const allChanges = this.getAllChanges();
        return allChanges.filter(change => this.selectedChanges.has(change.path));
    }

    /**
     * Get current commit message from SCM input box
     */
    getCommitMessage(): string | null {
        const repo = this.currentRepository || this.getGitAPI()?.repositories[0];
        if (!repo?.inputBox) {
            return null;
        }
        return repo.inputBox.value || null;
    }

    /**
     * Set commit message in SCM input box
     * 
     * @param message Message to set
     * @param append Whether to append (true) or replace (false)
     */
    setCommitMessage(message: string, append: boolean = false): void {
        const repo = this.currentRepository || this.getGitAPI()?.repositories[0];
        if (!repo?.inputBox) {
            logger.warn('Cannot set commit message: no active repository');
            return;
        }

        try {
            const currentMessage = repo.inputBox.value;
            if (append && currentMessage.trim()) {
                repo.inputBox.value = `${currentMessage}\n\n${message}`;
            } else {
                repo.inputBox.value = message;
            }
            logger.info(`Commit message ${append ? 'appended' : 'set'}`);
        } catch (error) {
            logger.error('Failed to set commit message', error instanceof Error ? error : new Error(String(error)));
        }
    }

    /**
     * Check if SCM panel is available
     */
    isScmPanelAvailable(): boolean {
        const repo = this.currentRepository || this.getGitAPI()?.repositories[0];
        return repo?.inputBox !== undefined;
    }

    /**
     * Get the current repository
     */
    getRepository(): Repository | undefined {
        return this.currentRepository || this.getGitAPI()?.repositories[0];
    }

    /**
     * Select files for commit
     */
    selectFiles(paths: string[], mode: 'add' | 'replace' = 'add'): void {
        if (mode === 'replace') {
            this.selectedChanges.clear();
        }
        for (const path of paths) {
            this.selectedChanges.add(path);
        }
        logger.debug('Files selected', { count: this.selectedChanges.size, mode });
    }

    /**
     * Deselect files from commit
     */
    deselectFiles(paths: string[]): void {
        for (const path of paths) {
            this.selectedChanges.delete(path);
        }
        logger.debug('Files deselected', { remaining: this.selectedChanges.size });
    }

    /**
     * Select all changed files
     */
    selectAllFiles(): void {
        const changes = this.getAllChanges();
        this.selectedChanges = new Set(changes.map(c => c.path));
        logger.debug('All files selected', { count: this.selectedChanges.size });
    }

    /**
     * Deselect all files
     */
    deselectAllFiles(): void {
        this.selectedChanges.clear();
        logger.debug('All files deselected');
    }

    /**
     * Get list of selected file paths
     */
    getSelectedFilePaths(): string[] {
        return Array.from(this.selectedChanges);
    }

    /**
     * Convert VS Code Change to ChangeInfo
     */
    private convertChange(change: Change, staged: boolean): ChangeInfo {
        const relativePath = vscode.workspace.asRelativePath(change.uri);
        return {
            path: relativePath,
            status: convertStatus(change.status),
            staged,
            originalPath: change.renameUri 
                ? vscode.workspace.asRelativePath(change.originalUri)
                : undefined
        };
    }
}

// Export singleton accessor function for convenience
export function getCommitPanelAccessor(): CommitPanelAccessor {
    return CommitPanelAccessor.getInstance();
}

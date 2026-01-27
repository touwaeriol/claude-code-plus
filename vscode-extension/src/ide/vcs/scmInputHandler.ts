/**
 * SCM Input Handler
 * 
 * Source: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/vcs/ClaudeCheckinHandlerFactory.kt
 * 
 * VS Code equivalent of IDEA's CheckinHandlerFactory.
 * Captures SCM input box reference and tracks commit lifecycle events.
 * 
 * In VS Code, we use Git extension events instead of CheckinHandler:
 * - Repository state changes for tracking changes
 * - InputBox value changes for commit message tracking
 * - Post-commit cleanup via repository events
 */

import * as vscode from 'vscode';
import { getLogger } from '../../logging/logger';
import { getCommitPanelAccessor } from './commitPanelAccessor';
import type { GitExtension, Repository, API } from '../../types/git.d';

const logger = getLogger('ScmInputHandler');

/**
 * Commit lifecycle state
 */
export type CommitState = 'idle' | 'preparing' | 'committing' | 'success' | 'failed';

/**
 * Commit lifecycle event listener
 */
export interface CommitLifecycleListener {
    onCommitStateChange?(state: CommitState): void;
    onBeforeCommit?(): boolean | Promise<boolean>;
    onCommitSuccess?(): void;
    onCommitFailed?(error: Error): void;
}

/**
 * SCM Input Handler
 * 
 * Handles SCM input box events and commit lifecycle management.
 * Replaces IDEA's ClaudeCheckinHandler functionality.
 */
export class ScmInputHandler {
    private static instance: ScmInputHandler | undefined;

    /** Event disposables */
    private disposables: vscode.Disposable[] = [];

    /** Current commit state */
    private commitState: CommitState = 'idle';

    /** Registered lifecycle listeners */
    private lifecycleListeners: CommitLifecycleListener[] = [];

    /** Last known commit count for detecting new commits */
    private lastCommitHash: string | undefined;

    /** Tracked repositories */
    private trackedRepositories: Set<Repository> = new Set();

    private constructor() {
        this.initialize();
    }

    /**
     * Get singleton instance
     */
    static getInstance(): ScmInputHandler {
        if (!this.instance) {
            this.instance = new ScmInputHandler();
        }
        return this.instance;
    }

    /**
     * Dispose the instance
     */
    static dispose(): void {
        if (this.instance) {
            this.instance.disposables.forEach(d => d.dispose());
            this.instance.disposables = [];
            this.instance.lifecycleListeners = [];
            this.instance.trackedRepositories.clear();
            this.instance = undefined;
            logger.info('ScmInputHandler disposed');
        }
    }

    /**
     * Initialize the handler
     */
    private initialize(): void {
        logger.info('ScmInputHandler initializing...');

        const git = this.getGitAPI();
        if (!git) {
            logger.warn('Git extension not available, retrying in 2 seconds...');
            setTimeout(() => this.initialize(), 2000);
            return;
        }

        // Track existing repositories
        for (const repo of git.repositories) {
            this.trackRepository(repo);
        }

        // Track new repositories
        this.disposables.push(
            git.onDidOpenRepository((repo) => {
                logger.info('New repository opened', { root: repo.rootUri.fsPath });
                this.trackRepository(repo);
            })
        );

        // Untrack closed repositories
        this.disposables.push(
            git.onDidCloseRepository((repo) => {
                logger.info('Repository closed', { root: repo.rootUri.fsPath });
                this.trackedRepositories.delete(repo);
            })
        );

        logger.info('ScmInputHandler initialized', { repoCount: git.repositories.length });
    }

    /**
     * Track a repository for commit events
     */
    private trackRepository(repo: Repository): void {
        if (this.trackedRepositories.has(repo)) {
            return;
        }

        this.trackedRepositories.add(repo);
        this.lastCommitHash = repo.state.HEAD?.commit;

        // Update CommitPanelAccessor with the repository
        const accessor = getCommitPanelAccessor();
        accessor.setRepository(repo);

        // Track state changes to detect commits
        this.disposables.push(
            repo.state.onDidChange(() => {
                this.onRepositoryStateChange(repo);
            })
        );

        logger.info('Repository tracked', { 
            root: repo.rootUri.fsPath,
            branch: repo.state.HEAD?.name 
        });
    }

    /**
     * Handle repository state changes
     */
    private onRepositoryStateChange(repo: Repository): void {
        const currentHash = repo.state.HEAD?.commit;

        // Detect new commit
        if (currentHash && currentHash !== this.lastCommitHash) {
            logger.info('Commit detected', { 
                oldHash: this.lastCommitHash?.substring(0, 8),
                newHash: currentHash.substring(0, 8)
            });

            this.lastCommitHash = currentHash;
            this.handleCommitSuccess();
        }

        // Update selected changes in accessor
        const accessor = getCommitPanelAccessor();
        const allChanges = accessor.getAllChanges();
        accessor.updateSelectedChanges(allChanges.map(c => c.path));
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
     * Register a commit lifecycle listener
     */
    registerLifecycleListener(listener: CommitLifecycleListener): vscode.Disposable {
        this.lifecycleListeners.push(listener);
        return {
            dispose: () => {
                const index = this.lifecycleListeners.indexOf(listener);
                if (index >= 0) {
                    this.lifecycleListeners.splice(index, 1);
                }
            }
        };
    }

    /**
     * Get current commit state
     */
    getCommitState(): CommitState {
        return this.commitState;
    }

    /**
     * Set commit state and notify listeners
     */
    private setCommitState(state: CommitState): void {
        this.commitState = state;
        for (const listener of this.lifecycleListeners) {
            listener.onCommitStateChange?.(state);
        }
    }

    /**
     * Called before commit (hook point)
     * Returns true if commit should proceed
     */
    async beforeCommit(): Promise<boolean> {
        this.setCommitState('preparing');

        for (const listener of this.lifecycleListeners) {
            if (listener.onBeforeCommit) {
                const shouldProceed = await listener.onBeforeCommit();
                if (!shouldProceed) {
                    this.setCommitState('idle');
                    return false;
                }
            }
        }

        this.setCommitState('committing');
        return true;
    }

    /**
     * Handle successful commit
     */
    private handleCommitSuccess(): void {
        this.setCommitState('success');

        // Notify listeners
        for (const listener of this.lifecycleListeners) {
            listener.onCommitSuccess?.();
        }

        // Clear accessor state
        const accessor = getCommitPanelAccessor();
        accessor.clear();

        // Reset state after a short delay
        setTimeout(() => {
            this.setCommitState('idle');
        }, 1000);

        logger.info('Commit successful, accessor cleared');
    }

    /**
     * Handle failed commit
     */
    handleCommitFailed(error: Error): void {
        this.setCommitState('failed');

        // Notify listeners
        for (const listener of this.lifecycleListeners) {
            listener.onCommitFailed?.(error);
        }

        // Clear accessor state
        const accessor = getCommitPanelAccessor();
        accessor.clear();

        // Reset state after a short delay
        setTimeout(() => {
            this.setCommitState('idle');
        }, 1000);

        logger.info('Commit failed, accessor cleared');
    }

    /**
     * Check if there are changes to commit
     */
    hasChanges(): boolean {
        const accessor = getCommitPanelAccessor();
        return accessor.getAllChanges().length > 0;
    }

    /**
     * Get selected changes count
     */
    getSelectedChangesCount(): number {
        const accessor = getCommitPanelAccessor();
        const selected = accessor.getSelectedChanges();
        return selected ? selected.length : accessor.getAllChanges().length;
    }
}

/**
 * Initialize SCM input handler for the extension
 * Should be called during extension activation
 */
export function initializeScmInputHandler(): vscode.Disposable {
    const handler = ScmInputHandler.getInstance();
    logger.info('SCM Input Handler activated');
    
    return {
        dispose: () => {
            ScmInputHandler.dispose();
        }
    };
}

// Export singleton accessor function for convenience
export function getScmInputHandler(): ScmInputHandler {
    return ScmInputHandler.getInstance();
}

/**
 * Git Branch Service
 * 
 * Git branch operations using VS Code Git Extension
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/GitBranchService.kt
 */

import * as vscode from 'vscode';

/**
 * Git extension API interface
 */
interface GitExtension {
    getAPI(version: number): GitAPI | undefined;
}

interface GitAPI {
    repositories: Repository[];
}

interface Repository {
    state: RepositoryState;
}

interface RepositoryState {
    HEAD?: {
        name?: string;
        commit?: string;
    };
    refs: Ref[];
}

interface Ref {
    type: number; // 0 = Head, 1 = RemoteHead, 2 = Tag
    name?: string;
}

/**
 * Git Branch Service interface
 */
export interface GitBranchService {
    /**
     * Get current branch name
     * @returns Branch name, or undefined if not available
     */
    getCurrentBranchName(): string | undefined;
    
    /**
     * Get all local branch names
     * @returns List of branch names
     */
    getLocalBranches(): string[];
    
    /**
     * Check if Git is available
     * @returns true if project has Git repository
     */
    isGitAvailable(): boolean;
}

/**
 * VS Code Git Branch Service implementation
 */
export class VscodeGitBranchService implements GitBranchService {
    private static instance: VscodeGitBranchService | undefined;
    
    private constructor() {}
    
    static getInstance(): VscodeGitBranchService {
        if (!this.instance) {
            this.instance = new VscodeGitBranchService();
        }
        return this.instance;
    }
    
    /**
     * Get Git API from VS Code extension
     */
    private getGitAPI(): GitAPI | undefined {
        try {
            const gitExtension = vscode.extensions.getExtension<GitExtension>('vscode.git');
            if (!gitExtension) {
                return undefined;
            }
            
            if (!gitExtension.isActive) {
                return undefined;
            }
            
            return gitExtension.exports.getAPI(1);
        } catch {
            return undefined;
        }
    }
    
    /**
     * Get first repository
     */
    private getRepository(): Repository | undefined {
        const api = this.getGitAPI();
        return api?.repositories[0];
    }
    
    /**
     * Get current branch name
     */
    getCurrentBranchName(): string | undefined {
        const repo = this.getRepository();
        return repo?.state.HEAD?.name;
    }
    
    /**
     * Get all local branch names
     */
    getLocalBranches(): string[] {
        const repo = this.getRepository();
        if (!repo) return [];
        
        return repo.state.refs
            .filter(ref => ref.type === 0 && ref.name) // Type 0 = Head (local branch)
            .map(ref => ref.name!)
            .filter(name => name !== 'HEAD');
    }
    
    /**
     * Check if Git is available
     */
    isGitAvailable(): boolean {
        const api = this.getGitAPI();
        return api !== undefined && api.repositories.length > 0;
    }
}

/**
 * Noop Git Branch Service - when Git is not available
 */
export class NoopGitBranchService implements GitBranchService {
    getCurrentBranchName(): string | undefined {
        return undefined;
    }
    
    getLocalBranches(): string[] {
        return [];
    }
    
    isGitAvailable(): boolean {
        return false;
    }
}

// Export singleton instance
export const gitBranchService = VscodeGitBranchService.getInstance();

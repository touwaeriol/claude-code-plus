/**
 * 项目级会话状态服务
 *
 * 提供项目级别的会话状态管理功能
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/ProjectSessionStateService.kt
 */

import * as vscode from 'vscode';
import { SessionState, ServiceStats } from './claudeCodePlusBackgroundService';

/**
 * 项目会话状态服务
 *
 * 提供项目级别的会话状态管理功能
 */
export class ProjectSessionStateService {
    private static instance: ProjectSessionStateService | null = null;
    private currentSessionId: string | null = null;
    private sessionStates = new Map<string, SessionState>();

    private constructor() {}

    /**
     * 获取单例实例
     */
    static getInstance(): ProjectSessionStateService {
        if (!this.instance) {
            this.instance = new ProjectSessionStateService();
        }
        return this.instance;
    }

    /**
     * 获取当前项目路径
     */
    private getProjectPath(): string | undefined {
        const workspaceFolders = vscode.workspace.workspaceFolders;
        return workspaceFolders?.[0]?.uri.fsPath;
    }

    /**
     * 获取当前会话 ID
     */
    getCurrentSessionId(): string | null {
        return this.currentSessionId;
    }

    /**
     * 设置当前会话 ID
     */
    setCurrentSessionId(sessionId: string | null): void {
        this.currentSessionId = sessionId;
    }

    /**
     * 获取会话状态
     */
    getSessionState(sessionId: string): SessionState | null {
        return this.sessionStates.get(sessionId) || null;
    }

    /**
     * 设置会话状态
     */
    setSessionState(sessionId: string, state: SessionState): void {
        this.sessionStates.set(sessionId, state);
    }

    /**
     * 清理当前会话
     */
    clearCurrentSession(): void {
        if (this.currentSessionId) {
            this.sessionStates.delete(this.currentSessionId);
            this.currentSessionId = null;
        }
    }

    /**
     * 清理所有会话
     */
    clearAllSessions(): void {
        this.sessionStates.clear();
        this.currentSessionId = null;
    }

    /**
     * 获取统计信息
     */
    getStats(): ServiceStats {
        let totalMessages = 0;
        this.sessionStates.forEach(session => {
            totalMessages += session.messageCount;
        });

        return {
            activeSessionsCount: this.sessionStates.size,
            totalMessages,
            status: 'ready',
        };
    }

    /**
     * 获取服务统计信息（别名）
     */
    getServiceStats(): ServiceStats {
        return this.getStats();
    }

    /**
     * 获取所有会话 ID
     */
    getAllSessionIds(): string[] {
        return Array.from(this.sessionStates.keys());
    }

    /**
     * 检查会话是否存在
     */
    hasSession(sessionId: string): boolean {
        return this.sessionStates.has(sessionId);
    }

    /**
     * 获取活跃会话数量
     */
    getActiveSessionCount(): number {
        let count = 0;
        this.sessionStates.forEach(session => {
            if (session.status === 'active') {
                count++;
            }
        });
        return count;
    }

    /**
     * 释放资源
     */
    dispose(): void {
        this.clearAllSessions();
        ProjectSessionStateService.instance = null;
    }
}

/**
 * 获取项目会话状态服务实例
 */
export function getProjectSessionStateService(): ProjectSessionStateService {
    return ProjectSessionStateService.getInstance();
}

/**
 * 导出默认单例（兼容 Kotlin object 模式）
 */
export const projectSessionStateService = ProjectSessionStateService.getInstance();

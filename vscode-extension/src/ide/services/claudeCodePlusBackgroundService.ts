/**
 * Claude Code Plus 后台服务
 *
 * 管理会话状态和更新
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/services/ClaudeCodePlusBackgroundService.kt
 */

import { EventEmitter } from 'events';

/**
 * 会话状态
 */
export interface SessionState {
    sessionId: string;
    status: 'idle' | 'active' | 'waiting' | 'error';
    lastMessageTime?: number;
    messageCount: number;
    error?: string;
}

/**
 * 会话更新
 */
export interface SessionUpdate {
    sessionId: string;
    type: 'status_change' | 'message' | 'error';
    data?: any;
    timestamp: number;
}

/**
 * 服务统计信息
 */
export interface ServiceStats {
    activeSessionsCount: number;
    totalMessages: number;
    status: 'ready' | 'busy' | 'error';
}

/**
 * Claude Code Plus 后台服务
 *
 * 这是从 cli-wrapper 模块迁移到 claude-agent-sdk 的简化版本，
 * 包含了 vscode-extension 模块所需的基本类型定义。
 */
export class ClaudeCodePlusBackgroundService {
    private static instance: ClaudeCodePlusBackgroundService | null = null;
    private readonly emitter = new EventEmitter();
    private readonly sessions = new Map<string, SessionState>();
    private readonly projectSessions = new Map<string, Map<string, SessionState>>();

    private constructor() {
        // 设置最大监听器数量
        this.emitter.setMaxListeners(100);
    }

    /**
     * 获取单例实例
     */
    static getInstance(): ClaudeCodePlusBackgroundService {
        if (!this.instance) {
            this.instance = new ClaudeCodePlusBackgroundService();
        }
        return this.instance;
    }

    /**
     * 获取会话状态
     */
    getSessionState(sessionId: string): SessionState | null {
        return this.sessions.get(sessionId) || null;
    }

    /**
     * 设置会话状态
     */
    setSessionState(sessionId: string, state: SessionState): void {
        this.sessions.set(sessionId, state);
        this.emitSessionUpdate(sessionId, {
            sessionId,
            type: 'status_change',
            data: state,
            timestamp: Date.now(),
        });
    }

    /**
     * 观察会话更新
     * 返回一个取消订阅函数
     */
    observeSessionUpdates(
        sessionId: string,
        callback: (update: SessionUpdate) => void
    ): () => void {
        const eventName = `session:${sessionId}`;
        this.emitter.on(eventName, callback);

        return () => {
            this.emitter.off(eventName, callback);
        };
    }

    /**
     * 发送会话更新事件
     */
    private emitSessionUpdate(sessionId: string, update: SessionUpdate): void {
        const eventName = `session:${sessionId}`;
        this.emitter.emit(eventName, update);
    }

    /**
     * 获取服务统计信息
     */
    getServiceStats(): ServiceStats {
        let totalMessages = 0;
        this.sessions.forEach(session => {
            totalMessages += session.messageCount;
        });

        return {
            activeSessionsCount: this.sessions.size,
            totalMessages,
            status: 'ready',
        };
    }

    /**
     * 观察项目更新
     * 返回一个取消订阅函数
     */
    observeProjectUpdates(
        projectPath: string,
        callback: (sessions: Map<string, SessionState>) => void
    ): () => void {
        const eventName = `project:${projectPath}`;
        this.emitter.on(eventName, callback);

        return () => {
            this.emitter.off(eventName, callback);
        };
    }

    /**
     * 获取项目的所有会话
     */
    getProjectSessions(projectPath: string): Map<string, SessionState> {
        return this.projectSessions.get(projectPath) || new Map();
    }

    /**
     * 添加项目会话
     */
    addProjectSession(projectPath: string, sessionId: string, state: SessionState): void {
        let sessions = this.projectSessions.get(projectPath);
        if (!sessions) {
            sessions = new Map();
            this.projectSessions.set(projectPath, sessions);
        }
        sessions.set(sessionId, state);
        this.sessions.set(sessionId, state);

        // 发送项目更新事件
        const eventName = `project:${projectPath}`;
        this.emitter.emit(eventName, sessions);
    }

    /**
     * 移除项目会话
     */
    removeProjectSession(projectPath: string, sessionId: string): void {
        const sessions = this.projectSessions.get(projectPath);
        if (sessions) {
            sessions.delete(sessionId);
            if (sessions.size === 0) {
                this.projectSessions.delete(projectPath);
            }

            // 发送项目更新事件
            const eventName = `project:${projectPath}`;
            this.emitter.emit(eventName, sessions);
        }
        this.sessions.delete(sessionId);
    }

    /**
     * 清理所有会话
     */
    clearAllSessions(): void {
        this.sessions.clear();
        this.projectSessions.clear();
        this.emitter.removeAllListeners();
    }

    /**
     * 释放资源
     */
    dispose(): void {
        this.clearAllSessions();
        ClaudeCodePlusBackgroundService.instance = null;
    }
}

/**
 * 获取后台服务实例
 */
export function getBackgroundService(): ClaudeCodePlusBackgroundService {
    return ClaudeCodePlusBackgroundService.getInstance();
}

/**
 * 导出默认单例（兼容 Kotlin object 模式）
 */
export const claudeCodePlusBackgroundService = ClaudeCodePlusBackgroundService.getInstance();

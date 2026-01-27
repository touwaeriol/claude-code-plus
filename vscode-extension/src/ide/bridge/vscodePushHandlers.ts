/**
 * VS Code IDE 推送处理器
 *
 * 负责将 IDE 事件推送到前端客户端（通过 WebView 或 WebSocket）
 *
 * 对应 Kotlin 版本: jetbrains-plugin/src/main/kotlin/com/asakii/plugin/bridge/JetBrainsPushHandlers.kt
 */

import * as vscode from 'vscode';
import type { VsCodeIdeTheme, ActiveFileInfo, VsCodeSessionCommand } from './vscodeApiImpl';

/**
 * 客户端接口 - 可以是 WebView 或 WebSocket 连接
 */
export interface PushClient {
    id: string;
    send(message: ServerCallMessage): Promise<void>;
}

/**
 * WebView 客户端实现
 */
export class WebViewClient implements PushClient {
    constructor(
        public readonly id: string,
        private readonly webview: vscode.Webview
    ) {}

    async send(message: ServerCallMessage): Promise<void> {
        this.webview.postMessage(message);
    }
}

/**
 * 服务器推送消息格式
 */
export interface ServerCallMessage {
    callId: string;
    method: string;
    payload: any;
}

/**
 * 主题变更通知
 */
export interface ThemeChangedNotify {
    background: string;
    foreground: string;
    borderColor: string;
    panelBackground: string;
    textFieldBackground: string;
    selectionBackground: string;
    selectionForeground: string;
    linkColor: string;
    errorColor: string;
    warningColor: string;
    successColor: string;
    separatorColor: string;
    hoverBackground: string;
    accentColor: string;
    infoBackground: string;
    codeBackground: string;
    secondaryForeground: string;
    fontFamily: string;
    fontSize: number;
    editorFontFamily: string;
    editorFontSize: number;
}

/**
 * IDE 设置
 */
export interface IdeSettings {
    defaultModelId: string;
    defaultModelName: string;
    defaultBypassPermissions: boolean;
    claudeDefaultAutoCleanupContexts: boolean;
    codexDefaultAutoCleanupContexts: boolean;
    enableUserInteractionMcp: boolean;
    enableJetBrainsMcp: boolean;
    includePartialMessages: boolean;
    defaultThinkingLevel: string;
    defaultThinkingTokens: number;
    defaultThinkingLevelId: string;
    thinkingLevels: ThinkingLevelConfig[];
    permissionMode: string;
    codexDefaultModelId: string;
    codexDefaultReasoningEffort: string;
    codexDefaultReasoningSummary: string;
    codexDefaultSandboxMode: string;
}

/**
 * 思考级别配置
 */
export interface ThinkingLevelConfig {
    id: string;
    name: string;
    tokens: number;
    isCustom: boolean;
}

/**
 * 会话命令类型
 */
export enum SessionCommandType {
    UNSPECIFIED = 'UNSPECIFIED',
    SWITCH = 'SWITCH',
    CREATE = 'CREATE',
    CLOSE = 'CLOSE',
    RENAME = 'RENAME',
    TOGGLE_HISTORY = 'TOGGLE_HISTORY',
    SET_LOCALE = 'SET_LOCALE',
    DELETE = 'DELETE',
    RESET = 'RESET',
}

/**
 * 终端任务动作
 */
export enum TerminalTaskAction {
    STARTED = 'started',
    COMPLETED = 'completed',
    BACKGROUNDED = 'backgrounded',
}

/**
 * 终端任务更新通知
 */
export interface TerminalTaskUpdateNotify {
    toolUseId: string;
    sessionId: string;
    action: TerminalTaskAction;
    command: string;
    isBackground: boolean;
    startTime: number;
    elapsedMs?: number;
}

/**
 * 活跃文件变更通知
 */
export interface ActiveFileChangedNotify {
    hasActiveFile: boolean;
    path?: string;
    relativePath?: string;
    name?: string;
    line?: number;
    column?: number;
    hasSelection?: boolean;
    startLine?: number;
    startColumn?: number;
    endLine?: number;
    endColumn?: number;
}

/**
 * VS Code 推送处理器
 */
export class VsCodePushHandlers {
    private connectedClients: Map<string, PushClient> = new Map();
    private callIdCounter = 0;

    /**
     * 注册客户端
     */
    registerClient(client: PushClient): void {
        this.connectedClients.set(client.id, client);
        console.log(`📥 [VsCode Push] Client registered: ${client.id}`);
    }

    /**
     * 注销客户端
     */
    unregisterClient(clientId: string): void {
        this.connectedClients.delete(clientId);
        console.log(`📤 [VsCode Push] Client unregistered: ${clientId}`);
    }

    /**
     * 获取已连接客户端数量
     */
    get clientCount(): number {
        return this.connectedClients.size;
    }

    /**
     * 推送主题变化到前端
     * 广播给所有连接的客户端
     */
    async pushThemeChanged(theme: VsCodeIdeTheme): Promise<void> {
        const clients = Array.from(this.connectedClients.values());
        if (clients.length === 0) {
            console.warn('⚠️ [VsCode Push] No clients connected, skipping theme push');
            return;
        }

        try {
            const themeNotify: ThemeChangedNotify = {
                background: theme.background,
                foreground: theme.foreground,
                borderColor: theme.borderColor,
                panelBackground: theme.panelBackground,
                textFieldBackground: theme.textFieldBackground,
                selectionBackground: theme.selectionBackground,
                selectionForeground: theme.selectionForeground,
                linkColor: theme.linkColor,
                errorColor: theme.errorColor,
                warningColor: theme.warningColor,
                successColor: theme.successColor,
                separatorColor: theme.separatorColor,
                hoverBackground: theme.hoverBackground,
                accentColor: theme.accentColor,
                infoBackground: theme.infoBackground,
                codeBackground: theme.codeBackground,
                secondaryForeground: theme.secondaryForeground,
                fontFamily: theme.fontFamily,
                fontSize: theme.fontSize,
                editorFontFamily: theme.editorFontFamily,
                editorFontSize: theme.editorFontSize,
            };

            const callId = `vsc-${++this.callIdCounter}`;
            const serverCall: ServerCallMessage = {
                callId,
                method: 'onThemeChanged',
                payload: themeNotify,
            };

            await this.broadcastToClients(clients, serverCall);
            console.log(`📤 [VsCode Push] → pushThemeChanged (to ${clients.length} clients)`);
        } catch (e) {
            console.error(`❌ [VsCode Push] pushThemeChanged failed: ${e}`);
        }
    }

    /**
     * 推送设置变更到前端
     * 广播给所有连接的客户端
     */
    async pushSettingsChanged(settings: IdeSettings): Promise<void> {
        const clients = Array.from(this.connectedClients.values());
        if (clients.length === 0) {
            console.warn('⚠️ [VsCode Push] No clients connected, skipping settings push');
            return;
        }

        try {
            const callId = `vsc-${++this.callIdCounter}`;
            const serverCall: ServerCallMessage = {
                callId,
                method: 'onSettingsChanged',
                payload: { settings },
            };

            await this.broadcastToClients(clients, serverCall);
            console.log(`📤 [VsCode Push] → pushSettingsChanged (to ${clients.length} clients)`);
        } catch (e) {
            console.error(`❌ [VsCode Push] pushSettingsChanged failed: ${e}`);
        }
    }

    /**
     * 推送会话命令到前端
     * 广播给所有连接的客户端
     */
    async pushSessionCommand(command: VsCodeSessionCommand): Promise<void> {
        const clients = Array.from(this.connectedClients.values());
        if (clients.length === 0) {
            console.warn('⚠️ [VsCode Push] No clients connected, skipping command push');
            return;
        }

        try {
            const callId = `vsc-${++this.callIdCounter}`;
            const serverCall: ServerCallMessage = {
                callId,
                method: 'onSessionCommand',
                payload: command,
            };

            await this.broadcastToClients(clients, serverCall);
            console.log(`📤 [VsCode Push] → pushSessionCommand: ${command.type} (to ${clients.length} clients)`);
        } catch (e) {
            console.error(`❌ [VsCode Push] pushSessionCommand failed: ${e}`);
        }
    }

    /**
     * 推送终端任务更新到前端
     * 广播给所有连接的客户端
     */
    async pushTerminalTaskUpdate(
        toolUseId: string,
        sessionId: string,
        action: TerminalTaskAction | string,
        command: string,
        isBackground: boolean,
        startTime: number,
        elapsedMs?: number
    ): Promise<void> {
        const clients = Array.from(this.connectedClients.values());
        if (clients.length === 0) {
            console.debug('⚠️ [VsCode Push] No clients connected, skipping terminal task push');
            return;
        }

        try {
            const taskAction = typeof action === 'string' ? action as TerminalTaskAction : action;

            const notify: TerminalTaskUpdateNotify = {
                toolUseId,
                sessionId,
                action: taskAction,
                command,
                isBackground,
                startTime,
                elapsedMs,
            };

            const callId = `vsc-${++this.callIdCounter}`;
            const serverCall: ServerCallMessage = {
                callId,
                method: 'onTerminalTaskUpdate',
                payload: notify,
            };

            await this.broadcastToClients(clients, serverCall);
            console.debug(`📤 [VsCode Push] → pushTerminalTaskUpdate: ${action} (toolUseId=${toolUseId}, to ${clients.length} clients)`);
        } catch (e) {
            console.error(`❌ [VsCode Push] pushTerminalTaskUpdate failed: ${e}`);
        }
    }

    /**
     * 推送活跃文件变更到前端
     * 广播给所有连接的客户端
     */
    async pushActiveFileChanged(activeFile: ActiveFileInfo | null): Promise<void> {
        const clients = Array.from(this.connectedClients.values());
        if (clients.length === 0) {
            console.warn('⚠️ [VsCode Push] No clients connected, skipping active file push');
            return;
        }

        try {
            const notify: ActiveFileChangedNotify = {
                hasActiveFile: activeFile !== null,
            };

            if (activeFile) {
                notify.path = activeFile.path;
                notify.relativePath = activeFile.relativePath;
                notify.name = activeFile.name;
                notify.line = activeFile.line;
                notify.column = activeFile.column;
                notify.hasSelection = activeFile.hasSelection;
                notify.startLine = activeFile.startLine;
                notify.startColumn = activeFile.startColumn;
                notify.endLine = activeFile.endLine;
                notify.endColumn = activeFile.endColumn;
            }

            const callId = `vsc-${++this.callIdCounter}`;
            const serverCall: ServerCallMessage = {
                callId,
                method: 'onActiveFileChanged',
                payload: notify,
            };

            await this.broadcastToClients(clients, serverCall);

            if (activeFile) {
                const selectionInfo = activeFile.hasSelection
                    ? ` (selection: ${activeFile.startLine}:${activeFile.startColumn} - ${activeFile.endLine}:${activeFile.endColumn})`
                    : '';
                console.log(`📤 [VsCode Push] → pushActiveFileChanged: ${activeFile.relativePath} (to ${clients.length} clients)${selectionInfo}`);
            } else {
                console.log(`📤 [VsCode Push] → pushActiveFileChanged: null (no active file, to ${clients.length} clients)`);
            }
        } catch (e) {
            console.error(`❌ [VsCode Push] pushActiveFileChanged failed: ${e}`);
        }
    }

    /**
     * 广播消息给所有客户端
     */
    private async broadcastToClients(clients: PushClient[], message: ServerCallMessage): Promise<void> {
        const promises = clients.map(async (client) => {
            try {
                await client.send(message);
            } catch (e) {
                console.warn(`⚠️ [VsCode Push] Failed to push to client ${client.id}: ${e}`);
            }
        });

        await Promise.all(promises);
    }
}

/**
 * 创建全局推送处理器实例
 */
let globalPushHandlers: VsCodePushHandlers | null = null;

export function getPushHandlers(): VsCodePushHandlers {
    if (!globalPushHandlers) {
        globalPushHandlers = new VsCodePushHandlers();
    }
    return globalPushHandlers;
}

export function disposePushHandlers(): void {
    globalPushHandlers = null;
}

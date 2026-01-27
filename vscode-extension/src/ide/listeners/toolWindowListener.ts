/**
 * Tool Window Listener
 * 
 * Monitors webview panel visibility and manages session state continuity.
 * When panel is hidden, background service continues execution;
 * when panel is shown again, automatically restores latest state.
 * 
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/listeners/ClaudeToolWindowListener.kt
 */

import * as vscode from 'vscode';
import { SessionState } from '../types';
import { toolWindowStateEmitter } from './events';

/**
 * Background service interface (to avoid circular dependency)
 */
interface BackgroundServiceLike {
    getSessionState(sessionId: string): SessionState | undefined;
    getServiceStats(): Record<string, unknown>;
}

/**
 * Tool Window Listener for VS Code
 * 
 * Monitors webview panel visibility and maintains session state continuity.
 */
export class ToolWindowListener implements vscode.Disposable {
    private static instance: ToolWindowListener | undefined;
    
    // State
    private isToolWindowVisible = false;
    private lastVisibleTime = Date.now();
    private activeSessionIds = new Set<string>();
    
    // Disposables
    private disposables: vscode.Disposable[] = [];
    
    // Background service reference (injected)
    private backgroundService: BackgroundServiceLike | undefined;
    
    private constructor() {
        console.log('🎯 ToolWindowListener initialized');
    }
    
    static getInstance(): ToolWindowListener {
        if (!this.instance) {
            this.instance = new ToolWindowListener();
        }
        return this.instance;
    }
    
    /**
     * Set background service reference
     */
    setBackgroundService(service: BackgroundServiceLike): void {
        this.backgroundService = service;
    }
    
    /**
     * Handle webview panel visibility change
     */
    onWebviewPanelVisibilityChanged(visible: boolean): void {
        const wasVisible = this.isToolWindowVisible;
        const nowVisible = visible;
        
        if (wasVisible !== nowVisible) {
            if (nowVisible) {
                this.handleToolWindowShown();
            } else {
                this.handleToolWindowHidden();
            }
        }
    }
    
    /**
     * Handle tool window shown
     */
    private handleToolWindowShown(): void {
        console.log('👁️ Tool window shown');
        this.isToolWindowVisible = true;
        
        // Calculate hidden duration
        const hiddenDuration = Date.now() - this.lastVisibleTime;
        console.log(`⏱️ Tool window was hidden for: ${Math.round(hiddenDuration / 1000)}s`);
        
        // Restore session states
        this.restoreSessionStates();
        
        // Notify listeners
        toolWindowStateEmitter.emit(true);
    }
    
    /**
     * Handle tool window hidden
     */
    private handleToolWindowHidden(): void {
        console.log('🙈 Tool window hidden');
        this.isToolWindowVisible = false;
        this.lastVisibleTime = Date.now();
        
        // Save current state snapshot
        this.saveCurrentSessionStates();
        
        // Record active sessions
        this.recordActiveSessionIds();
        
        // Notify listeners
        toolWindowStateEmitter.emit(false);
        
        console.log('💾 Session state saved, background service continues running');
    }
    
    /**
     * Save current session states
     */
    private saveCurrentSessionStates(): void {
        if (!this.backgroundService) return;
        
        try {
            // Background service maintains state in memory
            // Just record session IDs here
            console.log(`💾 Saving ${this.activeSessionIds.size} session states`);
        } catch (error) {
            console.error('Failed to save session states:', error);
        }
    }
    
    /**
     * Restore session states
     */
    private restoreSessionStates(): void {
        if (!this.backgroundService) return;
        
        try {
            console.log(`🔄 Restoring session states, active sessions: ${this.activeSessionIds.size}`);
            
            this.activeSessionIds.forEach(sessionId => {
                const state = this.backgroundService!.getSessionState(sessionId);
                if (state) {
                    console.log(`✅ Session ${sessionId} in memory: ${state.messages.length} messages, generating=${state.isGenerating}`);
                } else {
                    console.log(`⚠️ Session ${sessionId} not in memory`);
                }
            });
            
            // Get service stats
            const stats = this.backgroundService.getServiceStats();
            console.log('📊 Background service stats:', stats);
        } catch (error) {
            console.error('Failed to restore session states:', error);
        }
    }
    
    /**
     * Record active session IDs
     */
    private recordActiveSessionIds(): void {
        // This should be called from UI component
        // Recording happens automatically through registerSession
    }
    
    /**
     * Register session
     */
    registerSession(sessionId: string): void {
        this.activeSessionIds.add(sessionId);
        console.log(`➕ Session registered: ${sessionId}, total: ${this.activeSessionIds.size}`);
    }
    
    /**
     * Unregister session
     */
    unregisterSession(sessionId: string): void {
        this.activeSessionIds.delete(sessionId);
        console.log(`➖ Session unregistered: ${sessionId}, remaining: ${this.activeSessionIds.size}`);
    }
    
    /**
     * Check if tool window is visible
     */
    isToolWindowCurrentlyVisible(): boolean {
        return this.isToolWindowVisible;
    }
    
    /**
     * Get active session IDs
     */
    getActiveSessionIds(): string[] {
        return Array.from(this.activeSessionIds);
    }
    
    /**
     * Dispose resources
     */
    dispose(): void {
        console.log('🧹 Disposing ToolWindowListener');
        this.disposables.forEach(d => d.dispose());
        this.disposables = [];
        this.activeSessionIds.clear();
        ToolWindowListener.instance = undefined;
    }
}

// Export singleton instance
export const toolWindowListener = ToolWindowListener.getInstance();

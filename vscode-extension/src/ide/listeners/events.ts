/**
 * Tool Window State Changed Event
 * 
 * Event types for tool window state changes
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/listeners/ToolWindowStateChangedTopic.kt
 */

import * as vscode from 'vscode';

/**
 * Tool window state change event
 */
export interface ToolWindowStateChangeEvent {
    isVisible: boolean;
    timestamp: number;
}

/**
 * Tool window state change listener
 */
export type ToolWindowStateChangeListener = (event: ToolWindowStateChangeEvent) => void;

/**
 * Event emitter for tool window state changes
 */
class ToolWindowStateEmitter {
    private listeners: Set<ToolWindowStateChangeListener> = new Set();
    
    /**
     * Subscribe to state changes
     */
    subscribe(listener: ToolWindowStateChangeListener): vscode.Disposable {
        this.listeners.add(listener);
        return {
            dispose: () => {
                this.listeners.delete(listener);
            }
        };
    }
    
    /**
     * Emit state change event
     */
    emit(isVisible: boolean): void {
        const event: ToolWindowStateChangeEvent = {
            isVisible,
            timestamp: Date.now()
        };
        this.listeners.forEach(listener => {
            try {
                listener(event);
            } catch (error) {
                console.error('Error in tool window state listener:', error);
            }
        });
    }
    
    /**
     * Clear all listeners
     */
    clear(): void {
        this.listeners.clear();
    }
}

// Export singleton instance
export const toolWindowStateEmitter = new ToolWindowStateEmitter();

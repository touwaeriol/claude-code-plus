/**
 * IDE Integration Interface
 * 
 * Defines IDE-related operation interfaces
 * Translated from jetbrains-plugin/src/main/kotlin/com/asakii/plugin/adapters/IdeIntegration.kt
 */

import { LegacyToolCall } from '../types';

/**
 * Notification type
 */
export enum NotificationType {
    INFO = 'INFO',
    WARNING = 'WARNING',
    ERROR = 'ERROR'
}

/**
 * IDE Integration interface
 * 
 * Defines common operations for IDE integration
 */
export interface IdeIntegration {
    /**
     * Handle tool click event
     */
    handleToolClick(toolCall: LegacyToolCall): Promise<boolean>;
    
    /**
     * Open file
     */
    openFile(filePath: string, line?: number, column?: number): Promise<boolean>;
    
    /**
     * Show file diff
     */
    showDiff(filePath: string, oldContent: string, newContent: string): Promise<boolean>;
    
    /**
     * Show notification
     */
    showNotification(message: string, type: NotificationType): void;
    
    /**
     * Check if supported
     */
    isSupported(): boolean;
    
    /**
     * Get IDE locale
     */
    getIdeLocale(): string;
}

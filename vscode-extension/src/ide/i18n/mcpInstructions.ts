/**
 * MCP Instructions Loader
 * 
 * Loads localized MCP instruction files (.md) based on current locale.
 * Equivalent to JetBrains McpInstructions.
 */

import * as vscode from 'vscode';
import * as path from 'path';
import * as fs from 'fs';
import { detectLocale } from './baseBundle';
import { Locale } from './types';

/**
 * Predefined MCP names
 */
export const McpNames = {
    USER_INTERACTION: 'user-interaction',
    // IDE-agnostic names (shared across JetBrains and VS Code)
    IDE_LSP: 'ide-lsp',
    IDE_FILE: 'ide-file',
    IDE_TERMINAL: 'ide-terminal',
    IDE_GIT: 'ide-git',
    CONTEXT7: 'context7',
    
    // Deprecated aliases (for backward compatibility)
    /** @deprecated Use IDE_LSP instead */
    JETBRAINS_LSP: 'ide-lsp',
    /** @deprecated Use IDE_FILE instead */
    JETBRAINS_FILE: 'ide-file',
    /** @deprecated Use IDE_TERMINAL instead */
    JETBRAINS_TERMINAL: 'ide-terminal',
    /** @deprecated Use IDE_GIT instead */
    JETBRAINS_GIT: 'ide-git',
    /** @deprecated Use IDE_LSP instead */
    VSCODE_LSP: 'ide-lsp',
    /** @deprecated Use IDE_FILE instead */
    VSCODE_FILE: 'ide-file',
    /** @deprecated Use IDE_TERMINAL instead */
    VSCODE_TERMINAL: 'ide-terminal',
    /** @deprecated Use IDE_GIT instead */
    VSCODE_GIT: 'ide-git',
} as const;

export type McpName = typeof McpNames[keyof typeof McpNames];

/**
 * MCP Instructions Loader
 * 
 * Loads MCP instruction markdown files based on current locale.
 * Falls back to English if locale-specific file is not found.
 */
export class McpInstructions {
    private static extensionPath: string | undefined;
    private static currentLocale: Locale = detectLocale();
    
    /**
     * Initialize with extension context
     * Must be called during extension activation
     */
    static initialize(context: vscode.ExtensionContext): void {
        this.extensionPath = context.extensionPath;
    }
    
    /**
     * Set custom extension path (for testing)
     */
    static setExtensionPath(path: string): void {
        this.extensionPath = path;
    }
    
    /**
     * Get language directory name based on current locale
     */
    private static getLanguageDir(): string {
        switch (this.currentLocale) {
            case 'zh_CN':
                return 'zh_CN';
            case 'ja':
                return 'ja';
            case 'ko':
                return 'ko';
            default:
                return 'en';
        }
    }
    
    /**
     * Set current locale
     */
    static setLocale(locale: Locale): void {
        this.currentLocale = locale;
    }
    
    /**
     * Get current locale
     */
    static getLocale(): Locale {
        return this.currentLocale;
    }
    
    /**
     * Load MCP instruction file
     * 
     * @param name MCP name (e.g., 'jetbrains-git', 'vscode-terminal')
     * @returns Instruction content or empty string if not found
     */
    static load(name: McpName | string): string {
        if (!this.extensionPath) {
            console.warn('[McpInstructions] Extension path not initialized. Call initialize() first.');
            return '';
        }
        
        const lang = this.getLanguageDir();
        const filePath = path.join(
            this.extensionPath,
            'resources',
            'mcp-instructions',
            lang,
            `${name}.md`
        );
        
        try {
            if (fs.existsSync(filePath)) {
                return fs.readFileSync(filePath, 'utf-8');
            }
            return this.loadFallback(name);
        } catch (error) {
            console.warn(`[McpInstructions] Failed to load ${filePath}:`, error);
            return this.loadFallback(name);
        }
    }
    
    /**
     * Load fallback (English) version
     */
    private static loadFallback(name: string): string {
        if (!this.extensionPath) {
            return '';
        }
        
        const fallbackPath = path.join(
            this.extensionPath,
            'resources',
            'mcp-instructions',
            'en',
            `${name}.md`
        );
        
        try {
            if (fs.existsSync(fallbackPath)) {
                return fs.readFileSync(fallbackPath, 'utf-8');
            }
            return '';
        } catch (error) {
            console.warn(`[McpInstructions] Failed to load fallback ${fallbackPath}:`, error);
            return '';
        }
    }
    
    /**
     * Load instruction from string content (for embedded instructions)
     * Useful when instructions are bundled as TypeScript strings
     * 
     * @param instructions Map of locale to instruction content
     * @returns Instruction content for current locale
     */
    static loadFromStrings(instructions: Partial<Record<Locale, string>>): string {
        const content = instructions[this.currentLocale];
        if (content) {
            return content;
        }
        
        // Fallback to English
        return instructions['en'] ?? '';
    }
    
    /**
     * Check if an instruction file exists
     * 
     * @param name MCP name
     * @returns true if instruction exists (in any locale)
     */
    static exists(name: McpName | string): boolean {
        if (!this.extensionPath) {
            return false;
        }
        
        const lang = this.getLanguageDir();
        const filePath = path.join(
            this.extensionPath,
            'resources',
            'mcp-instructions',
            lang,
            `${name}.md`
        );
        
        if (fs.existsSync(filePath)) {
            return true;
        }
        
        // Check English fallback
        const fallbackPath = path.join(
            this.extensionPath,
            'resources',
            'mcp-instructions',
            'en',
            `${name}.md`
        );
        
        return fs.existsSync(fallbackPath);
    }
}

/**
 * Convenience function to load MCP instructions
 * 
 * @param name MCP name
 * @returns Instruction content
 */
export function loadMcpInstructions(name: McpName | string): string {
    return McpInstructions.load(name);
}

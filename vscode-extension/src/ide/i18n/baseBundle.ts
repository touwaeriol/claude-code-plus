/**
 * Base Bundle Class
 * 
 * Provides common functionality for message bundles with locale support.
 * Similar to JetBrains DynamicBundle.
 */

import * as vscode from 'vscode';
import { Locale, LocaleMessages, MessageBundle } from './types';

/**
 * Detect current locale from VS Code settings
 */
export function detectLocale(): Locale {
    const vscodeLocale = vscode.env.language;
    
    // Map VS Code locale to our supported locales
    if (vscodeLocale.startsWith('zh')) {
        // zh-cn, zh-tw, zh-hans, zh-hant
        return 'zh_CN';
    } else if (vscodeLocale.startsWith('ja')) {
        return 'ja';
    } else if (vscodeLocale.startsWith('ko')) {
        return 'ko';
    }
    
    return 'en';
}

/**
 * Abstract base class for message bundles
 */
export abstract class BaseBundle<K extends string = string> implements MessageBundle {
    private currentLocale: Locale;
    
    constructor(
        protected readonly messages: LocaleMessages<K>,
        initialLocale?: Locale
    ) {
        this.currentLocale = initialLocale ?? detectLocale();
    }
    
    /**
     * Get a localized message with parameter substitution
     * 
     * @param key Message key
     * @param params Parameters to substitute (replaces {0}, {1}, etc.)
     * @returns Localized string
     */
    message(key: K | string, ...params: (string | number)[]): string {
        const dict = this.messages[this.currentLocale] ?? this.messages['en'];
        let text = dict[key as K];
        
        // Fallback to English if key not found
        if (text === undefined && this.currentLocale !== 'en') {
            text = this.messages['en'][key as K];
        }
        
        // Return key if still not found
        if (text === undefined) {
            return key;
        }
        
        // Substitute parameters: {0}, {1}, etc.
        if (params.length > 0) {
            params.forEach((param, index) => {
                text = text.replace(new RegExp(`\\{${index}\\}`, 'g'), String(param));
            });
        }
        
        return text;
    }
    
    /**
     * Get current locale
     */
    getLocale(): Locale {
        return this.currentLocale;
    }
    
    /**
     * Set current locale
     */
    setLocale(locale: Locale): void {
        this.currentLocale = locale;
    }
    
    /**
     * Get language directory name for resource loading
     */
    getLanguageDir(): string {
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
}

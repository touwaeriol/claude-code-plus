/**
 * i18n Types and Interfaces
 * 
 * Provides type definitions for internationalization support.
 */

/**
 * Supported locales
 */
export type Locale = 'en' | 'zh_CN' | 'ja' | 'ko';

/**
 * Message dictionary type
 */
export type MessageDictionary = Record<string, string>;

/**
 * Locale messages map
 */
export type LocaleMessages<K extends string = string> = {
    [L in Locale]: Record<K, string>;
};

/**
 * Bundle interface for message retrieval
 */
export interface MessageBundle {
    /**
     * Get a localized message
     * @param key Message key
     * @param params Optional parameters for substitution
     * @returns Localized string
     */
    message(key: string, ...params: (string | number)[]): string;
    
    /**
     * Get current locale
     */
    getLocale(): Locale;
    
    /**
     * Set current locale
     */
    setLocale(locale: Locale): void;
}

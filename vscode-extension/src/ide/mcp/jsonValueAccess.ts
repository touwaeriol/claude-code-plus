/**
 * JSON Value Access Utilities
 * 
 * Helper functions for safely accessing values from JSON objects.
 * Translated from JetBrains plugin's JsonValueAccess.kt
 */

/**
 * Get a string value from a JSON object
 */
export function getString(obj: Record<string, unknown>, key: string): string | undefined {
    const value = obj[key];
    if (typeof value === 'string') {
        return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean') {
        return String(value);
    }
    return undefined;
}

/**
 * Get an integer value from a JSON object
 */
export function getInt(obj: Record<string, unknown>, key: string): number | undefined {
    const value = obj[key];
    if (typeof value === 'number') {
        return Math.floor(value);
    }
    if (typeof value === 'string') {
        const parsed = parseInt(value, 10);
        if (!isNaN(parsed)) {
            return parsed;
        }
        const asDouble = parseFloat(value);
        if (!isNaN(asDouble)) {
            return Math.floor(asDouble);
        }
    }
    return undefined;
}

/**
 * Get a long (number) value from a JSON object
 */
export function getLong(obj: Record<string, unknown>, key: string): number | undefined {
    const value = obj[key];
    if (typeof value === 'number') {
        return Math.floor(value);
    }
    if (typeof value === 'string') {
        const parsed = parseInt(value, 10);
        if (!isNaN(parsed)) {
            return parsed;
        }
        const asDouble = parseFloat(value);
        if (!isNaN(asDouble)) {
            return Math.floor(asDouble);
        }
    }
    return undefined;
}

/**
 * Get a boolean value from a JSON object
 */
export function getBoolean(obj: Record<string, unknown>, key: string): boolean | undefined {
    const value = obj[key];
    if (typeof value === 'boolean') {
        return value;
    }
    if (typeof value === 'string') {
        const lower = value.toLowerCase();
        if (lower === 'true') return true;
        if (lower === 'false') return false;
    }
    return undefined;
}

/**
 * Get a string array from a JSON object
 */
export function getStringList(obj: Record<string, unknown>, key: string): string[] | undefined {
    const value = obj[key];
    if (!Array.isArray(value)) {
        return undefined;
    }
    return value
        .map(item => {
            if (typeof item === 'string') return item;
            if (typeof item === 'number' || typeof item === 'boolean') return String(item);
            return undefined;
        })
        .filter((item): item is string => item !== undefined);
}

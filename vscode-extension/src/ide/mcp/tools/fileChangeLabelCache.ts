/**
 * File Change Label Cache
 * 
 * Caches original file contents before Edit/Write operations
 * to support Diff display and rollback.
 * Translated from JetBrains plugin's FileChangeLabelCache.kt
 */

/**
 * File change label cache singleton
 */
export class FileChangeLabelCache {
    private static instance: FileChangeLabelCache | null = null;
    
    // toolUseId -> original file content
    private originalContents: Map<string, string> = new Map();

    private constructor() {}

    /**
     * Get singleton instance
     */
    static getInstance(): FileChangeLabelCache {
        if (!FileChangeLabelCache.instance) {
            FileChangeLabelCache.instance = new FileChangeLabelCache();
        }
        return FileChangeLabelCache.instance;
    }

    /**
     * Record original content before file modification
     * @param toolUseId Tool use ID
     * @param content Original file content
     */
    recordOriginalContent(toolUseId: string, content: string): void {
        this.originalContents.set(toolUseId, content);
    }

    /**
     * Get original content before file modification
     * @param toolUseId Tool use ID
     * @returns Original content, or undefined if not found
     */
    getOriginalContent(toolUseId: string): string | undefined {
        return this.originalContents.get(toolUseId);
    }

    /**
     * Remove specified original content record
     * @param toolUseId Tool use ID
     */
    remove(toolUseId: string): void {
        this.originalContents.delete(toolUseId);
    }

    /**
     * Clear all cache (called when session ends)
     */
    clearAll(): void {
        this.originalContents.clear();
    }

    /**
     * Get current cache entry count
     */
    size(): number {
        return this.originalContents.size;
    }
}

// Export singleton instance for convenience
export const fileChangeLabelCache = FileChangeLabelCache.getInstance();

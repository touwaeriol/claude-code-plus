/**
 * Tool Use Context - Async context for passing toolUseId in MCP tool call chain
 * 
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/mcp/ToolUseContext.kt
 * 
 * Similar to ThreadLocal in Java or coroutine context in Kotlin,
 * this uses AsyncLocalStorage to make toolUseId accessible throughout the async call chain.
 * 
 * Mainly used for MCP tools to get current tool call ID for file history recording, etc.
 * 
 * Usage:
 * ```typescript
 * // When calling a tool, inject the context
 * await withToolUseContext(toolUseId, async () => {
 *     await tool.execute(arguments);
 * });
 * 
 * // Inside the tool, get toolUseId
 * const toolUseId = currentToolUseId();
 * ```
 */

import { AsyncLocalStorage } from 'async_hooks';

/**
 * Tool use context data
 */
export interface ToolUseContextData {
    toolUseId: string | null;
    /**
     * Additional context data that can be extended
     */
    extras?: Record<string, unknown>;
}

/**
 * AsyncLocalStorage instance for tool use context
 */
const toolUseContextStorage = new AsyncLocalStorage<ToolUseContextData>();

/**
 * Run a function with tool use context
 * 
 * @param toolUseId Tool use ID to inject into context
 * @param fn Function to run with the context
 * @returns Result of the function
 */
export function withToolUseContext<T>(
    toolUseId: string | null | undefined,
    fn: () => T
): T {
    return toolUseContextStorage.run(
        { toolUseId: toolUseId ?? null },
        fn
    );
}

/**
 * Run an async function with tool use context
 * 
 * @param toolUseId Tool use ID to inject into context
 * @param fn Async function to run with the context
 * @returns Promise of the function result
 */
export async function withToolUseContextAsync<T>(
    toolUseId: string | null | undefined,
    fn: () => Promise<T>
): Promise<T> {
    return toolUseContextStorage.run(
        { toolUseId: toolUseId ?? null },
        fn
    );
}

/**
 * Run a function with extended tool use context
 * 
 * @param context Full context data to inject
 * @param fn Function to run with the context
 * @returns Result of the function
 */
export function withToolUseContextFull<T>(
    context: ToolUseContextData,
    fn: () => T
): T {
    return toolUseContextStorage.run(context, fn);
}

/**
 * Get the current tool use ID from context
 * 
 * @returns Current tool use ID, or null if not in a tool context
 */
export function currentToolUseId(): string | null {
    const store = toolUseContextStorage.getStore();
    return store?.toolUseId ?? null;
}

/**
 * Get the current tool use context
 * 
 * @returns Current tool use context data, or undefined if not in a tool context
 */
export function currentToolUseContext(): ToolUseContextData | undefined {
    return toolUseContextStorage.getStore();
}

/**
 * Check if currently in a tool use context
 * 
 * @returns true if in a tool use context
 */
export function isInToolUseContext(): boolean {
    return toolUseContextStorage.getStore() !== undefined;
}

/**
 * Get extra data from the current tool use context
 * 
 * @param key Key of the extra data
 * @returns Value of the extra data, or undefined if not found
 */
export function getToolUseContextExtra<T>(key: string): T | undefined {
    const store = toolUseContextStorage.getStore();
    return store?.extras?.[key] as T | undefined;
}

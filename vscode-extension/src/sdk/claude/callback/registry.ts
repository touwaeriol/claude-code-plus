/**
 * Tool Callback Registry - Manages all custom tool callbacks
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/callback/ToolCallbackRegistry.kt
 *
 * Thread-safe implementation supporting concurrent registration and queries.
 *
 * @example
 * ```typescript
 * const registry = new ToolCallbackRegistry();
 *
 * // Register callback
 * registry.register(new AskUserQuestionCallback());
 *
 * // Query callback
 * const callback = registry.get('AskUserQuestion');
 * if (callback) {
 *   const result = await callback.execute(toolId, input);
 * }
 * ```
 */

import type { ToolCallback, ToolCallbackResult } from './toolCallback';
import type { JsonValue } from '../types/common';

/**
 * Logger interface for registry operations.
 */
export interface RegistryLogger {
  info(message: string): void;
  warn(message: string): void;
  debug(message: string): void;
}

/**
 * Default console logger implementation.
 */
const defaultLogger: RegistryLogger = {
  info: (msg) => console.log(`[ToolCallbackRegistry] ${msg}`),
  warn: (msg) => console.warn(`[ToolCallbackRegistry] ${msg}`),
  debug: (msg) => console.debug(`[ToolCallbackRegistry] ${msg}`),
};

/**
 * Tool callback registry - manages all custom tool callbacks.
 *
 * This registry allows tools to be intercepted and handled by custom callbacks
 * instead of the default CLI behavior.
 */
export class ToolCallbackRegistry {
  private callbacks: Map<string, ToolCallback> = new Map();
  private logger: RegistryLogger;

  constructor(logger?: RegistryLogger) {
    this.logger = logger ?? defaultLogger;
  }

  /**
   * Register a tool callback.
   *
   * @param callback - Tool callback implementation
   * @throws Warning if the tool name is already registered (will be overwritten)
   */
  register(callback: ToolCallback): void {
    const existing = this.callbacks.get(callback.toolName);
    if (existing) {
      this.logger.warn(`Tool '${callback.toolName}' already registered, overwriting old callback`);
    } else {
      this.logger.info(`Registered tool callback: ${callback.toolName}`);
    }
    this.callbacks.set(callback.toolName, callback);
  }

  /**
   * Register multiple tool callbacks at once.
   *
   * @param callbacks - Array of tool callbacks
   */
  registerAll(callbacks: ToolCallback[]): void {
    for (const callback of callbacks) {
      this.register(callback);
    }
  }

  /**
   * Get a tool callback by name.
   *
   * @param toolName - Tool name
   * @returns Tool callback, or undefined if not registered
   */
  get(toolName: string): ToolCallback | undefined {
    return this.callbacks.get(toolName);
  }

  /**
   * Check if a callback is registered for a tool.
   *
   * @param toolName - Tool name
   * @returns Whether a callback is registered
   */
  hasCallback(toolName: string): boolean {
    return this.callbacks.has(toolName);
  }

  /**
   * Remove a tool callback.
   *
   * @param toolName - Tool name
   * @returns The removed callback, or undefined if not found
   */
  unregister(toolName: string): ToolCallback | undefined {
    const removed = this.callbacks.get(toolName);
    if (removed) {
      this.callbacks.delete(toolName);
      this.logger.info(`Removed tool callback: ${toolName}`);
    }
    return removed;
  }

  /**
   * Get all registered tool names.
   */
  getRegisteredToolNames(): Set<string> {
    return new Set(this.callbacks.keys());
  }

  /**
   * Get the count of registered callbacks.
   */
  get size(): number {
    return this.callbacks.size;
  }

  /**
   * Clear all callbacks.
   */
  clear(): void {
    this.callbacks.clear();
    this.logger.info('Cleared all tool callbacks');
  }

  /**
   * Execute a tool callback if registered.
   *
   * @param toolName - Tool name
   * @param toolId - Tool use ID
   * @param input - Tool input parameters
   * @returns Callback result, or undefined if no callback is registered
   */
  async executeIfRegistered(
    toolName: string,
    toolId: string,
    input: JsonValue
  ): Promise<ToolCallbackResult | undefined> {
    const callback = this.get(toolName);
    if (!callback) {
      return undefined;
    }

    this.logger.debug(`Executing callback for tool: ${toolName}`);
    try {
      return await callback.execute(toolId, input);
    } catch (error) {
      this.logger.warn(`Callback execution failed for ${toolName}: ${error}`);
      return {
        content: `Callback error: ${error instanceof Error ? error.message : String(error)}`,
        isError: true,
      };
    }
  }

  /**
   * Create a copy of this registry with all callbacks.
   */
  clone(): ToolCallbackRegistry {
    const copy = new ToolCallbackRegistry(this.logger);
    this.callbacks.forEach((callback, name) => {
      copy.callbacks.set(name, callback);
    });
    return copy;
  }

  /**
   * Iterate over all registered callbacks.
   */
  *[Symbol.iterator](): IterableIterator<[string, ToolCallback]> {
    for (const entry of Array.from(this.callbacks.entries())) {
      yield entry;
    }
  }

  /**
   * Get all callbacks as an array.
   */
  toArray(): ToolCallback[] {
    return Array.from(this.callbacks.values());
  }
}

/**
 * Global default registry instance.
 * Use this for simple cases where a single registry is sufficient.
 */
let globalRegistry: ToolCallbackRegistry | null = null;

/**
 * Get the global registry instance.
 * Creates one if it doesn't exist.
 */
export function getGlobalRegistry(): ToolCallbackRegistry {
  if (!globalRegistry) {
    globalRegistry = new ToolCallbackRegistry();
  }
  return globalRegistry;
}

/**
 * Reset the global registry (mainly for testing).
 */
export function resetGlobalRegistry(): void {
  globalRegistry = null;
}

/**
 * Tool Callback Interface - For custom tool processing logic
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/callback/ToolCallback.kt
 *
 * When Claude CLI invokes a tool, if that tool has a registered callback,
 * the SDK will invoke the callback instead of letting CLI auto-execute.
 *
 * Use cases:
 * - AskUserQuestion: Requires frontend interaction, waiting for user selection
 * - ExitPlanMode: Requires user to confirm the plan
 * - Other tools that need custom handling
 *
 * @example
 * ```typescript
 * class AskUserQuestionCallback implements ToolCallback {
 *   readonly toolName = 'AskUserQuestion';
 *
 *   async execute(toolId: string, input: JsonValue): Promise<ToolCallbackResult> {
 *     // Call frontend to display question, wait for user selection
 *     const answers = await askFrontend(input);
 *     return {
 *       content: `User answered: ${answers}`,
 *       isError: false
 *     };
 *   }
 * }
 * ```
 */

import type { JsonValue } from '../types/common';

/**
 * Tool callback interface - base interface for all tool callbacks.
 */
export interface ToolCallback {
  /**
   * Tool name, must exactly match the tool name in Claude CLI.
   * e.g., "AskUserQuestion", "ExitPlanMode", "Read", "Write"
   */
  readonly toolName: string;

  /**
   * Execute the tool callback.
   *
   * @param toolId - Tool use ID (e.g., "toolu_xxx"), used to correlate tool_result
   * @param input - Tool input parameters (JSON format)
   * @returns Tool execution result
   */
  execute(toolId: string, input: JsonValue): Promise<ToolCallbackResult>;
}

/**
 * Tool callback execution result.
 */
export interface ToolCallbackResult {
  /**
   * Content returned to Claude, will be used as tool_result's content.
   */
  content: string;

  /**
   * Whether this is an error result.
   */
  isError?: boolean;
}

/**
 * Helper function to create a success result.
 */
export function successResult(content: string): ToolCallbackResult {
  return { content, isError: false };
}

/**
 * Helper function to create an error result.
 */
export function errorResult(content: string): ToolCallbackResult {
  return { content, isError: true };
}

/**
 * Abstract base class for tool callbacks with common utilities.
 */
export abstract class BaseToolCallback implements ToolCallback {
  abstract readonly toolName: string;
  abstract execute(toolId: string, input: JsonValue): Promise<ToolCallbackResult>;

  /**
   * Helper to create a success result.
   */
  protected success(content: string): ToolCallbackResult {
    return successResult(content);
  }

  /**
   * Helper to create an error result.
   */
  protected error(content: string): ToolCallbackResult {
    return errorResult(content);
  }
}

/**
 * Function-based tool callback implementation.
 * Allows creating callbacks using simple functions.
 */
export class FunctionToolCallback implements ToolCallback {
  readonly toolName: string;
  private readonly handler: (toolId: string, input: JsonValue) => Promise<ToolCallbackResult>;

  constructor(
    toolName: string,
    handler: (toolId: string, input: JsonValue) => Promise<ToolCallbackResult>
  ) {
    this.toolName = toolName;
    this.handler = handler;
  }

  execute(toolId: string, input: JsonValue): Promise<ToolCallbackResult> {
    return this.handler(toolId, input);
  }
}

/**
 * Factory function to create a tool callback from a function.
 *
 * @example
 * ```typescript
 * const callback = createToolCallback('MyTool', async (toolId, input) => {
 *   // Process the tool call
 *   return { content: 'Done!', isError: false };
 * });
 * ```
 */
export function createToolCallback(
  toolName: string,
  handler: (toolId: string, input: JsonValue) => Promise<ToolCallbackResult>
): ToolCallback {
  return new FunctionToolCallback(toolName, handler);
}

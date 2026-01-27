/**
 * Hook Builder - Provides convenient Hook definition DSL
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/builders/HookBuilder.kt
 *
 * @example
 * ```typescript
 * const hooks = hookBuilder()
 *   // Security Hook
 *   .onPreToolUse('Bash', (toolCall) => {
 *     const command = toolCall.getStringParam('command');
 *     if (command.includes('rm -rf')) {
 *       return toolCall.block('Dangerous command blocked: ' + command);
 *     }
 *     return toolCall.allow('Security check passed');
 *   })
 *   // Statistics Hook
 *   .onPreToolUse('.*', (toolCall) => {
 *     console.log('Tool called:', toolCall.toolName);
 *     return toolCall.allow('Statistics recorded');
 *   })
 *   .build();
 * ```
 */

import type { JsonValue, JsonObject } from '../types/common';
import type { HookEvent, HookMatcher, HookCallback, HookJSONOutput, HookContext } from '../types/hooks';

/**
 * Convenient tool call information wrapper.
 */
export class ToolCall {
  readonly toolName: string;
  readonly toolUseId: string | undefined;
  readonly input: JsonObject;
  readonly context: HookContext;

  constructor(
    toolName: string,
    toolUseId: string | undefined,
    input: JsonObject,
    context: HookContext
  ) {
    this.toolName = toolName;
    this.toolUseId = toolUseId;
    this.input = input;
    this.context = context;
  }

  /**
   * Get a string parameter from input.
   */
  getStringParam(name: string): string {
    const value = this.input[name];
    return typeof value === 'string' ? value : '';
  }

  /**
   * Get a number parameter from input.
   */
  getNumberParam(name: string): number {
    const value = this.input[name];
    return typeof value === 'number' ? value : 0;
  }

  /**
   * Get a boolean parameter from input.
   */
  getBooleanParam(name: string): boolean {
    const value = this.input[name];
    return typeof value === 'boolean' ? value : false;
  }

  /**
   * Get an object parameter from input.
   */
  getMapParam(name: string): JsonObject {
    const value = this.input[name];
    return typeof value === 'object' && value !== null && !Array.isArray(value)
      ? (value as JsonObject)
      : {};
  }

  /**
   * Get an array parameter from input.
   */
  getArrayParam(name: string): JsonValue[] {
    const value = this.input[name];
    return Array.isArray(value) ? value : [];
  }

  /**
   * Create an allow result.
   */
  allow(message: string = ''): HookJSONOutput {
    return { systemMessage: message };
  }

  /**
   * Create a block result.
   */
  block(message: string, output: JsonValue = 'blocked'): HookJSONOutput {
    return {
      decision: 'block',
      systemMessage: message,
      hookSpecificOutput: output,
    };
  }

  /**
   * Create an interrupt result.
   */
  interrupt(message: string): HookJSONOutput {
    return {
      decision: 'block',
      systemMessage: message,
      hookSpecificOutput: 'interrupted',
    };
  }

  toString(): string {
    return `ToolCall(name='${this.toolName}', params=${JSON.stringify(this.input)})`;
  }
}

/**
 * Hook handler function type.
 */
export type HookHandler = (toolCall: ToolCall) => HookJSONOutput | Promise<HookJSONOutput>;

/**
 * Hook builder for fluent configuration.
 */
export class HookBuilder {
  private hooks: Map<HookEvent, HookMatcher[]> = new Map();

  /**
   * Add a PRE_TOOL_USE hook.
   */
  onPreToolUse(matcher: string, handler: HookHandler): this {
    return this.addHook('PRE_TOOL_USE', matcher, handler);
  }

  /**
   * Add a POST_TOOL_USE hook.
   */
  onPostToolUse(matcher: string, handler: HookHandler): this {
    return this.addHook('POST_TOOL_USE', matcher, handler);
  }

  /**
   * Add a USER_PROMPT_SUBMIT hook.
   */
  onUserPromptSubmit(handler: HookHandler): this {
    return this.addHook('USER_PROMPT_SUBMIT', undefined, handler);
  }

  /**
   * Add a STOP hook.
   */
  onStop(handler: HookHandler): this {
    return this.addHook('STOP', undefined, handler);
  }

  /**
   * Add a SUBAGENT_STOP hook.
   */
  onSubagentStop(handler: HookHandler): this {
    return this.addHook('SUBAGENT_STOP', undefined, handler);
  }

  /**
   * Add a PRE_COMPACT hook.
   */
  onPreCompact(handler: HookHandler): this {
    return this.addHook('PRE_COMPACT', undefined, handler);
  }

  /**
   * Add a hook for a specific event.
   */
  private addHook(event: HookEvent, matcher: string | undefined, handler: HookHandler): this {
    const hookCallback: HookCallback = async (input, toolUseId, context) => {
      const toolName =
        typeof input.tool_name === 'string' ? input.tool_name : '';
      const toolInput =
        typeof input.tool_input === 'object' && input.tool_input !== null && !Array.isArray(input.tool_input)
          ? (input.tool_input as JsonObject)
          : {};

      const toolCall = new ToolCall(toolName, toolUseId, toolInput, context);
      const result = await handler(toolCall);
      return result;
    };

    const hookMatcher: HookMatcher = {
      matcher,
      hooks: [hookCallback],
    };

    const existingMatchers = this.hooks.get(event) ?? [];
    this.hooks.set(event, [...existingMatchers, hookMatcher]);

    return this;
  }

  /**
   * Build the hooks configuration.
   */
  build(): Partial<Record<HookEvent, HookMatcher[]>> {
    const result: Partial<Record<HookEvent, HookMatcher[]>> = {};
    this.hooks.forEach((matchers, event) => {
      result[event] = matchers;
    });
    return result;
  }
}

/**
 * Create a new hook builder.
 *
 * @example
 * ```typescript
 * const hooks = hookBuilder()
 *   .onPreToolUse('Bash', (tc) => tc.allow('OK'))
 *   .build();
 * ```
 */
export function hookBuilder(): HookBuilder {
  return new HookBuilder();
}

/**
 * Quick security hook builder.
 *
 * @example
 * ```typescript
 * const hooks = securityHook(['rm -rf', 'sudo'], ['npm install']);
 * ```
 */
export function securityHook(
  dangerousPatterns: string[] = ['rm -rf', 'sudo', 'format', 'delete'],
  allowedCommands: string[] = []
): Partial<Record<HookEvent, HookMatcher[]>> {
  return hookBuilder()
    .onPreToolUse('Bash', (toolCall) => {
      const command = toolCall.getStringParam('command');

      // Check allowed list
      const isAllowed = allowedCommands.some((allowed) =>
        command.toLowerCase().includes(allowed.toLowerCase())
      );
      if (isAllowed) {
        return toolCall.allow('Command is in allowed list');
      }

      // Check dangerous patterns
      for (const pattern of dangerousPatterns) {
        if (command.toLowerCase().includes(pattern.toLowerCase())) {
          return toolCall.block(`Security policy blocked dangerous command: ${pattern}`);
        }
      }

      return toolCall.allow('Security check passed');
    })
    .build();
}

/**
 * Quick statistics hook builder.
 *
 * @example
 * ```typescript
 * const hooks = statisticsHook();
 * ```
 */
export function statisticsHook(): Partial<Record<HookEvent, HookMatcher[]>> {
  let callCount = 0;
  const toolStats: Map<string, number> = new Map();

  return hookBuilder()
    .onPreToolUse('.*', (toolCall) => {
      callCount++;
      const currentCount = toolStats.get(toolCall.toolName) ?? 0;
      toolStats.set(toolCall.toolName, currentCount + 1);

      console.log(`[Statistics] Tool call #${callCount}: ${toolCall.toolName}`);
      console.debug(`[Statistics] Tool usage stats:`, Object.fromEntries(toolStats));

      return toolCall.allow(
        `Statistics: Total ${callCount}, ${toolCall.toolName} #${toolStats.get(toolCall.toolName)}`
      );
    })
    .build();
}

/**
 * Create a simple logging hook.
 */
export function loggingHook(
  logger: (message: string) => void = console.log
): Partial<Record<HookEvent, HookMatcher[]>> {
  return hookBuilder()
    .onPreToolUse('.*', (toolCall) => {
      logger(`[PRE] Tool: ${toolCall.toolName}, Input: ${JSON.stringify(toolCall.input)}`);
      return toolCall.allow();
    })
    .onPostToolUse('.*', (toolCall) => {
      logger(`[POST] Tool: ${toolCall.toolName}`);
      return toolCall.allow();
    })
    .build();
}

/**
 * Create a rate limiting hook.
 */
export function rateLimitHook(
  maxCallsPerMinute: number = 60
): Partial<Record<HookEvent, HookMatcher[]>> {
  const callTimestamps: number[] = [];

  return hookBuilder()
    .onPreToolUse('.*', (toolCall) => {
      const now = Date.now();
      const oneMinuteAgo = now - 60000;

      // Remove old timestamps
      while (callTimestamps.length > 0 && callTimestamps[0] < oneMinuteAgo) {
        callTimestamps.shift();
      }

      if (callTimestamps.length >= maxCallsPerMinute) {
        return toolCall.block(
          `Rate limit exceeded: ${callTimestamps.length}/${maxCallsPerMinute} calls in the last minute`
        );
      }

      callTimestamps.push(now);
      return toolCall.allow();
    })
    .build();
}

/**
 * Create a tool filtering hook that blocks specific tools.
 */
export function toolFilterHook(blockedTools: string[]): Partial<Record<HookEvent, HookMatcher[]>> {
  return hookBuilder()
    .onPreToolUse('.*', (toolCall) => {
      if (blockedTools.includes(toolCall.toolName)) {
        return toolCall.block(`Tool '${toolCall.toolName}' is blocked by policy`);
      }
      return toolCall.allow();
    })
    .build();
}

/**
 * Merge multiple hook configurations.
 */
export function mergeHooks(
  ...hookConfigs: Partial<Record<HookEvent, HookMatcher[]>>[]
): Partial<Record<HookEvent, HookMatcher[]>> {
  const result: Partial<Record<HookEvent, HookMatcher[]>> = {};

  for (const config of hookConfigs) {
    for (const [event, matchers] of Object.entries(config)) {
      const hookEvent = event as HookEvent;
      const existingMatchers = result[hookEvent] ?? [];
      result[hookEvent] = [...existingMatchers, ...(matchers ?? [])];
    }
  }

  return result;
}

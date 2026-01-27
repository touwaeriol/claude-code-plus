/**
 * Hook types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Hooks.kt
 */

import type { JsonValue, JsonObject } from './common';

/**
 * Supported hook event types.
 */
export type HookEvent =
  | 'PRE_TOOL_USE'
  | 'POST_TOOL_USE'
  | 'USER_PROMPT_SUBMIT'
  | 'STOP'
  | 'SUBAGENT_STOP'
  | 'PRE_COMPACT';

/**
 * Hook event enum values.
 */
export const HookEvents = {
  PRE_TOOL_USE: 'PRE_TOOL_USE',
  POST_TOOL_USE: 'POST_TOOL_USE',
  USER_PROMPT_SUBMIT: 'USER_PROMPT_SUBMIT',
  STOP: 'STOP',
  SUBAGENT_STOP: 'SUBAGENT_STOP',
  PRE_COMPACT: 'PRE_COMPACT',
} as const;

/**
 * Hook JSON output format.
 */
export interface HookJSONOutput {
  /** "block" to block the action */
  decision?: string;
  /** System message not visible to Claude */
  systemMessage?: string;
  /** Hook-specific output */
  hookSpecificOutput?: JsonValue;
}

/**
 * Context information for hook callbacks.
 */
export interface HookContext {
  /** Future: abort signal support */
  signal?: unknown;
}

/**
 * Hook callback function type.
 */
export type HookCallback = (
  input: JsonObject,
  toolUseId: string | undefined,
  context: HookContext
) => Promise<HookJSONOutput>;

/**
 * Hook matcher configuration.
 */
export interface HookMatcher {
  /** Matcher pattern (e.g., "Bash" or "Write|Edit") */
  matcher?: string;
  /** List of callback functions */
  hooks?: HookCallback[];
}

/**
 * Hook execution result - discriminated union.
 */
export type HookResult = HookResultAllow | HookResultBlock | HookResultModify;

/**
 * Allow hook result.
 */
export interface HookResultAllow {
  type: 'allow';
  modifiedInput?: JsonValue;
  systemMessage?: string;
}

/**
 * Block hook result.
 */
export interface HookResultBlock {
  type: 'block';
  reason: string;
  systemMessage?: string;
}

/**
 * Modify hook result.
 */
export interface HookResultModify {
  type: 'modify';
  modifiedInput: JsonValue;
  systemMessage?: string;
}

/**
 * Helper functions to create hook results.
 */
export const HookResult = {
  allow(modifiedInput?: JsonValue, systemMessage?: string): HookResultAllow {
    return { type: 'allow', modifiedInput, systemMessage };
  },
  block(reason: string, systemMessage?: string): HookResultBlock {
    return { type: 'block', reason, systemMessage };
  },
  modify(modifiedInput: JsonValue, systemMessage?: string): HookResultModify {
    return { type: 'modify', modifiedInput, systemMessage };
  },
};

/**
 * Hook registry for managing hook callbacks.
 */
export class HookRegistry {
  private hooks: Map<HookEvent, HookMatcher[]> = new Map();

  register(event: HookEvent, matcher: HookMatcher): void {
    const list = this.hooks.get(event) ?? [];
    list.push(matcher);
    this.hooks.set(event, list);
  }

  unregister(event: HookEvent, matcher: HookMatcher): void {
    const list = this.hooks.get(event);
    if (list) {
      const index = list.indexOf(matcher);
      if (index !== -1) {
        list.splice(index, 1);
      }
    }
  }

  getHooks(event: HookEvent): HookMatcher[] {
    return this.hooks.get(event) ?? [];
  }

  clear(): void {
    this.hooks.clear();
  }
}

/**
 * Hook execution environment providing context and utilities.
 */
export interface HookExecutionEnvironment {
  sessionId?: string;
  toolName?: string;
  input: JsonObject;
  timestamp?: number;
  metadata?: JsonObject;
}

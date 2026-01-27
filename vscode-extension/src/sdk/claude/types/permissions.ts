/**
 * Permission types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Permissions.kt
 */

import type { JsonValue, JsonObject } from './common';

/**
 * Permission modes for tool usage.
 */
export type PermissionMode = 'default' | 'acceptEdits' | 'plan' | 'bypassPermissions';

/**
 * Permission mode enum values.
 */
export const PermissionModes = {
  DEFAULT: 'default',
  ACCEPT_EDITS: 'acceptEdits',
  PLAN: 'plan',
  BYPASS_PERMISSIONS: 'bypassPermissions',
} as const;

/**
 * Permission behavior types.
 */
export type PermissionBehavior = 'allow' | 'deny' | 'ask';

/**
 * Permission behavior enum values.
 */
export const PermissionBehaviors = {
  ALLOW: 'allow',
  DENY: 'deny',
  ASK: 'ask',
} as const;

/**
 * Permission update destination.
 */
export type PermissionUpdateDestination =
  | 'userSettings'
  | 'projectSettings'
  | 'localSettings'
  | 'session';

/**
 * Permission update destination enum values.
 */
export const PermissionUpdateDestinations = {
  USER_SETTINGS: 'userSettings',
  PROJECT_SETTINGS: 'projectSettings',
  LOCAL_SETTINGS: 'localSettings',
  SESSION: 'session',
} as const;

/**
 * Permission rule value.
 */
export interface PermissionRuleValue {
  toolName: string;
  ruleContent?: string;
}

/**
 * Permission update types.
 */
export type PermissionUpdateType =
  | 'addRules'
  | 'replaceRules'
  | 'removeRules'
  | 'setMode'
  | 'addDirectories'
  | 'removeDirectories';

/**
 * Permission update type enum values.
 */
export const PermissionUpdateTypes = {
  ADD_RULES: 'addRules',
  REPLACE_RULES: 'replaceRules',
  REMOVE_RULES: 'removeRules',
  SET_MODE: 'setMode',
  ADD_DIRECTORIES: 'addDirectories',
  REMOVE_DIRECTORIES: 'removeDirectories',
} as const;

/**
 * Permission update configuration.
 */
export interface PermissionUpdate {
  type: PermissionUpdateType;
  rules?: PermissionRuleValue[];
  behavior?: PermissionBehavior;
  mode?: PermissionMode;
  directories?: string[];
  destination?: PermissionUpdateDestination;
}

/**
 * Context information for tool permission callbacks.
 */
export interface ToolPermissionContext {
  /** Future: abort signal support */
  signal?: unknown;
  /** Permission suggestions from CLI */
  suggestions?: PermissionUpdate[];
}

/**
 * Union type for permission results.
 */
export type PermissionResult = PermissionResultAllow | PermissionResultDeny;

/**
 * Allow permission result.
 */
export interface PermissionResultAllow {
  behavior: 'allow';
  updatedInput?: Record<string, JsonValue>;
  updatedPermissions?: PermissionUpdate[];
}

/**
 * Deny permission result.
 */
export interface PermissionResultDeny {
  behavior: 'deny';
  message?: string;
  interrupt?: boolean;
}

/**
 * Helper functions to create permission results.
 */
export const PermissionResult = {
  allow(
    updatedInput?: Record<string, JsonValue>,
    updatedPermissions?: PermissionUpdate[]
  ): PermissionResultAllow {
    return { behavior: 'allow', updatedInput, updatedPermissions };
  },
  deny(message?: string, interrupt?: boolean): PermissionResultDeny {
    return { behavior: 'deny', message, interrupt };
  },
};

/**
 * Tool permission callback function type.
 * @param toolName Tool name
 * @param input Tool input parameters (JSON object)
 * @param toolUseId Tool call ID (for precise UI association)
 * @param context Permission context
 */
export type CanUseTool = (
  toolName: string,
  input: Record<string, JsonValue>,
  toolUseId: string | undefined,
  context: ToolPermissionContext
) => Promise<PermissionResult>;

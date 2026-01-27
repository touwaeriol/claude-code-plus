/**
 * Error types for Claude Agent SDK
 * Translated from Kotlin: claude-agent-sdk/src/main/kotlin/com/asakii/claude/agent/sdk/types/Errors.kt
 */

import type { JsonObject } from './common';

/**
 * Base error class for all Claude SDK errors.
 */
export class ClaudeSDKError extends Error {
  constructor(
    message: string,
    public readonly cause?: Error
  ) {
    super(message);
    this.name = 'ClaudeSDKError';
    // Maintains proper stack trace for where our error was thrown (only available on V8)
    if (Error.captureStackTrace) {
      Error.captureStackTrace(this, this.constructor);
    }
  }
}

/**
 * Thrown when Claude Code CLI is not found or not installed.
 */
export class CLINotFoundError extends ClaudeSDKError {
  constructor(
    message: string = 'Claude Code CLI not found. Please install Claude Code CLI.',
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'CLINotFoundError';
  }
}

/**
 * Thrown when there's a connection error with Claude Code CLI.
 */
export class CLIConnectionError extends ClaudeSDKError {
  constructor(message: string, cause?: Error) {
    super(message, cause);
    this.name = 'CLIConnectionError';
  }
}

/**
 * Thrown when the CLI process fails or exits with an error.
 */
export class ProcessError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly exitCode?: number,
    public readonly stderr?: string,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'ProcessError';
  }
}

/**
 * Thrown when JSON decoding from CLI output fails.
 */
export class CLIJSONDecodeError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly rawOutput?: string,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'CLIJSONDecodeError';
  }
}

/**
 * Thrown when there's an error with MCP server communication.
 */
export class MCPServerError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly serverName?: string,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'MCPServerError';
  }
}

/**
 * Thrown when tool execution fails.
 */
export class ToolExecutionError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly toolName?: string,
    public readonly toolInput?: JsonObject,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'ToolExecutionError';
  }
}

/**
 * Thrown when permission is denied for a tool operation.
 */
export class PermissionDeniedError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly toolName?: string,
    public readonly reason?: string,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'PermissionDeniedError';
  }
}

/**
 * Thrown when session management operations fail.
 */
export class SessionError extends ClaudeSDKError {
  constructor(
    message: string,
    public readonly sessionId?: string,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'SessionError';
  }
}

/**
 * Thrown when conversation timeout occurs.
 */
export class ConversationTimeoutError extends ClaudeSDKError {
  constructor(
    message: string = 'Conversation timed out',
    public readonly timeoutMs?: number,
    cause?: Error
  ) {
    super(message, cause);
    this.name = 'ConversationTimeoutError';
  }
}

/**
 * Type guard to check if an error is a ClaudeSDKError.
 */
export function isClaudeSDKError(error: unknown): error is ClaudeSDKError {
  return error instanceof ClaudeSDKError;
}

/**
 * Type guard to check if an error is a specific Claude SDK error type.
 */
export function isCLINotFoundError(error: unknown): error is CLINotFoundError {
  return error instanceof CLINotFoundError;
}

export function isCLIConnectionError(error: unknown): error is CLIConnectionError {
  return error instanceof CLIConnectionError;
}

export function isProcessError(error: unknown): error is ProcessError {
  return error instanceof ProcessError;
}

export function isCLIJSONDecodeError(error: unknown): error is CLIJSONDecodeError {
  return error instanceof CLIJSONDecodeError;
}

export function isMCPServerError(error: unknown): error is MCPServerError {
  return error instanceof MCPServerError;
}

export function isToolExecutionError(error: unknown): error is ToolExecutionError {
  return error instanceof ToolExecutionError;
}

export function isPermissionDeniedError(error: unknown): error is PermissionDeniedError {
  return error instanceof PermissionDeniedError;
}

export function isSessionError(error: unknown): error is SessionError {
  return error instanceof SessionError;
}

export function isConversationTimeoutError(error: unknown): error is ConversationTimeoutError {
  return error instanceof ConversationTimeoutError;
}

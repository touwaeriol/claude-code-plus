package com.claudecodeplus.sdk.types

/**
 * Base exception for all Claude SDK errors.
 */
abstract class ClaudeSDKError(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)

/**
 * Thrown when Claude Code CLI is not found or not installed.
 */
class CLINotFoundError(
    message: String = "Claude Code CLI not found. Please install Claude Code CLI.",
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when there's a connection error with Claude Code CLI.
 */
class CLIConnectionError(
    message: String,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when the CLI process fails or exits with an error.
 */
class ProcessError(
    message: String,
    val exitCode: Int? = null,
    val stderr: String? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when JSON decoding from CLI output fails.
 */
class CLIJSONDecodeError(
    message: String,
    val rawOutput: String? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when there's an error with MCP server communication.
 */
class MCPServerError(
    message: String,
    val serverName: String? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when tool execution fails.
 */
class ToolExecutionError(
    message: String,
    val toolName: String? = null,
    val toolInput: Map<String, Any>? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when permission is denied for a tool operation.
 */
class PermissionDeniedError(
    message: String,
    val toolName: String? = null,
    val reason: String? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when session management operations fail.
 */
class SessionError(
    message: String,
    val sessionId: String? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)

/**
 * Thrown when conversation timeout occurs.
 */
class ConversationTimeoutError(
    message: String = "Conversation timed out",
    val timeoutMs: Long? = null,
    cause: Throwable? = null
) : ClaudeSDKError(message, cause)
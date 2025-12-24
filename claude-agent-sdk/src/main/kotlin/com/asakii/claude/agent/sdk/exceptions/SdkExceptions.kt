package com.asakii.claude.agent.sdk.exceptions

/**
 * Base exception for all Claude Code SDK errors.
 * Maps to Python SDK's ClaudeSDKError.
 */
sealed class ClaudeCodeSdkException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Exception thrown when CLI connection fails.
 * Maps to Python SDK's CLIConnectionError.
 */
open class CLIConnectionException(
    message: String,
    cause: Throwable? = null
) : ClaudeCodeSdkException(message, cause)

/**
 * Exception thrown when Claude Code CLI is not found or not installed.
 * Maps to Python SDK's CLINotFoundError.
 */
class CLINotFoundException(
    message: String = "Claude Code not found",
    val cliPath: String? = null,
    cause: Throwable? = null
) : CLIConnectionException(
    message = if (cliPath != null) "$message: $cliPath" else message,
    cause = cause
) {
    companion object {
        fun withInstallInstructions(nodeInstalled: Boolean = true): CLINotFoundException {
            val message = if (!nodeInstalled) {
                """
                Claude Code requires Node.js, which is not installed.
                
                Install Node.js from: https://nodejs.org/
                
                After installing Node.js, install Claude Code:
                  npm install -g @anthropic-ai/claude-code
                """.trimIndent()
            } else {
                """
                Claude Code not found. Install with:
                  npm install -g @anthropic-ai/claude-code
                
                If already installed locally, try:
                  export PATH="${"$"}HOME/node_modules/.bin:${"$"}PATH"
                
                Or specify the path when creating transport.
                """.trimIndent()
            }
            return CLINotFoundException(message)
        }
    }
}

/**
 * Exception thrown when the CLI process fails.
 * Maps to Python SDK's ProcessError.
 */
class ProcessException(
    message: String,
    val exitCode: Int? = null,
    val stderr: String? = null,
    cause: Throwable? = null
) : ClaudeCodeSdkException(
    message = buildMessage(message, exitCode, stderr),
    cause = cause
) {
    companion object {
        private fun buildMessage(message: String, exitCode: Int?, stderr: String?): String {
            var result = message
            if (exitCode != null) {
                result = "$result (exit code: $exitCode)"
            }
            if (!stderr.isNullOrBlank()) {
                result = "$result\nError output: $stderr"
            }
            return result
        }
    }
}

/**
 * Exception thrown when unable to decode JSON from CLI output.
 * Maps to Python SDK's CLIJSONDecodeError.
 */
class JSONDecodeException(
    message: String,
    val originalLine: String,
    cause: Throwable? = null
) : ClaudeCodeSdkException(
    message = "$message: ${originalLine.take(100)}${if (originalLine.length > 100) "..." else ""}",
    cause = cause
)

/**
 * Exception thrown when transport operations fail.
 */
class TransportException(
    message: String,
    cause: Throwable? = null
) : ClaudeCodeSdkException(message, cause)

/**
 * Exception thrown when message parsing fails.
 * Maps to Python SDK's MessageParseError.
 */
class MessageParsingException(
    message: String,
    val data: Map<String, Any>? = null,
    cause: Throwable? = null
) : ClaudeCodeSdkException(message, cause)

/**
 * Exception thrown when control protocol operations fail.
 */
class ControlProtocolException(
    message: String,
    cause: Throwable? = null
) : ClaudeCodeSdkException(message, cause)

/**
 * Exception thrown when client is not connected.
 */
class ClientNotConnectedException(
    message: String = "Client is not connected. Call connect() first."
) : ClaudeCodeSdkException(message)

/**
 * Exception thrown when an operation is interrupted.
 */
class InterruptedException(
    message: String = "Operation was interrupted"
) : ClaudeCodeSdkException(message)

/**
 * Exception thrown when Node.js is not found or the configured path is invalid.
 * This is a strict validation - if user configures a path, it must be valid.
 */
class NodeNotFoundException(
    message: String,
    val configuredPath: String? = null,
    cause: Throwable? = null
) : CLIConnectionException(message, cause) {
    companion object {
        /**
         * Create exception for invalid user-configured path
         */
        fun invalidConfiguredPath(path: String): NodeNotFoundException {
            return NodeNotFoundException(
                message = """
                    Node.js path is invalid: $path

                    The configured Node.js executable does not exist or is not executable.
                    Please check the path in Settings > Claude Code > Node.js path.
                """.trimIndent(),
                configuredPath = path
            )
        }

        /**
         * Create exception when Node.js cannot be auto-detected
         */
        fun notFound(): NodeNotFoundException {
            return NodeNotFoundException(
                message = """
                    Node.js not found.

                    Please either:
                    1. Install Node.js and ensure it's in your system PATH
                    2. Configure the Node.js path in Settings > Claude Code > Node.js path

                    Download Node.js from: https://nodejs.org/
                """.trimIndent()
            )
        }
    }
}
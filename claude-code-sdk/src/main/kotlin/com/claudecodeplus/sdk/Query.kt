package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.protocol.ControlProtocol
import com.claudecodeplus.sdk.transport.SubprocessTransport
import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Query Claude Agent for one-shot or unidirectional streaming interactions.
 *
 * This function is ideal for simple, stateless queries where you don't need
 * bidirectional communication or conversation management. For interactive,
 * stateful conversations, use ClaudeCodeSdkClient instead.
 *
 * Key differences from ClaudeCodeSdkClient:
 * - **Unidirectional**: Send all messages upfront, receive all responses
 * - **Stateless**: Each query is independent, no conversation state
 * - **Simple**: Fire-and-forget style, no connection management
 * - **No interrupts**: Cannot interrupt or send follow-up messages
 *
 * When to use query():
 * - Simple one-off questions ("What is 2+2?")
 * - Batch processing of independent prompts
 * - Code generation or analysis tasks
 * - Automated scripts and CI/CD pipelines
 * - When you know all inputs upfront
 *
 * When to use ClaudeCodeSdkClient:
 * - Interactive conversations with follow-ups
 * - Chat applications or REPL-like interfaces
 * - When you need to send messages based on responses
 * - When you need interrupt capabilities
 * - Long-running sessions with state
 *
 * @param prompt The prompt to send to Claude
 * @param options Optional configuration (defaults to ClaudeAgentOptions() if null)
 * @param transport Optional transport implementation for custom transport logic
 * @return Flow of Messages from the conversation
 *
 * Example - Simple query:
 * ```kotlin
 * query("What is the capital of France?").collect { message ->
 *     println(message)
 * }
 * ```
 *
 * Example - With options:
 * ```kotlin
 * query(
 *     prompt = "Create a Python web server",
 *     options = ClaudeAgentOptions(
 *         systemPrompt = "You are an expert Python developer",
 *         cwd = Path.of("/home/user/project"),
 *         allowedTools = listOf("Read", "Write", "Bash")
 *     )
 * ).collect { message ->
 *     when (message) {
 *         is AssistantMessage -> println("Claude: ${message.content}")
 *         is ResultMessage -> println("Done! Cost: $${message.totalCostUsd}")
 *     }
 * }
 * ```
 *
 * Example - With system prompt preset:
 * ```kotlin
 * query(
 *     prompt = "Help me refactor this code",
 *     options = ClaudeAgentOptions(
 *         systemPrompt = SystemPromptPreset(preset = "claude_code"),
 *         permissionMode = PermissionMode.ACCEPT_EDITS
 *     )
 * ).collect { message ->
 *     println(message)
 * }
 * ```
 */
suspend fun query(
    prompt: String,
    options: ClaudeAgentOptions? = null,
    transport: Transport? = null
): Flow<Message> = flow {
    val actualOptions = options ?: ClaudeAgentOptions()

    // Set SDK entrypoint environment variable
    System.setProperty("CLAUDE_CODE_ENTRYPOINT", "sdk-kt")

    // Use provided transport or create subprocess transport
    val actualTransport = transport ?: SubprocessTransport(actualOptions, streamingMode = true)

    try {
        // Create control protocol
        val controlProtocol = ControlProtocol(actualTransport, actualOptions)

        // Connect transport
        actualTransport.connect()

        // Start message processing (using a temporary scope)
        kotlinx.coroutines.coroutineScope {
            controlProtocol.startMessageProcessing(this)

            // Send the prompt
            val messageJson = kotlinx.serialization.json.buildJsonObject {
                put("type", kotlinx.serialization.json.JsonPrimitive("user"))
                put("message", kotlinx.serialization.json.buildJsonObject {
                    put("role", kotlinx.serialization.json.JsonPrimitive("user"))
                    put("content", kotlinx.serialization.json.JsonPrimitive(prompt))
                })
                put("parent_tool_use_id", kotlinx.serialization.json.JsonNull)
                put("session_id", kotlinx.serialization.json.JsonPrimitive("default"))
            }

            actualTransport.write(messageJson.toString())

            // Collect and emit all messages
            controlProtocol.sdkMessages.collect { message ->
                emit(message)

                // Stop after ResultMessage
                if (message is ResultMessage) {
                    return@collect
                }
            }
        }
    } finally {
        // Cleanup
        try {
            actualTransport.close()
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}

/**
 * Backward compatibility alias.
 * @deprecated Use query() instead
 */
@Deprecated(
    message = "Use query() instead",
    replaceWith = ReplaceWith("query(prompt, options, transport)")
)
suspend fun simpleQuery(
    prompt: String,
    options: ClaudeAgentOptions? = null,
    transport: Transport? = null
): Flow<Message> = query(prompt, options, transport)
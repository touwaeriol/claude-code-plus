package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.exceptions.ClientNotConnectedException
import com.asakii.claude.agent.sdk.protocol.ControlProtocol
import com.asakii.claude.agent.sdk.transport.SubprocessTransport
import com.asakii.claude.agent.sdk.transport.Transport
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import com.asakii.logging.*
import kotlin.jvm.JvmOverloads

/**
 * JSON 配置：使用 "type" 作为多态类型鉴别器
 * 这样 TextInput 序列化为 {"type": "text", "text": "..."}
 */
private val streamJsonFormat = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/**
 * Client for bidirectional, interactive conversations with Claude Agent.
 *
 * This client provides full control over the conversation flow with support
 * for streaming, interrupts, and dynamic message sending.
 *
 * Key features:
 * - **Bidirectional**: Send and receive messages at any time
 * - **Stateful**: Maintains conversation context across messages
 * - **Interactive**: Send follow-ups based on responses
 * - **Control flow**: Support for interrupts and session management
 * - **Partial messages**: Stream partial message updates (when enabled)
 * - **Programmatic agents**: Define subagents inline
 *
 * When to use ClaudeCodeSdkClient:
 * - Building chat interfaces or conversational UIs
 * - Interactive debugging or exploration sessions
 * - Multi-turn conversations with context
 * - When you need to react to Claude's responses
 * - Real-time applications with user input
 * - When you need interrupt capabilities
 *
 * API Design:
 * The simplified API provides a clean query → receive_response pattern:
 * - `query()` sends a message to Claude
 * - `receiveResponse()` returns a Flow that ends after ResultMessage
 * - Each response is complete and self-contained
 * - No need for continuous message streaming
 *
 * Example usage:
 * ```kotlin
 * val options = ClaudeAgentOptions(
 *     model = "claude-3-5-sonnet",
 *     allowedTools = listOf("Read", "Write", "Bash"),
 *     systemPrompt = SystemPromptPreset(preset = "claude_code")
 * )
 * val client = ClaudeCodeSdkClient(options)
 *
 * client.connect()
 * client.query("Hello, Claude!")
 *
 * client.receiveResponse().collect { message ->
 *     when (message) {
 *         is AssistantMessage -> println("Claude: ${message.content}")
 *         is StreamEvent -> println("Partial: ${message.event}")
 *         is ResultMessage -> println("Done!")
 *     }
 * }
 *
 * client.disconnect()
 * ```
 */
class ClaudeCodeSdkClient @JvmOverloads constructor(
    private val options: ClaudeAgentOptions = ClaudeAgentOptions(),
    private val transport: Transport? = null
) {
    private var actualTransport: Transport? = null
    private var controlProtocol: ControlProtocol? = null
    private var clientScope: CoroutineScope? = null
    private var serverInfo: JsonObject? = null
    private val commandMutex = Mutex()
    private var pendingModelUpdate: CompletableDeferred<String?>? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val logger = getLogger("ClaudeCodeSdkClient")
    
    /**
     * Format systemPrompt for logging (handles String, SystemPromptPreset, or null).
     */
    private fun formatSystemPrompt(systemPrompt: Any?): String {
        return when (systemPrompt) {
            null -> "null"
            is String -> {
                val truncated = if (systemPrompt.length > 100) {
                    systemPrompt.substring(0, 100) + "..."
                } else {
                    systemPrompt
                }
                "\"$truncated\""
            }
            else -> systemPrompt.toString().take(100)
        }
    }

    /**
     * Connect to Claude with optional initial prompt.
     */
    @JvmOverloads
    suspend fun connect(prompt: String? = null) {
        logger.info { "🔌 [SDK] 开始连接到Claude CLI..." }

        // 打印 connect 参数
        logger.info { "📋 [SDK] connect 调用参数:" }
        logger.info { "  - prompt: ${prompt ?: "null"}" }
        logger.info { "📋 [SDK] 客户端配置 (在创建时传入):" }
        logger.info { "  - model: ${options.model}" }
        logger.info { "  - permissionMode: ${options.permissionMode}" }
        logger.info { "  - maxTurns: ${options.maxTurns}" }
        logger.info { "  - systemPrompt: ${formatSystemPrompt(options.systemPrompt)}" }
        logger.info { "  - dangerouslySkipPermissions: ${options.dangerouslySkipPermissions}" }
        logger.info { "  - allowDangerouslySkipPermissions: ${options.allowDangerouslySkipPermissions}" }
        logger.info { "  - allowedTools: ${options.allowedTools}" }
        logger.info { "  - includePartialMessages: ${options.includePartialMessages}" }
        logger.info { "  - canUseTool: ${options.canUseTool != null}" }
        logger.info { "  - permissionPromptToolName: ${options.permissionPromptToolName}" }
        
        // Create or use provided transport
        actualTransport = transport ?: SubprocessTransport(options, streamingMode = true)
        logger.info { "🚀 创建SubprocessTransport，流模式: true" }

        // Create control protocol
        controlProtocol = ControlProtocol(actualTransport!!, options).apply {
            systemInitCallback = { modelId -> onSystemInit(modelId) }
        }
        logger.info { "📡 创建ControlProtocol" }

        // Create client scope for background tasks
        clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        logger.info { "⚡ 创建ClientScope" }

        try {
            // Start transport
            logger.info { "🚀 启动Transport连接..." }
            actualTransport!!.connect()
            logger.info { "✅ Transport连接成功" }

            // 注册 MCP 服务器 - 必须在 startMessageProcessing 之前！
            // 因为 CLI 启动后会立即发送 mcp_message 请求
            logger.info { "📦 注册 MCP 服务器..." }
            controlProtocol!!.registerMcpServers()
            logger.info { "✅ MCP 服务器注册完成" }

            // Start message processing
            logger.info { "📥 启动消息处理..." }
            controlProtocol!!.startMessageProcessing(clientScope!!)
            logger.info { "✅ 消息处理已启动" }

            // Initialize control protocol (registers hooks)
            logger.info { "🔧 初始化控制协议..." }
            controlProtocol!!.initialize()
            logger.info { "✅ 控制协议初始化完成" }

            serverInfo = buildJsonObject {
                put("status", "connected")
                put("mode", "stream-json")
            }
            logger.info { "🎉 Claude SDK客户端连接成功!" }

            // Send initial prompt if provided
            prompt?.let {
                logger.info { "📝 发送初始提示: $it" }
                query(it)
            }

        } catch (e: Exception) {
            logger.error { "❌ 连接失败: ${e.message}" }
            // Cleanup on failure
            disconnect()
            throw e
        }
    }
    
    /**
     * Send a user message to Claude (text only).
     */
    @JvmOverloads
    suspend fun query(prompt: String, sessionId: String = "default") {
        val message = StreamJsonUserMessage(
            message = UserMessagePayload(prompt),
            sessionId = sessionId
        )
        query(message)
    }

    /**
     * Send a user message with arbitrary content blocks.
     *
     * @param content List of content blocks (TextInput, ImageInput)
     * @param sessionId Session ID
     */
    @JvmOverloads
    suspend fun query(content: List<UserInputContent>, sessionId: String = "default") {
        val message = StreamJsonUserMessage(
            message = UserMessagePayload(content = content),
            sessionId = sessionId
        )
        query(message)
    }

    /**
     * Send a complete StreamJsonUserMessage to Claude.
     *
     * This is the core method - all other query overloads delegate to this.
     *
     * @param message Complete stream-json user message
     */
    suspend fun query(message: StreamJsonUserMessage) {
        runCommand {
            ensureConnected()

            logger.info { "💬 发送用户消息 [session=${message.sessionId}]: ${message.message.content.size} 个内容块" }

            val jsonString = streamJsonFormat.encodeToString(StreamJsonUserMessage.serializer(), message)
            logger.info { "📤 发送JSON消息: $jsonString" }
            actualTransport!!.write(jsonString)
            logger.info { "✅ 消息已发送到CLI" }
        }
    }

    /**
     * Send a stream of messages to Claude.
     */
    @JvmOverloads
    suspend fun queryStream(messages: Flow<JsonObject>, sessionId: String = "default") {
        runCommand {
            ensureConnected()
            messages.collect { messageData ->
                val enhancedMessage = buildJsonObject {
                    messageData.forEach { (key, value) -> put(key, value) }
                    put("session_id", sessionId)
                }
                actualTransport!!.write(enhancedMessage.toString())
            }
        }
    }
    
    /**
     * Receive a single complete response (until ResultMessage).
     * This is the main method for receiving Claude's responses.
     *
     * The Flow will automatically complete after receiving a ResultMessage.
     * 使用 transformWhile 让 Flow 自然结束，不会抛出 CancellationException。
     */
    fun receiveResponse(): Flow<Message> {
        ensureConnected()
        logger.info { "📬 [receiveResponse] 开始接收Claude响应消息..." }

        var messageCount = 0

        // 使用 transformWhile 在 ResultMessage 后结束 Flow（和官方 Python SDK 一致）
        return controlProtocol!!.sdkMessages
            .onEach { message ->
                messageCount++
                val messageType = message::class.simpleName
                logger.info { "📨 [receiveResponse] 收到消息 #$messageCount: $messageType" }

                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.filterIsInstance<TextBlock>()
                            .joinToString("") { it.text }
                        logger.info { "🤖 [receiveResponse] Claude回复: ${content.take(100)}${if (content.length > 100) "..." else ""}" }
                    }
                    is ResultMessage -> {
                        logger.info { "🎯 [receiveResponse] 结果消息: subtype=${message.subtype}, isError=${message.isError}" }
                        logger.info { "📊 [receiveResponse] 统计: 共收到 $messageCount 条消息" }
                    }
                    is UserMessage -> {
                        logger.info { "👤 [receiveResponse] 用户消息: isReplay=${message.isReplay}" }
                    }
                    else -> {
                        logger.info { "📄 [receiveResponse] 其他消息: $messageType" }
                    }
                }
            }
            .transformWhile { message ->
                emit(message)
                // 只有 ResultMessage 才结束 Flow（和官方 Python SDK receive_response() 一致）
                message !is ResultMessage
            }
            .onCompletion { cause ->
                if (cause == null) {
                    logger.info { "✅ [receiveResponse] Flow 正常完成，共收到 $messageCount 条消息" }
                } else if (cause is kotlinx.coroutines.CancellationException) {
                    logger.info { "ℹ️ [receiveResponse] Flow 被取消，共收到 $messageCount 条消息" }
                } else {
                    logger.warn { "⚠️ [receiveResponse] Flow 异常结束: ${cause.message}" }
                }
            }
    }

    /**
     * 获取底层的持续消息流（不会在 ResultMessage 后结束）
     *
     * 这个流会持续推送所有来自 Claude 的消息，适用于需要持续监听的场景（如 WebSocket）。
     * 与 receiveResponse() 不同，这个流不会自动结束。
     *
     * @return 持续的消息流
     */
    fun getAllMessages(): Flow<Message> {
        ensureConnected()
        return controlProtocol!!.sdkMessages
    }
    
    /**
     * Interrupt the current operation.
     */
    suspend fun interrupt() {
        ensureConnected()
        controlProtocol!!.interrupt()
    }

    /**
     * Get MCP servers status.
     *
     * Returns the status of all connected MCP servers including their tools.
     *
     * @return List of MCP server status info
     */
    suspend fun getMcpStatus(): List<McpServerStatusInfo> {
        ensureConnected()
        return controlProtocol!!.getMcpStatus()
    }

    /**
     * Reconnect a specific MCP server.
     *
     * This calls the CLI internal reconnect function to re-establish the connection
     * without doing a full server replacement. Useful when a server temporarily
     * disconnects or needs to be refreshed.
     *
     * @param serverName The name of the MCP server to reconnect
     * @return Response with success status, server info and any errors
     */
    suspend fun reconnectMcp(serverName: String): McpReconnectResponse {
        ensureConnected()
        return controlProtocol!!.reconnectMcp(serverName)
    }

    /**
     * Change permission mode during conversation.
     *
     * This allows dynamically switching between permission modes without reconnecting.
     *
     * @param mode The permission mode to set:
     *   - "default": CLI prompts for dangerous tools
     *   - "acceptEdits": Auto-accept file edits
     *   - "bypassPermissions": Allow all tools (use with caution)
     *   - "plan": Plan mode (for planning without executing)
     *
     * Example:
     * ```kotlin
     * val client = ClaudeCodeSdkClient(options)
     * client.connect()
     *
     * // Start with default permissions
     * client.query("Analyze this codebase")
     * client.receiveResponse().collect { ... }
     *
     * // Switch to auto-accept edits
     * client.setPermissionMode(PermissionMode.ACCEPT_EDITS)
     * client.query("Implement the fix")
     * client.receiveResponse().collect { ... }
     * ```
     */
    suspend fun setPermissionMode(mode: PermissionMode) {
        runCommand {
            ensureConnected()
            val modeString = mode.toCliString()
            logger.info { "🔐 设置权限模式: $mode ($modeString)" }

            controlProtocol!!.setPermissionMode(modeString)

            logger.info { "✅ 权限模式已更新为: $mode" }
        }
    }

    /**
     * 将 PermissionMode 枚举转换为 CLI 期望的字符串格式
     */
    private fun PermissionMode.toCliString(): String = when (this) {
        PermissionMode.DEFAULT -> "default"
        PermissionMode.ACCEPT_EDITS -> "acceptEdits"
        PermissionMode.BYPASS_PERMISSIONS -> "bypassPermissions"
        PermissionMode.PLAN -> "plan"
    }

    /**
     * Change the AI model during conversation.
     *
     * This allows switching models mid-conversation for different tasks.
     *
     * @param model The model to use, or null to use default. Examples:
     *   - "claude-sonnet-4-20250514"
     *   - "claude-opus-4-20250514"
     *   - "claude-haiku-4-20250514"
     *   - null (use default model)
     *
     * Example:
     * ```kotlin
     * val client = ClaudeCodeSdkClient(options)
     * client.connect()
     *
     * // Start with default model
     * client.query("Explain this architecture")
     * client.receiveResponse().collect { ... }
     *
     * // Switch to a faster model for simple tasks
     * client.setModel("claude-haiku-4-20250514")
     * client.query("Add a docstring to this function")
     * client.receiveResponse().collect { ... }
     * ```
     */
    suspend fun setModel(model: String?): String? = runCommand {
        ensureConnected()
        logger.info { "🤖 设置模型: ${model ?: "default"}" }

        val deferred = CompletableDeferred<String?>()
        pendingModelUpdate?.cancel()
        pendingModelUpdate = deferred

        try {
            controlProtocol!!.setModel(model ?: "default")
        } catch (e: Exception) {
            pendingModelUpdate = null
            deferred.completeExceptionally(e)
            throw e
        }

        val result = try {
            withTimeout(5_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            logger.warn { "等待模型切换确认超时，使用请求模型作为回退: ${model ?: "default"}" }
            model
        } finally {
            pendingModelUpdate = null
        }

        updateCachedModel(result ?: model)
        logger.info { "✅ 模型已更新为: ${result ?: model ?: "default"}" }
        result ?: model
    }

    /**
     * Dynamically set max thinking tokens without reconnecting.
     *
     * @param maxThinkingTokens The maximum thinking tokens:
     *   - null: Disable thinking (use default behavior)
     *   - 0: Disable thinking
     *   - positive value: Set the limit (e.g., 8000, 16000)
     */
    suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?) = runCommand {
        ensureConnected()
        logger.info { "🧠 设置思考 token 上限: $maxThinkingTokens" }
        controlProtocol!!.setMaxThinkingTokens(maxThinkingTokens)
        logger.info { "✅ 思考 token 上限已设置为: $maxThinkingTokens" }
    }

    /**
     * Get server initialization information.
     */
    fun getServerInfo(): JsonObject? = serverInfo
    
    /**
     * Check if the client is connected.
     */
    fun isConnected(): Boolean {
        val transportConnected = actualTransport?.isConnected() == true
        val hasBasicConnection = serverInfo != null

        logger.error { "🔍 [isConnected] transport=${transportConnected}, hasBasicConnection=${hasBasicConnection}, serverInfo=$serverInfo" }

        // 如果transport连接且有基本连接信息（包括fallback模式），则认为已连接
        val result = transportConnected && hasBasicConnection
        logger.error { "🔍 [isConnected] 最终结果: $result" }
        return result
    }
    
    /**
     * Disconnect from Claude and cleanup resources.
     */
    suspend fun disconnect() {
        try {
            pendingModelUpdate?.cancel()
            pendingModelUpdate = null
            controlProtocol?.stopMessageProcessing()
            actualTransport?.close()
            clientScope?.let { scope ->
                scope.cancel()
                // CoroutineScope doesn't have join(), we use Job.join()
                scope.coroutineContext[Job]?.join()
            }
        } finally {
            actualTransport = null
            controlProtocol = null
            clientScope = null
            serverInfo = null
        }
    }

    private fun updateCachedModel(model: String?) {
        val existing = serverInfo
        serverInfo = buildJsonObject {
            existing?.forEach { (key, value) -> put(key, value) }
            put("model", model ?: "default")
            if (existing?.containsKey("status") != true) {
                put("status", "connected")
            }
            if (existing?.containsKey("mode") != true) {
                put("mode", "stream-json")
            }
        }
    }

    private suspend fun <T> runCommand(block: suspend () -> T): T {
        return commandMutex.withLock { block() }
    }

    internal fun onSystemInit(modelId: String?) {
        pendingModelUpdate?.let { deferred ->
            if (!deferred.isCompleted) {
                deferred.complete(modelId)
            }
        }
    }
    
    /**
     * Use the client within a scope that automatically handles connection lifecycle.
     */
    suspend fun <T> use(block: suspend ClaudeCodeSdkClient.() -> T): T {
        connect()
        return try {
            block()
        } finally {
            disconnect()
        }
    }
    
    /**
     * Create a simple query function for one-shot interactions.
     */
    suspend fun simpleQuery(prompt: String): List<Message> {
        return use {
            query(prompt)
            receiveResponse().toList()
        }
    }
    
    /**
     * Ensure the client is connected, throw exception if not.
     */
    private fun ensureConnected() {
        if (!isConnected()) {
            throw ClientNotConnectedException()
        }
    }
}

/**
 * Builder function for creating ClaudeCodeSdkClient with options.
 * Usage:
 * ```kotlin
 * val client = claudeCodeSdkClient(
 *     ClaudeAgentOptions(
 *         model = "claude-3-5-sonnet-20241022",
 *         allowedTools = listOf("Read", "Write")
 *     )
 * )
 * ```
 */
@JvmOverloads
fun claudeCodeSdkClient(options: ClaudeAgentOptions = ClaudeAgentOptions()): ClaudeCodeSdkClient {
    return ClaudeCodeSdkClient(options)
}

/**
 * Convenience function for simple one-shot queries.
 */
@JvmOverloads
suspend fun claudeQuery(
    prompt: String,
    options: ClaudeAgentOptions = ClaudeAgentOptions()
): List<Message> {
    return ClaudeCodeSdkClient(options).simpleQuery(prompt)
}

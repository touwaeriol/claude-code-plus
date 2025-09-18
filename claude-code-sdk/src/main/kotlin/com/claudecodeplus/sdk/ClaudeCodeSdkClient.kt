package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.exceptions.ClientNotConnectedException
import com.claudecodeplus.sdk.protocol.ControlProtocol
import com.claudecodeplus.sdk.transport.SubprocessTransport
import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.logging.Logger

/**
 * Client for bidirectional, interactive conversations with Claude Code.
 *
 * This client provides full control over the conversation flow with support
 * for streaming, interrupts, and dynamic message sending. 
 *
 * Key features:
 * - **Bidirectional**: Send and receive messages at any time
 * - **Stateful**: Maintains conversation context across messages
 * - **Interactive**: Send follow-ups based on responses
 * - **Control flow**: Support for interrupts and session management
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
 * val options = ClaudeCodeOptions(
 *     model = "claude-3-5-sonnet",
 *     allowedTools = listOf("Read", "Write", "Bash")
 * )
 * val client = ClaudeCodeSdkClient(options)
 * 
 * client.connect()
 * client.query("Hello, Claude!")
 * 
 * client.receiveResponse().collect { message ->
 *     when (message) {
 *         is AssistantMessage -> println("Claude: ${message.content}")
 *         is ResultMessage -> println("Done!")
 *     }
 * }
 * 
 * client.disconnect()
 * ```
 */
class ClaudeCodeSdkClient(
    private val options: ClaudeCodeOptions = ClaudeCodeOptions(),
    private val transport: Transport? = null
) {
    private var actualTransport: Transport? = null
    private var controlProtocol: ControlProtocol? = null
    private var clientScope: CoroutineScope? = null
    private var serverInfo: Map<String, Any>? = null
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val logger = Logger.getLogger(ClaudeCodeSdkClient::class.java.name)
    
    /**
     * Connect to Claude with optional initial prompt.
     */
    suspend fun connect(prompt: String? = null) {
        logger.info("🔌 开始连接到Claude CLI...")
        logger.info("📋 使用配置: model=${options.model}, allowedTools=${options.allowedTools}")
        
        // Create or use provided transport
        actualTransport = transport ?: SubprocessTransport(options, streamingMode = true)
        logger.info("🚀 创建SubprocessTransport，流模式: true")
        
        // Create control protocol
        controlProtocol = ControlProtocol(actualTransport!!, options)
        logger.info("📡 创建ControlProtocol")
        
        // Create client scope for background tasks
        clientScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        logger.info("⚡ 创建ClientScope")
        
        try {
            // Start transport
            logger.info("🚀 启动Transport连接...")
            actualTransport!!.connect()
            logger.info("✅ Transport连接成功")
            
            // Start message processing
            logger.info("📥 启动消息处理...")
            controlProtocol!!.startMessageProcessing(clientScope!!)
            logger.info("✅ 消息处理已启动")

            // 跳过控制协议初始化 - Claude CLI不需要这个步骤
            logger.info("✅ 跳过控制协议初始化（Claude CLI直接使用stream-json模式）")
            serverInfo = mapOf("status" to "connected", "mode" to "stream-json")
            logger.info("🎉 Claude SDK客户端连接成功!")
            
            // Send initial prompt if provided
            prompt?.let { 
                logger.info("📝 发送初始提示: $it")
                query(it) 
            }
            
        } catch (e: Exception) {
            logger.severe("❌ 连接失败: ${e.message}")
            // Cleanup on failure
            disconnect()
            throw e
        }
    }
    
    /**
     * Send a user message to Claude.
     */
    suspend fun query(prompt: String, sessionId: String = "default") {
        ensureConnected()
        
        logger.info("💬 发送用户消息 [session=$sessionId]: $prompt")
        
        val userMessage = UserMessage(
            content = JsonPrimitive(prompt),
            sessionId = sessionId
        )
        
        val messageJson = buildJsonObject {
            put("type", "user")
            put("message", buildJsonObject {
                put("role", "user")
                put("content", prompt)
            })
            put("parent_tool_use_id", JsonNull)
            put("session_id", sessionId)
        }
        
        logger.info("📤 发送JSON消息: ${messageJson.toString()}")
        actualTransport!!.write(messageJson.toString())
        logger.info("✅ 消息已发送到CLI")
    }
    
    /**
     * Send a stream of messages to Claude.
     */
    suspend fun queryStream(messages: Flow<Map<String, Any>>, sessionId: String = "default") {
        ensureConnected()
        
        messages.collect { messageData ->
            val enhancedMessage = messageData.toMutableMap().apply {
                put("session_id", sessionId)
            }
            
            val messageJson = Json.encodeToJsonElement(enhancedMessage)
            actualTransport!!.write(messageJson.toString())
        }
    }
    
    /**
     * Receive a single complete response (until ResultMessage).
     * This is the main method for receiving Claude's responses.
     * 
     * The Flow will automatically complete after receiving a ResultMessage.
     */
    fun receiveResponse(): Flow<Message> {
        ensureConnected()
        logger.info("📬 开始接收Claude响应消息...")
        
        return channelFlow {
            val job = launch {
                controlProtocol!!.sdkMessages.collect { message ->
                    logger.info("📨 收到消息: ${message::class.simpleName}")
                    when (message) {
                        is AssistantMessage -> {
                            val content = message.content.filterIsInstance<TextBlock>()
                                .joinToString("") { it.text }
                            logger.info("🤖 Claude回复: ${content.take(100)}${if (content.length > 100) "..." else ""}")
                        }
                        is SystemMessage -> {
                            logger.info("🔧 系统消息: ${message.subtype} - ${message.data}")
                        }
                        is ResultMessage -> {
                            logger.info("🎯 结果消息: ${message.subtype}, error=${message.isError}")
                        }
                        is UserMessage -> {
                            logger.info("👤 用户消息: ${message.content}")
                        }
                        else -> {
                            logger.info("📄 其他消息: ${message::class.simpleName}")
                        }
                    }
                    
                    send(message)
                    
                    if (message is ResultMessage) {
                        logger.info("🏁 收到ResultMessage，响应流结束")
                        close() // Close channel after ResultMessage, terminating the Flow
                    }
                }
            }
            awaitClose { 
                logger.info("🚪 响应流已关闭")
                job.cancel() 
            }
        }
    }
    
    /**
     * Interrupt the current operation.
     */
    suspend fun interrupt() {
        ensureConnected()
        controlProtocol!!.interrupt()
    }
    
    /**
     * Get server initialization information.
     */
    fun getServerInfo(): Map<String, Any>? = serverInfo
    
    /**
     * Check if the client is connected.
     */
    fun isConnected(): Boolean {
        val transportConnected = actualTransport?.isConnected() == true
        val hasBasicConnection = serverInfo != null

        logger.severe("🔍 [isConnected] transport=${transportConnected}, hasBasicConnection=${hasBasicConnection}, serverInfo=$serverInfo")

        // 如果transport连接且有基本连接信息（包括fallback模式），则认为已连接
        val result = transportConnected && hasBasicConnection
        logger.severe("🔍 [isConnected] 最终结果: $result")
        return result
    }
    
    /**
     * Disconnect from Claude and cleanup resources.
     */
    suspend fun disconnect() {
        try {
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
 *     ClaudeCodeOptions(
 *         model = "claude-3-5-sonnet-20241022",
 *         allowedTools = listOf("Read", "Write")
 *     )
 * )
 * ```
 */
fun claudeCodeSdkClient(options: ClaudeCodeOptions = ClaudeCodeOptions()): ClaudeCodeSdkClient {
    return ClaudeCodeSdkClient(options)
}

/**
 * Convenience function for simple one-shot queries.
 */
suspend fun claudeQuery(
    prompt: String,
    options: ClaudeCodeOptions = ClaudeCodeOptions()
): List<Message> {
    return ClaudeCodeSdkClient(options).simpleQuery(prompt)
}
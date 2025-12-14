package com.asakii.claude.agent.sdk.protocol

import   com.asakii.claude.agent.sdk.exceptions.ControlProtocolException
import com.asakii.claude.agent.sdk.transport.Transport
import com.asakii.claude.agent.sdk.types.*
import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.claude.agent.sdk.types.ResultMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import mu.KotlinLogging

/**
 * Control protocol handler for managing bidirectional communication with Claude CLI.
 */
class ControlProtocol(
    private val transport: Transport,
    private val options: ClaudeCodeOptions
) {
    var systemInitCallback: ((String?) -> Unit)? = null
    private val messageParser = MessageParser()
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    // Request tracking
    private val requestCounter = AtomicLong(0)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<ControlResponse>>()
    
    // Hook callbacks
    private val hookCallbacks = ConcurrentHashMap<String, HookCallback>()
    private val hookIdCounter = AtomicLong(0)
    
    // SDK MCP servers - 新增支持
    private val sdkMcpServers = ConcurrentHashMap<String, Any>()
    
    // New McpServer interface instances
    private val newMcpServers = ConcurrentHashMap<String, McpServer>()
    
    // Message routing
    private val _sdkMessages = Channel<Message>(Channel.UNLIMITED)
    val sdkMessages: Flow<Message> = _sdkMessages.receiveAsFlow()
    
    private var messageProcessingJob: Job? = null
    
    // 初始化状态追踪
    private var initialized = false
    private val _initializationResult = CompletableDeferred<Map<String, Any>>()
    
    // Logger
    private val logger = KotlinLogging.logger {}
    
    /**
     * Start processing messages from transport.
     */
    fun startMessageProcessing(scope: CoroutineScope) {
        logger.info("🚀 [ControlProtocol] 开始消息处理任务")
        messageProcessingJob = scope.launch {
            var messageCount = 0
            try {
                transport.readMessages().collect { jsonElement ->
                    messageCount++
                    try {
                        logger.info("📥 [ControlProtocol] 从 Transport 收到原始消息 #$messageCount")
                        routeMessage(jsonElement)
                    } catch (e: Exception) {
                        logger.error("❌ [ControlProtocol] 处理消息失败: ${e.message}")
                        e.printStackTrace()
                    }
                }
            } catch (e: CancellationException) {
                logger.info("ℹ️ [ControlProtocol] 消息处理任务被取消")
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: e::class.simpleName ?: "Unknown transport error"
                logger.error("❌ [ControlProtocol] 从 Transport 读取消息失败: $errorMessage")
                logger.error("📊 [ControlProtocol] 统计: 共处理 $messageCount 条消息")
                e.printStackTrace()
                // Push an error result so上层能够收到错误事件而不是卡死
                _sdkMessages.trySend(
                    ResultMessage(
                        subtype = "error",
                        durationMs = 0,
                        durationApiMs = 0,
                        isError = true,
                        numTurns = 0,
                        sessionId = "default",
                        result = errorMessage
                    )
                )
                _sdkMessages.close()
                logger.info("🔒 [ControlProtocol] sdkMessages channel 已关闭")
            }
        }
    }
    
    /**
     * 注册 MCP 服务器（不发送请求）
     * 必须在 startMessageProcessing() 之前调用！
     * 因为 CLI 启动后会立即发送 mcp_message 请求
     */
    fun registerMcpServers() {
        println("🔄 注册 MCP 服务器...")
        println("📋 MCP 服务器配置: ${options.mcpServers}")
        println("📋 MCP 服务器数量: ${options.mcpServers.size}")

        options.mcpServers.forEach { (name, config) ->
            when {
                config is Map<*, *> && config["type"] == "sdk" -> {
                    val instance = config["instance"]
                    if (instance != null) {
                        when (instance) {
                            is McpServer -> {
                                newMcpServers[name] = instance
                                println("📦 注册新接口 MCP 服务器: $name (${instance::class.simpleName})")
                            }
                            else -> {
                                sdkMcpServers[name] = instance
                                println("📦 注册旧版 SDK MCP 服务器: $name")
                            }
                        }
                    }
                }
                config is McpServer -> {
                    newMcpServers[name] = config
                    println("📦 注册直接提供的 MCP 服务器: $name (${config::class.simpleName})")
                }
            }
        }
        println("✅ MCP 服务器注册完成: ${newMcpServers.keys + sdkMcpServers.keys}")
    }

    /**
     * Initialize control protocol - 仿照Python SDK实现
     * This must be called after startMessageProcessing() and before using hooks
     */
    suspend fun initialize(): Map<String, Any> {
        if (initialized) {
            return _initializationResult.await()
        }

        println("🔄 初始化控制协议...")

        // 构建hooks配置（仿照Python SDK的hooks_config构建）
        val hooksConfig = mutableMapOf<String, JsonElement>()
        options.hooks?.let { hooks ->
            hooks.forEach { (event, matchers) ->
                if (matchers.isNotEmpty()) {
                    val eventName = when (event) {
                        HookEvent.PRE_TOOL_USE -> "PreToolUse"
                        HookEvent.POST_TOOL_USE -> "PostToolUse"
                        HookEvent.USER_PROMPT_SUBMIT -> "UserPromptSubmit"
                        HookEvent.STOP -> "Stop"
                        HookEvent.SUBAGENT_STOP -> "SubagentStop"
                        HookEvent.PRE_COMPACT -> "PreCompact"
                    }
                    
                    val eventMatchers = mutableListOf<JsonObject>()
                    matchers.forEach { matcher ->
                        val callbackIds = mutableListOf<String>()
                        matcher.hooks.forEach { callback ->
                            val callbackId = "hook_${hookIdCounter.incrementAndGet()}"
                            hookCallbacks[callbackId] = callback
                            callbackIds.add(callbackId)
                            println("🎣 注册Hook回调: $callbackId")
                        }
                        
                        eventMatchers.add(buildJsonObject {
                            matcher.matcher?.let { put("matcher", it) }
                            put("hookCallbackIds", JsonArray(callbackIds.map { JsonPrimitive(it) }))
                        })
                    }
                    
                    hooksConfig[eventName] = JsonArray(eventMatchers)
                }
            }
        }
        
        // 3. 发送初始化控制请求（仿照Python SDK）
        val initRequest = buildJsonObject {
            put("subtype", "initialize")
            if (hooksConfig.isNotEmpty()) {
                put("hooks", JsonObject(hooksConfig))
            }
        }

        // 计算超时时间（仿照 Python SDK，支持环境变量）
        // CLAUDE_CODE_STREAM_CLOSE_TIMEOUT 单位是毫秒，转换为秒
        val timeoutMs = System.getenv("CLAUDE_CODE_STREAM_CLOSE_TIMEOUT")?.toLongOrNull() ?: 60000L
        val initializeTimeout = maxOf(timeoutMs, 60000L) // 至少 60 秒
        
        logger.info("⏱️ [ControlProtocol] Initialize 超时设置: ${initializeTimeout}ms")

        // 发送初始化请求（与 Python SDK 一致，如果超时会抛出异常）
        val response = sendControlRequestInternal(initRequest, initializeTimeout)
        initialized = true

        val result = response.response?.jsonObject?.toMap() ?: mapOf("status" to "initialized")
        _initializationResult.complete(result)

        println("✅ 控制协议初始化完成")
        return result
    }
    
    /**
     * Stop message processing.
     */
    fun stopMessageProcessing() {
        messageProcessingJob?.cancel()
        _sdkMessages.close()
        _systemInitReceived.close()
    }
    
    /**
     * Route incoming messages to appropriate handlers.
     */
    private suspend fun routeMessage(jsonElement: JsonElement) {
        val jsonObject = jsonElement.jsonObject
        val type = jsonObject["type"]?.jsonPrimitive?.content
        
        logger.info("🔀 [ControlProtocol] 路由消息: type=$type")
        
        // Route messages based on type
        when (type) {
            "system" -> {
                val subtype = jsonObject["subtype"]?.jsonPrimitive?.content
                logger.info("🔧 [ControlProtocol] 系统消息: subtype=$subtype")
                when (subtype) {
                    "init" -> {
                        handleSystemInit(jsonElement)
                    }
                    "status" -> {
                        // 状态消息（如 compacting）- 解析并发送到 sdkMessages
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            logger.info("📊 [ControlProtocol] 状态消息: ${(message as? StatusSystemMessage)?.status}")
                            _sdkMessages.send(message)
                            logger.info("✅ [ControlProtocol] 状态消息已发送")
                        } catch (e: Exception) {
                            logger.warn("⚠️ [ControlProtocol] 解析状态消息失败: ${e.message}")
                        }
                    }
                    "compact_boundary" -> {
                        // 压缩边界消息 - 解析并发送到 sdkMessages
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            val compactMsg = message as? CompactBoundaryMessage
                            logger.info("📦 [ControlProtocol] 压缩边界消息: preTokens=${compactMsg?.compactMetadata?.preTokens}, trigger=${compactMsg?.compactMetadata?.trigger}")
                            _sdkMessages.send(message)
                            logger.info("✅ [ControlProtocol] 压缩边界消息已发送")
                        } catch (e: Exception) {
                            logger.warn("⚠️ [ControlProtocol] 解析压缩边界消息失败: ${e.message}")
                        }
                    }
                    else -> {
                        // 其他系统消息（需要有 data 字段）
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            logger.info("📤 [ControlProtocol] 发送系统消息到 sdkMessages: ${message::class.simpleName}")
                            _sdkMessages.send(message)
                            logger.info("✅ [ControlProtocol] 系统消息已发送")
                        } catch (e: Exception) {
                            logger.error("❌ [ControlProtocol] 解析系统消息失败: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                }
            }
            "control_request" -> {
                logger.info("🎮 [ControlProtocol] 控制请求消息")
                val (requestId, request) = messageParser.parseControlRequest(jsonElement)
                handleControlRequest(requestId, request)
            }
            "control_response" -> {
                logger.info("🎮 [ControlProtocol] 控制响应消息")
                val response = messageParser.parseControlResponse(jsonElement)
                val deferred = pendingRequests.remove(response.requestId)
                deferred?.complete(response)
            }
            "assistant", "user", "result", "stream_event" -> {
                // Regular SDK messages
                logger.info("📨 [ControlProtocol] SDK 消息: type=$type")
                try {
                    val message = messageParser.parseMessage(jsonElement)
                    val messageType = message::class.simpleName
                    logger.info("📤 [ControlProtocol] 解析成功，准备发送到 sdkMessages: $messageType")
                    
                    // 记录消息详情
                    when (message) {
                        is ResultMessage -> {
                            logger.info("🎯 [ControlProtocol] ResultMessage 详情: subtype=${message.subtype}, isError=${message.isError}, sessionId=${message.sessionId}")
                        }
                        is StreamEvent -> {
                            val eventType = try {
                                message.event.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                            } catch (e: Exception) {
                                "parse_error"
                            }
                            logger.info("🌊 [ControlProtocol] StreamEvent 详情: eventType=$eventType, sessionId=${message.sessionId}, uuid=${message.uuid}")
                        }
                        is AssistantMessage -> {
                            logger.info("🤖 [ControlProtocol] AssistantMessage 详情: model=${message.model}, contentBlocks=${message.content.size}, parentToolUseId=${message.parentToolUseId}")
                        }
                        is SystemMessage -> {
                            logger.info("🔧 [ControlProtocol] SystemMessage 详情: subtype=${message.subtype}")
                        }
                        is UserMessage -> {
                            logger.info("👤 [ControlProtocol] UserMessage 详情: sessionId=${message.sessionId}, parentToolUseId=${message.parentToolUseId}, isReplay=${message.isReplay}")
                        }
                        is StatusSystemMessage -> {
                            logger.info("📊 [ControlProtocol] StatusSystemMessage 详情: status=${message.status}, sessionId=${message.sessionId}")
                        }
                        is CompactBoundaryMessage -> {
                            logger.info("📦 [ControlProtocol] CompactBoundaryMessage 详情: preTokens=${message.compactMetadata?.preTokens}, trigger=${message.compactMetadata?.trigger}")
                        }
                        else -> {
                            logger.info("📄 [ControlProtocol] 其他消息类型: $messageType")
                        }
                    }
                    
                    _sdkMessages.send(message)
                    logger.info("✅ [ControlProtocol] SDK 消息 ($messageType) 已发送到 sdkMessages channel")
                } catch (e: Exception) {
                    logger.error("❌ [ControlProtocol] 解析 SDK 消息失败: type=$type, error=${e.message}")
                    e.printStackTrace()
                }
            }
            else -> {
                logger.warn("⚠️ [ControlProtocol] 未知消息类型: $type")
            }
        }
    }
    
    // System init handling
    private val _systemInitReceived = Channel<Map<String, Any>>(1)
    
    /**
     * Handle system initialization message from Claude CLI.
     */
    private suspend fun handleSystemInit(jsonElement: JsonElement) {
        try {
            val jsonObject = jsonElement.jsonObject
            val serverInfo = mutableMapOf<String, Any>()
            
            // Extract server information from init message
            jsonObject["session_id"]?.jsonPrimitive?.content?.let { serverInfo["session_id"] = it }
            jsonObject["cwd"]?.jsonPrimitive?.content?.let { serverInfo["cwd"] = it }
            val modelId = jsonObject["model"]?.jsonPrimitive?.content
            modelId?.let { serverInfo["model"] = it }
            jsonObject["permissionMode"]?.jsonPrimitive?.content?.let { serverInfo["permissionMode"] = it }
            jsonObject["apiKeySource"]?.jsonPrimitive?.content?.let { serverInfo["apiKeySource"] = it }
            
            // Extract tools array
            jsonObject["tools"]?.jsonArray?.let { toolsArray ->
                val tools = toolsArray.map { it.jsonPrimitive.content }
                serverInfo["tools"] = tools
            }
            
            // Extract MCP servers
            jsonObject["mcp_servers"]?.jsonArray?.let { mcpArray ->
                val mcpServers = mcpArray.map { mcpServer ->
                    val mcpObj = mcpServer.jsonObject
                    mapOf(
                        "name" to (mcpObj["name"]?.jsonPrimitive?.content ?: ""),
                        "status" to (mcpObj["status"]?.jsonPrimitive?.content ?: "")
                    )
                }
                serverInfo["mcp_servers"] = mcpServers
            }
            
            // 注册hooks（如果提供了的话）
            val hooksConfig = options.hooks?.let { hooks ->
                convertHooksToProtocolFormat(hooks)
            }
            if (hooksConfig != null) {
                // 发送hook注册消息（但这只是可选的，主要依赖动态回调）
                // serverInfo["hooks_registered"] = true
            }
            
            serverInfo["status"] = "connected"
            
            // Send to waiting initialize function
            _systemInitReceived.trySend(serverInfo)
            
            println("System initialization received: $serverInfo")
            systemInitCallback?.invoke(modelId)
        } catch (e: Exception) {
            println("Failed to handle system init: ${e.message}")
            _systemInitReceived.trySend(mapOf("status" to "error", "error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Handle incoming control requests from CLI.
     */
    private suspend fun handleControlRequest(requestId: String, request: ControlRequest) {
        logger.info("🎯 [handleControlRequest] 收到控制请求: requestId=$requestId, subtype=${request.subtype}, type=${request::class.simpleName}")
        try {
            val response = when (request) {
                is HookCallbackRequest -> handleHookCallback(request)
                is PermissionRequest -> handlePermissionRequest(request)
                is McpMessageRequest -> {
                    // MCP 响应需要用 mcp_response 字段包装（参考 Python SDK）
                    val mcpResponse = handleMcpMessage(request)
                    buildJsonObject {
                        put("mcp_response", mcpResponse)
                    }
                }
                else -> throw ControlProtocolException("Unsupported control request: ${request.subtype}")
            }

            sendControlResponse(requestId, "success", response)
        } catch (e: Exception) {
            sendControlResponse(requestId, "error", null, e.message ?: "Unknown error")
        }
    }
    
    /**
     * Handle hook callback requests.
     */
    private suspend fun handleHookCallback(request: HookCallbackRequest): JsonElement {
        val callback = hookCallbacks[request.callbackId]
            ?: throw ControlProtocolException("Unknown hook callback ID: ${request.callbackId}")
        
        // Convert input JsonElement to Map
        val inputMap = when (val input = request.input) {
            is JsonObject -> input.toMap().mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNull ?: value.toString()
                    else -> value.toString()
                }
            }
            else -> throw ControlProtocolException("Hook input must be an object")
        }
        
        val context = HookContext()
        val result = callback(inputMap, request.toolUseId, context)
        
        return Json.encodeToJsonElement(result)
    }
    
    /**
     * Handle tool permission requests.
     */
    private suspend fun handlePermissionRequest(request: PermissionRequest): JsonElement {
        logger.info("🔐 [handlePermissionRequest] ==========================================")
        logger.info("🔐 [handlePermissionRequest] 收到权限请求: toolName=${request.toolName}, toolUseId=${request.toolUseId}")
        logger.info("🔐 [handlePermissionRequest] input keys: ${(request.input as? JsonObject)?.keys}")
        logger.info("🔐 [handlePermissionRequest] suggestions count: ${request.permissionSuggestions?.size ?: 0}")
        logger.info("🔐 [handlePermissionRequest] canUseTool callback configured: ${options.canUseTool != null}")

        val canUseTool = options.canUseTool
            ?: throw ControlProtocolException("No permission callback configured")

        // 直接使用 JsonObject 的 Map<String, JsonElement>
        val inputMap: Map<String, JsonElement> = when (val input = request.input) {
            is JsonObject -> input.toMap()
            else -> throw ControlProtocolException("Permission input must be an object")
        }

        // 解析 permissionSuggestions
        val suggestions: List<PermissionUpdate> = request.permissionSuggestions?.mapNotNull { element ->
            try {
                Json.decodeFromJsonElement<PermissionUpdate>(element)
            } catch (e: Exception) {
                null // 忽略解析失败的建议
            }
        } ?: emptyList()

        val context = ToolPermissionContext(
            suggestions = suggestions
        )

        val result = canUseTool(request.toolName, inputMap, request.toolUseId, context)

        return when (result) {
            is PermissionResultAllow -> {
                buildJsonObject {
                    put("behavior", result.behavior.value)
                    put("updatedInput", JsonObject(result.updatedInput ?: inputMap))
                    result.updatedPermissions?.let { permissions ->
                        putJsonArray("updatedPermissions") {
                            permissions.forEach { perm ->
                                add(Json.encodeToJsonElement(perm))
                            }
                        }
                    }
                }
            }
            is PermissionResultDeny -> {
                buildJsonObject {
                    put("behavior", result.behavior.value)
                    put("message", result.message)
                    if (result.interrupt) {
                        put("interrupt", result.interrupt)
                    }
                }
            }
            else -> throw ControlProtocolException("Unknown permission result type")
        }
    }
    
    /**
     * Handle SDK MCP server message requests - 增强支持新接口
     */
    private suspend fun handleMcpMessage(request: McpMessageRequest): JsonElement {
        val serverName = request.serverName
        val message = request.message
        
        println("📨 处理MCP消息: server=$serverName, method=${message.jsonObject["method"]?.jsonPrimitive?.content}")
        
        // 检查新接口服务器是否存在
        val newServer = newMcpServers[serverName]
        val oldServer = sdkMcpServers[serverName]
        
        if (newServer == null && oldServer == null) {
            return buildJsonObject {
                put("jsonrpc", "2.0")
                message.jsonObject["id"]?.let { put("id", it) }
                putJsonObject("error") {
                    put("code", -32601)
                    put("message", "Server '$serverName' not found")
                }
            }
        }
        
        val method = message.jsonObject["method"]?.jsonPrimitive?.content
        val params = message.jsonObject["params"]?.jsonObject ?: buildJsonObject {}
        val id = message.jsonObject["id"]
        
        try {
            // 优先使用新接口服务器
            if (newServer != null) {
                return handleNewMcpServerMethod(newServer, method, params, id)
            } else if (oldServer != null) {
                // 兼容旧的实现方式
                return handleLegacyMcpServerMethod(serverName, oldServer, method, params, id)
            } else {
                // 不应该到达这里，但作为后备
                return buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("error") {
                        put("code", -32601)
                        put("message", "Server '$serverName' not found")
                    }
                }
            }
        } catch (e: Exception) {
            println("❌ MCP消息处理失败: ${e.message}")
            return buildJsonObject {
                put("jsonrpc", "2.0")
                id?.let { put("id", it) }
                putJsonObject("error") {
                    put("code", -32603)
                    put("message", e.message ?: "Internal error")
                }
            }
        }
    }
    
    /**
     * Send control response back to CLI.
     */
    private suspend fun sendControlResponse(
        requestId: String,
        subtype: String,
        response: JsonElement? = null,
        error: String? = null
    ) {
        val responseMessage = buildJsonObject {
            put("type", "control_response")
            putJsonObject("response") {
                put("subtype", subtype)
                put("request_id", requestId)
                response?.let { put("response", it) }
                error?.let { put("error", it) }
            }
        }
        
        transport.write(responseMessage.toString())
    }
    
    /**
     * Send control request to CLI and wait for response.
     */
    suspend fun sendControlRequest(request: ControlRequest): ControlResponse {
        val requestJson = json.encodeToJsonElement(request)
        return sendControlRequestInternal(requestJson as JsonObject)
    }
    
    /**
     * Internal method for sending control request with JsonObject.
     * @param request The control request to send
     * @param timeoutMs Timeout in milliseconds (default: 60000ms = 60 seconds, matching Python SDK)
     */
    private suspend fun sendControlRequestInternal(
        request: JsonObject, 
        timeoutMs: Long = 60000L
    ): ControlResponse {
        val requestId = "req_${requestCounter.incrementAndGet()}_${System.currentTimeMillis()}"
        val deferred = CompletableDeferred<ControlResponse>()
        pendingRequests[requestId] = deferred
        
        val requestMessage = buildJsonObject {
            put("type", "control_request")
            put("request_id", requestId)
            put("request", request)
        }
        
        try {
            transport.write(requestMessage.toString())
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            throw ControlProtocolException("Control request timeout for $requestId after ${timeoutMs}ms")
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            throw ControlProtocolException("Failed to send control request", e)
        }
    }
    
    /**
     * Initialize the session with hooks configuration.
     */
    /**
     * Wait for system initialization message from Claude CLI.
     * Claude CLI automatically sends this message after connecting.
     */
    suspend fun waitForSystemInit(): Map<String, Any> {
        return withTimeout(30000) { // 30 seconds timeout
            _systemInitReceived.receive()
        }
    }
    
    /**
     * Send interrupt request to CLI.
     */
    suspend fun interrupt() {
        val interruptRequest = InterruptRequest()
        val response = sendControlRequest(interruptRequest)
        
        if (response.subtype == "error") {
            throw ControlProtocolException("Interrupt failed: ${response.error}")
        }
    }
    
    /**
     * Convert hooks configuration to protocol format.
     */
    private fun convertHooksToProtocolFormat(hooks: Map<HookEvent, List<HookMatcher>>): Map<String, JsonElement> {
        val protocolHooks = mutableMapOf<String, JsonElement>()
        
        hooks.forEach { (event, matchers) ->
            val eventName = when (event) {
                HookEvent.PRE_TOOL_USE -> "PreToolUse"
                HookEvent.POST_TOOL_USE -> "PostToolUse"
                HookEvent.USER_PROMPT_SUBMIT -> "UserPromptSubmit"
                HookEvent.STOP -> "Stop"
                HookEvent.SUBAGENT_STOP -> "SubagentStop"
                HookEvent.PRE_COMPACT -> "PreCompact"
            }
            
            val protocolMatchers = matchers.map { matcher ->
                val callbackIds = matcher.hooks.map { callback ->
                    val hookId = "hook_${hookIdCounter.incrementAndGet()}"
                    hookCallbacks[hookId] = callback
                    hookId
                }
                
                buildJsonObject {
                    matcher.matcher?.let { put("matcher", it) }
                    put("hookCallbackIds", JsonArray(callbackIds.map { JsonPrimitive(it) }))
                }
            }
            
            protocolHooks[eventName] = JsonArray(protocolMatchers)
        }
        
        return protocolHooks
    }
    
    /**
     * Handle new McpServer interface methods
     */
    private suspend fun handleNewMcpServerMethod(
        server: McpServer, 
        method: String?, 
        params: JsonObject, 
        id: JsonElement?
    ): JsonElement {
        return when (method) {
            "initialize" -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("result") {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {}
                        }
                        putJsonObject("serverInfo") {
                            put("name", server.name)
                            put("version", server.version)
                            put("description", server.description)
                        }
                    }
                }
            }
            
            "tools/list" -> {
                val tools = server.listTools()
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("result") {
                        putJsonArray("tools") {
                            tools.forEach { tool ->
                                addJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    // 手动将 Map<String, Any> 转换为 JsonElement
                                    put("inputSchema", mapToJsonElement(tool.inputSchema))
                                }
                            }
                        }
                    }
                }
            }
            
            "tools/call" -> {
                val toolName = params["name"]?.jsonPrimitive?.content
                    ?: return buildJsonObject {
                        put("jsonrpc", "2.0")
                        id?.let { put("id", it) }
                        putJsonObject("error") {
                            put("code", -32602)
                            put("message", "Missing required parameter: name")
                        }
                    }
                
                // 直接传递 JsonObject，让工具自己反序列化为强类型
                val argumentsJson = params["arguments"]?.jsonObject ?: buildJsonObject {}

                println("🛠️ 调用新接口工具: $toolName, args: $argumentsJson")

                val result = server.callToolJson(toolName, argumentsJson)
                
                when (result) {
                    is ToolResult.Success -> {
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            id?.let { put("id", it) }
                            putJsonObject("result") {
                                putJsonArray("content") {
                                    result.content.forEach { contentItem ->
                                        addJsonObject {
                                            when (contentItem) {
                                                is ContentItem.Text -> {
                                                    put("type", "text")
                                                    put("text", contentItem.text)
                                                }
                                                is ContentItem.Json -> {
                                                    put("type", "text")
                                                    put("text", contentItem.data.toString())
                                                }
                                                is ContentItem.Binary -> {
                                                    put("type", "resource")
                                                    put("mimeType", contentItem.mimeType)
                                                    // Base64编码数据
                                                    put("data", java.util.Base64.getEncoder().encodeToString(contentItem.data))
                                                }
                                            }
                                        }
                                    }
                                }
                                if (result.metadata.isNotEmpty()) {
                                    put("meta", Json.encodeToJsonElement(result.metadata))
                                }
                            }
                        }
                    }
                    is ToolResult.Error -> {
                        buildJsonObject {
                            put("jsonrpc", "2.0")
                            id?.let { put("id", it) }
                            putJsonObject("error") {
                                put("code", result.code)
                                put("message", result.error)
                            }
                        }
                    }
                }
            }
            
            "notifications/initialized" -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    putJsonObject("result") {}
                }
            }
            
            else -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("error") {
                        put("code", -32601)
                        put("message", "Method '$method' not found")
                    }
                }
            }
        }
    }

    /**
     * 将 Map<String, Any> 递归转换为 JsonElement
     */
    private fun mapToJsonElement(map: Map<String, Any?>): JsonElement {
        return buildJsonObject {
            map.forEach { (key, value) ->
                put(key, anyToJsonElement(value))
            }
        }
    }

    /**
     * 将任意值转换为 JsonElement
     */
    @Suppress("UNCHECKED_CAST")
    private fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> mapToJsonElement(value as Map<String, Any?>)
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            is JsonElement -> value
            else -> JsonPrimitive(value.toString())
        }
    }

    /**
     * Handle legacy MCP server methods (for backward compatibility)
     */
    private suspend fun handleLegacyMcpServerMethod(
        serverName: String,
        server: Any,
        method: String?,
        params: JsonObject,
        id: JsonElement?
    ): JsonElement {
        // 保持原来的旧实现方式，用于兼容性
        return when (method) {
            "initialize" -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("result") {
                        put("protocolVersion", "2024-11-05")
                        putJsonObject("capabilities") {
                            putJsonObject("tools") {}
                        }
                        putJsonObject("serverInfo") {
                            put("name", serverName)
                            put("version", "1.0.0")
                        }
                    }
                }
            }
            
            "tools/list" -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("result") {
                        put("tools", JsonArray(emptyList())) // 旧版本暂不支持工具列表
                    }
                }
            }
            
            "tools/call" -> {
                val toolName = params["name"]?.jsonPrimitive?.content
                val arguments = params["arguments"]?.jsonObject ?: buildJsonObject {}
                
                println("🛠️ 调用旧版工具: $toolName, args: $arguments")
                
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("result") {
                        putJsonArray("content") {
                            addJsonObject {
                                put("type", "text")
                                put("text", "工具 $toolName 执行成功（旧版兼容模式）")
                            }
                        }
                    }
                }
            }
            
            "notifications/initialized" -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    putJsonObject("result") {}
                }
            }
            
            else -> {
                buildJsonObject {
                    put("jsonrpc", "2.0")
                    id?.let { put("id", it) }
                    putJsonObject("error") {
                        put("code", -32601)
                        put("message", "Method '$method' not found")
                    }
                }
            }
        }
    }
    
    /**
     * Convert JsonObject to Map for easier handling.
     */
    private fun JsonObject.toMap(): Map<String, JsonElement> =
        this.entries.associate { it.key to it.value }
}




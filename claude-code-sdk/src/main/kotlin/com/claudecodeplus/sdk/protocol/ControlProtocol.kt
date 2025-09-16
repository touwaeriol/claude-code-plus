package com.claudecodeplus.sdk.protocol

import com.claudecodeplus.sdk.exceptions.ControlProtocolException
import com.claudecodeplus.sdk.transport.Transport
import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.sdk.mcp.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Control protocol handler for managing bidirectional communication with Claude CLI.
 */
class ControlProtocol(
    private val transport: Transport,
    private val options: ClaudeCodeOptions
) {
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
    
    /**
     * Start processing messages from transport.
     */
    fun startMessageProcessing(scope: CoroutineScope) {
        messageProcessingJob = scope.launch {
            transport.readMessages().collect { jsonElement ->
                try {
                    routeMessage(jsonElement)
                } catch (e: Exception) {
                    println("Error processing message: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Initialize control protocol - 仿照Python SDK实现
     * This must be called after startMessageProcessing() and before using hooks/MCP servers
     */
    suspend fun initialize(): Map<String, Any> {
        if (initialized) {
            return _initializationResult.await()
        }
        
        println("🔄 初始化控制协议...")
        
        // 1. 提取SDK MCP servers（仿照Python SDK + 支持新接口）
        options.mcpServers?.forEach { (name, config) ->
            when {
                config is Map<*, *> && config["type"] == "sdk" -> {
                    val instance = config["instance"]
                    if (instance != null) {
                        when (instance) {
                            is McpServer -> {
                                // 新接口实例
                                newMcpServers[name] = instance
                                println("📦 注册新接口 MCP 服务器: $name (${instance::class.simpleName})")
                            }
                            else -> {
                                // 旧的任意实例（保持兼容性）
                                sdkMcpServers[name] = instance
                                println("📦 注册旧版 SDK MCP 服务器: $name")
                            }
                        }
                    }
                }
                config is McpServer -> {
                    // 直接提供McpServer实例
                    newMcpServers[name] = config
                    println("📦 注册直接提供的 MCP 服务器: $name (${config::class.simpleName})")
                }
            }
        }
        
        // 2. 构建hooks配置（仿照Python SDK的hooks_config构建）
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
        
        val response = sendControlRequestInternal(initRequest)
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
        
        // Route messages based on type
        
        when (type) {
            "system" -> {
                val subtype = jsonObject["subtype"]?.jsonPrimitive?.content
                if (subtype == "init") {
                    handleSystemInit(jsonElement)
                } else {
                    // Other system messages
                    try {
                        val message = messageParser.parseMessage(jsonElement)
                        _sdkMessages.send(message)
                    } catch (e: Exception) {
                        println("Failed to parse system message: ${e.message}")
                    }
                }
            }
            "control_request" -> {
                val (requestId, request) = messageParser.parseControlRequest(jsonElement)
                handleControlRequest(requestId, request)
            }
            "control_response" -> {
                val response = messageParser.parseControlResponse(jsonElement)
                val deferred = pendingRequests.remove(response.requestId)
                deferred?.complete(response)
            }
            "assistant", "user", "result" -> {
                // Regular SDK messages
                try {
                    val message = messageParser.parseMessage(jsonElement)
                    _sdkMessages.send(message)
                } catch (e: Exception) {
                    println("Failed to parse SDK message: ${e.message}")
                }
            }
            else -> {
                println("Unknown message type: $type")
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
            jsonObject["model"]?.jsonPrimitive?.content?.let { serverInfo["model"] = it }
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
        } catch (e: Exception) {
            println("Failed to handle system init: ${e.message}")
            _systemInitReceived.trySend(mapOf("status" to "error", "error" to (e.message ?: "Unknown error")))
        }
    }
    
    /**
     * Handle incoming control requests from CLI.
     */
    private suspend fun handleControlRequest(requestId: String, request: ControlRequest) {
        try {
            val response = when (request) {
                is HookCallbackRequest -> handleHookCallback(request)
                is PermissionRequest -> handlePermissionRequest(request)
                is McpMessageRequest -> handleMcpMessage(request) // 新增MCP消息处理
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
        val canUseTool = options.canUseTool 
            ?: throw ControlProtocolException("No permission callback configured")
        
        // Convert input JsonElement to Map
        val inputMap = when (val input = request.input) {
            is JsonObject -> input.toMap().mapValues { (_, value) ->
                when (value) {
                    is JsonPrimitive -> value.contentOrNull ?: value.toString()
                    else -> value.toString()
                }
            }
            else -> throw ControlProtocolException("Permission input must be an object")
        }
        
        val context = ToolPermissionContext(
            suggestions = emptyList() // TODO: Parse permission suggestions
        )
        
        val result = canUseTool(request.toolName, inputMap, context)
        
        return when (result) {
            is PermissionResultAllow -> Json.encodeToJsonElement(
                mapOf(
                    "allow" to true,
                    "input" to (result.updatedInput ?: inputMap)
                )
            )
            is PermissionResultDeny -> Json.encodeToJsonElement(
                mapOf(
                    "allow" to false,
                    "message" to result.message,
                    "interrupt" to result.interrupt
                )
            )
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
     */
    private suspend fun sendControlRequestInternal(request: JsonObject): ControlResponse {
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
            return withTimeout(30000) { // 30 seconds timeout
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            throw ControlProtocolException("Control request timeout for $requestId")
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
                                    put("inputSchema", Json.encodeToJsonElement(tool.inputSchema))
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
                
                val arguments = params["arguments"]?.jsonObject?.toMap()?.mapValues { (_, value) ->
                    when (value) {
                        is JsonPrimitive -> value.contentOrNull ?: value.toString()
                        is JsonObject -> value.toMap()
                        is JsonArray -> value.toList()
                        else -> value.toString()
                    }
                } ?: emptyMap()
                
                println("🛠️ 调用新接口工具: $toolName, args: $arguments")
                
                val result = server.callTool(toolName, arguments)
                
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
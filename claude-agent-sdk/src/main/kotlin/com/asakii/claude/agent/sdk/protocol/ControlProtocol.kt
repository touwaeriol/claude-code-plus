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
import com.asakii.logging.*

/**
 * Control protocol handler for managing bidirectional communication with Claude CLI.
 */
class ControlProtocol(
    private val transport: Transport,
    private val options: ClaudeAgentOptions
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
    
    // New McpServer interface instances
    private val newMcpServers = ConcurrentHashMap<String, McpServer>()
    
    // Message routing
    private val _sdkMessages = Channel<Message>(Channel.UNLIMITED)
    val sdkMessages: Flow<Message> = _sdkMessages.receiveAsFlow()
    
    private var messageProcessingJob: Job? = null
    
    // 初始化状态追踪
    private var initialized = false
    private val _initializationResult = CompletableDeferred<JsonObject>()
    
    // Logger
    private val logger = getLogger("ControlProtocol")
    
    // MCP message handler
    private val mcpMessageHandler = McpMessageHandler()
    
    /**
     * Start processing messages from transport.
     */
    fun startMessageProcessing(scope: CoroutineScope) {
        logger.info { "🚀 [ControlProtocol] 开始消息处理任务" }
        messageProcessingJob = scope.launch {
            var messageCount = 0
            try {
                transport.readMessages().collect { jsonElement ->
                    messageCount++
                    try {
                        logger.info { "📥 [ControlProtocol] 从 Transport 收到原始消息 #$messageCount" }
                        routeMessage(jsonElement)
                    } catch (e: Exception) {
                        logger.error { "❌ [ControlProtocol] 处理消息失败: ${e.message}" }
                        e.printStackTrace()
                    }
                }
            } catch (e: CancellationException) {
                logger.info { "ℹ️ [ControlProtocol] 消息处理任务被取消" }
                throw e
            } catch (e: Exception) {
                val errorMessage = e.message ?: e::class.simpleName ?: "Unknown transport error"
                logger.error { "❌ [ControlProtocol] 从 Transport 读取消息失败: $errorMessage" }
                logger.error { "📊 [ControlProtocol] 统计: 共处理 $messageCount 条消息" }
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
                logger.info { "🔒 [ControlProtocol] sdkMessages channel 已关闭" }
            }
        }
    }
    
    /**
     * 注册 MCP 服务器（不发送请求）
     * 必须在 startMessageProcessing() 之前调用！
     * 因为 CLI 启动后会立即发送 mcp_message 请求
     */
    fun registerMcpServers() {
        logger.info { "🔄 注册 MCP 服务器..." }
        logger.debug { "📋 MCP 服务器配置: ${options.mcpServers}" }
        logger.debug { "📋 MCP 服务器数量: ${options.mcpServers.size}" }

        options.mcpServers.forEach { (name, config) ->
            when (config) {
                is McpServer -> {
                    newMcpServers[name] = config
                    logger.info { "📦 注册 MCP 服务器: $name (${config::class.simpleName})" }
                }
                is McpServerConfig -> {
                    logger.debug { "📦 MCP 配置已记录: $name (type=${config.type})" }
                }
                else -> {
                    logger.warn { "⚠️ 未知 MCP 配置类型: $name -> ${config::class.simpleName}" }
                }
            }
        }
        logger.info { "✅ MCP 服务器注册完成: ${newMcpServers.keys}" }
    }

    /**
     * Initialize control protocol - 仿照Python SDK实现
     * This must be called after startMessageProcessing() and before using hooks
     */
    suspend fun initialize(): JsonObject {
        if (initialized) {
            return _initializationResult.await()
        }

        logger.info { "🔄 初始化控制协议..." }

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
                            logger.info { "🎣 注册Hook回调: $callbackId" }
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

        logger.info { "⏱️ [ControlProtocol] Initialize 超时设置: ${initializeTimeout}ms" }

        // 发送初始化请求（与 Python SDK 一致，如果超时会抛出异常）
        val response = sendControlRequestInternal(initRequest, initializeTimeout)
        initialized = true

        val result = response.response?.jsonObject ?: buildJsonObject { put("status", "initialized") }
        _initializationResult.complete(result)

        logger.info { "✅ 控制协议初始化完成" }
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

        logger.info { "🔀 [ControlProtocol] 路由消息: type=$type" }

        // Route messages based on type
        when (type) {
            "system" -> {
                val subtype = jsonObject["subtype"]?.jsonPrimitive?.content
                logger.info { "🔧 [ControlProtocol] 系统消息: subtype=$subtype" }
                when (subtype) {
                    "init" -> {
                        handleSystemInit(jsonElement)
                    }
                    "status" -> {
                        // 状态消息（如 compacting）- 解析并发送到 sdkMessages
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            logger.info { "📊 [ControlProtocol] 状态消息: ${(message as? StatusSystemMessage)?.status}" }
                            _sdkMessages.send(message)
                            logger.info { "✅ [ControlProtocol] 状态消息已发送" }
                        } catch (e: Exception) {
                            logger.warn { "⚠️ [ControlProtocol] 解析状态消息失败: ${e.message}" }
                        }
                    }
                    "compact_boundary" -> {
                        // 压缩边界消息 - 解析并发送到 sdkMessages
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            val compactMsg = message as? CompactBoundaryMessage
                            logger.info { "📦 [ControlProtocol] 压缩边界消息: preTokens=${compactMsg?.compactMetadata?.preTokens}, trigger=${compactMsg?.compactMetadata?.trigger}" }
                            _sdkMessages.send(message)
                            logger.info { "✅ [ControlProtocol] 压缩边界消息已发送" }
                        } catch (e: Exception) {
                            logger.warn { "⚠️ [ControlProtocol] 解析压缩边界消息失败: ${e.message}" }
                        }
                    }
                    else -> {
                        // 其他系统消息（需要有 data 字段）
                        try {
                            val message = messageParser.parseMessage(jsonElement)
                            logger.info { "📤 [ControlProtocol] 发送系统消息到 sdkMessages: ${message::class.simpleName}" }
                            _sdkMessages.send(message)
                            logger.info { "✅ [ControlProtocol] 系统消息已发送" }
                        } catch (e: Exception) {
                            logger.error { "❌ [ControlProtocol] 解析系统消息失败: ${e.message}" }
                            e.printStackTrace()
                        }
                    }
                }
            }
            "control_request" -> {
                logger.info { "🎮 [ControlProtocol] 控制请求消息" }
                val (requestId, request) = messageParser.parseControlRequest(jsonElement)
                handleControlRequest(requestId, request)
            }
            "control_response" -> {
                val response = messageParser.parseControlResponse(jsonElement)
                logger.info { "🎮 [ControlProtocol] 控制响应消息: requestId=${response.requestId}, subtype=${response.subtype}, error=${response.error}" }
                val deferred = pendingRequests.remove(response.requestId)
                if (deferred != null) {
                    deferred.complete(response)
                    logger.info { "✅ [ControlProtocol] 响应已匹配到等待的请求: ${response.requestId}" }
                } else {
                    logger.warn { "⚠️ [ControlProtocol] 未找到匹配的等待请求: ${response.requestId}, pendingRequests=${pendingRequests.keys}" }
                }
            }
            "assistant", "user", "result", "stream_event" -> {
                // Regular SDK messages
                logger.info { "📨 [ControlProtocol] SDK 消息: type=$type" }
                try {
                    val message = messageParser.parseMessage(jsonElement)
                    val messageType = message::class.simpleName
                    logger.info { "📤 [ControlProtocol] 解析成功，准备发送到 sdkMessages: $messageType" }

                    // 记录消息详情
                    when (message) {
                        is ResultMessage -> {
                            logger.info { "🎯 [ControlProtocol] ResultMessage 详情: subtype=${message.subtype}, isError=${message.isError}, sessionId=${message.sessionId}" }
                        }
                        is StreamEvent -> {
                            val eventType = try {
                                message.event.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                            } catch (e: Exception) {
                                "parse_error"
                            }
                            logger.info { "🌊 [ControlProtocol] StreamEvent 详情: eventType=$eventType, sessionId=${message.sessionId}, uuid=${message.uuid}" }
                        }
                        is AssistantMessage -> {
                            logger.info { "🤖 [ControlProtocol] AssistantMessage 详情: model=${message.model}, contentBlocks=${message.content.size}, parentToolUseId=${message.parentToolUseId}" }
                        }
                        is SystemMessage -> {
                            logger.info { "🔧 [ControlProtocol] SystemMessage 详情: subtype=${message.subtype}" }
                        }
                        is UserMessage -> {
                            logger.info { "👤 [ControlProtocol] UserMessage 详情: sessionId=${message.sessionId}, parentToolUseId=${message.parentToolUseId}, isReplay=${message.isReplay}" }
                        }
                        is StatusSystemMessage -> {
                            logger.info { "📊 [ControlProtocol] StatusSystemMessage 详情: status=${message.status}, sessionId=${message.sessionId}" }
                        }
                        is CompactBoundaryMessage -> {
                            logger.info { "📦 [ControlProtocol] CompactBoundaryMessage 详情: preTokens=${message.compactMetadata?.preTokens}, trigger=${message.compactMetadata?.trigger}" }
                        }
                        else -> {
                            logger.info { "📄 [ControlProtocol] 其他消息类型: $messageType" }
                        }
                    }

                    _sdkMessages.send(message)
                    logger.info { "✅ [ControlProtocol] SDK 消息 ($messageType) 已发送到 sdkMessages channel" }
                } catch (e: Exception) {
                    logger.error { "❌ [ControlProtocol] 解析 SDK 消息失败: type=$type, error=${e.message}" }
                    e.printStackTrace()
                }
            }
            else -> {
                logger.warn { "⚠️ [ControlProtocol] 未知消息类型: $type" }
            }
        }
    }
    
    // System init handling
    private val _systemInitReceived = Channel<JsonObject>(1)
    
    /**
     * Handle system initialization message from Claude CLI.
     */
    private suspend fun handleSystemInit(jsonElement: JsonElement) {
        try {
            val jsonObject = jsonElement.jsonObject

            // Extract server information from init message
            val sessionId = jsonObject["session_id"]?.jsonPrimitive?.content ?: "default"
            val cwd = jsonObject["cwd"]?.jsonPrimitive?.content
            val modelId = jsonObject["model"]?.jsonPrimitive?.content
            val permissionMode = jsonObject["permissionMode"]?.jsonPrimitive?.content
            val apiKeySource = jsonObject["apiKeySource"]?.jsonPrimitive?.content

            // Extract tools array
            val tools = jsonObject["tools"]?.jsonArray?.map { it.jsonPrimitive.content }

            // Extract MCP servers
            val mcpServers = jsonObject["mcp_servers"]?.jsonArray?.map { mcpServer ->
                val mcpObj = mcpServer.jsonObject
                CliMcpServerInfo(
                    name = mcpObj["name"]?.jsonPrimitive?.content ?: "",
                    status = mcpObj["status"]?.jsonPrimitive?.content ?: ""
                )
            }

            // 注册hooks（如果提供了的话）
            val hooksConfig = options.hooks?.let { hooks ->
                convertHooksToProtocolFormat(hooks)
            }
            if (hooksConfig != null) {
                // 发送hook注册消息（但这只是可选的，主要依赖动态回调）
                // serverInfo["hooks_registered"] = true
            }

            val serverInfo = buildJsonObject {
                put("session_id", sessionId)
                cwd?.let { put("cwd", it) }
                modelId?.let { put("model", it) }
                permissionMode?.let { put("permissionMode", it) }
                apiKeySource?.let { put("apiKeySource", it) }
                tools?.let {
                    putJsonArray("tools") { it.forEach { tool -> add(tool) } }
                }
                mcpServers?.let { put("mcp_servers", Json.encodeToJsonElement(it)) }
                put("status", "connected")
            }

            // Send to waiting initialize function
            _systemInitReceived.trySend(serverInfo)

            // 🆕 发送 SystemInitMessage 到 sdkMessages，让前端获取真正的 sessionId
            val systemInitMessage = SystemInitMessage(
                sessionId = sessionId,
                cwd = cwd,
                model = modelId,
                permissionMode = permissionMode,
                apiKeySource = apiKeySource,
                tools = tools,
                mcpServers = mcpServers
            )
            logger.info { "📤 [ControlProtocol] 发送 SystemInitMessage 到 sdkMessages: sessionId=$sessionId, model=$modelId" }
            _sdkMessages.send(systemInitMessage)

            logger.info { "System initialization received: $serverInfo" }
            systemInitCallback?.invoke(modelId)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to handle system init: ${e.message}" }
            _systemInitReceived.trySend(
                buildJsonObject {
                    put("status", "error")
                    put("error", e.message ?: "Unknown error")
                }
            )
        }
    }
    
    /**
     * Handle incoming control requests from CLI.
     */
    private suspend fun handleControlRequest(requestId: String, request: ControlRequest) {
        logger.info { "🎯 [handleControlRequest] 收到控制请求: requestId=$requestId, subtype=${request.subtype}, type=${request::class.simpleName}" }
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
        
        val inputObject = request.input as? JsonObject
            ?: throw ControlProtocolException("Hook input must be an object")

        val context = HookContext()
        val result = callback(inputObject, request.toolUseId, context)
        
        return Json.encodeToJsonElement(result)
    }
    
    /**
     * Handle tool permission requests.
     */
    private suspend fun handlePermissionRequest(request: PermissionRequest): JsonElement {
        logger.info { "🔐 [handlePermissionRequest] ==========================================" }
        logger.info { "🔐 [handlePermissionRequest] 收到权限请求: toolName=${request.toolName}, toolUseId=${request.toolUseId}" }
        logger.info { "🔐 [handlePermissionRequest] input keys: ${(request.input as? JsonObject)?.keys}" }
        logger.info { "🔐 [handlePermissionRequest] suggestions count: ${request.permissionSuggestions?.size ?: 0}" }
        logger.info { "🔐 [handlePermissionRequest] canUseTool callback configured: ${options.canUseTool != null}" }

        val canUseTool = options.canUseTool
            ?: throw ControlProtocolException("No permission callback configured")

        // 直接使用 JsonObject 的 Map<String, JsonElement>
        val inputMap: Map<String, JsonElement> = when (val input = request.input) {
            is JsonObject -> input
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
        }
    }
    
    /**
     * Handle SDK MCP server message requests - 增强支持新接口
     */
    private suspend fun handleMcpMessage(request: McpMessageRequest): JsonElement {
        val serverName = request.serverName
        val message = request.message
        
        logger.debug { "📨 处理MCP消息: server=$serverName, method=${message.jsonObject["method"]?.jsonPrimitive?.content}" }
        
        // 检查新接口服务器是否存在
        val server = newMcpServers[serverName]
        val id = message.jsonObject["id"]
        
        if (server == null) {
            return mcpMessageHandler.buildServerNotFoundError(serverName, id)
        }
        
        val method = message.jsonObject["method"]?.jsonPrimitive?.content
        val params = message.jsonObject["params"]?.jsonObject ?: buildJsonObject {}
        
        try {
            return mcpMessageHandler.handleMethod(server, method, params, id)
        } catch (e: Exception) {
            logger.warn(e) { "❌ MCP消息处理失败: ${e.message}" }
            return mcpMessageHandler.buildInternalError(e.message ?: "Internal error", id)
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

        // ✅ 使用 Json.encodeToString 而不是 toString()，避免 BOM 问题
        transport.write(json.encodeToString(responseMessage))
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
        val subtype = request["subtype"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val deferred = CompletableDeferred<ControlResponse>()
        pendingRequests[requestId] = deferred

        val requestMessage = buildJsonObject {
            put("type", "control_request")
            put("request_id", requestId)
            put("request", request)
        }

        try {
            // ✅ 使用 Json.encodeToString 而不是 toString()，避免 BOM 问题
            val jsonStr = json.encodeToString(requestMessage)
            logger.info { "📤 [ControlProtocol] 发送控制请求: requestId=$requestId, subtype=$subtype" }
            logger.debug { "📤 [ControlProtocol] 请求内容: $jsonStr" }
            transport.write(jsonStr)
            logger.info { "⏳ [ControlProtocol] 等待响应: requestId=$requestId, timeout=${timeoutMs}ms" }
            return withTimeout(timeoutMs) {
                deferred.await()
            }
        } catch (e: TimeoutCancellationException) {
            pendingRequests.remove(requestId)
            logger.error { "❌ [ControlProtocol] 控制请求超时: requestId=$requestId, subtype=$subtype, timeout=${timeoutMs}ms" }
            throw ControlProtocolException("Control request timeout for $requestId after ${timeoutMs}ms")
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            logger.error { "❌ [ControlProtocol] 控制请求失败: requestId=$requestId, subtype=$subtype, error=${e.message}" }
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
    suspend fun waitForSystemInit(): JsonObject {
        return withTimeout(30000) { // 30 seconds timeout
            _systemInitReceived.receive()
        }
    }
    
    /**
     * Send interrupt request to CLI.
     */
    suspend fun interrupt() {
        // 手动构建 JSON，避免 kotlinx.serialization 添加 type 字段
        val request = buildJsonObject {
            put("subtype", "interrupt")
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Interrupt failed: ${response.error}")
        }
    }

    /**
     * Send agent_run_to_background request to CLI.
     * This allows the current Task tool (subagent) to continue running in the background
     * without blocking for user input.
     *
     * Note: Bash background support has been removed. For background Bash execution,
     * use the custom Bash tool via JetBrains MCP instead.
     *
     * @param targetId Optional target ID:
     *   - agentId from Task tool result to background a specific agent
     *   - null: Background the latest agent (compatibility mode)
     */
    suspend fun agentRunToBackground(targetId: String? = null) {
        // 使用统一的 run_to_background 端点 (007补丁)
        // CLI 2.1.4+ 不再支持旧的 agent_run_to_background 端点
        val request = buildJsonObject {
            put("subtype", "run_to_background")
            targetId?.let { put("task_id", it) }  // 007补丁使用 task_id 参数
        }
        val targetInfo = targetId ?: "all"
        logger.info { "📤 [ControlProtocol] 发送 run_to_background 请求 (target: $targetInfo)" }

        val response = sendControlRequestInternal(request)
        logger.info { "📥 [ControlProtocol] 收到 run_to_background 响应: subtype=${response.subtype}, error=${response.error}" }

        if (response.subtype == "error") {
            throw ControlProtocolException("Run to background failed: ${response.error}")
        }

        logger.info { "✅ [ControlProtocol] 任务已切换到后台运行 (target: $targetInfo)" }
    }

    /**
     * Send run_to_background request to CLI in batch mode.
     * This will background ALL currently running tasks (bash + agents) at once.
     *
     * This is equivalent to the unified Ctrl+B feature in CLI 2.1.0+, which
     * moves all foreground tasks to background simultaneously.
     *
     * @return AgentsBackgroundResult containing count and list of backgrounded agent IDs
     */
    suspend fun agentsRunAllToBackground(): AgentsBackgroundResult {
        // 使用统一的 run_to_background 端点 (007补丁)
        // 不传 task_id 即为批量模式
        val request = buildJsonObject {
            put("subtype", "run_to_background")
            // 不传 task_id，批量后台化所有任务
        }
        logger.info { "📤 [ControlProtocol] 发送 run_to_background 请求 (批量后台)" }

        val response = sendControlRequestInternal(request)
        logger.info { "📥 [ControlProtocol] 收到 run_to_background 响应: subtype=${response.subtype}" }

        if (response.subtype == "error") {
            throw ControlProtocolException("Run all to background failed: ${response.error}")
        }

        // 解析响应 - 007补丁返回 { success: true, mode: "all" }
        val responseData = response.response?.jsonObject
        val mode = responseData?.get("mode")?.jsonPrimitive?.contentOrNull ?: "all"

        logger.info { "✅ [ControlProtocol] 批量后台完成 (mode: $mode)" }

        // 兼容旧接口，返回空列表（007补丁不返回详细ID列表）
        return AgentsBackgroundResult(0, emptyList())
    }

    /**
     * Send run_to_background request to CLI for a specific Bash task.
     * This allows a running Bash command to continue running in the background
     * without blocking for output.
     *
     * Note: Uses the unified run_to_background endpoint (007 patch) which
     * automatically detects task type and calls the appropriate internal function.
     *
     * @param taskId The tool_use_id of the Bash command to background
     * @return BashBackgroundResult containing success status and taskId
     */
    suspend fun bashRunToBackground(taskId: String): BashBackgroundResult {
        // 使用统一的 run_to_background 端点 (007补丁)
        // CLI 会自动判断任务类型并调用对应的内部函数
        val request = buildJsonObject {
            put("subtype", "run_to_background")
            put("task_id", taskId)
        }
        logger.info { "📤 [ControlProtocol] 发送 run_to_background 请求 (task_id: $taskId)" }

        val response = sendControlRequestInternal(request)
        logger.info { "📥 [ControlProtocol] 收到 run_to_background 响应: subtype=${response.subtype}, error=${response.error}" }

        if (response.subtype == "error") {
            throw ControlProtocolException("Run to background failed: ${response.error}")
        }

        // 解析响应 - 007补丁返回 { success: true, type: "bash", task_id: xxx }
        val responseData = response.response?.jsonObject
        val backgroundTaskId = responseData?.get("task_id")?.jsonPrimitive?.contentOrNull

        logger.info { "✅ [ControlProtocol] Bash 已切换到后台运行 (task_id: $backgroundTaskId)" }

        return BashBackgroundResult(
            success = true,
            taskId = backgroundTaskId,
            command = null  // 007补丁不返回 command 字段
        )
    }

    /**
     * Unified run_to_background request to CLI.
     *
     * This method calls the CLI's internal functions directly:
     * - iV1: Background all tasks (Bash + Agent)
     * - Me5: Background single Bash task
     * - R42: Background single Agent task
     *
     * The CLI automatically detects task type (Bash/Agent) and calls the appropriate function.
     *
     * Behavior:
     * - If taskId is provided: Background that specific task (auto-detect type)
     * - If taskId is null: Background ALL foreground tasks (Bash + Agents)
     *
     * @param taskId Optional task ID to background a specific task
     * @return UnifiedBackgroundResult with details of what was backgrounded
     */
    suspend fun runToBackground(taskId: String? = null): UnifiedBackgroundResult {
        val request = buildJsonObject {
            put("subtype", "run_to_background")
            taskId?.let { put("task_id", it) }
        }
        logger.info { "📤 [ControlProtocol] 发送 run_to_background 请求 (task_id: ${taskId ?: "null - batch mode"})" }

        val response = sendControlRequestInternal(request)
        logger.info { "📥 [ControlProtocol] 收到 run_to_background 响应: subtype=${response.subtype}" }

        if (response.subtype == "error") {
            return UnifiedBackgroundResult(
                success = false,
                error = response.error ?: "Unknown error"
            )
        }

        val responseData = response.response?.jsonObject

        // Check response mode
        val mode = responseData?.get("mode")?.jsonPrimitive?.contentOrNull
        val type = responseData?.get("type")?.jsonPrimitive?.contentOrNull
        val returnedTaskId = responseData?.get("task_id")?.jsonPrimitive?.contentOrNull

        // Handle batch mode (mode == "all")
        if (mode == "all") {
            logger.info { "✅ [ControlProtocol] 批量后台完成 (iV1 called)" }
            return UnifiedBackgroundResult(
                success = true
                // Note: The new CLI patch calls iV1 which handles both Bash and Agent
            )
        }

        // Handle single task mode
        if (taskId != null) {
            when (type) {
                "bash" -> {
                    logger.info { "✅ [ControlProtocol] Bash 已后台化: task_id=$returnedTaskId" }
                    return UnifiedBackgroundResult(
                        success = true,
                        isBash = true,
                        taskId = returnedTaskId,
                        bashCount = 1,
                        backgroundedBashIds = listOfNotNull(returnedTaskId)
                    )
                }
                "agent" -> {
                    logger.info { "✅ [ControlProtocol] Agent 已后台化: task_id=$returnedTaskId" }
                    return UnifiedBackgroundResult(
                        success = true,
                        isBash = false,
                        taskId = returnedTaskId,
                        agentCount = 1,
                        backgroundedAgentIds = listOfNotNull(returnedTaskId)
                    )
                }
                else -> {
                    val error = responseData?.get("error")?.jsonPrimitive?.contentOrNull
                    logger.warn { "⚠️ [ControlProtocol] 未知任务类型: type=$type, error=$error" }
                    return UnifiedBackgroundResult(
                        success = false,
                        taskId = taskId,
                        error = error ?: "Unknown task type"
                    )
                }
            }
        }

        // Default success case
        logger.info { "✅ [ControlProtocol] run_to_background 完成" }
        return UnifiedBackgroundResult(
            success = responseData?.get("success")?.jsonPrimitive?.booleanOrNull ?: true
        )
    }

    /**
     * Query CLI capabilities.
     *
     * Returns runtime capability flags indicating which features are enabled.
     * Currently returns:
     * - backgroundTasksEnabled: Whether background tasks are enabled
     *   (false when CLAUDE_CODE_DISABLE_BACKGROUND_TASKS is set)
     *
     * @return CliCapabilities containing feature flags
     */
    suspend fun getCapabilities(): CliCapabilities {
        val request = buildJsonObject {
            put("subtype", "get_capabilities")
        }
        logger.info { "📤 [ControlProtocol] 发送 get_capabilities 请求" }

        val response = sendControlRequestInternal(request)
        logger.info { "📥 [ControlProtocol] 收到 get_capabilities 响应: subtype=${response.subtype}" }

        if (response.subtype == "error") {
            throw ControlProtocolException("Get capabilities failed: ${response.error}")
        }

        // 解析响应
        val responseData = response.response?.jsonObject
        val capabilities = responseData?.get("capabilities")?.jsonObject
        val backgroundTasksEnabled = capabilities?.get("background_tasks_enabled")?.jsonPrimitive?.booleanOrNull ?: true

        logger.info { "✅ [ControlProtocol] 获取能力: backgroundTasksEnabled=$backgroundTasksEnabled" }

        return CliCapabilities(
            backgroundTasksEnabled = backgroundTasksEnabled
        )
    }

    /**
     * Set max thinking tokens for the current session.
     * This allows dynamic control of thinking mode without reconnecting.
     *
     * @param maxThinkingTokens The maximum thinking tokens to set:
     *   - null: Disable thinking (use default behavior)
     *   - 0: Disable thinking
     *   - positive value: Set the limit (e.g., 8000, 16000)
     */
    suspend fun setMaxThinkingTokens(maxThinkingTokens: Int?) {
        // 手动构建 JSON，避免 kotlinx.serialization 添加 type 字段
        val request = buildJsonObject {
            put("subtype", "set_max_thinking_tokens")
            put("max_thinking_tokens", maxThinkingTokens)
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Set max thinking tokens failed: ${response.error}")
        }
        logger.info { "✅ [ControlProtocol] 设置 maxThinkingTokens = $maxThinkingTokens" }
    }

    /**
     * Set model for the current session.
     * This allows dynamic model switching without reconnecting.
     *
     * @param model The model to use, or "default" to use the default model
     */
    suspend fun setModel(model: String) {
        // 手动构建 JSON，避免 kotlinx.serialization 添加 type 字段
        val request = buildJsonObject {
            put("subtype", "set_model")
            put("model", model)
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Set model failed: ${response.error}")
        }
        logger.info { "✅ [ControlProtocol] 设置 model = $model" }
    }

    /**
     * Set permission mode for the current session.
     */
    suspend fun setPermissionMode(mode: String) {
        val request = buildJsonObject {
            put("subtype", "set_permission_mode")
            put("mode", mode)
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Set permission mode failed: ${response.error}")
        }
        logger.info { "✅ [ControlProtocol] 设置 permissionMode = $mode" }
    }

    /**
     * Get MCP servers status.
     * Returns the status of all connected MCP servers.
     *
     * @return List of MCP server status info
     */
    suspend fun getMcpStatus(): List<McpServerStatusInfo> {
        // 手动构建 JSON，避免 kotlinx.serialization 添加 type 字段
        val request = buildJsonObject {
            put("subtype", "mcp_status")
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Get MCP status failed: ${response.error}")
        }

        val mcpServers = response.response?.jsonObject?.get("mcpServers")?.jsonArray
            ?: return emptyList()

        return mcpServers.map { serverJson ->
            val obj = serverJson.jsonObject
            McpServerStatusInfo(
                name = obj["name"]?.jsonPrimitive?.content ?: "",
                status = obj["status"]?.jsonPrimitive?.content ?: "",
                serverInfo = obj["serverInfo"]
            )
        }
    }

    /**
     * Get Chrome extension status.
     * Returns information about the Chrome extension connection state.
     *
     * Uses the `get_chrome_status` control command which calls internal CLI functions:
     * - installed: a4A() - checks NativeMessagingHost config file
     * - enabled: k1().claudeInChromeDefaultEnabled - user's default preference
     * - connected: MCP server status == "connected"
     * - mcpServerStatus: MCP server type (connected/failed/pending/etc.)
     * - extensionVersion: serverInfo.version when connected
     *
     * @return ChromeStatus with installed, enabled, connected states
     */
    suspend fun getChromeStatus(): ChromeStatus {
        val request = buildJsonObject {
            put("subtype", "get_chrome_status")
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("get_chrome_status failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: throw ControlProtocolException("get_chrome_status returned empty response")

        return ChromeStatus(
            installed = responseObj["installed"]?.jsonPrimitive?.booleanOrNull ?: false,
            enabled = responseObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: false,
            connected = responseObj["connected"]?.jsonPrimitive?.booleanOrNull ?: false,
            mcpServerStatus = responseObj["mcpServerStatus"]?.jsonPrimitive?.contentOrNull,
            extensionVersion = responseObj["extensionVersion"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * Reconnect a specific MCP server.
     * This calls the CLI internal reconnect function (x2A) to re-establish
     * the connection without doing a full server replacement.
     *
     * @param serverName The name of the MCP server to reconnect
     * @return Response with success status, server info and any errors
     */
    suspend fun reconnectMcp(serverName: String): McpReconnectResponse {
        val request = buildJsonObject {
            put("subtype", "mcp_reconnect")
            put("serverName", serverName)  // Official CLI 2.1.19 uses camelCase
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Reconnect MCP failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: return McpReconnectResponse(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = "Empty response"
            )

        return McpReconnectResponse(
            success = responseObj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            serverName = responseObj["server_name"]?.jsonPrimitive?.contentOrNull ?: serverName,
            status = responseObj["status"]?.jsonPrimitive?.contentOrNull,
            toolsCount = responseObj["tools_count"]?.jsonPrimitive?.intOrNull ?: 0,
            error = responseObj["error"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * Get the list of tools for a specific MCP server or all servers.
     * This reads from the CLI's internal tool registry (y.mcp.tools).
     *
     * @param serverName Optional server name to filter tools. If null, returns all tools.
     * @return Response with tool list and count
     */
    suspend fun getMcpTools(serverName: String? = null): McpToolsResponse {
        val request = buildJsonObject {
            put("subtype", "mcp_tools")
            serverName?.let { put("server_name", it) }
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Get MCP tools failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: return McpToolsResponse(
                serverName = serverName,
                tools = emptyList(),
                count = 0
            )

        val toolsArray = responseObj["tools"]?.jsonArray ?: return McpToolsResponse(
            serverName = serverName,
            tools = emptyList(),
            count = 0
        )

        val tools = toolsArray.mapNotNull { element ->
            val toolObj = element.jsonObject
            McpToolInfo(
                name = toolObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                description = toolObj["description"]?.jsonPrimitive?.contentOrNull ?: "",
                inputSchema = toolObj["inputSchema"]
            )
        }

        return McpToolsResponse(
            serverName = responseObj["server_name"]?.jsonPrimitive?.contentOrNull,
            tools = tools,
            count = responseObj["count"]?.jsonPrimitive?.intOrNull ?: tools.size
        )
    }

    /**
     * Disable a specific MCP server.
     * This updates the user's disabledMcpServers configuration and disconnects the server.
     *
     * Official CLI 2.1.19 uses mcp_toggle with enabled=false.
     *
     * @param serverName The name of the MCP server to disable
     * @return Response with success status and server state
     */
    suspend fun disableMcp(serverName: String): McpDisableEnableResponse {
        val request = buildJsonObject {
            put("subtype", "mcp_toggle")
            put("serverName", serverName)  // Official CLI 2.1.19 uses camelCase
            put("enabled", false)
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Disable MCP failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: return McpDisableEnableResponse(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = "Empty response"
            )

        return McpDisableEnableResponse(
            success = responseObj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            serverName = responseObj["server_name"]?.jsonPrimitive?.contentOrNull ?: serverName,
            status = responseObj["status"]?.jsonPrimitive?.contentOrNull,
            toolsCount = responseObj["tools_count"]?.jsonPrimitive?.intOrNull ?: 0,
            error = responseObj["error"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * Enable a specific MCP server.
     * This removes the server from disabledMcpServers and reconnects it.
     *
     * Official CLI 2.1.19 uses mcp_toggle with enabled=true.
     *
     * @param serverName The name of the MCP server to enable
     * @return Response with success status and server state
     */
    suspend fun enableMcp(serverName: String): McpDisableEnableResponse {
        val request = buildJsonObject {
            put("subtype", "mcp_toggle")
            put("serverName", serverName)  // Official CLI 2.1.19 uses camelCase
            put("enabled", true)
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Enable MCP failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: return McpDisableEnableResponse(
                success = false,
                serverName = serverName,
                status = null,
                toolsCount = 0,
                error = "Empty response"
            )

        return McpDisableEnableResponse(
            success = responseObj["success"]?.jsonPrimitive?.booleanOrNull ?: false,
            serverName = responseObj["server_name"]?.jsonPrimitive?.contentOrNull ?: serverName,
            status = responseObj["status"]?.jsonPrimitive?.contentOrNull,
            toolsCount = responseObj["tools_count"]?.jsonPrimitive?.intOrNull ?: 0,
            error = responseObj["error"]?.jsonPrimitive?.contentOrNull
        )
    }

    /**
     * Dynamically set MCP servers for the current session.
     * This allows adding/removing MCP servers without reconnecting.
     *
     * **IMPORTANT: This is a FULL REPLACEMENT, not incremental update!**
     * - Servers in the map will be added or updated
     * - Servers NOT in the map will be REMOVED
     * - To add a server without removing others, first call getMcpStatus() to get
     *   current servers, then include them in the new map
     *
     * @param servers Map of server name to server configuration (replaces all servers)
     * @return Response with added, removed servers and any errors
     */
    suspend fun setMcpServers(servers: Map<String, McpStdioServerDto>): McpSetServersResponse {
        // 手动构建 JSON，避免 kotlinx.serialization 添加 type 字段
        val request = buildJsonObject {
            put("subtype", "mcp_set_servers")
            putJsonObject("servers") {
                servers.forEach { (name, config) ->
                    putJsonObject(name) {
                        put("command", config.command)
                        putJsonArray("args") {
                            config.args.forEach { add(it) }
                        }
                        putJsonObject("env") {
                            config.env.forEach { (k, v) -> put(k, v) }
                        }
                    }
                }
            }
        }
        val response = sendControlRequestInternal(request)

        if (response.subtype == "error") {
            throw ControlProtocolException("Set MCP servers failed: ${response.error}")
        }

        val responseObj = response.response?.jsonObject
            ?: return McpSetServersResponse(emptyList(), emptyList(), emptyMap())

        return McpSetServersResponse(
            added = responseObj["added"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            removed = responseObj["removed"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            errors = responseObj["errors"]?.jsonObject?.entries?.associate {
                it.key to (it.value.jsonPrimitive.contentOrNull ?: "")
            } ?: emptyMap()
        )
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
    
}

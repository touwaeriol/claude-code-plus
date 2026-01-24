package com.asakii.server

import com.asakii.bridge.IdeEvent
import com.asakii.bridge.FrontendRequest
import com.asakii.bridge.FrontendResponse
import com.asakii.rpc.api.IdeTools
import com.asakii.rpc.api.IdeTheme
import com.asakii.rpc.api.DiffRequest
import com.asakii.rpc.api.EditOperation
import com.asakii.rpc.api.JetBrainsApi
import com.asakii.rpc.api.JetBrainsCapabilities
import com.asakii.rpc.api.DefaultJetBrainsApi
import com.asakii.rpc.api.JetBrainsSessionState
import com.asakii.rpc.api.JetBrainsSessionCommand

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.engine.*
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
// import org.jetbrains.kotlinx.rpc.krpc.ktor.server.Krpc // Temporarily removed
import com.asakii.rpc.proto.GetHistoryMetadataRequest
import com.asakii.rpc.proto.LoadHistoryRequest
import com.asakii.server.history.HistoryJsonlLoader
import com.asakii.server.rpc.AiAgentRpcServiceImpl
import com.asakii.rpc.api.RpcHistorySessionsResult
import com.asakii.server.mcp.McpHttpGateway
import com.asakii.server.rpc.ClientCallerRegistry
import com.asakii.server.mcp.McpProviders
import com.asakii.server.rsocket.ProtoConverter.toProto
import com.asakii.server.codex.CodexBackendProvider
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.io.IOException
import com.asakii.logging.*
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds



/**
 * HTTP + SSE 服务器（基于 Ktor）
 * 提供前后端通信 API
 *
 * 架构：
 * - HTTP: 静态资源 + REST API
 * - SSE: 实时事件推送（主题变化、Claude 消息等）
 */
private val logger = getLogger("HttpApiServer")

class HttpApiServer(
    private val ideTools: IdeTools,
    private val scope: CoroutineScope,
    private val frontendDir: Path? = null,  // 开发模式下可以为 null
    private val jetbrainsApi: JetBrainsApi = DefaultJetBrainsApi,  // 默认不支持 JetBrains 集成
    private val jetbrainsRSocketHandler: JetBrainsRSocketHandlerProvider? = null,  // JetBrains RSocket 处理器
    private val mcpHttpGateway: McpHttpGateway? = null,  // 项目级 MCP HTTP 网关（仅 Codex 模式使用）
    private val mcpProviders: McpProviders = McpProviders.DEFAULT,  // All MCP Server Providers
    private val serviceConfigProvider: () -> com.asakii.server.config.AiAgentServiceConfig = { com.asakii.server.config.AiAgentServiceConfig() },  // 服务配置提供者（每次 connect 时调用获取最新配置）
    private val codexBackendProvider: CodexBackendProvider? = null  // Codex 后端提供者（可选）
) : com.asakii.bridge.EventBridge {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "type"  // ✅ 显式设置 type 作为多态判别器
    }

    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    private var baseUrl: String? = null

    // SSE 事件流
    private val _eventFlow = MutableSharedFlow<IdeEvent>(
        replay = 0,
        extraBufferCapacity = 100,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val eventFlow = _eventFlow.asSharedFlow()

    // Codex 历史辅助类
    private val codexHistoryHelper = CodexHistoryHelper(ideTools, serviceConfigProvider, scope)

    companion object {
        private const val DEFAULT_HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 8765
    }

    /**
     * 启动服务器
     * @param preferredPort 外部指定端口（可选，null 则使用随机端口）
     * @return 服务器 URL
     */
    fun start(preferredPort: Int? = null): String {
        val configuredPort = preferredPort
            ?: System.getenv("CLAUDE_HTTP_PORT")?.toIntOrNull()
            ?: 0  // 使用 0 让操作系统自动分配端口

        // 启动 Ktor 服务器 (使用 Netty 引擎)
        server = embeddedServer(Netty, port = configuredPort, host = DEFAULT_HOST) {
            // 重新启用 ContentNegotiation
            install(ContentNegotiation) {
                json(json)
            }

            install(CORS) {
                anyHost()
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
            }

            install(SSE)

            install(WebSockets) {
                pingPeriod = 15.seconds
                timeout = 15.seconds
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }

            // RSocket 支持
            install(RSocketSupport)

            // 路由配置
            routing {
                val serverPort = configuredPort

                // RSocket RPC 路由 (Protobuf over RSocket)
                // 重要：每个连接创建完全独立的 handler，绝不共享任何状态！
                rSocket("rsocket") {
                    // 生成唯一 connectId（带碰撞检测）
                    var connectId = java.util.UUID.randomUUID().toString()
                    while (ClientCallerRegistry.contains(connectId)) {
                        connectId = java.util.UUID.randomUUID().toString()
                    }
                    logger.info { "🔌 [RSocket] 新连接: connectId=$connectId" }

                    // 每次连接时调用 provider 获取最新配置（支持用户实时更新设置）
                    val currentConfig = serviceConfigProvider()

                    // 直接在构造时传入 requester，确保每个连接使用独立的 requester
                    val rsocketHandler = com.asakii.server.rsocket.RSocketHandler(
                        ideTools = ideTools,
                        clientRequester = requester,
                        connectId = connectId,
                        mcpHttpGateway = mcpHttpGateway,
                        mcpProviders = mcpProviders,
                        serviceConfigProvider = { currentConfig }
                    )

                    rsocketHandler.createHandler()
                }

                // JetBrains IDE 集成 RSocket 端点
                if (jetbrainsRSocketHandler != null) {
                    rSocket("jetbrains-rsocket") {
                        val clientId = java.util.UUID.randomUUID().toString()
                        logger.info { "🔌 [JetBrains RSocket] 客户端连接: $clientId" }
                        jetbrainsRSocketHandler.setClientRequester(clientId, requester)

                        // 连接关闭时清理
                        requester.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion {
                            logger.info { "🔌 [JetBrains RSocket] 客户端断开: $clientId" }
                            jetbrainsRSocketHandler.removeClient(clientId)
                        }

                        jetbrainsRSocketHandler.createHandler()
                    }
                }

                // Codex 事件流 WebSocket 端点
                if (codexBackendProvider != null) {
                    webSocket("/codex-events") {
                        val clientId = java.util.UUID.randomUUID().toString()
                        logger.info { "🔌 [Codex WebSocket] 客户端连接: $clientId" }

                        try {
                            // 启动事件监听协程
                            val eventJob = scope.launch {
                                codexBackendProvider.events.collect { event ->
                                    try {
                                        val eventJson = when (event) {
                                            is CodexBackendProvider.CodexEvent.ThreadCreated -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("thread_created"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("thread", json.encodeToJsonElement(event.thread))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.ThreadResumed -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("thread_resumed"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("thread", json.encodeToJsonElement(event.thread))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.ThreadArchived -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("thread_archived"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.TurnStarted -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("turn_started"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    put("turn", json.encodeToJsonElement(event.turn))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.TurnCompleted -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("turn_completed"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    put("turn", json.encodeToJsonElement(event.turn))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.TurnInterrupted -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("turn_interrupted"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.TurnError -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("turn_error"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    put("error", JsonPrimitive(event.error))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.ItemStarted -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("item_started"))
                                                    put("item", json.encodeToJsonElement(event.item))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.ItemCompleted -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("item_completed"))
                                                    put("item", json.encodeToJsonElement(event.item))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.StreamingContent -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("streaming_content"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("itemId", JsonPrimitive(event.itemId))
                                                    put("contentType", JsonPrimitive(event.contentType))
                                                    put("content", JsonPrimitive(event.content))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.CommandApprovalRequired -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("command_approval_required"))
                                                    put("requestId", JsonPrimitive(event.requestId))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    event.command?.let { put("command", JsonPrimitive(it)) }
                                                    event.cwd?.let { put("cwd", JsonPrimitive(it)) }
                                                    event.reason?.let { put("reason", JsonPrimitive(it)) }
                                                    event.proposedExecpolicyAmendment?.let { amendment ->
                                                        put("proposedExecpolicyAmendment", json.encodeToJsonElement(amendment))
                                                    }
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.FileChangeApprovalRequired -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("file_change_approval_required"))
                                                    put("requestId", JsonPrimitive(event.requestId))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    put("changes", json.encodeToJsonElement(event.changes))
                                                    event.reason?.let { put("reason", JsonPrimitive(it)) }
                                                    event.grantRoot?.let { put("grantRoot", JsonPrimitive(it)) }
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.TokenUsage -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("token_usage"))
                                                    put("threadId", JsonPrimitive(event.threadId))
                                                    put("turnId", JsonPrimitive(event.turnId))
                                                    put("usage", json.encodeToJsonElement(event.usage))
                                                }
                                            }
                                            is CodexBackendProvider.CodexEvent.Error -> {
                                                buildJsonObject {
                                                    put("type", JsonPrimitive("error"))
                                                    put("message", JsonPrimitive(event.message))
                                                }
                                            }
                                        }

                                        send(io.ktor.websocket.Frame.Text(eventJson.toString()))
                                    } catch (e: Exception) {
                                        logger.error(e) { "❌ [Codex WebSocket] Failed to send event" }
                                    }
                                }
                            }

                            // 等待连接关闭
                            for (frame in incoming) {
                                // 接收客户端消息（暂时不处理，仅保持连接）
                                if (frame is io.ktor.websocket.Frame.Text) {
                                    val text = frame.data.decodeToString()
                                    logger.debug { "📨 [Codex WebSocket] Received message: $text" }
                                }
                            }

                            eventJob.cancel()
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [Codex WebSocket] Connection error" }
                        } finally {
                            logger.info { "🔌 [Codex WebSocket] 客户端断开: $clientId" }
                        }
                    }
                }

                // RESTful API 路由
                route("/api") {
                    // 通用 RPC 端点（用于前端测试连接和通用调用）
                    post("/") {
                        try {
                            // ✅ 使用 Ktor 的自动反序列化而不是手工正则表达式
                            val request = call.receive<FrontendRequest>()
                            val action = request.action
                            logger.info { "📥 Received request: action=$action" }

                            when (action) {
                                "test.ping" -> {
                                    call.respondText("""{"success":true,"message":"pong"}""", ContentType.Application.Json)
                                }
                                "ide.getProjectPath" -> {
                                    val projectPath = ideTools.getProjectPath()
                                    val response = FrontendResponse(
                                        success = true,
                                        data = mapOf("projectPath" to JsonPrimitive(projectPath))
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                // 注：ide.openFile, ide.showDiff, ide.getLocale, ide.setLocale
                                // 已迁移到 RSocket (/jetbrains-rsocket)
                                "ide.searchFiles" -> {
                                    // ✅ 直接从反序列化对象获取数据
                                    val dataObj = request.data?.jsonObject
                                    val query = dataObj?.get("query")?.jsonPrimitive?.contentOrNull ?: ""
                                    val maxResults = dataObj?.get("maxResults")?.jsonPrimitive?.intOrNull ?: 20

                                    val result = ideTools.searchFiles(query, maxResults)
                                    val response = result.fold(
                                        onSuccess = { files ->
                                            val filePaths = files.map { it.path }
                                            // 前端期望data字段包含文件路径数组
                                            FrontendResponse(success = true, data = mapOf("files" to JsonPrimitive(json.encodeToString(filePaths))))
                                        },
                                        onFailure = { FrontendResponse(success = false, error = it.message) }
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "ide.getFileContent" -> {
                                    // ✅ 直接从反序列化对象获取数据
                                    val dataObj = request.data?.jsonObject
                                    val filePath = dataObj?.get("filePath")?.jsonPrimitive?.contentOrNull ?: ""
                                    val lineStart = dataObj?.get("lineStart")?.jsonPrimitive?.intOrNull
                                    val lineEnd = dataObj?.get("lineEnd")?.jsonPrimitive?.intOrNull

                                    val result = ideTools.getFileContent(filePath, lineStart, lineEnd)
                                    val response = result.fold(
                                        onSuccess = { content ->
                                            FrontendResponse(success = true, data = mapOf("content" to JsonPrimitive(content)))
                                        },
                                        onFailure = { FrontendResponse(success = false, error = it.message) }
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "node.detect" -> {
                                    val result = ideTools.detectNode()
                                    val response = FrontendResponse(
                                        success = result.found,
                                        data = mapOf(
                                            "found" to JsonPrimitive(result.found),
                                            "path" to JsonPrimitive(result.path ?: ""),
                                            "version" to JsonPrimitive(result.version ?: ""),
                                            "error" to JsonPrimitive(result.error ?: "")
                                        )
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "ide.getActiveFile" -> {
                                    val activeFile = ideTools.getActiveEditorFile()
                                    val response = if (activeFile != null) {
                                        FrontendResponse(
                                            success = true,
                                            data = mapOf(
                                                "path" to JsonPrimitive(activeFile.path),
                                                "relativePath" to JsonPrimitive(activeFile.relativePath),
                                                "name" to JsonPrimitive(activeFile.name),
                                                "line" to JsonPrimitive(activeFile.line ?: 0),
                                                "column" to JsonPrimitive(activeFile.column ?: 0),
                                                "hasSelection" to JsonPrimitive(activeFile.hasSelection),
                                                "startLine" to JsonPrimitive(activeFile.startLine ?: 0),
                                                "startColumn" to JsonPrimitive(activeFile.startColumn ?: 0),
                                                "endLine" to JsonPrimitive(activeFile.endLine ?: 0),
                                                "endColumn" to JsonPrimitive(activeFile.endColumn ?: 0)
                                            )
                                        )
                                    } else {
                                        FrontendResponse(success = true, data = null)
                                    }
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "ide.hasIdeEnvironment" -> {
                                    val hasIde = ideTools.hasIdeEnvironment()
                                    val response = FrontendResponse(
                                        success = true,
                                        data = mapOf("hasIde" to JsonPrimitive(hasIde))
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "file.getOriginalContent" -> {
                                    // 获取缓存的原始文件内容（用于显示 Edit Diff）
                                    val dataObj = request.data?.jsonObject
                                    val toolUseId = dataObj?.get("toolUseId")?.jsonPrimitive?.contentOrNull
                                    if (toolUseId == null) {
                                        val response = FrontendResponse(success = false, error = "Missing toolUseId")
                                        call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                    } else {
                                        val originalContent = com.asakii.server.services.FileContentCache.getOriginalContent(toolUseId)
                                        val response = FrontendResponse(
                                            success = true,
                                            data = mapOf(
                                                "found" to JsonPrimitive(originalContent != null),
                                                "content" to JsonPrimitive(originalContent ?: "")
                                            )
                                        )
                                        call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                    }
                                }
                                "ide.openUrl" -> {
                                    val dataObj = request.data?.jsonObject
                                    val url = dataObj?.get("url")?.jsonPrimitive?.contentOrNull ?: ""
                                    val result = ideTools.openUrl(url)
                                    val response = result.fold(
                                        onSuccess = { FrontendResponse(success = true) },
                                        onFailure = { FrontendResponse(success = false, error = it.message) }
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                // 注：ide.getFileHistoryContent 已迁移到 RSocket 实现
                                // 参见 JetBrainsRSocketHandler.handleGetFileHistoryContent
                                "settings.getDefault" -> {
                                    // 获取默认配置（浏览器模式下使用，IDE 模式下使用 RSocket）
                                    val config = serviceConfigProvider()
                                    val response = FrontendResponse(
                                        success = true,
                                        data = mapOf(
                                            "defaultBackendType" to JsonPrimitive(config.defaultProvider.name.lowercase()),
                                            "claudeDefaultModelId" to JsonPrimitive(config.defaultModel ?: ""),
                                            "codexDefaultModelId" to JsonPrimitive(config.codex.defaultModelId ?: "gpt-5.2-codex"),
                                            "codexReasoningEffort" to JsonPrimitive(config.codex.defaultReasoningEffort ?: "medium"),
                                            "codexReasoningSummary" to JsonPrimitive(config.codex.defaultReasoningSummary ?: "auto"),
                                            "codexSandboxMode" to JsonPrimitive(config.codex.sandboxMode ?: "workspace-write"),
                                            "claudeDefaultAutoCleanupContexts" to JsonPrimitive(config.claude.defaultAutoCleanupContexts),
                                            "codexDefaultAutoCleanupContexts" to JsonPrimitive(config.codex.defaultAutoCleanupContexts),
                                            "defaultBypassPermissions" to JsonPrimitive(config.claude.dangerouslySkipPermissions),
                                            "includePartialMessages" to JsonPrimitive(config.claude.includePartialMessages),
                                            "defaultThinkingLevel" to JsonPrimitive(config.claude.defaultThinkingLevel),
                                            "defaultThinkingTokens" to JsonPrimitive(config.claude.defaultThinkingTokens)
                                        )
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "models.getAvailable" -> {
                                    // 获取可用模型列表（内置模型 + 自定义模型）
                                    val config = serviceConfigProvider()

                                    // 内置模型列表
                                    val builtInClaudeModels = listOf(
                                        mapOf(
                                            "displayName" to JsonPrimitive("Opus 4.5"),
                                            "modelId" to JsonPrimitive("claude-opus-4-5-20251101"),
                                            "isBuiltIn" to JsonPrimitive(true)
                                        ),
                                        mapOf(
                                            "displayName" to JsonPrimitive("Sonnet 4.5"),
                                            "modelId" to JsonPrimitive("claude-sonnet-4-5-20250929"),
                                            "isBuiltIn" to JsonPrimitive(true)
                                        ),
                                        mapOf(
                                            "displayName" to JsonPrimitive("Haiku 4.5"),
                                            "modelId" to JsonPrimitive("claude-haiku-4-5-20251001"),
                                            "isBuiltIn" to JsonPrimitive(true)
                                        )
                                    )

                                    // 自定义模型列表
                                    val claudeCustomModels = config.customModels.map { model ->
                                        mapOf(
                                            "displayName" to JsonPrimitive(model.displayName),
                                            "modelId" to JsonPrimitive(model.modelId),
                                            "isBuiltIn" to JsonPrimitive(false)
                                        )
                                    }

                                    val builtInCodexModels = listOf(
                                        mapOf(
                                            "displayName" to JsonPrimitive("GPT-5.1-Codex-Max"),
                                            "modelId" to JsonPrimitive("gpt-5.1-codex-max"),
                                            "isBuiltIn" to JsonPrimitive(true),
                                            "supportsThinking" to JsonPrimitive(true)
                                        ),
                                        mapOf(
                                            "displayName" to JsonPrimitive("GPT-5.2-Codex"),
                                            "modelId" to JsonPrimitive("gpt-5.2-codex"),
                                            "isBuiltIn" to JsonPrimitive(true),
                                            "supportsThinking" to JsonPrimitive(true)
                                        ),
                                        mapOf(
                                            "displayName" to JsonPrimitive("GPT-5.2"),
                                            "modelId" to JsonPrimitive("gpt-5.2"),
                                            "isBuiltIn" to JsonPrimitive(true),
                                            "supportsThinking" to JsonPrimitive(true)
                                        )
                                    )

                                    val codexCustomModels = config.codexCustomModels.map { model ->
                                        mapOf(
                                            "displayName" to JsonPrimitive(model.displayName),
                                            "modelId" to JsonPrimitive(model.modelId),
                                            "isBuiltIn" to JsonPrimitive(false),
                                            "supportsThinking" to JsonPrimitive(true)
                                        )
                                    }

                                    val response = FrontendResponse(
                                        success = true,
                                        data = mapOf(
                                            "claudeModels" to JsonArray((builtInClaudeModels + claudeCustomModels).map { JsonObject(it) }),
                                            "codexModels" to JsonArray((builtInCodexModels + codexCustomModels).map { JsonObject(it) }),
                                            "defaultBackendType" to JsonPrimitive(config.defaultProvider.name.lowercase()),
                                            "defaultClaudeModelId" to JsonPrimitive(config.defaultModel),
                                            "defaultCodexModelId" to JsonPrimitive(config.codex.defaultModelId ?: "gpt-5.2-codex")
                                        )
                                    )
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                else -> {
                                    call.respondText(
                                        """{"success":false,"error":"Unknown action: $action"}""",
                                        ContentType.Application.Json,
                                        HttpStatusCode.BadRequest
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            logger.error { "❌ RPC call failed: ${e.message}" }
                            e.printStackTrace()
                            call.respondText(
                                """{"success":false,"error":"${e.message?.replace("\"", "\\\"") ?: "Unknown error"}"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }

                    // JetBrains IDE 集成 API
                    route("/jetbrains") {
                        // 能力检测端点
                        get("/capabilities") {
                            val capabilities = jetbrainsApi.capabilities.get()
                            call.respondText(
                                """{"supported":${capabilities.supported},"version":"${capabilities.version}"}""",
                                ContentType.Application.Json
                            )
                        }
                    }

                    // 文件搜索 API
                    route("/files") {
                        // 搜索文件（query 为空时返回项目根目录文件）
                        get("/search") {
                            try {
                                val query = call.request.queryParameters["query"] ?: ""
                                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 10
                                val projectPath = ideTools.getProjectPath()

                                val fileInfos = if (query.isEmpty()) {
                                    // 空查询：返回项目根目录文件
                                    val projectDir = java.io.File(projectPath)
                                    projectDir.listFiles()
                                        ?.filter { it.isFile }
                                        ?.sortedByDescending { it.lastModified() }
                                        ?.take(maxResults)
                                        ?.map { file ->
                                            IndexedFileInfo(
                                                name = file.name,
                                                relativePath = file.name,
                                                absolutePath = file.absolutePath,
                                                fileType = file.extension.ifEmpty { "unknown" },
                                                size = file.length(),
                                                lastModified = file.lastModified()
                                            )
                                        } ?: emptyList()
                                } else {
                                    // 有查询：调用 ideTools 搜索
                                    val result = ideTools.searchFiles(query, maxResults)
                                    val files = result.getOrElse { emptyList() }
                                    files.map { fileInfo ->
                                        val file = java.io.File(fileInfo.path)
                                        val relativePath = if (fileInfo.path.startsWith(projectPath)) {
                                            fileInfo.path.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                                        } else {
                                            fileInfo.path
                                        }
                                        IndexedFileInfo(
                                            name = file.name,
                                            relativePath = relativePath,
                                            absolutePath = fileInfo.path,
                                            fileType = file.extension.ifEmpty { "unknown" },
                                            size = if (file.exists()) file.length() else 0L,
                                            lastModified = if (file.exists()) file.lastModified() else 0L
                                        )
                                    }
                                }
                                call.respond(FileSearchResponse(success = true, data = fileInfos))
                            } catch (e: Exception) {
                                // 检查是否是索引中异常（通过异常类名或消息判断）
                                val isIndexingError = e::class.simpleName == "IndexingInProgressException" ||
                                        e.message?.contains("indexing", ignoreCase = true) == true

                                if (isIndexingError) {
                                    logger.info { "⏳ Project is indexing, file search unavailable" }
                                    call.respond(
                                        FileSearchResponse(
                                            success = false,
                                            error = "Project is indexing, please wait",
                                            errorCode = "INDEXING"
                                        )
                                    )
                                } else {
                                    logger.error { "❌ Failed to search files: ${e.message}" }
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        FileSearchResponse(success = false, error = e.message ?: "Unknown error")
                                    )
                                }
                            }
                        }
                    }

                    // 配置 API
                    route("/config") {
                        get {
                            // TODO: 实现配置获取
                            call.respond(mapOf("model" to "claude-sonnet-4-5-20250929"))
                        }

                        put {
                            // TODO: 实现配置保存
                            call.respond(mapOf("success" to true))
                        }
                    }

                    // 历史会话列表 API (HTTP 接口，避免 RSocket 连接)
                    get("/history/sessions") {
                        try {
                            val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                            val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 30
                            val provider = call.request.queryParameters["provider"]?.lowercase()?.trim()

                            logger.info { "📋 [HTTP] 获取历史会话列表 (offset=$offset, maxResults=$maxResults)" }

                            // 直接调用 RPC 服务实现（复用逻辑，传递 MCP Server Providers）
                            // HTTP API 使用临时 connectId（不参与 MCP 路由）
                            val rpcService = com.asakii.server.rpc.AiAgentRpcServiceImpl(
                                ideTools = ideTools,
                                clientCaller = null,
                                mcpProviders = mcpProviders,
                                connectId = "http-api-${java.util.UUID.randomUUID()}"
                            )
                            val result = if (provider == "codex") {
                                codexHistoryHelper.listHistorySessions(maxResults, offset)
                            } else {
                                rpcService.getHistorySessions(maxResults, offset)
                            }

                            call.respond(HttpStatusCode.OK, result)
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [HTTP] 获取历史会话失败" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 删除历史会话 API
                    delete("/history/sessions/{sessionId}") {
                        try {
                            val sessionId = call.parameters["sessionId"]
                                ?: return@delete call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("success" to false, "error" to "Missing sessionId")
                                )
                            val provider = call.request.queryParameters["provider"]?.lowercase()?.trim()

                            logger.info { "🗑️ [HTTP] 删除历史会话: $sessionId" }

                            val deleted = if (provider == "codex") {
                                codexHistoryHelper.archiveSession(sessionId)
                            } else {
                                val projectPath = ideTools.getProjectPath()
                                com.asakii.claude.agent.sdk.utils.ClaudeSessionScanner.deleteSession(projectPath, sessionId)
                            }

                            if (deleted) {
                                call.respond(HttpStatusCode.OK, mapOf("success" to true))
                            } else {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("success" to false, "error" to "Session not found or delete failed")
                                )
                            }
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [HTTP] 删除历史会话失败" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 历史元数据（protobuf，HTTP 直读 JSONL）
                    post("/history/metadata.pb") {
                        try {
                            val provider = call.request.queryParameters["provider"]?.lowercase()?.trim()
                            val body = call.receive<ByteArray>()
                            val req = GetHistoryMetadataRequest.parseFrom(body)
                            val sessionId = req.sessionId
                            val projectPath = req.projectPath

                            val meta = if (provider == "codex") {
                                val threadId = sessionId.takeIf { it.isNotBlank() }
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing sessionId")
                                    )
                                codexHistoryHelper.getHistoryMetadata(threadId, projectPath.takeIf { it.isNotBlank() }).toProto()
                            } else {
                                val rpcService = AiAgentRpcServiceImpl(
                                    ideTools = ideTools,
                                    clientCaller = null,
                                    mcpProviders = mcpProviders,
                                    connectId = "http-api-${java.util.UUID.randomUUID()}"
                                )
                                rpcService.getHistoryMetadata(sessionId, projectPath).toProto()
                            }

                            call.respondBytes(
                                bytes = meta.toByteArray(),
                                contentType = ContentType.Application.OctetStream
                            )
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [HTTP] 获取历史元数据失败" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 历史内容加载（protobuf，HTTP 直读 JSONL）
                    post("/history/load.pb") {
                        try {
                            val provider = call.request.queryParameters["provider"]?.lowercase()?.trim()
                            val body = call.receive<ByteArray>()
                            val req = LoadHistoryRequest.parseFrom(body)
                            val result = if (provider == "codex") {
                                val threadId = req.sessionId.takeIf { it.isNotBlank() }
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing sessionId")
                                    )
                                codexHistoryHelper.loadHistory(
                                    threadId = threadId,
                                    fallbackProjectPath = req.projectPath.takeIf { it.isNotBlank() },
                                    offset = req.offset,
                                    limit = req.limit
                                ).toProto()
                            } else {
                                val rpcService = AiAgentRpcServiceImpl(
                                    ideTools = ideTools,
                                    clientCaller = null,
                                    mcpProviders = mcpProviders,
                                    connectId = "http-api-${java.util.UUID.randomUUID()}"
                                )
                                rpcService.loadHistory(
                                    req.sessionId,
                                    req.projectPath,
                                    req.offset,
                                    req.limit
                                ).toProto()
                            }

                            call.respondBytes(
                                bytes = result.toByteArray(),
                                contentType = ContentType.Application.OctetStream
                            )
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [HTTP] 加载历史失败" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 字体下载 API
                    get("/font/{fontFamily}") {
                        try {
                            val fontFamily = call.parameters["fontFamily"]
                                ?: return@get call.respond(
                                    HttpStatusCode.BadRequest,
                                    mapOf("error" to "Missing fontFamily parameter")
                                )

                            logger.info { "🔤 [Font] Requesting font: $fontFamily" }

                            val fontData = ideTools.getFontData(fontFamily)
                            if (fontData != null) {
                                logger.info { "✅ [Font] Found font: ${fontData.fontFamily} (${fontData.data.size} bytes)" }
                                call.response.headers.append(
                                    HttpHeaders.ContentDisposition,
                                    "attachment; filename=\"${fontFamily}.ttf\""
                                )
                                call.respondBytes(
                                    bytes = fontData.data,
                                    contentType = ContentType.parse(fontData.mimeType)
                                )
                            } else {
                                logger.info { "⚠️ [Font] Font not found: $fontFamily" }
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("error" to "Font not found: $fontFamily")
                                )
                            }
                        } catch (e: Exception) {
                            logger.error { "❌ [Font] Failed to get font: ${e.message}" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 主题 API
                    get("/theme") {
                        val theme = ideTools.getTheme()
                        call.respond(theme)
                    }

                    // 主题 API（显式 current 路径，便于前端区分）
                    get("/theme/current") {
                        val theme = ideTools.getTheme()
                        call.respond(mapOf("theme" to theme))
                    }

                    // 项目路径 API
                    get("/project-path") {
                        val projectPath = ideTools.getProjectPath()
                        call.respond(mapOf("projectPath" to projectPath))
                    }

                    // Codex 后端 API
                    route("/codex") {
                        // Thread 管理
                        post("/thread/start") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@post
                                }

                                val requestBody = call.receive<JsonObject>()
                                val model = requestBody["model"]?.jsonPrimitive?.contentOrNull
                                val cwd = requestBody["cwd"]?.jsonPrimitive?.contentOrNull
                                val approvalPolicy = requestBody["approvalPolicy"]?.jsonPrimitive?.contentOrNull
                                val sandbox = requestBody["sandbox"]?.jsonPrimitive?.contentOrNull

                                val config = CodexBackendProvider.ThreadConfig(
                                    model = model,
                                    cwd = cwd,
                                    approvalPolicy = approvalPolicy,
                                    sandbox = sandbox
                                )

                                val threadId = codexBackendProvider.createThread(config)
                                call.respond(mapOf("success" to true, "threadId" to threadId))
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to start thread" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        post("/thread/resume") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@post
                                }

                                val requestBody = call.receive<JsonObject>()
                                val threadId = requestBody["threadId"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing threadId")
                                    )

                                codexBackendProvider.resumeThread(threadId)
                                call.respond(mapOf("success" to true))
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to resume thread" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        post("/thread/archive") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@post
                                }

                                val requestBody = call.receive<JsonObject>()
                                val threadId = requestBody["threadId"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing threadId")
                                    )

                                codexBackendProvider.archiveThread(threadId)
                                call.respond(mapOf("success" to true))
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to archive thread" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // Turn 管理
                        post("/turn/start") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@post
                                }

                                val requestBody = call.receive<JsonObject>()
                                val threadId = requestBody["threadId"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing threadId")
                                    )
                                val input = requestBody["input"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing input")
                                    )

                                val turnId = codexBackendProvider.startTurn(threadId, input)
                                call.respond(mapOf("success" to true, "turnId" to turnId))
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to start turn" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        post("/turn/interrupt") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@post
                                }

                                val requestBody = call.receive<JsonObject>()
                                val threadId = requestBody["threadId"]?.jsonPrimitive?.content
                                    ?: return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing threadId")
                                    )

                                codexBackendProvider.interruptTurn(threadId)
                                call.respond(mapOf("success" to true))
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to interrupt turn" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 配置管理
                        route("/config") {
                            get {
                                try {
                                    if (codexBackendProvider == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            mapOf("success" to false, "error" to "Codex backend not available")
                                        )
                                        return@get
                                    }

                                    // 返回 Codex 配置信息
                                    call.respond(
                                        mapOf(
                                            "success" to true,
                                            "available" to true,
                                            "version" to "1.0.0"
                                        )
                                    )
                                } catch (e: Exception) {
                                    logger.error(e) { "❌ [Codex] Failed to get config" }
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                    )
                                }
                            }

                            put {
                                try {
                                    if (codexBackendProvider == null) {
                                        call.respond(
                                            HttpStatusCode.ServiceUnavailable,
                                            mapOf("success" to false, "error" to "Codex backend not available")
                                        )
                                        return@put
                                    }

                                    // 目前配置更新通过启动时传入，暂不支持运行时更新
                                    call.respond(
                                        mapOf("success" to true, "message" to "Config update not supported at runtime")
                                    )
                                } catch (e: Exception) {
                                    logger.error(e) { "❌ [Codex] Failed to update config" }
                                    call.respond(
                                        HttpStatusCode.InternalServerError,
                                        mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                    )
                                }
                            }
                        }

                        // Thread 状态查询
                        get("/thread/{threadId}/state") {
                            try {
                                if (codexBackendProvider == null) {
                                    call.respond(
                                        HttpStatusCode.ServiceUnavailable,
                                        mapOf("success" to false, "error" to "Codex backend not available")
                                    )
                                    return@get
                                }

                                val threadId = call.parameters["threadId"]
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("success" to false, "error" to "Missing threadId")
                                    )

                                val state = codexBackendProvider.getThreadState(threadId)
                                if (state != null) {
                                    call.respond(
                                        mapOf(
                                            "success" to true,
                                            "state" to mapOf(
                                                "threadId" to state.threadId,
                                                "isActive" to state.isActive,
                                                "currentTurnId" to state.currentTurnId,
                                                "config" to mapOf(
                                                    "model" to state.config.model,
                                                    "cwd" to state.config.cwd,
                                                    "approvalPolicy" to state.config.approvalPolicy,
                                                    "sandbox" to state.config.sandbox
                                                )
                                            )
                                        )
                                    )
                                } else {
                                    call.respond(
                                        HttpStatusCode.NotFound,
                                        mapOf("success" to false, "error" to "Thread not found")
                                    )
                                }
                            } catch (e: Exception) {
                                logger.error(e) { "❌ [Codex] Failed to get thread state" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }
                    }

                    // 后端可用性检测
                    get("/backend/available") {
                        try {
                            val backends = mutableMapOf<String, Boolean>()

                            // Claude 后端始终可用（通过 RSocket）
                            backends["claude"] = true

                            // Codex 后端根据 provider 是否存在判断
                            backends["codex"] = codexBackendProvider != null

                            call.respond(
                                mapOf(
                                    "success" to true,
                                    "backends" to backends,
                                    "defaultBackend" to "claude"
                                )
                            )
                        } catch (e: Exception) {
                            logger.error(e) { "❌ Failed to check backend availability" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 临时图片上传 API
                    route("/temp-image") {
                        // 上传图片
                        post("/upload") {
                            try {
                                val multipart = call.receiveMultipart()
                                var fileBytes: ByteArray? = null
                                var originalFilename: String? = null

                                // 遍历 multipart 的所有部分
                                multipart.forEachPart { part ->
                                    when (part) {
                                        is PartData.FileItem -> {
                                            originalFilename = part.originalFileName ?: "image.png"
                                            // 读取文件内容到 ByteArray
                                            val channel = part.provider()
                                            fileBytes = channel.toInputStream().readBytes()
                                        }
                                        else -> {}
                                    }
                                    part.dispose()
                                }

                                if (fileBytes == null || originalFilename == null) {
                                    return@post call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "No image file provided")
                                    )
                                }

                                // 保存图片到临时目录
                                val absolutePath = com.asakii.server.services.TempImageService.saveImage(
                                    fileBytes!!,
                                    originalFilename!!
                                )

                                call.respond(mapOf(
                                    "success" to true,
                                    "path" to absolutePath,
                                    "filename" to java.io.File(absolutePath).name
                                ))
                            } catch (e: Exception) {
                                logger.error { "❌ Failed to upload image: ${e.message}" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 读取临时图片
                        get("/{filename}") {
                            try {
                                val filename = call.parameters["filename"]
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing filename")
                                    )

                                val imageFile = com.asakii.server.services.TempImageService.getImage(filename)

                                if (imageFile == null) {
                                    return@get call.respond(
                                        HttpStatusCode.NotFound,
                                        mapOf("error" to "Image not found")
                                    )
                                }

                                // 检测 MIME 类型
                                val mimeType = when (imageFile.extension.lowercase()) {
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "gif" -> "image/gif"
                                    "bmp" -> "image/bmp"
                                    "webp" -> "image/webp"
                                    "svg" -> "image/svg+xml"
                                    else -> "application/octet-stream"
                                }

                                call.respondFile(imageFile)
                                call.response.headers.append(HttpHeaders.ContentType, mimeType)
                            } catch (e: Exception) {
                                logger.error { "❌ Failed to read image: ${e.message}" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }
                    }
                }



                // SSE 事件流
                sse("/events") {
                    logger.info { "🔌 SSE client connected: ${call.request.local.remoteHost}" }

                    try {
                        // 发送初始主题
                        val initialThemePayload = buildJsonObject {
                            put("theme", json.encodeToJsonElement(ideTools.getTheme()))
                        }
                        send(io.ktor.sse.ServerSentEvent(
                            data = initialThemePayload.toString(),
                            event = "theme.changed",
                            id = System.currentTimeMillis().toString()
                        ))

                        // 订阅事件流
                        eventFlow.collect { event ->
                            val payload = event.data ?: emptyMap()
                            send(io.ktor.sse.ServerSentEvent(
                                data = json.encodeToString(payload),
                                event = event.type,
                                id = System.currentTimeMillis().toString()
                            ))
                        }
                    } catch (e: Exception) {
                        logger.warn { "⚠️ SSE connection closed: ${e.message}" }
                    } finally {
                        logger.info { "🔌 SSE client disconnected" }
                    }
                }

                // 健康检查
                get("/health") {
                    call.respondText("""{"status":"ok","port":$serverPort}""", ContentType.Application.Json)
                }

                // 动态处理 index.html，根据 URL 参数注入环境变量（仅在生产模式下）
                if (frontendDir != null) {
                    get("/") {
                        val indexFile = frontendDir.resolve("index.html").toFile()
                        if (indexFile.exists()) {
                            var html = indexFile.readText()

                            // 检查是否来自 IDEA 插件（通过 URL 参数 ?ide=true）
                            val isIdeMode = call.request.queryParameters["ide"] == "true"

                            if (isIdeMode) {
                                // IDEA 插件模式：标记环境 __IDEA_MODE__ = true
                                // 前端会检测此标记并通过 RSocket 与后端通信
                                val injection = """
                                    <script>
                                        window.__IDEA_MODE__ = true;
                                        console.log('✅ Environment: IDEA Plugin Mode');
                                    </script>
                                """.trimIndent()
                                html = html.replace("</head>", "$injection\n</head>")
                            } else {
                                // 浏览器模式：不注入（前端会使用默认值）
                                val injection = """
                                    <script>
                                        console.log('✅ Environment: Browser Mode');
                                        console.log('🔗 Using default server URL');
                                    </script>
                                """.trimIndent()
                                html = html.replace("</head>", "$injection\n</head>")
                            }

                            call.respondText(html, ContentType.Text.Html)
                        } else {
                            call.respondText("index.html not found", ContentType.Text.Plain, HttpStatusCode.NotFound)
                        }
                    }

                    // 静态资源 - 放在最后以避免拦截 API 请求
                    staticFiles("/", frontendDir.toFile())
                } else {
                    // 开发模式：返回提示信息
                    get("/") {
                        call.respondText(
                            """
                            <!DOCTYPE html>
                            <html>
                            <head>
                                <title>Claude Code Plus - Dev Mode</title>
                            </head>
                            <body>
                                <h1>🔧 Development Mode</h1>
                                <p>Backend server is running on port $serverPort</p>
                                <p>Please start the frontend development server separately:</p>
                                <pre>cd frontend && npm run dev</pre>
                                <p>WebSocket endpoint: ws://localhost:$serverPort/ws</p>
                                <p>API endpoint: http://localhost:$serverPort/api/</p>
                            </body>
                            </html>
                            """.trimIndent(),
                            ContentType.Text.Html
                        )
                    }
                }
            }
        }.start(wait = false)

        // 获取实际分配的端口
        val actualPort = runBlocking {
            server!!.engine.resolvedConnectors().first().port
        }

        val url = "http://$DEFAULT_HOST:$actualPort"
        baseUrl = url
        logger.info { "🚀 Ktor server started at: $url (configured: $configuredPort, actual: $actualPort)" }
        return url
    }

    // Stop server.
    fun stop() {
        try {
            server?.stop(1000, 2000)
            logger.info { "🛑 Server stopped" }
        } catch (e: Exception) {
            logger.error { "❌ Failed to stop server: ${e.message}" }
        }
    }


    /**
     * 推送事件给所有前端客户端（通过 SSE）
     */
    override fun pushEvent(event: IdeEvent) {
        _eventFlow.tryEmit(event)
        logger.info { "📤 Pushed event: ${event.type}" }
    }
}

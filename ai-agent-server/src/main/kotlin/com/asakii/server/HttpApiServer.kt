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
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.server.mcp.DefaultJetBrainsMcpServerProvider
import com.asakii.server.rsocket.ProtoConverter.toProto
import io.rsocket.kotlin.ktor.server.RSocketSupport
import io.rsocket.kotlin.ktor.server.rSocket
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.Serializable
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.io.IOException
import mu.KotlinLogging
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds



/**
 * 前端期望的文件信息格式
 * 用于 /api/files/search 和 /api/files/recent 端点
 */
@Serializable
data class IndexedFileInfo(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val fileType: String,
    val size: Long,
    val lastModified: Long
)

/**
 * 文件搜索 API 响应
 */
@Serializable
data class FileSearchResponse(
    val success: Boolean,
    val data: List<IndexedFileInfo>? = null,
    val error: String? = null
)

/**
 * HTTP + SSE 服务器（基于 Ktor）
 * 提供前后端通信 API
 *
 * 架构：
 * - HTTP: 静态资源 + REST API
 * - SSE: 实时事件推送（主题变化、Claude 消息等）
 */
private val logger = KotlinLogging.logger {}

/**
 * JetBrains RSocket Handler 接口
 * 由插件模块实现，用于处理 JetBrains IDE 集成的 RSocket 调用
 */
interface JetBrainsRSocketHandlerProvider {
    /**
     * 创建 RSocket 请求处理器
     */
    fun createHandler(): io.rsocket.kotlin.RSocket

    /**
     * 设置客户端 requester（用于反向调用）
     */
    fun setClientRequester(clientId: String, requester: io.rsocket.kotlin.RSocket)

    /**
     * 移除客户端
     */
    fun removeClient(clientId: String)
}

class HttpApiServer(
    private val ideTools: IdeTools,
    private val scope: CoroutineScope,
    private val frontendDir: Path? = null,  // 开发模式下可以为 null
    private val jetbrainsApi: JetBrainsApi = DefaultJetBrainsApi,  // 默认不支持 JetBrains 集成
    private val jetbrainsRSocketHandler: JetBrainsRSocketHandlerProvider? = null,  // JetBrains RSocket 处理器
    private val jetBrainsMcpServerProvider: JetBrainsMcpServerProvider = DefaultJetBrainsMcpServerProvider  // JetBrains MCP Server Provider
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
                    val connectionId = java.util.UUID.randomUUID().toString()
                    logger.info { "🔌 [RSocket] 新连接: $connectionId" }

                    // 直接在构造时传入 requester，确保每个连接使用独立的 requester
                    val rsocketHandler = com.asakii.server.rsocket.RSocketHandler(
                        ideTools = ideTools,
                        clientRequester = requester,
                        connectionId = connectionId,
                        jetBrainsMcpServerProvider = jetBrainsMcpServerProvider
                    )

                    // 监听连接关闭
                    requester.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                        logger.info { "🔌 [RSocket] 连接断开: $connectionId (cause: ${cause?.message ?: "正常关闭"})" }
                    }

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

                // RESTful API 路由
                route("/api") {
                    // 通用 RPC 端点（用于前端测试连接和通用调用）
                    post("/") {
                        try {
                            val requestBody = call.receiveText()
                            logger.info { "📥 Received request: $requestBody" }

                            // 简单解析 JSON (避免序列化问题)
                            val actionMatch = """"action"\s*:\s*"([^"]+)"""".toRegex().find(requestBody)
                            val action = actionMatch?.groupValues?.get(1) ?: ""

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
                                    // 解析请求数据
                                    val dataMatch = """"data"\s*:\s*\{([^}]+)\}""".toRegex().find(requestBody)
                                    val queryMatch = """"query"\s*:\s*"([^"]+)"""".toRegex().find(dataMatch?.value ?: "")
                                    val maxResultsMatch = """"maxResults"\s*:\s*(\d+)""".toRegex().find(dataMatch?.value ?: "")

                                    val query = queryMatch?.groupValues?.get(1) ?: ""
                                    val maxResults = maxResultsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 20

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
                                    // 解析请求数据
                                    val dataMatch = """"data"\s*:\s*\{([^}]+)\}""".toRegex().find(requestBody)
                                    val filePathMatch = """"filePath"\s*:\s*"([^"]+)"""".toRegex().find(dataMatch?.value ?: "")
                                    val lineStartMatch = """"lineStart"\s*:\s*(\d+)""".toRegex().find(dataMatch?.value ?: "")
                                    val lineEndMatch = """"lineEnd"\s*:\s*(\d+)""".toRegex().find(dataMatch?.value ?: "")

                                    val filePath = filePathMatch?.groupValues?.get(1) ?: ""
                                    val lineStart = lineStartMatch?.groupValues?.get(1)?.toIntOrNull()
                                    val lineEnd = lineEndMatch?.groupValues?.get(1)?.toIntOrNull()

                                    val result = ideTools.getFileContent(filePath, lineStart, lineEnd)
                                    val response = result.fold(
                                        onSuccess = { content ->
                                            FrontendResponse(success = true, data = mapOf("content" to JsonPrimitive(content)))
                                        },
                                        onFailure = { FrontendResponse(success = false, error = it.message) }
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
                                logger.error { "❌ Failed to search files: ${e.message}" }
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    FileSearchResponse(success = false, error = e.message ?: "Unknown error")
                                )
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

                            logger.info { "📋 [HTTP] 获取历史会话列表 (offset=$offset, maxResults=$maxResults)" }

                            // 直接调用 RPC 服务实现（复用逻辑，传递 JetBrains MCP Server Provider）
                            val rpcService = com.asakii.server.rpc.AiAgentRpcServiceImpl(
                                ideTools = ideTools,
                                clientCaller = null,
                                jetBrainsMcpServerProvider = jetBrainsMcpServerProvider
                            )
                            val result = rpcService.getHistorySessions(maxResults, offset)

                            call.respond(HttpStatusCode.OK, result)
                        } catch (e: Exception) {
                            logger.error(e) { "❌ [HTTP] 获取历史会话失败" }
                            call.respond(
                                HttpStatusCode.InternalServerError,
                                mapOf("error" to (e.message ?: "Unknown error"))
                            )
                        }
                    }

                    // 历史元数据（protobuf，HTTP 直读 JSONL）
                    post("/history/metadata.pb") {
                        try {
                            val body = call.receive<ByteArray>()
                            val req = GetHistoryMetadataRequest.parseFrom(body)
                            val sessionId = req.sessionId
                            val projectPath = req.projectPath

                            val rpcService = AiAgentRpcServiceImpl(
                                ideTools = ideTools,
                                clientCaller = null,
                                jetBrainsMcpServerProvider = jetBrainsMcpServerProvider
                            )
                            val meta = rpcService.getHistoryMetadata(sessionId, projectPath).toProto()

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
                            val body = call.receive<ByteArray>()
                            val req = LoadHistoryRequest.parseFrom(body)
                            val rpcService = AiAgentRpcServiceImpl(
                                ideTools = ideTools,
                                clientCaller = null,
                                jetBrainsMcpServerProvider = jetBrainsMcpServerProvider
                            )
                            val result = rpcService.loadHistory(
                                req.sessionId,
                                req.projectPath,
                                req.offset,
                                req.limit
                            ).toProto()

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

    /**
     * 停止服务器
     */
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

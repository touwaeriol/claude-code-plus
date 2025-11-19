package com.claudecodeplus.server

import com.claudecodeplus.bridge.IdeEvent
import com.claudecodeplus.bridge.IdeTheme

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
import io.ktor.utils.io.*
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
import java.awt.Color
import java.io.IOException
import java.net.BindException
import java.net.ServerSocket
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds



/**
 * HTTP + SSE 服务器（基于 Ktor）
 * 提供前后端通信 API
 *
 * 架构：
 * - HTTP: 静态资源 + REST API
 * - SSE: 实时事件推送（主题变化、Claude 消息等）
 */
class HttpApiServer(
    private val ideActionBridge: IdeActionBridge,
    private val scope: CoroutineScope,
    private val frontendDir: Path
) : com.claudecodeplus.bridge.EventBridge {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        classDiscriminator = "type"  // ✅ 显式设置 type 作为多态判别器
    }

    private var server: EmbeddedServer<*, *>? = null
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
     * @param preferredPort 外部指定端口（可选）
     * @return 服务器 URL
     */
    fun start(preferredPort: Int? = null): String {
        val configuredPort = preferredPort
            ?: System.getenv("CLAUDE_HTTP_PORT")?.toIntOrNull()
            ?: DEFAULT_PORT

        val portInUse = try {
            startServerOn(configuredPort)
        } catch (e: Exception) {
            if (e is BindException) {
                val fallbackPort = findAvailablePort()
                logger.warning("⚠️ Port $configuredPort is busy, falling back to $fallbackPort")
                startServerOn(fallbackPort)
            } else {
                throw e
            }
        }

        val url = "http://$DEFAULT_HOST:$portInUse"
        baseUrl = url
        logger.info("🚀 Ktor server started at: $url")
        return url
    }

    private fun startServerOn(port: Int): Int {
        // 启动 Ktor 服务器 (使用 Netty 引擎)
        server = embeddedServer(Netty, port = port, host = DEFAULT_HOST) {
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

            // install(Krpc) // Temporarily disabled due to version incompatibility

            // 路由配置
            routing {
                val serverPort = port

                // WebSocket RPC 路由 (新架构)
                val wsHandler = WebSocketHandler(ideActionBridge)
                with(wsHandler) {
                    configureWebSocket()
                }

                // RESTful API 路由
                route("/api") {
                    // 通用 RPC 端点（用于前端测试连接和通用调用）
                    post("/") {
                        try {
                            val requestBody = call.receiveText()
                            logger.info("📥 Received request: $requestBody")

                            // 简单解析 JSON (避免序列化问题)
                            val actionMatch = """"action"\s*:\s*"([^"]+)"""".toRegex().find(requestBody)
                            val action = actionMatch?.groupValues?.get(1) ?: ""

                            when (action) {
                                "test.ping" -> {
                                    call.respondText("""{"success":true,"message":"pong"}""", ContentType.Application.Json)
                                }
                                "ide.getTheme" -> {
                                    // 返回默认主题配置
                                    call.respondText(
                                        """{"success":true,"data":{"isDark":false,"background":"#ffffff","foreground":"#24292e","panelBackground":"#f6f8fa"}}""",
                                        ContentType.Application.Json
                                    )
                                }
                                "ide.getProjectPath" -> {
                                    // 返回项目路径
                                    val projectPath = System.getProperty("user.dir")
                                    call.respondText(
                                        """{"success":true,"data":"${projectPath.replace("\\", "\\\\")}"}""",
                                        ContentType.Application.Json
                                    )
                                }
                                "ide.openFile" -> {
                                    // 解析请求数据
                                    val request = json.decodeFromString<FrontendRequest>(requestBody)
                                    val response = ideActionBridge.openFile(request)
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "ide.showDiff" -> {
                                    // 解析请求数据
                                    val request = json.decodeFromString<FrontendRequest>(requestBody)
                                    val response = ideActionBridge.showDiff(request)
                                    call.respondText(json.encodeToString(response), ContentType.Application.Json)
                                }
                                "ide.searchFiles" -> {
                                    // 解析请求数据
                                    val dataMatch = """"data"\s*:\s*\{([^}]+)\}""".toRegex().find(requestBody)
                                    val queryMatch = """"query"\s*:\s*"([^"]+)"""".toRegex().find(dataMatch?.value ?: "")
                                    val maxResultsMatch = """"maxResults"\s*:\s*(\d+)""".toRegex().find(dataMatch?.value ?: "")

                                    val query = queryMatch?.groupValues?.get(1) ?: ""
                                    val maxResults = maxResultsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 20

                                    val results = ideActionBridge.searchFiles(query, maxResults)
                                    call.respondText(
                                        """{"success":true,"data":${json.encodeToString(results)}}""",
                                        ContentType.Application.Json
                                    )
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

                                    // 读取文件内容
                                    val file = java.io.File(filePath)
                                    if (!file.exists()) {
                                        call.respondText(
                                            """{"success":false,"error":"File not found: $filePath"}""",
                                            ContentType.Application.Json,
                                            HttpStatusCode.NotFound
                                        )
                                    } else {
                                        val lines = file.readLines()
                                        val content = if (lineStart != null && lineEnd != null) {
                                            lines.subList(
                                                (lineStart - 1).coerceAtLeast(0),
                                                lineEnd.coerceAtMost(lines.size)
                                            ).joinToString("\n")
                                        } else {
                                            lines.joinToString("\n")
                                        }
                                        call.respondText(
                                            """{"success":true,"data":"${content.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}"}""",
                                            ContentType.Application.Json
                                        )
                                    }
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
                            logger.severe("❌ RPC call failed: ${e.message}")
                            e.printStackTrace()
                            call.respondText(
                                """{"success":false,"error":"${e.message?.replace("\"", "\\\"") ?: "Unknown error"}"}""",
                                ContentType.Application.Json,
                                HttpStatusCode.InternalServerError
                            )
                        }
                    }

                    // 文件搜索 API
                    route("/files") {
                        // 搜索文件
                        get("/search") {
                            try {
                                val query = call.request.queryParameters["query"] ?: ""
                                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 10

                                val results = ideActionBridge.searchFiles(query, maxResults)

                                call.respond(mapOf("success" to true, "data" to results))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to search files: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 获取最近打开的文件
                        get("/recent") {
                            try {
                                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 10

                                val results = ideActionBridge.getRecentFiles(maxResults)

                                call.respond(mapOf("success" to true, "data" to results))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to get recent files: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("success" to false, "error" to (e.message ?: "Unknown error"))
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

                    // 主题 API
                    get("/theme") {
                        val theme = ideActionBridge.getTheme()
                        call.respond(theme)
                    }

                    // 项目路径 API
                    get("/project-path") {
                        val projectPath = ideActionBridge.getProjectPath()
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
                                val absolutePath = com.claudecodeplus.server.services.TempImageService.saveImage(
                                    fileBytes!!,
                                    originalFilename!!
                                )

                                call.respond(mapOf(
                                    "success" to true,
                                    "path" to absolutePath,
                                    "filename" to java.io.File(absolutePath).name
                                ))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to upload image: ${e.message}")
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

                                val imageFile = com.claudecodeplus.server.services.TempImageService.getImage(filename)

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
                                logger.severe("❌ Failed to read image: ${e.message}")
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
                    logger.info("🔌 SSE client connected: ${call.request.local.remoteHost}")

                    try {
                        // 发送初始主题
                        val theme = ideActionBridge.getTheme()
                        send(io.ktor.sse.ServerSentEvent(
                            data = json.encodeToString(theme),
                            event = "theme",
                            id = System.currentTimeMillis().toString()
                        ))

                        // 订阅事件流
                        eventFlow.collect { event ->
                            send(io.ktor.sse.ServerSentEvent(
                                data = json.encodeToString(event.data ?: mapOf<String, Any>()),
                                event = event.type,
                                id = System.currentTimeMillis().toString()
                            ))
                        }
                    } catch (e: Exception) {
                        logger.warning("⚠️ SSE connection closed: ${e.message}")
                    } finally {
                        logger.info("🔌 SSE client disconnected")
                    }
                }

                // 健康检查
                get("/health") {
                    call.respondText("""{"status":"ok","port":$serverPort}""", ContentType.Application.Json)
                }

                // 动态处理 index.html，根据 URL 参数注入环境变量
                get("/") {
                    val indexFile = frontendDir.resolve("index.html").toFile()
                    if (indexFile.exists()) {
                        var html = indexFile.readText()

                        // 检查是否来自 IDEA 插件（通过 URL 参数 ?ide=true）
                        val isIdeMode = call.request.queryParameters["ide"] == "true"

                        if (isIdeMode) {
                            // IDEA 插件模式：注入 window.__serverUrl
                            val injection = """
                                <script>
                                    window.__serverUrl = 'http://localhost:$serverPort';
                                    console.log('✅ Environment: IDEA Plugin Mode');
                                    console.log('🔗 Server URL:', window.__serverUrl);
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
            }
        }.start(wait = false)

        return port
    }

    /**
     * 停止服务器
     */
    fun stop() {
        try {
            server?.stop(1000, 2000)
            logger.info("🛑 Server stopped")
        } catch (e: Exception) {
            logger.severe("❌ Failed to stop server: ${e.message}")
        }
    }


    /**
     * 推送事件给所有前端客户端（通过 SSE）
     */
    override fun pushEvent(event: IdeEvent) {
        _eventFlow.tryEmit(event)
        logger.info("📤 Pushed event: ${event.type}")
    }







    /**
     * 查找可用端口（系统自动分配）
     * 使用 ServerSocket(0) 让操作系统自动分配一个可用的随机端口
     */
    private fun findAvailablePort(): Int {
        ServerSocket(0).use { socket ->
            return socket.localPort
        }
    }
}


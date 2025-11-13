package com.claudecodeplus.server

import com.claudecodeplus.bridge.ClaudeActionHandler
import com.claudecodeplus.bridge.FrontendRequest
import com.claudecodeplus.bridge.FrontendResponse
import com.claudecodeplus.bridge.IdeEvent
import com.claudecodeplus.bridge.IdeTheme
import com.claudecodeplus.bridge.SessionActionHandler
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import java.awt.Color
import java.io.IOException
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
    private val project: Project,
    private val scope: CoroutineScope,
    private val frontendDir: Path
) {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
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

    // 请求处理器
    private lateinit var claudeHandler: ClaudeActionHandler
    private lateinit var sessionHandler: SessionActionHandler

    /**
     * 启动服务器
     * @return 服务器 URL (随机端口)
     */
    fun start(): String {
        val port = findAvailablePort()

        // 初始化处理器
        initHandlers()

        // 启动 Ktor 服务器
        server = embeddedServer(CIO, port = port, host = "127.0.0.1") {
            // 安装插件
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

            // 路由配置
            routing {
                // 静态资源
                staticFiles("/", frontendDir.toFile()) {
                    default("index.html")
                }

                // WebSocket 路由
                val wsHandler = WebSocketHandler(project)
                with(wsHandler) {
                    configureWebSocket()
                }

                // RESTful API 路由
                route("/api") {
                    // 会话管理 API
                    route("/sessions") {
                        // 列出所有会话
                        get {
                            try {
                                val sessions = sessionHandler.listSessions()
                                call.respond(mapOf("sessions" to sessions))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to list sessions: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 创建新会话
                        post {
                            try {
                                val body = call.receiveNullable<Map<String, String>>()
                                val name = body?.get("name")
                                val session = sessionHandler.createSession(name)
                                call.respond(session)
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to create session: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 获取会话历史
                        get("/{sessionId}/history") {
                            try {
                                val sessionId = call.parameters["sessionId"]
                                    ?: return@get call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing sessionId")
                                    )

                                val messages = sessionHandler.getHistory(sessionId)
                                call.respond(mapOf("messages" to messages))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to get history: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 删除会话
                        delete("/{sessionId}") {
                            try {
                                val sessionId = call.parameters["sessionId"]
                                    ?: return@delete call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing sessionId")
                                    )

                                sessionHandler.deleteSession(sessionId)
                                call.respond(mapOf("success" to true))
                            } catch (e: IllegalArgumentException) {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("error" to e.message)
                                )
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to delete session: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }

                        // 重命名会话
                        patch("/{sessionId}") {
                            try {
                                val sessionId = call.parameters["sessionId"]
                                    ?: return@patch call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing sessionId")
                                    )

                                val body = call.receive<Map<String, String>>()
                                val newName = body["name"]
                                    ?: return@patch call.respond(
                                        HttpStatusCode.BadRequest,
                                        mapOf("error" to "Missing name")
                                    )

                                sessionHandler.renameSession(sessionId, newName)
                                call.respond(mapOf("success" to true))
                            } catch (e: IllegalArgumentException) {
                                call.respond(
                                    HttpStatusCode.NotFound,
                                    mapOf("error" to e.message)
                                )
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to rename session: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    mapOf("error" to (e.message ?: "Unknown error"))
                                )
                            }
                        }
                    }

                    // 文件搜索 API
                    route("/files") {
                        // 搜索文件
                        get("/search") {
                            try {
                                val query = call.request.queryParameters["query"] ?: ""
                                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 10

                                val fileIndexService = project.getService(com.claudecodeplus.plugin.adapters.SimpleFileIndexService::class.java)
                                val results = fileIndexService.searchFiles(query, maxResults)

                                call.respond(FrontendResponse.success(results))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to search files: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    FrontendResponse.error<List<Any>>(e.message ?: "Unknown error")
                                )
                            }
                        }

                        // 获取最近打开的文件
                        get("/recent") {
                            try {
                                val maxResults = call.request.queryParameters["maxResults"]?.toIntOrNull() ?: 10

                                val fileIndexService = project.getService(com.claudecodeplus.plugin.adapters.SimpleFileIndexService::class.java)
                                val results = fileIndexService.getRecentFiles(maxResults)

                                call.respond(FrontendResponse.success(results))
                            } catch (e: Exception) {
                                logger.severe("❌ Failed to get recent files: ${e.message}")
                                call.respond(
                                    HttpStatusCode.InternalServerError,
                                    FrontendResponse.error<List<Any>>(e.message ?: "Unknown error")
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
                        val theme = extractIdeTheme()
                        call.respond(theme)
                    }

                    // 项目路径 API
                    get("/project-path") {
                        val projectPath = project.basePath ?: project.projectFilePath ?: "未知"
                        call.respond(mapOf("projectPath" to projectPath))
                    }
                }

                // 兼容旧的统一 API（保留一段时间以支持旧前端）
                post("/api/") {
                    try {
                        val request = call.receive<FrontendRequest>()
                        logger.info("📨 Legacy API Request: ${request.action}")

                        val response = handleRequest(request)
                        call.respond(response)
                    } catch (e: Exception) {
                        logger.severe("❌ API error: ${e.message}")
                        e.printStackTrace()
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            FrontendResponse(
                                success = false,
                                error = e.message ?: "Unknown error"
                            )
                        )
                    }
                }

                // SSE 事件流
                sse("/events") {
                    logger.info("🔌 SSE client connected: ${call.request.local.remoteHost}")

                    try {
                        // 发送初始主题
                        val theme = extractIdeTheme()
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
                    call.respond(mapOf("status" to "ok", "port" to port))
                }
            }
        }.start(wait = false)

        // 监听主题变化
        setupThemeListener()

        val url = "http://127.0.0.1:$port"
        baseUrl = url
        logger.info("🚀 Ktor server started at: $url")
        return url
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
     * 初始化处理器
     */
    private fun initHandlers() {
        // 使用 EventBridge 接口
        val eventBridge = object : com.claudecodeplus.bridge.EventBridge {
            override fun pushEvent(event: IdeEvent) {
                this@HttpApiServer.pushEvent(event)
            }
        }

        claudeHandler = ClaudeActionHandler(project, eventBridge, scope)
        sessionHandler = SessionActionHandler(project)

        // 设置处理器关联
        claudeHandler.sessionHandler = sessionHandler
        sessionHandler.claudeHandler = claudeHandler
    }

    /**
     * 处理业务请求
     */
    private fun handleRequest(request: FrontendRequest): FrontendResponse {
        logger.info("Processing action: ${request.action}")

        return when {
            request.action.startsWith("test.") -> handleTestAction(request)
            request.action.startsWith("ide.") -> handleIdeAction(request)
            request.action.startsWith("claude.") -> claudeHandler.handle(request)
            request.action.startsWith("session.") -> sessionHandler.handle(request)
            else -> FrontendResponse(false, error = "Unknown action: ${request.action}")
        }
    }

    /**
     * 处理测试操作
     */
    private fun handleTestAction(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "test.ping" -> {
                FrontendResponse(
                    success = true,
                    data = mapOf(
                        "pong" to kotlinx.serialization.json.JsonPrimitive(true),
                        "timestamp" to kotlinx.serialization.json.JsonPrimitive(System.currentTimeMillis())
                    )
                )
            }
            else -> FrontendResponse(false, error = "Unknown test action")
        }
    }

    /**
     * 处理 IDE 操作
     */
    private fun handleIdeAction(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "ide.getTheme" -> {
                val theme = extractIdeTheme()
                val themeJsonString = json.encodeToString(theme)
                val themeJson = json.parseToJsonElement(themeJsonString)
                FrontendResponse(
                    success = true,
                    data = mapOf("theme" to themeJson)
                )
            }
            "ide.getProjectPath" -> {
                val projectPath = project.basePath ?: project.projectFilePath ?: "未知"
                FrontendResponse(
                    success = true,
                    data = mapOf("projectPath" to JsonPrimitive(projectPath))
                )
            }
            "ide.openFile" -> handleOpenFile(request)
            "ide.showDiff" -> handleShowDiff(request)
            else -> FrontendResponse(false, error = "Unknown IDE action: ${request.action}")
        }
    }

    /**
     * 打开文件
     *
     * 增强功能：
     * - 支持行号、列号定位
     * - 支持内容选择（selectContent + content）
     * - 支持选择范围（selectionStart + selectionEnd）
     */
    private fun handleOpenFile(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing filePath")
        val line = data["line"]?.toString()?.trim('"')?.toIntOrNull()
        val column = data["column"]?.toString()?.trim('"')?.toIntOrNull()
        val selectContent = data["selectContent"]?.toString()?.trim('"')?.toBoolean() ?: false
        val content = data["content"]?.toString()?.trim('"')
        val selectionStart = data["selectionStart"]?.toString()?.trim('"')?.toIntOrNull()
        val selectionEnd = data["selectionEnd"]?.toString()?.trim('"')?.toIntOrNull()

        return try {
            ApplicationManager.getApplication().invokeLater {
                val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)

                if (file != null) {
                    val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    fileEditorManager.openFile(file, true)

                    val editor = fileEditorManager.selectedTextEditor
                    if (editor != null) {
                        when {
                            // 优先级1：使用指定的选择范围
                            selectionStart != null && selectionEnd != null -> {
                                val start = selectionStart.coerceIn(0, editor.document.textLength)
                                val end = selectionEnd.coerceIn(start, editor.document.textLength)
                                editor.selectionModel.setSelection(start, end)
                                editor.caretModel.moveToOffset(start)
                                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                                logger.info("✅ Selected range [$start, $end] in $filePath")
                            }
                            // 优先级2：选择指定内容
                            selectContent && content != null && content.isNotEmpty() -> {
                                val text = editor.document.text
                                val index = text.indexOf(content)
                                if (index >= 0) {
                                    val start = index
                                    val end = index + content.length
                                    editor.selectionModel.setSelection(start, end)
                                    editor.caretModel.moveToOffset(start)
                                    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                                    logger.info("✅ Selected content in $filePath")
                                } else {
                                    logger.warning("⚠️ Content not found in file: $filePath")
                                }
                            }
                            // 优先级3：跳转到行号
                            line != null && line > 0 -> {
                                val lineIndex = (line - 1).coerceAtLeast(0)
                                val offset = editor.document.getLineStartOffset(lineIndex.coerceAtMost(editor.document.lineCount - 1))
                                val targetOffset = if (column != null && column > 0) {
                                    offset + (column - 1)
                                } else {
                                    offset
                                }
                                editor.caretModel.moveToOffset(targetOffset.coerceAtMost(editor.document.textLength))
                                editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                                logger.info("✅ Navigated to line $line in $filePath")
                            }
                        }
                    }

                    logger.info("✅ Opened file: $filePath")
                } else {
                    logger.warning("⚠️ File not found: $filePath")
                }
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("❌ Failed to open file: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to open file")
        }
    }

    /**
     * 显示文件差异对比
     *
     * 增强功能：
     * - 支持完整文件 Diff（rebuildFromFile = true）
     * - 支持多个编辑操作的重建（edits 数组）
     * - 自动从当前文件重建修改前内容
     */
    private fun handleShowDiff(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing filePath")
        val oldContent = data["oldContent"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing oldContent")
        val newContent = data["newContent"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing newContent")
        val title = data["title"]?.toString()?.trim('"')
        val rebuildFromFile = data["rebuildFromFile"]?.toString()?.trim('"')?.toBoolean() ?: false
        val editsJson = data["edits"]

        return try {
            ApplicationManager.getApplication().invokeLater {
                val fileName = java.io.File(filePath).name
                val fileType = com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                // 确定要显示的内容
                val (finalOldContent, finalNewContent, finalTitle) = if (rebuildFromFile) {
                    // 从文件重建完整 Diff（对齐 Compose UI 实现）
                    val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
                        ?: throw IllegalStateException("文件不存在: $filePath")

                    // 刷新文件（确保读取最新内容）
                    file.refresh(false, false)

                    val currentContent = String(file.contentsToByteArray(), Charsets.UTF_8)

                    // 解析编辑操作
                    val edits = if (editsJson != null) {
                        json.decodeFromJsonElement<List<EditOperation>>(editsJson)
                    } else {
                        listOf(EditOperation(oldContent, newContent, false))
                    }

                    // 重建修改前内容（失败时抛出异常）
                    val rebuiltOldContent = rebuildBeforeContent(currentContent, edits)

                    Triple(
                        rebuiltOldContent,
                        currentContent,
                        title ?: "文件变更: $fileName (${edits.size} 处修改)"
                    )
                } else {
                    Triple(oldContent, newContent, title ?: "文件差异对比: $fileName")
                }

                // 创建 Diff 内容
                val leftContent = com.intellij.diff.contents.DiffContentFactory.getInstance()
                    .create(project, finalOldContent, fileType)

                val rightContent = com.intellij.diff.contents.DiffContentFactory.getInstance()
                    .create(project, finalNewContent, fileType)

                // 创建 diff 请求
                val diffRequest = com.intellij.diff.requests.SimpleDiffRequest(
                    finalTitle,
                    leftContent,
                    rightContent,
                    "$fileName (修改前)",
                    "$fileName (修改后)"
                )

                // 显示 diff 对话框
                com.intellij.diff.DiffManager.getInstance().showDiff(project, diffRequest)

                logger.info("✅ Showing diff for: $filePath")
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("❌ Failed to show diff: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to show diff")
        }
    }

    /**
     * 编辑操作数据类
     */
    @kotlinx.serialization.Serializable
    private data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean
    )

    /**
     * 从修改后的内容重建修改前的内容
     *
     * 通过反向应用所有编辑操作来重建原始内容
     *
     * @param afterContent 修改后的文件内容（当前文件内容）
     * @param operations 编辑操作列表
     * @return 重建的修改前内容
     * @throws IllegalStateException 如果重建失败（newString 不在文件中）
     */
    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent

        // 反向应用所有操作（从后往前）
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                // 全局替换：将所有 newString 替换回 oldString
                if (!content.contains(operation.newString)) {
                    throw IllegalStateException(
                        "重建失败：文件中找不到 newString (replace_all)\n" +
                        "期望找到: ${operation.newString.take(100)}..."
                    )
                }
                content = content.replace(operation.newString, operation.oldString)
            } else {
                // 单次替换：只替换第一个匹配
                val index = content.indexOf(operation.newString)
                if (index < 0) {
                    throw IllegalStateException(
                        "重建失败：文件中找不到 newString\n" +
                        "期望找到: ${operation.newString.take(100)}..."
                    )
                }
                content = buildString {
                    append(content.substring(0, index))
                    append(operation.oldString)
                    append(content.substring(index + operation.newString.length))
                }
            }
        }

        logger.info("✅ Successfully rebuilt before content (${operations.size} operations)")
        return content
    }

    /**
     * 推送事件给所有前端客户端（通过 SSE）
     */
    fun pushEvent(event: IdeEvent) {
        _eventFlow.tryEmit(event)
        logger.info("📤 Pushed event: ${event.type}")
    }

    /**
     * 监听主题变化
     */
    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                try {
                    logger.info("🎨 Theme changed, broadcasting to clients")
                    val theme = extractIdeTheme()
                    pushEvent(IdeEvent(
                        type = "theme",
                        data = mapOf("theme" to json.parseToJsonElement(json.encodeToString(theme)))
                    ))
                } catch (e: Exception) {
                    logger.severe("❌ Failed to notify theme change: ${e.message}")
                }
            })
    }

    /**
     * 提取 IDE 主题
     */
    private fun extractIdeTheme(): IdeTheme {
        return IdeTheme(
            isDark = UIUtil.isUnderDarcula(),
            background = colorToHex(UIUtil.getPanelBackground()),
            foreground = colorToHex(UIUtil.getLabelForeground()),
            borderColor = colorToHex(JBColor.border()),
            panelBackground = colorToHex(UIUtil.getPanelBackground()),
            textFieldBackground = colorToHex(UIUtil.getTextFieldBackground()),
            selectionBackground = colorToHex(UIUtil.getListSelectionBackground(true)),
            selectionForeground = colorToHex(UIUtil.getListSelectionForeground(true)),
            linkColor = colorToHex(JBColor.namedColor("Link.foreground", JBColor.BLUE)),
            errorColor = colorToHex(JBColor.RED),
            warningColor = colorToHex(JBColor.YELLOW),
            successColor = colorToHex(JBColor.GREEN),
            separatorColor = colorToHex(JBColor.border()),
            hoverBackground = colorToHex(UIUtil.getListBackground(true)),
            accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.BLUE)),
            infoBackground = colorToHex(JBColor.namedColor("Component.infoForeground", JBColor.GRAY)),
            codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
            secondaryForeground = colorToHex(JBColor.GRAY)
        )
    }

    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
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

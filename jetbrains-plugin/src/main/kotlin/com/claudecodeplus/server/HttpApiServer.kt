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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.awt.Color
import java.io.IOException
import java.net.ServerSocket
import java.nio.file.Path
import java.util.logging.Logger

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
                allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.ContentType)
            }

            install(SSE)

            // 路由配置
            routing {
                // 静态资源
                staticFiles("/", frontendDir.toFile()) {
                    default("index.html")
                }

                // REST API
                post("/api/") {
                    try {
                        val request = call.receive<FrontendRequest>()
                        logger.info("📨 API Request: ${request.action}")

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
                val projectPath = project.basePath ?: project.projectFilePath ?: "δ֪"
                FrontendResponse(
                    success = true,
                    data = mapOf("projectPath" to JsonPrimitive(projectPath))
                )
            }
            else -> FrontendResponse(false, error = "Unknown IDE action: ${request.action}")
        }
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

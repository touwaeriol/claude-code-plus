package com.claudecodeplus.bridge

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.awt.Color
import java.util.logging.Logger

/**
 * 前后端通信桥接
 * 负责 JCEF 浏览器与 Kotlin 后端的双向通信
 */
class FrontendBridge(
    private val project: Project,
    private val browser: JBCefBrowser,
    private val scope: CoroutineScope
) {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    // 前端 -> 后端 (Request/Response 模式)
    private val queryHandler = JBCefJSQuery.create(browser as JBCefBrowserBase)

    // 后端 -> 前端 (事件推送)
    private var isReady = false

    init {
        setupQueryHandler()
        setupThemeListener()
    }

    /**
     * 注册请求处理器
     */
    private fun setupQueryHandler() {
        queryHandler.addHandler { requestJson ->
            try {
                logger.info("📨 Received request: $requestJson")
                val request = json.decodeFromString<FrontendRequest>(requestJson)
                val response = handleRequest(request)
                val responseJson = json.encodeToString(response)
                logger.info("📤 Sending response: $responseJson")
                JBCefJSQuery.Response(responseJson)
            } catch (e: Exception) {
                logger.severe("❌ Error handling request: ${e.message}")
                e.printStackTrace()
                val error = FrontendResponse(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
                JBCefJSQuery.Response(json.encodeToString(error))
            }
        }

        // 注入 JavaScript 桥接代码
        injectBridgeScript()
    }

    /**
     * 注入前端可调用的 JavaScript API
     */
    private fun injectBridgeScript() {
        val script = """
            (function() {
                console.log('🔧 Injecting IDEA bridge...');

                // 前端调用后端 (异步)
                window.ideaBridge = {
                    query: async function(action, data) {
                        console.log('🚀 Bridge query:', action, data);
                        const request = JSON.stringify({ action, data });
                        try {
                            const responseJson = await new Promise((resolve, reject) => {
                                ${queryHandler.inject("request", "resolve")}
                            });
                            const response = JSON.parse(responseJson);
                            console.log('✅ Bridge response:', response);
                            return response;
                        } catch (error) {
                            console.error('❌ Bridge query failed:', error);
                            return { success: false, error: String(error) };
                        }
                    }
                };

                // 后端推送事件给前端
                window.onIdeEvent = function(event) {
                    console.log('📥 IDE Event:', event);
                    window.dispatchEvent(new CustomEvent('ide-event', { detail: event }));
                };

                // 标记桥接已就绪
                window.__bridgeReady = true;
                window.dispatchEvent(new Event('bridge-ready'));
                console.log('✅ IDEA bridge ready');

                // 移除加载样式
                document.body.classList.remove('theme-loading');
                document.body.classList.add('theme-loaded');
            })();
        """.trimIndent()

        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        isReady = true
        logger.info("✅ Bridge script injected")
    }

    /**
     * 处理来自前端的请求
     */
    private fun handleRequest(request: FrontendRequest): FrontendResponse {
        logger.info("Processing action: ${request.action}")

        return when {
            request.action.startsWith("test.") -> handleTestAction(request)
            request.action.startsWith("ide.") -> handleIdeAction(request)
            request.action.startsWith("claude.") -> handleClaudeAction(request)
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
                        "pong" to JsonPrimitive(true),
                        "timestamp" to JsonPrimitive(System.currentTimeMillis())
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
                FrontendResponse(
                    success = true,
                    data = mapOf("theme" to json.encodeToJsonElement(theme))
                )
            }
            "ide.openFile" -> {
                // TODO: 实现文件打开
                FrontendResponse(success = true)
            }
            "ide.showDiff" -> {
                // TODO: 实现 Diff 显示
                FrontendResponse(success = true)
            }
            else -> FrontendResponse(false, error = "Unknown IDE action: ${request.action}")
        }
    }

    /**
     * 处理 Claude 操作
     */
    private fun handleClaudeAction(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "claude.connect" -> {
                // TODO: 实现 Claude 连接
                scope.launch {
                    // 模拟连接成功
                    pushEvent(IdeEvent(
                        type = "claude.connected",
                        data = mapOf("sessionId" to JsonPrimitive("test-session"))
                    ))
                }
                FrontendResponse(success = true)
            }
            "claude.query" -> {
                val messageData = request.data as? JsonObject
                val message = messageData?.get("message")?.jsonPrimitive?.content

                if (message == null) {
                    return FrontendResponse(false, error = "Missing message")
                }

                // TODO: 实际调用 ClaudeCodeSdkClient
                scope.launch {
                    // 模拟响应
                    pushEvent(IdeEvent(
                        type = "claude.message",
                        data = mapOf(
                            "message" to buildJsonObject {
                                put("type", "assistant")
                                put("content", buildJsonArray {
                                    add(buildJsonObject {
                                        put("type", "text")
                                        put("text", "这是来自后端的测试响应: $message")
                                    })
                                })
                            }
                        )
                    ))
                }
                FrontendResponse(success = true)
            }
            "claude.interrupt" -> {
                // TODO: 实现中断
                FrontendResponse(success = true)
            }
            "claude.disconnect" -> {
                // TODO: 实现断开连接
                FrontendResponse(success = true)
            }
            else -> FrontendResponse(false, error = "Unknown Claude action: ${request.action}")
        }
    }

    /**
     * 推送事件给前端
     */
    fun pushEvent(event: IdeEvent) {
        if (!isReady) {
            logger.warning("⚠️ Bridge not ready, cannot push event: ${event.type}")
            return
        }

        try {
            val eventJson = json.encodeToString(event)
            val script = "window.onIdeEvent($eventJson);"
            browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
            logger.info("📤 Pushed event: ${event.type}")
        } catch (e: Exception) {
            logger.severe("❌ Failed to push event: ${e.message}")
        }
    }

    /**
     * 设置主题监听器
     */
    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                logger.info("🎨 Theme changed, notifying frontend")
                val theme = extractIdeTheme()
                pushEvent(IdeEvent(
                    type = "theme.changed",
                    data = mapOf("theme" to json.encodeToJsonElement(theme))
                ))
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
            linkColor = colorToHex(JBColor.link()),
            errorColor = colorToHex(JBColor.red),
            warningColor = colorToHex(JBColor.yellow),
            successColor = colorToHex(JBColor.green),
            separatorColor = colorToHex(JBColor.border()),
            hoverBackground = colorToHex(UIUtil.getListBackground(true)),
            accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.link())),
            infoBackground = colorToHex(JBColor.namedColor("Component.infoForeground", JBColor.gray)),
            codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
            secondaryForeground = colorToHex(JBColor.gray)
        )
    }

    /**
     * 颜色转十六进制
     */
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}

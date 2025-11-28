package com.asakii.plugin.bridge

import com.asakii.bridge.IdeTheme
import com.asakii.plugin.theme.IdeaThemeAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.CopyOnWriteArrayList

/**
 * IDE 模式下的 JCEF ↔ Vue 会话桥接。
 * 负责：
 * 1. 接收前端回传的会话状态，供标题栏渲染。
 * 2. 将 ToolWindow 标题栏的交互命令发送给前端。
 * 3. 推送主题变化到前端（JCEF 环境）。
 */
class IdeSessionBridge(
    private val browser: JBCefBrowser,
    private val project: Project
) : Disposable {

    private val logger = Logger.getInstance(IdeSessionBridge::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val listeners = CopyOnWriteArrayList<(SessionState) -> Unit>()
    private val pendingCommands = mutableListOf<String>()

    private var frontendReady = false
    private var lastState: SessionState? = null
    private var themeChangeListener: (() -> Unit)? = null
    private val ideActionBridge = IdeActionBridgeImpl(project)

    private val sessionStateQuery = JBCefJSQuery.create(browser).apply {
        addHandler { payload ->
            handleSessionState(payload)
            null
        }
    }

    init {
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    injectBridge(frame)
                    injectJcefBridge(frame)
                    setupThemeListener()
                }
            }
        }, browser.cefBrowser)
    }

    private fun injectBridge(frame: CefFrame) {
        val script = """
            window.__CLAUDE_IDE_HOST__ = window.__CLAUDE_IDE_HOST__ || {};
            window.__CLAUDE_IDE_HOST__.postSessionState = function(payload) {
                ${sessionStateQuery.inject("payload")}
            };
        """.trimIndent()
        frame.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
        logger.info("✅ Injected IDE session bridge bootstrap script")
    }

    /**
     * 注入 JCEF 桥接脚本，供前端检测 JCEF 环境
     * 同时推送初始主题
     */
    private fun injectJcefBridge(frame: CefFrame) {
        val script = """
            (function() {
                window.__CLAUDE_IDE_BRIDGE__ = window.__CLAUDE_IDE_BRIDGE__ || {};
                window.__CLAUDE_IDE_HOST__ = window.__CLAUDE_IDE_HOST__ || {};
                console.log('✅ JCEF Bridge injected');
            })();
        """.trimIndent()
        frame.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
        logger.info("✅ Injected JCEF bridge script")

        injectThemeBridge(frame)
        notifyThemeChange()

        // 推送语言设置（前端会根据 IDEA 语言自动切换）
        pushLocale()
    }

    /**
     * 注入主题桥接脚本，供 Vue 直接读取/订阅 IDE 主题
     */
    private fun injectThemeBridge(frame: CefFrame) {
        val script = """
            (function() {
                var bridge = window.__themeBridge;
                if (!bridge) {
                    var currentTheme = null;
                    bridge = {
                        onChange: null,
                        getCurrent: function() {
                            return currentTheme;
                        },
                        push: function(theme) {
                            currentTheme = theme;
                            if (typeof bridge.onChange === 'function') {
                                try {
                                    bridge.onChange(theme);
                                } catch (err) {
                                    console.error('[themeBridge] onChange failed', err);
                                }
                            }
                            try {
                                window.dispatchEvent(new CustomEvent('claude:themeBridgePush', { detail: theme }));
                            } catch (eventErr) {
                                console.warn('[themeBridge] Failed to dispatch push event', eventErr);
                            }
                        }
                    };
                    window.__themeBridge = bridge;
                }
                try {
                    window.dispatchEvent(new CustomEvent('claude:themeBridgeReady'));
                } catch (err) {
                    console.warn('[themeBridge] Failed to dispatch ready event', err);
                }
                console.log('✅ Theme bridge ready');
            })();
        """.trimIndent()
        frame.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
    }

    /**
     * 设置主题变化监听器
     */
    private fun setupThemeListener() {
        themeChangeListener = {
            notifyThemeChange()
        }
        
        IdeaThemeAdapter.registerThemeChangeListener { isDark ->
            themeChangeListener?.invoke()
        }
        
        // 立即通知一次当前主题
        themeChangeListener?.invoke()
    }

    /**
     * 通知前端主题变化（推送完整主题对象）
     */
    private fun notifyThemeChange() {
        runCatching {
            val theme = ideActionBridge.getTheme()
            val themeJson = json.encodeToString(IdeTheme.serializer(), theme)
        val script = """
                (function(theme) {
                    if (window.__themeBridge && typeof window.__themeBridge.push === 'function') {
                        window.__themeBridge.push(theme);
            } else {
                        console.warn('Theme bridge is not ready, drop theme update');
            }
                })($themeJson);
        """.trimIndent()
            executeScript(script)
            logger.info("🎨 Notified frontend of theme change: ${if (theme.isDark) "dark" else "light"}")
        }.onFailure { e ->
            logger.warn("❌ Failed to notify theme change: ${e.message}", e)
        }
    }

    private fun handleSessionState(payload: String) {
        runCatching {
            json.decodeFromString(SessionStateMessage.serializer(), payload)
        }.onFailure {
            logger.warn("❌ Failed to parse session state payload: $payload", it)
        }.onSuccess { message ->
            if (message.type != "session:update") {
                logger.debug("Ignoring message type ${message.type}")
                return
            }

            val summaries = message.sessions.orEmpty().map { summary ->
                SessionSummary(
                    id = summary.id,
                    title = summary.title?.takeIf { it.isNotBlank() }
                        ?: summary.name?.takeIf { it.isNotBlank() }
                        ?: summary.id.takeLast(8),
                    isGenerating = summary.isGenerating,
                    isConnected = summary.isConnected
                )
            }

            val state = SessionState(
                sessions = summaries,
                activeSessionId = message.activeSessionId
            )
            lastState = state
            frontendReady = true
            listeners.forEach { listener -> listener.invoke(state) }
            flushPendingCommands()
        }
    }

    private fun flushPendingCommands() {
        if (!frontendReady) return
        val iterator = pendingCommands.iterator()
        while (iterator.hasNext()) {
            val script = iterator.next()
            executeScript(script)
            iterator.remove()
        }
    }

    private fun enqueueCommand(script: String) {
        if (!frontendReady) {
            pendingCommands.add(script)
            logger.debug("Queue command (frontend not ready): $script")
        } else {
            executeScript(script)
        }
    }

    private fun executeScript(script: String) {
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
    }

    /**
     * 注册监听器，获取最新的会话状态。
     * @return 取消监听的函数
     */
    fun addSessionStateListener(listener: (SessionState) -> Unit): () -> Unit {
        listeners.add(listener)
        lastState?.let(listener)
        return { listeners.remove(listener) }
    }

    fun latestState(): SessionState? = lastState

    fun switchSession(sessionId: String) {
        val payload = buildJsonObject { put("sessionId", sessionId) }
        sendCommand("switchSession", payload)
    }

    fun requestNewSession() {
        sendCommand("createSession")
    }

    fun toggleHistoryOverlay() {
        sendCommand("toggleHistory")
    }

    /**
     * 关闭指定会话
     */
    fun closeSession(sessionId: String) {
        val payload = buildJsonObject { put("sessionId", sessionId) }
        sendCommand("closeSession", payload)
    }

    /**
     * 推送当前 IDEA 语言设置到前端
     * 前端收到后会刷新页面应用新语言
     */
    fun pushLocale() {
        val locale = ideActionBridge.getLocale()
        val payload = buildJsonObject { put("locale", locale) }
        sendCommand("setLocale", payload)
        logger.info("🌐 Pushed locale to frontend: $locale")
    }

    private fun sendCommand(type: String, payload: JsonObject? = null) {
        val commandJson = json.encodeToString(HostCommand(type = type, payload = payload))
        val script = """
            if (window.__CLAUDE_IDE_BRIDGE__ && window.__CLAUDE_IDE_BRIDGE__.onHostCommand) {
                window.__CLAUDE_IDE_BRIDGE__.onHostCommand($commandJson);
            } else {
                console.warn('IDE host command dropped, bridge is not ready yet');
            }
        """.trimIndent()
        enqueueCommand(script)
    }

    override fun dispose() {
        sessionStateQuery.dispose()
        listeners.clear()
        pendingCommands.clear()
        themeChangeListener = null
    }

    data class SessionState(
        val sessions: List<SessionSummary>,
        val activeSessionId: String?
    )

    data class SessionSummary(
        val id: String,
        val title: String,
        val isGenerating: Boolean,
        val isConnected: Boolean  // 是否已连接（进行中会话）
    )

    @Serializable
    private data class SessionStateMessage(
        val type: String,
        val sessions: List<SessionSummaryMessage>? = null,
        val activeSessionId: String? = null
    )

    @Serializable
    private data class SessionSummaryMessage(
        val id: String,
        val title: String? = null,
        val name: String? = null,
        val isGenerating: Boolean = false,
        val isConnected: Boolean = false
    )

    @Serializable
    private data class HostCommand(
        val type: String,
        val payload: JsonObject? = null
    )
}


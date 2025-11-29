package com.asakii.plugin.bridge

import com.asakii.rpc.api.IdeTheme
import com.asakii.plugin.services.IdeaPlatformService
import com.asakii.plugin.theme.IdeaThemeAdapter
import com.asakii.server.HttpServerProjectService
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
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

    // ====== JBCefJSQuery ======

    private val sessionStateQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { payload ->
            handleSessionState(payload)
            null
        }
    }

    /**
     * 打开文件并选中范围
     * payload: { filePath, startLine?, endLine?, startOffset?, endOffset? }
     */
    private val openFileWithSelectionQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { payload ->
            handleOpenFileWithSelection(payload)
            null
        }
    }

    /**
     * 显示 Diff
     * payload: { filePath, oldContent, newContent, title? }
     */
    private val showDiffQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { payload ->
            handleShowDiff(payload)
            null
        }
    }

    /**
     * 显示 MultiEdit Diff（多个编辑合并展示）
     * payload: { filePath, edits: [{oldString, newString, replaceAll}], currentContent }
     */
    private val showMultiEditDiffQuery = JBCefJSQuery.create(browser as JBCefBrowserBase).apply {
        addHandler { payload ->
            handleShowMultiEditDiff(payload)
            null
        }
    }

    init {
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    injectIdeaJcefBridge(frame)  // 统一注入，包含初始主题
                    setupThemeListener()  // 监听后续主题变化
                }
            }
        }, browser.cefBrowser)
    }

    /**
     * 统一注入 IDEA JCEF 桥接
     * 整合：服务器地址、工具展示、主题（含初始值）、会话状态
     */
    private fun injectIdeaJcefBridge(frame: CefFrame) {
        // 获取服务器 URL
        val serverUrl = HttpServerProjectService.getInstance(project).serverUrl ?: "http://localhost:8765"

        // 获取当前主题作为初始值
        val initialTheme = ideActionBridge.getTheme()
        val initialThemeJson = json.encodeToString(IdeTheme.serializer(), initialTheme)

        val script = """
            (function() {
                // 注入服务器地址（优先级最高）
                window.__serverUrl = '$serverUrl';
                console.log('🔗 Server URL injected via JCEF:', window.__serverUrl);

                // 初始主题
                var initialTheme = $initialThemeJson;

                window.__IDEA_JCEF__ = {
                    // ====== 工具展示 API ======
                    toolShow: {
                        openFile: function(payload) {
                            ${openFileWithSelectionQuery.inject("JSON.stringify(payload)")}
                        },
                        showDiff: function(payload) {
                            ${showDiffQuery.inject("JSON.stringify(payload)")}
                        },
                        showMultiEditDiff: function(payload) {
                            ${showMultiEditDiffQuery.inject("JSON.stringify(payload)")}
                        }
                    },

                    // ====== 主题 API ======
                    theme: {
                        _current: initialTheme,  // 初始值
                        _onChange: null,
                        push: function(theme) {
                            this._current = theme;
                            if (typeof this._onChange === 'function') {
                                try {
                                    this._onChange(theme);
                                } catch (err) {
                                    console.error('[IDEA_JCEF] theme.onChange failed', err);
                                }
                            }
                            window.dispatchEvent(new CustomEvent('idea:themeChange', { detail: theme }));
                        },
                        getCurrent: function() {
                            return this._current;
                        },
                        set onChange(fn) { this._onChange = fn; },
                        get onChange() { return this._onChange; }
                    },

                    // ====== 会话 API ======
                    session: {
                        postState: function(payload) {
                            ${sessionStateQuery.inject("payload")}
                        }
                    }
                };

                console.log('✅ IDEA JCEF Bridge injected with initial theme');
                window.dispatchEvent(new CustomEvent('idea:jcefReady'));
            })();
        """.trimIndent()
        frame.executeJavaScript(script, browser.cefBrowser.url ?: "", 0)
        logger.info("✅ Injected unified IDEA JCEF bridge with serverUrl=$serverUrl")
    }

    // ====== IDEA 工具处理函数 ======

    @Serializable
    private data class OpenFilePayload(
        val filePath: String,
        val startLine: Int? = null,
        val endLine: Int? = null,
        val startOffset: Int? = null,
        val endOffset: Int? = null
    )

    @Serializable
    private data class ShowDiffPayload(
        val filePath: String,
        val oldContent: String,
        val newContent: String,
        val title: String? = null
    )

    @Serializable
    private data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean = false
    )

    @Serializable
    private data class ShowMultiEditDiffPayload(
        val filePath: String,
        val edits: List<EditOperation>,
        val currentContent: String? = null
    )

    private fun handleOpenFileWithSelection(payload: String) {
        runCatching {
            val data = json.decodeFromString(OpenFilePayload.serializer(), payload)
            logger.info("📂 Opening file with selection: ${data.filePath}")

            val platformService = IdeaPlatformService(project)

            // 构建选择范围
            val selectionRange = if (data.startOffset != null && data.endOffset != null) {
                IdeaPlatformService.SelectionRange(data.startOffset, data.endOffset)
            } else {
                null
            }

            platformService.openFile(
                filePath = data.filePath,
                line = data.startLine,
                selectionRange = selectionRange
            )
        }.onFailure { e ->
            logger.warn("❌ Failed to open file with selection: ${e.message}", e)
        }
    }

    private fun handleShowDiff(payload: String) {
        runCatching {
            val data = json.decodeFromString(ShowDiffPayload.serializer(), payload)
            logger.info("📝 Showing diff for: ${data.filePath}")

            val platformService = IdeaPlatformService(project)
            platformService.showDiff(
                filePath = data.filePath,
                oldContent = data.oldContent,
                newContent = data.newContent,
                title = data.title
            )
        }.onFailure { e ->
            logger.warn("❌ Failed to show diff: ${e.message}", e)
        }
    }

    private fun handleShowMultiEditDiff(payload: String) {
        runCatching {
            val data = json.decodeFromString(ShowMultiEditDiffPayload.serializer(), payload)
            logger.info("📝 Showing multi-edit diff for: ${data.filePath} (${data.edits.size} edits)")

            // 从文件读取当前内容，反推修改前内容
            val file = java.io.File(data.filePath)
            if (!file.exists()) {
                logger.warn("❌ File not found: ${data.filePath}")
                return
            }

            val currentContent = data.currentContent ?: file.readText()
            val beforeContent = rebuildBeforeContent(currentContent, data.edits)

            val platformService = IdeaPlatformService(project)
            platformService.showDiff(
                filePath = data.filePath,
                oldContent = beforeContent,
                newContent = currentContent,
                title = "Multi-Edit: ${file.name} (${data.edits.size} changes)"
            )
        }.onFailure { e ->
            logger.warn("❌ Failed to show multi-edit diff: ${e.message}", e)
        }
    }

    /**
     * 从修改后的内容和编辑操作列表，反推修改前的内容
     */
    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                content = content.replace(operation.newString, operation.oldString)
            } else {
                val index = content.indexOf(operation.newString)
                if (index >= 0) {
                    content = buildString {
                        append(content.substring(0, index))
                        append(operation.oldString)
                        append(content.substring(index + operation.newString.length))
                    }
                }
            }
        }
        return content
    }


    /**
     * 设置主题变化监听器
     * 注意：初始主题已在 JCEF 注入时包含，这里只监听后续变化
     */
    private fun setupThemeListener() {
        themeChangeListener = {
            notifyThemeChange()
        }

        IdeaThemeAdapter.registerThemeChangeListener { isDark ->
            themeChangeListener?.invoke()
        }
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
                    if (window.__IDEA_JCEF__?.theme) {
                        window.__IDEA_JCEF__.theme.push(theme);
                    } else {
                        console.warn('[IDEA_JCEF] Theme bridge is not ready, drop theme update');
                    }
                })($themeJson);
            """.trimIndent()
            executeScript(script)
            logger.info("🎨 Notified frontend of theme change")
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
        openFileWithSelectionQuery.dispose()
        showDiffQuery.dispose()
        showMultiEditDiffQuery.dispose()
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


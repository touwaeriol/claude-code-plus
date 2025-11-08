package com.claudecodeplus.bridge

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeManager
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
 * 鍓嶅悗绔€氫俊妗ユ帴
 * 璐熻矗 JCEF 娴忚鍣ㄤ笌 Kotlin 鍚庣鐨勫弻鍚戦€氫俊
 */
class FrontendBridge(
    private val project: Project,
    private val browser: JBCefBrowser,
    private val scope: CoroutineScope
) : EventBridge {
    private val logger = Logger.getLogger(javaClass.name)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    // 鍓嶇 -> 鍚庣 (Request/Response 妯″紡)
    private val queryHandler = JBCefJSQuery.create(browser as JBCefBrowserBase)

    // 鍚庣 -> 鍓嶇 (浜嬩欢鎺ㄩ€?
    private var isReady = false

    // Claude 鎿嶄綔澶勭悊鍣?
    private val claudeHandler = ClaudeActionHandler(project, this, scope)

    // 浼氳瘽鎿嶄綔澶勭悊鍣?
    private val sessionHandler = SessionActionHandler(project)

    init {
        // 璁剧疆 Claude 澶勭悊鍣ㄤ笌浼氳瘽澶勭悊鍣ㄧ殑鍏宠仈锛堝弻鍚戝紩鐢級
        claudeHandler.sessionHandler = sessionHandler
        sessionHandler.claudeHandler = claudeHandler

        setupQueryHandler()
        setupThemeListener()
    }

    /**
     * 娉ㄥ唽璇锋眰澶勭悊鍣?
     */
    private fun setupQueryHandler() {
        queryHandler.addHandler { requestJson ->
            try {
                logger.info("馃摠 Received request: $requestJson")
                val request = json.decodeFromString<FrontendRequest>(requestJson)
                val response = handleRequest(request)
                val responseJson = json.encodeToString(response)
                logger.info("馃摛 Sending response: $responseJson")
                JBCefJSQuery.Response(responseJson)
            } catch (e: Exception) {
                logger.severe("鉂?Error handling request: ${e.message}")
                e.printStackTrace()
                val error = FrontendResponse(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
                JBCefJSQuery.Response(json.encodeToString(error))
            }
        }

        // 娉ㄦ剰锛欽avaScript 妗ユ帴鑴氭湰蹇呴』鍦ㄩ〉闈㈠姞杞藉畬鎴愬悗娉ㄥ叆
        // 涓嶈鍦ㄨ繖閲岃皟鐢?injectBridgeScript()
    }

    /**
     * 娉ㄥ叆鍓嶇鍙皟鐢ㄧ殑 JavaScript API
     * 蹇呴』鍦ㄩ〉闈㈠姞杞藉悗璋冪敤
     */
    fun injectBridgeScript() {
        val script = """
            (function() {
                const markThemeLoaded = () => {
                    if (!document.body) {
                        return;
                    }
                    document.body.classList.remove('theme-loading');
                    document.body.classList.add('theme-loaded');
                };

                try {
                    console.log('馃敡 Injecting IDEA bridge...');

                    // 鍓嶇璋冪敤鍚庣 (寮傛)
                    window.ideaBridge = {
                        query: async function(action, data) {
                            console.log('馃殌 Bridge query:', action, data);
                            const request = JSON.stringify({ action, data });
                            try {
                                const responseJson = await new Promise((resolve, reject) => {
                                    ${queryHandler.inject("request", "resolve", "reject")}
                                });
                                const response = JSON.parse(responseJson);
                                console.log('鉁?Bridge response:', response);
                                return response;
                            } catch (error) {
                                console.error('鉂?Bridge query failed:', error);
                                return { success: false, error: String(error) };
                            }
                        },

                        // 鏍囪妗ユ帴宸插氨缁?
                        isReady: true
                    };

                    // 鍚庣鎺ㄩ€佷簨浠剁粰鍓嶇
                    window.onIdeEvent = function(event) {
                        console.log('馃摜 IDE Event:', event);
                        window.dispatchEvent(new CustomEvent('ide-event', { detail: event }));
                    };

                    // 鏍囪妗ユ帴宸插氨缁?
                    window.__bridgeReady = true;
                    window.dispatchEvent(new Event('bridge-ready'));
                    console.log('鉁?IDEA bridge ready');
                } catch (error) {
                    console.error('鉂?Failed to initialize IDEA bridge:', error);
                    window.__bridgeReady = false;
                    const root = document.getElementById('app');
                    if (root && !root.querySelector('.bridge-init-error')) {
                        root.innerHTML = `
                            <div class="bridge-init-error" style="padding:24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#d22;background:rgba(210,34,34,0.08);border:1px solid rgba(210,34,34,0.3);border-radius:8px;">
                                <h3 style="margin-bottom:12px;">IDEA 妗ユ帴鍒濆鍖栧け璐?/h3>
                                <p style="margin-bottom:8px;">璇锋煡鐪?IDE 鏃ュ織浜嗚В璇︽儏銆?/p>
                                <code style="display:block;white-space:pre-wrap;font-size:12px;color:#a11;">${'$'}{String(error)}</code>
                            </div>`;
                    }
                } finally {
                    markThemeLoaded();
                }
            })();
        """.trimIndent()

        logger.info("馃И Bridge script preview: ${script.take(200)}...")
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        isReady = true
        logger.info("鉁?Bridge script injected")
    }

    /**
     * 澶勭悊鏉ヨ嚜鍓嶇鐨勮姹?
     */
    private fun handleRequest(request: FrontendRequest): FrontendResponse {
        logger.info("Processing action: ${request.action}")

        return when {
            request.action.startsWith("test.") -> handleTestAction(request)
            request.action.startsWith("ide.") -> handleIdeAction(request)
            request.action.startsWith("claude.") -> handleClaudeAction(request)
            request.action.startsWith("session.") -> handleSessionAction(request)
            else -> FrontendResponse(false, error = "Unknown action: ${request.action}")
        }
    }

    /**
     * 澶勭悊娴嬭瘯鎿嶄綔
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
     * 澶勭悊 IDE 鎿嶄綔
     */
    private fun handleIdeAction(request: FrontendRequest): FrontendResponse {
        return when (request.action) {
            "ide.getTheme" -> {
                val theme = extractIdeTheme()
                // 鍏堝簭鍒楀寲鎴愬瓧绗︿覆,鍐嶈В鏋愭垚 JsonElement
                val themeJsonString = json.encodeToString(theme)
                val themeJson = json.parseToJsonElement(themeJsonString)
                FrontendResponse(
                    success = true,
                    data = mapOf("theme" to themeJson)
                )
            }
            "ide.getServerUrl" -> {
                val httpServerService = com.claudecodeplus.server.HttpServerProjectService.getInstance(project)
                val serverUrl = httpServerService.serverUrl ?: "未启动"
                FrontendResponse(
                    success = true,
                    data = mapOf("serverUrl" to JsonPrimitive(serverUrl))
                )
            }
            "ide.openFile" -> handleOpenFile(request)
            "ide.showDiff" -> handleShowDiff(request)
            "ide.searchFiles" -> handleSearchFiles(request)
            "ide.getFileContent" -> handleGetFileContent(request)
            else -> FrontendResponse(false, error = "Unknown IDE action: ${request.action}")
        }
    }

    /**
     * 澶勭悊 Claude 鎿嶄綔
     */
    private fun handleClaudeAction(request: FrontendRequest): FrontendResponse {
        return claudeHandler.handle(request)
    }

    /**
     * 澶勭悊浼氳瘽鎿嶄綔
     */
    private fun handleSessionAction(request: FrontendRequest): FrontendResponse {
        return sessionHandler.handle(request)
    }

    /**
     * 鎺ㄩ€佷簨浠剁粰鍓嶇
     */
    override fun pushEvent(event: IdeEvent) {
        if (!isReady) {
            logger.warning("鈿狅笍 Bridge not ready, cannot push event: ${event.type}")
            return
        }

        try {
            val eventJson = json.encodeToString(event)
            val script = "window.onIdeEvent($eventJson);"
            browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
            logger.info("馃摛 Pushed event: ${event.type}")
        } catch (e: Exception) {
            logger.severe("鉂?Failed to push event: ${e.message}")
        }
    }

    /**
     * 璁剧疆涓婚鐩戝惉鍣?
     */
    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                try {
                    logger.info("馃帹 Theme changed, notifying frontend")
                    val theme = extractIdeTheme()
                    // 鍏堝簭鍒楀寲鎴愬瓧绗︿覆,鍐嶈В鏋愭垚 JsonElement
                    val themeJsonString = json.encodeToString(theme)
                    val themeJson = json.parseToJsonElement(themeJsonString)
                    pushEvent(IdeEvent(
                        type = "theme.changed",
                        data = mapOf("theme" to themeJson)
                    ))
                } catch (e: Exception) {
                    logger.severe("鉂?Failed to notify theme change: ${e.message}")
                    e.printStackTrace()
                }
            })
    }

    /**
     * 鎻愬彇 IDE 涓婚
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

    /**
     * 鎼滅储鏂囦欢
     */
    private fun handleSearchFiles(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val query = data["query"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing query")
        val maxResults = data["maxResults"]?.toString()?.trim('"')?.toIntOrNull() ?: 20

        return try {
            val files = mutableListOf<Map<String, JsonElement>>()

            // 浣跨敤 VirtualFileManager 鎼滅储鏂囦欢
            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                val baseDir = project.baseDir ?: return@runReadAction
                searchFilesRecursive(baseDir, query, files, maxResults)
            }

            FrontendResponse(
                success = true,
                data = mapOf("files" to JsonArray(files.map { JsonObject(it) }))
            )
        } catch (e: Exception) {
            logger.severe("鉂?Failed to search files: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to search files")
        }
    }

    /**
     * 閫掑綊鎼滅储鏂囦欢
     */
    private fun searchFilesRecursive(
        dir: com.intellij.openapi.vfs.VirtualFile,
        query: String,
        results: MutableList<Map<String, JsonElement>>,
        maxResults: Int
    ) {
        if (results.size >= maxResults) return

        dir.children?.forEach { file ->
            if (results.size >= maxResults) return

            val name = file.name
            if (name.contains(query, ignoreCase = true)) {
                results.add(mapOf(
                    "name" to JsonPrimitive(name),
                    "path" to JsonPrimitive(file.path),
                    "isDirectory" to JsonPrimitive(file.isDirectory)
                ))
            }

            if (file.isDirectory && !name.startsWith(".") && name != "node_modules") {
                searchFilesRecursive(file, query, results, maxResults)
            }
        }
    }

    /**
     * 鑾峰彇鏂囦欢鍐呭
     */
    private fun handleGetFileContent(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing filePath")
        val lineStart = data["lineStart"]?.toString()?.trim('"')?.toIntOrNull()
        val lineEnd = data["lineEnd"]?.toString()?.trim('"')?.toIntOrNull()

        return try {
            var content: String? = null

            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                val fileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                val file = fileManager.findFileByUrl("file://$filePath")
                    ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)

                if (file != null && !file.isDirectory) {
                    val fullContent = String(file.contentsToByteArray(), Charsets.UTF_8)

                    content = if (lineStart != null) {
                        val lines = fullContent.lines()
                        val start = (lineStart - 1).coerceAtLeast(0)
                        val end = (lineEnd ?: lineStart).coerceAtMost(lines.size)
                        lines.subList(start, end).joinToString("\n")
                    } else {
                        fullContent
                    }
                }
            }

            if (content != null) {
                FrontendResponse(
                    success = true,
                    data = mapOf("content" to JsonPrimitive(content))
                )
            } else {
                FrontendResponse(false, error = "File not found: $filePath")
            }
        } catch (e: Exception) {
            logger.severe("鉂?Failed to get file content: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to get file content")
        }
    }

    /**
     * 鎵撳紑鏂囦欢
     */
    private fun handleOpenFile(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing filePath")
        val line = data["line"]?.toString()?.trim('"')?.toIntOrNull()
        val column = data["column"]?.toString()?.trim('"')?.toIntOrNull()

        return try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                val file = fileManager.findFileByUrl("file://$filePath")
                    ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)

                if (file != null) {
                    val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    fileEditorManager.openFile(file, true)

                    // 濡傛灉鎸囧畾浜嗚鍙凤紝璺宠浆鍒版寚瀹氫綅缃?
                    if (line != null && line > 0) {
                        val editor = fileEditorManager.selectedTextEditor
                        if (editor != null) {
                            val lineIndex = (line - 1).coerceAtLeast(0)
                            val offset = editor.document.getLineStartOffset(lineIndex.coerceAtMost(editor.document.lineCount - 1))
                            val targetOffset = if (column != null && column > 0) {
                                offset + (column - 1)
                            } else {
                                offset
                            }
                            editor.caretModel.moveToOffset(targetOffset.coerceAtMost(editor.document.textLength))
                            editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                        }
                    }

                    logger.info("鉁?Opened file: $filePath at line $line")
                } else {
                    logger.warning("鈿狅笍 File not found: $filePath")
                }
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("鉂?Failed to open file: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to open file")
        }
    }

    /**
     * 鏄剧ず鏂囦欢宸紓瀵规瘮
     */
    private fun handleShowDiff(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing filePath")
        val oldContent = data["oldContent"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing oldContent")
        val newContent = data["newContent"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing newContent")
        val title = data["title"]?.toString()?.trim('"') ?: "鏂囦欢宸紓瀵规瘮"

        return try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileName = java.io.File(filePath).name

                // 鍒涘缓铏氭嫙鏂囦欢鍐呭
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                val leftContent = DiffContentFactory.getInstance()
                    .create(project, oldContent, fileType)

                val rightContent = DiffContentFactory.getInstance()
                    .create(project, newContent, fileType)

                // 鍒涘缓 diff 璇锋眰
                val diffRequest = SimpleDiffRequest(
                    title,
                    leftContent,
                    rightContent,
                    "原内容",
                    "新内容"
                )

                // 鏄剧ず diff 瀵硅瘽妗?
                DiffManager.getInstance().showDiff(project, diffRequest)

                logger.info("鉁?Showing diff for: $filePath")
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("鉂?Failed to show diff: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to show diff")
        }
    }

    /**
     * 棰滆壊杞崄鍏繘鍒?
     */
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}

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

    // Claude 操作处理器
    private val claudeHandler = ClaudeActionHandler(project, this, scope)

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
            "ide.openFile" -> handleOpenFile(request)
            "ide.showDiff" -> handleShowDiff(request)
            "ide.searchFiles" -> handleSearchFiles(request)
            "ide.getFileContent" -> handleGetFileContent(request)
            else -> FrontendResponse(false, error = "Unknown IDE action: ${request.action}")
        }
    }

    /**
     * 处理 Claude 操作
     */
    private fun handleClaudeAction(request: FrontendRequest): FrontendResponse {
        return claudeHandler.handle(request)
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
     * 搜索文件
     */
    private fun handleSearchFiles(request: FrontendRequest): FrontendResponse {
        val data = request.data ?: return FrontendResponse(false, error = "Missing data")
        val query = data["query"]?.toString() ?: return FrontendResponse(false, error = "Missing query")
        val maxResults = data["maxResults"]?.toString()?.toIntOrNull() ?: 20

        return try {
            val files = mutableListOf<Map<String, JsonElement>>()

            // 使用 VirtualFileManager 搜索文件
            com.intellij.openapi.application.ApplicationManager.getApplication().runReadAction {
                val baseDir = project.baseDir ?: return@runReadAction
                searchFilesRecursive(baseDir, query, files, maxResults)
            }

            FrontendResponse(
                success = true,
                data = mapOf("files" to JsonArray(files.map { JsonObject(it) }))
            )
        } catch (e: Exception) {
            logger.severe("❌ Failed to search files: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to search files")
        }
    }

    /**
     * 递归搜索文件
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
     * 获取文件内容
     */
    private fun handleGetFileContent(request: FrontendRequest): FrontendResponse {
        val data = request.data ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString() ?: return FrontendResponse(false, error = "Missing filePath")
        val lineStart = data["lineStart"]?.toString()?.toIntOrNull()
        val lineEnd = data["lineEnd"]?.toString()?.toIntOrNull()

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
            logger.severe("❌ Failed to get file content: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to get file content")
        }
    }

    /**
     * 打开文件
     */
    private fun handleOpenFile(request: FrontendRequest): FrontendResponse {
        val data = request.data ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString() ?: return FrontendResponse(false, error = "Missing filePath")
        val line = data["line"]?.toString()?.toIntOrNull()
        val column = data["column"]?.toString()?.toIntOrNull()

        return try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                val file = fileManager.findFileByUrl("file://$filePath")
                    ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)

                if (file != null) {
                    val fileEditorManager = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project)
                    fileEditorManager.openFile(file, true)

                    // 如果指定了行号，跳转到指定位置
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

                    logger.info("✅ Opened file: $filePath at line $line")
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
     */
    private fun handleShowDiff(request: FrontendRequest): FrontendResponse {
        val data = request.data ?: return FrontendResponse(false, error = "Missing data")
        val filePath = data["filePath"]?.toString() ?: return FrontendResponse(false, error = "Missing filePath")
        val oldContent = data["oldContent"]?.toString() ?: return FrontendResponse(false, error = "Missing oldContent")
        val newContent = data["newContent"]?.toString() ?: return FrontendResponse(false, error = "Missing newContent")
        val title = data["title"]?.toString() ?: "文件差异对比"

        return try {
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileName = filePath.split(/[\\/]/).last()

                // 创建虚拟文件内容
                val leftContent = com.intellij.diff.contents.DiffContentFactory.getInstance()
                    .create(project, oldContent, com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName(fileName))

                val rightContent = com.intellij.diff.contents.DiffContentFactory.getInstance()
                    .create(project, newContent, com.intellij.openapi.fileTypes.FileTypeManager.getInstance().getFileTypeByFileName(fileName))

                // 创建 diff 请求
                val request = com.intellij.diff.requests.SimpleDiffRequest(
                    title,
                    leftContent,
                    rightContent,
                    "原内容",
                    "新内容"
                )

                // 显示 diff 对话框
                com.intellij.diff.DiffManager.getInstance().showDiff(project, request)

                logger.info("✅ Showing diff for: $filePath")
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("❌ Failed to show diff: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to show diff")
        }
    }

    /**
     * 颜色转十六进制
     */
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}

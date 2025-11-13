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
 * 前后端通信桥接
 * 负责 JCEF 浏览器与 Kotlin 后端的双向通信
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

    // 前端 -> 后端 (Request/Response 模式)
    private val queryHandler = JBCefJSQuery.create(browser as JBCefBrowserBase)

    // 后端 -> 前端 (事件推�?
    private var isReady = false

    // Claude 操作处理�?
    private val claudeHandler = ClaudeActionHandler(project, this, scope)

    // 会话操作处理�?
    private val sessionHandler = SessionActionHandler(project)

    init {
        // 设置 Claude 处理器与会话处理器的关联（双向引用）
        claudeHandler.sessionHandler = sessionHandler
        sessionHandler.claudeHandler = claudeHandler

        setupQueryHandler()
        setupThemeListener()
    }

    /**
     * 注册请求处理�?
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
                logger.severe("�?Error handling request: ${e.message}")
                e.printStackTrace()
                val error = FrontendResponse(
                    success = false,
                    error = e.message ?: "Unknown error"
                )
                JBCefJSQuery.Response(json.encodeToString(error))
            }
        }

        // 注意：JavaScript 桥接脚本必须在页面加载完成后注入
        // 不要在这里调�?injectBridgeScript()
    }

    /**
     * 注入前端可调用的 JavaScript API
     * 必须在页面加载后调用
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
                    console.log('🔧 Injecting IDEA bridge...');

                    // 前端调用后端 (异步)
                    window.ideaBridge = {
                        query: async function(action, data) {
                            console.log('🚀 Bridge query:', action, data);
                            const request = JSON.stringify({ action, data });
                            try {
                                const responseJson = await new Promise((resolve, reject) => {
                                    ${queryHandler.inject("request", "resolve", "reject")}
                                });
                                const response = JSON.parse(responseJson);
                                console.log('�?Bridge response:', response);
                                return response;
                            } catch (error) {
                                console.error('�?Bridge query failed:', error);
                                return { success: false, error: String(error) };
                            }
                        },

                        // 标记桥接已就�?
                        isReady: true
                    };

                    // 后端推送事件给前端
                    window.onIdeEvent = function(event) {
                        console.log('📥 IDE Event:', event);
                        window.dispatchEvent(new CustomEvent('ide-event', { detail: event }));
                    };

                    // 标记桥接已就�?
                    window.__bridgeReady = true;
                    window.dispatchEvent(new Event('bridge-ready'));
                    console.log('�?IDEA bridge ready');
                } catch (error) {
                    console.error('�?Failed to initialize IDEA bridge:', error);
                    window.__bridgeReady = false;
                    const root = document.getElementById('app');
                    if (root && !root.querySelector('.bridge-init-error')) {
                        root.innerHTML = `
                            <div class="bridge-init-error" style="padding:24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#d22;background:rgba(210,34,34,0.08);border:1px solid rgba(210,34,34,0.3);border-radius:8px;">
                                <h3 style="margin-bottom:12px;">IDEA 桥接初始化失�?/h3>
                                <p style="margin-bottom:8px;">请查�?IDE 日志了解详情�?/p>
                                <code style="display:block;white-space:pre-wrap;font-size:12px;color:#a11;">${'$'}{String(error)}</code>
                            </div>`;
                    }
                } finally {
                    markThemeLoaded();
                }
            })();
        """.trimIndent()

        logger.info("🧪 Bridge script preview: ${script.take(200)}...")
        browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
        isReady = true
        logger.info("�?Bridge script injected")
    }

    /**
     * 处理来自前端的请�?
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
                // 先序列化成字符串,再解析成 JsonElement
                val themeJsonString = json.encodeToString(theme)
                val themeJson = json.parseToJsonElement(themeJsonString)
                FrontendResponse(
                    success = true,
                    data = mapOf("theme" to themeJson)
                )
            }
            "ide.getServerUrl" -> {
                val httpServerService = com.claudecodeplus.server.HttpServerProjectService.getInstance(project)
                val serverUrl = httpServerService.serverUrl ?: "δ����"
                FrontendResponse(
                    success = true,
                    data = mapOf("serverUrl" to JsonPrimitive(serverUrl))
                )
            }
            "ide.getProjectPath" -> {
                val projectPath = project.basePath ?: project.projectFilePath ?: "δ֪"
                FrontendResponse(
                    success = true,
                    data = mapOf("projectPath" to JsonPrimitive(projectPath))
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
     * 处理会话操作
     */
    private fun handleSessionAction(request: FrontendRequest): FrontendResponse {
        return sessionHandler.handle(request)
    }

    /**
     * 推送事件给前端
     */
    override fun pushEvent(event: IdeEvent) {
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
            logger.severe("�?Failed to push event: ${e.message}")
        }
    }

    /**
     * 设置主题监听�?
     */
    private fun setupThemeListener() {
        ApplicationManager.getApplication().messageBus
            .connect()
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                try {
                    logger.info("🎨 Theme changed, notifying frontend")
                    val theme = extractIdeTheme()
                    // 先序列化成字符串,再解析成 JsonElement
                    val themeJsonString = json.encodeToString(theme)
                    val themeJson = json.parseToJsonElement(themeJsonString)
                    pushEvent(IdeEvent(
                        type = "theme.changed",
                        data = mapOf("theme" to themeJson)
                    ))
                } catch (e: Exception) {
                    logger.severe("�?Failed to notify theme change: ${e.message}")
                    e.printStackTrace()
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

    /**
     * 搜索文件
     */
    private fun handleSearchFiles(request: FrontendRequest): FrontendResponse {
        val data = request.data?.let { json.decodeFromJsonElement<Map<String, JsonElement>>(it) }
            ?: return FrontendResponse(false, error = "Missing data")
        val query = data["query"]?.toString()?.trim('"') ?: return FrontendResponse(false, error = "Missing query")
        val maxResults = data["maxResults"]?.toString()?.trim('"')?.toIntOrNull() ?: 20

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
            logger.severe("�?Failed to search files: ${e.message}")
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
            logger.severe("�?Failed to get file content: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to get file content")
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
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                val file = fileManager.findFileByUrl("file://$filePath")
                    ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)

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
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                val fileName = java.io.File(filePath).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                // 确定要显示的内容
                val (finalOldContent, finalNewContent, finalTitle) = if (rebuildFromFile) {
                    // 从文件重建完整 Diff（对齐 Compose UI 实现）
                    val fileManager = com.intellij.openapi.vfs.VirtualFileManager.getInstance()
                    val file = fileManager.findFileByUrl("file://$filePath")
                        ?: com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(filePath)
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
                val leftContent = DiffContentFactory.getInstance()
                    .create(project, finalOldContent, fileType)

                val rightContent = DiffContentFactory.getInstance()
                    .create(project, finalNewContent, fileType)

                // 创建 diff 请求
                val diffRequest = SimpleDiffRequest(
                    finalTitle,
                    leftContent,
                    rightContent,
                    "$fileName (修改前)",
                    "$fileName (修改后)"
                )

                // 显示 diff 对话框
                DiffManager.getInstance().showDiff(project, diffRequest)

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
     * 颜色转十六进�?
     */
    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }
}


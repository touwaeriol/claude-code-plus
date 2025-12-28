package com.asakii.plugin.bridge

import com.asakii.plugin.theme.IdeaThemeAdapter
import com.asakii.rpc.api.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.util.PropertiesComponent
import com.asakii.plugin.compat.LocalizationCompat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Logger

/**
 * JetBrains IDE 集成 API 实现
 *
 * 使用组合模式：
 * - jetbrainsApi.capabilities.isSupported()
 * - jetbrainsApi.file.openFile(...)
 * - jetbrainsApi.theme.get()
 * - jetbrainsApi.session.getState()
 * - jetbrainsApi.locale.get()
 */
class JetBrainsApiImpl(private val ideaProject: Project) : JetBrainsApi {

    private val logger = Logger.getLogger(JetBrainsApiImpl::class.java.name)

    override val capabilities = CapabilitiesApiImpl()
    override val file = FileApiImpl()
    override val theme = ThemeApiImpl()
    override val session = SessionApiImpl()
    override val locale = LocaleApiImpl()
    override val project = ProjectApiImpl()

    // ========== 能力检测 API 实现 ==========

    inner class CapabilitiesApiImpl : JetBrainsCapabilitiesApi {
        override fun isSupported() = true
        override fun get() = JetBrainsCapabilities(supported = true, version = "1.0")
    }

    // ========== 文件操作 API 实现 ==========

    inner class FileApiImpl : JetBrainsFileApi {
        override fun openFile(request: JetBrainsOpenFileRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    val file = LocalFileSystem.getInstance().findFileByIoFile(File(request.filePath))
                    if (file != null) {
                        val fileEditorManager = FileEditorManager.getInstance(ideaProject)
                        val line = request.line
                        val column = request.column
                        val startOffset = request.startOffset  // 1-based 行号
                        val endOffset = request.endOffset      // 1-based 行号

                        // 确定跳转的目标行
                        val targetLine = when {
                            startOffset != null && startOffset > 0 -> startOffset
                            line != null && line > 0 -> line
                            else -> null
                        }

                        val descriptor = if (targetLine != null) {
                            OpenFileDescriptor(
                                ideaProject,
                                file,
                                targetLine - 1,  // 转为 0-based
                                column?.let { it - 1 } ?: 0
                            )
                        } else {
                            OpenFileDescriptor(ideaProject, file)
                        }

                        val editor = fileEditorManager.openTextEditor(descriptor, true)

                        // 如果有行范围参数，选中指定行范围
                        if (editor != null && startOffset != null && endOffset != null && startOffset > 0 && endOffset >= startOffset) {
                            val document = editor.document
                            val lineCount = document.lineCount

                            // 确保行号在有效范围内（转换为 0-based）
                            val startLine = (startOffset - 1).coerceIn(0, lineCount - 1)
                            val endLine = (endOffset - 1).coerceIn(0, lineCount - 1)

                            // 获取行的字符偏移量
                            val startCharOffset = document.getLineStartOffset(startLine)
                            val endCharOffset = document.getLineEndOffset(endLine)

                            // 选中指定行范围
                            editor.selectionModel.setSelection(startCharOffset, endCharOffset)

                            logger.info("✅ [JetBrainsApi.file] Opened with selection: ${request.filePath} (lines $startOffset-$endOffset)")
                        } else {
                            logger.info("✅ [JetBrainsApi.file] Opened: ${request.filePath}")
                        }
                    } else {
                        logger.warning("⚠️ [JetBrainsApi.file] Not found: ${request.filePath}")
                    }
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to open: ${e.message}")
                Result.failure(e)
            }
        }

        override fun showDiff(request: JetBrainsShowDiffRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    val fileName = File(request.filePath).name
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val leftContent = DiffContentFactory.getInstance()
                        .create(ideaProject, request.oldContent, fileType)
                    val rightContent = DiffContentFactory.getInstance()
                        .create(ideaProject, request.newContent, fileType)

                    val diffRequest = SimpleDiffRequest(
                        request.title ?: "File Diff: $fileName",
                        leftContent,
                        rightContent,
                        "$fileName (before)",
                        "$fileName (after)"
                    )

                    DiffManager.getInstance().showDiff(ideaProject, diffRequest)
                    logger.info("✅ [JetBrainsApi.file] Showing diff: ${request.filePath}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to show diff: ${e.message}")
                Result.failure(e)
            }
        }

        override fun showMultiEditDiff(request: JetBrainsShowMultiEditDiffRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                        ?: throw IllegalStateException("File not found: ${request.filePath}")
                    file.refresh(false, false)
                    val currentContent = request.currentContent
                        ?: String(file.contentsToByteArray(), Charsets.UTF_8)

                    val beforeContent = rebuildBeforeContent(currentContent, request.edits)
                    val fileName = File(request.filePath).name
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val leftContent = DiffContentFactory.getInstance()
                        .create(ideaProject, beforeContent, fileType)
                    val rightContent = DiffContentFactory.getInstance()
                        .create(ideaProject, currentContent, fileType)

                    val diffRequest = SimpleDiffRequest(
                        "File Changes: $fileName (${request.edits.size} edits)",
                        leftContent,
                        rightContent,
                        "$fileName (before)",
                        "$fileName (after)"
                    )

                    DiffManager.getInstance().showDiff(ideaProject, diffRequest)
                    logger.info("✅ [JetBrainsApi.file] Showing multi-edit diff: ${request.filePath}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to show multi-edit diff: ${e.message}")
                Result.failure(e)
            }
        }

        override fun showEditPreviewDiff(request: JetBrainsShowEditPreviewRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                    file?.refresh(false, false)
                    val currentContent = file?.let { String(it.contentsToByteArray(), Charsets.UTF_8) } ?: ""

                    // 依次应用所有编辑操作得到预览后的内容
                    var afterContent = currentContent
                    for (edit in request.edits) {
                        afterContent = if (edit.replaceAll) {
                            afterContent.replace(edit.oldString, edit.newString)
                        } else {
                            val index = afterContent.indexOf(edit.oldString)
                            if (index >= 0) {
                                buildString {
                                    append(afterContent.substring(0, index))
                                    append(edit.newString)
                                    append(afterContent.substring(index + edit.oldString.length))
                                }
                            } else {
                                // 找不到时保持不变，记录警告
                                logger.warning("⚠️ [JetBrainsApi.file] oldString not found in file, skipping edit")
                                afterContent
                            }
                        }
                    }

                    val fileName = File(request.filePath).name
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val leftContent = DiffContentFactory.getInstance()
                        .create(ideaProject, currentContent, fileType)
                    val rightContent = DiffContentFactory.getInstance()
                        .create(ideaProject, afterContent, fileType)

                    val diffRequest = SimpleDiffRequest(
                        request.title ?: "Edit Preview: $fileName",
                        leftContent,
                        rightContent,
                        "$fileName (current)",
                        "$fileName (after edit)"
                    )

                    DiffManager.getInstance().showDiff(ideaProject, diffRequest)
                    logger.info("✅ [JetBrainsApi.file] Showing edit preview diff: ${request.filePath}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to show edit preview diff: ${e.message}")
                Result.failure(e)
            }
        }

        override fun showEditFullDiff(request: JetBrainsShowEditFullDiffRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                    file?.refresh(false, false)
                    val currentContent = file?.let { String(it.contentsToByteArray(), Charsets.UTF_8) } ?: ""

                    // 优先使用缓存的原始内容，否则通过反向替换计算
                    val beforeContent = if (!request.originalContent.isNullOrEmpty()) {
                        // 使用缓存的原始内容
                        logger.info("✅ [JetBrainsApi.file] Using cached original content for diff")
                        request.originalContent
                    } else {
                        // 回退：通过反向替换计算修改前的内容
                        logger.info("⚠️ [JetBrainsApi.file] No cached content, using reverse replacement")
                        if (request.replaceAll) {
                            currentContent.replace(request.newString, request.oldString)
                        } else {
                            val index = currentContent.indexOf(request.newString)
                            if (index >= 0) {
                                buildString {
                                    append(currentContent.substring(0, index))
                                    append(request.oldString)
                                    append(currentContent.substring(index + request.newString.length))
                                }
                            } else {
                                // newString 不在文件中，可能文件已被修改，使用当前内容
                                logger.warning("⚠️ [JetBrainsApi.file] newString not found in file, showing current content")
                                currentContent
                            }
                        }
                    }

                    // 计算修改后的内容：在原始内容上应用 oldString → newString 替换
                    val afterContent = if (!request.originalContent.isNullOrEmpty()) {
                        // 有原始内容时，应用正向替换得到修改后的内容
                        if (request.replaceAll) {
                            beforeContent.replace(request.oldString, request.newString)
                        } else {
                            val index = beforeContent.indexOf(request.oldString)
                            if (index >= 0) {
                                buildString {
                                    append(beforeContent.substring(0, index))
                                    append(request.newString)
                                    append(beforeContent.substring(index + request.oldString.length))
                                }
                            } else {
                                // oldString 不在原始内容中，使用当前文件内容
                                currentContent
                            }
                        }
                    } else {
                        // 没有原始内容时，使用当前文件内容作为修改后的内容
                        currentContent
                    }

                    val fileName = File(request.filePath).name
                    val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                    val leftContent = DiffContentFactory.getInstance()
                        .create(ideaProject, beforeContent, fileType)
                    val rightContent = DiffContentFactory.getInstance()
                        .create(ideaProject, afterContent, fileType)

                    val diffRequest = SimpleDiffRequest(
                        request.title ?: "Edit: $fileName",
                        leftContent,
                        rightContent,
                        "$fileName (before)",
                        "$fileName (after)"
                    )

                    DiffManager.getInstance().showDiff(ideaProject, diffRequest)
                    logger.info("✅ [JetBrainsApi.file] Showing edit full diff: ${request.filePath}")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to show edit full diff: ${e.message}")
                Result.failure(e)
            }
        }

        override fun showMarkdown(request: JetBrainsShowMarkdownRequest): Result<Unit> {
            return try {
                ApplicationManager.getApplication().invokeLater {
                    // 创建临时虚拟文件并打开（IDEA 会自动渲染 Markdown）
                    val fileName = request.title?.let { "$it.md" } ?: "plan-preview.md"
                    val tempFile = LightVirtualFile(
                        fileName,
                        FileTypeManager.getInstance().getFileTypeByExtension("md"),
                        request.content
                    )
                    FileEditorManager.getInstance(ideaProject).openFile(tempFile, true)
                    logger.info("✅ [JetBrainsApi.file] Showing markdown: $fileName")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to show markdown: ${e.message}")
                Result.failure(e)
            }
        }

        override fun getActiveFile(): ActiveFileInfo? {
            return try {
                var result: ActiveFileInfo? = null
                ApplicationManager.getApplication().invokeAndWait {
                    ApplicationManager.getApplication().runReadAction {
                        val fileEditorManager = FileEditorManager.getInstance(ideaProject)
                        val selectedTextEditor = fileEditorManager.selectedTextEditor
                        val selectedFile = fileEditorManager.selectedFiles.firstOrNull()
                        val projectPath = ideaProject.basePath ?: ""

                        if (selectedFile != null) {
                            val absolutePath = selectedFile.path
                            val relativePath = if (absolutePath.startsWith(projectPath)) {
                                absolutePath.removePrefix(projectPath).removePrefix("/").removePrefix("\\")
                            } else {
                                absolutePath
                            }
                            val fileName = selectedFile.name

                            // 获取光标位置和选区信息
                            val editor = selectedTextEditor
                            if (editor != null) {
                                val caretModel = editor.caretModel
                                val selectionModel = editor.selectionModel
                                val document = editor.document

                                val caretOffset = caretModel.offset
                                val line = document.getLineNumber(caretOffset) + 1
                                val column = caretOffset - document.getLineStartOffset(line - 1) + 1

                                val hasSelection = selectionModel.hasSelection()
                                var startLine: Int? = null
                                var startColumn: Int? = null
                                var endLine: Int? = null
                                var endColumn: Int? = null
                                var selectedContent: String? = null

                                if (hasSelection) {
                                    val startOffset = selectionModel.selectionStart
                                    val endOffset = selectionModel.selectionEnd
                                    startLine = document.getLineNumber(startOffset) + 1
                                    startColumn = startOffset - document.getLineStartOffset(startLine - 1) + 1
                                    endLine = document.getLineNumber(endOffset) + 1
                                    endColumn = endOffset - document.getLineStartOffset(endLine - 1) + 1
                                    selectedContent = selectionModel.selectedText
                                }

                                result = ActiveFileInfo(
                                    path = absolutePath,
                                    relativePath = relativePath,
                                    name = fileName,
                                    line = line,
                                    column = column,
                                    hasSelection = hasSelection,
                                    startLine = startLine,
                                    startColumn = startColumn,
                                    endLine = endLine,
                                    endColumn = endColumn,
                                    selectedContent = selectedContent
                                )
                                logger.info("✅ [JetBrainsApi.file] Active file: $relativePath")
                            } else {
                                // 非文本编辑器，只返回路径
                                result = ActiveFileInfo(
                                    path = absolutePath,
                                    relativePath = relativePath,
                                    name = fileName
                                )
                                logger.info("✅ [JetBrainsApi.file] Active file (no editor): $relativePath")
                            }
                        }
                    }
                }
                result
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e  // 必须重新抛出
            } catch (e: Exception) {
                logger.severe("❌ [JetBrainsApi.file] Failed to get active file: ${e.message}")
                null
            }
        }

        private fun rebuildBeforeContent(afterContent: String, edits: List<JetBrainsEditOperation>): String {
            var content = afterContent
            for (operation in edits.asReversed()) {
                if (operation.replaceAll) {
                    if (!content.contains(operation.newString)) {
                        throw IllegalStateException("Rebuild failed: newString not found (replace_all)")
                    }
                    content = content.replace(operation.newString, operation.oldString)
                } else {
                    val index = content.indexOf(operation.newString)
                    if (index < 0) {
                        throw IllegalStateException("Rebuild failed: newString not found")
                    }
                    content = buildString {
                        append(content.substring(0, index))
                        append(operation.oldString)
                        append(content.substring(index + operation.newString.length))
                    }
                }
            }
            return content
        }
    }

    // ========== 主题 API 实现 ==========

    inner class ThemeApiImpl : JetBrainsThemeApi {
        private val changeListeners = CopyOnWriteArrayList<(JetBrainsIdeTheme) -> Unit>()

        init {
            // 监听 IDE 主题变化
            IdeaThemeAdapter.registerThemeChangeListener { _ ->
                val theme = get()
                changeListeners.forEach { it(theme) }
            }
        }

        override fun get(): JetBrainsIdeTheme {
            val editorScheme = EditorColorsManager.getInstance().globalScheme
            val editorFontName = editorScheme.editorFontName
            val editorFontSize = editorScheme.editorFontSize

            val uiFont = JBUI.Fonts.label()
            val uiFontSize = uiFont.size
            logger.info("🔤 [Theme] fontFamily=${uiFont.family}, fontSize=$uiFontSize")

            return JetBrainsIdeTheme(
                background = colorToHex(UIUtil.getPanelBackground()),
                foreground = colorToHex(UIUtil.getLabelForeground()),
                borderColor = colorToHex(JBColor.border()),
                panelBackground = colorToHex(UIUtil.getPanelBackground()),
                textFieldBackground = colorToHex(UIUtil.getTextFieldBackground()),
                selectionBackground = colorToHex(UIUtil.getListSelectionBackground(true)),
                selectionForeground = colorToHex(UIUtil.getListSelectionForeground(true)),
                linkColor = colorToHex(JBUI.CurrentTheme.Link.Foreground.ENABLED),
                errorColor = colorToHex(UIUtil.getErrorForeground()),
                warningColor = colorToHex(JBColor.namedColor("Label.warningForeground", JBColor.YELLOW)),
                successColor = colorToHex(JBColor.namedColor("Label.successForeground", JBColor(0x59A869, 0x499C54))),
                separatorColor = colorToHex(JBColor.border()),
                hoverBackground = colorToHex(JBColor.namedColor("List.hoverBackground", JBColor(0xF3F4F6, 0x2A2D2E))),
                accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor(0x0366D6, 0x0E639C))),
                infoBackground = colorToHex(JBUI.CurrentTheme.Banner.INFO_BACKGROUND),
                codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
                secondaryForeground = colorToHex(JBUI.CurrentTheme.Label.foreground(false)),
                fontFamily = uiFont.getFamily(),
                fontSize = uiFontSize,
                editorFontFamily = editorFontName,
                editorFontSize = editorFontSize
            )
        }

        override fun addChangeListener(listener: (JetBrainsIdeTheme) -> Unit): () -> Unit {
            changeListeners.add(listener)
            return { changeListeners.remove(listener) }
        }

        private fun colorToHex(color: Color): String {
            return "#%02x%02x%02x".format(color.red, color.green, color.blue)
        }
    }

    // ========== 会话管理 API 实现 ==========

    inner class SessionApiImpl : JetBrainsSessionApi {
        private var currentState: JetBrainsSessionState? = null
        private val stateListeners = CopyOnWriteArrayList<(JetBrainsSessionState) -> Unit>()
        private val commandListeners = CopyOnWriteArrayList<(JetBrainsSessionCommand) -> Unit>()

        override fun receiveState(state: JetBrainsSessionState) {
            currentState = state
            logger.info("[JetBrainsApi.session] Received state: ${state.sessions.size} sessions, active=${state.activeSessionId}")
            stateListeners.forEach { it(state) }
        }

        override fun getState(): JetBrainsSessionState? = currentState

        override fun addStateListener(listener: (JetBrainsSessionState) -> Unit): () -> Unit {
            stateListeners.add(listener)
            currentState?.let { listener(it) }
            return { stateListeners.remove(listener) }
        }

        override fun sendCommand(command: JetBrainsSessionCommand) {
            logger.info("[JetBrainsApi.session] Sending command: ${command.type}")
            commandListeners.forEach { it(command) }
        }

        override fun addCommandListener(listener: (JetBrainsSessionCommand) -> Unit): () -> Unit {
            commandListeners.add(listener)
            return { commandListeners.remove(listener) }
        }
    }

    // ========== 语言设置 API 实现 ==========

    inner class LocaleApiImpl : JetBrainsLocaleApi {
        private val PREFERRED_LOCALE_KEY = "com.asakii.locale"

        override fun get(): String {
            val preferred = PropertiesComponent.getInstance().getValue(PREFERRED_LOCALE_KEY)
            if (!preferred.isNullOrBlank()) {
                return preferred
            }

            return try {
                val locale = LocalizationCompat.getLocale()
                "${locale.language}-${locale.country}"
            } catch (e: Exception) {
                val locale = Locale.getDefault()
                "${locale.language}-${locale.country}"
            }
        }

        override fun set(locale: String): Result<Unit> {
            return try {
                PropertiesComponent.getInstance().setValue(PREFERRED_LOCALE_KEY, locale)
                logger.info("[JetBrainsApi.locale] Set to: $locale")
                Result.success(Unit)
            } catch (e: Exception) {
                logger.warning("[JetBrainsApi.locale] Failed to set: ${e.message}")
                Result.failure(e)
            }
        }
    }

    // ========== 项目信息 API 实现 ==========

    inner class ProjectApiImpl : JetBrainsProjectApi {
        override fun getPath(): String = ideaProject.basePath ?: ""
    }
}

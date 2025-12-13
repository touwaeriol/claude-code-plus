package com.asakii.plugin.bridge

import com.asakii.plugin.theme.IdeaThemeAdapter
import com.asakii.rpc.api.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.util.PropertiesComponent
import com.intellij.l10n.LocalizationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
                        val startOffset = request.startOffset
                        val descriptor = when {
                            line != null && line > 0 -> {
                                OpenFileDescriptor(
                                    ideaProject,
                                    file,
                                    line - 1,
                                    column?.let { it - 1 } ?: 0
                                )
                            }
                            startOffset != null -> {
                                OpenFileDescriptor(ideaProject, file, startOffset)
                            }
                            else -> {
                                OpenFileDescriptor(ideaProject, file)
                            }
                        }
                        fileEditorManager.openTextEditor(descriptor, true)
                        logger.info("✅ [JetBrainsApi.file] Opened: ${request.filePath}")
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
            val uiFontFamily = uiFont.family
            val uiFontSize = uiFont.size

            return JetBrainsIdeTheme(
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
                hoverBackground = colorToHex(JBColor.namedColor("List.hoverBackground", UIUtil.getPanelBackground())),
                accentColor = colorToHex(JBColor.namedColor("Accent.focusColor", JBColor.BLUE)),
                infoBackground = colorToHex(JBColor.namedColor("Component.infoForeground", JBColor.GRAY)),
                codeBackground = colorToHex(UIUtil.getTextFieldBackground()),
                secondaryForeground = colorToHex(JBColor.GRAY),
                fontFamily = "$uiFontFamily, -apple-system, BlinkMacSystemFont, sans-serif",
                fontSize = uiFontSize,
                editorFontFamily = "$editorFontName, JetBrains Mono, Consolas, monospace",
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
                val locale = LocalizationUtil.getLocale(true)
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

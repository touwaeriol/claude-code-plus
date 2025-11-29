package com.asakii.plugin.bridge

import com.asakii.bridge.FrontendRequest
import com.asakii.bridge.FrontendResponse
import com.asakii.rpc.api.IdeTheme
import com.asakii.server.IdeActionBridge
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.openapi.fileTypes.FileTypeManager
import kotlinx.serialization.Serializable
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File
import java.util.logging.Logger
import com.intellij.openapi.project.Project
import com.intellij.l10n.LocalizationUtil
import java.util.Locale
import com.intellij.ide.util.PropertiesComponent

class IdeActionBridgeImpl(private val project: Project) : IdeActionBridge {

    private val PREFERRED_LOCALE_KEY = "com.asakii.locale"

    override fun getTheme(): IdeTheme {
        return IdeTheme(
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
            secondaryForeground = colorToHex(JBColor.GRAY)
        )
    }

    private fun colorToHex(color: Color): String {
        return "#%02x%02x%02x".format(color.red, color.green, color.blue)
    }

    override fun getProjectPath(): String {
        return project.basePath ?: ""
    }
    
    override fun getLocale(): String {
        // Check for user preference first
        val preferred = PropertiesComponent.getInstance().getValue(PREFERRED_LOCALE_KEY)
        if (!preferred.isNullOrBlank()) {
            return preferred
        }

        return try {
            val locale = LocalizationUtil.getLocale(true) // true for restarting (though we just need the value)
            "${locale.language}-${locale.country}"
        } catch (e: Exception) {
            val locale = Locale.getDefault()
            "${locale.language}-${locale.country}"
        }
    }

    override fun setLocale(locale: String): Boolean {
        try {
            PropertiesComponent.getInstance().setValue(PREFERRED_LOCALE_KEY, locale)
            logger.info("Locale preference set to: $locale")
            return true
        } catch (e: Exception) {
            logger.warning("Failed to set locale preference: ${e.message}")
            return false
        }
    }

    private val logger = Logger.getLogger(IdeActionBridgeImpl::class.java.name)

    override fun openFile(request: FrontendRequest): FrontendResponse {
        val data = request.data?.jsonObject
        val filePath = data?.get("filePath")?.jsonPrimitive?.contentOrNull
        val line = data?.get("line")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val column = data?.get("column")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
        val content = data?.get("content")?.jsonPrimitive?.contentOrNull
        val selectContent = data?.get("selectContent")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false

        if (filePath == null) {
            return FrontendResponse(false, error = "Missing filePath")
        }

        return try {
            ApplicationManager.getApplication().invokeLater {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(filePath))
                if (file != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val descriptor = if (line != null && line > 0) {
                        OpenFileDescriptor(project, file, line - 1, column?.let { it - 1 } ?: 0)
                    } else {
                        OpenFileDescriptor(project, file)
                    }
                    fileEditorManager.openTextEditor(descriptor, true)
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

    override fun showDiff(request: FrontendRequest): FrontendResponse {
        val data = request.data?.jsonObject
        val filePath = data?.get("filePath")?.jsonPrimitive?.contentOrNull
        val oldContent = data?.get("oldContent")?.jsonPrimitive?.contentOrNull
        val newContent = data?.get("newContent")?.jsonPrimitive?.contentOrNull
        val title = data?.get("title")?.jsonPrimitive?.contentOrNull
        val rebuildFromFile = data?.get("rebuildFromFile")?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
        val editsJson = data?.get("edits")

        if (filePath == null) {
            return FrontendResponse(false, error = "Missing filePath")
        }

        return try {
            ApplicationManager.getApplication().invokeLater {
                val fileName = File(filePath).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)

                val (finalOldContent, finalNewContent, finalTitle) = if (rebuildFromFile) {
                    val file = LocalFileSystem.getInstance().findFileByPath(filePath)
                        ?: throw IllegalStateException("File not found: $filePath")
                    file.refresh(false, false)
                    val currentContent = String(file.contentsToByteArray(), Charsets.UTF_8)

                    val edits = if (editsJson != null && editsJson is kotlinx.serialization.json.JsonArray) {
                        editsJson.map { editElement ->
                            val editObj = editElement as? kotlinx.serialization.json.JsonObject
                                ?: throw IllegalArgumentException("Invalid edit format")
                            EditOperation(
                                oldString = editObj["oldString"]?.jsonPrimitive?.content ?: "",
                                newString = editObj["newString"]?.jsonPrimitive?.content ?: "",
                                replaceAll = editObj["replaceAll"]?.jsonPrimitive?.content?.toBoolean() ?: false
                            )
                        }
                    } else {
                        listOf(EditOperation(oldString = oldContent ?: "", newString = newContent ?: "", replaceAll = false))
                    }

                    val rebuiltOldContent = rebuildBeforeContent(currentContent, edits)

                    Triple(
                        rebuiltOldContent,
                        currentContent,
                        title ?: "File Changes: $fileName (${edits.size} edits)"
                    )
                } else {
                    Triple(oldContent!!, newContent!!, title ?: "File Diff: $fileName")
                }

                val leftContent = DiffContentFactory.getInstance()
                    .create(project, finalOldContent, fileType)

                val rightContent = DiffContentFactory.getInstance()
                    .create(project, finalNewContent, fileType)

                val diffRequest = SimpleDiffRequest(
                    finalTitle,
                    leftContent,
                    rightContent,
                    "$fileName (before)",
                    "$fileName (after)"
                )

                DiffManager.getInstance().showDiff(project, diffRequest)

                logger.info("✅ Showing diff for: $filePath")
            }

            FrontendResponse(success = true)
        } catch (e: Exception) {
            logger.severe("❌ Failed to show diff: ${e.message}")
            FrontendResponse(false, error = e.message ?: "Failed to show diff")
        }
    }

    @Serializable
    private data class EditOperation(
        val oldString: String,
        val newString: String,
        val replaceAll: Boolean
    )

    private fun rebuildBeforeContent(afterContent: String, operations: List<EditOperation>): String {
        var content = afterContent
        for (operation in operations.asReversed()) {
            if (operation.replaceAll) {
                if (!content.contains(operation.newString)) {
                    throw IllegalStateException(
                        "Rebuild failed: newString not found (replace_all)\n" +
                        "Expected to find: ${operation.newString.take(100)}..."
                    )
                }
                content = content.replace(operation.newString, operation.oldString)
            } else {
                val index = content.indexOf(operation.newString)
                if (index < 0) {
                    throw IllegalStateException(
                        "Rebuild failed: newString not found\n" +
                        "Expected to find: ${operation.newString.take(100)}..."
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

    override fun searchFiles(query: String, maxResults: Int): List<String> {
        val result = mutableListOf<String>()
        ApplicationManager.getApplication().runReadAction {
            val projectScope = GlobalSearchScope.projectScope(project)
            val files = FilenameIndex.getAllFilesByExt(project, "kt", projectScope)
            result.addAll(files.mapNotNull { it.path })
        }
        return result.take(maxResults)
    }

    override fun getRecentFiles(maxResults: Int): List<String> {
        return FileEditorManager.getInstance(project).openFiles.mapNotNull { it.path }.take(maxResults)
    }
}

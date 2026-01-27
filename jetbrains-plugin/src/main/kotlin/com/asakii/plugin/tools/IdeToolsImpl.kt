package com.asakii.plugin.tools

import com.asakii.claude.agent.sdk.types.AgentDefinition
import com.asakii.rpc.api.*
import com.asakii.settings.AgentDefaults
import com.asakii.settings.AgentSettingsService
import kotlinx.serialization.json.*
import com.asakii.rpc.api.ActiveFileInfo
import com.asakii.server.tools.IdeToolsDefault
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.ide.util.PropertiesComponent
import com.asakii.plugin.compat.LocalizationCompat
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ide.BrowserUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.io.File
import java.util.Locale
import com.intellij.openapi.diagnostic.Logger
import com.asakii.plugin.logging.*

/**
 * IDE 工具 IDEA 实现（继承默认实现，覆盖 IDEA 特有方法）
 *
 * - 继承 IdeToolsDefault 的通用实现（文件搜索、内容读取等）
 * - 覆盖需要 IDEA Platform API 的方法（openFile、showDiff、getTheme 等）
 */
class IdeToolsImpl(
    private val project: Project
) : IdeToolsDefault(project.basePath) {
    
    private val logger = Logger.getInstance(IdeToolsImpl::class.java.name)
    private val PREFERRED_LOCALE_KEY = "com.asakii.locale"
    
    // 辅助类
    private val diffContentHelper = DiffContentHelper()
    private val activeFileHelper = ActiveFileHelper(project)
    private val fontHelper = FontHelper()
    
    override fun openFile(path: String, line: Int, column: Int): Result<Unit> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(path))
                if (file != null) {
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val descriptor = if (line > 0) {
                        OpenFileDescriptor(project, file, line - 1, (column - 1).coerceAtLeast(0))
                    } else {
                        OpenFileDescriptor(project, file)
                    }
                    fileEditorManager.openTextEditor(descriptor, true)
                    logger.info { "✅ Opened file: $path (line=$line, column=$column)" }
                } else {
                    logger.warn { "⚠️ File not found: $path" }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logError { "❌ Failed to open file: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun showDiff(request: DiffRequest): Result<Unit> {
        if (request.filePath.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            ApplicationManager.getApplication().invokeLater {
                val fileName = File(request.filePath).name
                val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
                
                val (finalOldContent, finalNewContent, finalTitle) = if (request.rebuildFromFile) {
                    val file = LocalFileSystem.getInstance().findFileByPath(request.filePath)
                        ?: throw IllegalStateException("File not found: ${request.filePath}")
                     file.refresh(false, false)
                    val currentContent = ApplicationManager.getApplication().runReadAction<String> {
                        String(file.contentsToByteArray(), file.charset)
                    }
                    
                    val edits = request.edits ?: listOf(
                        EditOperation(
                            oldString = request.oldContent,
                            newString = request.newContent,
                            replaceAll = false
                        )
                    )
                    
                    val rebuiltOldContent = diffContentHelper.rebuildBeforeContent(currentContent, edits)
                    
                    Triple(
                        rebuiltOldContent,
                        currentContent,
                        request.title ?: "File Changes: $fileName (${edits.size} edits)"
                    )
                } else {
                    Triple(
                        request.oldContent,
                        request.newContent,
                        request.title ?: "File Diff: $fileName"
                    )
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
                
                logger.info { "✅ Showing diff for: ${request.filePath}" }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            logger.logError { "❌ Failed to show diff: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun searchFiles(query: String, maxResults: Int): Result<List<FileInfo>> {
        if (query.isBlank()) {
            return Result.success(emptyList())
        }
        
        return try {
            val result = mutableListOf<FileInfo>()
            ApplicationManager.getApplication().runReadAction {
                val projectScope = GlobalSearchScope.projectScope(project)
                // 搜索所有文件类型，使用多个常见扩展名
                val commonExtensions = listOf("kt", "java", "js", "ts", "py", "md", "json", "xml", "html", "css", "gradle", "kts", "properties")
                val allFiles = mutableSetOf<VirtualFile>()
                
                // 对每个扩展名进行搜索
                for (ext in commonExtensions) {
                    val files = FilenameIndex.getAllFilesByExt(project, ext, projectScope)
                        .filter { it.name.contains(query, ignoreCase = true) }
                    allFiles.addAll(files)
                }
                
                result.addAll(allFiles.take(maxResults).mapNotNull { file: VirtualFile ->
                    val path = file.path
                    if (path.isNotEmpty()) FileInfo(path) else null
                })
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warn { "Failed to search files: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun getFileContent(path: String, lineStart: Int?, lineEnd: Int?): Result<String> {
        if (path.isBlank()) {
            return Result.failure(IllegalArgumentException("File path cannot be empty"))
        }
        
        return try {
            val content = ApplicationManager.getApplication().runReadAction<String?> {
                val file = LocalFileSystem.getInstance().findFileByIoFile(File(path)) ?: return@runReadAction null
                String(file.contentsToByteArray(), file.charset)
            } ?: return Result.failure(IllegalArgumentException("File not found: $path"))
            
            val result = if (lineStart != null && lineEnd != null) {
                val lines = content.lines()
                lines.subList(
                    (lineStart - 1).coerceAtLeast(0),
                    lineEnd.coerceAtMost(lines.size)
                ).joinToString("\n")
            } else {
                content
            }
            
            Result.success(result)
        } catch (e: Exception) {
            logger.logError { "Failed to get file content: ${e.message}" }
            Result.failure(e)
        }
    }
    
    override fun getRecentFiles(maxResults: Int): Result<List<FileInfo>> {
        return try {
            val files = com.intellij.openapi.application.runReadAction {
                FileEditorManager.getInstance(project).openFiles.toList()
            }
            val result = files.take(maxResults).map { file ->
                FileInfo(file.path)
            }
            Result.success(result)
        } catch (e: Exception) {
            logger.warn { "Failed to get recent files: ${e.message}" }
            Result.failure(e)
        }
    }
    
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
        // 检查用户偏好设置
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
    
    override fun setLocale(locale: String): Result<Unit> {
        return try {
            PropertiesComponent.getInstance().setValue(PREFERRED_LOCALE_KEY, locale)
            logger.info { "Locale preference set to: $locale" }
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn { "Failed to set locale preference: ${e.message}" }
            Result.failure(e)
        }
    }

    override fun getAgentDefinitions(): Map<String, AgentDefinition> {
        return try {
            logger.info { "🔍 [getAgentDefinitions] 开始加载自定义代理..." }

            // 从 AgentSettingsService 读取用户配置
            val settingsService = AgentSettingsService.getInstance()
            val customAgentsJson = settingsService.customAgents

            if (customAgentsJson.isBlank() || customAgentsJson == "{}") {
                // 没有用户配置时，使用默认的 ExploreWithJetbrains 代理
                logger.info { "ℹ️ [getAgentDefinitions] 用户未配置自定义代理，使用默认配置" }
                return getDefaultAgentDefinitions()
            }

            val json = Json { ignoreUnknownKeys = true }
            val agentsConfig = json.parseToJsonElement(customAgentsJson).jsonObject
            val agents = agentsConfig["agents"]?.jsonObject ?: agentsConfig

            val result = mutableMapOf<String, AgentDefinition>()

            // 检查 JetBrains MCP 是否启用
            val jetBrainsMcpEnabled = settingsService.enableJetBrainsMcp

            for ((name, value) in agents) {
                try {
                    val agentObj = value.jsonObject

                    // 检查是否启用（默认启用）
                    val enabled = agentObj["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                    if (!enabled) {
                        logger.info { "⏭️ Skipping disabled agent: $name" }
                        continue
                    }

                    // ExploreWithJetbrains agent 依赖 JetBrains MCP
                    if (name == "ExploreWithJetbrains" && !jetBrainsMcpEnabled) {
                        logger.info { "⏭️ Skipping ExploreWithJetbrains: JetBrains MCP is disabled" }
                        continue
                    }

                    // CodeWithJetbrains agent 依赖 JetBrains MCP
                    if (name == "CodeWithJetbrains" && !jetBrainsMcpEnabled) {
                        logger.info { "⏭️ Skipping CodeWithJetbrains: JetBrains MCP is disabled" }
                        continue
                    }

                    val description = agentObj["description"]?.jsonPrimitive?.contentOrNull ?: ""
                    val prompt = agentObj["prompt"]?.jsonPrimitive?.contentOrNull ?: ""
                    val tools = agentObj["tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    // 空字符串视为 null（使用默认模型）
                    val model = agentObj["model"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

                    result[name] = AgentDefinition(
                        description = description,
                        prompt = prompt,
                        tools = tools,
                        model = model
                    )
                    logger.info("✅ Loaded agent: $name (tools: ${tools?.size ?: 0}, model: ${model ?: "inherit"})")
                } catch (e: Exception) {
                    logger.warn { "⚠️ Failed to parse agent '$name': ${e.message}" }
                }
            }

            if (result.isNotEmpty()) {
                logger.info { "📦 Loaded ${result.size} custom agents from settings: ${result.keys.joinToString()}" }
            } else {
                logger.warn { "⚠️ [getAgentDefinitions] 未加载到任何自定义代理" }
            }

            result
        } catch (e: Exception) {
            logger.warn { "Failed to load agent definitions: ${e.message}" }
            getDefaultAgentDefinitions()
        }
    }

    /**
     * 获取默认的代理定义
     * 当用户未配置或配置解析失败时使用
     *
     * 注意：ExploreWithJetbrains 和 CodeWithJetbrains agent 只在 JetBrains MCP 启用时才可用
     */
    private fun getDefaultAgentDefinitions(): Map<String, AgentDefinition> {
        // 检查 JetBrains MCP 是否启用
        val settings = AgentSettingsService.getInstance()
        if (!settings.enableJetBrainsMcp) {
            logger.info { "⏭️ JetBrains MCP is disabled, ExploreWithJetbrains and CodeWithJetbrains agents not available" }
            return emptyMap()
        }

        val result = mutableMapOf<String, AgentDefinition>()

        // ExploreWithJetbrains agent
        val exploreAgent = AgentDefaults.EXPLORE_WITH_JETBRAINS
        result[exploreAgent.name] = AgentDefinition(
            description = exploreAgent.description,
            prompt = exploreAgent.prompt,
            tools = exploreAgent.tools,
            model = null // 使用默认模型
        )

        // CodeWithJetbrains agent
        val codeAgent = AgentDefaults.CODE_WITH_JETBRAINS
        result[codeAgent.name] = AgentDefinition(
            description = codeAgent.description,
            prompt = codeAgent.prompt,
            tools = codeAgent.tools,
            model = null // 使用默认模型
        )

        logger.info { "📦 Using default agents: ${result.keys.joinToString()}" }
        return result
    }

    override fun getActiveEditorFile(): ActiveFileInfo? {
        return activeFileHelper.getActiveEditorFile()
    }

    /**
     * 检查是否在 IDE 环境中运行
     *
     * IdeToolsImpl 由 jetbrains-plugin 提供，表示在 IDEA 中运行
     */
    override fun hasIdeEnvironment(): Boolean = true

    /**
     * 获取字体文件数据
     *
     * 从系统字体目录中查找指定字体并返回其二进制数据
     * 支持 TrueType (.ttf) 和 OpenType (.otf) 字体
     */
    override fun getFontData(fontFamily: String): FontData? {
        return fontHelper.getFontData(fontFamily)
    }

    /**
     * 在系统默认浏览器中打开 URL
     *
     * 使用 IntelliJ Platform 的 BrowserUtil 打开 URL，
     * 这是在 IDEA 环境中打开浏览器的推荐方式
     */
    override fun openUrl(url: String): Result<Unit> {
        logger.info { "[IDEA] Opening URL in browser: $url" }
        return try {
            BrowserUtil.browse(url)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.warn { "Failed to open URL: ${e.message}" }
            Result.failure(e)
        }
    }

    /**
     * 获取文件历史内容（基于 LocalHistory 时间戳）
     *
     * 使用 IDEA LocalHistory API 查询指定时间点之前的文件内容，
     * 用于 Edit/Write 工具的历史快照恢复和 Diff 显示。
     */
    override fun getFileHistoryContent(filePath: String, beforeTimestamp: Long): String? {
        logger.info { "[IDEA] Getting file history content: $filePath (before: $beforeTimestamp)" }
        return com.asakii.plugin.services.FileHistoryService.getContentBefore(filePath, beforeTimestamp)
    }
}

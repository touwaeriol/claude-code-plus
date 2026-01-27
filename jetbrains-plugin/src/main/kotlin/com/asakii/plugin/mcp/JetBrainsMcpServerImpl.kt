package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.plugin.mcp.tools.*
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import com.asakii.logging.*

private val logger = getLogger("JetBrainsMcpServerImpl")

/**
 * JetBrains LSP MCP 服务器实现
 *
 * 提供 IDEA 平台相关的 IDE 集成工具，如目录树、文件问题检测、文件索引搜索、代码搜索等。
 * 这些工具利用 IDEA 的强大索引和分析能力，提供比纯文件系统操作更丰富的功能。
 *
 * 注意：文件操作工具（Read/Write/Edit）已移至独立的 jetbrains-file MCP 服务器。
 */
@McpServerConfig(
    name = "ide-lsp",
    version = "1.0.0",
    description = "JetBrains IDE integration tool server, providing directory browsing, file problem detection, index search, code search and other features"
)
class JetBrainsMcpServerImpl(private val project: Project) : McpServerBase() {

    /**
     * 工具调用超时时间（毫秒）
     * 从 AgentSettingsService 读取配置（秒），转换为毫秒
     * 0 或负数表示无限超时
     */
    override val timeout: Long?
        get() {
            val timeoutSec = AgentSettingsService.getInstance().jetbrainsMcpTimeout
            return if (timeoutSec <= 0) null else timeoutSec * 1000L
        }

    // 工具实例
    private lateinit var directoryTreeTool: DirectoryTreeTool
    private lateinit var fileProblemsTool: FileProblemsTool
    private lateinit var fileIndexTool: FileIndexTool
    private lateinit var codeSearchTool: CodeSearchTool
    private lateinit var findUsagesTool: FindUsagesTool
    private lateinit var renameTool: RenameTool

    override fun getSystemPromptAppendix(): String {
        val provider = McpSystemPromptContext.getProvider()
        return AgentSettingsService.getInstance().getEffectiveJetbrainsInstructionsForProvider(provider)
    }

    /**
     * 获取需要自动允许的工具列表
     * JetBrains LSP MCP 的所有工具都是只读或安全的重构工具
     * 用户可以在设置中自定义
     */
    override fun getAllowedTools(): List<String> = 
        AgentSettingsService.getInstance().getJetbrainsLspAutoApprovedTools()

    companion object {
        /**
         * 预加载的工具 Schema（使用 McpDefaults 中的静态定义）
         */
        val TOOL_SCHEMAS: Map<String, JsonObject> = loadAllSchemas()

        /**
         * 从 McpDefaults 加载所有工具 Schema
         */
        private fun loadAllSchemas(): Map<String, JsonObject> {
            logger.info { "📂 [JetBrainsMcpServer] Loading schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.JETBRAINS_TOOLS_SCHEMA)
                logger.info { "✅ [JetBrainsMcpServer] Loaded ${toolsMap.size} tool schemas: ${toolsMap.keys}" }
                toolsMap
            } catch (e: Exception) {
                logger.error(e) { "❌ [JetBrainsMcpServer] Failed to parse schemas: ${e.message}" }
                emptyMap()
            }
        }

        /**
         * 获取指定工具的 Schema
         */
        fun getToolSchema(toolName: String): JsonObject {
            return TOOL_SCHEMAS[toolName] ?: run {
                logger.warn { "⚠️ [JetBrainsMcpServer] Tool schema not found: $toolName" }
                buildJsonObject { }
            }
        }
    }

    override suspend fun onInitialize() {
        logger.info { "Initializing JetBrains LSP MCP Server for project: ${project.name}" }

        try {
            // 验证预加载的 Schema
            logger.info { "Using pre-loaded schemas: ${TOOL_SCHEMAS.size} tools (${TOOL_SCHEMAS.keys})" }

            if (TOOL_SCHEMAS.isEmpty()) {
                logger.error { "No schemas loaded! Tools will not work properly." }
            }

            // 初始化工具实例
            logger.info { "Creating tool instances..." }
            directoryTreeTool = DirectoryTreeTool(project)
            fileProblemsTool = FileProblemsTool(project)
            fileIndexTool = FileIndexTool(project)
            codeSearchTool = CodeSearchTool(project)
            findUsagesTool = FindUsagesTool(project)
            renameTool = RenameTool(project)
            logger.info { "All tool instances created" }

            // 注册目录树工具（使用预加载的 Schema）
            val directoryTreeSchema = getToolSchema("DirectoryTree")
            logger.info { "DirectoryTree schema: ${directoryTreeSchema.keys}" }
            registerToolFromSchema("DirectoryTree", directoryTreeSchema) { arguments ->
                wrapToolResult(directoryTreeTool.execute(arguments))
            }

            // 注册文件问题检测工具
            val fileProblemsSchema = getToolSchema("FileProblems")
            logger.info { "FileProblems schema: ${fileProblemsSchema.keys}" }
            registerToolFromSchema("FileProblems", fileProblemsSchema) { arguments ->
                wrapToolResult(fileProblemsTool.execute(arguments))
            }

            // 注册文件索引搜索工具
            val fileIndexSchema = getToolSchema("FileIndex")
            logger.info { "FileIndex schema: ${fileIndexSchema.keys}" }
            registerToolFromSchema("FileIndex", fileIndexSchema) { arguments ->
                wrapToolResult(fileIndexTool.execute(arguments))
            }

            // 注册代码搜索工具
            val codeSearchSchema = getToolSchema("CodeSearch")
            logger.info { "CodeSearch schema: ${codeSearchSchema.keys}" }
            registerToolFromSchema("CodeSearch", codeSearchSchema) { arguments ->
                wrapToolResult(codeSearchTool.execute(arguments))
            }

            // 注册查找引用工具
            val findUsagesSchema = getToolSchema("FindUsages")
            logger.info { "FindUsages schema: ${findUsagesSchema.keys}" }
            registerToolFromSchema("FindUsages", findUsagesSchema) { arguments ->
                wrapToolResult(findUsagesTool.execute(arguments))
            }

            // 注册重命名工具
            val renameSchema = getToolSchema("Rename")
            logger.info { "Rename schema: ${renameSchema.keys}" }
            registerToolFromSchema("Rename", renameSchema) { arguments ->
                wrapToolResult(renameTool.execute(arguments))
            }

            logger.info { "JetBrains LSP MCP Server initialized, registered 6 tools" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize JetBrains LSP MCP Server: ${e.message}" }
            throw e
        }
    }
}

/**
 * JetBrains LSP MCP 服务器提供者实现
 *
 * 在 jetbrains-plugin 模块中实现，提供对 IDEA Platform API 的访问。
 */
class JetBrainsMcpServerProviderImpl(private val project: Project) : JetBrainsMcpServerProvider {

    private val _server: McpServer by lazy {
        logger.info { "Creating JetBrains LSP MCP Server for project: ${project.name}" }
        JetBrainsMcpServerImpl(project).also {
            logger.info { "JetBrains LSP MCP Server instance created" }
        }
    }

    override fun getServer(): McpServer {
        logger.info { "JetBrainsMcpServerProvider.getServer() called" }
        return _server
    }

    /**
     * 获取需要禁用的内置工具列表
     *
     * 当 JetBrains MCP 启用时，禁用内置的 Glob 和 Grep 工具，
     * 因为 JetBrains MCP 的 CodeSearch 和 FileIndex 工具提供更强大的功能。
     */
    override fun getDisallowedBuiltinTools(): List<String> {
        val settings = AgentSettingsService.getInstance()
        // 只有当 JetBrains MCP 启用时才禁用 Glob 和 Grep
        return if (settings.enableJetBrainsMcp) {
            listOf("Glob", "Grep")
        } else {
            emptyList()
        }
    }
}

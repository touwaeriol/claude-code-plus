package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.*
import com.asakii.server.mcp.JetBrainsMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * JetBrains MCP 服务器实现
 * 
 * 提供 IDEA 平台相关的工具，如目录树、文件问题检测、文件索引搜索、代码搜索等。
 * 这些工具利用 IDEA 的强大索引和分析能力，提供比纯文件系统操作更丰富的功能。
 */
@McpServerConfig(
    name = "jetbrains",
    version = "1.0.0",
    description = "JetBrains IDE integration tool server, providing directory browsing, file problem detection, index search, code search and other features"
)
class JetBrainsMcpServerImpl(private val project: Project) : McpServerBase() {
    
    // 工具实例
    private lateinit var directoryTreeTool: DirectoryTreeTool
    private lateinit var fileProblemsTool: FileProblemsTool
    private lateinit var fileIndexTool: FileIndexTool
    private lateinit var codeSearchTool: CodeSearchTool
    private lateinit var findUsagesTool: FindUsagesTool
    private lateinit var renameTool: RenameTool

    override fun getSystemPromptAppendix(): String {
        return AgentSettingsService.getInstance().effectiveJetbrainsInstructions
    }

    /**
     * 获取需要自动允许的工具列表
     * JetBrains MCP 的所有工具都应该自动允许，因为它们只是读取 IDE 信息
     */
    override fun getAllowedTools(): List<String> = listOf(
        "DirectoryTree",
        "FileProblems",
        "FileIndex",
        "CodeSearch",
        "FindUsages",
        "Rename"
    )

    companion object {
        /**
         * 预加载的工具 Schema（使用 McpDefaults 中的静态定义）
         */
        val TOOL_SCHEMAS: Map<String, Map<String, Any>> = loadAllSchemas()

        /**
         * 从 McpDefaults 加载所有工具 Schema
         */
        private fun loadAllSchemas(): Map<String, Map<String, Any>> {
            logger.info { "📂 [JetBrainsMcpServer] Loading schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.JETBRAINS_TOOLS_SCHEMA)
                val result = toolsMap.mapValues { (_, jsonObj) -> jsonObjectToMap(jsonObj) }
                logger.info { "✅ [JetBrainsMcpServer] Loaded ${result.size} tool schemas: ${result.keys}" }
                result
            } catch (e: Exception) {
                logger.error(e) { "❌ [JetBrainsMcpServer] Failed to parse schemas: ${e.message}" }
                emptyMap()
            }
        }

        /**
         * 将 JsonObject 递归转换为 Map<String, Any>
         */
        private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
            return jsonObject.mapValues { (_, value) -> jsonElementToAny(value) }
        }

        /**
         * 将 JsonElement 递归转换为 Any
         */
        private fun jsonElementToAny(element: JsonElement): Any {
            return when (element) {
                is JsonPrimitive -> when {
                    element.isString -> element.content
                    element.booleanOrNull != null -> element.boolean
                    element.intOrNull != null -> element.int
                    element.longOrNull != null -> element.long
                    element.doubleOrNull != null -> element.double
                    else -> element.content
                }
                is JsonArray -> element.map { jsonElementToAny(it) }
                is JsonObject -> jsonObjectToMap(element)
                is JsonNull -> ""
            }
        }

        /**
         * 获取指定工具的 Schema
         */
        fun getToolSchema(toolName: String): Map<String, Any> {
            return TOOL_SCHEMAS[toolName] ?: run {
                logger.warn { "⚠️ [JetBrainsMcpServer] Tool schema not found: $toolName" }
                emptyMap()
            }
        }
    }

    override suspend fun onInitialize() {
        logger.info { "🔧 Initializing JetBrains MCP Server for project: ${project.name}" }

        try {
            // 验证预加载的 Schema
            logger.info { "📋 Using pre-loaded schemas: ${TOOL_SCHEMAS.size} tools (${TOOL_SCHEMAS.keys})" }

            if (TOOL_SCHEMAS.isEmpty()) {
                logger.error { "❌ No schemas loaded! Tools will not work properly." }
            }

            // 初始化工具实例
            logger.info { "🔧 Creating tool instances..." }
            directoryTreeTool = DirectoryTreeTool(project)
            fileProblemsTool = FileProblemsTool(project)
            fileIndexTool = FileIndexTool(project)
            codeSearchTool = CodeSearchTool(project)
            findUsagesTool = FindUsagesTool(project)
            renameTool = RenameTool(project)
            logger.info { "✅ All tool instances created" }

            // 注册目录树工具（使用预加载的 Schema）
            val directoryTreeSchema = getToolSchema("DirectoryTree")
            logger.info { "📝 DirectoryTree schema: ${directoryTreeSchema.keys}" }
            registerToolFromSchema("DirectoryTree", directoryTreeSchema) { arguments ->
                directoryTreeTool.execute(arguments)
            }

            // 注册文件问题检测工具
            val fileProblemsSchema = getToolSchema("FileProblems")
            logger.info { "📝 FileProblems schema: ${fileProblemsSchema.keys}" }
            registerToolFromSchema("FileProblems", fileProblemsSchema) { arguments ->
                fileProblemsTool.execute(arguments)
            }

            // 注册文件索引搜索工具
            val fileIndexSchema = getToolSchema("FileIndex")
            logger.info { "📝 FileIndex schema: ${fileIndexSchema.keys}" }
            registerToolFromSchema("FileIndex", fileIndexSchema) { arguments ->
                fileIndexTool.execute(arguments)
            }

            // 注册代码搜索工具
            val codeSearchSchema = getToolSchema("CodeSearch")
            logger.info { "📝 CodeSearch schema: ${codeSearchSchema.keys}" }
            registerToolFromSchema("CodeSearch", codeSearchSchema) { arguments ->
                codeSearchTool.execute(arguments)
            }

            // 注册查找引用工具
            val findUsagesSchema = getToolSchema("FindUsages")
            logger.info { "📝 FindUsages schema: ${findUsagesSchema.keys}" }
            registerToolFromSchema("FindUsages", findUsagesSchema) { arguments ->
                findUsagesTool.execute(arguments)
            }

            // 注册重命名工具
            val renameSchema = getToolSchema("Rename")
            logger.info { "📝 Rename schema: ${renameSchema.keys}" }
            registerToolFromSchema("Rename", renameSchema) { arguments ->
                renameTool.execute(arguments)
            }

            logger.info { "✅ JetBrains MCP Server initialized, registered 6 tools" }
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to initialize JetBrains MCP Server: ${e.message}" }
            throw e
        }
    }
}

/**
 * JetBrains MCP 服务器提供者实现
 * 
 * 在 jetbrains-plugin 模块中实现，提供对 IDEA Platform API 的访问。
 */
class JetBrainsMcpServerProviderImpl(private val project: Project) : JetBrainsMcpServerProvider {

    private val _server: McpServer by lazy {
        logger.info { "🔧 Creating JetBrains MCP Server for project: ${project.name}" }
        JetBrainsMcpServerImpl(project).also {
            logger.info { "✅ JetBrains MCP Server instance created" }
        }
    }

    override fun getServer(): McpServer {
        logger.info { "📤 JetBrainsMcpServerProvider.getServer() called" }
        return _server
    }
}


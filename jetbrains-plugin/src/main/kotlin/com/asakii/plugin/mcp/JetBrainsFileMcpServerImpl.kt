package com.asakii.plugin.mcp

import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.*
import com.asakii.server.mcp.JetBrainsFileMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import com.asakii.logging.*

private val logger = getLogger("JetBrainsFileMcpServerImpl")

/**
 * JetBrains File MCP 服务器实现
 *
 * 提供独立的文件操作工具：ReadFile、WriteFile、EditFile。
 * 与 jetbrains-lsp MCP 分离，专注于文件读写操作。
 *
 * 特点：
 * - ReadFile 自动允许，无需用户授权
 * - WriteFile 和 EditFile 需要用户授权
 * - 支持通过协程上下文获取 toolUseId，用于文件历史记录
 */
@McpServerConfig(
    name = "ide-file",
    version = "1.0.0",
    description = "JetBrains file operations MCP server providing read, write, and edit capabilities"
)
class JetBrainsFileMcpServerImpl(private val project: Project) : McpServerBase() {

    /**
     * 工具调用超时时间（毫秒）
     * 从 AgentSettingsService 读取配置（秒），转换为毫秒
     * 0 或负数表示无限超时
     */
    override val timeout: Long?
        get() {
            val timeoutSec = AgentSettingsService.getInstance().jetbrainsFileMcpTimeout
            return if (timeoutSec <= 0) null else timeoutSec * 1000L
        }

    // 工具实例
    private lateinit var readFileTool: ReadFileTool
    private lateinit var writeFileTool: WriteFileTool
    private lateinit var editFileTool: EditFileTool

    override fun getSystemPromptAppendix(): String {
        val provider = McpSystemPromptContext.getProvider()
        return AgentSettingsService.getInstance().getEffectiveJetbrainsFileInstructionsForProvider(provider)
    }

    /**
     * 获取需要自动允许的工具列表
     * 只有 ReadFile 自动允许，WriteFile 和 EditFile 需要用户授权
     * 用户可以在设置中自定义
     */
    override fun getAllowedTools(): List<String> = 
        AgentSettingsService.getInstance().getJetbrainsFileAutoApprovedTools()

    companion object {
        /**
         * 预加载的工具 Schema
         */
        val TOOL_SCHEMAS: Map<String, JsonObject> = loadAllSchemas()

        /**
         * 从 McpDefaults 加载所有工具 Schema
         */
        private fun loadAllSchemas(): Map<String, JsonObject> {
            logger.info { "[JetBrainsFileMcpServer] Loading schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.JETBRAINS_FILE_TOOLS_SCHEMA)
                logger.info { "[JetBrainsFileMcpServer] Loaded ${toolsMap.size} tool schemas: ${toolsMap.keys}" }
                toolsMap
            } catch (e: Exception) {
                logger.error(e) { "[JetBrainsFileMcpServer] Failed to parse schemas: ${e.message}" }
                emptyMap()
            }
        }

        /**
         * 获取指定工具的 Schema
         */
        fun getToolSchema(toolName: String): JsonObject {
            return TOOL_SCHEMAS[toolName] ?: run {
                logger.warn { "[JetBrainsFileMcpServer] Tool schema not found: $toolName" }
                buildJsonObject { }
            }
        }
    }

    override suspend fun onInitialize() {
        logger.info { "Initializing JetBrains File MCP Server for project: ${project.name}" }

        try {
            // 验证预加载的 Schema
            logger.info { "Using pre-loaded schemas: ${TOOL_SCHEMAS.size} tools (${TOOL_SCHEMAS.keys})" }

            if (TOOL_SCHEMAS.isEmpty()) {
                logger.error { "No schemas loaded! Tools will not work properly." }
            }

            // 初始化工具实例
            logger.info { "Creating tool instances..." }
            readFileTool = ReadFileTool(project)
            writeFileTool = WriteFileTool(project)
            editFileTool = EditFileTool(project)
            logger.info { "All tool instances created" }

            // 注册文件读取工具
            val readFileSchema = getToolSchema("ReadFile")
            logger.info { "ReadFile schema: ${readFileSchema.keys}" }
            registerToolFromSchema("ReadFile", readFileSchema) { arguments ->
                wrapToolResult(readFileTool.execute(arguments))
            }

            // 注册文件写入工具
            val writeFileSchema = getToolSchema("WriteFile")
            logger.info { "WriteFile schema: ${writeFileSchema.keys}" }
            registerToolFromSchema("WriteFile", writeFileSchema) { arguments ->
                wrapToolResult(writeFileTool.execute(arguments))
            }

            // 注册文件编辑工具
            val editFileSchema = getToolSchema("EditFile")
            logger.info { "EditFile schema: ${editFileSchema.keys}" }
            registerToolFromSchema("EditFile", editFileSchema) { arguments ->
                wrapToolResult(editFileTool.execute(arguments))
            }

            logger.info { "JetBrains File MCP Server initialized, registered 3 tools" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize JetBrains File MCP Server: ${e.message}" }
            throw e
        }
    }
}

/**
 * JetBrains File MCP 服务器提供者实现
 */
class JetBrainsFileMcpServerProviderImpl(private val project: Project) : JetBrainsFileMcpServerProvider {

    private val _server: McpServer by lazy {
        logger.info { "Creating JetBrains File MCP Server for project: ${project.name}" }
        JetBrainsFileMcpServerImpl(project).also {
            logger.info { "JetBrains File MCP Server instance created" }
        }
    }

    override fun getServer(): McpServer {
        logger.info { "JetBrainsFileMcpServerProvider.getServer() called" }
        return _server
    }

    /**
     * 获取需要禁用的内置工具列表
     *
     * 当 JetBrains File MCP 启用且 jetbrainsFileDisableBuiltinTools 为 true 时，
     * 禁用配置的内置工具（默认为 Read、Write、Edit），
     * 因为 JetBrains File MCP 提供相同功能且支持文件历史记录和 Diff 显示。
     */
    override fun getDisallowedBuiltinTools(): List<String> {
        val settings = AgentSettingsService.getInstance()
        // 只有当 JetBrains File MCP 启用且 jetbrainsFileDisableBuiltinTools 为 true 时才禁用内置工具
        return if (settings.enableJetBrainsFileMcp && settings.jetbrainsFileDisableBuiltinTools) {
            settings.getJetbrainsFileDisabledToolsList()
        } else {
            emptyList()
        }
    }
}

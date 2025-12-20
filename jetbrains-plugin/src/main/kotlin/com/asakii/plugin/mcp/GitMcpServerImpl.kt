package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.git.*
import com.asakii.server.mcp.GitMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Git MCP 服务器实现
 *
 * 提供 IDEA VCS/Git 集成工具，如获取变更、读写 commit message 等。
 */
@McpServerConfig(
    name = "jetbrains_git",
    version = "1.0.0",
    description = "JetBrains IDE Git/VCS integration tools for commit message generation and VCS operations"
)
class GitMcpServerImpl(private val project: Project) : McpServerBase() {

    // 工具实例
    private lateinit var getVcsChangesTool: GetVcsChangesTool
    private lateinit var getCommitMessageTool: GetCommitMessageTool
    private lateinit var setCommitMessageTool: SetCommitMessageTool
    private lateinit var getVcsStatusTool: GetVcsStatusTool

    override fun getSystemPromptAppendix(): String {
        return AgentSettingsService.getInstance().effectiveGitInstructions
    }

    /**
     * 获取需要自动允许的工具列表
     * Git MCP 的所有工具都应该自动允许
     */
    override fun getAllowedTools(): List<String> = listOf(
        "GetVcsChanges",
        "GetCommitMessage",
        "SetCommitMessage",
        "GetVcsStatus"
    )

    companion object {
        /**
         * 预加载的工具 Schema
         */
        val TOOL_SCHEMAS: Map<String, Map<String, Any>> = loadAllSchemas()

        private fun loadAllSchemas(): Map<String, Map<String, Any>> {
            logger.info { "Loading Git MCP tool schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.GIT_TOOLS_SCHEMA)
                val result = toolsMap.mapValues { (_, jsonObj) -> jsonObjectToMap(jsonObj) }
                logger.info { "Loaded ${result.size} Git MCP tool schemas: ${result.keys}" }
                result
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse Git MCP schemas: ${e.message}" }
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

        fun getToolSchema(toolName: String): Map<String, Any> {
            return TOOL_SCHEMAS[toolName] ?: run {
                logger.warn { "Git MCP tool schema not found: $toolName" }
                emptyMap()
            }
        }
    }

    override suspend fun onInitialize() {
        logger.info { "Initializing Git MCP Server for project: ${project.name}" }

        try {
            logger.info { "Using pre-loaded schemas: ${TOOL_SCHEMAS.size} tools (${TOOL_SCHEMAS.keys})" }

            if (TOOL_SCHEMAS.isEmpty()) {
                logger.error { "No Git MCP schemas loaded! Tools will not work properly." }
            }

            // 初始化工具实例
            logger.info { "Creating Git MCP tool instances..." }
            getVcsChangesTool = GetVcsChangesTool(project)
            getCommitMessageTool = GetCommitMessageTool(project)
            setCommitMessageTool = SetCommitMessageTool(project)
            getVcsStatusTool = GetVcsStatusTool(project)
            logger.info { "All Git MCP tool instances created" }

            // 注册工具
            registerToolFromSchema("GetVcsChanges", getToolSchema("GetVcsChanges")) { arguments ->
                getVcsChangesTool.execute(arguments)
            }

            registerToolFromSchema("GetCommitMessage", getToolSchema("GetCommitMessage")) { arguments ->
                getCommitMessageTool.execute(arguments)
            }

            registerToolFromSchema("SetCommitMessage", getToolSchema("SetCommitMessage")) { arguments ->
                setCommitMessageTool.execute(arguments)
            }

            registerToolFromSchema("GetVcsStatus", getToolSchema("GetVcsStatus")) { arguments ->
                getVcsStatusTool.execute(arguments)
            }

            logger.info { "Git MCP Server initialized, registered 4 tools" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Git MCP Server: ${e.message}" }
            throw e
        }
    }
}

/**
 * Git MCP 服务器提供者实现
 */
class GitMcpServerProviderImpl(private val project: Project) : GitMcpServerProvider {

    private val _server: McpServer by lazy {
        logger.info { "Creating Git MCP Server for project: ${project.name}" }
        GitMcpServerImpl(project).also {
            logger.info { "Git MCP Server instance created" }
        }
    }

    override fun getServer(): McpServer {
        logger.info { "GitMcpServerProvider.getServer() called" }
        return _server
    }
}

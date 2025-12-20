package com.asakii.plugin.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.terminal.*
import com.asakii.server.mcp.TerminalMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpDefaults
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Terminal MCP 服务器实现
 *
 * 提供 IDEA 内置终端功能，支持命令执行、输出读取、会话管理等。
 * 可替代 Claude CLI 内置的 Bash 工具，提供更丰富的功能。
 */
@McpServerConfig(
    name = "terminal",
    version = "1.0.0",
    description = "IDEA integrated terminal tool server, providing command execution, output reading and session management"
)
class TerminalMcpServerImpl(private val project: Project) : McpServerBase(), Disposable {

    // 会话管理器
    internal val sessionManager by lazy { TerminalSessionManager(project) }

    // 工具实例
    private lateinit var terminalTool: TerminalTool
    private lateinit var terminalReadTool: TerminalReadTool
    private lateinit var terminalListTool: TerminalListTool
    private lateinit var terminalKillTool: TerminalKillTool
    private lateinit var terminalTypesTool: TerminalTypesTool
    private lateinit var terminalRenameTool: TerminalRenameTool

    override fun getSystemPromptAppendix(): String {
        return AgentSettingsService.getInstance().effectiveTerminalInstructions
    }

    /**
     * 获取需要自动允许的工具列表
     */
    override fun getAllowedTools(): List<String> = listOf(
        "Terminal",
        "TerminalRead",
        "TerminalList",
        "TerminalKill",
        "TerminalTypes",
        "TerminalRename"
    )

    /**
     * 获取需要禁用的内置工具列表
     *
     * 只有当 Terminal MCP 启用且 terminalDisableBuiltinBash 为 true 时才禁用 Bash
     */
    fun getDisallowedBuiltinTools(): List<String> {
        val settings = AgentSettingsService.getInstance()
        // 只有当 Terminal MCP 启用时才禁用 Bash
        return if (settings.enableTerminalMcp && settings.terminalDisableBuiltinBash) {
            listOf("Bash")
        } else {
            emptyList()
        }
    }

    companion object {
        /**
         * 预加载的工具 Schema
         */
        val TOOL_SCHEMAS: Map<String, Map<String, Any>> = loadAllSchemas()

        private fun loadAllSchemas(): Map<String, Map<String, Any>> {
            logger.info { "Loading Terminal tool schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.TERMINAL_TOOLS_SCHEMA)
                val result = toolsMap.mapValues { (_, jsonObj) -> jsonObjectToMap(jsonObj) }
                logger.info { "Loaded ${result.size} terminal tool schemas: ${result.keys}" }
                result
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse Terminal schemas: ${e.message}" }
                emptyMap()
            }
        }

        private fun jsonObjectToMap(jsonObject: JsonObject): Map<String, Any> {
            return jsonObject.mapValues { (_, value) -> jsonElementToAny(value) }
        }

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
                logger.warn { "Terminal tool schema not found: $toolName" }
                emptyMap()
            }
        }
    }

    override suspend fun onInitialize() {
        logger.info { "Initializing Terminal MCP Server for project: ${project.name}" }

        try {
            logger.info { "Using pre-loaded schemas: ${TOOL_SCHEMAS.size} tools (${TOOL_SCHEMAS.keys})" }

            if (TOOL_SCHEMAS.isEmpty()) {
                logger.error { "No Terminal schemas loaded! Tools will not work properly." }
            }

            // 初始化工具实例
            logger.info { "Creating Terminal tool instances..." }
            terminalTool = TerminalTool(sessionManager)
            terminalReadTool = TerminalReadTool(sessionManager)
            terminalListTool = TerminalListTool(sessionManager)
            terminalKillTool = TerminalKillTool(sessionManager)
            terminalTypesTool = TerminalTypesTool(sessionManager)
            terminalRenameTool = TerminalRenameTool(sessionManager)
            logger.info { "All Terminal tool instances created" }

            // 注册工具
            registerToolFromSchema("Terminal", getToolSchema("Terminal")) { arguments ->
                terminalTool.execute(arguments)
            }

            registerToolFromSchema("TerminalRead", getToolSchema("TerminalRead")) { arguments ->
                terminalReadTool.execute(arguments)
            }

            registerToolFromSchema("TerminalList", getToolSchema("TerminalList")) { arguments ->
                terminalListTool.execute(arguments)
            }

            registerToolFromSchema("TerminalKill", getToolSchema("TerminalKill")) { arguments ->
                terminalKillTool.execute(arguments)
            }

            registerToolFromSchema("TerminalTypes", getToolSchema("TerminalTypes")) { arguments ->
                terminalTypesTool.execute(arguments)
            }

            registerToolFromSchema("TerminalRename", getToolSchema("TerminalRename")) { arguments ->
                terminalRenameTool.execute(arguments)
            }

            logger.info { "Terminal MCP Server initialized, registered 6 tools" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Terminal MCP Server: ${e.message}" }
            throw e
        }
    }

    override fun dispose() {
        logger.info { "Disposing Terminal MCP Server" }
        sessionManager.dispose()
    }
}

/**
 * Terminal MCP 服务器提供者实现
 */
class TerminalMcpServerProviderImpl(private val project: Project) : TerminalMcpServerProvider {

    private val _server: TerminalMcpServerImpl by lazy {
        logger.info { "Creating Terminal MCP Server for project: ${project.name}" }
        TerminalMcpServerImpl(project).also {
            logger.info { "Terminal MCP Server instance created" }
        }
    }

    override fun getServer(): McpServer {
        logger.info { "TerminalMcpServerProvider.getServer() called" }
        return _server
    }

    override fun getDisallowedBuiltinTools(): List<String> {
        return _server.getDisallowedBuiltinTools()
    }
}

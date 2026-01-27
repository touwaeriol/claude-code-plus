package com.asakii.plugin.mcp

import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.git.CommitChangesTool
import com.asakii.plugin.mcp.git.DeselectAllFilesTool
import com.asakii.plugin.mcp.git.DeselectFilesTool
import com.asakii.plugin.mcp.git.GetCommitMessageTool
import com.asakii.plugin.mcp.git.GetVcsChangesTool
import com.asakii.plugin.mcp.git.GetVcsStatusTool
import com.asakii.plugin.mcp.git.SelectAllFilesTool
import com.asakii.plugin.mcp.git.SelectFilesTool
import com.asakii.plugin.mcp.git.SetCommitMessageTool
import com.asakii.server.mcp.GitMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.asakii.settings.McpBundle
import com.asakii.settings.McpDefaults
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import com.asakii.logging.*

private val logger = getLogger("GitMcpServerImpl")

/**
 * Git MCP server implementation.
 */
@McpServerConfig(
    name = "ide-git",
    version = "1.0.0",
    description = "JetBrains IDE Git/VCS integration tools for commit message generation and VCS operations"
)
class GitMcpServerImpl(private val project: Project) : McpServerBase() {

    /**
     * 工具调用超时时间（毫秒）
     * 从 AgentSettingsService 读取配置（秒），转换为毫秒
     * 0 或负数表示无限超时
     */
    override val timeout: Long?
        get() {
            val timeoutSec = AgentSettingsService.getInstance().gitMcpTimeout
            return if (timeoutSec <= 0) null else timeoutSec * 1000L
        }

    private lateinit var getVcsChangesTool: GetVcsChangesTool
    private lateinit var getCommitMessageTool: GetCommitMessageTool
    private lateinit var setCommitMessageTool: SetCommitMessageTool
    private lateinit var getVcsStatusTool: GetVcsStatusTool
    private lateinit var selectFilesTool: SelectFilesTool
    private lateinit var deselectFilesTool: DeselectFilesTool
    private lateinit var selectAllFilesTool: SelectAllFilesTool
    private lateinit var deselectAllFilesTool: DeselectAllFilesTool
    private lateinit var commitChangesTool: CommitChangesTool

    override fun getSystemPromptAppendix(): String {
        val settings = AgentSettingsService.getInstance()
        val provider = McpSystemPromptContext.getProvider()
        val baseInstructions = settings.getEffectiveGitInstructionsForProvider(provider)
        val commitLang = settings.gitCommitLanguage
        
        // Language requirement at the very beginning (fixed, not user-editable)
        // Uses internationalized messages from McpBundle
        val title = McpBundle.message("mcp.git.commitLang.title")
        val langMessage = when (commitLang) {
            "zh_CN" -> McpBundle.message("mcp.git.commitLang.zh_CN")
            "zh_TW" -> McpBundle.message("mcp.git.commitLang.zh_TW")
            "ja" -> McpBundle.message("mcp.git.commitLang.ja")
            "ko" -> McpBundle.message("mcp.git.commitLang.ko")
            else -> McpBundle.message("mcp.git.commitLang.en")
        }
        val langRequirement = "$title\n\n$langMessage\n\n"
        
        // Language requirement first (fixed), then user-customizable instructions
        return langRequirement + baseInstructions
    }

    override fun getAllowedTools(): List<String> = 
        AgentSettingsService.getInstance().getGitAutoApprovedTools()

    companion object {
        val TOOL_SCHEMAS: Map<String, JsonObject> = loadAllSchemas()

        private fun loadAllSchemas(): Map<String, JsonObject> {
            logger.info { "Loading Git MCP tool schemas from McpDefaults" }

            return try {
                val json = Json { ignoreUnknownKeys = true }
                val toolsMap = json.decodeFromString<Map<String, JsonObject>>(McpDefaults.GIT_TOOLS_SCHEMA)
                logger.info { "Loaded ${toolsMap.size} Git MCP tool schemas: ${toolsMap.keys}" }
                toolsMap
            } catch (e: Exception) {
                logger.error(e) { "Failed to parse Git MCP schemas: ${e.message}" }
                emptyMap()
            }
        }

        fun getToolSchema(toolName: String): JsonObject {
            return TOOL_SCHEMAS[toolName] ?: run {
                logger.warn { "Git MCP tool schema not found: $toolName" }
                buildJsonObject { }
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

            logger.info { "Creating Git MCP tool instances..." }
            getVcsChangesTool = GetVcsChangesTool(project)
            getCommitMessageTool = GetCommitMessageTool(project)
            setCommitMessageTool = SetCommitMessageTool(project)
            getVcsStatusTool = GetVcsStatusTool(project)
            selectFilesTool = SelectFilesTool(project)
            deselectFilesTool = DeselectFilesTool(project)
            selectAllFilesTool = SelectAllFilesTool(project)
            deselectAllFilesTool = DeselectAllFilesTool(project)
            commitChangesTool = CommitChangesTool(project)
            logger.info { "All Git MCP tool instances created" }

            registerToolFromSchema("GetVcsChanges", getToolSchema("GetVcsChanges")) { arguments ->
                wrapToolResult(getVcsChangesTool.execute(arguments))
            }

            registerToolFromSchema("GetCommitMessage", getToolSchema("GetCommitMessage")) { arguments ->
                wrapToolResult(getCommitMessageTool.execute(arguments))
            }

            registerToolFromSchema("SetCommitMessage", getToolSchema("SetCommitMessage")) { arguments ->
                wrapToolResult(setCommitMessageTool.execute(arguments))
            }

            registerToolFromSchema("GetVcsStatus", getToolSchema("GetVcsStatus")) { arguments ->
                wrapToolResult(getVcsStatusTool.execute(arguments))
            }

            registerToolFromSchema("SelectFiles", getToolSchema("SelectFiles")) { arguments ->
                wrapToolResult(selectFilesTool.execute(arguments))
            }

            registerToolFromSchema("DeselectFiles", getToolSchema("DeselectFiles")) { arguments ->
                wrapToolResult(deselectFilesTool.execute(arguments))
            }

            registerToolFromSchema("SelectAllFiles", getToolSchema("SelectAllFiles")) { arguments ->
                wrapToolResult(selectAllFilesTool.execute(arguments))
            }

            registerToolFromSchema("DeselectAllFiles", getToolSchema("DeselectAllFiles")) { arguments ->
                wrapToolResult(deselectAllFilesTool.execute(arguments))
            }

            registerToolFromSchema("CommitChanges", getToolSchema("CommitChanges")) { arguments ->
                wrapToolResult(commitChangesTool.execute(arguments))
            }

            logger.info { "Git MCP Server initialized, registered 9 tools" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Git MCP Server: ${e.message}" }
            throw e
        }
    }
}

/**
 * Git MCP server provider implementation.
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

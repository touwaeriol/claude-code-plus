package com.asakii.plugin.mcp

import com.asakii.ai.agent.sdk.McpSystemPromptContext
import com.asakii.claude.agent.sdk.mcp.McpServer
import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.plugin.mcp.tools.terminal.*
import com.asakii.server.mcp.TerminalBackgroundResult
import com.asakii.server.mcp.TerminalMcpServerProvider
import com.asakii.settings.AgentSettingsService
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.asakii.logging.*
import java.util.concurrent.ConcurrentHashMap

private val logger = getLogger("TerminalMcpServerImpl")

/**
 * JetBrains Terminal MCP server implementation.
 */
@McpServerConfig(
    name = "ide-terminal",
    version = "1.0.0",
    description = "IDEA integrated terminal tool server, providing command execution, output reading and session management"
)
class TerminalMcpServerImpl(private val project: Project) : McpServerBase(), Disposable {

    /**
     * 工具调用超时时间（毫秒）
     * 从 AgentSettingsService 读取配置（秒），转换为毫秒
     * 0 或负数表示无限超时
     */
    override val timeout: Long?
        get() {
            val timeoutSec = AgentSettingsService.getInstance().terminalMcpTimeout
            return if (timeoutSec <= 0) null else timeoutSec * 1000L
        }

    internal val sessionManager by lazy { TerminalSessionManager(project) }

    private lateinit var terminalTool: TerminalTool
    private lateinit var terminalReadTool: TerminalReadTool
    private lateinit var terminalListTool: TerminalListTool
    private lateinit var terminalKillTool: TerminalKillTool
    private lateinit var terminalTypesTool: TerminalTypesTool
    private lateinit var terminalRenameTool: TerminalRenameTool
    private lateinit var terminalInterruptTool: TerminalInterruptTool

    override fun getSystemPromptAppendix(): String {
        val settings = AgentSettingsService.getInstance()
        val provider = McpSystemPromptContext.getProvider()
        val baseInstructions = settings.getEffectiveTerminalInstructionsForProvider(provider)

        val platform = if (settings.isWindows()) "Windows" else "Unix"
        val defaultShell = settings.getEffectiveDefaultShell()
        val availableShells = settings.getEffectiveAvailableShells()

        val systemInfo = buildString {
            appendLine()
            appendLine("**Current Environment:**")
            appendLine("- Platform: $platform")
            appendLine("- Default Shell: $defaultShell")
            appendLine("- Available Shells: ${availableShells.joinToString(", ")}")
            appendLine()
            appendLine("**Auto-configured (no need for `--no-pager` flags):**")
            appendLine("- `TERM=dumb`, `PAGER=cat`, `GIT_PAGER=cat`")
            appendLine("- Git commands (`log`, `diff`, `show`) output directly without pager")
            appendLine()
            appendLine("**Still Interactive (avoid or use `TerminalInterrupt`):**")
            appendLine("- Direct calls to `less`, `vim`, `nano`")
            appendLine("- Interactive prompts (use `--yes`, `-y` flags)")
        }

        return baseInstructions + systemInfo
    }

    override fun getAllowedTools(): List<String> = 
        AgentSettingsService.getInstance().getTerminalAutoApprovedTools()

    fun getDisallowedBuiltinTools(): List<String> = TerminalFeatureConfig.getDisallowedBuiltinTools()

    fun getCodexDisabledFeatures(): List<String> = TerminalFeatureConfig.getCodexDisabledFeatures()

    override suspend fun onInitialize() {
        logger.info { "Initializing Terminal MCP Server for project: ${project.name}" }

        try {
            val schemas = TerminalToolSchemaManager.TOOL_SCHEMAS
            logger.info { "Using pre-loaded schemas: ${schemas.size} tools (${schemas.keys})" }

            if (schemas.isEmpty()) {
                logger.error { "No Terminal schemas loaded! Tools will not work properly." }
            }

            logger.info { "Creating Terminal tool instances..." }
            terminalTool = TerminalTool(sessionManager)
            terminalReadTool = TerminalReadTool(sessionManager)
            terminalListTool = TerminalListTool(sessionManager)
            terminalKillTool = TerminalKillTool(sessionManager)
            terminalTypesTool = TerminalTypesTool(sessionManager)
            terminalRenameTool = TerminalRenameTool(sessionManager)
            terminalInterruptTool = TerminalInterruptTool(sessionManager)
            logger.info { "All Terminal tool instances created" }

            registerToolFromSchema("Terminal", TerminalToolSchemaManager.getToolSchema("Terminal")) { arguments ->
                wrapToolResult(terminalTool.execute(arguments))
            }

            registerToolFromSchema("TerminalRead", TerminalToolSchemaManager.getToolSchema("TerminalRead")) { arguments ->
                wrapToolResult(terminalReadTool.execute(arguments))
            }

            registerToolFromSchema("TerminalList", TerminalToolSchemaManager.getToolSchema("TerminalList")) { arguments ->
                wrapToolResult(terminalListTool.execute(arguments))
            }

            registerToolFromSchema("TerminalKill", TerminalToolSchemaManager.getToolSchema("TerminalKill")) { arguments ->
                wrapToolResult(terminalKillTool.execute(arguments))
            }

            registerToolFromSchema("TerminalTypes", TerminalToolSchemaManager.getToolSchema("TerminalTypes")) { arguments ->
                wrapToolResult(terminalTypesTool.execute(arguments))
            }

            registerToolFromSchema("TerminalRename", TerminalToolSchemaManager.getToolSchema("TerminalRename")) { arguments ->
                wrapToolResult(terminalRenameTool.execute(arguments))
            }

            registerToolFromSchema("TerminalInterrupt", TerminalToolSchemaManager.getToolSchema("TerminalInterrupt")) { arguments ->
                wrapToolResult(terminalInterruptTool.execute(arguments))
            }

            logger.info { "Terminal MCP Server initialized, registered 7 tools" }
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
 * Terminal MCP server provider implementation.
 */
class TerminalMcpServerProviderImpl(private val project: Project) : TerminalMcpServerProvider {

    internal val servers = ConcurrentHashMap<String, TerminalMcpServerImpl>()

    override fun getServer(): McpServer {
        return getServerForSession("default")
    }

    override fun getServerForSession(aiSessionId: String): McpServer {
        return servers.computeIfAbsent(aiSessionId) {
            logger.info { "Creating Terminal MCP Server for session: $aiSessionId" }
            TerminalMcpServerImpl(project).also { server ->
                server.sessionManager.setCurrentAiSession(aiSessionId)
                logger.info { "Terminal MCP Server instance created for session: $aiSessionId" }
            }
        }
    }

    override fun getDisallowedBuiltinTools(): List<String> = TerminalFeatureConfig.getDisallowedBuiltinTools()

    override fun getCodexDisabledFeatures(): List<String> = TerminalFeatureConfig.getCodexDisabledFeatures()

    override fun setCurrentAiSession(aiSessionId: String?) {
        if (aiSessionId == null) return
        servers[aiSessionId]?.sessionManager?.setCurrentAiSession(aiSessionId)
    }

    override fun disposeSession(aiSessionId: String?) {
        if (aiSessionId == null) return
        servers.remove(aiSessionId)?.let { server ->
            Disposer.dispose(server)
        }
    }

    override fun runToBackground(toolUseId: String?): TerminalBackgroundResult {
        return TerminalBackgroundManager.runToBackground(servers, toolUseId)
    }
}

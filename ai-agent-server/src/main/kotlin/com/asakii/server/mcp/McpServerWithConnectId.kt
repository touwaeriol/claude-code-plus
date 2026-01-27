package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.logging.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject

private val logger = getLogger("McpServerWithConnectId")

/**
 * MCP Server 包装器，用于在 SDK 模式下注入 connectId
 *
 * 在 HTTP 模式下，connectId 通过 HTTP header 传递，在 McpHttpGateway.callHandler 中注入到 McpCallContext。
 * 在 SDK 模式下，Claude CLI 直接通过 mcp_message 调用 McpServer，没有 HTTP header 可用。
 *
 * 此包装器在创建时绑定 connectId，在每次工具调用时自动注入到协程上下文，
 * 使 MCP 工具可以通过 currentConnectId() 获取前端连接标识。
 *
 * 使用场景：
 * - Claude Code 使用 SDK MCP 模式时
 * - 需要支持用户交互（如 AskUserQuestion）的 MCP 工具
 *
 * @param delegate 原始的 MCP Server 实例
 * @param connectId 前端连接标识，用于回调前端
 */
class McpServerWithConnectId(
    private val delegate: McpServer,
    private val connectId: String
) : McpServer {

    override val name: String get() = delegate.name
    override val version: String get() = delegate.version
    override val description: String get() = delegate.description
    override val timeout: Long? get() = delegate.timeout
    override val resetTimeoutOnProgress: Boolean get() = delegate.resetTimeoutOnProgress
    override val progressReportingEnabled: Boolean get() = delegate.progressReportingEnabled

    override fun getSystemPromptAppendix(): String? = delegate.getSystemPromptAppendix()
    override fun getAllowedTools(): List<String> = delegate.getAllowedTools()

    override suspend fun listTools(): List<ToolDefinition> {
        return delegate.listTools()
    }

    override suspend fun callTool(toolName: String, arguments: JsonObject): ToolResult {
        return withConnectIdContext {
            delegate.callTool(toolName, arguments)
        }
    }

    override suspend fun callToolWithContext(
        toolName: String,
        arguments: JsonObject,
        toolUseId: String?
    ): ToolResult {
        return withConnectIdContext {
            delegate.callToolWithContext(toolName, arguments, toolUseId)
        }
    }

    override suspend fun callToolJson(toolName: String, arguments: JsonObject): ToolResult {
        return withConnectIdContext {
            delegate.callToolJson(toolName, arguments)
        }
    }

    /**
     * 在 McpCallContext 中执行代码块
     * 这样工具内部可以通过 currentConnectId() 获取 connectId
     */
    private suspend inline fun <T> withConnectIdContext(crossinline block: suspend () -> T): T {
        logger.debug { "[SDK MCP] Injecting connectId=$connectId" }
        return withContext(McpCallContext(connectId)) {
            block()
        }
    }
}

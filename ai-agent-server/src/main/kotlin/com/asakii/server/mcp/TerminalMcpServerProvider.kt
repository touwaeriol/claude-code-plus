package com.asakii.server.mcp

import com.asakii.claude.agent.sdk.mcp.McpServer

/**
 * Terminal MCP 服务器提供者接口
 *
 * 由于 Terminal MCP 需要访问 IDEA Terminal API，
 * 实现类必须在 jetbrains-plugin 模块中创建。
 * 此接口用于解耦 ai-agent-server 和 jetbrains-plugin 的依赖。
 */
interface TerminalMcpServerProvider {
    /**
     * 获取 Terminal MCP 服务器实例
     * @return McpServer 实例，如果不可用则返回 null
     */
    fun getServer(): McpServer?

    /**
     * 获取需要禁用的内置工具列表
     * 当 Terminal MCP 启用时，可以禁用内置的 Bash 工具
     * @return 需要禁用的工具名称列表
     */
    fun getDisallowedBuiltinTools(): List<String> = emptyList()
}

/**
 * 默认实现（不支持 Terminal 集成时使用）
 */
object DefaultTerminalMcpServerProvider : TerminalMcpServerProvider {
    override fun getServer(): McpServer? = null
    override fun getDisallowedBuiltinTools(): List<String> = emptyList()
}

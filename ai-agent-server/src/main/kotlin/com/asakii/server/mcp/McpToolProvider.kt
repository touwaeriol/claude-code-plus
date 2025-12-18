package com.asakii.server.mcp

/**
 * MCP 工具提供者接口
 * 由 plugin 模块实现，用于提供 JetBrains IDE 相关工具
 */
interface McpToolProvider {
    fun getTools(): List<McpToolDefinition>
    suspend fun callTool(name: String, arguments: Map<String, Any>): McpToolResult
}

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: String = """{"type":"object"}"""
)

data class McpToolResult(
    val content: String,
    val isError: Boolean = false
)

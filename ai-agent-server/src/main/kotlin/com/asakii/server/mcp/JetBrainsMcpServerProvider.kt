package com.asakii.server.mcp

/**
 * JetBrains MCP 工具提供者接口
 *
 * 由 jetbrains-plugin 模块实现，用于提供 JetBrains IDE 相关工具
 * 此接口用于解耦 ai-agent-server 和 jetbrains-plugin 的依赖
 */
interface JetBrainsMcpServerProvider {
    /**
     * 获取 MCP 工具提供者
     * @return McpToolProvider 实例，如果不可用则返回 null
     */
    fun getToolProvider(): McpToolProvider?
}

/**
 * 默认实现（不支持 JetBrains 集成时使用）
 */
object DefaultJetBrainsMcpServerProvider : JetBrainsMcpServerProvider {
    override fun getToolProvider(): McpToolProvider? = null
}

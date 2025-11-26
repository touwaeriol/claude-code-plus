package com.asakii.claude.agent.sdk.builders

import com.asakii.claude.agent.sdk.mcp.*
import kotlinx.serialization.json.*

/**
 * 快捷工具函数 - 创建简单的单工具服务器
 * 
 * 这是为不想使用注解的用户提供的手动注册方式。
 * 大多数情况下，推荐使用基于注解的 McpServerBase 实现方式。
 */
fun simpleTool(
    name: String,
    description: String = "",
    handler: suspend (Map<String, Any>) -> Any
): McpServer = object : McpServer {
    override val name: String = name
    override val version: String = "1.0.0" 
    override val description: String = description.ifEmpty { "简单工具: $name" }
    
    private val tool = ToolHandler(
        name = name,
        description = this.description,
        parameterSchema = null,
        handler = handler
    )
    
    override suspend fun listTools(): List<ToolDefinition> {
        return listOf(tool.toDefinition())
    }
    
    override suspend fun callTool(toolName: String, arguments: Map<String, Any>): ToolResult {
        if (toolName != name) {
            return ToolResult.error("工具 '$toolName' 未找到")
        }
        
        return try {
            val result = tool.handler(arguments)
            when (result) {
                is ToolResult -> result
                Unit -> ToolResult.success("操作完成")
                else -> ToolResult.success(result)
            }
        } catch (e: Exception) {
            ToolResult.error("工具执行失败: ${e.message}")
        }
    }
}
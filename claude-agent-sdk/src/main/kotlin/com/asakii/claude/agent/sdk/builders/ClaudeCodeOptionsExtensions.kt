package com.asakii.claude.agent.sdk.builders

import com.asakii.claude.agent.sdk.mcp.*
import com.asakii.claude.agent.sdk.types.*

/**
 * ClaudeAgentOptions 扩展函数 - 提供便捷的 MCP 服务器配置
 * 
 * 使用示例：
 * ```kotlin
 * val options = ClaudeAgentOptions(
 *     model = "claude-3-5-sonnet-20241022",
 *     allowedTools = listOf("Bash", "Read", "mcp__calculator__*")
 * ).apply {
 *     addMcpServer("calculator", CalculatorServer())
 *     addSecurityHooks()
 *     addStatisticsHooks()
 * }
 * ```
 */

/**
 * 添加MCP服务器实例
 */
fun ClaudeAgentOptions.addMcpServer(name: String, server: Any): ClaudeAgentOptions {
    val currentMcpServers = this.mcpServers?.toMutableMap() ?: mutableMapOf()
    currentMcpServers[name] = server
    
    return this.copy(mcpServers = currentMcpServers)
}

/**
 * 添加安全Hook配置
 */
fun ClaudeAgentOptions.addSecurityHooks(
    dangerousPatterns: List<String> = listOf("rm -rf", "sudo", "format", "delete"),
    allowedCommands: List<String> = emptyList()
): ClaudeAgentOptions {
    val securityHooks = securityHook(dangerousPatterns, allowedCommands)
    
    val currentHooks = this.hooks?.toMutableMap() ?: mutableMapOf()
    securityHooks.forEach { (event, matchers) ->
        val existingMatchers = currentHooks[event] ?: emptyList()
        currentHooks[event] = existingMatchers + matchers
    }
    
    return this.copy(hooks = currentHooks)
}

/**
 * 添加统计Hook配置
 */
fun ClaudeAgentOptions.addStatisticsHooks(): ClaudeAgentOptions {
    val statsHooks = statisticsHook()
    
    val currentHooks = this.hooks?.toMutableMap() ?: mutableMapOf()
    statsHooks.forEach { (event, matchers) ->
        val existingMatchers = currentHooks[event] ?: emptyList()
        currentHooks[event] = existingMatchers + matchers
    }
    
    return this.copy(hooks = currentHooks)
}

/**
 * 添加自定义Hook
 */
fun ClaudeAgentOptions.addHook(
    event: HookEvent,
    matcher: String?,
    callback: HookCallback
): ClaudeAgentOptions {
    val hookMatcher = HookMatcher(matcher, listOf(callback))
    
    val currentHooks = this.hooks?.toMutableMap() ?: mutableMapOf()
    val existingMatchers = currentHooks[event] ?: emptyList()
    currentHooks[event] = existingMatchers + hookMatcher
    
    return this.copy(hooks = currentHooks)
}

/**
 * 使用Hook Builder DSL添加Hook
 */
fun ClaudeAgentOptions.addHooksDsl(
    init: HookBuilder.() -> Unit
): ClaudeAgentOptions {
    val newHooks = hookBuilder(init)
    
    val currentHooks = this.hooks?.toMutableMap() ?: mutableMapOf()
    newHooks.forEach { (event, matchers) ->
        val existingMatchers = currentHooks[event] ?: emptyList()
        currentHooks[event] = existingMatchers + matchers
    }
    
    return this.copy(hooks = currentHooks)
}

/**
 * 批量添加工具权限
 */
fun ClaudeAgentOptions.addAllowedTools(vararg tools: String): ClaudeAgentOptions {
    val currentTools = this.allowedTools ?: emptyList()
    val newTools = currentTools + tools.toList()
    return this.copy(allowedTools = newTools)
}

/**
 * 添加MCP服务器相关的工具权限
 */
fun ClaudeAgentOptions.addMcpServerTools(serverName: String, vararg tools: String): ClaudeAgentOptions {
    val mcpTools = tools.map { "mcp__${serverName}__$it" }
    return addAllowedTools(*mcpTools.toTypedArray())
}

/**
 * 为MCP服务器添加通配符权限
 */
fun ClaudeAgentOptions.addMcpServerWildcardTools(serverName: String): ClaudeAgentOptions {
    return addAllowedTools("mcp__${serverName}__*")
}
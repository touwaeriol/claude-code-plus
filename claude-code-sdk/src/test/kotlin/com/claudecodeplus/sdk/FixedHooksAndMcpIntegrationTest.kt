package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * 验证修复后的Hook和MCP功能的完整集成测试
 * 
 * 基于对Python SDK的深入分析，验证Kotlin SDK的修复是否成功：
 * 1. ✅ 控制协议初始化流程
 * 2. ✅ Hook回调注册和执行
 * 3. ✅ SDK MCP Server处理
 * 4. ✅ 真实AI调用测试
 */
class FixedHooksAndMcpIntegrationTest {

    companion object {
        private val toolExecutionCount = AtomicInteger(0)
        private val hookExecutionCount = AtomicInteger(0)
        private val lastExecutedTools = mutableListOf<String>()
        private val hookResults = mutableListOf<String>()
    }

    /**
     * 测试1: 验证Hook初始化和回调机制
     */
    @Test
    fun `test hooks initialization and callback mechanism`() = runBlocking {
        println("=== 🎣 Hook初始化和回调机制测试 ===")
        
        // 重置状态
        toolExecutionCount.set(0)
        hookExecutionCount.set(0)
        lastExecutedTools.clear()
        hookResults.clear()
        
        // 创建安全Hook - 基于Python SDK模式
        val securityHook: HookCallback = { input, toolUseId, context ->
            hookExecutionCount.incrementAndGet()
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            println("🎣 [安全Hook] 检查工具: $toolName")
            hookResults.add("SecurityHook执行: $toolName")
            
            if (toolName == "Bash") {
                val command = toolInput["command"] as? String ?: ""
                if (command.contains("rm -rf") || command.contains("dangerous")) {
                    println("🚫 [安全Hook] 阻止危险命令: $command")
                    HookJSONOutput(
                        decision = "block",
                        systemMessage = "🛡️ 安全Hook阻止危险命令: $command",
                        hookSpecificOutput = JsonPrimitive("security_block")
                    )
                } else {
                    HookJSONOutput(systemMessage = "✅ 安全检查通过: $command")
                }
            } else {
                HookJSONOutput(systemMessage = "✅ 非Bash工具，安全通过")
            }
        }
        
        // 创建统计Hook
        val statisticsHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val count = toolExecutionCount.incrementAndGet()
            
            println("📊 [统计Hook] 第 $count 次工具调用: $toolName")
            hookResults.add("StatisticsHook执行: 第${count}次调用$toolName")
            
            HookJSONOutput(systemMessage = "📊 工具调用统计: 总计 $count 次")
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Bash", "Read"),
            // 关键：配置多个Hook，测试初始化流程
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "Bash", // 安全Hook只匹配Bash
                        hooks = listOf(securityHook)
                    ),
                    HookMatcher(
                        matcher = ".*", // 统计Hook匹配所有工具
                        hooks = listOf(statisticsHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("🔌 连接客户端...")
            client.connect()
            assertTrue(client.isConnected(), "客户端应该成功连接")
            
            // 验证服务器信息包含初始化结果
            val serverInfo = client.getServerInfo()
            assertNotNull(serverInfo, "应该有服务器初始化信息")
            println("ℹ️ 服务器信息: $serverInfo")
            
            // 测试安全命令（应该通过）
            println("\n--- 测试安全命令 ---")
            client.query("请执行命令: echo 'Hook测试成功'")
            
            var receivedMessages = 0
            client.receiveResponse().collect { message ->
                receivedMessages++
                println("📨 收到消息 $receivedMessages: ${message::class.simpleName}")
                
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> println("🤖 Claude: ${block.text}")
                                is ToolUseBlock -> {
                                    println("🔧 工具调用: ${block.name}")
                                    lastExecutedTools.add(block.name)
                                }
                                else -> println("🔹 其他内容块: ${block::class.simpleName}")
                            }
                        }
                    }
                    is SystemMessage -> {
                        println("🔧 系统消息: ${message.subtype}")
                    }
                    is ResultMessage -> {
                        println("📊 结果: ${message.subtype}, error=${message.isError}")
                        return@collect
                    }
                    else -> {
                        println("📄 其他消息: ${message::class.simpleName}")
                    }
                }
            }
            
            // 验证Hook执行
            println("\n--- 验证Hook执行结果 ---")
            println("🎣 Hook执行次数: ${hookExecutionCount.get()}")
            println("🔧 工具执行次数: ${toolExecutionCount.get()}")
            println("📋 执行的工具: $lastExecutedTools")
            println("📊 Hook结果: $hookResults")
            
            // 关键验证
            assertTrue(hookExecutionCount.get() > 0, "Hook应该被执行")
            assertTrue(hookResults.isNotEmpty(), "应该有Hook执行结果")
            assertTrue(hookResults.any { it.contains("SecurityHook执行") }, "安全Hook应该被执行")
            
            println("✅ Hook初始化和回调机制测试成功")
            
        } finally {
            client.disconnect()
        }
    }

    /**
     * 测试2: 验证SDK MCP Server处理
     */
    @Test
    fun `test SDK MCP server handling`() = runBlocking {
        println("=== 📦 SDK MCP Server处理测试 ===")
        
        // 创建自定义MCP Server配置（仿照Python SDK）
        val calculatorServer = mapOf(
            "type" to "sdk",
            "name" to "calculator",
            "instance" to object {
                fun add(a: Double, b: Double): Double = a + b
                fun multiply(a: Double, b: Double): Double = a * b
            }
        )
        
        // 创建Hook来拦截和处理MCP工具调用
        val mcpInterceptorHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            println("🎯 [MCP拦截器] 处理工具: $toolName")
            
            when {
                toolName.contains("mcp__calculator__add") -> {
                    val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                    val result = a + b
                    
                    println("🧮 [计算器] 加法: $a + $b = $result")
                    hookResults.add("计算器加法: $a + $b = $result")
                    
                    HookJSONOutput(
                        decision = "block", // 阻止默认MCP调用
                        systemMessage = "🧮 SDK计算器完成加法: $result",
                        hookSpecificOutput = JsonPrimitive("calculator_add_executed")
                    )
                }
                
                toolName.contains("mcp__calculator__multiply") -> {
                    val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                    val result = a * b
                    
                    println("🧮 [计算器] 乘法: $a × $b = $result")
                    hookResults.add("计算器乘法: $a × $b = $result")
                    
                    HookJSONOutput(
                        decision = "block",
                        systemMessage = "🧮 SDK计算器完成乘法: $result",
                        hookSpecificOutput = JsonPrimitive("calculator_multiply_executed")
                    )
                }
                
                else -> HookJSONOutput(systemMessage = "非MCP工具")
            }
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            // 关键：配置SDK MCP服务器
            mcpServers = mapOf("calculator" to calculatorServer),
            allowedTools = listOf(
                "mcp__calculator__add",
                "mcp__calculator__multiply"
            ),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__calculator__.*",
                        hooks = listOf(mcpInterceptorHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            assertTrue(client.isConnected(), "客户端应该连接成功")
            
            // 验证MCP服务器注册
            val serverInfo = client.getServerInfo()
            println("ℹ️ 服务器信息（包含MCP）: $serverInfo")
            
            // 测试MCP工具调用
            println("\n--- 测试MCP工具调用 ---")
            client.query("请使用calculator的add工具计算 25 + 17，然后用multiply工具计算 6 × 8")
            
            hookResults.clear()
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> println("🤖 Claude: ${block.text}")
                                is ToolUseBlock -> {
                                    println("🔧 尝试调用工具: ${block.name}")
                                    println("   参数: ${block.input}")
                                }
                                else -> println("🔹 其他内容块: ${block::class.simpleName}")
                            }
                        }
                    }
                    is SystemMessage -> {
                        println("🔧 系统消息: ${message.subtype} - ${message.data}")
                    }
                    is ResultMessage -> {
                        println("📊 MCP测试结果: ${message.subtype}")
                        return@collect
                    }
                    else -> {
                        println("📄 其他消息: ${message::class.simpleName}")
                    }
                }
            }
            
            // 验证MCP处理结果
            println("\n--- 验证MCP处理结果 ---")
            println("📊 Hook结果: $hookResults")
            
            // 关键验证
            assertTrue(hookResults.any { it.contains("计算器") }, "应该有计算器工具执行结果")
            println("✅ SDK MCP Server处理测试成功")
            
        } finally {
            client.disconnect()
        }
    }

    /**
     * 测试3: 综合测试 - Hook和MCP同时工作
     */
    @Test
    fun `test comprehensive hooks and MCP integration`() = runBlocking {
        println("=== 🚀 综合集成测试：Hook + MCP + 真实AI调用 ===")
        
        hookResults.clear()
        toolExecutionCount.set(0)
        hookExecutionCount.set(0)
        
        // 多层Hook系统
        val securityHook: HookCallback = { input, toolUseId, context ->
            hookExecutionCount.incrementAndGet()
            val toolName = input["tool_name"] as? String ?: ""
            println("🛡️ [安全层] 检查: $toolName")
            HookJSONOutput(systemMessage = "✅ 安全层检查通过")
        }
        
        val mcpHook: HookCallback = { input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            if (toolName.contains("mcp__")) {
                println("📦 [MCP层] 处理: $toolName")
                hookResults.add("MCP工具处理: $toolName")
                HookJSONOutput(
                    decision = "block",
                    systemMessage = "📦 MCP工具在SDK中执行完成",
                    hookSpecificOutput = JsonPrimitive("mcp_handled")
                )
            } else {
                HookJSONOutput(systemMessage = "非MCP工具")
            }
        }
        
        val statisticsHook: HookCallback = { input, _, _ ->
            val count = toolExecutionCount.incrementAndGet()
            println("📊 [统计层] 第 $count 次工具调用")
            HookJSONOutput(systemMessage = "📊 统计: $count 次调用")
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            mcpServers = mapOf(
                "testServer" to mapOf(
                    "type" to "sdk",
                    "name" to "testServer",
                    "instance" to "mock_server"
                )
            ),
            allowedTools = listOf("Bash", "Read", "mcp__testServer__process"),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher("Bash", listOf(securityHook)),
                    HookMatcher("mcp__.*", listOf(mcpHook)),
                    HookMatcher(".*", listOf(statisticsHook))
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            // 综合测试查询
            client.query("你好！请执行一个简单的echo命令输出'集成测试成功'")
            
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        println("🤖 收到AI响应")
                    }
                    is ResultMessage -> {
                        println("📊 综合测试完成: ${message.subtype}")
                        return@collect
                    }
                    else -> {
                        println("📄 其他消息: ${message::class.simpleName}")
                    }
                }
            }
            
            // 最终验证
            println("\n--- 最终验证 ---")
            println("🎣 Hook执行次数: ${hookExecutionCount.get()}")
            println("🔧 工具调用次数: ${toolExecutionCount.get()}")
            println("📋 处理结果: $hookResults")
            
            // 关键成功指标
            assertTrue(hookExecutionCount.get() > 0, "Hook系统应该正常工作")
            assertTrue(toolExecutionCount.get() > 0, "工具统计应该正常工作")
            
            println("🎉 综合集成测试成功！SDK修复验证通过！")
            
        } finally {
            client.disconnect()
        }
    }
}
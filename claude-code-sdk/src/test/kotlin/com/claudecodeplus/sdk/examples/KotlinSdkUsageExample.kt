package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kotlin Claude Code SDK 使用示例
 * 展示如何创建自定义 Hook 和 MCP 工具
 */
class KotlinSdkUsageExample {

    // 全局状态管理
    companion object {
        private val requestCounter = AtomicInteger(0)
        private val customToolResults = mutableListOf<String>()
    }

    /**
     * 示例1：基础安全 Hook
     * 阻止危险的 Bash 命令执行
     */
    private val securityHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        println("🔒 [安全检查] 工具: $toolName")
        
        if (toolName == "Bash") {
            val command = toolInput["command"] as? String ?: ""
            val dangerousPatterns = listOf("rm -rf", "sudo", "format", "delete")
            
            for (pattern in dangerousPatterns) {
                if (command.contains(pattern, ignoreCase = true)) {
                    println("🚫 [安全检查] 阻止危险命令: $command")
                    HookJSONOutput(
                        decision = "block",
                        systemMessage = "🛡️ 安全策略阻止执行危险命令",
                        hookSpecificOutput = JsonPrimitive("security_block")
                    )
                }
            }
        }
        
        // 允许安全操作
        HookJSONOutput(systemMessage = "✅ 安全检查通过")
    }

    /**
     * 示例2：自定义 MCP 工具 Hook
     * 拦截并在 Kotlin 中执行自定义业务逻辑
     */
    private val customToolHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        when {
            // 自定义计算器工具
            toolName.contains("calculator") -> {
                println("🧮 [自定义工具] 执行计算器功能")
                handleCalculatorTool(toolName, toolInput)
            }
            
            // 自定义数据库工具
            toolName.contains("database") -> {
                println("🗄️ [自定义工具] 执行数据库操作")
                handleDatabaseTool(toolName, toolInput)
            }
            
            // 自定义文件处理工具
            toolName.contains("fileprocessor") -> {
                println("📁 [自定义工具] 执行文件处理")
                handleFileProcessorTool(toolName, toolInput)
            }
            
            else -> {
                // 不是自定义工具，放行
                HookJSONOutput(systemMessage = "非自定义工具，正常执行")
            }
        }
    }

    /**
     * 计算器工具处理函数
     */
    private suspend fun handleCalculatorTool(toolName: String, toolInput: Map<*, *>): HookJSONOutput {
        return try {
            val result = when {
                toolName.contains("add") -> {
                    val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                    val sum = a + b
                    "计算结果: $a + $b = $sum"
                }
                toolName.contains("multiply") -> {
                    val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                    val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                    val product = a * b
                    "计算结果: $a × $b = $product"
                }
                else -> "未知计算操作"
            }
            
            customToolResults.add(result)
            println("✅ [计算器] $result")
            
            HookJSONOutput(
                decision = "block", // 阻止默认工具执行
                systemMessage = "🧮 计算完成: $result",
                hookSpecificOutput = JsonPrimitive("calculator_executed")
            )
        } catch (e: Exception) {
            println("❌ [计算器] 执行失败: ${e.message}")
            HookJSONOutput(
                decision = "block",
                systemMessage = "❌ 计算器工具执行失败: ${e.message}",
                hookSpecificOutput = JsonPrimitive("calculator_error")
            )
        }
    }

    /**
     * 数据库工具处理函数
     */
    private suspend fun handleDatabaseTool(toolName: String, toolInput: Map<*, *>): HookJSONOutput {
        return try {
            val query = toolInput["query"] as? String ?: ""
            val mockResult = "模拟数据库查询结果: 找到 ${(1..10).random()} 条记录"
            
            customToolResults.add(mockResult)
            println("✅ [数据库] 执行查询: $query -> $mockResult")
            
            HookJSONOutput(
                decision = "block",
                systemMessage = "🗄️ 数据库查询完成: $mockResult",
                hookSpecificOutput = JsonPrimitive("database_executed")
            )
        } catch (e: Exception) {
            HookJSONOutput(
                decision = "block",
                systemMessage = "❌ 数据库操作失败: ${e.message}",
                hookSpecificOutput = JsonPrimitive("database_error")
            )
        }
    }

    /**
     * 文件处理工具处理函数
     */
    private suspend fun handleFileProcessorTool(toolName: String, toolInput: Map<*, *>): HookJSONOutput {
        return try {
            val filePath = toolInput["file"] as? String ?: ""
            val operation = toolInput["operation"] as? String ?: "process"
            val result = "文件处理完成: 对 $filePath 执行 $operation 操作"
            
            customToolResults.add(result)
            println("✅ [文件处理] $result")
            
            HookJSONOutput(
                decision = "block",
                systemMessage = "📁 $result",
                hookSpecificOutput = JsonPrimitive("fileprocessor_executed")
            )
        } catch (e: Exception) {
            HookJSONOutput(
                decision = "block",
                systemMessage = "❌ 文件处理失败: ${e.message}",
                hookSpecificOutput = JsonPrimitive("fileprocessor_error")
            )
        }
    }

    /**
     * 示例3：请求统计 Hook
     * 统计所有工具调用
     */
    private val statisticsHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: "unknown"
        val count = requestCounter.incrementAndGet()
        
        println("📊 [统计] 第 $count 次工具调用: $toolName")
        
        HookJSONOutput(
            systemMessage = "📊 工具调用统计: 总计 $count 次调用"
        )
    }

    /**
     * 完整的使用示例
     */
    suspend fun runCompleteExample() {
        println("=== 🚀 Kotlin Claude Code SDK 完整使用示例 ===\n")
        
        // 重置状态
        requestCounter.set(0)
        customToolResults.clear()
        
        // 1. 配置 MCP 服务器
        val mcpServers = mapOf(
            "calculator" to McpStdioServerConfig(
                command = "echo",
                args = listOf("calculator-server")
            ),
            "database" to McpHttpServerConfig(
                url = "http://localhost:8080/mcp"
            ),
            "fileprocessor" to McpSSEServerConfig(
                url = "https://api.example.com/mcp"
            )
        )

        // 2. 配置 Claude Code 选项
        val options = ClaudeCodeOptions(
            // 基础配置
            model = "claude-3-5-sonnet-20241022",
            
            // MCP 服务器配置
            mcpServers = mcpServers,
            
            // 工具权限配置
            allowedTools = listOf(
                "Bash",
                "Read", 
                "Write",
                // 自定义 MCP 工具
                "mcp__calculator__add",
                "mcp__calculator__multiply", 
                "mcp__database__query",
                "mcp__fileprocessor__process"
            ),
            
            // Hooks 配置
            hooks = mapOf(
                // PRE_TOOL_USE: 工具执行前的拦截
                HookEvent.PRE_TOOL_USE to listOf(
                    // 安全检查 Hook (优先级最高)
                    HookMatcher(
                        matcher = "Bash", // 只匹配 Bash 工具
                        hooks = listOf(securityHook)
                    ),
                    // 自定义工具处理 Hook
                    HookMatcher(
                        matcher = "mcp__.*", // 匹配所有 MCP 工具
                        hooks = listOf(customToolHook)
                    ),
                    // 统计 Hook (匹配所有工具)
                    HookMatcher(
                        matcher = ".*", // 匹配所有工具
                        hooks = listOf(statisticsHook)
                    )
                ),
                
                // USER_PROMPT_SUBMIT: 用户提交提示时
                HookEvent.USER_PROMPT_SUBMIT to listOf(
                    HookMatcher(
                        matcher = null, // 匹配所有提示
                        hooks = listOf { input, toolUseId, context ->
                            println("📝 [用户提示] 新的对话开始")
                            HookJSONOutput(
                                systemMessage = "欢迎使用 Claude Code SDK！我已配置了安全检查和自定义工具。"
                            )
                        }
                    )
                )
            )
        )

        // 3. 创建并使用客户端
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("🔌 连接到 Claude...")
            client.connect()
            
            if (!client.isConnected()) {
                println("❌ 连接失败")
                return
            }
            
            println("✅ 连接成功!\n")
            
            // 测试场景1: 安全的 Bash 命令
            println("--- 测试1: 安全 Bash 命令 ---")
            testSafeBashCommand(client)
            
            delay(2000)
            
            // 测试场景2: 自定义计算器工具
            println("\n--- 测试2: 自定义计算器工具 ---")
            testCustomCalculator(client)
            
            delay(2000)
            
            // 测试场景3: 危险命令拦截
            println("\n--- 测试3: 危险命令拦截 ---")
            testDangerousCommand(client)
            
            // 显示最终统计
            println("\n--- 📊 最终统计 ---")
            println("总工具调用次数: ${requestCounter.get()}")
            println("自定义工具执行结果:")
            customToolResults.forEachIndexed { index, result ->
                println("  ${index + 1}. $result")
            }
            
        } catch (e: Exception) {
            println("❌ 执行异常: ${e.message}")
            e.printStackTrace()
        } finally {
            println("\n🔌 断开连接...")
            client.disconnect()
            println("✅ 示例完成!")
        }
    }

    private suspend fun testSafeBashCommand(client: ClaudeCodeSdkClient) {
        println("🗣️ 请求: 执行安全的 echo 命令")
        client.query("请运行 bash 命令: echo 'Hello from Kotlin SDK!'")
        
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> println("🤖 Claude: ${block.text}")
                                is ToolUseBlock -> println("🔧 工具调用: ${block.name}")
                                else -> {}
                            }
                        }
                    }
                    is ResultMessage -> {
                        println("📊 结果: ${message.subtype}")
                        return@collect
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun testCustomCalculator(client: ClaudeCodeSdkClient) {
        println("🗣️ 请求: 使用计算器工具计算 25 + 17")
        client.query("请使用 calculator 的 add 工具计算 25 + 17")
        
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> println("🤖 Claude: ${block.text}")
                                is ToolUseBlock -> {
                                    println("🔧 工具调用: ${block.name}")
                                    // 模拟执行自定义工具
                                    if (block.name.contains("add")) {
                                        launch {
                                            handleCalculatorTool(block.name, block.input as Map<*, *>)
                                        }
                                    }
                                }
                                else -> {}
                            }
                        }
                    }
                    is ResultMessage -> {
                        println("📊 结果: ${message.subtype}")
                        return@collect
                    }
                    else -> {}
                }
            }
        }
    }

    private suspend fun testDangerousCommand(client: ClaudeCodeSdkClient) {
        println("🗣️ 请求: 尝试执行危险命令 (应被阻止)")
        client.query("请运行 bash 命令: rm -rf /tmp/test")
        
        withTimeout(15000) {
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> println("🤖 Claude: ${block.text}")
                                is ToolUseBlock -> println("🔧 工具调用: ${block.name}")
                                else -> {}
                            }
                        }
                    }
                    is ResultMessage -> {
                        println("📊 结果: ${message.subtype}")
                        return@collect
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * 运行示例的主函数
 */
suspend fun main() {
    val example = KotlinSdkUsageExample()
    example.runCompleteExample()
}
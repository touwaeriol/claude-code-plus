package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive

/**
 * 🚀 Kotlin Claude Code SDK 快速上手示例
 * 
 * 展示如何：
 * 1. 创建基础客户端
 * 2. 添加安全 Hook
 * 3. 实现自定义 MCP 工具
 */

// 1️⃣ 最简单的客户端创建
suspend fun basicClientExample() {
    println("=== 基础客户端示例 ===")
    
    val options = ClaudeCodeOptions(
        model = "claude-3-5-sonnet-20241022"
    )
    
    val client = ClaudeCodeSdkClient(options)
    
    client.use { // 自动管理连接生命周期
        client.query("你好！请介绍一下你自己。")
        
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("🤖 Claude: ${block.text}")
                        }
                    }
                }
                is ResultMessage -> return@collect
                else -> {}
            }
        }
    }
}

// 2️⃣ 添加安全 Hook
suspend fun securityHookExample() {
    println("=== 安全 Hook 示例 ===")
    
    // 定义安全检查 Hook
    val securityHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        if (toolName == "Bash") {
            val command = toolInput["command"] as? String ?: ""
            
            // 检查危险命令
            if (command.contains("rm -rf") || command.contains("sudo")) {
                println("🚫 阻止危险命令: $command")
                
                HookJSONOutput(
                    decision = "block", // 阻止执行
                    systemMessage = "🛡️ 安全策略：禁止执行危险命令",
                    hookSpecificOutput = JsonPrimitive("security_block")
                )
            }
        }
        
        // 允许安全操作
        HookJSONOutput(systemMessage = "✅ 安全检查通过")
    }
    
    val options = ClaudeCodeOptions(
        model = "claude-3-5-sonnet-20241022",
        allowedTools = listOf("Bash"), // 允许 Bash 工具
        hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = "Bash", // 只拦截 Bash 工具
                    hooks = listOf(securityHook)
                )
            )
        )
    )
    
    val client = ClaudeCodeSdkClient(options)
    
    client.use {
        client.connect()
        println("🗣️ 测试安全命令...")
        client.query("请运行命令: echo 'Hello World'")
        
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
                is ResultMessage -> return@collect
                else -> {}
            }
        }
        
        delay(2000)
        
        println("\n🗣️ 测试危险命令（应被阻止）...")
        client.query("请运行命令: sudo rm -rf /tmp")
        
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("🤖 Claude: ${block.text}")
                        }
                    }
                }
                is ResultMessage -> return@collect
                else -> {}
            }
        }
    }
}

// 3️⃣ 自定义 MCP 工具（通过 Hook 实现）
suspend fun customToolExample() {
    println("=== 自定义工具示例 ===")
    
    // 自定义计算器数据
    val calculatorResults = mutableListOf<String>()
    
    // 自定义工具 Hook
    val calculatorHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        when {
            toolName.contains("calculator_add") -> {
                val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                val result = a + b
                val resultText = "计算结果: $a + $b = $result"
                
                calculatorResults.add(resultText)
                println("🧮 [计算器] $resultText")
                
                HookJSONOutput(
                    decision = "block", // 阻止默认 MCP 调用
                    systemMessage = "🧮 $resultText",
                    hookSpecificOutput = JsonPrimitive("calculator_executed")
                )
            }
            
            toolName.contains("calculator_multiply") -> {
                val a = (toolInput["a"] as? Number)?.toDouble() ?: 0.0
                val b = (toolInput["b"] as? Number)?.toDouble() ?: 0.0
                val result = a * b
                val resultText = "计算结果: $a × $b = $result"
                
                calculatorResults.add(resultText)
                println("🧮 [计算器] $resultText")
                
                HookJSONOutput(
                    decision = "block",
                    systemMessage = "🧮 $resultText",
                    hookSpecificOutput = JsonPrimitive("calculator_executed")
                )
            }
            
            else -> {
                HookJSONOutput(systemMessage = "非计算器工具")
            }
        }
    }
    
    val options = ClaudeCodeOptions(
        model = "claude-3-5-sonnet-20241022",
        
        // 配置虚拟 MCP 服务器（实际不会调用，因为被 Hook 拦截）
        mcpServers = mapOf(
            "calculator" to McpStdioServerConfig(
                command = "echo",
                args = listOf("calculator-server")
            )
        ),
        
        // 允许自定义工具
        allowedTools = listOf(
            "mcp__calculator__add",
            "mcp__calculator__multiply"
        ),
        
        hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = "mcp__calculator__.*", // 拦截所有计算器工具
                    hooks = listOf(calculatorHook)
                )
            )
        )
    )
    
    val client = ClaudeCodeSdkClient(options)
    
    client.use {
        client.connect()
        println("🗣️ 请求计算...")
        client.query("请使用 calculator 的 add 工具计算 15 + 27，然后用 multiply 工具计算 6 × 8")
        
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("🤖 Claude: ${block.text}")
                            is ToolUseBlock -> {
                                println("🔧 工具调用: ${block.name}")
                                
                                // 手动触发计算器执行（模拟 Hook 调用）
                                runBlocking {
                                    calculatorHook(
                                        mapOf(
                                            "tool_name" to block.name,
                                            "tool_input" to block.input
                                        ),
                                        null,
                                        HookContext(emptyMap<String, Any>())
                                    )
                                }
                            }
                            else -> {}
                        }
                    }
                }
                is ResultMessage -> return@collect
                else -> {}
            }
        }
    }
    
    println("\n📊 计算器执行结果:")
    calculatorResults.forEachIndexed { index, result ->
        println("  ${index + 1}. $result")
    }
}

// 4️⃣ 完整功能示例
suspend fun fullFeaturedExample() {
    println("=== 完整功能示例 ===")
    
    var toolCallCount = 0
    val customResults = mutableListOf<String>()
    
    // 多功能 Hook
    val multiHook: HookCallback = { input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        toolCallCount++
        println("📊 [统计] 第 $toolCallCount 次工具调用: $toolName")
        
        when {
            // 安全检查
            toolName == "Bash" -> {
                val command = toolInput["command"] as? String ?: ""
                if (command.contains("dangerous")) {
                    HookJSONOutput(
                        decision = "block",
                        systemMessage = "🚫 危险命令被阻止",
                        hookSpecificOutput = JsonPrimitive("security_block")
                    )
                } else {
                    HookJSONOutput(systemMessage = "✅ Bash 命令安全")
                }
            }
            
            // 自定义数据处理工具
            toolName.contains("data_processor") -> {
                val data = toolInput["data"] as? String ?: "no data"
                val processed = "处理后的数据: ${data.uppercase()}"
                customResults.add(processed)
                
                println("📊 [数据处理] $processed")
                HookJSONOutput(
                    decision = "block",
                    systemMessage = "📊 数据处理完成: $processed",
                    hookSpecificOutput = JsonPrimitive("data_processed")
                )
            }
            
            else -> {
                HookJSONOutput(systemMessage = "🔄 工具调用记录")
            }
        }
    }
    
    val options = ClaudeCodeOptions(
        model = "claude-3-5-sonnet-20241022",
        
        mcpServers = mapOf(
            "data" to McpHttpServerConfig(url = "http://localhost:8080/mcp")
        ),
        
        allowedTools = listOf(
            "Bash",
            "Read",
            "Write", 
            "mcp__data__processor"
        ),
        
        hooks = mapOf(
            HookEvent.PRE_TOOL_USE to listOf(
                HookMatcher(
                    matcher = ".*", // 拦截所有工具
                    hooks = listOf(multiHook)
                )
            ),
            
            HookEvent.USER_PROMPT_SUBMIT to listOf(
                HookMatcher(
                    matcher = null,
                    hooks = listOf { _, _, _ ->
                        println("📝 [会话] 用户提交新提示")
                        HookJSONOutput(systemMessage = "会话开始，已启用安全检查和自定义工具")
                    }
                )
            )
        )
    )
    
    val client = ClaudeCodeSdkClient(options)
    
    client.use {
        client.connect()
        client.query("你好！请帮我处理一些数据，并运行一个安全的 echo 命令。")
        
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("🤖 Claude: ${block.text}")
                            is ToolUseBlock -> println("🔧 工具: ${block.name}")
                            else -> {}
                        }
                    }
                }
                is ResultMessage -> return@collect
                else -> {}
            }
        }
    }
    
    println("\n📊 最终统计:")
    println("工具调用总数: $toolCallCount")
    println("自定义处理结果: ${customResults.size} 条")
}

/**
 * 运行所有示例
 */
suspend fun main() {
    try {
        basicClientExample()
        println("\n" + "=".repeat(50) + "\n")
        
        securityHookExample()
        println("\n" + "=".repeat(50) + "\n")
        
        customToolExample()
        println("\n" + "=".repeat(50) + "\n")
        
        fullFeaturedExample()
        
    } catch (e: Exception) {
        println("❌ 示例执行异常: ${e.message}")
        e.printStackTrace()
    }
}
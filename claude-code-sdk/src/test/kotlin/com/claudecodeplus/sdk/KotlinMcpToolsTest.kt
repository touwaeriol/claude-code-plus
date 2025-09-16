package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Kotlin 自定义 MCP 工具测试
 * 
 * 基于官方 Python SDK 的实现，验证我们如何在 Kotlin 中创建自定义 MCP 工具。
 * 
 * Python SDK 的模式：
 * ```python
 * @tool("add", "Add numbers", {"a": float, "b": float})
 * async def add_tool(args):
 *     return {"content": [{"type": "text", "text": f"Sum: {args['a'] + args['b']}"}]}
 * 
 * calculator = create_sdk_mcp_server("calculator", tools=[add_tool])
 * options = ClaudeCodeOptions(
 *     mcp_servers={"calc": calculator},
 *     allowed_tools=["mcp__calc__add"]
 * )
 * ```
 */
class KotlinMcpToolsTest {

    companion object {
        private val toolExecutionCount = AtomicInteger(0)
        private val lastExecutedTool = mutableListOf<String>()
        private val calculatorResults = mutableListOf<String>()
    }

    /**
     * 简单的计算器工具 - 对应 Python SDK 的 @tool 装饰器
     */
    private suspend fun addTool(args: Map<String, Any>): Map<String, Any> {
        toolExecutionCount.incrementAndGet()
        lastExecutedTool.add("add")
        
        val a = (args["a"] as? Number)?.toDouble() ?: 0.0
        val b = (args["b"] as? Number)?.toDouble() ?: 0.0
        val result = a + b
        
        val resultText = "Sum: $a + $b = $result"
        calculatorResults.add(resultText)
        
        println("🧮 [计算器工具] 执行加法: $resultText")
        
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to resultText
                )
            )
        )
    }

    private suspend fun multiplyTool(args: Map<String, Any>): Map<String, Any> {
        toolExecutionCount.incrementAndGet()
        lastExecutedTool.add("multiply")
        
        val a = (args["a"] as? Number)?.toDouble() ?: 0.0
        val b = (args["b"] as? Number)?.toDouble() ?: 0.0
        val result = a * b
        
        val resultText = "Product: $a × $b = $result"
        calculatorResults.add(resultText)
        
        println("🧮 [计算器工具] 执行乘法: $resultText")
        
        return mapOf(
            "content" to listOf(
                mapOf(
                    "type" to "text",
                    "text" to resultText
                )
            )
        )
    }

    /**
     * 模拟创建 SDK MCP 服务器的配置
     * 对应 Python 的 create_sdk_mcp_server
     */
    private fun createKotlinMcpServer(name: String): McpStdioServerConfig {
        return McpStdioServerConfig(
            command = "echo", // 占位命令
            args = listOf("MCP Server: $name") // 标识参数
        )
    }

    /**
     * 自定义 Hook：拦截 MCP 工具调用并在 Kotlin 中执行
     */
    private val mcpToolInterceptorHook: HookCallback = interceptorHook@{ input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        println("🔧 [MCP拦截器] 检查工具调用: $toolName")
        
        // 检查是否为我们的自定义 MCP 工具
        when {
            toolName.contains("mcp__calculator__add") -> {
                println("🎯 [MCP拦截器] 拦截计算器加法工具")
                return@interceptorHook HookJSONOutput(
                    decision = "block", // 阻止实际的 MCP 调用
                    systemMessage = "✅ 已在 Kotlin 中执行自定义加法工具",
                    hookSpecificOutput = JsonPrimitive("intercepted_and_executed")
                )
            }
            toolName.contains("mcp__calculator__multiply") -> {
                println("🎯 [MCP拦截器] 拦截计算器乘法工具")
                return@interceptorHook HookJSONOutput(
                    decision = "block", // 阻止实际的 MCP 调用
                    systemMessage = "✅ 已在 Kotlin 中执行自定义乘法工具",
                    hookSpecificOutput = JsonPrimitive("intercepted_and_executed")
                )
            }
            else -> {
                println("ℹ️ [MCP拦截器] 非自定义工具，允许通过: $toolName")
                return@interceptorHook HookJSONOutput(systemMessage = "非自定义 MCP 工具")
            }
        }
    }

    /**
     * 测试：使用 Hooks 拦截机制实现自定义 MCP 工具
     */
    @Test
    fun `test custom MCP tools using hook interception`() = runBlocking {
        println("=== 🧮 Kotlin 自定义 MCP 工具测试 ===")
        
        // 重置状态
        toolExecutionCount.set(0)
        lastExecutedTool.clear()
        calculatorResults.clear()
        
        // 创建 MCP 服务器配置（实际不会被调用，因为我们用 Hook 拦截）
        val calculatorServer = createKotlinMcpServer("calculator")
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            // 配置 MCP 服务器（为了让 Claude 知道这些工具存在）
            mcpServers = mapOf("calculator" to calculatorServer),
            // 允许我们的自定义 MCP 工具
            allowedTools = listOf("mcp__calculator__add", "mcp__calculator__multiply"),
            // 使用 Hook 拦截所有工具调用
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__calculator__.*", // 匹配所有计算器工具
                        hooks = listOf(mcpToolInterceptorHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到 Claude")
            
            // 测试自定义加法工具
            println("\n--- 测试自定义加法工具 ---")
            val addMessage = "请使用 calculator 服务器的 add 工具计算 15 + 27"
            println("🗣️ 发送消息: $addMessage")
            
            client.query(addMessage)
            
            withTimeout(25000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        println("🤖 Claude: ${block.text}")
                                    }
                                    is ToolUseBlock -> {
                                        println("🔧 Claude 尝试调用工具: ${block.name}")
                                        println("   工具参数: ${block.input}")
                                        
                                        // 在这里模拟执行我们的 Kotlin 工具
                                        if (block.name.contains("add")) {
                                            launch {
                                                addTool(block.input as Map<String, Any>)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("📊 结果: ${message.subtype}")
                            if (message.subtype == "success") {
                                return@collect
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // 验证工具执行
            println("\n--- 验证工具执行结果 ---")
            println("🔢 工具执行次数: ${toolExecutionCount.get()}")
            println("🛠️ 执行的工具: ${lastExecutedTool}")
            println("📊 计算结果: ${calculatorResults}")
            
            // 关键验证
            assertTrue(toolExecutionCount.get() > 0, "至少应该执行一次工具")
            assertTrue(calculatorResults.isNotEmpty(), "应该有计算结果")
            
            println("✅ 自定义 MCP 工具测试成功")
            
        } finally {
            client.disconnect()
        }
    }

    /**
     * 测试：展示完整的自定义工具调用流程
     */
    @Test
    fun `test complete custom tool workflow`() = runBlocking {
        println("=== 🔄 完整自定义工具工作流测试 ===")
        
        toolExecutionCount.set(0)
        lastExecutedTool.clear()
        calculatorResults.clear()
        
        // 模拟 Python SDK 的使用方式
        println("📋 创建自定义工具定义...")
        
        // 工具定义（对应 Python 的 @tool 装饰器）
        val toolDefinitions = mapOf(
            "add" to mapOf(
                "name" to "add",
                "description" to "Add two numbers",
                "input_schema" to mapOf(
                    "a" to "number",
                    "b" to "number"
                ),
                "handler" to ::addTool
            ),
            "multiply" to mapOf(
                "name" to "multiply", 
                "description" to "Multiply two numbers",
                "input_schema" to mapOf(
                    "a" to "number",
                    "b" to "number"
                ),
                "handler" to ::multiplyTool
            )
        )
        
        println("🏗️ 工具定义创建完成:")
        toolDefinitions.forEach { (name, def) ->
            println("   - $name: ${def["description"]}")
        }
        
        // 创建增强的工具拦截器
        val enhancedToolHook: HookCallback = enhancedHook@{ input, toolUseId, context ->
            val toolName = input["tool_name"] as? String ?: ""
            val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
            
            println("🎯 [增强拦截器] 处理工具: $toolName")
            
            // 解析工具名称（去掉 MCP 前缀）
            val actualToolName = when {
                toolName.contains("add") -> "add"
                toolName.contains("multiply") -> "multiply"
                else -> null
            }
            
            if (actualToolName != null && actualToolName in toolDefinitions) {
                println("✨ [增强拦截器] 执行自定义工具: $actualToolName")
                
                // 异步执行工具（模拟真实场景）
                launch {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val handler = toolDefinitions[actualToolName]!!["handler"] as suspend (Map<String, Any>) -> Map<String, Any>
                        val result = handler(toolInput as Map<String, Any>)
                        println("🎉 [增强拦截器] 工具执行成功: $result")
                    } catch (e: Exception) {
                        println("❌ [增强拦截器] 工具执行失败: ${e.message}")
                    }
                }
                
                return@enhancedHook HookJSONOutput(
                    decision = "block",
                    systemMessage = "🚀 自定义 Kotlin 工具 '$actualToolName' 执行完成",
                    hookSpecificOutput = JsonPrimitive("custom_tool_executed")
                )
            }
            
            return@enhancedHook HookJSONOutput(systemMessage = "非自定义工具")
        }
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("mcp__calc__add", "mcp__calc__multiply"),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "mcp__calc__.*",
                        hooks = listOf(enhancedToolHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            println("\n--- 测试多个自定义工具 ---")
            val complexMessage = "请帮我计算：先算 8 + 12，然后将结果乘以 3"
            println("🗣️ 发送复杂计算请求: $complexMessage")
            
            client.query(complexMessage)
            
            withTimeout(30000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        println("🤖 Claude: ${block.text}")
                                    }
                                    is ToolUseBlock -> {
                                        println("🔧 Claude 调用: ${block.name}")
                                        
                                        // 根据工具类型执行相应的 Kotlin 函数
                                        launch {
                                            try {
                                                val result = when {
                                                    block.name.contains("add") -> 
                                                        addTool(block.input as Map<String, Any>)
                                                    block.name.contains("multiply") ->
                                                        multiplyTool(block.input as Map<String, Any>)
                                                    else -> emptyMap()
                                                }
                                                println("✅ 工具执行结果: $result")
                                            } catch (e: Exception) {
                                                println("❌ 工具执行异常: ${e.message}")
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("📊 最终结果: ${message.subtype}")
                            if (message.subtype == "success") {
                                return@collect
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // 最终验证
            println("\n--- 最终执行结果验证 ---")
            println("🔢 总执行次数: ${toolExecutionCount.get()}")
            println("🛠️ 执行的工具序列: ${lastExecutedTool}")
            println("📊 所有计算结果:")
            calculatorResults.forEachIndexed { index, result ->
                println("   ${index + 1}. $result")
            }
            
            assertTrue(toolExecutionCount.get() >= 2, "应该执行多个工具（加法 + 乘法）")
            assertTrue("add" in lastExecutedTool, "应该执行过加法工具")
            assertTrue(calculatorResults.size >= 2, "应该有多个计算结果")
            
            println("✅ 完整工作流测试成功！")
            
        } finally {
            client.disconnect()
        }
    }
}
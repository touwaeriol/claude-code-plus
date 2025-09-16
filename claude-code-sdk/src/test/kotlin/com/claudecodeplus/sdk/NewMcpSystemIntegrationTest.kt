package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.examples.*
import com.claudecodeplus.sdk.mcp.*
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 简化 MCP 系统完整集成测试
 * 
 * 验证简化后的功能：
 * 1. ✅ McpServerBase 基类和注解系统
 * 2. ✅ simpleTool 快捷工具创建
 * 3. ✅ ControlProtocol 的接口支持
 * 4. ✅ 真实 AI 调用和工具执行测试
 */
class NewMcpSystemIntegrationTest {

    /**
     * 测试1: 基于注解的 MCP Server 功能
     */
    @Test
    fun `test annotation-based MCP server`() = runBlocking {
        println("=== 🧮 基于注解的 MCP Server 测试 ===")
        
        // 创建注解服务器实例
        val calculatorServer = CalculatorServer()
        
        // 验证服务器配置
        assertEquals("CalculatorServer", calculatorServer.name)  // 使用默认类名
        assertEquals("1.0.0", calculatorServer.version)
        assertEquals("", calculatorServer.description)  // 无描述注解
        
        // 获取工具列表
        val tools = calculatorServer.listTools()
        println("📋 发现 ${tools.size} 个工具: ${tools.map { it.name }}")
        
        // 验证注解工具被正确发现
        val toolNames = tools.map { it.name }.toSet()
        assertTrue(toolNames.contains("add"), "应该包含 add 工具")
        assertTrue(toolNames.contains("subtract"), "应该包含 subtract 工具")
        assertTrue(toolNames.contains("multiply"), "应该包含 multiply 工具")
        assertTrue(toolNames.contains("divide"), "应该包含 divide 工具")
        assertTrue(toolNames.contains("power"), "应该包含 power 工具")
        assertTrue(toolNames.contains("sqrt"), "应该包含 sqrt 工具")
        
        // 测试工具调用 - 加法
        val addResult = calculatorServer.callTool("add", mapOf("a" to 25.0, "b" to 17.0))
        assertTrue(addResult is ToolResult.Success, "加法调用应该成功")
        val addContent = (addResult as ToolResult.Success).content.first() as ContentItem.Json
        println("🧮 加法结果: ${addContent.data}")
        
        // 测试工具调用 - 除法（正常情况）
        val divideResult = calculatorServer.callTool("divide", mapOf("dividend" to 10.0, "divisor" to 3.0))
        assertTrue(divideResult is ToolResult.Success, "除法调用应该成功")
        println("➗ 除法结果: ${(divideResult.content.first() as ContentItem.Json).data}")
        
        // 测试工具调用 - 除法（除零错误）
        val divideZeroResult = calculatorServer.callTool("divide", mapOf("dividend" to 10.0, "divisor" to 0.0))
        assertTrue(divideZeroResult is ToolResult.Success, "除零也应该返回成功但包含错误信息")
        
        println("✅ 注解 MCP Server 测试通过")
    }

    /**
     * 测试2: simpleTool 快捷工具功能
     */
    @Test
    fun `test simpleTool quick server`() = runBlocking {
        println("=== 🛠️ simpleTool 快捷工具测试 ===")
        
        // 使用 simpleTool 创建简单计算器
        val simpleCalc = createSimpleCalculator()
        
        // 验证服务器信息
        assertEquals("simple_add", simpleCalc.name)
        assertEquals("1.0.0", simpleCalc.version)
        assertEquals("简单加法工具", simpleCalc.description)
        
        // 获取工具列表
        val tools = simpleCalc.listTools()
        println("🧮 简单工具: ${tools.map { it.name }}")
        
        assertEquals(1, tools.size, "应该只有一个工具")
        assertEquals("simple_add", tools.first().name, "工具名应该是 simple_add")
        
        // 测试简单加法工具
        val addResult = simpleCalc.callTool("simple_add", mapOf("a" to 15.0, "b" to 25.0))
        assertTrue(addResult is ToolResult.Success, "简单加法应该成功")
        val result = (addResult as ToolResult.Success).content.first() as ContentItem.Json
        assertEquals(40.0, result.data as Double, "15 + 25 应该等于 40")
        println("🧮 简单加法结果: ${result.data}")
        
        println("✅ simpleTool 测试通过")
    }

    /**
     * 测试3: 基础配置功能
     */
    @Test 
    fun `test basic configuration`() = runBlocking {
        println("=== ⚙️ 基础配置测试 ===")
        
        // 创建基础配置
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Bash", "Read", "Write", "mcp__calculator__*"),
            mcpServers = mapOf(
                "calculator" to CalculatorServer(),
                "text_processor" to TextProcessorServer()
            )
        )
        
        // 验证配置
        assertEquals("claude-3-5-sonnet-20241022", options.model)
        assertTrue(options.allowedTools?.contains("Bash") == true, "应该包含 Bash 工具")
        assertTrue(options.allowedTools?.contains("mcp__calculator__*") == true, "应该包含 MCP 通配符工具")
        
        // 验证 MCP 服务器
        assertNotNull(options.mcpServers?.get("calculator"), "应该有计算器服务器")
        assertNotNull(options.mcpServers?.get("text_processor"), "应该有文本处理服务器")
        
        val calcServer = options.mcpServers?.get("calculator") as? CalculatorServer
        assertNotNull(calcServer, "计算器服务器应该是正确类型")
        
        println("📊 配置的工具: ${options.allowedTools}")
        println("🛠️ MCP 服务器: ${options.mcpServers?.keys}")
        
        println("✅ 基础配置测试通过")
    }

    /**
     * 测试4: 完整的 Claude 集成测试（使用新系统）
     */
    @Test
    fun `test complete Claude integration with new MCP system`() = runBlocking {
        println("=== 🚀 完整 Claude 集成测试（新 MCP 系统）===")
        
        // 创建丰富的配置，包含多种服务器
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf(
                "Bash", "Read", "Write", "Edit", "Glob", "Grep", 
                "mcp__calculator__*", "mcp__text_processor__*", "mcp__simple_add__*"
            ),
            mcpServers = mapOf(
                "calculator" to CalculatorServer(),
                "text_processor" to TextProcessorServer(),
                "simple_add" to createSimpleCalculator()
            )
        )
        
        println("📦 配置的 MCP 服务器: ${options.mcpServers?.keys}")
        println("🛠️ 允许的工具: ${options.allowedTools?.take(10)}")
        println("🎣 Hook 事件: ${options.hooks?.keys}")
        
        // 创建客户端
        val client = ClaudeCodeSdkClient(options)
        
        try {
            println("🔌 连接到 Claude...")
            client.connect()
            assertTrue(client.isConnected(), "客户端应该成功连接")
            
            // 验证服务器信息
            val serverInfo = client.getServerInfo()
            assertNotNull(serverInfo, "应该有服务器初始化信息")
            println("ℹ️ 服务器信息: $serverInfo")
            
            // 测试复杂的查询，让 AI 使用多种 MCP 工具
            println("\\n--- 发送复杂查询 ---")
            client.query("""
                你好！请帮我测试简化的 MCP 系统。请执行以下操作：
                
                1. 使用计算器工具计算 25 + 17 和 6 × 8 以及 sqrt(16)
                2. 使用文本处理工具将 "Hello World" 转换为大写
                3. 使用简单加法工具计算 10 + 15
                4. 执行一个简单的 echo 命令输出 "简化MCP系统测试成功"
                
                展示每个工具的使用结果。
            """.trimIndent())
            
            var messageCount = 0
            var toolCallCount = 0
            var receivedMcpTools = mutableSetOf<String>()
            
            client.receiveResponse().collect { message ->
                messageCount++
                println("📨 收到消息 $messageCount: ${message::class.simpleName}")
                
                when (message) {
                    is AssistantMessage -> {
                        message.content.forEach { block ->
                            when (block) {
                                is TextBlock -> {
                                    if (block.text.isNotEmpty()) {
                                        println("🤖 Claude: ${block.text.take(200)}${if(block.text.length > 200) "..." else ""}")
                                    }
                                }
                                is ToolUseBlock -> {
                                    toolCallCount++
                                    println("🔧 工具调用 #$toolCallCount: ${block.name}")
                                    println("   参数: ${block.input}")
                                    
                                    if (block.name.startsWith("mcp__")) {
                                        receivedMcpTools.add(block.name)
                                    }
                                }
                                else -> {
                                    println("🔹 其他内容块: ${block::class.simpleName}")
                                }
                            }
                        }
                    }
                    is SystemMessage -> {
                        println("🔧 系统消息: ${message.subtype}")
                        println("   数据: ${message.data}")
                    }
                    is ResultMessage -> {
                        println("🎯 最终结果: ${message.subtype}, error=${message.isError}")
                        if (message.isError && message.result?.isNotEmpty() == true) {
                            println("❌ 错误信息: ${message.result}")
                        }
                        return@collect
                    }
                    is UserMessage -> {
                        println("👤 用户消息确认")
                    }
                    else -> {
                        println("📄 其他消息: ${message::class.simpleName}")
                    }
                }
            }
            
            // 验证测试结果
            println("\\n--- 集成测试结果验证 ---")
            println("📊 总消息数: $messageCount")
            println("🔧 工具调用次数: $toolCallCount")
            println("📦 调用的 MCP 工具: $receivedMcpTools")
            
            // 基本验证
            assertTrue(messageCount > 0, "应该收到至少一条消息")
            assertTrue(toolCallCount > 0, "应该有工具调用")
            
            // 验证 MCP 工具是否被调用
            val expectedMcpPrefixes = listOf("mcp__calculator__", "mcp__text_processor__", "mcp__simple_add__")
            val calledMcpPrefixes = expectedMcpPrefixes.filter { prefix ->
                receivedMcpTools.any { it.startsWith(prefix) }
            }
            
            println("🎉 成功调用的 MCP 工具前缀: $calledMcpPrefixes")
            assertTrue(calledMcpPrefixes.isNotEmpty(), "至少应该调用一个 MCP 工具")
            
            println("🎉 完整 Claude 集成测试成功！")
            println("✅ 简化 MCP 系统验证通过！")
            
        } catch (e: Exception) {
            println("❌ 集成测试异常: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            client.disconnect()
        }
    }
    
    /**
     * 测试5: MCP Server 工具参数验证和错误处理
     */
    @Test
    fun `test MCP server parameter validation and error handling`() = runBlocking {
        println("=== 🔍 MCP Server 参数验证和错误处理测试 ===")
        
        val textProcessor = TextProcessorServer()
        
        // 测试正常调用
        val normalResult = textProcessor.callTool("toUpperCase", mapOf("text" to "hello world"))
        assertTrue(normalResult is ToolResult.Success)
        val normalContent = (normalResult.content.first() as ContentItem.Json).data
        assertEquals("HELLO WORLD", normalContent.toString().removeSurrounding("\""))
        
        // 测试参数类型转换
        val numberToStringResult = textProcessor.callTool("toUpperCase", mapOf("text" to 12345))
        assertTrue(numberToStringResult is ToolResult.Success)
        
        // 测试缺少必需参数
        val missingParamResult = textProcessor.callTool("toUpperCase", emptyMap())
        // 应该成功，因为 text 会被转换为空字符串
        assertTrue(missingParamResult is ToolResult.Success)
        
        // 测试数据处理服务器的数组处理
        val dataProcessor = DataProcessorServer()
        
        // 正常数组统计
        val arrayStatsResult = dataProcessor.callTool("arrayStats", 
            mapOf("numbers" to listOf(1.0, 2.0, 3.0, 4.0, 5.0)))
        assertTrue(arrayStatsResult is ToolResult.Success)
        val statsData = (arrayStatsResult.content.first() as ContentItem.Json).data
        println("📊 数组统计: $statsData")
        
        // 空数组处理
        val emptyArrayResult = dataProcessor.callTool("arrayStats", mapOf("numbers" to emptyList<Double>()))
        assertTrue(emptyArrayResult is ToolResult.Success)
        val emptyStats = (emptyArrayResult.content.first() as ContentItem.Json).data
        println("📊 空数组结果: $emptyStats")
        
        println("✅ 参数验证和错误处理测试通过")
    }
}
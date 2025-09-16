package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.examples.*
import com.claudecodeplus.sdk.mcp.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 简单的 MCP 系统测试 - 验证简化后的功能
 */
class SimpleMcpTest {

    @Test
    fun `test annotation-based MCP server basic functionality`() = runBlocking {
        println("=== 🧮 简化MCP系统基础功能测试 ===")
        
        // 创建注解服务器实例
        val calculatorServer = CalculatorServer()
        
        // 验证服务器配置
        assertEquals("CalculatorServer", calculatorServer.name)
        assertEquals("1.0.0", calculatorServer.version)
        assertEquals("", calculatorServer.description)
        
        // 获取工具列表
        val tools = calculatorServer.listTools()
        println("📋 发现 ${tools.size} 个工具: ${tools.map { it.name }}")
        
        // 验证所有预期工具都存在
        val toolNames = tools.map { it.name }.toSet()
        assertTrue(toolNames.contains("add"), "应该包含 add 工具")
        assertTrue(toolNames.contains("subtract"), "应该包含 subtract 工具")
        assertTrue(toolNames.contains("multiply"), "应该包含 multiply 工具")
        assertTrue(toolNames.contains("divide"), "应该包含 divide 工具")
        assertTrue(toolNames.contains("power"), "应该包含 power 工具")
        assertTrue(toolNames.contains("sqrt"), "应该包含 sqrt 工具")
        
        // 测试基本数学运算
        testCalculatorOperations(calculatorServer)
        
        println("✅ 简化MCP系统基础功能测试通过")
    }
    
    @Test
    fun `test simpleTool helper function`() = runBlocking {
        println("=== 🛠️ simpleTool 辅助函数测试 ===")
        
        // 使用 simpleTool 创建服务器
        val simpleCalc = createSimpleCalculator()
        
        // 验证基本属性
        assertEquals("simple_add", simpleCalc.name)
        assertEquals("1.0.0", simpleCalc.version)
        assertEquals("简单加法工具", simpleCalc.description)
        
        // 验证工具列表
        val tools = simpleCalc.listTools()
        assertEquals(1, tools.size)
        assertEquals("simple_add", tools.first().name)
        
        // 测试工具调用
        val result = simpleCalc.callTool("simple_add", mapOf("a" to 15.0, "b" to 25.0))
        assertTrue(result is ToolResult.Success)
        val jsonResult = (result as ToolResult.Success).content.first() as ContentItem.Json
        assertEquals(40.0, when (val data = jsonResult.data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        })
        
        println("✅ simpleTool 辅助函数测试通过")
    }
    
    @Test
    fun `test text processor server`() = runBlocking {
        println("=== 📝 文本处理服务器测试 ===")
        
        val textProcessor = TextProcessorServer()
        
        // 验证配置
        assertEquals("TextProcessorServer", textProcessor.name)
        assertTrue(textProcessor.listTools().isNotEmpty())
        
        // 测试文本转换功能
        val upperResult = textProcessor.callTool("toUpperCase", mapOf("text" to "hello world"))
        assertTrue(upperResult is ToolResult.Success)
        val upperContent = (upperResult as ToolResult.Success).content.first() as ContentItem.Json
        assertEquals("HELLO WORLD", upperContent.data.toString().removeSurrounding("\""))
        
        val lowerResult = textProcessor.callTool("toLowerCase", mapOf("text" to "HELLO WORLD"))
        assertTrue(lowerResult is ToolResult.Success)
        val lowerContent = (lowerResult as ToolResult.Success).content.first() as ContentItem.Json
        assertEquals("hello world", lowerContent.data.toString().removeSurrounding("\""))
        
        println("✅ 文本处理服务器测试通过")
    }
    
    private suspend fun testCalculatorOperations(calculator: CalculatorServer) {
        println("📋 测试计算器运算...")
        
        // 加法
        val addResult = calculator.callTool("add", mapOf("a" to 25.0, "b" to 17.0))
        assertTrue(addResult is ToolResult.Success)
        val addValue = when (val data = ((addResult as ToolResult.Success).content.first() as ContentItem.Json).data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        }
        assertEquals(42.0, addValue)
        println("➕ 25 + 17 = $addValue")
        
        // 乘法
        val multiplyResult = calculator.callTool("multiply", mapOf("a" to 6.0, "b" to 8.0))
        assertTrue(multiplyResult is ToolResult.Success)
        val multiplyValue = when (val data = ((multiplyResult as ToolResult.Success).content.first() as ContentItem.Json).data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        }
        assertEquals(48.0, multiplyValue)
        println("✖️ 6 × 8 = $multiplyValue")
        
        // 除法 - 正常情况
        val divideResult = calculator.callTool("divide", mapOf("dividend" to 10.0, "divisor" to 2.0))
        assertTrue(divideResult is ToolResult.Success)
        val divideValue = when (val data = ((divideResult as ToolResult.Success).content.first() as ContentItem.Json).data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        }
        assertEquals(5.0, divideValue)
        println("➗ 10 ÷ 2 = $divideValue")
        
        // 平方根
        val sqrtResult = calculator.callTool("sqrt", mapOf("number" to 16.0))
        assertTrue(sqrtResult is ToolResult.Success)
        val sqrtValue = when (val data = ((sqrtResult as ToolResult.Success).content.first() as ContentItem.Json).data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        }
        assertEquals(4.0, sqrtValue)
        println("√ √16 = $sqrtValue")
        
        // 幂运算
        val powerResult = calculator.callTool("power", mapOf("base" to 2.0, "exponent" to 3.0))
        assertTrue(powerResult is ToolResult.Success)
        val powerValue = when (val data = ((powerResult as ToolResult.Success).content.first() as ContentItem.Json).data) {
            is Number -> data.toDouble()
            is JsonPrimitive -> data.double
            else -> fail("Expected number result, got ${data::class}")
        }
        assertEquals(8.0, powerValue)
        println("📈 2³ = $powerValue")
        
        println("📊 所有计算器运算测试通过")
    }
}
package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.examples.*
import com.claudecodeplus.sdk.mcp.*
import com.claudecodeplus.sdk.mcp.annotations.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * 测试增强的 MCP 功能：参数描述和类型转换
 */
class EnhancedMcpTest {

    /**
     * 带参数描述的示例服务器
     */
    @McpServerConfig(
        name = "enhanced_calculator", 
        version = "2.0.0",
        description = "增强版计算器，支持参数描述"
    )
    class EnhancedCalculatorServer : McpServerBase() {
        
        @McpTool("高级除法运算，支持精度控制")
        suspend fun divide(
            @ToolParam("被除数，例如 10.0") dividend: Double,
            @ToolParam("除数，不能为0，例如 2.0") divisor: Double,
            @ToolParam("保留小数位数，可选，默认2，范围0-10") precision: Int = 2
        ): String {
            if (divisor == 0.0) throw IllegalArgumentException("除数不能为0")
            val result = dividend / divisor
            return "%.${precision}f".format(result)
        }
        
        @McpTool("字符串处理功能")
        suspend fun processString(
            @ToolParam("输入文本，长度1-1000字符") text: String,
            @ToolParam("是否转为大写，可选，默认false") uppercase: Boolean = false,
            @ToolParam("重复次数，可选，默认1，范围1-10") repeat: Int = 1
        ): String {
            var result = text
            if (uppercase) result = result.uppercase()
            return result.repeat(repeat)
        }
    }

    @Test
    fun `test parameter descriptions in tool definition`() = runBlocking {
        println("=== 🔍 参数描述测试 ===")
        
        val server = EnhancedCalculatorServer()
        val tools = server.listTools()
        
        // 验证工具数量
        assertEquals(2, tools.size)
        
        // 验证除法工具的参数描述
        val divideToolOpt = tools.find { it.name == "divide" }
        assertNotNull(divideToolOpt, "应该找到 divide 工具")
        val divideTool = divideToolOpt
        
        println("🔧 divide 工具定义:")
        println("  描述: ${divideTool.description}")
        println("  输入 Schema: ${divideTool.inputSchema}")
        
        // 检查 inputSchema 结构
        val inputSchema = divideTool.inputSchema
        assertTrue(inputSchema.containsKey("properties"), "应该包含 properties")
        
        @Suppress("UNCHECKED_CAST")
        val properties = inputSchema["properties"] as Map<String, Map<String, Any>>
        
        // 验证 dividend 参数
        assertTrue(properties.containsKey("dividend"), "应该包含 dividend 参数")
        val dividendProp = properties["dividend"]!!
        assertEquals("number", dividendProp["type"])
        assertEquals("被除数，例如 10.0", dividendProp["description"])
        
        // 验证 divisor 参数
        assertTrue(properties.containsKey("divisor"), "应该包含 divisor 参数")
        val divisorProp = properties["divisor"]!!
        assertEquals("number", divisorProp["type"])
        assertEquals("除数，不能为0，例如 2.0", divisorProp["description"])
        
        // 验证 precision 参数
        assertTrue(properties.containsKey("precision"), "应该包含 precision 参数")
        val precisionProp = properties["precision"]!!
        assertEquals("number", precisionProp["type"])
        assertEquals("保留小数位数，可选，默认2，范围0-10", precisionProp["description"])
        
        // 验证 required 字段 - 所有参数都在 required 中
        @Suppress("UNCHECKED_CAST")
        val required = inputSchema["required"] as List<String>
        assertTrue(required.contains("dividend"), "dividend 应该在 required 中")
        assertTrue(required.contains("divisor"), "divisor 应该在 required 中")
        assertTrue(required.contains("precision"), "precision 应该在 required 中")
        
        println("✅ 参数描述测试通过")
    }

    @Test  
    fun `test string to type conversion`() = runBlocking {
        println("=== 🔄 字符串类型转换测试 ===")
        
        val server = EnhancedCalculatorServer()
        
        // 测试字符串参数传递给数值类型
        println("🔢 测试字符串转数值...")
        val divideResult = server.callTool("divide", mapOf(
            "dividend" to "20.5",  // 字符串转 Double
            "divisor" to "4.1",    // 字符串转 Double  
            "precision" to "3"     // 字符串转 Int
        ))
        
        assertTrue(divideResult is ToolResult.Success)
        val content = (divideResult as ToolResult.Success).content.first()
        assertTrue(content is ContentItem.Json)
        val resultValue = (content as ContentItem.Json).data.toString().removeSurrounding("\"")
        assertEquals("5.000", resultValue)
        println("  ✓ 20.5 ÷ 4.1 = $resultValue (精度3位)")
        
        // 测试字符串处理功能
        println("🔤 测试字符串和布尔类型转换...")
        val stringResult = server.callTool("processString", mapOf(
            "text" to "Hello",
            "uppercase" to "true",    // 字符串转 Boolean
            "repeat" to "2"           // 字符串转 Int
        ))
        
        assertTrue(stringResult is ToolResult.Success)
        val stringContent = (stringResult as ToolResult.Success).content.first()
        assertTrue(stringContent is ContentItem.Json)
        val stringValue = (stringContent as ContentItem.Json).data.toString().removeSurrounding("\"")
        assertEquals("HELLOHELLO", stringValue)
        println("  ✓ 'Hello' -> 大写 + 重复2次 = '$stringValue'")
        
        println("✅ 字符串类型转换测试通过")
    }

    @Test
    fun `test type conversion error handling`() = runBlocking {
        println("=== ❌ 类型转换错误处理测试 ===")
        
        val server = EnhancedCalculatorServer()
        
        // 测试无效数值转换
        println("🚫 测试无效数值转换...")
        val invalidResult = server.callTool("divide", mapOf(
            "dividend" to "not_a_number",
            "divisor" to "2.0"
        ))
        
        assertTrue(invalidResult is ToolResult.Error)
        val errorResult = invalidResult as ToolResult.Error
        assertTrue(errorResult.error.contains("无法转换为目标类型") || 
                  errorResult.error.contains("工具执行失败"))
        println("  ✓ 无效数值转换被正确捕获: ${errorResult.error}")
        
        // 测试业务逻辑错误（除以0）
        println("🚫 测试业务逻辑错误...")
        val divideByZeroResult = server.callTool("divide", mapOf(
            "dividend" to "10.0",
            "divisor" to "0.0"
        ))
        
        assertTrue(divideByZeroResult is ToolResult.Error)
        val businessErrorResult = divideByZeroResult as ToolResult.Error
        assertTrue(businessErrorResult.error.contains("除数不能为0") || 
                  businessErrorResult.error.contains("工具执行失败"))
        println("  ✓ 业务逻辑错误被正确捕获: ${businessErrorResult.error}")
        
        println("✅ 错误处理测试通过")
    }
}
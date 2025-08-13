// 调试工具调用解析的测试脚本
import kotlinx.serialization.json.*

fun main() {
    println("=== 测试 Claude CLI JSONL 消息中的工具调用解析 ===")
    
    // 模拟一个包含工具调用的 assistant 消息（从之前的日志中得到的格式）
    val assistantMessageWithTool = """{"type":"assistant","uuid":"test-uuid","sessionId":"test-session","timestamp":"2025-08-13T10:30:00.000Z","message":{"id":"msg_test","type":"message","role":"assistant","model":"claude-3-5-sonnet-20241022","content":[{"type":"tool_use","id":"tool_test_123","name":"LS","input":{"path":"/Users/erio/codes/idea/claude-code-plus"}},{"type":"text","text":"I'll list the files in the directory."}],"stop_reason":"tool_use","usage":{"input_tokens":100,"output_tokens":50}}}"""
    
    // 模拟对应的工具结果消息  
    val toolResultMessage = """{"type":"user","uuid":"test-result-uuid","sessionId":"test-session","timestamp":"2025-08-13T10:30:01.000Z","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"tool_test_123","content":"desktop/\ntoolwindow/\ncli-wrapper/\ndocs/\n.gradle/"}]}}"""
    
    println("\n1. 原始 assistant 消息:")
    println(assistantMessageWithTool)
    println("\n2. 工具结果消息:")
    println(toolResultMessage)
    
    // 测试 JSON 解析
    val json = Json { ignoreUnknownKeys = true; isLenient = true }
    
    try {
        val assistantJson = json.parseToJsonElement(assistantMessageWithTool).jsonObject
        val messageObj = assistantJson["message"]?.jsonObject
        val contentArray = messageObj?.get("content")?.jsonArray
        
        println("\n3. 解析出的内容块数量: ${contentArray?.size}")
        
        contentArray?.forEachIndexed { index, element ->
            val contentObj = element.jsonObject
            val type = contentObj["type"]?.jsonPrimitive?.content
            println("   Block $index: type=$type")
            
            if (type == "tool_use") {
                val id = contentObj["id"]?.jsonPrimitive?.content
                val name = contentObj["name"]?.jsonPrimitive?.content
                val input = contentObj["input"]?.jsonObject
                println("     - Tool ID: $id")
                println("     - Tool Name: $name")
                println("     - Input keys: ${input?.keys}")
            } else if (type == "text") {
                val text = contentObj["text"]?.jsonPrimitive?.content
                println("     - Text: ${text?.take(50)}...")
            }
        }
        
        // 测试工具结果解析
        val resultJson = json.parseToJsonElement(toolResultMessage).jsonObject
        val resultMessageObj = resultJson["message"]?.jsonObject
        val resultContentArray = resultMessageObj?.get("content")?.jsonArray
        
        println("\n4. 工具结果内容块数量: ${resultContentArray?.size}")
        resultContentArray?.forEachIndexed { index, element ->
            val contentObj = element.jsonObject
            val type = contentObj["type"]?.jsonPrimitive?.content
            
            if (type == "tool_result") {
                val toolUseId = contentObj["tool_use_id"]?.jsonPrimitive?.content
                val content = contentObj["content"]?.jsonPrimitive?.content
                println("   Result $index: toolUseId=$toolUseId")
                println("     - Content: ${content?.take(100)}...")
            }
        }
        
        println("\n5. 工具调用解析测试完成 ✅")
        
    } catch (e: Exception) {
        println("\n❌ JSON 解析失败: ${e.message}")
        e.printStackTrace()
    }
}
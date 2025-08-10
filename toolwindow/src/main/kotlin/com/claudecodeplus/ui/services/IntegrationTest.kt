package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.ui.models.*
import mu.KotlinLogging

/**
 * 集成测试 - 验证从Claude CLI到UI的完整消息转换流程
 * 
 * 测试覆盖：
 * 1. JSONL消息解析和转换
 * 2. ClaudeFileMessage转换
 * 3. 工具调用处理
 * 4. 错误处理
 * 5. 批量处理
 */
class IntegrationTest {
    private val logger = KotlinLogging.logger {}
    private val messageConverter = MessageFlowConverter()
    
    /**
     * 运行完整的集成测试
     */
    fun runIntegrationTests(): TestResult {
        logger.info { "开始运行UI集成测试..." }
        
        val results = mutableListOf<TestCase>()
        
        // 测试1：基本用户消息转换
        results.add(testUserMessageConversion())
        
        // 测试2：助手消息转换（包含工具调用）
        results.add(testAssistantMessageWithTools())
        
        // 测试3：系统消息转换
        results.add(testSystemMessageConversion())
        
        // 测试4：错误消息处理
        results.add(testErrorMessageHandling())
        
        // 测试5：批量消息转换
        results.add(testBatchMessageConversion())
        
        // 测试6：JSONL直接转换
        results.add(testJsonlDirectConversion())
        
        // 测试7：消息去重机制
        results.add(testMessageDeduplication())
        
        val passed = results.count { it.passed }
        val total = results.size
        
        logger.info { "UI集成测试完成：通过 $passed/$total 个测试" }
        
        return TestResult(
            testName = "UI集成测试",
            passed = passed,
            total = total,
            details = results
        )
    }
    
    /**
     * 测试用户消息转换
     */
    private fun testUserMessageConversion(): TestCase {
        return try {
            // 模拟ClaudeFileMessage
            val fileMessage = createMockUserFileMessage()
            val sessionId = "test-session-001"
            
            // 转换消息
            val enhancedMessage = messageConverter.convertMessage(fileMessage, sessionId)
            
            if (enhancedMessage != null && 
                enhancedMessage.role == MessageRole.USER && 
                enhancedMessage.content.contains("测试用户消息")) {
                TestCase("用户消息转换", true, "成功转换用户消息")
            } else {
                TestCase("用户消息转换", false, "转换结果不符合预期: $enhancedMessage")
            }
        } catch (e: Exception) {
            logger.error(e) { "用户消息转换测试失败" }
            TestCase("用户消息转换", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试包含工具调用的助手消息转换
     */
    private fun testAssistantMessageWithTools(): TestCase {
        return try {
            // 模拟包含工具调用的助手消息
            val fileMessage = createMockAssistantWithToolsMessage()
            val sessionId = "test-session-002"
            
            // 转换消息
            val enhancedMessage = messageConverter.convertMessage(fileMessage, sessionId)
            
            if (enhancedMessage != null && 
                enhancedMessage.role == MessageRole.ASSISTANT) {
                TestCase("助手消息+工具调用转换", true, "成功转换包含工具调用的助手消息")
            } else {
                TestCase("助手消息+工具调用转换", false, "转换结果不符合预期: $enhancedMessage")
            }
        } catch (e: Exception) {
            logger.error(e) { "助手消息+工具调用转换测试失败" }
            TestCase("助手消息+工具调用转换", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试系统消息转换
     */
    private fun testSystemMessageConversion(): TestCase {
        return try {
            val fileMessage = createMockSystemMessage()
            val sessionId = "test-session-003"
            
            val enhancedMessage = messageConverter.convertMessage(fileMessage, sessionId)
            
            if (enhancedMessage != null && 
                enhancedMessage.role == MessageRole.SYSTEM) {
                TestCase("系统消息转换", true, "成功转换系统消息")
            } else {
                TestCase("系统消息转换", false, "转换结果不符合预期: $enhancedMessage")
            }
        } catch (e: Exception) {
            logger.error(e) { "系统消息转换测试失败" }
            TestCase("系统消息转换", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试错误消息处理
     */
    private fun testErrorMessageHandling(): TestCase {
        return try {
            // 测试创建错误消息
            val errorMessage = messageConverter.createErrorMessage(
                content = "测试错误消息",
                sessionId = "test-session-004",
                errorType = "test_error"
            )
            
            if (errorMessage.role == MessageRole.ERROR && 
                errorMessage.isError && 
                errorMessage.content.contains("测试错误消息")) {
                TestCase("错误消息处理", true, "成功创建和处理错误消息")
            } else {
                TestCase("错误消息处理", false, "错误消息格式不正确: $errorMessage")
            }
        } catch (e: Exception) {
            logger.error(e) { "错误消息处理测试失败" }
            TestCase("错误消息处理", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试批量消息转换
     */
    private fun testBatchMessageConversion(): TestCase {
        return try {
            val fileMessages = listOf(
                createMockUserFileMessage(),
                createMockAssistantWithToolsMessage(),
                createMockSystemMessage()
            )
            val sessionId = "test-session-005"
            
            val enhancedMessages = messageConverter.convertMessages(fileMessages, sessionId)
            
            if (enhancedMessages.size == 3 && 
                enhancedMessages.any { it.role == MessageRole.USER } &&
                enhancedMessages.any { it.role == MessageRole.ASSISTANT } &&
                enhancedMessages.any { it.role == MessageRole.SYSTEM }) {
                TestCase("批量消息转换", true, "成功批量转换${enhancedMessages.size}条消息")
            } else {
                TestCase("批量消息转换", false, "批量转换结果不正确，转换了${enhancedMessages.size}条消息")
            }
        } catch (e: Exception) {
            logger.error(e) { "批量消息转换测试失败" }
            TestCase("批量消息转换", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试JSONL直接转换
     */
    private fun testJsonlDirectConversion(): TestCase {
        return try {
            val jsonlContent = createMockJsonlContent()
            val sessionId = "test-session-006"
            
            val enhancedMessages = messageConverter.convertFromJsonLines(jsonlContent, sessionId)
            
            if (enhancedMessages.isNotEmpty()) {
                TestCase("JSONL直接转换", true, "成功从JSONL转换${enhancedMessages.size}条消息")
            } else {
                TestCase("JSONL直接转换", false, "JSONL转换失败，没有获得任何消息")
            }
        } catch (e: Exception) {
            logger.error(e) { "JSONL直接转换测试失败" }
            TestCase("JSONL直接转换", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 测试消息去重机制
     */
    private fun testMessageDeduplication(): TestCase {
        return try {
            val fileMessage = createMockUserFileMessage()
            val sessionId = "test-session-007"
            
            // 第一次转换
            val first = messageConverter.convertMessage(fileMessage, sessionId)
            // 第二次转换（应该被去重）
            val second = messageConverter.convertMessage(fileMessage, sessionId)
            
            if (first != null && second == null) {
                TestCase("消息去重机制", true, "去重机制正常工作")
            } else {
                TestCase("消息去重机制", false, "去重机制失效，first=$first, second=$second")
            }
        } catch (e: Exception) {
            logger.error(e) { "消息去重机制测试失败" }
            TestCase("消息去重机制", false, "异常: ${e.message}")
        }
    }
    
    /**
     * 创建模拟的用户文件消息
     */
    private fun createMockUserFileMessage(): ClaudeFileMessage {
        return ClaudeFileMessage(
            type = "user",
            timestamp = "2024-01-01T10:00:00Z",
            sessionId = "user-msg-001",
            message = com.claudecodeplus.sdk.session.MessageContent(
                role = "user",
                content = "测试用户消息内容"
            )
        )
    }
    
    /**
     * 创建模拟的包含工具调用的助手消息
     */
    private fun createMockAssistantWithToolsMessage(): ClaudeFileMessage {
        return ClaudeFileMessage(
            type = "assistant",
            timestamp = "2024-01-01T10:01:00Z",
            sessionId = "assistant-msg-001",
            message = com.claudecodeplus.sdk.session.MessageContent(
                role = "assistant",
                content = listOf(
                    mapOf(
                        "type" to "text",
                        "text" to "我来帮您查看文件内容。"
                    ),
                    mapOf(
                        "type" to "tool_use",
                        "id" to "tool-call-001",
                        "name" to "Read",
                        "input" to mapOf("file_path" to "/test/file.txt")
                    )
                )
            )
        )
    }
    
    /**
     * 创建模拟的系统消息
     */
    private fun createMockSystemMessage(): ClaudeFileMessage {
        return ClaudeFileMessage(
            type = "system",
            timestamp = "2024-01-01T10:02:00Z",
            sessionId = "system-msg-001",
            message = com.claudecodeplus.sdk.session.MessageContent(
                role = "system",
                content = "测试系统消息"
            )
        )
    }
    
    /**
     * 创建模拟的JSONL内容
     */
    private fun createMockJsonlContent(): String {
        return """
            {"type":"user","timestamp":"2024-01-01T10:00:00Z","uuid":"user-001","message":{"role":"user","content":[{"type":"text","text":"测试消息1"}]}}
            {"type":"assistant","timestamp":"2024-01-01T10:01:00Z","uuid":"assistant-001","message":{"role":"assistant","content":[{"type":"text","text":"测试回复1"}]}}
        """.trimIndent()
    }
    
    /**
     * 测试用例
     */
    data class TestCase(
        val name: String,
        val passed: Boolean,
        val message: String
    )
    
    /**
     * 测试结果
     */
    data class TestResult(
        val testName: String,
        val passed: Int,
        val total: Int,
        val details: List<TestCase>
    )
}

/**
 * 运行UI集成测试的便捷函数
 */
fun main() {
    val test = IntegrationTest()
    val result = test.runIntegrationTests()
    
    println("=== UI集成测试结果 ===")
    println("测试名称: ${result.testName}")
    println("通过率: ${result.passed}/${result.total} (${String.format("%.1f", result.passed.toDouble() / result.total * 100)}%)")
    println()
    
    println("详细结果:")
    result.details.forEach { case ->
        val status = if (case.passed) "✅" else "❌"
        println("$status ${case.name}: ${case.message}")
    }
}
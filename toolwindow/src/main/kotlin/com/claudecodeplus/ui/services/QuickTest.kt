package com.claudecodeplus.ui.services

import com.claudecodeplus.sdk.session.ClaudeFileMessage
import com.claudecodeplus.sdk.session.MessageContent
import com.claudecodeplus.ui.models.MessageRole

/**
 * 快速测试 - 验证基本的消息转换功能
 */
fun quickTestMessageConversion(): String {
    val result = StringBuilder()
    result.appendLine("=== 快速消息转换测试 ===\n")
    
    try {
        val messageConverter = MessageFlowConverter()
        
        // 测试1: 用户消息转换
        result.appendLine("测试1: 用户消息转换")
        val userMessage = ClaudeFileMessage(
            type = "user",
            timestamp = "2024-01-01T10:00:00Z",
            sessionId = "test-001",
            message = MessageContent(
                role = "user",
                content = "测试用户消息"
            )
        )
        
        val convertedUser = messageConverter.convertMessage(userMessage, "test-session")
        if (convertedUser != null && convertedUser.role == MessageRole.USER) {
            result.appendLine("✅ 用户消息转换成功")
            result.appendLine("   内容: ${convertedUser.content}")
            result.appendLine("   角色: ${convertedUser.role}")
            result.appendLine("   ID: ${convertedUser.id}")
        } else {
            result.appendLine("❌ 用户消息转换失败: $convertedUser")
        }
        
        result.appendLine()
        
        // 测试2: 助手消息转换
        result.appendLine("测试2: 助手消息转换")
        val assistantMessage = ClaudeFileMessage(
            type = "assistant",
            timestamp = "2024-01-01T10:01:00Z",
            sessionId = "test-002",
            message = MessageContent(
                role = "assistant",
                content = "测试助手回复"
            )
        )
        
        val convertedAssistant = messageConverter.convertMessage(assistantMessage, "test-session")
        if (convertedAssistant != null && convertedAssistant.role == MessageRole.ASSISTANT) {
            result.appendLine("✅ 助手消息转换成功")
            result.appendLine("   内容: ${convertedAssistant.content}")
            result.appendLine("   角色: ${convertedAssistant.role}")
        } else {
            result.appendLine("❌ 助手消息转换失败: $convertedAssistant")
        }
        
        result.appendLine()
        
        // 测试3: JSONL直接转换
        result.appendLine("测试3: JSONL直接转换")
        val jsonlLine = """{"type":"user","timestamp":"2024-01-01T10:00:00Z","message":{"role":"user","content":[{"type":"text","text":"JSONL测试消息"}]}}"""
        
        val convertedJsonl = messageConverter.convertFromJsonLine(jsonlLine, "test-session-2")
        if (convertedJsonl != null) {
            result.appendLine("✅ JSONL转换成功")
            result.appendLine("   内容: ${convertedJsonl.content}")
            result.appendLine("   角色: ${convertedJsonl.role}")
        } else {
            result.appendLine("❌ JSONL转换失败")
        }
        
        result.appendLine()
        
        // 测试4: 错误消息创建
        result.appendLine("测试4: 错误消息创建")
        val errorMessage = messageConverter.createErrorMessage("测试错误", "test-session", "test_error")
        if (errorMessage.role == MessageRole.ERROR && errorMessage.isError) {
            result.appendLine("✅ 错误消息创建成功")
            result.appendLine("   内容: ${errorMessage.content}")
        } else {
            result.appendLine("❌ 错误消息创建失败")
        }
        
        result.appendLine()
        
        // 测试5: 会话统计
        result.appendLine("测试5: 会话统计")
        val stats = messageConverter.getSessionStats("test-session")
        result.appendLine("✅ 会话统计获取成功")
        result.appendLine("   消息总数: ${stats.totalMessages}")
        result.appendLine("   活跃工具调用: ${stats.activeToolCalls}")
        
        result.appendLine()
        result.appendLine("=== 测试完成 ===")
        
    } catch (e: Exception) {
        result.appendLine("❌ 测试过程中发生异常: ${e.message}")
        result.appendLine("异常详情: ${e.stackTraceToString()}")
    }
    
    return result.toString()
}

fun main() {
    println(quickTestMessageConversion())
}
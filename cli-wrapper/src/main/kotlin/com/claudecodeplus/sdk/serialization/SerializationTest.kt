package com.claudecodeplus.sdk.serialization

import kotlinx.serialization.json.*

/**
 * 序列化系统基础测试
 * 
 * 验证新的Claude JSONL序列化系统的核心功能，不依赖UI模型
 */
object SerializationTest {
    
    /**
     * 运行基本测试
     */
    fun runBasicTests(): TestResult {
        val results = mutableListOf<String>()
        var passed = 0
        var failed = 0
        
        // 测试1: 解析用户消息
        try {
            val userJson = """{"type":"user","uuid":"test-uuid","sessionId":"test-session","timestamp":"2025-01-01T00:00:00Z","message":{"role":"user","content":[{"type":"text","text":"Hello Claude!"}]}}"""
            val parsed = ClaudeMessageParser.parseMessage(userJson)
            if (parsed?.message is UserMessage) {
                results.add("✅ 用户消息解析成功")
                passed++
            } else {
                results.add("❌ 用户消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 用户消息解析异常: ${e.message}")
            failed++
        }
        
        // 测试2: 解析助手消息
        try {
            val assistantJson = """{"type":"assistant","uuid":"test-uuid-2","sessionId":"test-session","timestamp":"2025-01-01T00:01:00Z","message":{"id":"msg-1","type":"message","role":"assistant","model":"claude-3-sonnet-20240229","content":[{"type":"text","text":"Hello! How can I help you?"}],"usage":{"input_tokens":10,"output_tokens":8}}}"""
            val parsed = ClaudeMessageParser.parseMessage(assistantJson)
            if (parsed?.message is AssistantMessage) {
                results.add("✅ 助手消息解析成功")
                passed++
            } else {
                results.add("❌ 助手消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 助手消息解析异常: ${e.message}")
            failed++
        }
        
        // 测试3: 解析工具调用
        try {
            val toolUseJson = """{"type":"assistant","uuid":"test-uuid-3","sessionId":"test-session","timestamp":"2025-01-01T00:02:00Z","message":{"id":"msg-2","type":"message","role":"assistant","model":"claude-3-sonnet-20240229","content":[{"type":"tool_use","id":"tool-1","name":"read","input":{"file_path":"/test/file.txt"}}],"usage":{"input_tokens":15,"output_tokens":12}}}"""
            val parsed = ClaudeMessageParser.parseMessage(toolUseJson)
            if (parsed != null && parsed.toolUseRequests.isNotEmpty()) {
                val request = parsed.toolUseRequests[0]
                if (request.name == "read" && request.id == "tool-1") {
                    results.add("✅ 工具调用解析成功")
                    passed++
                } else {
                    results.add("❌ 工具调用解析数据错误")
                    failed++
                }
            } else {
                results.add("❌ 工具调用解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 工具调用解析异常: ${e.message}")
            failed++
        }
        
        // 测试4: 解析工具结果
        try {
            val toolResultJson = """{"type":"user","uuid":"test-uuid-4","sessionId":"test-session","timestamp":"2025-01-01T00:03:00Z","message":{"role":"user","content":[{"type":"tool_result","tool_use_id":"tool-1","content":"File content here","is_error":false}]}}"""
            val parsed = ClaudeMessageParser.parseMessage(toolResultJson)
            if (parsed != null && parsed.toolUseResponses.isNotEmpty()) {
                val response = parsed.toolUseResponses[0]
                if (response.toolUseId == "tool-1" && !response.isError) {
                    results.add("✅ 工具结果解析成功")
                    passed++
                } else {
                    results.add("❌ 工具结果解析数据错误")
                    failed++
                }
            } else {
                results.add("❌ 工具结果解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 工具结果解析异常: ${e.message}")
            failed++
        }
        
        // 测试5: 创建用户消息
        try {
            val serialized = ClaudeMessageSerializer.createUserMessage(
                content = "Test message",
                sessionId = "test-session"
            )
            val parsed = ClaudeMessageParser.parseMessage(serialized)
            if (parsed?.message is UserMessage) {
                val userMsg = parsed.message as UserMessage
                if (userMsg.sessionId == "test-session") {
                    results.add("✅ 用户消息创建成功")
                    passed++
                } else {
                    results.add("❌ 用户消息创建数据错误")
                    failed++
                }
            } else {
                results.add("❌ 用户消息创建失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 用户消息创建异常: ${e.message}")
            failed++
        }
        
        // 测试6: 序列化服务工具调用管理
        try {
            val service = ClaudeSerializationService()
            val toolUseJson = """{"type":"assistant","uuid":"test-uuid-5","sessionId":"test-session","timestamp":"2025-01-01T00:04:00Z","message":{"id":"msg-3","type":"message","role":"assistant","model":"claude-3-sonnet-20240229","content":[{"type":"tool_use","id":"tool-2","name":"bash","input":{"command":"ls -la"}}],"usage":{"input_tokens":20,"output_tokens":15}}}"""
            
            val result = service.processMessage(toolUseJson)
            if (result != null && result.toolCalls.isNotEmpty()) {
                val toolCall = result.toolCalls[0]
                if (toolCall.request.name == "bash" && toolCall.tool.name.equals("bash", ignoreCase = true)) {
                    results.add("✅ 序列化服务工具调用管理成功")
                    passed++
                } else {
                    results.add("❌ 序列化服务工具调用管理数据错误：request.name=${toolCall.request.name}, tool.name=${toolCall.tool.name}")
                    failed++
                }
            } else {
                results.add("❌ 序列化服务工具调用管理失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 序列化服务工具调用管理异常: ${e.message}")
            failed++
        }
        
        // 测试7: 接口系统验证
        try {
            val userJson = """{"type":"user","uuid":"test-uuid-7","sessionId":"test-session","timestamp":"2025-01-01T00:05:00Z","message":{"role":"user","content":[{"type":"text","text":"Interface test"}]}}"""
            val parsed = ClaudeMessageParser.parseMessage(userJson)
            
            if (parsed?.message != null) {
                val nativeMessage = parsed.message
                
                // 验证原生消息接口
                if (nativeMessage is ClaudeNativeMessage && 
                    nativeMessage.type == "user" && 
                    nativeMessage.sessionId == "test-session") {
                    
                    // 验证显示消息转换
                    val displayMessage = MessageDisplayAdapter.toDisplayMessage(nativeMessage)
                    if (displayMessage != null && 
                        displayMessage is DisplayableMessage && 
                        displayMessage.displayRole == MessageDisplayRole.USER &&
                        displayMessage.displayContent == "Interface test") {
                        
                        results.add("✅ 接口系统验证成功")
                        passed++
                    } else {
                        results.add("❌ 显示消息转换失败")
                        failed++
                    }
                } else {
                    results.add("❌ 原生消息接口验证失败")
                    failed++
                }
            } else {
                results.add("❌ 接口系统消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 接口系统验证异常: ${e.message}")
            failed++
        }
        
        return TestResult(
            totalTests = passed + failed,
            passed = passed,
            failed = failed,
            details = results
        )
    }
}

/**
 * 测试结果
 */
data class TestResult(
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val details: List<String>
) {
    fun printResults() {
        println("=== Claude 序列化系统测试结果 ===")
        println("总测试数: $totalTests")
        println("通过: $passed")
        println("失败: $failed")
        println("成功率: ${(passed.toDouble() / totalTests * 100).toInt()}%")
        println()
        println("详细结果:")
        details.forEach { println("  $it") }
        println()
        
        if (failed == 0) {
            println("🎉 所有测试通过！序列化系统工作正常。")
        } else {
            println("⚠️  有 $failed 个测试失败，需要检查相关问题。")
        }
    }
}
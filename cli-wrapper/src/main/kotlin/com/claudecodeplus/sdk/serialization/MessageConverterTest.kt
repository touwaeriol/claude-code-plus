package com.claudecodeplus.sdk.serialization

/**
 * 消息转换器专项测试
 * 
 * 测试从Claude原生消息到界面显示消息的转换功能
 */
object MessageConverterTest {
    
    /**
     * 运行消息转换器测试
     */
    fun runConverterTests(): TestResult {
        val results = mutableListOf<String>()
        var passed = 0
        var failed = 0
        
        // 测试1: 用户消息转换
        try {
            val userJson = """{"type":"user","uuid":"user-123","sessionId":"sess-1","timestamp":"2025-01-01T10:00:00Z","message":{"role":"user","content":[{"type":"text","text":"请帮我查看文件内容"},{"type":"text","text":"谢谢！"}]}}"""
            val parsed = ClaudeMessageParser.parseMessage(userJson)?.message as? UserMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed) as? UserDisplayMessage
                
                if (displayMessage != null) {
                    val expectedContent = "请帮我查看文件内容\n谢谢！"
                    val actualContent = displayMessage.displayContent
                    
                    if (actualContent == expectedContent &&
                        displayMessage.displayRole == MessageDisplayRole.USER &&
                        displayMessage.displayId == "user-123" &&
                        !displayMessage.isStreamable) {
                        results.add("✅ 用户消息转换成功")
                        passed++
                    } else {
                        results.add("❌ 用户消息转换失败 - 内容或属性不匹配")
                        results.add("  期望内容: '$expectedContent'")
                        results.add("  实际内容: '$actualContent'")
                        failed++
                    }
                } else {
                    results.add("❌ 用户消息转换失败 - 转换结果为null")
                    failed++
                }
            } else {
                results.add("❌ 用户消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 用户消息转换异常: ${e.message}")
            failed++
        }
        
        // 测试2: 助手消息转换（包含文本内容）
        try {
            val assistantJson = """{"type":"assistant","uuid":"assistant-123","sessionId":"sess-1","timestamp":"2025-01-01T10:01:00Z","message":{"id":"msg-1","type":"message","role":"assistant","model":"claude-3-sonnet-20240229","content":[{"type":"text","text":"我来帮你查看文件内容。"},{"type":"text","text":"请稍等片刻..."}],"usage":{"input_tokens":50,"output_tokens":30}}}"""
            val parsed = ClaudeMessageParser.parseMessage(assistantJson)?.message as? AssistantMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed) as? AssistantDisplayMessage
                
                if (displayMessage != null) {
                    val expectedContent = "我来帮你查看文件内容。\n请稍等片刻..."
                    val actualContent = displayMessage.displayContent
                    
                    if (actualContent == expectedContent &&
                        displayMessage.displayRole == MessageDisplayRole.ASSISTANT &&
                        displayMessage.displayId == "assistant-123" &&
                        displayMessage.isStreamable &&
                        displayMessage is ToolCallMessage &&
                        displayMessage is StreamableMessage &&
                        displayMessage is StatisticsMessage &&
                        displayMessage.tokenUsage?.input_tokens == 50 &&
                        displayMessage.tokenUsage?.output_tokens == 30) {
                        results.add("✅ 助手消息转换成功")
                        passed++
                    } else {
                        results.add("❌ 助手消息转换失败 - 内容或接口不匹配")
                        results.add("  期望内容: '$expectedContent'")
                        results.add("  实际内容: '$actualContent'")
                        results.add("  Token统计: ${displayMessage.tokenUsage}")
                        failed++
                    }
                } else {
                    results.add("❌ 助手消息转换失败 - 转换结果为null")
                    failed++
                }
            } else {
                results.add("❌ 助手消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 助手消息转换异常: ${e.message}")
            failed++
        }
        
        // 测试3: 带工具调用的助手消息转换
        try {
            val toolCallJson = """{"type":"assistant","uuid":"tool-assistant-123","sessionId":"sess-1","timestamp":"2025-01-01T10:02:00Z","message":{"id":"msg-2","type":"message","role":"assistant","model":"claude-3-sonnet-20240229","content":[{"type":"text","text":"我需要读取文件来查看内容。"},{"type":"tool_use","id":"tool-1","name":"read","input":{"file_path":"/path/to/file.txt"}},{"type":"tool_use","id":"tool-2","name":"bash","input":{"command":"ls -la"}}],"usage":{"input_tokens":80,"output_tokens":40}}}"""
            val parsed = ClaudeMessageParser.parseMessage(toolCallJson)?.message as? AssistantMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed) as? AssistantDisplayMessage
                
                if (displayMessage != null) {
                    val expectedContent = "我需要读取文件来查看内容。"
                    val actualContent = displayMessage.displayContent
                    
                    if (actualContent == expectedContent &&
                        displayMessage is ToolCallMessage &&
                        displayMessage.toolCalls.size == 2 &&
                        displayMessage.toolCalls[0].name == "read" &&
                        displayMessage.toolCalls[0].id == "tool-1" &&
                        displayMessage.toolCalls[0].status == ToolCallStatus.PENDING &&
                        displayMessage.toolCalls[1].name == "bash" &&
                        displayMessage.toolCalls[1].id == "tool-2") {
                        results.add("✅ 工具调用助手消息转换成功")
                        passed++
                    } else {
                        results.add("❌ 工具调用助手消息转换失败")
                        results.add("  实际内容: '$actualContent'")
                        results.add("  工具调用数量: ${displayMessage.toolCalls.size}")
                        displayMessage.toolCalls.forEach { toolCall ->
                            results.add("    - ${toolCall.name} (${toolCall.id}): ${toolCall.status}")
                        }
                        failed++
                    }
                } else {
                    results.add("❌ 工具调用助手消息转换失败 - 转换结果为null")
                    failed++
                }
            } else {
                results.add("❌ 工具调用助手消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 工具调用助手消息转换异常: ${e.message}")
            failed++
        }
        
        // 测试4: 系统消息转换
        try {
            val systemJson = """{"type":"system","subtype":"session_start","uuid":"system-123","sessionId":"sess-1","timestamp":"2025-01-01T09:59:00Z","tools":["read","write","bash","grep"],"model":"claude-3-sonnet-20240229","permissionMode":"default"}"""
            val parsed = ClaudeMessageParser.parseMessage(systemJson)?.message as? SystemMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed) as? SystemDisplayMessage
                
                if (displayMessage != null) {
                    val expectedContent = "系统: session_start\n可用工具: read, write, bash, grep\n模型: claude-3-sonnet-20240229\n权限: default"
                    val actualContent = displayMessage.displayContent
                    
                    if (actualContent == expectedContent &&
                        displayMessage.displayRole == MessageDisplayRole.SYSTEM &&
                        displayMessage.displayId == "system-123" &&
                        !displayMessage.isStreamable) {
                        results.add("✅ 系统消息转换成功")
                        passed++
                    } else {
                        results.add("❌ 系统消息转换失败")
                        results.add("  期望内容: '$expectedContent'")
                        results.add("  实际内容: '$actualContent'")
                        failed++
                    }
                } else {
                    results.add("❌ 系统消息转换失败 - 转换结果为null")
                    failed++
                }
            } else {
                results.add("❌ 系统消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 系统消息转换异常: ${e.message}")
            failed++
        }
        
        // 测试5: 摘要消息转换
        try {
            val summaryJson = """{"type":"summary","uuid":"summary-123","sessionId":"sess-1","timestamp":"2025-01-01T10:30:00Z","summary":"用户询问文件内容，AI使用read工具读取了/path/to/file.txt文件并返回了内容。","leafUuid":"leaf-123","isCompactSummary":true}"""
            val parsed = ClaudeMessageParser.parseMessage(summaryJson)?.message as? SummaryMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed) as? SummaryDisplayMessage
                
                if (displayMessage != null) {
                    val expectedContent = "用户询问文件内容，AI使用read工具读取了/path/to/file.txt文件并返回了内容。"
                    val actualContent = displayMessage.displayContent
                    
                    if (actualContent == expectedContent &&
                        displayMessage.displayRole == MessageDisplayRole.SYSTEM &&
                        displayMessage.displayId == "summary-123" &&
                        displayMessage.isCompactSummary &&
                        !displayMessage.isStreamable) {
                        results.add("✅ 摘要消息转换成功")
                        passed++
                    } else {
                        results.add("❌ 摘要消息转换失败")
                        results.add("  期望内容: '$expectedContent'")
                        results.add("  实际内容: '$actualContent'")
                        results.add("  是否压缩摘要: ${displayMessage.isCompactSummary}")
                        failed++
                    }
                } else {
                    results.add("❌ 摘要消息转换失败 - 转换结果为null")
                    failed++
                }
            } else {
                results.add("❌ 摘要消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 摘要消息转换异常: ${e.message}")
            failed++
        }
        
        // 测试6: 结果消息处理（应该返回null，不直接显示）
        try {
            val resultJson = """{"type":"result","subtype":"api_usage","uuid":"result-123","sessionId":"sess-1","timestamp":"2025-01-01T10:35:00Z","usage":{"input_tokens":200,"output_tokens":150},"total_cost_usd":0.05}"""
            val parsed = ClaudeMessageParser.parseMessage(resultJson)?.message as? ResultMessage
            
            if (parsed != null) {
                val displayMessage = MessageDisplayAdapter.toDisplayMessage(parsed)
                
                if (displayMessage == null) {
                    results.add("✅ 结果消息正确处理（不转换为显示消息）")
                    passed++
                } else {
                    results.add("❌ 结果消息不应该转换为显示消息")
                    failed++
                }
            } else {
                results.add("❌ 结果消息解析失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 结果消息处理异常: ${e.message}")
            failed++
        }
        
        // 测试7: 错误消息创建
        try {
            val errorMessage = MessageDisplayAdapter.createErrorMessage(
                content = "网络连接错误，请检查网络设置",
                errorType = "network",
                details = "Connection timeout after 30 seconds"
            )
            
            if (errorMessage.displayContent == "网络连接错误，请检查网络设置" &&
                errorMessage.displayRole == MessageDisplayRole.ERROR &&
                errorMessage.errorType == "network" &&
                errorMessage.errorDetails == "Connection timeout after 30 seconds" &&
                !errorMessage.isStreamable) {
                results.add("✅ 错误消息创建成功")
                passed++
            } else {
                results.add("❌ 错误消息创建失败")
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 错误消息创建异常: ${e.message}")
            failed++
        }
        
        // 测试8: 批量转换测试
        try {
            val jsonLines = listOf(
                """{"type":"user","uuid":"batch-user-1","sessionId":"sess-batch","message":{"role":"user","content":[{"type":"text","text":"批量测试用户消息"}]}}""",
                """{"type":"assistant","uuid":"batch-assistant-1","sessionId":"sess-batch","message":{"id":"batch-msg-1","role":"assistant","content":[{"type":"text","text":"批量测试助手回复"}],"usage":{"input_tokens":20,"output_tokens":15}}}""",
                """{"type":"summary","uuid":"batch-summary-1","sessionId":"sess-batch","summary":"批量测试摘要","isCompactSummary":false}"""
            )
            
            val nativeMessages = jsonLines.mapNotNull { 
                ClaudeMessageParser.parseMessage(it)?.message 
            }
            
            val displayMessages = MessageDisplayAdapter.toDisplayMessages(nativeMessages)
            
            if (nativeMessages.size == 3 && 
                displayMessages.size == 3 &&
                displayMessages[0] is UserDisplayMessage &&
                displayMessages[1] is AssistantDisplayMessage &&
                displayMessages[2] is SummaryDisplayMessage) {
                results.add("✅ 批量转换测试成功")
                passed++
            } else {
                results.add("❌ 批量转换测试失败")
                results.add("  原生消息数: ${nativeMessages.size}")
                results.add("  显示消息数: ${displayMessages.size}")
                displayMessages.forEachIndexed { index, message ->
                    results.add("    [$index] ${message::class.simpleName}")
                }
                failed++
            }
        } catch (e: Exception) {
            results.add("❌ 批量转换测试异常: ${e.message}")
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
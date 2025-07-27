package com.claudecodeplus.sdk

import kotlinx.serialization.json.*
import kotlin.test.*
import java.io.File

class ClaudeMessagesTest {
    
    @Test
    fun `test parsing summary message`() {
        val json = """{"type":"summary","summary":"Claude Code Plus: AI Programming Assistant Project","leafUuid":"ab26ea7e-3354-4d98-aab3-42e1cc8ba1c0"}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is SummaryMessage)
        assertEquals("Claude Code Plus: AI Programming Assistant Project", message.summary)
        assertEquals("ab26ea7e-3354-4d98-aab3-42e1cc8ba1c0", message.leafUuid)
    }
    
    @Test
    fun `test parsing user message with simple content`() {
        val json = """{"parentUuid":null,"isSidechain":false,"userType":"external","cwd":"C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","version":"1.0.61","gitBranch":"feature/compose-ui","type":"user","message":{"role":"user","content":"理解当前项目"},"uuid":"65a7c3a9-0fe9-4397-8209-27caa76e49f5","timestamp":"2025-07-27T03:23:22.742Z"}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is UserMessage)
        assertEquals("65a7c3a9-0fe9-4397-8209-27caa76e49f5", message.uuid)
        assertEquals("1170ed86-66ee-4ff6-b944-ae2da6929fb9", message.sessionId)
        assertEquals("C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop", message.cwd)
        
        // 验证消息内容
        assertNotNull(message.message)
        assertEquals("user", message.message?.role)
        
        // 解析简单字符串内容
        val content = message.message?.content
        assertTrue(content is ContentOrList.StringContent)
        assertEquals("理解当前项目", content.value)
    }
    
    @Test
    fun `test parsing assistant message with text content`() {
        val json = """{"parentUuid":"65a7c3a9-0fe9-4397-8209-27caa76e49f5","isSidechain":false,"userType":"external","cwd":"C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","version":"1.0.61","gitBranch":"feature/compose-ui","message":{"id":"msg_0137ARc898e5JLJmAb2oJLCq","type":"message","role":"assistant","model":"claude-opus-4-20250514","content":[{"type":"text","text":"我来帮你理解当前的 Claude Code Plus 项目。"}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":4,"cache_creation_input_tokens":22762,"cache_read_input_tokens":0,"output_tokens":4,"service_tier":"standard"}},"requestId":"req_011CRWom1kdQzZzGxWE36SQS","type":"assistant","uuid":"bf63d19d-c03d-45d5-82bd-eb11edc387d9","timestamp":"2025-07-27T03:23:26.842Z"}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is AssistantMessage)
        assertEquals("bf63d19d-c03d-45d5-82bd-eb11edc387d9", message.uuid)
        assertEquals("req_011CRWom1kdQzZzGxWE36SQS", message.requestId)
        
        // 验证助手消息数据
        val messageData = message.message
        assertNotNull(messageData)
        assertEquals("msg_0137ARc898e5JLJmAb2oJLCq", messageData.id)
        assertEquals("message", messageData.type)
        assertEquals("assistant", messageData.role)
        assertEquals("claude-opus-4-20250514", messageData.model)
        
        // 验证内容块
        val content = messageData.content
        assertNotNull(content)
        assertEquals(1, content.size)
        
        val textBlock = content[0]
        assertTrue(textBlock is TextBlock)
        assertEquals("我来帮你理解当前的 Claude Code Plus 项目。", textBlock.text)
        
        // 验证使用统计
        val usage = messageData.usage
        assertNotNull(usage)
        assertEquals(4, usage.inputTokens)
        assertEquals(22762, usage.cacheCreationInputTokens)
        assertEquals(0, usage.cacheReadInputTokens)
        assertEquals(4, usage.outputTokens)
    }
    
    @Test
    fun `test parsing assistant message with tool use`() {
        val json = """{"parentUuid":"bf63d19d-c03d-45d5-82bd-eb11edc387d9","isSidechain":false,"userType":"external","cwd":"C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","version":"1.0.61","gitBranch":"feature/compose-ui","message":{"id":"msg_0137ARc898e5JLJmAb2oJLCq","type":"message","role":"assistant","model":"claude-opus-4-20250514","content":[{"type":"tool_use","id":"toolu_0147xwJhoT44PTBU4khyM3De","name":"TodoWrite","input":{"todos":[{"id":"1","content":"阅读项目核心文档了解架构","status":"in_progress","priority":"high"},{"id":"2","content":"查看项目结构和模块组成","status":"pending","priority":"high"},{"id":"3","content":"了解当前实现状态","status":"pending","priority":"medium"}]}}],"stop_reason":null,"stop_sequence":null,"usage":{"input_tokens":4,"cache_creation_input_tokens":22762,"cache_read_input_tokens":0,"output_tokens":219,"service_tier":"standard"}},"requestId":"req_011CRWom1kdQzZzGxWE36SQS","type":"assistant","uuid":"fb2d7dc7-fabd-401b-9334-9e11581ec478","timestamp":"2025-07-27T03:23:29.095Z"}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is AssistantMessage)
        
        val content = message.message?.content
        assertNotNull(content)
        assertEquals(1, content.size)
        
        val toolUse = content[0]
        assertTrue(toolUse is ToolUseBlock)
        assertEquals("toolu_0147xwJhoT44PTBU4khyM3De", toolUse.id)
        assertEquals("TodoWrite", toolUse.name)
        
        // 验证工具输入
        val input = toolUse.input
        assertNotNull(input)
        assertTrue(input.containsKey("todos"))
    }
    
    @Test
    fun `test parsing user message with tool result`() {
        val json = """{"parentUuid":"fb2d7dc7-fabd-401b-9334-9e11581ec478","isSidechain":false,"userType":"external","cwd":"C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","version":"1.0.61","gitBranch":"feature/compose-ui","type":"user","message":{"role":"user","content":[{"tool_use_id":"toolu_0147xwJhoT44PTBU4khyM3De","type":"tool_result","content":"Todos have been modified successfully. Ensure that you continue to use the todo list to track your progress. Please proceed with the current tasks if applicable"}]},"uuid":"d7cfdaa0-6caf-40b5-9114-09fef76f292e","timestamp":"2025-07-27T03:23:29.144Z","toolUseResult":{"oldTodos":[],"newTodos":[{"content":"阅读项目核心文档了解架构","status":"in_progress","priority":"high","id":"1"},{"content":"查看项目结构和模块组成","status":"pending","priority":"high","id":"2"},{"content":"了解当前实现状态","status":"pending","priority":"medium","id":"3"}]}}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is UserMessage)
        
        // 验证内容块
        val messageData = message.message
        assertNotNull(messageData)
        val content = messageData.content
        assertTrue(content is ContentOrList.ListContent)
        
        val blocks = content.value
        assertEquals(1, blocks.size)
        
        val toolResult = blocks[0]
        assertTrue(toolResult is ToolResultBlock)
        assertEquals("toolu_0147xwJhoT44PTBU4khyM3De", toolResult.toolUseId)
        
        val resultContent = toolResult.content
        assertTrue(resultContent is ContentOrString.StringValue)
        assertTrue(resultContent.value.contains("Todos have been modified successfully"))
        
        // 验证额外的工具结果数据
        assertNotNull(message.toolUseResult)
    }
    
    @Test
    fun `test parsing system message`() {
        val json = """{"subtype":"init","uuid":"a20e3a4d-4e90-4098-b3f6-d6cf388c41d8","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","type":"system","timestamp":"2025-07-27T03:23:00.000Z","apiKeySource":"user","cwd":"C:\\Users\\16790\\IdeaProjects\\claude-code-plus\\desktop","version":"1.0.61","tools":["Read","Write","Edit","Bash","TodoWrite"],"mcp_servers":[{"name":"gradle-class-finder","status":"connected"},{"name":"excel-stdio-mcp","status":"connected"}],"model":"claude-opus-4-20250514","permissionMode":"default"}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is SystemMessage)
        assertEquals("init", message.subtype)
        assertEquals("a20e3a4d-4e90-4098-b3f6-d6cf388c41d8", message.uuid)
        assertEquals("user", message.apiKeySource)
        assertEquals("claude-opus-4-20250514", message.model)
        assertEquals("default", message.permissionMode)
        
        // 验证工具列表
        val tools = message.tools
        assertNotNull(tools)
        assertEquals(5, tools.size)
        assertTrue(tools.contains("Read"))
        assertTrue(tools.contains("TodoWrite"))
        
        // 验证 MCP 服务器
        val mcpServers = message.mcpServers
        assertNotNull(mcpServers)
        assertEquals(2, mcpServers.size)
        assertEquals("gradle-class-finder", mcpServers[0].name)
        assertEquals("connected", mcpServers[0].status)
    }
    
    @Test
    fun `test parsing result message`() {
        val json = """{"subtype":"success","uuid":"result-12345","sessionId":"1170ed86-66ee-4ff6-b944-ae2da6929fb9","type":"result","timestamp":"2025-07-27T03:30:00.000Z","duration_ms":420000,"duration_api_ms":15000,"num_turns":5,"is_error":false,"result":"Successfully completed the task","total_cost_usd":0.15,"usage":{"input_tokens":5000,"output_tokens":2500,"cache_creation_input_tokens":25000,"cache_read_input_tokens":0}}"""
        
        val message = parseClaudeMessage(json)
        
        assertNotNull(message)
        assertTrue(message is ResultMessage)
        assertEquals("success", message.subtype)
        assertEquals("result-12345", message.uuid)
        assertEquals(420000L, message.durationMs)
        assertEquals(15000L, message.durationApiMs)
        assertEquals(5, message.numTurns)
        assertEquals(false, message.isError)
        assertEquals("Successfully completed the task", message.result)
        assertEquals(0.15, message.totalCostUsd)
        
        // 验证使用统计
        val usage = message.usage
        assertNotNull(usage)
        assertEquals(5000, usage.inputTokens)
        assertEquals(2500, usage.outputTokens)
    }
    
    @Test
    fun `test parsing all messages from file`() {
        val testFile = this::class.java.getResource("/test-session.jsonl")?.toURI()?.let { File(it) }
        assertNotNull(testFile, "Test file should exist")
        
        var successCount = 0
        var failCount = 0
        val messageTypes = mutableMapOf<String, Int>()
        
        testFile.forEachLine { line ->
            if (line.isNotBlank()) {
                val message = parseClaudeMessage(line)
                if (message != null) {
                    successCount++
                    val type = when (message) {
                        is UserMessage -> "user"
                        is AssistantMessage -> "assistant"
                        is SystemMessage -> "system"
                        is ResultMessage -> "result"
                        is SummaryMessage -> "summary"
                    }
                    messageTypes[type] = messageTypes.getOrDefault(type, 0) + 1
                } else {
                    failCount++
                    println("Failed to parse: ${line.take(100)}...")
                }
            }
        }
        
        println("Parsing summary:")
        println("  Success: $successCount")
        println("  Failed: $failCount")
        println("  Message types: $messageTypes")
        
        assertTrue(successCount > 0, "Should successfully parse at least one message")
        assertEquals(0, failCount, "Should not have any parsing failures")
    }
    
    @Test
    fun `test tool ID matching between tool use and tool result`() {
        // 解析工具使用消息
        val toolUseJson = """{"type":"assistant","message":{"content":[{"type":"tool_use","id":"toolu_0147xwJhoT44PTBU4khyM3De","name":"TodoWrite","input":{}}]}}"""
        val toolUseMessage = parseClaudeMessage(toolUseJson) as? AssistantMessage
        assertNotNull(toolUseMessage)
        
        val toolUseBlock = toolUseMessage.message?.content?.firstOrNull() as? ToolUseBlock
        assertNotNull(toolUseBlock)
        val toolId = toolUseBlock.id
        
        // 解析工具结果消息
        val toolResultJson = """{"type":"user","message":{"content":[{"tool_use_id":"toolu_0147xwJhoT44PTBU4khyM3De","type":"tool_result","content":"Success"}]}}"""
        val toolResultMessage = parseClaudeMessage(toolResultJson) as? UserMessage
        assertNotNull(toolResultMessage)
        
        val content = toolResultMessage.message?.content as? ContentOrList.ListContent
        assertNotNull(content)
        val toolResultBlock = content.value.firstOrNull() as? ToolResultBlock
        assertNotNull(toolResultBlock)
        
        // 验证 ID 匹配
        assertEquals(toolId, toolResultBlock.toolUseId, "Tool IDs should match")
    }
}
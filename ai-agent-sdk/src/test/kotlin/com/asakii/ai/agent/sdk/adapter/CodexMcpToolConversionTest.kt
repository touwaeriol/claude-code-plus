package com.asakii.ai.agent.sdk.adapter

import com.asakii.ai.agent.sdk.model.*
import com.asakii.codex.agent.sdk.appserver.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * 测试 Codex MCP 工具调用事件到 Claude 标准格式的转换
 *
 * 验证流程：
 * 1. Codex 发出 ItemStarted (McpToolCall) → 转换为 ContentStartedEvent (ToolUseContent)
 * 2. Codex 发出 ItemCompleted (McpToolCall) → 转换为 ContentCompletedEvent (ToolResultContent)
 * 3. TurnCompleted → 转换为 AssistantMessageEvent
 *
 * 关键验证点：
 * - tool_use.id 必须与 tool_result.tool_use_id 匹配
 * - AssistantMessageEvent.content 是否包含 ToolUseContent
 */
class CodexMcpToolConversionTest {

    private val testThreadId = "thread_test_001"
    private val testTurnId = "turn_test_001"

    private fun createAdapter(): CodexAppServerStreamAdapter {
        return CodexAppServerStreamAdapter(
            sessionIdProvider = { "test-session-id" }
        )
    }

    @Test
    fun `test MCP tool call conversion - ItemStarted should produce ContentStartedEvent with ToolUseContent`() {
        // 模拟 Codex 的 MCP 工具调用开始事件
        val mcpToolCall = ThreadItem.McpToolCall(
            id = "call_abc123",
            server = "ide-file",
            tool = "ReadFile",
            status = McpToolCallStatus.InProgress,
            arguments = buildJsonObject {
                put("filePath", "src/main/kotlin/Test.kt")
            }
        )

        val itemStartedEvent = AppServerEvent.ItemStarted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCall
        )

        // 创建适配器并转换
        val adapter = createAdapter()
        val events = adapter.convert(itemStartedEvent)

        // 验证
        println("=== ItemStarted 转换结果 ===")
        events.forEach { event ->
            println("Event type: ${event::class.simpleName}")
            when (event) {
                is ContentStartedEvent -> {
                    println("  - index: ${event.index}")
                    println("  - contentType: ${event.contentType}")
                    println("  - toolName: ${event.toolName}")
                    println("  - content: ${event.content}")
                    val content = event.content
                    if (content is ToolUseContent) {
                        println("  - ToolUseContent.id: ${content.id}")
                        println("  - ToolUseContent.name: ${content.name}")
                        println("  - ToolUseContent.input: ${content.input}")
                        println("  - ToolUseContent.status: ${content.status}")
                    }
                }
                else -> println("  - $event")
            }
        }

        // 断言
        assertTrue(events.isNotEmpty(), "应该产生至少一个事件")
        val contentStarted = events.filterIsInstance<ContentStartedEvent>().firstOrNull()
        assertNotNull(contentStarted, "应该产生 ContentStartedEvent")

        val toolUseContent = contentStarted?.content as? ToolUseContent
        assertNotNull(toolUseContent, "content 应该是 ToolUseContent")
        assertEquals("call_abc123", toolUseContent?.id, "tool_use.id 应该匹配")
        assertEquals("mcp__ide-file__ReadFile", toolUseContent?.name, "工具名称应该正确")
    }

    @Test
    fun `test MCP tool call conversion - ItemCompleted should produce ContentCompletedEvent with ToolResultContent`() {
        // 模拟 Codex 的 MCP 工具调用完成事件
        val mcpToolCallInProgress = ThreadItem.McpToolCall(
            id = "call_abc123",
            server = "ide-file",
            tool = "ReadFile",
            status = McpToolCallStatus.InProgress,
            arguments = buildJsonObject {
                put("filePath", "src/main/kotlin/Test.kt")
            }
        )

        val mcpToolCallCompleted = ThreadItem.McpToolCall(
            id = "call_abc123",
            server = "ide-file",
            tool = "ReadFile",
            status = McpToolCallStatus.Completed,
            arguments = buildJsonObject {
                put("filePath", "src/main/kotlin/Test.kt")
            },
            result = McpToolCallResult(
                content = listOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "file content here...")
                    }
                )
            )
        )

        // 需要先处理 ItemStarted 来建立索引映射
        val adapter = createAdapter()
        adapter.convert(AppServerEvent.ItemStarted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallInProgress
        ))

        // 转换 ItemCompleted
        val events = adapter.convert(AppServerEvent.ItemCompleted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallCompleted
        ))

        // 验证
        println("\n=== ItemCompleted 转换结果 ===")
        events.forEach { event ->
            println("Event type: ${event::class.simpleName}")
            when (event) {
                is ContentCompletedEvent -> {
                    println("  - index: ${event.index}")
                    println("  - content: ${event.content}")
                    val content = event.content
                    if (content is ToolResultContent) {
                        println("  - ToolResultContent.toolUseId: ${content.toolUseId}")
                        println("  - ToolResultContent.content: ${content.content}")
                        println("  - ToolResultContent.isError: ${content.isError}")
                    }
                }
                else -> println("  - $event")
            }
        }

        // 断言
        assertTrue(events.isNotEmpty(), "应该产生至少一个事件")
        val contentCompleted = events.filterIsInstance<ContentCompletedEvent>().firstOrNull()
        assertNotNull(contentCompleted, "应该产生 ContentCompletedEvent")

        val toolResultContent = contentCompleted?.content as? ToolResultContent
        assertNotNull(toolResultContent, "content 应该是 ToolResultContent")
        assertEquals("call_abc123", toolResultContent?.toolUseId, "tool_result.tool_use_id 应该与 tool_use.id 匹配")
        assertFalse(toolResultContent?.isError ?: true, "不应该是错误")
    }

    @Test
    fun `test MCP tool call conversion - failed should surface result content when error is null`() {
        val mcpToolCallInProgress = ThreadItem.McpToolCall(
            id = "call_err_001",
            server = "ide-file",
            tool = "WriteFile",
            status = McpToolCallStatus.InProgress,
            arguments = buildJsonObject {
                put("filePath", "demo.txt")
                put("content", "demo")
            }
        )

        val mcpToolCallFailed = ThreadItem.McpToolCall(
            id = "call_err_001",
            server = "ide-file",
            tool = "WriteFile",
            status = McpToolCallStatus.Failed,
            arguments = buildJsonObject {
                put("filePath", "demo.txt")
                put("content", "demo")
            },
            result = McpToolCallResult(
                content = listOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "错误: Permission denied: test")
                    }
                )
            )
        )

        val adapter = createAdapter()
        adapter.convert(AppServerEvent.ItemStarted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallInProgress
        ))

        val events = adapter.convert(AppServerEvent.ItemCompleted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallFailed
        ))

        val contentCompleted = events.filterIsInstance<ContentCompletedEvent>().firstOrNull()
        assertNotNull(contentCompleted, "应该产生 ContentCompletedEvent")
        val toolResultContent = contentCompleted?.content as? ToolResultContent
        assertNotNull(toolResultContent, "content 应该是 ToolResultContent")
        assertTrue(toolResultContent?.isError ?: false, "应标记为错误")

        val contentJson = toolResultContent?.content as? JsonObject
        val text = contentJson?.get("text")?.jsonPrimitive?.content
        assertEquals("错误: Permission denied: test", text, "应返回工具结果中的错误文本")
    }

    @Test
    fun `test complete MCP tool call flow - verify tool_use_id matching and AssistantMessageEvent content`() {
        val toolCallId = "call_xyz789"

        // 1. 模拟完整的 MCP 工具调用流程
        val mcpToolCallInProgress = ThreadItem.McpToolCall(
            id = toolCallId,
            server = "ide-lsp",
            tool = "CodeSearch",
            status = McpToolCallStatus.InProgress,
            arguments = buildJsonObject {
                put("query", "class MyClass")
            }
        )

        val mcpToolCallCompleted = ThreadItem.McpToolCall(
            id = toolCallId,
            server = "ide-lsp",
            tool = "CodeSearch",
            status = McpToolCallStatus.Completed,
            arguments = buildJsonObject {
                put("query", "class MyClass")
            },
            result = McpToolCallResult(
                content = listOf(
                    buildJsonObject {
                        put("type", "text")
                        put("text", "Found 3 matches...")
                    }
                )
            )
        )

        // 2. 模拟 TurnInfo
        val turnInfo = TurnInfo(
            id = testTurnId,
            status = TurnStatus.Completed,
            items = listOf(mcpToolCallCompleted)
        )

        val adapter = createAdapter()

        // 3. 处理事件序列
        println("\n=== 完整 MCP 工具调用流程 ===")

        // TurnStarted
        val turnStartedEvents = adapter.convert(AppServerEvent.TurnStarted(
            threadId = testThreadId,
            turn = turnInfo.copy(status = TurnStatus.InProgress)
        ))
        println("\n[1] TurnStarted events: ${turnStartedEvents.map { it::class.simpleName }}")

        // ItemStarted
        val itemStartedEvents = adapter.convert(AppServerEvent.ItemStarted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallInProgress
        ))
        println("[2] ItemStarted events: ${itemStartedEvents.map { it::class.simpleName }}")

        var toolUseId: String? = null
        itemStartedEvents.filterIsInstance<ContentStartedEvent>().forEach { event ->
            val content = event.content
            if (content is ToolUseContent) {
                toolUseId = content.id
                println("    -> ToolUseContent.id = $toolUseId")
            }
        }

        // ItemCompleted
        val itemCompletedEvents = adapter.convert(AppServerEvent.ItemCompleted(
            threadId = testThreadId,
            turnId = testTurnId,
            item = mcpToolCallCompleted
        ))
        println("[3] ItemCompleted events: ${itemCompletedEvents.map { it::class.simpleName }}")

        var toolResultToolUseId: String? = null
        itemCompletedEvents.filterIsInstance<ContentCompletedEvent>().forEach { event ->
            val content = event.content
            if (content is ToolResultContent) {
                toolResultToolUseId = content.toolUseId
                println("    -> ToolResultContent.toolUseId = $toolResultToolUseId")
            }
        }

        // TurnCompleted
        val turnCompletedEvents = adapter.convert(AppServerEvent.TurnCompleted(
            threadId = testThreadId,
            turn = turnInfo
        ))
        println("[4] TurnCompleted events: ${turnCompletedEvents.map { it::class.simpleName }}")

        // 检查 AssistantMessageEvent 的 content
        turnCompletedEvents.filterIsInstance<AssistantMessageEvent>().forEach { event ->
            println("    -> AssistantMessageEvent.content types: ${event.content.map { it::class.simpleName }}")
            event.content.forEach { block ->
                when (block) {
                    is ToolUseContent -> println("       - ToolUseContent: id=${block.id}")
                    is TextContent -> println("       - TextContent: ${block.text.take(50)}...")
                    is ThinkingContent -> println("       - ThinkingContent: ${block.thinking.take(50)}...")
                    else -> println("       - ${block::class.simpleName}")
                }
            }
        }

        // 4. 验证 ID 匹配
        println("\n=== 验证结果 ===")
        println("tool_use.id = $toolUseId")
        println("tool_result.tool_use_id = $toolResultToolUseId")
        println("IDs match: ${toolUseId == toolResultToolUseId}")

        assertEquals(toolUseId, toolResultToolUseId, "tool_use.id 和 tool_result.tool_use_id 必须匹配！")
        assertEquals(toolCallId, toolUseId, "tool_use.id 应该等于原始的 id")

        // 5. 检查 AssistantMessageEvent 是否包含 ToolUseContent
        val assistantMessage = turnCompletedEvents.filterIsInstance<AssistantMessageEvent>().firstOrNull()
        val hasToolUseInContent = assistantMessage?.content?.any { it is ToolUseContent } ?: false
        println("\n⚠️ AssistantMessageEvent 包含 ToolUseContent: $hasToolUseInContent")

        // 这是关键问题：AssistantMessageEvent.content 是否包含 ToolUseContent？
        // 如果不包含，前端的 resolveToolStatus 可能无法正确匹配
        // 但实际上，前端应该通过 content_block_start 事件来获取 tool_use 信息，
        // 而不是依赖 AssistantMessageEvent.content
        
        if (!hasToolUseInContent) {
            println("⚠️ 注意：AssistantMessageEvent.content 不包含 ToolUseContent")
            println("   这可能导致前端无法正确匹配 tool_use 和 tool_result")
            println("   前端需要依赖 content_block_start 事件来获取 tool_use 信息")
        }
    }
}

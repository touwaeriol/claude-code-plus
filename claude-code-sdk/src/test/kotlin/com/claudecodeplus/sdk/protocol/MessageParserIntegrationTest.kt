package com.claudecodeplus.sdk.protocol

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MessageParserIntegrationTest {

    private val parser = MessageParser()

    @Test
    fun `should parse assistant message with specific tool types`() {
        // Given - 一个包含多种工具调用的助手消息
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", "我将帮你执行这些任务。")
                })
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "bash_123")
                    put("name", "Bash")
                    put("input", buildJsonObject {
                        put("command", "ls -la")
                        put("description", "List files")
                    })
                })
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "edit_456")
                    put("name", "Edit")
                    put("input", buildJsonObject {
                        put("file_path", "/test.txt")
                        put("old_string", "old")
                        put("new_string", "new")
                        put("replace_all", false)
                    })
                })
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "mcp_789")
                    put("name", "mcp__testserver__calculate")
                    put("input", buildJsonObject {
                        put("expression", "2 + 2")
                        put("format", "decimal")
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then
        assertIs<AssistantMessage>(result)
        assertEquals("claude-3-5-sonnet-20241022", result.model)
        assertEquals(4, result.content.size)

        // 验证文本块
        val textBlock = result.content[0]
        assertIs<TextBlock>(textBlock)
        assertEquals("我将帮你执行这些任务。", textBlock.text)

        // 验证Bash工具
        val bashTool = result.content[1]
        assertIs<BashToolUse>(bashTool)
        assertEquals("bash_123", bashTool.id)
        assertEquals(ToolType.BASH, bashTool.toolType)
        assertEquals("ls -la", bashTool.command)
        assertEquals("List files", bashTool.description)

        // 验证Edit工具
        val editTool = result.content[2]
        assertIs<EditToolUse>(editTool)
        assertEquals("edit_456", editTool.id)
        assertEquals(ToolType.EDIT, editTool.toolType)
        assertEquals("/test.txt", editTool.filePath)
        assertEquals("old", editTool.oldString)
        assertEquals("new", editTool.newString)
        assertEquals(false, editTool.replaceAll)

        // 验证MCP工具
        val mcpTool = result.content[3]
        assertIs<McpToolUse>(mcpTool)
        assertEquals("mcp_789", mcpTool.id)
        assertEquals(ToolType.MCP_TOOL, mcpTool.toolType)
        assertEquals("mcp__testserver__calculate", mcpTool.fullToolName)
        assertEquals("testserver", mcpTool.serverName)
        assertEquals("calculate", mcpTool.functionName)
        assertEquals("2 + 2", mcpTool.parameters["expression"])
        assertEquals("decimal", mcpTool.parameters["format"])
    }

    @Test
    fun `should parse TodoWrite tool correctly`() {
        // Given
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "todo_123")
                    put("name", "TodoWrite")
                    put("input", buildJsonObject {
                        put("todos", buildJsonArray {
                            add(buildJsonObject {
                                put("content", "Complete task 1")
                                put("status", "pending")
                                put("activeForm", "Completing task 1")
                            })
                            add(buildJsonObject {
                                put("content", "Review code")
                                put("status", "in_progress")
                                put("activeForm", "Reviewing code")
                            })
                        })
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then
        assertIs<AssistantMessage>(result)
        val todoTool = result.content[0]
        assertIs<TodoWriteToolUse>(todoTool)
        assertEquals("todo_123", todoTool.id)
        assertEquals(ToolType.TODO_WRITE, todoTool.toolType)
        assertEquals(2, todoTool.todos.size)

        val firstTodo = todoTool.todos[0]
        assertEquals("Complete task 1", firstTodo.content)
        assertEquals("pending", firstTodo.status)
        assertEquals("Completing task 1", firstTodo.activeForm)

        val secondTodo = todoTool.todos[1]
        assertEquals("Review code", secondTodo.content)
        assertEquals("in_progress", secondTodo.status)
        assertEquals("Reviewing code", secondTodo.activeForm)
    }

    @Test
    fun `should parse unknown tool as UnknownToolUse`() {
        // Given
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "unknown_123")
                    put("name", "SomeUnknownTool")
                    put("input", buildJsonObject {
                        put("param1", "value1")
                        put("param2", 42)
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then
        assertIs<AssistantMessage>(result)
        val unknownTool = result.content[0]
        assertIs<UnknownToolUse>(unknownTool)
        assertEquals("unknown_123", unknownTool.id)
        assertEquals(ToolType.UNKNOWN, unknownTool.toolType)
        assertEquals("SomeUnknownTool", unknownTool.toolName)
        assertEquals("value1", unknownTool.parameters["param1"])
        assertEquals(42, unknownTool.parameters["param2"])
    }

    @Test
    fun `should verify getTypedParameters works correctly`() {
        // Given
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "read_123")
                    put("name", "Read")
                    put("input", buildJsonObject {
                        put("file_path", "/test/file.txt")
                        put("offset", 10)
                        put("limit", 50)
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then
        assertIs<AssistantMessage>(result)
        val readTool = result.content[0]
        assertIs<ReadToolUse>(readTool)

        // 验证具体工具的强类型参数
        assertEquals("/test/file.txt", readTool.filePath)
        assertEquals(10, readTool.offset)
        assertEquals(50, readTool.limit)

        // 验证通用接口的参数访问
        val typedParams = readTool.getTypedParameters()
        assertEquals("/test/file.txt", typedParams["file_path"])
        assertEquals(10, typedParams["offset"])
        assertEquals(50, typedParams["limit"])
    }

    @Test
    fun `should handle complex MultiEdit tool`() {
        // Given
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "multiedit_123")
                    put("name", "MultiEdit")
                    put("input", buildJsonObject {
                        put("file_path", "/test/complex.txt")
                        put("edits", buildJsonArray {
                            add(buildJsonObject {
                                put("old_string", "first")
                                put("new_string", "FIRST")
                                put("replace_all", false)
                            })
                            add(buildJsonObject {
                                put("old_string", "second")
                                put("new_string", "SECOND")
                                put("replace_all", true)
                            })
                        })
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then
        assertIs<AssistantMessage>(result)
        val multiEditTool = result.content[0]
        assertIs<MultiEditToolUse>(multiEditTool)
        assertEquals("multiedit_123", multiEditTool.id)
        assertEquals(ToolType.MULTI_EDIT, multiEditTool.toolType)
        assertEquals("/test/complex.txt", multiEditTool.filePath)
        assertEquals(2, multiEditTool.edits.size)

        val firstEdit = multiEditTool.edits[0]
        assertEquals("first", firstEdit.oldString)
        assertEquals("FIRST", firstEdit.newString)
        assertEquals(false, firstEdit.replaceAll)

        val secondEdit = multiEditTool.edits[1]
        assertEquals("second", secondEdit.oldString)
        assertEquals("SECOND", secondEdit.newString)
        assertEquals(true, secondEdit.replaceAll)
    }

    @Test
    fun `should preserve backward compatibility with old UI code`() {
        // Given - 测试向后兼容性
        val messageJson = buildJsonObject {
            put("type", "assistant")
            put("content", buildJsonArray {
                add(buildJsonObject {
                    put("type", "tool_use")
                    put("id", "bash_123")
                    put("name", "Bash")
                    put("input", buildJsonObject {
                        put("command", "echo hello")
                    })
                })
            })
            put("model", "claude-3-5-sonnet-20241022")
        }

        // When
        val result = parser.parseMessage(messageJson)

        // Then - 验证新类型也实现了ContentBlock接口
        assertIs<AssistantMessage>(result)
        val toolUse = result.content[0]

        // 新的具体类型检查
        assertIs<BashToolUse>(toolUse)
        assertEquals("echo hello", toolUse.command)

        // 旧的通用接口仍然有效
        assertIs<ContentBlock>(toolUse)
        assertIs<SpecificToolUse>(toolUse)

        // 通过通用接口访问基本属性
        assertEquals("bash_123", toolUse.id)
        assertEquals(ToolType.BASH, toolUse.toolType)

        // 原始参数仍然可用
        val originalInput = toolUse.originalParameters.jsonObject
        assertEquals("echo hello", originalInput["command"]?.jsonPrimitive?.content)
    }
}
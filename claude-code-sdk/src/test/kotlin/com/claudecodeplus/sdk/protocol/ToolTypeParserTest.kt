package com.claudecodeplus.sdk.protocol

import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ToolTypeParserTest {

    @Test
    fun `should parse Bash tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("command", "ls -la")
            put("description", "List files")
            put("timeout", 5000)
            put("run_in_background", true)
        }
        val toolUseBlock = ToolUseBlock(
            id = "bash_123",
            name = "Bash",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<BashToolUse>(result)
        assertEquals("bash_123", result.id)
        assertEquals(ToolType.BASH, result.toolType)
        assertEquals("ls -la", result.command)
        assertEquals("List files", result.description)
        assertEquals(5000L, result.timeout)
        assertTrue(result.runInBackground)
    }

    @Test
    fun `should parse Edit tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("file_path", "/path/to/file.txt")
            put("old_string", "old content")
            put("new_string", "new content")
            put("replace_all", true)
        }
        val toolUseBlock = ToolUseBlock(
            id = "edit_123",
            name = "Edit",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<EditToolUse>(result)
        assertEquals("edit_123", result.id)
        assertEquals(ToolType.EDIT, result.toolType)
        assertEquals("/path/to/file.txt", result.filePath)
        assertEquals("old content", result.oldString)
        assertEquals("new content", result.newString)
        assertTrue(result.replaceAll)
    }

    @Test
    fun `should parse MultiEdit tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("file_path", "/path/to/file.txt")
            put("edits", buildJsonArray {
                add(buildJsonObject {
                    put("old_string", "old1")
                    put("new_string", "new1")
                    put("replace_all", false)
                })
                add(buildJsonObject {
                    put("old_string", "old2")
                    put("new_string", "new2")
                    put("replace_all", true)
                })
            })
        }
        val toolUseBlock = ToolUseBlock(
            id = "multiedit_123",
            name = "MultiEdit",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<MultiEditToolUse>(result)
        assertEquals("multiedit_123", result.id)
        assertEquals(ToolType.MULTI_EDIT, result.toolType)
        assertEquals("/path/to/file.txt", result.filePath)
        assertEquals(2, result.edits.size)

        val firstEdit = result.edits[0]
        assertEquals("old1", firstEdit.oldString)
        assertEquals("new1", firstEdit.newString)
        assertEquals(false, firstEdit.replaceAll)

        val secondEdit = result.edits[1]
        assertEquals("old2", secondEdit.oldString)
        assertEquals("new2", secondEdit.newString)
        assertTrue(secondEdit.replaceAll)
    }

    @Test
    fun `should parse Read tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("file_path", "/path/to/file.txt")
            put("offset", 100)
            put("limit", 50)
        }
        val toolUseBlock = ToolUseBlock(
            id = "read_123",
            name = "Read",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<ReadToolUse>(result)
        assertEquals("read_123", result.id)
        assertEquals(ToolType.READ, result.toolType)
        assertEquals("/path/to/file.txt", result.filePath)
        assertEquals(100, result.offset)
        assertEquals(50, result.limit)
    }

    @Test
    fun `should parse Write tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("file_path", "/path/to/file.txt")
            put("content", "Hello, World!")
        }
        val toolUseBlock = ToolUseBlock(
            id = "write_123",
            name = "Write",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<WriteToolUse>(result)
        assertEquals("write_123", result.id)
        assertEquals(ToolType.WRITE, result.toolType)
        assertEquals("/path/to/file.txt", result.filePath)
        assertEquals("Hello, World!", result.content)
    }

    @Test
    fun `should parse TodoWrite tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("todos", buildJsonArray {
                add(buildJsonObject {
                    put("content", "Task 1")
                    put("status", "pending")
                    put("activeForm", "Task 1 in progress")
                })
                add(buildJsonObject {
                    put("content", "Task 2")
                    put("status", "completed")
                    put("activeForm", "Task 2 completed")
                })
            })
        }
        val toolUseBlock = ToolUseBlock(
            id = "todo_123",
            name = "TodoWrite",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<TodoWriteToolUse>(result)
        assertEquals("todo_123", result.id)
        assertEquals(ToolType.TODO_WRITE, result.toolType)
        assertEquals(2, result.todos.size)

        val firstTodo = result.todos[0]
        assertEquals("Task 1", firstTodo.content)
        assertEquals("pending", firstTodo.status)
        assertEquals("Task 1 in progress", firstTodo.activeForm)

        val secondTodo = result.todos[1]
        assertEquals("Task 2", secondTodo.content)
        assertEquals("completed", secondTodo.status)
        assertEquals("Task 2 completed", secondTodo.activeForm)
    }

    @Test
    fun `should parse MCP tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("server_arg", "value1")
            put("function_arg", 42)
            put("complex_arg", buildJsonObject {
                put("nested", "value")
            })
        }
        val toolUseBlock = ToolUseBlock(
            id = "mcp_123",
            name = "mcp__myserver__myfunction",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<McpToolUse>(result)
        assertEquals("mcp_123", result.id)
        assertEquals(ToolType.MCP_TOOL, result.toolType)
        assertEquals("mcp__myserver__myfunction", result.fullToolName)
        assertEquals("myserver", result.serverName)
        assertEquals("myfunction", result.functionName)
        assertEquals("value1", result.parameters["server_arg"])
        assertEquals(42, result.parameters["function_arg"])
        assertTrue(result.parameters["complex_arg"] is Map<*, *>)
    }

    @Test
    fun `should parse unknown tool correctly`() {
        // Given
        val jsonInput = buildJsonObject {
            put("unknown_param", "value")
            put("another_param", 123)
        }
        val toolUseBlock = ToolUseBlock(
            id = "unknown_123",
            name = "UnknownTool",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<UnknownToolUse>(result)
        assertEquals("unknown_123", result.id)
        assertEquals(ToolType.UNKNOWN, result.toolType)
        assertEquals("UnknownTool", result.toolName)
        assertEquals("value", result.parameters["unknown_param"])
        assertEquals(123, result.parameters["another_param"])
    }

    @Test
    fun `should parse Grep tool with all options`() {
        // Given
        val jsonInput = buildJsonObject {
            put("pattern", "error")
            put("path", "/logs")
            put("output_mode", "content")
            put("glob", "*.log")
            put("type", "log")
            put("-i", true)
            put("-n", true)
            put("-B", 2)
            put("-A", 3)
            put("head_limit", 100)
        }
        val toolUseBlock = ToolUseBlock(
            id = "grep_123",
            name = "Grep",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertIs<GrepToolUse>(result)
        assertEquals("grep_123", result.id)
        assertEquals(ToolType.GREP, result.toolType)
        assertEquals("error", result.pattern)
        assertEquals("/logs", result.path)
        assertEquals("content", result.outputMode)
        assertEquals("*.log", result.glob)
        assertEquals("log", result.type)
        assertTrue(result.caseInsensitive)
        assertTrue(result.showLineNumbers)
        assertEquals(2, result.contextBefore)
        assertEquals(3, result.contextAfter)
        assertEquals(100, result.headLimit)
    }

    @Test
    fun `should handle missing required parameters gracefully`() {
        // Given - Bash tool without required command
        val jsonInput = buildJsonObject {
            put("description", "Missing command")
        }
        val toolUseBlock = ToolUseBlock(
            id = "bash_123",
            name = "Bash",
            input = jsonInput
        )

        // When/Then - Should return UnknownToolUse instead of throwing
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        assertIs<UnknownToolUse>(result)
        assertEquals("Bash", result.toolName)
    }

    @Test
    fun `should test getTypedParameters method`() {
        // Given
        val jsonInput = buildJsonObject {
            put("command", "echo hello")
            put("description", "Test command")
        }
        val toolUseBlock = ToolUseBlock(
            id = "bash_123",
            name = "Bash",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock) as BashToolUse
        val typedParams = result.getTypedParameters()

        // Then
        assertEquals("echo hello", typedParams["command"])
        assertEquals("Test command", typedParams["description"])
        assertEquals(false, typedParams["run_in_background"])
    }

    @Test
    fun `should preserve original parameters`() {
        // Given
        val jsonInput = buildJsonObject {
            put("file_path", "/test.txt")
            put("content", "test content")
        }
        val toolUseBlock = ToolUseBlock(
            id = "write_123",
            name = "Write",
            input = jsonInput
        )

        // When
        val result = ToolTypeParser.parseToolUseBlock(toolUseBlock)

        // Then
        assertEquals(jsonInput, result.originalParameters)
    }

    @Test
    fun `should handle ToolType enum correctly`() {
        assertEquals(ToolType.BASH, ToolType.fromToolName("Bash"))
        assertEquals(ToolType.MCP_TOOL, ToolType.fromToolName("mcp__server__function"))
        assertEquals(ToolType.UNKNOWN, ToolType.fromToolName("NonExistentTool"))
    }
}
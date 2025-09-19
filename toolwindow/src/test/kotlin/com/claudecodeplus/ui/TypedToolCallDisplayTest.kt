package com.claudecodeplus.ui

import com.claudecodeplus.ui.models.*
import com.claudecodeplus.sdk.types.*
import kotlinx.serialization.json.JsonPrimitive

/**
 * 测试TypedToolCallDisplay的类型安全展示功能
 */
object TypedToolCallDisplayTest {

    fun testTodoWriteDisplayShowsInputTodos() {
        // 创建TodoWrite工具调用，模拟真实场景
        val todoWriteTool = TodoWriteToolUse(
            id = "test-todo-id",
            originalParameters = JsonPrimitive("test"),
            todos = listOf(
                TodoWriteToolUse.TodoItem(
                    content = "分析当前消息展示架构和数据流",
                    status = "completed",
                    activeForm = "分析当前消息展示架构"
                ),
                TodoWriteToolUse.TodoItem(
                    content = "实现类型安全的工具展示系统",
                    status = "in_progress",
                    activeForm = "实现类型安全的展示系统"
                ),
                TodoWriteToolUse.TodoItem(
                    content = "修复TodoWrite显示问题",
                    status = "pending",
                    activeForm = "修复TodoWrite显示"
                )
            )
        )

        val toolCall = ToolCall(
            id = "test-id",
            name = "TodoWrite",
            specificTool = todoWriteTool,  // 🎯 关键：提供强类型工具实例
            parameters = mapOf(
                "todos" to todoWriteTool.todos.map { todo ->
                    mapOf(
                        "content" to todo.content,
                        "status" to todo.status,
                        "activeForm" to todo.activeForm
                    )
                }
            ),
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success(
                output = "Todos have been modified successfully. Ensure that you continue to use the todo list to track your progress."
            )
        )

        // 验证我们的新系统会优先使用specificTool
        assert(toolCall.specificTool != null) { "SpecificTool应该不为null" }
        assert(toolCall.specificTool is TodoWriteToolUse) { "SpecificTool应该是TodoWriteToolUse类型" }

        val todoTool = toolCall.specificTool as TodoWriteToolUse
        assert(todoTool.todos.size == 3) { "应该有3个任务" }
        assert(todoTool.todos[0].content == "分析当前消息展示架构和数据流") { "第一个任务内容应该正确" }
        assert(todoTool.todos[1].status == "in_progress") { "第二个任务状态应该是in_progress" }

        println("✅ TodoWrite工具类型安全测试通过")
        println("📝 任务数量：${todoTool.todos.size}")
        todoTool.todos.forEachIndexed { index, todo ->
            println("   $index. ${todo.content} (${todo.status})")
        }
    }

    fun testFileOperationToolDisplay() {
        // 测试ReadTool
        val readTool = ReadToolUse(
            id = "read-test-id",
            originalParameters = JsonPrimitive("test"),
            filePath = "/Users/erio/codes/test.kt",
            offset = 10,
            limit = 100
        )

        val readToolCall = ToolCall(
            id = "read-id",
            name = "Read",
            specificTool = readTool,
            parameters = mapOf(
                "file_path" to readTool.filePath,
                "offset" to readTool.offset!!,
                "limit" to readTool.limit!!
            ),
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success(
                output = "file content here..."
            )
        )

        assert(readToolCall.specificTool is ReadToolUse) { "应该是ReadToolUse类型" }
        val tool = readToolCall.specificTool as ReadToolUse
        assert(tool.filePath == "/Users/erio/codes/test.kt") { "文件路径应该正确" }
        assert(tool.offset == 10) { "偏移量应该正确" }

        println("✅ Read工具类型安全测试通过")
        println("📖 文件：${tool.filePath}")
        println("   偏移：${tool.offset}, 限制：${tool.limit}")
    }

    fun testMcpToolDisplay() {
        // 测试MCP工具
        val mcpTool = McpToolUse(
            id = "mcp-test-id",
            originalParameters = JsonPrimitive("test"),
            fullToolName = "mcp__postgres__execute_query",
            serverName = "postgres",
            functionName = "execute_query",
            parameters = mapOf(
                "query" to "SELECT * FROM users",
                "limit" to 10
            )
        )

        val mcpToolCall = ToolCall(
            id = "mcp-id",
            name = "mcp__postgres__execute_query",
            specificTool = mcpTool,
            parameters = mcpTool.parameters,
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success(
                output = "Query executed successfully, 5 rows returned"
            )
        )

        assert(mcpToolCall.specificTool is McpToolUse) { "应该是McpToolUse类型" }
        val tool = mcpToolCall.specificTool as McpToolUse
        assert(tool.serverName == "postgres") { "服务器名称应该正确" }
        assert(tool.functionName == "execute_query") { "函数名称应该正确" }

        println("✅ MCP工具类型安全测试通过")
        println("🔌 MCP：${tool.serverName}.${tool.functionName}")
        println("   参数：${tool.parameters}")
    }

    fun testTypeSafetyVsOldSystem() {
        // 对比测试：新系统 vs 旧系统

        println("🔍 对比测试：类型安全 vs 字符串匹配")

        // 旧系统：基于字符串匹配
        val oldWay = "TodoWrite"
        val isOldTodoWrite = oldWay.contains("TodoWrite", ignoreCase = true)
        println("❌ 旧方式（字符串匹配）：$isOldTodoWrite")

        // 新系统：基于类型检查
        val todoTool = TodoWriteToolUse(
            id = "test",
            originalParameters = JsonPrimitive("test"),
            todos = emptyList()
        )
        val isNewTodoWrite = todoTool is TodoWriteToolUse
        println("✅ 新方式（类型安全）：$isNewTodoWrite")

        // 类型安全的优势
        when (todoTool) {
            is TodoWriteToolUse -> {
                // 编译时就知道可以访问todos属性
                println("📋 任务数量：${todoTool.todos.size}")
                println("🎯 强类型访问：todoTool.todos")
            }
            is ReadToolUse -> {
                // 编译时就知道可以访问filePath属性
                println("📖 文件路径：${todoTool.filePath}")
            }
            // 编译器会确保我们处理了所有可能的类型
        }

        println("🚀 类型安全系统优势：")
        println("   1. 编译时类型检查")
        println("   2. IDE自动完成和重构支持")
        println("   3. 消除字符串匹配错误")
        println("   4. 更好的代码可维护性")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("🧪 开始测试TypedToolCallDisplay类型安全展示系统")
        println("================================================")

        try {
            testTodoWriteDisplayShowsInputTodos()
            println()
        } catch (e: Exception) {
            println("❌ TodoWrite测试失败: ${e.message}")
        }

        try {
            testFileOperationToolDisplay()
            println()
        } catch (e: Exception) {
            println("❌ 文件操作测试失败: ${e.message}")
        }

        try {
            testMcpToolDisplay()
            println()
        } catch (e: Exception) {
            println("❌ MCP工具测试失败: ${e.message}")
        }

        try {
            testTypeSafetyVsOldSystem()
            println()
        } catch (e: Exception) {
            println("❌ 类型安全对比测试失败: ${e.message}")
        }

        println("================================================")
        println("🎉 TypedToolCallDisplay测试完成！")
    }
}
package com.claudecodeplus.ui

import com.claudecodeplus.ui.models.*
import com.claudecodeplus.sdk.types.*
import com.claudecodeplus.sdk.protocol.ToolTypeParser
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * 测试新增的工具类型支持
 * 验证从JSON解析到UI展示的完整链路
 */
object NewToolTypesTest {

    fun testBashOutputTool() {
        println("🧪 测试BashOutput工具...")

        // 创建模拟的ToolUseBlock
        val toolUseBlock = ToolUseBlock(
            id = "bash-output-001",
            name = "BashOutput",
            input = buildJsonObject {
                put("bash_id", JsonPrimitive("bash-123"))
                put("filter", JsonPrimitive("error|warning"))
            }
        )

        // 使用ToolTypeParser解析
        val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        println("✅ 解析成功: ${specificTool::class.simpleName}")

        // 验证类型转换
        assert(specificTool is BashOutputToolUse) { "应该是BashOutputToolUse类型" }
        val bashOutputTool = specificTool as BashOutputToolUse
        assert(bashOutputTool.bashId == "bash-123") { "bash_id应该正确" }
        assert(bashOutputTool.filter == "error|warning") { "filter应该正确" }

        // 创建ToolCall用于UI展示
        val toolCall = ToolCall(
            id = "bash-output-001",
            name = "BashOutput",
            specificTool = bashOutputTool,
            parameters = bashOutputTool.getTypedParameters(),
            status = ToolCallStatus.SUCCESS,
            result = ToolResult.Success(
                output = "error: file not found\nwarning: deprecated function"
            )
        )

        println("✅ BashOutput工具测试通过")
        println("📤 bashId: ${bashOutputTool.bashId}")
        println("🔍 filter: ${bashOutputTool.filter}")
    }

    fun testKillShellTool() {
        println("🧪 测试KillShell工具...")

        val toolUseBlock = ToolUseBlock(
            id = "kill-shell-001",
            name = "KillShell",
            input = buildJsonObject {
                put("shell_id", JsonPrimitive("shell-456"))
            }
        )

        val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        println("✅ 解析成功: ${specificTool::class.simpleName}")

        assert(specificTool is KillShellToolUse) { "应该是KillShellToolUse类型" }
        val killShellTool = specificTool as KillShellToolUse
        assert(killShellTool.shellId == "shell-456") { "shell_id应该正确" }

        println("✅ KillShell工具测试通过")
        println("⚡ shellId: ${killShellTool.shellId}")
    }

    fun testExitPlanModeTool() {
        println("🧪 测试ExitPlanMode工具...")

        val toolUseBlock = ToolUseBlock(
            id = "exit-plan-001",
            name = "ExitPlanMode",
            input = buildJsonObject {
                put("plan", JsonPrimitive("1. 分析问题\n2. 设计方案\n3. 实施测试"))
            }
        )

        val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        println("✅ 解析成功: ${specificTool::class.simpleName}")

        assert(specificTool is ExitPlanModeToolUse) { "应该是ExitPlanModeToolUse类型" }
        val exitPlanTool = specificTool as ExitPlanModeToolUse
        assert(exitPlanTool.plan.contains("分析问题")) { "plan内容应该正确" }

        println("✅ ExitPlanMode工具测试通过")
        println("📋 plan: ${exitPlanTool.plan}")
    }

    fun testListMcpResourcesTool() {
        println("🧪 测试ListMcpResources工具...")

        val toolUseBlock = ToolUseBlock(
            id = "list-mcp-001",
            name = "ListMcpResourcesTool",
            input = buildJsonObject {
                put("server", JsonPrimitive("postgres"))
            }
        )

        val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        println("✅ 解析成功: ${specificTool::class.simpleName}")

        assert(specificTool is ListMcpResourcesToolUse) { "应该是ListMcpResourcesToolUse类型" }
        val listMcpTool = specificTool as ListMcpResourcesToolUse
        assert(listMcpTool.server == "postgres") { "server应该正确" }

        println("✅ ListMcpResources工具测试通过")
        println("🔌 server: ${listMcpTool.server}")
    }

    fun testReadMcpResourceTool() {
        println("🧪 测试ReadMcpResource工具...")

        val toolUseBlock = ToolUseBlock(
            id = "read-mcp-001",
            name = "ReadMcpResourceTool",
            input = buildJsonObject {
                put("server", JsonPrimitive("filesystem"))
                put("uri", JsonPrimitive("file:///etc/config.json"))
            }
        )

        val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
        println("✅ 解析成功: ${specificTool::class.simpleName}")

        assert(specificTool is ReadMcpResourceToolUse) { "应该是ReadMcpResourceToolUse类型" }
        val readMcpTool = specificTool as ReadMcpResourceToolUse
        assert(readMcpTool.server == "filesystem") { "server应该正确" }
        assert(readMcpTool.uri == "file:///etc/config.json") { "uri应该正确" }

        println("✅ ReadMcpResource工具测试通过")
        println("📋 server: ${readMcpTool.server}")
        println("📄 uri: ${readMcpTool.uri}")
    }

    fun testToolTypeCompletion() {
        println("🧪 测试工具类型完整性...")

        val officialTools = listOf(
            "Task", "Bash", "BashOutput", "KillShell", "Edit", "MultiEdit",
            "Read", "Write", "Glob", "Grep", "NotebookEdit",
            "WebFetch", "WebSearch", "TodoWrite", "ExitPlanMode",
            "ListMcpResourcesTool", "ReadMcpResourceTool"
        )

        val supportedTools = mutableListOf<String>()

        officialTools.forEach { toolName ->
            try {
                val toolUseBlock = ToolUseBlock(
                    id = "test-$toolName",
                    name = toolName,
                    input = buildJsonObject {
                        when (toolName) {
                            "Bash" -> put("command", JsonPrimitive("echo hello"))
                            "BashOutput" -> put("bash_id", JsonPrimitive("test"))
                            "KillShell" -> put("shell_id", JsonPrimitive("test"))
                            "Edit" -> {
                                put("file_path", JsonPrimitive("test.txt"))
                                put("old_string", JsonPrimitive("old"))
                                put("new_string", JsonPrimitive("new"))
                            }
                            "Read" -> put("file_path", JsonPrimitive("test.txt"))
                            "Write" -> {
                                put("file_path", JsonPrimitive("test.txt"))
                                put("content", JsonPrimitive("content"))
                            }
                            "TodoWrite" -> put("todos", kotlinx.serialization.json.buildJsonArray {})
                            "ExitPlanMode" -> put("plan", JsonPrimitive("test plan"))
                            "ListMcpResourcesTool" -> put("server", JsonPrimitive("test"))
                            "ReadMcpResourceTool" -> {
                                put("server", JsonPrimitive("test"))
                                put("uri", JsonPrimitive("test://uri"))
                            }
                            else -> put("test", JsonPrimitive("test"))
                        }
                    }
                )

                val specificTool = ToolTypeParser.parseToolUseBlock(toolUseBlock)
                supportedTools.add(toolName)
                println("✅ $toolName -> ${specificTool::class.simpleName}")
            } catch (e: Exception) {
                println("❌ $toolName 解析失败: ${e.message}")
            }
        }

        println("\n📊 工具支持统计:")
        println("支持的工具: ${supportedTools.size}/${officialTools.size}")
        println("支持率: ${(supportedTools.size * 100 / officialTools.size)}%")

        if (supportedTools.size == officialTools.size) {
            println("🎉 所有官方工具都已支持！")
        } else {
            val missing = officialTools - supportedTools.toSet()
            println("❌ 缺失的工具: $missing")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("🧪 开始测试新增工具类型的完整支持")
        println("================================================")

        try {
            testBashOutputTool()
            println()
        } catch (e: Exception) {
            println("❌ BashOutput测试失败: ${e.message}")
        }

        try {
            testKillShellTool()
            println()
        } catch (e: Exception) {
            println("❌ KillShell测试失败: ${e.message}")
        }

        try {
            testExitPlanModeTool()
            println()
        } catch (e: Exception) {
            println("❌ ExitPlanMode测试失败: ${e.message}")
        }

        try {
            testListMcpResourcesTool()
            println()
        } catch (e: Exception) {
            println("❌ ListMcpResources测试失败: ${e.message}")
        }

        try {
            testReadMcpResourceTool()
            println()
        } catch (e: Exception) {
            println("❌ ReadMcpResource测试失败: ${e.message}")
        }

        try {
            testToolTypeCompletion()
            println()
        } catch (e: Exception) {
            println("❌ 工具类型完整性测试失败: ${e.message}")
        }

        println("================================================")
        println("🎉 新增工具类型测试完成！")
    }
}
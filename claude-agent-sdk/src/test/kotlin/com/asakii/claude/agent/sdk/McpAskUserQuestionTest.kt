package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.mcp.McpServerBase
import com.asakii.claude.agent.sdk.mcp.ParameterInfo
import com.asakii.claude.agent.sdk.mcp.ParameterType
import com.asakii.claude.agent.sdk.mcp.annotations.McpServerConfig
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Path

/**
 * 测试自定义 MCP AskUserQuestion 工具
 *
 * 验证通过 MCP 注册的 AskUserQuestion 工具能否被 Claude 识别并调用
 *
 * 运行方式:
 * ./gradlew :claude-agent-sdk:runMcpAskUserQuestionTest
 */

@McpServerConfig(
    name = "user_interaction",
    version = "1.0.0",
    description = "用户交互工具服务器"
)
class TestUserInteractionMcpServer : McpServerBase() {

    override suspend fun onInitialize() {
        registerTool(
            name = "AskUserQuestion",
            description = "向用户询问问题并获取选择。使用此工具在需要用户输入或确认时与用户交互。",
            parameterSchema = mapOf(
                "questions" to ParameterInfo(
                    type = ParameterType.ARRAY,
                    description = "问题列表，每个问题包含 question, header, options, multiSelect 字段"
                )
            )
        ) { arguments ->
            println("\n🎯🎯🎯 MCP AskUserQuestion 工具被调用! 🎯🎯🎯")
            println("参数: $arguments")

            // 模拟用户回答
            mapOf(
                "你想要什么类型的配置文件格式？" to "JSON",
                "配置文件应该放在哪个目录？" to "config目录"
            ).toString()
        }

        println("✅ [TestUserInteractionMcpServer] 已注册 AskUserQuestion 工具")
    }
}

fun main() = runBlocking {
    println("=".repeat(70))
    println("🔬 MCP AskUserQuestion 工具测试")
    println("=".repeat(70))

    val workDir = Path.of("C:\\Users\\16790\\IdeaProjects\\claude-code-plus")

    // 创建 MCP Server 实例
    val mcpServer = TestUserInteractionMcpServer()

    val options = ClaudeAgentOptions(
        cwd = workDir,
        permissionMode = PermissionMode.DEFAULT,
        includePartialMessages = true,
        maxTurns = 5,
        maxThinkingTokens = 2000,
        // 注册 MCP Server
        mcpServers = mapOf(
            "user_interaction" to mcpServer
        )
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("\n📡 连接到 Claude...")
        client.connect()
        println("✅ 连接成功\n")

        // 发送查询，让 AI 使用 AskUserQuestion
        val prompt = """
你需要帮我创建一个配置文件。请使用 AskUserQuestion 工具（来自 user_interaction MCP 服务器）询问我：

1. 我想要什么类型的配置文件格式？选项：JSON、YAML、TOML
2. 配置文件应该放在哪个目录？选项：根目录、config目录、.config目录

请务必使用工具来询问我。
        """.trimIndent()

        println("📤 发送查询:\n$prompt\n")
        println("-".repeat(70))

        client.query(prompt)

        var mcpToolCalled = false

        client.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        println("\n[SystemMessage] 初始化完成")
                        // 打印可用工具列表
                        println("📋 检查是否包含 MCP 工具...")
                    }
                }

                is StreamEvent -> {
                    val eventJson = message.event
                    if (eventJson is JsonObject) {
                        val eventType = eventJson["type"]?.jsonPrimitive?.contentOrNull

                        if (eventType == "content_block_start") {
                            val contentBlock = eventJson["content_block"]?.jsonObject
                            val blockType = contentBlock?.get("type")?.jsonPrimitive?.contentOrNull

                            if (blockType == "tool_use") {
                                val toolName = contentBlock?.get("name")?.jsonPrimitive?.contentOrNull
                                println("\n🔧 [ToolUse] Tool: $toolName")

                                if (toolName == "AskUserQuestion" || toolName?.contains("AskUserQuestion") == true) {
                                    mcpToolCalled = true
                                    println("  ⭐ MCP AskUserQuestion 工具被调用!")
                                }
                            }
                        }
                    }
                }

                is AssistantMessage -> {
                    println("\n[AssistantMessage]")
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("📝 ${block.text.take(200)}...")
                            is ToolUseBlock -> {
                                println("🔧 Tool: ${block.name}")
                                if (block.name == "AskUserQuestion" || block.name.contains("AskUserQuestion")) {
                                    mcpToolCalled = true
                                }
                            }
                            else -> {}
                        }
                    }
                }

                is ResultMessage -> {
                    println("\n[ResultMessage] isError=${message.isError}")
                }

                else -> {}
            }
        }

        println("\n" + "=".repeat(70))
        if (mcpToolCalled) {
            println("🎉 成功! MCP AskUserQuestion 工具被识别并调用!")
        } else {
            println("❌ 失败! MCP AskUserQuestion 工具未被调用")
            println("   可能原因：Claude 不知道这个 MCP 工具的存在")
        }
        println("=".repeat(70))

    } catch (e: Exception) {
        println("\n❌ 错误: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("\n🔌 已断开连接")
    }
}

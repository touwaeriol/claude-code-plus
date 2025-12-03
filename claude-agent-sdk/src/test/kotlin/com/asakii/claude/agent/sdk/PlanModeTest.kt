package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Path

/**
 * 测试 Plan 模式的交互方式
 *
 * 运行方式: 在项目根目录执行
 * ./gradlew :claude-agent-sdk:runPlanModeTest
 */
fun main() = runBlocking {
    println("=" .repeat(60))
    println("🔬 Plan 模式测试")
    println("=" .repeat(60))

    val workDir = Path.of("C:\\Users\\16790\\IdeaProjects\\claude-code-plus")

    val options = ClaudeAgentOptions(
        cwd = workDir,
        permissionMode = PermissionMode.PLAN,  // 使用 PLAN 模式
        includePartialMessages = true,  // 启用流式消息以查看完整交互
        maxTurns = 5,  // 限制轮次
        verbose = true,
        maxThinkingTokens = 4000
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("\n📡 连接到 Claude...")
        client.connect()
        println("✅ 连接成功\n")

        // 发送一个需要计划的任务
        val prompt = """
请帮我在这个项目中实现一个新功能：添加一个用户偏好设置页面。
这个页面需要：
1. 显示当前用户的设置项
2. 允许用户修改主题（亮色/暗色）
3. 允许用户修改语言设置

请先给我一个实现计划。
        """.trimIndent()

        println("📤 发送查询:\n$prompt\n")
        println("-".repeat(60))

        client.query(prompt)

        var messageCount = 0
        var lastEventType: String? = null

        client.receiveResponse().collect { message ->
            messageCount++

            when (message) {
                is StreamEvent -> {
                    // 流式事件 - 显示详细信息
                    // event 是 JsonElement，需要解析
                    val eventJson = message.event
                    val eventType = if (eventJson is JsonObject) {
                        eventJson["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    } else {
                        eventJson.toString()
                    }

                    if (eventType != lastEventType) {
                        println("\n[StreamEvent: $eventType]")
                        lastEventType = eventType
                    }

                    // 打印原始数据以分析结构
                    println("  Event: ${eventJson.toString().take(500)}")
                }

                is AssistantMessage -> {
                    println("\n" + "=".repeat(60))
                    println("[AssistantMessage]")
                    println("=".repeat(60))
                    println("  Model: ${message.model}")

                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> {
                                println("\n📝 [TextBlock]:")
                                println(block.text)
                            }
                            is ThinkingBlock -> {
                                println("\n🤔 [ThinkingBlock]:")
                                println(block.thinking.take(500) + if (block.thinking.length > 500) "..." else "")
                            }
                            is ToolUseBlock -> {
                                println("\n🔧 [ToolUseBlock]:")
                                println("  Tool: ${block.name}")
                                println("  ID: ${block.id}")
                                println("  Input: ${block.input}")
                            }
                            else -> {
                                println("\n❓ [Unknown Block]: ${block::class.simpleName}")
                            }
                        }
                    }
                }

                is ResultMessage -> {
                    println("\n" + "=".repeat(60))
                    println("[ResultMessage]")
                    println("=".repeat(60))
                    println("  isError: ${message.isError}")
                    println("  numTurns: ${message.numTurns}")
                    println("  durationMs: ${message.durationMs}")
                    println("  totalCostUsd: ${message.totalCostUsd}")
                    println("  sessionId: ${message.sessionId}")
                    println("  result: ${message.result?.take(200) ?: "null"}")
                }

                is UserMessage -> {
                    println("\n[UserMessage]")
                    println("  Content: ${message.content.toString().take(200)}...")
                }

                is SystemMessage -> {
                    println("\n[SystemMessage]")
                    println("  Subtype: ${message.subtype}")
                    println("  Data: ${message.data.toString().take(200)}")
                }

                else -> {
                    println("\n[${message::class.simpleName}]")
                    println("  $message")
                }
            }
        }

        println("\n" + "=".repeat(60))
        println("✅ 测试完成，共收到 $messageCount 条消息")
        println("=".repeat(60))

    } catch (e: Exception) {
        println("\n❌ 错误: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("\n🔌 已断开连接")
    }
}

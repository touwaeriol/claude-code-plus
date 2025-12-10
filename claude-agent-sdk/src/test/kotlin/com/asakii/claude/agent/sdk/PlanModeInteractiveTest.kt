package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Path

/**
 * 测试 Plan 模式的用户交互功能
 *
 * 包括：
 * - ExitPlanMode 调用
 * - 用户选择/确认
 * - EnterPlanMode 调用
 *
 * 运行方式:
 * ./gradlew :claude-agent-sdk:runPlanModeInteractiveTest
 */
fun main() = runBlocking {
    println("=" .repeat(70))
    println("🔬 Plan 模式交互测试 - 用户选择功能")
    println("=" .repeat(70))

    val workDir = Path.of("C:\\Users\\16790\\IdeaProjects\\claude-code-plus")

    val options = ClaudeAgentOptions(
        cwd = workDir,
        permissionMode = PermissionMode.PLAN,
        includePartialMessages = true,
        maxTurns = 10,
        verbose = true,
        maxThinkingTokens = 2000  // 减少思考以加快测试
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("\n📡 连接到 Claude...")
        client.connect()
        println("✅ 连接成功\n")

        // 发送一个简单的任务，让AI生成计划
        val prompt = """
创建一个简单的 hello.txt 文件，内容是 "Hello World"。
        """.trimIndent()

        println("📤 发送查询: $prompt\n")
        println("-".repeat(70))

        client.query(prompt)

        var messageCount = 0
        var exitPlanModeToolId: String? = null
        var planContent: String? = null

        client.receiveResponse().collect { message ->
            messageCount++

            when (message) {
                is StreamEvent -> {
                    val eventJson = message.event
                    val eventType = if (eventJson is JsonObject) {
                        eventJson["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
                    } else {
                        eventJson.toString()
                    }

                    // 只显示关键事件
                    when (eventType) {
                        "content_block_start" -> {
                            val contentBlock = eventJson.jsonObject["content_block"]
                            val blockType = contentBlock?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull
                            println("\n[StreamEvent: $eventType] blockType=$blockType")

                            // 检查是否是 tool_use
                            if (blockType == "tool_use") {
                                val toolName = contentBlock?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                                val toolId = contentBlock?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                                println("  🔧 Tool: $toolName (id: $toolId)")

                                if (toolName == "ExitPlanMode") {
                                    exitPlanModeToolId = toolId
                                    println("  ⚠️ 检测到 ExitPlanMode 工具调用！")
                                }
                            }
                        }
                        "content_block_stop" -> {
                            // 静默
                        }
                        "message_start", "message_stop", "message_delta" -> {
                            // 静默
                        }
                        "content_block_delta" -> {
                            val delta = eventJson.jsonObject["delta"]
                            val deltaType = delta?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull

                            // 只显示重要的 delta
                            when (deltaType) {
                                "input_json_delta" -> {
                                    val partialJson = delta?.jsonObject?.get("partial_json")?.jsonPrimitive?.contentOrNull ?: ""
                                    // 累积 plan 内容（简化处理）
                                    if (exitPlanModeToolId != null && partialJson.isNotEmpty()) {
                                        print(partialJson.take(50))
                                    }
                                }
                                "text_delta" -> {
                                    val text = delta?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull ?: ""
                                    print(text)
                                }
                            }
                        }
                    }
                }

                is AssistantMessage -> {
                    println("\n\n" + "=".repeat(70))
                    println("[AssistantMessage] model=${message.model}")
                    println("=".repeat(70))

                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> {
                                println("\n📝 [TextBlock]:")
                                println(block.text.take(500) + if (block.text.length > 500) "..." else "")
                            }
                            is ThinkingBlock -> {
                                println("\n🤔 [ThinkingBlock]: (${block.thinking.length} chars)")
                            }
                            is ToolUseBlock -> {
                                println("\n🔧 [ToolUseBlock]:")
                                println("  Tool: ${block.name}")
                                println("  ID: ${block.id}")

                                // 解析 input
                                val inputStr = block.input.toString()
                                if (block.name == "ExitPlanMode") {
                                    println("  ⭐ ExitPlanMode 调用!")
                                    // 尝试提取 plan 内容
                                    try {
                                        val inputObj = Json.parseToJsonElement(inputStr).jsonObject
                                        planContent = inputObj["plan"]?.jsonPrimitive?.contentOrNull
                                        println("  Plan 内容 (前200字符):")
                                        println("  ${planContent?.take(200) ?: "null"}...")
                                    } catch (e: Exception) {
                                        println("  Input: ${inputStr.take(200)}...")
                                    }
                                } else {
                                    println("  Input: ${inputStr.take(100)}...")
                                }
                            }
                            else -> {
                                println("\n❓ [${block::class.simpleName}]")
                            }
                        }
                    }
                }

                is SystemMessage -> {
                    println("\n[SystemMessage] subtype=${message.subtype}")

                    // 检查是否有用户交互相关的系统消息
                    val dataStr = message.data.toString()
                    if (dataStr.contains("permission") ||
                        dataStr.contains("approval") ||
                        dataStr.contains("confirm") ||
                        dataStr.contains("ask")) {
                        println("  ⚠️ 可能的用户交互: $dataStr")
                    } else {
                        println("  Data: ${dataStr.take(200)}...")
                    }
                }

                is ResultMessage -> {
                    println("\n" + "=".repeat(70))
                    println("[ResultMessage]")
                    println("=".repeat(70))
                    println("  isError: ${message.isError}")
                    println("  numTurns: ${message.numTurns}")
                    println("  durationMs: ${message.durationMs}")
                    println("  totalCostUsd: ${message.totalCostUsd}")
                    println("  result: ${message.result?.take(300) ?: "null"}")
                }

                is UserMessage -> {
                    println("\n[UserMessage]")
                    val contentStr = message.content.toString()

                    // 检查是否包含 tool_result
                    if (contentStr.contains("tool_result")) {
                        println("  Tool Result 消息")
                    } else {
                        println("  Content: ${contentStr.take(200)}...")
                    }
                }

                is CompactBoundaryMessage -> {
                    println("\n[CompactBoundaryMessage]")
                }

                is StatusSystemMessage -> {
                    println("\n[StatusSystemMessage]")
                }
            }
        }

        println("\n" + "=".repeat(70))
        println("✅ 测试完成，共收到 $messageCount 条消息")
        if (planContent != null) {
            println("\n📋 检测到的计划内容:")
            println(planContent?.take(500) ?: "null")
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

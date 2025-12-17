package com.asakii.claude.agent.sdk

import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Path

private val prettyJson = Json { prettyPrint = true }

/**
 * 测试 AskUserQuestion 工具调用
 *
 * 尝试让 AI 生成用户选择选项，观察实际行为
 *
 * 运行方式:
 * ./gradlew :claude-agent-sdk:runAskUserQuestionTest
 */
fun main() = runBlocking {
    println("=" .repeat(70))
    println("🔬 AskUserQuestion 工具测试")
    println("=" .repeat(70))

    val workDir = Path.of("C:\\Users\\16790\\IdeaProjects\\claude-code-plus")

    // 使用默认模式，让 AI 可以调用 AskUserQuestion
    val options = ClaudeAgentOptions(
        cwd = workDir,
        permissionMode = PermissionMode.DEFAULT,  // 使用默认模式
        includePartialMessages = true,
        maxTurns = 5,
        maxThinkingTokens = 2000
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        println("\n📡 连接到 Claude...")
        client.connect()
        println("✅ 连接成功\n")

        // 发送一个需要用户选择的提示
        val prompt = """
你需要帮我创建一个配置文件。但首先，请使用 AskUserQuestion 工具询问我以下问题：

1. 我想要什么类型的配置文件格式？选项：JSON、YAML、TOML
2. 配置文件应该放在哪个目录？选项：根目录、config目录、.config目录

请务必使用 AskUserQuestion 工具来询问我，不要直接假设答案。
        """.trimIndent()

        println("📤 发送查询:\n$prompt\n")
        println("-".repeat(70))

        client.query(prompt)

        var messageCount = 0
        var askUserQuestionFound = false
        var toolUseId: String? = null

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

                            if (blockType == "tool_use") {
                                val toolName = contentBlock?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                                val id = contentBlock?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
                                println("\n🔧 [ToolUse Start] Tool: $toolName, ID: $id")

                                if (toolName == "AskUserQuestion") {
                                    askUserQuestionFound = true
                                    toolUseId = id
                                    println("  ⭐ 找到 AskUserQuestion 工具调用!")
                                }
                            }
                        }
                        "content_block_delta" -> {
                            val delta = eventJson.jsonObject["delta"]
                            val deltaType = delta?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull

                            when (deltaType) {
                                "input_json_delta" -> {
                                    val partialJson = delta?.jsonObject?.get("partial_json")?.jsonPrimitive?.contentOrNull ?: ""
                                    if (askUserQuestionFound && partialJson.isNotEmpty()) {
                                        print(partialJson)
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
                    println("[AssistantMessage]")
                    println("=".repeat(70))

                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> {
                                println("\n📝 [TextBlock]:")
                                println(block.text)
                            }
                            is ThinkingBlock -> {
                                println("\n🤔 [ThinkingBlock]: (${block.thinking.length} chars)")
                            }
                            is ToolUseBlock -> {
                                println("\n🔧 [ToolUseBlock]:")
                                println("  Tool: ${block.name}")
                                println("  ID: ${block.id}")
                                println("  Input: ${block.input}")

                                if (block.name == "AskUserQuestion") {
                                    askUserQuestionFound = true
                                    toolUseId = block.id
                                    println("\n  ⭐⭐⭐ AskUserQuestion 工具被调用! ⭐⭐⭐")
                                    println("  完整 Input:")
                                    try {
                                        val formatted = prettyJson.encodeToString(
                                            JsonElement.serializer(),
                                            block.input
                                        )
                                        println(formatted)
                                    } catch (e: Exception) {
                                        println("  ${block.input}")
                                    }
                                }
                            }
                            else -> {
                                println("\n❓ [${block::class.simpleName}]")
                            }
                        }
                    }
                }

                is ResultMessage -> {
                    println("\n" + "=".repeat(70))
                    println("[ResultMessage]")
                    println("=".repeat(70))
                    println("  isError: ${message.isError}")
                    println("  numTurns: ${message.numTurns}")
                    println("  durationMs: ${message.durationMs}")
                    println("  result: ${message.result?.take(200) ?: "null"}")
                }

                is SystemMessage -> {
                    if (message.subtype == "init") {
                        println("\n[SystemMessage] 初始化完成")
                    }
                }

                else -> {}
            }
        }

        println("\n" + "=".repeat(70))
        println("✅ 测试完成，共收到 $messageCount 条消息")
        println("=".repeat(70))

        if (askUserQuestionFound) {
            println("\n🎉 成功触发 AskUserQuestion 工具!")
            println("   Tool Use ID: $toolUseId")
            println("\n📝 下一步：需要发送 tool_result 来响应这个工具调用")
        } else {
            println("\n⚠️ 未触发 AskUserQuestion 工具")
            println("   AI 可能直接给出了回答，或使用了其他方式")
        }

    } catch (e: Exception) {
        println("\n❌ 错误: ${e.message}")
        e.printStackTrace()
    } finally {
        client.disconnect()
        println("\n🔌 已断开连接")
    }
}

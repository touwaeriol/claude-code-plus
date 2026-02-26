package com.asakii.claude.agent.sdk.examples


import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * 测试切换到 Claude Sonnet 4.6 模型
 *
 * 验证：
 * 1. 使用 Sonnet 4 启动
 * 2. 切换到 Sonnet 4.6
 * 3. 询问 AI 当前模型
 */
fun main() = runBlocking {
    println("=== 切换到 Sonnet 4.6 测试 ===\n")

    val options = ClaudeAgentOptions(
        model = "claude-sonnet-4-20250514",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read"),
        maxTurns = 2,
        systemPrompt = "You are a helpful assistant."
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        // 连接
        println("📡 连接到 Claude...")
        client.connect()
        println("✅ 已连接\n")

        // ========== 第一阶段：Sonnet 4 ==========
        println("=" * 60)
        println("第一阶段：使用 Sonnet 4")
        println("=" * 60)

        println("\n🤖 初始模型: claude-sonnet-4-20250514")
        println("❓ 询问：你是什么模型？\n")

        client.query("请简单告诉我你是什么 Claude 模型，只要模型名称。")

        val sonnet4Response = StringBuilder()
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            sonnet4Response.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 查询完成 (${message.durationMs}ms)")
                    }
                }
                else -> {}
            }
        }

        val sonnet4Answer = sonnet4Response.toString().trim()
        println("\n📝 Sonnet 4 回答: \"$sonnet4Answer\"\n")

        // ========== 第二阶段：切换到 Sonnet 4.6 ==========
        println("=" * 60)
        println("第二阶段：切换到 Sonnet 4.6")
        println("=" * 60)

        println("\n🔄 执行模型切换...")
        client.setModel("claude-sonnet-4-6")
        println("✅ setModel() 调用完成")

        // 等待切换生效
        kotlinx.coroutines.delay(1000)

        println("\n🤖 目标模型: claude-sonnet-4-6")
        println("❓ 询问：你现在是什么模型？\n")

        client.query("请再次告诉我你现在是什么 Claude 模型，只要模型名称。")

        val sonnet45Response = StringBuilder()
        var systemInitModel = ""

        client.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    // 捕获系统初始化消息中的模型信息
                    if (message.subtype == "init") {
                        val data = message.data
                        if (data is Map<*, *>) {
                            systemInitModel = data["model"]?.toString() ?: ""
                            if (systemInitModel.isNotEmpty()) {
                                println("\n🔍 System Init 确认模型: $systemInitModel\n")
                            }
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            sonnet45Response.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 查询完成 (${message.durationMs}ms)")
                    }
                }
                else -> {}
            }
        }

        val sonnet45Answer = sonnet45Response.toString().trim()
        println("\n📝 Sonnet 4.6 回答: \"$sonnet45Answer\"\n")

        // ========== 结果对比 ==========
        println("=" * 60)
        println("结果对比")
        println("=" * 60)

        println("\n🔵 切换前 (Sonnet 4):")
        println("   回答: \"$sonnet4Answer\"")

        println("\n🟢 切换后 (Sonnet 4.6):")
        println("   System Init: $systemInitModel")
        println("   回答: \"$sonnet45Answer\"")

        // 分析结果
        println("\n📊 分析:")
        if (systemInitModel.contains("sonnet-4-6")) {
            println("   ✅ System Init 确认已切换到 Sonnet 4.6")
            println("   ✅ 模型切换成功！")
        } else if (systemInitModel.contains("sonnet-4")) {
            println("   ⚠️  System Init 显示: $systemInitModel")
            if (systemInitModel.contains("sonnet-4-6")) {
                println("   ✅ 已切换到 Sonnet 4.6")
            } else {
                println("   ❌ 可能未切换或切换失败")
            }
        } else {
            println("   ⚠️  System Init 模型: $systemInitModel")
            println("   💡 请检查日志确认切换状态")
        }

        if (sonnet45Answer.lowercase().contains("4.6") || sonnet45Answer.lowercase().contains("sonnet 4.6")) {
            println("   ✅ AI 回答明确提到了 Sonnet 4.6")
        } else if (!sonnet45Answer.startsWith("API Error")) {
            println("   💡 AI 回答: $sonnet45Answer")
        }

    } catch (e: Exception) {
        println("\n❌ 发生错误: ${e.message}")
        e.printStackTrace()
    } finally {
        println("\n📡 断开连接...")
        client.disconnect()
        println("✅ 已断开")
    }

    println("\n=== 测试完成 ===")
}

private operator fun String.times(n: Int): String = this.repeat(n)

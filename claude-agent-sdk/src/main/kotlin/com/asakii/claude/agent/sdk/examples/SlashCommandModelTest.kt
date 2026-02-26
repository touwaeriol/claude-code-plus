package com.asakii.claude.agent.sdk.examples


import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * 测试通过 /model 斜杠命令切换模型
 *
 * 验证：
 * 1. 使用 Sonnet 4.6 启动
 * 2. 通过 /model 命令切换回 Sonnet 4
 * 3. 对比 System Init 消息确认切换
 */
fun main() = runBlocking {
    println("=== 测试 /model 斜杠命令切换模型 ===\n")

    val options = ClaudeAgentOptions(
        model = "claude-sonnet-4-6",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read"),
        maxTurns = 3,
        systemPrompt = "You are a helpful assistant."
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        // 连接
        println("📡 连接到 Claude...")
        client.connect()
        println("✅ 已连接\n")

        // ========== 第一阶段：确认初始模型 ==========
        println("=" * 60)
        println("第一阶段：确认初始模型 (Sonnet 4.6)")
        println("=" * 60)

        println("\n🤖 初始配置: claude-sonnet-4-6")
        println("❓ 询问：你是什么模型？\n")

        client.query("请简单告诉我你是什么 Claude 模型，只要模型名称。")

        var initialModel = ""
        val initialResponse = StringBuilder()

        client.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data
                        if (data is Map<*, *>) {
                            initialModel = data["model"]?.toString() ?: ""
                            if (initialModel.isNotEmpty()) {
                                println("🔍 System Init 模型: $initialModel")
                            }
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            initialResponse.append(block.text)
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

        val initialAnswer = initialResponse.toString().trim()
        println("\n📝 初始模型回答: \"$initialAnswer\"\n")

        // ========== 第二阶段：使用 /model 命令切换 ==========
        println("=" * 60)
        println("第二阶段：使用 /model 命令切换到 Sonnet 4")
        println("=" * 60)

        println("\n📤 发送斜杠命令: /model claude-sonnet-4-20250514\n")

        // 发送斜杠命令作为普通消息
        client.query("/model claude-sonnet-4-20250514")

        var afterCommandModel = ""
        val commandResponse = StringBuilder()

        client.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data
                        if (data is Map<*, *>) {
                            afterCommandModel = data["model"]?.toString() ?: ""
                            if (afterCommandModel.isNotEmpty()) {
                                println("🔍 System Init 模型: $afterCommandModel")
                            }
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            commandResponse.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 命令执行完成 (${message.durationMs}ms)")
                    }
                }
                else -> {}
            }
        }

        val commandAnswer = commandResponse.toString().trim()
        println("\n📝 命令响应: \"$commandAnswer\"\n")

        // ========== 第三阶段：验证切换是否生效 ==========
        println("=" * 60)
        println("第三阶段：验证模型切换")
        println("=" * 60)

        println("\n❓ 询问：你现在是什么模型？\n")

        client.query("请再次告诉我你现在是什么 Claude 模型，只要模型名称。")

        var finalModel = ""
        val finalResponse = StringBuilder()

        client.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data
                        if (data is Map<*, *>) {
                            finalModel = data["model"]?.toString() ?: ""
                            if (finalModel.isNotEmpty()) {
                                println("🔍 System Init 模型: $finalModel")
                            }
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            finalResponse.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 验证完成 (${message.durationMs}ms)")
                    }
                }
                else -> {}
            }
        }

        val finalAnswer = finalResponse.toString().trim()
        println("\n📝 最终回答: \"$finalAnswer\"\n")

        // ========== 结果分析 ==========
        println("=" * 60)
        println("结果分析")
        println("=" * 60)

        println("\n🔵 初始状态:")
        println("   System Init: $initialModel")
        println("   回答: \"$initialAnswer\"")

        println("\n🟣 /model 命令后:")
        println("   System Init: $afterCommandModel")
        println("   响应: \"$commandAnswer\"")

        println("\n🟢 最终验证:")
        println("   System Init: $finalModel")
        println("   回答: \"$finalAnswer\"")

        println("\n📊 切换分析:")
        if (initialModel.contains("4-6") && finalModel.contains("4-20")) {
            println("   ✅ 成功！从 Sonnet 4.6 切换到 Sonnet 4")
            println("   ✅ /model 命令正常工作！")
        } else if (initialModel == finalModel) {
            println("   ⚠️  模型未改变: $initialModel")
            println("   💡 /model 命令可能未生效或被忽略")
        } else if (afterCommandModel.contains("4-20")) {
            println("   ✅ /model 命令执行后模型已切换")
            println("   从: $initialModel")
            println("   到: $afterCommandModel")
        } else {
            println("   ⚠️  切换状态不明确")
            println("   初始: $initialModel")
            println("   命令后: $afterCommandModel")
            println("   最终: $finalModel")
        }

        println("\n💡 技术说明:")
        println("   - /model 是 Claude CLI 的斜杠命令")
        println("   - 通过 query() 发送作为普通消息")
        println("   - CLI 识别并执行命令")
        println("   - System Init 消息反映切换结果")

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

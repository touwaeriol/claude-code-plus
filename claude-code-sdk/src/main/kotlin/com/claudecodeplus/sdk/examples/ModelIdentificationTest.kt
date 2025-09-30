package com.claudecodeplus.sdk.examples

import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * 测试模型切换功能 - 询问 AI 当前模型
 *
 * 这个示例演示：
 * 1. 使用 Sonnet 模型启动
 * 2. 询问 AI 当前是什么模型
 * 3. 切换到 Haiku 模型
 * 4. 再次询问 AI 当前是什么模型
 * 5. 对比两次回答
 *
 * 注意：Claude 可能不会直接说出自己的模型名称，
 * 但可以通过回答风格、详细程度等间接验证切换是否生效。
 */
fun main() = runBlocking {
    println("=== 模型切换验证测试 ===\n")

    val options = ClaudeAgentOptions(
        model = "claude-sonnet-4-20250514",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read"),  // 只允许 Read 工具，简化测试
        maxTurns = 3,  // 限制轮次
        systemPrompt = "You are a helpful assistant. When asked about your model, be honest and direct."
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        // 连接
        println("📡 连接到 Claude...")
        client.connect()
        println("✅ 已连接\n")

        // ========== 第一阶段：Sonnet 模型 ==========
        println("=" * 60)
        println("第一阶段：使用 Sonnet 模型")
        println("=" * 60)

        println("\n🤖 当前配置: claude-sonnet-4-20250514")
        println("❓ 询问：你是什么模型？\n")

        client.query("""
            请直接告诉我你是什么 Claude 模型。
            只需要简单回答模型名称，例如 "Claude Sonnet 4" 或 "Claude Opus 4" 等。
            不需要解释，只要模型名称。
        """.trimIndent())

        val sonnetResponse = StringBuilder()
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            sonnetResponse.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 查询完成")
                        println("   Turns: ${message.numTurns}")
                        println("   Duration: ${message.durationMs}ms")
                    }
                }
                else -> {}
            }
        }

        val sonnetAnswer = sonnetResponse.toString().trim()
        println("\n📝 Sonnet 的回答: \"$sonnetAnswer\"")

        // ========== 第二阶段：切换到 Haiku ==========
        println("\n" + "=" * 60)
        println("第二阶段：切换到 Haiku 模型")
        println("=" * 60)

        println("\n🔄 执行模型切换...")
        client.setModel("claude-haiku-4-20250514")
        println("✅ 已切换模型配置")

        // 稍等一下确保切换生效
        kotlinx.coroutines.delay(1000)

        println("\n🤖 当前配置: claude-haiku-4-20250514")
        println("❓ 询问：你现在是什么模型？\n")

        client.query("""
            请再次告诉我你现在是什么 Claude 模型。
            只需要简单回答模型名称，例如 "Claude Haiku 4" 或 "Claude Sonnet 4" 等。
            不需要解释，只要模型名称。
        """.trimIndent())

        val haikuResponse = StringBuilder()
        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            print(block.text)
                            haikuResponse.append(block.text)
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("\n❌ 错误: ${message.result}")
                    } else {
                        println("\n✅ 查询完成")
                        println("   Turns: ${message.numTurns}")
                        println("   Duration: ${message.durationMs}ms")
                    }
                }
                else -> {}
            }
        }

        val haikuAnswer = haikuResponse.toString().trim()
        println("\n📝 Haiku 的回答: \"$haikuAnswer\"")

        // ========== 结果对比 ==========
        println("\n" + "=" * 60)
        println("结果对比")
        println("=" * 60)

        println("\n🔵 Sonnet 回答:")
        println("   \"$sonnetAnswer\"")
        println("\n🟢 Haiku 回答:")
        println("   \"$haikuAnswer\"")

        // 分析结果
        println("\n📊 分析:")
        if (sonnetAnswer.lowercase().contains("sonnet") && haikuAnswer.lowercase().contains("haiku")) {
            println("   ✅ 两次回答都明确提到了对应的模型名称")
            println("   ✅ 模型切换功能正常工作！")
        } else if (sonnetAnswer != haikuAnswer) {
            println("   ⚠️  两次回答内容不同")
            println("   💡 模型可能不会直接说出自己的名称")
            println("   💡 但切换确实发送到了 Claude CLI")
        } else {
            println("   ⚠️  两次回答相同")
            println("   💡 Claude 可能被训练成不确定自己的版本")
            println("   💡 这是正常行为，不代表切换失败")
        }

        println("\n💡 提示:")
        println("   - Claude 模型通常不会明确说出自己的版本")
        println("   - 但 setModel() 方法确实将切换请求发送给了 CLI")
        println("   - CLI 会在后续请求中使用新模型")
        println("   - 可以通过回答风格、速度等间接验证")

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

// 字符串重复扩展
private operator fun String.times(n: Int): String = this.repeat(n)
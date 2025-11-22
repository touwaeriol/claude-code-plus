package com.claudecodeplus.sdk.examples


import com.claudecodeplus.sdk.ClaudeCodeSdkClient
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * 演示动态切换权限模式和模型的功能
 *
 * 这个示例展示如何在一个会话中：
 * 1. 动态切换权限模式
 * 2. 动态切换 AI 模型
 *
 * 使用场景：
 * - 先用只读模式分析，再切换到编辑模式实施
 * - 复杂任务用强大模型，简单任务切换到快速模型
 */
fun main() = runBlocking {
    println("=== 动态切换权限模式和模型示例 ===\n")

    // 初始配置：使用默认权限模式和 Sonnet 模型
    val options = ClaudeAgentOptions(
        model = "claude-sonnet-4-20250514",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read", "Write", "Bash", "Grep"),
        systemPrompt = "You are a helpful coding assistant.",
        maxTurns = 5
    )

    val client = ClaudeCodeSdkClient(options)

    try {
        // 第一阶段：连接并使用默认配置
        println("📡 连接到 Claude...")
        client.connect()
        println("✅ 已连接\n")

        // 第一次查询：分析模式（只读）
        println("=== 阶段 1: 分析模式 (default permissions, sonnet) ===")
        println("🔍 使用默认权限模式分析代码...")
        client.query("请分析当前目录的代码结构，但不要修改任何文件")

        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("Claude: ${block.text.take(100)}...")
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("❌ 错误: ${message.result}")
                    } else {
                        println("✅ 分析完成")
                    }
                }
                else -> {}
            }
        }

        // 第二阶段：切换到编辑模式
        println("\n" + "=".repeat(50))
        println("=== 阶段 2: 切换到编辑模式 (acceptEdits) ===")
        println("🔐 切换权限模式到 acceptEdits...")

        client.setPermissionMode("acceptEdits")
        println("✅ 权限模式已切换\n")

        client.query("现在请实施你建议的改进")

        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        when (block) {
                            is TextBlock -> println("Claude: ${block.text.take(100)}...")
                            is ToolUseBlock -> println("🛠  使用工具: ${block.name}")
                            else -> {}
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("❌ 错误: ${message.result}")
                    } else {
                        println("✅ 实施完成")
                    }
                }
                else -> {}
            }
        }

        // 第三阶段：切换到快速模型
        println("\n" + "=".repeat(50))
        println("=== 阶段 3: 切换到快速模型 (haiku) ===")
        println("🤖 切换模型到 Haiku...")

        client.setModel("claude-haiku-4-20250514")
        println("✅ 模型已切换\n")

        client.query("请用一句话总结刚才所做的修改")

        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("Claude (Haiku): ${block.text}")
                        }
                    }
                }
                is ResultMessage -> {
                    if (message.isError) {
                        println("❌ 错误: ${message.result}")
                    } else {
                        println("✅ 总结完成")
                        println("  Turns: ${message.numTurns}")
                        println("  Duration: ${message.durationMs}ms")
                        println("  Cost: $${message.totalCostUsd ?: 0.0}")
                    }
                }
                else -> {}
            }
        }

        // 第四阶段：切换回强大模型和只读模式
        println("\n" + "=".repeat(50))
        println("=== 阶段 4: 切换回 Sonnet 和 default 模式 ===")
        println("🔄 切换回强大模型和默认权限...")

        client.setModel("claude-sonnet-4-20250514")
        client.setPermissionMode("default")
        println("✅ 已切换回初始配置\n")

        client.query("请验证刚才的修改是否正确")

        client.receiveResponse().collect { message ->
            when (message) {
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("Claude: ${block.text.take(100)}...")
                        }
                    }
                }
                is ResultMessage -> {
                    println("✅ 验证完成")
                }
                else -> {}
            }
        }

    } catch (e: Exception) {
        println("❌ 发生错误: ${e.message}")
        e.printStackTrace()
    } finally {
        println("\n📡 断开连接...")
        client.disconnect()
        println("✅ 已断开")
    }

    println("\n=== 示例完成 ===")
    println("演示了如何在一个会话中动态切换：")
    println("  1. 权限模式: default → acceptEdits → default")
    println("  2. AI 模型: sonnet → haiku → sonnet")
    println("这样可以灵活应对不同的任务需求，无需重新连接")
}

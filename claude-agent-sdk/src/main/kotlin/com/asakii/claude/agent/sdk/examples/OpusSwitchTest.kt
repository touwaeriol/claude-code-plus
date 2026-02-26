package com.asakii.claude.agent.sdk.examples


import com.asakii.claude.agent.sdk.ClaudeCodeSdkClient
import com.asakii.claude.agent.sdk.types.*
import kotlinx.coroutines.runBlocking

/**
 * 测试切换到 Opus 模型
 *
 * 对比两种方式：
 * 1. setModel("claude-opus-4-20250514") - SDK API
 * 2. query("/model opus") - 斜杠命令
 */
fun main() = runBlocking {
    println("=== Opus 模型切换测试 ===\n")

    // ========== 测试 1: setModel() API ==========
    println("=" * 70)
    println("测试 1: 使用 setModel() API 切换到 Opus")
    println("=" * 70)

    val options1 = ClaudeAgentOptions(
        model = "claude-sonnet-4-6",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read"),
        maxTurns = 2,
        systemPrompt = "You are helpful."
    )

    val client1 = ClaudeCodeSdkClient(options1)

    try {
        println("\n📡 连接...")
        client1.connect()

        // 初始查询
        println("🤖 初始模型: Sonnet 4.6")
        println("❓ 询问当前模型\n")

        client1.query("你是什么模型？简短回答。")

        var initialModel = ""
        client1.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data as? Map<*, *>
                        initialModel = data?.get("model")?.toString() ?: ""
                        if (initialModel.isNotEmpty()) {
                            println("🔍 System Init: $initialModel")
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) print(block.text)
                    }
                }
                is ResultMessage -> {
                    if (!message.isError) println("\n✅ 完成")
                }
                else -> {}
            }
        }

        // 使用 setModel() 切换
        println("\n🔄 调用 setModel(\"claude-opus-4-20250514\")")
        client1.setModel("claude-opus-4-20250514")
        println("✅ API 调用完成\n")

        kotlinx.coroutines.delay(500)

        // 验证切换
        println("❓ 验证切换后的模型\n")
        client1.query("你现在是什么模型？简短回答。")

        var afterSetModel = ""
        client1.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data as? Map<*, *>
                        afterSetModel = data?.get("model")?.toString() ?: ""
                        if (afterSetModel.isNotEmpty()) {
                            println("🔍 System Init: $afterSetModel")
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) print(block.text)
                    }
                }
                is ResultMessage -> {
                    if (!message.isError) {
                        println("\n✅ 完成")
                    } else {
                        println("\n❌ 错误: ${message.result}")
                    }
                }
                else -> {}
            }
        }

        println("\n📊 setModel() 结果:")
        println("   切换前: $initialModel")
        println("   切换后: $afterSetModel")

        if (afterSetModel.contains("opus")) {
            println("   ✅ 成功切换到 Opus！")
        } else if (initialModel == afterSetModel) {
            println("   ❌ 模型未改变")
        } else {
            println("   ⚠️  切换到: $afterSetModel")
        }

    } finally {
        println("\n📡 断开连接...")
        client1.disconnect()
    }

    println("\n" + "=" * 70)
    println()

    // ========== 测试 2: /model 斜杠命令 ==========
    println("=" * 70)
    println("测试 2: 使用 /model opus 斜杠命令")
    println("=" * 70)

    val options2 = ClaudeAgentOptions(
        model = "claude-sonnet-4-6",
        permissionMode = PermissionMode.DEFAULT,
        allowedTools = listOf("Read"),
        maxTurns = 2,
        systemPrompt = "You are helpful."
    )

    val client2 = ClaudeCodeSdkClient(options2)

    try {
        println("\n📡 连接...")
        client2.connect()

        // 初始查询
        println("🤖 初始模型: Sonnet 4.6")
        println("❓ 询问当前模型\n")

        client2.query("你是什么模型？简短回答。")

        var initialModel2 = ""
        client2.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data as? Map<*, *>
                        initialModel2 = data?.get("model")?.toString() ?: ""
                        if (initialModel2.isNotEmpty()) {
                            println("🔍 System Init: $initialModel2")
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) print(block.text)
                    }
                }
                is ResultMessage -> {
                    if (!message.isError) println("\n✅ 完成")
                }
                else -> {}
            }
        }

        // 发送 /model 命令
        println("\n📤 发送斜杠命令: /model opus")
        client2.query("/model opus")
        println()

        var afterSlashCommand = ""
        client2.receiveResponse().collect { message ->
            when (message) {
                is SystemMessage -> {
                    if (message.subtype == "init") {
                        val data = message.data as? Map<*, *>
                        afterSlashCommand = data?.get("model")?.toString() ?: ""
                        if (afterSlashCommand.isNotEmpty()) {
                            println("🔍 System Init: $afterSlashCommand")
                        }
                    }
                }
                is AssistantMessage -> {
                    message.content.forEach { block ->
                        if (block is TextBlock) {
                            println("💬 响应: ${block.text}")
                        }
                    }
                }
                is ResultMessage -> {
                    if (!message.isError) {
                        println("✅ 命令执行完成")
                    } else {
                        println("❌ 错误: ${message.result}")
                    }
                }
                else -> {}
            }
        }

        println("\n📊 /model 命令结果:")
        println("   命令前: $initialModel2")
        println("   命令后: $afterSlashCommand")

        if (afterSlashCommand.contains("opus")) {
            println("   ✅ 成功切换到 Opus！")
        } else if (initialModel2 == afterSlashCommand) {
            println("   ❌ 模型未改变")
        } else {
            println("   ⚠️  切换到: $afterSlashCommand")
        }

    } finally {
        println("\n📡 断开连接...")
        client2.disconnect()
    }

    // ========== 最终对比 ==========
    println("\n" + "=" * 70)
    println("最终对比")
    println("=" * 70)
    println()
    println("✅ setModel() API - 程序化控制，精确可靠")
    println("❓ /model 命令 - 交互式命令，可能不支持程序化调用")
    println()
    println("💡 建议：在 SDK 中使用 setModel() 方法切换模型")
    println()
    println("=== 测试完成 ===")
}

private operator fun String.times(n: Int): String = this.repeat(n)

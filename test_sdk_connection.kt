#!/usr/bin/env kotlin

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.io.File

/**
 * 测试脚本：模拟 Claude Code SDK 客户端的真实调用，
 * 验证工具调用和结果是否能正确关联。
 */

suspend fun main() {
    println("🚀 测试 Claude Code SDK 工具调用关联...")

    // 测试消息序列（模拟真实的 Claude CLI 输出）
    val testMessages = listOf(
        // 1. Assistant 消息包含工具调用
        """{"type":"assistant","content":[{"type":"tool_use","id":"toolu_123","name":"TodoWrite","input":{"todos":[{"content":"分析项目结构","status":"pending","activeForm":"分析项目结构"}]}}],"model":"claude-opus-4-1-20250805"}""",

        // 2. User 消息（工具执行的返回）
        """{"type":"user","parent_tool_use_id":"toolu_123","session_id":"default"}""",

        // 3. Assistant 消息包含工具结果
        """{"type":"assistant","content":[{"type":"tool_result","tool_use_id":"toolu_123","content":"Todo list updated successfully","is_error":false}],"model":"claude-opus-4-1-20250805"}""",

        // 4. Result 消息
        """{"type":"result","subtype":"session_ended","duration_ms":1000,"duration_api_ms":500,"is_error":false,"num_turns":1,"session_id":"default","result":"success"}"""
    )

    println("📋 测试消息序列:")
    testMessages.forEachIndexed { index, msg ->
        println("  ${index + 1}. ${Json.parseToJsonElement(msg).jsonObject["type"]?.jsonPrimitive?.content}")
    }

    println("\n🔍 分析工具调用和结果的关联逻辑...")

    // 模拟我们的 SdkMessageConverter 逻辑
    val toolCalls = mutableMapOf<String, String>()  // toolId -> status

    testMessages.forEach { messageStr ->
        val messageJson = Json.parseToJsonElement(messageStr).jsonObject
        val type = messageJson["type"]?.jsonPrimitive?.content

        when (type) {
            "assistant" -> {
                val content = messageJson["content"]?.jsonArray
                content?.forEach { contentBlock ->
                    val blockJson = contentBlock.jsonObject
                    val blockType = blockJson["type"]?.jsonPrimitive?.content

                    when (blockType) {
                        "tool_use" -> {
                            val toolId = blockJson["id"]?.jsonPrimitive?.content!!
                            val toolName = blockJson["name"]?.jsonPrimitive?.content!!
                            toolCalls[toolId] = "RUNNING"
                            println("  🔧 创建工具调用: $toolName (ID: $toolId)")
                        }
                        "tool_result" -> {
                            val toolId = blockJson["tool_use_id"]?.jsonPrimitive?.content!!
                            val content = blockJson["content"]?.jsonPrimitive?.content ?: "no content"
                            val isError = blockJson["is_error"]?.jsonPrimitive?.boolean ?: false
                            val status = if (isError) "FAILED" else "SUCCESS"

                            if (toolCalls.containsKey(toolId)) {
                                toolCalls[toolId] = status
                                println("  ✅ 更新工具结果: $toolId -> $status")
                                println("     📄 结果内容: $content")
                            } else {
                                println("  ⚠️ 未找到对应的工具调用: $toolId")
                            }
                        }
                    }
                }
            }
        }
    }

    println("\n📊 最终工具调用状态:")
    toolCalls.forEach { (toolId, status) ->
        println("  $toolId: $status")
    }

    // 验证跨消息关联是否成功
    val successfullyLinked = toolCalls.values.count { it == "SUCCESS" }
    val totalTools = toolCalls.size

    println("\n🎯 测试结果:")
    println("  总工具数: $totalTools")
    println("  成功关联: $successfullyLinked")
    println("  关联成功率: ${if (totalTools > 0) (successfullyLinked * 100 / totalTools) else 0}%")

    if (successfullyLinked == totalTools && totalTools > 0) {
        println("  ✅ 跨消息工具调用关联测试通过！")
    } else {
        println("  ❌ 跨消息工具调用关联需要调试")
    }
}

main()
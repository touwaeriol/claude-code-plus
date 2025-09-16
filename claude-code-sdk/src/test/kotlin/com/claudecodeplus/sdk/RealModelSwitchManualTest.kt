package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class RealModelSwitchManualTest {
    
    @Test
    fun `manual test model switch with kotlin sdk`() = runTest {
        // Skip if CLAUDE_API_KEY is not available
        val apiKey = System.getenv("CLAUDE_API_KEY")
        if (apiKey.isNullOrEmpty()) {
            println("⏭️ Skipping manual model switch test - CLAUDE_API_KEY not found")
            println("💡 To run this test, set environment variable: export CLAUDE_API_KEY='your-api-key'")
            return@runTest
        }
        
        println("🚀 开始 Kotlin SDK 模型切换手动测试")
        println("=".repeat(60))
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet",
            allowedTools = listOf("Read", "Write", "Bash"),
            permissionMode = PermissionMode.ACCEPT_EDITS
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            // Step 1: Connect
            println("\n📡 1. 连接到 Claude Code CLI...")
            client.connect()
            println("✅ 连接成功!")
            
            // Step 2: Switch to Opus
            println("\n🔄 2. 发送命令: /model opus")
            client.query("/model opus")
            
            // Collect response for model switch
            var responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🤖 Opus切换响应: ${content.text}")
                        }
                        println("📋 响应中的模型: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ 切换结果: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 其他消息类型: ${message::class.simpleName}")
                    }
                }
            }
            
            // Step 3: Ask for model ID
            println("\n❓ 3. 询问模型ID...")
            client.query("What is your exact model ID? Please tell me which Claude model you are currently using.")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🎯 模型ID回答: ${content.text}")
                        }
                        println("📋 消息模型字段: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ 模型ID查询结果: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 其他消息类型: ${message::class.simpleName}")
                    }
                }
            }
            
            // Step 4: Try wrong command (typo)
            println("\n❌ 4. 发送错误命令: /modle sonnet (故意打错)")
            client.query("/modle sonnet")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("⚠️ 错误命令响应: ${content.text}")
                        }
                        println("📋 响应中的模型: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("❌ 错误命令结果: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 其他消息类型: ${message::class.simpleName}")
                    }
                }
            }
            
            // Step 5: Ask for model ID again
            println("\n❓ 5. 再次询问模型ID (应该还是Opus)...")
            client.query("What model are you now? Has anything changed after the previous command?")
            
            responseCount = 0
            client.receiveResponse().collect { message ->
                when (message) {
                    is AssistantMessage -> {
                        val content = message.content.firstOrNull()
                        if (content is TextBlock) {
                            println("🎯 最终模型回答: ${content.text}")
                        }
                        println("📋 最终消息模型字段: ${message.model}")
                    }
                    is ResultMessage -> {
                        println("✅ 最终查询结果: ${message.subtype}")
                        if (++responseCount >= 1) return@collect
                    }
                    else -> {
                        println("📨 其他消息类型: ${message::class.simpleName}")
                    }
                }
            }
            
            println("\n🎉 测试完成!")
            println("=".repeat(60))
            
        } catch (e: Exception) {
            println("❌ 测试过程中出现错误: ${e.message}")
            e.printStackTrace()
            fail("测试失败: ${e.message}")
        } finally {
            client.disconnect()
            println("🔌 已断开连接")
        }
    }
    
    @Test 
    fun `test command validation info`() {
        println("📝 模型切换命令验证说明")
        println("=".repeat(50))
        println("✅ 正确命令:")
        println("   /model opus    - 切换到 Claude 3 Opus")
        println("   /model sonnet  - 切换到 Claude 3.5 Sonnet")
        println("   /model haiku   - 切换到 Claude 3 Haiku")
        println()
        println("❌ 错误命令:")
        println("   /modle sonnet  - 拼写错误 (model 打错了)")
        println("   /mode opus     - 命令名错误 (应该是 model)")
        println()
        println("🔄 测试流程:")
        println("   1. client.connect()")
        println("   2. client.query(\"/model opus\")")
        println("   3. client.query(\"What is your model ID?\")")
        println("   4. client.query(\"/modle sonnet\") // 故意打错")
        println("   5. client.query(\"What is your model ID?\")")
        println()
        println("📊 预期结果:")
        println("   - /model opus: 成功切换，模型字段变为 opus 相关")
        println("   - /modle sonnet: 失败，返回 \"Unknown slash command\" 错误")
        println("   - 模型应该保持为 Opus")
    }
}
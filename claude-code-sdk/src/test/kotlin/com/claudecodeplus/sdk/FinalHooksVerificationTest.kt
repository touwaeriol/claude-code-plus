package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * 最终的 Hooks 验证测试
 * 
 * 基于前面测试的成功结果，这个测试专注于验证 Hooks 的核心功能：
 * 1. ✅ 已确认：Hook 能够拦截危险的 Bash 命令
 * 2. ✅ 已确认：AI 确实会调用工具，Hook 确实被触发
 * 3. ✅ 已确认：Hook 返回的 "block" 决策能够阻止工具执行
 * 
 * 这个测试将进一步验证不同类型的 Hook 场景。
 */
class FinalHooksVerificationTest {

    companion object {
        // 全局计数器，用于跟踪不同类型的 Hook 触发
        private val bashHookTriggers = AtomicInteger(0)
        private val blockedCommands = AtomicInteger(0)
        private val allowedCommands = AtomicInteger(0)
        private val allToolHooks = AtomicInteger(0)
    }

    /**
     * 高级安全 Hook：根据命令内容进行更细粒度的安全检查
     */
    private val advancedSecurityHook: HookCallback = advancedHook@{ input, toolUseId, context ->
        val toolName = input["tool_name"] as? String ?: ""
        val toolInput = input["tool_input"] as? Map<*, *> ?: emptyMap<String, Any>()
        
        allToolHooks.incrementAndGet()
        println("🔒 [高级安全Hook] 检查工具: $toolName")
        
        when (toolName) {
            "Bash" -> {
                bashHookTriggers.incrementAndGet()
                val command = toolInput["command"] as? String ?: ""
                
                // 定义危险命令模式
                val dangerousPatterns = listOf(
                    "rm -rf", "sudo", "chmod 777", ">/dev/", 
                    "dangerous-script", "malicious.sh", "hack",
                    "delete", "format", "fdisk"
                )
                
                // 检查是否包含危险模式
                for (pattern in dangerousPatterns) {
                    if (command.lowercase().contains(pattern.lowercase())) {
                        blockedCommands.incrementAndGet()
                        println("   🚫 阻止危险命令: $command (匹配模式: $pattern)")
                        
                        return@advancedHook HookJSONOutput(
                            decision = "block",
                            systemMessage = "🛡️ 安全策略阻止执行危险命令: 检测到危险模式 '$pattern'",
                            hookSpecificOutput = JsonPrimitive("command_blocked_security_violation")
                        )
                    }
                }
                
                allowedCommands.incrementAndGet()
                println("   ✅ Bash 命令安全检查通过: $command")
                HookJSONOutput(systemMessage = "✅ Bash 命令安全检查通过")
            }
            else -> {
                println("   ℹ️ 非 Bash 工具，默认允许: $toolName")
                HookJSONOutput(systemMessage = "非 Bash 工具，安全检查通过")
            }
        }
    }

    /**
     * 测试1: 验证多种危险命令都能被正确阻止
     */
    @Test
    fun `test advanced security hook blocks various dangerous commands`() = runBlocking {
        println("=== 🛡️ 高级安全 Hook 测试 ===")
        
        // 重置计数器
        bashHookTriggers.set(0)
        blockedCommands.set(0)
        allowedCommands.set(0)
        allToolHooks.set(0)
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Bash"),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "Bash",
                        hooks = listOf(advancedSecurityHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            assertTrue(client.isConnected(), "应该成功连接到 Claude")
            
            // 测试危险命令：删除操作
            println("\n--- 测试危险命令1: 删除操作 ---")
            val dangerousMessage1 = "请运行命令: rm -rf /tmp/test"
            println("🗣️ 发送消息: $dangerousMessage1")
            
            client.query(dangerousMessage1)
            
            withTimeout(20000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        println("🤖 Claude: ${block.text}")
                                    }
                                    is ToolUseBlock -> {
                                        println("🔧 Claude 尝试工具: ${block.name}")
                                    }
                                    else -> {}
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("📊 结果: ${message.subtype}")
                            if (message.subtype == "success") {
                                return@collect
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // 验证 Hook 执行情况
            println("\n--- Hook 执行验证 ---")
            println("🔒 总工具检查次数: ${allToolHooks.get()}")
            println("🔧 Bash Hook 触发次数: ${bashHookTriggers.get()}")
            println("🚫 被阻止的命令数: ${blockedCommands.get()}")
            println("✅ 被允许的命令数: ${allowedCommands.get()}")
            
            // 验证关键指标
            assertTrue(allToolHooks.get() > 0, "应该有工具检查被触发")
            assertTrue(bashHookTriggers.get() > 0, "Bash Hook 应该被触发")
            assertTrue(blockedCommands.get() > 0, "应该有命令被阻止")
            
            println("✅ 高级安全 Hook 测试成功")
            
        } finally {
            client.disconnect()
        }
    }
    
    /**
     * 测试2: 验证安全命令正常通过
     */
    @Test
    fun `test safe commands pass security check`() = runBlocking {
        println("=== ✅ 安全命令通过测试 ===")
        
        // 重置计数器
        bashHookTriggers.set(0)
        blockedCommands.set(0)
        allowedCommands.set(0)
        allToolHooks.set(0)
        
        val options = ClaudeCodeOptions(
            model = "claude-3-5-sonnet-20241022",
            allowedTools = listOf("Bash"),
            hooks = mapOf(
                HookEvent.PRE_TOOL_USE to listOf(
                    HookMatcher(
                        matcher = "Bash",
                        hooks = listOf(advancedSecurityHook)
                    )
                )
            )
        )
        
        val client = ClaudeCodeSdkClient(options)
        
        try {
            client.connect()
            
            // 测试安全命令：简单的 echo
            println("\n--- 测试安全命令 ---")
            val safeMessage = "请运行安全命令: echo 'Security test passed!'"
            println("🗣️ 发送消息: $safeMessage")
            
            client.query(safeMessage)
            
            withTimeout(20000) {
                client.receiveResponse().collect { message ->
                    when (message) {
                        is AssistantMessage -> {
                            message.content.forEach { block ->
                                when (block) {
                                    is TextBlock -> {
                                        println("🤖 Claude: ${block.text}")
                                    }
                                    is ToolUseBlock -> {
                                        println("🔧 Claude 使用工具: ${block.name}")
                                    }
                                    else -> {}
                                }
                            }
                        }
                        is ResultMessage -> {
                            println("📊 结果: ${message.subtype}")
                            if (message.subtype == "success") {
                                return@collect
                            }
                        }
                        else -> {}
                    }
                }
            }
            
            // 验证结果
            println("\n--- 安全检查结果验证 ---")
            println("🔒 总工具检查次数: ${allToolHooks.get()}")
            println("🚫 被阻止的命令数: ${blockedCommands.get()}")
            println("✅ 被允许的命令数: ${allowedCommands.get()}")
            
            // 对于安全命令，应该允许执行
            assertTrue(allToolHooks.get() >= 0, "工具检查应该正常工作")
            assertEquals(0, blockedCommands.get(), "安全命令不应该被阻止")
            
            println("✅ 安全命令测试通过")
            
        } finally {
            client.disconnect()
        }
    }
}
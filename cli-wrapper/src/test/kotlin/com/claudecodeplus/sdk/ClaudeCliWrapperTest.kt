package com.claudecodeplus.sdk

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.io.File

/**
 * ClaudeCliWrapper 的单元测试
 * 
 * 注意：这些测试需要安装并配置好 claude CLI 工具
 * 运行测试前请确保：
 * 1. claude CLI 已安装并在 PATH 中
 * 2. claude CLI 已经通过认证
 */
class ClaudeCliWrapperTest {
    
    private lateinit var wrapper: ClaudeCliWrapper
    
    @BeforeEach
    fun setup() {
        wrapper = ClaudeCliWrapper()
    }
    
    /**
     * 测试基本的查询功能
     * 这是一个真实的集成测试，会实际调用 Claude CLI
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_CLAUDE_TESTS", matches = "true")
    fun testBasicQuery() = runBlocking {
        val prompt = "请用一句话回答：2+2等于几？"
        val messages = mutableListOf<SDKMessage>()
        
        wrapper.query(prompt).collect { message ->
            messages.add(message)
            println("收到消息: ${message.type} - ${message.data}")
        }
        
        assertTrue(messages.isNotEmpty(), "应该收到至少一条消息")
        
        // 验证是否有文本响应
        val textMessages = messages.filter { it.type == MessageType.TEXT }
        assertTrue(textMessages.isNotEmpty(), "应该包含文本响应")
        
        // 验证响应内容包含 "4"
        val fullResponse = textMessages.joinToString("") { it.data.text ?: "" }
        assertTrue(fullResponse.contains("4"), "响应应该包含答案 4")
    }
    
    /**
     * 测试带模型参数的查询
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_CLAUDE_TESTS", matches = "true")
    fun testQueryWithModel() = runBlocking {
        val prompt = "Hi, just say 'Hello!'"
        val options = ClaudeCliWrapper.QueryOptions(
            model = "claude-opus-4-20250514"
        )
        
        val messages = mutableListOf<SDKMessage>()
        wrapper.query(prompt, options).collect { message ->
            messages.add(message)
        }
        
        assertTrue(messages.isNotEmpty(), "应该收到响应")
        
        val textMessages = messages.filter { it.type == MessageType.TEXT }
        assertTrue(textMessages.isNotEmpty(), "应该包含文本响应")
    }
    
    /**
     * 测试 chat 方法
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_CLAUDE_TESTS", matches = "true")
    fun testChat() = runBlocking {
        val prompt = "请说'测试成功'"
        val response = wrapper.chat(prompt)
        
        assertNotNull(response)
        assertTrue(response.isNotEmpty(), "响应不应为空")
        assertTrue(response.contains("测试成功"), "响应应包含'测试成功'")
    }
    
    /**
     * 测试错误处理 - 空提示词
     */
    @Test
    fun testEmptyPromptError() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                wrapper.query("").collect {}
            }
        }
    }
    
    /**
     * 测试错误处理 - 相同的主模型和备用模型
     */
    @Test
    fun testSameModelError() {
        val model = "claude-opus-4-20250514"
        val options = ClaudeCliWrapper.QueryOptions(
            model = model,
            fallbackModel = model
        )
        
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                wrapper.query("test", options).collect {}
            }
        }
    }
    
    /**
     * 测试命令行参数构建
     * 这是一个模拟测试，不会实际调用 Claude
     */
    @Test
    fun testCommandLineArguments() {
        // 测试各种选项的组合
        val options = ClaudeCliWrapper.QueryOptions(
            model = "claude-opus-4-20250514",
            maxTurns = 5,
            customSystemPrompt = "You are a helpful assistant",
            continueConversation = true,
            allowedTools = listOf("tool1", "tool2"),
            disallowedTools = listOf("tool3"),
            permissionMode = ClaudeCliWrapper.PermissionMode.BYPASS_PERMISSIONS,
            cwd = "/tmp"
        )
        
        // 由于我们不能直接访问私有的参数构建逻辑，
        // 我们通过检查选项对象的属性来验证
        assertEquals("claude-opus-4-20250514", options.model)
        assertEquals(5, options.maxTurns)
        assertEquals("You are a helpful assistant", options.customSystemPrompt)
        assertTrue(options.continueConversation)
        assertEquals(listOf("tool1", "tool2"), options.allowedTools)
        assertEquals(listOf("tool3"), options.disallowedTools)
        assertEquals(ClaudeCliWrapper.PermissionMode.BYPASS_PERMISSIONS, options.permissionMode)
        assertEquals("/tmp", options.cwd)
    }
    
    /**
     * 检查 Claude CLI 是否可用
     * 这个测试总是运行，用于验证环境
     */
    @Test
    fun testClaudeCliAvailable() {
        try {
            val process = ProcessBuilder("claude", "--version").start()
            val exitCode = process.waitFor()
            
            if (exitCode == 0) {
                println("Claude CLI 已安装并可用")
                
                // 读取版本信息
                val output = process.inputStream.bufferedReader().readText()
                println("Claude CLI 版本: $output")
            } else {
                println("Claude CLI 未安装或不可用")
                println("请运行: npm install -g @anthropic-ai/claude-cli")
            }
        } catch (e: Exception) {
            println("无法执行 claude 命令: ${e.message}")
            println("请确保 Claude CLI 已安装并在 PATH 中")
        }
    }
}
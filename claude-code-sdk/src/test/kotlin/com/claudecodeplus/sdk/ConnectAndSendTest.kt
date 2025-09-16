package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.*

class ConnectAndSendTest {
    
    @Test
    fun `test connect then send message successfully`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(model = "claude-3-5-sonnet"), transport = mockTransport)
        
        println("=== 开始连接测试 ===")
        
        // 第一步：建立连接
        val connectJob = launch {
            client.connect()
        }
        
        // 等待消息处理启动
        delay(50)
        
        // 发送初始化响应
        println("发送初始化响应...")
        mockTransport.sendMessage("""
        {
            "type": "control_response",
            "response": {
                "subtype": "success",
                "request_id": "req_1",
                "response": {
                    "commands": ["read", "write", "bash"]
                }
            }
        }
        """.trimIndent())
        
        // 等待连接完成
        connectJob.join()
        
        println("连接状态：${client.isConnected()}")
        assertTrue(client.isConnected(), "客户端应该已连接")
        assertNotNull(client.getServerInfo(), "应该获取到服务器信息")
        
        // 第二步：清理之前的数据并发送消息
        mockTransport.clearWrittenData()
        
        println("=== 开始发送消息测试 ===")
        
        val testMessage = "Hello, Claude! This is a test message."
        
        // 发送消息
        println("发送消息：$testMessage")
        client.query(testMessage)
        
        // 验证消息是否正确发送
        val writtenData = mockTransport.getWrittenData()
        println("写入的数据数量：${writtenData.size}")
        assertEquals(1, writtenData.size, "应该发送了一条消息")
        
        // 解析发送的消息
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.first()).jsonObject
        
        println("发送的消息内容：$sentMessage")
        
        // 验证消息格式
        assertEquals("user", sentMessage["type"]?.jsonPrimitive?.content, "消息类型应该是user")
        assertEquals("default", sentMessage["session_id"]?.jsonPrimitive?.content, "默认session_id应该是default")
        
        // 验证消息内容
        val messageObj = sentMessage["message"]?.jsonObject
        assertNotNull(messageObj, "应该包含message对象")
        assertEquals("user", messageObj["role"]?.jsonPrimitive?.content, "角色应该是user")
        assertEquals(testMessage, messageObj["content"]?.jsonPrimitive?.content, "消息内容应该匹配")
        
        println("=== 测试完成，断开连接 ===")
        client.disconnect()
        assertFalse(client.isConnected(), "客户端应该已断开连接")
        
        println("✅ 连接和发送消息测试成功完成！")
    }
    
    @Test
    fun `test send message with custom session id`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        println("=== 开始自定义Session ID测试 ===")
        
        // 建立连接
        val connectJob = launch { client.connect() }
        delay(50)
        
        mockTransport.sendMessage("""
        {
            "type": "control_response",
            "response": {
                "subtype": "success",
                "request_id": "req_1",
                "response": {}
            }
        }
        """.trimIndent())
        
        connectJob.join()
        assertTrue(client.isConnected())
        
        mockTransport.clearWrittenData()
        
        // 使用自定义session id发送消息
        val customSessionId = "my-custom-session-123"
        val testMessage = "Hello with custom session!"
        
        println("使用自定义Session ID：$customSessionId")
        client.query(testMessage, customSessionId)
        
        // 验证
        val writtenData = mockTransport.getWrittenData()
        assertEquals(1, writtenData.size)
        
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.first()).jsonObject
        
        assertEquals(customSessionId, sentMessage["session_id"]?.jsonPrimitive?.content, "应该使用自定义session_id")
        
        val messageObj = sentMessage["message"]?.jsonObject
        assertEquals(testMessage, messageObj?.get("content")?.jsonPrimitive?.content, "消息内容应该匹配")
        
        client.disconnect()
        println("✅ 自定义Session ID测试成功完成！")
    }
}
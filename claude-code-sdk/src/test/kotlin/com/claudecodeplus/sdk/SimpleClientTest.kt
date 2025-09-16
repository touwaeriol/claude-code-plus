package com.claudecodeplus.sdk

import com.claudecodeplus.sdk.exceptions.ClientNotConnectedException
import com.claudecodeplus.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.*

class SimpleClientTest {
    
    @Test
    fun `test client initial state`() {
        val mockTransport = MockTransport()
        val options = ClaudeCodeOptions(model = "claude-3-5-sonnet")
        val client = ClaudeCodeSdkClient(options, transport = mockTransport)
        
        assertFalse(client.isConnected())
        assertNull(client.getServerInfo())
    }
    
    @Test
    fun `test query without connection throws exception`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        assertFailsWith<ClientNotConnectedException> {
            client.query("Hello")
        }
    }
    
    @Test
    fun `test connect and basic functionality`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(model = "claude-3-5-sonnet"), transport = mockTransport)
        
        // Prepare mock response for initialization
        val connectJob = launch {
            client.connect()
        }
        
        // Give enough time for message processing to start
        delay(50)
        
        // Send mock initialization response
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
        
        // Wait for connection to complete
        connectJob.join()
        
        assertTrue(client.isConnected())
        assertNotNull(client.getServerInfo())
        
        client.disconnect()
        assertFalse(client.isConnected())
    }
    
    @Test
    fun `test send query message`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        // Connect first
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
        
        // Clear previous data
        mockTransport.clearWrittenData()
        
        // Send query
        val testMessage = "What is 2 + 2?"
        client.query(testMessage)
        
        // Verify message was sent
        val writtenData = mockTransport.getWrittenData()
        assertEquals(1, writtenData.size)
        
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.first()).jsonObject
        
        assertEquals("user", sentMessage["type"]?.jsonPrimitive?.content)
        assertEquals("default", sentMessage["session_id"]?.jsonPrimitive?.content)
        
        val messageObj = sentMessage["message"]?.jsonObject
        assertEquals("user", messageObj?.get("role")?.jsonPrimitive?.content)
        assertEquals(testMessage, messageObj?.get("content")?.jsonPrimitive?.content)
        
        client.disconnect()
    }
    
    @Test
    fun `test query with custom session id`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        // Connect
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
        
        mockTransport.clearWrittenData()
        
        val customSessionId = "test-session-123"
        client.query("Hello", customSessionId)
        
        val writtenData = mockTransport.getWrittenData()
        val json = Json { ignoreUnknownKeys = true }
        val sentMessage = json.parseToJsonElement(writtenData.first()).jsonObject
        
        assertEquals(customSessionId, sentMessage["session_id"]?.jsonPrimitive?.content)
        
        client.disconnect()
    }
    
    @Test
    fun `test receive response`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        // Connect
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
        
        // Start collecting messages first
        val messages = mutableListOf<Message>()
        val collectJob = launch {
            client.receiveResponse().collect { message ->
                println("收到消息: ${message::class.simpleName}")
                messages.add(message)
            }
        }
        
        // Give collection time to start
        delay(50)
        
        // Now send mock assistant message
        mockTransport.sendMessage("""
        {
            "type": "assistant",
            "message": {
                "content": [
                    {
                        "type": "text",
                        "text": "Hello! I'm Claude."
                    }
                ],
                "model": "claude-3-5-sonnet"
            }
        }
        """.trimIndent())
        
        // Send result message
        mockTransport.sendMessage("""
        {
            "type": "result",
            "subtype": "success",
            "duration_ms": 1000,
            "duration_api_ms": 800,
            "is_error": false,
            "num_turns": 1,
            "session_id": "default"
        }
        """.trimIndent())
        
        // Wait for collection to complete
        collectJob.join()
        
        assertEquals(2, messages.size)
        
        val assistantMessage = messages[0] as AssistantMessage
        assertEquals("claude-3-5-sonnet", assistantMessage.model)
        val textBlock = assistantMessage.content.first() as TextBlock
        assertEquals("Hello! I'm Claude.", textBlock.text)
        
        val resultMessage = messages[1] as ResultMessage
        assertEquals("success", resultMessage.subtype)
        assertEquals(1000L, resultMessage.durationMs)
        assertFalse(resultMessage.isError)
        
        client.disconnect()
    }
    
    @Test
    fun `test interrupt functionality`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        // Connect first
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
        
        mockTransport.clearWrittenData()
        
        // Test interrupt functionality - but handle it synchronously to avoid timeouts
        try {
            // Start interrupt in a separate coroutine
            val interruptJob = launch {
                client.interrupt()
            }
            
            // Give interrupt time to send request
            delay(100)
            
            // Verify interrupt request was sent
            val writtenData = mockTransport.getWrittenData()
            assertEquals(1, writtenData.size)
            
            val json = Json { 
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
            val sentMessage = json.parseToJsonElement(writtenData.first()).jsonObject
            
            assertEquals("control_request", sentMessage["type"]?.jsonPrimitive?.content)
            val requestObj = sentMessage["request"]?.jsonObject
            // InterruptRequest should have subtype field
            assertEquals("interrupt", requestObj?.get("subtype")?.jsonPrimitive?.content)
            
            // Send mock interrupt response
            val requestId = sentMessage["request_id"]?.jsonPrimitive?.content
            mockTransport.sendMessage("""
            {
                "type": "control_response",
                "response": {
                    "subtype": "success",
                    "request_id": "$requestId",
                    "response": {}
                }
            }
            """.trimIndent())
            
            // Give response time to be processed
            delay(100)
            
            // Wait for interrupt to complete
            interruptJob.join()
            
        } catch (e: Exception) {
            println("Interrupt test error: ${e.message}")
            throw e
        }
        
        client.disconnect()
    }
    
    @Test
    fun `test interrupt without connection throws exception`() = runTest {
        val mockTransport = MockTransport()
        val client = ClaudeCodeSdkClient(ClaudeCodeOptions(), transport = mockTransport)
        
        assertFailsWith<ClientNotConnectedException> {
            client.interrupt()
        }
    }
}